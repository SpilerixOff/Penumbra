package fr.penumbra.perception;

import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.director.HorrorDirector;
import fr.penumbra.director.sequence.LinkedSceneManager;
import fr.penumbra.entity.absent.AbsentEntity;
import fr.penumbra.entity.absent.AbsentManager;
import fr.penumbra.story.StoryManager;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;

/**
 * Possède les perceptions privées côté serveur.
 *
 * <p>Une session appartient à un seul joueur et n'est jamais coordonnée avec
 * le verrou de groupe : deux voisins peuvent donc voir des réalités différentes.
 * L'entité associée reste transitoire et est supprimée lors d'une déconnexion,
 * d'un changement de dimension ou d'un arrêt serveur.</p>
 */
public final class IndividualPerceptionManager {

    private static final int CLEANUP_INTERVAL_TICKS = 10;
    private static final double ABSENT_EXCLUSION_RADIUS = 112.0;
    private static final Map<MinecraftServer, ServerState> STATES =
            new IdentityHashMap<>();

    private IndividualPerceptionManager() {
    }

    /** Conditions naturelles, peu coûteuses, utilisées par le pool du directeur. */
    public static boolean canStartNatural(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        MinecraftServer server = player.level().getServer();
        ServerState state = state(server);
        prune(state, server);
        PenumbraConfig config = PenumbraConfig.get();
        boolean eligible = PerceptionRules.eligible(
                config.individualPerceptionsEnabled,
                config.falseCompanionsEnabled,
                player.isAlive(), player.isSpectator(), player.isSleeping(),
                player.level().getDifficulty() == Difficulty.PEACEFUL,
                HorrorDirector.isGraceComplete(player),
                // Une perception privée ne réserve jamais la zone des compagnons.
                true,
                state.activeByOwner.containsKey(player.getUUID()),
                state.activeByOwner.size() < Math.max(1, config.perceptionMaxActive),
                HorrorDirector.effectiveContentStage(player),
                HorrorDirector.getState(server, player.getUUID()));
        if (!eligible || LinkedSceneManager.active(player).isPresent()) {
            return false;
        }
        return !AbsentManager.hasAbsentNearby(
                player.level(), player.blockPosition(), ABSENT_EXCLUSION_RADIUS, player);
    }

    /**
     * Démarre un faux compagnon. Le forçage ignore grâce, tension et stade mais
     * conserve les options, le budget serveur et toutes les sécurités physiques.
     */
    public static boolean startFalseCompanion(ServerPlayer player, boolean forced) {
        if (player == null || !player.isAlive() || player.isSpectator()
                || player.level().getDifficulty() == Difficulty.PEACEFUL) {
            return false;
        }
        PenumbraConfig config = PenumbraConfig.get();
        if (!config.individualPerceptionsEnabled || !config.falseCompanionsEnabled) {
            return false;
        }
        MinecraftServer server = player.level().getServer();
        ServerState state = state(server);
        prune(state, server);
        if (state.activeByOwner.containsKey(player.getUUID())
                || state.activeByOwner.size() >= Math.max(1, config.perceptionMaxActive)
                || !forced && !canStartNatural(player)) {
            return false;
        }
        AbsentEntity entity = AbsentManager.forceFalseCompanion(player);
        if (entity == null) {
            return false;
        }
        state.activeByOwner.put(player.getUUID(), new ActivePerception(
                entity, player.level().dimension().identifier().toString()));
        StoryManager.noteDiscovery(player, StoryManager.KEY_FALSE_COMPANION);
        return true;
    }

    /** Instantané destiné aux commandes et diagnostics, sans exposer l'entité. */
    public static Optional<Snapshot> active(ServerPlayer player) {
        if (player == null) {
            return Optional.empty();
        }
        MinecraftServer server = player.level().getServer();
        ServerState state = STATES.get(server);
        if (state == null) {
            return Optional.empty();
        }
        prune(state, server);
        ActivePerception active = state.activeByOwner.get(player.getUUID());
        if (active == null) {
            return Optional.empty();
        }
        AbsentEntity entity = active.entity();
        int seconds = Math.max(0, (entity.getRemainingPerceptionTicks() + 19) / 20);
        return Optional.of(new Snapshot(entity.getImitatedName(), seconds));
    }

    /** Supprime immédiatement la perception du joueur, si elle existe. */
    public static boolean clear(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        ServerState state = STATES.get(player.level().getServer());
        if (state == null) {
            return false;
        }
        ActivePerception active = state.activeByOwner.remove(player.getUUID());
        if (active == null) {
            return false;
        }
        discard(active);
        return true;
    }

    /** Nettoyage borné : aucune recherche d'entité ni allocation par tick joueur. */
    public static void onServerTick(MinecraftServer server) {
        if (Math.floorMod(server.getTickCount(), CLEANUP_INTERVAL_TICKS) != 0) {
            return;
        }
        ServerState state = STATES.get(server);
        if (state != null) {
            prune(state, server);
        }
    }

    public static void onPlayerDisconnect(MinecraftServer server, UUID playerId) {
        ServerState state = STATES.get(server);
        if (state == null) {
            return;
        }
        ActivePerception active = state.activeByOwner.remove(playerId);
        if (active != null) {
            discard(active);
        }
    }

    public static void onServerStopped(MinecraftServer server) {
        ServerState state = STATES.remove(server);
        if (state == null) {
            return;
        }
        state.activeByOwner.values().forEach(IndividualPerceptionManager::discard);
        state.activeByOwner.clear();
    }

    private static ServerState state(MinecraftServer server) {
        return STATES.computeIfAbsent(server, ignored -> new ServerState());
    }

    private static void prune(ServerState state, MinecraftServer server) {
        state.activeByOwner.entrySet().removeIf(entry -> {
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
            ActivePerception active = entry.getValue();
            AbsentEntity entity = active.entity();
            boolean invalid = owner == null || !owner.isAlive() || owner.isSpectator()
                    || !active.dimension().equals(
                    owner.level().dimension().identifier().toString())
                    || entity.isRemoved() || !entity.isAlive()
                    || !entity.isFalseCompanion() || !entity.isOwnedBy(owner)
                    || entity.level() != owner.level();
            if (invalid) {
                discard(active);
            }
            return invalid;
        });
    }

    private static void discard(ActivePerception active) {
        if (active != null && !active.entity().isRemoved()) {
            active.entity().discard();
        }
    }

    public record Snapshot(String copiedName, int remainingSeconds) {
    }

    private record ActivePerception(AbsentEntity entity, String dimension) {
    }

    private static final class ServerState {
        private final Map<UUID, ActivePerception> activeByOwner = new HashMap<>();
    }
}

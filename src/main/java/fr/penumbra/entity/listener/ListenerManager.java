package fr.penumbra.entity.listener;

import fr.penumbra.PenumbraMod;
import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.director.HorrorDirector;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Planifie les apparitions de L'Écouteur et lui transmet les bruits ponctuels.
 *
 * <p>Une décision amortie toutes les cinq secondes, un cooldown individuel de
 * six minutes, une seule scène par joueur et six au total : la créature reste
 * un événement, pas un habitant. Elle ne peut apparaître qu'à partir du stade
 * narratif 2 et jamais avant la fin de la période de grâce.</p>
 */
public final class ListenerManager {

    private static final int CHECK_INTERVAL_TICKS = 100;
    private static final int CHECK_OFFSET_TICKS = 40;
    private static final int BASE_SPAWN_ONE_IN = 46;
    public static final long PLAYER_COOLDOWN_TICKS = 20L * 60L * 6L;
    private static final int MIN_CONTENT_STAGE = 2;
    private static final int MAX_ACTIVE = 6;
    private static final double OWNER_EXCLUSION_RADIUS = 80.0;
    private static final double SPAWN_MIN_DISTANCE = 22.0;
    private static final double SPAWN_MAX_DISTANCE = 34.0;
    /** Rayon de recherche des scènes concernées par un bruit ponctuel. */
    private static final double BURST_SEARCH_RADIUS = ListenerNoise.MAX_RADIUS + 4.0;

    /**
     * Portée d'un bloc cassé, exposée aux autres systèmes (leurres du directeur)
     * pour qu'ils décrivent leur bruit avec exactement le même barème.
     */
    public static final double BLOCK_BREAK_RADIUS = ListenerNoise.BLOCK_BREAK_RADIUS;

    private static final Map<UUID, Long> LAST_SCENE_TICKS = new HashMap<>();
    private static final Map<UUID, UUID> ACTIVE_BY_OWNER = new HashMap<>();

    private ListenerManager() {
    }

    /**
     * Enregistre le hook de bruit. Un bloc cassé porte à 26 blocs, même pour un
     * joueur parfaitement immobile : c'est le principal moyen d'attirer — ou de
     * détourner — la créature.
     */
    public static void register() {
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, entity) -> {
            if (level instanceof ServerLevel serverLevel
                    && player instanceof ServerPlayer serverPlayer) {
                reportBurst(serverLevel, serverPlayer,
                        Vec3.atCenterOf(pos), ListenerNoise.BLOCK_BREAK_RADIUS);
            }
        });
    }

    /** À brancher sur {@code ServerTickEvents.END_SERVER_TICK}. */
    public static void onServerTick(MinecraftServer server) {
        if (Math.floorMod(server.getTickCount() - CHECK_OFFSET_TICKS,
                CHECK_INTERVAL_TICKS) != 0) {
            return;
        }
        pruneInactive(server);
        PenumbraConfig config = PenumbraConfig.get();
        if (!config.listenerEnabled || config.eventFrequency <= 0.0f
                || config.horrorIntensity <= 0.0f) {
            return;
        }
        int denominator = ListenerNoise.spawnDenominator(
                BASE_SPAWN_ONE_IN, config.eventFrequency, config.horrorIntensity);
        long now = server.getTickCount();

        for (ServerPlayer player : HorrorDirector.fairAutomaticPlayers(
                server, CHECK_INTERVAL_TICKS, 3)) {
            if (!eligible(player)
                    || !ListenerNoise.cooldownReady(
                            LAST_SCENE_TICKS.get(player.getUUID()), now,
                            PLAYER_COOLDOWN_TICKS)
                    || player.getRandom().nextInt(denominator) != 0
                    || !HorrorDirector.automaticSceneAvailable(player)) {
                continue;
            }
            if (ListenerEntity.spawnNear(player.level(), player,
                    SPAWN_MIN_DISTANCE, SPAWN_MAX_DISTANCE)) {
                LAST_SCENE_TICKS.put(player.getUUID(), now);
                HorrorDirector.recordAutomaticScene(player);
                HorrorDirector.noteManifestation(player);
                if (config.debugLogging) {
                    PenumbraMod.LOGGER.info("[Penumbra] Écouteur pour {}",
                            player.getGameProfile().name());
                }
            }
        }
    }

    private static boolean eligible(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && !player.isSleeping()
                && player.level().getDifficulty() != Difficulty.PEACEFUL
                && HorrorDirector.isGraceComplete(player)
                && HorrorDirector.physicalManifestationAllowed(player)
                && HorrorDirector.effectiveContentStage(player) >= MIN_CONTENT_STAGE
                && mayCreate(player);
    }

    /**
     * Transmet un bruit ponctuel aux scènes du joueur assez proches. Aucune
     * recherche globale : uniquement les entités déjà à portée d'oreille.
     */
    public static void reportBurst(ServerLevel level, ServerPlayer player,
                                   Vec3 origin, double radius) {
        if (ACTIVE_BY_OWNER.isEmpty() || !PenumbraConfig.get().listenerEnabled) {
            return;
        }
        for (ListenerEntity entity :
                ListenerEntity.ownedNear(level, player, BURST_SEARCH_RADIUS)) {
            entity.hearBurst(origin, radius);
        }
    }

    /** Variante pratique pour un bruit localisé sur un bloc. */
    public static void reportBurst(ServerLevel level, ServerPlayer player,
                                   BlockPos pos, double radius) {
        reportBurst(level, player, Vec3.atCenterOf(pos), radius);
    }

    /** Commande de diagnostic : placement réel, sans chance ni cooldown. */
    public static boolean force(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator()
                || player.level().getDifficulty() == Difficulty.PEACEFUL) {
            return false;
        }
        return ListenerEntity.spawnNear(player.level(), player,
                SPAWN_MIN_DISTANCE, SPAWN_MAX_DISTANCE);
    }

    /** Réservation de budget appelée par la fabrique de l'entité. */
    static boolean mayCreate(ServerPlayer owner) {
        UUID activeId = ACTIVE_BY_OWNER.get(owner.getUUID());
        if (activeId != null) {
            Entity active = find(owner.level().getServer(), activeId);
            if (active instanceof ListenerEntity && active.isAlive()) {
                return false;
            }
            ACTIVE_BY_OWNER.remove(owner.getUUID());
        }
        return ACTIVE_BY_OWNER.size() < MAX_ACTIVE
                && !ListenerEntity.hasOwnedNearby(
                        owner.level(), owner, OWNER_EXCLUSION_RADIUS);
    }

    static void track(UUID ownerId, UUID entityId) {
        if (ownerId != null && entityId != null) {
            ACTIVE_BY_OWNER.put(ownerId, entityId);
        }
    }

    static void release(UUID ownerId, UUID entityId) {
        if (ownerId != null && entityId != null) {
            ACTIVE_BY_OWNER.remove(ownerId, entityId);
        }
    }

    private static void pruneInactive(MinecraftServer server) {
        ACTIVE_BY_OWNER.entrySet().removeIf(entry -> {
            Entity entity = find(server, entry.getValue());
            return !(entity instanceof ListenerEntity) || !entity.isAlive();
        });
    }

    private static Entity find(MinecraftServer server, UUID entityId) {
        if (server == null) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(entityId);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    /** À brancher sur {@code ServerPlayConnectionEvents.DISCONNECT}. */
    public static void onPlayerDisconnect(UUID playerId) {
        // L'entité se dissipe seule dès que son propriétaire quitte la dimension.
    }

    /** À brancher sur {@code ServerLifecycleEvents.SERVER_STOPPED}. */
    public static void onServerStopped(MinecraftServer server) {
        LAST_SCENE_TICKS.clear();
        ACTIVE_BY_OWNER.clear();
    }
}

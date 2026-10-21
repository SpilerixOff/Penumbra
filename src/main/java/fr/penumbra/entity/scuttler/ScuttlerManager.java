package fr.penumbra.entity.scuttler;

import fr.penumbra.PenumbraMod;
import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.director.HorrorDirector;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LightLayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Planifie les deux scènes naturelles et personnelles de la Cavaleuse.
 * Une décision amortie toutes les cinq secondes, un cooldown individuel de
 * deux minutes, une entité par propriétaire et douze au total bornent le coût.
 */
public final class ScuttlerManager {

    private static final int CHECK_INTERVAL_TICKS = 100;
    private static final int CHECK_OFFSET_TICKS = 90;
    private static final int BASE_SPAWN_ONE_IN = 32;
    public static final long PLAYER_COOLDOWN_TICKS = 20L * 60L * 2L;
    private static final int MIN_CONTENT_STAGE = 1;
    /**
     * Lumière de bloc maximale au pied du joueur.
     *
     * <p>Relevé de 6 à 9 en 0.19.0. À 6, une seule torche posée dans un couloir
     * suffisait à interdire la scène dans toute la zone éclairée — or c'est
     * exactement là que les joueurs passent leur temps sous terre. La Cavaleuse
     * ne se montrait donc quasiment jamais en grotte. À 9, un couloir balisé
     * reste éligible ; une pièce franchement éclairée ne l'est toujours pas.</p>
     */
    private static final int MAX_BLOCK_LIGHT = 9;
    private static final int MAX_ACTIVE = 12;
    private static final double OWNER_EXCLUSION_RADIUS = 72.0;

    private static final Map<UUID, Long> LAST_SCENE_TICKS = new HashMap<>();
    private static final Map<UUID, UUID> ACTIVE_BY_OWNER = new HashMap<>();

    private ScuttlerManager() {
    }

    /** À brancher sur {@code ServerTickEvents.END_SERVER_TICK}. */
    public static void onServerTick(MinecraftServer server) {
        if (Math.floorMod(server.getTickCount() - CHECK_OFFSET_TICKS,
                CHECK_INTERVAL_TICKS) != 0) {
            return;
        }
        pruneInactive(server);
        PenumbraConfig config = PenumbraConfig.get();
        if (!config.personalEventsEnabled
                || config.eventFrequency <= 0.0f || config.horrorIntensity <= 0.0f
                || !config.nightEventsEnabled && !config.undergroundEventsEnabled) {
            return;
        }
        int denominator = ScuttlerBehavior.spawnDenominator(
                BASE_SPAWN_ONE_IN, config.eventFrequency, config.horrorIntensity);
        long now = server.getTickCount();

        for (ServerPlayer player : HorrorDirector.fairAutomaticPlayers(
                server, CHECK_INTERVAL_TICKS, 4)) {
            if (!eligibleNaturally(player, config)
                    || !ScuttlerBehavior.cooldownReady(
                            LAST_SCENE_TICKS.get(player.getUUID()), now,
                            PLAYER_COOLDOWN_TICKS)
                    || player.getRandom().nextInt(denominator) != 0) {
                continue;
            }
            int scene = ScuttlerBehavior.chooseScene(player.getRandom().nextInt(100));
            boolean chase = scene == ScuttlerEntity.SCENE_CHASE && config.chasesEnabled;
            if (!HorrorDirector.automaticSceneAvailable(player)) {
                continue;
            }
            boolean spawned = chase
                    ? ScuttlerEntity.spawnChase(player.level(), player)
                    : ScuttlerEntity.spawnCrossing(player.level(), player);
            if (spawned) {
                LAST_SCENE_TICKS.put(player.getUUID(), now);
                HorrorDirector.recordAutomaticScene(player);
                HorrorDirector.noteManifestation(player);
                if (config.debugLogging) {
                    PenumbraMod.LOGGER.info(
                            "[Penumbra] Cavaleuse {} pour {}",
                            chase ? "chase" : "crossing",
                            player.getGameProfile().name());
                }
            }
        }
    }

    private static boolean eligibleNaturally(
            ServerPlayer player, PenumbraConfig config) {
        if (!player.isAlive() || player.isSpectator() || player.isSleeping()
                || player.level().getDifficulty() == Difficulty.PEACEFUL
                || !HorrorDirector.isGraceComplete(player)
                || !HorrorDirector.physicalManifestationAllowed(player)
                || HorrorDirector.effectiveContentStage(player) < MIN_CONTENT_STAGE
                || !darkCaveOrNight(
                        player.level(), player.blockPosition(), config)) {
            return false;
        }
        return mayCreate(player);
    }

    /**
     * Obscurité locale obligatoire, puis soit une vraie zone souterraine, soit
     * une nuit à ciel ouvert. Les maisons éclairées et le plein jour sont exclus.
     */
    private static boolean darkCaveOrNight(
            ServerLevel level, BlockPos pos, PenumbraConfig config) {
        if (!level.getFluidState(pos).isEmpty()
                || level.getBrightness(LightLayer.BLOCK, pos) > MAX_BLOCK_LIGHT) {
            return false;
        }
        boolean seesSky = level.canSeeSky(pos.above());
        boolean cave = config.undergroundEventsEnabled
                && !seesSky && pos.getY() <= level.getSeaLevel() + 20;
        boolean night = config.nightEventsEnabled && seesSky
                && ScuttlerBehavior.isNight(level.getOverworldClockTime());
        return cave || night;
    }

    /** Commande/API de diagnostic : placement réel, mais sans chance ni cooldown. */
    public static boolean forceCrossing(ServerPlayer player) {
        return force(player, false);
    }

    /** Commande/API de diagnostic : poursuite réelle de 3–5 s, toujours sans dégâts. */
    public static boolean forceChase(ServerPlayer player) {
        return force(player, true);
    }

    private static boolean force(ServerPlayer player, boolean chase) {
        if (!player.isAlive() || player.isSpectator()
                || player.level().getDifficulty() == Difficulty.PEACEFUL) {
            return false;
        }
        return chase
                ? ScuttlerEntity.spawnChase(player.level(), player)
                : ScuttlerEntity.spawnCrossing(player.level(), player);
    }

    /** Réservation de budget appelée par les deux fabriques de l'entité. */
    static boolean mayCreate(ServerPlayer owner) {
        UUID activeId = ACTIVE_BY_OWNER.get(owner.getUUID());
        if (activeId != null) {
            Entity active = find(owner.level().getServer(), activeId);
            if (active instanceof ScuttlerEntity && active.isAlive()) {
                return false;
            }
            ACTIVE_BY_OWNER.remove(owner.getUUID());
        }
        return ACTIVE_BY_OWNER.size() < MAX_ACTIVE
                && !ScuttlerEntity.hasOwnedNearby(
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
            return !(entity instanceof ScuttlerEntity) || !entity.isAlive();
        });
    }

    private static Entity find(MinecraftServer server, UUID entityId) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(entityId);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    /** Le cooldown reste en mémoire pendant une reconnexion dans la session. */
    public static void onPlayerDisconnect(UUID playerId) {
        // L'entité se supprime seule dès que le propriétaire quitte la dimension.
    }

    /** À brancher sur {@code ServerLifecycleEvents.SERVER_STOPPED}. */
    public static void onServerStopped(MinecraftServer server) {
        LAST_SCENE_TICKS.clear();
        ACTIVE_BY_OWNER.clear();
    }
}

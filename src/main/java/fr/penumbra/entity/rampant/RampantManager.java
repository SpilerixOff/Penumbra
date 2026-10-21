package fr.penumbra.entity.rampant;

import fr.penumbra.PenumbraMod;
import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.director.HorrorDirector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Apparitions naturelles du Rampant. Le budget est strictement borné :
 * une décision toutes les cinq secondes, un seul Rampant par propriétaire
 * et six minutes minimum entre deux apparitions réussies.
 */
public final class RampantManager {

    private static final int CHECK_INTERVAL_TICKS = 100;
    private static final int CHECK_OFFSET_TICKS = 60;
    private static final int BASE_SPAWN_ONE_IN = 40;
    private static final long PLAYER_COOLDOWN_TICKS = 20L * 60L * 6L;
    private static final int MIN_CONTENT_STAGE = 2;
    private static final int NATURAL_ATTEMPTS = 36;
    private static final int FORCED_ATTEMPTS = 96;
    private static final double SPAWN_MIN_DISTANCE = 10.0;
    private static final double SPAWN_MAX_DISTANCE = 22.0;
    private static final double OWNER_EXCLUSION_RADIUS = 96.0;
    private static final int MAX_BLOCK_LIGHT = 5;
    private static final Direction[] WALL_DIRECTIONS = {
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    private static final Map<UUID, Long> LAST_SPAWN_TICK = new HashMap<>();

    private RampantManager() {
    }

    /** À enregistrer sur {@code ServerTickEvents.END_SERVER_TICK}. */
    public static void onServerTick(MinecraftServer server) {
        if (Math.floorMod(server.getTickCount() - CHECK_OFFSET_TICKS,
                CHECK_INTERVAL_TICKS) != 0) {
            return;
        }
        PenumbraConfig config = PenumbraConfig.get();
        if (!config.undergroundEventsEnabled || !config.chasesEnabled
                || config.eventFrequency <= 0.0f || config.horrorIntensity <= 0.0f) {
            return;
        }

        int denominator = RampantBehavior.spawnDenominator(
                BASE_SPAWN_ONE_IN, config.eventFrequency, config.horrorIntensity);
        for (ServerPlayer player : HorrorDirector.fairAutomaticPlayers(
                server, CHECK_INTERVAL_TICKS, 2)) {
            if (!eligibleForNaturalSpawn(player)) {
                continue;
            }
            long now = player.level().getGameTime();
            long previous = LAST_SPAWN_TICK.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2);
            if (now - previous < PLAYER_COOLDOWN_TICKS
                    || player.getRandom().nextInt(denominator) != 0) {
                continue;
            }
            if (!HorrorDirector.automaticSceneAvailable(player)) {
                continue;
            }
            if (spawnAttached(player, NATURAL_ATTEMPTS) != null) {
                LAST_SPAWN_TICK.put(player.getUUID(), now);
                HorrorDirector.recordAutomaticScene(player);
                HorrorDirector.noteManifestation(player);
            }
        }
    }

    private static boolean eligibleForNaturalSpawn(ServerPlayer player) {
        if (player.isSpectator() || !player.isAlive() || player.isSleeping()
                || !HorrorDirector.isGraceComplete(player)
                || HorrorDirector.effectiveContentStage(player) < MIN_CONTENT_STAGE
                || !HorrorDirector.physicalManifestationAllowed(player)
                || !(player.level() instanceof ServerLevel level)
                || !level.dimension().equals(Level.OVERWORLD)
                || level.getDifficulty() == Difficulty.PEACEFUL
                || !isDarkCave(level, player.blockPosition())) {
            return false;
        }
        return !RampantEntity.hasOwnedRampantNearby(level, player, OWNER_EXCLUSION_RADIUS);
    }

    /**
     * Crochet de commande autonome : force une recherche autour du joueur sans
     * consommer le cooldown naturel. Retourne {@code null} si aucune vraie
     * surface de grotte sombre n'est disponible.
     */
    public static RampantEntity forceSpawn(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)
                || level.getDifficulty() == Difficulty.PEACEFUL
                || RampantEntity.hasOwnedRampantNearby(level, player, OWNER_EXCLUSION_RADIUS)) {
            return null;
        }
        return spawnAttached(player, FORCED_ATTEMPTS);
    }

    private static RampantEntity spawnAttached(ServerPlayer player, int attempts) {
        ServerLevel level = player.level();
        AttachmentSpot spot = findAttachment(level, player, attempts);
        if (spot == null) {
            return null;
        }

        RampantEntity rampant = new RampantEntity(RampantEntities.RAMPANT, level);
        Vec3 position = Vec3.atBottomCenterOf(spot.airPos());
        rampant.snapTo(position.x, position.y, position.z, player.getYRot() + 180.0f, 0.0f);
        rampant.finalizeSpawn(level, level.getCurrentDifficultyAt(spot.airPos()),
                EntitySpawnReason.EVENT, null);
        rampant.beginClinging(player, position, player.blockPosition(), spot.attachment());
        if (!level.addFreshEntity(rampant)) {
            return null;
        }

        if (PenumbraConfig.get().debugLogging) {
            PenumbraMod.LOGGER.info(
                    "[Penumbra] Rampant fixé en grotte pour {} à {} (attache {})",
                    player.getGameProfile().name(), spot.airPos().toShortString(), spot.attachment());
        }
        return rampant;
    }

    private static AttachmentSpot findAttachment(ServerLevel level, ServerPlayer player, int attempts) {
        RandomSource random = player.getRandom();
        Vec3 look = player.getLookAngle();
        Vec3 flatLook = new Vec3(look.x, 0.0, look.z);
        double facing = flatLook.lengthSqr() < 1.0E-5
                ? player.getYRot() * Mth.DEG_TO_RAD
                : Math.atan2(flatLook.z, flatLook.x);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int attempt = 0; attempt < attempts; attempt++) {
            // Majoritairement dans les côtés et le dos : le joueur entend d'abord
            // le décrochage, mais peut encore lever les yeux et le découvrir.
            double angle = facing + Math.PI
                    + (random.nextDouble() * 2.0 - 1.0) * Math.toRadians(115.0);
            double distance = SPAWN_MIN_DISTANCE
                    + random.nextDouble() * (SPAWN_MAX_DISTANCE - SPAWN_MIN_DISTANCE);
            int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
            int centerY = Mth.floor(player.getY()) + random.nextInt(5) - 1;

            for (int dy = 8; dy >= -7; dy--) {
                cursor.set(x, centerY + dy, z);
                AttachmentSpot spot = validateAttachment(level, cursor, random);
                if (spot != null) {
                    return spot;
                }
            }
        }
        return null;
    }

    private static AttachmentSpot validateAttachment(
            ServerLevel level, BlockPos.MutableBlockPos mutable, RandomSource random) {
        BlockPos air = mutable.immutable();
        if (!level.isLoaded(air)
                || !level.getBlockState(air).isAir()
                || !level.getFluidState(air).isEmpty()
                || level.canSeeSky(air)
                || level.getBrightness(LightLayer.BLOCK, air) > MAX_BLOCK_LIGHT
                || !hasLandingBelow(level, air)) {
            return null;
        }

        BlockPos ceiling = air.above();
        if (level.getBlockState(ceiling).isFaceSturdy(level, ceiling, Direction.DOWN)
                && level.getFluidState(ceiling).isEmpty()
                && random.nextFloat() < 0.72f) {
            return new AttachmentSpot(air, RampantEntity.ATTACHMENT_CEILING);
        }

        int start = random.nextInt(WALL_DIRECTIONS.length);
        for (int index = 0; index < WALL_DIRECTIONS.length; index++) {
            Direction towardWall = WALL_DIRECTIONS[(start + index) % WALL_DIRECTIONS.length];
            BlockPos support = air.relative(towardWall);
            if (level.isLoaded(support)
                    && level.getBlockState(support)
                    .isFaceSturdy(level, support, towardWall.getOpposite())
                    && level.getFluidState(support).isEmpty()) {
                return new AttachmentSpot(air, RampantEntity.attachmentFor(towardWall));
            }
        }

        // Si le plafond était valide mais que le tirage privilégiait une paroi
        // inexistante, il reste préférable de l'utiliser plutôt que d'échouer.
        if (level.getBlockState(ceiling).isFaceSturdy(level, ceiling, Direction.DOWN)
                && level.getFluidState(ceiling).isEmpty()) {
            return new AttachmentSpot(air, RampantEntity.ATTACHMENT_CEILING);
        }
        return null;
    }

    /** Vérifie qu'une chute de huit blocs maximum mène à un sol sans liquide. */
    private static boolean hasLandingBelow(ServerLevel level, BlockPos air) {
        for (int down = 1; down <= 8; down++) {
            BlockPos floor = air.below(down);
            if (!level.isLoaded(floor)) {
                return false;
            }
            if (!level.getBlockState(floor).isAir()) {
                return level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)
                        && level.getFluidState(floor).isEmpty();
            }
            if (!level.getFluidState(floor).isEmpty()) {
                return false;
            }
        }
        return false;
    }

    private static boolean isDarkCave(ServerLevel level, BlockPos playerPos) {
        return !level.canSeeSky(playerPos.above())
                && playerPos.getY() <= level.getSeaLevel() + 12
                && level.getBrightness(LightLayer.BLOCK, playerPos) <= MAX_BLOCK_LIGHT + 1
                && level.getFluidState(playerPos).isEmpty();
    }

    /**
     * Le cooldown survit à une reconnexion dans la même session pour empêcher
     * son contournement. L'entité se supprime seule dès que son owner disparaît.
     */
    public static void onPlayerDisconnect(UUID playerId) {
        // no-op intentionnel
    }

    /** À appeler depuis {@code ServerLifecycleEvents.SERVER_STOPPED}. */
    public static void onServerStopped(MinecraftServer server) {
        LAST_SPAWN_TICK.clear();
    }

    private record AttachmentSpot(BlockPos airPos, int attachment) {
    }
}

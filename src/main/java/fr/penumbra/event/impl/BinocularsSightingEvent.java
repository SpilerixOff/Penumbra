package fr.penumbra.event.impl;

import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.data.PenumbraPersistentState;
import fr.penumbra.director.PlayerHorrorProfile;
import fr.penumbra.director.TensionState;
import fr.penumbra.entity.HostBehavior;
import fr.penumbra.entity.HostEntity;
import fr.penumbra.event.HorrorEvent;
import fr.penumbra.network.PenumbraC2S;
import fr.penumbra.registry.PenumbraEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;

/**
 * Apparition aperçue aux jumelles (SPEC2 §6).
 * <p>
 * Candidat uniquement pendant le zoom : {@code PenumbraC2S.isZooming}, tension &gt;=
 * {@code WATCHED}, {@code hostEnabled} et {@code binocularsHostReactionsEnabled}, et aucun
 * Hôte déjà présent à moins de {@value #NO_HOST_RADIUS} blocs. L'exécution fait apparaître
 * un Hôte à 60–90 blocs dans le cône de regard (±{@value #CONE_HALF_ANGLE_DEGREES}°
 * horizontalement), comportement {@code OBSERVING}, orienté vers le joueur ; la portée de
 * tracking effective (clientTrackingRange 6 × 16 = 96 blocs, vérifié dans
 * {@code ChunkMap} 26.1.2) le rend visible malgré la distance. Si aucun emplacement valide
 * n'est trouvé en {@value #ATTEMPTS} essais, l'événement est annulé sans AUCUN effet.
 * Gain de tension : +12 en cas de réussite uniquement.
 * </p>
 */
public final class BinocularsSightingEvent implements HorrorEvent {

    /** Distance minimale d'apparition (blocs). */
    private static final double MIN_DISTANCE = 60.0;
    /** Distance maximale d'apparition (blocs). */
    private static final double MAX_DISTANCE = 90.0;
    /** Demi-angle du cône de regard dans lequel l'Hôte apparaît (degrés). */
    private static final float CONE_HALF_ANGLE_DEGREES = 15.0f;
    /** Rayon d'exclusion : aucun Hôte ne doit déjà exister à moins de cette distance. */
    private static final double NO_HOST_RADIUS = 100.0;
    /** Nombre d'essais de placement avant abandon. */
    private static final int ATTEMPTS = 12;
    /** Amplitude de la recherche verticale de sol (au-dessus / en dessous du joueur). */
    private static final int SCAN_UP = 8;
    private static final int SCAN_DOWN = 32;

    @Override
    public String id() {
        return "binoculars_sighting";
    }

    @Override
    public int weight(TensionState state) {
        // canRun exige tension >= WATCHED : les états inférieurs sont de fait impossibles.
        return switch (state) {
            case CALM -> 0;
            case UNEASE -> 0;
            case WATCHED -> 8;
            case MANIFESTATION -> 10;
            case HUNT -> 10;
            case CRISIS -> 8;
            case FALSE_CALM -> 4;
        };
    }

    @Override
    public int cooldownTicks() {
        return 2400;
    }

    @Override
    public boolean canRun(ServerPlayer player, PlayerHorrorProfile profile) {
        PenumbraConfig config = PenumbraConfig.get();
        if (!config.hostEnabled || !config.binocularsHostReactionsEnabled) {
            return false;
        }
        if (profile.tension() < TensionState.WATCHED.threshold) {
            return false;
        }
        if (!PenumbraC2S.isZooming(player.getUUID())) {
            return false;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        return !HostEntity.hasAnyHostNearby(
                level, player.blockPosition(), NO_HOST_RADIUS, player);
    }

    @Override
    public boolean run(ServerPlayer player) {
        PenumbraConfig config = PenumbraConfig.get();
        if (!config.hostEnabled || !config.binocularsHostReactionsEnabled) {
            return false;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        HostEntity host = spawnInGazeCone(level, player);
        if (host == null) {
            return false; // aucun emplacement valide : événement annulé, aucun effet
        }
        host.setBehavior(HostBehavior.OBSERVING);
        return true;
    }

    /**
     * Tente d'invoquer un Hôte à 60–90 blocs dans le cône de regard horizontal (±15°).
     * La direction du regard en 26.1 est {@code (-sin(yaw), cos(yaw))} ; chaque essai
     * rejoue angle et distance, puis cherche un sol valide à la verticale du point visé.
     *
     * @return l'Hôte invoqué (déjà ajouté au monde), ou {@code null} si échec.
     */
    private static HostEntity spawnInGazeCone(ServerLevel level, ServerPlayer player) {
        RandomSource random = player.getRandom();
        float baseYaw = player.getYRot();
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            float yawDegrees = baseYaw + (random.nextFloat() * 2.0f - 1.0f) * CONE_HALF_ANGLE_DEGREES;
            double yaw = Math.toRadians(yawDegrees);
            double distance = MIN_DISTANCE + random.nextDouble() * (MAX_DISTANCE - MIN_DISTANCE);
            int baseX = Mth.floor(player.getX() - Mth.sin(yaw) * distance);
            int baseZ = Mth.floor(player.getZ() + Mth.cos(yaw) * distance);
            int baseY = Mth.floor(player.getY());

            BlockPos spot = findGroundSpot(level, baseX, baseY, baseZ);
            if (spot == null) {
                continue;
            }

            HostEntity host = new HostEntity(PenumbraEntities.HOST, level);
            host.setOwner(player);
            double x = spot.getX() + 0.5;
            double z = spot.getZ() + 0.5;
            // Yaw orienté vers le joueur : (-sin(yaw), cos(yaw)) doit pointer sur lui.
            float faceYaw = (float) Math.toDegrees(Mth.atan2(-(player.getX() - x), player.getZ() - z));
            host.snapTo(x, spot.getY(), z, faceYaw, 0.0f);
            host.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), EntitySpawnReason.EVENT, null);
            if (level.addFreshEntity(host)) {
                return host;
            }
            return null;
        }
        return null;
    }

    /**
     * Cherche un sol valide à la verticale du point (x, z) : chunk chargé, sol solide,
     * 2 blocs d'air, aucun liquide. Contrairement à {@code HostEntity.spawnNear}, aucune
     * contrainte d'obscurité : l'apparition doit être VISIBLE aux jumelles, de jour comme
     * de nuit. Balayage en ligne borné à {@value #SCAN_UP}/{@value #SCAN_DOWN} blocs —
     * aucun balayage volumique.
     */
    private static BlockPos findGroundSpot(ServerLevel level, int baseX, int baseY, int baseZ) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = SCAN_UP; dy >= -SCAN_DOWN; dy--) {
            cursor.set(baseX, baseY + dy, baseZ);
            if (!level.isLoaded(cursor)) {
                return null; // hors zone chargée : abandon, jamais de chunk forcé
            }
            if (!isValidSpot(level, cursor)) {
                continue;
            }
            return cursor.immutable();
        }
        return null;
    }

    /** Emplacement debout valide : sol solide, pieds et tête à l'air, aucun fluide. */
    private static boolean isValidSpot(ServerLevel level, BlockPos feet) {
        if (!level.getBlockState(feet.below()).isSolidRender()) {
            return false;
        }
        if (!level.getBlockState(feet).isAir() || !level.getBlockState(feet.above()).isAir()) {
            return false;
        }
        return level.getFluidState(feet.below()).isEmpty()
                && level.getFluidState(feet).isEmpty()
                && level.getFluidState(feet.above()).isEmpty();
    }
}

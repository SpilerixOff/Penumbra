package fr.penumbra.event.night;

import fr.penumbra.config.PenumbraConfig;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Utilitaires mutualisés des événements nocturnes (contrat Agent_NightEvents).
 * <p>
 * Classe package-private : seuls les événements de ce paquet et le provider
 * {@link NightEvents} l'utilisent. Toutes les méthodes sont statiques, sans état.
 * Règles communes : {@link #commonNight(ServerPlayer)} impose nuit + surface ou
 * abri de surface + {@code nightEventsEnabled} ; les scans de blocs sont TOUS
 * bornés (coquilles de Chebyshev, sortie anticipée dès que possible) ; aucune
 * méthode n'est appelée par tick depuis {@code canRun} hors lookups O(1).
 * </p>
 */
final class NightSupport {

    /** Hauteur minimale pour considérer qu'un joueur abrité est « en surface » (profil : UNDERGROUND_Y = 60). */
    private static final int SURFACE_SHELTER_MIN_Y = 55;

    private NightSupport() {
    }

    /**
     * Contexte nocturne commun : {@code nightEventsEnabled}, joueur éveillé, dans
     * l'overworld, de nuit ({@code isDarkOutside}, même API que
     * {@code PlayerHorrorProfile.trackRefugeLearning}), et en surface ou dans un
     * abri de surface (ciel visible, ou y &ge; {@value #SURFACE_SHELTER_MIN_Y}).
     * Coût O(1) : aucun scan de blocs.
     */
    static boolean commonNight(ServerPlayer player) {
        if (!PenumbraConfig.get().nightEventsEnabled || player.isSleeping()) {
            return false;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        if (level.dimension() != Level.OVERWORLD || !level.isDarkOutside()) {
            return false;
        }
        return seesSky(level, player.blockPosition()) || player.getY() >= SURFACE_SHELTER_MIN_Y;
    }

    /**
     * Ciel visible au-dessus de {@code pos} : heightmap {@code MOTION_BLOCKING}
     * (lookup O(1), {@code Level.getHeight(Heightmap.Types, int, int)} — vérifié
     * javap 26.1.2 ; {@code canSeeSky} n'existe plus en 26.1.2). Vrai si la
     * position est au niveau ou au-dessus du premier bloc occultant.
     */
    static boolean seesSky(ServerLevel level, BlockPos pos) {
        return pos.getY() >= level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
    }

    /**
     * Bloc « ouverture » d'un abri : porte en bois (tag vanilla) ou vitre —
     * {@code HalfTransparentBlock} (verres plain/teinté, vérifié javap 26.1.2 :
     * {@code AbstractGlassBlock} n'existe plus) ou {@code IronBarsBlock}
     * (couvre les panneaux de verre ; accepte aussi les barreaux de fer, une
     * ouverture grillagée reste une ouverture).
     */
    static boolean isOpening(BlockState state) {
        return state.is(BlockTags.WOODEN_DOORS)
                || state.getBlock() instanceof HalfTransparentBlock
                || state.getBlock() instanceof IronBarsBlock;
    }

    /** Bloc « sensible » d'un refuge : lit (tag vanilla {@code beds}) ou coffre (et variantes, {@code ChestBlock}). */
    static boolean isBedOrChest(BlockState state) {
        return state.is(BlockTags.BEDS) || state.getBlock() instanceof ChestBlock;
    }

    /**
     * Vrai si un lit/coffre se trouve dans le cube de Chebyshev de rayon {@code radius}
     * autour de {@code pos} (scan borné à (2r+1)³, sortie anticipée). Discipline des
     * modifications du monde : jamais à moins de 8 blocs d'un lit/coffre du joueur.
     */
    static boolean nearBedOrChest(ServerLevel level, BlockPos pos, int radius) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    cursor.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    if (isBedOrChest(level.getBlockState(cursor))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Cherche le bloc le plus proche satisfaisant {@code predicate} par coquilles de
     * Chebyshev croissantes (sortie anticipée : la première coquille contenant un
     * candidat donne le plus proche). Volume balayé borné à (2*radius+1)³.
     * Pattern repris de {@code TorchSputterEvent.findNearestTorch}.
     */
    static BlockPos findNearest(ServerLevel level, BlockPos origin, int radius, Predicate<BlockState> predicate) {
        for (int r = 1; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.max(Math.abs(dx), Math.abs(dy)), Math.abs(dz)) != r) {
                            continue; // intérieur déjà balayé : coquille r uniquement
                        }
                        BlockPos pos = origin.offset(dx, dy, dz);
                        if (predicate.test(level.getBlockState(pos))) {
                            return pos;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Cherche un emplacement debout valide à la verticale de (x, z) : chunk chargé,
     * sol solide, 2 blocs d'air, aucun liquide. Balayage en ligne borné
     * ({@code up} au-dessus / {@code down} en dessous) — jamais de scan volumique,
     * jamais de chunk forcé. Pattern repris de {@code BinocularsSightingEvent.findGroundSpot}.
     */
    static BlockPos findStandingSpot(ServerLevel level, int baseX, int baseY, int baseZ, int up, int down) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = up; dy >= -down; dy--) {
            cursor.set(baseX, baseY + dy, baseZ);
            if (!level.isLoaded(cursor)) {
                return null; // hors zone chargée : abandon
            }
            if (isValidStandingSpot(level, cursor)) {
                return cursor.immutable();
            }
        }
        return null;
    }

    /** Emplacement debout valide : sol solide, pieds et tête à l'air, aucun fluide. */
    private static boolean isValidStandingSpot(ServerLevel level, BlockPos feet) {
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

    /**
     * Yaw (degrés) orientant une entité située en (fromX, fromZ) vers le point
     * (toX, toZ) — convention 26.1 : direction du regard (-sin(yaw), cos(yaw)),
     * formule reprise de {@code BinocularsSightingEvent.spawnInGazeCone}.
     */
    static float faceYaw(double fromX, double fromZ, double toX, double toZ) {
        return (float) Math.toDegrees(Math.atan2(-(toX - fromX), toZ - fromZ));
    }

    /** Direction horizontale normalisée de {@code from} vers {@code to} (secours : +Z si dégénérée). */
    static Vec3 horizontalDir(Vec3 from, Vec3 to) {
        Vec3 flat = new Vec3(to.x - from.x, 0.0, to.z - from.z);
        if (flat.lengthSqr() < 1.0E-4) {
            return new Vec3(0.0, 0.0, 1.0);
        }
        return flat.normalize();
    }

    /** Gain de tension individualisé (pattern commun des événements Phase 1/2). */
}

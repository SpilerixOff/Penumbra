package fr.penumbra.event.underground;

import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.director.PlayerHorrorProfile;
import fr.penumbra.event.HorrorEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Base commune des événements souterrains (Phase 3).
 * <p>
 * Fournit le {@link #canRun(ServerPlayer, PlayerHorrorProfile)} contractuel :
 * {@code undergroundEventsEnabled}, joueur éveillé, et présence sous terre —
 * sous Y 55, ou (même détection que {@code PlayerHorrorProfile.tickBehavior})
 * sous Y 60 sans ciel visible et dans le noir (lumière brute ≤ 4).
 * Les événements à conditions supplémentaires surchargent canRun en appelant
 * {@code super.canRun(...)}.
 * </p>
 * <p>
 * Contient aussi les helpers de roche partagés : {@link #isSoftRock(BlockState)}
 * (cibles remplaçables : pierre, deepslate, tuff, andésite, diorite, granite,
 * gravier, terre) et {@link #isHardRock(BlockState)} (roche pleine creusable :
 * jamais gravier/sable/terre, jamais de bloc à entité). Toute position candidate
 * à une entité de bloc (coffre, spawner, four…) est toujours refusée.
 * </p>
 */
public abstract class AbstractUndergroundEvent implements HorrorEvent {

    /** Hauteur stricte sous laquelle le joueur est toujours considéré sous terre. */
    private static final double UNDERGROUND_HARD_Y = 55.0;
    /** Hauteur souple (même seuil que le profil : 60) avec condition d'obscurité. */
    private static final double UNDERGROUND_SOFT_Y = 60.0;
    /** Luminosité maximale pour être « dans le noir » (identique au profil). */
    private static final int DARK_LIGHT_LEVEL = 4;

    @Override
    public boolean canRun(ServerPlayer player, PlayerHorrorProfile profile) {
        return PenumbraConfig.get().undergroundEventsEnabled
                && !player.isSleeping()
                && isUnderground(player);
    }

    /**
     * Détection « sous terre » : sous {@value #UNDERGROUND_HARD_Y} toujours ; entre
     * cette hauteur et {@value #UNDERGROUND_SOFT_Y}, seulement dans le noir — même
     * critère que {@code PlayerHorrorProfile.tickBehavior} (lumière locale brute
     * ≤ {@value #DARK_LIGHT_LEVEL}, ce qui implique une lumière de ciel nulle).
     * 26.1 : {@code canSeeSky} n'existe plus dans LevelReader ; la lumière brute
     * locale intègre déjà la contribution du ciel.
     */
    public static boolean isUnderground(ServerPlayer player) {
        if (player.getY() < UNDERGROUND_HARD_Y) {
            return true;
        }
        if (player.getY() >= UNDERGROUND_SOFT_Y) {
            return false;
        }
        BlockPos pos = player.blockPosition();
        return player.level().getMaxLocalRawBrightness(pos) <= DARK_LIGHT_LEVEL;
    }

    /**
     * Roche « meuble » remplaçable par les événements de modification : pierre,
     * deepslate, tuff, andésite, diorite, granite, gravier, terre. Jamais de bloc
     * à entité (coffre, spawner, four, panneau…), jamais de torche.
     */
    public static boolean isSoftRock(BlockState state) {
        if (state.hasBlockEntity()) {
            return false;
        }
        return state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE) || state.is(Blocks.TUFF)
                || state.is(Blocks.ANDESITE) || state.is(Blocks.DIORITE) || state.is(Blocks.GRANITE)
                || state.is(Blocks.GRAVEL) || state.is(Blocks.DIRT);
    }

    /**
     * Roche « pleine » creusable pour la salle impossible : même famille que
     * {@link #isSoftRock(BlockState)} mais SANS gravier ni terre (instables), et
     * explicitement sans sable, grès, liquides ni entité de bloc (donjon exclu).
     */
    public static boolean isHardRock(BlockState state) {
        if (state.hasBlockEntity() || !state.getFluidState().isEmpty()) {
            return false;
        }
        return state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE) || state.is(Blocks.TUFF)
                || state.is(Blocks.ANDESITE) || state.is(Blocks.DIORITE) || state.is(Blocks.GRANITE);
    }

    /** Vrai si au moins un voisin direct du bloc est de l'air (paroi visible d'une galerie). */
    public static boolean hasAirNeighbor(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (level.getBlockState(pos.relative(direction)).isAir()) {
                return true;
            }
        }
        return false;
    }
}

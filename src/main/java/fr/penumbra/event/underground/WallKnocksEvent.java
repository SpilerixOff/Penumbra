package fr.penumbra.event.underground;

import fr.penumbra.data.PenumbraPersistentState;
import fr.penumbra.director.HorrorDirector;
import fr.penumbra.director.TensionState;
import fr.penumbra.network.PenumbraNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Coups sourds derrière une paroi (Phase 3, {@code wall_knocks}).
 * <p>
 * 3 à 5 coups sourds ({@code STONE_HIT}, pitch 0.5–0.65) joués à travers la roche,
 * 3 à 8 blocs derrière la première paroi rencontrée dans une direction horizontale
 * aléatoire (12 essais en ligne, aucun scan volumique). Cadence volontairement
 * irrégulière : 5 à 15 ticks entre chaque coup, via {@code HorrorDirector.runLater}.
 * Si aucune paroi n'est trouvée, l'événement est annulé sans aucun effet.
 * Aucune modification du monde. Gain de tension : +4.
 * </p>
 */
public final class WallKnocksEvent extends AbstractUndergroundEvent {

    /** Distance minimale de la paroi (blocs). */
    private static final int MIN_WALL_DIST = 3;
    /** Distance maximale de la paroi (blocs). */
    private static final int MAX_WALL_DIST = 8;
    /** Essais de directions horizontales pour trouver une paroi. */
    private static final int ATTEMPTS = 12;

    @Override
    public String id() {
        return "wall_knocks";
    }

    @Override
    public int weight(TensionState state) {
        return switch (state) {
            case CALM -> 4;
            case UNEASE -> 6;
            case WATCHED -> 7;
            case MANIFESTATION -> 4;
            case HUNT -> 2;
            case CRISIS -> 1;
            case FALSE_CALM -> 8;
        };
    }

    @Override
    public int cooldownTicks() {
        return 3600;
    }

    @Override
    public boolean run(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        UndergroundRestorationTracker.checkDeadlines(level); // maintenance amortie
        RandomSource random = player.getRandom();
        BlockPos wall = findWall(level, player, random);
        if (wall == null) {
            return false; // aucune paroi à portée : événement sans effet
        }
        Vec3 knockPos = Vec3.atCenterOf(wall);
        int knocks = 3 + random.nextInt(3); // 3..5
        int delay = 0;
        for (int i = 0; i < knocks; i++) {
            delay += 5 + random.nextInt(11); // 5..15 ticks : cadence irrégulière
            float pitch = 0.5f + random.nextFloat() * 0.15f; // 0.5..0.65, sourd
            HorrorDirector.runLater(level, delay, () -> {
                if (player.isAlive() && player.level() == level) {
                    PenumbraNetworking.sendSound(player, SoundEvents.STONE_HIT,
                            knockPos, 0.55f, pitch);
                }
            });
        }
        return true;
    }

    /**
     * Cherche la première paroi de roche pleine à {@value #MIN_WALL_DIST}–{@value #MAX_WALL_DIST}
     * blocs du joueur : marche en ligne droite (pas de scan de volume), à hauteur des yeux,
     * dans {@value #ATTEMPTS} directions horizontales aléatoires.
     */
    private static BlockPos findWall(ServerLevel level, ServerPlayer player, RandomSource random) {
        BlockPos origin = player.blockPosition().above();
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            double angle = random.nextDouble() * (Math.PI * 2.0);
            int stepX = Mth.floor(Math.cos(angle) * 100.0) >= 0 ? 1 : -1;
            int stepZ = Mth.floor(Math.sin(angle) * 100.0) >= 0 ? 1 : -1;
            // Direction horizontale quantifiée aux 8 points cardinaux.
            int dx = Math.abs(Math.cos(angle)) < 0.38 ? 0 : stepX;
            int dz = Math.abs(Math.sin(angle)) < 0.38 ? 0 : stepZ;
            if (dx == 0 && dz == 0) {
                continue;
            }
            BlockPos.MutableBlockPos cursor = origin.mutable();
            for (int dist = MIN_WALL_DIST; dist <= MAX_WALL_DIST; dist++) {
                cursor.set(origin.getX() + dx * dist, origin.getY(), origin.getZ() + dz * dist);
                if (!level.isLoaded(cursor)) {
                    break;
                }
                BlockState state = level.getBlockState(cursor);
                if (isHardRock(state) || isSoftRock(state)) {
                    return cursor.immutable();
                }
                if (!state.isAir()) {
                    break; // obstacle non rocheux (torche, rail…) : direction abandonnée
                }
            }
        }
        return null;
    }
}

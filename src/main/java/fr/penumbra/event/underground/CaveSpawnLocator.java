package fr.penumbra.event.underground;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Recherche bornée d'un emplacement d'apparition relié à la galerie du joueur.
 * Contrairement aux anciens tirs de colonnes radiales, la marche reste dans les
 * chunks déjà chargés et suit l'air praticable de la grotte : elle ne choisit
 * donc presque jamais un point enfermé dans la roche.
 */
public final class CaveSpawnLocator {

    private static final int MAX_VISITED = 768;
    private static final int MAX_VERTICAL_DELTA = 8;
    private static final int[] STEP_HEIGHTS = {0, 1, -1};

    private CaveSpawnLocator() {
    }

    /**
     * Retourne un sol valide à {@code minDistance..maxDistance} blocs. Les
     * emplacements visibles dans le cône avant sont préférés ; à défaut, un
     * point relié autour d'un virage reste acceptable.
     */
    public static BlockPos find(ServerLevel level, ServerPlayer player,
                                double minDistance, double maxDistance,
                                RandomSource random) {
        BlockPos origin = findPassableOrigin(level, player.blockPosition());
        if (origin == null) {
            return null;
        }

        double minSqr = minDistance * minDistance;
        double maxSqr = maxDistance * maxDistance;
        int originY = origin.getY();
        Vec3 eye = player.getEyePosition();
        Vec3 flatLook = new Vec3(player.getLookAngle().x, 0.0, player.getLookAngle().z);
        if (flatLook.lengthSqr() > 1.0E-4) {
            flatLook = flatLook.normalize();
        }

        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        List<BlockPos> visible = new ArrayList<>();
        List<BlockPos> connected = new ArrayList<>();
        queue.add(origin);
        visited.add(origin.asLong());

        while (!queue.isEmpty() && visited.size() <= MAX_VISITED) {
            BlockPos current = queue.removeFirst();
            double distanceSqr = current.distToCenterSqr(
                    player.getX(), player.getY(), player.getZ());
            if (distanceSqr >= minSqr && distanceSqr <= maxSqr
                    && isHostStandable(level, current)) {
                connected.add(current.immutable());
                Vec3 toSpot = Vec3.atCenterOf(current).subtract(player.position());
                Vec3 flatToSpot = new Vec3(toSpot.x, 0.0, toSpot.z);
                boolean inFront = flatLook.lengthSqr() < 1.0E-4
                        || (flatToSpot.lengthSqr() > 1.0E-4
                        && flatLook.dot(flatToSpot.normalize()) >= -0.15);
                if (inFront && hasLineOfSight(level, player, eye, current)) {
                    visible.add(current.immutable());
                }
            }

            for (Direction direction : Direction.Plane.HORIZONTAL) {
                for (int dy : STEP_HEIGHTS) {
                    BlockPos next = current.relative(direction).above(dy);
                    if (Math.abs(next.getY() - originY) > MAX_VERTICAL_DELTA
                            || next.distSqr(origin) > maxSqr
                            || !level.isLoaded(next)
                            || !isPlayerPassable(level, next)
                            || !visited.add(next.asLong())) {
                        continue;
                    }
                    queue.addLast(next.immutable());
                    break; // une hauteur praticable suffit pour cette direction
                }
            }
        }

        List<BlockPos> pool = visible.isEmpty() ? connected : visible;
        return pool.isEmpty() ? null : pool.get(random.nextInt(pool.size()));
    }

    private static BlockPos findPassableOrigin(ServerLevel level, BlockPos around) {
        for (int dy = 1; dy >= -2; dy--) {
            BlockPos candidate = around.above(dy);
            if (isPlayerPassable(level, candidate)) {
                return candidate.immutable();
            }
        }
        return null;
    }

    /** Deux blocs d'air et un sol solide : graphe praticable par un joueur. */
    private static boolean isPlayerPassable(ServerLevel level, BlockPos feet) {
        return level.getBlockState(feet).isAir()
                && level.getBlockState(feet.above()).isAir()
                && level.getBlockState(feet.below()).isSolidRender()
                && level.getFluidState(feet).isEmpty()
                && level.getFluidState(feet.above()).isEmpty();
    }

    /** L'Hôte mesure plus de deux blocs : trois blocs d'air sont exigés. */
    private static boolean isHostStandable(ServerLevel level, BlockPos feet) {
        return isPlayerPassable(level, feet)
                && level.getBlockState(feet.above(2)).isAir()
                && level.getFluidState(feet.above(2)).isEmpty();
    }

    private static boolean hasLineOfSight(ServerLevel level, ServerPlayer player,
                                          Vec3 eye, BlockPos feet) {
        Vec3 target = Vec3.atCenterOf(feet).add(0.0, 1.1, 0.0);
        BlockHitResult hit = level.clip(new ClipContext(
                eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS;
    }
}

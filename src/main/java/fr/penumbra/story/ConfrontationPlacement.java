package fr.penumbra.story;

import fr.penumbra.entity.absent.AbsentEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Small, grounded mirror steps. A blocked reflection stays visible in place. */
final class ConfrontationPlacement {
    static final double MAX_STEP = 0.6;
    private static final double EPSILON = 1.0E-5;

    private ConfrontationPlacement() { }

    static Vec3 stepToward(Vec3 current, Vec3 target) {
        Vec3 flat = new Vec3(target.x - current.x, 0, target.z - current.z);
        double length = flat.length();
        return length <= MAX_STEP ? current.add(flat)
                : current.add(flat.scale(MAX_STEP / length));
    }

    static boolean keepsSeparation(Vec3 current, Vec3 next, Vec3 player, double minimum) {
        double oldDistance = current.subtract(player).horizontalDistanceSqr();
        double newDistance = next.subtract(player).horizontalDistanceSqr();
        // If the player approaches the copy, a retreat must remain possible.
        return newDistance + EPSILON >= Math.min(minimum * minimum, oldDistance);
    }

    static boolean withinBorder(AABB box, double minX, double minZ, double maxX, double maxZ) {
        return box.minX >= minX && box.minZ >= minZ && box.maxX <= maxX && box.maxZ <= maxZ;
    }

    static boolean safeInitialPlacement(ServerLevel level, AbsentEntity copy) {
        return isSafe(level, copy, copy.getBoundingBox(), copy.getBoundingBox());
    }

    static boolean tryMove(ServerLevel level, AbsentEntity copy, Vec3 desired,
                           Vec3 player, double minimum) {
        Vec3 next = stepToward(copy.position(), desired);
        if (!keepsSeparation(copy.position(), next, player, minimum)) return false;
        Vec3 offset = next.subtract(copy.position());
        AABB target = copy.getBoundingBox().move(offset);
        AABB swept = copy.getBoundingBox().minmax(target);
        if (!isSafe(level, copy, target, swept)) return false;
        copy.snapTo(next.x, next.y, next.z,
                ConfrontationManager.yawTowards(next, player), 0.0f);
        return true;
    }

    private static boolean isSafe(ServerLevel level, AbsentEntity copy,
                                  AABB target, AABB swept) {
        // The whole swept hitbox (and collision-query margin) must already be loaded.
        int minX = Mth.floor(swept.minX) - 1;
        int maxX = Mth.floor(swept.maxX) + 1;
        int minZ = Mth.floor(swept.minZ) - 1;
        int maxZ = Mth.floor(swept.maxZ) + 1;
        if (!level.hasChunksAt(minX, minZ, maxX, maxZ)
                || !level.isInWorldBounds(BlockPos.containing(swept.minX, swept.minY, swept.minZ))
                || !level.isInWorldBounds(BlockPos.containing(swept.maxX, swept.maxY, swept.maxZ))
                || !withinBorder(swept, level.getWorldBorder().getMinX(),
                        level.getWorldBorder().getMinZ(), level.getWorldBorder().getMaxX(),
                        level.getWorldBorder().getMaxZ())
                || !level.noCollision(copy, swept)) return false;

        int footY = Mth.floor(target.minY - EPSILON);
        for (int x = Mth.floor(target.minX + EPSILON); x <= Mth.floor(target.maxX - EPSILON); x++) {
            for (int z = Mth.floor(target.minZ + EPSILON); z <= Mth.floor(target.maxZ - EPSILON); z++) {
                BlockPos floor = new BlockPos(x, footY, z);
                BlockState ground = level.getBlockState(floor);
                if (!ground.isFaceSturdy(level, floor, Direction.UP)
                        || !level.getFluidState(floor).isEmpty() || hazardous(ground)) return false;
            }
        }
        // Fluids and non-solid hazards do not necessarily appear in noCollision.
        for (BlockPos pos : BlockPos.betweenClosed(
                Mth.floor(swept.minX + EPSILON), Mth.floor(swept.minY + EPSILON),
                Mth.floor(swept.minZ + EPSILON), Mth.floor(swept.maxX - EPSILON),
                Mth.floor(swept.maxY - EPSILON), Mth.floor(swept.maxZ - EPSILON))) {
            if (!level.getFluidState(pos).isEmpty() || hazardous(level.getBlockState(pos))) return false;
        }
        return true;
    }

    private static boolean hazardous(BlockState state) {
        return state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS)
                || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)
                || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.WITHER_ROSE);
    }
}

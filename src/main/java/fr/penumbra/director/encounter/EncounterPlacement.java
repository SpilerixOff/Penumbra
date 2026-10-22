package fr.penumbra.director.encounter;

import fr.penumbra.entity.HostEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/** Bounded queries against already-loaded terrain; never edits a block or loads a chunk. */
final class EncounterPlacement {
    private static final double EPSILON = 1.0E-5;
    private static final double STEP = 0.22;

    private EncounterPlacement() { }

    static Optional<Vec3> find(ServerLevel level, ServerPlayer player, HostEntity host,
                               @Nullable BlockPos anchor, boolean architectural,
                               boolean behind, @Nullable Vec3 previous) {
        for (BlockPos feet : candidates(player.position(), player.getLookAngle(), anchor)) {
            Vec3 point = Vec3.atBottomCenterOf(feet);
            double distance = point.distanceToSqr(player.position());
            double dot = facingDot(player.position(), player.getLookAngle(), point);
            if (distance < 3.5 * 3.5 || distance > 14 * 14
                    || behind && dot > -0.15 || !behind && dot < -0.15
                    || previous != null && point.distanceToSqr(previous) < 16) continue;
            if (!safeAppearance(level, player, host, point, architectural)) continue;
            return Optional.of(point);
        }
        return Optional.empty();
    }

    /** Candidate ordering is contextual, with a fixed upper bound independent of world size. */
    static List<BlockPos> candidates(Vec3 player, Vec3 look, @Nullable BlockPos anchor) {
        Vec3 center = anchor != null && Vec3.atCenterOf(anchor).distanceToSqr(player) <= 144
                ? Vec3.atBottomCenterOf(anchor) : player;
        double facing = Math.atan2(look.z, look.x);
        var positions = new LinkedHashSet<BlockPos>();
        for (int radius : new int[]{5, 8, 11, 3}) {
            for (int i = 0; i < 16; i++) {
                // Alternating sides finds a silhouette beside the sightline before the direct center.
                int sector = i % 2 == 0 ? i / 2 : -(i + 1) / 2;
                double angle = facing + sector * Math.PI / 8.0;
                for (int dy : new int[]{0, 1, -1, 2, -2}) {
                    positions.add(BlockPos.containing(center.x + Math.cos(angle) * radius,
                            player.y + dy, center.z + Math.sin(angle) * radius));
                }
            }
        }
        return new ArrayList<>(positions);
    }

    static double facingDot(Vec3 origin, Vec3 look, Vec3 target) {
        Vec3 direction = target.subtract(origin);
        if (direction.lengthSqr() < EPSILON || look.lengthSqr() < EPSILON) return -1;
        return direction.normalize().dot(look.normalize());
    }

    static Vec3 stepAway(Vec3 actor, Vec3 player) {
        Vec3 away = new Vec3(actor.x - player.x, 0, actor.z - player.z);
        return away.lengthSqr() < EPSILON ? actor : actor.add(away.normalize().scale(STEP));
    }

    static boolean retreat(ServerLevel level, HostEntity host, ServerPlayer player) {
        Vec3 next = stepAway(host.position(), player.position());
        AABB target = host.getBoundingBox().move(next.subtract(host.position()));
        AABB swept = host.getBoundingBox().minmax(target);
        if (next.equals(host.position()) || !safe(level, host, target, swept, true)
                || anotherPlayerNear(level, player, target)) return false;
        host.setPos(next);
        return true;
    }

    static boolean safeCurrent(ServerLevel level, HostEntity host, boolean grounded) {
        return safe(level, host, host.getBoundingBox(), host.getBoundingBox(), grounded);
    }

    static boolean safeAppearance(ServerLevel level, ServerPlayer player, HostEntity host,
                                   Vec3 point, boolean architectural) {
        double distance = point.distanceToSqr(player.position());
        if (distance < 3.5 * 3.5 || distance > 14 * 14) return false;
        AABB box = host.getBoundingBox().move(point.subtract(host.position()));
        return safe(level, host, box, box, true) && visible(level, player, point)
                && (!architectural || nearOpeningOrCorner(level, BlockPos.containing(point)))
                && !anotherPlayerNear(level, player, box);
    }

    static boolean visible(ServerLevel level, ServerPlayer player, Vec3 feet) {
        Vec3 eye = player.getEyePosition();
        // A head can be behind a window lintel while the torso remains clearly visible.
        return clearRay(level, player, eye, feet.add(0, 1.3, 0))
                || clearRay(level, player, eye, feet.add(0, 2.3, 0));
    }

    private static boolean clearRay(ServerLevel level, ServerPlayer player, Vec3 from, Vec3 to) {
        if (!level.hasChunksAt(Mth.floor(Math.min(from.x, to.x)) - 1,
                Mth.floor(Math.min(from.z, to.z)) - 1, Mth.floor(Math.max(from.x, to.x)) + 1,
                Mth.floor(Math.max(from.z, to.z)) + 1)) return false;
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
    }

    static boolean anotherPlayerNear(ServerLevel level, ServerPlayer owner, AABB box) {
        return !level.getEntitiesOfClass(ServerPlayer.class, box.inflate(2.0),
                other -> other != owner && other.isAlive() && !other.isSpectator()).isEmpty();
    }

    private static boolean nearOpeningOrCorner(ServerLevel level, BlockPos feet) {
        // Open standing space next to an actual wall: a frame, doorway side or exterior corner.
        // This rejects a plain field for refuge scenes without assuming the house's dimensions.
        boolean north = wall(level, feet.north());
        boolean south = wall(level, feet.south());
        boolean east = wall(level, feet.east());
        boolean west = wall(level, feet.west());
        return framedOrCorner(north, south, east, west);
    }

    static boolean framedOrCorner(boolean north, boolean south, boolean east, boolean west) {
        return east && west && !(north && south) || north && south && !(east && west)
                || (north || south) && (east || west) && !(north && south && east && west);
    }

    private static boolean wall(ServerLevel level, BlockPos pos) {
        if (!level.hasChunksAt(pos.getX() - 1, pos.getZ() - 1, pos.getX() + 1, pos.getZ() + 1)) return false;
        return !level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty();
    }

    static boolean withinBorder(AABB box, double minX, double minZ, double maxX, double maxZ) {
        return box.minX >= minX && box.minZ >= minZ && box.maxX <= maxX && box.maxZ <= maxZ;
    }

    private static boolean safe(ServerLevel level, HostEntity host, AABB target, AABB swept, boolean grounded) {
        if (!level.hasChunksAt(Mth.floor(swept.minX) - 1, Mth.floor(swept.minZ) - 1,
                Mth.floor(swept.maxX) + 1, Mth.floor(swept.maxZ) + 1)
                || !level.isInWorldBounds(BlockPos.containing(swept.minX, swept.minY, swept.minZ))
                || !level.isInWorldBounds(BlockPos.containing(swept.maxX, swept.maxY, swept.maxZ))
                || !withinBorder(swept, level.getWorldBorder().getMinX(), level.getWorldBorder().getMinZ(),
                        level.getWorldBorder().getMaxX(), level.getWorldBorder().getMaxZ())
                || !level.noCollision(host, swept)) return false;
        if (grounded) {
            int y = Mth.floor(target.minY - EPSILON);
            for (int x = Mth.floor(target.minX + EPSILON); x <= Mth.floor(target.maxX - EPSILON); x++) {
                for (int z = Mth.floor(target.minZ + EPSILON); z <= Mth.floor(target.maxZ - EPSILON); z++) {
                    BlockPos floor = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(floor);
                    if (!state.isFaceSturdy(level, floor, Direction.UP)
                            || !level.getFluidState(floor).isEmpty() || hazardous(state)) return false;
                }
            }
        }
        for (BlockPos pos : BlockPos.betweenClosed(Mth.floor(swept.minX + EPSILON),
                Mth.floor(swept.minY + EPSILON), Mth.floor(swept.minZ + EPSILON),
                Mth.floor(swept.maxX - EPSILON), Mth.floor(swept.maxY - EPSILON),
                Mth.floor(swept.maxZ - EPSILON))) {
            if (!level.getFluidState(pos).isEmpty() || hazardous(level.getBlockState(pos))) return false;
        }
        return true;
    }

    private static boolean hazardous(BlockState state) {
        return state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS) || state.is(Blocks.FIRE)
                || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)
                || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.POINTED_DRIPSTONE);
    }
}

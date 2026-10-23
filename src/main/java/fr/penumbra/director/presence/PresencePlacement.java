package fr.penumbra.director.presence;

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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/** Small flat routes around real corners. Every query is confined to loaded terrain. */
final class PresencePlacement {
    static final int MAX_ROUTE_VISITS = 72;
    static final int MAX_ROUTE_CHECKS = 220;
    static final int MAX_ROUTE_ATTEMPTS = 4;
    static final double STEP = 0.11;
    private static final double EPS = 1.0E-5;
    record Plan(List<Vec3> points) {
        Plan { points = List.copyOf(points); }
        Vec3 origin() { return points.getFirst(); }
    }

    private PresencePlacement() { }

    static Optional<Plan> find(ServerLevel level, ServerPlayer owner, HostEntity actor) {
        int routes = 0;
        for (BlockPos feet : candidates(owner.position(), owner.getLookAngle())) {
            Vec3 point = Vec3.atBottomCenterOf(feet);
            if (!safeAt(level, actor, point) || !occluded(level, owner, point)) continue;
            Optional<Plan> route = route(level, owner, actor, feet);
            if (route.isPresent()) return route;
            if (++routes >= MAX_ROUTE_ATTEMPTS) break;
        }
        return Optional.empty();
    }

    static List<BlockPos> candidates(Vec3 owner, Vec3 look) {
        double heading = Math.atan2(look.z, look.x);
        var result = new LinkedHashSet<BlockPos>();
        for (int radius : new int[]{8, 11, 6}) {
            for (int sector = 0; sector < 16; sector++) {
                double angle = heading + Math.PI * sector / 8.0;
                for (int dy : new int[]{0, -1, 1}) {
                    result.add(BlockPos.containing(owner.x + Math.cos(angle) * radius,
                            owner.y + dy, owner.z + Math.sin(angle) * radius));
                }
            }
        }
        return List.copyOf(result);
    }

    private static Optional<Plan> route(ServerLevel level, ServerPlayer owner,
                                         HostEntity actor, BlockPos start) {
        var queue = new ArrayDeque<BlockPos>();
        var seen = new HashSet<BlockPos>();
        var parents = new HashMap<BlockPos, BlockPos>();
        queue.add(start); seen.add(start);
        int visits = 0, checks = 0;
        while (!queue.isEmpty() && visits++ < MAX_ROUTE_VISITS && checks < MAX_ROUTE_CHECKS) {
            BlockPos current = queue.removeFirst();
            Vec3 point = Vec3.atBottomCenterOf(current);
            // A useful route emerges at least two blocks from its hidden starting point.
            if (current.distSqr(start) >= 4 && visible(level, owner, point)) {
                var points = new ArrayList<Vec3>();
                BlockPos cursor = current;
                while (cursor != null) {
                    points.add(Vec3.atBottomCenterOf(cursor));
                    cursor = parents.get(cursor);
                }
                Collections.reverse(points);
                return Optional.of(new Plan(points));
            }
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos next = current.relative(direction);
                if (next.distSqr(start) > 25 || !seen.add(next)) continue;
                checks++;
                Vec3 target = Vec3.atBottomCenterOf(next);
                double distance = target.distanceToSqr(owner.position());
                if (distance < 5 * 5 || distance > 14 * 14
                        || !safeSegment(level, actor, point, target)) continue;
                parents.put(next, current);
                queue.addLast(next);
            }
        }
        return Optional.empty();
    }

    static Vec3 stepToward(Vec3 from, Vec3 to, double maximum) {
        Vec3 delta = to.subtract(from);
        double distance = delta.length();
        return distance <= maximum ? to : from.add(delta.scale(maximum / distance));
    }

    /** A close player may follow the retreat; only movement farther away is safe inside the buffer. */
    static boolean separationAllowsStep(Vec3 current, Vec3 next, Vec3 owner, boolean retreating) {
        double nextDistance = next.distanceToSqr(owner);
        return nextDistance >= 3.5 * 3.5
                || retreating && nextDistance > current.distanceToSqr(owner) + EPS;
    }

    static boolean observed(ServerLevel level, ServerPlayer owner, Vec3 feet) {
        return inView(owner, feet) && visible(level, owner, feet);
    }

    static boolean inView(ServerPlayer owner, Vec3 feet) {
        Vec3 toward = feet.add(0, 1.6, 0).subtract(owner.getEyePosition());
        return toward.lengthSqr() > EPS && owner.getLookAngle().normalize().dot(toward.normalize()) >= 0.65;
    }

    static boolean occluded(ServerLevel level, ServerPlayer owner, Vec3 feet) {
        // Missing chunks must never be mistaken for an occluding wall.
        if (!rayLoaded(level, owner.getEyePosition(), feet.add(0, 2.5, 0))) return false;
        Vec3 toward = feet.subtract(owner.getEyePosition()).multiply(1, 0, 1).normalize();
        Vec3 shoulder = new Vec3(-toward.z, 0, toward.x).scale(0.32);
        return opaqueRayBlocked(level, owner, feet.add(0, 0.4, 0))
                && opaqueRayBlocked(level, owner, feet.add(0, 1.3, 0))
                && opaqueRayBlocked(level, owner, feet.add(0, 2.5, 0))
                && opaqueRayBlocked(level, owner, feet.add(0, 1.7, 0).add(shoulder))
                && opaqueRayBlocked(level, owner, feet.add(0, 1.7, 0).subtract(shoulder));
    }

    static boolean visible(ServerLevel level, ServerPlayer owner, Vec3 feet) {
        return rayLoaded(level, owner.getEyePosition(), feet.add(0, 2.5, 0)) && !occluded(level, owner, feet);
    }

    private static boolean opaqueRayBlocked(ServerLevel level, ServerPlayer owner, Vec3 target) {
        Vec3 eye = owner.getEyePosition();
        if (!rayLoaded(level, eye, target)) return false;
        var hit = level.clip(new ClipContext(eye, target,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        // Glass, leaves, fences and an unloaded ray are never proof that the body is concealed.
        // A transparent first hit deliberately rejects even a solid wall behind it (safe false negative).
        return hit.getType() == HitResult.Type.BLOCK && level.getBlockState(hit.getBlockPos()).isSolidRender();
    }

    private static boolean rayLoaded(ServerLevel level, Vec3 from, Vec3 to) {
        return level.hasChunksAt(Mth.floor(Math.min(from.x, to.x)) - 1,
                Mth.floor(Math.min(from.z, to.z)) - 1, Mth.floor(Math.max(from.x, to.x)) + 1,
                Mth.floor(Math.max(from.z, to.z)) + 1);
    }

    static boolean anotherPlayerNear(ServerLevel level, ServerPlayer owner, HostEntity actor) {
        AABB area = owner.getBoundingBox().inflate(5).minmax(actor.getBoundingBox().inflate(3));
        return !level.getEntitiesOfClass(ServerPlayer.class, area,
                other -> other != owner && other.isAlive() && !other.isSpectator()).isEmpty();
    }

    static boolean safeAt(ServerLevel level, HostEntity actor, Vec3 position) {
        AABB box = actor.getBoundingBox().move(position.subtract(actor.position()));
        return safe(level, actor, box, box);
    }

    static boolean safeSegment(ServerLevel level, HostEntity actor, Vec3 from, Vec3 to) {
        AABB origin = actor.getBoundingBox().move(from.subtract(actor.position()));
        AABB target = actor.getBoundingBox().move(to.subtract(actor.position()));
        return safe(level, actor, target, origin.minmax(target));
    }

    private static boolean safe(ServerLevel level, HostEntity actor, AABB target, AABB swept) {
        if (!level.hasChunksAt(Mth.floor(swept.minX) - 1, Mth.floor(swept.minZ) - 1,
                Mth.floor(swept.maxX) + 1, Mth.floor(swept.maxZ) + 1)
                || !level.isInWorldBounds(BlockPos.containing(swept.minX, swept.minY, swept.minZ))
                || !level.isInWorldBounds(BlockPos.containing(swept.maxX, swept.maxY, swept.maxZ))
                || !withinBorder(swept, level.getWorldBorder().getMinX(), level.getWorldBorder().getMinZ(),
                        level.getWorldBorder().getMaxX(), level.getWorldBorder().getMaxZ())
                || !level.noCollision(actor, swept)) return false;
        // Every swept foot cell needs a solid dry floor: no crossing gaps on the way to a safe end.
        int floorY = Mth.floor(target.minY - EPS);
        for (int x = Mth.floor(swept.minX + EPS); x <= Mth.floor(swept.maxX - EPS); x++) {
            for (int z = Mth.floor(swept.minZ + EPS); z <= Mth.floor(swept.maxZ - EPS); z++) {
                BlockPos floor = new BlockPos(x, floorY, z);
                BlockState state = level.getBlockState(floor);
                if (!state.isFaceSturdy(level, floor, Direction.UP)
                        || !level.getFluidState(floor).isEmpty() || hazardous(state)) return false;
            }
        }
        for (BlockPos pos : BlockPos.betweenClosed(Mth.floor(swept.minX + EPS),
                Mth.floor(swept.minY + EPS), Mth.floor(swept.minZ + EPS),
                Mth.floor(swept.maxX - EPS), Mth.floor(swept.maxY - EPS), Mth.floor(swept.maxZ - EPS))) {
            if (!level.getFluidState(pos).isEmpty() || hazardous(level.getBlockState(pos))) return false;
        }
        return true;
    }

    static boolean withinBorder(AABB box, double minX, double minZ, double maxX, double maxZ) {
        return box.minX >= minX && box.minZ >= minZ && box.maxX <= maxX && box.maxZ <= maxZ;
    }

    private static boolean hazardous(BlockState state) {
        return state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS) || state.is(Blocks.FIRE)
                || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)
                || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.POINTED_DRIPSTONE);
    }
}

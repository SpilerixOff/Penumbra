package fr.penumbra.entity;

import net.minecraft.world.phys.Vec3;

/** Shared timing and bounded, deterministic movement for the Host's physical grab. */
public final class HostGrapple {
    public static final int GRAB_TICKS = 6;
    public static final int LIFT_TICKS = 12;
    public static final int HOLD_TICKS = 4;
    public static final int THROW_TICKS = 6;
    public static final int THROW_START_TICK = GRAB_TICKS + LIFT_TICKS + HOLD_TICKS;
    public static final int TOTAL_TICKS = THROW_START_TICK + THROW_TICKS;
    public static final double GRIP_DISTANCE = 0.90;
    public static final double LIFT_HEIGHT = 1.05;
    public static final double LEAP_HEIGHT = 0.85;
    public static final double THROW_HORIZONTAL_SPEED = 1.20;
    public static final double THROW_VERTICAL_SPEED = 0.35;
    public static final double MAX_CONTACT_DISTANCE = 1.10;
    public static final double MAX_HOLD_DRIFT = 1.75;

    private HostGrapple() { }

    /** A real world-space arc: leaves and returns to the committed approach line. */
    public static double leapHeight(double progress) {
        double t = clamp(progress);
        return 4.0 * LEAP_HEIGHT * t * (1.0 - t);
    }

    public static float progress(int elapsedTicks, float partialTick) {
        return (float) clamp((Math.max(0, elapsedTicks) + clamp(partialTick)) / TOTAL_TICKS);
    }

    /** The player remains at full lift height while the hands finish their push. */
    public static double liftHeight(double elapsedTicks) {
        return LIFT_HEIGHT * smooth((elapsedTicks - GRAB_TICKS) / LIFT_TICKS);
    }

    public static Vec3 direction(Vec3 host, Vec3 player, Vec3 fallback) {
        Vec3 delta = player.subtract(host).multiply(1.0, 0.0, 1.0);
        if (!finite(delta) || delta.lengthSqr() < 1.0E-6) {
            delta = fallback.multiply(1.0, 0.0, 1.0);
        }
        if (!finite(delta) || delta.lengthSqr() < 1.0E-6) delta = new Vec3(0.0, 0.0, 1.0);
        return delta.normalize();
    }

    /** Fixed horizontal grip; only the first six ticks pull the contacted body into the hands. */
    public static Vec3 heldPosition(Vec3 start, Vec3 host, Vec3 direction, int elapsedTicks) {
        Vec3 horizontal = direction(Vec3.ZERO, direction, new Vec3(0.0, 0.0, 1.0));
        Vec3 grip = new Vec3(host.x + horizontal.x * GRIP_DISTANCE, start.y,
                host.z + horizontal.z * GRIP_DISTANCE);
        Vec3 base = start.lerp(grip, smooth(elapsedTicks / (double) GRAB_TICKS));
        return base.add(0.0, liftHeight(elapsedTicks), 0.0);
    }

    public static Vec3 throwVelocity(Vec3 direction) {
        Vec3 horizontal = direction(Vec3.ZERO, direction, new Vec3(0.0, 0.0, 1.0));
        return horizontal.scale(THROW_HORIZONTAL_SPEED).add(0.0, THROW_VERTICAL_SPEED, 0.0);
    }

    /** Close physical contact is required; a lateral or vertical dodge wins. */
    public static boolean canGrab(Vec3 host, Vec3 player, boolean clearSight) {
        return clearSight && finite(host) && finite(player)
                && host.distanceToSqr(player) <= MAX_CONTACT_DISTANCE * MAX_CONTACT_DISTANCE
                && Math.abs(host.y - player.y) <= 0.60;
    }

    /** External teleports and large displacement release instead of pulling the player back. */
    public static boolean canContinueHolding(Vec3 current, Vec3 previousAnchor) {
        return finite(current) && finite(previousAnchor)
                && current.distanceToSqr(previousAnchor) <= MAX_HOLD_DRIFT * MAX_HOLD_DRIFT;
    }

    private static boolean finite(Vec3 vector) {
        return vector != null && Double.isFinite(vector.x)
                && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    private static double smooth(double progress) {
        double t = clamp(progress);
        return t * t * (3.0 - 2.0 * t);
    }

    private static double clamp(double value) {
        return Double.isFinite(value) ? Math.max(0.0, Math.min(1.0, value)) : 0.0;
    }
}

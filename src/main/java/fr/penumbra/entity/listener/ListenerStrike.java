package fr.penumbra.entity.listener;

/** Shared server/render timing for the Listener's single committed arm sweep. */
public final class ListenerStrike {
    public static final int WARNING_TICKS = 12;
    public static final int IMPACT_TICK = 3;
    public static final int FOLLOW_THROUGH_TICKS = 6;
    public static final int TOTAL_TICKS = 20;

    private ListenerStrike() { }

    /** Zero means inactive; a positive value contains elapsed ticks plus one. */
    public static float elapsed(int encoded, float partialTick) {
        if (encoded <= 0) return 0.0f;
        float partial = Float.isFinite(partialTick)
                ? Math.max(0.0f, Math.min(1.0f, partialTick)) : 0.0f;
        return Math.min(TOTAL_TICKS, Math.max(0, encoded - 1) + partial);
    }

    public static boolean impactDue(int elapsed) { return elapsed == IMPACT_TICK; }
    public static boolean complete(int elapsed) { return elapsed >= TOTAL_TICKS; }

    /** A sidestep behind the committed shoulder cannot be hit by a front sweep. */
    public static boolean withinSweep(double dx, double dz, float yawDegrees) {
        if (!Double.isFinite(dx) || !Double.isFinite(dz) || !Float.isFinite(yawDegrees)) return false;
        double distance = Math.hypot(dx, dz);
        if (distance < 0.05) return true;
        double yaw = Math.toRadians(yawDegrees);
        return (-Math.sin(yaw) * dx + Math.cos(yaw) * dz) / distance >= 0.0;
    }
}

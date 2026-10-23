package fr.penumbra.entity.scuttler;

/** Timing of one harmless ankle feint; all displacement still uses vanilla collision. */
public final class ScuttlerFeint {
    public static final int BRAKE_TICKS = 4;
    public static final int RAISE_END_TICK = 9;
    public static final int RECOIL_START_TICK = 12;
    public static final int TOTAL_TICKS = 20;

    private ScuttlerFeint() { }

    public static boolean canStart(boolean used, int sceneTicks, int ticksRemaining,
                                   double distanceSquared, boolean onGround, boolean visible) {
        return !used && sceneTicks >= 12 && ticksRemaining >= TOTAL_TICKS
                && onGround && visible && Double.isFinite(distanceSquared)
                && distanceSquared >= 0.0 && distanceSquared <= 2.1 * 2.1;
    }

    public static float elapsed(int encoded, float partialTick) {
        if (encoded <= 0) return 0.0f;
        float partial = Float.isFinite(partialTick)
                ? Math.max(0.0f, Math.min(1.0f, partialTick)) : 0.0f;
        return Math.min(TOTAL_TICKS, Math.max(0, encoded - 1) + partial);
    }

    public static boolean recoiling(int elapsed) {
        return elapsed >= RECOIL_START_TICK && elapsed < TOTAL_TICKS;
    }

    public static double recoilSpeed(int elapsed) {
        if (!recoiling(elapsed)) return 0.0;
        double progress = (elapsed - RECOIL_START_TICK) / (double) (TOTAL_TICKS - RECOIL_START_TICK);
        return 0.32 - 0.14 * progress;
    }
}

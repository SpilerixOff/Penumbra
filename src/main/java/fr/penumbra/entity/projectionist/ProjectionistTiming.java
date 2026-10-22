package fr.penumbra.entity.projectionist;

/** Server timeline of the harmless photographic manifestation. */
public final class ProjectionistTiming {
    public static final int ARM_TICKS = 12;
    public static final int ADVANCE_TICKS = 18;
    public static final int RECOVERY_TICKS = 16;
    public static final int RECOVERY_START = ARM_TICKS + ADVANCE_TICKS;
    public static final int TOTAL_TICKS = RECOVERY_START + RECOVERY_TICKS;

    private ProjectionistTiming() { }

    public static boolean isAdvancing(int elapsed) {
        return elapsed > ARM_TICKS && elapsed <= RECOVERY_START;
    }

    public static double advanceStep(int elapsed) {
        if (!isAdvancing(elapsed)) return 0.0;
        double progress = (elapsed - ARM_TICKS) / (double) ADVANCE_TICKS;
        return 0.10 + 0.12 * Math.sin(progress * Math.PI);
    }
}

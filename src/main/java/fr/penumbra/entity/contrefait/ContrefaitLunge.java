package fr.penumbra.entity.contrefait;

/** Calculs purs et état compact de la charge 3D du Contrefait. */
final class ContrefaitLunge {

    static final int MIN_DURATION_TICKS = 8;
    static final int MAX_DURATION_TICKS = 12;

    private static final int TICK_BITS = 8;
    private static final int TICK_MASK = (1 << TICK_BITS) - 1;

    private ContrefaitLunge() {
    }

    static int pack(int elapsedTicks, int durationTicks) {
        int duration = Math.max(MIN_DURATION_TICKS, Math.min(MAX_DURATION_TICKS, durationTicks));
        int elapsed = Math.max(0, Math.min(duration, elapsedTicks));
        return (duration << TICK_BITS) | elapsed;
    }

    static int elapsedTicks(int state) {
        return state & TICK_MASK;
    }

    static int durationTicks(int state) {
        return state >>> TICK_BITS;
    }

    static float progress(int state, float partialTick) {
        if (state == 0) {
            return 0.0f;
        }
        int duration = durationTicks(state);
        if (duration <= 0) {
            return 0.0f;
        }
        float partial = Math.max(0.0f, Math.min(1.0f, partialTick));
        return Math.max(0.0f, Math.min(1.0f,
                (elapsedTicks(state) + partial) / duration));
    }

    static int stareThresholdTicks(int configuredSeconds) {
        return Math.max(20, Math.max(0, configuredSeconds) * 20);
    }

    static int nextStareTicks(int currentTicks, boolean preciseStare, int intervalTicks) {
        if (!preciseStare) {
            return 0;
        }
        long next = (long) Math.max(0, currentTicks) + Math.max(1, intervalTicks);
        return (int) Math.min(Integer.MAX_VALUE, next);
    }

    /** Smoothstep : accélération visible puis arrivée stable au visage. */
    static double easedProgress(int elapsedTicks, int durationTicks) {
        double progress = Math.max(0.0, Math.min(1.0,
                elapsedTicks / (double) Math.max(1, durationTicks)));
        return progress * progress * (3.0 - 2.0 * progress);
    }
}

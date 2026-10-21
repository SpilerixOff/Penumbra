package fr.penumbra.entity;

/** Encodage synchronisé et courbe pure du bond déclenché par le regard. */
public final class StareLunge {

    public static final int MIN_DURATION_TICKS = 8;
    public static final int MAX_DURATION_TICKS = 12;
    private static final int ELAPSED_MASK = 0xFFFF;

    private StareLunge() {
    }

    public static int pack(int elapsedTicks, int durationTicks) {
        int duration = Math.max(1, Math.min(0x7FFF, durationTicks));
        int elapsed = Math.max(0, Math.min(duration, elapsedTicks));
        return duration << 16 | elapsed;
    }

    public static int elapsedTicks(int state) {
        return state & ELAPSED_MASK;
    }

    public static int durationTicks(int state) {
        return Math.max(1, state >>> 16);
    }

    public static float progress(int state, float partialTick) {
        if (state == 0) {
            return 0.0f;
        }
        return Math.min(1.0f,
                (elapsedTicks(state) + Math.max(0.0f, partialTick)) / durationTicks(state));
    }

    /** Départ retenu, accélération très brutale dans le dernier tiers. */
    public static double easedProgress(int elapsedTicks, int durationTicks) {
        double t = Math.max(0.0, Math.min(1.0, elapsedTicks / (double) Math.max(1, durationTicks)));
        return t * t * t;
    }
}

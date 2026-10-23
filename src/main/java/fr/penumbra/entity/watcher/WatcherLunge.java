package fr.penumbra.entity.watcher;

/**
 * Encodage compact et calculs purs de la lunge de l'Observateur.
 * Le tick courant et la duree tiennent dans un seul entier synchronise afin
 * que le client puisse interpoler la pose sans dupliquer un compteur local.
 */
final class WatcherLunge {

    static final int MIN_DURATION_TICKS = 6;
    static final int MAX_DURATION_TICKS = 9;

    private static final int TICK_BITS = 8;
    private static final int TICK_MASK = (1 << TICK_BITS) - 1;

    private WatcherLunge() {
    }

    static int pack(int elapsedTicks, int durationTicks) {
        int duration = Math.max(MIN_DURATION_TICKS,
                Math.min(MAX_DURATION_TICKS, durationTicks));
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

    /** Smoothstep : depart contenu, acceleration brutale, arrivee sans tremblement. */
    static double easedProgress(int elapsedTicks, int durationTicks) {
        double progress = Math.max(0.0, Math.min(1.0,
                elapsedTicks / (double) Math.max(1, durationTicks)));
        return progress * progress * (3.0 - 2.0 * progress);
    }
}

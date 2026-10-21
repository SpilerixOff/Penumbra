package fr.penumbra.entity;

/**
 * Calculs purs de la charge terminale de l'Hote.
 *
 * <p>Le tick ecoule et la duree sont encodes dans un entier synchronise afin
 * que le serveur reste l'unique autorite sur la trajectoire, tandis que le
 * client peut interpoler proprement la pose 3D.</p>
 */
final class HostLunge {

    static final int MIN_DURATION_TICKS = 8;
    static final int MAX_DURATION_TICKS = 12;

    private static final int TICK_BITS = 8;
    private static final int TICK_MASK = (1 << TICK_BITS) - 1;

    private HostLunge() {
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

    /** Courbe d'approche : depart lisible, acceleration brutale, fin stable. */
    static double easedProgress(int elapsedTicks, int durationTicks) {
        double progress = Math.max(0.0, Math.min(1.0,
                elapsedTicks / (double) Math.max(1, durationTicks)));
        return progress * progress * (3.0 - 2.0 * progress);
    }

    /** Les poursuites et les etats terminaux ne doivent jamais armer un screamer de regard. */
    static boolean canArmFrom(HostBehavior behavior) {
        return behavior != HostBehavior.CHASING
                && behavior != HostBehavior.ATTACKING
                && behavior != HostBehavior.RETREATING
                && behavior != HostBehavior.COOLDOWN;
    }
}

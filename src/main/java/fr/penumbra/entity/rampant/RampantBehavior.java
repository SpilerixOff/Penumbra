package fr.penumbra.entity.rampant;

/**
 * Calculs purs du Rampant, séparés de l'entité afin de garder le rythme naturel
 * et les transitions testables sans démarrer Minecraft.
 */
final class RampantBehavior {

    static final int CLING_MIN_TICKS = 35;
    static final int CLING_MAX_TICKS = 75;
    static final int LEAP_TIMEOUT_TICKS = 18;

    private RampantBehavior() {
    }

    /**
     * Fréquence et intensité accélèrent ensemble les apparitions, sans jamais
     * produire un dénominateur nul. Les valeurs à zéro sont filtrées par le
     * gestionnaire avant cet appel.
     */
    static int spawnDenominator(int baseDenominator, float frequency, float intensity) {
        double safeFrequency = Math.max(0.05, frequency);
        double safeIntensity = Math.max(0.0, intensity);
        double scale = safeFrequency * (0.5 + 0.5 * safeIntensity);
        return Math.max(1, (int) Math.round(Math.max(1, baseDenominator) / scale));
    }

    /** Le Rampant se décroche au timeout, ou plus tôt si sa proie passe dessous. */
    static boolean shouldRelease(int clingingTicks, double targetDistanceSqr) {
        if (clingingTicks >= CLING_MAX_TICKS) {
            return true;
        }
        return clingingTicks >= CLING_MIN_TICKS && targetDistanceSqr <= 14.0 * 14.0;
    }

    /** Une attaque aérienne ne doit jamais rester bloquée dans son état. */
    static boolean leapExpired(int leapTicks) {
        return leapTicks >= LEAP_TIMEOUT_TICKS;
    }
}

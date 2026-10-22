package fr.penumbra.entity.rampant;

/**
 * Calculs purs du Rampant, séparés de l'entité afin de garder le rythme naturel
 * et les transitions testables sans démarrer Minecraft.
 */
final class RampantBehavior {

    static final int CLING_MIN_TICKS = 40;
    static final int CLING_MAX_TICKS = 400;
    static final int AMBUSH_WARNING_TICKS = 12;
    static final int LEAP_TIMEOUT_TICKS = 18;
    static final int LOST_SIGHT_TICKS = 60;
    static final int MAX_PURSUIT_TICKS = 160;

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

    /** A ceiling ambush needs a real passage underneath, never proximity through a wall. */
    static boolean inAmbushZone(boolean ceiling, double dx, double dy, double dz,
                               boolean clearSight) {
        if (!clearSight || !Double.isFinite(dx) || !Double.isFinite(dy)
                || !Double.isFinite(dz)) return false;
        double radius = ceiling ? 2.4 : 3.0;
        return dx * dx + dz * dz <= radius * radius
                && (ceiling ? dy <= -1.2 && dy >= -7.5 : Math.abs(dy) <= 3.0);
    }

    enum AmbushDecision { WAIT, WARN, RELEASE, EXPIRE }

    /** Moving out of the warned drop zone cancels the attack; waiting never forces it. */
    static final class Ambush {
        private int warningTicks;

        void reset() { warningTicks = 0; }

        AmbushDecision tick(int age, boolean inZone) {
            if (age >= CLING_MAX_TICKS) return AmbushDecision.EXPIRE;
            if (age < CLING_MIN_TICKS || !inZone) {
                warningTicks = 0;
                return AmbushDecision.WAIT;
            }
            warningTicks++;
            return warningTicks >= AMBUSH_WARNING_TICKS ? AmbushDecision.RELEASE
                    : warningTicks == 1 ? AmbushDecision.WARN : AmbushDecision.WAIT;
        }

        boolean warning() { return warningTicks > 0; }
    }

    /** Pursuit destinations are observations, not the concealed owner's current position. */
    static final class Pursuit {
        record Position(double x, double y, double z) { }
        private Position lastSeen;
        private int age;
        private int unseen;

        void reset() { lastSeen = null; age = 0; unseen = 0; }

        void observe(boolean visible, double x, double y, double z) {
            age = Math.min(MAX_PURSUIT_TICKS, age + 1);
            if (visible && Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)) {
                lastSeen = new Position(x, y, z);
                unseen = 0;
            } else {
                unseen = Math.min(LOST_SIGHT_TICKS, unseen + 1);
            }
        }

        Position lastSeen() { return lastSeen; }
        boolean exhausted() { return age >= MAX_PURSUIT_TICKS || unseen >= LOST_SIGHT_TICKS; }
    }

    /** Une attaque aérienne ne doit jamais rester bloquée dans son état. */
    static boolean leapExpired(int leapTicks) {
        return leapTicks >= LEAP_TIMEOUT_TICKS;
    }
}

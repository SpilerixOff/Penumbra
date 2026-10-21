package fr.penumbra.entity.rampant;

/** Calculs purs de locomotion sur paroi et plafond. */
final class RampantSurfaceMotion {

    static final double WALL_SPEED = 0.115;
    static final double CEILING_SPEED = 0.105;
    static final int MAX_CLIMB_TICKS = 110;
    static final double REPERCH_DISTANCE = 4.5;

    private RampantSurfaceMotion() {
    }

    /**
     * Le retour en hauteur après une attaque est prioritaire. Hors attaque, une
     * différence verticale ou une chasse déjà longue déclenchent l'escalade.
     */
    static boolean shouldSeekWall(
            int huntingTicks, double targetVerticalDelta, boolean reperchRequested) {
        if (reperchRequested) {
            return huntingTicks >= 8;
        }
        return (huntingTicks >= 20 && targetVerticalDelta > 1.75)
                || huntingTicks >= 90;
    }

    /** Hystérésis verticale : évite d'osciller lorsque la cible saute. */
    static int chooseWallDirection(double targetVerticalDelta, int currentDirection,
                                   boolean reperchRequested) {
        if (reperchRequested || targetVerticalDelta > 1.0) {
            return 1;
        }
        if (targetVerticalDelta < -2.0) {
            return -1;
        }
        return currentDirection < 0 ? -1 : 1;
    }

    static Step wallStep(int direction) {
        return new Step(0.0, direction < 0 ? -WALL_SPEED : WALL_SPEED, 0.0);
    }

    /** Direction horizontale normalisée, donc jamais plus rapide en diagonale. */
    static Step ceilingStep(double targetX, double targetZ) {
        double length = Math.sqrt(targetX * targetX + targetZ * targetZ);
        if (length < 1.0E-7) {
            return new Step(0.0, 0.0, 0.0);
        }
        return new Step(targetX / length * CEILING_SPEED, 0.0,
                targetZ / length * CEILING_SPEED);
    }

    static boolean shouldPerch(int climbingTicks, double surfaceDistance,
                               double targetVerticalDelta, boolean reperchRequested) {
        if (climbingTicks >= MAX_CLIMB_TICKS) {
            return true;
        }
        if (reperchRequested && surfaceDistance >= REPERCH_DISTANCE) {
            return true;
        }
        return surfaceDistance >= 3.0 && Math.abs(targetVerticalDelta) <= 1.0;
    }

    record Step(double x, double y, double z) {
        double length() {
            return Math.sqrt(x * x + y * y + z * z);
        }
    }
}

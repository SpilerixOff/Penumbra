package fr.penumbra.entity.absent;

import net.minecraft.util.Mth;

/**
 * Fonctions pures de la machine d'etats et de la signature de marche de
 * L'Absent. Les valeurs compactes sont envoyees au client : aucune articulation
 * n'est synchronisee chaque tick.
 */
public final class AbsentMimicLogic {

    public static final int IMITATE = 0;
    public static final int LURE = 1;
    public static final int SUSPICION = 2;
    public static final int REVEAL = 3;
    public static final int HUNT = 4;

    public static final int GAIT_SPRINTING = 1 << 16;
    public static final int GAIT_CROUCHING = 1 << 17;
    public static final int GAIT_AIRBORNE = 1 << 18;

    private static final int BYTE_MASK = 0xFF;

    private AbsentMimicLogic() {
    }

    /**
     * Empaquette la vitesse horizontale et l'amplitude des rotations dans deux
     * octets, puis trois indices de posture. Les bornes correspondent aux
     * mouvements utiles d'un joueur, pas aux vitesses extremes de commandes.
     */
    public static int packGait(double horizontalSpeed, float yawDelta,
                               boolean sprinting, boolean crouching,
                               boolean airborne) {
        int speed = Mth.clamp((int) Math.round(horizontalSpeed / 0.42 * 255.0), 0, 255);
        int turn = Mth.clamp((int) Math.round(Math.abs(yawDelta) / 45.0f * 255.0f), 0, 255);
        int packed = speed | (turn << 8);
        if (sprinting) {
            packed |= GAIT_SPRINTING;
        }
        if (crouching) {
            packed |= GAIT_CROUCHING;
        }
        if (airborne) {
            packed |= GAIT_AIRBORNE;
        }
        return packed;
    }

    public static float unpackSpeed(int packed) {
        return (packed & BYTE_MASK) / 255.0f;
    }

    public static float unpackTurn(int packed) {
        return ((packed >>> 8) & BYTE_MASK) / 255.0f;
    }

    public static boolean isSprinting(int packed) {
        return (packed & GAIT_SPRINTING) != 0;
    }

    public static boolean isCrouching(int packed) {
        return (packed & GAIT_CROUCHING) != 0;
    }

    public static boolean isAirborne(int packed) {
        return (packed & GAIT_AIRBORNE) != 0;
    }

    public static int nextStareTicks(int current, boolean watched) {
        return watched ? Math.min(80, current + 1) : Math.max(0, current - 3);
    }

    public static boolean shouldEnterSuspicion(int phaseTicks, double distance,
                                                int stareTicks, int followTicks,
                                                boolean duplicateExposed) {
        return duplicateExposed
                || stareTicks >= 34
                || (phaseTicks >= 42 && distance <= 8.5)
                || followTicks >= 28;
    }

    /** Niveau minimal d'erreur lisible par les animations et la camera. */
    public static float baseError(int phase) {
        return switch (phase) {
            case LURE -> 0.18f;
            case SUSPICION -> 0.52f;
            case REVEAL, HUNT -> 1.0f;
            default -> 0.025f;
        };
    }

    /**
     * Petits arrets deterministes : deux clients voient la meme hesitation sans
     * trafic reseau additionnel.
     */
    public static boolean movementHitch(long seed, int lifetimeTicks, float error) {
        if (error < 0.16f) {
            return false;
        }
        long mixed = seed ^ (long) (lifetimeTicks / 9) * 0x9E3779B97F4A7C15L;
        mixed ^= mixed >>> 30;
        mixed *= 0xBF58476D1CE4E5B9L;
        mixed ^= mixed >>> 27;
        int threshold = 2 + Mth.clamp((int) (error * 7.0f), 0, 7);
        return (mixed & 63L) < threshold && lifetimeTicks % 9 < 3;
    }
}

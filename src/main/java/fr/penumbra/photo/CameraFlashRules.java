package fr.penumbra.photo;

/**
 * Règles pures du contre-jeu lumineux de la caméra. Le flash physique est
 * volontairement plus large que le cadre de la photo, mais sa portée utile
 * reste courte : une créature derrière le photographe ou cachée par un mur ne
 * peut pas être révélée par un simple paquet client.
 */
public final class CameraFlashRules {

    /** Distance maximale à laquelle la lumière peut briser une imitation. */
    public static final double REVEAL_DISTANCE = 24.0;
    /** Cône lumineux large de 70 degrés de demi-angle. */
    public static final double MIN_REVEAL_ALIGNMENT = Math.cos(Math.toRadians(70.0));

    private CameraFlashRules() {
    }

    public static boolean isExposed(double distance, double alignment, boolean visible) {
        return visible
                && Double.isFinite(distance)
                && Double.isFinite(alignment)
                && distance > 0.0
                && distance <= REVEAL_DISTANCE
                && alignment >= MIN_REVEAL_ALIGNMENT;
    }
}

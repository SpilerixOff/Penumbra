package fr.penumbra.photo;

/** Mathématiques pures du cadrage et du développement, testables sans monde Minecraft. */
public final class PhotoEvidenceRules {

    public static final double MAX_DISTANCE = 64.0;
    /** Cône central généreux de 36° de demi-angle. */
    public static final double MIN_ALIGNMENT = Math.cos(Math.toRadians(36.0));
    public static final float MIN_ZOOM_FACTOR = 1.0f;
    public static final float MAX_ZOOM_FACTOR = 6.05f;

    private PhotoEvidenceRules() {
    }

    public static boolean isFramed(double distance, double alignment, boolean visible) {
        return isFramed(distance, alignment, visible, 1.0f);
    }

    public static boolean isFramed(double distance, double alignment, boolean visible,
                                   float zoomFactor) {
        return visible
                && Double.isFinite(distance)
                && Double.isFinite(alignment)
                && distance > 0.0
                && distance <= MAX_DISTANCE
                && alignment >= minimumAlignment(zoomFactor);
    }

    /** Réduit le cône de preuve comme le FOV réellement capturé par la caméra. */
    public static double minimumAlignment(float zoomFactor) {
        float safeZoom = sanitizeZoomFactor(zoomFactor);
        double halfAngle = Math.atan(Math.tan(Math.toRadians(36.0)) / safeZoom);
        return Math.cos(halfAngle);
    }

    public static float sanitizeZoomFactor(float zoomFactor) {
        return Float.isFinite(zoomFactor)
                ? Math.max(MIN_ZOOM_FACTOR, Math.min(MAX_ZOOM_FACTOR, zoomFactor))
                : MIN_ZOOM_FACTOR;
    }

    /** La priorité départage les catégories, puis le centrage et enfin la distance. */
    public static double score(PhotoEvidence evidence, double distance, double alignment) {
        if (evidence == null || !evidence.discoverable()
                || !Double.isFinite(distance) || !Double.isFinite(alignment)) {
            return Double.NEGATIVE_INFINITY;
        }
        return evidence.priority() * 1_000.0 + alignment * 100.0 - distance;
    }

    public static boolean isDeveloped(long now, long capturedAt, int developmentTicks) {
        return developmentTicks >= 0 && now >= capturedAt
                && now - capturedAt >= developmentTicks;
    }
}

package fr.penumbra.photo;

/** Short frontal flash counterplay; remote photographs are still valid evidence. */
public final class PhysicalFlashRules {
    public static final double DISTANCE = 12.0;
    public static final double ALIGNMENT = Math.cos(Math.toRadians(50));
    private PhysicalFlashRules() { }
    public static boolean canRepel(double distance, double alignment, boolean visible) {
        return visible && Double.isFinite(distance) && Double.isFinite(alignment)
                && distance > 0 && distance <= DISTANCE && alignment >= ALIGNMENT;
    }
}

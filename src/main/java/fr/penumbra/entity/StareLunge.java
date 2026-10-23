package fr.penumbra.entity;

import net.minecraft.world.phys.Vec3;

/** Encodage synchronisé et courbe pure du bond déclenché par le regard. */
public final class StareLunge {

    public static final int MIN_DURATION_TICKS = 6;
    public static final int MAX_DURATION_TICKS = 9;
    public static final int RECOVERY_TICKS = 36;
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

    /** A dodge or a wall always wins over an already-running presentation. */
    public static boolean canContact(double distanceSquared, boolean clearSight) {
        return clearSight && Double.isFinite(distanceSquared)
                && distanceSquared >= 0.0 && distanceSquared <= 1.9 * 1.9;
    }

    /** Deterministic horizontal recoil; callers validate the swept body and supporting floor. */
    public static Vec3 retreatStep(Vec3 origin, Vec3 threat, Vec3 fallback, int elapsed) {
        Vec3 away = origin.subtract(threat).multiply(1.0, 0.0, 1.0);
        if (away.lengthSqr() < 1.0E-6) away = fallback.multiply(-1.0, 0.0, -1.0);
        if (away.lengthSqr() < 1.0E-6) away = new Vec3(1.0, 0.0, 0.0);
        double speed = elapsed < 10 ? 0.22 : 0.10;
        return away.normalize().scale(speed);
    }
}

package fr.penumbra.client.animation;

import fr.penumbra.entity.projectionist.ProjectionistTiming;

/** A camera winds, opens its coat, then folds back. Never repeats a strike by age. */
public final class ProjectionistAnimation {
    private ProjectionistAnimation() { }

    public static Pose pose(float ticks) {
        float t = Float.isFinite(ticks) ? Math.max(0.0f, ticks) : 0.0f;
        Pose rest = new Pose(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        Pose armed = new Pose(-0.13f, 0.07f, 0.32f, 1.15f,
                -0.48f, -0.10f, 0.10f, -0.04f, 0.02f, 0.12f);
        Pose open = new Pose(0.34f, -0.10f, -0.12f, 2.0f,
                -1.38f, -0.46f, 0.92f, -0.48f, 0.62f, 1.0f);
        Pose withdrawn = new Pose(-0.18f, 0.05f, 0.20f, 0.25f,
                0.20f, -0.22f, 0.06f, -0.08f, -0.16f, 0.10f);
        if (t <= ProjectionistTiming.ARM_TICKS) {
            return blend(rest, armed, ease(t / ProjectionistTiming.ARM_TICKS));
        }
        if (t <= ProjectionistTiming.RECOVERY_START) {
            float p = (t - ProjectionistTiming.ARM_TICKS) / ProjectionistTiming.ADVANCE_TICKS;
            return blend(armed, open, ease(p / 0.62f));
        }
        float p = (t - ProjectionistTiming.RECOVERY_START) / ProjectionistTiming.RECOVERY_TICKS;
        return blend(open, withdrawn, ease(p));
    }

    /** Integral of reel speed: no positional jump when the advance begins. */
    public static float reelRotation(float ticks) {
        float t = Float.isFinite(ticks) ? Math.max(0.0f, Math.min(ProjectionistTiming.TOTAL_TICKS, ticks)) : 0.0f;
        float arm = Math.min(t, ProjectionistTiming.ARM_TICKS);
        float advance = Math.max(0, Math.min(ProjectionistTiming.ADVANCE_TICKS, t - ProjectionistTiming.ARM_TICKS));
        float recovery = Math.max(0, t - ProjectionistTiming.RECOVERY_START);
        return 0.015f * arm * arm + 0.36f * advance
                + 0.36f * recovery - 0.01125f * recovery * recovery;
    }

    private static float ease(float p) { return MonsterAnimationMath.smoothstep(p); }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static Pose blend(Pose a, Pose b, float t) {
        return new Pose(lerp(a.torsoPitch, b.torsoPitch, t), lerp(a.torsoRoll, b.torsoRoll, t),
                lerp(a.headPitch, b.headPitch, t), lerp(a.lensExtension, b.lensExtension, t),
                lerp(a.rightArmPitch, b.rightArmPitch, t), lerp(a.leftArmPitch, b.leftArmPitch, t),
                lerp(a.rightArmRoll, b.rightArmRoll, t), lerp(a.leftArmRoll, b.leftArmRoll, t),
                lerp(a.stride, b.stride, t), lerp(a.coatOpen, b.coatOpen, t));
    }
    public record Pose(float torsoPitch, float torsoRoll, float headPitch, float lensExtension,
                       float rightArmPitch, float leftArmPitch, float rightArmRoll,
                       float leftArmRoll, float stride, float coatOpen) { }
}

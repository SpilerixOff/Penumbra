package fr.penumbra.client.animation;

/** An almost rigid body tips forward; the head counter-rotates to hold its gaze. */
public final class OriginelAnimation {
    private OriginelAnimation() { }

    public static Pose windup(float progress) {
        float p = ease(progress);
        return new Pose(-0.16f * p, 0.055f * p, 0.16f * p, -0.08f * p,
                -0.06f * p, 0.06f * p);
    }

    public static Pose lunge(float progress) {
        float p = MonsterAnimationMath.clamp01(progress);
        Pose committed = new Pose(0.66f, -0.035f, -0.42f, 0.16f, 0.48f, -0.48f);
        Pose landed = new Pose(0.46f, -0.025f, -0.64f, 0.10f, 0.10f, -0.10f);
        if (p < 0.45f) return blend(windup(1.0f), committed, ease(p / 0.45f));
        return blend(committed, landed, ease((p - 0.45f) / 0.55f));
    }

    public static Pose recovery(float progress, float interruptedLungeProgress) {
        return blend(lunge(interruptedLungeProgress),
                new Pose(-0.10f, 0.035f, 0.14f, -0.12f, -0.08f, 0.08f), ease(progress));
    }

    private static float ease(float p) { return MonsterAnimationMath.smoothstep(p); }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static Pose blend(Pose a, Pose b, float t) {
        if (t <= 0) return a;
        if (t >= 1) return b;
        return new Pose(lerp(a.torsoPitch, b.torsoPitch, t), lerp(a.torsoRoll, b.torsoRoll, t),
                lerp(a.rightArmPitch, b.rightArmPitch, t), lerp(a.leftArmPitch, b.leftArmPitch, t),
                lerp(a.rightLegPitch, b.rightLegPitch, t), lerp(a.leftLegPitch, b.leftLegPitch, t));
    }
    public record Pose(float torsoPitch, float torsoRoll, float rightArmPitch,
                       float leftArmPitch, float rightLegPitch, float leftLegPitch) { }
}

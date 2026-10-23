package fr.penumbra.client.animation;

/** A folded statue unwinds into one oblique forearm strike; no grab or lift. */
public final class WatcherAttackAnimation {
    private WatcherAttackAnimation() { }

    public record Pose(float bend, float lean, float twist, float headTilt,
                       float rightArm, float rightElbow, float leftArm, float leftElbow) { }

    public static Pose windup(float progress) {
        float p = MonsterAnimationMath.smootherstep(progress);
        return new Pose(.52f * p, -.16f * p, -.42f * p, .58f * p,
                -.38f * p, -1.30f * p, .18f * p, -.12f * p);
    }

    public static Pose strike(float progress) {
        float p = MonsterAnimationMath.smootherstep(progress / .78f);
        return mix(windup(1), new Pose(.24f, .57f, .56f, -.23f,
                -1.40f, -.25f, .36f, -.10f), p);
    }

    public static Pose recovery(float progress, boolean contacted) {
        return recovery(progress, contacted ? 3.0f : .7f);
    }

    /** 0..1 is interrupted preparation, 2..3 is interrupted strike. */
    public static Pose recovery(float progress, float sourcePose) {
        float p = MonsterAnimationMath.clamp01(progress);
        float settle = MonsterAnimationMath.smootherstep(p / .46f);
        Pose start = sourcePose >= 2 ? strike(sourcePose - 2) : windup(sourcePose);
        Pose folded = new Pose(.18f, -.20f, -.12f, .18f, -.32f, -.88f, .06f, -.24f);
        return mix(mix(start, folded, settle), windup(0),
                MonsterAnimationMath.smootherstep((p - .58f) / .42f));
    }

    /** Equal two-bone legs keep their soles on the ground while the hips crouch. */
    public static float hipY(float bend) {
        return -16.0f * (float) Math.cos(bend);
    }

    private static Pose mix(Pose a, Pose b, float p) {
        if (p <= 0) return a;
        if (p >= 1) return b;
        return new Pose(lerp(a.bend, b.bend, p), lerp(a.lean, b.lean, p),
                lerp(a.twist, b.twist, p), lerp(a.headTilt, b.headTilt, p),
                lerp(a.rightArm, b.rightArm, p), lerp(a.rightElbow, b.rightElbow, p),
                lerp(a.leftArm, b.leftArm, p), lerp(a.leftElbow, b.leftElbow, p));
    }
    private static float lerp(float a, float b, float p) { return a + (b - a) * p; }
}

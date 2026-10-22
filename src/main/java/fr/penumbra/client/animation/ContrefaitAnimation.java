package fr.penumbra.client.animation;

/** An off-balance puppet: the shoulder leads, the elbow catches up, then recoil. */
public final class ContrefaitAnimation {
    private ContrefaitAnimation() { }

    public record Limb(float pitch, float yaw, float roll, float joint) { }
    public record Pose(float torsoPitch, float torsoYaw, float torsoRoll,
                       float headPitch, float headYaw, float headRoll,
                       Limb rightArm, Limb leftArm, Limb rightLeg, Limb leftLeg) {
        /** Both six-pixel leg segments keep the lower foot on the ground. */
        public float hipsY() { return -Math.max(reach(rightLeg), reach(leftLeg)); }
        private static float reach(Limb leg) {
            return 6.0f * (float) (Math.cos(leg.pitch) + Math.cos(leg.pitch + leg.joint));
        }
    }

    public static Pose windup(float progress) {
        float p = ease(progress);
        return new Pose(.12f * p, -.46f * p, .16f * p,
                -.10f * p, .30f * p, .10f + .17f * p,
                new Limb(.35f * p, -.30f * p, .16f * p, -.65f * p),
                new Limb(-.18f * p, .04f * p, -.08f * p, -.12f * p),
                new Limb(-.42f * p, .05f * p, 0, .84f * p),
                new Limb(-.12f * p, -.03f * p, 0, .24f * p));
    }

    public static Pose strike(float progress) {
        float p = MonsterAnimationMath.clamp01(progress);
        Pose shoulder = new Pose(.34f, .20f, -.14f, -.18f, -.20f, -.18f,
                new Limb(-.42f, -.60f, .22f, -1.25f),
                new Limb(.30f, .12f, -.15f, -.25f),
                new Limb(-.30f, .08f, 0, .60f), new Limb(-.08f, -.08f, 0, .16f));
        Pose hook = new Pose(.24f, .72f, -.06f, -.12f, -.62f, .15f,
                new Limb(-1.30f, .65f, .22f, -.40f),
                new Limb(-.50f, -.10f, -.32f, -.82f),
                new Limb(-.12f, .08f, 0, .24f), new Limb(-.32f, -.08f, 0, .64f));
        // The torso arrives first. The crooked hand whips across only afterwards.
        return p < .42f ? blend(windup(1), shoulder, ease(p / .42f))
                : blend(shoulder, hook, ease((p - .42f) / .58f));
    }

    public static Pose recovery(float progress, float interruptedPose) {
        Pose source = interruptedPose < 0 ? windup(-interruptedPose) : strike(interruptedPose);
        Pose slack = new Pose(-.08f, -.10f, .12f, .05f, .08f, .20f,
                new Limb(.18f, -.10f, .04f, -.22f),
                new Limb(-.10f, .04f, -.05f, -.40f),
                new Limb(-.15f, 0, 0, .30f), new Limb(-.07f, 0, 0, .14f));
        return blend(source, slack, ease((progress - .12f) / .88f));
    }

    private static float ease(float p) { return MonsterAnimationMath.smootherstep(p); }
    private static float mix(float a, float b, float t) { return a + (b - a) * t; }
    private static Limb blend(Limb a, Limb b, float t) {
        return new Limb(mix(a.pitch, b.pitch, t), mix(a.yaw, b.yaw, t),
                mix(a.roll, b.roll, t), mix(a.joint, b.joint, t));
    }
    private static Pose blend(Pose a, Pose b, float t) {
        if (t <= 0) return a;
        if (t >= 1) return b;
        return new Pose(mix(a.torsoPitch,b.torsoPitch,t), mix(a.torsoYaw,b.torsoYaw,t),
                mix(a.torsoRoll,b.torsoRoll,t), mix(a.headPitch,b.headPitch,t),
                mix(a.headYaw,b.headYaw,t), mix(a.headRoll,b.headRoll,t),
                blend(a.rightArm,b.rightArm,t), blend(a.leftArm,b.leftArm,t),
                blend(a.rightLeg,b.rightLeg,t), blend(a.leftLeg,b.leftLeg,t));
    }
}

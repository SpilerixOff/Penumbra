package fr.penumbra.client.animation;

/** The imitation separates at the shoulders: crossed guard, opposing torsion, whip. */
public final class AbsentAttackAnimation {
    private AbsentAttackAnimation() { }
    public record Anchor(float x, float y, float z) { }
    public record Pose(float bodyPitch, float bodyYaw, float bodyRoll,
                       float headPitch, float headYaw, float headRoll,
                       float rightPitch, float rightYaw, float rightRoll,
                       float leftPitch, float leftYaw, float leftRoll,
                       float rightLeg, float leftLeg) { }

    public static Pose attack(float progress) {
        float p = MonsterAnimationMath.clamp01(progress);
        Pose crouch = new Pose(.46f, -.08f, 0, -.25f, .08f, 0,
                -1.10f, -.14f, .09f, -1.06f, .14f, -.09f, -.14f, .14f);
        Pose cross = new Pose(.38f, -.62f, -.16f, -.30f, .55f, .12f,
                -1.28f, -.92f, -.36f, -.32f, .20f, -.32f, -.55f, .45f);
        Pose whip = new Pose(.72f, .58f, .14f, -.40f, -.50f, -.16f,
                -1.55f, .55f, .32f, -1.17f, -.46f, .18f, .32f, -.48f);
        // At contact range the crossed arm is already legible within two ticks.
        return p < .25f ? blend(crouch, cross, ease(p / .25f))
                : blend(cross, whip, ease((p - .25f) / .75f));
    }

    public static Pose followThrough(float progress, float contactProgress) {
        float p = MonsterAnimationMath.clamp01(progress);
        Pose reached = attack(contactProgress);
        Pose slack = new Pose(.28f, .18f, .10f, -.18f, -.18f, .12f,
                -.30f, .15f, .12f, -.55f, -.10f, -.08f, -.08f, .08f);
        return p < .50f ? blend(reached, attack(1), ease(p / .50f))
                : blend(attack(1), slack, ease((p - .50f) / .50f));
    }

    public static Pose reveal(float progress) {
        float p = MonsterAnimationMath.clamp01(progress);
        float turn = ease(p / .55f);
        float shoulderCatch = ease((p - .42f) / .58f);
        return new Pose(.08f - .28f * shoulderCatch, -.48f * turn + .30f * shoulderCatch,
                -.14f * turn, -.15f * shoulderCatch, .62f * turn - .42f * shoulderCatch,
                .19f * turn, -.08f - .90f * shoulderCatch, -.30f * turn, -.24f * shoulderCatch,
                .06f - .60f * shoulderCatch, .12f * turn, -.06f, -.09f * turn, .09f * turn);
    }

    /** Flat HumanoidModel parts need rotated shoulder anchors to avoid detached arms. */
    public static Anchor attachment(Pose pose, float x, float y, float z) {
        y -= 12;
        double cx = Math.cos(pose.bodyPitch), sx = Math.sin(pose.bodyPitch);
        double cy = Math.cos(pose.bodyYaw), sy = Math.sin(pose.bodyYaw);
        double cz = Math.cos(pose.bodyRoll), sz = Math.sin(pose.bodyRoll);
        double y1 = y * cx - z * sx, z1 = y * sx + z * cx;
        double x2 = x * cy + z1 * sy, z2 = -x * sy + z1 * cy;
        return new Anchor((float) (x2 * cz - y1 * sz),
                (float) (12 + x2 * sz + y1 * cz), (float) z2);
    }

    private static float ease(float p) { return MonsterAnimationMath.smootherstep(p); }
    private static float mix(float a, float b, float t) { return a + (b - a) * t; }
    private static Pose blend(Pose a, Pose b, float t) {
        if (t <= 0) return a;
        if (t >= 1) return b;
        return new Pose(mix(a.bodyPitch,b.bodyPitch,t),mix(a.bodyYaw,b.bodyYaw,t),
                mix(a.bodyRoll,b.bodyRoll,t),mix(a.headPitch,b.headPitch,t),
                mix(a.headYaw,b.headYaw,t),mix(a.headRoll,b.headRoll,t),
                mix(a.rightPitch,b.rightPitch,t),mix(a.rightYaw,b.rightYaw,t),
                mix(a.rightRoll,b.rightRoll,t),mix(a.leftPitch,b.leftPitch,t),
                mix(a.leftYaw,b.leftYaw,t),mix(a.leftRoll,b.leftRoll,t),
                mix(a.rightLeg,b.rightLeg,t),mix(a.leftLeg,b.leftLeg,t));
    }
}

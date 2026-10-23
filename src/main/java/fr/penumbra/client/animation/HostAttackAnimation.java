package fr.penumbra.client.animation;

import fr.penumbra.entity.HostGrapple;

/**
 * Poses de l'attaque physique, en pixels de modele et radians. Les angles des
 * bras et de la tete sont absolus : le rig les convertit relativement au torse.
 * Les mains suivent la poitrine soulevee, sans allonger les os ni le maillage.
 */
public final class HostAttackAnimation {

    public static final float HIP_HEIGHT = 14.0f;
    public static final float RIGHT_SHOULDER_HEIGHT = 19.4f;
    public static final float LEFT_SHOULDER_HEIGHT = 18.4f;
    public static final float ARM_SEGMENT_LENGTH = 8.0f;
    public static final float HAND_REACH = 2.2f;
    public static final float PLAYER_CHEST_HEIGHT = 1.20f;
    private static final float HALF_PI = (float) (Math.PI / 2.0);

    private HostAttackAnimation() {
    }

    public static Pose windup(float progress) {
        float tension = ease(progress);
        float crouch = 0.62f * tension;
        Arms arms = new Arms(0.42f * tension, -0.85f * tension,
                0.10f * tension);
        return new Pose(groundedHips(crouch), 0.48f * tension,
                -0.08f * tension, -crouch, crouch * 2.0f,
                arms, arms, 0.07f * tension, 0.55f * tension);
    }

    public static Pose lunge(float progress) {
        float p = MonsterAnimationMath.clamp01(progress);
        // Un vrai plongeon : torse presque horizontal, mains en avant et
        // genoux replies. L'altitude provient de la trajectoire du serveur.
        Pose dive = new Pose(-11.5f, 1.05f, -0.08f, -1.05f, 1.85f,
                new Arms(-1.52f, -0.12f, 0.07f),
                new Arms(-1.52f, -0.12f, 0.07f), 0.10f, 0.0f);
        if (p < 0.24f) {
            return blend(windup(1.0f), dive, ease(p / 0.24f));
        }
        // Les coudes absorbent le contact et les jambes reviennent sous le
        // bassin avant la saisie ; la pose de jonction est exactement commune.
        return blend(dive, grapple(0.0f), ease((p - 0.55f) / 0.45f));
    }

    public static Pose grapple(float progress) {
        float ticks = MonsterAnimationMath.clamp01(progress) * HostGrapple.TOTAL_TICKS;
        float grab = ease(ticks / HostGrapple.GRAB_TICKS);
        float lift = ease((ticks - HostGrapple.GRAB_TICKS) / HostGrapple.LIFT_TICKS);
        float throwStart = HostGrapple.GRAB_TICKS + HostGrapple.LIFT_TICKS
                + HostGrapple.HOLD_TICKS;
        float push = ease((ticks - throwStart) / 3.0f);
        float followThrough = ease((ticks - throwStart - 3.0f) / 3.0f);
        float open = ease((ticks - throwStart) / 2.0f);

        float crouch = 0.50f * (1.0f - grab);
        float hips = groundedHips(crouch);
        // Se pencher assez pour atteindre la poitrine basse, puis redresser
        // le buste avec la victime : les avant-bras gardent leur vraie longueur.
        float torso = lerp(0.55f, 0.30f, grab) - 0.25f * lift
                + 0.46f * push - 0.18f * followThrough;
        float handForward = (float) HostGrapple.GRIP_DISTANCE * 16.0f
                + 5.2f * push;
        float handHeight = PLAYER_CHEST_HEIGHT * 16.0f
                + (float) HostGrapple.LIFT_HEIGHT * 16.0f * lift
                - 4.5f * push - 2.0f * followThrough;

        return new Pose(hips, torso, -0.20f * lift, -crouch, 2.0f * crouch,
                reach(hips, torso, RIGHT_SHOULDER_HEIGHT, handForward, handHeight),
                reach(hips, torso, LEFT_SHOULDER_HEIGHT, handForward, handHeight),
                0.0f, grab * (1.0f - open));
    }

    public static Pose recovery(float progress) {
        float p = MonsterAnimationMath.clamp01(progress);
        float settle = ease(p / 0.70f);
        Pose thrown = grapple(1.0f);
        float recoil = (float) Math.sin(Math.min(p / 0.45f, 1.0f) * Math.PI);
        Arms right = new Arms(-0.18f, -0.32f, -0.12f);
        Arms left = new Arms(-0.12f, -0.26f, -0.08f);
        Pose rest = new Pose(groundedHips(0.12f), -0.12f, -0.15f,
                -0.12f, 0.24f, right, left, 0.03f, 0.15f);
        Pose pose = blend(thrown, rest, settle);
        return new Pose(pose.hipsY(), pose.torsoPitch() - 0.25f * recoil,
                pose.headPitch(), pose.thighPitch(), pose.kneePitch(),
                pose.rightArm(), pose.leftArm(), pose.spread(), pose.grip());
    }

    private static Arms reach(float hipsY, float torsoPitch, float shoulderHeight,
                              float handForward, float handHeight) {
        float shoulderY = hipsY - shoulderHeight * (float) Math.cos(torsoPitch);
        float shoulderForward = shoulderHeight * (float) Math.sin(torsoPitch);
        float down = -handHeight - shoulderY;
        float forward = handForward - HAND_REACH - shoulderForward;
        float distance = (float) Math.sqrt(down * down + forward * forward);
        float bounded = Math.max(0.001f, Math.min(15.999f, distance));
        float aim = -(float) Math.atan2(forward, down);
        float shoulder = aim + (float) Math.acos(bounded / (2.0f * ARM_SEGMENT_LENGTH));
        float elbow = -2.0f * (float) Math.acos(bounded / (2.0f * ARM_SEGMENT_LENGTH));
        // Les paumes restent orientees vers la victime pendant toute la levee.
        return new Arms(shoulder, elbow, -HALF_PI - shoulder - elbow);
    }

    private static float groundedHips(float crouch) {
        // Deux segments de 7 px opposes : pieds fixes au sol durant la flexion.
        return -HIP_HEIGHT * (float) Math.cos(crouch);
    }

    private static Pose blend(Pose a, Pose b, float t) {
        return new Pose(lerp(a.hipsY(), b.hipsY(), t),
                lerp(a.torsoPitch(), b.torsoPitch(), t),
                lerp(a.headPitch(), b.headPitch(), t),
                lerp(a.thighPitch(), b.thighPitch(), t),
                lerp(a.kneePitch(), b.kneePitch(), t),
                blend(a.rightArm(), b.rightArm(), t),
                blend(a.leftArm(), b.leftArm(), t),
                lerp(a.spread(), b.spread(), t), lerp(a.grip(), b.grip(), t));
    }

    private static Arms blend(Arms a, Arms b, float t) {
        return new Arms(lerp(a.shoulderPitch(), b.shoulderPitch(), t),
                lerp(a.elbowPitch(), b.elbowPitch(), t),
                lerp(a.wristPitch(), b.wristPitch(), t));
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float ease(float p) {
        return MonsterAnimationMath.smoothstep(p);
    }

    public record Arms(float shoulderPitch, float elbowPitch, float wristPitch) {
    }

    public record Pose(float hipsY, float torsoPitch, float headPitch,
                       float thighPitch, float kneePitch, Arms rightArm,
                       Arms leftArm, float spread, float grip) {
    }
}

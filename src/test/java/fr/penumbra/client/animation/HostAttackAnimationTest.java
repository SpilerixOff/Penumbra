package fr.penumbra.client.animation;

import fr.penumbra.entity.HostGrapple;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostAttackAnimationTest {

    @Test
    void feetStayOnTheFloorThroughCrouchAndLift() {
        for (int sample = 0; sample <= 100; sample++) {
            assertGrounded(HostAttackAnimation.windup(sample / 100.0f));
            assertGrounded(HostAttackAnimation.grapple(sample / 100.0f));
        }
    }

    @Test
    void bothHandsFollowTheLiftedChestWithoutStretchingTheArms() {
        int liftEnd = HostGrapple.GRAB_TICKS + HostGrapple.LIFT_TICKS;
        for (float ticks = HostGrapple.GRAB_TICKS; ticks <= liftEnd; ticks += 0.25f) {
            HostAttackAnimation.Pose pose = HostAttackAnimation.grapple(
                    ticks / HostGrapple.TOTAL_TICKS);
            float lift = MonsterAnimationMath.smoothstep(
                    (ticks - HostGrapple.GRAB_TICKS) / HostGrapple.LIFT_TICKS);
            double targetHeight = HostAttackAnimation.PLAYER_CHEST_HEIGHT * 16.0
                    + HostGrapple.LIFT_HEIGHT * 16.0 * lift;
            assertHandAt(pose, pose.rightArm(), HostAttackAnimation.RIGHT_SHOULDER_HEIGHT,
                    HostGrapple.GRIP_DISTANCE * 16.0, targetHeight);
            assertHandAt(pose, pose.leftArm(), HostAttackAnimation.LEFT_SHOULDER_HEIGHT,
                    HostGrapple.GRIP_DISTANCE * 16.0, targetHeight);
            assertEquals(1.0f, pose.grip(), 0.0001f);
        }
    }

    @Test
    void phaseJoinsDoNotSnapTheSkeleton() {
        assertSamePose(HostAttackAnimation.windup(1.0f), HostAttackAnimation.lunge(0.0f));
        assertSamePose(HostAttackAnimation.lunge(1.0f), HostAttackAnimation.grapple(0.0f));
        assertSamePose(HostAttackAnimation.grapple(1.0f), HostAttackAnimation.recovery(0.0f));
    }

    @Test
    void diveFoldsTheKneesAndThrowOpensBothHands() {
        HostAttackAnimation.Pose dive = HostAttackAnimation.lunge(0.45f);
        assertTrue(dive.torsoPitch() > 0.9f);
        assertTrue(dive.kneePitch() > 1.5f);
        assertTrue(dive.rightArm().shoulderPitch() < -1.4f);
        assertTrue(dive.leftArm().shoulderPitch() < -1.4f);
        assertEquals(0.0f, dive.grip());

        float held = (HostGrapple.GRAB_TICKS + HostGrapple.LIFT_TICKS
                + HostGrapple.HOLD_TICKS) / (float) HostGrapple.TOTAL_TICKS;
        assertEquals(1.0f, HostAttackAnimation.grapple(held).grip());
        assertEquals(0.0f, HostAttackAnimation.grapple(1.0f).grip());
    }

    @Test
    void invalidProgressCannotPoisonModelTransforms() {
        assertSamePose(HostAttackAnimation.grapple(0.0f), HostAttackAnimation.grapple(Float.NaN));
        assertSamePose(HostAttackAnimation.grapple(0.0f), HostAttackAnimation.grapple(-1.0f));
        assertSamePose(HostAttackAnimation.grapple(1.0f), HostAttackAnimation.grapple(3.0f));
    }

    private static void assertGrounded(HostAttackAnimation.Pose pose) {
        double ankleY = pose.hipsY() + 7.0 * Math.cos(pose.thighPitch())
                + 7.0 * Math.cos(pose.thighPitch() + pose.kneePitch());
        double ankleForward = -7.0 * Math.sin(pose.thighPitch())
                - 7.0 * Math.sin(pose.thighPitch() + pose.kneePitch());
        assertEquals(0.0, ankleY, 0.0001);
        assertEquals(0.0, ankleForward, 0.0001);
    }

    private static void assertHandAt(HostAttackAnimation.Pose pose, HostAttackAnimation.Arms arm,
                                     float shoulderHeight, double forward, double height) {
        // Forward kinematics of the actual fixed-length rig, independent from
        // its inverse-kinematics solver: checks the two physical contact points.
        double armAngle = arm.shoulderPitch();
        double forearmAngle = armAngle + arm.elbowPitch();
        double handAngle = forearmAngle + arm.wristPitch();
        double handY = pose.hipsY() - shoulderHeight * Math.cos(pose.torsoPitch())
                + 8.0 * Math.cos(armAngle) + 8.0 * Math.cos(forearmAngle)
                + HostAttackAnimation.HAND_REACH * Math.cos(handAngle);
        double handForward = shoulderHeight * Math.sin(pose.torsoPitch())
                - 8.0 * Math.sin(armAngle) - 8.0 * Math.sin(forearmAngle)
                - HostAttackAnimation.HAND_REACH * Math.sin(handAngle);
        assertEquals(forward, handForward, 0.0001);
        assertEquals(height, -handY, 0.0001);
    }

    private static void assertSamePose(HostAttackAnimation.Pose a, HostAttackAnimation.Pose b) {
        assertEquals(a.hipsY(), b.hipsY(), 0.0001f);
        assertEquals(a.torsoPitch(), b.torsoPitch(), 0.0001f);
        assertEquals(a.headPitch(), b.headPitch(), 0.0001f);
        assertEquals(a.thighPitch(), b.thighPitch(), 0.0001f);
        assertEquals(a.kneePitch(), b.kneePitch(), 0.0001f);
        assertEquals(a.rightArm().shoulderPitch(), b.rightArm().shoulderPitch(), 0.0001f);
        assertEquals(a.rightArm().elbowPitch(), b.rightArm().elbowPitch(), 0.0001f);
        assertEquals(a.rightArm().wristPitch(), b.rightArm().wristPitch(), 0.0001f);
        assertEquals(a.leftArm().shoulderPitch(), b.leftArm().shoulderPitch(), 0.0001f);
        assertEquals(a.leftArm().elbowPitch(), b.leftArm().elbowPitch(), 0.0001f);
        assertEquals(a.leftArm().wristPitch(), b.leftArm().wristPitch(), 0.0001f);
        assertEquals(a.grip(), b.grip(), 0.0001f);
    }
}

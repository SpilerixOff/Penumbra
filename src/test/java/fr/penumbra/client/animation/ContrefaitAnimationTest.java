package fr.penumbra.client.animation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContrefaitAnimationTest {
    @Test
    void preparationJoinsChargeAndInterruptionRecoversFromItsActualPose() {
        assertEquals(ContrefaitAnimation.windup(1), ContrefaitAnimation.strike(0));
        assertEquals(ContrefaitAnimation.strike(.63f), ContrefaitAnimation.recovery(0, .63f));
        assertEquals(ContrefaitAnimation.windup(.5f), ContrefaitAnimation.recovery(0, -.5f));
    }

    @Test
    void shoulderArrivesBeforeHookAndArmsRemainAsymmetric() {
        var early = ContrefaitAnimation.strike(.42f);
        var late = ContrefaitAnimation.strike(1);
        assertTrue(early.torsoYaw() > 0);
        assertTrue(early.rightArm().pitch() > -.5f);
        assertTrue(late.rightArm().pitch() < -1.2f);
        assertTrue(Math.abs(late.rightArm().pitch() - late.leftArm().pitch()) > .5f);
    }

    @Test
    void articulatedLegsKeepOneFootGroundedThroughAllPhases() {
        for (int i = 0; i <= 200; i++) {
            float p = i / 200f;
            for (var pose : new ContrefaitAnimation.Pose[]{ContrefaitAnimation.windup(p),
                    ContrefaitAnimation.strike(p), ContrefaitAnimation.recovery(p, .8f)}) {
                double right = foot(pose.hipsY(), pose.rightLeg());
                double left = foot(pose.hipsY(), pose.leftLeg());
                assertEquals(0, Math.max(right, left), 1E-5);
                assertTrue(Math.min(right, left) <= .001);
                assertTrue(Float.isFinite(pose.hipsY()));
            }
        }
    }
    private static double foot(float hipY, ContrefaitAnimation.Limb leg) {
        return hipY + 6 * Math.cos(leg.pitch()) + 6 * Math.cos(leg.pitch() + leg.joint());
    }
}

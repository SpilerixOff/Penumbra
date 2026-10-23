package fr.penumbra.client.animation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OriginelAnimationTest {
    @Test
    void warningAndCommittedTiltShareTheirBoundaryPose() {
        assertEquals(OriginelAnimation.windup(1), OriginelAnimation.lunge(0));
        var nearEnd = OriginelAnimation.lunge(0.44999f);
        var next = OriginelAnimation.lunge(0.45001f);
        assertEquals(nearEnd.torsoPitch(), next.torsoPitch(), 0.001f);
        assertEquals(nearEnd.rightLegPitch(), next.rightLegPitch(), 0.001f);
    }

    @Test
    void collisionAtAnyAttackFrameCanRecoverWithoutPoseJump() {
        for (int i = 0; i <= 100; i++) {
            float interrupted = i / 100.0f;
            assertEquals(OriginelAnimation.lunge(interrupted), OriginelAnimation.recovery(0, interrupted));
        }
        assertEquals(OriginelAnimation.recovery(1, 0.1f), OriginelAnimation.recovery(1, 0.9f));
    }

    @Test
    void rigidTiltAvoidsAHostStyleTwoHandGrab() {
        var pose = OriginelAnimation.lunge(0.45f);
        assertTrue(pose.torsoPitch() > 0.6f && pose.torsoPitch() < 0.75f);
        assertTrue(pose.leftArmPitch() > 0 && pose.rightArmPitch() < 0);
    }

    @Test
    void invalidOrLateTimeCannotRestartTheStrike() {
        assertEquals(OriginelAnimation.lunge(0), OriginelAnimation.lunge(Float.NaN));
        assertEquals(OriginelAnimation.lunge(1), OriginelAnimation.lunge(500));
        assertEquals(OriginelAnimation.windup(0), OriginelAnimation.windup(-100));
    }
}

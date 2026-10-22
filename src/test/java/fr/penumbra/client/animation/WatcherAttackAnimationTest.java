package fr.penumbra.client.animation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WatcherAttackAnimationTest {
    @Test void interruptionStartsAtCapturedPoseInsteadOfSnappingBack() {
        assertEquals(WatcherAttackAnimation.strike(1), WatcherAttackAnimation.recovery(0, 3f));
        assertEquals(WatcherAttackAnimation.windup(.25f), WatcherAttackAnimation.recovery(0, .25f));
    }
    @Test void preparationConnectsToStrikeAndContactRecovery() {
        assertEquals(WatcherAttackAnimation.windup(1), WatcherAttackAnimation.strike(0));
        assertEquals(WatcherAttackAnimation.strike(1), WatcherAttackAnimation.recovery(0, true));
        assertEquals(WatcherAttackAnimation.windup(0), WatcherAttackAnimation.recovery(1, true));
    }
    @Test void groundedLegChainRemainsPlantedThroughEveryPhase() {
        for (int frame = 0; frame <= 300; frame++) {
            float p = frame / 300f;
            for (var pose : new WatcherAttackAnimation.Pose[] {
                    WatcherAttackAnimation.windup(p), WatcherAttackAnimation.strike(p),
                    WatcherAttackAnimation.recovery(p, true), WatcherAttackAnimation.recovery(p, false)}) {
                double sole = WatcherAttackAnimation.hipY(pose.bend()) + 16 * Math.cos(pose.bend());
                assertEquals(0, sole, .00001);
                assertTrue(pose.bend() >= 0 && pose.bend() < .8f);
            }
        }
    }
    @Test void onlyOneArmStrikesAndCancelledRecoveryDoesNotFakeContact() {
        var strike = WatcherAttackAnimation.strike(.85f);
        assertTrue(strike.rightArm() < -1.2f);
        assertTrue(strike.leftArm() > 0);
        assertNotEquals(WatcherAttackAnimation.recovery(0, true), WatcherAttackAnimation.recovery(0, false));
    }
    @Test void malformedProgressIsBoundedAndFinite() {
        for (float p : new float[] {Float.NaN, Float.POSITIVE_INFINITY, -100, 100}) {
            var pose = WatcherAttackAnimation.strike(p);
            assertTrue(Float.isFinite(pose.lean()));
            assertTrue(Math.abs(pose.twist()) < 1);
            assertTrue(Math.abs(pose.rightArm()) < 2);
        }
    }
}

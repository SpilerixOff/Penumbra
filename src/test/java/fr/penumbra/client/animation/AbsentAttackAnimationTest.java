package fr.penumbra.client.animation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AbsentAttackAnimationTest {
    @Test
    void earlyCrossUnwindsIntoOppositeTorsoTurn() {
        var crossed = AbsentAttackAnimation.attack(.25f);
        var strike = AbsentAttackAnimation.attack(1);
        assertTrue(crossed.bodyYaw() < -.5f);
        assertTrue(strike.bodyYaw() > .5f);
        assertTrue(crossed.rightYaw() < -.8f);
        assertTrue(Math.abs(crossed.rightPitch() - crossed.leftPitch()) > .8f);
    }
    @Test
    void contactContinuesFromActualImpactPoseInsteadOfRestarting() {
        for (float p : new float[]{0,.1f,.5f,.9f,1}) {
            assertEquals(AbsentAttackAnimation.attack(p), AbsentAttackAnimation.followThrough(0,p));
            assertEquals(AbsentAttackAnimation.attack(1), AbsentAttackAnimation.followThrough(.5f,p));
        }
    }
    @Test
    void flatHumanoidPartsStayAttachedAtConstantShoulderSeparation() {
        for (int i = 0; i <= 100; i++) {
            var pose = AbsentAttackAnimation.attack(i / 100f);
            var right = AbsentAttackAnimation.attachment(pose,-5,2,0);
            var left = AbsentAttackAnimation.attachment(pose,5,2,0);
            double dx = right.x()-left.x(), dy = right.y()-left.y(), dz = right.z()-left.z();
            assertEquals(10, Math.sqrt(dx*dx+dy*dy+dz*dz), 1E-5);
            var hip = AbsentAttackAnimation.attachment(pose,0,12,0);
            assertEquals(0, hip.x(), 1E-6);
            assertEquals(12, hip.y(), 1E-6);
            assertEquals(0, hip.z(), 1E-6);
        }
    }
    @Test
    void revealHeadTurnsAgainstShouldersAndAllCurvesAreBounded() {
        var reveal = AbsentAttackAnimation.reveal(.5f);
        assertTrue(reveal.bodyYaw() < 0 && reveal.headYaw() > 0);
        assertEquals(AbsentAttackAnimation.attack(0), AbsentAttackAnimation.attack(Float.NaN));
        assertEquals(AbsentAttackAnimation.attack(1), AbsentAttackAnimation.attack(42));
        assertEquals(AbsentAttackAnimation.reveal(0), AbsentAttackAnimation.reveal(-1));
    }
}

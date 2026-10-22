package fr.penumbra.client.animation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RampantAttackAnimationTest {
    @Test void ForelegsLetGoSequentiallyBeforeRearLegs() {
        var early = RampantAttackAnimation.release(.25f);
        assertTrue(early.right() > .5f);
        assertEquals(0, early.left());
        assertEquals(0, early.rear());
        var middle = RampantAttackAnimation.release(.6f);
        assertEquals(1, middle.right());
        assertTrue(middle.left() > .5f);
        assertEquals(0, middle.rear());
        assertEquals(new RampantAttackAnimation.Release(1, 1, 1), RampantAttackAnimation.release(1));
    }
    @Test void FlightOpensEarlyAndHasNoEndlessFlapping() {
        assertTrue(RampantAttackAnimation.flight(1).frontReach() < RampantAttackAnimation.flight(5).frontReach());
        assertEquals(RampantAttackAnimation.flight(5), RampantAttackAnimation.flight(18));
    }
    @Test void ContactHasOneHookAndSettlesAtEnd() {
        assertEquals(0, RampantAttackAnimation.contact(0).hook());
        assertTrue(RampantAttackAnimation.contact(3).hook() > .8f);
        var end = RampantAttackAnimation.contact(12);
        assertEquals(0, end.hook());
        assertEquals(0, end.compress(), .00001);
        assertEquals(1, end.withdraw());
    }
    @Test void SampledContactAndReleaseCannotExplodeOrInvert() {
        for (float p : new float[] {Float.NaN, Float.NEGATIVE_INFINITY, -10, 0, .2f, .8f, 1, 100}) {
            var release = RampantAttackAnimation.release(p);
            var contact = RampantAttackAnimation.contact(p * 12);
            for (float value : new float[] {release.right(), release.left(), release.rear(),
                    contact.hook(), contact.compress(), contact.withdraw()}) {
                assertTrue(Float.isFinite(value));
                assertTrue(value >= -0.00001f && value <= 1);
            }
        }
    }
}

package fr.penumbra.client.animation;

import fr.penumbra.entity.listener.ListenerStrike;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ListenerAttackAnimationTest {
    @Test
    void theEarsReactBeforeTheHeadAndTheHeadBeforeTheTorso() {
        var early = ListenerAttackAnimation.warning(0.15f);
        assertTrue(early.ear() > 0.5f);
        assertEquals(0.0f, early.head());
        assertEquals(0.0f, early.torso());
        var middle = ListenerAttackAnimation.warning(0.4f);
        assertEquals(1.0f, middle.ear());
        assertTrue(middle.head() > 0.5f);
        assertEquals(0.0f, middle.torso());
        assertEquals(new ListenerAttackAnimation.Warning(1, 1, 1), ListenerAttackAnimation.warning(1));
    }

    @Test
    void theSweepReachesFullExtensionAtTheOnlyDamageTickThenUnwinds() {
        assertEquals(0.0f, ListenerAttackAnimation.sweep(0).reach());
        assertEquals(1.0f, ListenerAttackAnimation.sweep(ListenerStrike.IMPACT_TICK).reach());
        assertTrue(ListenerAttackAnimation.sweep(0).torsoYaw() < 0.0f);
        assertTrue(ListenerAttackAnimation.sweep(ListenerStrike.IMPACT_TICK).torsoYaw() > 0.5f);
        var end = ListenerAttackAnimation.sweep(ListenerStrike.TOTAL_TICKS);
        assertEquals(0.0f, end.active());
        assertEquals(0.0f, end.reach());
        assertEquals(0.0f, end.torsoYaw());
        assertEquals(0.34f, end.torsoPitch());
    }

    @Test
    void curvesStayFiniteBoundedAndContinuousAtImpactAndRecoveryJoins() {
        for (float t = 0; t <= ListenerStrike.TOTAL_TICKS; t += 0.025f) {
            var pose = ListenerAttackAnimation.sweep(t);
            assertTrue(pose.reach() >= 0 && pose.reach() <= 1);
            assertTrue(pose.active() >= 0 && pose.active() <= 1);
            assertTrue(Math.abs(pose.torsoYaw()) <= 0.58f);
            assertTrue(Float.isFinite(pose.torsoRoll()));
        }
        for (float boundary : new float[] {ListenerStrike.IMPACT_TICK, ListenerStrike.FOLLOW_THROUGH_TICKS}) {
            assertEquals(ListenerAttackAnimation.sweep(boundary - 0.0001f).reach(),
                    ListenerAttackAnimation.sweep(boundary + 0.0001f).reach(), 0.0001f);
        }
        assertEquals(ListenerAttackAnimation.sweep(0), ListenerAttackAnimation.sweep(Float.NaN));
        assertEquals(ListenerAttackAnimation.warning(0), ListenerAttackAnimation.warning(Float.NaN));
    }
}

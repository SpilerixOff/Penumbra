package fr.penumbra.entity.listener;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ListenerStrikeTest {
    @Test
    void aCommittedFrontSweepCannotReachBehindTheCreature() {
        assertTrue(ListenerStrike.withinSweep(0.5, 0.5, 0));
        assertFalse(ListenerStrike.withinSweep(0.1, -0.5, 0));
        assertTrue(ListenerStrike.withinSweep(-0.5, 0, 90));
        assertFalse(ListenerStrike.withinSweep(0.5, 0, 90));
        assertFalse(ListenerStrike.withinSweep(Double.NaN, 1, 0));
    }

    @Test
    void exactlyOneContactMomentPrecedesVisibleRecoilAndDeparture() {
        int opportunities = 0;
        for (int tick = 0; tick <= ListenerStrike.TOTAL_TICKS; tick++) {
            if (ListenerStrike.impactDue(tick)) opportunities++;
            assertEquals(tick >= ListenerStrike.TOTAL_TICKS, ListenerStrike.complete(tick));
        }
        assertEquals(1, opportunities);
        assertTrue(ListenerStrike.FOLLOW_THROUGH_TICKS > ListenerStrike.IMPACT_TICK);
        assertTrue(ListenerStrike.TOTAL_TICKS - ListenerStrike.FOLLOW_THROUGH_TICKS >= 12);
    }

    @Test
    void lateTrackingUsesTheAuthoritativeElapsedTickWithoutRestartingTheSweep() {
        assertEquals(14.5f, ListenerStrike.elapsed(15, 0.5f));
        assertEquals(0.0f, ListenerStrike.elapsed(0, 0.9f));
        assertEquals(0.0f, ListenerStrike.elapsed(-4, 0.9f));
        assertEquals(ListenerStrike.TOTAL_TICKS, ListenerStrike.elapsed(Integer.MAX_VALUE, 1.0f));
        assertEquals(4.0f, ListenerStrike.elapsed(5, Float.NaN));
    }

    @Test
    void aPlayerCanBecomeSilentOrStepBehindCoverDuringTheArmSweep() {
        ListenerPursuit pursuit = new ListenerPursuit();
        pursuit.start();
        for (int tick = 0; tick < ListenerStrike.WARNING_TICKS; tick++) {
            pursuit.heardNoise();
            pursuit.tick();
        }
        assertTrue(pursuit.canContact(ListenerNoise.WALK_RADIUS, 0.8, true));
        for (int tick = 0; tick < ListenerStrike.IMPACT_TICK; tick++) pursuit.tick();
        assertFalse(pursuit.canContact(0.0, 0.8, true));
        assertFalse(pursuit.canContact(ListenerNoise.WALK_RADIUS, 0.8, false));
        assertFalse(pursuit.canContact(ListenerNoise.WALK_RADIUS, 1.2, true));
        assertTrue(pursuit.canContact(ListenerNoise.WALK_RADIUS, 0.8, true));
    }
}

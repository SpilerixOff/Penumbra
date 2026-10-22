package fr.penumbra.entity.listener;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ListenerPursuitTest {

    @Test
    void lockAtPointBlankStillGivesThePlayerTimeToReact() {
        ListenerPursuit pursuit = new ListenerPursuit();
        pursuit.start();
        for (int tick = 1; tick < ListenerPursuit.WARNING_TICKS; tick++) {
            pursuit.heardNoise();
            pursuit.tick();
            assertFalse(pursuit.canContact(ListenerNoise.WALK_RADIUS, 0.1, true));
        }
        pursuit.tick();
        assertTrue(pursuit.canContact(ListenerNoise.WALK_RADIUS, 0.1, true));
    }

    @Test
    void becomingSilentProtectsTheOwnerBeforeTheLockFullyExpires() {
        ListenerPursuit pursuit = readyPursuit();
        assertTrue(pursuit.canContact(ListenerNoise.WALK_RADIUS, 0.5, true));
        assertFalse(pursuit.canContact(0.0, 0.5, true));
        assertFalse(pursuit.lostSound(), "silence must protect before the search pose changes");
        for (int tick = 0; tick < ListenerNoise.LOCK_GRACE_TICKS; tick++) {
            pursuit.tick();
        }
        assertTrue(pursuit.lostSound());
    }

    @Test
    void aNoisyTeammateCannotMakeTheListenerHitItsSilentOwner() {
        ListenerPursuit pursuit = readyPursuit();
        for (int tick = 0; tick < 100; tick++) {
            pursuit.heardNoise();
            pursuit.tick();
            assertFalse(pursuit.lostSound());
            assertFalse(pursuit.canContact(0.0, 0.1, true));
        }
    }

    @Test
    void audibleOwnerBehindCoverOrOutsideContactRangeCannotBeHit() {
        ListenerPursuit pursuit = readyPursuit();
        assertFalse(pursuit.canContact(ListenerNoise.SPRINT_RADIUS, 0.4, false));
        assertFalse(pursuit.canContact(ListenerNoise.SPRINT_RADIUS,
                ListenerPursuit.CONTACT_DISTANCE + 0.001, true));
        assertTrue(pursuit.canContact(ListenerNoise.WALK_RADIUS,
                ListenerPursuit.CONTACT_DISTANCE, true));
    }

    @Test
    void aNewNoiseInterruptsSilenceButANewChargeRestoresTheWarning() {
        ListenerPursuit pursuit = readyPursuit();
        pursuit.heardNoise();
        for (int tick = 1; tick < ListenerNoise.LOCK_GRACE_TICKS; tick++) {
            pursuit.tick();
        }
        assertFalse(pursuit.lostSound());
        pursuit.heardNoise();
        pursuit.tick();
        assertFalse(pursuit.lostSound());
        pursuit.start();
        assertFalse(pursuit.warningComplete());
        assertFalse(pursuit.canContact(ListenerNoise.WALK_RADIUS, 0.1, true));
    }

    private static ListenerPursuit readyPursuit() {
        ListenerPursuit pursuit = new ListenerPursuit();
        pursuit.start();
        for (int tick = 0; tick < ListenerPursuit.WARNING_TICKS; tick++) {
            pursuit.heardNoise();
            pursuit.tick();
        }
        return pursuit;
    }

    @Test
    void sustainedNoiseCannotKeepAChargeAliveForever() {
        ListenerPursuit pursuit = new ListenerPursuit();
        pursuit.start();
        for (int tick = 1; tick < ListenerPursuit.MAX_CHARGE_TICKS; tick++) {
            pursuit.heardNoise();
            pursuit.tick();
            assertFalse(pursuit.exhausted());
        }
        pursuit.heardNoise();
        pursuit.tick();
        assertTrue(pursuit.exhausted());
        assertFalse(pursuit.canContact(ListenerNoise.SPRINT_RADIUS, 0.1, true));
    }

    @Test
    void wallsMuffleMovementWithoutTurningSilenceIntoSound() {
        double hiddenWalking = ListenerPursuit.heardRadius(ListenerNoise.WALK_RADIUS, false);
        assertFalse(ListenerNoise.audible(hiddenWalking, 4.0));
        assertTrue(ListenerNoise.audible(hiddenWalking, 2.0));
        assertFalse(ListenerNoise.lockable(hiddenWalking, 2.0));
        assertEquals(ListenerNoise.WALK_RADIUS,
                ListenerPursuit.heardRadius(ListenerNoise.WALK_RADIUS, true));
        assertEquals(0.0, ListenerPursuit.heardRadius(0.0, false));
        assertEquals(0.0, ListenerPursuit.heardRadius(Double.NaN, true));
        assertEquals(ListenerNoise.MAX_RADIUS, ListenerPursuit.heardRadius(1000, true));
    }
}

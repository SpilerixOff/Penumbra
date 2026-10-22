package fr.penumbra.story;

import org.junit.jupiter.api.Test;

import static fr.penumbra.story.ConfrontationSequence.Phase.*;
import static org.junit.jupiter.api.Assertions.*;

class ConfrontationSequenceTest {
    private static ConfrontationSequence observing() {
        ConfrontationSequence sequence = new ConfrontationSequence();
        sequence.advance(ConfrontationSequence.ARRIVAL_TICKS, true);
        assertEquals(MIRROR, sequence.phase());
        assertEquals(0, sequence.focusTicks(), "arrival cannot silently precharge observation");
        return sequence;
    }

    @Test
    void observationRequiresUninterruptedSilenceAndFraming() {
        ConfrontationSequence sequence = observing();
        sequence.advance(29, true);
        sequence.advance(1, false);
        assertEquals(0, sequence.focusTicks());
        sequence.advance(29, true);
        assertEquals(MIRROR, sequence.phase());
        sequence.advance(1, true);
        assertEquals(EXPOSED, sequence.phase());
    }

    @Test
    void onlyOneFramedFlashCanConsumeAnEarnedWindow() {
        ConfrontationSequence sequence = observing();
        assertFalse(sequence.flash(true));
        sequence.advance(30, true);
        assertFalse(sequence.flash(false), "a blocked or off-camera shot cannot consume the window");
        assertEquals(EXPOSED, sequence.phase());
        assertTrue(sequence.flash(true));
        for (int i = 0; i < 10; i++) assertFalse(sequence.flash(true));
        assertEquals(1, sequence.flashes());
        assertEquals(RECOVERY, sequence.phase());
    }

    @Test
    void missedWindowPreservesFragmentsAndAllowsAnotherAttemptAfterRecovery() {
        ConfrontationSequence sequence = observing();
        sequence.advance(30, true);
        assertTrue(sequence.flash(true));
        sequence.advance(60, true);
        sequence.advance(40, true);
        assertEquals(EXPOSED, sequence.phase());
        sequence.advance(60, true);
        assertEquals(RECOVERY, sequence.phase());
        assertEquals(1, sequence.flashes());
        sequence.advance(60, true);
        sequence.advance(40, true);
        assertTrue(sequence.flash(true));
        assertEquals(2, sequence.flashes());
    }

    @Test
    void laterFragmentsRequireLongerObservationAndThreeSeparateWindowsWin() {
        ConfrontationSequence sequence = observing();
        for (int fragment = 0; fragment < 3; fragment++) {
            int focus = 30 + fragment * 10;
            assertEquals(focus, sequence.requiredFocusTicks());
            sequence.advance(focus - 1, true);
            assertEquals(MIRROR, sequence.phase());
            sequence.advance(1, true);
            assertTrue(sequence.flash(true));
            if (fragment < 2) {
                sequence.advance(59, true);
                assertEquals(RECOVERY, sequence.phase());
                sequence.advance(1, true);
                assertEquals(0, sequence.focusTicks());
            }
        }
        assertEquals(COMPLETE, sequence.phase());
        sequence.advance(5000, false);
        assertEquals(COMPLETE, sequence.phase());
        assertEquals(3, sequence.flashes());
    }

    @Test
    void shutterBreaksConcentrationAndCannotAddFragmentsDuringArrivalOrRecovery() {
        ConfrontationSequence sequence = new ConfrontationSequence();
        assertFalse(sequence.flash(true));
        sequence.advance(60, true);
        sequence.advance(20, true);
        assertFalse(sequence.flash(true));
        assertEquals(0, sequence.focusTicks());
        sequence.advance(29, true);
        assertEquals(MIRROR, sequence.phase());
    }

    @Test
    void exposureOutlastsFullCameraCooldownAndTimeoutNeverAwardsVictory() {
        assertTrue(ConfrontationSequence.EXPOSURE_TICKS > 40,
                "the server camera cooldown must leave an actionable part of each window");
        ConfrontationSequence sequence = observing();
        sequence.advance(ConfrontationSequence.TIMEOUT_TICKS, false);
        assertEquals(EXPIRED, sequence.phase());
        assertFalse(sequence.flash(true));
        assertEquals(0, sequence.flashes());
    }
}

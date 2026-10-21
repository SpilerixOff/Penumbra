package fr.penumbra.perception;

import fr.penumbra.director.TensionState;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PerceptionRulesTest {

    @Test
    void individualPerceptionIsVisibleOnlyToItsOwner() {
        UUID owner = UUID.randomUUID();
        assertTrue(PerceptionRules.mayObserve(owner, owner, true));
        assertFalse(PerceptionRules.mayObserve(owner, UUID.randomUUID(), true));
        assertFalse(PerceptionRules.mayObserve(null, owner, true));
        assertTrue(PerceptionRules.mayObserve(owner, UUID.randomUUID(), false));
    }

    @Test
    void sustainedVerificationTurnsThenDismissesTheCompanion() {
        int focus = 0;
        for (int tick = 0; tick < PerceptionRules.NOTICE_FOCUS_TICKS; tick++) {
            focus = PerceptionRules.nextFocusTicks(focus, true, false);
        }
        assertEquals(PerceptionRules.Verification.NOTICED,
                PerceptionRules.verification(focus, false));
        while (focus < PerceptionRules.DISMISS_FOCUS_TICKS) {
            focus = PerceptionRules.nextFocusTicks(focus, true, true);
        }
        assertEquals(PerceptionRules.Verification.DISMISS,
                PerceptionRules.verification(focus, false));
        assertEquals(PerceptionRules.Verification.DISMISS,
                PerceptionRules.verification(0, true));
    }

    @Test
    void lookingAwayLetsSuspicionFallInsteadOfAccumulatingForever() {
        int focus = PerceptionRules.NOTICE_FOCUS_TICKS;
        for (int tick = 0; tick < 8; tick++) {
            focus = PerceptionRules.nextFocusTicks(focus, false, true);
        }
        assertEquals(0, focus);
        assertEquals(PerceptionRules.Verification.UNAWARE,
                PerceptionRules.verification(focus, false));
    }

    @Test
    void eligibilityRejectsEveryUnsafeOrUnreadyContext() {
        assertTrue(eligible(true, true, true, false, false,
                false, true, true, false, true, 2, TensionState.WATCHED));
        assertFalse(eligible(false, true, true, false, false,
                false, true, true, false, true, 2, TensionState.WATCHED));
        assertFalse(eligible(true, true, true, false, false,
                false, true, true, true, true, 2, TensionState.WATCHED));
        assertFalse(eligible(true, true, true, false, false,
                false, true, true, false, false, 2, TensionState.WATCHED));
        assertFalse(eligible(true, true, true, false, false,
                false, true, true, false, true, 1, TensionState.WATCHED));
        assertFalse(eligible(true, true, true, false, false,
                false, true, true, false, true, 2, TensionState.UNEASE));
        assertFalse(eligible(true, true, true, false, false,
                false, true, true, false, true, 6, TensionState.FALSE_CALM));
        assertFalse(eligible(true, true, true, true, false,
                false, true, true, false, true, 2, TensionState.WATCHED));
        assertFalse(eligible(true, true, true, false, true,
                false, true, true, false, true, 2, TensionState.WATCHED));
        assertFalse(eligible(true, true, true, false, false,
                true, true, true, false, true, 2, TensionState.WATCHED));
        assertFalse(eligible(true, true, true, false, false,
                false, false, true, false, true, 2, TensionState.WATCHED));
        assertFalse(eligible(true, true, true, false, false,
                false, true, false, false, true, 2, TensionState.WATCHED));
        assertFalse(eligible(true, true, true, false, false,
                false, true, true, false, false, 2, TensionState.WATCHED));
        assertFalse(eligible(true, true, true, false, false,
                false, true, true, false, true, 2, null));
    }

    @Test
    void pacingScalesButRemainsBounded() {
        assertEquals(Integer.MAX_VALUE,
                PerceptionRules.spawnDenominator(36, 0.0f, 2.0f));
        assertEquals(36, PerceptionRules.spawnDenominator(36, 1.0f, 1.0f));
        assertTrue(PerceptionRules.spawnDenominator(36, 4.0f, 2.0f) < 36);
        assertTrue(PerceptionRules.cooldownTicks(240, 4.0f, 2.0f)
                >= 20L * 45L);
        assertEquals(20L * 1_800L * 2L,
                PerceptionRules.cooldownTicks(Integer.MAX_VALUE, Float.NaN, 2.0f));
        for (long value : new long[]{Long.MIN_VALUE, -1L, 0L, 1L, Long.MAX_VALUE}) {
            int lifetime = PerceptionRules.lifetimeTicks(value);
            assertTrue(lifetime >= PerceptionRules.MIN_LIFETIME_TICKS);
            assertTrue(lifetime <= PerceptionRules.MAX_LIFETIME_TICKS);
        }
    }

    private static boolean eligible(boolean perceptionsEnabled,
                                    boolean falseCompanionsEnabled,
                                    boolean alive, boolean spectator,
                                    boolean sleeping, boolean peaceful,
                                    boolean graceComplete,
                                    boolean automaticSceneAvailable,
                                    boolean ownerAlreadyBusy,
                                    boolean serverBudgetAvailable,
                                    int stage, TensionState tension) {
        return PerceptionRules.eligible(perceptionsEnabled, falseCompanionsEnabled,
                alive, spectator, sleeping, peaceful, graceComplete,
                automaticSceneAvailable, ownerAlreadyBusy, serverBudgetAvailable,
                stage, tension);
    }
}

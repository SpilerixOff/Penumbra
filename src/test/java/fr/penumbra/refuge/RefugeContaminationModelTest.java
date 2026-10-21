package fr.penumbra.refuge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefugeContaminationModelTest {

    @Test
    void progressionIsMonotonicAndCappedAtFour() {
        RefugeContaminationModel.State state = RefugeContaminationModel.State.initial();
        long now = 1_000L;
        for (int index = 0; index < 20; index++) {
            state = RefugeContaminationModel.afterSuccessfulEffect(state, now,
                    4, 0L);
            now = state.nextEligibleTick();
        }
        assertEquals(RefugeContaminationModel.Stage.IMPOSSIBLE, state.stage());
    }

    @Test
    void configuredStageAndIntervalAreHonored() {
        RefugeContaminationModel.State state = RefugeContaminationModel.State.initial();
        long now = 2_000L;
        for (int index = 0; index < 10; index++) {
            state = RefugeContaminationModel.afterSuccessfulEffect(
                    state, now, 2, 6_000L);
            assertTrue(state.nextEligibleTick() - now >= 6_000L);
            now = state.nextEligibleTick();
        }
        assertEquals(RefugeContaminationModel.Stage.DISTURBED, state.stage());
    }

    @Test
    void triggerRequiresProximityLoadedAreaBudgetAndExpiredCooldown() {
        RefugeContaminationModel.State state = new RefugeContaminationModel.State(
                RefugeContaminationModel.Stage.MARKED, 100L, 500L, 1, 0);
        assertFalse(RefugeContaminationModel.canTrigger(state, 499L, true, true, true));
        assertFalse(RefugeContaminationModel.canTrigger(state, 500L, false, true, true));
        assertFalse(RefugeContaminationModel.canTrigger(state, 500L, true, false, true));
        assertFalse(RefugeContaminationModel.canTrigger(state, 500L, true, true, false));
        assertTrue(RefugeContaminationModel.canTrigger(state, 500L, true, true, true));
    }

    @Test
    void cleanupNeverTouchesUnloadedOrChangedEntities() {
        assertEquals(RefugeContaminationModel.RestorationAction.WAIT_FOR_LOADED_CHUNK,
                RefugeContaminationModel.restorationAction(false, true, true, true));
        assertEquals(RefugeContaminationModel.RestorationAction.FORGET_MISSING_VISUAL,
                RefugeContaminationModel.restorationAction(true, false, false, false));
        assertEquals(RefugeContaminationModel.RestorationAction.LEAVE_CHANGED_VISUAL,
                RefugeContaminationModel.restorationAction(true, true, true, false));
        assertEquals(RefugeContaminationModel.RestorationAction.REMOVE_MATCHING_VISUAL,
                RefugeContaminationModel.restorationAction(true, true, true, true));
    }
}

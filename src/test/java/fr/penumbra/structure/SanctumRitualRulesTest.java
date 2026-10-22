package fr.penumbra.structure;

import fr.penumbra.story.StoryManager;
import fr.penumbra.story.StoryProgression;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SanctumRitualRulesTest {
    @Test
    void recognitionOfferingUnlocksTheDuelDuringTheSameRitual() {
        assertTrue(SanctumRitualRules.summonsConfrontation(5, false));
        var stage = new AtomicInteger(5);
        var count = new AtomicInteger();
        var completion = SanctumRitualRules.complete(stage.get(), false, () -> {
            count.incrementAndGet();
            stage.set(StoryProgression.nextStage(stage.get(),
                    Map.of(StoryManager.KEY_SANCTUM_RITUAL, count.get()), StoryManager.KEY_SANCTUM_RITUAL));
        }, () -> {
            assertEquals(6, stage.get(), "The summoner sees the unlocked stage on the first offering");
            return true;
        });
        assertEquals(SanctumRitualRules.Completion.SUMMONED, completion);
        assertEquals(1, count.get());
    }

    @Test
    void bothRecognitionAndFailedFinalAttemptsRequireACameraBeforeSpendingTheSigil() {
        for (int stage : new int[]{5, 6}) {
            assertTrue(SanctumRitualRules.needsCamera(stage, false, false));
            assertFalse(SanctumRitualRules.needsCamera(stage, false, true));
            assertFalse(SanctumRitualRules.needsCamera(stage, true, false));
        }
    }

    @Test
    void earlyRitualsAndVictorsStillReceiveTheOrdinaryRitual() {
        for (int stage = 0; stage < 5; stage++) {
            assertFalse(SanctumRitualRules.summonsConfrontation(stage, false));
            assertFalse(SanctumRitualRules.needsCamera(stage, false, false));
        }
        assertFalse(SanctumRitualRules.summonsConfrontation(6, true));
    }

    @Test
    void failedSummonNeverFallsBackToOrdinaryCalmAndCanBeRetried() {
        var count = new AtomicInteger();
        assertEquals(SanctumRitualRules.Completion.NO_SPACE,
                SanctumRitualRules.complete(6, false, count::incrementAndGet, () -> false));
        assertEquals(1, count.get());
        assertTrue(SanctumRitualRules.summonsConfrontation(6, false));
    }

    @Test
    void ordinaryCompletionRecordsOneRitualWithoutCallingTheSummoner() {
        var count = new AtomicInteger();
        assertEquals(SanctumRitualRules.Completion.ORDINARY,
                SanctumRitualRules.complete(4, false, count::incrementAndGet,
                        () -> { fail("Early rituals must not summon"); return false; }));
        assertEquals(SanctumRitualRules.Completion.ORDINARY,
                SanctumRitualRules.complete(6, true, count::incrementAndGet,
                        () -> { fail("A previous victor must not summon again"); return false; }));
        assertEquals(2, count.get());
    }
}

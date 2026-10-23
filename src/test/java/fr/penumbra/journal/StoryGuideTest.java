package fr.penumbra.journal;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StoryGuideTest {
    @Test
    void eachStageShowsOnlyItsOwnObjective() {
        for (int stage = 0; stage <= 6; stage++) {
            StoryGuide.Snapshot state = state(stage, false, true, 0, false, 0);
            List<String> keys = keys(state);
            assertTrue(keys.contains("journal.penumbra.guide.stage" + stage + ".body"));
            for (int other = 0; other <= 6; other++) {
                if (other == stage) continue;
                int hidden = other;
                assertTrue(keys.stream().noneMatch(key -> key.startsWith(
                        "journal.penumbra.guide.stage" + hidden + ".")));
            }
            assertEquals("journal.penumbra.guide.stage" + stage + ".title",
                    StoryGuide.subtitleKey(state));
        }
    }

    @Test
    void refugeNotesSeparateRestFromTheMissingVisitor() {
        StoryGuide.Snapshot newcomer = state(2, false, true, 1, false, 0);
        assertTrue(keys(newcomer).contains("journal.penumbra.guide.stage2.visitor_missing"));
        assertEquals(List.of(1), StoryGuide.lines(newcomer).stream()
                .filter(line -> line.key().endsWith("stage2.beds")).findFirst().orElseThrow().arguments());
        assertTrue(keys(state(2, false, true, 2, true, 0))
                .contains("journal.penumbra.guide.stage2.visitor_seen"));
    }

    @Test
    void disabledCopiesKeepTheShiftedHouseRouteWithoutPromisingAPersonalCopy() {
        List<String> keys = keys(state(3, false, false, 0, false, 0));
        assertTrue(keys.contains("journal.penumbra.guide.stage3.body"));
        assertTrue(keys.contains("journal.penumbra.guide.stage3.no_copies"));
        assertFalse(keys.contains("journal.penumbra.guide.stage3.copy"));
    }

    @Test
    void finalStageIsNotMistakenForVictory() {
        assertTrue(keys(state(6, false, true, 2, true, 4))
                .contains("journal.penumbra.guide.stage6.flash"));
        List<String> won = keys(state(6, true, true, 2, true, 4));
        assertTrue(won.contains("journal.penumbra.guide.won.continue"));
        assertTrue(won.stream().noneMatch(key -> key.contains("stage6")));
    }

    @Test
    void disabledStoryDoesNotSuggestAnUnreachableObjective() {
        StoryGuide.Snapshot disabled = new StoryGuide.Snapshot(false, 5, false, true, 2, true, 4);
        assertEquals(List.of("journal.penumbra.guide.disabled.body",
                "journal.penumbra.guide.disabled.notes", "journal.penumbra.guide.personal"), keys(disabled));
        assertEquals("journal.penumbra.guide.disabled.title", StoryGuide.subtitleKey(disabled));
    }

    @Test
    void snapshotValuesAndOutputAreBoundedAndImmutable() {
        StoryGuide.Snapshot state = state(99, false, true, -8, false, Integer.MAX_VALUE);
        assertEquals(6, state.stage());
        assertEquals(0, state.bedUses());
        assertEquals(4, state.hostObservations());
        assertThrows(UnsupportedOperationException.class, () -> StoryGuide.lines(state).clear());
    }

    @Test
    void bookTrailAlwaysPointsAtTheFirstMissingVolumeEvenWhenFoundOutOfOrder() {
        for (int mask = 0; mask < 15; mask++) {
            StoryGuide.Snapshot state = investigation(3, false, mask, false);
            List<String> keys = keys(state);
            int expectedBook = 1;
            while ((mask & (1 << (expectedBook - 1))) != 0) expectedBook++;
            assertTrue(keys.contains("journal.penumbra.guide.investigation.book" + expectedBook));
            for (int other = 1; other <= 4; other++) {
                if (other != expectedBook) {
                    assertFalse(keys.contains("journal.penumbra.guide.investigation.book" + other));
                }
            }
            assertEquals(List.of(Integer.bitCount(mask)), StoryGuide.lines(state).stream()
                    .filter(line -> line.key().endsWith("investigation.count"))
                    .findFirst().orElseThrow().arguments());
            assertTrue(keys.contains("journal.penumbra.guide.investigation.compass"));
            assertTrue(keys.contains("journal.penumbra.guide.investigation.recovery"));
            assertFalse(keys.contains("journal.penumbra.guide.investigation.ready"));
            assertFalse(keys.contains("journal.penumbra.guide.stage3.body"));
        }
    }

    @Test
    void fourBooksStillRequireListeningToTheCassetteBeforeTheFinalObjective() {
        List<String> waiting = keys(investigation(5, false, 15, false));
        assertTrue(waiting.contains("journal.penumbra.guide.investigation.tape"));
        assertFalse(waiting.contains("journal.penumbra.guide.investigation.ready"));
        List<String> ready = keys(investigation(5, false, 15, true));
        assertTrue(ready.contains("journal.penumbra.guide.investigation.ready"));
        assertFalse(ready.contains("journal.penumbra.guide.investigation.tape"));
        assertTrue(ready.contains("journal.penumbra.guide.investigation.recovery"));
    }

    @Test
    void confrontationAndVictoryKeepTheirSpecificInstructionsWithTheBookTrailEnabled() {
        List<String> confrontation = keys(investigation(6, false, 15, true));
        assertTrue(confrontation.contains("journal.penumbra.guide.stage6.focus"));
        assertTrue(confrontation.contains("journal.penumbra.guide.stage6.flash"));
        assertTrue(confrontation.stream().noneMatch(key -> key.contains(".investigation.")));
        List<String> victory = keys(investigation(6, true, 15, true));
        assertTrue(victory.contains("journal.penumbra.guide.won.continue"));
        assertTrue(victory.stream().noneMatch(key -> key.contains(".investigation.") || key.contains("stage6")));
    }

    @Test
    void disabledStorySuppressesBookObjectivesAndVolumeCountersAreBounded() {
        StoryGuide.Snapshot disabled = new StoryGuide.Snapshot(false, 2, false, true, 0, false, 0,
                true, 15, true);
        assertTrue(keys(disabled).stream().noneMatch(key -> key.contains(".investigation.")));
        assertEquals(0, investigation(0, false, -1, false).volumesMask());
        assertEquals(15, investigation(0, false, Integer.MAX_VALUE, false).volumesMask());
        assertFalse(state(0, false, true, 0, false, 0).investigationEnabled(),
                "The legacy constructor preserves the original story guide");
    }

    private static StoryGuide.Snapshot investigation(int stage, boolean won, int mask, boolean heard) {
        return new StoryGuide.Snapshot(true, stage, won, true, 2, true, 4, true, mask, heard);
    }

    private static StoryGuide.Snapshot state(int stage, boolean won, boolean copies,
                                             int beds, boolean visitor, int observations) {
        return new StoryGuide.Snapshot(true, stage, won, copies, beds, visitor, observations);
    }

    private static List<String> keys(StoryGuide.Snapshot state) {
        return StoryGuide.lines(state).stream().map(StoryGuide.Line::key).toList();
    }
}

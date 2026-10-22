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

    private static StoryGuide.Snapshot state(int stage, boolean won, boolean copies,
                                             int beds, boolean visitor, int observations) {
        return new StoryGuide.Snapshot(true, stage, won, copies, beds, visitor, observations);
    }

    private static List<String> keys(StoryGuide.Snapshot state) {
        return StoryGuide.lines(state).stream().map(StoryGuide.Line::key).toList();
    }
}

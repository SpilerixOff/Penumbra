package fr.penumbra.client.journal;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JournalLayoutTest {
    @Test
    void bookTextAndNavigationFitMinimumGuiAndLargerWindows() {
        for (int[] viewport : new int[][] {{320, 180}, {320, 240}, {427, 240},
                {640, 360}, {854, 480}, {1920, 1080}}) {
            JournalLayout.Spread layout = JournalLayout.fit(viewport[0], viewport[1]);
            JournalLayout.Rect screen = new JournalLayout.Rect(0, 0, viewport[0], viewport[1]);
            assertInside(layout.book(), screen);
            assertInside(layout.leftPaper(), layout.book());
            assertInside(layout.rightPaper(), layout.book());
            assertInside(layout.leftBody(), layout.leftPaper());
            assertInside(layout.rightBody(), layout.rightPaper());
            assertInside(layout.footer(), layout.book());
            assertInside(layout.previous(), layout.leftPaper());
            assertInside(layout.next(), layout.rightPaper());
            assertTrue(layout.leftPaper().right() < layout.rightPaper().x());
            assertTrue(layout.leftBody().bottom() < layout.footer().y());
            assertTrue(layout.rightBody().bottom() < layout.footer().y());
            assertTrue(layout.previous().right() < layout.footer().x());
            assertTrue(layout.footer().right() < layout.next().x());
            assertTrue(layout.rightBody().height() >= 7 * 13,
                    "The minimum GUI must still fit seven handwritten lines");
            assertTrue(layout.rightBody().width() >= 110);
        }
    }

    @Test
    void aParagraphLongerThanASheetRemainsEntirelyReadable() {
        List<List<JournalLayout.Line>> pages = JournalLayout.paginate(List.of(101), 26, 13, 3);
        assertEquals(51, pages.size());
        assertPreservedAndBounded(List.of(101), pages, 26, 13);
    }

    @Test
    void shortParagraphMovesWholeToNextSheet() {
        List<List<JournalLayout.Line>> pages = JournalLayout.paginate(List.of(3, 4), 50, 10, 3);
        assertEquals(2, pages.size());
        assertTrue(pages.get(0).stream().allMatch(line -> line.paragraph() == 0));
        assertTrue(pages.get(1).stream().allMatch(line -> line.paragraph() == 1));
        assertPreservedAndBounded(List.of(3, 4), pages, 50, 10);
    }

    @Test
    void exactFitDoesNotCreateExtraSheetAndGapDoesNotConsumeFirstLine() {
        assertEquals(1, JournalLayout.paginate(List.of(3, 0), 30, 10, 3).size());
        List<List<JournalLayout.Line>> pages = JournalLayout.paginate(List.of(1, 1), 20, 10, 3);
        assertEquals(2, pages.size());
        assertEquals(0, pages.get(1).getFirst().y());
        assertPreservedAndBounded(List.of(1, 1), pages, 20, 10);
    }

    @Test
    void allCluesSurviveDifferentFontsHeightsAndParagraphLengths() {
        Random random = new Random(0x5E1EC7L);
        for (int sample = 0; sample < 250; sample++) {
            List<Integer> counts = new ArrayList<>();
            for (int paragraph = 0; paragraph < 1 + random.nextInt(8); paragraph++) {
                counts.add(random.nextInt(60));
            }
            int advance = 9 + random.nextInt(8);
            int height = advance * (1 + random.nextInt(20)) + random.nextInt(advance);
            List<List<JournalLayout.Line>> pages = JournalLayout.paginate(
                    counts, height, advance, random.nextInt(10));
            assertPreservedAndBounded(counts, pages, height, advance);
        }
    }

    @Test
    void emptyEntryStillHasOneSheetAndImpossibleLineHeightFailsFast() {
        assertEquals(List.of(List.of()), JournalLayout.paginate(List.of(0, 0), 100, 10, 3));
        assertThrows(IllegalArgumentException.class,
                () -> JournalLayout.paginate(List.of(2), 8, 9, 3));
        assertThrows(IllegalArgumentException.class,
                () -> JournalLayout.paginate(List.of(2), 100, 0, 3));
        assertThrows(IllegalArgumentException.class,
                () -> JournalLayout.paginate(List.of(-1), 100, 10, 3));
    }

    private static void assertInside(JournalLayout.Rect inner, JournalLayout.Rect outer) {
        assertTrue(inner.width() > 0 && inner.height() > 0);
        assertTrue(inner.x() >= outer.x() && inner.y() >= outer.y());
        assertTrue(inner.right() <= outer.right() && inner.bottom() <= outer.bottom(),
                inner + " must fit in " + outer);
    }

    private static void assertPreservedAndBounded(List<Integer> counts,
                                                List<List<JournalLayout.Line>> pages,
                                                int height, int advance) {
        List<String> expected = new ArrayList<>();
        for (int paragraph = 0; paragraph < counts.size(); paragraph++) {
            for (int line = 0; line < counts.get(paragraph); line++) {
                expected.add(paragraph + ":" + line);
            }
        }
        List<String> actual = new ArrayList<>();
        for (List<JournalLayout.Line> page : pages) {
            if (!expected.isEmpty()) {
                assertFalse(page.isEmpty(), "No empty intermediate sheets");
                assertEquals(0, page.getFirst().y(), "No paragraph gap at the top of a sheet");
            }
            int previousBottom = 0;
            for (JournalLayout.Line line : page) {
                assertTrue(line.y() >= previousBottom, "Lines must never overlap");
                assertTrue(line.y() + advance <= height, "No line may reach the footer");
                previousBottom = line.y() + advance;
                actual.add(line.paragraph() + ":" + line.index());
            }
        }
        assertEquals(expected, actual, "Every source line must appear once, in reading order");
    }
}

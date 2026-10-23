package fr.penumbra.client.journal;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JournalCraftingLayoutTest {
    @Test
    void allNineIngredientsOutputAndCaptionFitEvenTheMinimumGui() {
        for (int[] viewport : new int[][] {{320, 180}, {320, 240}, {427, 240},
                {640, 360}, {854, 480}, {1920, 1080}}) {
            JournalLayout.Spread layout = JournalLayout.fit(viewport[0], viewport[1]);
            JournalCraftingLayout.Diagram diagram = JournalCraftingLayout.fit(layout.leftBody());
            assertEquals(9, diagram.ingredients().size());
            List<JournalLayout.Rect> elements = new ArrayList<>(diagram.ingredients());
            elements.add(diagram.result());
            elements.add(diagram.arrow());
            elements.add(diagram.caption());
            for (JournalLayout.Rect element : elements) {
                assertInside(element, layout.leftBody());
            }
            assertTrue(diagram.caption().height() >= 30, "Names retain at least three lines");
            for (int first = 0; first < elements.size(); first++) {
                for (int second = first + 1; second < elements.size(); second++) {
                    assertFalse(overlap(elements.get(first), elements.get(second)),
                            "Slots, arrow and caption must never overlap");
                }
            }
            for (JournalLayout.Rect slot : diagram.ingredients()) {
                assertTrue(slot.width() >= 18 && slot.height() >= 18,
                        "Vanilla 16-pixel items retain their slot border");
            }
        }
    }

    @Test
    void quickLinksFitAboveTheCounterWithoutTouchingPageArrows() {
        for (int[] viewport : new int[][] {{320, 180}, {427, 240}, {640, 360}}) {
            JournalLayout.Spread layout = JournalLayout.fit(viewport[0], viewport[1]);
            JournalLayout.Rect investigation = JournalCraftingLayout.investigationTab(layout.footer());
            JournalLayout.Rect crafting = JournalCraftingLayout.craftingTab(layout.footer());
            assertInside(investigation, layout.footer());
            assertInside(crafting, layout.footer());
            assertFalse(overlap(investigation, crafting));
            assertFalse(overlap(investigation, layout.previous()));
            assertFalse(overlap(crafting, layout.next()));
            assertTrue(crafting.bottom() <= layout.footer().bottom() - 11);
            assertTrue(investigation.width() >= 80 && crafting.width() >= 80);
        }
    }

    private static boolean overlap(JournalLayout.Rect a, JournalLayout.Rect b) {
        return a.x() < b.right() && b.x() < a.right() && a.y() < b.bottom() && b.y() < a.bottom();
    }

    private static void assertInside(JournalLayout.Rect inner, JournalLayout.Rect outer) {
        assertTrue(inner.width() > 0 && inner.height() > 0);
        assertTrue(inner.x() >= outer.x() && inner.y() >= outer.y());
        assertTrue(inner.right() <= outer.right() && inner.bottom() <= outer.bottom(),
                inner + " must fit in " + outer);
    }
}

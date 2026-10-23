package fr.penumbra.client.journal;

import java.util.ArrayList;
import java.util.List;

/** The recipe grid and its output always fit the minimum 320 × 180 journal spread. */
final class JournalCraftingLayout {
    private static final int SLOT = 18;

    private JournalCraftingLayout() {
    }

    record Diagram(List<JournalLayout.Rect> ingredients, JournalLayout.Rect result,
                   JournalLayout.Rect arrow, JournalLayout.Rect caption) {
    }

    static Diagram fit(JournalLayout.Rect body) {
        int gridX = body.x() + (body.width() - 94) / 2;
        // Reserve the entire area above the grid for a wrapped item name.
        int gridY = body.bottom() - 3 * SLOT - 3;
        List<JournalLayout.Rect> slots = new ArrayList<>();
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                slots.add(new JournalLayout.Rect(gridX + column * SLOT,
                        gridY + row * SLOT, SLOT, SLOT));
            }
        }
        return new Diagram(List.copyOf(slots),
                new JournalLayout.Rect(gridX + 76, gridY + SLOT, SLOT, SLOT),
                new JournalLayout.Rect(gridX + 58, gridY + SLOT + 5, 13, 8),
                new JournalLayout.Rect(body.x(), body.y(), body.width(),
                        Math.max(1, gridY - body.y() - 4)));
    }

    static JournalLayout.Rect investigationTab(JournalLayout.Rect footer) {
        return new JournalLayout.Rect(footer.x(), footer.y(), footer.width() / 2 - 2, 11);
    }

    static JournalLayout.Rect craftingTab(JournalLayout.Rect footer) {
        return new JournalLayout.Rect(footer.centerX() + 2, footer.y(),
                footer.width() - footer.width() / 2 - 2, 11);
    }
}

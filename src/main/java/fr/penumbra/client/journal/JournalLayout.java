package fr.penumbra.client.journal;

import java.util.ArrayList;
import java.util.List;

/** Geometry and pagination without a client or a font, so their bounds can be tested. */
final class JournalLayout {
    private JournalLayout() {
    }

    record Rect(int x, int y, int width, int height) {
        int right() { return x + width; }
        int bottom() { return y + height; }
        int centerX() { return x + width / 2; }

        boolean contains(double pointX, double pointY) {
            return pointX >= x && pointX < right() && pointY >= y && pointY < bottom();
        }
    }

    record Spread(Rect book, Rect leftPaper, Rect rightPaper, Rect leftBody,
                  Rect rightBody, Rect footer, Rect previous, Rect next) {
    }

    /** Fits the usual 320 x 180 minimum GUI as well as larger GUI scales. */
    static Spread fit(int viewportWidth, int viewportHeight) {
        int width = Math.max(1, Math.min(420, viewportWidth - 16));
        int height = Math.max(1, Math.min(268, viewportHeight - 16));
        Rect book = new Rect((viewportWidth - width) / 2,
                (viewportHeight - height) / 2, width, height);
        int edge = Math.min(10, width / 12);
        int seam = Math.min(6, width / 30);
        int inset = Math.min(10, width / 24);
        int middle = book.x() + width / 2;
        Rect leftPaper = new Rect(book.x() + edge, book.y() + edge,
                middle - seam - book.x() - edge, height - 2 * edge);
        Rect rightPaper = new Rect(middle + seam, book.y() + edge,
                book.right() - edge - middle - seam, height - 2 * edge);
        int footerHeight = Math.min(24, height / 4);
        int footerY = book.bottom() - edge - footerHeight;
        int bodyY = book.y() + Math.min(22, height / 6);
        int bodyHeight = footerY - Math.min(8, height / 20) - bodyY;
        Rect leftBody = new Rect(leftPaper.x() + inset, bodyY,
                leftPaper.width() - 2 * inset, bodyHeight);
        Rect rightBody = new Rect(rightPaper.x() + inset, bodyY,
                rightPaper.width() - 2 * inset, bodyHeight);
        Rect previous = new Rect(book.x() + edge, footerY, 24, footerHeight);
        Rect next = new Rect(book.right() - edge - 24, footerY, 24, footerHeight);
        Rect footer = new Rect(previous.right() + 6, footerY,
                next.x() - previous.right() - 12, footerHeight);
        return new Spread(book, leftPaper, rightPaper, leftBody, rightBody,
                footer, previous, next);
    }

    /** A wrapped source line and its vertical position on a sheet. */
    record Line(int paragraph, int index, int y) {
    }

    /**
     * Keeps a paragraph together when it fits on a fresh sheet, but splits any
     * longer paragraph. No source line is dropped, even when only one fits.
     */
    static List<List<Line>> paginate(List<Integer> paragraphLines, int availableHeight,
                                     int lineAdvance, int paragraphGap) {
        if (lineAdvance <= 0 || availableHeight < lineAdvance || paragraphGap < 0) {
            throw new IllegalArgumentException("A journal sheet must fit at least one line");
        }
        List<List<Line>> pages = new ArrayList<>();
        List<Line> lines = new ArrayList<>();
        int used = 0;
        for (int paragraph = 0; paragraph < paragraphLines.size(); paragraph++) {
            int count = paragraphLines.get(paragraph);
            if (count < 0) {
                throw new IllegalArgumentException("Negative paragraph line count");
            }
            if (count == 0) {
                continue;
            }
            if (!lines.isEmpty()) {
                long paragraphHeight = (long) count * lineAdvance;
                if (used + paragraphGap + lineAdvance > availableHeight
                        || (paragraphHeight <= availableHeight
                        && used + paragraphGap + paragraphHeight > availableHeight)) {
                    pages.add(List.copyOf(lines));
                    lines.clear();
                    used = 0;
                } else {
                    used += paragraphGap;
                }
            }
            for (int index = 0; index < count; index++) {
                if (used + lineAdvance > availableHeight) {
                    pages.add(List.copyOf(lines));
                    lines.clear();
                    used = 0;
                }
                lines.add(new Line(paragraph, index, used));
                used += lineAdvance;
            }
        }
        if (!lines.isEmpty() || pages.isEmpty()) {
            pages.add(List.copyOf(lines));
        }
        return List.copyOf(pages);
    }
}

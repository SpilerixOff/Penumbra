package fr.penumbra.client.journal;

import fr.penumbra.PenumbraMod;
import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.journal.JournalEntry;
import fr.penumbra.registry.PenumbraSounds;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Le Relevé : papier vieilli, portraits à l'encre et feuillets mesurés à la police active. */
@Environment(EnvType.CLIENT)
public final class SurveyJournalScreen extends Screen {
    private static final Identifier SPREAD = PenumbraMod.id("textures/gui/journal_spread.png");
    private static final Identifier PORTRAITS = PenumbraMod.id("textures/gui/journal_portraits.png");
    private static final int PORTRAIT_GRID = 4;
    private static final int PORTRAIT_SIZE = 96;
    private static final int INK = 0xFF2E2418;
    private static final int INK_SOFT = 0xFF554431;
    private static final int INK_FADED = 0xFF73654F;
    private static final int INK_RED = 0xFF6E2A1E;
    private static final int PAPER_LEFT = 0xFFE5D4AF;
    private static final int PAPER_RIGHT = 0xFFEADDBF;
    private static final int PAPER_SHADE = 0x224A3A24;
    private static final int PARAGRAPH_GAP = 3;
    private static final FontDescription.Resource INK_FONT =
            new FontDescription.Resource(PenumbraMod.id("ink"));

    private final Map<JournalEntry, JournalEntry.State> states;
    private final int progress;
    private final int total;
    private final RandomSource smudgeRandom = RandomSource.create(0x5E1EC7L);
    private final List<Sheet> sheets = new ArrayList<>();
    private JournalLayout.Spread layout;
    private int page;
    private double scrollRemainder;

    public SurveyJournalScreen(Set<String> readableIds, int progress, int total) {
        super(Component.translatable("screen.penumbra.journal.title"));
        this.progress = Math.max(0, progress);
        this.total = Math.max(1, total);
        this.states = new EnumMap<>(JournalEntry.class);
        for (JournalEntry entry : JournalEntry.ORDER) {
            this.states.put(entry, readableIds.contains(entry.id())
                    ? JournalEntry.State.KNOWN : entry.defaultState());
        }
    }

    /** Une police manuscrite peut être fournie par un pack ; sinon, écriture vanilla nette. */
    private static MutableComponent ink(Component text) {
        MutableComponent copy = text.copy();
        if (PenumbraConfig.get().journalHandwrittenFont) {
            return copy.withStyle(style -> style.withFont(INK_FONT));
        }
        return copy;
    }

    private record Paragraph(Component text, int color) {
    }

    private record BodyLine(FormattedCharSequence text, int color, JournalLayout.Line position) {
    }

    private record Sheet(JournalEntry entry, List<BodyLine> lines, boolean continued,
                         boolean lastForEntry) {
    }

    @Override
    protected void init() {
        Sheet previous = this.sheets.isEmpty() ? null : sheet();
        this.layout = JournalLayout.fit(this.width, this.height);
        paginate();
        // Une nouvelle taille change le nombre de feuillets. Revenir au même
        // paragraphe évite de sauter à une autre créature après le redimensionnement.
        this.page = 0;
        if (previous != null) {
            int paragraph = previous.lines().isEmpty() ? 0
                    : previous.lines().getFirst().position().paragraph();
            for (int index = 0; index < this.sheets.size(); index++) {
                Sheet candidate = this.sheets.get(index);
                if (candidate.entry() == previous.entry()
                        && (candidate.lines().isEmpty() || candidate.lines().stream()
                        .anyMatch(line -> line.position().paragraph() == paragraph))) {
                    this.page = index;
                    break;
                }
            }
        }
        this.scrollRemainder = 0;
    }

    private void paginate() {
        this.sheets.clear();
        for (JournalEntry entry : JournalEntry.ORDER) {
            List<Paragraph> paragraphs = paragraphs(entry);
            List<List<FormattedCharSequence>> wrapped = paragraphs.stream()
                    .map(paragraph -> this.font.split(paragraph.text(),
                            this.layout.rightBody().width() - 4)).toList();
            List<List<JournalLayout.Line>> pages = JournalLayout.paginate(
                    wrapped.stream().map(List::size).toList(),
                    this.layout.rightBody().height(), lineAdvance(), PARAGRAPH_GAP);
            for (int index = 0; index < pages.size(); index++) {
                List<BodyLine> lines = pages.get(index).stream().map(position -> new BodyLine(
                        wrapped.get(position.paragraph()).get(position.index()),
                        paragraphs.get(position.paragraph()).color(), position)).toList();
                this.sheets.add(new Sheet(entry, lines, index > 0, index == pages.size() - 1));
            }
        }
    }

    /** Les indices des pages arrachées sont paginés aussi : ils ne doivent jamais disparaître. */
    private List<Paragraph> paragraphs(JournalEntry entry) {
        List<Paragraph> result = new ArrayList<>();
        switch (this.states.getOrDefault(entry, entry.defaultState())) {
            case KNOWN -> {
                for (int index = 0; index < entry.lineCount(); index++) {
                    result.add(new Paragraph(ink(Component.translatable(entry.lineKey(index))), INK));
                }
            }
            case FADED -> {
                result.add(new Paragraph(ink(Component.translatable(entry.fadedKey())), INK_SOFT));
                result.add(new Paragraph(ink(Component.translatable("screen.penumbra.journal.faded")),
                        INK_FADED));
            }
            case TORN -> {
                result.add(new Paragraph(ink(Component.translatable("screen.penumbra.journal.torn_body")),
                        INK_SOFT));
                result.add(new Paragraph(Component.translatable("screen.penumbra.journal.hint_label"),
                        INK_RED));
                result.add(new Paragraph(ink(Component.translatable(entry.hintKey())), INK_SOFT));
            }
        }
        return result;
    }

    private int lineAdvance() {
        return this.font.lineHeight + (PenumbraConfig.get().journalHandwrittenFont ? 4 : 1);
    }

    private Sheet sheet() {
        return this.sheets.get(Math.max(0, Math.min(this.sheets.size() - 1, this.page)));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                   float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xE0080709);
        JournalLayout.Rect book = this.layout.book();
        graphics.blit(SPREAD, book.x(), book.y(), book.right(), book.bottom(),
                0.0f, 1.0f, 0.0f, 1.0f);
        // La texture garde les bords usés et le pli. Sous l'écriture, le papier
        // est opaque : aucune hachure ne peut traverser les lettres.
        drawPaper(graphics, this.layout.leftPaper(), PAPER_LEFT);
        drawPaper(graphics, this.layout.rightPaper(), PAPER_RIGHT);
        Sheet sheet = sheet();
        JournalEntry.State state = this.states.getOrDefault(sheet.entry(), sheet.entry().defaultState());
        drawLeftPage(graphics, sheet, state, mouseX, mouseY);
        drawRightPage(graphics, sheet, state);
        drawFooter(graphics, mouseX, mouseY);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void drawPaper(GuiGraphicsExtractor graphics, JournalLayout.Rect paper, int color) {
        graphics.fill(paper.x(), paper.y(), paper.right(), paper.bottom(), color);
        graphics.outline(paper.x(), paper.y(), paper.width(), paper.height(), PAPER_SHADE);
        graphics.fill(paper.x() + 1, paper.y() + 1, paper.x() + 3, paper.bottom() - 1,
                0x14664D2C);
    }

    private void drawLeftPage(GuiGraphicsExtractor graphics, Sheet sheet, JournalEntry.State state,
                              int mouseX, int mouseY) {
        JournalLayout.Rect body = this.layout.leftBody();
        Component title = state == JournalEntry.State.TORN
                ? Component.translatable("screen.penumbra.journal.torn_title")
                : Component.translatable(sheet.entry().titleKey());
        Component subtitle = state == JournalEntry.State.TORN ? Component.empty()
                : ink(Component.translatable(sheet.continued()
                ? "screen.penumbra.journal.continued" : sheet.entry().subtitleKey()));
        List<FormattedCharSequence> titleLines = this.font.split(title, body.width() - 4);
        List<FormattedCharSequence> subtitleLines = state == JournalEntry.State.TORN ? List.of()
                : this.font.split(subtitle, body.width() - 4);
        int captionHeight = titleLines.size() * this.font.lineHeight
                + (subtitleLines.isEmpty() ? 0 : 6 + subtitleLines.size() * lineAdvance());
        int portraitSize = Math.max(0, Math.min(PORTRAIT_SIZE,
                Math.min(body.width() - 4, body.height() - captionHeight - 12)));
        if (portraitSize < 24) {
            portraitSize = 0;
        }
        int portraitLeft = body.centerX() - portraitSize / 2;
        if (portraitSize > 0) {
            if (state == JournalEntry.State.TORN) {
                drawTornStub(graphics, portraitLeft, body.y(), portraitSize);
            } else {
                float cell = 1.0f / PORTRAIT_GRID;
                int column = sheet.entry().portraitIndex() % PORTRAIT_GRID;
                int row = sheet.entry().portraitIndex() / PORTRAIT_GRID;
                graphics.blit(PORTRAITS, portraitLeft, body.y(),
                        portraitLeft + portraitSize, body.y() + portraitSize,
                        column * cell, (column + 1) * cell, row * cell, (row + 1) * cell);
                if (state == JournalEntry.State.FADED) {
                    graphics.fill(portraitLeft, body.y(), portraitLeft + portraitSize,
                            body.y() + portraitSize, 0xC4D9CBAC);
                    drawCenteredText(graphics, Component.literal("?"), body.centerX(),
                            body.y() + (portraitSize - this.font.lineHeight) / 2, INK_FADED);
                }
                graphics.outline(portraitLeft - 1, body.y() - 1,
                        portraitSize + 2, portraitSize + 2, PAPER_SHADE);
            }
        }
        int titleY = body.y() + (portraitSize > 0 ? portraitSize + 12 : 0);
        graphics.enableScissor(body.x(), body.y(), body.right(), body.bottom());
        int subtitleY = drawCenteredLines(graphics, titleLines, body, titleY,
                this.font.lineHeight, state == JournalEntry.State.KNOWN ? INK : INK_SOFT);
        if (!subtitleLines.isEmpty()) {
            if (subtitleY + 6 < body.bottom()) {
                int ruleHalfWidth = Math.min(46, body.width() / 2 - 2);
                graphics.fill(body.centerX() - ruleHalfWidth, subtitleY + 2,
                        body.centerX() + ruleHalfWidth, subtitleY + 3, PAPER_SHADE);
            }
            drawCenteredLines(graphics, subtitleLines, body, subtitleY + 6,
                    lineAdvance(), INK_SOFT);
        }
        graphics.disableScissor();
        // Un pack peut fournir un titre exceptionnellement long : le survol
        // conserve l'accès au cartouche entier, sans empiéter sur le texte.
        if (captionHeight > body.height() && body.contains(mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(title.copy().append("\n").append(subtitle), mouseX, mouseY);
        }
    }

    private int drawCenteredLines(GuiGraphicsExtractor graphics, List<FormattedCharSequence> lines,
                                  JournalLayout.Rect bounds, int y, int advance, int color) {
        int capacity = Math.max(0, (bounds.bottom() - y) / advance);
        for (int index = 0; index < Math.min(lines.size(), capacity); index++) {
            if (index == capacity - 1 && lines.size() > capacity) {
                drawCenteredText(graphics, Component.literal("…"), bounds.centerX(), y, color);
            } else {
                FormattedCharSequence line = lines.get(index);
                graphics.text(this.font, line, bounds.centerX() - this.font.width(line) / 2,
                        y, color, false);
            }
            y += advance;
        }
        return y;
    }

    /** Les overloads sans booléen ajoutent une ombre, qui brouille l'encre sur papier clair. */
    private void drawCenteredText(GuiGraphicsExtractor graphics, Component text,
                                  int centerX, int y, int color) {
        graphics.text(this.font, text, centerX - this.font.width(text) / 2, y, color, false);
    }

    private void drawRightPage(GuiGraphicsExtractor graphics, Sheet sheet, JournalEntry.State state) {
        JournalLayout.Rect body = this.layout.rightBody();
        graphics.enableScissor(body.x(), body.y(), body.right(), body.bottom());
        int end = body.y();
        for (BodyLine line : sheet.lines()) {
            int y = body.y() + line.position().y();
            graphics.text(this.font, line.text(), body.x() + 2, y, line.color(), false);
            end = y + lineAdvance();
        }
        if (state == JournalEntry.State.FADED && sheet.lastForEntry()) {
            drawSmudges(graphics, body.x() + 2, end + 8, body.width() - 4, body.bottom());
        }
        graphics.disableScissor();
    }

    /** Le lavis est déterministe : les taches ne bougent pas sous les yeux du lecteur. */
    private void drawSmudges(GuiGraphicsExtractor graphics, int x, int y, int width, int bottom) {
        this.smudgeRandom.setSeed(0x5E1EC7L + sheet().entry().ordinal() * 977L);
        for (int row = 0; row < 7; row++) {
            int rowY = y + row * (this.font.lineHeight + 2);
            if (rowY + 4 > bottom) {
                return;
            }
            int cursor = x;
            int limit = x + width - this.smudgeRandom.nextInt(Math.min(24, Math.max(1, width)));
            while (cursor < limit) {
                int run = 4 + this.smudgeRandom.nextInt(18);
                int height = 1 + this.smudgeRandom.nextInt(2);
                int alpha = 0x30 + this.smudgeRandom.nextInt(0x28);
                graphics.fill(cursor, rowY + 2, Math.min(limit, cursor + run),
                        rowY + 2 + height, (alpha << 24) | 0x6B5C44);
                cursor += run + 2 + this.smudgeRandom.nextInt(6);
            }
        }
    }

    private void drawTornStub(GuiGraphicsExtractor graphics, int x, int y, int size) {
        this.smudgeRandom.setSeed(0x70A2L);
        for (int row = 0; row < size; row++) {
            int jag = 6 + this.smudgeRandom.nextInt(9);
            graphics.fill(x + size - jag, y + row, x + size, y + row + 1, 0x33534227);
        }
        graphics.fill(x + size - 2, y, x + size, y + size, 0x554A3A24);
    }

    private void drawFooter(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        JournalLayout.Rect footer = this.layout.footer();
        graphics.fill(footer.x() - 2, footer.y(), footer.right() + 2, footer.bottom(), PAPER_RIGHT);
        Component label = Component.translatable("screen.penumbra.journal.footer",
                this.page + 1, this.sheets.size(), this.progress, this.total);
        List<FormattedCharSequence> lines = this.font.split(label, footer.width() - 4);
        int advance = this.font.lineHeight + 1;
        if (lines.size() * advance > footer.height()) {
            lines = this.font.split(Component.literal((this.page + 1) + " / " + this.sheets.size()
                    + "  ·  " + this.progress + " / " + this.total), footer.width() - 4);
        }
        drawCenteredLines(graphics, lines, footer,
                footer.y() + Math.max(0, (footer.height() - lines.size() * advance) / 2),
                advance, INK_SOFT);
        if (this.page > 0) {
            drawArrow(graphics, this.layout.previous(), false,
                    this.layout.previous().contains(mouseX, mouseY));
        }
        if (this.page < this.sheets.size() - 1) {
            drawArrow(graphics, this.layout.next(), true, this.layout.next().contains(mouseX, mouseY));
        }
    }

    private void drawArrow(GuiGraphicsExtractor graphics, JournalLayout.Rect bounds,
                           boolean forward, boolean hovered) {
        if (hovered) {
            graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), PAPER_SHADE);
        }
        int x = bounds.centerX() - 2;
        int y = bounds.y() + (bounds.height() - 9) / 2;
        for (int step = 0; step < 5; step++) {
            int columnX = forward ? x + 4 - step : x + step;
            graphics.fill(columnX, y + 4 - step, columnX + 1, y + 5 + step,
                    hovered ? INK_RED : INK_SOFT);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (event.button() != 0) {
            return false;
        }
        if (this.layout.previous().contains(event.x(), event.y())) {
            return turn(-1);
        }
        if (this.layout.next().contains(event.x(), event.y())) {
            return turn(1);
        }
        if (this.layout.book().contains(event.x(), event.y())) {
            return turn(event.x() >= this.layout.book().centerX() ? 1 : -1);
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.layout.book().contains(mouseX, mouseY) && scrollY != 0) {
            this.scrollRemainder = Math.clamp(this.scrollRemainder + scrollY, -1.0, 1.0);
            if (Math.abs(this.scrollRemainder) >= 1.0) {
                turn(this.scrollRemainder < 0 ? 1 : -1);
                this.scrollRemainder = 0;
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return switch (event.key()) {
            case 263, 65, 81, 266 -> turn(-1); // Gauche, A, Q, page précédente.
            case 262, 68, 267 -> turn(1);      // Droite, D, page suivante.
            case 268 -> turn(-this.page);     // Début.
            case 269 -> turn(this.sheets.size() - 1 - this.page); // Fin.
            default -> super.keyPressed(event);
        };
    }

    private boolean turn(int delta) {
        int next = this.page + delta;
        if (next == this.page || next < 0 || next >= this.sheets.size()) {
            return false;
        }
        this.page = next;
        this.scrollRemainder = 0;
        Minecraft client = Minecraft.getInstance();
        if (client.level != null && client.player != null) {
            client.level.playLocalSound(client.player.getX(), client.player.getY(),
                    client.player.getZ(), PenumbraSounds.PAGE_TURN, SoundSource.PLAYERS,
                    0.55f, 0.94f + client.player.getRandom().nextFloat() * 0.12f, false);
        }
        return true;
    }

    /** Le monde continue de tourner pendant la lecture. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public List<JournalEntry> pages() {
        return new ArrayList<>(JournalEntry.ORDER);
    }
}

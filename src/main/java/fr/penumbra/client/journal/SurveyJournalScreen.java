package fr.penumbra.client.journal;

import fr.penumbra.PenumbraMod;
import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.journal.JournalEntry;
import fr.penumbra.registry.PenumbraSounds;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
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

/**
 * Le Relevé (0.18.0) — double page de parchemin, écriture à l'encre.
 *
 * <p>Trois rendus selon l'état de la page :</p>
 * <ul>
 *   <li><b>Lisible</b> : portrait à l'encre, titre, sous-titre daté, et le texte
 *       complet en italique brun sur le feuillet de droite.</li>
 *   <li><b>Délavée</b> : le portrait est noyé sous un lavis, une seule ligne
 *       reste lisible, et le reste est dessiné en <em>taches d'encre diluée</em>
 *       plutôt qu'en faux caractères — l'œil comprend « illisible » sans
 *       chercher à déchiffrer.</li>
 *   <li><b>Arrachée</b> : il ne reste qu'un talon dentelé au niveau de la
 *       reliure, et l'indice que l'ancien avait noté sur la page d'en face.</li>
 * </ul>
 *
 * <p>Aucun nouvel accès API : uniquement {@code fill}, {@code blit},
 * {@code text}, {@code centeredText} et {@code outline} de
 * {@code GuiGraphicsExtractor}, déjà utilisés par {@code PhotoAlbumScreen}.</p>
 */
@Environment(EnvType.CLIENT)
public final class SurveyJournalScreen extends Screen {

    private static final Identifier SPREAD =
            PenumbraMod.id("textures/gui/journal_spread.png");
    private static final Identifier PORTRAITS =
            PenumbraMod.id("textures/gui/journal_portraits.png");

    /** Dimensions du livre à l'écran, et de l'atlas de portraits. */
    private static final int BOOK_WIDTH = 348;
    private static final int BOOK_HEIGHT = 224;
    private static final int PORTRAIT_GRID = 4;
    private static final int PORTRAIT_SIZE = 96;

    // ——— Encres ———
    private static final int INK = 0xFF2E2418;
    private static final int INK_SOFT = 0xFF554431;
    private static final int INK_FADED = 0xFF9A8A70;
    private static final int INK_RED = 0xFF6E2A1E;
    private static final int PAPER_SHADE = 0x224A3A24;
    /** Blanc entre deux paragraphes d'Elias. */
    private static final int PARAGRAPH_GAP = 3;

    private final Map<JournalEntry, JournalEntry.State> states;
    private final int progress;
    private final int total;
    private final RandomSource smudgeRandom = RandomSource.create(0x5E1EC7L);

    private int page;
    private int left;
    private int top;

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


    /**
     * Identifiant de la police manuscrite optionnelle. Le mod ne livre aucun
     * fichier TTF : si {@code journalHandwrittenFont} est actif, il faut que
     * {@code assets/penumbra/font/ink.json} et le TTF qu'il référence aient été
     * déposés par le joueur ou par un pack de ressources.
     */
    private static final FontDescription.Resource INK_FONT =
            new FontDescription.Resource(PenumbraMod.id("ink"));

    /**
     * Passe un texte à l'encre. Sans police manuscrite installée, on retombe sur
     * l'italique vanilla — c'est le rendu par défaut du carnet, et il tient tout
     * seul. Tout le texte de l'écran passe par ici : changer de police est donc
     * une modification d'une seule ligne.
     */
    private static MutableComponent ink(Component text) {
        MutableComponent copy = text.copy();
        if (PenumbraConfig.get().journalHandwrittenFont) {
            return copy.withStyle(style -> style.withFont(INK_FONT));
        }
        return copy.withStyle(ChatFormatting.ITALIC);
    }

    /**
     * Un feuillet affiché : une entrée, et la tranche de ses paragraphes qui
     * tient réellement sur la page de droite.
     *
     * <p>C'est le cœur de la mise en page. Les entrées d'Elias font jusqu'à six
     * paragraphes de trois lignes : elles ne tiennent pas sur une page, et les
     * tronquer couperait justement les lignes de contre-mesure, qui sont
     * toujours les dernières. Le carnet se feuillette donc comme un vrai livre —
     * une entrée longue occupe deux ou trois feuillets.</p>
     */
    private record Sheet(JournalEntry entry, int firstParagraph, int paragraphCount,
                         boolean continued) {
    }

    private final List<Sheet> sheets = new ArrayList<>();

    @Override
    protected void init() {
        this.left = (this.width - BOOK_WIDTH) / 2;
        this.top = (this.height - BOOK_HEIGHT) / 2;
        // La police n'est disponible qu'ici : c'est le premier moment où l'on
        // peut mesurer le texte, donc le premier où la pagination est possible.
        paginate();
        this.page = Math.max(0, Math.min(this.sheets.size() - 1, this.page));
    }

    /** Découpe chaque entrée en autant de feuillets que nécessaire. */
    private void paginate() {
        this.sheets.clear();
        int available = textBottom() - textTop();
        for (JournalEntry entry : JournalEntry.ORDER) {
            JournalEntry.State state = this.states.getOrDefault(entry, entry.defaultState());
            if (state != JournalEntry.State.KNOWN || entry.lineCount() == 0) {
                // Une page délavée ou arrachée n'a qu'un feuillet, par construction.
                this.sheets.add(new Sheet(entry, 0, 0, false));
                continue;
            }
            int first = 0;
            int used = 0;
            int count = 0;
            for (int index = 0; index < entry.lineCount(); index++) {
                int height = paragraphHeight(entry, index);
                if (count > 0 && used + height > available) {
                    this.sheets.add(new Sheet(entry, first, count, first > 0));
                    first = index;
                    used = 0;
                    count = 0;
                }
                used += height;
                count++;
            }
            this.sheets.add(new Sheet(entry, first, Math.max(1, count), first > 0));
        }
    }

    /** Hauteur réellement occupée par un paragraphe, espacement compris. */
    private int paragraphHeight(JournalEntry entry, int index) {
        Component line = ink(Component.translatable(entry.lineKey(index)));
        return this.font.split(line, textWidth()).size() * lineAdvance() + PARAGRAPH_GAP;
    }

    private int textLeft() {
        return this.left + BOOK_WIDTH / 2 + 16;
    }

    private int textWidth() {
        return BOOK_WIDTH / 2 - 34;
    }

    private int textTop() {
        return this.top + 30;
    }

    private int textBottom() {
        return this.top + BOOK_HEIGHT - 44;
    }

    /**
     * Interligne. Une police TTF manuscrite dépasse largement les neuf pixels de
     * la police vanilla : sans marge supplémentaire, ses jambages mordraient sur
     * la ligne suivante.
     */
    private int lineAdvance() {
        return this.font.lineHeight
                + (PenumbraConfig.get().journalHandwrittenFont ? 4 : 1);
    }

    private Sheet sheet() {
        if (this.sheets.isEmpty()) {
            return new Sheet(JournalEntry.FRONTISPIECE, 0, 0, false);
        }
        return this.sheets.get(
                Math.max(0, Math.min(this.sheets.size() - 1, this.page)));
    }

    private JournalEntry current() {
        return sheet().entry();
    }

    private JournalEntry.State state() {
        return this.states.getOrDefault(current(), current().defaultState());
    }

    // ===================== Rendu =====================

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                   float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xE0080709);
        graphics.blit(SPREAD, this.left, this.top,
                this.left + BOOK_WIDTH, this.top + BOOK_HEIGHT, 0.0f, 1.0f, 0.0f, 1.0f);

        JournalEntry entry = current();
        JournalEntry.State state = state();
        drawLeftPage(graphics, entry, state);
        drawRightPage(graphics, entry, state);
        drawFooter(graphics, mouseX, mouseY);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    /** Feuillet gauche : le portrait à l'encre et son cartouche. */
    private void drawLeftPage(GuiGraphicsExtractor graphics, JournalEntry entry,
                              JournalEntry.State state) {
        int centerX = this.left + BOOK_WIDTH / 4;
        int portraitLeft = centerX - PORTRAIT_SIZE / 2;
        int portraitTop = this.top + 30;

        if (state == JournalEntry.State.TORN) {
            drawTornStub(graphics, portraitLeft, portraitTop);
        } else {
            float cell = 1.0f / PORTRAIT_GRID;
            int column = entry.portraitIndex() % PORTRAIT_GRID;
            int row = entry.portraitIndex() / PORTRAIT_GRID;
            graphics.blit(PORTRAITS, portraitLeft, portraitTop,
                    portraitLeft + PORTRAIT_SIZE, portraitTop + PORTRAIT_SIZE,
                    column * cell, (column + 1) * cell,
                    row * cell, (row + 1) * cell);
            if (state == JournalEntry.State.FADED) {
                // Lavis : le portrait a coulé, il ne reste qu'une masse.
                graphics.fill(portraitLeft, portraitTop,
                        portraitLeft + PORTRAIT_SIZE, portraitTop + PORTRAIT_SIZE, 0xC4D9CBAC);
                graphics.centeredText(this.font, Component.literal("?"),
                        centerX, portraitTop + PORTRAIT_SIZE / 2 - this.font.lineHeight / 2,
                        INK_FADED);
            }
            graphics.outline(portraitLeft - 2, portraitTop - 2,
                    PORTRAIT_SIZE + 4, PORTRAIT_SIZE + 4, PAPER_SHADE);
        }

        int titleY = portraitTop + PORTRAIT_SIZE + 12;
        Component title = state == JournalEntry.State.TORN
                ? Component.translatable("screen.penumbra.journal.torn_title")
                : Component.translatable(entry.titleKey());
        graphics.centeredText(this.font, title, centerX, titleY,
                state == JournalEntry.State.KNOWN ? INK : INK_FADED);
        // Trait de plume sous le titre, jamais parfaitement droit.
        graphics.fill(centerX - 46, titleY + 11, centerX + 43, titleY + 12, PAPER_SHADE);

        if (state != JournalEntry.State.TORN) {
            Component subtitle = ink(sheet().continued()
                    ? Component.translatable("screen.penumbra.journal.continued")
                    : Component.translatable(entry.subtitleKey()));
            graphics.centeredText(this.font, subtitle, centerX, titleY + 17,
                    state == JournalEntry.State.KNOWN ? INK_SOFT : INK_FADED);
        }
    }

    /** Feuillet droit : le corps du texte, ou ce qu'il en reste. */
    private void drawRightPage(GuiGraphicsExtractor graphics, JournalEntry entry,
                               JournalEntry.State state) {
        int textLeft = textLeft();
        int textWidth = textWidth();
        int y = textTop();

        switch (state) {
            case KNOWN -> {
                Sheet sheet = sheet();
                int last = Math.min(entry.lineCount(),
                        sheet.firstParagraph() + sheet.paragraphCount());
                for (int index = sheet.firstParagraph(); index < last; index++) {
                    Component line = ink(Component.translatable(entry.lineKey(index)));
                    y = drawWrapped(graphics, line, textLeft, y, textWidth, INK);
                    y += PARAGRAPH_GAP;
                }
            }
            case FADED -> {
                Component line = ink(Component.translatable(entry.fadedKey()));
                y = drawWrapped(graphics, line, textLeft, y, textWidth, INK_SOFT) + 8;
                drawSmudges(graphics, textLeft, y, textWidth, 7);
                graphics.text(this.font,
                        ink(Component.translatable("screen.penumbra.journal.faded")),
                        textLeft, this.top + BOOK_HEIGHT - 52, INK_FADED);
            }
            case TORN -> {
                Component line = ink(Component.translatable("screen.penumbra.journal.torn_body"));
                y = drawWrapped(graphics, line, textLeft, y, textWidth, INK_SOFT) + 10;
                // L'indice que l'ancien avait griffonné sur la page d'en face,
                // et qui a donc survécu à l'arrachage.
                graphics.text(this.font,
                        Component.translatable("screen.penumbra.journal.hint_label"),
                        textLeft, y, INK_RED);
                y += this.font.lineHeight + 3;
                drawWrapped(graphics, ink(Component.translatable(entry.hintKey())),
                        textLeft, y, textWidth, INK_SOFT);
            }
        }
    }

    /** Écrit un paragraphe avec retour à la ligne, et retourne le y suivant. */
    private int drawWrapped(GuiGraphicsExtractor graphics, Component text,
                            int x, int y, int width, int color) {
        List<FormattedCharSequence> lines = this.font.split(text, width);
        for (FormattedCharSequence line : lines) {
            graphics.text(this.font, line, x, y, color);
            y += lineAdvance();
        }
        return y;
    }

    /**
     * Taches d'encre diluée à la place du texte perdu. Le tirage est déterministe
     * (graine fixe) : la page délavée est <em>toujours</em> délavée de la même
     * manière, sinon l'œil verrait le papier bouger d'une frame à l'autre.
     */
    private void drawSmudges(GuiGraphicsExtractor graphics, int x, int y, int width, int rows) {
        this.smudgeRandom.setSeed(0x5E1EC7L + current().ordinal() * 977L);
        for (int row = 0; row < rows; row++) {
            int rowY = y + row * (this.font.lineHeight + 2);
            if (rowY > this.top + BOOK_HEIGHT - 60) {
                return;
            }
            int cursor = x;
            int limit = x + width - this.smudgeRandom.nextInt(24);
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

    /** Talon dentelé d'une page arrachée, contre la reliure. */
    private void drawTornStub(GuiGraphicsExtractor graphics, int x, int y) {
        this.smudgeRandom.setSeed(0x70A2L);
        for (int row = 0; row < PORTRAIT_SIZE; row++) {
            int jag = 6 + this.smudgeRandom.nextInt(9);
            graphics.fill(x + PORTRAIT_SIZE - jag, y + row,
                    x + PORTRAIT_SIZE, y + row + 1, 0x33534227);
        }
        graphics.fill(x + PORTRAIT_SIZE - 2, y, x + PORTRAIT_SIZE, y + PORTRAIT_SIZE, 0x554A3A24);
    }

    /** Pied de page : numéro, avancement et flèches. */
    private void drawFooter(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int footerY = this.top + BOOK_HEIGHT - 26;
        graphics.centeredText(this.font,
                Component.translatable("screen.penumbra.journal.footer",
                        this.page + 1, Math.max(1, this.sheets.size()),
                        this.progress, this.total),
                this.left + BOOK_WIDTH / 2, footerY, INK_SOFT);

        if (this.page > 0) {
            drawArrow(graphics, this.left + 22, footerY - 1, false,
                    isOverArrow(mouseX, mouseY, true));
        }
        if (this.page < this.sheets.size() - 1) {
            drawArrow(graphics, this.left + BOOK_WIDTH - 30, footerY - 1, true,
                    isOverArrow(mouseX, mouseY, false));
        }
    }

    private void drawArrow(GuiGraphicsExtractor graphics, int x, int y,
                           boolean forward, boolean hovered) {
        int color = hovered ? INK_RED : INK_SOFT;
        for (int step = 0; step < 5; step++) {
            int height = 1 + step * 2;
            int columnX = forward ? x + step : x + 4 - step;
            graphics.fill(columnX, y + 4 - step, columnX + 1, y + 4 - step + height, color);
        }
    }

    private boolean isOverArrow(int mouseX, int mouseY, boolean previous) {
        int footerY = this.top + BOOK_HEIGHT - 27;
        int x = previous ? this.left + 18 : this.left + BOOK_WIDTH - 34;
        return mouseX >= x && mouseX <= x + 16 && mouseY >= footerY && mouseY <= footerY + 14;
    }

    // ===================== Interaction =====================

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (event.button() != 0) {
            return false;
        }
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();
        if (isOverArrow(mouseX, mouseY, true)) {
            return turn(-1);
        }
        if (isOverArrow(mouseX, mouseY, false)) {
            return turn(1);
        }
        // Cliquer dans la moitié droite avance, dans la moitié gauche recule :
        // c'est le geste que tout le monde essaie sur un livre ouvert.
        if (mouseY > this.top && mouseY < this.top + BOOK_HEIGHT
                && mouseX > this.left && mouseX < this.left + BOOK_WIDTH) {
            return turn(mouseX > this.left + BOOK_WIDTH / 2 ? 1 : -1);
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == 263 || key == 65) {           // flèche gauche, Q/A
            return turn(-1);
        }
        if (key == 262 || key == 68) {           // flèche droite, D
            return turn(1);
        }
        return super.keyPressed(event);
    }

    private boolean turn(int delta) {
        int next = this.page + delta;
        if (next < 0 || next >= this.sheets.size()) {
            return false;
        }
        this.page = next;
        Minecraft client = Minecraft.getInstance();
        if (client.level != null && client.player != null) {
            client.level.playLocalSound(client.player.getX(), client.player.getY(),
                    client.player.getZ(), PenumbraSounds.PAGE_TURN, SoundSource.PLAYERS,
                    0.55f, 0.94f + client.player.getRandom().nextFloat() * 0.12f, false);
        }
        return true;
    }

    /** Le carnet se lit posément : le monde continue de tourner derrière. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Liste ordonnée des pages, exposée pour les tests d'intégration. */
    public List<JournalEntry> pages() {
        return new ArrayList<>(JournalEntry.ORDER);
    }
}

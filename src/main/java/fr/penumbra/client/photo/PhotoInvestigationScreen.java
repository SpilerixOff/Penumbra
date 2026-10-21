package fr.penumbra.client.photo;

import fr.penumbra.photo.PhotoEvidence;
import fr.penumbra.photo.investigation.InvestigationClue;
import fr.penumbra.photo.investigation.InvestigationLead;
import fr.penumbra.photo.investigation.InvestigationRules;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/** Lecture du dossier personnel reçu du serveur, jamais reconstitué à partir de photos publiques. */
public final class PhotoInvestigationScreen extends Screen {

    private final Screen parent;
    private final List<PhotoEvidence> discovered;
    private List<Sheet> sheets = List.of();
    private List<FormattedCharSequence> body = List.of();
    private List<FormattedCharSequence> heading = List.of();
    private int page;
    private int scroll;
    private int bodyTop;
    private int visibleLines;
    private int deductions;
    private Button previousButton;
    private Button nextButton;

    public PhotoInvestigationScreen(Screen parent, List<PhotoEvidence> discovered) {
        super(Component.translatable("screen.penumbra.dossier.title"));
        this.parent = parent;
        this.discovered = PhotoEvidence.normalizeDossier(discovered);
    }

    @Override
    protected void init() {
        List<Sheet> content = new ArrayList<>();
        content.add(new Sheet(Component.translatable("screen.penumbra.dossier.method"), List.of(
                Component.translatable("screen.penumbra.dossier.first_steps",
                        minecraft.options.keyAttack.getTranslatedKeyMessage(),
                        minecraft.options.keyUse.getTranslatedKeyMessage()),
                Component.translatable("screen.penumbra.dossier.method_body"),
                Component.translatable("screen.penumbra.dossier.personal"))));
        List<InvestigationClue> confirmed = InvestigationRules.unlockedClues(discovered);
        deductions = confirmed.size();
        for (InvestigationClue clue : confirmed) {
            content.add(new Sheet(Component.translatable(clue.translationKey()), List.of(
                    Component.translatable("screen.penumbra.dossier.deduction"),
                    evidenceNames(clue.requiredEvidence().stream().sorted().toList()),
                    Component.translatable(clue.counterplay().translationKey()))));
        }
        for (InvestigationLead lead : InvestigationRules.partialLeads(discovered)) {
            content.add(new Sheet(Component.translatable("screen.penumbra.dossier.lead"), List.of(
                    Component.translatable("screen.penumbra.dossier.based_on",
                            Component.translatable(lead.observed().translationKey())),
                    Component.translatable(lead.translationKey()),
                    Component.translatable("screen.penumbra.dossier.unconfirmed"))));
        }
        for (PhotoEvidence evidence : discovered) {
            content.add(new Sheet(Component.translatable(evidence.translationKey()), List.of(
                    Component.translatable("screen.penumbra.dossier.observation"),
                    Component.translatable("dossier.penumbra.observation." + evidence.id()))));
        }
        sheets = List.copyOf(content);
        page = Math.min(page, sheets.size() - 1);
        int center = width / 2;
        previousButton = addRenderableWidget(Button.builder(Component.literal("‹"),
                button -> changePage(-1)).bounds(center - 104, height - 26, 28, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.penumbra.dossier.back"),
                button -> onClose()).bounds(center - 70, height - 26, 140, 20).build());
        nextButton = addRenderableWidget(Button.builder(Component.literal("›"),
                button -> changePage(1)).bounds(center + 76, height - 26, 28, 20).build());
        preparePage();
    }

    private static Component evidenceNames(List<PhotoEvidence> evidence) {
        var names = Component.empty();
        for (PhotoEvidence value : evidence) {
            if (!names.getString().isEmpty()) {
                names.append(" + ");
            }
            names.append(Component.translatable(value.translationKey()));
        }
        return names;
    }

    private int textWidth() {
        return Math.max(80, Math.min(460, width - 40));
    }

    private void preparePage() {
        Sheet sheet = sheets.get(page);
        heading = font.split(sheet.heading(), textWidth());
        bodyTop = 44 + heading.size() * (font.lineHeight + 2) + 8;
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (Component paragraph : sheet.paragraphs()) {
            if (!lines.isEmpty()) {
                lines.add(FormattedCharSequence.EMPTY);
            }
            lines.addAll(font.split(paragraph, textWidth()));
        }
        body = List.copyOf(lines);
        visibleLines = Math.max(1, (height - 52 - bodyTop) / (font.lineHeight + 3));
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        previousButton.active = page > 0;
        nextButton.active = page + 1 < sheets.size();
    }

    private int maxScroll() {
        return Math.max(0, body.size() - visibleLines);
    }

    private void changePage(int direction) {
        int next = Math.max(0, Math.min(sheets.size() - 1, page + direction));
        if (next != page) {
            page = next;
            scroll = 0;
            preparePage();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0.0 && maxScroll() > 0) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll + (scrollY < 0.0 ? 3 : -3)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        switch (event.key()) {
            case 263 -> changePage(-1); // Left.
            case 262 -> changePage(1);  // Right.
            case 265 -> scroll = Math.max(0, scroll - 1); // Up.
            case 264 -> scroll = Math.min(maxScroll(), scroll + 1); // Down.
            case 266 -> scroll = Math.max(0, scroll - visibleLines); // Page Up.
            case 267 -> scroll = Math.min(maxScroll(), scroll + visibleLines); // Page Down.
            case 268 -> scroll = 0; // Home.
            case 269 -> scroll = maxScroll(); // End.
            default -> {
                return super.keyPressed(event);
            }
        }
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                   float partialTick) {
        graphics.fill(0, 0, width, height, 0xF20A0B0D);
        graphics.centeredText(font, title, width / 2, 8, 0xFFE6D7B8);
        graphics.centeredText(font, Component.translatable("screen.penumbra.dossier.progress",
                discovered.size(), deductions),
                width / 2, 25, 0xFF9C968A);
        int headingY = 44;
        for (FormattedCharSequence line : heading) {
            graphics.text(font, line, (width - font.width(line)) / 2,
                    headingY, 0xFFE3C98E, false);
            headingY += font.lineHeight + 2;
        }
        int textLeft = (width - textWidth()) / 2;
        for (int index = 0; index < visibleLines && scroll + index < body.size(); index++) {
            graphics.text(font, body.get(scroll + index), textLeft,
                    bodyTop + index * (font.lineHeight + 3), 0xFFD4CEC2, false);
        }
        Component footer = Component.translatable("screen.penumbra.album.page", page + 1, sheets.size());
        if (maxScroll() > 0) {
            footer = Component.translatable("screen.penumbra.dossier.scroll", footer,
                    scroll + 1, Math.min(body.size(), scroll + visibleLines), body.size());
        }
        graphics.centeredText(font, footer, width / 2, height - 42, 0xFF9C968A);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record Sheet(Component heading, List<Component> paragraphs) {
    }
}

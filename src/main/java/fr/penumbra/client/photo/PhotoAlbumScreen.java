package fr.penumbra.client.photo;

import fr.penumbra.network.PhotoNetworking;
import fr.penumbra.photo.PhotoAlbumState;
import fr.penumbra.photo.PhotoData;
import fr.penumbra.photo.PhotoEvidence;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Galerie paginée de la caméra. Seules les six vignettes visibles sont chargées. */
public final class PhotoAlbumScreen extends Screen {

    private static final int COLUMNS = 3;
    private static final int ROWS = 2;
    private static final int PAGE_SIZE = COLUMNS * ROWS;
    private static final long DELETE_CONFIRM_MS = 3_000L;
    private static final long PAGE_RETRY_MS = 1_500L;
    private static final int MAX_PAGE_ATTEMPTS = 3;

    private byte scope = PhotoNetworking.ALBUM_SCOPE_OWNED;
    private int page;
    private int totalEntries;
    private int dossierStage;
    private int dossierDiscovered;
    private List<PhotoEvidence> dossierEvidence = List.of();
    private List<PhotoNetworking.AlbumEntry> entries = List.of();
    private UUID selectedId;
    private boolean loading = true;
    private long deleteArmedUntil;
    private Component status = Component.empty();
    private final Set<UUID> dossierRefreshes = new HashSet<>();
    private long lastPageRequestMillis = Long.MIN_VALUE;
    private int pageRequestAttempts;
    private boolean pageTimedOut;

    private Button ownedTab;
    private Button publicTab;
    private Button dossierButton;
    private Button previousButton;
    private Button nextButton;
    private Button viewButton;
    private Button visibilityButton;
    private Button deleteButton;
    private Button importantButton;
    private Button renameButton;
    private EditBox renameBox;

    public PhotoAlbumScreen() {
        super(Component.translatable("screen.penumbra.album.title"));
    }

    @Override
    protected void init() {
        int center = width / 2;
        int tabWidth = Math.max(42, (Math.min(420, width - 12) - 86) / 3);
        int toolbarLeft = center - (tabWidth * 3 + 86) / 2;
        ownedTab = addRenderableWidget(Button.builder(
                Component.translatable("screen.penumbra.album.mine"), button -> switchScope(
                        PhotoNetworking.ALBUM_SCOPE_OWNED))
                .bounds(toolbarLeft, 14, tabWidth, 20).build());
        publicTab = addRenderableWidget(Button.builder(
                Component.translatable("screen.penumbra.album.public"), button -> switchScope(
                        PhotoNetworking.ALBUM_SCOPE_PUBLIC))
                .bounds(toolbarLeft + tabWidth + 4, 14, tabWidth, 20).build());
        dossierButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.penumbra.dossier.tab"), button -> {
                    if (minecraft != null && !loading) {
                        minecraft.setScreen(new PhotoInvestigationScreen(this, dossierEvidence));
                    }
                }).bounds(toolbarLeft + (tabWidth + 4) * 2, 14, tabWidth, 20).build());
        int navigationLeft = toolbarLeft + (tabWidth + 4) * 3;
        previousButton = addRenderableWidget(Button.builder(Component.literal("‹"),
                button -> changePage(-1)).bounds(navigationLeft, 14, 22, 20).build());
        nextButton = addRenderableWidget(Button.builder(Component.literal("›"),
                button -> changePage(1)).bounds(navigationLeft + 26, 14, 22, 20).build());
        addRenderableWidget(Button.builder(Component.literal("×"), button -> onClose())
                .bounds(navigationLeft + 52, 14, 22, 20).build());

        int editY = Math.max(36, height - 50);
        int editWidth = Math.max(80, Math.min(190, width - 112));
        renameBox = addRenderableWidget(new EditBox(font, 10, editY, editWidth, 20,
                Component.translatable("screen.penumbra.album.rename")));
        renameBox.setMaxLength(PhotoAlbumState.MAX_TITLE_LENGTH);
        renameBox.setHint(Component.translatable("screen.penumbra.album.rename_hint"));
        renameButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.penumbra.album.rename"), button -> renameSelected())
                .bounds(14 + editWidth, editY, 82, 20).build());

        int actionY = Math.max(58, height - 26);
        int actionWidth = Math.max(58, Math.min(88, (width - 34) / 4));
        int startX = Math.max(4, (width - (actionWidth * 4 + 12)) / 2);
        viewButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.penumbra.album.view"), button -> openSelected())
                .bounds(startX, actionY, actionWidth, 20).build());
        visibilityButton = addRenderableWidget(Button.builder(Component.empty(),
                button -> toggleVisibility())
                .bounds(startX + actionWidth + 4, actionY, actionWidth, 20).build());
        importantButton = addRenderableWidget(Button.builder(Component.empty(),
                button -> toggleImportant())
                .bounds(startX + (actionWidth + 4) * 2, actionY, actionWidth, 20).build());
        deleteButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.penumbra.album.delete"), button -> deleteSelected())
                .bounds(startX + (actionWidth + 4) * 3, actionY, actionWidth, 20).build());

        updateWidgets();
        requestPage();
    }

    @Override
    public void tick() {
        super.tick();
        long now = Util.getMillis();
        if (deleteArmedUntil != 0L && now > deleteArmedUntil) {
            deleteArmedUntil = 0L;
            updateWidgets();
        }
        if (loading && lastPageRequestMillis != Long.MIN_VALUE
                && now >= lastPageRequestMillis
                && now - lastPageRequestMillis >= PAGE_RETRY_MS) {
            if (pageRequestAttempts < MAX_PAGE_ATTEMPTS) {
                sendPageRequest(true);
            } else {
                loading = false;
                status = Component.translatable("screen.penumbra.album.timeout");
                pageTimedOut = true;
                updateWidgets();
            }
        }
        if (!loading && scope == PhotoNetworking.ALBUM_SCOPE_OWNED) {
            boolean newlyDeveloped = false;
            for (PhotoNetworking.AlbumEntry entry : entries) {
                if (PhotoClientStore.progress(entry.photoId(), entry.capturedAtTick()) >= 1.0f) {
                    PhotoClientStore.ensureAnalysisRequested(entry.photoId());
                    if (dossierRefreshes.add(entry.photoId())) {
                        newlyDeveloped = true;
                    }
                }
            }
            if (newlyDeveloped) {
                requestPage();
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                   float partialTick) {
        graphics.fill(0, 0, width, height, 0xF20A0B0D);
        graphics.centeredText(font, title, width / 2, 2, 0xFFE6D7B8);
        int totalPages = Math.max(1, (totalEntries + PAGE_SIZE - 1) / PAGE_SIZE);
        Component stage = Component.translatable(
                "screen.penumbra.album.dossier_stage." + dossierStage);
        graphics.centeredText(font, Component.translatable("screen.penumbra.album.header",
                        stage, dossierDiscovered, PhotoEvidence.discoverableCount(),
                        page + 1, totalPages), width / 2, 37, 0xFF9C968A);

        Grid grid = grid();
        if (loading && entries.isEmpty()) {
            graphics.centeredText(font, Component.translatable("screen.penumbra.album.loading"),
                    width / 2, grid.top + grid.height / 2, 0xFFB8B2A4);
        } else if (entries.isEmpty()) {
            graphics.centeredText(font, Component.translatable("screen.penumbra.album.empty"),
                    width / 2, grid.top + grid.height / 2, 0xFFB8B2A4);
        }

        for (int index = 0; index < entries.size(); index++) {
            drawEntry(graphics, entries.get(index), index, grid);
        }
        if (!status.getString().isEmpty()) {
            graphics.centeredText(font, Component.literal(font.plainSubstrByWidth(
                            status.getString(), Math.max(20, width - 20))),
                    width / 2, Math.max(36, height - 63), 0xFFC6BDA9);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void drawEntry(GuiGraphicsExtractor graphics, PhotoNetworking.AlbumEntry entry,
                           int index, Grid grid) {
        Card card = card(index, grid);
        boolean selected = entry.photoId().equals(selectedId);
        graphics.fill(card.left, card.top, card.right(), card.bottom(),
                selected ? 0xFF554735 : 0xFF272527);
        graphics.outline(card.left, card.top, card.width, card.height,
                selected ? 0xFFE3C98E : 0xFF6D665C);

        int availableImageHeight = Math.max(1, card.height - font.lineHeight - 9);
        int imageWidth = Math.max(1, Math.min(card.width - 8,
                availableImageHeight * PhotoAlbumState.WIDTH / PhotoAlbumState.HEIGHT));
        int imageHeight = Math.max(1,
                imageWidth * PhotoAlbumState.HEIGHT / PhotoAlbumState.WIDTH);
        int imageLeft = card.left + (card.width - imageWidth) / 2;
        int imageTop = card.top + 4;
        Identifier texture = PhotoClientStore.texture(entry.photoId());
        if (texture == null) {
            graphics.fill(imageLeft, imageTop, imageLeft + imageWidth, imageTop + imageHeight,
                    0xFF020304);
            if (imageHeight >= font.lineHeight) {
                graphics.centeredText(font, Component.literal("…"),
                        imageLeft + imageWidth / 2,
                        imageTop + imageHeight / 2 - font.lineHeight / 2, 0xFF8F8B83);
            }
        } else {
            graphics.blit(texture, imageLeft, imageTop,
                    imageLeft + imageWidth, imageTop + imageHeight,
                    0.0f, 1.0f, 0.0f, 1.0f);
        }
        if (texture != null && imageHeight >= font.lineHeight + 3) {
            Component analysis = analysisLabel(entry);
            String clippedAnalysis = font.plainSubstrByWidth(
                    analysis.getString(), Math.max(10, imageWidth - 4));
            int analysisY = imageTop + imageHeight - font.lineHeight - 1;
            graphics.fill(imageLeft, analysisY - 1,
                    imageLeft + imageWidth, imageTop + imageHeight, 0xB0000000);
            graphics.centeredText(font, Component.literal(clippedAnalysis),
                    imageLeft + imageWidth / 2, analysisY, 0xFFE5D3A7);
        }
        if (entry.important()) {
            graphics.fill(card.right() - 13, card.top + 2, card.right() - 3,
                    card.top + 14, 0xDD5A3B12);
            graphics.centeredText(font, Component.literal("!"), card.right() - 8,
                    card.top + 3, 0xFFFFD56A);
        }
        String titleText = entry.title().isBlank()
                ? Component.translatable("screen.penumbra.album.untitled").getString()
                : entry.title();
        String clipped = font.plainSubstrByWidth(titleText, Math.max(10, card.width - 10));
        graphics.centeredText(font, Component.literal(clipped),
                card.left + card.width / 2, card.bottom() - font.lineHeight - 3,
                entry.publicVisible() ? 0xFFD4BE88 : 0xFFC7C2B9);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (event.button() != 0) {
            return false;
        }
        Grid grid = grid();
        for (int index = 0; index < entries.size(); index++) {
            Card card = card(index, grid);
            if (event.x() >= card.left && event.x() < card.right()
                    && event.y() >= card.top && event.y() < card.bottom()) {
                PhotoNetworking.AlbumEntry entry = entries.get(index);
                boolean wasSelected = entry.photoId().equals(selectedId);
                selectedId = entry.photoId();
                deleteArmedUntil = 0L;
                renameBox.setValue(entry.title());
                updateWidgets();
                if (doubleClick && wasSelected) {
                    openSelected();
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double scrollX, double scrollY) {
        if (scrollY > 0.0) {
            changePage(-1);
            return true;
        }
        if (scrollY < 0.0) {
            changePage(1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    public void receivePage(PhotoNetworking.AlbumPageS2C packet) {
        if (packet.scope() != scope) {
            return;
        }
        page = Math.max(0, packet.page());
        totalEntries = Math.max(0, packet.totalEntries());
        dossierStage = packet.dossierStage();
        dossierDiscovered = packet.dossierDiscovered();
        dossierEvidence = PhotoEvidence.dossierFromIds(packet.dossierEvidenceIds());
        entries = packet.entries();
        loading = false;
        lastPageRequestMillis = Long.MIN_VALUE;
        pageRequestAttempts = 0;
        if (pageTimedOut) {
            status = Component.empty();
            pageTimedOut = false;
        }
        if (selectedId != null && entries.stream()
                .noneMatch(entry -> entry.photoId().equals(selectedId))) {
            selectedId = null;
            renameBox.setValue("");
        }
        for (PhotoNetworking.AlbumEntry entry : entries) {
            PhotoClientStore.ensureRequested(entry.photoId());
            if (PhotoClientStore.progress(entry.photoId(), entry.capturedAtTick()) >= 1.0f) {
                dossierRefreshes.add(entry.photoId());
                PhotoClientStore.ensureAnalysisRequested(entry.photoId());
            }
        }
        updateWidgets();
    }

    public void receiveMutation(PhotoNetworking.AlbumMutationResultS2C packet) {
        status = Component.translatable(packet.success()
                ? "screen.penumbra.album.saved" : "screen.penumbra.album.denied");
        pageTimedOut = false;
        if (packet.success() && packet.action() == PhotoNetworking.MUTATION_DELETE) {
            selectedId = null;
            renameBox.setValue("");
        }
        deleteArmedUntil = 0L;
        requestPage();
    }

    public void photoRemoved(UUID photoId) {
        if (photoId != null && entries.stream().anyMatch(entry -> entry.photoId().equals(photoId))) {
            if (photoId.equals(selectedId)) {
                selectedId = null;
                renameBox.setValue("");
            }
            requestPage();
        }
    }

    private void requestPage() {
        sendPageRequest(false);
    }

    private void sendPageRequest(boolean retry) {
        long now = Util.getMillis();
        if (!retry) {
            pageRequestAttempts = 0;
        }
        loading = true;
        lastPageRequestMillis = now;
        pageRequestAttempts++;
        updateWidgets();
        if (!ClientPlayNetworking.canSend(PhotoNetworking.AlbumPageRequestC2S.TYPE)) {
            return;
        }
        ClientPlayNetworking.send(new PhotoNetworking.AlbumPageRequestC2S(scope, page));
    }

    private void switchScope(byte wantedScope) {
        if (scope == wantedScope) {
            return;
        }
        scope = wantedScope;
        page = 0;
        totalEntries = 0;
        entries = List.of();
        selectedId = null;
        status = Component.empty();
        renameBox.setValue("");
        updateWidgets();
        requestPage();
    }

    private void changePage(int delta) {
        int maxPage = Math.max(0, (totalEntries - 1) / PAGE_SIZE);
        int target = Math.max(0, Math.min(maxPage, page + delta));
        if (target == page || loading) {
            return;
        }
        page = target;
        entries = List.of();
        selectedId = null;
        renameBox.setValue("");
        updateWidgets();
        requestPage();
    }

    private void openSelected() {
        PhotoNetworking.AlbumEntry selected = selected();
        if (selected == null || minecraft == null) {
            return;
        }
        PhotoClientStore.ensureRequested(selected.photoId());
        minecraft.setScreen(new PhotoViewerScreen(new PhotoData(selected.photoId(),
                selected.capturedAtTick(), selected.projectionist()), this));
    }

    private void renameSelected() {
        sendMutation(PhotoNetworking.MUTATION_RENAME, renameBox.getValue());
    }

    private void toggleVisibility() {
        PhotoNetworking.AlbumEntry selected = selected();
        if (selected != null) {
            sendMutation(PhotoNetworking.MUTATION_VISIBILITY,
                    Boolean.toString(!selected.publicVisible()));
        }
    }

    private void toggleImportant() {
        PhotoNetworking.AlbumEntry selected = selected();
        if (selected != null) {
            sendMutation(PhotoNetworking.MUTATION_IMPORTANT,
                    Boolean.toString(!selected.important()));
        }
    }

    private void deleteSelected() {
        if (selected() == null) {
            return;
        }
        long now = Util.getMillis();
        if (deleteArmedUntil == 0L || now > deleteArmedUntil) {
            deleteArmedUntil = now + DELETE_CONFIRM_MS;
            updateWidgets();
            return;
        }
        sendMutation(PhotoNetworking.MUTATION_DELETE, "");
    }

    private void sendMutation(byte action, String value) {
        PhotoNetworking.AlbumEntry selected = selected();
        if (selected == null
                || !ClientPlayNetworking.canSend(PhotoNetworking.AlbumMutationC2S.TYPE)) {
            return;
        }
        ClientPlayNetworking.send(new PhotoNetworking.AlbumMutationC2S(
                selected.photoId(), action, value));
    }

    private void updateWidgets() {
        if (ownedTab == null) {
            return;
        }
        ownedTab.active = scope != PhotoNetworking.ALBUM_SCOPE_OWNED;
        publicTab.active = scope != PhotoNetworking.ALBUM_SCOPE_PUBLIC;
        dossierButton.active = !loading && !pageTimedOut;
        previousButton.active = !loading && page > 0;
        nextButton.active = !loading && (page + 1) * PAGE_SIZE < totalEntries;
        PhotoNetworking.AlbumEntry selected = selected();
        viewButton.active = selected != null;
        boolean ownsSelection = selected != null && minecraft != null && minecraft.player != null
                && selected.owner().equals(minecraft.player.getUUID())
                && scope == PhotoNetworking.ALBUM_SCOPE_OWNED;
        renameBox.setVisible(ownsSelection);
        renameButton.visible = ownsSelection;
        renameButton.active = ownsSelection;
        visibilityButton.visible = ownsSelection;
        visibilityButton.active = ownsSelection;
        importantButton.visible = ownsSelection;
        importantButton.active = ownsSelection;
        deleteButton.visible = ownsSelection;
        deleteButton.active = ownsSelection;
        if (ownsSelection) {
            visibilityButton.setMessage(Component.translatable(selected.publicVisible()
                    ? "screen.penumbra.album.make_private"
                    : "screen.penumbra.album.make_public"));
            importantButton.setMessage(Component.translatable(selected.important()
                    ? "screen.penumbra.album.unmark_important"
                    : "screen.penumbra.album.mark_important"));
            deleteButton.setMessage(Component.translatable(deleteArmedUntil == 0L
                    ? "screen.penumbra.album.delete"
                    : "screen.penumbra.album.confirm_delete"));
        }
    }

    private PhotoNetworking.AlbumEntry selected() {
        if (selectedId == null) {
            return null;
        }
        return entries.stream().filter(entry -> entry.photoId().equals(selectedId))
                .findFirst().orElse(null);
    }

    private Component analysisLabel(PhotoNetworking.AlbumEntry entry) {
        var evidence = PhotoClientStore.evidenceIfDeveloped(
                entry.photoId(), entry.capturedAtTick());
        if (evidence.isEmpty()) {
            int percent = Math.round(PhotoClientStore.progress(
                    entry.photoId(), entry.capturedAtTick()) * 100.0f);
            return Component.translatable("screen.penumbra.album.analysis", percent);
        }
        List<PhotoEvidence> revealed = evidence.get();
        if (revealed.isEmpty()) {
            return Component.translatable("screen.penumbra.album.evidence.none");
        }
        if (revealed.size() == 1) {
            return Component.translatable(revealed.getFirst().translationKey());
        }
        return Component.translatable("screen.penumbra.album.evidence.multiple",
                revealed.size());
    }

    private Grid grid() {
        int left = Math.max(6, (width - Math.min(620, width - 12)) / 2);
        int gridWidth = Math.max(120, Math.min(620, width - 12));
        int top = 49;
        int bottom = Math.max(top + 56, height - (status.getString().isEmpty() ? 58 : 72));
        return new Grid(left, top, gridWidth, Math.max(56, bottom - top));
    }

    private static Card card(int index, Grid grid) {
        int gap = 6;
        int width = Math.max(34, (grid.width - gap * (COLUMNS - 1)) / COLUMNS);
        int height = Math.max(26, (grid.height - gap * (ROWS - 1)) / ROWS);
        int column = index % COLUMNS;
        int row = index / COLUMNS;
        return new Card(grid.left + column * (width + gap),
                grid.top + row * (height + gap), width, height);
    }

    private record Grid(int left, int top, int width, int height) {
    }

    private record Card(int left, int top, int width, int height) {
        private int right() {
            return left + width;
        }

        private int bottom() {
            return top + height;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

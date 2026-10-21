package fr.penumbra.client.photo;

import fr.penumbra.network.PhotoNetworking;
import fr.penumbra.photo.PhotoData;
import fr.penumbra.photo.PhotoEvidence;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

/** Visionneuse plein écran d'une vraie capture, avec finition VHS discrète. */
public final class PhotoViewerScreen extends Screen {

    private static final double ASPECT = 16.0 / 9.0;
    private static final int HAUNTED_OBSERVATION_TICKS = 30;
    private static final int OBSERVATION_RETRY_TICKS = 20;
    private static final int MAX_OBSERVATION_ATTEMPTS = 5;
    private final PhotoData photo;
    private final Screen parent;
    private int developedObservationTicks;
    private int observationAttempts;

    public PhotoViewerScreen(PhotoData photo) {
        this(photo, null);
    }

    public PhotoViewerScreen(PhotoData photo, Screen parent) {
        super(Component.translatable("screen.penumbra.photo.title"));
        this.photo = photo;
        this.parent = parent;
    }

    @Override
    public void added() {
        super.added();
        PhotoClientStore.ensureRequested(photo.photoId());
    }

    @Override
    public void tick() {
        PhotoClientStore.ensureRequested(photo.photoId());
        float progress = PhotoClientStore.progress(photo.photoId(), photo.capturedAtTick());
        if (progress >= 1.0f) {
            PhotoClientStore.ensureAnalysisRequested(photo.photoId());
        }
        if (photo.projectionist() && progress >= 1.0f) {
            developedObservationTicks++;
            int nextAttemptTick = HAUNTED_OBSERVATION_TICKS
                    + observationAttempts * OBSERVATION_RETRY_TICKS;
            if (observationAttempts < MAX_OBSERVATION_ATTEMPTS
                    && developedObservationTicks >= nextAttemptTick
                    && ClientPlayNetworking.canSend(PhotoNetworking.PhotoObserveC2S.TYPE)) {
                observationAttempts++;
                ClientPlayNetworking.send(new PhotoNetworking.PhotoObserveC2S(photo.photoId()));
            }
        }
    }

    public boolean isViewing(java.util.UUID photoId) {
        return photo.photoId().equals(photoId);
    }

    /** Ferme proprement une photo révoquée et rafraîchit l'album parent. */
    public void photoRemoved(java.util.UUID photoId) {
        if (!isViewing(photoId)) {
            return;
        }
        if (parent instanceof PhotoAlbumScreen album) {
            album.photoRemoved(photoId);
        }
        onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xF207090A);

        int maxWidth = Math.max(64, Math.round(width * 0.86f));
        int maxHeight = Math.max(36, Math.round(height * 0.72f));
        int imageWidth = Math.min(maxWidth, (int) Math.floor(maxHeight * ASPECT));
        int imageHeight = Math.max(36, (int) Math.floor(imageWidth / ASPECT));
        int border = Math.max(7, Math.min(20, Math.min(width, height) / 28));
        int left = (width - imageWidth) / 2;
        int top = Math.max(border + 12, (height - imageHeight) / 2 - border / 2);

        // Ombre, papier instantané ivoire et mince liseré d'émulsion.
        graphics.fill(left - border + 4, top - border + 5,
                left + imageWidth + border + 4, top + imageHeight + border * 2 + 5, 0x76000000);
        graphics.fill(left - border, top - border,
                left + imageWidth + border, top + imageHeight + border * 2, 0xFFE3D4B5);
        graphics.fill(left - 2, top - 2, left + imageWidth + 2, top + imageHeight + 2, 0xFF241F1A);

        Identifier texture = PhotoClientStore.texture(photo.photoId());
        if (texture == null) {
            graphics.fill(left, top, left + imageWidth, top + imageHeight, 0xFF020304);
            graphics.centeredText(font, Component.translatable("screen.penumbra.photo.loading"),
                    width / 2, top + imageHeight / 2 - font.lineHeight / 2, 0xFFB8B2A4);
        } else {
            // En 26.1.2 : x0/y0/x1/y1 puis u0/u1/v0/v1. L'ancien
            // appel passait largeur/hauteur et u0/v0/u1/v1 : un seul texel gris.
            graphics.blit(texture, left, top, left + imageWidth, top + imageHeight,
                    0.0f, 1.0f, 0.0f, 1.0f);
            for (int y = top + 2; y < top + imageHeight - 2; y += 4) {
                graphics.fill(left + 1, y, left + imageWidth - 1, y + 1, 0x11020A0C);
            }
        }

        int percent = Math.round(PhotoClientStore.progress(photo.photoId(), photo.capturedAtTick()) * 100.0f);
        Component state;
        var revealed = PhotoClientStore.evidenceIfDeveloped(
                photo.photoId(), photo.capturedAtTick());
        if (revealed.isPresent()) {
            state = evidenceState(revealed.get());
        } else if (texture == null) {
            state = Component.translatable("screen.penumbra.photo.loading");
        } else if (percent >= 100) {
            state = Component.translatable("screen.penumbra.photo.analyzing");
        } else {
            state = Component.translatable("screen.penumbra.photo.developing", percent);
        }
        graphics.centeredText(font, state, width / 2,
                Math.min(height - font.lineHeight * 3, top + imageHeight + border / 2 + 4), 0xFF4A382C);
        graphics.centeredText(font, Component.translatable("screen.penumbra.photo.controls"),
                width / 2, height - font.lineHeight - 7, 0xFF9A968D);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 1) {
            onClose();
            return true;
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static Component evidenceState(List<PhotoEvidence> evidence) {
        if (evidence.isEmpty()) {
            return Component.translatable("screen.penumbra.photo.evidence.none");
        }
        PhotoEvidence first = evidence.getFirst();
        if (evidence.size() == 1) {
            return Component.translatable("screen.penumbra.photo.evidence.one",
                    Component.translatable(first.translationKey()));
        }
        return Component.translatable("screen.penumbra.photo.evidence.multiple",
                Component.translatable(first.translationKey()), evidence.size() - 1);
    }

    @Override
    public void onClose() {
        if (parent != null && minecraft != null) {
            minecraft.setScreen(parent);
        } else {
            super.onClose();
        }
    }
}

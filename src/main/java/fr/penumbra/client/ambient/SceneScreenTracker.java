package fr.penumbra.client.ambient;

import fr.penumbra.client.journal.SurveyJournalScreen;
import fr.penumbra.client.photo.PhotoAlbumScreen;
import fr.penumbra.client.photo.PhotoInvestigationScreen;
import fr.penumbra.client.photo.PhotoViewerScreen;
import fr.penumbra.network.PenumbraC2S;
import fr.penumbra.network.SceneScreenState;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.BookSignScreen;

/** Polls actual screens, including transitions between two non-container readers. */
public final class SceneScreenTracker {
    private static Object level;
    private static int lastKind = -1, heartbeat;
    private SceneScreenTracker() { }
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.level == null) {
                level = null;
                lastKind = -1;
                heartbeat = 0;
                return;
            }
            if (level != client.level) {
                level = client.level;
                lastKind = -1;
                heartbeat = 0;
            }
            int kind = client.screen == null ? SceneScreenState.NONE
                    : client.screen instanceof AbstractContainerScreen<?>
                    || client.screen instanceof BookViewScreen
                    || client.screen instanceof BookEditScreen
                    || client.screen instanceof BookSignScreen
                    || client.screen instanceof SurveyJournalScreen
                    || client.screen instanceof PhotoAlbumScreen
                    || client.screen instanceof PhotoViewerScreen
                    || client.screen instanceof PhotoInvestigationScreen
                    ? SceneScreenState.OCCUPIED : SceneScreenState.OTHER;
            if ((kind != lastKind || ++heartbeat >= 40)
                    && ClientPlayNetworking.canSend(PenumbraC2S.SceneScreenC2S.TYPE)) {
                ClientPlayNetworking.send(new PenumbraC2S.SceneScreenC2S(kind));
                lastKind = kind;
                heartbeat = 0;
            }
        });
    }
}

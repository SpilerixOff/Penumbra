package fr.penumbra.photo;

import com.mojang.serialization.JsonOps;
import fr.penumbra.network.PhotoNetworking;
import fr.penumbra.network.PhotoNetworking.AlbumEntry;
import fr.penumbra.network.PhotoNetworking.AlbumPageS2C;
import fr.penumbra.photo.investigation.InvestigationRules;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises storage, development and the real wire codec without constructing a fake Minecraft world. */
class PhotoInvestigationFlowTest {

    @Test
    void listenerNegativeSurvivesReloadDevelopsAndRemainsInTheTransmittedDossierAfterDeletion() {
        UUID owner = new UUID(0L, 211L);
        PhotoAlbumState album = new PhotoAlbumState();
        UUID photoId = album.add(owner, 1_200L, new byte[PhotoAlbumState.RGB_BYTES], false,
                List.of(PhotoEvidence.LISTENER)).orElseThrow().id();
        album = reload(album);

        assertFalse(album.revealDeveloped(owner, 1_399L, PhotoData.DEVELOPMENT_TICKS).changed());
        AlbumPageS2C developing = snapshot(album, owner, PhotoAlbumState.AlbumScope.OWNED);
        assertEquals(photoId, developing.entries().getFirst().photoId());
        assertTrue(developing.dossierEvidenceIds().isEmpty());

        assertEquals(List.of(PhotoEvidence.LISTENER), album.revealDeveloped(
                owner, 1_400L, PhotoData.DEVELOPMENT_TICKS).newlyDiscovered());
        AlbumPageS2C developed = snapshot(album, owner, PhotoAlbumState.AlbumScope.OWNED);
        assertEquals(List.of("listener"), developed.dossierEvidenceIds());
        assertEquals(1, developed.dossierDiscovered());
        assertEquals(1, developed.dossierStage());
        assertTrue(InvestigationRules.unlockedClues(
                PhotoEvidence.dossierFromIds(developed.dossierEvidenceIds())).isEmpty());

        assertTrue(album.delete(owner, photoId));
        PhotoAlbumState restored = reload(album);
        AlbumPageS2C withoutPrint = snapshot(restored, owner, PhotoAlbumState.AlbumScope.OWNED);
        assertEquals(List.of("listener"), withoutPrint.dossierEvidenceIds());
        assertTrue(withoutPrint.entries().isEmpty());
        assertFalse(restored.revealDeveloped(owner, 1_600L, PhotoData.DEVELOPMENT_TICKS).changed());
        assertFalse(restored.recordEvidence(owner, PhotoEvidence.LISTENER).changed());
    }

    @Test
    void publicListenerPhotoDoesNotBecomeTheViewersEvidenceOrExposeAnotherPlayersPrivatePrint() {
        UUID photographer = new UUID(0L, 212L);
        UUID viewer = new UUID(0L, 213L);
        PhotoAlbumState album = new PhotoAlbumState();
        UUID shared = album.add(photographer, 0L, new byte[PhotoAlbumState.RGB_BYTES], false,
                List.of(PhotoEvidence.LISTENER)).orElseThrow().id();
        album.add(photographer, 0L, new byte[PhotoAlbumState.RGB_BYTES], false,
                List.of(PhotoEvidence.ORIGINEL)).orElseThrow();
        album.setPublic(photographer, shared, true);
        album.add(viewer, 500L, new byte[PhotoAlbumState.RGB_BYTES], false,
                List.of(PhotoEvidence.RAMPANT)).orElseThrow();
        album = reload(album);
        album.revealDeveloped(photographer, 200L, PhotoData.DEVELOPMENT_TICKS);
        assertFalse(album.revealDeveloped(viewer, 200L, PhotoData.DEVELOPMENT_TICKS).changed());

        AlbumPageS2C publicPage = snapshot(album, viewer, PhotoAlbumState.AlbumScope.PUBLIC);
        assertEquals(List.of(shared), publicPage.entries().stream().map(AlbumEntry::photoId).toList());
        assertEquals(photographer, publicPage.entries().getFirst().owner());
        assertTrue(publicPage.dossierEvidenceIds().isEmpty());
        assertEquals(0, publicPage.dossierDiscovered());

        album.revealDeveloped(viewer, 700L, PhotoData.DEVELOPMENT_TICKS);
        assertEquals(List.of("rampant"), snapshot(album, viewer,
                PhotoAlbumState.AlbumScope.PUBLIC).dossierEvidenceIds());
        assertEquals(List.of("listener", "originel"), snapshot(album, photographer,
                PhotoAlbumState.AlbumScope.OWNED).dossierEvidenceIds());
    }

    @Test
    void emptyFrameAndADeletedUndevelopedNegativeNeverManufactureADiscovery() {
        UUID owner = new UUID(0L, 214L);
        PhotoAlbumState album = new PhotoAlbumState();
        UUID empty = album.add(owner, 0L, new byte[PhotoAlbumState.RGB_BYTES], false,
                List.of()).orElseThrow().id();
        UUID removed = album.add(owner, 0L, new byte[PhotoAlbumState.RGB_BYTES], false,
                List.of(PhotoEvidence.LISTENER)).orElseThrow().id();
        assertTrue(album.delete(owner, removed));
        album = reload(album);
        assertTrue(album.find(removed).isEmpty());
        assertFalse(album.revealDeveloped(owner, 200L, PhotoData.DEVELOPMENT_TICKS).changed());

        AlbumPageS2C packet = snapshot(album, owner, PhotoAlbumState.AlbumScope.OWNED);
        assertEquals(List.of(empty), packet.entries().stream().map(AlbumEntry::photoId).toList());
        assertEquals(0, packet.dossierStage());
        assertTrue(packet.dossierEvidenceIds().isEmpty());
        assertTrue(InvestigationRules.partialLeads(
                PhotoEvidence.dossierFromIds(packet.dossierEvidenceIds())).isEmpty());
    }

    private static PhotoAlbumState reload(PhotoAlbumState album) {
        var saved = PhotoAlbumState.CODEC.encodeStart(JsonOps.INSTANCE, album).getOrThrow();
        return PhotoAlbumState.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
    }

    private static AlbumPageS2C snapshot(PhotoAlbumState album, UUID viewer,
                                          PhotoAlbumState.AlbumScope scope) {
        PhotoAlbumState.DossierProgress dossier = album.dossier(viewer);
        PhotoAlbumState.AlbumPage page = album.page(viewer, scope, 0, PhotoNetworking.ALBUM_PAGE_SIZE);
        List<AlbumEntry> entries = page.entries().stream().map(photo -> new AlbumEntry(
                photo.id(), photo.owner(), photo.capturedAtTick(), photo.projectionist(),
                photo.title(), photo.publicVisible(), photo.important())).toList();
        AlbumPageS2C packet = new AlbumPageS2C(scope == PhotoAlbumState.AlbumScope.PUBLIC
                ? PhotoNetworking.ALBUM_SCOPE_PUBLIC : PhotoNetworking.ALBUM_SCOPE_OWNED,
                page.page(), page.totalEntries(), dossier.stage(), dossier.count(),
                dossier.discovered().stream().map(PhotoEvidence::id).toList(), entries);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            AlbumPageS2C.CODEC.encode(buffer, packet);
            AlbumPageS2C restored = AlbumPageS2C.CODEC.decode(buffer);
            assertEquals(0, buffer.readableBytes());
            return restored;
        } finally {
            buffer.release();
        }
    }
}

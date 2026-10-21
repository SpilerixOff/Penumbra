package fr.penumbra.photo;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhotoAlbumStateTest {

    @Test
    void codecRoundTripKeepsExactBoundedRgbPayload() {
        byte[] pixels = new byte[PhotoAlbumState.RGB_BYTES];
        for (int index = 0; index < pixels.length; index++) {
            pixels[index] = (byte) (index * 31);
        }
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000123");
        PhotoAlbumState album = new PhotoAlbumState();
        PhotoAlbumState.StoredPhoto stored =
                album.add(owner, 4_200L, pixels, true,
                        List.of(PhotoEvidence.HOST, PhotoEvidence.RAMPANT)).orElseThrow();
        assertTrue(album.rename(owner, stored.id(), "La mine"));
        assertTrue(album.setPublic(owner, stored.id(), true));
        assertTrue(album.setImportant(owner, stored.id(), true));
        PhotoAlbumState.EvidenceDiscovery discovery = album.recordEvidence(
                owner, List.of(PhotoEvidence.HOST, PhotoEvidence.RAMPANT));
        assertTrue(discovery.changed());

        JsonElement encoded = PhotoAlbumState.CODEC.encodeStart(JsonOps.INSTANCE, album).getOrThrow();
        PhotoAlbumState decoded = PhotoAlbumState.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        PhotoAlbumState.StoredPhoto restored = decoded.find(stored.id()).orElseThrow();

        assertEquals(owner, restored.owner());
        assertEquals(4_200L, restored.capturedAtTick());
        assertArrayEquals(pixels, restored.pixels());
        assertTrue(restored.projectionist());
        assertEquals("La mine", restored.title());
        assertTrue(restored.publicVisible());
        assertTrue(restored.important());
        assertEquals(List.of(PhotoEvidence.HOST, PhotoEvidence.RAMPANT,
                PhotoEvidence.PROJECTIONIST), restored.evidence());
        assertEquals(List.of(PhotoEvidence.HOST, PhotoEvidence.RAMPANT),
                decoded.dossier(owner).discovered());
    }

    @Test
    void storedPixelsAreDefensivelyCopied() {
        byte[] pixels = new byte[PhotoAlbumState.RGB_BYTES];
        pixels[0] = 41;
        PhotoAlbumState.StoredPhoto stored = new PhotoAlbumState()
                .add(UUID.randomUUID(), 0L, pixels).orElseThrow();
        pixels[0] = 99;
        byte[] read = stored.pixels();
        read[0] = 12;

        assertNotEquals(99, stored.pixels()[0]);
        assertEquals(41, stored.pixels()[0]);
    }

    @Test
    void malformedPixelLengthIsRejectedBeforeStorage() {
        PhotoAlbumState album = new PhotoAlbumState();
        assertTrue(album.add(UUID.randomUUID(), 0L,
                new byte[PhotoAlbumState.RGB_BYTES - 1]).isEmpty());
        assertEquals(0, album.size());
    }

    @Test
    void legacyAlbumWithoutProjectionistFieldDefaultsToNormalPhoto() {
        PhotoAlbumState album = new PhotoAlbumState();
        PhotoAlbumState.StoredPhoto stored = album.add(
                UUID.fromString("00000000-0000-0000-0000-000000000124"),
                12L, new byte[PhotoAlbumState.RGB_BYTES]).orElseThrow();
        JsonElement legacy = PhotoAlbumState.CODEC
                .encodeStart(JsonOps.INSTANCE, album).getOrThrow();
        legacy.getAsJsonObject().getAsJsonArray("photos").get(0)
                .getAsJsonObject().remove("projectionist");
        legacy.getAsJsonObject().getAsJsonArray("photos").get(0)
                .getAsJsonObject().remove("title");
        legacy.getAsJsonObject().getAsJsonArray("photos").get(0)
                .getAsJsonObject().remove("public");
        legacy.getAsJsonObject().getAsJsonArray("photos").get(0)
                .getAsJsonObject().remove("evidence");
        legacy.getAsJsonObject().getAsJsonArray("photos").get(0)
                .getAsJsonObject().remove("important");
        legacy.getAsJsonObject().remove("dossiers");

        PhotoAlbumState restored =
                PhotoAlbumState.CODEC.parse(JsonOps.INSTANCE, legacy).getOrThrow();
        PhotoAlbumState.StoredPhoto legacyPhoto = restored.find(stored.id()).orElseThrow();
        assertFalse(legacyPhoto.projectionist());
        assertEquals("", legacyPhoto.title());
        assertFalse(legacyPhoto.publicVisible());
        assertTrue(legacyPhoto.evidence().isEmpty());
        assertFalse(legacyPhoto.important());
        assertEquals(0, restored.dossier(legacyPhoto.owner()).count());
    }

    @Test
    void multiplayerAlbumNoLongerStopsAtTheOldFiveHundredTwelvePhotoCeiling() {
        PhotoAlbumState album = new PhotoAlbumState();
        byte[] pixels = new byte[PhotoAlbumState.RGB_BYTES];

        for (int ownerIndex = 0; ownerIndex < 5; ownerIndex++) {
            UUID owner = new UUID(0L, ownerIndex + 1L);
            for (int photoIndex = 0; photoIndex < 110; photoIndex++) {
                assertTrue(album.add(owner, photoIndex, pixels).isPresent());
            }
        }

        assertEquals(550, album.size());
        assertTrue(PhotoAlbumState.MAX_WORLD_PHOTOS >= 16
                * PhotoAlbumState.MAX_PLAYER_PHOTOS);
    }

    @Test
    void onePlayerCannotConsumeAnotherPlayersAlbumQuota() {
        PhotoAlbumState album = new PhotoAlbumState();
        byte[] pixels = new byte[PhotoAlbumState.RGB_BYTES];
        UUID first = new UUID(0L, 41L);
        UUID second = new UUID(0L, 42L);

        for (int index = 0; index < PhotoAlbumState.MAX_PLAYER_PHOTOS; index++) {
            assertTrue(album.add(first, index, pixels).isPresent());
        }
        assertTrue(album.add(first, 999L, pixels).isEmpty());
        assertTrue(album.add(second, 1_000L, pixels).isPresent());
    }

    @Test
    void loadingAlsoEnforcesThePerPlayerQuota() {
        byte[] pixels = new byte[PhotoAlbumState.RGB_BYTES];
        UUID first = new UUID(0L, 51L);
        UUID second = new UUID(0L, 52L);
        List<PhotoAlbumState.StoredPhoto> loaded = new ArrayList<>();
        for (int index = 0; index < PhotoAlbumState.MAX_PLAYER_PHOTOS + 7; index++) {
            loaded.add(new PhotoAlbumState.StoredPhoto(
                    new UUID(1L, index + 1L), first, index, pixels));
        }
        PhotoAlbumState.StoredPhoto secondPhoto = new PhotoAlbumState.StoredPhoto(
                new UUID(2L, 1L), second, 999L, pixels);
        loaded.add(secondPhoto);

        PhotoAlbumState album = new PhotoAlbumState(loaded);
        assertEquals(PhotoAlbumState.MAX_PLAYER_PHOTOS + 1, album.size());
        assertTrue(album.find(secondPhoto.id()).isPresent());
    }

    @Test
    void ownerCanRenamePublishAndDeleteWhileStrangerCannot() {
        PhotoAlbumState album = new PhotoAlbumState();
        UUID owner = new UUID(0L, 61L);
        UUID stranger = new UUID(0L, 62L);
        UUID photoId = album.add(owner, 10L, new byte[PhotoAlbumState.RGB_BYTES])
                .orElseThrow().id();

        assertFalse(album.rename(stranger, photoId, "Volée"));
        assertFalse(album.setPublic(stranger, photoId, true));
        assertFalse(album.setImportant(stranger, photoId, true));
        assertFalse(album.delete(stranger, photoId));
        assertEquals("", album.find(photoId).orElseThrow().title());

        assertTrue(album.rename(owner, photoId, "  Entrée §ksecrète\n  "));
        assertEquals("Entrée ksecrète", album.find(photoId).orElseThrow().title());
        assertTrue(album.setPublic(owner, photoId, true));
        assertTrue(album.find(photoId).orElseThrow().publicVisible());
        assertTrue(album.setImportant(owner, photoId, true));
        assertTrue(album.find(photoId).orElseThrow().important());
        assertTrue(album.delete(owner, photoId));
        assertEquals(0, album.ownedCount(owner));
        assertTrue(album.find(photoId).isEmpty());
    }

    @Test
    void deletionImmediatelyReleasesTheOwnersQuota() {
        PhotoAlbumState album = new PhotoAlbumState();
        UUID owner = new UUID(0L, 71L);
        UUID first = null;
        for (int index = 0; index < PhotoAlbumState.MAX_PLAYER_PHOTOS; index++) {
            UUID id = album.add(owner, index, new byte[PhotoAlbumState.RGB_BYTES])
                    .orElseThrow().id();
            if (first == null) {
                first = id;
            }
        }
        assertTrue(album.add(owner, 500L, new byte[PhotoAlbumState.RGB_BYTES]).isEmpty());

        assertTrue(album.delete(owner, first));
        assertTrue(album.add(owner, 501L, new byte[PhotoAlbumState.RGB_BYTES]).isPresent());
        assertEquals(PhotoAlbumState.MAX_PLAYER_PHOTOS, album.ownedCount(owner));
    }

    @Test
    void pagesAreBoundedSortedAndRespectPrivateVisibility() {
        PhotoAlbumState album = new PhotoAlbumState();
        UUID owner = new UUID(0L, 81L);
        UUID other = new UUID(0L, 82L);
        PhotoAlbumState.StoredPhoto old = album.add(owner, 10L,
                new byte[PhotoAlbumState.RGB_BYTES]).orElseThrow();
        PhotoAlbumState.StoredPhoto recent = album.add(owner, 30L,
                new byte[PhotoAlbumState.RGB_BYTES]).orElseThrow();
        PhotoAlbumState.StoredPhoto privateOther = album.add(other, 40L,
                new byte[PhotoAlbumState.RGB_BYTES]).orElseThrow();
        PhotoAlbumState.StoredPhoto publicOther = album.add(other, 20L,
                new byte[PhotoAlbumState.RGB_BYTES]).orElseThrow();
        assertTrue(album.setPublic(owner, old.id(), true));
        assertTrue(album.setPublic(other, publicOther.id(), true));

        PhotoAlbumState.AlbumPage owned = album.page(owner,
                PhotoAlbumState.AlbumScope.OWNED, 0, 99);
        assertEquals(2, owned.totalEntries());
        assertEquals(PhotoAlbumState.MAX_PAGE_SIZE, owned.pageSize());
        assertEquals(List.of(recent.id(), old.id()),
                owned.entries().stream().map(PhotoAlbumState.PhotoMetadata::id).toList());

        PhotoAlbumState.AlbumPage published = album.page(owner,
                PhotoAlbumState.AlbumScope.PUBLIC, 0, 1);
        assertEquals(2, published.totalEntries());
        assertEquals(1, published.entries().size());
        assertEquals(publicOther.id(), published.entries().getFirst().id());
        assertFalse(published.entries().stream()
                .anyMatch(entry -> entry.id().equals(privateOther.id())));
        PhotoAlbumState.AlbumPage secondPage = album.page(owner,
                PhotoAlbumState.AlbumScope.PUBLIC, 1, 1);
        assertEquals(old.id(), secondPage.entries().getFirst().id());
    }

    @Test
    void titleSanitizerIsNullSafeAndStrictlyBounded() {
        assertEquals("", PhotoAlbumState.sanitizeTitle(null));
        assertEquals("", PhotoAlbumState.sanitizeTitle(" \n\t "));
        assertEquals(PhotoAlbumState.MAX_TITLE_LENGTH,
                PhotoAlbumState.sanitizeTitle("x".repeat(200)).length());
        assertFalse(PhotoAlbumState.sanitizeTitle("abc\u0000def§ghi").contains("§"));
    }

    @Test
    void legacyProjectionistNegativeBecomesProjectionistEvidence() {
        UUID owner = new UUID(0L, 91L);
        PhotoAlbumState album = new PhotoAlbumState();
        PhotoAlbumState.StoredPhoto photo = album.add(owner, 10L,
                new byte[PhotoAlbumState.RGB_BYTES], true).orElseThrow();
        JsonElement legacy = PhotoAlbumState.CODEC
                .encodeStart(JsonOps.INSTANCE, album).getOrThrow();
        legacy.getAsJsonObject().getAsJsonArray("photos").get(0)
                .getAsJsonObject().remove("evidence");

        PhotoAlbumState restored = PhotoAlbumState.CODEC
                .parse(JsonOps.INSTANCE, legacy).getOrThrow();
        assertEquals(List.of(PhotoEvidence.PROJECTIONIST),
                restored.find(photo.id()).orElseThrow().evidence());
    }

    @Test
    void metadataMutationsNeverEraseEvidenceOrImportantFlag() {
        PhotoAlbumState album = new PhotoAlbumState();
        UUID owner = new UUID(0L, 92L);
        UUID id = album.add(owner, 20L, new byte[PhotoAlbumState.RGB_BYTES], false,
                List.of(PhotoEvidence.ABSENT, PhotoEvidence.SCUTTLER)).orElseThrow().id();
        assertTrue(album.setImportant(owner, id, true));
        assertTrue(album.rename(owner, id, "Sous le puits"));
        assertTrue(album.setPublic(owner, id, true));

        PhotoAlbumState.StoredPhoto stored = album.find(id).orElseThrow();
        assertEquals(List.of(PhotoEvidence.ABSENT, PhotoEvidence.SCUTTLER), stored.evidence());
        assertTrue(stored.important());
    }

    @Test
    void ownedAlbumPinsImportantButPublicAlbumRemainsChronological() {
        PhotoAlbumState album = new PhotoAlbumState();
        UUID owner = new UUID(0L, 93L);
        PhotoAlbumState.StoredPhoto oldImportant = album.add(owner, 10L,
                new byte[PhotoAlbumState.RGB_BYTES]).orElseThrow();
        PhotoAlbumState.StoredPhoto recent = album.add(owner, 30L,
                new byte[PhotoAlbumState.RGB_BYTES]).orElseThrow();
        assertTrue(album.setImportant(owner, oldImportant.id(), true));
        assertTrue(album.setPublic(owner, oldImportant.id(), true));
        assertTrue(album.setPublic(owner, recent.id(), true));

        assertEquals(oldImportant.id(), album.page(owner,
                PhotoAlbumState.AlbumScope.OWNED, 0, 6).entries().getFirst().id());
        assertEquals(recent.id(), album.page(owner,
                PhotoAlbumState.AlbumScope.PUBLIC, 0, 6).entries().getFirst().id());
    }

    @Test
    void dossierIsUniqueIdempotentAndUsesExactStageThresholds() {
        PhotoAlbumState album = new PhotoAlbumState();
        UUID owner = new UUID(0L, 94L);

        assertFalse(album.recordEvidence(owner, PhotoEvidence.NONE).changed());
        assertEquals(0, album.dossier(owner).stage());
        assertEquals(1, album.recordEvidence(owner, PhotoEvidence.HOST).newStage());
        assertFalse(album.recordEvidence(owner, PhotoEvidence.HOST).changed());
        assertEquals(2, album.recordEvidence(owner,
                List.of(PhotoEvidence.WATCHER, PhotoEvidence.ORIGINEL)).newStage());
        assertEquals(3, album.recordEvidence(owner,
                List.of(PhotoEvidence.CONTREFAIT, PhotoEvidence.RAMPANT)).newStage());
        assertEquals(4, album.recordEvidence(owner,
                List.of(PhotoEvidence.ABSENT, PhotoEvidence.SCUTTLER)).newStage());
        assertEquals(7, album.dossier(owner).count());
    }

    @Test
    void evidenceIsRevealedOnlyAfterDevelopmentAndDeletionDoesNotEraseIt() {
        PhotoAlbumState album = new PhotoAlbumState();
        UUID owner = new UUID(0L, 95L);
        PhotoAlbumState.StoredPhoto photo = album.add(owner, 1_000L,
                new byte[PhotoAlbumState.RGB_BYTES], false,
                List.of(PhotoEvidence.ORIGINEL)).orElseThrow();

        assertFalse(album.revealDeveloped(owner, 1_199L,
                PhotoData.DEVELOPMENT_TICKS).changed());
        assertEquals(0, album.dossier(owner).count());
        assertTrue(album.revealDeveloped(owner, 1_200L,
                PhotoData.DEVELOPMENT_TICKS).changed());
        assertEquals(1, album.dossier(owner).count());
        assertTrue(album.delete(owner, photo.id()));
        assertEquals(List.of(PhotoEvidence.ORIGINEL), album.dossier(owner).discovered());
    }

    @Test
    void evidenceListsAreDeduplicatedBoundedAndDefensive() {
        UUID owner = new UUID(0L, 96L);
        List<PhotoEvidence> requested = new ArrayList<>(List.of(
                PhotoEvidence.NONE, PhotoEvidence.HOST, PhotoEvidence.HOST,
                PhotoEvidence.WATCHER, PhotoEvidence.ORIGINEL));
        PhotoAlbumState.StoredPhoto stored = new PhotoAlbumState().add(owner, 0L,
                new byte[PhotoAlbumState.RGB_BYTES], false, requested).orElseThrow();
        requested.clear();

        assertEquals(List.of(PhotoEvidence.HOST, PhotoEvidence.WATCHER,
                PhotoEvidence.ORIGINEL), stored.evidence());
        assertThrowsUnsupported(stored.evidence());
    }

    private static void assertThrowsUnsupported(List<PhotoEvidence> evidence) {
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> evidence.add(PhotoEvidence.ABSENT));
    }
}

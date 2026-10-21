package fr.penumbra.photo;

import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhotoDataTest {

    private static final long CAPTURE_TICK = 1_000L;

    @Test
    void developmentProgressIsClampedBeforeAndAfterDevelopment() {
        assertEquals(0.0f, PhotoData.developmentProgress(CAPTURE_TICK - 20L, CAPTURE_TICK));
        assertEquals(0.0f, PhotoData.developmentProgress(CAPTURE_TICK, CAPTURE_TICK));
        assertEquals(0.25f, PhotoData.developmentProgress(CAPTURE_TICK + 50L, CAPTURE_TICK));
        assertEquals(0.575f, PhotoData.developmentProgress(CAPTURE_TICK + 115L, CAPTURE_TICK));
        assertEquals(1.0f, PhotoData.developmentProgress(
                CAPTURE_TICK + PhotoData.DEVELOPMENT_TICKS, CAPTURE_TICK));
        assertEquals(1.0f, PhotoData.developmentProgress(
                CAPTURE_TICK + PhotoData.DEVELOPMENT_TICKS + 500L, CAPTURE_TICK));
    }

    @Test
    void stagesChangeExactlyAtTheirFourVisualBoundaries() {
        assertEquals(0, PhotoData.developmentStage(CAPTURE_TICK - 1L, CAPTURE_TICK));
        assertEquals(0, PhotoData.developmentStage(CAPTURE_TICK + 49L, CAPTURE_TICK));
        assertEquals(1, PhotoData.developmentStage(CAPTURE_TICK + 50L, CAPTURE_TICK));
        assertEquals(1, PhotoData.developmentStage(CAPTURE_TICK + 114L, CAPTURE_TICK));
        assertEquals(2, PhotoData.developmentStage(CAPTURE_TICK + 115L, CAPTURE_TICK));
        assertEquals(2, PhotoData.developmentStage(CAPTURE_TICK + 199L, CAPTURE_TICK));
        assertEquals(3, PhotoData.developmentStage(CAPTURE_TICK + 200L, CAPTURE_TICK));
    }

    @Test
    void instanceMethodsUseTheCapturedTick() {
        PhotoData photo = new PhotoData(UUID.fromString("00000000-0000-0000-0000-000000000041"), CAPTURE_TICK);

        assertEquals(2, photo.stage(CAPTURE_TICK + 150L));
        assertEquals(0.75f, photo.progress(CAPTURE_TICK + 150L));
    }

    @Test
    void legacyComponentWithoutProjectionistFlagRemainsReadable() {
        PhotoData source = new PhotoData(
                UUID.fromString("00000000-0000-0000-0000-000000000041"),
                CAPTURE_TICK);
        var legacy = PhotoData.CODEC.encodeStart(JsonOps.INSTANCE, source).getOrThrow();
        legacy.getAsJsonObject().remove("projectionist");
        PhotoData decoded = PhotoData.CODEC.parse(JsonOps.INSTANCE, legacy).getOrThrow();

        assertFalse(decoded.projectionist());
    }

    @Test
    void projectionistFlagSurvivesPersistentCodecRoundTrip() {
        PhotoData source = new PhotoData(
                UUID.fromString("00000000-0000-0000-0000-000000000042"),
                CAPTURE_TICK, true);
        var encoded = PhotoData.CODEC.encodeStart(JsonOps.INSTANCE, source).getOrThrow();
        PhotoData decoded = PhotoData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();

        assertTrue(decoded.projectionist());
        assertEquals(source, decoded);
    }
}

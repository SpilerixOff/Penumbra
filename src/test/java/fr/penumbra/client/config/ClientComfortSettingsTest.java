package fr.penumbra.client.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ClientComfortSettingsTest {
    @TempDir Path directory;

    @Test
    void roundTripPreservesPreferencesWithoutTouchingGameplayConfig() throws IOException {
        Path gameplay = directory.resolve("penumbra.json");
        Files.writeString(gameplay, "{\"eventFrequency\":0.25}");
        ClientComfortSettings chosen = new ClientComfortSettings();
        chosen.reduceMotion = true;
        chosen.hideFaces = true;
        chosen.blockFlashes = true;
        chosen.grainEnabled = false;
        chosen.afterimagesEnabled = false;
        chosen.lightFlickerEnabled = false;
        chosen.suddenSoundVolume = 0.25f;
        chosen.ambientSoundVolume = 0.6f;
        Path local = directory.resolve("penumbra-client.json");
        ClientComfortSettings.write(local, chosen);
        ClientComfortSettings restored = ClientComfortSettings.read(local);
        assertAll(() -> assertTrue(restored.reduceMotion), () -> assertTrue(restored.hideFaces),
                () -> assertTrue(restored.blockFlashes), () -> assertFalse(restored.grainEnabled),
                () -> assertFalse(restored.afterimagesEnabled), () -> assertFalse(restored.lightFlickerEnabled),
                () -> assertEquals(0.25f, restored.suddenSoundVolume),
                () -> assertEquals(0.6f, restored.ambientSoundVolume),
                () -> assertEquals("{\"eventFrequency\":0.25}", Files.readString(gameplay)));
        try (var files = Files.list(directory)) {
            assertEquals(2L, files.count(), "A completed atomic save leaves no temporary file");
        }
    }

    @Test
    void missingKeysKeepNeutralDefaultsAndReadDoesNotCreateAFile() throws IOException {
        Path local = directory.resolve("penumbra-client.json");
        assertEquals(1.0f, ClientComfortSettings.read(local).ambientSoundVolume);
        assertFalse(Files.exists(local));
        Files.writeString(local, "{\"hideFaces\":true}");
        ClientComfortSettings restored = ClientComfortSettings.read(local);
        assertTrue(restored.hideFaces);
        assertFalse(restored.blockFlashes);
        assertTrue(restored.grainEnabled);
        assertEquals(1.0f, restored.suddenSoundVolume);
    }

    @Test
    void invalidInputIsReportedWithoutOverwritingIt() throws IOException {
        Path local = directory.resolve("penumbra-client.json");
        for (String invalid : new String[]{"[]", "{", "{\"hideFaces\":\"false\"}", "{\"ambientSoundVolume\":null}"}) {
            Files.writeString(local, invalid);
            assertThrows(IOException.class, () -> ClientComfortSettings.read(local));
            assertEquals(invalid, Files.readString(local));
        }
    }

    @Test
    void volumesAreBoundedAndSavingDoesNotMutateTheDraft() throws IOException {
        Path local = directory.resolve("penumbra-client.json");
        Files.writeString(local, "{\"suddenSoundVolume\":-4,\"ambientSoundVolume\":9}");
        ClientComfortSettings restored = ClientComfortSettings.read(local);
        assertEquals(0.0f, restored.suddenSoundVolume);
        assertEquals(1.0f, restored.ambientSoundVolume);
        restored.suddenSoundVolume = Float.NaN;
        restored.ambientSoundVolume = Float.POSITIVE_INFINITY;
        ClientComfortSettings.write(local, restored);
        assertTrue(Float.isNaN(restored.suddenSoundVolume));
        assertEquals(1.0f, ClientComfortSettings.read(local).suddenSoundVolume);
        assertEquals(1.0f, ClientComfortSettings.read(local).ambientSoundVolume);
    }

    @Test
    void failedReplacementKeepsExistingTargetAndCleansTemporaryFile() throws IOException {
        Path target = directory.resolve("penumbra-client.json");
        Files.createDirectory(target);
        Files.writeString(target.resolve("keep.txt"), "original");
        assertThrows(IOException.class, () -> ClientComfortSettings.write(target, new ClientComfortSettings()));
        assertEquals("original", Files.readString(target.resolve("keep.txt")));
        try (var files = Files.list(directory)) {
            assertEquals(1L, files.count());
        }
    }

    @Test
    void editingCopyDoesNotChangeOriginal() {
        ClientComfortSettings original = new ClientComfortSettings();
        ClientComfortSettings draft = original.copy();
        draft.hideFaces = true;
        draft.ambientSoundVolume = 0;
        assertFalse(original.hideFaces);
        assertEquals(1.0f, original.ambientSoundVolume);
    }
}

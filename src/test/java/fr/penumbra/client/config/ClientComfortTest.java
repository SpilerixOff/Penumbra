package fr.penumbra.client.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClientComfortTest {
    @Test void localPreferencesCannotEnableAnEffectDisabledInBaseConfiguration() {
        var settings = new ClientComfortSettings();
        assertFalse(ClientComfort.flashesEnabled(false, settings));
        assertFalse(ClientComfort.grainEnabled(false, settings));
        assertFalse(ClientComfort.afterimagesEnabled(false, settings));
        assertFalse(ClientComfort.lightFlickerEnabled(false, settings));
        assertTrue(ClientComfort.flashesEnabled(true, settings));
        assertTrue(ClientComfort.grainEnabled(true, settings));
    }

    @Test void reducedMotionDisablesAnimatedOverlaysEvenWhenIndividualTogglesAreOn() {
        var settings = new ClientComfortSettings();
        settings.reduceMotion = true;
        assertFalse(ClientComfort.flashesEnabled(true, settings));
        assertFalse(ClientComfort.grainEnabled(true, settings));
        assertFalse(ClientComfort.afterimagesEnabled(true, settings));
        assertFalse(ClientComfort.lightFlickerEnabled(true, settings));
        // Applying a restriction must not overwrite a user's individual preferences.
        assertTrue(settings.grainEnabled);
        assertTrue(settings.afterimagesEnabled);
        assertTrue(settings.lightFlickerEnabled);
    }

    @Test void soundReductionNeverAmplifiesAndHandlesInvalidNumbers() {
        assertEquals(0.4f, ClientComfort.scaleVolume(0.8f, 0.5f));
        assertEquals(0.8f, ClientComfort.scaleVolume(0.8f, 5.0f));
        assertEquals(0.0f, ClientComfort.scaleVolume(0.8f, -1.0f));
        assertEquals(0.0f, ClientComfort.scaleVolume(0.8f, Float.NaN));
        assertEquals(0.0f, ClientComfort.scaleVolume(Float.POSITIVE_INFINITY, 1.0f));
    }
}

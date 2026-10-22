package fr.penumbra.client.config;

import fr.penumbra.client.ambient.AmbientFearManager;
import fr.penumbra.client.jumpscare.JumpscareManager;
import fr.penumbra.config.PenumbraConfig;

/** Personal restrictions on presentation. Never writes the shared gameplay configuration. */
public final class ClientComfort {
    private ClientComfort() { }

    public static boolean motionAllowed() {
        return !ClientComfortSettings.get().reduceMotion;
    }

    public static boolean flashesEnabled() {
        return flashesEnabled(PenumbraConfig.get().flashesEnabled, ClientComfortSettings.get());
    }

    static boolean flashesEnabled(boolean configured, ClientComfortSettings settings) {
        return configured && !settings.blockFlashes && !settings.reduceMotion;
    }

    public static boolean facesEnabled() {
        return PenumbraConfig.get().screamersEnabled && !ClientComfortSettings.get().hideFaces;
    }

    public static boolean grainEnabled() {
        return grainEnabled(PenumbraConfig.get().perceptionGrainEnabled, ClientComfortSettings.get());
    }

    static boolean grainEnabled(boolean configured, ClientComfortSettings settings) {
        return configured && settings.grainEnabled && !settings.reduceMotion;
    }

    public static boolean afterimagesEnabled() {
        return afterimagesEnabled(PenumbraConfig.get().peripheralAfterimagesEnabled,
                ClientComfortSettings.get());
    }

    static boolean afterimagesEnabled(boolean configured, ClientComfortSettings settings) {
        return configured && settings.afterimagesEnabled && !settings.reduceMotion;
    }

    public static boolean lightFlickerEnabled() {
        return lightFlickerEnabled(PenumbraConfig.get().dynamicLightFlicker,
                ClientComfortSettings.get());
    }

    static boolean lightFlickerEnabled(boolean configured, ClientComfortSettings settings) {
        return configured && settings.lightFlickerEnabled && !settings.reduceMotion;
    }

    public static float suddenVolume(float original) {
        return scaleVolume(original, ClientComfortSettings.get().suddenSoundVolume);
    }

    public static float ambientVolume(float original) {
        return scaleVolume(original, ClientComfortSettings.get().ambientSoundVolume);
    }

    static float scaleVolume(float original, float scale) {
        if (!Float.isFinite(original) || original <= 0.0f) return 0.0f;
        float bounded = Float.isFinite(scale) ? Math.clamp(scale, 0.0f, 1.0f) : 0.0f;
        return original * bounded;
    }

    /** Apply reductions immediately, even while a solo world is paused in this screen. */
    public static void onSettingsChanged() {
        JumpscareManager.onComfortChanged();
        AmbientFearManager.onComfortChanged();
    }
}

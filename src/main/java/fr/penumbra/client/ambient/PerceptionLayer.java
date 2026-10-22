package fr.penumbra.client.ambient;

import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.client.config.ClientComfort;
import fr.penumbra.director.TensionState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** Client grain, scanlines and tension-driven veil pulse. No creature HUD images. */
@Environment(EnvType.CLIENT)
public final class PerceptionLayer {

    // ——— Grain ———
    /** Nombre maximal de grains dessinés (atteint seulement en CRISIS). */
    private static final int MAX_GRAIN = 46;
    /** Les positions ne sont retirées que tous les trois ticks (anti-scintillement). */
    private static final int GRAIN_REFRESH_TICKS = 3;
    private static final int GRAIN_MAX_ALPHA = 0x24;

    // ——— Lignes de balayage ———
    private static final int SCANLINE_SPACING = 4;
    private static final int SCANLINE_MAX_ALPHA = 0x16;

    private static final RandomSource RANDOM = RandomSource.create();

    /** Coordonnées normalisées des grains, en fractions de l'écran. */
    private static final float[] GRAIN_X = new float[MAX_GRAIN];
    private static final float[] GRAIN_Y = new float[MAX_GRAIN];
    private static final int[] GRAIN_SIZE = new int[MAX_GRAIN];

    private static int grainCountdown;
    /** Intensité lissée 0–1 du grain, dérivée de l'état de tension. */
    private static float grainIntensity;
    /** Multiplicateur 0.85–1.15 de la pulsation cardiaque du voile. */
    private static volatile float veilPulse = 1.0f;
    private static float pulsePhase;

    static {
        randomizeGrain();
    }

    private PerceptionLayer() {
    }

    // ===================== Tick =====================

    /**
     * Appelé par {@link AmbientFearManager#tick} après la mise à jour de l'état,
     * pour ne pas ajouter un second abonné au tick client.
     */
    static void tick(Minecraft client, LocalPlayer player, TensionState state) {
        PenumbraConfig config = PenumbraConfig.get();
        float strength = Math.max(0.0f, config.perceptionIntensity);

        grainIntensity = ClientComfort.grainEnabled()
                ? Math.min(1.6f, grainTarget(state) * strength) : 0.0f;
        if (--grainCountdown <= 0) {
            grainCountdown = GRAIN_REFRESH_TICKS;
            randomizeGrain();
        }

        // Pulsation du voile calée sur le battement (40 ticks) en CRISIS seulement.
        if (state == TensionState.CRISIS && config.fogEnabled && ClientComfort.motionAllowed()) {
            pulsePhase += Mth.TWO_PI / 40.0f;
            veilPulse = 1.0f + Mth.sin(pulsePhase) * 0.15f * Math.min(1.0f, strength);
        } else {
            pulsePhase = 0.0f;
            veilPulse = 1.0f;
        }

    }

    /** Réinitialisation complète (déconnexion, changement de monde). */
    static void reset() {
        grainIntensity = 0.0f;
        veilPulse = 1.0f;
        pulsePhase = 0.0f;
    }

    /** Cible du grain par état : rien avant UNEASE, maximal en CRISIS. */
    private static float grainTarget(TensionState state) {
        return switch (state) {
            case CALM -> 0.0f;
            case UNEASE -> 0.18f;
            case WATCHED -> 0.36f;
            case MANIFESTATION -> 0.58f;
            case HUNT -> 0.82f;
            case CRISIS -> 1.0f;
            // Le faux calme est propre : c'est ce qui le rend menteur.
            case FALSE_CALM -> 0.0f;
        };
    }

    private static void randomizeGrain() {
        for (int index = 0; index < MAX_GRAIN; index++) {
            GRAIN_X[index] = RANDOM.nextFloat();
            GRAIN_Y[index] = RANDOM.nextFloat();
            GRAIN_SIZE[index] = 1 + RANDOM.nextInt(2);
        }
    }

    /** Multiplicateur de pulsation lu chaque frame par {@code TensionVeilRenderer}. */
    public static float getVeilPulse() {
        return veilPulse;
    }

    // ===================== Rendu =====================

    /** Intensité courante du grain (0 si coupé), lue par le renderer. */
    static float grainIntensity() {
        return grainIntensity;
    }

    static int grainCount() {
        return Math.round(MAX_GRAIN * Math.min(1.0f, grainIntensity));
    }

    static float grainX(int index) {
        return GRAIN_X[index];
    }

    static float grainY(int index) {
        return GRAIN_Y[index];
    }

    static int grainSize(int index) {
        return GRAIN_SIZE[index];
    }

    static int grainAlpha() {
        return Mth.clamp(Math.round(GRAIN_MAX_ALPHA * grainIntensity), 0, 0xFF);
    }

    static int scanlineSpacing() {
        return SCANLINE_SPACING;
    }

    static int scanlineAlpha() {
        // Les lignes n'apparaissent qu'à partir de MANIFESTATION (grain ≥ 0.5).
        float ramp = Math.max(0.0f, (grainIntensity - 0.5f) * 2.0f);
        return Mth.clamp(Math.round(SCANLINE_MAX_ALPHA * ramp), 0, 0xFF);
    }

}

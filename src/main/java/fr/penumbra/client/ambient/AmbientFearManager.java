package fr.penumbra.client.ambient;

import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.client.config.ClientComfort;
import fr.penumbra.director.TensionState;
import fr.penumbra.registry.PenumbraSounds;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Couche ambiante de Penumbra : sons suivis, voile et dérive de FOV.
 * <ul>
 *   <li><b>Nappe sonore</b> ({@code improvedSoundsEnabled}) : segments de 8 s,
 *       renouvelés toutes les 5–6 s par fondu croisé. Au maximum deux voix,
 *       toutes arrêtées à l'entrée en FALSE_CALM ;</li>
 *   <li><b>battement cardiaque</b> en CRISIS uniquement (échantillon 2 s rejoué
 *       toutes les 2 s, volume 0.12, une seule voix suivie) ;</li>
 *   <li><b>voile périphérique</b> ({@code fogEnabled}) : cible d'intensité
 *       lissée (lerp 5 %/tick) lue par {@code TensionVeilRenderer} — la vision
 *       se rétrécit sans que rien ne l'annonce ;</li>
 *   <li><b>couche de perception</b> (0.17.0, {@link PerceptionLayer}) : grain,
 *       lignes de balayage et silhouette rémanente périphérique, tickés ici ;</li>
 *   <li><b>dérive de FOV</b> ({@code distortionEnabled}) : oscillation lente
 *       ±0,4 % (HUNT) à ±0,8 % (CRISIS) composée dans {@code CameraFovMixin}
 *       par-dessus le zoom des jumelles — le monde respire à peine faux.</li>
 * </ul>
 * Les voix relatives au joueur respectent la catégorie AMBIENT. Elles sont
 * détruites à la déconnexion et au changement de monde/joueur ; la pause gèle
 * leur horloge comme celle du moteur sonore vanilla.
 */
@Environment(EnvType.CLIENT)
public final class AmbientFearManager {

    /** Intervalle de base entre deux segments de nappe (5 s ; segments de 8 s). */
    private static final int BED_INTERVAL_TICKS = 100;
    /** Gigue aléatoire ajoutée à l'intervalle (anti-mécanique). */
    private static final int BED_INTERVAL_JITTER = 20;
    /** Vitesse de lissage du voile périphérique (par tick). */
    private static final float VEIL_LERP = 0.05f;

    private static final RandomSource RANDOM = RandomSource.create();

    private static final AmbientAudioController AUDIO = new AmbientAudioController(
            AmbientFearManager::startVoice,
            () -> BED_INTERVAL_TICKS + RANDOM.nextInt(BED_INTERVAL_JITTER + 1),
            () -> 0.98f + RANDOM.nextFloat() * 0.04f);
    private static ClientLevel activeLevel;
    private static LocalPlayer activePlayer;
    /** Intensité affichée du voile périphérique (0–1), lissée. */
    private static float veilIntensity;
    /** Phase de la dérive de FOV (avance à chaque tick en HUNT+). */
    private static float fovDriftPhase;
    /** Facteur de FOV appliqué par {@code CameraFovMixin} (1.0 = neutre). */
    private static volatile float fovDriftFactor = 1.0f;
    /** 0.6.0 : dernier état vu par la coupure de musique (détection de transition). */
    private static TensionState lastMusicState = TensionState.CALM;
    private static final SceneHush HUSH = new SceneHush();

    private AmbientFearManager() {
    }

    /** Enregistre le tick client. Appelé par {@code PenumbraClient.onInitializeClient}. */
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(AmbientFearManager::tick);
        ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> client.execute(() -> reset(true)));
    }

    private static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            reset(true);
            return;
        }
        if (activeLevel != client.level || activePlayer != player) {
            // Ne pas effacer une première synchro reçue juste avant le premier
            // tick du monde. Les anciens contextes, eux, ne doivent pas fuir.
            reset(activeLevel != null || activePlayer != null);
            activeLevel = client.level;
            activePlayer = player;
        }
        boolean audioEnabled = PenumbraConfig.get().improvedSoundsEnabled && player.isAlive()
                && ClientComfort.ambientVolume(1.0f) > 0.0f && !HUSH.active();
        if (client.isPaused()) {
            AUDIO.tick(ClientTensionState.state(), audioEnabled, true);
            return; // Ni expiration de synchro, ni variation visuelle durant une vraie pause.
        }
        ClientTensionState.tick();
        TensionState state = ClientTensionState.state();
        AUDIO.tick(state, audioEnabled, false);
        HUSH.tick();
        tickVeil(state);
        tickFovDrift(state);
        tickMusicCut(client, state);
        // Grain, lignes de balayage et pulsation du voile ; aucune silhouette HUD.
        // Branché ici plutôt que sur un second abonné END_CLIENT_TICK.
        PerceptionLayer.tick(client, player, state);
    }

    private static void reset(boolean resetTension) {
        AUDIO.reset();
        HUSH.reset();
        activeLevel = null;
        activePlayer = null;
        if (resetTension) {
            ClientTensionState.reset();
        }
        veilIntensity = 0.0f;
        fovDriftPhase = 0.0f;
        fovDriftFactor = 1.0f;
        lastMusicState = TensionState.CALM;
        PerceptionLayer.reset();
    }

    /** Recreate only Penumbra's tracked voices at the new gain, without clearing server tension. */
    public static void onComfortChanged() {
        AUDIO.reset();
        fovDriftPhase = 0.0f;
        fovDriftFactor = 1.0f;
        PerceptionLayer.reset();
    }

    /** Preserve steps and cries: only our owned ambient loops are interrupted. */
    public static void hush(int ticks) {
        HUSH.begin(ticks);
        if (HUSH.active()) AUDIO.reset();
    }

    /**
     * 0.6.0 : coupure de musique — à l'ENTRÉE en HUNT ou CRISIS, la musique
     * vanilla en cours s'arrête net ({@code MusicManager.stopPlaying()},
     * vérifié par désassemblage du jar 26.1.2). Le silence soudain est un
     * jumpscare que le joueur s'inflige lui-même en le remarquant. Une seule
     * coupure par transition ; le silence ne fait peur que s'il est rare.
     */
    private static void tickMusicCut(Minecraft client, TensionState state) {
        if (state == lastMusicState) {
            return;
        }
        boolean entering = (state == TensionState.HUNT || state == TensionState.CRISIS)
                && lastMusicState != TensionState.HUNT && lastMusicState != TensionState.CRISIS;
        lastMusicState = state;
        if (entering && PenumbraConfig.get().improvedSoundsEnabled) {
            client.getMusicManager().stopPlaying();
        }
    }

    // ================= Voile périphérique ({@code fogEnabled}) =================

    private static void tickVeil(TensionState state) {
        float target = PenumbraConfig.get().fogEnabled ? veilTarget(state) : 0f;
        veilIntensity += (target - veilIntensity) * VEIL_LERP;
        if (Math.abs(veilIntensity - target) < 0.002f) {
            veilIntensity = target;
        }
    }

    /** Cible du voile par état : rien avant MANIFESTATION, maximal en CRISIS. */
    private static float veilTarget(TensionState state) {
        return switch (state) {
            case MANIFESTATION -> 0.35f;
            case HUNT -> 0.65f;
            case CRISIS -> 1.0f;
            default -> 0f;
        };
    }

    /** Intensité lissée 0–1 du voile, lue chaque frame par {@code TensionVeilRenderer}. */
    public static float getVeilIntensity() {
        return veilIntensity;
    }

    // ================= Dérive de FOV ({@code distortionEnabled}) =================

    private static void tickFovDrift(TensionState state) {
        float amplitude = switch (state) {
            case HUNT -> 0.004f;
            case CRISIS -> 0.008f;
            default -> 0f;
        };
        if (amplitude <= 0f || !PenumbraConfig.get().distortionEnabled
                || !ClientComfort.motionAllowed()) {
            fovDriftFactor = 1.0f;
            return;
        }
        fovDriftPhase += 0.011f; // période ≈ 28 s : une houle, pas un tremblement
        fovDriftFactor = 1.0f + Mth.sin(fovDriftPhase) * amplitude;
    }

    /**
     * Facteur de dérive de FOV composé par {@code CameraFovMixin} avec le zoom
     * des jumelles. Vaut exactement {@code 1.0f} hors HUNT/CRISIS ou si
     * {@code distortionEnabled} est faux.
     */
    public static float getFovDriftFactor() {
        return ClientComfort.motionAllowed() ? fovDriftFactor : 1.0f;
    }

    private static AmbientAudioController.Voice startVoice(AmbientAudioController.Cue cue,
                                                           float volume, float pitch,
                                                           int fadeTicks, int maximumTicks) {
        SoundEvent sound = switch (cue) {
            case LOW -> PenumbraSounds.AMBIENT_BED_LOW;
            case MID -> PenumbraSounds.AMBIENT_BED_MID;
            case HIGH -> PenumbraSounds.AMBIENT_BED_HIGH;
            case HEARTBEAT -> PenumbraSounds.HEARTBEAT;
        };
        Minecraft client = Minecraft.getInstance();
        TrackedAmbientSound voice = new TrackedAmbientSound(client, sound, volume, pitch,
                fadeTicks, maximumTicks);
        client.getSoundManager().play(voice);
        return voice;
    }
}

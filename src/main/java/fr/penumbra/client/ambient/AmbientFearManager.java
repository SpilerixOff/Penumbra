package fr.penumbra.client.ambient;

import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.director.TensionState;
import fr.penumbra.registry.PenumbraSounds;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Couche ambiante de Penumbra (0.5.0) — branche enfin {@code improvedSoundsEnabled},
 * {@code fogEnabled} et {@code distortionEnabled} sur du réel :
 * <ul>
 *   <li><b>Nappe sonore</b> ({@code improvedSoundsEnabled}) : segments de 8 s à
 *       fondus intégrés, rejoués toutes les ~5 s (crossfade par recouvrement),
 *       choisis et dosés par l'état — inaudibles consciemment, mais le corps
 *       les entend. FALSE_CALM = silence TOTAL : le faux calme n'est crédible
 *       que si plus rien ne vibre ;</li>
 *   <li><b>battement cardiaque</b> en CRISIS uniquement (échantillon 2 s rejoué
 *       toutes les 2 s, volume 0.12 — subliminal, jamais un métronome) ;</li>
 *   <li><b>voile périphérique</b> ({@code fogEnabled}) : cible d'intensité
 *       lissée (lerp 5 %/tick) lue par {@code TensionVeilRenderer} — la vision
 *       se rétrécit sans que rien ne l'annonce ;</li>
 *   <li><b>couche de perception</b> (0.17.0, {@link PerceptionLayer}) : grain,
 *       lignes de balayage et silhouette rémanente périphérique, tickés ici ;</li>
 *   <li><b>dérive de FOV</b> ({@code distortionEnabled}) : oscillation lente
 *       ±0,4 % (HUNT) à ±0,8 % (CRISIS) composée dans {@code CameraFovMixin}
 *       par-dessus le zoom des jumelles — le monde respire à peine faux.</li>
 * </ul>
 * Aucun nouvel accès API : uniquement {@code playLocalSound} (pattern
 * {@code JumpscareManager}) et le mixin Camera déjà vérifié en 26.1.2.
 */
@Environment(EnvType.CLIENT)
public final class AmbientFearManager {

    /** Intervalle de base entre deux segments de nappe (5 s ; segments de 8 s). */
    private static final int BED_INTERVAL_TICKS = 100;
    /** Gigue aléatoire ajoutée à l'intervalle (anti-mécanique). */
    private static final int BED_INTERVAL_JITTER = 20;
    /** Intervalle du battement cardiaque en CRISIS (l'échantillon dure 2 s). */
    private static final int HEARTBEAT_INTERVAL_TICKS = 40;
    /** Volume du battement cardiaque en CRISIS (subliminal). */
    private static final float HEARTBEAT_VOLUME = 0.12f;
    /** Vitesse de lissage du voile périphérique (par tick). */
    private static final float VEIL_LERP = 0.05f;

    private static final RandomSource RANDOM = RandomSource.create();

    private static int bedCountdown = BED_INTERVAL_TICKS;
    private static int heartbeatCountdown;
    /** Intensité affichée du voile périphérique (0–1), lissée. */
    private static float veilIntensity;
    /** Phase de la dérive de FOV (avance à chaque tick en HUNT+). */
    private static float fovDriftPhase;
    /** Facteur de FOV appliqué par {@code CameraFovMixin} (1.0 = neutre). */
    private static volatile float fovDriftFactor = 1.0f;
    /** 0.6.0 : dernier état vu par la coupure de musique (détection de transition). */
    private static TensionState lastMusicState = TensionState.CALM;

    private AmbientFearManager() {
    }

    /** Enregistre le tick client. Appelé par {@code PenumbraClient.onInitializeClient}. */
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(AmbientFearManager::tick);
    }

    private static void tick(Minecraft client) {
        ClientTensionState.tick();
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            ClientTensionState.reset();
            veilIntensity = 0f;
            fovDriftFactor = 1.0f;
            bedCountdown = BED_INTERVAL_TICKS;
            PerceptionLayer.reset();
            return;
        }
        TensionState state = ClientTensionState.state();
        tickBed(client, state);
        tickHeartbeat(client, player, state);
        tickVeil(state);
        tickFovDrift(state);
        tickMusicCut(client, state);
        // 0.17.0 : grain, lignes de balayage, rémanence et pulsation du voile.
        // Branché ici plutôt que sur un second abonné END_CLIENT_TICK.
        PerceptionLayer.tick(client, player, state);
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

    // ================= Nappe sonore =================

    private static void tickBed(Minecraft client, TensionState state) {
        if (--bedCountdown > 0) {
            return;
        }
        bedCountdown = BED_INTERVAL_TICKS + RANDOM.nextInt(BED_INTERVAL_JITTER + 1);
        if (!PenumbraConfig.get().improvedSoundsEnabled || client.isPaused()) {
            return;
        }
        SoundEvent bed = bedFor(state);
        if (bed == null) {
            return; // CALM/UNEASE : rien ; FALSE_CALM : silence total voulu
        }
        float volume = bedVolume(state);
        float pitch = 0.98f + RANDOM.nextFloat() * 0.04f;
        playLocal(client, bed, volume, pitch);
    }

    /** Nappe par état — null quand le silence EST l'effet. */
    private static SoundEvent bedFor(TensionState state) {
        return switch (state) {
            case CALM, UNEASE, FALSE_CALM -> null;
            case WATCHED -> PenumbraSounds.AMBIENT_BED_LOW;
            case MANIFESTATION -> PenumbraSounds.AMBIENT_BED_MID;
            case HUNT, CRISIS -> PenumbraSounds.AMBIENT_BED_HIGH;
        };
    }

    private static float bedVolume(TensionState state) {
        return switch (state) {
            case WATCHED -> 0.10f;
            case MANIFESTATION -> 0.16f;
            case HUNT -> 0.22f;
            case CRISIS -> 0.30f;
            default -> 0f;
        };
    }

    // ================= Battement cardiaque (CRISIS) =================

    private static void tickHeartbeat(Minecraft client, LocalPlayer player, TensionState state) {
        if (state != TensionState.CRISIS || !PenumbraConfig.get().improvedSoundsEnabled) {
            heartbeatCountdown = 0;
            return;
        }
        if (--heartbeatCountdown > 0) {
            return;
        }
        heartbeatCountdown = HEARTBEAT_INTERVAL_TICKS;
        if (!client.isPaused()) {
            playLocal(client, PenumbraSounds.HEARTBEAT, HEARTBEAT_VOLUME, 0.95f);
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
        if (amplitude <= 0f || !PenumbraConfig.get().distortionEnabled) {
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
        return fovDriftFactor;
    }

    /** Joue un son en local à la position du joueur (pattern {@code JumpscareManager}). */
    private static void playLocal(Minecraft client, SoundEvent sound, float volume, float pitch) {
        if (client.level == null || client.player == null) {
            return;
        }
        client.level.playLocalSound(client.player.getX(), client.player.getY(), client.player.getZ(),
                sound, SoundSource.AMBIENT, volume, pitch, false);
    }
}

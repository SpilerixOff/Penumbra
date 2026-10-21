package fr.penumbra.registry;

import fr.penumbra.PenumbraMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

/**
 * Registre des événements sonores de Penumbra (SPEC §4.4, SPEC2 §2).
 * <p>
 * Phase 1 : 10 sons. Phase 2 : +7 sons (jumelles, leurres, poursuite, variantes).
 * Enregistrement statique simple ; les fichiers .ogg correspondants sont déclarés dans
 * {@code assets/penumbra/sounds.json}. {@link #register()} ne fait rien : elle force le
 * chargement statique de la classe au démarrage du mod.
 * </p>
 */
public final class PenumbraSounds {

    public static final SoundEvent HOST_WHISPER = reg("host_whisper");
    public static final SoundEvent HOST_STINGER = reg("host_stinger");
    public static final SoundEvent HOST_STEP = reg("host_step");
    public static final SoundEvent FOOTSTEP_MIMIC = reg("footstep_mimic");
    public static final SoundEvent BREATH_BEHIND = reg("breath_behind");
    public static final SoundEvent DOOR_GROAN = reg("door_groan");
    public static final SoundEvent FAKE_CREEPER = reg("fake_creeper");
    public static final SoundEvent TORCH_SPUTTER = reg("torch_sputter");
    public static final SoundEvent SILENCE_DRONE = reg("silence_drone");
    public static final SoundEvent HEARTBEAT = reg("heartbeat");

    // Phase 2 (SPEC2 §2) : jumelles, leurres, poursuite, variantes.
    public static final SoundEvent BINOCULARS_ON = reg("binoculars_on");
    public static final SoundEvent BINOCULARS_OFF = reg("binoculars_off");
    public static final SoundEvent LURE_DOOR = reg("lure_door");
    public static final SoundEvent DISTANT_HUM = reg("distant_hum");
    public static final SoundEvent CHASE_STINGER = reg("chase_stinger");
    public static final SoundEvent FOOTSTEP_MIMIC_2 = reg("footstep_mimic_2");
    public static final SoundEvent BREATH_BEHIND_2 = reg("breath_behind_2");

    // Phase 5 (0.5.0) : nappes ambiantes pilotées par l'état, variantes, Originel, rituel.
    public static final SoundEvent AMBIENT_BED_LOW = reg("ambient_bed_low");
    public static final SoundEvent AMBIENT_BED_MID = reg("ambient_bed_mid");
    public static final SoundEvent AMBIENT_BED_HIGH = reg("ambient_bed_high");
    public static final SoundEvent HOST_WHISPER_2 = reg("host_whisper_2");
    public static final SoundEvent ORIGINEL_HUM = reg("originel_hum");
    public static final SoundEvent RITUAL_RELEASE = reg("ritual_release");

    // 0.6.0 : screamers (hurlement du visage plein écran, voix-leurre du minage).
    public static final SoundEvent SCREAMER_SHRIEK = reg("screamer_shriek");
    public static final SoundEvent VOICE_HEY = reg("voice_hey");

    // 0.16.0 : berceuse du disque « Mémoire » (jukebox, streamée).
    public static final SoundEvent MEMOIRE_DISC = reg("memoire_disc");

    // 0.17.0 : L'Écouteur — écholocation, écoute, verrouillage, renoncement.
    public static final SoundEvent LISTENER_CLICK = reg("listener_click");
    public static final SoundEvent LISTENER_INHALE = reg("listener_inhale");
    public static final SoundEvent LISTENER_LOCK = reg("listener_lock");
    public static final SoundEvent LISTENER_LEAVE = reg("listener_leave");

    // 0.17.0 : événements sonores du Directeur.
    /** Le monde inspire puis se tait complètement (held_breath). */
    public static final SoundEvent HELD_BREATH = reg("held_breath");
    /** Le pas de trop, décalé d'une fraction de seconde (wrong_footfall). */
    public static final SoundEvent EXTRA_STEP = reg("extra_step");

    // 0.18.0 : le Relevé.
    /** Page tournée : froissement bref, sans résonance. */
    public static final SoundEvent PAGE_TURN = reg("page_turn");

    private PenumbraSounds() {
    }

    /**
     * Variante de murmure (0.5.0) : {@code HOST_WHISPER_2} une fois sur deux si
     * {@code improvedSoundsEnabled}, sinon toujours {@code HOST_WHISPER}.
     * La familiarité tue la peur : deux murmures valent mieux qu'un.
     */
    public static SoundEvent whisperVariant(net.minecraft.util.RandomSource random) {
        if (fr.penumbra.config.PenumbraConfig.get().improvedSoundsEnabled && random.nextBoolean()) {
            return HOST_WHISPER_2;
        }
        return HOST_WHISPER;
    }

    /**
     * Enregistre un SoundEvent à portée variable sous l'id {@code penumbra:<name>}.
     * Vérifié en 26.1.2 : {@code SoundEvent.of} n'existe plus, le nom officiel est
     * {@code createVariableRangeEvent(Identifier)}.
     */
    private static SoundEvent reg(String name) {
        return Registry.register(BuiltInRegistries.SOUND_EVENT, PenumbraMod.id(name),
                SoundEvent.createVariableRangeEvent(PenumbraMod.id(name)));
    }

    /**
     * Force le chargement statique (appelée par {@code PenumbraMod.onInitialize}).
     */
    public static void register() {
    }
}

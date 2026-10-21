package fr.penumbra.client.jumpscare;

import fr.penumbra.PenumbraMod;
import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.registry.PenumbraSounds;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import java.util.Random;

/**
 * Gestionnaire client des jumpscares.
 *
 * <p>Les effets ordinaires et les intrusions visuelles critiques ont des cooldowns
 * séparés : un sting faible ne peut plus faire disparaître un visage narratif,
 * tandis qu'un verrou critique court évite tout spam. Les options d'accessibilité
 * continuent de piloter indépendamment flash, tremblement et visage.</p>
 */
@Environment(EnvType.CLIENT)
public final class JumpscareManager {

    private static final int NORMAL_COOLDOWN_TICKS = 400;
    private static final int CRITICAL_COOLDOWN_TICKS = 100;
    private static final JumpscareCooldownGate COOLDOWNS =
            new JumpscareCooldownGate(NORMAL_COOLDOWN_TICKS, CRITICAL_COOLDOWN_TICKS);

    private static final RandomSource RANDOM = RandomSource.create();

    /**
     * Les quinze visages disponibles. Les six derniers restent accessibles par
     * leur déclencheur narratif, mais rejoignent aussi la rotation générale afin
     * que deux poursuites successives ne recyclent pas toujours les mêmes plans.
     */
    private static final Identifier[] SCREAMER_TEXTURES = {
            PenumbraMod.id("textures/gui/screamer_host.png"),
            PenumbraMod.id("textures/gui/screamer_host_2.png"),
            PenumbraMod.id("textures/gui/screamer_host_3.png"),
            PenumbraMod.id("textures/gui/screamer_host_4.png"),
            PenumbraMod.id("textures/gui/screamer_host_5.png"),
            PenumbraMod.id("textures/gui/screamer_host_6.png"),
            PenumbraMod.id("textures/gui/screamer_host_7.png"),
            PenumbraMod.id("textures/gui/screamer_host_8.png"),
            PenumbraMod.id("textures/gui/screamer_host_9.png"),
            PenumbraMod.id("textures/gui/screamer_rampant.png"),
            PenumbraMod.id("textures/gui/screamer_absent.png"),
            PenumbraMod.id("textures/gui/screamer_chorus.png"),
            PenumbraMod.id("textures/gui/screamer_refuge.png"),
            PenumbraMod.id("textures/gui/screamer_mimic.png"),
            // 0.17.0 : le visage sans yeux de L'Écouteur, oreilles grandes ouvertes.
            PenumbraMod.id("textures/gui/screamer_listener.png")
    };

    private static final int RAMPANT_VARIANT = 9;
    private static final int ABSENT_VARIANT = 10;
    private static final int CHORUS_VARIANT = 11;
    private static final int REFUGE_VARIANT = 12;
    private static final int MIMIC_VARIANT = 13;
    private static final int LISTENER_VARIANT = 14;

    /** Cycle complet des quinze visages, sans répétition à la frontière des cycles. */
    private static final ShuffledIndexBag SCREAMER_VARIANTS =
            new ShuffledIndexBag(SCREAMER_TEXTURES.length, new Random());
    /** Rotation séparée des trois silhouettes réservées aux apparitions (4 à 6). */
    private static final ShuffledIndexBag APPARITION_VARIANTS =
            new ShuffledIndexBag(3, new Random());
    /** Rotation des styles de présentation (0.14.0) : jamais deux fois le même cadrage. */
    private static final ShuffledIndexBag PRESENTATION_STYLES =
            new ShuffledIndexBag(ScreamerPresentation.STYLE_COUNT, new Random());

    private static final int SCREAMER_FACE_DURATION_TICKS = 8;
    private static final int SCREAMER_FACE_FADE_TICKS = 3;
    private static final int LUNGE_FACE_DURATION_TICKS = 12;
    private static final int LUNGE_FACE_FADE_TICKS = 4;
    private static final int APPARITION_FACE_DURATION_TICKS = 6;
    private static final int APPARITION_FACE_FADE_TICKS = 4;
    private static final int APPARITION_FACE_MAX_ALPHA = 104;

    private static int shakeTicks;
    private static int shakeDuration;
    private static float shakeIntensity;
    /** Offsets du tick précédent, retirés avant d'appliquer le prochain shake. */
    private static float lastYawOffset;
    private static float lastPitchOffset;
    private static int flashTicks;

    private static int faceVariant;
    private static int faceTicks;
    private static int faceFadeTicks;
    private static int faceMaxAlpha;
    /** Style {@link ScreamerPresentation} du visage en cours. */
    private static int faceStyle = ScreamerPresentation.CLASSIC;
    /** Durée totale du visage en cours, pour dériver la progression. */
    private static int faceDurationTotal = 1;
    /** Côté du décalage horizontal des styles décentrés (-1 ou 1). */
    private static int faceOffsetSign = 1;
    /** Dernier visage montré, pour éviter un doublon entre les deux rotations. */
    private static int lastShownVariant = -1;

    static {
        HudElementRegistry.addLast(PenumbraMod.id("jumpscare_flash"), (extractor, deltaTracker) -> {
            if (flashTicks <= 0 || !PenumbraConfig.get().flashesEnabled) {
                return;
            }
            Minecraft client = Minecraft.getInstance();
            extractor.fill(0, 0,
                    client.getWindow().getGuiScaledWidth(),
                    client.getWindow().getGuiScaledHeight(),
                    0xFF000000);
        });

        HudElementRegistry.addLast(PenumbraMod.id("screamer_face"), (extractor, deltaTracker) -> {
            if (faceTicks <= 0 || !PenumbraConfig.get().screamersEnabled) {
                return;
            }
            int total = Math.max(1, faceDurationTotal);
            int tickIndex = total - faceTicks;
            // Certains styles (DOUBLE_CUT) intercalent un trou noir : le visage
            // reste « en cours » mais n'est pas dessiné à ce tick.
            if (!ScreamerPresentation.visibleAt(faceStyle, tickIndex, total)) {
                return;
            }
            Minecraft client = Minecraft.getInstance();
            int width = client.getWindow().getGuiScaledWidth();
            int height = client.getWindow().getGuiScaledHeight();

            int baseAlpha = faceTicks >= faceFadeTicks
                    ? faceMaxAlpha
                    : Math.round(faceMaxAlpha * faceTicks / (float) Math.max(1, faceFadeTicks));
            int alpha = ScreamerPresentation.alphaCap(faceStyle, baseAlpha);
            int color = (alpha << 24) | 0xFFFFFF;

            // Les textures sont carrées. Un carré de côté max(largeur, hauteur)
            // donne un vrai rendu « cover » : proportions intactes, surplus rogné
            // symétriquement, sans étirer le visage sur un écran 16:9. Le style
            // ne fait que grossir ou décaler ce carré (échelle toujours ≥ 1),
            // les bords de la texture ne deviennent donc jamais visibles.
            float progress = tickIndex / (float) total;
            float scale = ScreamerPresentation.scale(faceStyle, progress);
            int side = Math.round(Math.max(width, height) * scale);
            int left = (width - side) / 2
                    + Math.round(ScreamerPresentation.offsetXFraction(faceStyle, faceOffsetSign) * width);
            int top = (height - side) / 2
                    + Math.round(ScreamerPresentation.offsetYFraction(faceStyle) * height);
            extractor.blit(RenderPipelines.GUI_TEXTURED,
                    SCREAMER_TEXTURES[faceVariant], left, top, 0.0f, 0.0f,
                    side, side, side, side, color);
        });
    }

    private JumpscareManager() {
    }

    /**
     * Déclenche un effet reconnu ({@code sting}, {@code fake_creeper},
     * {@code apparition}, {@code screamer}, l'interne {@code lunge_screamer}
     * ou une finale nommée ({@code rampant_screamer},
     * {@code absent_screamer}, {@code chorus_screamer},
     * {@code refuge_screamer}, {@code mimic_screamer},
     * {@code listener_screamer}). Les effets qui affichent
     * un visage sont critiques et contournent donc le cooldown des sons ordinaires.
     */
    public static void trigger(String scareType, float intensity) {
        PenumbraConfig config = PenumbraConfig.get();
        if (config.jumpscareFrequency <= 0.0f) {
            return;
        }

        boolean namedFinal = "rampant_screamer".equals(scareType)
                || "absent_screamer".equals(scareType)
                || "chorus_screamer".equals(scareType)
                || "refuge_screamer".equals(scareType)
                || "mimic_screamer".equals(scareType)
                || "listener_screamer".equals(scareType);
        boolean known = "sting".equals(scareType) || "fake_creeper".equals(scareType)
                || "apparition".equals(scareType) || "screamer".equals(scareType)
                || "lunge_screamer".equals(scareType) || namedFinal;
        if (!known) {
            PenumbraMod.LOGGER.warn("[Penumbra] Type de jumpscare inconnu : {}", scareType);
            return;
        }

        float clamped = Float.isFinite(intensity)
                ? Math.max(0.0f, Math.min(1.0f, intensity)) : 0.0f;
        // Une intensité nulle est un no-op complet : aucun son, aucune image et
        // surtout aucun cooldown consommé pour le prochain événement réel.
        if (clamped <= 0.0f) {
            return;
        }
        boolean lungeFinal = "lunge_screamer".equals(scareType) || namedFinal;
        boolean critical = "screamer".equals(scareType)
                || "apparition".equals(scareType) || lungeFinal;
        if (!COOLDOWNS.tryAcquire(critical, lungeFinal)) {
            return;
        }

        float volume = Math.max(0.0f, Math.min(1.0f, config.jumpscareVolume * clamped));

        switch (scareType) {
            case "sting" -> {
                playLocal(PenumbraSounds.HOST_STINGER, volume, 1.0f);
                startShake(12, clamped, config);
                if (config.flashesEnabled) {
                    flashTicks = 3;
                }
            }
            case "fake_creeper" -> playLocal(PenumbraSounds.FAKE_CREEPER, volume, 1.0f);
            case "apparition" -> {
                playLocal(PenumbraSounds.HOST_WHISPER, volume * 0.7f, 0.9f);
                startShake(6, clamped * 0.5f, config);
                if (config.screamersEnabled) {
                    // Les indices 3, 4 et 5 correspondent aux fichiers 4, 5 et 6.
                    startFace(3 + APPARITION_VARIANTS.next(),
                            APPARITION_FACE_DURATION_TICKS,
                            APPARITION_FACE_FADE_TICKS,
                            APPARITION_FACE_MAX_ALPHA,
                            ScreamerPresentation.CLASSIC);
                }
            }
            case "screamer", "lunge_screamer", "rampant_screamer",
                    "absent_screamer", "chorus_screamer", "refuge_screamer",
                    "mimic_screamer", "listener_screamer" -> {
                if (config.screamersEnabled) {
                    // 0.14.0 : chaque screamer tire une mise en scène différente
                    // (cadrage, zoom, coupes, son) pour casser l'habituation.
                    int style = config.screamerVarietyEnabled
                            ? PRESENTATION_STYLES.next() : ScreamerPresentation.CLASSIC;
                    if (lungeFinal) {
                        style = ScreamerPresentation.finalizeForContact(style);
                    }
                    boolean whisper = ScreamerPresentation.whisperInsteadOfShriek(style);
                    if (whisper) {
                        // Subliminal : un souffle, pas un cri — le doute fait le travail.
                        playLocal(PenumbraSounds.HOST_WHISPER, volume * 0.6f, 0.82f);
                    } else {
                        playLocal(PenumbraSounds.SCREAMER_SHRIEK, volume,
                                ScreamerPresentation.shriekPitch(RANDOM.nextFloat(), lungeFinal));
                    }
                    startShake(ScreamerPresentation.shakeDurationTicks(style, lungeFinal ? 24 : 16),
                            whisper ? clamped * 0.5f : clamped, config);
                    if (lungeFinal && config.flashesEnabled) {
                        flashTicks = 2;
                    }
                    int variant = switch (scareType) {
                        case "rampant_screamer" -> RAMPANT_VARIANT;
                        case "absent_screamer" -> ABSENT_VARIANT;
                        case "chorus_screamer" -> CHORUS_VARIANT;
                        case "refuge_screamer" -> REFUGE_VARIANT;
                        case "mimic_screamer" -> MIMIC_VARIANT;
                        case "listener_screamer" -> LISTENER_VARIANT;
                        default -> nextGeneralVariant();
                    };
                    startFace(variant,
                            ScreamerPresentation.durationTicks(style,
                                    lungeFinal ? LUNGE_FACE_DURATION_TICKS : SCREAMER_FACE_DURATION_TICKS),
                            lungeFinal ? LUNGE_FACE_FADE_TICKS : SCREAMER_FACE_FADE_TICKS,
                            255, style);
                } else {
                    playLocal(PenumbraSounds.HOST_STINGER, volume, 1.0f);
                    startShake(12, clamped, config);
                }
            }
            default -> {
                // Le type a déjà été filtré avant l'acquisition du cooldown.
            }
        }
    }

    /** Tick client : compteurs d'affichage et offsets amortis de la caméra. */
    public static void tick(Minecraft client) {
        COOLDOWNS.tick();
        if (flashTicks > 0) {
            flashTicks--;
        }
        if (faceTicks > 0) {
            faceTicks--;
        }

        LocalPlayer player = client.player;
        if (player == null) {
            shakeTicks = 0;
            lastYawOffset = 0.0f;
            lastPitchOffset = 0.0f;
            return;
        }

        if (lastYawOffset != 0.0f || lastPitchOffset != 0.0f) {
            player.setYRot(player.getYRot() - lastYawOffset);
            player.setXRot(player.getXRot() - lastPitchOffset);
            lastYawOffset = 0.0f;
            lastPitchOffset = 0.0f;
        }

        if (shakeTicks <= 0) {
            return;
        }
        if (!PenumbraConfig.get().cameraShakeEnabled) {
            shakeTicks = 0;
            return;
        }
        float damping = (float) shakeTicks / (float) shakeDuration;
        float amplitude = 1.2f * shakeIntensity * damping;
        float yawOffset = (RANDOM.nextFloat() * 2.0f - 1.0f) * amplitude;
        float pitchOffset = (RANDOM.nextFloat() * 2.0f - 1.0f) * amplitude * 0.7f;
        player.setYRot(player.getYRot() + yawOffset);
        player.setXRot(player.getXRot() + pitchOffset);
        lastYawOffset = yawOffset;
        lastPitchOffset = pitchOffset;
        shakeTicks--;
    }

    /** Purge toute image, tout son en attente et tout offset entre deux serveurs. */
    public static void reset() {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player != null && (lastYawOffset != 0.0f || lastPitchOffset != 0.0f)) {
            player.setYRot(player.getYRot() - lastYawOffset);
            player.setXRot(player.getXRot() - lastPitchOffset);
        }
        COOLDOWNS.reset();
        shakeTicks = 0;
        shakeDuration = 0;
        shakeIntensity = 0.0f;
        lastYawOffset = 0.0f;
        lastPitchOffset = 0.0f;
        flashTicks = 0;
        faceTicks = 0;
        faceFadeTicks = 0;
        faceMaxAlpha = 0;
        faceStyle = ScreamerPresentation.CLASSIC;
        faceDurationTotal = 1;
        faceOffsetSign = 1;
        lastShownVariant = -1;
    }

    private static void startFace(int variant, int durationTicks, int fadeTicks,
                                  int maxAlpha, int style) {
        faceVariant = variant;
        faceTicks = durationTicks;
        faceDurationTotal = Math.max(1, durationTicks);
        faceFadeTicks = fadeTicks;
        faceMaxAlpha = maxAlpha;
        faceStyle = ScreamerPresentation.sanitizeStyle(style);
        faceOffsetSign = RANDOM.nextBoolean() ? 1 : -1;
        lastShownVariant = variant;
    }

    /**
     * Tirage général avec garde anti-doublon : les apparitions (indices 3–5)
     * partagent des fichiers avec la rotation générale, un second tirage évite
     * de remontrer immédiatement le visage affiché à l'instant.
     */
    private static int nextGeneralVariant() {
        int variant = SCREAMER_VARIANTS.next();
        if (variant == lastShownVariant) {
            variant = SCREAMER_VARIANTS.next();
        }
        return variant;
    }

    /** Démarre un shake seulement lorsque l'option d'accessibilité l'autorise. */
    private static void startShake(int durationTicks, float intensity, PenumbraConfig config) {
        if (!config.cameraShakeEnabled) {
            return;
        }
        shakeDuration = durationTicks;
        shakeTicks = durationTicks;
        shakeIntensity = intensity;
    }

    /** Joue un son local à la position du joueur, avec le canal hostile. */
    private static void playLocal(SoundEvent sound, float volume, float pitch) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            return;
        }
        client.level.playLocalSound(client.player.getX(), client.player.getY(), client.player.getZ(),
                sound, SoundSource.HOSTILE, volume, pitch, false);
    }
}

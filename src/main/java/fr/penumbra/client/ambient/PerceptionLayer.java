package fr.penumbra.client.ambient;

import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.director.TensionState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

/**
 * Couche de perception (0.17.0) — l'état mental du joueur devient une image.
 *
 * <p>Trois effets, tous strictement clients, tous dosés par la tension et
 * coupables séparément :</p>
 * <ul>
 *   <li><b>Grain et lignes de balayage</b> : la caméra intérieure du joueur se
 *       dégrade. Les positions du grain ne changent que tous les trois ticks et
 *       les alphas restent sous {@code 0x24} — c'est une texture, pas un
 *       clignotement. Aucune fréquence proche du seuil photosensible.</li>
 *   <li><b>Silhouette rémanente périphérique</b> : quand une vraie manifestation
 *       Penumbra se trouve à moins de 40 blocs <em>et hors du champ de vision</em>,
 *       une ombre humanoïde apparaît trois ticks du bon côté de l'écran. Elle ne
 *       ment jamais : si le joueur se retourne, la chose est là. C'est un indice
 *       honnête déguisé en hallucination.</li>
 *   <li><b>Pulsation du voile</b> : en CRISIS, l'assombrissement périphérique
 *       respire au rythme du battement cardiaque déjà joué par
 *       {@link AmbientFearManager} (2 s), au lieu de rester constant.</li>
 * </ul>
 *
 * <p>Aucun nouvel accès API : uniquement {@code extractor.fill} et
 * {@code HudElementRegistry}, déjà validés par {@code TensionVeilRenderer}.</p>
 */
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

    // ——— Rémanence périphérique ———
    /** Distance maximale d'une présence prise en compte. */
    private static final double AFTERIMAGE_RANGE = 40.0;
    /** Angle minimal (degrés) entre le regard et la présence : hors du champ. */
    private static final double AFTERIMAGE_MIN_ANGLE = 58.0;
    /** Angle maximal : au-delà elle est exactement dans le dos, sans côté lisible. */
    private static final double AFTERIMAGE_MAX_ANGLE = 168.0;
    /** Durée d'affichage d'une rémanence, et son fondu. */
    private static final int AFTERIMAGE_TICKS = 4;
    /** Intervalle minimal entre deux rémanences (8 s) : rare, donc crédible. */
    private static final int AFTERIMAGE_COOLDOWN_TICKS = 160;
    private static final int AFTERIMAGE_MAX_ALPHA = 0x52;

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

    private static int afterimageTicks;
    private static int afterimageCooldown;
    /** -1 = bord gauche, 1 = bord droit. */
    private static int afterimageSide = 1;
    /** Hauteur normalisée (0 = haut, 1 = bas) du centre de la silhouette. */
    private static float afterimageHeight = 0.5f;

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

        grainIntensity = config.perceptionGrainEnabled
                ? Math.min(1.6f, grainTarget(state) * strength) : 0.0f;
        if (--grainCountdown <= 0) {
            grainCountdown = GRAIN_REFRESH_TICKS;
            randomizeGrain();
        }

        // Pulsation du voile calée sur le battement (40 ticks) en CRISIS seulement.
        if (state == TensionState.CRISIS && config.fogEnabled) {
            pulsePhase += Mth.TWO_PI / 40.0f;
            veilPulse = 1.0f + Mth.sin(pulsePhase) * 0.15f * Math.min(1.0f, strength);
        } else {
            pulsePhase = 0.0f;
            veilPulse = 1.0f;
        }

        if (afterimageTicks > 0) {
            afterimageTicks--;
        }
        if (afterimageCooldown > 0) {
            afterimageCooldown--;
        }
        if (config.peripheralAfterimagesEnabled && strength > 0.0f
                && afterimageTicks <= 0 && afterimageCooldown <= 0
                && state != TensionState.CALM && !client.isPaused()) {
            tryAfterimage(client, player);
        }
    }

    /** Réinitialisation complète (déconnexion, changement de monde). */
    static void reset() {
        grainIntensity = 0.0f;
        veilPulse = 1.0f;
        pulsePhase = 0.0f;
        afterimageTicks = 0;
        afterimageCooldown = 0;
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

    // ===================== Rémanence =====================

    /**
     * Cherche une présence Penumbra réellement proche et hors champ. Sans
     * candidat, aucune rémanence n'est jamais inventée : l'effet reste un indice
     * honnête, jamais un mensonge.
     */
    private static void tryAfterimage(Minecraft client, LocalPlayer player) {
        ClientLevel level = client.level;
        if (level == null) {
            return;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f).multiply(1.0, 0.0, 1.0);
        if (look.lengthSqr() < 1.0E-6) {
            return;
        }
        look = look.normalize();
        Vec3 right = new Vec3(-look.z, 0.0, look.x);

        Entity best = null;
        double bestDistanceSqr = AFTERIMAGE_RANGE * AFTERIMAGE_RANGE;
        for (Entity entity : level.entitiesForRendering()) {
            if (entity == player || !entity.isAlive() || entity.isRemoved()
                    || !isManifestation(entity.getType())) {
                continue;
            }
            double distanceSqr = entity.position().distanceToSqr(eye);
            if (distanceSqr > bestDistanceSqr || distanceSqr < 4.0) {
                continue;
            }
            best = entity;
            bestDistanceSqr = distanceSqr;
        }
        if (best == null) {
            return;
        }

        Vec3 toEntity = best.position().subtract(eye).multiply(1.0, 0.0, 1.0);
        if (toEntity.lengthSqr() < 1.0E-6) {
            return;
        }
        toEntity = toEntity.normalize();
        double angle = Math.toDegrees(Math.acos(Mth.clamp(look.dot(toEntity), -1.0, 1.0)));
        if (angle < AFTERIMAGE_MIN_ANGLE || angle > AFTERIMAGE_MAX_ANGLE) {
            return;
        }

        afterimageSide = right.dot(toEntity) >= 0.0 ? 1 : -1;
        // Une présence lointaine se devine haut dans le cadre, une proche plus bas.
        double distance = Math.sqrt(bestDistanceSqr);
        afterimageHeight = (float) Mth.clamp(0.30 + distance / AFTERIMAGE_RANGE * 0.30, 0.28, 0.62);
        afterimageTicks = AFTERIMAGE_TICKS;
        afterimageCooldown = AFTERIMAGE_COOLDOWN_TICKS;
    }

    /** Vrai pour toute entité vivante du mod, hors cadre photo décoratif. */
    private static boolean isManifestation(EntityType<?> type) {
        Identifier key = EntityType.getKey(type);
        return "penumbra".equals(key.getNamespace())
                && !"photo_frame".equals(key.getPath());
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

    static boolean afterimageVisible() {
        return afterimageTicks > 0;
    }

    static int afterimageSide() {
        return afterimageSide;
    }

    static float afterimageHeight() {
        return afterimageHeight;
    }

    /** Alpha de la rémanence : montée d'un tick, descente sur les trois suivants. */
    static int afterimageAlpha() {
        if (afterimageTicks <= 0) {
            return 0;
        }
        float progress = afterimageTicks / (float) AFTERIMAGE_TICKS;
        float envelope = progress >= 0.75f ? (1.0f - progress) * 4.0f : progress / 0.75f;
        float strength = Math.min(1.0f, Math.max(0.0f, PenumbraConfig.get().perceptionIntensity));
        return Mth.clamp(Math.round(AFTERIMAGE_MAX_ALPHA * envelope * strength), 0, 0xFF);
    }
}

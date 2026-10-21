package fr.penumbra.client.jumpscare;

/**
 * Styles de présentation des screamers (0.14.0, anti-habituation visuelle).
 *
 * <p>Un screamer toujours cadré, minuté et sonorisé de la même façon devient
 * prévisible : le cerveau apprend le motif et cesse d'avoir peur. Cette classe
 * décrit six mises en scène différentes du même visage — cadrage, zoom, coupes,
 * alpha, son — choisies en rotation sans répétition. Toutes les fonctions sont
 * pures et bornées : aucune dépendance client, testable en JUnit.</p>
 *
 * <ul>
 *   <li>{@link #CLASSIC} : plein écran, coupe sèche (comportement historique) ;</li>
 *   <li>{@link #CREEP_ZOOM} : le visage avance lentement vers l'écran ;</li>
 *   <li>{@link #OFFSET_CUT} : cadrage décentré, le visage surgit d'un bord ;</li>
 *   <li>{@link #DOUBLE_CUT} : deux apparitions séparées par un trou noir de
 *       2 ticks, la seconde légèrement plus proche (rémanence) ;</li>
 *   <li>{@link #SUBLIMINAL} : 3 ticks à alpha réduit, chuchotement au lieu du
 *       cri — « est-ce que je viens de voir ça ? » ;</li>
 *   <li>{@link #CLOSE_CRAWL} : démarre trop près, cadré serré, puis recule.</li>
 * </ul>
 *
 * <p>Les finales physiques (bond au visage) n'utilisent jamais un style qui
 * affaiblit l'impact : {@link #finalizeForContact(int)} remappe les styles
 * doux vers un équivalent plein cadre.</p>
 */
public final class ScreamerPresentation {

    public static final int CLASSIC = 0;
    public static final int CREEP_ZOOM = 1;
    public static final int OFFSET_CUT = 2;
    public static final int DOUBLE_CUT = 3;
    public static final int SUBLIMINAL = 4;
    public static final int CLOSE_CRAWL = 5;
    public static final int STYLE_COUNT = 6;

    /** Alpha maximal du style subliminal : visible mais jamais assené. */
    static final int SUBLIMINAL_ALPHA_CAP = 170;
    /** Durée fixe du style subliminal, en ticks. */
    static final int SUBLIMINAL_DURATION_TICKS = 3;

    private ScreamerPresentation() {
    }

    static float clamp01(float value) {
        if (!Float.isFinite(value)) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    /** Tout style hors bornes retombe sur {@link #CLASSIC}. */
    public static int sanitizeStyle(int style) {
        return style < 0 || style >= STYLE_COUNT ? CLASSIC : style;
    }

    /**
     * Style équivalent plein impact pour une finale de contact : pas de
     * subliminal ni de trou noir pendant qu'une entité frappe le joueur.
     */
    public static int finalizeForContact(int style) {
        return switch (sanitizeStyle(style)) {
            case SUBLIMINAL -> CLOSE_CRAWL;
            case DOUBLE_CUT -> CREEP_ZOOM;
            default -> sanitizeStyle(style);
        };
    }

    /** Durée d'affichage du visage pour ce style, à partir de la durée de base. */
    public static int durationTicks(int style, int baseTicks) {
        int base = Math.max(2, baseTicks);
        return switch (sanitizeStyle(style)) {
            case CREEP_ZOOM -> base + 4;
            case OFFSET_CUT -> Math.max(2, base - 1);
            case DOUBLE_CUT -> base + 3;
            case SUBLIMINAL -> SUBLIMINAL_DURATION_TICKS;
            case CLOSE_CRAWL -> base + 2;
            default -> base;
        };
    }

    /**
     * Visibilité du visage au tick {@code tickIndex} (0 = premier tick).
     * Seul {@link #DOUBLE_CUT} introduit un trou noir de 2 ticks au premier tiers.
     */
    public static boolean visibleAt(int style, int tickIndex, int durationTicks) {
        int total = Math.max(1, durationTicks);
        if (tickIndex < 0 || tickIndex >= total) {
            return false;
        }
        if (sanitizeStyle(style) != DOUBLE_CUT) {
            return true;
        }
        int gapStart = Math.max(1, total / 3);
        return tickIndex < gapStart || tickIndex >= gapStart + 2;
    }

    /**
     * Échelle du carré « cover » (1 = plein écran exact). Toujours ≥ 1 pour ne
     * jamais laisser apparaître les bords de la texture.
     */
    public static float scale(int style, float progress) {
        float p = clamp01(progress);
        return switch (sanitizeStyle(style)) {
            case CREEP_ZOOM -> 1.0f + 0.35f * smooth(p);
            case OFFSET_CUT -> 1.18f;
            case DOUBLE_CUT -> p < 0.34f ? 1.0f : 1.12f;
            case SUBLIMINAL -> 1.06f;
            case CLOSE_CRAWL -> 1.45f - 0.27f * smooth(p);
            default -> 1.0f;
        };
    }

    /** Décalage horizontal, en fraction de la largeur d'écran, signé. */
    public static float offsetXFraction(int style, int sign) {
        int s = sign >= 0 ? 1 : -1;
        return switch (sanitizeStyle(style)) {
            case OFFSET_CUT -> 0.16f * s;
            case CLOSE_CRAWL -> 0.05f * s;
            default -> 0.0f;
        };
    }

    /** Décalage vertical, en fraction de la hauteur d'écran (négatif = haut). */
    public static float offsetYFraction(int style) {
        return switch (sanitizeStyle(style)) {
            case OFFSET_CUT -> -0.05f;
            case CLOSE_CRAWL -> -0.06f;
            default -> 0.0f;
        };
    }

    /** Plafond d'alpha du style ; borne l'entrée dans [0;255]. */
    public static int alphaCap(int style, int baseAlpha) {
        int alpha = Math.max(0, Math.min(255, baseAlpha));
        return sanitizeStyle(style) == SUBLIMINAL
                ? Math.min(alpha, SUBLIMINAL_ALPHA_CAP) : alpha;
    }

    /** true : ce style chuchote au lieu de crier (jamais pour une finale). */
    public static boolean whisperInsteadOfShriek(int style) {
        return sanitizeStyle(style) == SUBLIMINAL;
    }

    /** Durée du tremblement de caméra adaptée au style. */
    public static int shakeDurationTicks(int style, int baseTicks) {
        int base = Math.max(0, baseTicks);
        return switch (sanitizeStyle(style)) {
            case SUBLIMINAL -> Math.min(base, 4);
            case DOUBLE_CUT -> base + 4;
            default -> base;
        };
    }

    /**
     * Pitch du cri : jitter borné pour que deux screamers consécutifs ne
     * sonnent jamais identiques, sans jamais devenir aigu au point d'être
     * comique. Les finales de contact restent dans le grave.
     */
    public static float shriekPitch(float roll01, boolean contactFinal) {
        float r = clamp01(roll01);
        return contactFinal ? 0.86f + 0.10f * r : 0.88f + 0.20f * r;
    }

    private static float smooth(float p) {
        return p * p * (3.0f - 2.0f * p);
    }
}

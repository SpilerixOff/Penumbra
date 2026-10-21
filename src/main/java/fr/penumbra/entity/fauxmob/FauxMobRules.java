package fr.penumbra.entity.fauxmob;

/**
 * Règles pures du Faux-Semblant (0.15.0).
 *
 * <p>Le Faux-Semblant n'est pas une entité custom : c'est une <b>vraie</b>
 * créature vanilla (vache, cochon, mouton, poule — zombie la nuit) pilotée par
 * le serveur, qui porte le plus souvent un défaut minuscule : elle est muette,
 * ou elle vous fixe, ou elle est figée face à un point sans intérêt. Et
 * parfois, elle est <b>parfaite</b> — indiscernable — jusqu'à ce qu'on la
 * touche. Plus le joueur a rencontré de Faux-Semblants, plus la part de
 * parfaits augmente : la paranoïa croît avec l'expérience.</p>
 *
 * <p>Classe entièrement pure : les tirages consomment des rolls uniformes
 * fournis par l'appelant, tout est testable en JUnit sans Minecraft.</p>
 */
public final class FauxMobRules {

    /** Déguisements possibles. Le zombie exige la nuit en surface. */
    public enum Disguise { COW, PIG, SHEEP, CHICKEN, ZOMBIE }

    /** Défaut porté par la créature. {@code NONE} = copie parfaite. */
    public enum Defect { NONE, SILENT, STARE, FROZEN }

    /** Cause de dissolution, pour le gain de tension et les effets. */
    public enum RevealCause { STARED, STRUCK, TOUCHED, FLASHED, TIMEOUT }

    /** Part de zombie quand la nuit l'autorise. */
    static final float ZOMBIE_SHARE = 0.18f;
    /** Chance de copie parfaite à la première rencontre. */
    static final float PERFECT_BASE = 0.15f;
    /** Progression de la chance de parfait par rencontre vécue. */
    static final float PERFECT_PER_SEEN = 0.05f;
    /** Plafond de la chance de parfait. */
    static final float PERFECT_CAP = 0.45f;

    /** Cône de regard du joueur vers la créature (cos, ~15°). */
    public static final double STARE_DOT_THRESHOLD = 0.965;
    /** Portée maximale du regard qui compte. */
    public static final double STARE_RANGE = 32.0;
    /** Ticks cumulés de regard avant que la créature ne se sache vue. */
    public static final int STARE_REVEAL_TICKS = 90;
    /** Entre le moment où elle rend le regard et sa dissolution. */
    public static final int HEAD_TURN_TICKS = 30;
    /** Distance de contact : elle ne se laisse jamais toucher. */
    public static final double TOUCH_DISTANCE = 2.5;
    /** Durée de vie maximale d'une scène (2 minutes). */
    public static final long LIFETIME_TICKS = 2400L;
    /** En dessous de ce dot, le joueur ne « voit » plus la créature. */
    public static final double UNSEEN_DOT = 0.2;
    /** Distance au-delà de laquelle la créature est considérée hors de vue. */
    public static final double UNSEEN_DISTANCE = 40.0;

    private FauxMobRules() {
    }

    static float clamp01(float value) {
        if (!Float.isFinite(value)) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    /**
     * Choix du déguisement. {@code zombieAllowed} = nuit en surface et option
     * active ; sinon répartition égale entre les quatre animaux.
     */
    public static Disguise pickDisguise(float roll, boolean zombieAllowed) {
        float r = clamp01(roll);
        if (zombieAllowed) {
            if (r < ZOMBIE_SHARE) {
                return Disguise.ZOMBIE;
            }
            r = (r - ZOMBIE_SHARE) / (1.0f - ZOMBIE_SHARE);
        }
        if (r < 0.25f) {
            return Disguise.COW;
        }
        if (r < 0.5f) {
            return Disguise.PIG;
        }
        if (r < 0.75f) {
            return Disguise.SHEEP;
        }
        return Disguise.CHICKEN;
    }

    /**
     * Chance qu'une copie soit parfaite : croît avec le vécu du joueur.
     * À force d'apprendre les défauts, c'est l'absence de défaut qui effraie.
     */
    public static float perfectChance(int timesSeen) {
        int seen = Math.max(0, timesSeen);
        return Math.min(PERFECT_CAP, PERFECT_BASE + PERFECT_PER_SEEN * seen);
    }

    /**
     * Choix du défaut. {@code perfectRoll} décide d'une copie parfaite ;
     * sinon {@code roll} répartit équitablement les trois défauts visibles.
     */
    public static Defect pickDefect(float roll, float perfectRoll, int timesSeen) {
        if (clamp01(perfectRoll) < perfectChance(timesSeen)) {
            return Defect.NONE;
        }
        float r = clamp01(roll);
        if (r < 1.0f / 3.0f) {
            return Defect.SILENT;
        }
        if (r < 2.0f / 3.0f) {
            return Defect.STARE;
        }
        return Defect.FROZEN;
    }

    /** Le regard du joueur compte-t-il comme une observation directe ? */
    public static boolean isStaring(double dot, double distanceSq) {
        return dot > STARE_DOT_THRESHOLD
                && distanceSq < STARE_RANGE * STARE_RANGE;
    }

    /**
     * Une copie parfaite ne réagit jamais au regard : c'est sa définition.
     * Les copies défectueuses finissent par se savoir vues.
     */
    public static boolean stareCanReveal(Defect defect) {
        return defect != Defect.NONE;
    }

    /** La créature est-elle hors de vue (peut disparaître sans témoin) ? */
    public static boolean isUnseen(double dot, double distanceSq) {
        return dot < UNSEEN_DOT
                || distanceSq > UNSEEN_DISTANCE * UNSEEN_DISTANCE;
    }

    /** Fin de vie : uniquement hors du regard — jamais de disparition observée gratuite. */
    public static boolean shouldTimeout(long ageTicks, boolean unseen) {
        return ageTicks >= LIFETIME_TICKS && unseen;
    }

    /** Gain de tension par cause de dissolution. */
    public static float tensionGain(RevealCause cause) {
        return switch (cause) {
            case STARED -> 6.0f;
            case STRUCK -> 8.0f;
            case TOUCHED -> 7.0f;
            case FLASHED -> 5.0f;
            case TIMEOUT -> 0.0f;
        };
    }

    /** Une dissolution silencieuse (aucun effet) ? Seul le timeout l'est. */
    public static boolean isSilentDissolve(RevealCause cause) {
        return cause == RevealCause.TIMEOUT;
    }
}

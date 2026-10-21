package fr.penumbra.entity.originel;

/**
 * Calculs purs du cycle de l'Originel. Garder ces calculs hors de l'entite
 * permet de verifier les bornes temporelles sans demarrer Minecraft.
 */
public final class OriginelTiming {

    public static final int MIN_SIGHTING_DELAY_TICKS = 4 * 60 * 20;
    public static final int MAX_SIGHTING_DELAY_TICKS = 7 * 60 * 20;
    public static final int MIN_CAVE_SCENE_TICKS = 80;
    public static final int MAX_CAVE_SCENE_TICKS = 220;

    private OriginelTiming() {
    }

    /**
     * Convertit un tirage uniforme dans la fenetre 4--7 minutes en delai,
     * module par la frequence globale. A frequence 1, le resultat reste donc
     * strictement compris entre 4 et 7 minutes.
     */
    public static int sightingDelayTicks(float eventFrequency, int randomOffsetTicks) {
        int window = MAX_SIGHTING_DELAY_TICKS - MIN_SIGHTING_DELAY_TICKS;
        int offset = Math.max(0, Math.min(window, randomOffsetTicks));
        float frequency = Math.max(0.05f, eventFrequency);
        return Math.max(20, Math.round((MIN_SIGHTING_DELAY_TICKS + offset) / frequency));
    }

    /** Courbe d'acceleration de la charge : lente au depart, brutale a la fin. */
    public static double lungeEase(double progress) {
        double t = Math.max(0.0, Math.min(1.0, progress));
        return t * t * t;
    }

    /**
     * Laisse toujours au joueur le temps d'atteindre le seuil de regard
     * configuré, plus une seconde de marge, sans allonger le réglage normal.
     */
    public static int caveSceneDurationTicks(int requestedTicks, int stareSeconds) {
        int requested = Math.max(MIN_CAVE_SCENE_TICKS, Math.min(140, requestedTicks));
        int stare = Math.max(1, Math.min(10, stareSeconds)) * 20 + 20;
        return Math.min(MAX_CAVE_SCENE_TICKS, Math.max(requested, stare));
    }
}

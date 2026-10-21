package fr.penumbra.director;

/**
 * États de tension d'un joueur, ordonnés par seuil croissant (0–100).
 * {@link #FALSE_CALM} est un état spécial piloté par le
 * {@link HorrorDirector} : il n'est jamais retourné par {@link #forTension(float)}.
 */
public enum TensionState {

    CALM(0f),
    UNEASE(15f),
    WATCHED(35f),
    MANIFESTATION(55f),
    HUNT(75f),
    CRISIS(90f),
    FALSE_CALM(0f);

    /** Seuil de tension à partir duquel l'état devient actif. */
    public final float threshold;

    TensionState(float threshold) {
        this.threshold = threshold;
    }

    /**
     * Retourne l'état correspondant à une tension donnée.
     * FALSE_CALM est volontairement ignoré : il est géré par le directeur
     * (entrée programmée après des manifestations en CRISIS), pas par la courbe de tension.
     *
     * @param t tension courante (0–100)
     * @return l'état le plus élevé dont le seuil est atteint
     */
    public static TensionState forTension(float t) {
        TensionState result = CALM;
        for (TensionState state : values()) {
            if (state == FALSE_CALM) {
                continue;
            }
            if (t >= state.threshold) {
                result = state;
            }
        }
        return result;
    }
}

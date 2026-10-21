package fr.penumbra.director;

/** Règles de respiration communes au pool, aux scènes autonomes et aux scares. */
final class DirectorPacingRules {

    static final long RECOVERY_TICKS = 200L;
    static final long PERCEPTIBLE_GAP_TICKS = 200L;
    static final long MANIFESTATION_GAP_TICKS = 400L;

    private DirectorPacingRules() {
    }

    /** Les indices ouvrent l'arc ; ses retombées ne relancent pas une attaque. */
    static boolean allowsPressure(DirectorMemory.ArcPhase phase) {
        return phase == DirectorMemory.ArcPhase.DORMANT
                || phase == DirectorMemory.ArcPhase.BUILD
                || phase == DirectorMemory.ArcPhase.MANIFEST;
    }

    /** Une apparition en BUILD doit avoir le temps d'être comprise avant l'escalade. */
    static boolean allowsEscalation(DirectorMemory.ArcPhase phase) {
        return phase == DirectorMemory.ArcPhase.DORMANT
                || phase == DirectorMemory.ArcPhase.MANIFEST;
    }

    static boolean falseCalmActive(DirectorMemory.ArcState arc, long now) {
        return arc.phase() == DirectorMemory.ArcPhase.FALSE_CALM && now < arc.phaseEndsTick();
    }

    static boolean vulnerable(float health, float maximumHealth, int food,
                              int air, boolean burning, boolean hurt) {
        return !Float.isFinite(health) || health <= Math.max(6.0f, maximumHealth * 0.3f)
                || food <= 6 || air < 100 || burning || hurt;
    }

    /** Aucune dette de manifestation ne peut supprimer le temps de réaction. */
    static boolean sceneGapElapsed(DirectorMemory.PacingState pacing, long now) {
        return !pacing.falseCalmBreakPending()
                && elapsed(now, pacing.lastPerceptibleTick(), PERCEPTIBLE_GAP_TICKS)
                && (pacing.lastManifestationTick() < 0L
                || elapsed(now, pacing.lastManifestationTick(), MANIFESTATION_GAP_TICKS));
    }

    /** Les événements incidents réels comptent aussi, sans changer l'historique du pool. */
    static boolean selectionGapElapsed(DirectorMemory.PacingState pacing, long now, long gap) {
        return elapsed(now, Math.max(pacing.lastDirectedTick(), pacing.lastPerceptibleTick()), gap);
    }

    private static boolean elapsed(long now, long previous, long gap) {
        return now >= previous && now - previous >= Math.max(0L, gap);
    }
}

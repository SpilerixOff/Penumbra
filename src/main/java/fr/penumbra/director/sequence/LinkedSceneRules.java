package fr.penumbra.director.sequence;

/** Regles pures de disponibilite, delais et limites des scenes liees. */
public final class LinkedSceneRules {

    public static final int DEFAULT_MAX_ACTIVE = 8;
    public static final int HARD_MAX_ACTIVE = 32;
    public static final int MAX_FOLLOW_UP_ATTEMPTS = 2;
    public static final int RETRY_DELAY_TICKS = 20;

    private LinkedSceneRules() {
    }

    public static int minimumStage(LinkedSceneType type) {
        return switch (type) {
            case FALSE_GUIDE -> 3;
            case SKITTER_OMEN -> 2;
            case NEGATIVE_WITNESS -> 4;
        };
    }

    public static long maximumDurationTicks(LinkedSceneType type) {
        return switch (type) {
            case FALSE_GUIDE -> 600L;
            case SKITTER_OMEN -> 500L;
            case NEGATIVE_WITNESS -> 800L;
        };
    }

    /** Une conclusion correcte donne une fenetre de reaction plus genereuse. */
    public static int followUpDelayTicks(LinkedSceneType type, boolean counterplayKnown) {
        return switch (type) {
            case FALSE_GUIDE -> counterplayKnown ? 100 : 60;
            case SKITTER_OMEN -> counterplayKnown ? 160 : 100;
            case NEGATIVE_WITNESS -> 0;
        };
    }

    /**
     * Temps pendant lequel rouvrir la camera donne une sortie a l'Originel.
     * Le dossier complet reduit le danger, sans supprimer la consequence.
     */
    public static int cameraTrapTicks(boolean counterplayKnown) {
        return counterplayKnown ? 300 : 400;
    }

    /**
     * Garde commune aux trois declencheurs naturels. Le Projectionniste peut
     * apparaitre hors grotte ; les deux chaines menant au Rampant exigent une
     * vraie zone souterraine.
     */
    public static boolean eligible(LinkedSceneType type, boolean featureEnabled,
                                   boolean ownerAlive, boolean spectator,
                                   boolean peaceful, boolean graceComplete,
                                   boolean physicalManifestationAllowed,
                                   int effectiveContentStage, boolean underground) {
        if (type == null || !featureEnabled || !ownerAlive || spectator || peaceful
                || !graceComplete || !physicalManifestationAllowed
                || effectiveContentStage < minimumStage(type)) {
            return false;
        }
        return type == LinkedSceneType.NEGATIVE_WITNESS || underground;
    }
}

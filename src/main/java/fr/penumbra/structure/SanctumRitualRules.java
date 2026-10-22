package fr.penumbra.structure;

import fr.penumbra.story.StoryStage;

import java.util.function.BooleanSupplier;

/** Shared eligibility for altar retries, camera checks and the prelude's handoff. */
public final class SanctumRitualRules {
    public enum Completion { ORDINARY, SUMMONED, NO_SPACE }

    private SanctumRitualRules() {
    }

    public static boolean summonsConfrontation(int stage, boolean alreadyWon) {
        return stage >= StoryStage.RECOGNITION.index() && !alreadyWon;
    }

    public static boolean needsCamera(int stage, boolean alreadyWon, boolean hasCamera) {
        return summonsConfrontation(stage, alreadyWon) && !hasCamera;
    }

    /** Record this ritual once before the summoner checks the newly unlocked stage. */
    public static Completion complete(int stage, boolean alreadyWon, Runnable recordRitual,
                                      BooleanSupplier trySummon) {
        boolean finalAttempt = summonsConfrontation(stage, alreadyWon);
        recordRitual.run();
        if (!finalAttempt) return Completion.ORDINARY;
        return trySummon.getAsBoolean() ? Completion.SUMMONED : Completion.NO_SPACE;
    }
}

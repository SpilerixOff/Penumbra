package fr.penumbra.story;

import java.util.Map;

import static fr.penumbra.story.StoryManager.*;

/** Narrative evidence rules, independent of a live player or server. */
public final class StoryProgression {
    private StoryProgression() {
    }

    /** One stage per discovery; evidence collected earlier remains useful. */
    public static int nextStage(int currentStage, Map<String, Integer> evidence, String discovery) {
        StoryStage stage = StoryStage.byIndex(currentStage);
        boolean ready = switch (stage) {
            case NORMALCY -> count(evidence, KEY_FIRST_ANOMALY) >= 1
                    || count(evidence, KEY_STRUCTURE_OBSERVATION_POST) >= 1;
            case TRACES -> count(evidence, KEY_MIMIC_EVENT) >= 3;
            case IMITATION -> count(evidence, KEY_REFUGE_HOST_SEEN) >= 1
                    && count(evidence, KEY_BED_USED) >= 2;
            case FAMILIARITY -> count(evidence, KEY_COPY_BUILT) >= 1
                    || count(evidence, KEY_STRUCTURE_SHIFTED_HOUSE) >= 1;
            case SUBSTITUTION -> count(evidence, KEY_HOST_OBSERVED) >= 4;
            // An early ritual remains in the journal, but recognition requires
            // returning to the altar. An unrelated event cannot start the finale.
            case RECOGNITION -> KEY_SANCTUM_RITUAL.equals(discovery);
            case CONFRONTATION -> false;
        };
        return ready ? stage.next().index() : stage.index();
    }

    private static int count(Map<String, Integer> evidence, String key) {
        return Math.max(0, evidence.getOrDefault(key, 0));
    }
}

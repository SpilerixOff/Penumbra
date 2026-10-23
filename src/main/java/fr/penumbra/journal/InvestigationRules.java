package fr.penumbra.journal;

import fr.penumbra.structure.StructureType;
import java.util.List;

/** Stable book identities: old torn pages and new volumes share the same evidence. */
public final class InvestigationRules {
    public static final List<JournalEntry> VOLUMES = List.of(JournalEntry.METHOD_CAMERA,
            JournalEntry.METHOD_REFUGE, JournalEntry.METHOD_STILLNESS, JournalEntry.LAST_PAGE);

    private InvestigationRules() { }

    public static int firstMissing(int mask) {
        for (int i = 0; i < 4; i++) if ((mask & (1 << i)) == 0) return i;
        return -1;
    }

    public static boolean ready(int mask, boolean heard) {
        return (mask & 15) == 15 && heard;
    }

    /** Advance only along the collected prefix; an early volume never skips its predecessors. */
    public static int stageFloor(int mask, boolean heard) {
        int missing = firstMissing(mask);
        return missing < 0 ? (heard ? 5 : 4) : missing;
    }

    public static StructureType destination(int mask, boolean heard) {
        int missing = firstMissing(mask);
        return missing >= 0 ? VOLUMES.get(missing).heldBy().orElseThrow()
                : heard ? StructureType.ASH_SANCTUM : StructureType.PENUMBRA_WELL;
    }

    public static boolean preserveLegacyFinale(int previousStage, boolean initialized) {
        return !initialized && previousStage >= 5;
    }
}

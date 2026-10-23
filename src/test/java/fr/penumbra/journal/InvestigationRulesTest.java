package fr.penumbra.journal;

import fr.penumbra.structure.StructureType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InvestigationRulesTest {
    @Test
    void theFourVolumesHaveStableIdentitiesAndFourDistinctArchives() {
        assertEquals(List.of("method_camera", "method_refuge", "method_stillness", "last_page"),
                InvestigationRules.VOLUMES.stream().map(JournalEntry::id).toList());
        assertEquals(List.of(StructureType.OBSERVATION_POST, StructureType.SHIFTED_HOUSE,
                        StructureType.ASH_SANCTUM, StructureType.PENUMBRA_WELL),
                InvestigationRules.VOLUMES.stream().map(e -> e.heldBy().orElseThrow()).toList());
        assertTrue(InvestigationRules.VOLUMES.stream().allMatch(e -> e.kind() == JournalEntry.Kind.METHOD));
    }

    @Test
    void theNormalRouteRequiresEachBookThenTheCassetteBeforeReturningToTheSanctum() {
        int[] masks = {0, 1, 3, 7, 15};
        StructureType[] destinations = {StructureType.OBSERVATION_POST, StructureType.SHIFTED_HOUSE,
                StructureType.ASH_SANCTUM, StructureType.PENUMBRA_WELL, StructureType.PENUMBRA_WELL};
        for (int step = 0; step < masks.length; step++) {
            assertEquals(step == 4 ? -1 : step, InvestigationRules.firstMissing(masks[step]));
            assertEquals(step, InvestigationRules.stageFloor(masks[step], false));
            assertEquals(destinations[step], InvestigationRules.destination(masks[step], false));
            assertFalse(InvestigationRules.ready(masks[step], false));
        }
        assertTrue(InvestigationRules.ready(15, true));
        assertEquals(5, InvestigationRules.stageFloor(15, true));
        assertEquals(StructureType.ASH_SANCTUM, InvestigationRules.destination(15, true));
    }

    @Test
    void findingTheLastVolumeFirstNeverSkipsTheMissingPredecessorsEvenAfterHearingItsTape() {
        assertEquals(0, InvestigationRules.stageFloor(8, true));
        assertEquals(StructureType.OBSERVATION_POST, InvestigationRules.destination(8, true));
        assertEquals(1, InvestigationRules.stageFloor(9, true));
        assertEquals(StructureType.SHIFTED_HOUSE, InvestigationRules.destination(9, true));
        assertEquals(1, InvestigationRules.stageFloor(13, true));
        assertFalse(InvestigationRules.ready(13, true));
        assertEquals(5, InvestigationRules.stageFloor(15, true));
    }

    @Test
    void everyIncompleteSubsetPointsAtItsFirstGapAndCannotUnlockTheFinale() {
        for (int mask = 0; mask < 15; mask++) {
            int firstGap = Integer.numberOfTrailingZeros(~mask);
            for (boolean heard : List.of(false, true)) {
                assertEquals(firstGap, InvestigationRules.firstMissing(mask));
                assertEquals(firstGap, InvestigationRules.stageFloor(mask, heard));
                assertEquals(InvestigationRules.VOLUMES.get(firstGap).heldBy().orElseThrow(),
                        InvestigationRules.destination(mask, heard));
                assertFalse(InvestigationRules.ready(mask, heard));
            }
        }
    }

    @Test
    void unknownHighBitsCannotCountAsBooksOrChangeTheRoute() {
        for (int mask = 0; mask < 16; mask++) for (boolean heard : List.of(false, true)) {
            int futureMask = mask | 0x7000;
            assertEquals(InvestigationRules.firstMissing(mask), InvestigationRules.firstMissing(futureMask));
            assertEquals(InvestigationRules.stageFloor(mask, heard), InvestigationRules.stageFloor(futureMask, heard));
            assertEquals(InvestigationRules.destination(mask, heard), InvestigationRules.destination(futureMask, heard));
            assertEquals(InvestigationRules.ready(mask, heard), InvestigationRules.ready(futureMask, heard));
        }
    }

    @Test
    void onlyAnExistingFinaleIsGrandfatheredBeforeTheNewInvestigationStarts() {
        for (int stage = 0; stage <= 6; stage++) {
            assertEquals(stage >= 5, InvestigationRules.preserveLegacyFinale(stage, false));
            assertFalse(InvestigationRules.preserveLegacyFinale(stage, true),
                    "New investigators reaching stage five must still collect their evidence");
        }
    }
}

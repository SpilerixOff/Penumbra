package fr.penumbra.director;

import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirectorPacingRulesTest {

    @Test
    void foreshadowAndAftermathKeepApparitionsOutEvenAtHighTension() {
        assertFalse(DirectorPacingRules.allowsPressure(DirectorMemory.ArcPhase.FORESHADOW));
        assertFalse(DirectorPacingRules.allowsPressure(DirectorMemory.ArcPhase.AFTERMATH));
        assertFalse(DirectorPacingRules.allowsPressure(DirectorMemory.ArcPhase.FALSE_CALM));
        assertTrue(DirectorPacingRules.allowsPressure(DirectorMemory.ArcPhase.BUILD));
        assertTrue(DirectorPacingRules.allowsPressure(DirectorMemory.ArcPhase.MANIFEST));
        assertTrue(DirectorPacingRules.allowsPressure(DirectorMemory.ArcPhase.DORMANT));
    }

    @Test
    void buildingAnArcCanShowAThreatBeforeItCanBecomeAChaseOrScare() {
        assertTrue(DirectorPacingRules.allowsPressure(DirectorMemory.ArcPhase.BUILD));
        assertFalse(DirectorPacingRules.allowsEscalation(DirectorMemory.ArcPhase.BUILD));
        assertTrue(DirectorPacingRules.allowsEscalation(DirectorMemory.ArcPhase.MANIFEST));
        assertTrue(DirectorPacingRules.allowsEscalation(DirectorMemory.ArcPhase.DORMANT));
    }

    @Test
    void manifestationDebtStillWaitsAfterARecentDirectedEvent() {
        DirectorMemory memory = DirectorMemory.fresh(1_000L, 1_000L)
                .recordDirected(4_000L, "footsteps", "audio", true, false);
        assertTrue(HorrorDirector.manifestationDue(
                memory.pacing().lastManifestationTick(), 4_100L, 2_400L));
        assertFalse(DirectorPacingRules.selectionGapElapsed(memory.pacing(), 4_100L, 700L));
        assertTrue(DirectorPacingRules.selectionGapElapsed(memory.pacing(), 4_700L, 700L));
    }

    @Test
    void anExternalManifestationCreatesTheSameBreathingRoomAfterReload() {
        DirectorMemory memory = DirectorMemory.fresh(1_000L, 1_000L)
                .recordManifestation(2_000L);
        var saved = DirectorMemory.CODEC.encodeStart(JsonOps.INSTANCE, memory).getOrThrow();
        DirectorMemory reloaded = DirectorMemory.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();

        assertFalse(DirectorPacingRules.sceneGapElapsed(reloaded.pacing(), 2_399L));
        assertTrue(DirectorPacingRules.sceneGapElapsed(reloaded.pacing(), 2_400L));
        assertEquals(1_000L, reloaded.pacing().lastDirectedTick(),
                "a spontaneous creature must not pretend to be a selected pool event");
    }

    @Test
    void anIncidentalScarePreventsASecondSceneWithoutErasingManifestationDebt() {
        DirectorMemory memory = DirectorMemory.fresh(1_000L, 1_000L)
                .recordIncidental(2_000L);
        assertFalse(DirectorPacingRules.sceneGapElapsed(memory.pacing(), 2_199L));
        assertTrue(DirectorPacingRules.sceneGapElapsed(memory.pacing(), 2_200L));
        assertFalse(DirectorPacingRules.selectionGapElapsed(memory.pacing(), 2_300L, 400L));
        assertTrue(DirectorPacingRules.selectionGapElapsed(memory.pacing(), 2_400L, 400L));
        assertEquals(1_000L, memory.pacing().lastManifestationTick());
    }

    @Test
    void clockRollbackPreservesTheRemainingPersonalGap() {
        DirectorMemory memory = DirectorMemory.fresh(10_000L, 10_000L)
                .recordManifestation(10_000L).rebaseClock(1_000L);
        assertFalse(DirectorPacingRules.sceneGapElapsed(memory.pacing(), 1_399L));
        assertTrue(DirectorPacingRules.sceneGapElapsed(memory.pacing(), 1_400L));
        assertFalse(DirectorPacingRules.selectionGapElapsed(memory.pacing(), 999L, 100L));
    }

    @Test
    void survivalEmergenciesProtectPlayersButNormalExplorationStillAllowsPressure() {
        assertFalse(DirectorPacingRules.vulnerable(20f, 20f, 20, 300, false, false));
        assertFalse(DirectorPacingRules.vulnerable(7f, 20f, 7, 100, false, false));
        assertTrue(DirectorPacingRules.vulnerable(6f, 20f, 20, 300, false, false));
        assertTrue(DirectorPacingRules.vulnerable(12f, 40f, 20, 300, false, false));
        assertTrue(DirectorPacingRules.vulnerable(20f, 20f, 6, 300, false, false));
        assertTrue(DirectorPacingRules.vulnerable(20f, 20f, 20, 99, false, false));
        assertTrue(DirectorPacingRules.vulnerable(20f, 20f, 20, 300, true, false));
        assertTrue(DirectorPacingRules.vulnerable(20f, 20f, 20, 300, false, true));
    }

    @Test
    void anOwedQuietBreakPrecedesAnyNewManifestation() {
        DirectorMemory pending = DirectorMemory.fresh(1_000L, 1_000L)
                .withFalseCalmBreakPending(true, 1_000L);
        assertFalse(DirectorPacingRules.sceneGapElapsed(pending.pacing(), 10_000L));
        DirectorMemory consumed = HorrorArcEngine.consumeBreak(pending, 10_000L)
                .recordDirected(10_000L, "footsteps", "audio", true, false);
        assertFalse(DirectorPacingRules.sceneGapElapsed(consumed.pacing(), 10_199L));
        assertTrue(DirectorPacingRules.sceneGapElapsed(consumed.pacing(), 10_200L));
    }

    @Test
    void persistedFalseCalmEndsAtItsDeadlineWithoutKeepingTheClientInCrisis() {
        DirectorMemory memory = HorrorArcEngine.forceFalseCalm(
                DirectorMemory.fresh(1_000L, 1_000L), 1_000L, 900L);
        assertTrue(DirectorPacingRules.falseCalmActive(memory.arc(), 1_899L));
        assertFalse(DirectorPacingRules.falseCalmActive(memory.arc(), 1_900L));
    }
}

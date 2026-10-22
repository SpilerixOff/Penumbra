package fr.penumbra.director;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HorrorDirectorPacingTest {

    @Test
    void pressureGuardIncludesStingsAndCreaturesBeyondTheOldHostOnlyFilter() {
        for (String event : new String[]{"watcher_group", "listener_passage", "sleep_interrupt",
                "fake_creeper", "binoculars_reveal", "false_companion", "mining_bait"}) {
            assertTrue(HorrorDirector.isPressureEvent(event), event);
        }
        assertFalse(HorrorDirector.isPressureEvent("footsteps"));
        assertFalse(HorrorDirector.isPressureEvent("silence"));
        assertFalse(HorrorDirector.isPressureEvent("paired_echo"));
        assertFalse(HorrorDirector.isPressureEvent("door_anomaly"));
        assertFalse(HorrorDirector.isPressureEvent("faux_creature"));
    }

    @Test
    void calmEventsCanAccumulateAcrossTheDefaultNinetySecondGap() {
        PlayerHorrorProfile profile = new PlayerHorrorProfile();
        for (int event = 0; event < 5; event++) {
            profile.addTension(6.0f);
            for (int second = 0; second < 90; second++) {
                profile.decayTension(HorrorDirector.TENSION_DECAY_PER_SECOND);
            }
        }

        assertTrue(profile.tension() >= TensionState.UNEASE.threshold,
                "the director must be able to leave CALM without an admin command");
        assertEquals(TensionState.UNEASE, TensionState.forTension(profile.tension()));
    }

    @Test
    void narrativeStagesKeepMajorManifestationsForLater() {
        assertEquals(0, HorrorDirector.minimumStageForEvent("footsteps"));
        assertEquals(0, HorrorDirector.minimumStageForEvent("tunnel_silhouette"));
        assertEquals(1, HorrorDirector.minimumStageForEvent("distant_sighting"));
        assertEquals(2, HorrorDirector.minimumStageForEvent("watcher_stare"));
        assertEquals(3, HorrorDirector.minimumStageForEvent("mining_bait"));
    }

    @Test
    void tensionTemporarilyUnlocksContentWithoutChangingTheStory() {
        assertEquals(0, HorrorDirector.stageUnlockedByTension(TensionState.CALM));
        assertEquals(2, HorrorDirector.stageUnlockedByTension(TensionState.WATCHED));
        assertEquals(4, HorrorDirector.stageUnlockedByTension(TensionState.HUNT));
        assertEquals(5, HorrorDirector.stageUnlockedByTension(TensionState.CRISIS));
    }

    @Test
    void defaultPacingIsObservableAndFrequencyFourReachesSelectionCadence() {
        assertEquals(2400L, HorrorDirector.gracePeriodTicks(120));
        assertEquals(1050L,
                HorrorDirector.minimumEventGapTicks(35, 1.0f, TensionState.CALM));
        assertEquals(350L,
                HorrorDirector.minimumEventGapTicks(35, 1.0f, TensionState.CRISIS));
        assertEquals(100L,
                HorrorDirector.minimumEventGapTicks(35, 4.0f, TensionState.CRISIS));
    }

    @Test
    void firstManifestationIsImmediatelyDueThenWaitsForItsDeadline() {
        assertTrue(HorrorDirector.manifestationDue(null, 1000L, 2400L));
        assertTrue(!HorrorDirector.manifestationDue(1000L, 3399L, 2400L));
        assertTrue(HorrorDirector.manifestationDue(1000L, 3400L, 2400L));
    }

    @Test
    void hostSlotsAndEffectiveManifestationsAreIndependent() {
        assertTrue(HorrorDirector.consumesHostSlot("mining_bait"));
        assertTrue(!HorrorDirector.countsAsManifestation("mining_bait"));
        assertTrue(HorrorDirector.isAggressiveEvent("mining_bait"));
        assertTrue(!HorrorDirector.isHostObservationEvent("mining_bait"));

        assertTrue(!HorrorDirector.consumesHostSlot("cave_face"));
        assertTrue(!HorrorDirector.countsAsManifestation("cave_face"));
        assertTrue(HorrorDirector.isAggressiveEvent("cave_face"));
        assertTrue(!HorrorDirector.isHostObservationEvent("cave_face"));

        assertTrue(HorrorDirector.consumesHostSlot("tunnel_silhouette"));
        assertTrue(HorrorDirector.countsAsManifestation("tunnel_silhouette"));
        assertTrue(HorrorDirector.isHostObservationEvent("tunnel_silhouette"));
        assertTrue(HorrorDirector.countsAsManifestation("watcher_stare"));
        assertTrue(!HorrorDirector.isHostObservationEvent("watcher_stare"));
    }

    @Test
    void onlyHeavyAutomaticScenesUseTheNearbyGroupLock() {
        assertTrue(HorrorDirector.usesGroupCoordination("tunnel_silhouette"));
        assertTrue(HorrorDirector.usesGroupCoordination("watcher_group"));
        assertTrue(HorrorDirector.usesGroupCoordination("sleep_interrupt"));
        assertTrue(HorrorDirector.usesGroupCoordination("behind_other"));
        assertTrue(HorrorDirector.usesGroupCoordination("paired_echo"));
        assertTrue(HorrorDirector.usesGroupCoordination("stolen_name"));
        assertTrue(!HorrorDirector.usesGroupCoordination("footsteps"));
        assertTrue(!HorrorDirector.usesGroupCoordination("breath"));
    }

    @Test
    void multiplayerScenesUnlockProgressivelyAndKeepTheirThreatClassification() {
        assertEquals(1, HorrorDirector.minimumStageForEvent("paired_echo"));
        assertEquals(2, HorrorDirector.minimumStageForEvent("behind_other"));
        assertEquals(3, HorrorDirector.minimumStageForEvent("stolen_name"));

        assertTrue(HorrorDirector.isHostObservationEvent("behind_other"));
        assertTrue(HorrorDirector.countsAsManifestation("stolen_name"));
        assertTrue(HorrorDirector.isAggressiveEvent("stolen_name"));
        assertTrue(!HorrorDirector.isAggressiveEvent("paired_echo"));
    }

    @Test
    void falseCompanionIsAPrivateMimicManifestationWithoutSharedLocks() {
        assertEquals(2, HorrorDirector.minimumStageForEvent("false_companion"));
        assertEquals("mimic", HorrorDirector.eventFamily("false_companion"));

        assertTrue(HorrorDirector.countsAsManifestation("false_companion"));
        assertTrue(HorrorDirector.isAggressiveEvent("false_companion"));
        assertFalse(HorrorDirector.consumesHostSlot("false_companion"));
        assertFalse(HorrorDirector.isHostObservationEvent("false_companion"));
        assertFalse(HorrorDirector.usesGroupCoordination("false_companion"),
                "une perception individuelle ne doit jamais verrouiller les joueurs voisins");
    }
}

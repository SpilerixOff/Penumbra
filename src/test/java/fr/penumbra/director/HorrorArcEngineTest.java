package fr.penumbra.director;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HorrorArcEngineTest {

    @Test
    void undergroundContextStartsAThreeToSevenMinuteCaveArc() {
        long start = 10_000L;
        DirectorMemory memory = DirectorMemory.fresh(start, start);
        HorrorArcEngine.Decision decision = HorrorArcEngine.tick(memory, start, 40.0f,
                new HorrorArcEngine.Context(true, false, false), 42L);
        assertEquals(DirectorMemory.ArcTheme.CAVE, decision.memory().arc().theme());
        assertEquals(DirectorMemory.ArcPhase.FORESHADOW, decision.memory().arc().phase());

        long finished = start;
        memory = decision.memory();
        for (int phase = 0; phase < 6; phase++) {
            finished = memory.arc().phaseEndsTick();
            decision = HorrorArcEngine.tick(memory, finished, 40.0f,
                    new HorrorArcEngine.Context(true, false, false), 42L);
            memory = decision.memory();
            if (memory.arc().phase() == DirectorMemory.ArcPhase.DORMANT) {
                break;
            }
        }
        long duration = finished - start;
        assertTrue(duration >= 20L * 60L * 3L, "arc shorter than three minutes");
        assertTrue(duration <= 20L * 60L * 7L, "arc longer than seven minutes");
    }

    @Test
    void manifestPhaseCreatesAHardVisualDeadline() {
        long now = 2_000L;
        DirectorMemory memory = DirectorMemory.fresh(now, now)
                .withArc(new DirectorMemory.ArcState(
                        DirectorMemory.ArcTheme.STALKER, DirectorMemory.ArcPhase.MANIFEST,
                        1_000L, 1_800L, 3_000L, 1_900L, 2_100L, 4, false));

        assertFalse(HorrorArcEngine.tick(memory, 2_099L, 80.0f,
                new HorrorArcEngine.Context(false, false, false), 1L).hardManifestationDue());
        assertTrue(HorrorArcEngine.tick(memory, 2_100L, 80.0f,
                new HorrorArcEngine.Context(false, false, false), 1L).hardManifestationDue());

        DirectorMemory satisfied = memory.recordManifestation(2_050L);
        assertFalse(HorrorArcEngine.tick(satisfied, 2_100L, 80.0f,
                new HorrorArcEngine.Context(false, false, false), 1L).hardManifestationDue());
    }

    @Test
    void falseCalmSurvivesReloadAndProducesOneBreak() {
        DirectorMemory memory = HorrorArcEngine.forceFalseCalm(
                DirectorMemory.fresh(5_000L, 5_000L), 5_000L, 900L);
        HorrorArcEngine.Decision active = HorrorArcEngine.tick(memory, 5_500L, 95.0f,
                new HorrorArcEngine.Context(false, false, false), 7L);
        assertTrue(active.falseCalm());

        HorrorArcEngine.Decision ended = HorrorArcEngine.tick(active.memory(), 5_900L, 95.0f,
                new HorrorArcEngine.Context(false, false, false), 7L);
        assertTrue(ended.breakDue());
        assertEquals(DirectorMemory.ArcPhase.DORMANT, ended.memory().arc().phase());

        DirectorMemory consumed = HorrorArcEngine.consumeBreak(ended.memory(), 5_900L);
        HorrorArcEngine.Decision next = HorrorArcEngine.tick(consumed, 5_901L, 0.0f,
                new HorrorArcEngine.Context(false, false, false), 7L);
        assertFalse(next.breakDue());
    }

    @Test
    void phaseMultipliersMakeManifestFasterAndAftermathSlower() {
        assertTrue(HorrorArcEngine.gapMultiplier(DirectorMemory.ArcPhase.MANIFEST)
                < HorrorArcEngine.gapMultiplier(DirectorMemory.ArcPhase.BUILD));
        assertTrue(HorrorArcEngine.gapMultiplier(DirectorMemory.ArcPhase.AFTERMATH)
                > HorrorArcEngine.gapMultiplier(DirectorMemory.ArcPhase.FORESHADOW));
        assertEquals(2.2f, HorrorArcEngine.themeMultiplier(
                DirectorMemory.ArcTheme.REFUGE, "refuge"));
    }
}

package fr.penumbra.entity.scuttler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScuttlerBehaviorTest {

    @Test
    void sceneTableContainsExactlySeventyCrossingsAndThirtyChases() {
        int crossings = 0;
        int chases = 0;
        for (int roll = 0; roll < 100; roll++) {
            if (ScuttlerBehavior.chooseScene(roll) == ScuttlerEntity.SCENE_CROSSING) {
                crossings++;
            } else {
                chases++;
            }
        }
        assertEquals(70, crossings);
        assertEquals(30, chases);
    }

    @Test
    void chaseDurationAlwaysStaysBetweenThreeAndFiveSeconds() {
        assertEquals(60, ScuttlerBehavior.chaseDurationTicks(0));
        assertEquals(100, ScuttlerBehavior.chaseDurationTicks(40));
        assertEquals(60, ScuttlerBehavior.chaseDurationTicks(41));
        for (int entropy = -250; entropy <= 250; entropy++) {
            int duration = ScuttlerBehavior.chaseDurationTicks(entropy);
            assertTrue(duration >= 60 && duration <= 100);
        }
    }

    @Test
    void cooldownHandlesBoundaryAndWorldTimeRollback() {
        long cooldown = ScuttlerManager.PLAYER_COOLDOWN_TICKS;
        assertTrue(ScuttlerBehavior.cooldownReady(null, 500L, cooldown));
        assertFalse(ScuttlerBehavior.cooldownReady(500L, 500L + cooldown - 1L, cooldown));
        assertTrue(ScuttlerBehavior.cooldownReady(500L, 500L + cooldown, cooldown));
        assertTrue(ScuttlerBehavior.cooldownReady(500L, 10L, cooldown));
    }

    @Test
    void frequencyPressureScalesAndNeverCreatesInvalidRandomBound() {
        assertEquals(32, ScuttlerBehavior.spawnDenominator(32, 1.0f, 1.0f));
        assertEquals(8, ScuttlerBehavior.spawnDenominator(32, 2.0f, 2.0f));
        assertEquals(1, ScuttlerBehavior.spawnDenominator(32, 100.0f, 100.0f));
        assertTrue(ScuttlerBehavior.spawnDenominator(32, 0.0f, 0.0f) >= 1);
    }

    @Test
    void nightWindowRepeatsAcrossMinecraftDays() {
        assertFalse(ScuttlerBehavior.isNight(12_999L));
        assertTrue(ScuttlerBehavior.isNight(13_000L));
        assertTrue(ScuttlerBehavior.isNight(23_000L));
        assertFalse(ScuttlerBehavior.isNight(23_001L));
        assertTrue(ScuttlerBehavior.isNight(24_000L + 18_000L));
        assertTrue(ScuttlerBehavior.isNight(-6_000L));
    }
}

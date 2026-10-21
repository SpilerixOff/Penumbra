package fr.penumbra.client.animation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonsterAnimationMathTest {

    @Test
    void easingClampsInvalidAndOutOfRangeInputs() {
        assertEquals(0.0f, MonsterAnimationMath.smoothstep(Float.NaN));
        assertEquals(0.0f, MonsterAnimationMath.smootherstep(-4.0f));
        assertEquals(1.0f, MonsterAnimationMath.smootherstep(4.0f));
    }

    @Test
    void attackHasPreparationThenStableFullReach() {
        MonsterAnimationMath.AttackPose start = MonsterAnimationMath.attackPose(0.0f);
        MonsterAnimationMath.AttackPose preparation = MonsterAnimationMath.attackPose(0.18f);
        MonsterAnimationMath.AttackPose end = MonsterAnimationMath.attackPose(1.0f);

        assertEquals(0.0f, start.anticipation());
        assertTrue(preparation.anticipation() > preparation.strike());
        assertEquals(1.0f, end.strike(), 0.00001f);
        assertEquals(0.0f, end.impact(), 0.00001f);
    }

    @Test
    void proximityIsMonotonicAndDistanceBounded() {
        float far = MonsterAnimationMath.proximity(20.0 * 20.0, 3.0f, 12.0f);
        float middle = MonsterAnimationMath.proximity(7.0 * 7.0, 3.0f, 12.0f);
        float near = MonsterAnimationMath.proximity(2.0 * 2.0, 3.0f, 12.0f);

        assertEquals(0.0f, far);
        assertTrue(middle > far && middle < near);
        assertEquals(1.0f, near);
        assertEquals(0.0f, MonsterAnimationMath.proximity(
                Double.NaN, 3.0f, 12.0f));
    }

    @Test
    void organicBreathAlwaysStaysFiniteAndNormalized() {
        for (int tick = 0; tick < 10_000; tick++) {
            float breath = MonsterAnimationMath.organicBreath(tick, 0.031f);
            assertTrue(Float.isFinite(breath));
            assertTrue(breath >= -1.0f && breath <= 1.0f);
        }
    }

    @Test
    void twitchPulseStaysBoundedAndMostlySilent() {
        int active = 0;
        for (int tick = 0; tick < 20_000; tick++) {
            float pulse = MonsterAnimationMath.twitchPulse(tick, 220.0f, 7.0f, 1013);
            assertTrue(Float.isFinite(pulse));
            assertTrue(pulse >= 0.0f && pulse <= 1.0f);
            if (pulse > 0.0f) {
                active++;
            }
        }
        // Le spasme existe mais reste rare : moins de 10 % du temps actif.
        assertTrue(active > 0);
        assertTrue(active < 2_000);
    }

    @Test
    void twitchPulseShiftsPositionBetweenCyclesAndSeeds() {
        int firstCycleTick = firstActiveTick(0, 1013);
        int secondCycleTick = firstActiveTick(1, 1013);
        int otherSeedTick = firstActiveTick(0, 977);
        // Position relative dans le cycle : différente d'un cycle et d'un seed à l'autre.
        assertTrue(firstCycleTick % 220 != secondCycleTick % 220
                || firstCycleTick % 220 != otherSeedTick % 220);
    }

    @Test
    void twitchPulseRejectsDegenerateInputs() {
        assertEquals(0.0f, MonsterAnimationMath.twitchPulse(Float.NaN, 220.0f, 7.0f, 1));
        assertEquals(0.0f, MonsterAnimationMath.twitchPulse(50.0f, 0.0f, 7.0f, 1));
        assertEquals(0.0f, MonsterAnimationMath.twitchPulse(50.0f, 220.0f, 0.0f, 1));
        assertEquals(0.0f, MonsterAnimationMath.twitchPulse(50.0f, 7.0f, 220.0f, 1));
    }

    private static int firstActiveTick(int cycle, int seed) {
        for (int tick = cycle * 220; tick < (cycle + 1) * 220; tick++) {
            if (MonsterAnimationMath.twitchPulse(tick, 220.0f, 7.0f, seed) > 0.0f) {
                return tick;
            }
        }
        return -1;
    }
}

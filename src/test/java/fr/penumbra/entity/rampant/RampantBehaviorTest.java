package fr.penumbra.entity.rampant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RampantBehaviorTest {

    @Test
    void intensityAndFrequencyIncreaseSpawnRateWithoutZeroDenominator() {
        assertEquals(40, RampantBehavior.spawnDenominator(40, 1.0f, 1.0f));
        assertTrue(RampantBehavior.spawnDenominator(40, 2.0f, 2.0f) < 40);
        assertEquals(1, RampantBehavior.spawnDenominator(1, 4.0f, 2.0f));
    }

    @Test
    void clingHasMinimumButAlwaysEndsAtMaximum() {
        assertFalse(RampantBehavior.shouldRelease(
                RampantBehavior.CLING_MIN_TICKS - 1, 1.0));
        assertTrue(RampantBehavior.shouldRelease(
                RampantBehavior.CLING_MIN_TICKS, 14.0 * 14.0));
        assertTrue(RampantBehavior.shouldRelease(
                RampantBehavior.CLING_MAX_TICKS, 1000.0));
    }

    @Test
    void leapTimeoutIsBounded() {
        assertFalse(RampantBehavior.leapExpired(RampantBehavior.LEAP_TIMEOUT_TICKS - 1));
        assertTrue(RampantBehavior.leapExpired(RampantBehavior.LEAP_TIMEOUT_TICKS));
    }

    @Test
    void wallSeekingPrioritizesHeightAndPostAttackReperch() {
        assertFalse(RampantSurfaceMotion.shouldSeekWall(19, 8.0, false));
        assertTrue(RampantSurfaceMotion.shouldSeekWall(20, 1.76, false));
        assertTrue(RampantSurfaceMotion.shouldSeekWall(8, 0.0, true));
        assertTrue(RampantSurfaceMotion.shouldSeekWall(90, -4.0, false));
    }

    @Test
    void wallDirectionUsesHysteresisAndReperchAlwaysClimbs() {
        assertEquals(1, RampantSurfaceMotion.chooseWallDirection(2.0, -1, false));
        assertEquals(-1, RampantSurfaceMotion.chooseWallDirection(-3.0, 1, false));
        assertEquals(-1, RampantSurfaceMotion.chooseWallDirection(0.0, -1, false));
        assertEquals(1, RampantSurfaceMotion.chooseWallDirection(-4.0, -1, true));
    }

    @Test
    void ceilingStepIsNormalizedAndStillWhenAlreadyAligned() {
        RampantSurfaceMotion.Step diagonal = RampantSurfaceMotion.ceilingStep(3.0, 4.0);
        assertEquals(RampantSurfaceMotion.CEILING_SPEED, diagonal.length(), 0.000001);
        assertEquals(0.0, RampantSurfaceMotion.ceilingStep(0.0, 0.0).length());
        assertEquals(RampantSurfaceMotion.WALL_SPEED,
                RampantSurfaceMotion.wallStep(1).length(), 0.000001);
    }

    @Test
    void surfaceMotionAlwaysEndsInAPerch() {
        assertFalse(RampantSurfaceMotion.shouldPerch(20, 2.0, 4.0, false));
        assertTrue(RampantSurfaceMotion.shouldPerch(20,
                RampantSurfaceMotion.REPERCH_DISTANCE, 4.0, true));
        assertTrue(RampantSurfaceMotion.shouldPerch(
                RampantSurfaceMotion.MAX_CLIMB_TICKS, 0.0, 10.0, false));
    }
}

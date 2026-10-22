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
    void ceilingAmbushNeedsAPassageBelowAndClearSightInsteadOfFourteenBlockProximity() {
        assertFalse(RampantBehavior.inAmbushZone(true, 12, -3, 0, true));
        assertFalse(RampantBehavior.inAmbushZone(true, 0, 2, 0, true));
        assertFalse(RampantBehavior.inAmbushZone(true, 0, -3, 0, false));
        assertFalse(RampantBehavior.inAmbushZone(true, Double.NaN, -3, 0, true));
        assertTrue(RampantBehavior.inAmbushZone(true, 1, -3, 1, true));
        assertTrue(RampantBehavior.inAmbushZone(false, 2, 0, 1, true));
    }

    @Test
    void waitingNeverAttacksAtATimeoutEvenIfTheOwnerRemainsNearby() {
        RampantBehavior.Ambush ambush = new RampantBehavior.Ambush();
        for (int tick = 0; tick < RampantBehavior.CLING_MAX_TICKS; tick++) {
            assertEquals(RampantBehavior.AmbushDecision.WAIT, ambush.tick(tick, false));
        }
        assertEquals(RampantBehavior.AmbushDecision.EXPIRE,
                ambush.tick(RampantBehavior.CLING_MAX_TICKS, true));
    }

    @Test
    void steppingOutDuringTheWarningCancelsTheDropAndRequiresANewWarning() {
        RampantBehavior.Ambush ambush = new RampantBehavior.Ambush();
        assertEquals(RampantBehavior.AmbushDecision.WAIT,
                ambush.tick(RampantBehavior.CLING_MIN_TICKS - 1, true));
        int tick = RampantBehavior.CLING_MIN_TICKS;
        assertEquals(RampantBehavior.AmbushDecision.WARN, ambush.tick(tick++, true));
        for (int i = 0; i < 7; i++) ambush.tick(tick++, true);
        assertEquals(RampantBehavior.AmbushDecision.WAIT, ambush.tick(tick++, false));
        assertFalse(ambush.warning());
        assertEquals(RampantBehavior.AmbushDecision.WARN, ambush.tick(tick++, true));
        for (int i = 1; i < RampantBehavior.AMBUSH_WARNING_TICKS - 1; i++) {
            assertEquals(RampantBehavior.AmbushDecision.WAIT, ambush.tick(tick++, true));
        }
        assertEquals(RampantBehavior.AmbushDecision.RELEASE, ambush.tick(tick, true));
        ambush.reset();
        assertFalse(ambush.warning());
    }

    @Test
    void coverFreezesTheLastSeenPositionAndEndsTheRampantPursuit() {
        RampantBehavior.Pursuit pursuit = new RampantBehavior.Pursuit();
        pursuit.observe(true, 12, 30, 8);
        for (int tick = 1; tick < RampantBehavior.LOST_SIGHT_TICKS; tick++) {
            pursuit.observe(false, 12 + tick, 30, 8 + tick);
            assertEquals(new RampantBehavior.Pursuit.Position(12, 30, 8), pursuit.lastSeen());
            assertFalse(pursuit.exhausted());
        }
        pursuit.observe(false, 100, 30, 100);
        assertTrue(pursuit.exhausted());
    }

    @Test
    void reacquiringSightCannotExtendTheTotalChaseAndANewPerchForgetsTheOldPoint() {
        RampantBehavior.Pursuit pursuit = new RampantBehavior.Pursuit();
        for (int tick = 1; tick < RampantBehavior.MAX_PURSUIT_TICKS; tick++) {
            pursuit.observe(tick % 10 == 0, tick, 30, 8);
            assertFalse(pursuit.exhausted());
        }
        pursuit.observe(true, 50, 30, 8);
        assertTrue(pursuit.exhausted());
        pursuit.reset();
        pursuit.observe(false, 500, 20, 500);
        assertEquals(null, pursuit.lastSeen());
        assertFalse(pursuit.exhausted());
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

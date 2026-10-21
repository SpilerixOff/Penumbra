package fr.penumbra.entity.absent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbsentMimicLogicTest {

    @Test
    void gaitPackingClampsAndPreservesPostureFlags() {
        int packed = AbsentMimicLogic.packGait(
                0.21, 22.5f, true, false, true);

        assertEquals(0.5f, AbsentMimicLogic.unpackSpeed(packed), 0.01f);
        assertEquals(0.5f, AbsentMimicLogic.unpackTurn(packed), 0.01f);
        assertTrue(AbsentMimicLogic.isSprinting(packed));
        assertFalse(AbsentMimicLogic.isCrouching(packed));
        assertTrue(AbsentMimicLogic.isAirborne(packed));

        int clamped = AbsentMimicLogic.packGait(99.0, 999.0f,
                false, true, false);
        assertEquals(1.0f, AbsentMimicLogic.unpackSpeed(clamped));
        assertEquals(1.0f, AbsentMimicLogic.unpackTurn(clamped));
        assertTrue(AbsentMimicLogic.isCrouching(clamped));
    }

    @Test
    void attentionRisesWhileWatchedAndFallsQuicklyWhenGazeBreaks() {
        assertEquals(1, AbsentMimicLogic.nextStareTicks(0, true));
        assertEquals(34, AbsentMimicLogic.nextStareTicks(33, true));
        assertEquals(30, AbsentMimicLogic.nextStareTicks(33, false));
        assertEquals(0, AbsentMimicLogic.nextStareTicks(2, false));
    }

    @Test
    void suspicionHasSeveralFairTriggers() {
        assertTrue(AbsentMimicLogic.shouldEnterSuspicion(
                1, 30.0, 0, 0, true));
        assertTrue(AbsentMimicLogic.shouldEnterSuspicion(
                60, 7.0, 0, 0, false));
        assertTrue(AbsentMimicLogic.shouldEnterSuspicion(
                10, 30.0, 34, 0, false));
        assertTrue(AbsentMimicLogic.shouldEnterSuspicion(
                10, 30.0, 0, 28, false));
        assertFalse(AbsentMimicLogic.shouldEnterSuspicion(
                10, 30.0, 0, 0, false));
    }

    @Test
    void errorsGrowWithEveryBehavioralBreak() {
        assertTrue(AbsentMimicLogic.baseError(AbsentMimicLogic.IMITATE)
                < AbsentMimicLogic.baseError(AbsentMimicLogic.LURE));
        assertTrue(AbsentMimicLogic.baseError(AbsentMimicLogic.LURE)
                < AbsentMimicLogic.baseError(AbsentMimicLogic.SUSPICION));
        assertEquals(1.0f, AbsentMimicLogic.baseError(AbsentMimicLogic.REVEAL));
        assertEquals(1.0f, AbsentMimicLogic.baseError(AbsentMimicLogic.HUNT));
    }

    @Test
    void gaitHitchesAreDeterministicForASeedAndTick() {
        long seed = 0x5A17C0DEL;
        for (int tick = 0; tick < 500; tick++) {
            assertEquals(
                    AbsentMimicLogic.movementHitch(seed, tick, 0.7f),
                    AbsentMimicLogic.movementHitch(seed, tick, 0.7f));
        }
    }
}

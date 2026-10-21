package fr.penumbra.entity.originel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OriginelTimingTest {

    @Test
    void normalFrequencySchedulesBetweenFourAndSevenMinutes() {
        assertEquals(4 * 60 * 20, OriginelTiming.sightingDelayTicks(1.0f, 0));
        assertEquals(7 * 60 * 20,
                OriginelTiming.sightingDelayTicks(1.0f, 3 * 60 * 20));
    }

    @Test
    void frequencyScalesTheWholeWindow() {
        assertEquals(2 * 60 * 20, OriginelTiming.sightingDelayTicks(2.0f, 0));
        assertEquals(14 * 60 * 20,
                OriginelTiming.sightingDelayTicks(0.5f, 3 * 60 * 20));
    }

    @Test
    void lungeCurveIsClampedAndAccelerates() {
        assertEquals(0.0, OriginelTiming.lungeEase(-1.0));
        assertEquals(1.0, OriginelTiming.lungeEase(2.0));
        assertEquals(0.125, OriginelTiming.lungeEase(0.5), 1.0E-9);
        assertTrue(OriginelTiming.lungeEase(0.9) - OriginelTiming.lungeEase(0.8)
                > OriginelTiming.lungeEase(0.2) - OriginelTiming.lungeEase(0.1));
    }

    @Test
    void caveSceneAlwaysLeavesTimeToReachConfiguredStareThreshold() {
        assertEquals(100, OriginelTiming.caveSceneDurationTicks(100, 3));
        assertEquals(220, OriginelTiming.caveSceneDurationTicks(80, 10));
        assertEquals(140, OriginelTiming.caveSceneDurationTicks(140, 1));
    }
}

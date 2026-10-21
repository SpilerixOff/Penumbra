package fr.penumbra.entity.contrefait;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContrefaitLungeTest {

    @Test
    void packedStateKeepsElapsedAndClampsDuration() {
        int state = ContrefaitLunge.pack(5, 11);
        assertEquals(5, ContrefaitLunge.elapsedTicks(state));
        assertEquals(11, ContrefaitLunge.durationTicks(state));

        int clamped = ContrefaitLunge.pack(99, 99);
        assertEquals(ContrefaitLunge.MAX_DURATION_TICKS, ContrefaitLunge.elapsedTicks(clamped));
        assertEquals(ContrefaitLunge.MAX_DURATION_TICKS, ContrefaitLunge.durationTicks(clamped));
    }

    @Test
    void sustainedStareAccumulatesAndBrokenStareResets() {
        assertEquals(2, ContrefaitLunge.nextStareTicks(0, true, 2));
        assertEquals(42, ContrefaitLunge.nextStareTicks(40, true, 2));
        assertEquals(0, ContrefaitLunge.nextStareTicks(42, false, 2));
        assertEquals(20, ContrefaitLunge.stareThresholdTicks(0));
        assertEquals(60, ContrefaitLunge.stareThresholdTicks(3));
    }

    @Test
    void renderProgressAndTrajectoryAreBoundedAndMonotonic() {
        int state = ContrefaitLunge.pack(4, 10);
        assertEquals(0.45f, ContrefaitLunge.progress(state, 0.5f), 0.0001f);
        assertEquals(0.0f, ContrefaitLunge.progress(0, 0.5f));

        double previous = -1.0;
        for (int tick = 0; tick <= 10; tick++) {
            double current = ContrefaitLunge.easedProgress(tick, 10);
            assertTrue(current >= previous);
            previous = current;
        }
        assertEquals(1.0, previous);
    }
}

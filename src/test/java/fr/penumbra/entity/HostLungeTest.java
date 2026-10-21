package fr.penumbra.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostLungeTest {

    @Test
    void packedStateClampsElapsedAndDuration() {
        int state = HostLunge.pack(4, 10);
        assertEquals(4, HostLunge.elapsedTicks(state));
        assertEquals(10, HostLunge.durationTicks(state));

        int clamped = HostLunge.pack(99, 99);
        assertEquals(HostLunge.MAX_DURATION_TICKS, HostLunge.elapsedTicks(clamped));
        assertEquals(HostLunge.MAX_DURATION_TICKS, HostLunge.durationTicks(clamped));
    }

    @Test
    void renderProgressIsInterpolatedAndBounded() {
        int state = HostLunge.pack(4, 10);
        assertEquals(0.45f, HostLunge.progress(state, 0.5f), 0.0001f);
        assertEquals(0.0f, HostLunge.progress(0, 0.5f));
        assertEquals(1.0f, HostLunge.progress(HostLunge.pack(10, 10), 1.0f));
    }

    @Test
    void easedTrajectoryIsMonotonic() {
        assertEquals(0.0, HostLunge.easedProgress(0, 10));
        assertEquals(1.0, HostLunge.easedProgress(10, 10));
        double previous = -1.0;
        for (int tick = 0; tick <= 10; tick++) {
            double current = HostLunge.easedProgress(tick, 10);
            assertTrue(current >= previous);
            previous = current;
        }
    }

    @Test
    void realChasesAndTerminalStatesCannotArmTheReaction() {
        assertFalse(HostLunge.canArmFrom(HostBehavior.CHASING));
        assertFalse(HostLunge.canArmFrom(HostBehavior.ATTACKING));
        assertFalse(HostLunge.canArmFrom(HostBehavior.RETREATING));
        assertFalse(HostLunge.canArmFrom(HostBehavior.COOLDOWN));
        assertTrue(HostLunge.canArmFrom(HostBehavior.OBSERVING));
        assertTrue(HostLunge.canArmFrom(HostBehavior.STALKING));
        assertTrue(HostLunge.canArmFrom(HostBehavior.HIDING));
    }
}

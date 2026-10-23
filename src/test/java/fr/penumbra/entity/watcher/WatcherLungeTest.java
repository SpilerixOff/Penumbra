package fr.penumbra.entity.watcher;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WatcherLungeTest {

    @Test
    void packedStateKeepsElapsedTickAndBoundedDuration() {
        int state = WatcherLunge.pack(4, 8);
        assertEquals(4, WatcherLunge.elapsedTicks(state));
        assertEquals(8, WatcherLunge.durationTicks(state));

        int clamped = WatcherLunge.pack(99, 99);
        assertEquals(WatcherLunge.MAX_DURATION_TICKS, WatcherLunge.elapsedTicks(clamped));
        assertEquals(WatcherLunge.MAX_DURATION_TICKS, WatcherLunge.durationTicks(clamped));
    }

    @Test
    void renderProgressInterpolatesAndClamps() {
        int state = WatcherLunge.pack(4, 8);
        assertEquals(0.5625f, WatcherLunge.progress(state, 0.5f), 0.0001f);
        assertEquals(0.0f, WatcherLunge.progress(0, 0.5f));
        assertEquals(1.0f, WatcherLunge.progress(WatcherLunge.pack(8, 8), 1.0f));
    }

    @Test
    void smoothstepIsMonotonicAndReachesEndpoints() {
        assertEquals(0.0, WatcherLunge.easedProgress(0, 10));
        assertEquals(1.0, WatcherLunge.easedProgress(10, 10));
        double previous = -1.0;
        for (int tick = 0; tick <= 10; tick++) {
            double current = WatcherLunge.easedProgress(tick, 10);
            assertTrue(current >= previous);
            previous = current;
        }
    }
}

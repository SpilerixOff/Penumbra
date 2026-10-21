package fr.penumbra.network;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhotoRequestRateLimiterTest {

    @Test
    void limitsEachPlayerIndependentlyAndResetsAtWindowBoundary() {
        PhotoRequestRateLimiter limiter = new PhotoRequestRateLimiter(20L, 2);
        UUID first = new UUID(0L, 1L);
        UUID second = new UUID(0L, 2L);

        assertTrue(limiter.allow(first, 100L));
        assertTrue(limiter.allow(first, 101L));
        assertFalse(limiter.allow(first, 102L));
        assertTrue(limiter.allow(second, 102L));
        assertTrue(limiter.allow(first, 120L));
    }

    @Test
    void clockRollbackStartsAFreshWindowAndDisconnectCleanupIsBounded() {
        PhotoRequestRateLimiter limiter = new PhotoRequestRateLimiter(20L, 1);
        UUID player = new UUID(0L, 3L);

        assertTrue(limiter.allow(player, 500L));
        assertFalse(limiter.allow(player, 501L));
        assertTrue(limiter.allow(player, 12L));
        assertEquals(1, limiter.trackedPlayers());

        limiter.clear(player);
        assertEquals(0, limiter.trackedPlayers());
        assertFalse(limiter.allow(null, 20L));
    }
}

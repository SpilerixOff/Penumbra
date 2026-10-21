package fr.penumbra.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HostBehaviorTest {

    @Test
    void invalidNetworkOrdinalsAreClamped() {
        assertEquals(HostBehavior.DORMANT, HostBehavior.byId(-100));
        assertEquals(HostBehavior.COOLDOWN, HostBehavior.byId(10_000));
    }

    @Test
    void validOrdinalsRoundTrip() {
        for (HostBehavior behavior : HostBehavior.values()) {
            assertEquals(behavior, HostBehavior.byId(behavior.ordinal()));
        }
    }
}

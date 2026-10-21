package fr.penumbra.entity.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HostPursuitMemoryTest {

    @Test
    void hidingKeepsTheLastSeenCornerRatherThanTrackingThroughWalls() {
        HostPursuitMemory memory = new HostPursuitMemory();
        memory.observe(true, 12, 64, 8);
        for (int tick = 0; tick < HostPursuitMemory.LOST_SIGHT_TICKS - 1; tick++) {
            memory.observe(false, 12 + tick, 64, 8 + tick);
            assertEquals(new HostPursuitMemory.Position(12, 64, 8), memory.lastSeen());
            assertFalse(memory.escaped());
        }
        memory.observe(false, 100, 70, 100);
        assertTrue(memory.escaped());
    }

    @Test
    void peekingResetsTheEscapeWindowAndUpdatesTheSearchPoint() {
        HostPursuitMemory memory = new HostPursuitMemory();
        for (int tick = 0; tick < 70; tick++) {
            memory.observe(false, 0, 0, 0);
        }
        memory.observe(true, -4, 65, 12);
        assertEquals(new HostPursuitMemory.Position(-4, 65, 12), memory.lastSeen());
        for (int tick = 0; tick < 79; tick++) {
            memory.observe(false, 99, 99, 99);
        }
        assertFalse(memory.escaped());
        memory.observe(false, 99, 99, 99);
        assertTrue(memory.escaped());
    }

    @Test
    void aNewChaseCannotReuseThePreviousPlayersLocation() {
        HostPursuitMemory memory = new HostPursuitMemory();
        memory.observe(true, 12, 64, 8);
        memory.reset();
        memory.observe(false, 500, 64, 500);
        assertNull(memory.lastSeen());
        assertFalse(memory.escaped());
        memory.observe(true, Double.NaN, 64, 3);
        assertNull(memory.lastSeen());
    }
}

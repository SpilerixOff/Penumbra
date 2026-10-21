package fr.penumbra.network;

import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoundDeliveryGateTest {

    private static final Identifier OVERWORLD =
            Identifier.fromNamespaceAndPath("minecraft", "overworld");
    private static final Identifier NETHER =
            Identifier.fromNamespaceAndPath("minecraft", "the_nether");
    private static final Identifier WHISPER =
            Identifier.fromNamespaceAndPath("penumbra", "host_whisper");
    private static final Identifier STINGER =
            Identifier.fromNamespaceAndPath("penumbra", "host_stinger");
    private static final Vec3 ORIGIN = new Vec3(0.0, 64.0, 0.0);

    @Test
    void ordinaryBudgetIsIsolatedBySourceListenerAndDimension() {
        SoundDeliveryGate gate = new SoundDeliveryGate(32, 3L);
        UUID source = new UUID(0L, 1L);
        UUID otherSource = new UUID(0L, 2L);
        UUID listener = new UUID(0L, 3L);
        UUID otherListener = new UUID(0L, 4L);

        assertTrue(gate.tryAcquire(OVERWORLD, source, listener, WHISPER, ORIGIN, 100L, false));
        assertFalse(gate.tryAcquire(OVERWORLD, source, listener, STINGER, ORIGIN, 101L, false));
        assertTrue(gate.tryAcquire(OVERWORLD, otherSource, listener, WHISPER, ORIGIN, 101L, false));
        assertTrue(gate.tryAcquire(OVERWORLD, source, otherListener, WHISPER, ORIGIN, 101L, false));
        assertTrue(gate.tryAcquire(NETHER, source, listener, WHISPER, ORIGIN, 101L, false));
        assertTrue(gate.tryAcquire(OVERWORLD, source, listener, WHISPER, ORIGIN, 103L, false));
    }

    @Test
    void priorityDeduplicatesAnIdenticalPacketEvenWhenInterleaved() {
        SoundDeliveryGate gate = new SoundDeliveryGate(8, 3L);
        UUID source = new UUID(0L, 11L);
        UUID listener = new UUID(0L, 12L);

        assertTrue(gate.tryAcquire(OVERWORLD, source, listener, WHISPER, ORIGIN, 50L, true));
        assertFalse(gate.tryAcquire(OVERWORLD, source, listener, WHISPER, ORIGIN, 50L, true));
        assertTrue(gate.tryAcquire(OVERWORLD, source, listener, STINGER, ORIGIN, 50L, true));
        assertFalse(gate.tryAcquire(OVERWORLD, source, listener, WHISPER, ORIGIN, 50L, true));
        assertTrue(gate.tryAcquire(OVERWORLD, source, listener, WHISPER, ORIGIN, 51L, true));
    }

    @Test
    void priorityKeepsPacketsWithDifferentPositionVolumeOrPitch() {
        SoundDeliveryGate gate = new SoundDeliveryGate(8, 3L);
        UUID source = new UUID(0L, 13L);
        UUID listener = new UUID(0L, 14L);

        assertTrue(gate.tryAcquire(OVERWORLD, source, listener, WHISPER, ORIGIN,
                0.8f, 1.0f, 60L, true));
        assertTrue(gate.tryAcquire(OVERWORLD, source, listener, WHISPER, ORIGIN,
                0.9f, 1.0f, 60L, true));
        assertTrue(gate.tryAcquire(OVERWORLD, source, listener, WHISPER, ORIGIN,
                0.9f, 1.1f, 60L, true));
        assertTrue(gate.tryAcquire(OVERWORLD, source, listener, WHISPER,
                ORIGIN.add(0.001, 0.0, 0.0), 0.9f, 1.1f, 60L, true));
        assertFalse(gate.tryAcquire(OVERWORLD, source, listener, WHISPER,
                ORIGIN.add(0.001, 0.0, 0.0), 0.9f, 1.1f, 60L, true));
    }

    @Test
    void priorityNeverConsumesTheOrdinaryBudgetAndRollbackResetsIt() {
        SoundDeliveryGate gate = new SoundDeliveryGate(8, 3L);
        UUID source = new UUID(0L, 21L);
        UUID listener = new UUID(0L, 22L);

        assertTrue(gate.tryAcquire(OVERWORLD, source, listener, STINGER, ORIGIN, 500L, true));
        assertTrue(gate.tryAcquire(OVERWORLD, source, listener, WHISPER, ORIGIN, 500L, false));
        assertFalse(gate.tryAcquire(OVERWORLD, source, listener, WHISPER, ORIGIN, 501L, false));
        assertTrue(gate.tryAcquire(OVERWORLD, source, listener, WHISPER, ORIGIN, 5L, false));
    }

    @Test
    void routeTableStaysBoundedAndDisconnectCleanupRemovesBothSides() {
        SoundDeliveryGate gate = new SoundDeliveryGate(2, 0L);
        UUID listener = new UUID(0L, 31L);
        UUID sourceA = new UUID(0L, 32L);
        UUID sourceB = new UUID(0L, 33L);
        UUID sourceC = new UUID(0L, 34L);

        gate.tryAcquire(OVERWORLD, sourceA, listener, WHISPER, ORIGIN, 1L, false);
        gate.tryAcquire(OVERWORLD, sourceB, listener, WHISPER, ORIGIN, 1L, false);
        gate.tryAcquire(OVERWORLD, sourceC, listener, WHISPER, ORIGIN, 1L, false);
        assertTrue(gate.size() <= 2);

        gate.clear(listener);
        assertTrue(gate.size() == 0);
    }

    @Test
    void recentlyTouchedRouteKeepsItsThrottleUnderCapacityPressure() {
        SoundDeliveryGate gate = new SoundDeliveryGate(2, 3L);
        UUID sourceA = new UUID(0L, 41L);
        UUID sourceB = new UUID(0L, 42L);
        UUID sourceC = new UUID(0L, 43L);
        UUID listener = new UUID(0L, 44L);

        assertTrue(gate.tryAcquire(OVERWORLD, sourceA, listener, WHISPER, ORIGIN, 0L, false));
        assertTrue(gate.tryAcquire(OVERWORLD, sourceB, listener, WHISPER, ORIGIN, 0L, false));
        assertFalse(gate.tryAcquire(OVERWORLD, sourceA, listener, WHISPER, ORIGIN, 1L, false));
        assertTrue(gate.tryAcquire(OVERWORLD, sourceC, listener, WHISPER, ORIGIN, 1L, false));
        assertFalse(gate.tryAcquire(OVERWORLD, sourceA, listener, WHISPER, ORIGIN, 2L, false));
    }
}

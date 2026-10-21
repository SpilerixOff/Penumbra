package fr.penumbra.client.jumpscare;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JumpscareCooldownGateTest {

    @Test
    void criticalScareBypassesNormalCooldown() {
        JumpscareCooldownGate gate = new JumpscareCooldownGate(400, 100);

        assertTrue(gate.tryAcquire(false));
        assertTrue(gate.tryAcquire(true));
    }

    @Test
    void criticalCooldownStillPreventsScreamerSpam() {
        JumpscareCooldownGate gate = new JumpscareCooldownGate(400, 3);

        assertTrue(gate.tryAcquire(true));
        assertFalse(gate.tryAcquire(true));
        gate.tick();
        gate.tick();
        assertFalse(gate.tryAcquire(true));
        gate.tick();
        assertTrue(gate.tryAcquire(true));
    }

    @Test
    void criticalScareAlsoSilencesWeakEffectsAfterIt() {
        JumpscareCooldownGate gate = new JumpscareCooldownGate(2, 1);

        assertTrue(gate.tryAcquire(true));
        assertFalse(gate.tryAcquire(false));
        gate.tick();
        assertFalse(gate.tryAcquire(false));
        gate.tick();
        assertTrue(gate.tryAcquire(false));
    }

    @Test
    void lungeFinalReplacesARecentCriticalVisual() {
        JumpscareCooldownGate gate = new JumpscareCooldownGate(400, 100);

        assertTrue(gate.tryAcquire(true));
        assertFalse(gate.tryAcquire(true));
        assertTrue(gate.tryAcquire(true, true));
        assertFalse(gate.tryAcquire(true, true));
        assertFalse(gate.tryAcquire(false));
    }

    @Test
    void resetPreventsCooldownLeakBetweenServers() {
        JumpscareCooldownGate gate = new JumpscareCooldownGate(400, 100);

        assertTrue(gate.tryAcquire(true, true));
        assertFalse(gate.tryAcquire(true, true));
        gate.reset();
        assertTrue(gate.tryAcquire(true, true));
    }
}

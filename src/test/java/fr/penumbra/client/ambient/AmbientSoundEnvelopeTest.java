package fr.penumbra.client.ambient;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmbientSoundEnvelopeTest {
    @Test
    void fadeInAndReleaseHaveFiniteExactEndpoints() {
        AmbientSoundEnvelope envelope = new AmbientSoundEnvelope(0.30f, 20);
        assertEquals(0.0f, envelope.volume());
        for (int tick = 0; tick < 20; tick++) {
            float previous = envelope.volume();
            envelope.tick();
            assertTrue(envelope.volume() >= previous && envelope.volume() <= 0.30f);
        }
        assertEquals(0.30f, envelope.volume());
        envelope.release(20);
        for (int tick = 0; tick < 20; tick++) {
            float previous = envelope.volume();
            envelope.tick();
            assertTrue(envelope.volume() <= previous && envelope.volume() >= 0.0f);
        }
        assertEquals(0.0f, envelope.volume());
        assertTrue(envelope.stopped());
    }

    @Test
    void retargetingStartsAtCurrentGainWithoutAJumpAndCannotReviveReleasedVoices() {
        AmbientSoundEnvelope envelope = new AmbientSoundEnvelope(0.22f, 20);
        for (int tick = 0; tick < 10; tick++) {
            envelope.tick();
        }
        float current = envelope.volume();
        envelope.fadeTo(0.30f, 20);
        assertEquals(current, envelope.volume());
        envelope.release(6);
        envelope.fadeTo(1.0f, 0);
        for (int tick = 0; tick < 6; tick++) {
            envelope.tick();
        }
        assertTrue(envelope.stopped());
        envelope.fadeTo(1.0f, 0);
        envelope.tick();
        assertEquals(0.0f, envelope.volume());
    }

    @Test
    void zeroGainReleaseStopsImmediatelyAndBadValuesNeverReachTheMixer() {
        AmbientSoundEnvelope silent = new AmbientSoundEnvelope(0.30f, 20);
        silent.release(20);
        assertTrue(silent.stopped());
        for (float value : new float[] {Float.NaN, Float.POSITIVE_INFINITY, -1.0f, 100.0f}) {
            AmbientSoundEnvelope envelope = new AmbientSoundEnvelope(value, 0);
            assertTrue(Float.isFinite(envelope.volume()));
            assertTrue(envelope.volume() >= 0.0f && envelope.volume() <= 1.0f);
            envelope.stop();
            assertEquals(0.0f, envelope.volume());
        }
    }

    @Test
    void equalDurationCrossfadeNeverDoublesSteadyBedGain() {
        AmbientSoundEnvelope outgoing = new AmbientSoundEnvelope(0.30f, 0);
        AmbientSoundEnvelope incoming = new AmbientSoundEnvelope(0.30f, 20);
        outgoing.release(20);
        for (int tick = 0; tick < 20; tick++) {
            outgoing.tick();
            incoming.tick();
            assertEquals(0.30f, outgoing.volume() + incoming.volume(), 0.00001f);
        }
        assertTrue(outgoing.stopped());
        assertFalse(incoming.stopped());
    }
}

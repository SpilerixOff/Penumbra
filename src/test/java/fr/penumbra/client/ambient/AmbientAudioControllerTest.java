package fr.penumbra.client.ambient;

import fr.penumbra.director.TensionState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmbientAudioControllerTest {
    @Test
    void falseCalmStopsCurrentRetiringAndHeartbeatVoicesImmediately() {
        Fixture fixture = new Fixture();
        fixture.advance(TensionState.CRISIS, 101);
        assertEquals(2, fixture.beds());
        assertEquals(1, fixture.heartbeats());
        int starts = fixture.voices.size();

        fixture.controller.tick(TensionState.FALSE_CALM, true, false);
        assertEquals(0, fixture.activeVoices());
        fixture.advance(TensionState.FALSE_CALM, 500);
        assertEquals(starts, fixture.voices.size(), "False calm must never schedule another sound");
    }

    @Test
    void disableEvenWhilePausedCutsAllOwnedVoices() {
        Fixture fixture = new Fixture();
        fixture.advance(TensionState.CRISIS, 101);
        fixture.controller.tick(TensionState.CRISIS, false, true);
        assertEquals(0, fixture.activeVoices());
        int starts = fixture.voices.size();
        for (int tick = 0; tick < 500; tick++) {
            fixture.controller.tick(TensionState.CRISIS, false, false);
        }
        assertEquals(starts, fixture.voices.size());
        fixture.advance(TensionState.CRISIS, 1);
        assertEquals(1, fixture.beds());
        assertEquals(1, fixture.heartbeats());
    }

    @Test
    void pauseFreezesSchedulingWithoutRestartingOrExpiringCurrentVoices() {
        Fixture fixture = new Fixture();
        fixture.advance(TensionState.CRISIS, 99);
        int starts = fixture.voices.size();
        for (int tick = 0; tick < 1_000; tick++) {
            // Vanilla freezes the tickable sound instances during a true pause too.
            fixture.controller.tick(TensionState.CRISIS, true, true);
        }
        assertEquals(starts, fixture.voices.size());
        assertEquals(1, fixture.beds());
        assertEquals(1, fixture.heartbeats());
        fixture.advance(TensionState.CRISIS, 1);
        assertEquals(starts, fixture.voices.size());
        fixture.advance(TensionState.CRISIS, 1);
        assertEquals(starts + 1, fixture.voices.size());
        assertEquals(2, fixture.beds());
    }

    @Test
    void normalCalmReleasesCleanlyThenRemainsSilent() {
        Fixture fixture = new Fixture();
        fixture.advance(TensionState.CRISIS, 30);
        fixture.controller.tick(TensionState.CALM, true, false);
        assertEquals(1, fixture.beds(), "Ordinary calm fades instead of cutting a full-gain bed");
        fixture.advance(TensionState.CALM, AmbientAudioController.BED_FADE_TICKS);
        assertEquals(0, fixture.activeVoices());
        int starts = fixture.voices.size();
        fixture.advance(TensionState.UNEASE, 500);
        assertEquals(starts, fixture.voices.size());
    }

    @Test
    void disconnectOrWorldResetClearsEveryVoiceAndAllSchedulingState() {
        Fixture fixture = new Fixture();
        fixture.advance(TensionState.CRISIS, 101);
        List<FakeVoice> previousSession = List.copyOf(fixture.voices);
        fixture.controller.reset();
        fixture.controller.reset();
        assertTrue(previousSession.stream().noneMatch(FakeVoice::active));
        fixture.advance(TensionState.WATCHED, 1);
        assertEquals(1, fixture.beds());
        assertEquals(0, fixture.heartbeats());
        assertEquals(AmbientAudioController.Cue.LOW, fixture.voices.getLast().cue);
    }

    @Test
    void longRunsAndRapidStateChangesCannotStackStreamsOrRaiseTheGainCeiling() {
        Fixture fixture = new Fixture();
        TensionState[] states = {TensionState.CRISIS, TensionState.HUNT,
                TensionState.MANIFESTATION, TensionState.WATCHED, TensionState.CALM,
                TensionState.WATCHED, TensionState.CRISIS, TensionState.FALSE_CALM};
        for (int tick = 0; tick < 4_000; tick++) {
            TensionState state = states[(tick / (tick < 1_000 ? 200 : 3)) % states.length];
            fixture.advance(state, 1);
            assertTrue(fixture.beds() <= 2, "At most current and retiring bed streams");
            assertTrue(fixture.heartbeats() <= 1, "Heartbeat tracks must never overlap");
            double bedGain = fixture.voices.stream().filter(FakeVoice::active)
                    .filter(voice -> voice.cue != AmbientAudioController.Cue.HEARTBEAT)
                    .mapToDouble(voice -> voice.envelope.volume()).sum();
            assertTrue(bedGain <= 0.30001, "Crossfade must not double the strongest bed gain");
        }
    }

    @Test
    void completedOrReloadedStreamsAreReleasedWithoutBusyRetryingTheSoundDevice() {
        Fixture fixture = new Fixture();
        fixture.advance(TensionState.WATCHED, 1);
        FakeVoice missing = fixture.voices.getFirst();
        missing.live = false; // A reload, missing sample or unavailable audio device.
        fixture.advance(TensionState.WATCHED, 1);
        assertTrue(missing.explicitlyStopped, "Release the old handle before forgetting it");
        fixture.advance(TensionState.WATCHED, 98);
        assertEquals(1, fixture.voices.size());
        fixture.advance(TensionState.WATCHED, 1);
        assertEquals(2, fixture.voices.size());
        assertEquals(1, fixture.beds());
    }

    @Test
    void huntAndCrisisShareTheBedButRampItsVolumeAndStopThePulseOnExit() {
        Fixture fixture = new Fixture();
        fixture.advance(TensionState.HUNT, 30);
        FakeVoice bed = fixture.voices.getFirst();
        assertEquals(0.22f, bed.envelope.volume(), 0.00001f);
        fixture.advance(TensionState.CRISIS, 1);
        assertEquals(1, fixture.beds());
        fixture.advance(TensionState.CRISIS, 20);
        assertEquals(0.30f, bed.envelope.volume(), 0.00001f);
        fixture.advance(TensionState.HUNT, 7);
        assertEquals(0, fixture.heartbeats());
        assertFalse(bed.explicitlyStopped);
    }

    private static final class Fixture {
        final List<FakeVoice> voices = new ArrayList<>();
        final AmbientAudioController controller = new AmbientAudioController(
                (cue, volume, pitch, fadeTicks, maxTicks) -> {
                    FakeVoice voice = new FakeVoice(cue, volume, fadeTicks, maxTicks);
                    this.voices.add(voice);
                    return voice;
                }, () -> 100, () -> 1.0);

        void advance(TensionState state, int ticks) {
            for (int tick = 0; tick < ticks; tick++) {
                this.voices.forEach(FakeVoice::tick);
                this.controller.tick(state, true, false);
            }
        }

        long activeVoices() { return this.voices.stream().filter(FakeVoice::active).count(); }
        long beds() { return count(false); }
        long heartbeats() { return count(true); }

        private long count(boolean heartbeat) {
            return this.voices.stream().filter(FakeVoice::active)
                    .filter(voice -> (voice.cue == AmbientAudioController.Cue.HEARTBEAT) == heartbeat).count();
        }
    }

    private static final class FakeVoice implements AmbientAudioController.Voice {
        final AmbientAudioController.Cue cue;
        final AmbientSoundEnvelope envelope;
        final int maximumTicks;
        int age;
        boolean live = true;
        boolean explicitlyStopped;

        FakeVoice(AmbientAudioController.Cue cue, float volume, int fadeTicks, int maximumTicks) {
            this.cue = cue;
            this.envelope = new AmbientSoundEnvelope(volume, fadeTicks);
            this.maximumTicks = maximumTicks;
        }

        void tick() {
            if (active()) {
                this.envelope.tick();
                if (++this.age >= this.maximumTicks) {
                    this.live = false;
                }
            }
        }

        public boolean active() { return this.live && !this.envelope.stopped(); }
        public void fadeTo(float volume, int ticks) { this.envelope.fadeTo(volume, ticks); }
        public void release(int ticks) { this.envelope.release(ticks); }
        public void stopNow() {
            this.envelope.stop();
            this.live = false;
            this.explicitlyStopped = true;
        }
    }
}

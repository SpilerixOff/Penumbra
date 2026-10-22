package fr.penumbra.client.ambient;

import fr.penumbra.director.TensionState;

import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;

/** Owns only Penumbra's background beds and pulse, never other world sounds. */
final class AmbientAudioController {
    static final int BED_FADE_TICKS = 20;
    static final int BED_MAX_TICKS = 180;
    static final int HEARTBEAT_INTERVAL_TICKS = 40;
    static final int HEARTBEAT_MAX_TICKS = 50;
    static final float HEARTBEAT_VOLUME = 0.12f;

    enum Cue { LOW, MID, HIGH, HEARTBEAT }

    interface Voice {
        boolean active();
        void fadeTo(float volume, int ticks);
        void release(int ticks);
        void stopNow();
    }

    @FunctionalInterface
    interface Playback {
        Voice start(Cue cue, float volume, float pitch, int fadeTicks, int maximumTicks);
    }

    private final Playback playback;
    private final IntSupplier nextBedInterval;
    private final DoubleSupplier nextBedPitch;
    private Voice bed;
    private Voice retiringBed;
    private Voice heartbeat;
    private Cue bedCue;
    private float bedVolume;
    private int bedCountdown;
    private int heartbeatCountdown;
    private boolean heartbeatReleasing;

    AmbientAudioController(Playback playback, IntSupplier nextBedInterval, DoubleSupplier nextBedPitch) {
        this.playback = playback;
        this.nextBedInterval = nextBedInterval;
        this.nextBedPitch = nextBedPitch;
    }

    void tick(TensionState state, boolean enabled, boolean paused) {
        // A user's off switch and the false calm cut even voices already fading.
        if (!enabled || state == TensionState.FALSE_CALM) {
            reset();
            return;
        }
        // Minecraft pauses AMBIENT channels and their tickable envelopes itself.
        // Keep our clocks on that same timeline, including the heartbeat phase.
        if (paused) {
            return;
        }
        pruneFinishedVoices();
        tickBed(state);
        tickHeartbeat(state);
    }

    private void tickBed(TensionState state) {
        Cue cue = cueFor(state);
        if (cue == null) {
            retireBed();
            this.bedCue = null;
            this.bedCountdown = 0;
            return;
        }
        float volume = volumeFor(state);
        if (cue != this.bedCue || --this.bedCountdown <= 0) {
            retireBed();
            this.bed = this.playback.start(cue, volume, (float) this.nextBedPitch.getAsDouble(),
                    BED_FADE_TICKS, BED_MAX_TICKS);
            this.bedCue = cue;
            this.bedVolume = volume;
            this.bedCountdown = Math.clamp(this.nextBedInterval.getAsInt(), 100, 120);
        } else if (volume != this.bedVolume) {
            if (this.bed != null) {
                this.bed.fadeTo(volume, BED_FADE_TICKS);
            }
            this.bedVolume = volume;
        }
    }

    private void retireBed() {
        if (this.bed == null) {
            return;
        }
        // Rapid state changes must never accumulate a third stream.
        if (this.retiringBed != null) {
            this.retiringBed.stopNow();
        }
        this.retiringBed = this.bed;
        this.bed = null;
        this.retiringBed.release(BED_FADE_TICKS);
    }

    private void tickHeartbeat(TensionState state) {
        if (state != TensionState.CRISIS) {
            if (this.heartbeat != null && !this.heartbeatReleasing) {
                this.heartbeat.release(6);
                this.heartbeatReleasing = true;
            }
            this.heartbeatCountdown = 0;
            return;
        }
        if (--this.heartbeatCountdown > 0) {
            return;
        }
        // One pulse track at a time, even for resource packs with longer samples.
        if (this.heartbeat != null) {
            this.heartbeat.stopNow();
        }
        this.heartbeat = this.playback.start(Cue.HEARTBEAT, HEARTBEAT_VOLUME, 0.95f,
                2, HEARTBEAT_MAX_TICKS);
        this.heartbeatReleasing = false;
        this.heartbeatCountdown = HEARTBEAT_INTERVAL_TICKS;
    }

    private void pruneFinishedVoices() {
        if (this.bed != null && !this.bed.active()) {
            this.bed.stopNow();
            this.bed = null;
        }
        if (this.retiringBed != null && !this.retiringBed.active()) {
            this.retiringBed.stopNow();
            this.retiringBed = null;
        }
        if (this.heartbeat != null && !this.heartbeat.active()) {
            this.heartbeat.stopNow();
            this.heartbeat = null;
        }
    }

    /** Immediate, idempotent teardown on disconnect, dimension/player change or disable. */
    void reset() {
        if (this.bed != null) {
            this.bed.stopNow();
        }
        if (this.retiringBed != null) {
            this.retiringBed.stopNow();
        }
        if (this.heartbeat != null) {
            this.heartbeat.stopNow();
        }
        this.bed = null;
        this.retiringBed = null;
        this.heartbeat = null;
        this.bedCue = null;
        this.bedVolume = 0;
        this.bedCountdown = 0;
        this.heartbeatCountdown = 0;
        this.heartbeatReleasing = false;
    }

    private static Cue cueFor(TensionState state) {
        return switch (state) {
            case WATCHED -> Cue.LOW;
            case MANIFESTATION -> Cue.MID;
            case HUNT, CRISIS -> Cue.HIGH;
            default -> null;
        };
    }

    private static float volumeFor(TensionState state) {
        return switch (state) {
            case WATCHED -> 0.10f;
            case MANIFESTATION -> 0.16f;
            case HUNT -> 0.22f;
            case CRISIS -> 0.30f;
            default -> 0.0f;
        };
    }
}

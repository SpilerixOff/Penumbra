package fr.penumbra.client.ambient;

/** A bounded linear gain envelope. Releasing a voice is irreversible. */
final class AmbientSoundEnvelope {
    private float volume;
    private float start;
    private float target;
    private int duration;
    private int elapsed;
    private boolean releasing;
    private boolean stopped;

    AmbientSoundEnvelope(float targetVolume, int fadeInTicks) {
        fadeTo(targetVolume, fadeInTicks);
    }

    float volume() {
        return this.volume;
    }

    boolean stopped() {
        return this.stopped;
    }

    void fadeTo(float targetVolume, int ticks) {
        if (!this.releasing && !this.stopped) {
            ramp(targetVolume, ticks);
        }
    }

    void release(int ticks) {
        if (!this.releasing && !this.stopped) {
            this.releasing = true;
            ramp(0.0f, ticks);
            if (this.volume == 0.0f) {
                stop();
            }
        }
    }

    private void ramp(float targetVolume, int ticks) {
        this.start = this.volume;
        this.target = Float.isFinite(targetVolume) ? Math.clamp(targetVolume, 0.0f, 1.0f) : 0.0f;
        this.duration = Math.max(0, ticks);
        this.elapsed = 0;
        if (this.duration == 0) {
            this.volume = this.target;
        }
    }

    void tick() {
        if (this.stopped) {
            return;
        }
        if (this.elapsed < this.duration) {
            this.elapsed++;
            this.volume = this.elapsed == this.duration ? this.target
                    : this.start + (this.target - this.start) * this.elapsed / this.duration;
        }
        if (this.releasing && this.volume <= 0.0f) {
            stop();
        }
    }

    void stop() {
        this.volume = 0.0f;
        this.stopped = true;
    }
}

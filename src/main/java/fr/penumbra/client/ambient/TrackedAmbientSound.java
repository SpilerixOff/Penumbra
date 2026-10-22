package fr.penumbra.client.ambient;

import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.client.config.ClientComfort;
import fr.penumbra.director.TensionState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** A listener-relative, non-looping AMBIENT voice with an explicit gain envelope. */
@Environment(EnvType.CLIENT)
final class TrackedAmbientSound extends AbstractTickableSoundInstance implements AmbientAudioController.Voice {
    private final Minecraft client;
    private final ClientLevel level;
    private final LocalPlayer player;
    private final AmbientSoundEnvelope envelope;
    private final int maximumTicks;
    private int age;

    TrackedAmbientSound(Minecraft client, SoundEvent sound, float volume, float pitch,
                        int fadeTicks, int maximumTicks) {
        super(sound, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
        this.client = client;
        this.level = client.level;
        this.player = client.player;
        this.envelope = new AmbientSoundEnvelope(volume, fadeTicks);
        this.maximumTicks = Math.max(1, maximumTicks);
        this.volume = ClientComfort.ambientVolume(this.envelope.volume());
        this.pitch = Float.isFinite(pitch) ? Math.clamp(pitch, 0.9f, 1.1f) : 1.0f;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.looping = false;
    }

    @Override
    public boolean canStartSilent() {
        return true; // The envelope starts at zero and rises over the next ticks.
    }

    @Override
    public boolean canPlaySound() {
        return !isStopped() && this.level != null && this.player != null
                && this.client.level == this.level && this.client.player == this.player
                && this.player.isAlive() && PenumbraConfig.get().improvedSoundsEnabled
                && ClientComfort.ambientVolume(1.0f) > 0.0f
                && ClientTensionState.state() != TensionState.FALSE_CALM;
    }

    @Override
    public void tick() {
        if (!canPlaySound()) {
            stop();
            return;
        }
        this.envelope.tick();
        this.volume = ClientComfort.ambientVolume(this.envelope.volume());
        if (this.envelope.stopped() || ++this.age >= this.maximumTicks) {
            stop();
        }
    }

    @Override
    public boolean active() {
        return !isStopped() && this.client.getSoundManager().isActive(this);
    }

    @Override
    public void fadeTo(float volume, int ticks) {
        this.envelope.fadeTo(volume, ticks);
    }

    @Override
    public void release(int ticks) {
        this.envelope.release(ticks);
        if (this.envelope.stopped()) {
            stopNow();
        }
    }

    @Override
    public void stopNow() {
        this.envelope.stop();
        this.volume = 0.0f;
        stop();
        this.client.getSoundManager().stop(this);
    }
}

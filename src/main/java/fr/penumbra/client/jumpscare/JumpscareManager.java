package fr.penumbra.client.jumpscare;

import fr.penumbra.PenumbraMod;
import fr.penumbra.client.config.ClientComfort;
import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.registry.PenumbraSounds;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** Sound and brief camera feedback only: no HUD face or blackout, even for legacy packets. */
@Environment(EnvType.CLIENT)
public final class JumpscareManager {
    private static final JumpscareCooldownGate COOLDOWNS = new JumpscareCooldownGate(400, 100);
    private static final RandomSource RANDOM = RandomSource.create();
    private static int shakeTicks, shakeDuration;
    private static float shakeIntensity, lastYawOffset, lastPitchOffset;

    private JumpscareManager() { }

    public static void trigger(String scareType, float intensity) {
        PenumbraConfig config = PenumbraConfig.get();
        ScareFeedback effect = ScareFeedback.forType(scareType);
        if (effect == null) {
            PenumbraMod.LOGGER.warn("[Penumbra] Type de jumpscare inconnu : {}", scareType);
            return;
        }
        float clamped = Float.isFinite(intensity) ? Math.clamp(intensity, 0.0f, 1.0f) : 0.0f;
        if (config.jumpscareFrequency <= 0.0f || clamped <= 0.0f
                || !COOLDOWNS.tryAcquire(effect.critical(), effect.contact())) return;
        float volume = Math.clamp(config.jumpscareVolume * clamped, 0.0f, 1.0f);
        switch (effect.sound()) {
            case NONE -> { /* The entity already emitted its positioned impact sound. */ }
            case STINGER -> playLocal(PenumbraSounds.HOST_STINGER, volume, 1.0f);
            case CREEPER -> playLocal(PenumbraSounds.FAKE_CREEPER, volume, 1.0f);
            case WHISPER -> playLocal(PenumbraSounds.HOST_WHISPER, volume * 0.7f, 0.9f);
            case SHRIEK -> playLocal(config.screamersEnabled
                            ? PenumbraSounds.SCREAMER_SHRIEK : PenumbraSounds.HOST_STINGER,
                    volume, 0.92f + RANDOM.nextFloat() * 0.12f);
        }
        startShake(effect.shakeTicks(), clamped * effect.shakeScale(), config);
    }

    /** Remove the previous offset before adding the next, preserving player input. */
    public static void tick(Minecraft client) {
        COOLDOWNS.tick();
        LocalPlayer player = client.player;
        if (player == null) {
            shakeTicks = 0;
            lastYawOffset = lastPitchOffset = 0.0f;
            return;
        }
        clearOffsets(player);
        if (shakeTicks <= 0) return;
        if (client.screen != null || !PenumbraConfig.get().cameraShakeEnabled
                || !ClientComfort.motionAllowed()) {
            shakeTicks = 0;
            return;
        }
        float damping = (float) shakeTicks / shakeDuration;
        float amplitude = 1.2f * shakeIntensity * damping;
        lastYawOffset = (RANDOM.nextFloat() * 2.0f - 1.0f) * amplitude;
        lastPitchOffset = (RANDOM.nextFloat() * 2.0f - 1.0f) * amplitude * 0.7f;
        player.setYRot(player.getYRot() + lastYawOffset);
        player.setXRot(player.getXRot() + lastPitchOffset);
        shakeTicks--;
    }

    public static void reset() {
        clearOffsets(Minecraft.getInstance().player);
        COOLDOWNS.reset();
        shakeTicks = shakeDuration = 0;
        shakeIntensity = 0.0f;
    }

    public static void onComfortChanged() {
        Minecraft client = Minecraft.getInstance();
        if (!ClientComfort.motionAllowed()) {
            clearOffsets(client.player);
            shakeTicks = 0;
        }
        for (String id : new String[]{"host_stinger", "chase_stinger", "screamer_shriek",
                "fake_creeper", "host_whisper"}) {
            client.getSoundManager().stop(PenumbraMod.id(id), SoundSource.HOSTILE);
        }
    }

    private static void clearOffsets(LocalPlayer player) {
        if (player != null) {
            player.setYRot(player.getYRot() - lastYawOffset);
            player.setXRot(player.getXRot() - lastPitchOffset);
        }
        lastYawOffset = lastPitchOffset = 0.0f;
    }

    private static void startShake(int ticks, float intensity, PenumbraConfig config) {
        if (ticks <= 0 || !config.cameraShakeEnabled || !ClientComfort.motionAllowed()
                || Minecraft.getInstance().screen != null) return;
        shakeDuration = shakeTicks = ticks;
        shakeIntensity = intensity;
    }

    private static void playLocal(SoundEvent sound, float volume, float pitch) {
        volume = ClientComfort.suddenVolume(volume);
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || volume <= 0.0f) return;
        client.level.playLocalSound(client.player.getX(), client.player.getY(), client.player.getZ(),
                sound, SoundSource.HOSTILE, volume, pitch, false);
    }
}
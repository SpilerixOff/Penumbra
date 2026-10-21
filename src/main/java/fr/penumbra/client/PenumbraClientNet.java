package fr.penumbra.client;

import fr.penumbra.PenumbraMod;
import fr.penumbra.client.ambient.ClientTensionState;
import fr.penumbra.client.jumpscare.JumpscareManager;
import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.network.PenumbraNetworking;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * Récepteurs réseau côté client (SPEC §4.7).
 * Joue les sons d'horreur positionnés reçus et délègue les jumpscares au {@link JumpscareManager}.
 */
@Environment(EnvType.CLIENT)
public final class PenumbraClientNet {

    private PenumbraClientNet() {
    }

    /**
     * Enregistre les deux récepteurs S2C globaux. Appelé depuis {@code PenumbraClient#onInitializeClient()}.
     */
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
                PenumbraNetworking.HorrorSoundS2CPacket.TYPE,
                (packet, context) -> context.client().execute(() -> playHorrorSound(packet)));

        ClientPlayNetworking.registerGlobalReceiver(
                PenumbraNetworking.JumpscareS2CPacket.TYPE,
                (packet, context) -> context.client().execute(
                        () -> JumpscareManager.trigger(packet.scareType(), packet.intensity())));

        // 0.5.0 : synchro de l'état de tension pour la couche ambiante client.
        ClientPlayNetworking.registerGlobalReceiver(
                PenumbraNetworking.TensionSyncS2CPacket.TYPE,
                (packet, context) -> context.client().execute(
                        () -> {
                            if (Float.isFinite(packet.tension())) {
                                ClientTensionState.accept(packet.stateOrdinal(), packet.tension());
                            }
                        }));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            JumpscareManager.reset();
            ClientTensionState.reset();
        });
    }

    /**
     * Joue un son d'horreur en local, en {@link SoundSource#HOSTILE}.
     * Les stingers voient leur volume plafonné par {@code jumpscareVolume} (sécurité, jamais > 1.0).
     */
    private static void playHorrorSound(PenumbraNetworking.HorrorSoundS2CPacket packet) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return;
        }
        if (!Double.isFinite(packet.x()) || !Double.isFinite(packet.y()) || !Double.isFinite(packet.z())
                || !Float.isFinite(packet.volume()) || packet.volume() <= 0.0f
                || !Float.isFinite(packet.pitch()) || packet.pitch() <= 0.0f) {
            PenumbraMod.LOGGER.warn("[Penumbra] Paquet audio invalide ignoré");
            return;
        }
        // Vérifié en 26.1.2 : Registry#getValue(Identifier) retourne la valeur ou null.
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getValue(packet.soundId());
        if (sound == null) {
            PenumbraMod.LOGGER.warn("[Penumbra] Son inconnu reçu du serveur : {}", packet.soundId());
            return;
        }
        float volume = Math.min(PenumbraNetworking.MAX_NETWORK_VOLUME, packet.volume());
        if (packet.soundId().equals(PenumbraMod.id("host_stinger"))
                || packet.soundId().equals(PenumbraMod.id("chase_stinger"))
                || packet.soundId().equals(PenumbraMod.id("screamer_shriek"))
                || packet.soundId().equals(PenumbraMod.id("fake_creeper"))) {
            volume = Math.min(1.0f, volume * PenumbraConfig.get().jumpscareVolume);
        }
        SoundSource source = packet.soundId().equals(PenumbraMod.id("binoculars_on"))
                || packet.soundId().equals(PenumbraMod.id("binoculars_off"))
                ? SoundSource.PLAYERS : SoundSource.HOSTILE;
        float pitch = Math.max(PenumbraNetworking.MIN_NETWORK_PITCH,
                Math.min(PenumbraNetworking.MAX_NETWORK_PITCH, packet.pitch()));
        client.level.playLocalSound(packet.x(), packet.y(), packet.z(), sound, source,
                volume, pitch, false);
    }
}

package net.fabricmc.fabric.api.client.networking.v1;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
public final class ClientPlayNetworking {
    public interface Context {
        Minecraft client();
        LocalPlayer player();
        net.fabricmc.fabric.api.networking.v1.PacketSender responseSender();
    }
    @FunctionalInterface public interface PlayPayloadHandler<T extends CustomPacketPayload> {
        void receive(T payload, Context context);
    }
    public static <T extends CustomPacketPayload> void registerGlobalReceiver(
            CustomPacketPayload.Type<T> type, PlayPayloadHandler<T> handler) { }
    public static boolean canSend(CustomPacketPayload.Type<?> type) { return false; }
    public static void send(CustomPacketPayload payload) { }
}

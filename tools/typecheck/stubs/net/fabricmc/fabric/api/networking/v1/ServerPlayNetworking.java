package net.fabricmc.fabric.api.networking.v1;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
public final class ServerPlayNetworking {
    public interface Context {
        ServerPlayer player();
        MinecraftServer server();
        PacketSender responseSender();
    }
    @FunctionalInterface public interface PlayPayloadHandler<T extends CustomPacketPayload> {
        void receive(T payload, Context context);
    }
    public static <T extends CustomPacketPayload> void registerGlobalReceiver(
            CustomPacketPayload.Type<T> type, PlayPayloadHandler<T> handler) { }
    public static boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) { return false; }
    public static void send(ServerPlayer player, CustomPacketPayload payload) { }
}

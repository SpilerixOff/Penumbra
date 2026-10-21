package net.fabricmc.fabric.api.networking.v1;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
public final class ServerPlayConnectionEvents {
    @FunctionalInterface public interface Init { void onPlayInit(ServerGamePacketListenerImpl handler, MinecraftServer server); }
    @FunctionalInterface public interface Join { void onPlayReady(ServerGamePacketListenerImpl handler, PacketSender sender, MinecraftServer server); }
    @FunctionalInterface public interface Disconnect { void onPlayDisconnect(ServerGamePacketListenerImpl handler, MinecraftServer server); }
    public static final Event<Init> INIT = null;
    public static final Event<Join> JOIN = null;
    public static final Event<Disconnect> DISCONNECT = null;
}

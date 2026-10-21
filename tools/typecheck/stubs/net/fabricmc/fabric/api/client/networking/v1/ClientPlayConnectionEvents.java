package net.fabricmc.fabric.api.client.networking.v1;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
public final class ClientPlayConnectionEvents {
    @FunctionalInterface public interface Init { void onPlayInit(ClientPacketListener handler, Minecraft client); }
    @FunctionalInterface public interface Join { void onPlayReady(ClientPacketListener handler, net.fabricmc.fabric.api.networking.v1.PacketSender sender, Minecraft client); }
    @FunctionalInterface public interface Disconnect { void onPlayDisconnect(ClientPacketListener handler, Minecraft client); }
    public static final Event<Init> INIT = null;
    public static final Event<Join> JOIN = null;
    public static final Event<Disconnect> DISCONNECT = null;
}

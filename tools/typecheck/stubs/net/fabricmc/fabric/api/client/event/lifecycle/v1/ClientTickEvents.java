package net.fabricmc.fabric.api.client.event.lifecycle.v1;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
public final class ClientTickEvents {
    @FunctionalInterface public interface StartTick { void onStartTick(Minecraft client); }
    @FunctionalInterface public interface EndTick { void onEndTick(Minecraft client); }
    @FunctionalInterface public interface StartWorldTick { void onStartTick(ClientLevel world); }
    @FunctionalInterface public interface EndWorldTick { void onEndTick(ClientLevel world); }
    public static final Event<StartTick> START_CLIENT_TICK = null;
    public static final Event<EndTick> END_CLIENT_TICK = null;
    public static final Event<StartWorldTick> START_WORLD_TICK = null;
    public static final Event<EndWorldTick> END_WORLD_TICK = null;
}

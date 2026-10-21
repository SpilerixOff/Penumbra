package net.fabricmc.fabric.api.event.lifecycle.v1;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
public final class ServerTickEvents {
    @FunctionalInterface public interface StartTick { void onStartTick(MinecraftServer server); }
    @FunctionalInterface public interface EndTick { void onEndTick(MinecraftServer server); }
    @FunctionalInterface public interface StartWorldTick { void onStartTick(ServerLevel world); }
    @FunctionalInterface public interface EndWorldTick { void onEndTick(ServerLevel world); }
    public static final Event<StartTick> START_SERVER_TICK = null;
    public static final Event<EndTick> END_SERVER_TICK = null;
    public static final Event<StartWorldTick> START_WORLD_TICK = null;
    public static final Event<EndWorldTick> END_WORLD_TICK = null;
}

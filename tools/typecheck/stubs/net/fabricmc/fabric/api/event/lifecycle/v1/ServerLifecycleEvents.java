package net.fabricmc.fabric.api.event.lifecycle.v1;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.MinecraftServer;
public final class ServerLifecycleEvents {
    @FunctionalInterface public interface ServerStarting { void onServerStarting(MinecraftServer server); }
    @FunctionalInterface public interface ServerStarted { void onServerStarted(MinecraftServer server); }
    @FunctionalInterface public interface ServerStopping { void onServerStopping(MinecraftServer server); }
    @FunctionalInterface public interface ServerStopped { void onServerStopped(MinecraftServer server); }
    public static final Event<ServerStarting> SERVER_STARTING = null;
    public static final Event<ServerStarted> SERVER_STARTED = null;
    public static final Event<ServerStopping> SERVER_STOPPING = null;
    public static final Event<ServerStopped> SERVER_STOPPED = null;
}

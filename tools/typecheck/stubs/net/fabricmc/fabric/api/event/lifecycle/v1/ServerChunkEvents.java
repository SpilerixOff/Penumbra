package net.fabricmc.fabric.api.event.lifecycle.v1;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
public final class ServerChunkEvents {
    @FunctionalInterface public interface Load { void onChunkLoad(ServerLevel world, LevelChunk chunk); }
    @FunctionalInterface public interface Generate { void onChunkGenerate(ServerLevel world, LevelChunk chunk); }
    @FunctionalInterface public interface Unload { void onChunkUnload(ServerLevel world, LevelChunk chunk); }
    public static final Event<Load> CHUNK_LOAD = null;
    public static final Event<Generate> CHUNK_GENERATE = null;
    public static final Event<Unload> CHUNK_UNLOAD = null;
}

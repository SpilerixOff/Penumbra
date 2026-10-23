package fr.penumbra.journal;

import fr.penumbra.PenumbraMod;
import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.story.ConfrontationManager;
import fr.penumbra.structure.PenumbraStructures;
import fr.penumbra.structure.StructureTracker;
import fr.penumbra.structure.StructureType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;

/**
 * A missing chapter can appear in newly explored land without the rare well lottery.
 * Generation callbacks only enqueue coordinates; no world reads or writes occur there.
 * At most one loaded candidate is checked per second. Existing chunks are never candidates.
 */
public final class InvestigationRouteManager {
    private static final int INTERVAL = 20;
    private static final int MAX_PENDING = 64;
    private static final long EXPIRY_TICKS = 600;
    static final int FOOTPRINT_RADIUS = 5;
    static final int TOP = 9;
    static final double MIN_DISTANCE = 48.0;
    static final double MAX_DISTANCE = 320.0;
    private static final double STRUCTURE_SPACING_SQR = 64.0 * 64.0;

    private static final Set<Block> SOIL = Set.of(Blocks.GRASS_BLOCK, Blocks.DIRT,
            Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT, Blocks.PODZOL, Blocks.MYCELIUM,
            Blocks.STONE, Blocks.GRANITE, Blocks.DIORITE, Blocks.ANDESITE, Blocks.DEEPSLATE,
            Blocks.TUFF, Blocks.CALCITE, Blocks.GRAVEL, Blocks.SAND, Blocks.RED_SAND,
            Blocks.SANDSTONE, Blocks.RED_SANDSTONE, Blocks.CLAY, Blocks.SNOW_BLOCK,
            Blocks.COAL_ORE, Blocks.IRON_ORE, Blocks.COPPER_ORE, Blocks.GOLD_ORE,
            Blocks.LAPIS_ORE, Blocks.REDSTONE_ORE, Blocks.DIAMOND_ORE, Blocks.EMERALD_ORE,
            Blocks.DEEPSLATE_COAL_ORE, Blocks.DEEPSLATE_IRON_ORE, Blocks.DEEPSLATE_COPPER_ORE,
            Blocks.DEEPSLATE_GOLD_ORE, Blocks.DEEPSLATE_LAPIS_ORE, Blocks.DEEPSLATE_REDSTONE_ORE,
            Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.DEEPSLATE_EMERALD_ORE);
    private static final Set<Block> GROUND_COVER = Set.of(Blocks.SHORT_GRASS, Blocks.TALL_GRASS,
            Blocks.FERN, Blocks.LARGE_FERN, Blocks.DEAD_BUSH, Blocks.SNOW,
            Blocks.DANDELION, Blocks.POPPY, Blocks.BLUE_ORCHID, Blocks.ALLIUM, Blocks.AZURE_BLUET,
            Blocks.RED_TULIP, Blocks.ORANGE_TULIP, Blocks.WHITE_TULIP, Blocks.PINK_TULIP,
            Blocks.OXEYE_DAISY, Blocks.CORNFLOWER, Blocks.LILY_OF_THE_VALLEY);

    private static final ConcurrentLinkedQueue<Candidate> PENDING = new ConcurrentLinkedQueue<>();
    private static final Set<Long> QUEUED = ConcurrentHashMap.newKeySet();
    private static final Object QUEUE_LOCK = new Object();
    private static volatile List<Request> requests = List.of();

    record Request(UUID playerId, StructureType type, double x, double z) { }
    private record Candidate(int chunkX, int chunkZ, Request request, long ready, long expires) { }

    private InvestigationRouteManager() { }

    public static void register() {
        ServerChunkEvents.CHUNK_GENERATE.register(InvestigationRouteManager::onGenerated);
        ServerTickEvents.END_SERVER_TICK.register(InvestigationRouteManager::onTick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> clear());
    }

    private static void clear() {
        requests = List.of();
        clearQueue();
    }

    private static void clearQueue() {
        synchronized (QUEUE_LOCK) {
            PENDING.clear();
            QUEUED.clear();
        }
    }

    private static void enqueue(Candidate candidate) {
        synchronized (QUEUE_LOCK) {
            if (PENDING.size() < MAX_PENDING && QUEUED.add(chunkKey(candidate.chunkX(), candidate.chunkZ()))) {
                PENDING.offer(candidate);
            }
        }
    }

    private static Candidate poll() {
        synchronized (QUEUE_LOCK) {
            Candidate candidate = PENDING.poll();
            if (candidate != null) QUEUED.remove(chunkKey(candidate.chunkX(), candidate.chunkZ()));
            return candidate;
        }
    }

    private static void onGenerated(ServerLevel level, LevelChunk chunk) {
        if (!level.dimension().equals(Level.OVERWORLD) || PENDING.size() >= MAX_PENDING) return;
        int chunkX = chunk.getPos().x();
        int chunkZ = chunk.getPos().z();
        Optional<Request> request = chooseRequest(requests, chunkX * 16 + 8, chunkZ * 16 + 8);
        if (request.isEmpty()) return;
        long tick = level.getGameTime();
        enqueue(new Candidate(chunkX, chunkZ, request.get(), tick + 2, tick + EXPIRY_TICKS));
    }

    /** Snapshot selection is safe on the chunk-generation thread. */
    static Optional<Request> chooseRequest(List<Request> candidates, int x, int z) {
        return candidates.stream().filter(request -> inRange(request.x(), request.z(), x, z)).findFirst();
    }

    static boolean inRange(double playerX, double playerZ, int x, int z) {
        double dx = x + 0.5 - playerX;
        double dz = z + 0.5 - playerZ;
        double distance = dx * dx + dz * dz;
        return distance >= MIN_DISTANCE * MIN_DISTANCE && distance <= MAX_DISTANCE * MAX_DISTANCE;
    }

    private static long chunkKey(int x, int z) { return ((long) x << 32) ^ (z & 0xFFFFFFFFL); }

    static StructureType destinationFor(ServerPlayer player) {
        PenumbraConfig config = PenumbraConfig.get();
        boolean enabled = config.storyEnabled && config.journalEnabled;
        if (!enabled || ConfrontationManager.isWon(player)) return null;
        boolean required = InvestigationProgress.required(player);
        return selectDestination(true, false, required,
                required ? InvestigationProgress.nextDestination(player) : null);
    }

    /** Grandfathered investigators still need a route to their unfinished finale. */
    static StructureType selectDestination(boolean enabled, boolean won, boolean required,
                                           StructureType nextBook) {
        return !enabled || won ? null : required ? nextBook : StructureType.ASH_SANCTUM;
    }

    private static void onTick(MinecraftServer server) {
        if (server.getTickCount() % INTERVAL != 0) return;
        PenumbraConfig config = PenumbraConfig.get();
        if (!config.structuresEnabled || !config.storyEnabled || !config.journalEnabled) {
            clear();
            return;
        }
        StructureTracker tracker = StructureTracker.get(server);
        List<Request> fresh = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isAlive() || player.isSpectator() || !player.level().dimension().equals(Level.OVERWORLD)) continue;
            StructureType type = destinationFor(player);
            if (type != null && tracker.nearest(player, type).isEmpty()) {
                fresh.add(new Request(player.getUUID(), type, player.getX(), player.getZ()));
            }
        }
        requests = List.copyOf(fresh);
        if (fresh.isEmpty()) {
            clearQueue();
            return;
        }
        ServerLevel level = server.overworld();
        long now = level.getGameTime();
        // Drop stale objectives cheaply; they must not hold up the next chapter for sixty seconds.
        for (int attempts = 0; attempts < MAX_PENDING; attempts++) {
            Candidate candidate = poll();
            if (candidate == null) return;
            if (now > candidate.expires()) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(candidate.request().playerId());
            if (player == null || !player.isAlive() || player.isSpectator()
                    || player.level() != level || destinationFor(player) != candidate.request().type()
                    || tracker.nearest(player, candidate.request().type()).isPresent()) continue;
            if (now < candidate.ready()) {
                enqueue(candidate);
                return;
            }
            LevelChunk chunk = level.getChunkSource().getChunkNow(candidate.chunkX(), candidate.chunkZ());
            // Natural structures have priority, including a delayed build still waiting in its queue.
            if (chunk == null || PenumbraStructures.isGenerationPending(candidate.chunkX(), candidate.chunkZ())) {
                enqueue(candidate);
                return;
            }
            tryPlace(level, chunk, player, candidate.request().type(), tracker);
            return;
        }
    }

    private static void tryPlace(ServerLevel level, LevelChunk chunk, ServerPlayer player,
                                 StructureType type, StructureTracker tracker) {
        int x = chunk.getPos().getMiddleBlockX();
        int z = chunk.getPos().getMiddleBlockZ();
        if (!inRange(player.getX(), player.getZ(), x, z)) return;
        for (ServerPlayer other : level.players()) {
            double dx = other.getX() - (x + 0.5);
            double dz = other.getZ() - (z + 0.5);
            if (dx * dx + dz * dz < MIN_DISTANCE * MIN_DISTANCE) return;
        }
        for (StructureTracker.Entry entry : tracker.all()) {
            if (!entry.dimension().equals(level.dimension())) continue;
            double dx = entry.pos().getX() - x;
            double dz = entry.pos().getZ() - z;
            if (dx * dx + dz * dz < STRUCTURE_SPACING_SQR) return;
        }
        int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 1;
        if (y <= level.getSeaLevel() + 1 || y > 180 || y + TOP >= level.getMaxY()
                || y + bottom(type) < level.getMinY()) return;
        BlockPos origin = new BlockPos(x, y, z);
        BlockPos low = origin.offset(-FOOTPRINT_RADIUS, bottom(type), -FOOTPRINT_RADIUS);
        BlockPos high = origin.offset(FOOTPRINT_RADIUS, TOP, FOOTPRINT_RADIUS);
        if (!level.getWorldBorder().isWithinBounds(low) || !level.getWorldBorder().isWithinBounds(high)
                || !level.getEntitiesOfClass(Entity.class, new AABB(low.getX(), low.getY(), low.getZ(),
                high.getX() + 1.0, high.getY() + 1.0, high.getZ() + 1.0)).isEmpty()) return;
        for (int dx = -FOOTPRINT_RADIUS; dx <= FOOTPRINT_RADIUS; dx++) {
            for (int dz = -FOOTPRINT_RADIUS; dz <= FOOTPRINT_RADIUS; dz++) {
                int surface = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + dx, z + dz) + 1;
                if (Math.abs(surface - y) > 1 || !chunk.getBlockState(new BlockPos(x + dx, surface - 1, z + dz)).isSolid()) return;
            }
        }
        Map<BlockPos, BlockState> before = snapshotIfSafe(chunk::getBlockState, origin, type);
        if (before.isEmpty()) return;
        // Ground-level post and twenty-block well keep every block inside the checked volume.
        int variant = type == StructureType.OBSERVATION_POST ? 1 : 0;
        BlockPos anchor;
        try {
            long seed = level.getSeed() ^ origin.asLong() ^ type.ordinal() * 0x9E3779B97F4A7C15L;
            anchor = PenumbraStructures.build(type, level, origin, variant, seed);
            BlockPos chest = anchor == null ? null : switch (type) {
                case ASH_SANCTUM -> anchor.offset(2, 0, 0);
                case PENUMBRA_WELL -> anchor.offset(0, -20, -3);
                default -> anchor;
            };
            if (chest == null || !(level.getBlockEntity(chest) instanceof ChestBlockEntity)) {
                restore(level, before);
                return;
            }
        } catch (RuntimeException exception) {
            restore(level, before);
            PenumbraMod.LOGGER.warn("[Penumbra] Site d'enquête abandonné en {}", origin, exception);
            return;
        }
        // Once recorded, notification failures must never roll back a registered site into empty terrain.
        tracker.record(type, anchor, level, variant);
        player.sendSystemMessage(Component.translatable("journal.penumbra.investigation.route_found",
                Component.translatable("structure.penumbra." + type.id())));
        PenumbraMod.LOGGER.info("[Penumbra] Site d'enquête {} préparé en {}", type.id(), anchor);
    }

    static int bottom(StructureType type) { return type == StructureType.PENUMBRA_WELL ? -21 : -4; }

    /** Full preflight happens before the first mutation; even one protected block rejects the site. */
    static Map<BlockPos, BlockState> snapshotIfSafe(Function<BlockPos, BlockState> read,
                                                   BlockPos origin, StructureType type) {
        Map<BlockPos, BlockState> snapshot = new LinkedHashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-FOOTPRINT_RADIUS, bottom(type), -FOOTPRINT_RADIUS),
                origin.offset(FOOTPRINT_RADIUS, TOP, FOOTPRINT_RADIUS))) {
            BlockState state = read.apply(pos);
            if (!canReplace(state, pos.getY() >= origin.getY())) return Map.of();
            snapshot.put(pos.immutable(), state);
        }
        return snapshot;
    }

    static boolean canReplace(BlockState state, boolean aboveGround) {
        if (state.hasBlockEntity() || !state.getFluidState().isEmpty()) return false;
        return state.isAir() || GROUND_COVER.contains(state.getBlock())
                || !aboveGround && SOIL.contains(state.getBlock());
    }

    private static void restore(ServerLevel level, Map<BlockPos, BlockState> before) {
        before.forEach((pos, state) -> {
            if (!level.getBlockState(pos).equals(state)) level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        });
    }
}

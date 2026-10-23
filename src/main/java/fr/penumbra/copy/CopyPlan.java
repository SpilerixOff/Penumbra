package fr.penumbra.copy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.*;

/** Pure, bounded room interpretation. Block states only: never reads or copies a block entity. */
final class CopyPlan {
    static final int MAX_WIDTH = 9;
    static final int MAX_AREA = 63;
    static final int MAX_BLOCKS = MAX_AREA * 6 + 6;
    private static final List<Direction> SIDES = List.of(Direction.NORTH, Direction.EAST,
            Direction.SOUTH, Direction.WEST);

    record Source(int minX, int minZ, int width, int depth, int floorY, int height,
                  BlockState wall, BlockState floor, BlockState door, BlockPos doorPos,
                  List<Map.Entry<BlockPos, BlockState>> furniture, Set<Direction> windows) {}

    record Layout(int width, int depth, int height, Direction entranceSide, BlockPos entrance,
                  BlockPos chest, BlockPos sign, BlockPos mannequin, BlockPos sealedWindow,
                  Map<BlockPos, BlockState> blocks) {
        Layout {
            blocks = Collections.unmodifiableMap(new LinkedHashMap<>(blocks));
        }
    }

    private CopyPlan() {}

    /** Structural whitelist excludes ores, valuable blocks, machines and natural cave walls. */
    static boolean architectural(BlockState state) {
        Block block = state.getBlock();
        String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
        return path.endsWith("_planks") || block == Blocks.COBBLESTONE
                || block == Blocks.MOSSY_COBBLESTONE || block == Blocks.STONE_BRICKS
                || block == Blocks.CRACKED_STONE_BRICKS || block == Blocks.MOSSY_STONE_BRICKS
                || block == Blocks.BRICKS || block == Blocks.MUD_BRICKS
                || block == Blocks.POLISHED_DEEPSLATE || block == Blocks.DEEPSLATE_BRICKS
                || block == Blocks.DEEPSLATE_TILES || block == Blocks.SMOOTH_STONE
                || block == Blocks.SANDSTONE || block == Blocks.SMOOTH_SANDSTONE;
    }

    static Source measure(Map<BlockPos, BlockState> sample, BlockPos refuge) {
        var doors = sample.entrySet().stream()
                .filter(e -> e.getValue().getBlock() instanceof DoorBlock
                        && e.getValue().getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER)
                .sorted(Comparator.comparingDouble(e -> e.getKey().distSqr(refuge))).toList();
        var nearestDoor = doors.isEmpty() ? null : doors.getFirst();
        int floorY = nearestDoor != null && Math.abs(nearestDoor.getKey().getY() - refuge.getY()) <= 4
                ? nearestDoor.getKey().getY() : refuge.getY();
        int minX = refuge.getX(), maxX = minX, minZ = refuge.getZ(), maxZ = minZ;
        int highest = floorY + 2;
        Map<Block, Integer> walls = new LinkedHashMap<>();
        List<Map.Entry<BlockPos, BlockState>> furniture = new ArrayList<>();
        for (var entry : sample.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState state = entry.getValue();
            if (pos.getY() < floorY || pos.getY() > floorY + 4) continue;
            if (architectural(state) || state.getBlock() instanceof DoorBlock || isWindow(state)) {
                minX = Math.min(minX, pos.getX()); maxX = Math.max(maxX, pos.getX());
                minZ = Math.min(minZ, pos.getZ()); maxZ = Math.max(maxZ, pos.getZ());
                if (architectural(state)) {
                    walls.merge(state.getBlock(), 1, Integer::sum);
                    highest = Math.max(highest, pos.getY());
                }
            }
            if (pos.getY() == floorY && isFurniture(state)) {
                // A bed is one semantic object; its head is rebuilt with the foot.
                if (!(state.getBlock() instanceof BedBlock) || state.getValue(BedBlock.PART) == BedPart.FOOT) {
                    furniture.add(Map.entry(pos, state));
                }
            }
        }
        // A bare cave or small shelter still yields a usable room, never arbitrary terrain geometry.
        if (maxX - minX < 4) { minX = refuge.getX() - 2; maxX = refuge.getX() + 2; }
        if (maxZ - minZ < 4) { minZ = refuge.getZ() - 2; maxZ = refuge.getZ() + 2; }
        BlockState wall = dominant(walls, Blocks.OAK_PLANKS).defaultBlockState();
        Map<Block, Integer> floors = new LinkedHashMap<>();
        for (var entry : sample.entrySet()) {
            BlockPos pos = entry.getKey();
            if (pos.getY() == floorY - 1 && pos.getX() >= minX && pos.getX() <= maxX
                    && pos.getZ() >= minZ && pos.getZ() <= maxZ && architectural(entry.getValue())) {
                floors.merge(entry.getValue().getBlock(), 1, Integer::sum);
            }
        }
        Set<Direction> windows = EnumSet.noneOf(Direction.class);
        for (var entry : sample.entrySet()) {
            if (isWindow(entry.getValue()) && entry.getKey().getY() >= floorY
                    && entry.getKey().getY() <= floorY + 3) {
                windows.add(nearestSide(entry.getKey(), minX, minZ, maxX - minX + 1, maxZ - minZ + 1));
            }
        }
        furniture.sort(Comparator.comparingDouble(e -> e.getKey().distSqr(refuge)));
        return new Source(minX, minZ, maxX - minX + 1, maxZ - minZ + 1, floorY,
                Math.max(3, Math.min(4, highest - floorY)), wall,
                dominant(floors, wall.getBlock()).defaultBlockState(),
                nearestDoor == null ? Blocks.OAK_DOOR.defaultBlockState() : nearestDoor.getValue(),
                nearestDoor == null ? new BlockPos(refuge.getX(), floorY, minZ) : nearestDoor.getKey(),
                List.copyOf(furniture), windows);
    }

    static Layout build(Source source) {
        double scale = Math.min(1.0, (double) MAX_WIDTH / Math.max(source.width(), source.depth()));
        int width = Math.max(5, Math.min(MAX_WIDTH, (int) Math.round(source.width() * scale)));
        int depth = Math.max(5, Math.min(MAX_WIDTH, (int) Math.round(source.depth() * scale)));
        while (width * depth > MAX_AREA) {
            if (width >= depth) width--; else depth--;
        }
        int height = Math.max(3, Math.min(4, source.height()));
        Direction side = nearestSide(source.doorPos(), source.minX(), source.minZ(), source.width(), source.depth());
        int index = side.getAxis() == Direction.Axis.Z
                ? map(source.doorPos().getX() - source.minX(), source.width(), width)
                : map(source.doorPos().getZ() - source.minZ(), source.depth(), depth);
        BlockPos door = edge(side, index, width, depth, 0);
        Set<BlockPos> corridor = corridor(door, side, width, depth);
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        // Floor, empty interior and roof are explicit. The builder preflights the entire plan.
        for (int y = -1; y <= height; y++) {
            for (int x = 0; x < width; x++) for (int z = 0; z < depth; z++) {
                boolean boundary = x == 0 || z == 0 || x == width - 1 || z == depth - 1;
                BlockState state = y == -1 ? source.floor() : y == height || boundary ? source.wall() : Blocks.AIR.defaultBlockState();
                blocks.put(new BlockPos(x, y, z), state);
            }
        }
        BlockState doorState = source.door().getBlock().defaultBlockState()
                .setValue(DoorBlock.FACING, source.door().getValue(DoorBlock.FACING))
                .setValue(DoorBlock.HINGE, source.door().getValue(DoorBlock.HINGE))
                .setValue(DoorBlock.POWERED, false)
                // Iron doors must not trap the player in a room with no redstone system.
                .setValue(DoorBlock.OPEN, source.door().is(Blocks.IRON_DOOR));
        blocks.put(door, doorState.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        blocks.put(door.above(), doorState.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        for (int step = 1; step <= 2; step++) {
            BlockPos outside = door.relative(side, step);
            blocks.put(outside.below(), source.floor());
            blocks.put(outside, Blocks.AIR.defaultBlockState());
            blocks.put(outside.above(), Blocks.AIR.defaultBlockState());
        }
        Set<Direction> windowSides = EnumSet.noneOf(Direction.class);
        windowSides.addAll(source.windows());
        if (windowSides.isEmpty()) windowSides.add(side.getOpposite());
        // One deliberately sealed opening, one genuine window: an anomaly, not a random missing wall.
        windowSides.add(side.getClockWise());
        if (windowSides.size() < 2) windowSides.add(side.getOpposite());
        BlockPos sealedWindow = null;
        for (Direction windowSide : SIDES) {
            if (!windowSides.contains(windowSide)) continue;
            int span = windowSide.getAxis() == Direction.Axis.Z ? width : depth;
            int at = span / 2;
            BlockPos window = edge(windowSide, at, width, depth, 1);
            if (window.getX() == door.getX() && window.getZ() == door.getZ()) {
                at = at == span - 2 ? at - 1 : at + 1;
                window = edge(windowSide, at, width, depth, 1);
            }
            boolean sealed = sealedWindow == null;
            blocks.put(window, sealed ? wrongMaterial(source.wall()) : Blocks.GLASS.defaultBlockState());
            if (sealed) sealedWindow = window;
        }
        // Always reserve a chest and a clue sign before optional furnishing; no source inventory is read.
        BlockPos chest = null;
        for (var memory : source.furniture()) {
            if (memory.getValue().getBlock() instanceof ChestBlock || memory.getValue().is(Blocks.BARREL)) {
                chest = furnitureSpot(mapFurniture(memory.getKey(), source, width, depth), blocks, corridor, width, depth);
                break;
            }
        }
        if (chest == null) chest = furnitureSpot(new BlockPos(1, 0, 1), blocks, corridor, width, depth);
        if (chest != null) blocks.put(chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, side));
        BlockPos sign = furnitureSpot(new BlockPos(width - 2, 0, depth - 2), blocks, corridor, width, depth);
        if (sign != null) blocks.put(sign, Blocks.OAK_SIGN.defaultBlockState()
                .setValue(StandingSignBlock.ROTATION, ((int) (side.toYRot() * 16 / 360) + 1) & 15));
        int furnished = 0;
        for (var memory : source.furniture()) {
            BlockState original = memory.getValue();
            Block block = original.getBlock();
            if (block instanceof ChestBlock || block == Blocks.BARREL || furnished >= 3) continue;
            BlockPos at = furnitureSpot(mapFurniture(memory.getKey(), source, width, depth), blocks, corridor, width, depth);
            if (at == null) continue;
            if (block instanceof BedBlock) {
                Direction facing = original.getValue(BedBlock.FACING);
                BlockPos head = at.relative(facing);
                if (!free(head, blocks, corridor, width, depth)) continue;
                BlockState bed = block.defaultBlockState().setValue(BedBlock.FACING, facing).setValue(BedBlock.OCCUPIED, false);
                blocks.put(at, bed.setValue(BedBlock.PART, BedPart.FOOT));
                blocks.put(head, bed.setValue(BedBlock.PART, BedPart.HEAD));
            } else {
                BlockState state = block.defaultBlockState();
                if (state.hasProperty(AbstractFurnaceBlock.FACING)) state = state.setValue(AbstractFurnaceBlock.FACING, original.getValue(AbstractFurnaceBlock.FACING));
                blocks.put(at, state);
            }
            furnished++;
        }
        BlockPos mannequin = furnitureSpot(new BlockPos(width - 2, 0, 1), blocks, corridor, width, depth);
        // Ceiling lamp frees the walking surface and has a guaranteed full-block support.
        BlockPos lamp = new BlockPos(width / 2, height - 1, depth / 2);
        blocks.put(lamp, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        return new Layout(width, depth, height, side, door, chest, sign, mannequin, sealedWindow, blocks);
    }

    private static boolean isWindow(BlockState state) {
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return path.equals("glass") || path.equals("glass_pane") || path.endsWith("_stained_glass") || path.endsWith("_stained_glass_pane");
    }

    private static boolean isFurniture(BlockState state) {
        Block block = state.getBlock();
        return block instanceof ChestBlock || block instanceof BedBlock || block instanceof AbstractFurnaceBlock
                || block == Blocks.BARREL || block == Blocks.CRAFTING_TABLE || block == Blocks.CARTOGRAPHY_TABLE;
    }

    private static Block dominant(Map<Block, Integer> counts, Block fallback) {
        return counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(fallback);
    }

    private static BlockState wrongMaterial(BlockState state) {
        return (BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().endsWith("_planks")
                ? state.is(Blocks.DARK_OAK_PLANKS) ? Blocks.BIRCH_PLANKS : Blocks.DARK_OAK_PLANKS
                : state.is(Blocks.CRACKED_STONE_BRICKS) ? Blocks.MOSSY_STONE_BRICKS : Blocks.CRACKED_STONE_BRICKS).defaultBlockState();
    }

    static Direction nearestSide(BlockPos pos, int minX, int minZ, int width, int depth) {
        int[] distances = {Math.abs(pos.getZ() - minZ), Math.abs(pos.getX() - (minX + width - 1)),
                Math.abs(pos.getZ() - (minZ + depth - 1)), Math.abs(pos.getX() - minX)};
        int best = 0;
        for (int i = 1; i < 4; i++) if (distances[i] < distances[best]) best = i;
        return SIDES.get(best);
    }

    private static BlockPos edge(Direction side, int index, int width, int depth, int y) {
        return switch (side) {
            case NORTH -> new BlockPos(index, y, 0);
            case SOUTH -> new BlockPos(index, y, depth - 1);
            case WEST -> new BlockPos(0, y, index);
            default -> new BlockPos(width - 1, y, index);
        };
    }

    private static int map(int coordinate, int sourceSpan, int targetSpan) {
        return Math.max(1, Math.min(targetSpan - 2, (int) Math.round((double) coordinate * (targetSpan - 1) / Math.max(1, sourceSpan - 1))));
    }

    private static BlockPos mapFurniture(BlockPos pos, Source source, int width, int depth) {
        return new BlockPos(map(pos.getX() - source.minX(), source.width(), width), 0,
                map(pos.getZ() - source.minZ(), source.depth(), depth));
    }

    static Set<BlockPos> corridor(BlockPos door, Direction side, int width, int depth) {
        Set<BlockPos> path = new HashSet<>();
        BlockPos at = door.relative(side.getOpposite());
        BlockPos center = new BlockPos(width / 2, 0, depth / 2);
        path.add(at);
        // Walk perpendicular to the entry first, then along the center line.
        while (at.getX() != center.getX() || at.getZ() != center.getZ()) {
            if (side.getAxis() == Direction.Axis.X && at.getX() != center.getX()
                    || side.getAxis() == Direction.Axis.Z && at.getZ() == center.getZ() && at.getX() != center.getX()) {
                at = at.offset(Integer.signum(center.getX() - at.getX()), 0, 0);
            } else {
                at = at.offset(0, 0, Integer.signum(center.getZ() - at.getZ()));
            }
            path.add(at);
        }
        return path;
    }

    private static boolean free(BlockPos pos, Map<BlockPos, BlockState> blocks, Set<BlockPos> corridor, int width, int depth) {
        return pos.getX() > 0 && pos.getX() < width - 1 && pos.getZ() > 0 && pos.getZ() < depth - 1
                && !corridor.contains(pos) && blocks.getOrDefault(pos, Blocks.STONE.defaultBlockState()).isAir();
    }

    private static BlockPos furnitureSpot(BlockPos preferred, Map<BlockPos, BlockState> blocks, Set<BlockPos> corridor, int width, int depth) {
        List<BlockPos> spots = new ArrayList<>();
        for (int x = 1; x < width - 1; x++) for (int z = 1; z < depth - 1; z++) {
            BlockPos pos = new BlockPos(x, 0, z);
            if (free(pos, blocks, corridor, width, depth)) spots.add(pos);
        }
        return spots.stream().min(Comparator.comparingDouble(p -> p.distSqr(preferred))).orElse(null);
    }
}

package fr.penumbra.copy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CopyPlanTest {
    private static final List<Direction> SIDES = List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);

    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        // 26.1 binds item components after the basic registry bootstrap, during data loading.
        // Use the vanilla initializers so these real stacks have their proper defaults.
        if (!Items.PAPER.builtInRegistryHolder().areComponentsBound()) {
            BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(VanillaRegistries.createLookup())
                    .forEach(pending -> pending.apply());
        }
    }

    @Test
    void measuringARealRoomKeepsItsProportionsMaterialsAndEntranceWithoutMutatingTheSnapshot() {
        Map<BlockPos, BlockState> room = room(9, 7, 3);
        BlockPos door = new BlockPos(8, 64, 2);
        room.put(door, Blocks.BIRCH_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.WEST));
        room.put(door.above(), room.get(door).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        room.put(new BlockPos(0, 65, 3), Blocks.BLUE_STAINED_GLASS.defaultBlockState());
        room.put(new BlockPos(6, 64, 5), Blocks.CHEST.defaultBlockState());
        Map<BlockPos, BlockState> original = Map.copyOf(room);
        CopyPlan.Source source = CopyPlan.measure(room, new BlockPos(4, 64, 3));
        CopyPlan.Layout copy = CopyPlan.build(source);

        assertEquals(9, copy.width());
        assertEquals(7, copy.depth());
        assertEquals(3, copy.height());
        assertEquals(Direction.EAST, copy.entranceSide());
        assertEquals(new BlockPos(8, 0, 2), copy.entrance());
        assertTrue(copy.blocks().get(new BlockPos(4, -1, 3)).is(Blocks.OAK_PLANKS));
        assertTrue(copy.blocks().get(new BlockPos(4, 3, 3)).is(Blocks.STONE_BRICKS));
        assertTrue(copy.blocks().get(copy.entrance()).is(Blocks.BIRCH_DOOR));
        assertEquals(Direction.WEST, copy.blocks().get(copy.entrance()).getValue(DoorBlock.FACING));
        assertEquals(new BlockPos(6, 0, 5), copy.chest());
        assertEquals(original, room);
    }

    @Test
    void everyRoomSizeHasACompleteTrackedEnvelopeAndTwoBlockApproachWithinThePersistentBudget() {
        int[][] dimensions = {{5, 5}, {7, 9}, {25, 25}, {25, 5}, {5, 25}, {9, 7}};
        for (int[] shape : dimensions) for (int height : List.of(3, 4)) for (Direction side : SIDES) {
            CopyPlan.Layout copy = CopyPlan.build(source(shape[0], shape[1], height, side, List.of(), Set.of()));
            assertTrue(copy.width() >= 5 && copy.width() <= 9);
            assertTrue(copy.depth() >= 5 && copy.depth() <= 9);
            assertTrue(copy.width() * copy.depth() <= 63);
            assertEquals(copy.width() * copy.depth() * (height + 2) + 6, copy.blocks().size());
            assertTrue(copy.blocks().size() <= CopyTracker.MAX_BLOCKS_PER_COPY);
            for (int x = 0; x < copy.width(); x++) for (int z = 0; z < copy.depth(); z++) {
                assertTrue(copy.blocks().get(new BlockPos(x, -1, z)).is(Blocks.OAK_PLANKS));
                assertTrue(copy.blocks().get(new BlockPos(x, height, z)).is(Blocks.STONE_BRICKS));
            }
            for (int step = 1; step <= 2; step++) {
                BlockPos approach = copy.entrance().relative(side, step);
                assertTrue(copy.blocks().get(approach).isAir());
                assertTrue(copy.blocks().get(approach.above()).isAir());
                assertFalse(copy.blocks().get(approach.below()).isAir());
            }
        }
    }

    @Test
    void furnitureAndMannequinCannotBlockTheEntranceOrTheRouteToTheCenter() {
        List<Map.Entry<BlockPos, BlockState>> furniture = new ArrayList<>();
        for (int x = 1; x < 8; x++) for (int z = 1; z < 6; z++) {
            furniture.add(Map.entry(new BlockPos(x, 64, z), Blocks.CRAFTING_TABLE.defaultBlockState()));
        }
        for (Direction side : SIDES) {
            CopyPlan.Layout copy = CopyPlan.build(source(9, 7, 3, side, furniture, Set.of()));
            Set<BlockPos> corridor = CopyPlan.corridor(copy.entrance(), side, copy.width(), copy.depth());
            for (BlockPos pos : corridor) {
                assertTrue(copy.blocks().get(pos).isAir(), pos.toString());
                assertTrue(copy.blocks().get(pos.above()).isAir(), pos.toString());
            }
            assertFalse(corridor.contains(copy.chest()));
            assertFalse(corridor.contains(copy.sign()));
            assertFalse(corridor.contains(copy.mannequin()));
            assertEquals(3, copy.blocks().values().stream().filter(s -> s.is(Blocks.CRAFTING_TABLE)).count());
        }
    }

    @Test
    void theSealedWindowNeverReplacesTheOnlyRealWindowOrTheDoor() {
        for (Direction side : SIDES) for (Direction windowSide : SIDES) {
            CopyPlan.Layout copy = CopyPlan.build(source(5, 5, 3, side, List.of(), Set.of(windowSide)));
            assertNotNull(copy.sealedWindow());
            assertTrue(copy.blocks().values().stream().anyMatch(s -> s.is(Blocks.GLASS)));
            assertFalse(copy.blocks().get(copy.sealedWindow()).is(Blocks.STONE_BRICKS));
            assertNotEquals(copy.entrance().above(), copy.sealedWindow());
            assertEquals(DoubleBlockHalf.LOWER, copy.blocks().get(copy.entrance()).getValue(DoorBlock.HALF));
            assertEquals(DoubleBlockHalf.UPPER, copy.blocks().get(copy.entrance().above()).getValue(DoorBlock.HALF));
        }
    }

    @Test
    void furnitureRebuildsSafeDefaultStatesAndBedHalvesRatherThanRuntimeStateOrArbitraryMachines() {
        List<Map.Entry<BlockPos, BlockState>> furniture = List.of(
                Map.entry(new BlockPos(1, 64, 4), Blocks.RED_BED.defaultBlockState()
                        .setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.OCCUPIED, true)),
                Map.entry(new BlockPos(7, 64, 1), Blocks.FURNACE.defaultBlockState()
                        .setValue(AbstractFurnaceBlock.FACING, Direction.WEST).setValue(AbstractFurnaceBlock.LIT, true)));
        CopyPlan.Layout copy = CopyPlan.build(source(9, 7, 3, Direction.NORTH, furniture, Set.of()));
        var bed = copy.blocks().entrySet().stream().filter(e -> e.getValue().is(Blocks.RED_BED)
                && e.getValue().getValue(BedBlock.PART) == BedPart.FOOT).findFirst().orElseThrow();
        assertFalse(bed.getValue().getValue(BedBlock.OCCUPIED));
        BlockPos head = bed.getKey().relative(bed.getValue().getValue(BedBlock.FACING));
        assertEquals(BedPart.HEAD, copy.blocks().get(head).getValue(BedBlock.PART));
        BlockState furnace = copy.blocks().values().stream().filter(s -> s.is(Blocks.FURNACE)).findFirst().orElseThrow();
        assertEquals(Direction.WEST, furnace.getValue(AbstractFurnaceBlock.FACING));
        assertFalse(furnace.getValue(AbstractFurnaceBlock.LIT));
        Map<BlockPos, BlockState> sample = room(7, 7, 3);
        sample.put(new BlockPos(1, 64, 1), Blocks.SHULKER_BOX.defaultBlockState());
        sample.put(new BlockPos(1, 64, 2), Blocks.DIAMOND_BLOCK.defaultBlockState());
        CopyPlan.Source measured = CopyPlan.measure(sample, new BlockPos(3, 64, 3));
        assertTrue(measured.furniture().isEmpty());
        assertFalse(CopyPlan.architectural(Blocks.DIAMOND_BLOCK.defaultBlockState()));
        assertFalse(CopyPlan.architectural(Blocks.STONE.defaultBlockState()));
    }

    @Test
    void anIronDoorStaysPassableWithoutAKeyOrAHiddenRedstoneCircuit() {
        CopyPlan.Source normal = source(7, 7, 3, Direction.NORTH, List.of(), Set.of());
        CopyPlan.Source iron = new CopyPlan.Source(normal.minX(), normal.minZ(), normal.width(), normal.depth(),
                normal.floorY(), normal.height(), normal.wall(), normal.floor(), Blocks.IRON_DOOR.defaultBlockState(),
                normal.doorPos(), normal.furniture(), normal.windows());
        CopyPlan.Layout copy = CopyPlan.build(iron);
        assertTrue(copy.blocks().get(copy.entrance()).getValue(DoorBlock.OPEN));
        assertFalse(copy.blocks().get(copy.entrance()).getValue(DoorBlock.POWERED));
    }

    @Test
    void restorationPreservesDepositedItemsAndLegacyUnknownContents() {
        SimpleContainer chest = new SimpleContainer(27);
        assertTrue(CopyTracker.containsOnlyImitations(chest));
        ItemStack fake = new ItemStack(Items.PAPER);
        CopyTracker.markImitation(fake);
        chest.setItem(0, fake);
        assertTrue(CopyTracker.containsOnlyImitations(chest));
        chest.setItem(1, new ItemStack(Items.DIAMOND));
        assertFalse(CopyTracker.containsOnlyImitations(chest));
        chest.setItem(1, new ItemStack(Items.PAPER));
        assertFalse(CopyTracker.containsOnlyImitations(chest));
        ItemStack notAnImitation = new ItemStack(Items.DIAMOND);
        CopyTracker.markImitation(notAnImitation);
        chest.setItem(1, notAnImitation);
        assertFalse(CopyTracker.containsOnlyImitations(chest));
    }

    @Test
    void missingArchitectureFallsBackToACompleteRoomAndSiteWhitelistRejectsPlayerObjectsAndFluids() {
        CopyPlan.Layout copy = CopyPlan.build(CopyPlan.measure(Map.of(), new BlockPos(50, 80, -70)));
        assertEquals(5, copy.width()); assertEquals(5, copy.depth());
        assertTrue(copy.blocks().values().stream().anyMatch(s -> s.is(Blocks.CHEST)));
        assertTrue(CopyBuilder.isAirOrNatural(Blocks.AIR.defaultBlockState()));
        assertTrue(CopyBuilder.isAirOrNatural(Blocks.DIRT.defaultBlockState()));
        for (Block block : List.of(Blocks.WATER, Blocks.LAVA, Blocks.CHEST, Blocks.BARREL,
                Blocks.FURNACE, Blocks.OAK_PLANKS, Blocks.RED_BED, Blocks.SHULKER_BOX)) {
            assertFalse(CopyBuilder.isAirOrNatural(block.defaultBlockState()));
        }
    }

    private static CopyPlan.Source source(int width, int depth, int height, Direction side,
                                           List<Map.Entry<BlockPos, BlockState>> furniture, Set<Direction> windows) {
        BlockPos door = switch (side) {
            case NORTH -> new BlockPos(width / 2, 64, 0);
            case SOUTH -> new BlockPos(width / 2, 64, depth - 1);
            case WEST -> new BlockPos(0, 64, depth / 2);
            default -> new BlockPos(width - 1, 64, depth / 2);
        };
        return new CopyPlan.Source(0, 0, width, depth, 64, height, Blocks.STONE_BRICKS.defaultBlockState(),
                Blocks.OAK_PLANKS.defaultBlockState(), Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, side.getOpposite()), door, furniture, windows);
    }

    private static Map<BlockPos, BlockState> room(int width, int depth, int height) {
        Map<BlockPos, BlockState> sample = new LinkedHashMap<>();
        for (int y = -1; y <= height; y++) for (int x = 0; x < width; x++) for (int z = 0; z < depth; z++) {
            boolean boundary = x == 0 || z == 0 || x == width - 1 || z == depth - 1;
            Block block = y == -1 ? Blocks.OAK_PLANKS : y == height || boundary ? Blocks.STONE_BRICKS : Blocks.AIR;
            sample.put(new BlockPos(x, 64 + y, z), block.defaultBlockState());
        }
        return sample;
    }
}

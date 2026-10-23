package fr.penumbra.journal;

import fr.penumbra.structure.StructureType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class InvestigationRouteManagerTest {
    private static final BlockPos ORIGIN = new BlockPos(8, 80, 8);

    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void unfinishedLegacyFinalesKeepTheirSanctumRouteWhileWonOrDisabledStoriesStop() {
        assertEquals(StructureType.ASH_SANCTUM,
                InvestigationRouteManager.selectDestination(true, false, false, null));
        assertEquals(StructureType.PENUMBRA_WELL,
                InvestigationRouteManager.selectDestination(true, false, true, StructureType.PENUMBRA_WELL));
        assertNull(InvestigationRouteManager.selectDestination(true, true, false, null));
        assertNull(InvestigationRouteManager.selectDestination(false, false, true, StructureType.PENUMBRA_WELL));
    }

    @Test
    void requestsOnlyClaimNewTerrainNearAnInvestigatorButNotUnderTheirFeet() {
        var request = new InvestigationRouteManager.Request(new UUID(0, 1), StructureType.PENUMBRA_WELL,
                0.5, 0.5);
        assertTrue(InvestigationRouteManager.chooseRequest(List.of(request), 0, 0).isEmpty());
        assertTrue(InvestigationRouteManager.chooseRequest(List.of(request), 47, 0).isEmpty());
        assertEquals(request, InvestigationRouteManager.chooseRequest(List.of(request), 48, 0).orElseThrow());
        assertEquals(request, InvestigationRouteManager.chooseRequest(List.of(request), 320, 0).orElseThrow());
        assertTrue(InvestigationRouteManager.chooseRequest(List.of(request), 321, 0).isEmpty());
        assertTrue(InvestigationRouteManager.chooseRequest(List.of(), 100, 0).isEmpty());
    }

    @Test
    void aWellChecksItsEntireGalleryAndShaftWithoutLeavingItsSingleChunk() {
        AtomicInteger reads = new AtomicInteger();
        var snapshot = InvestigationRouteManager.snapshotIfSafe(pos -> {
            reads.incrementAndGet();
            assertEquals(0, Math.floorDiv(pos.getX(), 16));
            assertEquals(0, Math.floorDiv(pos.getZ(), 16));
            return pos.getY() < ORIGIN.getY() ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
        }, ORIGIN, StructureType.PENUMBRA_WELL);
        assertEquals(3751, snapshot.size());
        assertEquals(snapshot.size(), reads.get());
        assertTrue(snapshot.containsKey(ORIGIN.offset(0, -20, -3)), "The gallery chest is covered");
        assertTrue(snapshot.containsKey(ORIGIN.offset(0, -21, -3)), "Its foundation is covered too");
        assertFalse(snapshot.containsKey(ORIGIN.offset(0, -22, 0)));
    }

    @Test
    void oneDeepChestRejectsTheWholeWellBeforeConstruction() {
        BlockPos protectedChest = ORIGIN.offset(0, -20, -3);
        var snapshot = InvestigationRouteManager.snapshotIfSafe(pos -> pos.equals(protectedChest)
                ? Blocks.CHEST.defaultBlockState()
                : pos.getY() < ORIGIN.getY() ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(),
                ORIGIN, StructureType.PENUMBRA_WELL);
        assertTrue(snapshot.isEmpty());
    }

    @Test
    void rejectsPlayerBlocksLiquidsAndAboveGroundStoneWhileAllowingSmallWildPlants() {
        for (var block : List.of(Blocks.OAK_PLANKS, Blocks.COBBLESTONE, Blocks.TORCH,
                Blocks.CHEST, Blocks.RED_BED, Blocks.BARREL, Blocks.WATER, Blocks.LAVA, Blocks.OAK_LOG)) {
            assertFalse(InvestigationRouteManager.canReplace(block.defaultBlockState(), false), block.toString());
            assertFalse(InvestigationRouteManager.canReplace(block.defaultBlockState(), true), block.toString());
        }
        assertFalse(InvestigationRouteManager.canReplace(Blocks.STONE.defaultBlockState(), true));
        assertTrue(InvestigationRouteManager.canReplace(Blocks.STONE.defaultBlockState(), false));
        assertTrue(InvestigationRouteManager.canReplace(Blocks.SHORT_GRASS.defaultBlockState(), true));
        assertTrue(InvestigationRouteManager.canReplace(Blocks.AIR.defaultBlockState(), true));
    }

    @Test
    void surfaceSitesHaveABoundedVolumeAndNeverInspectDeepTerrain() {
        for (StructureType type : List.of(StructureType.OBSERVATION_POST, StructureType.SHIFTED_HOUSE,
                StructureType.ASH_SANCTUM)) {
            var snapshot = InvestigationRouteManager.snapshotIfSafe(pos -> pos.getY() < ORIGIN.getY()
                    ? Blocks.DIRT.defaultBlockState() : Blocks.AIR.defaultBlockState(), ORIGIN, type);
            assertEquals(1694, snapshot.size());
            assertFalse(snapshot.containsKey(ORIGIN.offset(0, -5, 0)));
        }
    }
}

package fr.penumbra.content.item;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HammerItemTest {

    private static final BlockPos ORIGIN = new BlockPos(10, 20, 30);

    @Test
    void horizontalLookBuildsSixteenUniqueBlocksOnTheWallPlane() {
        Set<BlockPos> northPlane = plane(Direction.NORTH);

        assertEquals(16, northPlane.size());
        assertTrue(northPlane.stream().allMatch(pos -> pos.getZ() == ORIGIN.getZ()));
        assertTrue(northPlane.contains(ORIGIN));
    }

    @Test
    void verticalLookBuildsSixteenUniqueBlocksOnTheFloorPlane() {
        Set<BlockPos> floorPlane = plane(Direction.DOWN);

        assertEquals(16, floorPlane.size());
        assertTrue(floorPlane.stream().allMatch(pos -> pos.getY() == ORIGIN.getY()));
        assertTrue(floorPlane.contains(ORIGIN));
    }

    @Test
    void eastWestLookBuildsSixteenUniqueBlocksOnTheCrossWallPlane() {
        Set<BlockPos> eastPlane = plane(Direction.EAST);

        assertEquals(16, eastPlane.size());
        assertTrue(eastPlane.stream().allMatch(pos -> pos.getX() == ORIGIN.getX()));
        assertTrue(eastPlane.contains(ORIGIN));
    }

    private static Set<BlockPos> plane(Direction direction) {
        Set<BlockPos> positions = new HashSet<>();
        for (int first = -1; first <= 2; first++) {
            for (int second = -1; second <= 2; second++) {
                positions.add(HammerMiningPlane.offset(ORIGIN, direction, first, second));
            }
        }
        return positions;
    }
}

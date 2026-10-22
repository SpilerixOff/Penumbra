package fr.penumbra.director.encounter;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class EncounterPlacementTest {
    @Test
    void candidatesAreBoundedDeduplicatedAndIgnoreAnUnrelatedDistantChest() {
        Vec3 player = new Vec3(10.5, 64, 20.5);
        Vec3 look = new Vec3(0, 0, 1);
        var candidates = EncounterPlacement.candidates(player, look, null);
        assertFalse(candidates.isEmpty());
        assertTrue(candidates.size() <= 320);
        assertEquals(candidates.size(), new HashSet<>(candidates).size());
        assertEquals(candidates, EncounterPlacement.candidates(player, look, new BlockPos(1000, 90, 1000)));
        for (BlockPos point : candidates) {
            assertTrue(Math.abs(point.getY() - player.y) <= 2);
            assertTrue(Vec3.atBottomCenterOf(point).distanceToSqr(player) < 13 * 13);
        }
    }

    @Test
    void refugeNeedsAnActualFrameOrCornerRatherThanAnOpenFieldOrSolidCell() {
        assertTrue(EncounterPlacement.framedOrCorner(false, false, true, true));
        assertTrue(EncounterPlacement.framedOrCorner(true, true, false, false));
        assertTrue(EncounterPlacement.framedOrCorner(true, false, true, false));
        assertFalse(EncounterPlacement.framedOrCorner(false, false, false, false));
        assertFalse(EncounterPlacement.framedOrCorner(true, true, true, true));
        assertFalse(EncounterPlacement.framedOrCorner(true, false, false, false));
    }

    @Test
    void recoilStaysOnTheSameFloorAndMovesAwayWithoutTeleporting() {
        Vec3 actor = new Vec3(3, 64, 4);
        Vec3 player = new Vec3(0, 70, 0);
        Vec3 next = EncounterPlacement.stepAway(actor, player);
        assertEquals(actor.y, next.y);
        assertEquals(0.22, next.distanceTo(actor), 1E-9);
        assertTrue(next.distanceToSqr(player) > actor.distanceToSqr(player));
        assertEquals(actor, EncounterPlacement.stepAway(actor, actor));
    }

    @Test
    void observationAndReboundUseOppositeSidesOfTheView() {
        Vec3 look = new Vec3(0, 0, 1);
        assertEquals(1, EncounterPlacement.facingDot(Vec3.ZERO, look, new Vec3(0, 0, 8)), 1E-9);
        assertEquals(-1, EncounterPlacement.facingDot(Vec3.ZERO, look, new Vec3(0, 0, -8)), 1E-9);
        assertEquals(-1, EncounterPlacement.facingDot(Vec3.ZERO, Vec3.ZERO, look), 1E-9);
    }

    @Test
    void worldBorderContainsTheWholeBodyNotOnlyTheFeet() {
        assertTrue(EncounterPlacement.withinBorder(new AABB(0, 64, 0, 1, 67, 1), 0, 0, 1, 1));
        assertFalse(EncounterPlacement.withinBorder(new AABB(-0.1, 64, 0, 0.9, 67, 1), 0, 0, 1, 1));
        assertFalse(EncounterPlacement.withinBorder(new AABB(0, 64, 0, 1.1, 67, 1), 0, 0, 1, 1));
    }
}

package fr.penumbra.story;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConfrontationPlacementTest {
    @Test
    void mirrorStepCannotTeleportAcrossArenaOrChangeFloors() {
        Vec3 from = new Vec3(0, 64, 0);
        Vec3 next = ConfrontationPlacement.stepToward(from, new Vec3(30, 100, 40));
        assertEquals(ConfrontationPlacement.MAX_STEP, from.distanceTo(next), 1.0E-9);
        assertEquals(64, next.y);
        assertEquals(from, ConfrontationPlacement.stepToward(from, from));
    }

    @Test
    void shortStepStopsAtDestinationWithoutOvershooting() {
        Vec3 target = new Vec3(0.2, 64, 0.1);
        assertEquals(target, ConfrontationPlacement.stepToward(new Vec3(0, 64, 0), target));
    }

    @Test
    void copyCannotApproachInsideSafetyRadiusButCanRetreatFromPlayerWhoApproaches() {
        Vec3 player = new Vec3(0, 64, 0);
        assertFalse(ConfrontationPlacement.keepsSeparation(
                new Vec3(5.1, 64, 0), new Vec3(4.9, 64, 0), player, 5));
        assertFalse(ConfrontationPlacement.keepsSeparation(
                new Vec3(2, 64, 0), new Vec3(1.9, 64, 0), player, 5));
        assertTrue(ConfrontationPlacement.keepsSeparation(
                new Vec3(2, 64, 0), new Vec3(2.1, 64, 0), player, 5));
    }

    @Test
    void borderMustContainWholeHitboxRatherThanOnlyItsCenter() {
        assertTrue(ConfrontationPlacement.withinBorder(new AABB(0, 64, 0, 1, 66, 1), 0, 0, 1, 1));
        assertFalse(ConfrontationPlacement.withinBorder(new AABB(-0.1, 64, 0, 0.9, 66, 1), 0, 0, 1, 1));
        assertFalse(ConfrontationPlacement.withinBorder(new AABB(0, 64, 0, 1.1, 66, 1), 0, 0, 1, 1));
    }
}

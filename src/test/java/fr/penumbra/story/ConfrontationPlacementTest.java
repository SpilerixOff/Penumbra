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

    @Test
    void repeatedMirrorStepsStopBeforeCentralPillarHidesCopy() {
        Vec3 viewerEyes = new Vec3(0, 65.62, -8);
        AABB pillar = new AABB(-1, 64, -1, 1, 68, 1);
        Vec3 current = new Vec3(6, 64, 8);
        Vec3 mirrored = new Vec3(0, 64, 8);
        boolean stopped = false;
        for (int tick = 0; tick < 40; tick++) {
            Vec3 next = ConfrontationPlacement.visibleStepToward(current, mirrored,
                    current.add(0, 1.62, 0),
                    eyes -> pillar.clip(viewerEyes, eyes).isEmpty());
            assertTrue(pillar.clip(viewerEyes, next.add(0, 1.62, 0)).isEmpty(),
                    "The copy must remain frameable while trying to mirror behind the pillar");
            stopped |= next.equals(current);
            current = next;
        }
        assertTrue(stopped);
        assertNotEquals(mirrored, current);
        assertTrue(current.x < 6, "Visible mirror steps should still be allowed");
    }

    @Test
    void visibilityUsesTranslatedEyesAndAllowsRetreatWhenPlayerStepsAside() {
        Vec3 current = new Vec3(2.4, 64, 8);
        Vec3 mirrored = new Vec3(0, 64, 8);
        AABB pillar = new AABB(-1, 64, -1, 1, 68, 1);
        Vec3 blockedViewer = new Vec3(0, 65.62, -8);
        assertEquals(current, ConfrontationPlacement.visibleStepToward(current, mirrored,
                current.add(0, 1.62, 0), eyes -> pillar.clip(blockedViewer, eyes).isEmpty()));

        Vec3 clearViewer = new Vec3(8, 65.62, -8);
        Vec3 next = ConfrontationPlacement.visibleStepToward(current, mirrored,
                current.add(0, 1.62, 0), eyes -> pillar.clip(clearViewer, eyes).isEmpty());
        assertEquals(ConfrontationPlacement.stepToward(current, mirrored), next);

        AABB lowObstacle = new AABB(-1, 64, -1, 1, 65, 1);
        Vec3 opposite = new Vec3(0, 64, 8);
        Vec3 closer = new Vec3(0, 64, 7.4);
        assertEquals(closer, ConfrontationPlacement.visibleStepToward(opposite, closer,
                opposite.add(0, 1.62, 0), eyes -> lowObstacle.clip(blockedViewer, eyes).isEmpty()),
                "A low obstacle below both eyes must not hide a usable mirror step");
    }
}

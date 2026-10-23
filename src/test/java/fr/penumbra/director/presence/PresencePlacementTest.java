package fr.penumbra.director.presence;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class PresencePlacementTest {
    @Test
    void candidateAndPathSearchBudgetsAreStrictAndIndependentOfWorldSize() {
        Vec3 position = new Vec3(10.5, 64, -30.5);
        var points = PresencePlacement.candidates(position, new Vec3(0, 0, 1));
        assertFalse(points.isEmpty());
        assertTrue(points.size() <= 144);
        assertEquals(points.size(), new HashSet<>(points).size());
        for (var point : points) {
            assertTrue(Math.abs(point.getY() - position.y) <= 1);
            assertTrue(Vec3.atBottomCenterOf(point).distanceTo(position) < 12);
        }
        assertTrue(PresencePlacement.MAX_ROUTE_ATTEMPTS * PresencePlacement.MAX_ROUTE_CHECKS <= 880);
        assertTrue(PresencePlacement.MAX_ROUTE_VISITS <= 72);
    }

    @Test
    void aBodyMovesInSmallContinuousStepsAndNeverOvershoots() {
        Vec3 from = new Vec3(1, 64, 4);
        Vec3 to = new Vec3(2, 64, 4);
        Vec3 step = PresencePlacement.stepToward(from, to, PresencePlacement.STEP);
        assertEquals(PresencePlacement.STEP, step.distanceTo(from), 1E-9);
        assertEquals(from.y, step.y);
        assertEquals(to, PresencePlacement.stepToward(new Vec3(1.97, 64, 4), to, PresencePlacement.STEP));
        assertEquals(to, PresencePlacement.stepToward(to, to, PresencePlacement.STEP));
    }

    @Test
    void aClosePlayerCanFollowTheRetreatWithoutCancellingTheThreat() {
        Vec3 owner = new Vec3(0, 64, 0);
        Vec3 current = new Vec3(3.3, 64, 0);
        assertTrue(PresencePlacement.separationAllowsStep(current, new Vec3(3.41, 64, 0), owner, true));
        assertFalse(PresencePlacement.separationAllowsStep(current, new Vec3(3.19, 64, 0), owner, true));
        assertFalse(PresencePlacement.separationAllowsStep(current, new Vec3(3.41, 64, 0), owner, false));
        assertFalse(PresencePlacement.separationAllowsStep(current, current, owner, true));
        assertTrue(PresencePlacement.separationAllowsStep(new Vec3(3.6, 64, 0), new Vec3(3.5, 64, 0), owner, false));
    }

    @Test
    void physicalSceneRemainsInsideTheBorderIncludingTheWholeBody() {
        assertTrue(PresencePlacement.withinBorder(new AABB(-2, 64, -2, 2, 67, 2), -2, -2, 2, 2));
        assertFalse(PresencePlacement.withinBorder(new AABB(-2.001, 64, -2, 2, 67, 2), -2, -2, 2, 2));
        assertFalse(PresencePlacement.withinBorder(new AABB(-2, 64, -2, 2.001, 67, 2), -2, -2, 2, 2));
    }

    @Test
    void calmMeansNotMovingNotMiningNotSprintingAndFeetOnTheGround() {
        Vec3 point = new Vec3(1, 64, 4);
        assertTrue(SecondPresenceManager.isCalm(point, point, false, false, true));
        assertFalse(SecondPresenceManager.isCalm(point, point.add(0.02, 0, 0), false, false, true));
        assertFalse(SecondPresenceManager.isCalm(point, point, true, false, true));
        assertFalse(SecondPresenceManager.isCalm(point, point, false, true, true));
        assertFalse(SecondPresenceManager.isCalm(point, point, false, false, false));
    }
}

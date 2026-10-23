package fr.penumbra.entity;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostGrappleTest {
    @Test
    void leapLeavesTheGroundAndReturnsBeforeGrabbing() {
        assertEquals(0.0, HostGrapple.leapHeight(0.0));
        assertEquals(HostGrapple.LEAP_HEIGHT, HostGrapple.leapHeight(0.5));
        assertEquals(0.0, HostGrapple.leapHeight(1.0));
        assertEquals(0.0, HostGrapple.leapHeight(-1.0));
        assertEquals(0.0, HostGrapple.leapHeight(2.0));
        assertEquals(0.0, HostGrapple.leapHeight(Double.NaN));
        for (int step = 0; step <= 100; step++) {
            double height = HostGrapple.leapHeight(step / 100.0);
            assertTrue(height >= 0.0 && height <= 0.85);
        }
    }

    @Test
    void phaseBoundariesLeaveTimeToReadTheHoldAndFollowThrough() {
        assertEquals(22, HostGrapple.THROW_START_TICK);
        assertEquals(28, HostGrapple.TOTAL_TICKS);
        assertEquals(0.0, HostGrapple.liftHeight(HostGrapple.GRAB_TICKS));
        assertEquals(HostGrapple.LIFT_HEIGHT, HostGrapple.liftHeight(
                HostGrapple.GRAB_TICKS + HostGrapple.LIFT_TICKS), 1.0E-9);
        assertEquals(HostGrapple.LIFT_HEIGHT, HostGrapple.liftHeight(HostGrapple.THROW_START_TICK));
        assertEquals(HostGrapple.LIFT_HEIGHT, HostGrapple.liftHeight(HostGrapple.TOTAL_TICKS));
    }

    @Test
    void grabUsesTheActualContactPositionThenLiftsInFrontOfBothHands() {
        Vec3 host = new Vec3(10.0, 60.0, 10.0);
        Vec3 player = new Vec3(10.15, 60.0, 10.68);
        Vec3 direction = HostGrapple.direction(host, player, Vec3.ZERO);
        assertEquals(player, HostGrapple.heldPosition(player, host, direction, 0));
        Vec3 grabbed = HostGrapple.heldPosition(player, host, direction, HostGrapple.GRAB_TICKS);
        assertEquals(HostGrapple.GRIP_DISTANCE, grabbed.subtract(host).horizontalDistance(), 1.0E-9);
        assertEquals(player.y, grabbed.y);
        Vec3 held = HostGrapple.heldPosition(player, host, direction, HostGrapple.THROW_START_TICK);
        assertEquals(grabbed.x, held.x);
        assertEquals(grabbed.z, held.z);
        assertEquals(player.y + HostGrapple.LIFT_HEIGHT, held.y, 1.0E-9);
    }

    @Test
    void liftIsMonotonicAndUsesSmallStepsForCollisionChecks() {
        double previous = 0.0;
        for (int tick = 0; tick <= HostGrapple.THROW_START_TICK; tick++) {
            double height = HostGrapple.liftHeight(tick);
            assertTrue(height >= previous);
            assertTrue(height - previous <= 0.14);
            assertTrue(height <= HostGrapple.LIFT_HEIGHT);
            previous = height;
        }
    }

    @Test
    void throwIsBoundedAndHorizontalDirectionDoesNotAddVerticalEnergy() {
        Vec3 throwVelocity = HostGrapple.throwVelocity(new Vec3(100.0, 1000.0, 0.0));
        assertEquals(HostGrapple.THROW_HORIZONTAL_SPEED, throwVelocity.horizontalDistance(), 1.0E-9);
        assertEquals(HostGrapple.THROW_VERTICAL_SPEED, throwVelocity.y);
        assertTrue(throwVelocity.x > 0.0);
        assertEquals(0.0, throwVelocity.z);
        Vec3 opposite = HostGrapple.throwVelocity(new Vec3(-10.0, -1000.0, 0.0));
        assertEquals(-throwVelocity.x, opposite.x, 1.0E-9);
        assertEquals(throwVelocity.y, opposite.y);
    }

    @Test
    void overlappingOrInvalidDirectionsStillProduceFiniteBoundedMotion() {
        for (Vec3 direction : new Vec3[] {Vec3.ZERO, new Vec3(0.0, 100.0, 0.0),
                new Vec3(Double.NaN, 0.0, 0.0), new Vec3(Double.POSITIVE_INFINITY, 0.0, 0.0)}) {
            Vec3 velocity = HostGrapple.throwVelocity(direction);
            assertEquals(HostGrapple.THROW_HORIZONTAL_SPEED, velocity.horizontalDistance(), 1.0E-9);
            assertEquals(HostGrapple.THROW_VERTICAL_SPEED, velocity.y);
        }
    }

    @Test
    void wallAndHorizontalOrVerticalDodgePreventTheGrab() {
        Vec3 host = Vec3.ZERO;
        assertTrue(HostGrapple.canGrab(host, new Vec3(0.0, 0.0, 0.68), true));
        assertFalse(HostGrapple.canGrab(host, new Vec3(0.0, 0.0, 0.68), false));
        assertFalse(HostGrapple.canGrab(host, new Vec3(1.2, 0.0, 0.68), true));
        assertFalse(HostGrapple.canGrab(host, new Vec3(0.0, 0.8, 0.68), true));
        assertFalse(HostGrapple.canGrab(host, new Vec3(Double.NaN, 0.0, 0.0), true));
        assertFalse(HostGrapple.canGrab(host, new Vec3(0.0, 0.0, 1.9), true));
    }

    @Test
    void externalTeleportReleasesInsteadOfPullingThePlayerBack() {
        Vec3 anchor = new Vec3(10.0, 60.0, 10.0);
        assertTrue(HostGrapple.canContinueHolding(anchor.add(0.3, -0.08, 0.0), anchor));
        assertFalse(HostGrapple.canContinueHolding(anchor.add(0.0, 0.0, 5.0), anchor));
        assertFalse(HostGrapple.canContinueHolding(new Vec3(Double.NaN, 60.0, 10.0), anchor));
        assertFalse(HostGrapple.canContinueHolding(anchor, null));
    }

    @Test
    void progressInterpolatesUntilTheFullFollowThroughIsFinished() {
        assertEquals(0.0f, HostGrapple.progress(0, 0.0f));
        assertEquals(0.5f, HostGrapple.progress(14, 0.0f));
        assertTrue(HostGrapple.progress(22, 0.5f) < 1.0f);
        assertEquals(1.0f, HostGrapple.progress(28, 0.5f));
        assertEquals(1.0f, HostGrapple.progress(100, 100.0f));
        assertEquals(0.0f, HostGrapple.progress(-100, -100.0f));
    }
}

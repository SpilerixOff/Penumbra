package fr.penumbra.entity.contrefait;

import org.junit.jupiter.api.Test;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ContrefaitLungeTest {

    @Test
    void fastCrossingSamplesCannotSkipTheVictimsBody() {
        for (double travel : new double[]{0, .01, .20, .201, 2, 15, 64, 128}) {
            int steps = ContrefaitLunge.collisionSteps(travel);
            assertTrue(steps >= 1);
            assertTrue(travel / steps <= .200001);
        }
        assertEquals(1, ContrefaitLunge.collisionSteps(Double.NaN));
        AABB victim = new AABB(-.3, 64, 4.7, .3, 65.8, 5.3);
        int steps = ContrefaitLunge.collisionSteps(10);
        boolean contacted = false;
        for (int i = 1; i <= steps; i++) {
            double z = 10.0 * i / steps;
            contacted |= ContrefaitLunge.canHit(new AABB(-.3,64,z-.3,.3,65.95,z+.3),victim,true);
        }
        assertTrue(contacted);
    }

    @Test
    void committedRouteEndsAtBodyReachAndDoesNotFollowAnEscapingTarget() {
        Vec3 start = new Vec3(0, 64, 0);
        Vec3 originalTarget = new Vec3(0, 64, 8);
        Vec3 committed = ContrefaitLunge.committedTarget(start, originalTarget);
        assertEquals(7.55, committed.z, 1E-6);
        assertEquals(committed, ContrefaitLunge.position(start, committed, 10, 10));
        AABB arrivalBody = new AABB(-.3, 64, committed.z - .3, .3, 65.95, committed.z + .3);
        AABB stationaryVictim = new AABB(-.3,64,7.7,.3,65.8,8.3);
        AABB dodgedVictim = stationaryVictim.move(1.4, 0, 0);
        assertTrue(ContrefaitLunge.canHit(arrivalBody, stationaryVictim, true));
        assertFalse(ContrefaitLunge.canHit(arrivalBody, dodgedVictim, true));
        assertFalse(ContrefaitLunge.canHit(arrivalBody, stationaryVictim, false));
        assertFalse(ContrefaitLunge.canHit(arrivalBody, stationaryVictim.move(0, 3, 0), true));
    }

    @Test
    void routeNeverOvershootsOrMovesWhenTargetIsAlreadyCoincident() {
        Vec3 start = new Vec3(3, 64, -2);
        assertEquals(start, ContrefaitLunge.committedTarget(start, start));
        Vec3 target = new Vec3(8,65,11);
        assertEquals(start, ContrefaitLunge.position(start, target, -5, 10));
        assertEquals(target, ContrefaitLunge.position(start, target, 50, 10));
    }

    @Test
    void packedStateKeepsElapsedAndClampsDuration() {
        int state = ContrefaitLunge.pack(5, 8);
        assertEquals(5, ContrefaitLunge.elapsedTicks(state));
        assertEquals(8, ContrefaitLunge.durationTicks(state));

        int clamped = ContrefaitLunge.pack(99, 99);
        assertEquals(ContrefaitLunge.MAX_DURATION_TICKS, ContrefaitLunge.elapsedTicks(clamped));
        assertEquals(ContrefaitLunge.MAX_DURATION_TICKS, ContrefaitLunge.durationTicks(clamped));
    }

    @Test
    void sustainedStareAccumulatesAndBrokenStareResets() {
        assertEquals(2, ContrefaitLunge.nextStareTicks(0, true, 2));
        assertEquals(42, ContrefaitLunge.nextStareTicks(40, true, 2));
        assertEquals(0, ContrefaitLunge.nextStareTicks(42, false, 2));
        assertEquals(20, ContrefaitLunge.stareThresholdTicks(0));
        assertEquals(60, ContrefaitLunge.stareThresholdTicks(3));
    }

    @Test
    void renderProgressAndTrajectoryAreBoundedAndMonotonic() {
        int state = ContrefaitLunge.pack(4, 8);
        assertEquals(0.5625f, ContrefaitLunge.progress(state, 0.5f), 0.0001f);
        assertEquals(0.0f, ContrefaitLunge.progress(0, 0.5f));

        double previous = -1.0;
        for (int tick = 0; tick <= 10; tick++) {
            double current = ContrefaitLunge.easedProgress(tick, 10);
            assertTrue(current >= previous);
            previous = current;
        }
        assertEquals(1.0, previous);
    }
}

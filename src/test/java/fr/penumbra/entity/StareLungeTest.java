package fr.penumbra.entity;

import org.junit.jupiter.api.Test;
import net.minecraft.world.phys.Vec3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class StareLungeTest {

    @Test
    void stateRoundTripsAndClampsElapsedTime() {
        int state = StareLunge.pack(99, 10);
        assertEquals(10, StareLunge.elapsedTicks(state));
        assertEquals(10, StareLunge.durationTicks(state));
        assertEquals(1.0f, StareLunge.progress(state, 0.5f));
    }

    @Test
    void terminalThirdIsFasterThanOpeningThird() {
        double opening = StareLunge.easedProgress(3, 12) - StareLunge.easedProgress(0, 12);
        double ending = StareLunge.easedProgress(12, 12) - StareLunge.easedProgress(9, 12);
        assertTrue(ending > opening * 3.0);
    }

    @Test void contactCannotFollowADodgeOrPassThroughAWall() {
        assertTrue(StareLunge.canContact(0.8 * 0.8, true));
        assertFalse(StareLunge.canContact(0.8 * 0.8, false));
        assertFalse(StareLunge.canContact(2.0 * 2.0, true));
        assertFalse(StareLunge.canContact(Double.NaN, true));
        assertFalse(StareLunge.canContact(Double.POSITIVE_INFINITY, true));
    }

    @Test void recoveryMovesAwayHorizontallyAndSlowsDown() {
        Vec3 origin = new Vec3(3.0, 10.0, 0.0);
        Vec3 threat = new Vec3(0.0, 4.0, 0.0);
        Vec3 first = StareLunge.retreatStep(origin, threat, Vec3.ZERO, 1);
        Vec3 later = StareLunge.retreatStep(origin, threat, Vec3.ZERO, 20);
        assertTrue(first.x > 0.0);
        assertEquals(0.0, first.y);
        assertTrue(first.length() > later.length());
        assertTrue(first.length() <= 0.23);
    }

    @Test void overlappingBodiesStillHaveABoundedEscapeDirection() {
        Vec3 step = StareLunge.retreatStep(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 1);
        assertEquals(0.22, step.length(), 1.0E-9);
        assertEquals(0.0, step.y);
        Vec3 backward = StareLunge.retreatStep(Vec3.ZERO, Vec3.ZERO, new Vec3(0, 0, 1), 1);
        assertTrue(backward.z < 0.0);
    }
}

package fr.penumbra.entity.scuttler;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScuttlerFeintTest {
    @Test
    void feintRequiresOneVisibleGroundedEncounterWithTimeToFinish() {
        assertTrue(ScuttlerFeint.canStart(false, 12, 20, 4.0, true, true));
        assertFalse(ScuttlerFeint.canStart(true, 12, 20, 4.0, true, true));
        assertFalse(ScuttlerFeint.canStart(false, 11, 20, 4.0, true, true));
        assertFalse(ScuttlerFeint.canStart(false, 12, 19, 4.0, true, true));
        assertFalse(ScuttlerFeint.canStart(false, 12, 20, 4.0, false, true));
        assertFalse(ScuttlerFeint.canStart(false, 12, 20, 4.0, true, false));
        assertFalse(ScuttlerFeint.canStart(false, 12, 20, 4.5, true, true));
        assertFalse(ScuttlerFeint.canStart(false, 12, 20, Double.NaN, true, true));
    }

    @Test
    void theCreatureStopsForTheDisplayThenRetreatsBrieflyWithoutTeleportation() {
        double travel = 0;
        for (int tick = 0; tick <= ScuttlerFeint.TOTAL_TICKS + 5; tick++) {
            double speed = ScuttlerFeint.recoilSpeed(tick);
            if (tick < ScuttlerFeint.RECOIL_START_TICK || tick >= ScuttlerFeint.TOTAL_TICKS) {
                assertEquals(0.0, speed);
            } else {
                assertTrue(speed >= 0.18 && speed <= 0.32);
                travel += speed;
            }
        }
        assertTrue(travel > 1.5 && travel < 2.5);
    }

    @Test
    void trackingMidFeintCannotReplayTheBrakeOrProduceUnboundedProgress() {
        assertEquals(13.25f, ScuttlerFeint.elapsed(14, 0.25f));
        assertEquals(0.0f, ScuttlerFeint.elapsed(0, 1));
        assertEquals(20.0f, ScuttlerFeint.elapsed(Integer.MAX_VALUE, 1));
        assertEquals(8.0f, ScuttlerFeint.elapsed(9, Float.NaN));
    }
}

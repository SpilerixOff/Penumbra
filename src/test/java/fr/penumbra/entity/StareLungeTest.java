package fr.penumbra.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}

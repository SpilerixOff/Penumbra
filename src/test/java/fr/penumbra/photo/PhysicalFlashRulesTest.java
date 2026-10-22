package fr.penumbra.photo;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PhysicalFlashRulesTest {
    @Test void cannotRepelThroughWallsBehindTheCameraOrAtPhotographicDistance() {
        assertTrue(PhysicalFlashRules.canRepel(3, 0.9, true));
        assertFalse(PhysicalFlashRules.canRepel(3, 0.9, false));
        assertFalse(PhysicalFlashRules.canRepel(3, -1, true));
        assertFalse(PhysicalFlashRules.canRepel(30, 1, true));
        assertFalse(PhysicalFlashRules.canRepel(5, Double.NaN, true));
    }
    @Test void edgeOfTheUsefulConeAndRangeIsIncluded() {
        assertTrue(PhysicalFlashRules.canRepel(12, PhysicalFlashRules.ALIGNMENT, true));
        assertFalse(PhysicalFlashRules.canRepel(12.01, 1, true));
        assertFalse(PhysicalFlashRules.canRepel(0, 1, true));
    }
}

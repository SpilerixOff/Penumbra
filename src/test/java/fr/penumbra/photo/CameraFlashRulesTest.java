package fr.penumbra.photo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraFlashRulesTest {

    @Test
    void exposesVisibleSubjectsInsideTheWideForwardCone() {
        assertTrue(CameraFlashRules.isExposed(8.0, 1.0, true));
        assertTrue(CameraFlashRules.isExposed(
                CameraFlashRules.REVEAL_DISTANCE,
                CameraFlashRules.MIN_REVEAL_ALIGNMENT, true));
    }

    @Test
    void neverPassesThroughWallsBehindTheCameraOrBeyondItsUsefulRange() {
        assertFalse(CameraFlashRules.isExposed(8.0, 1.0, false));
        assertFalse(CameraFlashRules.isExposed(8.0, -1.0, true));
        assertFalse(CameraFlashRules.isExposed(
                Math.nextUp(CameraFlashRules.REVEAL_DISTANCE), 1.0, true));
        assertFalse(CameraFlashRules.isExposed(Double.NaN, 1.0, true));
        assertFalse(CameraFlashRules.isExposed(8.0, Double.POSITIVE_INFINITY, true));
    }
}

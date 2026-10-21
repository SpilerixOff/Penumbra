package fr.penumbra.client.jumpscare;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreamerPresentationTest {

    @Test
    void sanitizeStyleFallsBackToClassic() {
        assertEquals(ScreamerPresentation.CLASSIC, ScreamerPresentation.sanitizeStyle(-1));
        assertEquals(ScreamerPresentation.CLASSIC,
                ScreamerPresentation.sanitizeStyle(ScreamerPresentation.STYLE_COUNT));
        assertEquals(ScreamerPresentation.CREEP_ZOOM,
                ScreamerPresentation.sanitizeStyle(ScreamerPresentation.CREEP_ZOOM));
    }

    @Test
    void contactFinalsNeverUseSoftStyles() {
        for (int style = 0; style < ScreamerPresentation.STYLE_COUNT; style++) {
            int finalized = ScreamerPresentation.finalizeForContact(style);
            assertFalse(finalized == ScreamerPresentation.SUBLIMINAL,
                    "une finale de contact ne peut pas être subliminale");
            assertFalse(finalized == ScreamerPresentation.DOUBLE_CUT,
                    "pas de trou noir pendant un contact physique");
        }
    }

    @Test
    void scaleNeverExposesTextureEdges() {
        for (int style = 0; style < ScreamerPresentation.STYLE_COUNT; style++) {
            for (float p = 0.0f; p <= 1.001f; p += 0.05f) {
                float scale = ScreamerPresentation.scale(style, p);
                assertTrue(scale >= 1.0f, "échelle < 1 au style " + style);
                assertTrue(scale <= 1.5f, "échelle démesurée au style " + style);
            }
        }
        assertEquals(1.0f, ScreamerPresentation.scale(ScreamerPresentation.CLASSIC, Float.NaN));
    }

    @Test
    void doubleCutHasGapButShowsMostTicks() {
        int duration = ScreamerPresentation.durationTicks(
                ScreamerPresentation.DOUBLE_CUT, 8);
        int visible = 0;
        boolean sawGap = false;
        for (int tick = 0; tick < duration; tick++) {
            if (ScreamerPresentation.visibleAt(ScreamerPresentation.DOUBLE_CUT, tick, duration)) {
                visible++;
            } else {
                sawGap = true;
            }
        }
        assertTrue(sawGap, "le double cut doit avoir un trou noir");
        assertTrue(visible >= duration - 2, "le trou noir dure exactement 2 ticks");
        // Les autres styles restent visibles en continu.
        for (int tick = 0; tick < 8; tick++) {
            assertTrue(ScreamerPresentation.visibleAt(ScreamerPresentation.CLASSIC, tick, 8));
        }
        assertFalse(ScreamerPresentation.visibleAt(ScreamerPresentation.CLASSIC, 8, 8));
        assertFalse(ScreamerPresentation.visibleAt(ScreamerPresentation.CLASSIC, -1, 8));
    }

    @Test
    void subliminalIsShortQuietAndCapped() {
        assertEquals(ScreamerPresentation.SUBLIMINAL_DURATION_TICKS,
                ScreamerPresentation.durationTicks(ScreamerPresentation.SUBLIMINAL, 8));
        assertTrue(ScreamerPresentation.whisperInsteadOfShriek(ScreamerPresentation.SUBLIMINAL));
        assertFalse(ScreamerPresentation.whisperInsteadOfShriek(ScreamerPresentation.CLASSIC));
        assertEquals(ScreamerPresentation.SUBLIMINAL_ALPHA_CAP,
                ScreamerPresentation.alphaCap(ScreamerPresentation.SUBLIMINAL, 255));
        assertEquals(255, ScreamerPresentation.alphaCap(ScreamerPresentation.CLASSIC, 255));
        assertEquals(0, ScreamerPresentation.alphaCap(ScreamerPresentation.CLASSIC, -10));
        assertTrue(ScreamerPresentation.shakeDurationTicks(ScreamerPresentation.SUBLIMINAL, 16) <= 4);
    }

    @Test
    void durationsStayReasonableForAllStyles() {
        for (int style = 0; style < ScreamerPresentation.STYLE_COUNT; style++) {
            int duration = ScreamerPresentation.durationTicks(style, 8);
            assertTrue(duration >= 2 && duration <= 16,
                    "durée hors bornes au style " + style + " : " + duration);
            assertTrue(ScreamerPresentation.durationTicks(style, Integer.MIN_VALUE) >= 2);
        }
    }

    @Test
    void offsetsStayOnScreenFraction() {
        for (int style = 0; style < ScreamerPresentation.STYLE_COUNT; style++) {
            for (int sign : new int[] {-1, 1}) {
                float x = ScreamerPresentation.offsetXFraction(style, sign);
                float y = ScreamerPresentation.offsetYFraction(style);
                assertTrue(Math.abs(x) <= 0.20f);
                assertTrue(Math.abs(y) <= 0.20f);
            }
        }
        // Le signe oriente le décalage horizontal du style décentré.
        assertTrue(ScreamerPresentation.offsetXFraction(ScreamerPresentation.OFFSET_CUT, 1) > 0.0f);
        assertTrue(ScreamerPresentation.offsetXFraction(ScreamerPresentation.OFFSET_CUT, -1) < 0.0f);
    }

    @Test
    void shriekPitchStaysInDarkRange() {
        for (float roll = 0.0f; roll <= 1.0f; roll += 0.1f) {
            float normal = ScreamerPresentation.shriekPitch(roll, false);
            float contact = ScreamerPresentation.shriekPitch(roll, true);
            assertTrue(normal >= 0.88f && normal <= 1.08f);
            assertTrue(contact >= 0.86f && contact <= 0.96f);
        }
        assertEquals(0.88f, ScreamerPresentation.shriekPitch(Float.NaN, false), 0.0001f);
    }
}

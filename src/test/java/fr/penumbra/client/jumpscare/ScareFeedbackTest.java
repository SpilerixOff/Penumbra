package fr.penumbra.client.jumpscare;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScareFeedbackTest {
    @Test void physicalImpactDoesNotPlayTheEntityCryTwice() {
        ScareFeedback impact = ScareFeedback.forType("impact");
        assertEquals(ScareFeedback.Sound.NONE, impact.sound());
        assertTrue(impact.critical());
        assertTrue(impact.contact());
        assertTrue(impact.shakeTicks() <= 8);
    }

    @Test void allLegacyContactPacketsRemainCompatible() {
        for (String name : new String[]{"lunge", "rampant", "absent", "chorus", "refuge", "mimic", "listener"}) {
            ScareFeedback effect = ScareFeedback.forType(name + "_screamer");
            assertNotNull(effect, name);
            assertEquals(ScareFeedback.Sound.SHRIEK, effect.sound());
            assertTrue(effect.contact());
        }
    }

    @Test void invalidPacketsHaveNoFeedback() {
        assertNull(ScareFeedback.forType(null));
        assertNull(ScareFeedback.forType("untrusted"));
    }
}

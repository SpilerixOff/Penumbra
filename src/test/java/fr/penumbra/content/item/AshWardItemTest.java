package fr.penumbra.content.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AshWardItemTest {

    @Test
    void banishmentUsesAnExactSixteenBlockRadius() {
        assertTrue(AshWardPolicy.isWithinRadius(0.0, 16.0));
        assertTrue(AshWardPolicy.isWithinRadius(16.0 * 16.0, 16.0));
        assertFalse(AshWardPolicy.isWithinRadius(
                Math.nextUp(16.0 * 16.0), 16.0));
        assertFalse(AshWardPolicy.isWithinRadius(-1.0, 16.0));
        assertFalse(AshWardPolicy.isWithinRadius(0.0, -1.0));
    }

    @Test
    void anotherPlayersPrivateManifestationIsPreserved() {
        assertTrue(AshWardPolicy.canBanishFor(false, false));
        assertTrue(AshWardPolicy.canBanishFor(true, true));
        assertFalse(AshWardPolicy.canBanishFor(true, false));
    }
}

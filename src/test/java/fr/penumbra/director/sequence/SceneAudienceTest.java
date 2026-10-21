package fr.penumbra.director.sequence;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SceneAudienceTest {

    private static final UUID OWNER = new UUID(0L, 1L);
    private static final UUID WITNESS = new UUID(0L, 2L);
    private static final UUID STRANGER = new UUID(0L, 3L);

    @Test
    void privateAudienceNeverLeaksToAnotherValidPlayer() {
        SceneAudience audience = SceneAudience.privateTo(OWNER);

        assertTrue(audience.mayObserve(OWNER, true, true, false, 400.0));
        assertFalse(audience.mayObserve(WITNESS, true, true, false, 1.0));
        assertEquals(Set.of(OWNER), audience.leaseParticipants());
    }

    @Test
    void localAudienceRequiresAnExplicitNearbyWitness() {
        SceneAudience audience = SceneAudience.local(OWNER, Set.of(WITNESS));

        assertTrue(audience.mayObserve(WITNESS, true, true, false,
                SceneAudience.LOCAL_RADIUS));
        assertFalse(audience.mayObserve(WITNESS, true, true, false,
                SceneAudience.LOCAL_RADIUS + 0.01));
        assertFalse(audience.mayObserve(STRANGER, true, true, false, 2.0));
        assertFalse(audience.mayObserve(WITNESS, false, true, false, 2.0));
        assertFalse(audience.mayObserve(WITNESS, true, false, false, 2.0));
        assertFalse(audience.mayObserve(WITNESS, true, true, true, 2.0));
    }

    @Test
    void sharedAudienceAllowsAnyValidObserverInsideItsBoundedRadius() {
        SceneAudience audience = SceneAudience.shared(OWNER, Set.of(WITNESS), 32.0);

        assertTrue(audience.mayObserve(STRANGER, true, true, false, 32.0));
        assertFalse(audience.mayObserve(STRANGER, true, true, false, 32.01));
        assertFalse(audience.mayObserve(STRANGER, true, true, false, Double.NaN));
    }

    @Test
    void witnessSetIsDeduplicatedSortedAndStrictlyBounded() {
        LinkedHashSet<UUID> requested = new LinkedHashSet<>();
        requested.add(OWNER);
        requested.add(null);
        for (int index = 30; index >= 1; index--) {
            requested.add(new UUID(0L, 100L + index));
        }
        SceneAudience audience = SceneAudience.local(OWNER, requested);

        assertEquals(SceneAudience.MAX_WITNESSES, audience.witnesses().size());
        assertFalse(audience.witnesses().contains(OWNER));
        assertFalse(audience.witnesses().contains(null));
        assertEquals(SceneAudience.MAX_WITNESSES + 1,
                audience.leaseParticipants().size());
    }
}

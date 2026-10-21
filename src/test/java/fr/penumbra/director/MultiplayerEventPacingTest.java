package fr.penumbra.director;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MultiplayerEventPacingTest {

    @Test
    void nearbySceneIsBlockedOnlyUntilTheBoundedGapExpires() {
        MultiplayerEventPacing pacing = new MultiplayerEventPacing();
        pacing.record("minecraft:overworld", 10.0, 64.0, 10.0, 1_000L);

        assertFalse(pacing.isAvailable("minecraft:overworld", 20.0, 64.0, 10.0, 1_079L));
        assertTrue(pacing.isAvailable("minecraft:overworld", 20.0, 64.0, 10.0, 1_080L));
        assertEquals(0, pacing.sceneCount("minecraft:overworld"));
    }

    @Test
    void distantPlayersAndOtherDimensionsRemainIndependent() {
        MultiplayerEventPacing pacing = new MultiplayerEventPacing();
        pacing.record("minecraft:overworld", 0.0, 64.0, 0.0, 200L);

        assertTrue(pacing.isAvailable("minecraft:overworld", 40.0, 64.0, 0.0, 201L));
        assertTrue(pacing.isAvailable("minecraft:the_nether", 0.0, 64.0, 0.0, 201L));
        assertFalse(pacing.isAvailable("minecraft:overworld", 32.0, 64.0, 0.0, 201L),
                "the radius boundary is inclusive");
    }

    @Test
    void clockRollbackCannotLeaveAStaleSceneLock() {
        MultiplayerEventPacing pacing = new MultiplayerEventPacing();
        pacing.record("minecraft:overworld", 0.0, 64.0, 0.0, 10_000L);

        assertTrue(pacing.isAvailable("minecraft:overworld", 0.0, 64.0, 0.0, 5L));
    }

    @Test
    void rotatingOrderLetsEveryPlayerLeadOncePerCycle() {
        UUID first = new UUID(0L, 1L);
        UUID second = new UUID(0L, 2L);
        UUID third = new UUID(0L, 3L);
        List<UUID> players = List.of(third, first, second);
        Set<UUID> leaders = new HashSet<>();

        for (long round = 0; round < players.size(); round++) {
            leaders.add(MultiplayerEventPacing.fairOrder(players, round, id -> id).get(0));
        }

        assertEquals(Set.copyOf(players), leaders);
        assertEquals(List.of(second, third, first),
                MultiplayerEventPacing.fairOrder(players, 1L, id -> id));
    }

    @Test
    void eachManagerInvocationAdvancesFairnessAtItsOwnCadence() {
        UUID first = new UUID(0L, 1L);
        UUID second = new UUID(0L, 2L);
        List<UUID> players = List.of(first, second);

        long originelFirst = MultiplayerEventPacing.fairRound(20L, 200, 0);
        long originelSecond = MultiplayerEventPacing.fairRound(220L, 200, 0);
        assertEquals(first,
                MultiplayerEventPacing.fairOrder(players, originelFirst, id -> id).get(0));
        assertEquals(second,
                MultiplayerEventPacing.fairOrder(players, originelSecond, id -> id).get(0));

        long rampantFirst = MultiplayerEventPacing.fairRound(60L, 100, 2);
        long rampantSecond = MultiplayerEventPacing.fairRound(160L, 100, 2);
        assertEquals(first,
                MultiplayerEventPacing.fairOrder(players, rampantFirst, id -> id).get(0));
        assertEquals(second,
                MultiplayerEventPacing.fairOrder(players, rampantSecond, id -> id).get(0));
    }
}

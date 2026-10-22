package fr.penumbra.story;

import fr.penumbra.data.PenumbraPersistentState;
import net.minecraft.nbt.NbtOps;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class StoryOnboardingTest {
    @Test
    void fullInventoryCanRetryAndReconnectNeverDuplicatesSuccessfulDelivery() {
        UUID player = new UUID(0L, 1L);
        PenumbraPersistentState state = new PenumbraPersistentState();
        AtomicInteger notices = new AtomicInteger();
        assertFalse(StoryOnboarding.deliverOnce(state, player, () -> false, notices::incrementAndGet));
        state = reload(state);
        assertEquals(0, notices.get());
        assertTrue(StoryOnboarding.deliverOnce(state, player, () -> true, notices::incrementAndGet));
        state = reload(state);
        assertTrue(StoryOnboarding.deliverOnce(state, player,
                () -> fail("A reconnect must not hand out another book"), notices::incrementAndGet));
        assertEquals(1, notices.get());
    }

    @Test
    void lateArrivalReceivesOwnBookWithoutInheritingAnotherPlayersVictory() {
        UUID veteran = new UUID(0L, 1L);
        UUID newcomer = new UUID(0L, 2L);
        PenumbraPersistentState state = new PenumbraPersistentState();
        state.markOneShot("confrontation_done:" + veteran);
        assertTrue(StoryOnboarding.deliverOnce(state, veteran, () -> true, () -> { }));
        state = reload(state);
        AtomicInteger delivered = new AtomicInteger();
        assertTrue(StoryOnboarding.deliverOnce(state, newcomer,
                () -> { delivered.incrementAndGet(); return true; }, () -> { }));
        assertEquals(1, delivered.get());
        assertFalse(state.isOneShotUsed("confrontation_done:" + newcomer));
        assertTrue(state.isOneShotUsed("confrontation_done:" + veteran));
    }

    private static PenumbraPersistentState reload(PenumbraPersistentState state) {
        var saved = PenumbraPersistentState.CODEC.encodeStart(NbtOps.INSTANCE, state).getOrThrow();
        return PenumbraPersistentState.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
    }
}

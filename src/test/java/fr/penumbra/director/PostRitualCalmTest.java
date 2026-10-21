package fr.penumbra.director;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import fr.penumbra.data.PenumbraPersistentState;
import net.minecraft.nbt.NbtOps;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PostRitualCalmTest {
    private static final UUID OWNER = new UUID(0L, 220L);
    private static final UUID OTHER = new UUID(0L, 221L);

    @Test
    void savedRewardSurvivesDisconnectAndReloadWithoutRestartingItsDuration() {
        PenumbraPersistentState state = new PenumbraPersistentState();
        state.profile(OWNER).extendPostRitualCalm(1_000L, 48_000L);
        state.markOneShot("confrontation_done:" + OWNER);
        state.profile(OWNER).advancePostRitualCalm(3_000L);
        state = reload(state);
        assertEquals(46_000L, state.profile(OWNER).postRitualCalm().remainingTicks());
        assertTrue(state.isOneShotUsed("confrontation_done:" + OWNER));

        HorrorDirector.onPlayerDisconnect(OWNER);
        state = reload(state);
        // The server kept ticking while the player was offline.
        state.profile(OWNER).advancePostRitualCalm(8_000L);
        assertEquals(41_000L, state.profile(OWNER).postRitualCalm().remainingTicks());
        state = reload(state);
        assertFalse(state.profile(OWNER).advancePostRitualCalm(8_000L),
                "A stopped world or another reconnect at the same game time spends no extra time");
        assertEquals(41_000L, state.profile(OWNER).postRitualCalm().remainingTicks());
    }

    @Test
    void savedRewardsRemainIndependentBetweenPlayersAndProfileReset() {
        PenumbraPersistentState state = new PenumbraPersistentState();
        state.profile(OWNER).extendPostRitualCalm(1_000L, 48_000L);
        state.profile(OTHER).extendPostRitualCalm(1_000L, 600L);
        state = reload(state);
        state.profile(OWNER).advancePostRitualCalm(1_600L);
        state.profile(OTHER).advancePostRitualCalm(1_600L);
        assertEquals(47_400L, state.profile(OWNER).postRitualCalm().remainingTicks());
        assertFalse(state.profile(OTHER).postRitualCalm().active());

        state.resetProfile(OWNER);
        assertFalse(reload(state).profile(OWNER).postRitualCalm().active());
    }

    @Test
    void legacySavedProfilesDoNotAcquireARewardOrChangeTheirProgress() {
        JsonObject profile = new JsonObject();
        profile.addProperty("tension", 37.0f);
        profile.addProperty("totalEvents", 12);
        JsonObject profiles = new JsonObject();
        profiles.add(OWNER.toString(), profile);
        JsonObject legacy = new JsonObject();
        legacy.add("profiles", profiles);
        legacy.addProperty("storyStage", 6);
        legacy.addProperty("worldSeedMirror", 123L);

        PenumbraPersistentState state = PenumbraPersistentState.CODEC
                .parse(JsonOps.INSTANCE, legacy).getOrThrow();
        assertFalse(state.profile(OWNER).postRitualCalm().active());
        assertEquals(37.0f, state.profile(OWNER).tension());
        assertEquals(12, state.profile(OWNER).totalEvents());
        assertEquals(6, reload(state).storyStage());
    }

    @Test
    void rewardExpiresAtExactGameTickAndCannotReappearAfterReloadOrRollback() {
        PlayerHorrorProfile profile = new PlayerHorrorProfile();
        profile.extendPostRitualCalm(100L, 48_000L);
        profile.advancePostRitualCalm(48_099L);
        assertEquals(1L, profile.postRitualCalm().remainingTicks());
        profile.advancePostRitualCalm(48_100L);
        profile = reload(profile);
        profile.advancePostRitualCalm(10L);
        assertEquals(PostRitualCalm.NONE, profile.postRitualCalm());
    }

    @Test
    void rolledBackWorldClockKeepsOnlyTheRemainingTime() {
        PlayerHorrorProfile profile = new PlayerHorrorProfile();
        profile.extendPostRitualCalm(10_000L, 48_000L);
        profile.advancePostRitualCalm(20_000L);
        profile = reload(profile);
        profile.advancePostRitualCalm(2_000L);
        assertEquals(38_000L, profile.postRitualCalm().remainingTicks());
        assertEquals(2_000L, profile.postRitualCalm().lastObservedTick());
        profile = reload(profile);
        profile.advancePostRitualCalm(40_000L);
        assertFalse(profile.postRitualCalm().active());
    }

    @Test
    void malformedDurationsAndExtremeClockValuesRemainBounded() {
        JsonObject oversized = new JsonObject();
        oversized.addProperty("remainingTicks", Long.MAX_VALUE);
        oversized.addProperty("lastObservedTick", 100L);
        PostRitualCalm calm = PostRitualCalm.CODEC.parse(JsonOps.INSTANCE, oversized).getOrThrow();
        assertEquals(48_000L, calm.remainingTicks());
        assertFalse(calm.advanceTo(Long.MAX_VALUE).active());
        oversized.addProperty("remainingTicks", -1L);
        assertEquals(PostRitualCalm.NONE,
                PostRitualCalm.CODEC.parse(JsonOps.INSTANCE, oversized).getOrThrow());

        calm = PostRitualCalm.NONE.extendAt(Long.MAX_VALUE - 10L, Long.MAX_VALUE);
        assertEquals(47_990L, calm.advanceTo(Long.MAX_VALUE).remainingTicks(),
                "Remaining time must not overflow an absolute deadline near the clock limit");
        assertEquals(PostRitualCalm.NONE, PostRitualCalm.NONE.extendAt(100L, -1L));
    }

    @Test
    void aShorterRewardDoesNotEraseExistingQuietTimeAndExplicitCancelPersists() {
        PlayerHorrorProfile profile = new PlayerHorrorProfile();
        profile.extendPostRitualCalm(1_000L, 48_000L);
        profile.extendPostRitualCalm(2_000L, 600L);
        assertEquals(47_000L, profile.postRitualCalm().remainingTicks());
        profile.clearPostRitualCalm();
        assertFalse(reload(profile).postRitualCalm().active());
    }

    @Test
    void rewardSilencesPerceivedPressureWithoutReplacingAdaptiveArc() {
        PlayerHorrorProfile profile = new PlayerHorrorProfile();
        profile.addTension(100.0f);
        profile.extendPostRitualCalm(1_000L, 600L);
        DirectorMemory memory = profile.directorMemory();
        assertEquals(TensionState.FALSE_CALM,
                HorrorDirector.perceivedState(OWNER, profile, 1_599L));
        assertSame(memory, profile.directorMemory());
        assertEquals(DirectorMemory.ArcPhase.DORMANT, memory.arc().phase());
        assertEquals(TensionState.CRISIS,
                HorrorDirector.perceivedState(OWNER, profile, 1_600L));
    }

    private static PenumbraPersistentState reload(PenumbraPersistentState state) {
        var encoded = PenumbraPersistentState.CODEC.encodeStart(NbtOps.INSTANCE, state).getOrThrow();
        return PenumbraPersistentState.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
    }

    private static PlayerHorrorProfile reload(PlayerHorrorProfile profile) {
        var encoded = PlayerHorrorProfile.CODEC.encodeStart(JsonOps.INSTANCE, profile).getOrThrow();
        return PlayerHorrorProfile.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
    }
}

package fr.penumbra.copy;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import fr.penumbra.director.HorrorDirector;
import net.minecraft.nbt.NbtOps;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CopyScheduleTest {
    private static final UUID OWNER = new UUID(0L, 230L);
    private static final UUID OTHER = new UUID(0L, 231L);

    @BeforeAll
    static void bootstrapRegistries() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void multipleShortSessionsReachTheOriginalFirstAttemptDeadline() {
        CopyTracker tracker = new CopyTracker();
        assertFalse(tracker.claimAttempt(OWNER, 10_000L));
        assertFalse(tracker.claimAttempt(OWNER, 11_000L));
        HorrorDirector.onPlayerDisconnect(OWNER);
        tracker = reload(tracker);
        assertFalse(tracker.claimAttempt(OWNER, 12_000L));
        tracker = reload(tracker);
        assertFalse(tracker.claimAttempt(OWNER, 12_399L));
        assertTrue(tracker.claimAttempt(OWNER, 12_400L));
    }

    @Test
    void rejectedSiteRetriesAfterOneMinuteWithoutBurstingAfterLongAbsence() {
        CopyTracker tracker = new CopyTracker();
        assertFalse(tracker.claimAttempt(OWNER, 0L));
        assertTrue(tracker.claimAttempt(OWNER, 2_400L));
        tracker = reload(tracker);
        assertFalse(tracker.claimAttempt(OWNER, 3_599L));
        assertTrue(tracker.claimAttempt(OWNER, 3_600L));
        tracker = reload(tracker);
        assertTrue(tracker.claimAttempt(OWNER, 100_000L));
        assertFalse(tracker.claimAttempt(OWNER, 100_000L));
        JsonObject saved = CopyTracker.CODEC.encodeStart(JsonOps.INSTANCE, tracker)
                .getOrThrow().getAsJsonObject();
        assertEquals(3, saved.getAsJsonObject("attempts").getAsJsonObject(OWNER.toString())
                .get("tries").getAsInt());
    }

    @Test
    void successfulBuildKeepsThreeDayCooldownIncludingAtWorldTickZero() {
        CopyTracker tracker = new CopyTracker();
        tracker.noteCopyBuilt(OWNER, 0L);
        tracker = reload(tracker);
        assertFalse(tracker.cooldownElapsed(OWNER, 0L));
        assertFalse(tracker.cooldownElapsed(OWNER, 71_999L));
        assertTrue(tracker.cooldownElapsed(OWNER, 72_000L));
        assertTrue(tracker.claimAttempt(OWNER, 72_000L));
        assertTrue(tracker.cooldownElapsed(OTHER, 0L));
    }

    @Test
    void playerSchedulesAndAdministrativeResetRemainIndependent() {
        CopyTracker tracker = new CopyTracker();
        assertFalse(tracker.claimAttempt(OWNER, 1_000L));
        assertFalse(tracker.claimAttempt(OTHER, 2_000L));
        tracker = reload(tracker);
        tracker.resetAttempts(OWNER);
        assertFalse(tracker.claimAttempt(OWNER, 3_400L));
        assertFalse(tracker.claimAttempt(OTHER, 4_399L));
        assertTrue(tracker.claimAttempt(OTHER, 4_400L));
        assertFalse(tracker.claimAttempt(OWNER, 5_799L));
        assertTrue(tracker.claimAttempt(OWNER, 5_800L));
    }

    @Test
    void legacyCooldownSurvivesAndMissingCalendarGetsOnlyOneInitialWait() {
        JsonObject saved = new JsonObject();
        JsonObject cooldowns = new JsonObject();
        cooldowns.addProperty(OWNER.toString(), 1_000L);
        saved.add("cooldowns", cooldowns);
        CopyTracker tracker = CopyTracker.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
        assertFalse(tracker.cooldownElapsed(OWNER, 72_999L));
        assertTrue(tracker.cooldownElapsed(OWNER, 73_000L));
        assertFalse(tracker.claimAttempt(OWNER, 73_000L));
        tracker = reload(tracker);
        assertTrue(tracker.claimAttempt(OWNER, 75_400L));
    }

    @Test
    void rolledBackOrExtremeClockCannotCreateUnboundedWaitOrRepeatedAttempts() {
        CopyTracker tracker = new CopyTracker();
        assertFalse(tracker.claimAttempt(OWNER, 10_000L));
        assertFalse(tracker.claimAttempt(OWNER, 11_000L));
        tracker = reload(tracker);
        assertFalse(tracker.claimAttempt(OWNER, 100L));
        assertFalse(tracker.claimAttempt(OWNER, 1_499L));
        assertTrue(tracker.claimAttempt(OWNER, 1_500L));
        assertFalse(tracker.claimAttempt(OTHER, Long.MAX_VALUE - 10L));
        assertFalse(tracker.claimAttempt(OTHER, Long.MAX_VALUE));
        tracker = reload(tracker);
        assertFalse(tracker.claimAttempt(OTHER, 0L));
        assertTrue(tracker.claimAttempt(OTHER, 2_390L));
    }

    @Test
    void serverWithMoreThan128PlayersDoesNotForgetSavedBuildCooldowns() {
        CopyTracker tracker = new CopyTracker();
        for (int i = 0; i < 150; i++) tracker.noteCopyBuilt(new UUID(0L, i), 1_000L);
        tracker = reload(tracker);
        for (int i = 0; i < 150; i++) {
            assertFalse(tracker.cooldownElapsed(new UUID(0L, i), 2_000L));
        }
    }

    private static CopyTracker reload(CopyTracker tracker) {
        var saved = CopyTracker.CODEC.encodeStart(NbtOps.INSTANCE, tracker).getOrThrow();
        return CopyTracker.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
    }
}

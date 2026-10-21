package fr.penumbra.director;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirectorMemoryTest {

    @Test
    void codecRoundTripPreservesArcPacingAndPendingConsequence() {
        DirectorMemory memory = DirectorMemory.fresh(1_000L, 800L)
                .withArc(new DirectorMemory.ArcState(
                        DirectorMemory.ArcTheme.REFUGE, DirectorMemory.ArcPhase.BUILD,
                        900L, 980L, 2_000L, 1_100L, 2_400L, 3, false))
                .recordDirected(1_020L, "door_knocks", "refuge", true, false)
                .scheduleConsequence(new DirectorMemory.PendingConsequence(
                        "false_calm", 1_600L, 2_500L, "crisis", 0));

        var encoded = DirectorMemory.CODEC.encodeStart(JsonOps.INSTANCE, memory).getOrThrow();
        DirectorMemory decoded = DirectorMemory.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();

        assertEquals(DirectorMemory.ArcTheme.REFUGE, decoded.arc().theme());
        assertEquals(DirectorMemory.ArcPhase.BUILD, decoded.arc().phase());
        assertEquals(1_020L, decoded.pacing().lastDirectedTick());
        assertEquals(1, decoded.pacing().audioStreak());
        assertEquals(1, decoded.pending().size());
    }

    @Test
    void missingFieldsDecodeToIndependentSafeDefaults() {
        DirectorMemory first = DirectorMemory.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow();
        DirectorMemory second = DirectorMemory.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow();

        DirectorMemory changed = first.scheduleConsequence(new DirectorMemory.PendingConsequence(
                "false_calm", 10L, 20L, "test", 0));
        assertEquals(1, changed.pending().size());
        assertTrue(second.pending().isEmpty());
    }

    @Test
    void malformedCollectionsAreBoundedAndSanitized() {
        Map<String, Long> cooldowns = new HashMap<>();
        for (int index = 0; index < 140; index++) {
            cooldowns.put("event_" + index, (long) index);
        }
        List<String> families = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            families.add("family_" + index);
        }
        DirectorMemory.PacingState pacing = new DirectorMemory.PacingState(
                0L, 0L, -1L, 99, 1L, cooldowns, families, false);

        assertEquals(DirectorMemory.MAX_EVENT_COOLDOWNS, pacing.eventCooldowns().size());
        assertEquals(DirectorMemory.MAX_RECENT_FAMILIES, pacing.recentFamilies().size());
        assertEquals(16, pacing.audioStreak());
        assertFalse(pacing.recentFamilies().contains("family_0"));
    }

    @Test
    void clockRollbackKeepsRemainingDurations() {
        DirectorMemory memory = DirectorMemory.fresh(10_000L, 9_500L)
                .withArc(new DirectorMemory.ArcState(
                        DirectorMemory.ArcTheme.CAVE, DirectorMemory.ArcPhase.MANIFEST,
                        9_000L, 9_800L, 11_000L, 10_200L, 10_400L, 2, false));

        DirectorMemory rebased = memory.rebaseClock(1_000L);
        assertEquals(2_000L, rebased.arc().phaseEndsTick());
        assertEquals(1_400L, rebased.arc().hardManifestationTick());
        assertEquals(1_000L, rebased.pacing().lastClockTick());
    }
}

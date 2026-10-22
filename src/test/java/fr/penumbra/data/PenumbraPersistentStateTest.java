package fr.penumbra.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.NbtOps;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PenumbraPersistentStateTest {
    @Test
    void allPersonalAndWorldFlagsSurviveRepeatedNbtReloadsBeyondTheOldLimit() {
        var state = new PenumbraPersistentState();
        List<String> expected = new ArrayList<>();
        expected.add("signal_pyre_built");
        for (int i = 0; i < 400; i++) {
            UUID player = new UUID(0L, i);
            expected.add("confrontation_done:" + player);
            expected.add("journal_received:" + player);
            expected.add("bed_shift:" + player);
        }
        expected.forEach(state::markOneShot);
        for (int cycle = 0; cycle < 3; cycle++) {
            state = reload(state);
            for (String key : expected) assertTrue(state.isOneShotUsed(key), key);
        }
    }

    @Test
    void legacyFlagListRetainsVictoriesAfterThe256thEntryRegardlessOfOrdering() {
        JsonObject legacy = legacyState();
        JsonArray flags = new JsonArray();
        for (int i = 0; i < 300; i++) flags.add("event:" + i);
        UUID winner = new UUID(4L, 2L);
        String victory = "confrontation_done:" + winner;
        flags.add(victory);
        legacy.add("oneShots", flags);
        var state = PenumbraPersistentState.CODEC.parse(JsonOps.INSTANCE, legacy).getOrThrow();
        assertTrue(reload(state).isOneShotUsed(victory));
    }

    @Test
    void onlyAnActualPersonalVictoryCounterRepairsAMissingFlag() {
        var state = new PenumbraPersistentState();
        UUID winner = new UUID(0L, 1L);
        UUID newcomer = new UUID(0L, 2L);
        state.advanceStoryStage(6);
        assertFalse(state.hasConfrontationVictory(winner, 0), "The final stage is not a victory");
        assertFalse(state.hasConfrontationVictory(winner, -1));
        assertTrue(state.hasConfrontationVictory(winner, 1));
        state = reload(state);
        assertTrue(state.hasConfrontationVictory(winner, 0), "The repaired flag is permanent");
        assertFalse(state.hasConfrontationVictory(newcomer, 0));
        assertTrue(state.hasConfrontationVictory(newcomer, 3));
    }

    @Test
    void oldSavesWithoutFlagsStayEmptyAndInvalidIdentifiersNeverEnterStorage() {
        var state = PenumbraPersistentState.CODEC.parse(JsonOps.INSTANCE, legacyState()).getOrThrow();
        state.markOneShot(null);
        state.markOneShot(" ");
        state.markOneShot("x".repeat(97));
        state.markOneShot("valid");
        assertFalse(state.isOneShotUsed(null));
        assertFalse(state.isOneShotUsed(" "));
        assertFalse(state.isOneShotUsed("x".repeat(97)));
        assertTrue(reload(state).isOneShotUsed("valid"));
    }

    private static JsonObject legacyState() {
        JsonObject legacy = new JsonObject();
        legacy.add("profiles", new JsonObject());
        legacy.addProperty("storyStage", 0);
        legacy.addProperty("worldSeedMirror", 0L);
        return legacy;
    }

    private static PenumbraPersistentState reload(PenumbraPersistentState state) {
        var encoded = PenumbraPersistentState.CODEC.encodeStart(NbtOps.INSTANCE, state).getOrThrow();
        return PenumbraPersistentState.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
    }
}

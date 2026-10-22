package fr.penumbra.story;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.NbtOps;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static fr.penumbra.story.StoryManager.*;
import static org.junit.jupiter.api.Assertions.*;

class StoryProgressionTest {
    private static final UUID OWNER = new UUID(0L, 230L);
    private static final UUID NEWCOMER = new UUID(0L, 231L);

    @Test
    void freshEvidenceUnlocksTheDuelButThreeSeparateWindowsAreStillNeededToWin() {
        PlayerStory story = familiarPlayer();
        story.discover(KEY_COPY_BUILT, true);
        assertEquals(4, story.stage());
        for (int i = 0; i < 3; i++) story.discover(KEY_HOST_OBSERVED, true);
        assertEquals(4, story.stage());
        story.discover(KEY_HOST_OBSERVED, true);
        assertEquals(5, story.stage());
        story.discover(KEY_SANCTUM_RITUAL, true);
        assertEquals(6, story.stage());
        assertEquals(0, story.count("confrontation"), "Unlocking the finale is not a victory");

        ConfrontationSequence duel = new ConfrontationSequence();
        assertFalse(duel.flash(true));
        duel.advance(ConfrontationSequence.ARRIVAL_TICKS, true);
        for (int fragment = 0; fragment < 3; fragment++) {
            duel.advance(duel.requiredFocusTicks(), true);
            assertTrue(duel.flash(true));
            assertFalse(duel.flash(true), "One shutter window cannot yield two fragments");
            if (fragment < 2) {
                assertNotEquals(ConfrontationSequence.Phase.COMPLETE, duel.phase());
                duel.advance(ConfrontationSequence.RECOVERY_TICKS, true);
            }
        }
        assertEquals(ConfrontationSequence.Phase.COMPLETE, duel.phase());
        story.discover("confrontation", true);
        assertEquals(6, story.stage());
        assertEquals(1, story.count("confrontation"));
    }

    @Test
    void shiftedHouseOffersTheSamePersonalProgressWithoutBuildingACopy() {
        PlayerStory story = familiarPlayer();
        story.discover(KEY_STRUCTURE_SHIFTED_HOUSE, true);
        assertEquals(4, story.stage());
        assertEquals(0, story.count(KEY_COPY_BUILT));
    }

    @Test
    void earlierHouseDiscoveryRemainsUsefulAfterReloadButDoesNotSkipPrerequisites() {
        PlayerStory story = new PlayerStory();
        story.discover(KEY_STRUCTURE_SHIFTED_HOUSE, true);
        assertEquals(0, story.stage());
        story = reload(story);
        reachFamiliarity(story);
        assertEquals(3, story.stage());
        story.discover(KEY_MIMIC_EVENT, true);
        assertEquals(4, story.stage());
    }

    @Test
    void earlyRitualCannotAdvanceRecognitionOnAnUnrelatedDiscovery() {
        JsonObject legacy = new JsonObject();
        legacy.addProperty("stage", 5);
        JsonObject counters = new JsonObject();
        counters.addProperty(KEY_SANCTUM_RITUAL, 1);
        legacy.add("counters", counters);
        PlayerStory story = PlayerStory.CODEC.parse(JsonOps.INSTANCE, legacy).getOrThrow();
        story.discover(KEY_HOST_OBSERVED, true);
        story.discover(KEY_STRUCTURE_SHIFTED_HOUSE, true);
        assertEquals(5, story.stage());
        story = reload(story);
        story.discover(KEY_SANCTUM_RITUAL, true);
        assertEquals(6, story.stage());
        assertEquals(2, story.count(KEY_SANCTUM_RITUAL), "The earlier ritual stays in the history");
    }

    @Test
    void aNewPlayerOnAnExistingWorldDoesNotInheritStagesOrEvidence() {
        StoryManager world = new StoryManager();
        reachFamiliarity(world.story(OWNER));
        world.story(OWNER).discover(KEY_STRUCTURE_SHIFTED_HOUSE, true);
        world = reload(world);
        assertEquals(4, world.story(OWNER).stage());
        assertEquals(0, world.story(NEWCOMER).stage());
        assertTrue(world.story(NEWCOMER).counters().isEmpty());
        world.story(NEWCOMER).discover(KEY_STRUCTURE_OBSERVATION_POST, true);
        world = reload(world);
        assertEquals(1, world.story(NEWCOMER).stage());
        assertEquals(4, world.story(OWNER).stage());
        assertEquals(0, world.story(NEWCOMER).count(KEY_STRUCTURE_SHIFTED_HOUSE));
    }

    @Test
    void disabledProgressionKeepsEvidenceAndLegacyMissingFieldsStartEmpty() {
        PlayerStory story = PlayerStory.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow();
        story.discover(KEY_FIRST_ANOMALY, false);
        story.discover(KEY_MIMIC_EVENT, false);
        story = reload(story);
        assertEquals(0, story.stage());
        assertEquals(1, story.count(KEY_MIMIC_EVENT));
        story.discover(KEY_MIMIC_EVENT, true);
        assertEquals(1, story.stage(), "Re-enabling advances at most one stage per discovery");
        story.discover(KEY_MIMIC_EVENT, true);
        assertEquals(2, story.stage());
    }

    @Test
    void unknownHistoricalCountersSurviveAndLargeCountsCannotOverflow() {
        JsonObject legacy = new JsonObject();
        legacy.addProperty("stage", 1);
        JsonObject counters = new JsonObject();
        counters.addProperty("older_story_clue", 7);
        counters.addProperty(KEY_MIMIC_EVENT, Integer.MAX_VALUE);
        legacy.add("counters", counters);
        PlayerStory story = PlayerStory.CODEC.parse(JsonOps.INSTANCE, legacy).getOrThrow();
        story.discover(KEY_MIMIC_EVENT, true);
        story = reload(story);
        assertEquals(2, story.stage());
        assertEquals(Integer.MAX_VALUE, story.count(KEY_MIMIC_EVENT));
        assertEquals(7, story.count("older_story_clue"));
    }

    private static PlayerStory familiarPlayer() {
        PlayerStory story = new PlayerStory();
        reachFamiliarity(story);
        return story;
    }

    private static void reachFamiliarity(PlayerStory story) {
        story.discover(KEY_FIRST_ANOMALY, true);
        assertEquals(1, story.stage());
        for (int i = 0; i < 2; i++) story.discover(KEY_MIMIC_EVENT, true);
        assertEquals(1, story.stage());
        story.discover(KEY_MIMIC_EVENT, true);
        assertEquals(2, story.stage());
        story.discover(KEY_BED_USED, true);
        story.discover(KEY_BED_USED, true);
        assertEquals(2, story.stage(), "Two mornings alone do not establish the Host's familiarity");
        story.discover(KEY_REFUGE_HOST_SEEN, true);
        assertEquals(3, story.stage());
    }

    private static PlayerStory reload(PlayerStory story) {
        var saved = PlayerStory.CODEC.encodeStart(NbtOps.INSTANCE, story).getOrThrow();
        return PlayerStory.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
    }

    private static StoryManager reload(StoryManager world) {
        var saved = StoryManager.CODEC.encodeStart(NbtOps.INSTANCE, world).getOrThrow();
        return StoryManager.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
    }
}

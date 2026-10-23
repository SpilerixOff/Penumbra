package fr.penumbra.journal;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import fr.penumbra.story.StoryManager;
import fr.penumbra.structure.StructureTracker;
import fr.penumbra.structure.StructureType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Persistence, shared-site recovery and inventory outcome contracts, without a synthetic ServerPlayer. */
class InvestigationProgressRecoveryTest {
    private static final UUID FIRST = new UUID(0, 2701);
    private static final UUID LATE_JOINER = new UUID(0, 2702);

    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void previouslyCollectedTornPagesAreTheSameEvidenceAsTheNewBooksAfterReload() {
        JsonArray oldPages = new JsonArray();
        for (JournalEntry entry : InvestigationRules.VOLUMES) oldPages.add(entry.id());
        oldPages.add("method_camera");
        oldPages.add("removed_future_book");
        JsonObject players = new JsonObject();
        players.add(FIRST.toString(), oldPages);
        JsonObject legacy = new JsonObject();
        legacy.add("pages", players);
        JournalKnowledge knowledge = JournalKnowledge.CODEC.parse(JsonOps.INSTANCE, legacy).getOrThrow();
        knowledge = reload(knowledge);
        for (JournalEntry volume : InvestigationRules.VOLUMES) {
            assertTrue(knowledge.hasPage(FIRST, volume));
            assertFalse(knowledge.markPageFound(FIRST, volume));
            assertEquals(JournalEntry.State.KNOWN, knowledge.stateFor(FIRST).get(volume));
        }
        assertEquals(4, knowledge.progress(FIRST));
        assertFalse(InvestigationRules.ready(mask(knowledge, FIRST), false), "The book does not count as hearing its cassette");
        assertTrue(InvestigationRules.ready(mask(knowledge, FIRST), true));
    }

    @Test
    void outOfOrderBooksSurviveReconnectAndOnlyTheMissingPrefixControlsTheRoute() {
        JournalKnowledge knowledge = new JournalKnowledge();
        knowledge.markPageFound(FIRST, JournalEntry.LAST_PAGE);
        knowledge.markPageFound(FIRST, JournalEntry.METHOD_STILLNESS);
        knowledge = reload(knowledge);
        assertEquals(0, InvestigationRules.stageFloor(mask(knowledge, FIRST), true));
        assertEquals(StructureType.OBSERVATION_POST, InvestigationRules.destination(mask(knowledge, FIRST), true));
        knowledge.markPageFound(FIRST, JournalEntry.METHOD_CAMERA);
        knowledge = reload(knowledge);
        assertEquals(1, InvestigationRules.stageFloor(mask(knowledge, FIRST), true));
        assertEquals(StructureType.SHIFTED_HOUSE, InvestigationRules.destination(mask(knowledge, FIRST), true));
        knowledge.markPageFound(FIRST, JournalEntry.METHOD_REFUGE);
        knowledge = reload(knowledge);
        assertEquals(5, InvestigationRules.stageFloor(mask(knowledge, FIRST), true));
    }

    @Test
    void aLootedArchiveStillGivesEachPlayerIndependentEvidenceAfterReconnect() {
        BlockPos archive = new BlockPos(300, 64, -500);
        List<StructureTracker.Entry> structures = List.of(new StructureTracker.Entry(
                StructureType.SHIFTED_HOUSE.id(), archive, Level.OVERWORLD, 0, false));
        JournalKnowledge knowledge = new JournalKnowledge();
        for (JournalEntry volume : InvestigationRules.VOLUMES) knowledge.markPageFound(FIRST, volume);
        knowledge = reload(knowledge);
        assertEquals(0, mask(knowledge, LATE_JOINER));
        assertTrue(StructurePageDiscovery.recoverPage(knowledge, LATE_JOINER, structures,
                Level.OVERWORLD, archive).isPresent());
        knowledge = reload(knowledge);
        assertEquals(15, mask(knowledge, FIRST));
        assertEquals(2, mask(knowledge, LATE_JOINER));
        assertFalse(InvestigationRules.ready(mask(knowledge, LATE_JOINER), true));
        assertTrue(StructurePageDiscovery.recoverPage(knowledge, LATE_JOINER, structures,
                Level.OVERWORLD, archive).isEmpty(), "Repeating the interaction cannot add duplicate knowledge");
    }

    @Test
    void losingThePhysicalItemsCannotErasePersistedEvidenceOrRevealUnseenCreatures() {
        JournalKnowledge knowledge = new JournalKnowledge();
        for (JournalEntry volume : InvestigationRules.VOLUMES) knowledge.markPageFound(FIRST, volume);
        // Knowledge is saved independently of player inventory, chest loot and the physical journal.
        knowledge = reload(knowledge);
        assertEquals(15, mask(knowledge, FIRST));
        assertEquals(4, knowledge.progress(FIRST));
        assertEquals(JournalEntry.State.FADED, knowledge.stateFor(FIRST).get(JournalEntry.ABSENT));
        assertEquals(JournalEntry.State.TORN, knowledge.stateFor(LATE_JOINER).get(JournalEntry.LAST_PAGE));
    }

    @Test
    void hearingAndMigrationFlagsStayPerPlayerAndSeparateFromPageKnowledge() {
        JsonObject firstCounters = new JsonObject();
        firstCounters.addProperty(InvestigationProgress.STARTED, 1);
        firstCounters.addProperty(InvestigationProgress.TAPE_HEARD, 1);
        firstCounters.addProperty(InvestigationProgress.LEGACY_FINALE, 1);
        JsonObject firstStory = new JsonObject();
        firstStory.addProperty("stage", 6);
        firstStory.add("counters", firstCounters);
        JsonObject stories = new JsonObject();
        stories.add(FIRST.toString(), firstStory);
        stories.add(LATE_JOINER.toString(), new JsonObject());
        JsonObject saved = new JsonObject();
        saved.add("players", stories);
        StoryManager state = StoryManager.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
        state = StoryManager.CODEC.parse(NbtOps.INSTANCE,
                StoryManager.CODEC.encodeStart(NbtOps.INSTANCE, state).getOrThrow()).getOrThrow();
        JsonObject reloaded = StoryManager.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow().getAsJsonObject()
                .getAsJsonObject("players");
        JsonObject first = reloaded.getAsJsonObject(FIRST.toString());
        assertEquals(6, first.get("stage").getAsInt());
        assertEquals(1, first.getAsJsonObject("counters").get(InvestigationProgress.TAPE_HEARD).getAsInt());
        assertEquals(1, first.getAsJsonObject("counters").get(InvestigationProgress.LEGACY_FINALE).getAsInt());
        JsonObject late = reloaded.getAsJsonObject(LATE_JOINER.toString());
        assertFalse(late.has("counters") && late.getAsJsonObject("counters").has(InvestigationProgress.TAPE_HEARD));
        assertEquals(0, mask(reload(new JournalKnowledge()), FIRST),
                "A hearing or migration flag does not fabricate book discoveries");
    }

    @Test
    void aFullInventoryOrCreativeDiscardCannotConsumeTheRecoveryEntitlement() {
        AtomicBoolean present = new AtomicBoolean(false);
        AtomicInteger attempts = new AtomicInteger();
        assertFalse(InvestigationProgress.ensureDelivered(present::get, attempts::incrementAndGet),
                "Insertion may return without retaining an item, including creative discard");
        assertEquals(1, attempts.get());
        assertTrue(InvestigationProgress.ensureDelivered(present::get, () -> {
            attempts.incrementAndGet();
            present.set(true);
        }), "Making room permits the next recovery attempt");
        assertEquals(2, attempts.get());
    }

    @Test
    void alreadyHeldVolumesAreNotDuplicatedButALostVolumeCanBeRecoveredAgain() {
        AtomicBoolean present = new AtomicBoolean(true);
        AtomicInteger copies = new AtomicInteger();
        assertTrue(InvestigationProgress.ensureDelivered(present::get,
                () -> fail("Already owned evidence must not be inserted again")));
        present.set(false);
        assertTrue(InvestigationProgress.ensureDelivered(present::get, () -> {
            copies.incrementAndGet();
            present.set(true);
        }));
        assertTrue(InvestigationProgress.ensureDelivered(present::get,
                () -> fail("Reopening the journal must not add another copy")));
        assertEquals(1, copies.get());
    }

    @Test
    void deliveryFailureDoesNotEraseLearnedEvidenceOrGrantItToAnotherPlayer() {
        JournalKnowledge knowledge = new JournalKnowledge();
        knowledge.markPageFound(FIRST, JournalEntry.LAST_PAGE);
        assertFalse(InvestigationProgress.ensureDelivered(() -> false, () -> { }));
        knowledge = reload(knowledge);
        assertTrue(knowledge.hasPage(FIRST, JournalEntry.LAST_PAGE));
        assertEquals(JournalEntry.State.KNOWN, knowledge.stateFor(FIRST).get(JournalEntry.LAST_PAGE));
        assertFalse(knowledge.hasPage(LATE_JOINER, JournalEntry.LAST_PAGE));
        assertEquals(0, InvestigationRules.stageFloor(mask(knowledge, FIRST), false));
    }

    private static int mask(JournalKnowledge knowledge, UUID player) {
        int result = 0;
        for (int index = 0; index < InvestigationRules.VOLUMES.size(); index++) {
            if (knowledge.hasPage(player, InvestigationRules.VOLUMES.get(index))) result |= 1 << index;
        }
        return result;
    }

    private static JournalKnowledge reload(JournalKnowledge knowledge) {
        return JournalKnowledge.CODEC.parse(NbtOps.INSTANCE,
                JournalKnowledge.CODEC.encodeStart(NbtOps.INSTANCE, knowledge).getOrThrow()).getOrThrow();
    }
}

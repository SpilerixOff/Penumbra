package fr.penumbra.journal;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.NbtOps;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JournalKnowledgeTest {
    @Test
    void moreThan256InvestigatorsKeepTheirPagesAndSightingsDuringPlayAndAfterReload() {
        var knowledge = new JournalKnowledge();
        for (int i = 0; i < 300; i++) {
            UUID id = new UUID(0L, i);
            assertTrue(knowledge.markSeen(id, JournalEntry.HOST));
            assertTrue(knowledge.markPageFound(id, JournalEntry.METHOD_CAMERA));
        }
        for (int reload = 0; reload < 3; reload++) {
            for (int i = 0; i < 300; i++) {
                UUID id = new UUID(0L, i);
                assertEquals(JournalEntry.State.KNOWN, knowledge.stateFor(id).get(JournalEntry.HOST));
                assertTrue(knowledge.hasPage(id, JournalEntry.METHOD_CAMERA));
                assertFalse(knowledge.markPageFound(id, JournalEntry.METHOD_CAMERA));
                assertEquals(2, knowledge.progress(id));
                assertFalse(knowledge.hasPage(id, JournalEntry.METHOD_STILLNESS));
            }
            var encoded = JournalKnowledge.CODEC.encodeStart(NbtOps.INSTANCE, knowledge).getOrThrow();
            knowledge = JournalKnowledge.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
        }
    }

    @Test
    void legacyStorageLoadsEveryInvestigatorAndStillDeduplicatesKnownIds() {
        var seen = new JsonObject();
        var pages = new JsonObject();
        for (int i = 0; i < 300; i++) {
            var sightings = new JsonArray();
            sightings.add("host");
            sightings.add("host");
            sightings.add("unknown_future_creature");
            var found = new JsonArray();
            found.add("method_refuge");
            found.add("method_refuge");
            found.add("unknown_future_page");
            String id = new UUID(0L, i).toString();
            seen.add(id, sightings);
            pages.add(id, found);
        }
        var legacy = new JsonObject();
        legacy.add("seen", seen);
        legacy.add("pages", pages);
        var knowledge = JournalKnowledge.CODEC.parse(JsonOps.INSTANCE, legacy).getOrThrow();
        for (int i = 0; i < 300; i++) {
            UUID id = new UUID(0L, i);
            assertEquals(2, knowledge.progress(id));
            assertTrue(knowledge.hasPage(id, JournalEntry.METHOD_REFUGE));
        }
    }
}

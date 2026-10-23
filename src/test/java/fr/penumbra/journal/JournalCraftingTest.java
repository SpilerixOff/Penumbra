package fr.penumbra.journal;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class JournalCraftingTest {
    @Test
    void basicPlansAreAlwaysReadableAndEachBookAddsItsOwnPlan() {
        assertEquals(List.of("survey_compass", "survey_journal"), ids(Set.of()));
        assertEquals(List.of("survey_compass", "survey_journal", "binoculars"),
                ids(Set.of("method_camera")));
        assertEquals(List.of("survey_compass", "survey_journal", "ash_ward"),
                ids(Set.of("method_refuge")));
        assertEquals(List.of("survey_compass", "survey_journal", "ash_sigil"),
                ids(Set.of("method_stillness")));
        assertEquals(5, ids(Set.of("method_camera", "method_refuge", "method_stillness")).size());
        assertEquals(ids(Set.of()), ids(Set.of("last_page", "host", "invalid")));
    }

    @Test
    void realCameraPatternKeepsEmptySlotsAndCountsRepeatedIngredients() {
        JournalCrafting.Recipe camera = JournalCrafting.available(Set.of("method_camera")).getLast();
        assertFalse(camera.shapeless());
        assertEquals("penumbra:binoculars", camera.resultId());
        assertEquals(1, camera.resultCount());
        assertEquals(List.of("minecraft:glass_pane", "", "minecraft:glass_pane",
                "minecraft:iron_ingot", "minecraft:leather", "minecraft:iron_ingot",
                "minecraft:glass_pane", "", "minecraft:glass_pane"), camera.slots());
        assertEquals(List.of(new JournalCrafting.Ingredient("minecraft:glass_pane", 4),
                new JournalCrafting.Ingredient("minecraft:iron_ingot", 2),
                new JournalCrafting.Ingredient("minecraft:leather", 1)), camera.ingredients());
    }

    @Test
    void realShapelessRecipesRetainTheirOutputCountAndNeverInventIngredients() {
        JournalCrafting.Recipe ward = JournalCrafting.available(Set.of("method_refuge")).getLast();
        assertTrue(ward.shapeless());
        assertEquals(2, ward.resultCount());
        assertEquals("penumbra:ash_ward", ward.resultId());
        assertEquals(List.of(new JournalCrafting.Ingredient("minecraft:paper", 1),
                new JournalCrafting.Ingredient("minecraft:charcoal", 1),
                new JournalCrafting.Ingredient("minecraft:redstone", 1)), ward.ingredients());
        assertEquals(6, ward.slots().stream().filter(String::isEmpty).count());
    }

    @Test
    void ritualPlanReadsTheNewCharcoalCrossAroundAmethyst() {
        JournalCrafting.Recipe sigil = JournalCrafting.available(Set.of("method_stillness")).getLast();
        assertFalse(sigil.shapeless());
        assertEquals("penumbra:ash_sigil", sigil.resultId());
        assertEquals(1, sigil.resultCount());
        assertEquals(List.of("", "minecraft:charcoal", "", "minecraft:charcoal",
                "minecraft:amethyst_shard", "minecraft:charcoal", "", "minecraft:charcoal", ""),
                sigil.slots());
        assertEquals(List.of(new JournalCrafting.Ingredient("minecraft:charcoal", 4),
                new JournalCrafting.Ingredient("minecraft:amethyst_shard", 1)), sigil.ingredients());
    }

    @Test
    void smallerShapedRecipesRespectRowsAndUnspecifiedCountIsOne() {
        JournalCrafting.Recipe recipe = JournalCrafting.parse("example", "", JsonParser.parseString("""
                {"type":"minecraft:crafting_shaped", "pattern":["AA"," A"],
                 "key":{"A":"minecraft:paper"},"result":{"id":"minecraft:book"}}
                """).getAsJsonObject());
        assertEquals(List.of("minecraft:paper", "minecraft:paper", "", "", "minecraft:paper",
                "", "", "", ""), recipe.slots());
        assertEquals(1, recipe.resultCount());
        assertEquals(List.of(new JournalCrafting.Ingredient("minecraft:paper", 3)), recipe.ingredients());
        assertThrows(UnsupportedOperationException.class, () -> recipe.slots().clear());
    }

    @Test
    void invalidOrUnsupportedPatternsFailInsteadOfShowingAnIncorrectCraft() {
        for (String json : List.of(
                """
                        {"type":"minecraft:smelting"}
                        """,
                """
                        {"type":"minecraft:crafting_shapeless","ingredients":[]}
                        """,
                """
                        {"type":"minecraft:crafting_shapeless","ingredients":["#minecraft:logs"]}
                        """,
                """
                        {"type":"minecraft:crafting_shaped","pattern":["AAAA"],"key":{}}
                        """,
                """
                        {"type":"minecraft:crafting_shaped","pattern":["AA","A"],"key":{"A":"minecraft:paper"}}
                        """)) {
            assertThrows(IllegalArgumentException.class, () -> JournalCrafting.parse("bad", "",
                    JsonParser.parseString(json).getAsJsonObject()));
        }
    }

    private static List<String> ids(Set<String> readable) {
        return JournalCrafting.available(readable).stream().map(JournalCrafting.Recipe::id).toList();
    }
}

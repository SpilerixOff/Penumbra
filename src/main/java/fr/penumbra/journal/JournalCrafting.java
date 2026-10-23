package fr.penumbra.journal;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Base-game recipes printed in the journal, read from the very same bundled JSON as crafting. */
public final class JournalCrafting {
    private JournalCrafting() {
    }

    public record Ingredient(String itemId, int count) {
    }

    public record Recipe(String id, String requiredEntry, List<String> slots,
                         String resultId, int resultCount, boolean shapeless) {
        public Recipe {
            slots = List.copyOf(slots);
            if (slots.size() != 9 || resultCount < 1) {
                throw new IllegalArgumentException("A journal recipe needs nine slots and an output");
            }
        }

        public List<Ingredient> ingredients() {
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (String item : slots) {
                if (!item.isEmpty()) {
                    counts.merge(item, 1, Integer::sum);
                }
            }
            return counts.entrySet().stream()
                    .map(entry -> new Ingredient(entry.getKey(), entry.getValue())).toList();
        }

        public String useKey() {
            return "screen.penumbra.journal.crafting.use." + id;
        }
    }

    private static final class Recipes {
        private static final List<Recipe> ALL = List.of(
                read("survey_compass", ""),
                read("survey_journal", ""),
                read("binoculars", "method_camera"),
                read("ash_ward", "method_refuge"),
                read("ash_sigil", "method_stillness"));
    }

    public static List<Recipe> available(Set<String> readableIds) {
        return Recipes.ALL.stream().filter(recipe -> recipe.requiredEntry().isEmpty()
                || readableIds.contains(recipe.requiredEntry())).toList();
    }

    /** Bundled plans share their source with crafting; server data-pack overrides are not included. */
    private static Recipe read(String id, String requiredEntry) {
        String path = "/data/penumbra/recipe/" + id + ".json";
        try (InputStream stream = JournalCrafting.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing journal recipe " + path);
            }
            return parse(id, requiredEntry, JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (IOException error) {
            throw new IllegalStateException("Cannot read journal recipe " + path, error);
        }
    }

    static Recipe parse(String id, String requiredEntry, JsonObject json) {
        List<String> slots = new ArrayList<>(Collections.nCopies(9, ""));
        String type = json.get("type").getAsString();
        boolean shapeless = "minecraft:crafting_shapeless".equals(type);
        if (shapeless) {
            JsonArray ingredients = json.getAsJsonArray("ingredients");
            if (ingredients.isEmpty() || ingredients.size() > 9) {
                throw new IllegalArgumentException("Invalid shapeless journal recipe " + id);
            }
            for (int index = 0; index < ingredients.size(); index++) {
                slots.set(index, itemId(ingredients.get(index)));
            }
        } else if ("minecraft:crafting_shaped".equals(type)) {
            JsonArray pattern = json.getAsJsonArray("pattern");
            JsonObject key = json.getAsJsonObject("key");
            if (pattern.isEmpty() || pattern.size() > 3) {
                throw new IllegalArgumentException("Invalid shaped journal recipe " + id);
            }
            int rowWidth = pattern.get(0).getAsString().length();
            for (int row = 0; row < pattern.size(); row++) {
                String line = pattern.get(row).getAsString();
                if (rowWidth < 1 || rowWidth > 3 || line.length() != rowWidth) {
                    throw new IllegalArgumentException("Invalid journal pattern " + id);
                }
                for (int column = 0; column < line.length(); column++) {
                    char symbol = line.charAt(column);
                    if (symbol != ' ') {
                        slots.set(row * 3 + column, itemId(key.get(Character.toString(symbol))));
                    }
                }
            }
        } else {
            throw new IllegalArgumentException("Unsupported journal recipe " + type);
        }
        JsonObject result = json.getAsJsonObject("result");
        return new Recipe(id, requiredEntry, slots, itemId(result.get("id")),
                result.has("count") ? result.get("count").getAsInt() : 1, shapeless);
    }

    private static String itemId(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Journal recipes require an explicit item id");
        }
        String item = value.getAsString();
        if (!item.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("Invalid journal item " + item);
        }
        return item;
    }
}

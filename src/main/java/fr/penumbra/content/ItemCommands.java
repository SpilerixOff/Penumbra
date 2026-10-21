package fr.penumbra.content;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.penumbra.registry.PenumbraItems;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sous-arbre {@code /penumbra item} (permission niveau 2).
 * <p>{@code /penumbra item give <id>} donne un exemplaire de l'objet demandé
 * au joueur exécutant et confirme avec la clé {@code penumbra.item.given}.
 * Suggestions : les six objets narratifs plus {@code ash_sigil} et
 * {@code binoculars} et {@code hammer}. Le message d'erreur sur identifiant inconnu reste un
 * littéral (précédent {@code PenumbraCommands}).</p>
 */
public final class ItemCommands {

    /** Objets donnables, dans l'ordre de suggestion (les 6 narratifs d'abord). */
    private static final Map<String, Item> GIVEABLE = buildGiveable();

    /** Ids proposés, dans le même ordre que {@link #GIVEABLE}. */
    private static final List<String> GIVEABLE_IDS = List.copyOf(GIVEABLE.keySet());

    /** Suggestions des identifiants d'objets. */
    private static final SuggestionProvider<CommandSourceStack> ITEM_ID_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(GIVEABLE_IDS, builder);

    private ItemCommands() {
    }

    /**
     * Greffe la branche {@code item give <id>} sur la racine {@code /penumbra}
     * fournie par l'intégrateur ({@code PenumbraCommands}). La permission
     * niveau 2 est re-exigée ici pour que la branche reste protégée quelle
     * que soit la racine reçue.
     */
    public static void attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("item")
                .requires(source -> source.permissions()
                        .hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS)))
                .then(Commands.literal("give")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(ITEM_ID_SUGGESTIONS)
                                .executes(ItemCommands::give))));
    }

    /** /penumbra item give <id> : donne 1 exemplaire au joueur exécutant. */
    private static int give(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        String id = StringArgumentType.getString(context, "id");
        Item item = GIVEABLE.get(id);
        if (item == null) {
            source.sendFailure(Component.literal("Objet Penumbra inconnu : " + id
                    + " (attendu : " + String.join(", ", GIVEABLE_IDS) + ")"));
            return 0;
        }
        ItemStack stack = new ItemStack(item);
        // Comportement vanilla de /give : inventaire, sinon dépôt aux pieds.
        player.getInventory().placeItemBackInInventory(stack);
        source.sendSystemMessage(Component.translatable("penumbra.item.given", stack.getHoverName()));
        return 1;
    }

    /** Table ordonnée id → objet pour les six narratifs et les objets utilisables. */
    private static Map<String, Item> buildGiveable() {
        Map<String, Item> giveable = new LinkedHashMap<>();
        giveable.put("cracked_lens", NarrativeItems.CRACKED_LENS);
        giveable.put("incomplete_journal", NarrativeItems.INCOMPLETE_JOURNAL);
        giveable.put("observation_fragment", NarrativeItems.OBSERVATION_FRAGMENT);
        giveable.put("blackened_plumb_bob", NarrativeItems.BLACKENED_PLUMB_BOB);
        giveable.put("impossible_photo", NarrativeItems.IMPOSSIBLE_PHOTO);
        giveable.put("silhouette_glass", NarrativeItems.SILHOUETTE_GLASS);
        giveable.put("ash_sigil", PenumbraItems.ASH_SIGIL);
        giveable.put("ash_ward", PenumbraItems.ASH_WARD);
        giveable.put("binoculars", PenumbraItems.BINOCULARS);
        giveable.put("stone_hammer", PenumbraItems.STONE_HAMMER);
        giveable.put("hammer", PenumbraItems.HAMMER);
        giveable.put("golden_hammer", PenumbraItems.GOLDEN_HAMMER);
        giveable.put("diamond_hammer", PenumbraItems.DIAMOND_HAMMER);
        return java.util.Collections.unmodifiableMap(giveable);
    }
}

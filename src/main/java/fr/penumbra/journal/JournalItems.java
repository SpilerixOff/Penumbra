package fr.penumbra.journal;

import com.mojang.serialization.Codec;
import fr.penumbra.PenumbraMod;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Registre du Relevé et des pages arrachées (0.18.0).
 *
 * <p>Même pattern d'enregistrement 26.1.2 que {@code NarrativeItems} :
 * {@code ResourceKey} posée via {@code Item.Properties.setId},
 * {@code Registry.register} manuel, ajout à l'onglet « Ingrédients ».</p>
 */
public final class JournalItems {

    /**
     * Identifiant de la page que porte une {@code torn_page}. Persistant et
     * synchronisé réseau pour que l'infobulle client puisse en montrer les
     * premiers mots sans dévoiler le contenu. Une valeur inconnue rend la page
     * inerte au lieu de casser l'objet.
     */
    public static final DataComponentType<String> PAGE_ID = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            PenumbraMod.id("page_id"),
            DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build());

    /** Identifiant du site que vise la Boussole d'arpenteur. */
    public static final DataComponentType<String> COMPASS_TARGET = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            PenumbraMod.id("compass_target"),
            DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build());

    /** Route choisie par le joueur ; les anciennes boussoles visent le camp. */
    public static final DataComponentType<Integer> COMPASS_MODE = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            PenumbraMod.id("compass_mode"),
            DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    /** Palier d'éloignement (0 à 4) affiché par la boussole, jamais un chiffre. */
    public static final DataComponentType<Integer> COMPASS_DISTANCE = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            PenumbraMod.id("compass_distance"),
            DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    private static final ResourceKey<Item> SURVEY_JOURNAL_KEY =
            ResourceKey.create(Registries.ITEM, PenumbraMod.id("survey_journal"));
    private static final ResourceKey<Item> TORN_PAGE_KEY =
            ResourceKey.create(Registries.ITEM, PenumbraMod.id("torn_page"));
    private static final ResourceKey<Item> SURVEY_COMPASS_KEY =
            ResourceKey.create(Registries.ITEM, PenumbraMod.id("survey_compass"));

    private static final ResourceKey<CreativeModeTab> INGREDIENTS_TAB =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB,
                    Identifier.withDefaultNamespace("ingredients"));

    /** Le Relevé : cuir gonflé d'humidité, un tirage qui dépasse. Unique. */
    public static final Item SURVEY_JOURNAL = Registry.register(
            BuiltInRegistries.ITEM,
            SURVEY_JOURNAL_KEY,
            new SurveyJournalItem(new Item.Properties()
                    .stacksTo(1)
                    .setId(SURVEY_JOURNAL_KEY)));

    /** Page arrachée : se glisse dans le Relevé au clic droit, puis disparaît. */
    public static final Item TORN_PAGE = Registry.register(
            BuiltInRegistries.ITEM,
            TORN_PAGE_KEY,
            new TornPageItem(new Item.Properties()
                    .stacksTo(1)
                    .setId(TORN_PAGE_KEY)));

    /**
     * Boussole d'arpenteur : choix entre le Bûcher, les sites inexplorés et le
     * site le plus proche. Fabricable — une boussole et un lingot de cuivre —
     * parce qu'elle sert justement à trouver ce qu'on ne trouve pas autrement.
     */
    public static final Item SURVEY_COMPASS = Registry.register(
            BuiltInRegistries.ITEM,
            SURVEY_COMPASS_KEY,
            new SurveyCompassItem(new Item.Properties()
                    .stacksTo(1)
                    .setId(SURVEY_COMPASS_KEY)));

    private JournalItems() {
    }

    /** Fabrique une page arrachée portant l'entrée demandée. */
    public static ItemStack pageStack(JournalEntry entry) {
        ItemStack stack = new ItemStack(TORN_PAGE);
        stack.set(PAGE_ID, entry.id());
        return stack;
    }

    /** Page portée par cette pile, ou {@code null} si l'objet est inerte. */
    public static JournalEntry pageOf(ItemStack stack) {
        String id = stack.getOrDefault(PAGE_ID, "");
        if (id.isEmpty()) {
            return null;
        }
        JournalEntry entry = JournalEntry.byId(id);
        return entry != null && entry.kind() == JournalEntry.Kind.METHOD ? entry : null;
    }

    /** Force le chargement statique et ajoute les deux objets à l'onglet. */
    public static void register() {
        CreativeModeTabEvents.modifyOutputEvent(INGREDIENTS_TAB)
                .register(entries -> {
                    entries.accept(SURVEY_JOURNAL);
                    entries.accept(SURVEY_COMPASS);
                    for (JournalEntry entry : JournalEntry.ORDER) {
                        if (entry.kind() == JournalEntry.Kind.METHOD) {
                            entries.accept(pageStack(entry));
                        }
                    }
                });
    }
}

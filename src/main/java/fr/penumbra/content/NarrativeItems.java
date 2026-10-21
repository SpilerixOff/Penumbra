package fr.penumbra.content;

import com.mojang.serialization.Codec;
import fr.penumbra.PenumbraMod;
import fr.penumbra.content.item.SilhouetteGlassItem;
import fr.penumbra.content.item.StructureClueItem;
import fr.penumbra.structure.StructureType;
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

/**
 * Registre des six objets narratifs de Penumbra (indices de structures).
 * <p>Reproduit le pattern d'enregistrement 1.21.2+/26.1.2 de
 * {@code fr.penumbra.registry.PenumbraItems} : {@code ResourceKey} obligatoire
 * posée via {@code Item.Properties.setId}, {@code Registry.register} manuel,
 * ajout au même onglet créatif « Ingrédients » ({@code minecraft:ingredients},
 * clé reconstruite car {@code CreativeModeTabs.INGREDIENTS} est privé).</p>
 * <p>Appelé par l'intégrateur depuis {@code PenumbraMod.onInitialize()}
 * (à côté de {@code PenumbraItems.register()}).</p>
 */
public final class NarrativeItems {

    /**
     * Composant du morceau de verre : 1 = la silhouette s'y reflète,
     * 2 = verre net. Absent = tirage pas encore effectué (voir
     * {@code SilhouetteGlassItem.inventoryTick}). Persistant (NBT) et
     * synchronisé réseau pour l'infobulle client. API 26.1.2 vérifiée par
     * javap : {@code DataComponentType.Builder.persistent(Codec)
     * .networkSynchronized(StreamCodec).build()} et registre
     * {@code BuiltInRegistries.DATA_COMPONENT_TYPE}.
     */
    public static final DataComponentType<Integer> SILHOUETTE_STATE = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            PenumbraMod.id("silhouette_state"),
            DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    private static final ResourceKey<Item> CRACKED_LENS_KEY =
            ResourceKey.create(Registries.ITEM, PenumbraMod.id("cracked_lens"));
    private static final ResourceKey<Item> INCOMPLETE_JOURNAL_KEY =
            ResourceKey.create(Registries.ITEM, PenumbraMod.id("incomplete_journal"));
    private static final ResourceKey<Item> OBSERVATION_FRAGMENT_KEY =
            ResourceKey.create(Registries.ITEM, PenumbraMod.id("observation_fragment"));
    private static final ResourceKey<Item> BLACKENED_PLUMB_BOB_KEY =
            ResourceKey.create(Registries.ITEM, PenumbraMod.id("blackened_plumb_bob"));
    private static final ResourceKey<Item> IMPOSSIBLE_PHOTO_KEY =
            ResourceKey.create(Registries.ITEM, PenumbraMod.id("impossible_photo"));
    private static final ResourceKey<Item> SILHOUETTE_GLASS_KEY =
            ResourceKey.create(Registries.ITEM, PenumbraMod.id("silhouette_glass"));

    /**
     * Même onglet créatif que les autres objets Penumbra : « Ingrédients »,
     * clé reconstruite à l'identique ({@code minecraft:ingredients}) comme
     * dans {@code PenumbraItems}.
     */
    private static final ResourceKey<CreativeModeTab> INGREDIENTS_TAB =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("ingredients"));

    /** Lentille fissurée : indice du poste d'observation. Non empilable. */
    public static final Item CRACKED_LENS = Registry.register(
            BuiltInRegistries.ITEM,
            CRACKED_LENS_KEY,
            new StructureClueItem(new Item.Properties().stacksTo(1).setId(CRACKED_LENS_KEY),
                    StructureType.OBSERVATION_POST));

    /**
     * Journal incomplet : pages de lore, couverture usée. Non empilable.
     * 0.6.0 : lisible au clic droit — {@link fr.penumbra.content.item.JournalItem}
     * envoie des lignes générées depuis les vraies statistiques du profil.
     */
    public static final Item INCOMPLETE_JOURNAL = Registry.register(
            BuiltInRegistries.ITEM,
            INCOMPLETE_JOURNAL_KEY,
            new fr.penumbra.content.item.JournalItem(
                    new Item.Properties().stacksTo(1).setId(INCOMPLETE_JOURNAL_KEY)));

    /** Fragment d'observation : parchemin taché de cendre. Empilable par 16. */
    public static final Item OBSERVATION_FRAGMENT = Registry.register(
            BuiltInRegistries.ITEM,
            OBSERVATION_FRAGMENT_KEY,
            new StructureClueItem(new Item.Properties().stacksTo(16).setId(OBSERVATION_FRAGMENT_KEY),
                    StructureType.ASH_SANCTUM));

    /** Fil à plomb noirci : indice du puits. Non empilable. */
    public static final Item BLACKENED_PLUMB_BOB = Registry.register(
            BuiltInRegistries.ITEM,
            BLACKENED_PLUMB_BOB_KEY,
            new StructureClueItem(new Item.Properties().stacksTo(1).setId(BLACKENED_PLUMB_BOB_KEY),
                    StructureType.PENUMBRA_WELL));

    /** Photographie impossible : indice narratif rare. Non empilable. */
    public static final Item IMPOSSIBLE_PHOTO = Registry.register(
            BuiltInRegistries.ITEM,
            IMPOSSIBLE_PHOTO_KEY,
            new StructureClueItem(new Item.Properties().stacksTo(1).setId(IMPOSSIBLE_PHOTO_KEY),
                    StructureType.SHIFTED_HOUSE));

    /** Morceau de verre : 15 % des stacks révèlent une silhouette (composant). */
    public static final Item SILHOUETTE_GLASS = Registry.register(
            BuiltInRegistries.ITEM,
            SILHOUETTE_GLASS_KEY,
            new SilhouetteGlassItem(new Item.Properties().stacksTo(1).setId(SILHOUETTE_GLASS_KEY)));

    private NarrativeItems() {
    }

    /** Force le chargement statique et ajoute les six objets à l'onglet Ingrédients. */
    public static void register() {
        CreativeModeTabEvents.modifyOutputEvent(INGREDIENTS_TAB)
                .register(entries -> {
                    entries.accept(CRACKED_LENS);
                    entries.accept(INCOMPLETE_JOURNAL);
                    entries.accept(OBSERVATION_FRAGMENT);
                    entries.accept(BLACKENED_PLUMB_BOB);
                    entries.accept(IMPOSSIBLE_PHOTO);
                    entries.accept(SILHOUETTE_GLASS);
                });
    }
}

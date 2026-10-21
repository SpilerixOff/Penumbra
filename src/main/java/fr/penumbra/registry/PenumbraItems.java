package fr.penumbra.registry;

import fr.penumbra.PenumbraMod;
import fr.penumbra.content.item.AshWardItem;
import fr.penumbra.content.item.HammerItem;
import fr.penumbra.content.item.VhsCameraItem;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.ToolMaterial;

/**
 * Registre des objets de Penumbra. Phase 1 : le Sceau de cendre (ash_sigil).
 */
public final class PenumbraItems {

    private static final ResourceKey<Item> ASH_SIGIL_KEY =
            ResourceKey.create(Registries.ITEM, PenumbraMod.id("ash_sigil"));
    private static final ResourceKey<Item> ASH_WARD_KEY =
            ResourceKey.create(Registries.ITEM, PenumbraMod.id("ash_ward"));
    private static final ResourceKey<Item> BINOCULARS_KEY =
            ResourceKey.create(Registries.ITEM, PenumbraMod.id("binoculars"));
    private static final ResourceKey<Item> STONE_HAMMER_KEY =
            ResourceKey.create(Registries.ITEM, PenumbraMod.id("stone_hammer"));
    private static final ResourceKey<Item> HAMMER_KEY =
            ResourceKey.create(Registries.ITEM, PenumbraMod.id("hammer"));
    private static final ResourceKey<Item> GOLDEN_HAMMER_KEY =
            ResourceKey.create(Registries.ITEM, PenumbraMod.id("golden_hammer"));
    private static final ResourceKey<Item> DIAMOND_HAMMER_KEY =
            ResourceKey.create(Registries.ITEM, PenumbraMod.id("diamond_hammer"));
    // 0.16.0 : le disque « Mémoire », trouvé dans la Maison décalée.
    private static final ResourceKey<Item> MEMOIRE_DISC_KEY =
            ResourceKey.create(Registries.ITEM, PenumbraMod.id("memoire_disc"));
    private static final ResourceKey<JukeboxSong> MEMOIRE_SONG_KEY =
            ResourceKey.create(Registries.JUKEBOX_SONG, PenumbraMod.id("memoire"));

    /**
     * Un marteau paie un point de durabilité par bloc réellement cassé. Les
     * valeurs sont donc volontairement supérieures aux pioches vanilla : elles
     * représentent respectivement environ 96, 256, 64 et 768 plans 4x4 pleins.
     */
    private static final ToolMaterial STONE_HAMMER_MATERIAL = new ToolMaterial(
            BlockTags.INCORRECT_FOR_STONE_TOOL,
            1536,
            4.5F,
            1.0F,
            5,
            ItemTags.STONE_TOOL_MATERIALS);
    private static final ToolMaterial IRON_HAMMER_MATERIAL = new ToolMaterial(
            BlockTags.INCORRECT_FOR_IRON_TOOL,
            4096,
            7.0F,
            2.0F,
            14,
            ItemTags.IRON_TOOL_MATERIALS);
    private static final ToolMaterial GOLD_HAMMER_MATERIAL = new ToolMaterial(
            BlockTags.INCORRECT_FOR_GOLD_TOOL,
            1024,
            12.0F,
            0.0F,
            22,
            ItemTags.GOLD_TOOL_MATERIALS);
    private static final ToolMaterial DIAMOND_HAMMER_MATERIAL = new ToolMaterial(
            BlockTags.INCORRECT_FOR_DIAMOND_TOOL,
            12288,
            9.0F,
            3.0F,
            10,
            ItemTags.DIAMOND_TOOL_MATERIALS);

    /**
     * Clé de l'onglet « Ingrédients ». En 26.1.2, {@code CreativeModeTabs.INGREDIENTS}
     * est devenu privé : la clé est donc reconstruite à l'identique
     * ({@code minecraft:ingredients}).
     */
    private static final ResourceKey<CreativeModeTab> INGREDIENTS_TAB =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("ingredients"));

    // Enregistrement d'objet au format 1.21.2+ : la ResourceKey est obligatoire et posée
    // via Item.Properties.setId. Vérifié en 26.1.2 : Items.registerItem n'existe plus,
    // on enregistre manuellement via Registry.register.
    // (SPEC §4.4 « Item.Settings().maxCount(1) » = noms Yarn ; officiel :
    //  Item.Properties().stacksTo(1).)
    /** Sceau de cendre : relique liée à l'Hôte, non empilable. */
    public static final Item ASH_SIGIL = Registry.register(
            BuiltInRegistries.ITEM,
            ASH_SIGIL_KEY,
            new Item(new Item.Properties().stacksTo(1).setId(ASH_SIGIL_KEY)));

    /** Sceau consommable : bannit les manifestations Penumbra dans 16 blocs. */
    public static final Item ASH_WARD = Registry.register(
            BuiltInRegistries.ITEM,
            ASH_WARD_KEY,
            new AshWardItem(new Item.Properties().stacksTo(16).setId(ASH_WARD_KEY)));

    /**
     * Jumelles (Phase 2, SPEC2 §8) : non empilables, 256 points de durabilité.
     * La consommation (1 point par activation) est appliquée côté serveur par
     * {@code PenumbraC2S} si {@code binocularsDurabilityEnabled}.
     */
    public static final Item BINOCULARS = Registry.register(
            BuiltInRegistries.ITEM,
            BINOCULARS_KEY,
            new VhsCameraItem(new Item.Properties().stacksTo(1).durability(256).setId(BINOCULARS_KEY)));

    /**
     * Disque « Mémoire » (0.16.0) : une berceuse à boîte à musique qui se
     * désaccorde lentement, avec quelque chose de chuchoté à l'envers dessous.
     * La chanson est data-driven ({@code data/penumbra/jukebox_song/memoire.json}).
     */
    public static final Item MEMOIRE_DISC = Registry.register(
            BuiltInRegistries.ITEM,
            MEMOIRE_DISC_KEY,
            new Item(new Item.Properties().stacksTo(1)
                    .jukeboxPlayable(MEMOIRE_SONG_KEY)
                    .setId(MEMOIRE_DISC_KEY)));

    /** Marteau en pierre : entrée de gamme 4x4, réparable avec de la pierre. */
    public static final Item STONE_HAMMER = Registry.register(
            BuiltInRegistries.ITEM,
            STONE_HAMMER_KEY,
            new HammerItem(new Item.Properties()
                    .pickaxe(STONE_HAMMER_MATERIAL, 2.0F, -3.2F)
                    .setId(STONE_HAMMER_KEY)));

    /** Marteau en fer historique : l'identifiant reste penumbra:hammer pour les sauvegardes. */
    public static final Item HAMMER = Registry.register(
            BuiltInRegistries.ITEM,
            HAMMER_KEY,
            new HammerItem(new Item.Properties()
                    .pickaxe(IRON_HAMMER_MATERIAL, 3.0F, -3.1F)
                    .setId(HAMMER_KEY)));

    /** Marteau en or : très rapide et enchantable, mais le moins durable. */
    public static final Item GOLDEN_HAMMER = Registry.register(
            BuiltInRegistries.ITEM,
            GOLDEN_HAMMER_KEY,
            new HammerItem(new Item.Properties()
                    .pickaxe(GOLD_HAMMER_MATERIAL, 2.0F, -3.0F)
                    .setId(GOLDEN_HAMMER_KEY)));

    /** Marteau en diamant : niveau diamant et très longue durée de vie. */
    public static final Item DIAMOND_HAMMER = Registry.register(
            BuiltInRegistries.ITEM,
            DIAMOND_HAMMER_KEY,
            new HammerItem(new Item.Properties()
                    .pickaxe(DIAMOND_HAMMER_MATERIAL, 4.0F, -3.0F)
                    .setId(DIAMOND_HAMMER_KEY)));

    private PenumbraItems() {
    }

    /** Force le chargement statique et ajoute les objets à l'onglet Ingrédients. */
    public static void register() {
        CreativeModeTabEvents.modifyOutputEvent(INGREDIENTS_TAB)
                .register(entries -> {
                    entries.accept(ASH_SIGIL);
                    entries.accept(ASH_WARD);
                    entries.accept(BINOCULARS);
                    entries.accept(STONE_HAMMER);
                    entries.accept(HAMMER);
                    entries.accept(GOLDEN_HAMMER);
                    entries.accept(DIAMOND_HAMMER);
                });
    }
}

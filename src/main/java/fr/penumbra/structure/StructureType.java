package fr.penumbra.structure;

import fr.penumbra.PenumbraMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Les quatre structures procédurales de Penumbra (Phase 3), rares, petites
 * (empreinte ≤ 16×16), surface de l'Overworld uniquement.
 * <p>
 * Le poids sert au tirage de type dans un chunk candidat (somme = 100) :
 * poste 40 %, maison 35 %, sanctuaire 20 %, puits 5 % — le puits est donc
 * environ ×0.15 plus rare que la moyenne des trois autres, comme spécifié.
 * Le Bûcher (0.18.0) porte un poids de 0 : il est placé à part, une seule fois
 * par monde, et reste donc hors de ce tirage.
 * </p>
 */
public enum StructureType {

    /** Petite tour/cabane en bois tournée vers le lointain (~5×7×5). */
    OBSERVATION_POST("observation_post", 40, 3),
    /** Maison aux proportions légèrement fausses (~7×6×7). */
    SHIFTED_HOUSE("shifted_house", 35, 3),
    /** Cercle de symboles de cendre avec pilier central et rituel. */
    ASH_SANCTUM("ash_sanctum", 20, 2),
    /** Puits de pierre très rare descendant anormalement profond. */
    PENUMBRA_WELL("penumbra_well", 5, 2),
    /**
     * 0.18.0 — Le Bûcher, camp de l'enquêteur du Relevé. <b>Poids 0 : il ne
     * participe jamais au tirage naturel</b> ({@link #pick(int)} accumule les
     * poids et ne peut donc pas l'atteindre). Sa position est unique par monde
     * et dérivée de la graine par {@code PyreManager} ; ce type n'existe ici
     * que pour lui donner une loot table et une entrée dans le tracker.
     */
    SIGNAL_PYRE("signal_pyre", 0, 1);

    private final String id;
    private final int weight;
    private final int variantCount;

    StructureType(String id, int weight, int variantCount) {
        this.id = id;
        this.weight = weight;
        this.variantCount = variantCount;
    }

    /** Identifiant court utilisé dans les commandes, les clés de découverte et les loot tables. */
    public String id() {
        return this.id;
    }

    /** Nombre de variantes de layout (choisies par hash déterministe). */
    public int variantCount() {
        return this.variantCount;
    }

    /**
     * Clé de la loot table {@code data/penumbra/loot_table/structures/<id>.json}.
     * Vérifié en 26.1.2 : {@code Registries.LOOT_TABLE} + {@code ResourceKey.create}.
     */
    public ResourceKey<LootTable> lootTable() {
        return ResourceKey.create(Registries.LOOT_TABLE, PenumbraMod.id("structures/" + this.id));
    }

    /** Type portant cet identifiant, ou {@code null} si inconnu. */
    public static StructureType byId(String id) {
        for (StructureType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Tirage pondéré sur {@code roll} ∈ [0, 100) : 40/35/20/5.
     * Toute valeur hors plage retombe sur le poste d'observation.
     */
    public static StructureType pick(int roll) {
        int cumulative = 0;
        for (StructureType type : values()) {
            cumulative += type.weight;
            if (roll < cumulative) {
                return type;
            }
        }
        return OBSERVATION_POST;
    }
}

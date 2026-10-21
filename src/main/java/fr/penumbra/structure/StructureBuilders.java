package fr.penumbra.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Helpers partagés des builders de structures : pose directe de blocs pendant
 * {@code ServerChunkEvents.CHUNK_GENERATE} avec le drapeau
 * {@link Block#UPDATE_NONE} (aucune mise à jour de voisinage, aucun pop-off,
 * aucun chargement de chunk forcé), coffres à loot table et panneaux à texte.
 * <p>
 * Vérifié par javap en 26.1.2 :
 * </p>
 * <ul>
 *   <li>{@code RandomizableContainerBlockEntity.setLootTable(ResourceKey<LootTable>)}
 *       + {@code setLootTableSeed(long)} (pas de surcharge à deux arguments) ;</li>
 *   <li>{@code SignBlockEntity.getFrontText()} / {@code SignText.setMessage(int, Component)}
 *       (renvoie un NOUVEAU SignText) / {@code SignBlockEntity.setText(SignText, boolean)} ;</li>
 *   <li>{@code StandingSignBlock.ROTATION}, {@code ChestBlock.FACING},
 *       {@code DoorBlock.FACING/HALF}, {@code Block.UPDATE_NONE}.</li>
 * </ul>
 */
abstract class StructureBuilders {

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    /** Profondeur maximale de descente des fondations sous un plancher. */
    private static final int FOUNDATION_DEPTH = 3;

    private StructureBuilders() {
    }

    /** Pose un bloc sans aucune mise à jour (flags UPDATE_NONE, génération only). */
    static void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_NONE);
    }

    /** Pose un bloc aux coordonnées données (raccourci de {@link #set}). */
    static void set(ServerLevel level, int x, int y, int z, BlockState state) {
        set(level, new BlockPos(x, y, z), state);
    }

    /** Remplit un parallélépipède inclusif avec un état donné. */
    static void fill(ServerLevel level, int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    set(level, x, y, z, state);
                }
            }
        }
    }

    /** Vide un parallélépipède inclusif (air). */
    static void clear(ServerLevel level, int x1, int y1, int z1, int x2, int y2, int z2) {
        fill(level, x1, y1, z1, x2, y2, z2, AIR);
    }

    /**
     * Coffre avec loot table différée : le contenu n'est tiré qu'à la première
     * ouverture, avec une graine propre à la structure (déterministe).
     */
    static void placeLootChest(ServerLevel level, BlockPos pos, Direction facing,
                               ResourceKey<LootTable> table, long lootSeed) {
        set(level, pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing));
        if (level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity container) {
            container.setLootTable(table);
            container.setLootTableSeed(lootSeed);
        }
    }

    /**
     * Panneau debout avec lignes de traduction (une clé par ligne, face avant).
     * {@code SignText.setMessage} est immuable : on enchaîne les copies puis on
     * pose le texte final via {@code sign.setText(text, true)}.
     */
    static void placeSign(ServerLevel level, BlockPos pos, int rotation, String... translationKeys) {
        set(level, pos, Blocks.OAK_SIGN.defaultBlockState()
                .setValue(StandingSignBlock.ROTATION, rotation & 15));
        if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
            SignText text = sign.getFrontText();
            for (int i = 0; i < translationKeys.length && i < SignText.LINES; i++) {
                text = text.setMessage(i, Component.translatable(translationKeys[i]));
            }
            sign.setText(text, true);
        }
    }

    /** Porte complète (deux moitiés) orientée ; sans mise à jour elle ne casse pas. */
    static void placeDoor(ServerLevel level, int x, int y, int z, Direction facing, BlockState doorBlock) {
        BlockState lower = doorBlock.setValue(DoorBlock.FACING, facing)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        set(level, x, y, z, lower);
        set(level, x, y + 1, z, lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    /**
     * Petite fondation : descend un poteau sous un angle de plancher tant que
     * le bloc est traversable (air/plante), borné à {@value #FOUNDATION_DEPTH}.
     */
    static void foundation(ServerLevel level, int x, int yBelow, int z, BlockState material) {
        for (int y = yBelow; y > yBelow - FOUNDATION_DEPTH; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState current = level.getBlockState(pos);
            if (current.isSolid() || !current.getFluidState().isEmpty()) {
                return;
            }
            set(level, pos, material);
        }
    }
}

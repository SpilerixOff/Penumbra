package fr.penumbra.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Maison décalée (~7×6×7) : proportions légèrement fausses — porte décalée
 * d'un bloc, fenêtres asymétriques, deux portes menant presque au même
 * endroit, plafond rabaissé à 2 blocs dans une moitié de pièce. Le coffre
 * « imitation d'inventaire » (loot {@code structures/shifted_house}) contient
 * des objets banals en quantités étranges. AUCUN jumpscare.
 */
abstract class ShiftedHouseBuilder {

    private static final BlockState PLANKS = Blocks.OAK_PLANKS.defaultBlockState();
    private static final BlockState LOG = Blocks.OAK_LOG.defaultBlockState();
    private static final BlockState SLAB = Blocks.OAK_SLAB.defaultBlockState();
    private static final BlockState ROOF = Blocks.SPRUCE_SLAB.defaultBlockState();
    private static final BlockState DOOR = Blocks.SPRUCE_DOOR.defaultBlockState();
    private static final BlockState COBWEB = Blocks.COBWEB.defaultBlockState();

    private ShiftedHouseBuilder() {
    }

    /**
     * Construit la variante demandée ; {@code origin} = premier air au centre
     * du chunk. Retourne l'ancre (coffre) pour le tracker.
     */
    static BlockPos build(ServerLevel level, BlockPos origin, int variant, long seed) {
        int x = origin.getX();
        int y = origin.getY();
        int z = origin.getZ();

        // Plancher à la place de la couche de surface, fondations légères.
        StructureBuilders.fill(level, x - 3, y - 1, z - 3, x + 3, y - 1, z + 3, PLANKS);
        for (int dx : new int[]{-3, 3}) {
            for (int dz : new int[]{-3, 3}) {
                StructureBuilders.foundation(level, x + dx, y - 2, z + dz, LOG);
            }
        }

        // Murs y..y+3 (4 de haut), poteaux d'angle en rondins.
        for (int dy = 0; dy <= 3; dy++) {
            for (int d = -3; d <= 3; d++) {
                StructureBuilders.set(level, x + d, y + dy, z - 3, wall(d, dy));
                StructureBuilders.set(level, x + d, y + dy, z + 3, wall(d, dy));
                StructureBuilders.set(level, x - 3, y + dy, z + d, wall(d, dy));
                StructureBuilders.set(level, x + 3, y + dy, z + d, wall(d, dy));
            }
        }
        StructureBuilders.clear(level, x - 2, y, z - 2, x + 2, y + 3, z + 2);

        // Toit plat décalé d'une demi-dalle (volontairement « pas tout à fait droit »).
        StructureBuilders.fill(level, x - 3, y + 4, z - 3, x + 3, y + 4, z + 3, ROOF);
        StructureBuilders.fill(level, x - 2, y + 5, z - 2, x + 1, y + 5, z + 1, SLAB);

        // Fenêtres asymétriques : une basse à l'ouest, une haute au nord.
        StructureBuilders.clear(level, x - 3, y + 1, z - 1, x - 3, y + 1, z);
        StructureBuilders.clear(level, x - 1, y + 3, z - 3, x, y + 3, z - 3);

        // Portes et plafond rabaissé selon la variante.
        switch (variant) {
            case 1 -> {
                StructureBuilders.placeDoor(level, x - 1, y, z + 3, Direction.SOUTH, DOOR);
                StructureBuilders.placeDoor(level, x + 3, y, z + 1, Direction.EAST, DOOR);
                loweredCeiling(level, x, y, z, false);
            }
            case 2 -> {
                // Deux portes adjacentes menant presque au même endroit.
                StructureBuilders.placeDoor(level, x, y, z + 3, Direction.SOUTH, DOOR);
                StructureBuilders.placeDoor(level, x + 1, y, z + 3, Direction.SOUTH, DOOR);
                loweredCeilingStrip(level, x, y, z);
            }
            default -> {
                StructureBuilders.placeDoor(level, x + 1, y, z + 3, Direction.SOUTH, DOOR);
                StructureBuilders.placeDoor(level, x + 3, y, z - 1, Direction.EAST, DOOR);
                loweredCeiling(level, x, y, z, true);
            }
        }

        // Une seule torche au sol (comme le butin), toile d'araignée au plafond.
        StructureBuilders.set(level, x + 2, y, z + 2, Blocks.TORCH.defaultBlockState());
        StructureBuilders.set(level, x - 2, y + 3, z - 2, COBWEB);

        BlockPos chest = new BlockPos(x - 2, y, z - 1);
        StructureBuilders.placeLootChest(level, chest, Direction.EAST,
                StructureType.SHIFTED_HOUSE.lootTable(), seed);
        return chest;
    }

    /** Matériau de mur : rondins aux angles, planches ailleurs. */
    private static BlockState wall(int d, int dy) {
        return Math.abs(d) == 3 || dy == 0 ? LOG : PLANKS;
    }

    /**
     * Plafond rabaissé à 2 blocs (planches à y+2) sur une moitié de la pièce :
     * est si {@code east}, ouest sinon. L'autre moitié garde 3 blocs de haut.
     */
    private static void loweredCeiling(ServerLevel level, int x, int y, int z, boolean east) {
        int from = east ? 0 : -2;
        int to = east ? 2 : -1;
        StructureBuilders.fill(level, x + from, y + 2, z - 2, x + to, y + 2, z + 2, PLANKS);
    }

    /** Variante 2 : plafond rabaissé partout sauf une étroite bande au nord. */
    private static void loweredCeilingStrip(ServerLevel level, int x, int y, int z) {
        StructureBuilders.fill(level, x - 2, y + 2, z - 1, x + 2, y + 2, z + 2, PLANKS);
    }
}

package fr.penumbra.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Puits de pénombre (TRÈS rare) : margelle de pierre 3×3 à la surface, puits
 * 1×1 descendant anormalement profond (20 ou 30 blocs, eau au fond) et petite
 * galerie narrative latérale au fond (3×2×3) avec un coffre
 * (loot {@code structures/penumbra_well}). Le creusement ne touche que des
 * chunks en cours de génération — aucune construction de joueur possible.
 */
abstract class PenumbraWellBuilder {

    private static final BlockState RING = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
    private static final BlockState POST = Blocks.COBBLESTONE_WALL.defaultBlockState();
    private static final BlockState ROOF = Blocks.SPRUCE_SLAB.defaultBlockState();
    private static final BlockState LINING = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
    private static final BlockState FLOOR = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState COBWEB = Blocks.COBWEB.defaultBlockState();
    private static final BlockState SOUL = Blocks.SOUL_SOIL.defaultBlockState();
    /** FACING points away from the support: EAST attaches to the west wall. */
    private static final BlockState LADDER = Blocks.LADDER.defaultBlockState()
            .setValue(LadderBlock.FACING, Direction.EAST);

    private PenumbraWellBuilder() {
    }

    /**
     * Construit le puits ; {@code origin} = premier air au centre du chunk.
     * Variante 0 : 20 blocs, galerie au nord. Variante 1 : 30 blocs, galerie
     * à l'est, traces de terre d'âme. Retourne l'ancre (bouche du puits).
     */
    static BlockPos build(ServerLevel level, BlockPos origin, int variant, long seed) {
        int x = origin.getX();
        int y = origin.getY();
        int z = origin.getZ();
        int depth = variant == 1 ? 30 : 20;

        // Margelle 3×3 à hauteur de taille, piliers d'angle et auvent.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) {
                    StructureBuilders.set(level, x + dx, y, z + dz, RING);
                }
            }
        }
        StructureBuilders.fill(level, x - 1, y + 1, z - 1, x - 1, y + 2, z - 1, POST);
        StructureBuilders.fill(level, x + 1, y + 1, z + 1, x + 1, y + 2, z + 1, POST);
        // Two clear blocks above the rim let a standing player step off the ladder.
        StructureBuilders.fill(level, x - 1, y + 3, z - 1, x + 1, y + 3, z + 1, ROOF);

        // Fût 1×1 : les 3 premières couches sont maçonnées, puis terre nue.
        for (int dy = 1; dy <= depth; dy++) {
            StructureBuilders.clear(level, x, y - dy, z, x, y - dy, z);
            if (dy <= 3) {
                StructureBuilders.set(level, x - 1, y - dy, z, LINING);
                StructureBuilders.set(level, x + 1, y - dy, z, LINING);
                StructureBuilders.set(level, x, y - dy, z - 1, LINING);
                StructureBuilders.set(level, x, y - dy, z + 1, LINING);
            }
        }

        // Eau au fond (deux couches, immobile grâce à UPDATE_NONE).
        int bottom = y - depth;
        StructureBuilders.set(level, x, bottom, z, WATER);
        StructureBuilders.set(level, x, bottom + 1, z, WATER);

        // A continuous escape route is part of the archive, even across underground caves.
        // Leave both water blocks and the north/east gallery entrances unobstructed.
        for (int ladderY = bottom + 2; ladderY <= y; ladderY++) {
            StructureBuilders.set(level, x - 1, ladderY, z, LINING);
            StructureBuilders.set(level, x, ladderY, z, LADDER);
        }

        // Galerie latérale 3×2×3 au fond, reliée au fût.
        Direction gallery = variant == 1 ? Direction.EAST : Direction.NORTH;
        carveGallery(level, x, bottom, z, gallery, variant, seed);
        return origin;
    }

    /** Creuse et meuble la galerie du fond (coffre narratif, toiles, âmes). */
    private static void carveGallery(ServerLevel level, int x, int bottom, int z,
                                     Direction direction, int variant, long seed) {
        int dx = direction.getStepX();
        int dz = direction.getStepZ();
        // Ouverture depuis le fût puis salle 3×2×3 (de g1 à g3 dans l'axe).
        StructureBuilders.clear(level, x + dx, bottom, z + dz, x + dx, bottom + 1, z + dz);
        int cx = x + dx * 2;
        int cz = z + dz * 2;
        StructureBuilders.clear(level, cx - 1, bottom, cz - 1, cx + 1, bottom + 1, cz + 1);
        StructureBuilders.clear(level, cx + dx, bottom, cz + dz, cx + dx, bottom + 1, cz + dz);
        // Plancher maçonné sous la salle.
        StructureBuilders.fill(level, cx - 1, bottom - 1, cz - 1, cx + 1, bottom - 1, cz + 1, FLOOR);
        StructureBuilders.set(level, cx + dx, bottom - 1, cz + dz, FLOOR);

        // Coffre au fond de la galerie, tourné vers l'entrée.
        BlockPos chest = new BlockPos(cx + dx, bottom, cz + dz);
        StructureBuilders.placeLootChest(level, chest, direction.getOpposite(),
                StructureType.PENUMBRA_WELL.lootTable(), seed);
        StructureBuilders.set(level, cx - dx, bottom, cz - dz, COBWEB);
        if (variant == 1) {
            StructureBuilders.set(level, cx, bottom - 1, cz, SOUL);
            StructureBuilders.set(level, cx + 1, bottom + 1, cz - 1, COBWEB);
        }
    }
}

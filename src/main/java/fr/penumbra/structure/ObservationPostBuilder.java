package fr.penumbra.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Poste d'observation (~5×7×5) : cabane ou guérite de bois aux longues
 * ouvertures orientées vers le lointain. Trois variantes : guérite sur
 * pilotis, cabane au sol, tour à deux niveaux. Chaque layout contient un
 * coffre (loot {@code structures/observation_post}), un panneau à phrase
 * incomplète et des traces de cendre ({@code black_wool}, parcimonieux —
 * {@code minecraft:ash} n'existe pas en vanilla 26.1.2).
 */
abstract class ObservationPostBuilder {

    private static final BlockState PLANKS = Blocks.SPRUCE_PLANKS.defaultBlockState();
    private static final BlockState LOG = Blocks.SPRUCE_LOG.defaultBlockState();
    private static final BlockState SLAB = Blocks.SPRUCE_SLAB.defaultBlockState();
    private static final BlockState FENCE = Blocks.SPRUCE_FENCE.defaultBlockState();
    private static final BlockState ASH_TRACE = Blocks.BLACK_WOOL.defaultBlockState();
    private static final BlockState COBWEB = Blocks.COBWEB.defaultBlockState();

    private ObservationPostBuilder() {
    }

    /**
     * Construit la variante demandée. {@code origin} est le premier bloc d'air
     * au-dessus du sol au centre du chunk. Retourne la position d'ancre
     * (coffre) pour le tracker.
     */
    static BlockPos build(ServerLevel level, BlockPos origin, int variant, long seed) {
        return switch (variant) {
            case 1 -> buildGroundHut(level, origin, seed);
            case 2 -> buildTower(level, origin, seed);
            default -> buildStiltPost(level, origin, seed);
        };
    }

    /** Variante 0 : guérite 5×5 sur pilotis, plancher à y+1, fenêtres en fente. */
    private static BlockPos buildStiltPost(ServerLevel level, BlockPos o, long seed) {
        int x = o.getX();
        int y = o.getY();
        int z = o.getZ();

        // Pilotis d'angle (descendus dans le sol) et plancher surélevé.
        for (int dx : new int[]{-2, 2}) {
            for (int dz : new int[]{-2, 2}) {
                StructureBuilders.fill(level, x + dx, y - 1, z + dz, x + dx, y, z + dz, LOG);
                StructureBuilders.foundation(level, x + dx, y - 2, z + dz, LOG);
            }
        }
        StructureBuilders.fill(level, x - 2, y + 1, z - 2, x + 2, y + 1, z + 2, PLANKS);
        // Trappe d'accès dans le plancher (pas d'échelle : il faut se hisser).
        StructureBuilders.clear(level, x, y + 1, z, x, y + 1, z);

        // Murs y+2..y+4 : fentes horizontales d'observation à y+3 (3 blocs par face).
        for (int dy = 2; dy <= 4; dy++) {
            for (int d = -2; d <= 2; d++) {
                boolean slit = dy == 3 && Math.abs(d) <= 1;
                if (!slit) {
                    StructureBuilders.set(level, x + d, y + dy, z - 2, PLANKS);
                    StructureBuilders.set(level, x + d, y + dy, z + 2, PLANKS);
                    StructureBuilders.set(level, x - 2, y + dy, z + d, PLANKS);
                    StructureBuilders.set(level, x + 2, y + dy, z + d, PLANKS);
                }
            }
        }
        StructureBuilders.clear(level, x - 1, y + 2, z - 1, x + 1, y + 4, z + 1);

        // Toit plat avec faîtage en dalles.
        StructureBuilders.fill(level, x - 2, y + 5, z - 2, x + 2, y + 5, z + 2, PLANKS);
        StructureBuilders.fill(level, x - 1, y + 6, z - 1, x + 1, y + 6, z + 1, SLAB);

        // Traces de cendre au sol, coffre et panneau.
        StructureBuilders.set(level, x + 1, y + 1, z, ASH_TRACE);
        StructureBuilders.set(level, x - 1, y + 1, z + 1, ASH_TRACE);
        BlockPos chest = new BlockPos(x + 1, y + 2, z - 1);
        StructureBuilders.placeLootChest(level, chest, Direction.SOUTH,
                StructureType.OBSERVATION_POST.lootTable(), seed);
        StructureBuilders.placeSign(level, new BlockPos(x - 1, y + 2, z - 1), 8,
                "penumbra.sign.observation_1", "penumbra.sign.observation_2");
        StructureBuilders.set(level, x - 2, y + 4, z + 1, COBWEB);
        return chest;
    }

    /** Variante 1 : cabane au sol 5×5×4, une seule ouverture haute à l'est. */
    private static BlockPos buildGroundHut(ServerLevel level, BlockPos o, long seed) {
        int x = o.getX();
        int y = o.getY();
        int z = o.getZ();

        // Plancher posé à la place de la couche de surface.
        StructureBuilders.fill(level, x - 2, y - 1, z - 2, x + 2, y - 1, z + 2, PLANKS);
        // Murs y..y+2 ; porte sommaire au sud, fente haute à l'est uniquement.
        for (int dy = 0; dy <= 2; dy++) {
            for (int d = -2; d <= 2; d++) {
                StructureBuilders.set(level, x + d, y + dy, z - 2, PLANKS);
                StructureBuilders.set(level, x - 2, y + dy, z + d, PLANKS);
                StructureBuilders.set(level, x + d, y + dy, z + 2, PLANKS);
                boolean eastSlit = dy == 2 && Math.abs(d) <= 1;
                if (!eastSlit) {
                    StructureBuilders.set(level, x + 2, y + dy, z + d, PLANKS);
                }
            }
        }
        StructureBuilders.clear(level, x - 1, y, z - 1, x + 1, y + 2, z + 1);
        StructureBuilders.clear(level, x, y, z + 2, x, y + 1, z + 2); // passage sud béant

        StructureBuilders.fill(level, x - 2, y + 3, z - 2, x + 2, y + 3, z + 2, SLAB);
        StructureBuilders.fill(level, x - 1, y + 4, z, x + 1, y + 4, z, PLANKS);

        StructureBuilders.set(level, x, y - 1, z, ASH_TRACE);
        StructureBuilders.set(level, x - 1, y - 1, z - 1, ASH_TRACE);
        BlockPos chest = new BlockPos(x + 1, y, z - 1);
        StructureBuilders.placeLootChest(level, chest, Direction.WEST,
                StructureType.OBSERVATION_POST.lootTable(), seed);
        StructureBuilders.placeSign(level, new BlockPos(x - 1, y, z + 1), 12,
                "penumbra.sign.observation_3");
        StructureBuilders.set(level, x + 1, y + 2, z + 1, COBWEB);
        return chest;
    }

    /** Variante 2 : tour 3×3 à deux niveaux (~9 de haut), salle haute d'observation. */
    private static BlockPos buildTower(ServerLevel level, BlockPos o, long seed) {
        int x = o.getX();
        int y = o.getY();
        int z = o.getZ();

        StructureBuilders.fill(level, x - 1, y - 1, z - 1, x + 1, y - 1, z + 1, PLANKS);
        // Parois y..y+6 ; fentes d'observation du niveau haut à y+5.
        for (int dy = 0; dy <= 6; dy++) {
            for (int d = -1; d <= 1; d++) {
                boolean upperSlit = dy == 5 && d == 0;
                if (!upperSlit) {
                    StructureBuilders.set(level, x + d, y + dy, z - 1, dy % 3 == 0 ? LOG : PLANKS);
                    StructureBuilders.set(level, x + d, y + dy, z + 1, dy % 3 == 0 ? LOG : PLANKS);
                    StructureBuilders.set(level, x - 1, y + dy, z + d, dy % 3 == 0 ? LOG : PLANKS);
                    StructureBuilders.set(level, x + 1, y + dy, z + d, dy % 3 == 0 ? LOG : PLANKS);
                }
            }
        }
        // Entrée basse au sud et plancher intermédiaire à y+3.
        StructureBuilders.clear(level, x, y, z + 1, x, y + 1, z + 1);
        StructureBuilders.clear(level, x, y, z, x, y + 6, z);
        StructureBuilders.set(level, x, y + 3, z, PLANKS);
        // Échelle intérieure adossée à la paroi nord (UPDATE_NONE : ne casse pas).
        for (int dy = 0; dy <= 3; dy++) {
            StructureBuilders.set(level, x, y + dy, z - 1,
                    Blocks.LADDER.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.SOUTH));
        }
        // Toit.
        StructureBuilders.fill(level, x - 1, y + 7, z - 1, x + 1, y + 7, z + 1, PLANKS);
        StructureBuilders.set(level, x, y + 8, z, SLAB);

        StructureBuilders.set(level, x, y - 1, z, ASH_TRACE);
        BlockPos chest = new BlockPos(x, y + 4, z);
        StructureBuilders.placeLootChest(level, chest, Direction.SOUTH,
                StructureType.OBSERVATION_POST.lootTable(), seed);
        StructureBuilders.placeSign(level, new BlockPos(x - 1, y, z), 4,
                "penumbra.sign.observation_2", "penumbra.sign.observation_1");
        return chest;
    }
}

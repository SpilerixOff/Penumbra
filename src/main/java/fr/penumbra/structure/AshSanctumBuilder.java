package fr.penumbra.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Sanctuaire de cendre : cercle de symboles au sol (blackstone / blackstone
 * polie — {@code minecraft:ash} n'existe pas en vanilla 26.1.2), pilier
 * central de 3 blocs et coffre (loot {@code structures/ash_sanctum}).
 * <p>
 * L'ancre retournée est la BASE DU PILIER : c'est elle que
 * {@code StructureTracker.findSanctumAt} utilise pour détecter le rituel
 * (poser un {@code ash_sigil} sur le pilier).
 * </p>
 */
abstract class AshSanctumBuilder {

    private static final BlockState OUTER = Blocks.BLACKSTONE.defaultBlockState();
    private static final BlockState INNER = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
    private static final BlockState PILLAR = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
    private static final BlockState PILLAR_TOP = Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
    private static final BlockState ASH_TRACE = Blocks.BLACK_WOOL.defaultBlockState();
    private static final BlockState SOUL = Blocks.SOUL_SOIL.defaultBlockState();

    private AshSanctumBuilder() {
    }

    /**
     * Construit le sanctuaire ; {@code origin} = premier air au centre du
     * chunk. Variante 0 : cercle simple. Variante 1 : double cercle + quatre
     * petits piliers cardinaux. Retourne la base du pilier central.
     */
    static BlockPos build(ServerLevel level, BlockPos origin, int variant, long seed) {
        int x = origin.getX();
        int y = origin.getY();
        int z = origin.getZ();

        // Cercle de symboles : on remplace la couche de surface (rayon ≤ 4).
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                int distSqr = dx * dx + dz * dz;
                if (distSqr > 16) {
                    continue;
                }
                int gy = level.getHeight(Heightmap.Types.WORLD_SURFACE, x + dx, z + dz);
                BlockState symbol = distSqr >= 9 ? OUTER : INNER;
                // Losange de « symboles » : seules les cases (dx+dz) paires du
                // cercle extérieur portent la blackstone, l'intérieur est plein.
                if (distSqr >= 9 && ((dx + dz) & 1) != 0) {
                    continue;
                }
                StructureBuilders.set(level, x + dx, gy - 1, z + dz, symbol);
            }
        }

        // Traces de cendre et terre d'âme dans le cercle intérieur.
        StructureBuilders.set(level, x + 1, y - 1, z + 1, ASH_TRACE);
        StructureBuilders.set(level, x - 1, y - 1, z - 1, ASH_TRACE);
        if (variant == 1) {
            StructureBuilders.set(level, x + 2, y - 1, z, SOUL);
            StructureBuilders.set(level, x - 2, y - 1, z, SOUL);
            // Quatre petits piliers cardinaux (2 blocs).
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                int px = x + direction.getStepX() * 3;
                int pz = z + direction.getStepZ() * 3;
                int gy = level.getHeight(Heightmap.Types.WORLD_SURFACE, px, pz);
                StructureBuilders.set(level, px, gy, pz, PILLAR);
                StructureBuilders.set(level, px, gy + 1, pz, PILLAR);
            }
        }

        // Pilier central de 3 blocs : base + fût + chapiteau ciselé.
        BlockPos pillarBase = new BlockPos(x, y, z);
        StructureBuilders.set(level, x, y, z, PILLAR);
        StructureBuilders.set(level, x, y + 1, z, PILLAR);
        StructureBuilders.set(level, x, y + 2, z, PILLAR_TOP);

        // Coffre au pied du pilier et deux panneaux à phrases incomplètes.
        StructureBuilders.placeLootChest(level, new BlockPos(x + 2, y, z), Direction.WEST,
                StructureType.ASH_SANCTUM.lootTable(), seed);
        StructureBuilders.placeSign(level, new BlockPos(x - 1, y, z + 1), 10,
                "penumbra.sign.sanctum_1");
        StructureBuilders.placeSign(level, new BlockPos(x + 1, y, z - 1), 2,
                "penumbra.sign.sanctum_2");
        return pillarBase;
    }
}

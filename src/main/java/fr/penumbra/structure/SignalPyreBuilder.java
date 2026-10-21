package fr.penumbra.structure;

import fr.penumbra.prop.MeshPropEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Le Bûcher (0.18.0) — le camp de l'enquêteur qui a tenu le Relevé.
 *
 * <p>Ce n'est pas une ruine décorative : c'est une scène. Une cheminée de pierre
 * de onze blocs qui crache encore de la fumée, un abri de fortune adossé
 * dessous, un coffre, et tout autour les traces d'un séjour bien trop long —
 * cendres, toiles, une pioche cassée, des piquets. Rien n'explique ce qui est
 * arrivé au propriétaire. Le carnet, lui, l'explique.</p>
 *
 * <p>Empreinte ~11×11, entièrement contenue dans le chunk d'ancrage. Le coffre
 * ({@code structures/signal_pyre}) contient le Relevé.</p>
 */
public abstract class SignalPyreBuilder {

    private static final BlockState STONE = Blocks.COBBLESTONE.defaultBlockState();
    private static final BlockState MOSSY = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
    private static final BlockState STONE_SLAB = Blocks.COBBLESTONE_SLAB.defaultBlockState();
    private static final BlockState LOG = Blocks.SPRUCE_LOG.defaultBlockState();
    private static final BlockState PLANKS = Blocks.SPRUCE_PLANKS.defaultBlockState();
    private static final BlockState FENCE = Blocks.SPRUCE_FENCE.defaultBlockState();
    private static final BlockState ASH_TRACE = Blocks.BLACK_WOOL.defaultBlockState();
    private static final BlockState COBWEB = Blocks.COBWEB.defaultBlockState();
    private static final BlockState CAMPFIRE = Blocks.CAMPFIRE.defaultBlockState()
            .setValue(CampfireBlock.LIT, Boolean.TRUE)
            .setValue(CampfireBlock.SIGNAL_FIRE, Boolean.TRUE);

    private SignalPyreBuilder() {
    }

    /**
     * Construit le camp. {@code ground} est le premier bloc d'air au-dessus du
     * sol. Retourne la position du coffre — l'ancre enregistrée par le tracker.
     */
    public static BlockPos build(ServerLevel level, BlockPos ground, long seed) {
        RandomSource random = RandomSource.create(seed ^ 0x5B9C4E1DL);
        int x = ground.getX();
        int y = ground.getY();
        int z = ground.getZ();

        StructureBuilders.clear(level, x - 5, y, z - 5, x + 5, y + 12, z + 5);
        StructureBuilders.foundation(level, x, y - 1, z, STONE);

        buildChimney(level, x, y, z, random);
        buildShelter(level, x, y, z, random);
        scatterCamp(level, x, y, z, random);
        placeProps(level, x, y, z);

        BlockPos chest = new BlockPos(x + 2, y, z + 3);
        StructureBuilders.placeLootChest(level, chest, Direction.NORTH,
                StructureType.SIGNAL_PYRE.lootTable(), seed);
        return chest;
    }

    /**
     * Cheminée de onze blocs : c'est elle qu'on voit de loin. Le feu de camp
     * posé au sommet est un vrai feu de signal — la colonne de fumée vanilla
     * monte donc de dix blocs supplémentaires quand le joueur est enfin assez
     * près pour la voir sans le panache forcé de {@code PyreManager}.
     */
    private static void buildChimney(ServerLevel level, int x, int y, int z, RandomSource random) {
        // Socle 3×3 évidé, deux blocs de haut.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                StructureBuilders.set(level, x + dx, y, z + dz,
                        random.nextInt(3) == 0 ? MOSSY : STONE);
                StructureBuilders.set(level, x + dx, y + 1, z + dz,
                        random.nextInt(4) == 0 ? MOSSY : STONE);
            }
        }
        // Conduit 1×1 jusqu'à onze blocs, irrégulier : il a été monté à la main.
        for (int height = 2; height <= 10; height++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    boolean corner = dx != 0 && dz != 0;
                    if (corner && height > 6 && random.nextInt(3) == 0) {
                        continue;   // la maçonnerie s'effrite en hauteur
                    }
                    StructureBuilders.set(level, x + dx, y + height, z + dz,
                            random.nextInt(5) == 0 ? MOSSY : STONE);
                }
            }
        }
        StructureBuilders.set(level, x, y + 11, z, CAMPFIRE);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) {
                    StructureBuilders.set(level, x + dx, y + 11, z + dz, STONE_SLAB);
                }
            }
        }
        // Le foyer du bas brûle encore lui aussi.
        StructureBuilders.set(level, x, y, z, CAMPFIRE);
    }

    /** Abri de fortune adossé à la cheminée : trois murs, un toit qui fuit. */
    private static void buildShelter(ServerLevel level, int x, int y, int z, RandomSource random) {
        int x0 = x + 2;
        int z0 = z + 2;
        int x1 = x + 5;
        int z1 = z + 5;
        StructureBuilders.fill(level, x0, y, z0, x1, y, z1, PLANKS);
        for (int height = 1; height <= 2; height++) {
            for (int dx = x0; dx <= x1; dx++) {
                StructureBuilders.set(level, dx, y + height, z1,
                        random.nextInt(6) == 0 ? Blocks.AIR.defaultBlockState() : PLANKS);
            }
            for (int dz = z0; dz <= z1; dz++) {
                StructureBuilders.set(level, x1, y + height, dz,
                        random.nextInt(6) == 0 ? Blocks.AIR.defaultBlockState() : PLANKS);
            }
        }
        StructureBuilders.set(level, x0, y + 1, z0, LOG);
        StructureBuilders.set(level, x0, y + 2, z0, LOG);
        StructureBuilders.set(level, x1, y + 1, z1, LOG);
        // Toit en pente d'un seul pan, avec deux trous.
        for (int dx = x0; dx <= x1; dx++) {
            for (int dz = z0; dz <= z1; dz++) {
                if (random.nextInt(9) == 0) {
                    continue;
                }
                StructureBuilders.set(level, dx, y + 3, dz, STONE_SLAB);
            }
        }
        StructureBuilders.set(level, x0 + 1, y + 1, z0 - 1, FENCE);
        StructureBuilders.set(level, x0 + 1, y + 2, z0 - 1, Blocks.LANTERN.defaultBlockState());
    }

    /**
     * Les affaires de Warrick, en trois maillages 3D (0.19.0).
     *
     * <p>Le carnet répète qu'il était arpenteur ; jusqu'ici on ne voyait jamais
     * son métier. Un <b>théodolite</b> resté monté sur son trépied, face à
     * l'horizon qu'il relevait, dit ce que quinze pages expliquent. À côté, son
     * <b>paquetage</b> éventré, et un <b>cairn</b> qu'il a empilé lui-même — la
     * seule chose du camp qui n'ait servi à rien.</p>
     *
     * <p>Chaque objet cherche son propre sol : le camp est bâti sur du terrain
     * naturel, et une pente suffirait sinon à le laisser flotter.</p>
     */
    private static void placeProps(ServerLevel level, int x, int y, int z) {
        // Positions choisies à l'écart de la pile de bûches (x-4..x-3, z-3..z-2)
        // et du panneau (x-2, z-4), sinon les objets se poseraient dessus.
        prop(level, x - 3.5, y, z + 0.5, "theodolite", 118.0f);
        prop(level, x + 3.5, y, z + 1.5, "campement", 42.0f);
        prop(level, x + 1.5, y, z - 4.5, "cairn", 0.0f);
    }

    /** Pose un décor sur le premier sol franc autour de {@code y}. */
    private static void prop(ServerLevel level, double x, int y, double z,
                             String mesh, float yaw) {
        BlockPos ground = null;
        for (int offset = 2; offset >= -3; offset--) {
            BlockPos feet = BlockPos.containing(x, y + offset, z);
            if (level.getBlockState(feet).isAir()
                    && level.getBlockState(feet.below()).isSolidRender()) {
                ground = feet;
                break;
            }
        }
        if (ground == null) {
            return;     // terrain trop accidenté ici : on renonce sans bruit
        }
        MeshPropEntity.place(level, new Vec3(x, ground.getY(), z), yaw, mesh, "", 1.0f);
    }

    /**
     * Les traces du séjour : cendres, toiles, outils abandonnés, piquets. Elles
     * disent la durée sans jamais rien expliquer — c'est le travail du carnet.
     */
    private static void scatterCamp(ServerLevel level, int x, int y, int z, RandomSource random) {
        for (int attempt = 0; attempt < 26; attempt++) {
            int dx = random.nextInt(11) - 5;
            int dz = random.nextInt(11) - 5;
            if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
                continue;
            }
            BlockPos pos = new BlockPos(x + dx, y, z + dz);
            if (!level.getBlockState(pos).isAir()) {
                continue;
            }
            int roll = random.nextInt(10);
            if (roll < 5) {
                StructureBuilders.set(level, pos, ASH_TRACE);
            } else if (roll < 7) {
                StructureBuilders.set(level, pos, COBWEB);
            } else if (roll < 9) {
                StructureBuilders.set(level, pos, FENCE);
            } else {
                StructureBuilders.set(level, pos, Blocks.TORCH.defaultBlockState());
            }
        }
        // Une pile de bûches jamais brûlée : il a arrêté de faire du feu bien
        // avant de manquer de bois.
        StructureBuilders.fill(level, x - 4, y, z - 3, x - 3, y + 1, z - 2, LOG);
        StructureBuilders.placeSign(level, new BlockPos(x - 2, y, z - 4), 8,
                "sign.penumbra.pyre.line1", "sign.penumbra.pyre.line2",
                "sign.penumbra.pyre.line3", "sign.penumbra.pyre.line4");
    }
}

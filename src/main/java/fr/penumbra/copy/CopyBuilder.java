package fr.penumbra.copy;

import fr.penumbra.director.PlayerHorrorProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Constructeur de copies imparfaites : une petite coquille de pièce (5×5,
 * ≤ {@value #MAX_COPY_BLOCKS} blocs) érigée à 15–30 blocs du refuge le plus
 * récent du profil, imitant mal ses matériaux et ses proportions.
 * <p>
 * Tout est déclenché par appel externe ({@link #tryBuild}) : aucune boucle
 * par tick. Tous les scans sont bornés (cubes de 25³ maximum). Seuls des
 * blocs naturels ou de l'air sont remplacés ; chaque pose est mémorisée dans
 * la liste {@link CopyTracker.PlacedBlock} retournée pour restauration.
 * </p>
 */
public final class CopyBuilder {

    /** Résultat d'une construction : centre du site au sol et blocs posés (ordre de pose). */
    public record BuildResult(BlockPos site, List<CopyTracker.PlacedBlock> blocks) {
    }

    /** Budget maximal de blocs posés par copie (spécification : « petite, ≤ 60 blocs »). */
    private static final int MAX_COPY_BLOCKS = 60;
    /** Distance minimale/maximale du site au refuge (blocs, horizontale). */
    private static final int MIN_REFUGE_DIST = 15;
    private static final int MAX_REFUGE_DIST = 30;
    /** Distance minimale joueur–site exigée (garde-fou, en blocs). */
    private static final double MIN_PLAYER_DIST_SQR = 40.0 * 40.0;
    /** Distance minimale autres joueurs–site exigée (garde-fou, en blocs). */
    private static final double MIN_OTHERS_DIST_SQR = 30.0 * 30.0;
    /** Rayon du scan borné autour du refuge (matériaux, porte) et du garde-fou meubles. */
    private static final int REFUGE_SCAN_RADIUS = 12;
    /** Nombre de candidats de site essayés avant abandon. */
    private static final int SITE_ATTEMPTS = 10;
    /** Hauteur des murs de la coquille (couches). */
    private static final int WALL_HEIGHT = 3;
    /** Part minimale du sol sous l'emprise devant être solide (sur 25 cellules). */
    private static final int MIN_SOLID_FLOOR = 18;
    /** Probabilité que la copie comporte un mannequin. */
    private static final float MANNEQUIN_CHANCE = 0.5f;

    // Palette sombre FIXE du mannequin (gris anthracite / brun). Le skin du
    // joueur n'est jamais lu : confidentialité et cohérence horrifique.
    private static final int MANNEQUIN_CHARCOAL = 0x2E2E33;
    private static final int MANNEQUIN_DARK_BROWN = 0x3A2A1E;

    /** Blocs naturels remplaçables (terrain courant, surface ou grotte). */
    private static final List<Block> NATURAL_BLOCKS = List.of(
            Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.STONE, Blocks.DEEPSLATE,
            Blocks.ANDESITE, Blocks.DIORITE, Blocks.GRANITE, Blocks.TUFF,
            Blocks.SAND, Blocks.GRAVEL);

    /**
     * Substitutions « presque bonnes » des matériaux du refuge : proches mais
     * faux (chêne → bouleau, pierre → pierre taillée, etc.).
     */
    private static final Map<Block, Block> SUBSTITUTIONS = createSubstitutions();

    private static Map<Block, Block> createSubstitutions() {
        Map<Block, Block> map = new HashMap<>();
        map.put(Blocks.OAK_PLANKS, Blocks.BIRCH_PLANKS);
        map.put(Blocks.BIRCH_PLANKS, Blocks.SPRUCE_PLANKS);
        map.put(Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_PLANKS);
        map.put(Blocks.DARK_OAK_PLANKS, Blocks.OAK_PLANKS);
        map.put(Blocks.JUNGLE_PLANKS, Blocks.ACACIA_PLANKS);
        map.put(Blocks.ACACIA_PLANKS, Blocks.JUNGLE_PLANKS);
        map.put(Blocks.STONE, Blocks.STONE_BRICKS);
        map.put(Blocks.STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS);
        map.put(Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE);
        map.put(Blocks.SMOOTH_STONE, Blocks.POLISHED_DEEPSLATE);
        map.put(Blocks.BRICKS, Blocks.MUD_BRICKS);
        return map;
    }

    private CopyBuilder() {
    }

    /**
     * Tente de construire une copie imparfaite près du refuge du joueur.
     * Les garde-fous de site sont TOUJOURS appliqués (joueur à ≥ 40 blocs,
     * aucun autre joueur à < 30 blocs, blocs naturels uniquement, aucun
     * coffre/lit/four à moins de 12 blocs, jamais dans l'eau ou la lave).
     *
     * @return le résultat de construction, ou vide si aucun site valide
     */
    public static Optional<BuildResult> tryBuild(ServerLevel level, ServerPlayer player,
                                                 PlayerHorrorProfile profile) {
        List<BlockPos> homePoints = profile.homePoints();
        if (homePoints.isEmpty()) {
            return Optional.empty();
        }
        // « Refuge le plus fréquent » : le profil ne mémorise pas de compteur de
        // visites par refuge ; on prend le plus récemment promu (fin de liste),
        // mémoire la plus fraîche de l'Hôte.
        BlockPos refuge = homePoints.get(homePoints.size() - 1);
        if (!level.hasChunksAt(
                refuge.getX() - REFUGE_SCAN_RADIUS, refuge.getZ() - REFUGE_SCAN_RADIUS,
                refuge.getX() + REFUGE_SCAN_RADIUS, refuge.getZ() + REFUGE_SCAN_RADIUS)) {
            return Optional.empty();
        }
        RandomSource random = level.getRandom();

        // Références copiées depuis le refuge (scans bornés 25×25×25).
        Block wallMaterial = detectWallMaterial(level, refuge);
        Block doorType = detectRefugeDoor(level, refuge);
        Item plankItem = detectRefugePlanks(level, refuge);

        for (int attempt = 0; attempt < SITE_ATTEMPTS; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = MIN_REFUGE_DIST + random.nextDouble() * (MAX_REFUGE_DIST - MIN_REFUGE_DIST);
            int centerX = refuge.getX() + Mth.floor(Math.cos(angle) * distance);
            int centerZ = refuge.getZ() + Mth.floor(Math.sin(angle) * distance);
            BlockPos center = new BlockPos(centerX, refuge.getY(), centerZ);

            // Les refuges mémorisés peuvent se trouver dans des chunks déchargés.
            // Un événement d'ambiance ne doit jamais les charger sur le tick serveur.
            if (!level.hasChunkAt(center)) {
                continue;
            }

            if (center.distSqr(player.blockPosition()) < MIN_PLAYER_DIST_SQR) {
                continue; // le joueur verrait la construction : refusé
            }
            if (isOtherPlayerNear(level, player, center)) {
                continue; // jamais en présence d'autres joueurs
            }

            Optional<BlockPos> floor = findGround(level, center, refuge.getY());
            if (floor.isEmpty()) {
                continue;
            }
            BlockPos origin = floor.get().offset(-2, 0, -2); // emprise 5×5 centrée
            if (!level.hasChunksAt(
                    origin.getX() - REFUGE_SCAN_RADIUS, origin.getZ() - REFUGE_SCAN_RADIUS,
                    origin.getX() + 4 + REFUGE_SCAN_RADIUS, origin.getZ() + 4 + REFUGE_SCAN_RADIUS)) {
                continue;
            }
            if (!isFootprintClean(level, origin)) {
                continue; // blocs non naturels, liquides ou sol insuffisant
            }
            if (hasRefugeFurnitureNearby(level, origin)) {
                continue; // à moins de 12 blocs d'un coffre/lit/four du refuge
            }

            List<CopyTracker.PlacedBlock> placed = new ArrayList<>();
            buildShell(level, origin, wallMaterial, doorType, random, placed);
            placeTorch(level, origin, random, placed);
            placeChest(level, origin, plankItem, player, random, placed);
            placeSign(level, origin, random, placed);
            if (random.nextFloat() < MANNEQUIN_CHANCE) {
                placeMannequin(level, origin, random);
            }
            return Optional.of(new BuildResult(floor.get(), placed));
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------
    // Garde-fous de site
    // ------------------------------------------------------------------

    /** Vrai si un autre joueur est à moins de 30 blocs du site candidat. */
    private static boolean isOtherPlayerNear(ServerLevel level, ServerPlayer player, BlockPos site) {
        for (ServerPlayer other : level.players()) {
            if (other != player && other.blockPosition().distSqr(site) < MIN_OTHERS_DIST_SQR) {
                return true;
            }
        }
        return false;
    }

    /**
     * Cherche un sol sous le candidat : premier point (du haut vers le bas,
     * ±16 autour du y du refuge) dont le bloc inférieur a une face supérieure
     * solide et naturelle, avec de l'air ou du naturel au-dessus. Convient
     * donc à une surface ou au plancher d'une grotte large.
     */
    private static Optional<BlockPos> findGround(ServerLevel level, BlockPos center, int refugeY) {
        int minY = Math.max(level.getMinY() + 2, refugeY - 16);
        int maxY = Math.min(level.getMaxY() - 6, refugeY + 8);
        for (int y = maxY; y >= minY; y--) {
            BlockPos pos = new BlockPos(center.getX(), y, center.getZ());
            BlockState below = level.getBlockState(pos.below());
            if (!below.isFaceSturdy(level, pos.below(), Direction.UP) || !isNatural(below)) {
                continue;
            }
            if (isAirOrNatural(level.getBlockState(pos))
                    && isAirOrNatural(level.getBlockState(pos.above()))) {
                return Optional.of(pos);
            }
        }
        return Optional.empty();
    }

    /**
     * Vérifie que l'emprise complète (5×5, marge de 1 pour la paroi décalée,
     * hauteur 6 pour plafond flottant) ne contient que de l'air ou des blocs
     * naturels, aucun liquide, et repose sur un sol majoritairement solide.
     */
    private static boolean isFootprintClean(ServerLevel level, BlockPos origin) {
        int solidFloor = 0;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-1, -1, -1), origin.offset(5, -1, 5))) {
            BlockState state = level.getBlockState(pos);
            if (!state.getFluidState().isEmpty()) {
                return false;
            }
            if (state.isFaceSturdy(level, pos, Direction.UP) && isNatural(state)) {
                solidFloor++;
            }
        }
        if (solidFloor < MIN_SOLID_FLOOR) {
            return false;
        }
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-1, 0, -1), origin.offset(5, 6, 5))) {
            BlockState state = level.getBlockState(pos);
            if (!state.getFluidState().isEmpty() || !isAirOrNatural(state)) {
                return false;
            }
        }
        return true;
    }

    /** Vrai si un coffre, lit ou four se trouve à moins de 12 blocs de l'emprise. */
    private static boolean hasRefugeFurnitureNearby(ServerLevel level, BlockPos origin) {
        BlockPos from = origin.offset(-REFUGE_SCAN_RADIUS, -6, -REFUGE_SCAN_RADIUS);
        BlockPos to = origin.offset(4 + REFUGE_SCAN_RADIUS, 6, 4 + REFUGE_SCAN_RADIUS);
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            Block block = level.getBlockState(pos).getBlock();
            if (block instanceof ChestBlock || block instanceof BedBlock || block instanceof AbstractFurnaceBlock) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNatural(BlockState state) {
        return NATURAL_BLOCKS.contains(state.getBlock());
    }

    private static boolean isAirOrNatural(BlockState state) {
        return state.isAir() || isNatural(state);
    }

    // ------------------------------------------------------------------
    // Scans bornés du refuge
    // ------------------------------------------------------------------

    /** Matériau de substitution dominant autour du refuge (défaut : bouleau, imitation du chêne). */
    private static Block detectWallMaterial(ServerLevel level, BlockPos refuge) {
        Map<Block, Integer> counts = new HashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                refuge.offset(-REFUGE_SCAN_RADIUS, -6, -REFUGE_SCAN_RADIUS),
                refuge.offset(REFUGE_SCAN_RADIUS, 6, REFUGE_SCAN_RADIUS))) {
            Block block = level.getBlockState(pos).getBlock();
            if (SUBSTITUTIONS.containsKey(block)) {
                counts.merge(block, 1, Integer::sum);
            }
        }
        Block best = null;
        int bestCount = 0;
        for (Map.Entry<Block, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > bestCount) {
                bestCount = entry.getValue();
                best = entry.getKey();
            }
        }
        return best != null ? SUBSTITUTIONS.get(best) : Blocks.BIRCH_PLANKS;
    }

    /** Type de porte la plus proche du refuge (défaut : porte en chêne). */
    private static Block detectRefugeDoor(ServerLevel level, BlockPos refuge) {
        Block best = null;
        double bestDistSqr = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(
                refuge.offset(-REFUGE_SCAN_RADIUS, -6, -REFUGE_SCAN_RADIUS),
                refuge.offset(REFUGE_SCAN_RADIUS, 6, REFUGE_SCAN_RADIUS))) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof DoorBlock) {
                double distSqr = pos.distSqr(refuge);
                if (distSqr < bestDistSqr) {
                    bestDistSqr = distSqr;
                    best = state.getBlock();
                }
            }
        }
        return best != null ? best : Blocks.OAK_DOOR;
    }

    /** Bois de planches dominant du refuge pour l'imitation d'inventaire (défaut : chêne). */
    private static Item detectRefugePlanks(ServerLevel level, BlockPos refuge) {
        Map<Block, Integer> counts = new HashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                refuge.offset(-REFUGE_SCAN_RADIUS, -6, -REFUGE_SCAN_RADIUS),
                refuge.offset(REFUGE_SCAN_RADIUS, 6, REFUGE_SCAN_RADIUS))) {
            Block block = level.getBlockState(pos).getBlock();
            if (SUBSTITUTIONS.containsKey(block) && block != Blocks.STONE && block != Blocks.STONE_BRICKS
                    && block != Blocks.COBBLESTONE && block != Blocks.SMOOTH_STONE && block != Blocks.BRICKS) {
                counts.merge(block, 1, Integer::sum);
            }
        }
        Block best = null;
        int bestCount = 0;
        for (Map.Entry<Block, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > bestCount) {
                bestCount = entry.getValue();
                best = entry.getKey();
            }
        }
        return (best != null ? best : Blocks.OAK_PLANKS).asItem();
    }

    // ------------------------------------------------------------------
    // Construction (chaque pose est validée puis mémorisée)
    // ------------------------------------------------------------------

    /** Pose un bloc si la cellule est de l'air ou un bloc naturel, et le mémorise. */
    private static boolean place(ServerLevel level, BlockPos pos, BlockState state,
                                 List<CopyTracker.PlacedBlock> placed) {
        if (placed.size() >= MAX_COPY_BLOCKS) {
            return false;
        }
        BlockState previous = level.getBlockState(pos);
        if (!isAirOrNatural(previous)) {
            return false; // jamais sur une construction ou un objet du joueur
        }
        if (level.setBlock(pos, state, Block.UPDATE_ALL)) {
            placed.add(new CopyTracker.PlacedBlock(pos.immutable(), previous, state));
            return true;
        }
        return false;
    }

    /**
     * Coquille 5×5, murs de {@value #WALL_HEIGHT} couches, avec les deux
     * imperfections de proportions : une paroi décalée d'un bloc vers
     * l'extérieur, et un plafond partiel posé à hauteur 3 (normal) ou 4
     * (flottant, faux). La porte reprend le TYPE de celle du refuge, avec la
     * même orientation mais décalée d'un bloc le long de sa paroi.
     */
    private static void buildShell(ServerLevel level, BlockPos origin, Block wallMaterial,
                                   Block doorType, RandomSource random,
                                   List<CopyTracker.PlacedBlock> placed) {
        Direction[] horizontals = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        Direction doorSide = horizontals[random.nextInt(4)];
        Direction shiftedWall = horizontals[random.nextInt(4)];
        int doorIndex = 2 + (random.nextBoolean() ? 1 : -1); // décalé du centre de la paroi
        BlockState wall = wallMaterial.defaultBlockState();

        for (int y = 0; y < WALL_HEIGHT; y++) {
            for (int i = 0; i < 5; i++) {
                for (Direction side : horizontals) {
                    // Cellule de paroi : bord nord (z=0), sud (z=4), ouest (x=0) ou est (x=4).
                    int x = side == Direction.WEST ? 0 : side == Direction.EAST ? 4 : i;
                    int z = side == Direction.NORTH ? 0 : side == Direction.SOUTH ? 4 : i;
                    if (side == doorSide && i == doorIndex && y < 2) {
                        continue; // ouverture de porte (2 haut)
                    }
                    BlockPos pos = origin.offset(x, y, z);
                    if (side == shiftedWall) {
                        pos = pos.relative(shiftedWall); // paroi décalée d'un bloc
                    }
                    place(level, pos, wall, placed);
                }
            }
        }

        // Porte : type du refuge, orientation frontale de la paroi, décalée d'un bloc.
        BlockPos doorBase = doorCell(origin, doorSide, doorIndex);
        BlockState door = doorType.defaultBlockState()
                .setValue(DoorBlock.FACING, doorSide)
                .setValue(DoorBlock.HINGE, random.nextBoolean() ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT)
                .setValue(DoorBlock.OPEN, false)
                .setValue(DoorBlock.POWERED, false);
        place(level, doorBase, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), placed);
        place(level, doorBase.above(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), placed);

        // Plafond partiel (3×2, excentré) : à hauteur 3, ou flottant à 4 (faux).
        int ceilingY = random.nextBoolean() ? WALL_HEIGHT : WALL_HEIGHT + 1;
        for (int x = 1; x <= 3; x++) {
            for (int z = 0; z <= 1; z++) {
                place(level, origin.offset(x, ceilingY, z), wall, placed);
            }
        }
    }

    /** Cellule de base de la porte sur la paroi donnée, à l'index donné le long de celle-ci. */
    private static BlockPos doorCell(BlockPos origin, Direction side, int index) {
        return switch (side) {
            case NORTH -> origin.offset(index, 0, 0);
            case SOUTH -> origin.offset(index, 0, 4);
            case WEST -> origin.offset(0, 0, index);
            default -> origin.offset(4, 0, index); // EAST
        };
    }

    /** Torche murale intérieure « presque » bien placée (décalée d'un bloc), sinon torche au sol. */
    private static void placeTorch(ServerLevel level, BlockPos origin, RandomSource random,
                                   List<CopyTracker.PlacedBlock> placed) {
        // Angle intérieur presque correct : contre la paroi ouest, un bloc trop loin du coin.
        BlockPos torchPos = origin.offset(1, 1, 3);
        Direction facing = Direction.EAST; // la torche pointe vers la salle, dos à la paroi ouest
        if (level.getBlockState(torchPos).isAir()
                && WallTorchBlock.canSurvive(level, torchPos, facing)) {
            if (place(level, torchPos,
                    Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, facing), placed)) {
                return;
            }
        }
        BlockPos floorTorch = origin.offset(3, 0, 3);
        if (level.getBlockState(floorTorch).isAir()
                && level.getBlockState(floorTorch.below()).isFaceSturdy(level, floorTorch.below(), Direction.UP)) {
            place(level, floorTorch, Blocks.TORCH.defaultBlockState(), placed);
        }
    }

    /**
     * Coffre « imitation d'inventaire » : contenu factice banal aux quantités
     * étranges (13 planches, 1 torche, 7 graines, 3 charbons) — jamais rien
     * de valeur.
     * <p>
     * 0.5.0 — objets « presque justes » : jusqu'à 3 copies d'objets de la
     * barre d'action du joueur sous forme de papiers factices, chacune renommée avec
     * une erreur d'un caractère ({@link #almostRightName}). Il apprend vos
     * outils comme il apprend vos murs : imparfaitement.
     * </p>
     */
    private static void placeChest(ServerLevel level, BlockPos origin, Item plankItem,
                                   ServerPlayer player, RandomSource random,
                                   List<CopyTracker.PlacedBlock> placed) {
        BlockPos chestPos = origin.offset(1, 0, 1);
        if (!level.getBlockState(chestPos).isAir()) {
            return;
        }
        Direction facing = Direction.getNearest(1, 0, 1, Direction.SOUTH); // vers le centre de la pièce
        if (!place(level, chestPos,
                Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing), placed)) {
            return;
        }
        if (level.getBlockEntity(chestPos) instanceof Container container) {
            container.setItem(0, new ItemStack(plankItem, 13));
            container.setItem(3, new ItemStack(Items.TORCH, 1));
            container.setItem(5, new ItemStack(Items.WHEAT_SEEDS, 7));
            container.setItem(7, new ItemStack(Items.COAL, 3));
            fillAlmostRightItems(container, player, random);
        }
    }

    /**
     * Dépose dans les emplacements libres 9–17 jusqu'à 3 imitations d'objets
     * de la barre d'action du joueur (slots 0–8 de l'inventaire), renommées
     * « presque juste ». Le support reste volontairement du papier : copier le
     * véritable type d'objet permettait de dupliquer des équipements rares.
     */
    private static void fillAlmostRightItems(Container container, ServerPlayer player,
                                             RandomSource random) {
        int placedCount = 0;
        int slot = 9;
        for (int hotbar = 0; hotbar < 9 && placedCount < 3 && slot < 18; hotbar++) {
            ItemStack original = player.getInventory().getItem(hotbar);
            if (original.isEmpty() || random.nextFloat() < 0.4f) {
                continue;
            }
            ItemStack imitation = new ItemStack(Items.PAPER, 1);
            imitation.set(DataComponents.CUSTOM_NAME,
                    Component.literal(almostRightName(original.getHoverName().getString(), random)));
            container.setItem(slot, imitation);
            slot += 1 + random.nextInt(2); // jamais rangées proprement
            placedCount++;
        }
    }

    /**
     * Déforme un nom d'objet d'exactement un caractère : lettre doublée,
     * deux lettres adjacentes échangées, ou point final surnuméraire.
     * « Pioche en fer » → « Pioche en ferr ». Une seule erreur : celle
     * qu'on remarque à la deuxième lecture, pas à la première.
     */
    private static String almostRightName(String name, RandomSource random) {
        if (name.isEmpty()) {
            return name;
        }
        int mode = random.nextInt(3);
        if (mode == 0 || name.length() < 2) {
            int at = random.nextInt(name.length());
            return name.substring(0, at + 1) + name.charAt(at) + name.substring(at + 1);
        }
        if (mode == 1) {
            int at = random.nextInt(name.length() - 1);
            return name.substring(0, at) + name.charAt(at + 1) + name.charAt(at)
                    + name.substring(at + 2);
        }
        return name + ".";
    }

    /** Panneau à la phrase incomplète (clé lang penumbra.sign.copy_1..4), orientation « presque » bonne. */
    private static void placeSign(ServerLevel level, BlockPos origin, RandomSource random,
                                  List<CopyTracker.PlacedBlock> placed) {
        BlockPos signPos = origin.offset(3, 0, 1);
        if (!level.getBlockState(signPos).isAir()
                || !level.getBlockState(signPos.below()).isFaceSturdy(level, signPos.below(), Direction.UP)) {
            return;
        }
        // « Presque » face à l'entrée : rotation idéale vers le centre, décalée d'un cran.
        int rotation = Mth.floor(((Direction.SOUTH.toYRot() + 180.0f) * 16.0f / 360.0f) + 0.5f) & 15;
        rotation = (rotation + (random.nextBoolean() ? 1 : -1)) & 15;
        BlockState sign = Blocks.OAK_SIGN.defaultBlockState()
                .setValue(StandingSignBlock.ROTATION, rotation);
        if (!place(level, signPos, sign, placed)) {
            return;
        }
        if (level.getBlockEntity(signPos) instanceof SignBlockEntity signEntity) {
            // API 26.1.2 (vérifié par javap) : SignText#setMessage(int, Component)
            // puis SignBlockEntity#setText(SignText, boolean isFrontText).
            String key = "penumbra.sign.copy_" + (1 + random.nextInt(4));
            signEntity.setText(signEntity.getFrontText()
                    .setMessage(1, Component.translatable(key)), true);
            signEntity.setChanged();
        }
    }

    /**
     * Mannequin optionnel : armor stand à l'armure de cuir teintée d'une
     * palette sombre FIXE (gris anthracite / brun). Le skin du joueur n'est
     * jamais lu — confidentialité garantie par construction.
     * API teinture 26.1.2 (vérifiée par javap) : DataComponents.DYED_COLOR +
     * record {@code DyedItemColor(int rgb)}.
     */
    private static void placeMannequin(ServerLevel level, BlockPos origin, RandomSource random) {
        BlockPos pos = origin.offset(2, 0, 2);
        if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()) {
            return;
        }
        ArmorStand stand = new ArmorStand(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        float yaw = random.nextFloat() * 360.0f;
        stand.setYRot(yaw);
        stand.setYHeadRot(yaw);
        stand.setYBodyRot(yaw);
        stand.setNoBasePlate(true);
        stand.setItemSlot(EquipmentSlot.HEAD, dyed(new ItemStack(Items.LEATHER_HELMET), MANNEQUIN_CHARCOAL));
        stand.setItemSlot(EquipmentSlot.CHEST, dyed(new ItemStack(Items.LEATHER_CHESTPLATE), MANNEQUIN_CHARCOAL));
        stand.setItemSlot(EquipmentSlot.LEGS, dyed(new ItemStack(Items.LEATHER_LEGGINGS), MANNEQUIN_DARK_BROWN));
        stand.setItemSlot(EquipmentSlot.FEET, dyed(new ItemStack(Items.LEATHER_BOOTS), MANNEQUIN_DARK_BROWN));
        stand.addTag(CopyTracker.MANNEQUIN_TAG);
        level.addFreshEntity(stand);
    }

    /** Teinte une pièce de cuir avec la couleur RGB fixe donnée. */
    private static ItemStack dyed(ItemStack stack, int rgb) {
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb));
        return stack;
    }
}

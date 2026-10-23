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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/** Bounded, recognizable room copies. Source states are read once; source worlds/inventories are never written. */
public final class CopyBuilder {
    public record BuildResult(BlockPos site, List<CopyTracker.PlacedBlock> blocks) {}

    private static final int MIN_REFUGE_DIST = 18;
    private static final int MAX_REFUGE_DIST = 36;
    private static final double MIN_PLAYER_DIST_SQR = 40.0 * 40.0;
    private static final double MIN_OTHERS_DIST_SQR = 30.0 * 30.0;
    private static final int REFUGE_SCAN_RADIUS = 12;
    private static final int SITE_ATTEMPTS = 10;
    private static final float MANNEQUIN_CHANCE = 0.5f;
    private static final int MANNEQUIN_CHARCOAL = 0x2E2E33;
    private static final int MANNEQUIN_DARK_BROWN = 0x3A2A1E;
    private static final Set<Block> NATURAL_BLOCKS = Set.of(
            Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.STONE, Blocks.DEEPSLATE,
            Blocks.ANDESITE, Blocks.DIORITE, Blocks.GRANITE, Blocks.TUFF,
            Blocks.SAND, Blocks.GRAVEL);

    private CopyBuilder() {}

    public static Optional<BuildResult> tryBuild(ServerLevel level, ServerPlayer player, PlayerHorrorProfile profile) {
        List<BlockPos> homes = profile.homePoints();
        if (homes.isEmpty()) return Optional.empty();
        BlockPos refuge = homes.getLast();
        if (!level.hasChunksAt(refuge.getX() - REFUGE_SCAN_RADIUS, refuge.getZ() - REFUGE_SCAN_RADIUS,
                refuge.getX() + REFUGE_SCAN_RADIUS, refuge.getZ() + REFUGE_SCAN_RADIUS)) return Optional.empty();
        // One 25×13×25 read-only snapshot replaces the old three repeated scans.
        Map<BlockPos, BlockState> sample = new LinkedHashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(refuge.offset(-12, -6, -12), refuge.offset(12, 6, 12))) {
            if (pos.getY() >= level.getMinY() && pos.getY() < level.getMaxY()) sample.put(pos.immutable(), level.getBlockState(pos));
        }
        CopyPlan.Source source = CopyPlan.measure(sample, refuge);
        CopyPlan.Layout plan = CopyPlan.build(source);
        RandomSource random = level.getRandom();
        for (int attempt = 0; attempt < SITE_ATTEMPTS; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = MIN_REFUGE_DIST + random.nextDouble() * (MAX_REFUGE_DIST - MIN_REFUGE_DIST);
            BlockPos center = new BlockPos(refuge.getX() + Mth.floor(Math.cos(angle) * distance), source.floorY(),
                    refuge.getZ() + Mth.floor(Math.sin(angle) * distance));
            if (!level.hasChunkAt(center) || center.distSqr(player.blockPosition()) < MIN_PLAYER_DIST_SQR
                    || isOtherPlayerNear(level, player, center)) continue;
            Optional<BlockPos> floor = findGround(level, center, source.floorY(), plan.height());
            if (floor.isEmpty()) continue;
            if (floor.get().distSqr(player.blockPosition()) < MIN_PLAYER_DIST_SQR
                    || isOtherPlayerNear(level, player, floor.get())) continue;
            BlockPos origin = floor.get().offset(-plan.width() / 2, 0, -plan.depth() / 2);
            if (!level.hasChunksAt(origin.getX() - REFUGE_SCAN_RADIUS, origin.getZ() - REFUGE_SCAN_RADIUS,
                    origin.getX() + plan.width() - 1 + REFUGE_SCAN_RADIUS,
                    origin.getZ() + plan.depth() - 1 + REFUGE_SCAN_RADIUS)) continue;
            if (!isFootprintClean(level, origin, plan) || hasRefugeFurnitureNearby(level, origin, plan)) continue;
            Optional<List<CopyTracker.PlacedBlock>> placed = apply(level, origin, plan);
            if (placed.isEmpty()) continue;
            if (plan.chest() != null && level.getBlockEntity(origin.offset(plan.chest())) instanceof Container chest) {
                // Only paper silhouettes; no item types, components or inventory contents are copied.
                fillAlmostRightItems(chest, player, random);
            }
            fillSign(level, plan.sign() == null ? null : origin.offset(plan.sign()), random);
            if (plan.mannequin() != null && random.nextFloat() < MANNEQUIN_CHANCE) {
                placeMannequin(level, origin.offset(plan.mannequin()), random);
            }
            return Optional.of(new BuildResult(floor.get(), placed.get()));
        }
        return Optional.empty();
    }

    private static boolean isOtherPlayerNear(ServerLevel level, ServerPlayer player, BlockPos site) {
        for (ServerPlayer other : level.players()) {
            if (other != player && other.blockPosition().distSqr(site) < MIN_OTHERS_DIST_SQR) return true;
        }
        return false;
    }

    private static Optional<BlockPos> findGround(ServerLevel level, BlockPos center, int refugeY, int height) {
        int minY = Math.max(level.getMinY() + 2, refugeY - 16);
        int maxY = Math.min(level.getMaxY() - height - 2, refugeY + 8);
        for (int y = maxY; y >= minY; y--) {
            BlockPos pos = new BlockPos(center.getX(), y, center.getZ());
            BlockState below = level.getBlockState(pos.below());
            if (below.isFaceSturdy(level, pos.below(), Direction.UP) && isNatural(below)
                    && level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()) {
                return Optional.of(pos);
            }
        }
        return Optional.empty();
    }

    private static boolean isFootprintClean(ServerLevel level, BlockPos origin, CopyPlan.Layout plan) {
        int solidFloor = 0;
        for (int x = 0; x < plan.width(); x++) for (int z = 0; z < plan.depth(); z++) {
            BlockPos pos = origin.offset(x, -1, z);
            BlockState state = level.getBlockState(pos);
            if (state.isFaceSturdy(level, pos, Direction.UP) && isNatural(state)) solidFloor++;
        }
        // Count the actual footprint, not its larger surrounding margin (the previous check did the latter).
        if (solidFloor * 4 < plan.width() * plan.depth() * 3) return false;
        // The two-block porch must meet existing open, walkable terrain. No sealed doorway into a hillside.
        BlockPos approach = origin.offset(plan.entrance()).relative(plan.entranceSide(), 3);
        if (!level.getWorldBorder().isWithinBounds(approach)
                || !level.getBlockState(approach).isAir() || !level.getBlockState(approach.above()).isAir()
                || !level.getBlockState(approach.below()).isFaceSturdy(level, approach.below(), Direction.UP)) return false;
        for (BlockPos relative : plan.blocks().keySet()) {
            BlockPos pos = origin.offset(relative);
            BlockState state = level.getBlockState(pos);
            if (pos.getY() < level.getMinY() || pos.getY() >= level.getMaxY()
                    || !level.getWorldBorder().isWithinBounds(pos) || !state.getFluidState().isEmpty()
                    || !isAirOrNatural(state) || level.getBlockEntity(pos) != null) return false;
        }
        return true;
    }

    private static boolean hasRefugeFurnitureNearby(ServerLevel level, BlockPos origin, CopyPlan.Layout plan) {
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-REFUGE_SCAN_RADIUS, -6, -REFUGE_SCAN_RADIUS),
                origin.offset(plan.width() - 1 + REFUGE_SCAN_RADIUS, 6, plan.depth() - 1 + REFUGE_SCAN_RADIUS))) {
            Block block = level.getBlockState(pos).getBlock();
            if (block instanceof ChestBlock || block instanceof BedBlock || block instanceof AbstractFurnaceBlock
                    || block == Blocks.BARREL) return true;
        }
        return false;
    }

    static boolean isNatural(BlockState state) { return NATURAL_BLOCKS.contains(state.getBlock()); }
    static boolean isAirOrNatural(BlockState state) { return state.isAir() || isNatural(state); }

    /** Complete preflight and rollback prevent truncated rooms, including when a placement is refused. */
    private static Optional<List<CopyTracker.PlacedBlock>> apply(ServerLevel level, BlockPos origin, CopyPlan.Layout plan) {
        if (plan.blocks().size() > CopyTracker.MAX_BLOCKS_PER_COPY) return Optional.empty();
        List<CopyTracker.PlacedBlock> placed = new ArrayList<>();
        for (var entry : plan.blocks().entrySet()) {
            BlockPos pos = origin.offset(entry.getKey());
            BlockState before = level.getBlockState(pos);
            BlockState after = entry.getValue();
            if (before.equals(after)) continue;
            // Known shapes avoid half-doors/beds or a lamp dropping while its counterpart is being placed.
            if (!isAirOrNatural(before) || level.getBlockEntity(pos) != null
                    || !level.setBlock(pos, after, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE)) {
                for (int i = placed.size() - 1; i >= 0; i--) {
                    CopyTracker.PlacedBlock block = placed.get(i);
                    if (level.getBlockState(block.pos()).equals(block.placedState())) {
                        level.setBlock(block.pos(), block.previousState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    }
                }
                return Optional.empty();
            }
            placed.add(new CopyTracker.PlacedBlock(pos.immutable(), before, after));
        }
        return Optional.of(List.copyOf(placed));
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
            CopyTracker.markImitation(imitation);
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

    private static void fillSign(ServerLevel level, BlockPos pos, RandomSource random) {
        if (pos != null && level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
            String key = "penumbra.sign.copy_" + (1 + random.nextInt(4));
            sign.setText(sign.getFrontText().setMessage(1, Component.translatable(key)), true);
            sign.setChanged();
        }
    }

    /**
     * Mannequin optionnel : armor stand à l'armure de cuir teintée d'une
     * palette sombre FIXE (gris anthracite / brun). Le skin du joueur n'est
     * jamais lu — confidentialité garantie par construction.
     * API teinture 26.1.2 (vérifiée par javap) : DataComponents.DYED_COLOR +
     * record {@code DyedItemColor(int rgb)}.
     */
    private static void placeMannequin(ServerLevel level, BlockPos pos, RandomSource random) {
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

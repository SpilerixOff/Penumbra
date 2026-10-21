package fr.penumbra.event.underground;

import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.data.PenumbraPersistentState;
import fr.penumbra.director.PlayerHorrorProfile;
import fr.penumbra.director.TensionState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Galerie décalée (Phase 3, {@code shifted_gallery}).
 * <p>
 * 3 à 6 blocs d'une galerie déjà visitée (cellule fréquente du profil) sont remplacés
 * par des variantes proches : stone→deepslate, gravel→cobblestone, dirt→coarse dirt,
 * andesite→tuff, cobblestone→mossy cobblestone. Chaque bloc est TRACKÉ et restauré
 * après 20 à 40 minutes SI encore intact (jamais d'écrasement d'une modification du
 * joueur). Sécurité : événement annulé si un coffre, un four ou un lit se trouve à
 * moins de {@value #SAFE_CONTAINER_RADIUS} blocs du centre de la cellule (scan borné
 * 25³ avec sortie anticipée). Exige {@code worldModificationEnabled}.
 * Gain de tension : +3.
 * </p>
 */
public final class ShiftedGalleryEvent extends AbstractUndergroundEvent {

    /** Rayon de protection autour du centre : jamais de décalage près d'un coffre/four/lit. */
    private static final int SAFE_CONTAINER_RADIUS = 12;
    /** Rayon Chebyshev du scan de blocs à décaler autour du centre de cellule : 11³. */
    private static final int SCAN_RADIUS = 5;
    /** Durée minimale avant restauration (20 min). */
    private static final long MIN_LIFE_TICKS = 24_000L;
    /** Durée maximale avant restauration (40 min). */
    private static final long MAX_LIFE_TICKS = 48_000L;

    /** Variantes proches autorisées (origine → décalage). */
    private static final Map<Block, Block> SHIFT = Map.of(
            Blocks.STONE, Blocks.DEEPSLATE,
            Blocks.GRAVEL, Blocks.COBBLESTONE,
            Blocks.DIRT, Blocks.COARSE_DIRT,
            Blocks.ANDESITE, Blocks.TUFF,
            Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE);

    @Override
    public String id() {
        return "shifted_gallery";
    }

    @Override
    public int weight(TensionState state) {
        return switch (state) {
            case CALM -> 4;
            case UNEASE -> 6;
            case WATCHED -> 5;
            case MANIFESTATION -> 3;
            case HUNT -> 1;
            case CRISIS -> 1;
            case FALSE_CALM -> 7;
        };
    }

    @Override
    public int cooldownTicks() {
        return 5400;
    }

    @Override
    public boolean run(ServerPlayer player) {
        if (!PenumbraConfig.get().worldModificationEnabled) {
            return false;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        UndergroundRestorationTracker.checkDeadlines(level); // maintenance amortie
        PlayerHorrorProfile profile = PenumbraPersistentState.get(level.getServer()).profile(player.getUUID());
        RandomSource random = player.getRandom();
        Optional<BlockPos> cell = profile.randomFrequentCell(random);
        if (cell.isEmpty()) {
            return false;
        }
        BlockPos center = cell.get();
        if (!level.hasChunksAt(
                center.getX() - SAFE_CONTAINER_RADIUS, center.getZ() - SAFE_CONTAINER_RADIUS,
                center.getX() + SAFE_CONTAINER_RADIUS, center.getZ() + SAFE_CONTAINER_RADIUS)) {
            return false;
        }
        if (hasContainerNearby(level, center)) {
            return false; // zone habitée : jamais de décalage près d'un coffre/four/lit
        }

        // Candidats : blocs décalables exposés à l'air (parois visibles de la galerie).
        List<BlockPos> candidates = new ArrayList<>();
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
            for (int dy = -SCAN_RADIUS; dy <= SCAN_RADIUS; dy++) {
                for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (SHIFT.containsKey(state.getBlock()) && hasAirNeighbor(level, pos)) {
                        candidates.add(pos);
                    }
                }
            }
        }
        if (candidates.size() < 3) {
            return false;
        }

        int count = Math.min(candidates.size(), 3 + random.nextInt(4)); // 3..6
        // 26.1 : RandomSource n'a pas nextLong(bound) ; tirage en double.
        long deadline = UndergroundRestorationTracker.gameTime(level) + MIN_LIFE_TICKS
                + (long) (random.nextDouble() * (double) (MAX_LIFE_TICKS - MIN_LIFE_TICKS));
        UndergroundRestorationTracker tracker = UndergroundRestorationTracker.get(level.getServer());
        for (int i = 0; i < count; i++) {
            BlockPos pos = candidates.remove(random.nextInt(candidates.size()));
            BlockState original = level.getBlockState(pos);
            BlockState shifted = SHIFT.get(original.getBlock()).defaultBlockState();
            level.setBlock(pos, shifted, 3);
            tracker.add(level, new UndergroundRestorationTracker.Entry(
                    pos.immutable(), level.dimension(), original, shifted,
                    UndergroundRestorationTracker.TYPE_GALLERY, deadline, player.getUUID().toString()));
        }
        return true;
    }

    /**
     * Vrai si un coffre (toutes variantes), un four (toutes variantes) ou un lit se
     * trouve dans le cube de rayon {@value #SAFE_CONTAINER_RADIUS} autour du centre.
     * Scan borné 25³ avec sortie anticipée au premier conteneur trouvé.
     */
    private static boolean hasContainerNearby(ServerLevel level, BlockPos center) {
        for (int dx = -SAFE_CONTAINER_RADIUS; dx <= SAFE_CONTAINER_RADIUS; dx++) {
            for (int dy = -SAFE_CONTAINER_RADIUS; dy <= SAFE_CONTAINER_RADIUS; dy++) {
                for (int dz = -SAFE_CONTAINER_RADIUS; dz <= SAFE_CONTAINER_RADIUS; dz++) {
                    BlockState state = level.getBlockState(center.offset(dx, dy, dz));
                    Block block = state.getBlock();
                    if (block instanceof ChestBlock || block instanceof AbstractFurnaceBlock
                            || block instanceof BedBlock) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}

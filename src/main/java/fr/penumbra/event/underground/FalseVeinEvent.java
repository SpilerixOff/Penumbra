package fr.penumbra.event.underground;

import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.data.PenumbraPersistentState;
import fr.penumbra.director.PlayerHorrorProfile;
import fr.penumbra.director.TensionState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Faux filon de minerai (Phase 3, {@code false_vein}).
 * <p>
 * Dans une galerie déjà visitée (cellule fréquente du profil), 2 à 4 blocs de minerai
 * apparaissent sur des parois rocheuses exposées à l'air : charbon (60 %) ou fer (40 %),
 * variantes deepslate sous Y 0. Chaque bloc posé est TRACKÉ dans
 * {@link UndergroundRestorationTracker} avec une échéance de 15 à 30 minutes : passé ce
 * délai, le minerai redevient la roche d'origine — SEULEMENT s'il est resté intact
 * (jamais d'écrasement : si le joueur l'a miné ou remplacé, rien n'est restauré).
 * Exige {@code worldModificationEnabled}. Aucun coffre/four/lit à proximité n'est touché :
 * seuls des blocs de roche sans entité sont remplacés. Gain de tension : +3.
 * </p>
 */
public final class FalseVeinEvent extends AbstractUndergroundEvent {

    /** Rayon Chebyshev du scan de parois autour du centre de cellule : 13³ = 2 197 positions. */
    private static final int SCAN_RADIUS = 6;
    /** Durée minimale avant restauration (15 min). */
    private static final long MIN_LIFE_TICKS = 18_000L;
    /** Durée maximale avant restauration (30 min). */
    private static final long MAX_LIFE_TICKS = 36_000L;

    @Override
    public String id() {
        return "false_vein";
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
            return false; // aucune galerie mémorisée : événement sans effet
        }

        // Parois rocheuses exposées à l'air autour du centre de la cellule (scan borné 13³).
        List<BlockPos> walls = new ArrayList<>();
        BlockPos center = cell.get();
        int loadedRadius = SCAN_RADIUS + 1; // +1 pour les voisins d'air testés sur le bord
        if (!level.hasChunksAt(
                center.getX() - loadedRadius, center.getZ() - loadedRadius,
                center.getX() + loadedRadius, center.getZ() + loadedRadius)) {
            return false;
        }
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
            for (int dy = -SCAN_RADIUS; dy <= SCAN_RADIUS; dy++) {
                for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (pos.getY() < level.getMinY() + 1 || pos.getY() > level.getMaxY() - 1) {
                        continue;
                    }
                    if (isSoftRock(level.getBlockState(pos)) && hasAirNeighbor(level, pos)) {
                        walls.add(pos);
                    }
                }
            }
        }
        if (walls.size() < 2) {
            return false;
        }

        int count = Math.min(walls.size(), 2 + random.nextInt(3)); // 2..4
        // 26.1 : RandomSource n'a pas nextLong(bound) ; tirage en double.
        long deadline = UndergroundRestorationTracker.gameTime(level) + MIN_LIFE_TICKS
                + (long) (random.nextDouble() * (double) (MAX_LIFE_TICKS - MIN_LIFE_TICKS));
        UndergroundRestorationTracker tracker = UndergroundRestorationTracker.get(level.getServer());
        for (int i = 0; i < count; i++) {
            BlockPos pos = walls.remove(random.nextInt(walls.size()));
            BlockState original = level.getBlockState(pos);
            BlockState ore = pickOre(pos.getY(), random).defaultBlockState();
            level.setBlock(pos, ore, 3);
            tracker.add(level, new UndergroundRestorationTracker.Entry(
                    pos.immutable(), level.dimension(), original, ore,
                    UndergroundRestorationTracker.TYPE_VEIN, deadline, player.getUUID().toString()));
        }
        return true;
    }

    /** Charbon (60 %) ou fer (40 %) ; variantes deepslate sous Y 0. */
    private static Block pickOre(int y, RandomSource random) {
        boolean coal = random.nextFloat() < 0.6f;
        if (y < 0) {
            return coal ? Blocks.DEEPSLATE_COAL_ORE : Blocks.DEEPSLATE_IRON_ORE;
        }
        return coal ? Blocks.COAL_ORE : Blocks.IRON_ORE;
    }
}

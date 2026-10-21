package fr.penumbra.event.underground;

import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.data.PenumbraPersistentState;
import fr.penumbra.director.PlayerHorrorProfile;
import fr.penumbra.director.TensionState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * Torche étrangère (Phase 3, {@code foreign_torch}).
 * <p>
 * Une torche murale que le joueur n'a JAMAIS posée apparaît sur une paroi d'une zone
 * déjà visitée (cellule fréquente du profil). Elle est PERMANENTE : c'est un indice,
 * purement additif, retirable par le joueur ; aucune restauration automatique
 * ({@link UndergroundRestorationTracker#NO_DEADLINE}). Cap global de
 * {@value #MAX_TORCHES_PER_PLAYER} torches par joueur : au-delà, la plus ancienne est
 * évincée (retirée si encore intacte) via le tracker FIFO. Exige
 * {@code worldModificationEnabled}. Gain de tension : +4.
 * </p>
 */
public final class ForeignTorchEvent extends AbstractUndergroundEvent {

    /** Nombre maximal de torches étrangères simultanées par joueur (cap global). */
    private static final int MAX_TORCHES_PER_PLAYER = 8;
    /** Rayon Chebyshev du scan d'emplacement autour du centre de cellule. */
    private static final int SCAN_RADIUS = 6;
    /** Essais aléatoires d'emplacement avant abandon. */
    private static final int ATTEMPTS = 32;

    /** Emplacement validé : bloc d'air + direction depuis la paroi porteuse vers l'air. */
    private record Spot(BlockPos pos, Direction facing) {
    }

    @Override
    public String id() {
        return "foreign_torch";
    }

    @Override
    public int weight(TensionState state) {
        return switch (state) {
            case CALM -> 3;
            case UNEASE -> 5;
            case WATCHED -> 7;
            case MANIFESTATION -> 4;
            case HUNT -> 1;
            case CRISIS -> 1;
            case FALSE_CALM -> 8;
        };
    }

    @Override
    public int cooldownTicks() {
        return 7200;
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

        Spot spot = findTorchSpot(level, cell.get(), random);
        if (spot == null) {
            return false; // aucune paroi valide : événement sans effet
        }

        BlockState original = level.getBlockState(spot.pos()); // air (jamais écrasé)
        BlockState torchState = Blocks.WALL_TORCH.defaultBlockState()
                .setValue(WallTorchBlock.FACING, spot.facing());

        UndergroundRestorationTracker tracker = UndergroundRestorationTracker.get(level.getServer());
        String uuid = player.getUUID().toString();
        // Cap global : si la plus ancienne torche est dans un chunk indisponible,
        // son retrait reste en attente et cette nouvelle apparition est annulée.
        // Cela évite une boucle infinie et garantit qu'aucun bloc posé ne soit oublié.
        while (tracker.countFor(uuid, UndergroundRestorationTracker.TYPE_TORCH) >= MAX_TORCHES_PER_PLAYER) {
            if (!tracker.evictOldestFor(uuid, UndergroundRestorationTracker.TYPE_TORCH, level)) {
                return false;
            }
        }

        level.setBlock(spot.pos(), torchState, 3);
        tracker.add(level, new UndergroundRestorationTracker.Entry(
                spot.pos().immutable(), level.dimension(), original, torchState,
                UndergroundRestorationTracker.TYPE_TORCH,
                UndergroundRestorationTracker.NO_DEADLINE, uuid));

        return true;
    }

    /**
     * Cherche un emplacement de torche murale près du centre de cellule : bloc d'air dont
     * un voisin horizontal est une paroi de roche pleine porteuse ({@code canSurvive}
     * vérifié). {@value #ATTEMPTS} essais aléatoires dans un cube borné (13³), aucun
     * scan exhaustif.
     */
    private static Spot findTorchSpot(ServerLevel level, BlockPos center, RandomSource random) {
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            int dx = random.nextInt(SCAN_RADIUS * 2 + 1) - SCAN_RADIUS;
            int dy = random.nextInt(SCAN_RADIUS * 2 + 1) - SCAN_RADIUS;
            int dz = random.nextInt(SCAN_RADIUS * 2 + 1) - SCAN_RADIUS;
            BlockPos pos = center.offset(dx, dy, dz);
            Direction facing = wallFacing(level, pos);
            if (facing != null) {
                return new Spot(pos, facing);
            }
        }
        return null;
    }

    /**
     * Retourne la direction depuis une paroi rocheuse voisine vers ce bloc d'air,
     * ou {@code null} si le bloc n'est pas un emplacement de torche murale valide.
     */
    private static Direction wallFacing(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos) || !level.getBlockState(pos).isAir()) {
            return null;
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (isHardRock(level.getBlockState(pos.relative(direction.getOpposite())))) {
                BlockState candidate = Blocks.WALL_TORCH.defaultBlockState()
                        .setValue(WallTorchBlock.FACING, direction);
                if (candidate.canSurvive(level, pos)) {
                    return direction;
                }
            }
        }
        return null;
    }
}

package fr.penumbra.event.underground;

import fr.penumbra.data.PenumbraPersistentState;
import fr.penumbra.director.HorrorDirector;
import fr.penumbra.director.TensionState;
import fr.penumbra.network.PenumbraNetworking;
import fr.penumbra.registry.PenumbraSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Respiration dans une galerie sans issue (Phase 3, {@code deadend_breath}).
 * <p>
 * Détection simple d'impasse : au plus {@value #MAX_OPEN_DIRECTIONS} directions
 * horizontales ouvertes parmi les 4, à 3 blocs du joueur (deux blocs d'air de haut).
 * Si le joueur est bien dans un cul-de-sac, une respiration proche
 * ({@code BREATH_BEHIND}, volume 0.22–0.3, pitch 0.75–0.85) est jouée 4 à 6 fois
 * toutes les 35 à 45 ticks (5 à 10 s au total), positionnée dans une direction
 * FERMÉE (dans la roche, derrière la paroi la plus proche). Aucune modification du
 * monde ; si la galerie n'est pas une impasse, événement sans effet.
 * Gain de tension : +5.
 * </p>
 */
public final class DeadendBreathEvent extends AbstractUndergroundEvent {

    /** Nombre maximal de directions ouvertes qualifiant une impasse. */
    private static final int MAX_OPEN_DIRECTIONS = 2;
    /** Distance de test d'ouverture (blocs). */
    private static final int PROBE_DISTANCE = 3;

    @Override
    public String id() {
        return "deadend_breath";
    }

    @Override
    public int weight(TensionState state) {
        return switch (state) {
            case CALM -> 3;
            case UNEASE -> 6;
            case WATCHED -> 8;
            case MANIFESTATION -> 5;
            case HUNT -> 2;
            case CRISIS -> 2;
            case FALSE_CALM -> 7;
        };
    }

    @Override
    public int cooldownTicks() {
        return 4200;
    }

    @Override
    public boolean run(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        UndergroundRestorationTracker.checkDeadlines(level); // maintenance amortie
        List<Direction> closed = closedDirections(level, player.blockPosition());
        int open = 4 - closed.size();
        if (open > MAX_OPEN_DIRECTIONS || closed.isEmpty()) {
            return false; // pas une impasse : événement sans effet
        }
        RandomSource random = player.getRandom();
        int breaths = 4 + random.nextInt(3); // 4..6
        int delay = 0;
        for (int i = 0; i < breaths; i++) {
            delay += 35 + random.nextInt(11); // 35..45 ticks
            HorrorDirector.runLater(level, delay, () -> playBreath(player, closed));
        }
        return true;
    }

    /**
     * Directions horizontales FERMÉES autour de {@code origin} : une direction est
     * ouverte si, à {@value #PROBE_DISTANCE} blocs, le bloc des pieds ET celui de la
     * tête sont de l'air.
     */
    private static List<Direction> closedDirections(ServerLevel level, BlockPos origin) {
        List<Direction> closed = new ArrayList<>(4);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos probe = origin.relative(direction, PROBE_DISTANCE);
            boolean openWay = level.getBlockState(probe).isAir()
                    && level.getBlockState(probe.above()).isAir();
            if (!openWay) {
                closed.add(direction);
            }
        }
        return closed;
    }

    /**
     * Joue une respiration 1,5–2,5 blocs dans une direction fermée (dans la roche),
     * recalculée à l'exécution pour suivre le joueur. Volume faible, pitch grave.
     */
    private static void playBreath(ServerPlayer player, List<Direction> closed) {
        if (player.isRemoved()) {
            return;
        }
        RandomSource random = player.getRandom();
        Direction direction = closed.get(random.nextInt(closed.size()));
        double distance = 1.5 + random.nextDouble(); // 1.5..2.5
        Vec3 pos = player.getEyePosition().add(
                direction.getStepX() * distance, -0.3, direction.getStepZ() * distance);
        float volume = 0.22f + random.nextFloat() * 0.08f; // 0.22..0.3
        float pitch = 0.75f + random.nextFloat() * 0.1f;   // 0.75..0.85
        PenumbraNetworking.sendSound(player, PenumbraSounds.BREATH_BEHIND, pos, volume, pitch);
    }
}

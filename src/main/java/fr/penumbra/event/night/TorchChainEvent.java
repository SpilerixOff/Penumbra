package fr.penumbra.event.night;

import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.director.HorrorDirector;
import fr.penumbra.director.PlayerHorrorProfile;
import fr.penumbra.director.TensionState;
import fr.penumbra.event.HorrorEvent;
import fr.penumbra.event.underground.UndergroundRestorationTracker;
import fr.penumbra.network.PenumbraNetworking;
import fr.penumbra.registry.PenumbraParticles;
import fr.penumbra.registry.PenumbraSounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Chaîne d'extinction de torches (événement nocturne {@code torch_chain}).
 * <p>
 * Collecte les torches EXTÉRIEURES proches (scan borné unique, cube de Chebyshev
 * rayon {@value #SCAN_RADIUS} ; extérieur = ciel visible au-dessus), écarte
 * celles à moins de {@value #HAZARD_RADIUS} blocs d'un lit/coffre (discipline des
 * modifications du monde), trie par distance croissante et en éteint 2 à 4 DANS
 * CET ORDRE, une toutes les {@value #MIN_STEP_TICKS}–{@value #MAX_STEP_TICKS}
 * ticks (extinction = air + {@code ASH_MOTE} + {@code TORCH_SPUTTER}, pattern
 * {@code TorchSputterEvent}). Chaque torche est trackée par son état exact
 * (posée ou murale, orientation comprise) et restaurée après
 * {@value #MIN_RESTORE_TICKS}–{@value #MAX_RESTORE_TICKS} ticks (10–20 min),
 * seulement si le bloc est resté de l'air — jamais d'écrasement d'une pose
 * ultérieure. Extinction re-validée à l'exécution : si la torche a été cassée
 * entre-temps, l'étape est simplement sautée. Maximum 4 blocs modifiés (≤ 6).
 * {@code noteTorchExtinguished} n'est PAS appelé : la chaîne est elle-même le
 * scare, armer 2–4 « torch_scare » retardés serait abusif.
 * Exige {@code worldModificationEnabled}. Annulé sans effet si moins de 2
 * torches éligibles. Gain de tension : +6.
 * </p>
 */
public final class TorchChainEvent implements HorrorEvent {

    /** Rayon Chebyshev du scan borné : 23³ = 12 167 positions ≤ 24³ (borne SPEC §4.6). */
    private static final int SCAN_RADIUS = 11;
    /** Rayon d'exclusion autour des lits/coffres (discipline modifications du monde). */
    private static final int HAZARD_RADIUS = 8;
    /** Intervalle entre deux extinctions (ticks). */
    private static final int MIN_STEP_TICKS = 30;
    private static final int MAX_STEP_TICKS = 50;
    /** Délai de restauration d'une torche (10–20 min). */
    private static final int MIN_RESTORE_TICKS = 12000;
    private static final int MAX_RESTORE_TICKS = 24000;

    @Override
    public String id() {
        return "torch_chain";
    }

    @Override
    public int weight(TensionState state) {
        return switch (state) {
            case CALM -> 0;
            case UNEASE -> 2;
            case WATCHED -> 3;
            case MANIFESTATION -> 4;
            case HUNT -> 2;
            case CRISIS -> 2;
            case FALSE_CALM -> 2;
        };
    }

    @Override
    public int cooldownTicks() {
        return 6000;
    }

    @Override
    public boolean canRun(ServerPlayer player, PlayerHorrorProfile profile) {
        // L'effet est une modification du monde : interdit si la config la refuse.
        return PenumbraConfig.get().worldModificationEnabled && NightSupport.commonNight(player);
    }

    @Override
    public boolean run(ServerPlayer player) {
        if (!PenumbraConfig.get().worldModificationEnabled) {
            return false;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        RandomSource random = player.getRandom();
        BlockPos origin = player.blockPosition();

        List<BlockPos> torches = buildEligibleTorches(level, origin);
        if (torches.size() < 2) {
            return false; // pas assez de torches extérieures éligibles : annulé, aucun effet
        }

        int count = Math.min(torches.size(), 2 + random.nextInt(3)); // 2..4
        int delay = 0;
        for (int i = 0; i < count; i++) {
            delay += MIN_STEP_TICKS + random.nextInt(MAX_STEP_TICKS - MIN_STEP_TICKS + 1);
            BlockPos torchPos = torches.get(i);
            HorrorDirector.runLater(level, delay, () -> extinguish(player, level, torchPos));
        }
        return true;
    }

    /**
     * Scan borné unique (23³) : torches extérieures triées par distance croissante,
     * moins celles à moins de {@value #HAZARD_RADIUS} blocs d'un lit/coffre repéré
     * dans le même scan.
     */
    private static List<BlockPos> buildEligibleTorches(ServerLevel level, BlockPos origin) {
        List<BlockPos> torches = new ArrayList<>();
        List<BlockPos> hazards = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
            for (int dy = -SCAN_RADIUS; dy <= SCAN_RADIUS; dy++) {
                for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    BlockState state = level.getBlockState(cursor);
                    if (isTorch(state)) {
                        // Extérieure : le ciel est visible au-dessus de la torche.
                        if (NightSupport.seesSky(level, cursor.above())) {
                            torches.add(cursor.immutable());
                        }
                    } else if (NightSupport.isBedOrChest(state)) {
                        hazards.add(cursor.immutable());
                    }
                }
            }
        }
        if (torches.isEmpty()) {
            return torches;
        }
        if (!hazards.isEmpty()) {
            torches.removeIf(pos -> nearAny(pos, hazards));
        }
        torches.sort(java.util.Comparator.comparingDouble(origin::distSqr));
        return torches;
    }

    /** Vrai si {@code pos} est à moins de {@value #HAZARD_RADIUS} blocs (Chebyshev) d'un hazard. */
    private static boolean nearAny(BlockPos pos, List<BlockPos> hazards) {
        for (BlockPos hazard : hazards) {
            int dx = Math.abs(pos.getX() - hazard.getX());
            int dy = Math.abs(pos.getY() - hazard.getY());
            int dz = Math.abs(pos.getZ() - hazard.getZ());
            if (Math.max(Math.max(dx, dy), dz) <= HAZARD_RADIUS) {
                return true;
            }
        }
        return false;
    }

    /**
     * Éteint une torche de la chaîne : re-validation (la torche doit être toujours
     * là), effets, puis restauration trackée de l'état exact après 10–20 min
     * (pattern {@code TorchSputterEvent.run}).
     */
    private static void extinguish(ServerPlayer player, ServerLevel level, BlockPos torchPos) {
        BlockState torchState = level.getBlockState(torchPos);
        if (!isTorch(torchState)) {
            return; // cassée ou remplacée entre-temps : étape sautée
        }
        RandomSource random = player.getRandom();

        Vec3 center = Vec3.atCenterOf(torchPos);
        level.sendParticles(PenumbraParticles.ASH_MOTE, center.x, center.y, center.z, 12, 0.25, 0.25, 0.25, 0.01);
        if (!player.isRemoved()) {
            PenumbraNetworking.sendSound(player, PenumbraSounds.TORCH_SPUTTER, center,
                    0.7f, 0.85f + random.nextFloat() * 0.15f);
        }

        BlockState air = Blocks.AIR.defaultBlockState();
        if (!level.setBlock(torchPos, air, 3)) {
            return;
        }
        int restoreDelay = MIN_RESTORE_TICKS + random.nextInt(MAX_RESTORE_TICKS - MIN_RESTORE_TICKS + 1);
        UndergroundRestorationTracker.get(level.getServer()).add(level,
                new UndergroundRestorationTracker.Entry(
                        torchPos.immutable(), level.dimension(), torchState, air,
                        UndergroundRestorationTracker.TYPE_TEMPORARY_EFFECT,
                        UndergroundRestorationTracker.gameTime(level) + restoreDelay,
                        player.getUUID().toString()));
    }

    private static boolean isTorch(BlockState state) {
        return state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH);
    }
}

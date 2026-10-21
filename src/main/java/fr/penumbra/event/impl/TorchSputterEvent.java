package fr.penumbra.event.impl;

import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.data.PenumbraPersistentState;
import fr.penumbra.director.HorrorDirector;
import fr.penumbra.director.PlayerHorrorProfile;
import fr.penumbra.director.TensionState;
import fr.penumbra.event.HorrorEvent;
import fr.penumbra.event.underground.UndergroundRestorationTracker;
import fr.penumbra.network.PenumbraNetworking;
import fr.penumbra.registry.PenumbraParticles;
import fr.penumbra.registry.PenumbraSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Torche qui crache et s'éteint (SPEC §4.11).
 * <p>
 * Recherche la torche la plus proche dans un cube borné (rayon Chebyshev 11, soit 23³ ≤ 24³,
 * avec sortie anticipée dès qu'une torche est trouvée — la coquille parcourue en premier est
 * la plus proche). Effets : 12 particules {@code ASH_MOTE} + son {@code TORCH_SPUTTER}.
 * Si {@code worldModificationEnabled} et 40 % : la torche est remplacée par de l'air puis
 * restaurée 1200 ticks plus tard par le tracker persistant (seulement si le bloc est resté de l'air).
 * Gain de tension : +5, uniquement si une torche a été trouvée.
 * </p>
 */
public final class TorchSputterEvent implements HorrorEvent {

    /** Rayon Chebyshev du scan borné : (2*11+1)³ = 23³ = 12 167 positions ≤ 24³ (SPEC §4.6). */
    private static final int SCAN_RADIUS = 11;

    /** Délai avant restauration de la torche éteinte (60 s). */
    private static final int RESTORE_DELAY_TICKS = 1200;

    @Override
    public String id() {
        return "torch_sputter";
    }

    @Override
    public int weight(TensionState state) {
        return switch (state) {
            case CALM -> 5;
            case UNEASE -> 7;
            case WATCHED -> 9;
            case MANIFESTATION -> 9;
            case HUNT -> 6;
            case CRISIS -> 6;
            case FALSE_CALM -> 5;
        };
    }

    @Override
    public int cooldownTicks() {
        return 1200;
    }

    @Override
    public boolean canRun(ServerPlayer player, PlayerHorrorProfile profile) {
        return !player.isSleeping();
    }

    @Override
    public boolean run(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        RandomSource random = player.getRandom();
        BlockPos torchPos = findNearestTorch(level, player.blockPosition());
        if (torchPos == null) {
            return false; // aucune torche à portée : événement sans effet
        }
        BlockState torchState = level.getBlockState(torchPos);

        Vec3 center = Vec3.atCenterOf(torchPos);
        level.sendParticles(PenumbraParticles.ASH_MOTE, center.x, center.y, center.z, 12, 0.25, 0.25, 0.25, 0.01);
        PenumbraNetworking.sendSound(player, PenumbraSounds.TORCH_SPUTTER, center, 0.7f, 0.9f + random.nextFloat() * 0.2f);

        if (PenumbraConfig.get().worldModificationEnabled && random.nextFloat() < 0.4f) {
            BlockState air = Blocks.AIR.defaultBlockState();
            if (level.setBlock(torchPos, air, 3)) {
                UndergroundRestorationTracker.get(level.getServer()).add(level,
                        new UndergroundRestorationTracker.Entry(
                                torchPos.immutable(), level.dimension(), torchState, air,
                                UndergroundRestorationTracker.TYPE_TEMPORARY_EFFECT,
                                UndergroundRestorationTracker.gameTime(level) + RESTORE_DELAY_TICKS,
                                player.getUUID().toString()));
                // SPEC2 §6 : le retrait réel arme le déclencheur retardé "torch_scare".
                HorrorDirector.noteTorchExtinguished(player, torchPos);
            }
        }

        return true;
    }

    /**
     * Cherche la torche (posée ou murale) la plus proche par coquilles de Chebyshev croissantes :
     * la première trouvée est la plus proche, et le scan s'arrête immédiatement (sortie anticipée).
     * Volume total balayé borné à (2*{@link #SCAN_RADIUS}+1)³ ≤ 24³.
     */
    private static BlockPos findNearestTorch(ServerLevel level, BlockPos origin) {
        for (int r = 1; r <= SCAN_RADIUS; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.max(Math.abs(dx), Math.abs(dy)), Math.abs(dz)) != r) {
                            continue; // intérieur déjà balayé : on ne parcourt que la coquille r
                        }
                        BlockPos pos = origin.offset(dx, dy, dz);
                        if (isTorch(level.getBlockState(pos))) {
                            return pos;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static boolean isTorch(BlockState state) {
        return state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH);
    }
}

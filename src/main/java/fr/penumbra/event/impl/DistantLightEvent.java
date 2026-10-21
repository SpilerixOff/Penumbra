package fr.penumbra.event.impl;

import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.data.PenumbraPersistentState;
import fr.penumbra.director.HorrorDirector;
import fr.penumbra.director.PlayerHorrorProfile;
import fr.penumbra.director.TensionState;
import fr.penumbra.event.HorrorEvent;
import fr.penumbra.event.underground.UndergroundRestorationTracker;
import fr.penumbra.network.PenumbraNetworking;
import fr.penumbra.registry.PenumbraSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Lueur lointaine leurre (SPEC2 §6).
 * <p>
 * Si {@code worldModificationEnabled} : pose une torche à 24–40 blocs du joueur sur un sol
 * valide (air + {@code canSurvive}), puis la retire après {@value #RESTORE_DELAY_TICKS}
 * ticks via le tracker persistant — même discipline de restauration que
 * {@code TorchSputterEvent} : l'état d'origine (capturé avant la pose) n'est restauré que
 * si le bloc est resté une torche, pour ne jamais écraser une construction du joueur.
 * Dans tous les cas, la nappe {@code DISTANT_HUM} (6 s) est jouée à la position visée ;
 * si la modification du monde est désactivée ou si aucun emplacement valide n'existe,
 * le bourdonnement est joué seul. Exige {@code luresEnabled}. Gain de tension : +5.
 * </p>
 */
public final class DistantLightEvent implements HorrorEvent {

    /** Délai avant le retrait de la torche leurée (60 s). */
    private static final int RESTORE_DELAY_TICKS = 1200;
    /** Distance minimale de la lueur (blocs). */
    private static final double MIN_DISTANCE = 24.0;
    /** Distance maximale de la lueur (blocs). */
    private static final double MAX_DISTANCE = 40.0;
    /** Amplitude de la recherche verticale d'un emplacement (au-dessus / en dessous du joueur). */
    private static final int SCAN_UP = 6;
    private static final int SCAN_DOWN = 24;

    @Override
    public String id() {
        return "distant_light";
    }

    @Override
    public int weight(TensionState state) {
        return switch (state) {
            case CALM -> 3;
            case UNEASE -> 6;
            case WATCHED -> 9;
            case MANIFESTATION -> 8;
            case HUNT -> 5;
            case CRISIS -> 4;
            case FALSE_CALM -> 10;
        };
    }

    @Override
    public int cooldownTicks() {
        return 2000;
    }

    @Override
    public boolean canRun(ServerPlayer player, PlayerHorrorProfile profile) {
        return PenumbraConfig.get().luresEnabled && !player.isSleeping();
    }

    @Override
    public boolean run(ServerPlayer player) {
        if (!PenumbraConfig.get().luresEnabled) {
            return false;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        RandomSource random = player.getRandom();

        // Direction horizontale libre : la lueur doit surprendre, y compris hors du regard.
        double angle = random.nextDouble() * (Math.PI * 2.0);
        double distance = MIN_DISTANCE + random.nextDouble() * (MAX_DISTANCE - MIN_DISTANCE);
        int baseX = Mth.floor(player.getX() + Math.cos(angle) * distance);
        int baseZ = Mth.floor(player.getZ() + Math.sin(angle) * distance);
        int baseY = Mth.floor(player.getY());

        Vec3 humPos;
        BlockPos spot = findTorchSpot(level, baseX, baseY, baseZ);
        if (PenumbraConfig.get().worldModificationEnabled && spot != null) {
            BlockState original = level.getBlockState(spot);
            BlockState torch = Blocks.TORCH.defaultBlockState();
            if (level.setBlock(spot, torch, 3)) {
                UndergroundRestorationTracker.get(level.getServer()).add(level,
                        new UndergroundRestorationTracker.Entry(
                                spot.immutable(), level.dimension(), original, torch,
                                UndergroundRestorationTracker.TYPE_TEMPORARY_EFFECT,
                                UndergroundRestorationTracker.gameTime(level) + RESTORE_DELAY_TICKS,
                                player.getUUID().toString()));
                humPos = Vec3.atCenterOf(spot);
            } else {
                humPos = player.position().add(Math.cos(angle) * distance, 1.0,
                        Math.sin(angle) * distance);
            }
        } else {
            // Modification désactivée ou aucun sol valide : DISTANT_HUM seul (SPEC2 §6).
            humPos = player.position().add(Math.cos(angle) * distance, 1.0, Math.sin(angle) * distance);
        }

        float pitch = 0.85f + random.nextFloat() * 0.15f; // 0.85..1.0
        PenumbraNetworking.sendSound(player, PenumbraSounds.DISTANT_HUM, humPos, 0.4f, pitch);
        return true;
    }

    /**
     * Cherche un emplacement de torche valide à la verticale du point (x, z) : bloc d'air,
     * chunk chargé, {@code canSurvive} (sol porteur en dessous). Balayage en ligne borné à
     * {@value #SCAN_UP} blocs au-dessus et {@value #SCAN_DOWN} en dessous de la hauteur du
     * joueur — aucun balayage volumique.
     */
    private static BlockPos findTorchSpot(ServerLevel level, int baseX, int baseY, int baseZ) {
        BlockState torch = Blocks.TORCH.defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = SCAN_UP; dy >= -SCAN_DOWN; dy--) {
            cursor.set(baseX, baseY + dy, baseZ);
            if (!level.isLoaded(cursor)) {
                return null; // hors zone chargée : inutile d'insister, jamais de chunk forcé
            }
            if (!level.getBlockState(cursor).isAir()) {
                continue;
            }
            if (torch.canSurvive(level, cursor)) {
                return cursor.immutable();
            }
        }
        return null;
    }
}

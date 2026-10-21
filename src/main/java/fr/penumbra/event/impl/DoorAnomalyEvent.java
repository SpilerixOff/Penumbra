package fr.penumbra.event.impl;

import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.data.PenumbraPersistentState;
import fr.penumbra.director.PlayerHorrorProfile;
import fr.penumbra.director.TensionState;
import fr.penumbra.event.HorrorEvent;
import fr.penumbra.network.PenumbraNetworking;
import fr.penumbra.registry.PenumbraSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Porte en bois qui s'ouvre toute seule (SPEC §4.11).
 * <p>
 * Cherche une porte en bois fermée à proximité (coquilles de Chebyshev jusqu'au rayon 11,
 * soit 23³ ≤ 24³ — borne dure du SPEC §4.6, qui bride la portée de 16 blocs annoncée en §4.11),
 * puis l'ouvre ({@code OPEN=true}) et joue {@code DOOR_GROAN} à la porte pour ce joueur
 * uniquement. Ne casse rien. Événement entièrement désactivé si
 * {@code worldModificationEnabled = false}. Gain de tension : +5, uniquement si une porte
 * a été trouvée et ouverte.
 * </p>
 */
public final class DoorAnomalyEvent implements HorrorEvent {

    /** Rayon Chebyshev du scan borné : (2*11+1)³ = 23³ = 12 167 positions ≤ 24³ (SPEC §4.6). */
    private static final int SCAN_RADIUS = 11;

    @Override
    public String id() {
        return "door_anomaly";
    }

    @Override
    public int weight(TensionState state) {
        return switch (state) {
            case CALM -> 3;
            case UNEASE -> 5;
            case WATCHED -> 8;
            case MANIFESTATION -> 10;
            case HUNT -> 6;
            case CRISIS -> 6;
            case FALSE_CALM -> 3;
        };
    }

    @Override
    public int cooldownTicks() {
        return 1500;
    }

    @Override
    public boolean canRun(ServerPlayer player, PlayerHorrorProfile profile) {
        // L'effet principal est une modification du monde : interdit si la config la refuse.
        return PenumbraConfig.get().worldModificationEnabled && !player.isSleeping();
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
        BlockPos doorPos = findClosedWoodenDoor(level, player.blockPosition());
        if (doorPos == null) {
            return false; // aucune porte éligible : événement sans effet
        }
        BlockState doorState = level.getBlockState(doorPos);
        // Flag 10 = clients + mise à jour immédiate, identique à l'ouverture vanilla d'une porte :
        // la deuxième moitié (DoubleBlockHalf) se synchronise via updateShape.
        level.setBlock(doorPos, doorState.setValue(DoorBlock.OPEN, true), 10);
        PenumbraNetworking.sendSound(player, PenumbraSounds.DOOR_GROAN, Vec3.atCenterOf(doorPos),
                0.8f, 0.75f + random.nextFloat() * 0.15f);
        return true;
    }

    /**
     * Cherche la porte en bois fermée la plus proche par coquilles de Chebyshev croissantes
     * (sortie anticipée dès la première trouvée). Volume balayé borné à (2*{@link #SCAN_RADIUS}+1)³ ≤ 24³.
     */
    private static BlockPos findClosedWoodenDoor(ServerLevel level, BlockPos origin) {
        for (int r = 1; r <= SCAN_RADIUS; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.max(Math.abs(dx), Math.abs(dy)), Math.abs(dz)) != r) {
                            continue;
                        }
                        BlockPos pos = origin.offset(dx, dy, dz);
                        BlockState state = level.getBlockState(pos);
                        if (state.is(BlockTags.WOODEN_DOORS) && !state.getValue(DoorBlock.OPEN)) {
                            return pos;
                        }
                    }
                }
            }
        }
        return null;
    }
}

package fr.penumbra.event.impl;

import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.data.PenumbraPersistentState;
import fr.penumbra.director.PlayerHorrorProfile;
import fr.penumbra.director.TensionState;
import fr.penumbra.entity.HostBehavior;
import fr.penumbra.entity.HostEntity;
import fr.penumbra.event.HorrorEvent;
import fr.penumbra.network.PenumbraNetworking;
import fr.penumbra.registry.PenumbraSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

/**
 * Apparition lointaine de l'Hôte (SPEC §4.11).
 * <p>
 * Si {@code hostEnabled} : {@code HostEntity.spawnNear(level, player, 24, 40)}, comportement
 * forcé à {@code OBSERVING}, puis {@code HOST_WHISPER} volume 0.4 à la position de l'Hôte.
 * Si {@code spawnNear} retourne {@code null}, l'événement est annulé : retour immédiat,
 * AUCUN effet (ni son, ni tension), il ne compte pas comme un événement.
 * Gain de tension : +12 en cas de réussite uniquement.
 * </p>
 */
public final class DistantHostSightingEvent implements HorrorEvent {

    @Override
    public String id() {
        return "distant_sighting";
    }

    @Override
    public int weight(TensionState state) {
        return switch (state) {
            case CALM -> 0;
            case UNEASE -> 2;
            case WATCHED -> 5;
            case MANIFESTATION -> 8;
            case HUNT -> 12;
            case CRISIS -> 16;
            case FALSE_CALM -> 0;
        };
    }

    @Override
    public int cooldownTicks() {
        return 2400;
    }

    @Override
    public boolean canRun(ServerPlayer player, PlayerHorrorProfile profile) {
        return PenumbraConfig.get().hostEnabled && !player.isSleeping();
    }

    @Override
    public boolean run(ServerPlayer player) {
        if (!PenumbraConfig.get().hostEnabled) {
            return false;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        HostEntity host = HostEntity.spawnNear(level, player, 24.0, 40.0);
        if (host == null) {
            return false; // apparition impossible sans être injuste : événement annulé, aucun effet
        }
        host.setBehavior(HostBehavior.OBSERVING);
        RandomSource random = player.getRandom();
        PenumbraNetworking.sendSound(player, PenumbraSounds.HOST_WHISPER, host.position(),
                0.4f, 0.85f + random.nextFloat() * 0.15f);
        return true;
    }
}

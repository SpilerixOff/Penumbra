package fr.penumbra.event.impl;

import fr.penumbra.data.PenumbraPersistentState;
import fr.penumbra.director.PlayerHorrorProfile;
import fr.penumbra.director.TensionState;
import fr.penumbra.event.HorrorEvent;
import fr.penumbra.network.PenumbraNetworking;
import fr.penumbra.registry.PenumbraSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

/**
 * Silence oppressant (SPEC §4.11).
 * <p>
 * Nappe {@code SILENCE_DRONE} de 12 s jouée sur le joueur à volume 0.25 (le fondu sonore
 * oppressant fait le reste côté client). Gain de tension : +6. Poids dominant en état
 * calme et faux calme.
 * </p>
 */
public final class SilenceEvent implements HorrorEvent {

    @Override
    public String id() {
        return "silence";
    }

    @Override
    public int weight(TensionState state) {
        return switch (state) {
            case CALM -> 12;
            case UNEASE -> 10;
            case WATCHED -> 7;
            case MANIFESTATION -> 5;
            case HUNT -> 4;
            case CRISIS -> 4;
            case FALSE_CALM -> 14;
        };
    }

    @Override
    public int cooldownTicks() {
        return 1800;
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
        PenumbraNetworking.sendSound(player, PenumbraSounds.SILENCE_DRONE, player.position(),
                0.25f, 0.85f + random.nextFloat() * 0.15f);
        return true;
    }
}

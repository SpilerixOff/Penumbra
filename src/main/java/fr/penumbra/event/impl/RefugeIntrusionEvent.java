package fr.penumbra.event.impl;

import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.director.PlayerHorrorProfile;
import fr.penumbra.director.TensionState;
import fr.penumbra.event.HorrorEvent;
import fr.penumbra.network.PenumbraNetworking;
import net.minecraft.server.level.ServerPlayer;

/**
 * Intrusion visuelle fiable dans un refuge appris. L'image reste privée au
 * propriétaire de la scène ; le cri est relayé spatialement aux compagnons
 * assez proches par {@link PenumbraNetworking}.
 */
public final class RefugeIntrusionEvent implements HorrorEvent {

    @Override
    public String id() {
        return "refuge_intrusion";
    }

    @Override
    public int weight(TensionState state) {
        return switch (state) {
            case CALM, UNEASE, FALSE_CALM -> 0;
            case WATCHED -> 1;
            case MANIFESTATION -> 5;
            case HUNT -> 8;
            case CRISIS -> 10;
        };
    }

    @Override
    public int cooldownTicks() {
        return 20 * 60 * 12;
    }

    @Override
    public boolean canRun(ServerPlayer player, PlayerHorrorProfile profile) {
        PenumbraConfig config = PenumbraConfig.get();
        return config.contextualScreamersEnabled
                && config.refugeContaminationEnabled
                && !player.isSleeping()
                && profile.isNearRefuge(player.blockPosition(), 14.0);
    }

    @Override
    public boolean run(ServerPlayer player) {
        PenumbraNetworking.sendJumpscare(player, "refuge_screamer", 0.92f);
        return true;
    }
}

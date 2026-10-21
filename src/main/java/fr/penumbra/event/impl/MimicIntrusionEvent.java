package fr.penumbra.event.impl;

import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.director.PlayerHorrorProfile;
import fr.penumbra.director.TensionState;
import fr.penumbra.event.HorrorEvent;
import fr.penumbra.network.PenumbraNetworking;
import net.minecraft.server.level.ServerPlayer;

/**
 * Repli visuel fiable du directeur : un visage copié de travers rompt une
 * dette de manifestation lorsqu'aucune entité ne peut être placée sans danger.
 */
public final class MimicIntrusionEvent implements HorrorEvent {

    @Override
    public String id() {
        return "mimic_intrusion";
    }

    @Override
    public int weight(TensionState state) {
        return switch (state) {
            case CALM, UNEASE, FALSE_CALM -> 0;
            case WATCHED -> 1;
            case MANIFESTATION -> 4;
            case HUNT -> 7;
            case CRISIS -> 9;
        };
    }

    @Override
    public int cooldownTicks() {
        return 20 * 60 * 10;
    }

    @Override
    public boolean canRun(ServerPlayer player, PlayerHorrorProfile profile) {
        return PenumbraConfig.get().contextualScreamersEnabled
                && !player.isSleeping()
                && (profile.totalEvents() >= 2
                || profile.tension() >= TensionState.HUNT.threshold);
    }

    @Override
    public boolean run(ServerPlayer player) {
        PenumbraNetworking.sendJumpscare(player, "mimic_screamer", 0.90f);
        return true;
    }
}

package fr.penumbra.event.impl;

import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.director.PlayerHorrorProfile;
import fr.penumbra.director.TensionState;
import fr.penumbra.event.HorrorEvent;
import fr.penumbra.perception.IndividualPerceptionManager;
import fr.penumbra.perception.PerceptionRules;
import net.minecraft.server.level.ServerPlayer;

/** Apparition privée d'un compagnon copié, intégrée au tempo persistant du directeur. */
public final class FalseCompanionEvent implements HorrorEvent {

    @Override
    public String id() {
        return "false_companion";
    }

    @Override
    public int weight(TensionState state) {
        return switch (state) {
            case CALM, UNEASE, FALSE_CALM -> 0;
            case WATCHED -> 5;
            case MANIFESTATION -> 9;
            case HUNT -> 7;
            case CRISIS -> 5;
        };
    }

    @Override
    public int cooldownTicks() {
        PenumbraConfig config = PenumbraConfig.get();
        return (int) Math.min(Integer.MAX_VALUE, PerceptionRules.cooldownTicks(
                config.perceptionIntervalSeconds,
                config.eventFrequency, config.horrorIntensity));
    }

    @Override
    public boolean canRun(ServerPlayer player, PlayerHorrorProfile profile) {
        return IndividualPerceptionManager.canStartNatural(player);
    }

    @Override
    public boolean run(ServerPlayer player) {
        return IndividualPerceptionManager.startFalseCompanion(player, false);
    }
}

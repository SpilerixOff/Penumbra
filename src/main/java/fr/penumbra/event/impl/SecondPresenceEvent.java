package fr.penumbra.event.impl;

import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.director.HorrorDirector;
import fr.penumbra.director.PlayerHorrorProfile;
import fr.penumbra.director.TensionState;
import fr.penumbra.director.presence.SecondPresenceManager;
import fr.penumbra.event.HorrorEvent;
import fr.penumbra.journal.WarrickCassetteItem;
import net.minecraft.server.level.ServerPlayer;

/** A rare, coherent sound-to-body encounter, selected by the existing personal director. */
public final class SecondPresenceEvent implements HorrorEvent {
    @Override public String id() { return "second_presence"; }

    @Override public int weight(TensionState state) {
        return switch (state) {
            case WATCHED -> 7;
            case MANIFESTATION -> 9;
            case HUNT -> 4;
            default -> 0;
        };
    }

    @Override public int cooldownTicks() { return 20 * 60 * 6; }

    @Override public boolean canRun(ServerPlayer player, PlayerHorrorProfile profile) {
        var config = PenumbraConfig.get();
        return config.secondPresenceEnabled && config.hostEnabled
                && !player.isSleeping() && !player.isSpectator() && !player.isPassenger()
                && !player.isInWater() && !player.isInLava()
                && !WarrickCassetteItem.isPlaying(player.getUUID())
                && HorrorDirector.effectiveContentStage(player) >= 2
                && HorrorDirector.physicalManifestationAllowed(player)
                && HorrorDirector.automaticSceneAvailable(player)
                && HorrorDirector.canSpawnHorrorEntityNear(player, 48.0);
    }

    @Override public boolean run(ServerPlayer player) {
        return SecondPresenceManager.begin(player);
    }
}

package fr.penumbra.event.impl;

import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.director.PlayerHorrorProfile;
import fr.penumbra.director.TensionState;
import fr.penumbra.entity.fauxmob.FauxMobManager;
import fr.penumbra.event.HorrorEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Faux-Semblant ({@code faux_creature}, 0.15.0).
 *
 * <p>Une vraie créature vanilla apparaît dans le dos du joueur, au sol, hors
 * de son champ de vision — mais elle est pilotée : muette, ou fixée sur le
 * joueur, ou figée face à un point sans intérêt… ou parfaite. La sélection
 * complète (déguisement, défaut, part de parfaits croissante) appartient à
 * {@link FauxMobManager#spawnNatural}. Si aucun emplacement honnête n'existe,
 * l'événement est annulé : aucun effet, aucune tension.</p>
 *
 * <p>Fort en FALSE_CALM : un cochon paisible est exactement ce qu'un faux
 * calme promet.</p>
 */
public final class FauxCreatureEvent implements HorrorEvent {

    @Override
    public String id() {
        return "faux_creature";
    }

    @Override
    public int weight(TensionState state) {
        return switch (state) {
            case CALM -> 3;
            case UNEASE -> 5;
            case WATCHED -> 6;
            case MANIFESTATION -> 5;
            case HUNT -> 2;
            case CRISIS -> 2;
            case FALSE_CALM -> 8;
        };
    }

    @Override
    public int cooldownTicks() {
        return 3600;
    }

    @Override
    public boolean canRun(ServerPlayer player, PlayerHorrorProfile profile) {
        PenumbraConfig config = PenumbraConfig.get();
        return config.fauxCreaturesEnabled
                && !player.isSleeping()
                && player.level() instanceof ServerLevel level
                && level.dimension() == Level.OVERWORLD;
    }

    @Override
    public boolean run(ServerPlayer player) {
        if (!PenumbraConfig.get().fauxCreaturesEnabled) {
            return false;
        }
        return FauxMobManager.spawnNatural(player);
    }
}

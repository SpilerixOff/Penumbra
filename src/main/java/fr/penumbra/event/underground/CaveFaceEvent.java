package fr.penumbra.event.underground;

import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.director.PlayerHorrorProfile;
import fr.penumbra.director.TensionState;
import fr.penumbra.network.PenumbraNetworking;
import net.minecraft.server.level.ServerPlayer;

/**
 * Apparition souterraine fiable : un visage surgit brièvement dans l'obscurité.
 * Contrairement aux silhouettes physiques, cet événement ne dépend d'aucun
 * emplacement d'entité et peut donc honorer la garantie de manifestation même
 * dans une galerie exiguë.
 */
public final class CaveFaceEvent extends AbstractUndergroundEvent {

    private static final float INTENSITY = 0.72f;

    @Override
    public String id() {
        return "cave_face";
    }

    @Override
    public int weight(TensionState state) {
        PenumbraConfig config = PenumbraConfig.get();
        if (!config.undergroundEventsEnabled || !config.screamersEnabled
                || config.jumpscareFrequency <= 0.0f) {
            return 0;
        }
        int base = switch (state) {
            case CALM, UNEASE, FALSE_CALM -> 0;
            case WATCHED -> 5;
            case MANIFESTATION -> 8;
            case HUNT -> 11;
            case CRISIS -> 14;
        };
        if (base == 0) {
            return 0;
        }
        return Math.max(1, Math.round(base * config.jumpscareFrequency));
    }

    @Override
    public int cooldownTicks() {
        return 1800;
    }

    @Override
    public boolean canRun(ServerPlayer player, PlayerHorrorProfile profile) {
        PenumbraConfig config = PenumbraConfig.get();
        return super.canRun(player, profile)
                && config.screamersEnabled
                && config.jumpscareFrequency > 0.0f;
    }

    @Override
    public boolean run(ServerPlayer player) {
        PenumbraConfig config = PenumbraConfig.get();
        if (!config.undergroundEventsEnabled || !config.screamersEnabled
                || config.jumpscareFrequency <= 0.0f) {
            return false;
        }
        PenumbraNetworking.sendJumpscare(player, "apparition", INTENSITY);
        return true;
    }
}

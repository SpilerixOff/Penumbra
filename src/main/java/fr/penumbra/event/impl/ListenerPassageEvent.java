package fr.penumbra.event.impl;

import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.director.HorrorDirector;
import fr.penumbra.director.PlayerHorrorProfile;
import fr.penumbra.director.TensionState;
import fr.penumbra.entity.listener.ListenerEntity;
import fr.penumbra.event.HorrorEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code listener_passage} (0.17.0) — elle passe, et elle ne vous cherche pas.
 *
 * <p>L'Écouteur apparaît à trente ou quarante-deux blocs, déjà occupé par un
 * bruit situé de l'autre côté du joueur, et traverse le champ à distance sans
 * jamais s'en approcher. C'est une <b>présentation</b> : le joueur voit d'abord
 * la créature de loin, entend ses clics, remarque que sa tête est tournée
 * ailleurs et comprend tout seul, avant sa première vraie rencontre, que cette
 * chose suit quelque chose d'autre que son regard.</p>
 *
 * <p>La scène est volontairement inoffensive : la destination est calculée hors
 * du joueur, et si celui-ci se met à courir vers elle il devient lui-même le
 * bruit le plus fort — la créature se retournera alors, mais ce sera son choix.
 * L'événement échoue proprement si aucun sol valide n'est trouvé.</p>
 */
public final class ListenerPassageEvent implements HorrorEvent {

    private static final double MIN_DISTANCE = 30.0;
    private static final double MAX_DISTANCE = 42.0;
    private static final int MIN_CONTENT_STAGE = 1;

    @Override
    public String id() {
        return "listener_passage";
    }

    @Override
    public int weight(TensionState state) {
        return switch (state) {
            case CALM -> 0;
            case UNEASE -> 6;
            case WATCHED -> 11;
            case MANIFESTATION -> 13;
            case HUNT -> 4;
            case CRISIS -> 0;
            case FALSE_CALM -> 3;
        };
    }

    @Override
    public int cooldownTicks() {
        return 6000;
    }

    @Override
    public boolean canRun(ServerPlayer player, PlayerHorrorProfile profile) {
        PenumbraConfig config = PenumbraConfig.get();
        return config.listenerEnabled
                && !player.isSleeping() && !player.isSpectator()
                && HorrorDirector.effectiveContentStage(player) >= MIN_CONTENT_STAGE
                && HorrorDirector.physicalManifestationAllowed(player)
                && HorrorDirector.canSpawnHorrorEntityNear(player, 48.0);
    }

    @Override
    public boolean run(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        return ListenerEntity.spawnPassage(level, player, MIN_DISTANCE, MAX_DISTANCE);
    }
}

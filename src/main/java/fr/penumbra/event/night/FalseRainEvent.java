package fr.penumbra.event.night;

import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.director.HorrorDirector;
import fr.penumbra.director.PlayerHorrorProfile;
import fr.penumbra.director.TensionState;
import fr.penumbra.event.HorrorEvent;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * La fausse pluie ({@code false_rain}, 0.16.0) — le ciel qui ment.
 *
 * <p>Il se met à pleuvoir. Le son, le voile, les gouttes — mais uniquement
 * pour ce joueur : le paquet météo est envoyé à lui seul, le monde reste sec.
 * Après 10 à 15 secondes, la pluie s'arrête d'un coup, comme si elle ne
 * s'était jamais produite. En multijoueur, un coéquipier à côté ne voit
 * rien — et c'est exactement le but : « il pleut, non ? »</p>
 *
 * <p>Aucun état serveur modifié : si une vraie pluie commence entre-temps,
 * le nettoyage renvoie l'état météo réel au lieu de l'annuler. Gated par
 * {@code individualPerceptionsEnabled} (c'est une perception).</p>
 */
public final class FalseRainEvent implements HorrorEvent {

    /** Durée de la fausse pluie : 10–15 s. */
    private static final int MIN_DURATION_TICKS = 200;
    private static final int DURATION_JITTER_TICKS = 100;

    @Override
    public String id() {
        return "false_rain";
    }

    @Override
    public int weight(TensionState state) {
        return switch (state) {
            case CALM -> 2;
            case UNEASE -> 4;
            case WATCHED -> 5;
            case MANIFESTATION -> 3;
            case HUNT -> 1;
            case CRISIS -> 1;
            case FALSE_CALM -> 6;
        };
    }

    @Override
    public int cooldownTicks() {
        return 6000;
    }

    @Override
    public boolean canRun(ServerPlayer player, PlayerHorrorProfile profile) {
        if (!PenumbraConfig.get().individualPerceptionsEnabled
                || !NightSupport.commonNight(player)) {
            return false;
        }
        // Une vraie pluie rendrait la fausse indiscernable — et le retour à sec absurde.
        return player.level() instanceof ServerLevel level && !level.isRaining();
    }

    @Override
    public boolean run(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || level.isRaining()) {
            return false;
        }
        player.connection.send(new ClientboundGameEventPacket(
                ClientboundGameEventPacket.START_RAINING, 0.0f));

        int duration = MIN_DURATION_TICKS
                + player.getRandom().nextInt(DURATION_JITTER_TICKS);
        UUID playerId = player.getUUID();
        HorrorDirector.runLater(level, duration, () -> {
            ServerPlayer target = level.getServer().getPlayerList().getPlayer(playerId);
            if (target == null || target.level() != level) {
                return;
            }
            // Si une vraie pluie a commencé, on renvoie l'état réel ; sinon, sec.
            target.connection.send(new ClientboundGameEventPacket(
                    level.isRaining()
                            ? ClientboundGameEventPacket.START_RAINING
                            : ClientboundGameEventPacket.STOP_RAINING,
                    0.0f));
        });
        return true;
    }
}

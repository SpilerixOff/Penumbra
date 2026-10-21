package fr.penumbra.event.impl;

import fr.penumbra.director.HorrorDirector;
import fr.penumbra.director.PlayerHorrorProfile;
import fr.penumbra.director.TensionState;
import fr.penumbra.event.HorrorEvent;
import fr.penumbra.network.PenumbraNetworking;
import fr.penumbra.registry.PenumbraSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * {@code held_breath} (0.17.0) — le monde retient son souffle.
 *
 * <p>Une longue inspiration très grave, puis rien : cinq à sept secondes pendant
 * lesquelles plus aucun son de Penumbra n'est joué. Le silence n'est pas un
 * effet en soi, c'est une promesse. À l'échéance, une seule expiration tombe
 * deux blocs derrière la tête du joueur — pas un cri, pas un screamer, pas de
 * tremblement de caméra : un souffle. Le joueur se retourne de lui-même, et il
 * n'y a rien. C'est lui qui s'est fait peur, ce qui ne s'use jamais.</p>
 *
 * <p>Aucune entité, aucun bloc, aucune apparition : l'événement est purement
 * sonore et compte donc dans le quota {@code maxConsecutiveAudioEvents} du
 * directeur, qui l'empêchera d'occuper seul une montée de tension.</p>
 */
public final class HeldBreathEvent implements HorrorEvent {

    /** Bornes du silence entre l'inspiration et l'expiration (5 à 7 s). */
    private static final int MIN_HOLD_TICKS = 100;
    private static final int MAX_HOLD_TICKS = 140;
    /** Distance de l'expiration finale, derrière la nuque. */
    private static final double BREATH_DISTANCE = 2.0;

    @Override
    public String id() {
        return "held_breath";
    }

    @Override
    public int weight(TensionState state) {
        return switch (state) {
            case CALM -> 4;
            case UNEASE -> 11;
            case WATCHED -> 14;
            case MANIFESTATION -> 9;
            case HUNT -> 3;
            case CRISIS -> 0;
            // En faux calme, la promesse non tenue est exactement l'effet voulu.
            case FALSE_CALM -> 16;
        };
    }

    @Override
    public int cooldownTicks() {
        return 3000;
    }

    @Override
    public boolean canRun(ServerPlayer player, PlayerHorrorProfile profile) {
        return !player.isSleeping() && !player.isSpectator();
    }

    @Override
    public boolean run(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        RandomSource random = player.getRandom();
        PenumbraNetworking.sendSound(player, PenumbraSounds.HELD_BREATH,
                player.position(), 0.34f, 0.9f + random.nextFloat() * 0.08f);

        int hold = MIN_HOLD_TICKS + random.nextInt(MAX_HOLD_TICKS - MIN_HOLD_TICKS + 1);
        java.util.UUID target = player.getUUID();
        HorrorDirector.runLater(level, hold, () -> {
            ServerPlayer current = level.getServer() == null
                    ? null : level.getServer().getPlayerList().getPlayer(target);
            if (current == null || !current.isAlive() || current.level() != level
                    || current.isSpectator()) {
                return;
            }
            // Recalculé à l'échéance : le souffle tombe derrière la nuque du
            // joueur telle qu'elle est maintenant, pas telle qu'elle était.
            Vec3 look = current.getLookAngle().multiply(1.0, 0.0, 1.0);
            look = look.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : look.normalize();
            Vec3 behind = current.getEyePosition().subtract(look.scale(BREATH_DISTANCE));
            PenumbraNetworking.sendSoundPrioritySpatial(current,
                    PenumbraSounds.BREATH_BEHIND, behind, 0.62f,
                    0.94f + current.getRandom().nextFloat() * 0.1f);
        });
        return true;
    }
}

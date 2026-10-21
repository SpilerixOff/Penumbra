package fr.penumbra.event.impl;

import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.director.HorrorDirector;
import fr.penumbra.director.PlayerHorrorProfile;
import fr.penumbra.director.TensionState;
import fr.penumbra.event.HorrorEvent;
import fr.penumbra.network.PenumbraNetworking;
import fr.penumbra.registry.PenumbraSounds;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * {@code wrong_footfall} (0.17.0) — le pas de trop.
 *
 * <p>Pendant huit à onze secondes, chacun des pas du joueur est suivi d'un
 * second pas, décalé de trois à cinq ticks et posé deux blocs derrière lui. Le
 * décalage est trop court pour être entendu comme un écho et trop long pour
 * passer pour son propre pas : l'oreille compte un pas de trop sans réussir à
 * dire lequel.</p>
 *
 * <p>La séquence ne se déclenche que si le joueur marche réellement, et
 * s'interrompt d'elle-même dès qu'il s'arrête — s'immobiliser fait taire le
 * double, ce qui apprend exactement la même leçon que L'Écouteur. Elle
 * n'utilise que des sons déjà validés ({@code footstep_mimic}) et le nouveau
 * {@code extra_step}, plus sec.</p>
 */
public final class WrongFootfallEvent implements HorrorEvent {

    /** Nombre de pas doublés (8 à 11 s à cadence de marche). */
    private static final int MIN_STEPS = 7;
    private static final int MAX_STEPS = 10;
    /** Cadence d'un pas de marche vanilla, avec un peu de gigue. */
    private static final int STEP_INTERVAL_TICKS = 12;
    private static final int STEP_JITTER_TICKS = 3;
    /** Décalage du double derrière le pas réel. */
    private static final int OFFSET_MIN_TICKS = 3;
    private static final int OFFSET_MAX_TICKS = 5;
    private static final double BEHIND_DISTANCE = 2.0;
    /** Vitesse horizontale minimale pour considérer que le joueur marche. */
    private static final double WALKING_SPEED = 0.03;

    @Override
    public String id() {
        return "wrong_footfall";
    }

    @Override
    public int weight(TensionState state) {
        return switch (state) {
            case CALM -> 0;
            case UNEASE -> 9;
            case WATCHED -> 13;
            case MANIFESTATION -> 10;
            case HUNT -> 5;
            case CRISIS -> 2;
            case FALSE_CALM -> 7;
        };
    }

    @Override
    public int cooldownTicks() {
        return 3600;
    }

    @Override
    public boolean canRun(ServerPlayer player, PlayerHorrorProfile profile) {
        return PenumbraConfig.get().improvedSoundsEnabled
                && !player.isSleeping() && !player.isSpectator()
                && !player.isPassenger()
                // Inutile de doubler des pas que le joueur ne fait pas.
                && player.getKnownMovement().horizontalDistance() > WALKING_SPEED;
    }

    @Override
    public boolean run(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        RandomSource random = player.getRandom();
        int steps = MIN_STEPS + random.nextInt(MAX_STEPS - MIN_STEPS + 1);
        schedule(level, player.getUUID(), steps, 0);
        return true;
    }

    /**
     * Programme le pas suivant. Chaque maillon revérifie le joueur : une mort,
     * une déconnexion, un changement de dimension ou un simple arrêt terminent
     * la séquence sans laisser de tâche orpheline.
     */
    private void schedule(ServerLevel level, UUID playerId, int remaining, int delay) {
        if (remaining <= 0) {
            return;
        }
        HorrorDirector.runLater(level, Math.max(1, delay), () -> {
            MinecraftServer server = level.getServer();
            ServerPlayer player = server == null
                    ? null : server.getPlayerList().getPlayer(playerId);
            if (player == null || !player.isAlive() || player.level() != level
                    || player.isSpectator() || player.isSleeping()) {
                return;
            }
            if (player.getKnownMovement().horizontalDistance() <= WALKING_SPEED) {
                // Le joueur s'est arrêté : le double s'arrête aussi. Toujours.
                return;
            }
            RandomSource random = player.getRandom();
            Vec3 look = player.getLookAngle().multiply(1.0, 0.0, 1.0);
            look = look.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : look.normalize();
            Vec3 behind = player.position().subtract(look.scale(BEHIND_DISTANCE));
            int offset = OFFSET_MIN_TICKS
                    + random.nextInt(OFFSET_MAX_TICKS - OFFSET_MIN_TICKS + 1);
            HorrorDirector.runLater(level, offset, () -> {
                ServerPlayer echoTarget = server.getPlayerList().getPlayer(playerId);
                if (echoTarget == null || !echoTarget.isAlive()
                        || echoTarget.level() != level) {
                    return;
                }
                PenumbraNetworking.sendSound(echoTarget, PenumbraSounds.EXTRA_STEP,
                        behind, 0.4f, 0.96f + random.nextFloat() * 0.1f);
            });
            schedule(level, playerId, remaining - 1,
                    STEP_INTERVAL_TICKS + random.nextInt(STEP_JITTER_TICKS + 1));
        });
    }
}

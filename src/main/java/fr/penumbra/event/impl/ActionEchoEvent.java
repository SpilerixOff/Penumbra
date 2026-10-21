package fr.penumbra.event.impl;

import fr.penumbra.director.HorrorDirector;
import fr.penumbra.director.PlayerHorrorProfile;
import fr.penumbra.director.TensionState;
import fr.penumbra.event.HorrorEvent;
import fr.penumbra.event.personal.ActionEchoLog;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * L'écho de tes actions ({@code action_echo}, 0.16.0).
 *
 * <p>Le mod a noté les portes, trappes et coffres que le joueur a manipulés
 * ({@link ActionEchoLog}). Cet événement rejoue la séquence exacte — mêmes
 * sons vanilla, même ordre, rythme compressé — depuis un point situé dans le
 * dos du joueur, à 10–16 blocs. Ce ne sont pas des pas imités : c'est sa
 * propre journée, rejouée par quelque chose qui a pris des notes.</p>
 *
 * <p>Les sons sont joués dans le monde ({@code level.playSound}) : en
 * multijoueur, un coéquipier proche les entend aussi — l'écho est réel.
 * La mémoire est consommée après rejeu : jamais deux fois le même écho.</p>
 */
public final class ActionEchoEvent implements HorrorEvent {

    private static final double ECHO_MIN_DIST = 10.0;
    private static final double ECHO_MAX_DIST = 16.0;

    @Override
    public String id() {
        return "action_echo";
    }

    @Override
    public int weight(TensionState state) {
        return switch (state) {
            case CALM -> 2;
            case UNEASE -> 5;
            case WATCHED -> 7;
            case MANIFESTATION -> 5;
            case HUNT -> 2;
            case CRISIS -> 2;
            case FALSE_CALM -> 6;
        };
    }

    @Override
    public int cooldownTicks() {
        return 4800;
    }

    @Override
    public boolean canRun(ServerPlayer player, PlayerHorrorProfile profile) {
        if (player.isSleeping() || !(player.level() instanceof ServerLevel level)) {
            return false;
        }
        return ActionEchoLog.recentFor(player.getUUID(), level.getGameTime())
                .size() >= ActionEchoLog.MIN_NOTES_FOR_ECHO;
    }

    @Override
    public boolean run(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        long gameTime = level.getGameTime();
        List<ActionEchoLog.Note> notes =
                ActionEchoLog.recentFor(player.getUUID(), gameTime);
        if (notes.size() < ActionEchoLog.MIN_NOTES_FOR_ECHO) {
            return false;
        }

        // Point d'écho : derrière le joueur, hors de vue.
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        if (flat.lengthSqr() < 1.0E-4) {
            flat = new Vec3(0.0, 0.0, 1.0);
        }
        double angleJitter = (player.getRandom().nextDouble() - 0.5) * 1.2;
        Vec3 back = flat.normalize().scale(-1.0).yRot((float) angleJitter);
        double dist = ECHO_MIN_DIST
                + player.getRandom().nextDouble() * (ECHO_MAX_DIST - ECHO_MIN_DIST);
        Vec3 at = player.position().add(back.scale(dist));
        BlockPos echoPos = BlockPos.containing(at.x, player.getY() + 0.5, at.z);

        long[] offsets = ActionEchoLog.replayOffsets(notes);
        UUID playerId = player.getUUID();
        for (int i = 0; i < notes.size(); i++) {
            ActionEchoLog.Action action = notes.get(i).action();
            float pitch = 0.9f + player.getRandom().nextFloat() * 0.08f;
            HorrorDirector.runLater(level, (int) offsets[i], () -> {
                // Le son n'est joué que si le propriétaire est encore là.
                if (level.getServer().getPlayerList().getPlayer(playerId) == null) {
                    return;
                }
                level.playSound(null, echoPos, switch (action) {
                    case CHEST -> SoundEvents.CHEST_OPEN;
                    case DOOR -> SoundEvents.WOODEN_DOOR_OPEN;
                    case TRAPDOOR -> SoundEvents.WOODEN_TRAPDOOR_OPEN;
                }, SoundSource.BLOCKS, 0.55f, pitch);
            });
        }
        // Clôture : la dernière porte se referme, quelques ticks après.
        long closeAt = offsets[offsets.length - 1] + ActionEchoLog.MIN_REPLAY_GAP_TICKS
                + player.getRandom().nextInt(10);
        HorrorDirector.runLater(level, (int) Mth.clamp(closeAt, 0, 400), () ->
                level.playSound(null, echoPos, SoundEvents.WOODEN_DOOR_CLOSE,
                        SoundSource.BLOCKS, 0.4f, 0.82f));

        ActionEchoLog.consume(playerId);
        return true;
    }
}

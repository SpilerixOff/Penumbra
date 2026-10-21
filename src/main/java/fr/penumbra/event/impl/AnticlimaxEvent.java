package fr.penumbra.event.impl;

import fr.penumbra.data.PenumbraPersistentState;
import fr.penumbra.director.HorrorDirector;
import fr.penumbra.director.PlayerHorrorProfile;
import fr.penumbra.director.TensionState;
import fr.penumbra.event.HorrorEvent;
import fr.penumbra.network.PenumbraNetworking;
import fr.penumbra.registry.PenumbraSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

/**
 * Faux soulagement sonore (SPEC2 §6).
 * <p>
 * Un cœur qui bat pendant 6 s ({@code HEARTBEAT}, échantillon de 2 s joué 3 fois espacées
 * de {@value #HEARTBEAT_INTERVAL_TICKS} ticks via {@code HorrorDirector.runLater}, position
 * recalculée à chaque pulsation) recouvert par la nappe {@code SILENCE_DRONE}. Puis… rien :
 * AUCUN autre effet, ni jumpscare, ni apparition, ni modification du monde. L'angoisse vient
 * précisément de l'absence de conclusion. Poids élevé en {@code FALSE_CALM}, où l'événement
 * mine le faux sentiment de sécurité. Gain de tension : +8.
 * </p>
 */
public final class AnticlimaxEvent implements HorrorEvent {

    /** Nombre de pulsations (3 × 2 s = 6 s de battement). */
    private static final int HEARTBEAT_BEATS = 3;
    /** Intervalle entre deux pulsations (40 ticks = 2 s, durée de l'échantillon). */
    private static final int HEARTBEAT_INTERVAL_TICKS = 40;

    @Override
    public String id() {
        return "anticlimax";
    }

    @Override
    public int weight(TensionState state) {
        return switch (state) {
            case CALM -> 2;
            case UNEASE -> 4;
            case WATCHED -> 6;
            case MANIFESTATION -> 6;
            case HUNT -> 3;
            case CRISIS -> 2;
            case FALSE_CALM -> 16;
        };
    }

    @Override
    public int cooldownTicks() {
        return 3000;
    }

    @Override
    public boolean canRun(ServerPlayer player, PlayerHorrorProfile profile) {
        return !player.isSleeping();
    }

    @Override
    public boolean run(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        RandomSource random = player.getRandom();
        for (int i = 0; i < HEARTBEAT_BEATS; i++) {
            HorrorDirector.runLater(level, i * HEARTBEAT_INTERVAL_TICKS, () -> playBeat(player));
        }
        PenumbraNetworking.sendSound(player, PenumbraSounds.SILENCE_DRONE, player.position(),
                0.25f, 0.85f + random.nextFloat() * 0.15f);
        return true;
    }

    /**
     * Joue une pulsation cardiaque sur la position courante du joueur (recalculée à
     * l'exécution, donc le battement le suit). Volume modéré, pitch 0.9–1.0.
     */
    private static void playBeat(ServerPlayer player) {
        if (player.isRemoved()) {
            return;
        }
        float pitch = 0.9f + player.getRandom().nextFloat() * 0.1f; // 0.9..1.0
        PenumbraNetworking.sendSound(player, PenumbraSounds.HEARTBEAT, player.position(), 0.5f, pitch);
    }
}

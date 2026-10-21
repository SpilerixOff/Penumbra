package fr.penumbra.event.impl;

import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.data.PenumbraPersistentState;
import fr.penumbra.director.PlayerHorrorProfile;
import fr.penumbra.director.TensionState;
import fr.penumbra.event.HorrorEvent;
import fr.penumbra.network.PenumbraNetworking;
import fr.penumbra.registry.PenumbraSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Réveil brutal (SPEC §4.11).
 * <p>
 * Candidat uniquement si le joueur dort ET {@code sleepEventsEnabled}. Réveille le joueur
 * ({@code stopSleeping()}), joue {@code HOST_WHISPER} à la position du lit, puis envoie le
 * jumpscare {@code "sting"} à intensité 0.6. Gain de tension : +10.
 * </p>
 */
public final class SleepInterruptEvent implements HorrorEvent {

    @Override
    public String id() {
        return "sleep_interrupt";
    }

    @Override
    public int weight(TensionState state) {
        // canRun ne passe que pendant le sommeil : les poids ne servent qu'à doser la fréquence.
        return switch (state) {
            case CALM -> 8;
            case UNEASE -> 8;
            case WATCHED -> 10;
            case MANIFESTATION -> 12;
            case HUNT -> 12;
            case CRISIS -> 14;
            case FALSE_CALM -> 4;
        };
    }

    @Override
    public int cooldownTicks() {
        return 6000;
    }

    @Override
    public boolean canRun(ServerPlayer player, PlayerHorrorProfile profile) {
        return PenumbraConfig.get().sleepEventsEnabled && player.isSleeping();
    }

    @Override
    public boolean run(ServerPlayer player) {
        if (!PenumbraConfig.get().sleepEventsEnabled || !player.isSleeping()) {
            return false;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        RandomSource random = player.getRandom();
        BlockPos bed = player.getSleepingPos().orElse(player.blockPosition());
        player.stopSleeping();
        PenumbraNetworking.sendSound(player, PenumbraSounds.HOST_WHISPER, Vec3.atCenterOf(bed),
                0.5f, 0.8f + random.nextFloat() * 0.2f);
        PenumbraNetworking.sendJumpscare(player, "sting", 0.6f);
        return true;
    }
}

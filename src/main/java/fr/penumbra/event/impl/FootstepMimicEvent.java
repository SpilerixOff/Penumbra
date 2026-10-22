package fr.penumbra.event.impl;

import fr.penumbra.data.PenumbraPersistentState;
import fr.penumbra.director.HorrorDirector;
import fr.penumbra.director.PlayerHorrorProfile;
import fr.penumbra.director.TensionState;
import fr.penumbra.event.HorrorEvent;
import fr.penumbra.network.PenumbraNetworking;
import fr.penumbra.registry.PenumbraSounds;
import fr.penumbra.story.ConfrontationManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Pas mimétiques derrière le joueur (SPEC §4.11).
 * <p>
 * 4 à 7 pas espacés de 6 à 10 ticks (via {@code HorrorDirector.runLater}), chacun joué
 * 2 à 5 blocs derrière le joueur, pitch 0.85–1.0, volume 0.5. Poids fort sous terre et
 * la nuit : l'événement n'est candidat que dans ces conditions. Gain de tension : +3.
 * </p>
 */
public final class FootstepMimicEvent implements HorrorEvent {

    /** Ticks continus sous terre requis pour que l'événement soit candidat de jour. */
    private static final long UNDERGROUND_MIN_TICKS = 200L;

    @Override
    public String id() {
        return "footsteps";
    }

    @Override
    public int weight(TensionState state) {
        return switch (state) {
            case CALM -> 14;
            case UNEASE -> 12;
            case WATCHED -> 8;
            case MANIFESTATION -> 4;
            case HUNT -> 2;
            case CRISIS -> 2;
            case FALSE_CALM -> 10;
        };
    }

    @Override
    public int cooldownTicks() {
        return 1400;
    }

    @Override
    public boolean canRun(ServerPlayer player, PlayerHorrorProfile profile) {
        if (player.isSleeping()) {
            return false;
        }
        if (profile.ticksUnderground() >= UNDERGROUND_MIN_TICKS) {
            return true;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        // 26.1 : getDayTime() s'appelle getOverworldClockTime() (vérifié dans les sources 26.1.2).
        long dayTime = level.getOverworldClockTime() % 24000L;
        return dayTime >= 13000L && dayTime <= 23000L;
    }

    @Override
    public boolean run(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        RandomSource random = player.getRandom();
        int steps = 4 + random.nextInt(4); // 4..7
        int delay = 0;
        for (int i = 0; i < steps; i++) {
            delay += 6 + random.nextInt(5); // 6..10 ticks entre chaque pas
            HorrorDirector.runLater(level, delay, () -> playStep(player));
        }
        return true;
    }

    /**
     * Joue un pas 2–5 blocs derrière la position courante du joueur (recalculée à l'exécution,
     * ce qui rend la poursuite crédible si le joueur a bougé entre-temps).
     */
    private static void playStep(ServerPlayer player) {
        if (player.isRemoved() || ConfrontationManager.isActive(player.getUUID())) {
            return;
        }
        RandomSource random = player.getRandom();
        Vec3 behind = behindOf(player, 2.0 + random.nextInt(4)); // 2..5 blocs
        float pitch = 0.85f + random.nextFloat() * 0.15f; // 0.85..1.0
        PenumbraNetworking.sendSound(player, PenumbraSounds.FOOTSTEP_MIMIC, behind, 0.5f, pitch);
    }

    /**
     * Position à {@code distance} blocs derrière le joueur, projetée sur l'horizontale.
     */
    private static Vec3 behindOf(ServerPlayer player, double distance) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        if (flat.lengthSqr() < 1.0E-4) {
            flat = new Vec3(0.0, 0.0, 1.0); // regard quasi vertical : direction arbitraire
        }
        return player.position().subtract(flat.normalize().scale(distance));
    }
}

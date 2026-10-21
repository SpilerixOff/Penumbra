package fr.penumbra.event.impl;

import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.data.PenumbraPersistentState;
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
 * Grincement de porte leurre (SPEC2 §6).
 * <p>
 * Joue {@code LURE_DOOR} à 4–10 blocs latéralement du joueur (perpendiculaire horizontale
 * à son regard, côté aléatoire) pour l'attirer hors de son refuge. AUCUNE modification de
 * bloc. Le caractère « fort près des refuges » est assuré par {@link #canRun} : l'interface
 * {@code weight(TensionState)} ne reçoit pas le profil, donc l'événement n'est candidat que
 * lorsque le joueur est à moins de {@value #REFUGE_RADIUS} blocs d'un refuge appris
 * ({@code profile.isNearRefuge}), et ses poids sont élevés. Exige {@code luresEnabled}.
 * Gain de tension : +4.
 * </p>
 */
public final class LureDoorEvent implements HorrorEvent {

    /** Rayon (blocs) autour d'un refuge appris rendant l'événement candidat. */
    private static final double REFUGE_RADIUS = 24.0;

    @Override
    public String id() {
        return "lure_door";
    }

    @Override
    public int weight(TensionState state) {
        return switch (state) {
            case CALM -> 3;
            case UNEASE -> 7;
            case WATCHED -> 10;
            case MANIFESTATION -> 9;
            case HUNT -> 5;
            case CRISIS -> 4;
            case FALSE_CALM -> 12;
        };
    }

    @Override
    public int cooldownTicks() {
        return 1600;
    }

    @Override
    public boolean canRun(ServerPlayer player, PlayerHorrorProfile profile) {
        return PenumbraConfig.get().luresEnabled
                && !player.isSleeping()
                && profile.isNearRefuge(player.blockPosition(), REFUGE_RADIUS);
    }

    @Override
    public boolean run(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        RandomSource random = player.getRandom();
        Vec3 look = player.getLookAngle();
        Vec3 lateral = new Vec3(-look.z, 0.0, look.x); // perpendiculaire horizontale au regard
        if (lateral.lengthSqr() < 1.0E-4) {
            lateral = new Vec3(1.0, 0.0, 0.0); // regard quasi vertical : direction arbitraire
        }
        double side = random.nextBoolean() ? 1.0 : -1.0;
        double distance = 4.0 + random.nextInt(7); // 4..10 blocs
        Vec3 pos = player.position().add(lateral.normalize().scale(distance * side)).add(0.0, 0.5, 0.0);
        float pitch = 0.8f + random.nextFloat() * 0.2f; // 0.8..1.0 : grincement lointain
        PenumbraNetworking.sendSound(player, PenumbraSounds.LURE_DOOR, pos, 0.8f, pitch);
        return true;
    }
}

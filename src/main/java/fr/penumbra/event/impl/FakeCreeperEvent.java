package fr.penumbra.event.impl;

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
 * Faux sifflement de creeper (SPEC §4.11).
 * <p>
 * {@code FAKE_CREEPER} joué à 3 blocs latéralement du joueur ; 30 % de chance de déclencher
 * en plus le jumpscare client {@code "fake_creeper"} à intensité 0.7. Gain de tension : +8.
 * Poids croissant avec la tension, dominant en crise.
 * </p>
 */
public final class FakeCreeperEvent implements HorrorEvent {

    @Override
    public String id() {
        return "fake_creeper";
    }

    @Override
    public int weight(TensionState state) {
        return switch (state) {
            case CALM -> 1;
            case UNEASE -> 3;
            case WATCHED -> 6;
            case MANIFESTATION -> 9;
            case HUNT -> 12;
            case CRISIS -> 16;
            case FALSE_CALM -> 0;
        };
    }

    @Override
    public int cooldownTicks() {
        return 1800;
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
        Vec3 look = player.getLookAngle();
        Vec3 lateral = new Vec3(-look.z, 0.0, look.x); // perpendiculaire horizontale au regard
        if (lateral.lengthSqr() < 1.0E-4) {
            lateral = new Vec3(1.0, 0.0, 0.0);
        }
        double side = random.nextBoolean() ? 1.0 : -1.0;
        Vec3 pos = player.position().add(lateral.normalize().scale(3.0 * side)).add(0.0, 1.0, 0.0);
        float pitch = 0.9f + random.nextFloat() * 0.2f;
        if (random.nextFloat() < 0.3f) {
            PenumbraNetworking.sendJumpscare(player, "fake_creeper", 0.7f);
        } else {
            PenumbraNetworking.sendSound(player, PenumbraSounds.FAKE_CREEPER, pos, 0.8f, pitch);
        }
        return true;
    }
}

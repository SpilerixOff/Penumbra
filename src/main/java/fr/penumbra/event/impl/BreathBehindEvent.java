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
 * Souffle dans la nuque (SPEC §4.11).
 * <p>
 * {@code BREATH_BEHIND} joué 1,5 bloc derrière la tête du joueur, volume 0.3,
 * pitch 0.8–0.95. Gain de tension : +4.
 * </p>
 */
public final class BreathBehindEvent implements HorrorEvent {

    @Override
    public String id() {
        return "breath";
    }

    @Override
    public int weight(TensionState state) {
        return switch (state) {
            case CALM -> 6;
            case UNEASE -> 10;
            case WATCHED -> 10;
            case MANIFESTATION -> 6;
            case HUNT -> 4;
            case CRISIS -> 3;
            case FALSE_CALM -> 8;
        };
    }

    @Override
    public int cooldownTicks() {
        return 1000;
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
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        if (flat.lengthSqr() < 1.0E-4) {
            flat = new Vec3(0.0, 0.0, 1.0);
        }
        Vec3 behindHead = player.getEyePosition().subtract(flat.normalize().scale(1.5));
        float pitch = 0.8f + random.nextFloat() * 0.15f; // 0.8..0.95
        PenumbraNetworking.sendSound(player, PenumbraSounds.BREATH_BEHIND, behindHead, 0.3f, pitch);
        return true;
    }
}

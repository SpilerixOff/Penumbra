package fr.penumbra.event.night;

import fr.penumbra.director.PlayerHorrorProfile;
import fr.penumbra.director.TensionState;
import fr.penumbra.event.HorrorEvent;
import fr.penumbra.network.PenumbraNetworking;
import fr.penumbra.registry.PenumbraParticles;
import fr.penumbra.registry.PenumbraSounds;
import java.util.List;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

/**
 * Animal figé qui fixe l'horizon (événement nocturne {@code animal_stare}).
 * <p>
 * Cherche l'animal passif adulte le plus proche à ≤ {@value #SEARCH_RADIUS}
 * blocs (requête entités unique dans {@code run} — {@code canRun} reste O(1)),
 * l'oriente vers un point fixe à l'horizon ({@value #HORIZON_DISTANCE} blocs,
 * hauteur des yeux), puis le ralentit fortement {@value #STARE_TICKS} ticks
 * (~20 s) avec un effet vanilla temporisé. Aucun drapeau {@code NoAI} n'est
 * modifié : un arrêt ou rechargement du serveur ne peut donc jamais laisser
 * l'animal définitivement figé. Les animaux déjà sans IA sont ignorés.
 * L'animal n'est JAMAIS tué
 * ni blessé. Effets : {@code ASH_MOTE} au-dessus de la tête + {@code HOST_WHISPER}
 * très faible à sa position. Annulé sans effet si aucun animal à portée.
 * Gain de tension : +5.
 * </p>
 */
public final class AnimalStareEvent implements HorrorEvent {

    /** Rayon de recherche de l'animal (blocs). */
    private static final double SEARCH_RADIUS = 24.0;
    /** Durée du regard fixe (~20 s). */
    private static final int STARE_TICKS = 400;
    /** Distance du point fixé à l'horizon (blocs). */
    private static final double HORIZON_DISTANCE = 32.0;

    @Override
    public String id() {
        return "animal_stare";
    }

    @Override
    public int weight(TensionState state) {
        return switch (state) {
            case CALM -> 1;
            case UNEASE -> 2;
            case WATCHED -> 3;
            case MANIFESTATION -> 3;
            case HUNT -> 1;
            case CRISIS -> 1;
            case FALSE_CALM -> 2;
        };
    }

    @Override
    public int cooldownTicks() {
        return 4800;
    }

    @Override
    public boolean canRun(ServerPlayer player, PlayerHorrorProfile profile) {
        return NightSupport.commonNight(player);
    }

    @Override
    public boolean run(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        List<Animal> animals = level.getEntitiesOfClass(Animal.class,
                player.getBoundingBox().inflate(SEARCH_RADIUS));
        Animal nearest = null;
        double best = Double.MAX_VALUE;
        for (Animal animal : animals) {
            if (animal.isBaby() || animal.isNoAi() || !animal.isAlive()) {
                continue; // un veau qui fixe l'horizon n'a aucune densité horrifique
            }
            double dist = animal.distanceToSqr(player);
            if (dist < best) {
                best = dist;
                nearest = animal;
            }
        }
        if (nearest == null) {
            return false; // aucun animal adulte à portée : annulé, aucun effet
        }
        Animal animal = nearest;
        RandomSource random = player.getRandom();

        // Point fixe à l'horizon, à hauteur des yeux de l'animal.
        double angle = random.nextDouble() * (Math.PI * 2.0);
        Vec3 eye = animal.getEyePosition();
        Vec3 target = eye.add(Math.cos(angle) * HORIZON_DISTANCE, 0.0, Math.sin(angle) * HORIZON_DISTANCE);

        animal.getNavigation().stop();
        animal.setDeltaMovement(Vec3.ZERO);
        animal.lookAt(EntityAnchorArgument.Anchor.EYES, target);
        animal.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,
                STARE_TICKS, 10, false, false, false));

        Vec3 head = animal.getEyePosition();
        level.sendParticles(PenumbraParticles.ASH_MOTE, head.x, head.y + 0.4, head.z, 6, 0.2, 0.15, 0.2, 0.005);
        PenumbraNetworking.sendSound(player, PenumbraSounds.HOST_WHISPER, animal.position(),
                0.3f, 0.6f + random.nextFloat() * 0.1f);
        return true;
    }
}

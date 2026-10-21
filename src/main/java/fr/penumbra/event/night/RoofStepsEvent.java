package fr.penumbra.event.night;

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
 * Bruits de pas sur le toit (événement nocturne {@code roof_steps}).
 * <p>
 * 3 à 6 pas espacés de 8 à 14 ticks (via {@code HorrorDirector.runLater},
 * pattern {@code FootstepMimicEvent}), chacun joué {@value #MIN_HEIGHT}–
 * {@value #MAX_HEIGHT} blocs AU-DESSUS de la position courante du joueur
 * (recalculée à l'exécution : le pas suit un joueur qui se déplace) et dérivant
 * lentement le long d'une direction horizontale fixe tirée au départ
 * ({@value #MIN_DRIFT}–{@value #MAX_DRIFT} bloc par pas). Son
 * {@code FOOTSTEP_MIMIC} à pitch grave (0.7–0.85) pour un rendu « sur les
 * planches ». AUCUNE entité, aucune modification du monde.
 * Gain de tension : +4.
 * </p>
 */
public final class RoofStepsEvent implements HorrorEvent {

    /** Hauteur min/max des pas au-dessus du joueur (blocs). */
    private static final double MIN_HEIGHT = 2.0;
    private static final double MAX_HEIGHT = 4.0;
    /** Dérive horizontale par pas (blocs). */
    private static final double MIN_DRIFT = 0.7;
    private static final double MAX_DRIFT = 1.3;

    @Override
    public String id() {
        return "roof_steps";
    }

    @Override
    public int weight(TensionState state) {
        return switch (state) {
            case CALM -> 1;
            case UNEASE -> 2;
            case WATCHED -> 3;
            case MANIFESTATION -> 3;
            case HUNT -> 2;
            case CRISIS -> 1;
            case FALSE_CALM -> 2;
        };
    }

    @Override
    public int cooldownTicks() {
        return 3600;
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
        RandomSource random = player.getRandom();
        int steps = 3 + random.nextInt(4); // 3..6
        double angle = random.nextDouble() * (Math.PI * 2.0);
        double dirX = Math.cos(angle);
        double dirZ = Math.sin(angle);

        int delay = 0;
        for (int i = 0; i < steps; i++) {
            delay += 8 + random.nextInt(7); // 8..14 ticks entre chaque pas
            double drift = (i + 1) * (MIN_DRIFT + random.nextDouble() * (MAX_DRIFT - MIN_DRIFT));
            double height = MIN_HEIGHT + random.nextDouble() * (MAX_HEIGHT - MIN_HEIGHT);
            double offsetX = dirX * drift;
            double offsetZ = dirZ * drift;
            HorrorDirector.runLater(level, delay, () -> playStep(player, offsetX, height, offsetZ));
        }
        return true;
    }

    /**
     * Joue un pas au-dessus de la position courante du joueur (recalculée à
     * l'exécution — pattern {@code FootstepMimicEvent.playStep}).
     */
    private static void playStep(ServerPlayer player, double offsetX, double height, double offsetZ) {
        if (player.isRemoved()) {
            return;
        }
        RandomSource random = player.getRandom();
        Vec3 pos = player.position().add(offsetX, height, offsetZ);
        float pitch = 0.7f + random.nextFloat() * 0.15f; // 0.70..0.85 : pas lourds sur les planches
        PenumbraNetworking.sendSound(player, PenumbraSounds.FOOTSTEP_MIMIC, pos, 0.45f, pitch);
    }
}

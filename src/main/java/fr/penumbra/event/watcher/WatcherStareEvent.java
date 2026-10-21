package fr.penumbra.event.watcher;

import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.data.PenumbraPersistentState;
import fr.penumbra.director.PlayerHorrorProfile;
import fr.penumbra.director.TensionState;
import fr.penumbra.entity.watcher.WatcherEntity;
import fr.penumbra.event.HorrorEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Un Observateur qui fixe le joueur (Phase 4, contrat Agent_Watcher).
 * <p>
 * Si {@code watcherEnabled} : {@code WatcherEntity.spawnInGazeCone(level, player, 15, 40, 60°)}
 * — un Observateur apparaît à 15–40 blocs, DANS le cône de vue du joueur (±60°), sur
 * sol solide, et le fixe. Exige la nuit ou une lumière faible (même détection que les
 * événements nocturnes : overworld + {@code isDarkOutside}, ou luminosité locale
 * ≤ 4), joueur éveillé, et cap de population non atteint (Hôtes + Observateurs à
 * ≤ 96 blocs &lt; {@code maxHostEntitiesPerPlayer}). Si {@code spawnInGazeCone}
 * retourne {@code null}, l'événement est annulé : retour immédiat, AUCUN effet, il ne
 * compte pas comme un événement. Apparition silencieuse : aucun son.
 * Gain de tension : +10 en cas de réussite uniquement.
 * </p>
 */
public final class WatcherStareEvent implements HorrorEvent {

    /** Distance minimale d'apparition (blocs). */
    private static final double MIN_DISTANCE = 15.0;
    /** Distance maximale d'apparition (blocs). */
    private static final double MAX_DISTANCE = 40.0;
    /** Demi-angle du cône de vue dans lequel l'Observateur apparaît (degrés). */
    private static final float CONE_HALF_ANGLE_DEGREES = 60.0f;
    /** Luminosité locale maximale pour une apparition de jour (équivalent nuit). */
    private static final int MAX_LIGHT_LEVEL = 4;

    @Override
    public String id() {
        return "watcher_stare";
    }

    @Override
    public int weight(TensionState state) {
        // Poids faible : apparition marquante mais peu fréquente.
        return switch (state) {
            case CALM -> 0;
            case UNEASE -> 1;
            case WATCHED -> 3;
            case MANIFESTATION -> 4;
            case HUNT -> 4;
            case CRISIS -> 5;
            case FALSE_CALM -> 2;
        };
    }

    @Override
    public int cooldownTicks() {
        return 7200;
    }

    @Override
    public boolean canRun(ServerPlayer player, PlayerHorrorProfile profile) {
        if (!PenumbraConfig.get().watcherEnabled || player.isSleeping()) {
            return false;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        if (!nightOrLowLight(player, level)) {
            return false;
        }
        return !WatcherEntity.populationCapReached(level, player);
    }

    @Override
    public boolean run(ServerPlayer player) {
        if (!PenumbraConfig.get().watcherEnabled) {
            return false;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        // Cap de population revérifié à l'exécution : refus net, sans effet.
        if (WatcherEntity.populationCapReached(level, player)) {
            return false;
        }
        WatcherEntity watcher = WatcherEntity.spawnInGazeCone(level, player,
                MIN_DISTANCE, MAX_DISTANCE, CONE_HALF_ANGLE_DEGREES);
        if (watcher == null) {
            return false; // aucun emplacement valide : événement annulé, aucun effet
        }
        return true;
    }

    /**
     * Nuit ou lumière faible : overworld, et soit {@code isDarkOutside} (même API que
     * {@code NightSupport.commonNight}), soit luminosité locale ≤ {@value #MAX_LIGHT_LEVEL}
     * (intérieur sombre, grotte peu profonde) au niveau du joueur. Coût O(1).
     */
    static boolean nightOrLowLight(ServerPlayer player, ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD) {
            return false;
        }
        return level.isDarkOutside()
                || level.getMaxLocalRawBrightness(player.blockPosition()) <= MAX_LIGHT_LEVEL;
    }
}

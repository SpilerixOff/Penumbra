package fr.penumbra.event.watcher;

import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.data.PenumbraPersistentState;
import fr.penumbra.director.PlayerHorrorProfile;
import fr.penumbra.director.TensionState;
import fr.penumbra.entity.watcher.WatcherEntity;
import fr.penumbra.event.HorrorEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Groupe d'Observateurs en arc de cercle (Phase 4, TRÈS rare — poids ×0.1 de
 * {@code watcher_stare}, arrondi à l'entier).
 * <p>
 * Exige {@code watcherEnabled} ET {@code watcherGroupsEnabled}, la nuit ou une lumière
 * faible (même détection que {@link WatcherStareEvent}) et le cap de population non
 * atteint. Le cap est revérifié avant chaque apparition : un arc peut donc être
 * incomplet, mais ne dépasse jamais {@code maxHostEntitiesPerPlayer}.
 * Trois Observateurs apparaissent à 25–45 blocs en arc de cercle autour de l'axe du
 * regard ({@value #ARC_OFFSET_DEGREES}° de part et d'autre, ±{@value #ARC_JITTER_DEGREES}°
 * de gigue), tous fixant le joueur. Si AUCUN des trois ne trouve d'emplacement,
 * l'événement est annulé : retour immédiat, AUCUN effet.
 * Gain de tension : +14 si au moins un Observateur est apparu.
 * </p>
 */
public final class WatcherGroupEvent implements HorrorEvent {

    /** Nombre d'Observateurs de l'arc. */
    private static final int GROUP_SIZE = 3;
    /** Distance minimale d'apparition (blocs). */
    private static final double MIN_DISTANCE = 25.0;
    /** Distance maximale d'apparition (blocs). */
    private static final double MAX_DISTANCE = 45.0;
    /** Décalage angulaire des Observateurs latéraux par rapport à l'axe du regard (degrés). */
    private static final float ARC_OFFSET_DEGREES = 25.0f;
    /** Gigue du cône de placement de chaque Observateur autour de sa direction d'arc (degrés). */
    private static final float ARC_JITTER_DEGREES = 6.0f;

    @Override
    public String id() {
        return "watcher_group";
    }

    @Override
    public int weight(TensionState state) {
        // ×0.1 de watcher_stare, arrondi : très rare, jamais en dessous de WATCHED.
        return switch (state) {
            case CALM -> 0;
            case UNEASE -> 0;
            case WATCHED -> 1;
            case MANIFESTATION -> 1;
            case HUNT -> 1;
            case CRISIS -> 1;
            case FALSE_CALM -> 0;
        };
    }

    @Override
    public int cooldownTicks() {
        return 24000;
    }

    @Override
    public boolean canRun(ServerPlayer player, PlayerHorrorProfile profile) {
        PenumbraConfig config = PenumbraConfig.get();
        if (!config.watcherEnabled || !config.watcherGroupsEnabled || player.isSleeping()) {
            return false;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        if (!WatcherStareEvent.nightOrLowLight(player, level)) {
            return false;
        }
        return !WatcherEntity.populationCapReached(level, player);
    }

    @Override
    public boolean run(ServerPlayer player) {
        PenumbraConfig config = PenumbraConfig.get();
        if (!config.watcherEnabled || !config.watcherGroupsEnabled) {
            return false;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        // Première vérification rapide ; la boucle revérifie ensuite le cap afin que
        // deux apparitions du même groupe ne puissent jamais le dépasser.
        if (WatcherEntity.populationCapReached(level, player)) {
            return false;
        }
        float baseYaw = player.getYRot();
        int spawned = 0;
        for (int i = 0; i < GROUP_SIZE; i++) {
            if (WatcherEntity.populationCapReached(level, player)) {
                break;
            }
            float arcYaw = baseYaw + (i - 1) * ARC_OFFSET_DEGREES;
            WatcherEntity watcher = WatcherEntity.spawnInGazeCone(level, player,
                    MIN_DISTANCE, MAX_DISTANCE, arcYaw, ARC_JITTER_DEGREES);
            if (watcher != null) {
                spawned++;
            }
        }
        if (spawned == 0) {
            return false; // aucun emplacement valide : événement annulé, aucun effet
        }
        return true;
    }
}

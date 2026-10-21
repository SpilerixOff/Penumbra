package fr.penumbra.director;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Coordination légère des scènes automatiques en multijoueur.
 * <p>
 * Les profils et leurs cooldowns restent strictement individuels. Cette classe
 * empêche uniquement deux scènes physiques/majeures de commencer presque au
 * même endroit au même moment. Les joueurs éloignés et les dimensions
 * différentes restent totalement indépendants.
 * </p>
 *
 * <p>Classe volontairement dépourvue de dépendance Minecraft afin que les
 * règles de portée, d'expiration et d'équité soient testables sans serveur.</p>
 */
final class MultiplayerEventPacing {

    /** Une scène majeure au plus toutes les quatre secondes dans un groupe proche. */
    static final long LOCAL_SCENE_GAP_TICKS = 80L;
    /** Distance à laquelle les joueurs vivent vraisemblablement la même scène. */
    static final double LOCAL_SCENE_RADIUS = 32.0;
    /** Borne défensive par dimension, même avec beaucoup de joueurs dispersés. */
    private static final int MAX_SCENES_PER_DIMENSION = 128;

    private record Scene(double x, double y, double z, long tick) {
        private double distanceToSqr(double otherX, double otherY, double otherZ) {
            double dx = this.x - otherX;
            double dy = this.y - otherY;
            double dz = this.z - otherZ;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    private final Map<String, List<Scene>> scenesByDimension = new HashMap<>();

    /**
     * Vrai si aucune scène encore active ne recouvre cette position. Un recul
     * d'horloge invalide les anciennes entrées au lieu de bloquer un nouveau
     * monde ou une dimension à l'horloge différente.
     */
    boolean isAvailable(String dimension, double x, double y, double z, long now) {
        List<Scene> scenes = this.scenesByDimension.get(dimension);
        if (scenes == null) {
            return true;
        }
        scenes.removeIf(scene -> now < scene.tick()
                || now - scene.tick() >= LOCAL_SCENE_GAP_TICKS);
        if (scenes.isEmpty()) {
            this.scenesByDimension.remove(dimension);
            return true;
        }
        double radiusSqr = LOCAL_SCENE_RADIUS * LOCAL_SCENE_RADIUS;
        for (Scene scene : scenes) {
            if (scene.distanceToSqr(x, y, z) <= radiusSqr) {
                return false;
            }
        }
        return true;
    }

    /** Enregistre une scène réussie ; les tentatives annulées ne consomment rien. */
    void record(String dimension, double x, double y, double z, long now) {
        List<Scene> scenes = this.scenesByDimension.computeIfAbsent(
                dimension, ignored -> new ArrayList<>());
        scenes.removeIf(scene -> now < scene.tick()
                || now - scene.tick() >= LOCAL_SCENE_GAP_TICKS);
        if (scenes.size() >= MAX_SCENES_PER_DIMENSION) {
            scenes.remove(0);
        }
        scenes.add(new Scene(x, y, z, now));
    }

    void clear() {
        this.scenesByDimension.clear();
    }

    int sceneCount(String dimension) {
        List<Scene> scenes = this.scenesByDimension.get(dimension);
        return scenes == null ? 0 : scenes.size();
    }

    /**
     * Ordre tournant et stable des joueurs à chaque cycle de sélection. Sans
     * cette rotation, le premier joueur de la liste serveur gagnerait toujours
     * le verrou local lorsqu'un groupe entier devient éligible au même tick.
     */
    static <T> List<T> fairOrder(Collection<T> values, long round,
                                 Function<T, UUID> idExtractor) {
        List<T> ordered = new ArrayList<>(values);
        ordered.sort(Comparator.comparing(idExtractor));
        if (ordered.size() <= 1) {
            return ordered;
        }
        int start = Math.floorMod(round, ordered.size());
        if (start == 0) {
            return ordered;
        }
        List<T> rotated = new ArrayList<>(ordered.size());
        rotated.addAll(ordered.subList(start, ordered.size()));
        rotated.addAll(ordered.subList(0, start));
        return rotated;
    }

    /** Numéro de passage propre à la cadence d'un gestionnaire autonome. */
    static long fairRound(long serverTick, int sourceIntervalTicks, int sourceSalt) {
        return Math.floorDiv(serverTick, Math.max(1, sourceIntervalTicks)) + sourceSalt;
    }
}

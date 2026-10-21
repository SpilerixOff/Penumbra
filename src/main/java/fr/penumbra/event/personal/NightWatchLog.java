package fr.penumbra.event.personal;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Journal de veille nocturne (0.16.0) — la peur rétroactive.
 *
 * <p>Pendant la nuit, le mod échantillonne la distance du plus proche Hôte.
 * Au matin, le Journal incomplet peut alors écrire : « Cette nuit, il s'est
 * approché trois fois. Le plus près : quatre blocs. » Le joueur apprend que
 * quelque chose <em>était</em> là — après coup, quand il ne peut plus rien y
 * faire.</p>
 *
 * <p>Mémoire transitoire de session (aucune persistance) ; le cœur de calcul
 * est pur et testable. Une « approche » est comptée par transition
 * hors-portée → en-portée : rester campé à 10 blocs toute la nuit compte
 * pour une seule approche.</p>
 */
public final class NightWatchLog {

    /** Distance en dessous de laquelle un Hôte « s'approche » (blocs). */
    public static final double WATCH_RADIUS = 24.0;

    /** Bilan d'une nuit terminée. */
    public record NightSummary(int approaches, int closestBlocks) {
    }

    /** Veille en cours pour un joueur. */
    static final class Watch {
        boolean inNight;
        boolean hostWasNear;
        int approaches;
        double closestSq = Double.MAX_VALUE;
        NightSummary lastNight;
    }

    private static final Map<UUID, Watch> WATCHES = new HashMap<>();

    private NightWatchLog() {
    }

    // ================= Cœur pur =================

    /** Une distance carrée est-elle une présence proche ? NaN = aucun Hôte. */
    public static boolean isNear(double distanceSq) {
        return !Double.isNaN(distanceSq)
                && distanceSq <= WATCH_RADIUS * WATCH_RADIUS;
    }

    /** Bilan d'une nuit : blocs arrondis, jamais négatifs, 0 approche = null. */
    public static NightSummary finishNight(int approaches, double closestSq) {
        if (approaches <= 0 || closestSq == Double.MAX_VALUE) {
            return new NightSummary(0, -1);
        }
        int blocks = (int) Math.round(Math.sqrt(Math.max(0.0, closestSq)));
        return new NightSummary(approaches, Math.max(0, blocks));
    }

    // ================= Session =================

    /**
     * Échantillon périodique. {@code nearestHostDistSq} vaut {@code Double.NaN}
     * quand aucun Hôte n'est à portée de requête.
     */
    public static void sample(UUID playerId, boolean isNight, double nearestHostDistSq) {
        Watch watch = WATCHES.computeIfAbsent(playerId, id -> new Watch());
        if (isNight && !watch.inNight) {
            watch.inNight = true;
            watch.hostWasNear = false;
            watch.approaches = 0;
            watch.closestSq = Double.MAX_VALUE;
        } else if (!isNight && watch.inNight) {
            watch.inNight = false;
            watch.lastNight = finishNight(watch.approaches, watch.closestSq);
        }
        if (!watch.inNight) {
            return;
        }
        boolean near = isNear(nearestHostDistSq);
        if (near && !watch.hostWasNear) {
            watch.approaches++;
        }
        watch.hostWasNear = near;
        if (near) {
            watch.closestSq = Math.min(watch.closestSq, nearestHostDistSq);
        }
    }

    /** Bilan de la dernière nuit terminée, ou {@code null} si aucune. */
    public static NightSummary summaryFor(UUID playerId) {
        Watch watch = WATCHES.get(playerId);
        return watch == null ? null : watch.lastNight;
    }

    public static void forget(UUID playerId) {
        WATCHES.remove(playerId);
    }

    public static void clearAll() {
        WATCHES.clear();
    }
}

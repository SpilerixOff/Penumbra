package fr.penumbra.director;

import net.minecraft.util.RandomSource;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Anti-habituation des screamers (0.14.0).
 *
 * <p>Le screamer est une monnaie qui se dévalue : chaque exposition récente
 * réduit l'effet de la suivante. Ce module regarde l'historique des visages
 * plein écran réellement montrés à un joueur dans les 5 dernières minutes et
 * décide, côté serveur, de trois choses :</p>
 *
 * <ul>
 *   <li><b>décote</b> — l'intensité envoyée décroît avec l'exposition récente
 *       (plancher {@link #FLOOR}) : le vacarme s'use, la menace reste ;</li>
 *   <li><b>refus sensibilisant</b> — passé deux visages récents, une part
 *       croissante des screamers non physiques est convertie en simple souffle :
 *       l'attente déçue ré-arme la peur du prochain vrai visage ;</li>
 *   <li><b>plancher d'espacement</b> — jamais deux visages non physiques à
 *       moins de {@link #MIN_FACE_GAP_TICKS} ticks d'écart.</li>
 * </ul>
 *
 * <p>Les finales de contact (bond d'une entité au visage) ne sont jamais
 * refusées — un coup physique sans image paraîtrait cassé — mais subissent la
 * décote d'intensité. L'état est une mémoire de session volontairement non
 * persistée : après un redémarrage, la sensibilité repart à neuf.</p>
 *
 * <p>Le cœur de décision ({@link #evaluate}) est pur et testable ; seul
 * {@link #decide} touche l'horloge du monde et l'aléa Minecraft. Accès depuis
 * le thread serveur uniquement, comme le reste du directeur.</p>
 */
public final class ScreamerHabituation {

    /** Fenêtre d'exposition récente : 5 minutes. */
    static final long RECENT_WINDOW_TICKS = 6000L;
    /** Espacement minimal entre deux visages non physiques : 25 secondes. */
    static final long MIN_FACE_GAP_TICKS = 500L;
    /** Plancher de la décote d'intensité. */
    static final float FLOOR = 0.55f;
    /** Perte d'intensité par visage récent. */
    static final float DECAY_PER_RECENT = 0.12f;
    /** Chance de refus par visage récent au-delà du premier, plafonnée. */
    static final float DENIAL_PER_RECENT = 0.15f;
    static final float DENIAL_CAP = 0.35f;
    /** Visages conservés par joueur ; au-delà, les plus anciens sont oubliés. */
    static final int HISTORY_LIMIT = 6;
    /** Garde-fou mémoire : purge des joueurs les plus anciens au-delà. */
    private static final int MAX_PLAYERS_TRACKED = 256;

    /** Horodatages (gameTime) des visages montrés, par joueur. */
    private static final Map<UUID, Deque<Long>> HISTORY = new HashMap<>();

    private ScreamerHabituation() {
    }

    /** Décision du module : refuser le visage, ou l'envoyer à intensité décotée. */
    public record Decision(boolean deny, float intensityMultiplier) {
        public static final Decision DENIED = new Decision(true, 0.0f);
    }

    // ================= Cœur pur (testable sans Minecraft) =================

    /** Décote appliquée au screamer suivant après {@code recentCount} visages récents. */
    public static float intensityMultiplier(int recentCount) {
        int recent = Math.max(0, recentCount);
        return Math.max(FLOOR, 1.0f - DECAY_PER_RECENT * recent);
    }

    /**
     * Probabilité de convertir ce screamer en refus. Nulle pour une finale de
     * contact ou tant que l'exposition récente reste faible.
     */
    public static float denialChance(int recentCount, boolean contactFinal) {
        if (contactFinal || recentCount <= 1) {
            return 0.0f;
        }
        return Math.min(DENIAL_CAP, DENIAL_PER_RECENT * (recentCount - 1));
    }

    /** true si un visage non physique arriverait trop tôt après le précédent. */
    public static boolean tooSoon(long lastFaceTick, long gameTime, boolean contactFinal) {
        return !contactFinal
                && lastFaceTick != Long.MIN_VALUE
                && gameTime - lastFaceTick < MIN_FACE_GAP_TICKS;
    }

    /**
     * Décision pure : {@code roll01} est un tirage uniforme déjà effectué.
     * L'appelant fournit l'exposition récente et l'horodatage du dernier visage.
     */
    public static Decision evaluate(int recentCount, long lastFaceTick, long gameTime,
                                    boolean contactFinal, float roll01) {
        if (tooSoon(lastFaceTick, gameTime, contactFinal)) {
            return Decision.DENIED;
        }
        float chance = denialChance(recentCount, contactFinal);
        float roll = Float.isFinite(roll01) ? Math.max(0.0f, Math.min(1.0f, roll01)) : 0.0f;
        if (chance > 0.0f && roll < chance) {
            return Decision.DENIED;
        }
        return new Decision(false, intensityMultiplier(recentCount));
    }

    // ================= État de session (thread serveur) =================

    /** Décision pour ce joueur maintenant ; n'enregistre rien par elle-même. */
    public static Decision decide(UUID playerId, long gameTime, boolean contactFinal,
                                  RandomSource random) {
        Deque<Long> faces = HISTORY.get(playerId);
        pruneStale(faces, gameTime);
        int recent = faces == null ? 0 : faces.size();
        long lastFace = faces == null || faces.isEmpty() ? Long.MIN_VALUE : faces.peekLast();
        return evaluate(recent, lastFace, gameTime, contactFinal, random.nextFloat());
    }

    /** À appeler après l'envoi effectif d'un visage plein écran. */
    public static void recordFace(UUID playerId, long gameTime) {
        if (HISTORY.size() >= MAX_PLAYERS_TRACKED && !HISTORY.containsKey(playerId)) {
            HISTORY.clear();
        }
        Deque<Long> faces = HISTORY.computeIfAbsent(playerId, id -> new ArrayDeque<>());
        pruneStale(faces, gameTime);
        faces.addLast(gameTime);
        while (faces.size() > HISTORY_LIMIT) {
            faces.pollFirst();
        }
    }

    /** Oubli d'un joueur (déconnexion). */
    public static void forget(UUID playerId) {
        HISTORY.remove(playerId);
    }

    /** Purge complète (arrêt ou changement de serveur). */
    public static void clearAll() {
        HISTORY.clear();
    }

    /**
     * Purge les visages hors fenêtre. Une horloge qui recule (changement de
     * monde) invalide tout l'historique du joueur plutôt que de bloquer ses
     * screamers sur un faux « trop tôt ».
     */
    private static void pruneStale(Deque<Long> faces, long gameTime) {
        if (faces == null) {
            return;
        }
        if (!faces.isEmpty() && faces.peekLast() > gameTime) {
            faces.clear();
            return;
        }
        Iterator<Long> iterator = faces.iterator();
        while (iterator.hasNext()) {
            if (gameTime - iterator.next() > RECENT_WINDOW_TICKS) {
                iterator.remove();
            } else {
                break;
            }
        }
    }
}

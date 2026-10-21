package fr.penumbra.event.personal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mémoire des gestes sonores du joueur (0.16.0) — « il rejoue ta journée ».
 *
 * <p>Chaque porte, trappe ou coffre manipulé par le joueur est noté avec son
 * horodatage. L'événement {@code action_echo} rejoue ensuite la séquence
 * exacte — mêmes sons, même ordre, rythme compressé — depuis l'obscurité,
 * derrière le joueur. Il n'imite plus en direct : il a pris des notes.</p>
 *
 * <p>Mémoire transitoire, bornée par joueur ; le cœur de compression du
 * rythme est pur et testable.</p>
 */
public final class ActionEchoLog {

    /** Gestes rejouables. */
    public enum Action { CHEST, DOOR, TRAPDOOR }

    /** Un geste noté. */
    public record Note(Action action, long gameTime) {
    }

    /** Fenêtre de mémoire : 10 minutes. */
    public static final long WINDOW_TICKS = 12000L;
    /** Gestes conservés par joueur. */
    public static final int MAX_NOTES = 8;
    /** Gestes minimum pour qu'un écho ait un sens narratif. */
    public static final int MIN_NOTES_FOR_ECHO = 3;
    /** Durée maximale du rejeu (8 s) : la séquence est compressée dedans. */
    public static final long MAX_REPLAY_SPAN_TICKS = 160L;
    /** Écart minimal entre deux sons rejoués. */
    public static final long MIN_REPLAY_GAP_TICKS = 8L;

    private static final Map<UUID, Deque<Note>> NOTES = new HashMap<>();

    private ActionEchoLog() {
    }

    /** Note un geste ; purge la fenêtre et borne la mémoire. */
    public static void note(UUID playerId, Action action, long gameTime) {
        Deque<Note> deque = NOTES.computeIfAbsent(playerId, id -> new ArrayDeque<>());
        while (!deque.isEmpty()
                && gameTime - deque.peekFirst().gameTime() > WINDOW_TICKS) {
            deque.pollFirst();
        }
        deque.addLast(new Note(action, gameTime));
        while (deque.size() > MAX_NOTES) {
            deque.pollFirst();
        }
    }

    /** Gestes encore dans la fenêtre, du plus ancien au plus récent. */
    public static List<Note> recentFor(UUID playerId, long gameTime) {
        Deque<Note> deque = NOTES.get(playerId);
        if (deque == null) {
            return List.of();
        }
        List<Note> recent = new ArrayList<>();
        for (Note note : deque) {
            if (gameTime - note.gameTime() <= WINDOW_TICKS) {
                recent.add(note);
            }
        }
        return recent;
    }

    /** Consomme la mémoire après un rejeu : jamais deux fois le même écho. */
    public static void consume(UUID playerId) {
        NOTES.remove(playerId);
    }

    /**
     * Compression pure du rythme : les horodatages deviennent des décalages de
     * rejeu qui préservent l'ordre et les proportions, bornés à
     * {@link #MAX_REPLAY_SPAN_TICKS} au total et espacés d'au moins
     * {@link #MIN_REPLAY_GAP_TICKS}.
     */
    public static long[] replayOffsets(List<Note> notes) {
        if (notes == null || notes.isEmpty()) {
            return new long[0];
        }
        long first = notes.get(0).gameTime();
        long span = Math.max(1L, notes.get(notes.size() - 1).gameTime() - first);
        double scale = Math.min(1.0, MAX_REPLAY_SPAN_TICKS / (double) span);
        long[] offsets = new long[notes.size()];
        long previous = -MIN_REPLAY_GAP_TICKS;
        for (int i = 0; i < notes.size(); i++) {
            long scaled = Math.round((notes.get(i).gameTime() - first) * scale);
            offsets[i] = Math.max(scaled, previous + MIN_REPLAY_GAP_TICKS);
            previous = offsets[i];
        }
        return offsets;
    }

    public static void forget(UUID playerId) {
        NOTES.remove(playerId);
    }

    public static void clearAll() {
        NOTES.clear();
    }
}

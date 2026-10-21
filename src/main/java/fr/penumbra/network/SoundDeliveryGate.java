package fr.penumbra.network;

import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Anti-spam borné des sons S2C, indépendant du code Fabric d'envoi.
 *
 * <p>Le budget ordinaire est isolé par dimension et par couple
 * source/auditeur. Les sons prioritaires ne consomment jamais ce budget, mais
 * une retransmission strictement identique au même tick est supprimée : cela
 * évite les doubles stingers dus à deux callbacks sans pouvoir étouffer un
 * autre son critique ou un son venant d'un autre joueur.</p>
 */
final class SoundDeliveryGate {

    private static final long ENTRY_TTL_TICKS = 200L;
    private static final long PURGE_INTERVAL_TICKS = 1_200L;

    private final int capacity;
    private final long normalIntervalTicks;
    private final Map<Route, State> routes;
    private long lastPurgeTick = Long.MIN_VALUE;

    SoundDeliveryGate(int capacity, long normalIntervalTicks) {
        if (capacity <= 0 || normalIntervalTicks < 0L) {
            throw new IllegalArgumentException("Capacité positive et intervalle non négatif requis");
        }
        this.capacity = capacity;
        this.normalIntervalTicks = normalIntervalTicks;
        // Ordre d'accès : une route encore active ne doit pas être évincée avant
        // une vieille route inactive, sinon la pression contournerait le throttle.
        this.routes = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Route, State> eldest) {
                return size() > SoundDeliveryGate.this.capacity;
            }
        };
    }

    synchronized boolean tryAcquire(Identifier dimension, UUID sourceId, UUID listenerId,
                                    Identifier soundId, Vec3 position, long gameTime,
                                    boolean priority) {
        return tryAcquire(dimension, sourceId, listenerId, soundId, position,
                1.0f, 1.0f, gameTime, priority);
    }

    synchronized boolean tryAcquire(Identifier dimension, UUID sourceId, UUID listenerId,
                                    Identifier soundId, Vec3 position,
                                    float volume, float pitch, long gameTime,
                                    boolean priority) {
        purgeIfNeeded(gameTime);
        Route route = new Route(dimension, sourceId, listenerId);
        State state = routes.computeIfAbsent(route, ignored -> new State());

        // Une horloge qui repart en arrière signifie un nouveau niveau dans la
        // même JVM : aucun état de l'ancien monde ne doit bloquer le nouveau.
        if (gameTime < state.lastTouchedTick) {
            state.reset();
        }
        state.lastTouchedTick = gameTime;

        if (priority) {
            PrioritySignature signature = PrioritySignature.of(
                    soundId, position, volume, pitch);
            if (gameTime != state.priorityTick) {
                state.priorityTick = gameTime;
                state.prioritySignatures.clear();
            }
            return state.prioritySignatures.add(signature);
        }

        if (state.lastNormalTick != Long.MIN_VALUE
                && gameTime >= state.lastNormalTick
                && gameTime - state.lastNormalTick < normalIntervalTicks) {
            return false;
        }
        state.lastNormalTick = gameTime;
        return true;
    }

    synchronized void clear(UUID playerId) {
        routes.keySet().removeIf(route -> route.sourceId.equals(playerId)
                || route.listenerId.equals(playerId));
    }

    synchronized void clearAll() {
        routes.clear();
        lastPurgeTick = Long.MIN_VALUE;
    }

    synchronized int size() {
        return routes.size();
    }

    private void purgeIfNeeded(long gameTime) {
        if (lastPurgeTick != Long.MIN_VALUE && gameTime >= lastPurgeTick
                && gameTime - lastPurgeTick < PURGE_INTERVAL_TICKS) {
            return;
        }
        lastPurgeTick = gameTime;
        routes.values().removeIf(state -> gameTime >= state.lastTouchedTick
                && gameTime - state.lastTouchedTick > ENTRY_TTL_TICKS);
    }

    private record Route(Identifier dimension, UUID sourceId, UUID listenerId) {
    }

    /** Signature exacte du paquet audio prioritaire après validation serveur. */
    private record PrioritySignature(Identifier soundId,
                                     long xBits, long yBits, long zBits,
                                     int volumeBits, int pitchBits) {
        private static PrioritySignature of(Identifier soundId, Vec3 position,
                                            float volume, float pitch) {
            return new PrioritySignature(soundId,
                    Double.doubleToLongBits(position.x),
                    Double.doubleToLongBits(position.y),
                    Double.doubleToLongBits(position.z),
                    Float.floatToIntBits(volume),
                    Float.floatToIntBits(pitch));
        }
    }

    private static final class State {
        private long lastTouchedTick = Long.MIN_VALUE;
        private long lastNormalTick = Long.MIN_VALUE;
        private long priorityTick = Long.MIN_VALUE;
        private final Set<PrioritySignature> prioritySignatures = new HashSet<>();

        private void reset() {
            lastNormalTick = Long.MIN_VALUE;
            priorityTick = Long.MIN_VALUE;
            prioritySignatures.clear();
        }
    }
}

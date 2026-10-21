package fr.penumbra.network;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Limiteur par joueur, déterministe et testable sans serveur Minecraft. */
public final class PhotoRequestRateLimiter {

    private final long windowTicks;
    private final int maximum;
    private final Map<UUID, Window> windows = new HashMap<>();

    public PhotoRequestRateLimiter(long windowTicks, int maximum) {
        if (windowTicks < 1L || maximum < 1) {
            throw new IllegalArgumentException("Positive photo rate limit required");
        }
        this.windowTicks = windowTicks;
        this.maximum = maximum;
    }

    public boolean allow(UUID playerId, long gameTime) {
        if (playerId == null) {
            return false;
        }
        Window window = windows.get(playerId);
        if (window == null || gameTime < window.startedAt
                || gameTime - window.startedAt >= windowTicks) {
            windows.put(playerId, new Window(gameTime, 1));
            return true;
        }
        if (window.accepted >= maximum) {
            return false;
        }
        window.accepted++;
        return true;
    }

    public void clear(UUID playerId) {
        windows.remove(playerId);
    }

    public void clear() {
        windows.clear();
    }

    int trackedPlayers() {
        return windows.size();
    }

    private static final class Window {
        private final long startedAt;
        private int accepted;

        private Window(long startedAt, int accepted) {
            this.startedAt = startedAt;
            this.accepted = accepted;
        }
    }
}

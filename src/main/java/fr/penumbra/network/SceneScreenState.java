package fr.penumbra.network;

/** A short-lived observation, never client authority to start a monster scene. */
public final class SceneScreenState {
    public static final int NONE = 0, OCCUPIED = 1, OTHER = 2;
    public static final int HEARTBEAT_TTL = 100;
    private static final int MIN_OCCUPIED_TICKS = 30;
    private static final int MAX_OCCUPIED_TICKS = 600;
    private static final int CLOSE_TTL = 20;

    private int kind = NONE;
    private long received = -1, opened = -1, closed = -1;
    private String dimension;

    public void accept(int next, long now, String dimension) {
        next = sanitize(next);
        if (!fresh(now) || !dimension.equals(this.dimension)) clear();
        if (next != kind) {
            closed = -1;
            if (kind == OCCUPIED && next == NONE && opened >= 0
                    && now - opened >= MIN_OCCUPIED_TICKS && now - opened <= MAX_OCCUPIED_TICKS) {
                closed = now;
            }
            opened = next == OCCUPIED ? now : -1;
            kind = next;
        }
        this.dimension = dimension;
        received = now;
    }

    public boolean blocks(long now, String dimension) {
        return fresh(now) && dimension.equals(this.dimension) && kind != NONE;
    }

    public boolean consumeClose(long now, String dimension) {
        long signal = closed;
        closed = -1;
        return fresh(now) && dimension.equals(this.dimension) && kind == NONE
                && signal >= 0 && now >= signal && now - signal <= CLOSE_TTL;
    }

    public static int sanitize(int kind) {
        return kind == NONE || kind == OCCUPIED ? kind : OTHER;
    }

    private boolean fresh(long now) {
        return received >= 0 && now >= received && now - received <= HEARTBEAT_TTL;
    }

    private void clear() {
        kind = NONE;
        opened = closed = received = -1;
    }
}

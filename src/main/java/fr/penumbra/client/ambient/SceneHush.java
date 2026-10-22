package fr.penumbra.client.ambient;

/** A bounded local silence measured in unpaused game ticks. */
final class SceneHush {
    private int remaining;
    void begin(int ticks) { remaining = Math.max(remaining, Math.max(0, Math.min(60, ticks))); }
    boolean active() { return remaining > 0; }
    void tick() { remaining = Math.max(0, remaining - 1); }
    void reset() { remaining = 0; }
}

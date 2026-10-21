package fr.penumbra.story;

/** A prelude belongs to one player instance, not a UUID that can reconnect. */
final class RitualSession {
    static final long MAX_TICKS = 300;
    static final double MAX_DISTANCE_SQUARED = 40.0 * 40.0;
    private final Object playerSession;
    private final Object level;
    private final long startedAt;
    private boolean cancelled;

    RitualSession(Object playerSession, Object level, long startedAt) {
        this.playerSession = playerSession;
        this.level = level;
        this.startedAt = startedAt;
    }

    boolean validate(Object currentPlayer, Object currentLevel, boolean canContinue,
                     double distanceSquared, long now) {
        if (cancelled || currentPlayer != playerSession || currentLevel != level
                || !canContinue || !Double.isFinite(distanceSquared)
                || distanceSquared > MAX_DISTANCE_SQUARED
                || now < startedAt || now - startedAt >= MAX_TICKS) {
            cancelled = true;
        }
        return !cancelled;
    }

    void cancel() {
        cancelled = true;
    }
}

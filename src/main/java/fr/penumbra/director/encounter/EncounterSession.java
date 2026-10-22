package fr.penumbra.director.encounter;

/** Tick-driven scene; no delayed closures survive a cancellation or a second encounter. */
final class EncounterSession {
    static final int MAX_TICKS = 300;
    static final int OBSERVE_MIN_TICKS = 24;
    static final int OBSERVE_MAX_TICKS = 100;
    static final int WATCH_TICKS = 10;
    static final int RETREAT_TICKS = 32;
    static final int HUSH_TICKS = 18;
    static final int CHASE_TICKS = 80;

    enum Variant { RECOIL, LUNGE, CHASE, REBOUND }
    enum Phase { OBSERVE, RETREAT, HUSH, REAPPEAR, CHASE, LUNGE, RECOVERY, DONE }

    private final Variant variant;
    private final long startedAt;
    private long previousTick;
    private int phaseTicks;
    private int watchedTicks;
    private boolean witnessed;
    private boolean rebounded;
    private Phase phase = Phase.OBSERVE;

    EncounterSession(Variant variant, long now) {
        this.variant = variant;
        this.startedAt = now;
        this.previousTick = now;
    }

    Phase phase() { return phase; }
    boolean clockChanged(long now) { return now != previousTick; }

    Phase advance(long now, boolean watched, boolean actorPresent, boolean chasesEnabled) {
        if (phase == Phase.DONE) return phase;
        if (now < previousTick || now - startedAt >= MAX_TICKS
                || !actorPresent && phase != Phase.HUSH) {
            cancel();
            return phase;
        }
        int elapsed = (int) Math.min(MAX_TICKS, now - previousTick);
        previousTick = now;
        phaseTicks += elapsed;
        watchedTicks = watched ? watchedTicks + elapsed : 0;
        switch (phase) {
            case OBSERVE, REAPPEAR -> {
                if (phaseTicks >= OBSERVE_MIN_TICKS && watchedTicks >= WATCH_TICKS) {
                    witnessed = true;
                    if (phase == Phase.REAPPEAR) transition(Phase.RETREAT);
                    else transition(switch (variant) {
                        case LUNGE -> chasesEnabled ? Phase.LUNGE : Phase.RETREAT;
                        case CHASE -> chasesEnabled ? Phase.CHASE : Phase.RETREAT;
                        default -> Phase.RETREAT;
                    });
                } else if (phaseTicks >= OBSERVE_MAX_TICKS) transition(Phase.RETREAT);
            }
            case RETREAT -> {
                if (phaseTicks >= RETREAT_TICKS) transition(
                        variant == Variant.REBOUND && witnessed && !rebounded ? Phase.HUSH : Phase.DONE);
            }
            case HUSH -> {
                if (phaseTicks >= HUSH_TICKS) {
                    rebounded = true;
                    transition(Phase.REAPPEAR);
                }
            }
            case CHASE -> {
                if (!chasesEnabled || phaseTicks >= CHASE_TICKS) transition(Phase.RETREAT);
            }
            case LUNGE -> {
                if (!chasesEnabled || phaseTicks >= 72) cancel();
            }
            case RECOVERY -> { if (phaseTicks >= 40) cancel(); }
            case DONE -> { }
        }
        return phase;
    }

    void retreat() { if (phase != Phase.DONE) transition(Phase.RETREAT); }
    void recover() { if (phase != Phase.DONE) transition(Phase.RECOVERY); }
    void cancel() { transition(Phase.DONE); }

    private void transition(Phase next) {
        phase = next;
        phaseTicks = 0;
        watchedTicks = 0;
    }
}

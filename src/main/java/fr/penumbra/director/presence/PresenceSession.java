package fr.penumbra.director.presence;

/** Finite, game-clock-driven choreography. Looking alone never authorizes an attack. */
final class PresenceSession {
    static final int MAX_TICKS = 760;
    static final int LISTEN_TICKS = 90;
    static final int CALM_TICKS = 100;
    static final int NOTICE_TICKS = 32;
    static final int WITHDRAW_TICKS = 48;
    static final int DEPART_TICKS = 80;
    static final int ATTACK_TICKS = 105;
    enum Phase { LISTENING, FOLLOWING, NOTICED, WITHDRAWING, LUNGE, RECOVERY, DEPARTING, DONE }

    private final long started;
    private long previous;
    private int phaseTicks;
    private int calmTicks;
    private boolean witnessed;
    private Phase phase = Phase.LISTENING;

    PresenceSession(long now) { started = now; previous = now; }
    Phase phase() { return phase; }
    int age(long now) { return (int) Math.clamp(now - started, 0, MAX_TICKS); }
    int phaseTicks() { return phaseTicks; }
    boolean witnessed() { return witnessed; }
    boolean clockChanged(long now) { return now != previous; }

    Phase advance(long now, boolean observed, boolean occluded, boolean calm,
                  boolean provoking, boolean attacksAllowed, boolean actorRecovering) {
        if (phase == Phase.DONE) return phase;
        if (now < previous || now - started >= MAX_TICKS) { cancel(); return phase; }
        int elapsed = (int) Math.min(MAX_TICKS, now - previous);
        previous = now;
        if (elapsed == 0) return phase;
        phaseTicks += elapsed;
        if (actorRecovering && phase != Phase.RECOVERY) transition(Phase.RECOVERY);
        if (phase == Phase.RECOVERY) {
            if (phaseTicks >= 45) cancel();
            return phase;
        }
        if (phase == Phase.LUNGE) {
            if (!attacksAllowed || phaseTicks >= ATTACK_TICKS) cancel();
            return phase;
        }
        calmTicks = occluded && calm ? Math.min(CALM_TICKS, calmTicks + elapsed) : 0;
        if (phase != Phase.DEPARTING && age(now) >= 120 && calmTicks >= CALM_TICKS) {
            transition(Phase.DEPARTING);
            return phase;
        }
        if ((phase == Phase.LISTENING || phase == Phase.FOLLOWING) && age(now) >= MAX_TICKS - DEPART_TICKS) {
            transition(Phase.DEPARTING);
            return phase;
        }
        switch (phase) {
            case LISTENING, FOLLOWING -> {
                if (observed) {
                    witnessed = true;
                    transition(Phase.NOTICED);
                } else if (phase == Phase.LISTENING && phaseTicks >= LISTEN_TICKS) {
                    transition(Phase.FOLLOWING);
                }
            }
            case NOTICED -> { if (phaseTicks >= NOTICE_TICKS) transition(Phase.WITHDRAWING); }
            case WITHDRAWING -> {
                if (phaseTicks >= WITHDRAW_TICKS) transition(
                        witnessed && observed && provoking && attacksAllowed ? Phase.LUNGE : Phase.DEPARTING);
            }
            case DEPARTING -> { if (phaseTicks >= DEPART_TICKS) cancel(); }
            default -> { }
        }
        return phase;
    }

    void recover() { if (phase != Phase.DONE && phase != Phase.RECOVERY) transition(Phase.RECOVERY); }
    void depart() { if (phase != Phase.DONE && phase != Phase.DEPARTING) transition(Phase.DEPARTING); }
    void cancel() { transition(Phase.DONE); }
    private void transition(Phase next) { phase = next; phaseTicks = 0; }
}

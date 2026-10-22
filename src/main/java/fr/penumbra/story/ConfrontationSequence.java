package fr.penumbra.story;

/** Server-independent timing: observation earns a window, never a damage race. */
final class ConfrontationSequence {
    enum Phase { ARRIVAL, MIRROR, EXPOSED, RECOVERY, COMPLETE, EXPIRED }

    static final int ARRIVAL_TICKS = 60;
    static final int EXPOSURE_TICKS = 60;
    static final int RECOVERY_TICKS = 60;
    static final int TIMEOUT_TICKS = 2400;
    static final int FLASHES_TO_WIN = 3;

    private Phase phase = Phase.ARRIVAL;
    private int age;
    private int phaseTicks;
    private int focusTicks;
    private int flashes;

    Phase phase() { return phase; }
    int flashes() { return flashes; }
    int focusTicks() { return focusTicks; }
    int requiredFocusTicks() { return 30 + 10 * flashes; }

    void advance(int ticks, boolean quietAndObserving) {
        for (int tick = 0; tick < ticks && !finished(); tick++) {
            if (++age >= TIMEOUT_TICKS) {
                enter(Phase.EXPIRED);
                break;
            }
            phaseTicks++;
            switch (phase) {
                case ARRIVAL -> {
                    if (phaseTicks >= ARRIVAL_TICKS) enter(Phase.MIRROR);
                }
                case MIRROR -> {
                    focusTicks = quietAndObserving ? focusTicks + 1 : 0;
                    if (focusTicks >= requiredFocusTicks()) enter(Phase.EXPOSED);
                }
                case EXPOSED -> {
                    if (phaseTicks >= EXPOSURE_TICKS) enter(Phase.RECOVERY);
                }
                case RECOVERY -> {
                    if (phaseTicks >= RECOVERY_TICKS) enter(Phase.MIRROR);
                }
                default -> { }
            }
        }
    }

    boolean flash(boolean framed) {
        // A shutter during observation breaks the silence; holding C cannot win.
        if (phase == Phase.MIRROR) focusTicks = 0;
        if (phase != Phase.EXPOSED || !framed) return false;
        flashes++;
        enter(flashes == FLASHES_TO_WIN ? Phase.COMPLETE : Phase.RECOVERY);
        return true;
    }

    private boolean finished() {
        return phase == Phase.COMPLETE || phase == Phase.EXPIRED;
    }

    private void enter(Phase next) {
        phase = next;
        phaseTicks = 0;
        focusTicks = 0;
    }
}

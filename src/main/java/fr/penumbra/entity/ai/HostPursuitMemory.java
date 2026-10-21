package fr.penumbra.entity.ai;

/** What the Host actually saw, rather than the hidden player's live coordinates. */
final class HostPursuitMemory {

    static final int LOST_SIGHT_TICKS = 80;

    record Position(double x, double y, double z) { }

    private Position lastSeen;
    private int unseenTicks;

    void reset() {
        lastSeen = null;
        unseenTicks = 0;
    }

    void observe(boolean visible, double x, double y, double z) {
        if (visible && Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)) {
            lastSeen = new Position(x, y, z);
            unseenTicks = 0;
        } else {
            unseenTicks = Math.min(LOST_SIGHT_TICKS, unseenTicks + 1);
        }
    }

    Position lastSeen() {
        return lastSeen;
    }

    boolean escaped() {
        return unseenTicks >= LOST_SIGHT_TICKS;
    }
}

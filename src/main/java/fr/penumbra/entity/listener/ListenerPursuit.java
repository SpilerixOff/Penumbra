package fr.penumbra.entity.listener;

/** Tick-based reaction window and silence counterplay, independent of rendering. */
final class ListenerPursuit {

    static final int WARNING_TICKS = 12;
    static final double CONTACT_DISTANCE = 1.05;
    static final int MAX_CHARGE_TICKS = 120;

    private int age;
    private int silence;

    void start() {
        age = 0;
        silence = 0;
    }

    void tick() {
        age = Math.min(Integer.MAX_VALUE - 1, age) + 1;
        silence = Math.min(ListenerNoise.LOCK_GRACE_TICKS, silence + 1);
    }

    void heardNoise() {
        silence = 0;
    }

    boolean warningComplete() {
        return age >= WARNING_TICKS;
    }

    boolean lostSound() {
        return silence >= ListenerNoise.LOCK_GRACE_TICKS;
    }

    boolean exhausted() { return age >= MAX_CHARGE_TICKS; }

    /** Cover muffles sound: it can invite a search, but cannot initiate a blind charge. */
    static double heardRadius(double radius, boolean clearPath) {
        if (!Double.isFinite(radius) || radius <= 0.0) return 0.0;
        return Math.min(ListenerNoise.MAX_RADIUS, radius) * (clearPath ? 1.0 : 0.35);
    }

    boolean canContact(double ownerNoiseRadius, double distance, boolean clearPath) {
        // Noise from another player can lure the Listener, never authorize a hit on
        // its silent owner. Cover is valid even while it can hear through walls.
        return warningComplete() && !lostSound() && !exhausted() && clearPath
                && distance >= 0.0 && distance <= CONTACT_DISTANCE
                && ListenerNoise.audible(ownerNoiseRadius, distance);
    }
}

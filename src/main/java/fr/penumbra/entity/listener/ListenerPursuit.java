package fr.penumbra.entity.listener;

/** Tick-based reaction window and silence counterplay, independent of rendering. */
final class ListenerPursuit {

    static final int WARNING_TICKS = 12;
    static final double CONTACT_DISTANCE = 1.05;

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

    boolean canContact(double ownerNoiseRadius, double distance, boolean clearPath) {
        // Noise from another player can lure the Listener, never authorize a hit on
        // its silent owner. Cover is valid even while it can hear through walls.
        return warningComplete() && !lostSound() && clearPath
                && distance >= 0.0 && distance <= CONTACT_DISTANCE
                && ListenerNoise.audible(ownerNoiseRadius, distance);
    }
}

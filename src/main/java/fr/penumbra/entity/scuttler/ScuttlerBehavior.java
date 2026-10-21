package fr.penumbra.entity.scuttler;

/** Calculs purs du rythme de la Cavaleuse, isolés pour les tests. */
final class ScuttlerBehavior {

    static final int CROSSING_WEIGHT_PERCENT = 70;
    static final int MIN_CHASE_TICKS = 60;
    static final int MAX_CHASE_TICKS = 100;

    private ScuttlerBehavior() {
    }

    static int chooseScene(int roll) {
        return Math.floorMod(roll, 100) < CROSSING_WEIGHT_PERCENT
                ? ScuttlerEntity.SCENE_CROSSING
                : ScuttlerEntity.SCENE_CHASE;
    }

    static int chaseDurationTicks(int entropy) {
        return MIN_CHASE_TICKS + Math.floorMod(
                entropy, MAX_CHASE_TICKS - MIN_CHASE_TICKS + 1);
    }

    static boolean cooldownReady(Long lastTick, long now, long cooldownTicks) {
        return lastTick == null || now < lastTick || now - lastTick >= cooldownTicks;
    }

    static int spawnDenominator(int base, float frequency, float intensity) {
        float pressure = Math.max(0.01f, frequency) * Math.max(0.01f, intensity);
        return Math.max(1, Math.round(Math.max(1, base) / pressure));
    }

    static boolean isNight(long dayTime) {
        long timeOfDay = Math.floorMod(dayTime, 24_000L);
        return timeOfDay >= 13_000L && timeOfDay <= 23_000L;
    }
}

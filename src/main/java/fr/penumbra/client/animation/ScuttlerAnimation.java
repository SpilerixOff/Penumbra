package fr.penumbra.client.animation;

import fr.penumbra.entity.scuttler.ScuttlerFeint;

/** Eight legs travel in a wave; the ankle feint has one server-timed rise and slap. */
public final class ScuttlerAnimation {
    private ScuttlerAnimation() { }

    public static float legPhase(float walk, int leg, boolean right) {
        float position = Float.isFinite(walk) ? walk : 0.0f;
        return position * 2.6f + Math.floorMod(leg, 4) * 1.28f
                + (right ? 0.0f : (float) Math.PI);
    }

    public static Feint feint(float ticks) {
        float t = Float.isFinite(ticks)
                ? Math.max(0.0f, Math.min(ScuttlerFeint.TOTAL_TICKS, ticks)) : 0.0f;
        float brake = MonsterAnimationMath.smootherstep(t / ScuttlerFeint.BRAKE_TICKS);
        float rise = MonsterAnimationMath.smootherstep((t - ScuttlerFeint.BRAKE_TICKS)
                / (ScuttlerFeint.RAISE_END_TICK - ScuttlerFeint.BRAKE_TICKS));
        float slap = MonsterAnimationMath.smootherstep((t - ScuttlerFeint.RAISE_END_TICK)
                / (ScuttlerFeint.RECOIL_START_TICK - ScuttlerFeint.RAISE_END_TICK));
        float release = MonsterAnimationMath.smootherstep((t - ScuttlerFeint.RECOIL_START_TICK)
                / (ScuttlerFeint.TOTAL_TICKS - ScuttlerFeint.RECOIL_START_TICK));
        return new Feint(brake * (1.0f - release), rise * (1.0f - slap),
                slap * (1.0f - release), release);
    }

    public record Feint(float crouch, float rise, float slap, float release) { }
}

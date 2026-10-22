package fr.penumbra.client.animation;

import fr.penumbra.entity.listener.ListenerStrike;

/** A sound-led contraction followed by one lateral sweep, never a grapple. */
public final class ListenerAttackAnimation {
    private ListenerAttackAnimation() { }

    public static Warning warning(float progress) {
        float p = MonsterAnimationMath.clamp01(progress);
        return new Warning(MonsterAnimationMath.smootherstep(p / 0.25f),
                MonsterAnimationMath.smootherstep((p - 0.16f) / 0.42f),
                MonsterAnimationMath.smootherstep((p - 0.42f) / 0.58f));
    }

    public static Sweep sweep(float ticks) {
        float t = Float.isFinite(ticks) ? Math.max(0.0f, Math.min(ListenerStrike.TOTAL_TICKS, ticks)) : 0.0f;
        float strike = MonsterAnimationMath.smootherstep(t / ListenerStrike.IMPACT_TICK);
        float recoil = MonsterAnimationMath.smootherstep((t - ListenerStrike.FOLLOW_THROUGH_TICKS)
                / (ListenerStrike.TOTAL_TICKS - ListenerStrike.FOLLOW_THROUGH_TICKS));
        float active = 1.0f - recoil;
        return new Sweep(strike * active, active,
                (-0.36f + 0.93f * strike) * active,
                0.34f + 0.28f * active,
                (0.12f - 0.32f * strike) * active);
    }

    public record Warning(float ear, float head, float torso) { }
    public record Sweep(float reach, float active, float torsoYaw,
                        float torsoPitch, float torsoRoll) { }
}

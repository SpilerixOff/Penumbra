package fr.penumbra.client.animation;

/** Sequentially releases its forelegs, opens in flight, then hooks and recoils. */
public final class RampantAttackAnimation {
    private RampantAttackAnimation() { }
    public record Release(float right, float left, float rear) { }
    public record Flight(float spread, float frontReach, float rearFold, float pitch) { }
    public record Contact(float hook, float compress, float withdraw) { }

    public static Release release(float progress) {
        float p = MonsterAnimationMath.clamp01(progress);
        return new Release(ease(p / .46f), ease((p - .26f) / .46f), ease((p - .68f) / .32f));
    }
    public static Flight flight(float ticks) {
        float open = ease(ticks / 5.0f);
        return new Flight(.70f - .39f * open, .28f + .67f * open,
                .24f + .56f * open, -.10f - .14f * open);
    }
    public static Contact contact(float ticks) {
        float p = MonsterAnimationMath.clamp01(ticks / 12.0f);
        float hook = ease(p / .24f) * (1 - ease((p - .30f) / .35f));
        float compression = (float) Math.sin(Math.PI * ease(p / .65f));
        return new Contact(hook, compression, ease((p - .25f) / .75f));
    }
    private static float ease(float p) { return MonsterAnimationMath.smootherstep(p); }
}

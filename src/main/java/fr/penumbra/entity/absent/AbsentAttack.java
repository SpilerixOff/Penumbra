package fr.penumbra.entity.absent;

/** Server-driven visual clock; zero denotes no attack, positive values elapsed + 1. */
public final class AbsentAttack {
    public static final int LUNGE_TICKS = 9;
    public static final int FOLLOW_THROUGH_TICKS = 8;
    private AbsentAttack() { }

    public static float progress(int state, float partialTick, int duration) {
        if (state <= 0 || duration <= 0) return 0;
        float partial = Float.isFinite(partialTick) ? Math.max(0, Math.min(1, partialTick)) : 0;
        return Math.max(0, Math.min(1, (state - 1 + partial) / duration));
    }
}

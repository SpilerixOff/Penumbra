package fr.penumbra.director;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Personal quiet time, measured only against the persistent Overworld game clock. */
public record PostRitualCalm(long remainingTicks, long lastObservedTick) {
    public static final long MAX_TICKS = 48_000L;
    public static final PostRitualCalm NONE = new PostRitualCalm(0L, 0L);
    public static final Codec<PostRitualCalm> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.optionalFieldOf("remainingTicks", 0L).forGetter(PostRitualCalm::remainingTicks),
            Codec.LONG.optionalFieldOf("lastObservedTick", 0L).forGetter(PostRitualCalm::lastObservedTick)
    ).apply(instance, PostRitualCalm::new));

    public PostRitualCalm {
        remainingTicks = Math.clamp(remainingTicks, 0L, MAX_TICKS);
        lastObservedTick = remainingTicks == 0L ? 0L : Math.max(0L, lastObservedTick);
    }

    public boolean active() {
        return remainingTicks > 0L;
    }

    public PostRitualCalm advanceTo(long overworldGameTime) {
        if (!active()) return NONE;
        long now = Math.max(0L, overworldGameTime);
        if (now == lastObservedTick) return this;
        // A restored/rolled-back clock preserves only the previously remaining duration.
        long elapsed = now >= lastObservedTick ? now - lastObservedTick : 0L;
        return elapsed >= remainingTicks ? NONE
                : new PostRitualCalm(remainingTicks - elapsed, now);
    }

    public PostRitualCalm extendAt(long overworldGameTime, long durationTicks) {
        PostRitualCalm current = advanceTo(overworldGameTime);
        long remaining = Math.max(current.remainingTicks,
                Math.clamp(durationTicks, 0L, MAX_TICKS));
        return remaining == 0L ? NONE
                : new PostRitualCalm(remaining, Math.max(0L, overworldGameTime));
    }
}

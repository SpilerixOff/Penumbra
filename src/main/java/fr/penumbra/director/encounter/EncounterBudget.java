package fr.penumbra.director.encounter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Short-lived server budget; failed placement never spends an encounter. */
final class EncounterBudget {
    static final int MAX_ACTIVE = 8;
    static final int MAX_COOLDOWNS = 1024;
    static final long COOLDOWN_TICKS = 1800L;
    private final Set<UUID> active = new HashSet<>();
    private final Map<UUID, Long> lastStarted = new HashMap<>();

    boolean canBegin(UUID owner, long now) {
        prune(now);
        return !active.contains(owner) && active.size() < MAX_ACTIVE
                && !lastStarted.containsKey(owner) && lastStarted.size() < MAX_COOLDOWNS;
    }

    boolean started(UUID owner, long now) {
        if (!canBegin(owner, now)) return false;
        active.add(owner);
        lastStarted.put(owner, now);
        return true;
    }

    void finished(UUID owner) { active.remove(owner); }

    void prune(long now) {
        // A clock rollback ends this transient budget, never an indefinite lockout.
        lastStarted.entrySet().removeIf(entry -> now < entry.getValue()
                || now - entry.getValue() >= COOLDOWN_TICKS);
    }
}

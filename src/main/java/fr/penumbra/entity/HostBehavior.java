package fr.penumbra.entity;

/**
 * États comportementaux de l'Hôte (Penumbra).
 * L'ordinal est synchronisé client/serveur via TrackedData dans {@link HostEntity}.
 */
public enum HostBehavior {
    DORMANT,
    OBSERVING,
    STALKING,
    INVESTIGATING,
    HIDING,
    LURING,
    CHASING,
    ATTACKING,
    RETREATING,
    COOLDOWN;

    private static final HostBehavior[] VALUES = values();

    /**
     * Retourne l'état correspondant à l'ordinal, borné pour éviter tout dépassement.
     */
    public static HostBehavior byId(int ordinal) {
        return VALUES[Math.max(0, Math.min(ordinal, VALUES.length - 1))];
    }
}

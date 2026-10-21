package fr.penumbra.director.sequence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Audience immuable capturee avant l'ajout de la premiere entite.
 *
 * <p>Les temoins explicites servent aussi de bail de groupe : deux scenes
 * lourdes ne peuvent pas employer simultanement le meme participant. Le mode
 * {@link AudienceScope#SHARED} autorise en plus les observateurs dynamiques
 * dans son rayon, sans les transformer en cible de la scene.</p>
 */
public record SceneAudience(AudienceScope scope, UUID owner, Set<UUID> witnesses,
                            double radius) {

    public static final int MAX_WITNESSES = 15;
    public static final double LOCAL_RADIUS = 48.0;
    public static final double DEFAULT_SHARED_RADIUS = 96.0;
    public static final double MAX_RADIUS = 128.0;

    public SceneAudience {
        if (scope == null || owner == null) {
            throw new IllegalArgumentException("A scene audience needs a scope and an owner");
        }
        double safeRadius = Double.isFinite(radius) ? radius : LOCAL_RADIUS;
        radius = Math.max(1.0, Math.min(MAX_RADIUS, safeRadius));

        if (scope == AudienceScope.PRIVATE || witnesses == null || witnesses.isEmpty()) {
            witnesses = Set.of();
        } else {
            TreeSet<UUID> sorted = new TreeSet<>();
            for (UUID witness : witnesses) {
                if (witness != null && !witness.equals(owner)) {
                    sorted.add(witness);
                    // La copie reste bornee meme si un appelant fournit une
                    // collection anormalement grande.
                    if (sorted.size() > MAX_WITNESSES) {
                        sorted.pollLast();
                    }
                }
            }
            witnesses = Collections.unmodifiableSet(new LinkedHashSet<>(sorted));
        }
    }

    public static SceneAudience privateTo(UUID owner) {
        return new SceneAudience(AudienceScope.PRIVATE, owner, Set.of(), 1.0);
    }

    public static SceneAudience local(UUID owner, Set<UUID> witnesses) {
        return new SceneAudience(AudienceScope.LOCAL, owner, witnesses, LOCAL_RADIUS);
    }

    public static SceneAudience shared(UUID owner, Set<UUID> witnesses, double radius) {
        return new SceneAudience(AudienceScope.SHARED, owner, witnesses, radius);
    }

    /** Participants dont l'UUID doit etre reserve pendant toute la scene. */
    public Set<UUID> leaseParticipants() {
        LinkedHashSet<UUID> participants = new LinkedHashSet<>();
        participants.add(owner);
        participants.addAll(witnesses);
        return Collections.unmodifiableSet(participants);
    }

    /**
     * Verdict pur a utiliser depuis {@code Entity#broadcastToPlayer}.
     * L'appelant fournit la distance reelle a l'ancre, sans charger de chunk.
     */
    public boolean mayObserve(UUID candidate, boolean sameDimension, boolean alive,
                              boolean spectator, double distanceFromAnchor) {
        if (candidate == null || !sameDimension || !alive || spectator
                || !Double.isFinite(distanceFromAnchor) || distanceFromAnchor < 0.0) {
            return false;
        }
        if (candidate.equals(owner)) {
            return true;
        }
        if (scope == AudienceScope.PRIVATE || distanceFromAnchor > radius) {
            return false;
        }
        return scope == AudienceScope.SHARED || witnesses.contains(candidate);
    }
}

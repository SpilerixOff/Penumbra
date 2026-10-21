package fr.penumbra.director.sequence;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Noyau serveur, sans acces au monde, des scenes multi-etapes de Penumbra.
 *
 * <p>Contrat d'integration :</p>
 * <ol>
 *   <li>capturer une {@link SceneAudience}, puis appeler {@link #tryAcquire};</li>
 *   <li>creer la manifestation primaire avec les gardes habituelles et appeler
 *       {@link #primaryFailed} si son placement echoue ;</li>
 *   <li>transmettre les transitions reelles de l'entite via {@link #signal};</li>
 *   <li>a chaque tick serveur, executer les actions retournees par
 *       {@link #pollDue} seulement si leur zone est deja chargee ;</li>
 *   <li>repondre avec {@link #acknowledge}. {@code AREA_UNLOADED} ne demande
 *       jamais de charger le chunk : deux reports bornes sont effectues, puis
 *       le bail est libere.</li>
 * </ol>
 *
 * <p>La classe est volontairement mono-thread : tous les hooks doivent venir
 * du thread serveur.</p>
 */
public final class LinkedSceneCoordinator {

    private static final int MAX_COMPLETION_BACKLOG = 64;
    private static final int MAX_DIMENSION_ID_LENGTH = 128;

    public enum Phase {
        PRIMARY_ACTIVE,
        FOLLOW_UP_PENDING,
        CAMERA_TRAP_ARMED,
        FOLLOW_UP_DISPATCHED
    }

    public enum Signal {
        ABSENT_FOLLOWED,
        ABSENT_FLASHED,
        SCUTTLER_VANISHED,
        PROJECTIONIST_NATURAL_VANISH,
        CAMERA_REOPENED,
        CAVE_LIT,
        PRIMARY_BANISHED,
        OWNER_INVALID
    }

    public enum SignalResult {
        UPDATED,
        COUNTERED,
        IGNORED,
        UNKNOWN_TOKEN,
        EXPIRED
    }

    public enum StartFailure {
        NONE,
        INVALID_REQUEST,
        OWNER_BUSY,
        AUDIENCE_BUSY,
        CAPACITY_REACHED
    }

    public enum ActionType {
        SPAWN_RAMPANT,
        SPAWN_ORIGINEL
    }

    public enum ActionOutcome {
        SPAWNED,
        AREA_UNLOADED,
        INVALID_PLACEMENT,
        BLOCKED_BY_LIGHT,
        DENIED
    }

    public enum EndReason {
        COMPLETED,
        COUNTERED_FLASH,
        COUNTERED_LIGHT,
        COUNTERED_WARD,
        ESCAPED_CAMERA_TRAP,
        PRIMARY_FAILED,
        FOLLOW_UP_UNAVAILABLE,
        OWNER_INVALID,
        EXPIRED,
        CLOCK_ROLLBACK,
        CANCELLED
    }

    public record SceneAnchor(double x, double y, double z) {
        public boolean isFinite() {
            return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
        }
    }

    public record StartRequest(UUID owner, LinkedSceneType type, SceneAudience audience,
                               String dimension, SceneAnchor anchor,
                               boolean counterplayKnown) {
    }

    public record StartResult(StartFailure failure, UUID token) {
        public boolean started() {
            return failure == StartFailure.NONE && token != null;
        }

        private static StartResult failed(StartFailure failure) {
            return new StartResult(failure, null);
        }
    }

    public record SceneAction(UUID token, UUID owner, LinkedSceneType type,
                              ActionType action, SceneAudience audience,
                              String dimension, SceneAnchor anchor, int attempt) {
    }

    public record SceneCompletion(UUID token, UUID owner, LinkedSceneType type,
                                  EndReason reason, long endedAtTick) {
    }

    public record SceneSnapshot(UUID token, UUID owner, LinkedSceneType type,
                                Phase phase, SceneAudience audience,
                                String dimension, SceneAnchor anchor,
                                boolean counterplayKnown, long startedAtTick,
                                long dueTick, long expiresAtTick, int attempts) {
    }

    private static final class ActiveScene {
        private final UUID token;
        private final UUID owner;
        private final LinkedSceneType type;
        private final SceneAudience audience;
        private final String dimension;
        private final SceneAnchor anchor;
        private final boolean counterplayKnown;
        private final long startedAtTick;
        private final long expiresAtTick;
        private Phase phase = Phase.PRIMARY_ACTIVE;
        private long dueTick = Long.MAX_VALUE;
        private long cameraTrapUntil = Long.MAX_VALUE;
        private long lastObservedTick;
        private int attempts;

        private ActiveScene(UUID token, StartRequest request, long now) {
            this.token = token;
            this.owner = request.owner();
            this.type = request.type();
            this.audience = request.audience();
            this.dimension = request.dimension();
            this.anchor = request.anchor();
            this.counterplayKnown = request.counterplayKnown();
            this.startedAtTick = now;
            this.expiresAtTick = saturatedAdd(
                    now, LinkedSceneRules.maximumDurationTicks(request.type()));
            this.lastObservedTick = now;
        }

        private SceneSnapshot snapshot() {
            long visibleDue = phase == Phase.CAMERA_TRAP_ARMED ? cameraTrapUntil : dueTick;
            return new SceneSnapshot(token, owner, type, phase, audience, dimension, anchor,
                    counterplayKnown, startedAtTick, visibleDue, expiresAtTick, attempts);
        }
    }

    private final int maxActive;
    private final Map<UUID, ActiveScene> byOwner = new HashMap<>();
    private final Map<UUID, UUID> ownerByToken = new HashMap<>();
    private final Map<UUID, UUID> participantLeases = new HashMap<>();
    private final ArrayDeque<SceneCompletion> completions = new ArrayDeque<>();

    public LinkedSceneCoordinator() {
        this(LinkedSceneRules.DEFAULT_MAX_ACTIVE);
    }

    public LinkedSceneCoordinator(int maxActive) {
        this.maxActive = Math.max(1, Math.min(LinkedSceneRules.HARD_MAX_ACTIVE, maxActive));
    }

    /** Tente de reserver le proprietaire et tous les temoins explicites. */
    public StartResult tryAcquire(StartRequest request, long now) {
        if (!valid(request)) {
            return StartResult.failed(StartFailure.INVALID_REQUEST);
        }
        if (byOwner.containsKey(request.owner())) {
            return StartResult.failed(StartFailure.OWNER_BUSY);
        }
        if (byOwner.size() >= maxActive) {
            return StartResult.failed(StartFailure.CAPACITY_REACHED);
        }
        Set<UUID> participants = request.audience().leaseParticipants();
        for (UUID participant : participants) {
            if (participantLeases.containsKey(participant)) {
                return StartResult.failed(StartFailure.AUDIENCE_BUSY);
            }
        }

        UUID token;
        do {
            token = UUID.randomUUID();
        } while (ownerByToken.containsKey(token));
        ActiveScene scene = new ActiveScene(token, request, now);
        byOwner.put(scene.owner, scene);
        ownerByToken.put(token, scene.owner);
        for (UUID participant : participants) {
            participantLeases.put(participant, token);
        }
        return new StartResult(StartFailure.NONE, token);
    }

    /** Libere immediatement un bail dont la manifestation primaire n'a pas pu naitre. */
    public boolean primaryFailed(UUID token, long now) {
        ActiveScene scene = find(token);
        if (scene == null) {
            return false;
        }
        finish(scene, EndReason.PRIMARY_FAILED, now);
        return true;
    }

    /** Applique une transition emise par la vraie entite ou par le joueur. */
    public SignalResult signal(UUID token, Signal signal, long now) {
        ActiveScene scene = find(token);
        if (scene == null || signal == null) {
            return SignalResult.UNKNOWN_TOKEN;
        }
        if (!advanceClock(scene, now)) {
            return SignalResult.EXPIRED;
        }
        if (signal == Signal.OWNER_INVALID) {
            finish(scene, EndReason.OWNER_INVALID, now);
            return SignalResult.COUNTERED;
        }
        if (signal == Signal.PRIMARY_BANISHED) {
            finish(scene, EndReason.COUNTERED_WARD, now);
            return SignalResult.COUNTERED;
        }

        return switch (scene.type) {
            case FALSE_GUIDE -> signalFalseGuide(scene, signal, now);
            case SKITTER_OMEN -> signalSkitterOmen(scene, signal, now);
            case NEGATIVE_WITNESS -> signalNegativeWitness(scene, signal, now);
        };
    }

    /**
     * Retourne chaque suivi arrive a echeance une seule fois. L'integration
     * doit verifier {@code level.isLoaded(pos)} avant tout placement.
     */
    public List<SceneAction> pollDue(long now) {
        List<SceneAction> actions = new ArrayList<>();
        List<ActiveScene> scenes = new ArrayList<>(byOwner.values());
        for (ActiveScene scene : scenes) {
            if (find(scene.token) == null || !advanceClock(scene, now)) {
                continue;
            }
            if (scene.phase != Phase.FOLLOW_UP_PENDING || now < scene.dueTick) {
                continue;
            }
            scene.phase = Phase.FOLLOW_UP_DISPATCHED;
            scene.attempts++;
            actions.add(new SceneAction(scene.token, scene.owner, scene.type,
                    scene.type.followUp() == ManifestationKind.RAMPANT
                            ? ActionType.SPAWN_RAMPANT : ActionType.SPAWN_ORIGINEL,
                    scene.audience, scene.dimension, scene.anchor, scene.attempts));
        }
        return List.copyOf(actions);
    }

    /** Acquitte l'unique action de suivi, avec deux tentatives au maximum. */
    public boolean acknowledge(UUID token, ActionOutcome outcome, long now) {
        ActiveScene scene = find(token);
        if (scene == null || outcome == null || scene.phase != Phase.FOLLOW_UP_DISPATCHED) {
            return false;
        }
        if (!advanceClock(scene, now)) {
            return false;
        }
        switch (outcome) {
            case SPAWNED -> finish(scene, EndReason.COMPLETED, now);
            case BLOCKED_BY_LIGHT -> finish(scene, EndReason.COUNTERED_LIGHT, now);
            case AREA_UNLOADED, INVALID_PLACEMENT -> {
                long retryAt = saturatedAdd(now, LinkedSceneRules.RETRY_DELAY_TICKS);
                if (scene.attempts < LinkedSceneRules.MAX_FOLLOW_UP_ATTEMPTS
                        && retryAt < scene.expiresAtTick) {
                    scene.phase = Phase.FOLLOW_UP_PENDING;
                    scene.dueTick = retryAt;
                } else {
                    finish(scene, EndReason.FOLLOW_UP_UNAVAILABLE, now);
                }
            }
            case DENIED -> finish(scene, EndReason.FOLLOW_UP_UNAVAILABLE, now);
        }
        return true;
    }

    /** Annulation explicite (reset operateur ou remplacement de monde). */
    public boolean cancel(UUID token, long now) {
        ActiveScene scene = find(token);
        if (scene == null) {
            return false;
        }
        finish(scene, EndReason.CANCELLED, now);
        return true;
    }

    public boolean cancelOwner(UUID owner, long now) {
        ActiveScene scene = byOwner.get(owner);
        if (scene == null) {
            return false;
        }
        finish(scene, EndReason.OWNER_INVALID, now);
        return true;
    }

    public boolean isOwnerBusy(UUID owner) {
        return owner != null && byOwner.containsKey(owner);
    }

    public boolean isParticipantBusy(UUID participant) {
        return participant != null && participantLeases.containsKey(participant);
    }

    public int activeCount() {
        return byOwner.size();
    }

    public Optional<SceneSnapshot> active(UUID owner) {
        ActiveScene scene = owner == null ? null : byOwner.get(owner);
        return scene == null ? Optional.empty() : Optional.of(scene.snapshot());
    }

    public Optional<SceneSnapshot> byToken(UUID token) {
        ActiveScene scene = find(token);
        return scene == null ? Optional.empty() : Optional.of(scene.snapshot());
    }

    /** Evenements de fin a transmettre au directeur, au journal ou aux logs. */
    public List<SceneCompletion> drainCompletions() {
        List<SceneCompletion> drained = List.copyOf(completions);
        completions.clear();
        return drained;
    }

    /** Purge de cycle de vie serveur ; aucune closure ni reference de monde n'est conservee. */
    public void clear(long now) {
        for (ActiveScene scene : new ArrayList<>(byOwner.values())) {
            finish(scene, EndReason.CANCELLED, now);
        }
    }

    private SignalResult signalFalseGuide(ActiveScene scene, Signal signal, long now) {
        if (signal == Signal.ABSENT_FLASHED && scene.phase != Phase.FOLLOW_UP_DISPATCHED) {
            finish(scene, EndReason.COUNTERED_FLASH, now);
            return SignalResult.COUNTERED;
        }
        if (signal == Signal.CAVE_LIT && scene.phase != Phase.FOLLOW_UP_DISPATCHED) {
            finish(scene, EndReason.COUNTERED_LIGHT, now);
            return SignalResult.COUNTERED;
        }
        if (signal == Signal.ABSENT_FOLLOWED && scene.phase == Phase.PRIMARY_ACTIVE) {
            scheduleFollowUp(scene, now);
            return SignalResult.UPDATED;
        }
        return SignalResult.IGNORED;
    }

    private SignalResult signalSkitterOmen(ActiveScene scene, Signal signal, long now) {
        if (signal == Signal.CAVE_LIT && scene.phase != Phase.FOLLOW_UP_DISPATCHED) {
            finish(scene, EndReason.COUNTERED_LIGHT, now);
            return SignalResult.COUNTERED;
        }
        if (signal == Signal.SCUTTLER_VANISHED && scene.phase == Phase.PRIMARY_ACTIVE) {
            scheduleFollowUp(scene, now);
            return SignalResult.UPDATED;
        }
        return SignalResult.IGNORED;
    }

    private SignalResult signalNegativeWitness(ActiveScene scene, Signal signal, long now) {
        if (signal == Signal.PROJECTIONIST_NATURAL_VANISH
                && scene.phase == Phase.PRIMARY_ACTIVE) {
            scene.phase = Phase.CAMERA_TRAP_ARMED;
            scene.cameraTrapUntil = Math.min(scene.expiresAtTick,
                    saturatedAdd(now, LinkedSceneRules.cameraTrapTicks(scene.counterplayKnown)));
            return SignalResult.UPDATED;
        }
        if (signal == Signal.CAMERA_REOPENED && scene.phase == Phase.CAMERA_TRAP_ARMED) {
            scene.phase = Phase.FOLLOW_UP_PENDING;
            scene.dueTick = now;
            return SignalResult.UPDATED;
        }
        return SignalResult.IGNORED;
    }

    private void scheduleFollowUp(ActiveScene scene, long now) {
        scene.phase = Phase.FOLLOW_UP_PENDING;
        scene.dueTick = Math.min(scene.expiresAtTick,
                saturatedAdd(now, LinkedSceneRules.followUpDelayTicks(
                        scene.type, scene.counterplayKnown)));
    }

    /** Met a jour l'horloge et traite toutes les fins temporelles. */
    private boolean advanceClock(ActiveScene scene, long now) {
        if (now < scene.lastObservedTick) {
            finish(scene, EndReason.CLOCK_ROLLBACK, now);
            return false;
        }
        scene.lastObservedTick = now;
        if (scene.phase == Phase.CAMERA_TRAP_ARMED && now >= scene.cameraTrapUntil) {
            finish(scene, EndReason.ESCAPED_CAMERA_TRAP, now);
            return false;
        }
        if (now >= scene.expiresAtTick) {
            finish(scene, EndReason.EXPIRED, now);
            return false;
        }
        return true;
    }

    private ActiveScene find(UUID token) {
        UUID owner = token == null ? null : ownerByToken.get(token);
        return owner == null ? null : byOwner.get(owner);
    }

    private void finish(ActiveScene scene, EndReason reason, long now) {
        byOwner.remove(scene.owner, scene);
        ownerByToken.remove(scene.token, scene.owner);
        for (UUID participant : scene.audience.leaseParticipants()) {
            participantLeases.remove(participant, scene.token);
        }
        if (completions.size() >= MAX_COMPLETION_BACKLOG) {
            completions.pollFirst();
        }
        completions.addLast(new SceneCompletion(
                scene.token, scene.owner, scene.type, reason, now));
    }

    private static boolean valid(StartRequest request) {
        if (request == null || request.owner() == null || request.type() == null
                || request.audience() == null || request.anchor() == null
                || !request.anchor().isFinite() || request.dimension() == null
                || request.dimension().isBlank()
                || request.dimension().length() > MAX_DIMENSION_ID_LENGTH) {
            return false;
        }
        return request.owner().equals(request.audience().owner());
    }

    private static long saturatedAdd(long value, long increment) {
        if (increment > 0L && value > Long.MAX_VALUE - increment) {
            return Long.MAX_VALUE;
        }
        return value + increment;
    }
}

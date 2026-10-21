package fr.penumbra.director.sequence;

import fr.penumbra.PenumbraMod;
import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.director.HorrorDirector;
import fr.penumbra.director.TensionState;
import fr.penumbra.entity.absent.AbsentManager;
import fr.penumbra.entity.originel.OriginelManager;
import fr.penumbra.entity.projectionist.ProjectionistEntity;
import fr.penumbra.entity.rampant.RampantManager;
import fr.penumbra.entity.scuttler.ScuttlerManager;
import fr.penumbra.event.underground.AbstractUndergroundEvent;
import fr.penumbra.network.PenumbraC2S;
import fr.penumbra.photo.PhotoAlbumState;
import fr.penumbra.photo.investigation.InvestigationRules;
import fr.penumbra.story.StoryManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

/**
 * Intégration monde des scènes liées 0.12.
 *
 * <p>Le coordinateur conserve les règles et les baux ; cette classe ne fait
 * que transformer des transitions observables (flash, mouvement, disparition,
 * lumière, réouverture de la caméra) en signaux. Aucun chunk n'est chargé ou
 * forcé par une scène.</p>
 */
public final class LinkedSceneManager {

    private static final int CHECK_INTERVAL_TICKS = 100;
    private static final int CHECK_OFFSET_TICKS = 35;
    private static final int BASE_ONE_IN = 28;
    private static final long NATURAL_COOLDOWN_TICKS = 20L * 60L * 3L;
    private static final double FOLLOW_DISTANCE_SQR = 3.0 * 3.0;
    private static final int LIGHT_COUNTER_MINIMUM = 8;

    private static final Map<MinecraftServer, ServerState> STATES =
            new IdentityHashMap<>();

    private LinkedSceneManager() {
    }

    private static final class RuntimeScene {
        private final UUID token;
        private final LinkedSceneType type;
        private final Vec3 startingPosition;
        private final int startingBlockLight;
        private final long signalAfterTick;
        private final UUID primaryEntity;
        private boolean cameraWasClosed;
        private boolean primaryTransitionSent;

        private RuntimeScene(UUID token, LinkedSceneType type, Vec3 startingPosition,
                             int startingBlockLight, long signalAfterTick,
                             UUID primaryEntity, boolean cameraWasClosed) {
            this.token = token;
            this.type = type;
            this.startingPosition = startingPosition;
            this.startingBlockLight = startingBlockLight;
            this.signalAfterTick = signalAfterTick;
            this.primaryEntity = primaryEntity;
            this.cameraWasClosed = cameraWasClosed;
        }
    }

    private static final class ServerState {
        private final LinkedSceneCoordinator coordinator = new LinkedSceneCoordinator();
        private final Map<UUID, RuntimeScene> runtimeByOwner = new HashMap<>();
        private final Map<UUID, Long> lastNaturalScene = new HashMap<>();
    }

    /** Boucle serveur : transitions, suivis dus, puis rare tentative naturelle. */
    public static void onServerTick(MinecraftServer server) {
        ServerState state = STATES.get(server);
        if (state != null) {
            long now = server.overworld().getGameTime();
            updateActive(server, state, now);
            dispatchDue(server, state, now);
            consumeCompletions(server, state);
        }

        if (Math.floorMod(server.getTickCount() - CHECK_OFFSET_TICKS,
                CHECK_INTERVAL_TICKS) != 0) {
            return;
        }
        PenumbraConfig config = PenumbraConfig.get();
        float pressure = Math.max(0.0f, config.eventFrequency)
                * Math.max(0.0f, config.horrorIntensity);
        if (!config.linkedMonsterEventsEnabled || pressure <= 0.0f) {
            return;
        }
        if (state == null) {
            state = STATES.computeIfAbsent(server, ignored -> new ServerState());
        }
        int oneIn = Math.max(2, Math.round(BASE_ONE_IN / pressure));
        long now = server.overworld().getGameTime();
        for (ServerPlayer player : HorrorDirector.fairAutomaticPlayers(
                server, CHECK_INTERVAL_TICKS, 12)) {
            if (state.coordinator.isOwnerBusy(player.getUUID())
                    || !naturalPacingAllows(state, player, now)
                    || player.getRandom().nextInt(oneIn) != 0
                    || !HorrorDirector.automaticSceneAvailable(player)) {
                continue;
            }
            LinkedSceneType type = chooseNaturalType(player);
            if (type != null && tryStart(state, player, type, now, false)) {
                state.lastNaturalScene.put(player.getUUID(), now);
                HorrorDirector.recordAutomaticScene(player);
                break; // une seule nouvelle scène lourde par cycle serveur
            }
        }
    }

    /** Commande de diagnostic : ignore chance, grâce et stade, jamais le placement réel. */
    public static boolean forceStart(ServerPlayer player, LinkedSceneType type) {
        if (player == null || type == null) {
            return false;
        }
        MinecraftServer server = player.level().getServer();
        ServerState state = STATES.computeIfAbsent(server, ignored -> new ServerState());
        return tryStart(state, player, type, server.overworld().getGameTime(), true);
    }

    /** Le flash validé par le serveur contre immédiatement le faux guide actif. */
    public static void onCameraFlash(ServerPlayer player) {
        if (player == null) {
            return;
        }
        ServerState state = STATES.get(player.level().getServer());
        RuntimeScene runtime = state == null ? null : state.runtimeByOwner.get(player.getUUID());
        if (runtime != null && runtime.type == LinkedSceneType.FALSE_GUIDE) {
            state.coordinator.signal(runtime.token,
                    LinkedSceneCoordinator.Signal.ABSENT_FLASHED,
                    player.level().getGameTime());
        }
    }

    /** Transition C2S fiable : seule une nouvelle activation peut armer le piège photo. */
    public static void onCameraStateChanged(ServerPlayer player, boolean cameraOpen) {
        if (player == null) {
            return;
        }
        ServerState state = STATES.get(player.level().getServer());
        RuntimeScene runtime = state == null ? null : state.runtimeByOwner.get(player.getUUID());
        if (runtime == null || runtime.type != LinkedSceneType.NEGATIVE_WITNESS) {
            return;
        }
        if (!cameraOpen) {
            runtime.cameraWasClosed = true;
            return;
        }
        Optional<LinkedSceneCoordinator.SceneSnapshot> snapshot =
                state.coordinator.byToken(runtime.token);
        if (runtime.cameraWasClosed && snapshot.isPresent()
                && snapshot.get().phase() == LinkedSceneCoordinator.Phase.CAMERA_TRAP_ARMED) {
            state.coordinator.signal(runtime.token,
                    LinkedSceneCoordinator.Signal.CAMERA_REOPENED,
                    player.level().getGameTime());
        }
    }

    public static Optional<LinkedSceneCoordinator.SceneSnapshot> active(ServerPlayer player) {
        if (player == null) {
            return Optional.empty();
        }
        ServerState state = STATES.get(player.level().getServer());
        return state == null ? Optional.empty() : state.coordinator.active(player.getUUID());
    }

    public static void onPlayerDisconnect(MinecraftServer server, UUID playerId) {
        ServerState state = STATES.get(server);
        if (state == null) {
            return;
        }
        state.coordinator.cancelOwner(playerId, server.overworld().getGameTime());
        state.runtimeByOwner.remove(playerId);
    }

    public static void onServerStopped(MinecraftServer server) {
        ServerState state = STATES.remove(server);
        if (state != null) {
            state.coordinator.clear(server.overworld().getGameTime());
            state.runtimeByOwner.clear();
            state.lastNaturalScene.clear();
        }
    }

    private static boolean naturalPacingAllows(
            ServerState state, ServerPlayer player, long now) {
        if (!player.isAlive() || player.isSpectator() || player.isSleeping()
                || player.level().getDifficulty() == Difficulty.PEACEFUL
                || !HorrorDirector.isGraceComplete(player)
                || !HorrorDirector.physicalManifestationAllowed(player)
                || HorrorDirector.getState(player.level().getServer(), player.getUUID())
                .ordinal() < TensionState.WATCHED.ordinal()) {
            return false;
        }
        Long previous = state.lastNaturalScene.get(player.getUUID());
        return previous == null || now < previous || now - previous >= NATURAL_COOLDOWN_TICKS;
    }

    private static LinkedSceneType chooseNaturalType(ServerPlayer player) {
        boolean underground = AbstractUndergroundEvent.isUnderground(player);
        int stage = HorrorDirector.effectiveContentStage(player);
        if (underground) {
            if (stage < LinkedSceneRules.minimumStage(LinkedSceneType.SKITTER_OMEN)) {
                return null;
            }
            if (stage >= LinkedSceneRules.minimumStage(LinkedSceneType.FALSE_GUIDE)
                    && player.getRandom().nextBoolean()) {
                return LinkedSceneType.FALSE_GUIDE;
            }
            return LinkedSceneType.SKITTER_OMEN;
        }
        return stage >= LinkedSceneRules.minimumStage(LinkedSceneType.NEGATIVE_WITNESS)
                ? LinkedSceneType.NEGATIVE_WITNESS : null;
    }

    private static boolean tryStart(ServerState state, ServerPlayer player,
                                    LinkedSceneType type, long now, boolean forced) {
        PenumbraConfig config = PenumbraConfig.get();
        boolean underground = AbstractUndergroundEvent.isUnderground(player);
        if (!forced && !LinkedSceneRules.eligible(type,
                config.linkedMonsterEventsEnabled, player.isAlive(), player.isSpectator(),
                player.level().getDifficulty() == Difficulty.PEACEFUL,
                HorrorDirector.isGraceComplete(player),
                HorrorDirector.physicalManifestationAllowed(player),
                HorrorDirector.effectiveContentStage(player), underground)) {
            return false;
        }
        if (!player.isAlive() || player.isSpectator()
                || player.level().getDifficulty() == Difficulty.PEACEFUL) {
            return false;
        }

        SceneAudience audience = captureAudience(player);
        Vec3 position = player.position();
        LinkedSceneCoordinator.StartRequest request = new LinkedSceneCoordinator.StartRequest(
                player.getUUID(), type, audience,
                player.level().dimension().identifier().toString(),
                new LinkedSceneCoordinator.SceneAnchor(position.x, position.y, position.z),
                InvestigationRules.knowsCounterplay(
                        PhotoAlbumState.get(player.level().getServer())
                                .dossier(player.getUUID()).discovered(), type));
        LinkedSceneCoordinator.StartResult lease = state.coordinator.tryAcquire(request, now);
        if (!lease.started()) {
            return false;
        }

        UUID primaryEntity = null;
        boolean spawned;
        switch (type) {
            case FALSE_GUIDE -> spawned = AbsentManager.forceSighting(player);
            case SKITTER_OMEN -> spawned = ScuttlerManager.forceCrossing(player);
            case NEGATIVE_WITNESS -> {
                ProjectionistEntity entity = ProjectionistEntity.spawnBehind(player.level(), player);
                spawned = entity != null;
                primaryEntity = entity == null ? null : entity.getUUID();
            }
            default -> spawned = false;
        }
        if (!spawned) {
            state.coordinator.primaryFailed(lease.token(), now);
            consumeCompletions(player.level().getServer(), state);
            return false;
        }

        long transitionDelay = switch (type) {
            case FALSE_GUIDE -> 60L;
            case SKITTER_OMEN -> 55L;
            case NEGATIVE_WITNESS -> 70L;
        };
        boolean cameraClosed = !PenumbraC2S.isZooming(player.getUUID());
        state.runtimeByOwner.put(player.getUUID(), new RuntimeScene(
                lease.token(), type, position,
                player.level().getBrightness(LightLayer.BLOCK, player.blockPosition()),
                now + transitionDelay, primaryEntity, cameraClosed));
        HorrorDirector.noteManifestation(player);
        if (config.debugLogging) {
            PenumbraMod.LOGGER.info("[Penumbra] Scène liée '{}' démarrée pour {} ({})",
                    type.id(), player.getGameProfile().name(), audience.scope());
        }
        return true;
    }

    private static SceneAudience captureAudience(ServerPlayer owner) {
        if (!PenumbraConfig.get().multiplayerContent) {
            return SceneAudience.privateTo(owner.getUUID());
        }
        double radiusSqr = SceneAudience.LOCAL_RADIUS * SceneAudience.LOCAL_RADIUS;
        Set<UUID> witnesses = new HashSet<>();
        for (ServerPlayer candidate : owner.level().players()) {
            if (candidate != owner && candidate.isAlive() && !candidate.isSpectator()
                    && candidate.distanceToSqr(owner) <= radiusSqr) {
                witnesses.add(candidate.getUUID());
            }
        }
        return witnesses.isEmpty()
                ? SceneAudience.privateTo(owner.getUUID())
                : SceneAudience.local(owner.getUUID(), witnesses);
    }

    private static void updateActive(
            MinecraftServer server, ServerState state, long now) {
        for (Map.Entry<UUID, RuntimeScene> entry
                : new ArrayList<>(state.runtimeByOwner.entrySet())) {
            UUID ownerId = entry.getKey();
            RuntimeScene runtime = entry.getValue();
            ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
            Optional<LinkedSceneCoordinator.SceneSnapshot> snapshot =
                    state.coordinator.byToken(runtime.token);
            if (snapshot.isEmpty()) {
                state.runtimeByOwner.remove(ownerId, runtime);
                continue;
            }
            if (owner == null || !owner.isAlive() || owner.isSpectator()
                    || !snapshot.get().dimension().equals(
                    owner.level().dimension().identifier().toString())) {
                state.coordinator.signal(runtime.token,
                        LinkedSceneCoordinator.Signal.OWNER_INVALID, now);
                continue;
            }

            if ((runtime.type == LinkedSceneType.FALSE_GUIDE
                    || runtime.type == LinkedSceneType.SKITTER_OMEN)
                    && caveWasLit(owner, runtime)) {
                state.coordinator.signal(runtime.token,
                        LinkedSceneCoordinator.Signal.CAVE_LIT, now);
                continue;
            }
            if (runtime.primaryTransitionSent || now < runtime.signalAfterTick) {
                continue;
            }
            switch (runtime.type) {
                case FALSE_GUIDE -> {
                    if (owner.position().distanceToSqr(runtime.startingPosition)
                            >= FOLLOW_DISTANCE_SQR) {
                        runtime.primaryTransitionSent = true;
                        state.coordinator.signal(runtime.token,
                                LinkedSceneCoordinator.Signal.ABSENT_FOLLOWED, now);
                    }
                }
                case SKITTER_OMEN -> {
                    runtime.primaryTransitionSent = true;
                    state.coordinator.signal(runtime.token,
                            LinkedSceneCoordinator.Signal.SCUTTLER_VANISHED, now);
                }
                case NEGATIVE_WITNESS -> {
                    Entity entity = findEntity(server, runtime.primaryEntity);
                    if (entity == null || !entity.isAlive() || now >= runtime.signalAfterTick + 80L) {
                        runtime.primaryTransitionSent = true;
                        state.coordinator.signal(runtime.token,
                                LinkedSceneCoordinator.Signal.PROJECTIONIST_NATURAL_VANISH, now);
                    }
                }
            }
        }
    }

    private static boolean caveWasLit(ServerPlayer owner, RuntimeScene runtime) {
        int current = owner.level().getBrightness(LightLayer.BLOCK, owner.blockPosition());
        return current >= LIGHT_COUNTER_MINIMUM
                && current >= runtime.startingBlockLight + 2;
    }

    private static void dispatchDue(MinecraftServer server, ServerState state, long now) {
        for (LinkedSceneCoordinator.SceneAction action : state.coordinator.pollDue(now)) {
            ServerPlayer owner = server.getPlayerList().getPlayer(action.owner());
            if (owner == null || !owner.isAlive() || owner.isSpectator()
                    || !action.dimension().equals(
                    owner.level().dimension().identifier().toString())) {
                state.coordinator.acknowledge(action.token(),
                        LinkedSceneCoordinator.ActionOutcome.DENIED, now);
                continue;
            }
            BlockPos anchor = BlockPos.containing(
                    action.anchor().x(), action.anchor().y(), action.anchor().z());
            if (!owner.level().isLoaded(anchor)) {
                state.coordinator.acknowledge(action.token(),
                        LinkedSceneCoordinator.ActionOutcome.AREA_UNLOADED, now);
                continue;
            }
            boolean spawned;
            if (action.action() == LinkedSceneCoordinator.ActionType.SPAWN_RAMPANT) {
                if (owner.level().getBrightness(LightLayer.BLOCK, owner.blockPosition())
                        >= LIGHT_COUNTER_MINIMUM) {
                    state.coordinator.acknowledge(action.token(),
                            LinkedSceneCoordinator.ActionOutcome.BLOCKED_BY_LIGHT, now);
                    continue;
                }
                spawned = RampantManager.forceSpawn(owner) != null;
            } else {
                spawned = OriginelManager.forceCameraSighting(owner)
                        || OriginelManager.forceSighting(owner);
            }
            state.coordinator.acknowledge(action.token(), spawned
                    ? LinkedSceneCoordinator.ActionOutcome.SPAWNED
                    : LinkedSceneCoordinator.ActionOutcome.INVALID_PLACEMENT, now);
            if (spawned) {
                HorrorDirector.noteManifestation(owner);
            }
        }
    }

    private static void consumeCompletions(
            MinecraftServer server, ServerState state) {
        for (LinkedSceneCoordinator.SceneCompletion completion
                : state.coordinator.drainCompletions()) {
            RuntimeScene runtime = state.runtimeByOwner.remove(completion.owner());
            ServerPlayer owner = server.getPlayerList().getPlayer(completion.owner());
            if (owner != null && completion.reason() == LinkedSceneCoordinator.EndReason.COMPLETED) {
                StoryManager.noteDiscovery(owner, StoryManager.KEY_LINKED_SCENE);
            } else if (owner != null && runtime != null
                    && state.coordinator.byToken(runtime.token).isEmpty()
                    && isCountered(completion.reason())) {
                owner.sendOverlayMessage(Component.translatable(
                        "message.penumbra.linked.countered." + completion.type().id()));
            }
            if (PenumbraConfig.get().debugLogging) {
                PenumbraMod.LOGGER.info("[Penumbra] Scène liée '{}' terminée pour {} : {}",
                        completion.type().id(), completion.owner(), completion.reason());
            }
        }
    }

    private static boolean isCountered(LinkedSceneCoordinator.EndReason reason) {
        return reason == LinkedSceneCoordinator.EndReason.COUNTERED_FLASH
                || reason == LinkedSceneCoordinator.EndReason.COUNTERED_LIGHT
                || reason == LinkedSceneCoordinator.EndReason.COUNTERED_WARD
                || reason == LinkedSceneCoordinator.EndReason.ESCAPED_CAMERA_TRAP;
    }

    private static Entity findEntity(MinecraftServer server, UUID entityId) {
        if (entityId == null) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(entityId);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }
}

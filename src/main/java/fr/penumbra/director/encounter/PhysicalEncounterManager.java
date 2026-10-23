package fr.penumbra.director.encounter;

import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.director.HorrorDirector;
import fr.penumbra.director.sequence.LinkedSceneManager;
import fr.penumbra.director.presence.SecondPresenceManager;
import fr.penumbra.entity.HostBehavior;
import fr.penumbra.entity.HostEntity;
import fr.penumbra.entity.absent.AbsentEntity;
import fr.penumbra.entity.contrefait.ContrefaitEntity;
import fr.penumbra.entity.listener.ListenerEntity;
import fr.penumbra.entity.originel.OriginelEntity;
import fr.penumbra.entity.rampant.RampantEntity;
import fr.penumbra.entity.scuttler.ScuttlerEntity;
import fr.penumbra.entity.watcher.WatcherEntity;
import fr.penumbra.network.PenumbraC2S;
import fr.penumbra.network.PenumbraNetworking;
import fr.penumbra.registry.PenumbraEntities;
import fr.penumbra.registry.PenumbraSounds;
import fr.penumbra.story.ConfrontationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

/** Owner-scoped, finite physical scenes. All callbacks run on the server thread. */
public final class PhysicalEncounterManager {
    private static final Map<MinecraftServer, ServerState> STATES = new IdentityHashMap<>();

    private PhysicalEncounterManager() { }

    private static final class ServerState {
        final EncounterBudget budget = new EncounterBudget();
        final Map<UUID, Scene> scenes = new HashMap<>();
    }

    private static final class Scene {
        final ServerPlayer player;
        final ServerLevel level;
        final Vec3 origin;
        final BlockPos anchor;
        final boolean architectural;
        final EncounterSession session;
        HostEntity actor;
        Vec3 previousPosition;
        Vec3 reboundPosition;

        Scene(ServerPlayer player, @Nullable BlockPos anchor, boolean architectural,
              HostEntity actor, EncounterSession.Variant variant, long now) {
            this.player = player;
            this.level = player.level();
            this.origin = player.position();
            this.anchor = anchor == null ? null : anchor.immutable();
            this.architectural = architectural;
            this.actor = actor;
            this.previousPosition = actor.position();
            this.session = new EncounterSession(variant, now);
        }
    }

    /** A rejected or unplaceable scene neither spawns an actor nor spends its cooldown. */
    public static boolean begin(ServerPlayer player, String scareId, @Nullable BlockPos anchor) {
        if (player == null || !supported(scareId) || !eligible(player)
                || HorrorDirector.isInGlobalCooldown(player)
                || LinkedSceneManager.active(player).isPresent()
                || SecondPresenceManager.isActive(player.getUUID())
                || hasCompetingManifestation(player, null)) return false;
        MinecraftServer server = player.level().getServer();
        ServerState state = STATES.computeIfAbsent(server, ignored -> new ServerState());
        long now = server.overworld().getGameTime();
        if (!state.budget.canBegin(player.getUUID(), now)) return false;

        boolean architectural = "refuge_scare".equals(scareId);
        HostEntity host = newActor(player);
        var position = EncounterPlacement.find(player.level(), player, host, anchor,
                architectural, false, null);
        if (position.isEmpty()) return false;
        host.setPos(position.get());
        facePlayer(host, player);
        if (!player.level().addFreshEntity(host)) return false;
        if (!state.budget.started(player.getUUID(), now)) {
            host.discard();
            return false;
        }
        EncounterSession.Variant variant = variant(scareId, player.getRandom().nextInt(4));
        state.scenes.put(player.getUUID(), new Scene(player, anchor, architectural, host, variant, now));
        PenumbraNetworking.sendSceneHush(player, 24);
        PenumbraNetworking.sendSoundDirect(player, architectural ? PenumbraSounds.DOOR_GROAN
                : PenumbraSounds.HOST_WHISPER, host.getEyePosition(), 0.38f, 0.9f);
        return true;
    }

    static boolean supported(String scareId) {
        return scareId != null && switch (scareId) {
            case "chest_scare", "refuge_scare", "lookback_scare", "inventory_scare",
                    "quiet_scare", "torch_scare" -> true;
            default -> false;
        };
    }

    static EncounterSession.Variant variant(String scareId, int choice) {
        if ("refuge_scare".equals(scareId)) return EncounterSession.Variant.REBOUND;
        if ("quiet_scare".equals(scareId) || "torch_scare".equals(scareId)) {
            return EncounterSession.Variant.RECOIL;
        }
        return switch (Math.floorMod(choice, 4)) {
            case 0 -> EncounterSession.Variant.LUNGE;
            case 1 -> EncounterSession.Variant.CHASE;
            default -> EncounterSession.Variant.RECOIL;
        };
    }

    public static boolean isActive(UUID playerId) {
        return STATES.values().stream().anyMatch(state -> state.scenes.containsKey(playerId));
    }

    public static void onServerTick(MinecraftServer server) {
        ServerState state = STATES.get(server);
        if (state == null) return;
        long now = server.overworld().getGameTime();
        state.budget.prune(now);
        var iterator = state.scenes.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            Scene scene = entry.getValue();
            if (!valid(server, scene)
                    || server.getTickCount() % 10 == 0 && hasCompetingManifestation(scene.player, scene.actor)
                    || !tick(scene, now)) {
                discard(scene);
                state.budget.finished(entry.getKey());
                iterator.remove();
            }
        }
    }

    private static boolean tick(Scene scene, long now) {
        // Fabric server callbacks still run while /tick freeze pauses world simulation.
        if (!scene.session.clockChanged(now)) return true;
        HostEntity host = scene.actor;
        boolean present = host != null && host.isAlive() && !host.isRemoved();
        EncounterSession.Phase before = scene.session.phase();
        if (present && (!EncounterPlacement.safeCurrent(scene.level, host,
                before != EncounterSession.Phase.LUNGE && before != EncounterSession.Phase.RECOVERY)
                || EncounterPlacement.anotherPlayerNear(scene.level, scene.player, host.getBoundingBox()))) return false;
        boolean watched = present && EncounterPlacement.facingDot(scene.player.getEyePosition(),
                scene.player.getLookAngle(), host.position().add(0, 1.3, 0)) >= 0.82
                && EncounterPlacement.visible(scene.level, scene.player, host.position());
        EncounterSession.Phase phase = scene.session.advance(now, watched, present,
                PenumbraConfig.get().chasesEnabled);
        if (phase == EncounterSession.Phase.DONE) return false;
        if (phase != before && !enter(scene, phase)) return false;
        host = scene.actor;
        switch (scene.session.phase()) {
            case OBSERVE, REAPPEAR -> {
                host.setDeltaMovement(Vec3.ZERO);
                facePlayer(host, scene.player);
            }
            case RETREAT -> {
                host.setDeltaMovement(Vec3.ZERO);
                EncounterPlacement.retreat(scene.level, host, scene.player);
                facePlayer(host, scene.player);
            }
            default -> { }
        }
        return true;
    }

    private static boolean enter(Scene scene, EncounterSession.Phase phase) {
        HostEntity host = scene.actor;
        switch (phase) {
            case RETREAT -> stopAndRetreat(host);
            case LUNGE -> {
                PenumbraNetworking.sendSceneHush(scene.player, 36);
                if (!host.beginStareLunge(scene.player)) {
                    scene.session.retreat();
                    stopAndRetreat(host);
                }
            }
            case CHASE -> {
                PenumbraNetworking.sendSceneHush(scene.player, 30);
                host.setBehavior(HostBehavior.CHASING);
                host.setTarget(scene.player);
                host.setNoAi(false);
            }
            case HUSH -> {
                scene.previousPosition = host.position();
                host.discard();
                scene.actor = null;
                HostEntity probe = newActor(scene.player);
                var behind = EncounterPlacement.find(scene.level, scene.player, probe, scene.anchor,
                        scene.architectural, true, scene.previousPosition);
                if (behind.isEmpty()) return false;
                scene.reboundPosition = behind.get();
                PenumbraNetworking.sendSceneHush(scene.player, EncounterSession.HUSH_TICKS);
                PenumbraNetworking.sendSoundDirect(scene.player, PenumbraSounds.HOST_STEP,
                        scene.reboundPosition, 0.5f, 0.86f);
            }
            case REAPPEAR -> {
                // Keep the sound's position when the player turns towards it, but recheck terrain.
                HostEntity second = newActor(scene.player);
                if (scene.reboundPosition == null || !EncounterPlacement.safeAppearance(scene.level,
                        scene.player, second, scene.reboundPosition, scene.architectural)) return false;
                second.setPos(scene.reboundPosition);
                facePlayer(second, scene.player);
                if (!scene.level.addFreshEntity(second)) return false;
                scene.actor = second;
            }
            default -> { }
        }
        return true;
    }

    private static boolean eligible(ServerPlayer player) {
        return PenumbraConfig.get().hostEnabled && PenumbraConfig.get().horrorIntensity > 0
                && player.isAlive() && !player.isRemoved() && !player.isSpectator()
                && !player.isSleeping() && !player.isPassenger() && !player.isInWater()
                && !player.isInLava() && player.level().getDifficulty() != Difficulty.PEACEFUL
                && !PenumbraC2S.isScreenBlocking(player) && !ConfrontationManager.isActive(player.getUUID());
    }

    private static boolean valid(MinecraftServer server, Scene scene) {
        return server.getPlayerList().getPlayer(scene.player.getUUID()) == scene.player
                && scene.player.level() == scene.level && eligible(scene.player)
                && scene.player.position().distanceToSqr(scene.origin) <= 36.0 * 36.0;
    }

    /** One local query, excluding our own body and other players' private manifestations. */
    private static boolean hasCompetingManifestation(ServerPlayer player, @Nullable HostEntity ownActor) {
        return !player.level().getEntitiesOfClass(net.minecraft.world.entity.Entity.class,
                player.getBoundingBox().inflate(24), entity -> entity != ownActor && entity.isAlive()
                        && (entity instanceof HostEntity host
                                && (!host.isPrivateToOwner() || host.isOwnedBy(player))
                        || entity instanceof WatcherEntity watcher && watcher.isOwnedBy(player)
                        || entity instanceof RampantEntity rampant && rampant.isOwnedBy(player)
                        || entity instanceof ListenerEntity listener
                                && (listener.isOwnedBy(player) || PenumbraConfig.get().multiplayerContent)
                        || entity instanceof AbsentEntity absent && absent.isOwnedBy(player)
                        || entity instanceof ScuttlerEntity scuttler && scuttler.isOwnedBy(player)
                        || entity instanceof OriginelEntity originel && originel.isOwnedBy(player)
                        || entity instanceof ContrefaitEntity contrefait && contrefait.isOwnedBy(player))).isEmpty();
    }

    private static HostEntity newActor(ServerPlayer player) {
        HostEntity host = new HostEntity(PenumbraEntities.HOST, player.level());
        host.setOwner(player);
        host.setPrivateToOwner(true);
        host.setAutonomousStareEnabled(false);
        host.setNoAi(true);
        host.noPhysics = false;
        host.setMaxLifetimeTicks(EncounterSession.MAX_TICKS + 20);
        host.setBehavior(HostBehavior.OBSERVING);
        return host;
    }

    private static void facePlayer(HostEntity host, ServerPlayer player) {
        Vec3 delta = player.getEyePosition().subtract(host.getEyePosition());
        float yaw = (float) (Math.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0f;
        host.setYRot(yaw);
        host.setYBodyRot(yaw);
        host.setYHeadRot(yaw);
        host.setXRot((float) (-Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z))
                * Mth.RAD_TO_DEG));
    }

    private static void stopAndRetreat(HostEntity host) {
        host.getNavigation().stop();
        host.setTarget(null);
        host.setBehavior(HostBehavior.RETREATING);
        host.setNoAi(true);
        host.setNoGravity(false);
    }

    /** PhotoNetworking has already called repelByCamera; keep that visible recoil, cancel all followups. */
    public static void onPhotographed(ServerPlayer player, HostEntity host) {
        ServerState state = STATES.get(player.level().getServer());
        if (state == null) return;
        Scene scene = state.scenes.get(player.getUUID());
        if (scene != null && scene.actor == host && host.isOwnedBy(player)) scene.session.recover();
    }

    /** A directed pursuit returns to its own retreat, never the director's generic teleport/dissolve. */
    public static boolean finishChase(ServerPlayer player, HostEntity host) {
        if (player == null) return false;
        ServerState state = STATES.get(player.level().getServer());
        Scene scene = state == null ? null : state.scenes.get(player.getUUID());
        if (scene == null || scene.actor != host || !host.isOwnedBy(player)) return false;
        if (scene.session.phase() != EncounterSession.Phase.RECOVERY) {
            scene.session.retreat();
            stopAndRetreat(host);
        }
        return true;
    }

    public static void onPlayerDisconnect(MinecraftServer server, UUID playerId) {
        ServerState state = STATES.get(server);
        if (state == null) return;
        Scene scene = state.scenes.remove(playerId);
        if (scene != null) discard(scene);
        state.budget.finished(playerId);
    }

    public static void onServerStopped(MinecraftServer server) {
        ServerState state = STATES.remove(server);
        if (state != null) state.scenes.values().forEach(PhysicalEncounterManager::discard);
    }

    private static void discard(Scene scene) {
        scene.session.cancel();
        if (scene.actor != null) scene.actor.discard();
        scene.actor = null;
    }
}

package fr.penumbra.director.presence;

import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.director.HorrorDirector;
import fr.penumbra.director.encounter.PhysicalEncounterManager;
import fr.penumbra.director.sequence.LinkedSceneManager;
import fr.penumbra.entity.HostBehavior;
import fr.penumbra.entity.HostEntity;
import fr.penumbra.entity.absent.AbsentEntity;
import fr.penumbra.entity.contrefait.ContrefaitEntity;
import fr.penumbra.entity.listener.ListenerEntity;
import fr.penumbra.entity.originel.OriginelEntity;
import fr.penumbra.entity.rampant.RampantEntity;
import fr.penumbra.entity.scuttler.ScuttlerEntity;
import fr.penumbra.entity.watcher.WatcherEntity;
import fr.penumbra.journal.WarrickCassetteItem;
import fr.penumbra.network.PenumbraC2S;
import fr.penumbra.network.PenumbraNetworking;
import fr.penumbra.registry.PenumbraEntities;
import fr.penumbra.registry.PenumbraParticles;
import fr.penumbra.registry.PenumbraSounds;
import fr.penumbra.story.ConfrontationManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** A private, continuous body behind a real corner; never modifies terrain or teleports after spawning. */
public final class SecondPresenceManager {
    private static final int MAX_ACTIVE = 6;
    private static final int MAX_COOLDOWNS = 1024;
    private static final long COOLDOWN_TICKS = 7200L;
    private static final Map<MinecraftServer, State> STATES = new IdentityHashMap<>();

    private SecondPresenceManager() { }

    private static final class State {
        final Map<UUID, Scene> scenes = new HashMap<>();
        final Map<UUID, Long> starts = new HashMap<>();
        void prune(long now) {
            starts.entrySet().removeIf(entry -> now < entry.getValue() || now - entry.getValue() >= COOLDOWN_TICKS);
        }
    }

    private static final class Scene {
        final ServerPlayer owner;
        final ServerLevel level;
        final HostEntity actor;
        final Vec3 origin;
        final PresencePlacement.Plan plan;
        final PresenceSession session;
        final boolean mayAttack;
        int pathIndex = 1;
        int retreatIndex;
        long lastStep;
        Vec3 previousOwner;
        Vec3 noticedOwner;
        Vec3 noticedActor;
        boolean observationRecorded;

        Scene(ServerPlayer owner, HostEntity actor, PresencePlacement.Plan plan, long now) {
            this.owner = owner; this.level = owner.level(); this.actor = actor; this.plan = plan;
            this.origin = owner.position(); this.previousOwner = owner.position();
            this.session = new PresenceSession(now); this.lastStep = now;
            // An approached body is not a deterministic trap: some encounters only withdraw.
            this.mayAttack = owner.getRandom().nextInt(3) != 0;
        }
    }

    /** Failed placement spends neither the personal cooldown nor any sound/manifestation. */
    public static boolean begin(ServerPlayer player) {
        return begin(player, false);
    }

    /** Administrator replay ignores only this encounter's personal repetition cooldown. */
    public static boolean force(ServerPlayer player) {
        return begin(player, true);
    }

    private static boolean begin(ServerPlayer player, boolean ignorePersonalCooldown) {
        if (player == null || !eligible(player) || vulnerable(player) || HorrorDirector.isInGlobalCooldown(player)
                || PhysicalEncounterManager.isActive(player.getUUID())
                || LinkedSceneManager.active(player).isPresent() || competing(player, null)) return false;
        MinecraftServer server = player.level().getServer();
        State state = STATES.computeIfAbsent(server, ignored -> new State());
        long now = server.overworld().getGameTime();
        state.prune(now);
        if (state.scenes.containsKey(player.getUUID()) || state.scenes.size() >= MAX_ACTIVE
                || !ignorePersonalCooldown && state.starts.containsKey(player.getUUID())
                || !state.starts.containsKey(player.getUUID()) && state.starts.size() >= MAX_COOLDOWNS) return false;
        HostEntity actor = new HostEntity(PenumbraEntities.HOST, player.level());
        actor.setOwner(player);
        actor.setPrivateToOwner(true);
        actor.setAutonomousStareEnabled(false);
        actor.setNoAi(true);
        actor.setNoGravity(false);
        actor.noPhysics = false;
        actor.setMaxLifetimeTicks(PresenceSession.MAX_TICKS + 20);
        actor.setBehavior(HostBehavior.OBSERVING);
        Optional<PresencePlacement.Plan> plan = PresencePlacement.find(player.level(), player, actor);
        if (plan.isEmpty()) return false;
        actor.setPos(plan.get().origin());
        if (PresencePlacement.anotherPlayerNear(player.level(), player, actor)) return false;
        facePlayer(actor, player);
        if (!player.level().addFreshEntity(actor)) return false;
        Scene scene = new Scene(player, actor, plan.get(), now);
        state.scenes.put(player.getUUID(), scene);
        state.starts.put(player.getUUID(), now);
        if (PenumbraConfig.get().improvedSoundsEnabled) {
            PenumbraNetworking.sendSoundDirectPriority(player, PenumbraSounds.HOST_STEP,
                    actor.position(), 0.48f, 0.81f);
        }
        return true;
    }

    public static boolean isActive(UUID playerId) {
        return STATES.values().stream().anyMatch(state -> state.scenes.containsKey(playerId));
    }

    /** Read-only preflight in HostEntity.tick, before its delegated physical attack can apply contact. */
    public static boolean mustCancelBeforeActorTick(HostEntity actor) {
        if (actor == null || !(actor.level() instanceof ServerLevel level)) return false;
        MinecraftServer server = level.getServer();
        State state = STATES.get(server);
        if (state == null) return false;
        for (Scene scene : state.scenes.values()) {
            if (scene.actor != actor) continue;
            return !valid(server, scene) || PresencePlacement.anotherPlayerNear(level, scene.owner, actor)
                    || (actor.isLunging() || actor.isGrappling()) && !PenumbraConfig.get().chasesEnabled;
        }
        return false;
    }

    public static void onServerTick(MinecraftServer server) {
        State state = STATES.get(server);
        if (state == null) return;
        long now = server.overworld().getGameTime();
        if (server.getTickCount() % 200 == 0) state.prune(now);
        var iterator = state.scenes.entrySet().iterator();
        while (iterator.hasNext()) {
            Scene scene = iterator.next().getValue();
            if (!valid(server, scene) || !tick(scene, now)) {
                finish(scene, server.getPlayerList().getPlayer(scene.owner.getUUID()) == scene.owner);
                iterator.remove();
            }
        }
    }

    private static boolean tick(Scene scene, long now) {
        if (!scene.session.clockChanged(now)) return true;
        HostEntity actor = scene.actor;
        if (!actor.isAlive() || actor.isRemoved()
                || PresencePlacement.anotherPlayerNear(scene.level, scene.owner, actor)
                || now % 10 == 0 && competing(scene.owner, actor)) return false;
        boolean attacking = actor.isLunging() || actor.isGrappling();
        if (!attacking && !actor.isRecovering() && vulnerable(scene.owner)) return false;
        if (!attacking && !actor.isRecovering() && !PresencePlacement.safeAt(scene.level, actor, actor.position())) return false;
        boolean occluded = PresencePlacement.occluded(scene.level, scene.owner, actor.position());
        boolean observed = !occluded && PresencePlacement.inView(scene.owner, actor.position());
        boolean calm = isCalm(scene.previousOwner, scene.owner.position(), scene.owner.swinging,
                scene.owner.isSprinting(), scene.owner.onGround());
        scene.previousOwner = scene.owner.position();
        PresenceSession.Phase before = scene.session.phase();
        boolean attacksAllowed = PenumbraConfig.get().chasesEnabled && scene.mayAttack;
        PresenceSession.Phase phase = scene.session.advance(now, observed, occluded, calm,
                provoking(scene), attacksAllowed, actor.isRecovering());
        if (phase == PresenceSession.Phase.DONE) return false;
        if (observed && !scene.observationRecorded) {
            scene.observationRecorded = true;
            scene.noticedOwner = scene.owner.position();
            scene.noticedActor = actor.position();
            HorrorDirector.noteHostObserved(scene.owner);
            HorrorDirector.noteManifestation(scene.owner);
        }
        if (phase != before) {
            if (phase == PresenceSession.Phase.NOTICED && PenumbraConfig.get().improvedSoundsEnabled) {
                PenumbraNetworking.sendSoundDirectPriority(scene.owner, PenumbraSounds.HOST_WHISPER,
                        actor.getEyePosition(), 0.33f, 0.77f);
            }
            if (phase == PresenceSession.Phase.WITHDRAWING || phase == PresenceSession.Phase.DEPARTING
                    && before != PresenceSession.Phase.WITHDRAWING) {
                scene.retreatIndex = Math.max(0, Math.min(scene.pathIndex - 1, scene.plan.points().size() - 1));
            }
            if (phase == PresenceSession.Phase.LUNGE) {
                if (!observed || !actor.beginStareLunge(scene.owner)) scene.session.depart();
            }
        }
        switch (scene.session.phase()) {
            case LISTENING, NOTICED -> { actor.setDeltaMovement(Vec3.ZERO); facePlayer(actor, scene.owner); }
            case FOLLOWING -> {
                // The owner can stop its approach by checking the corner. It does not jump to a new angle.
                if (!observed) moveAlong(scene, false, now);
                facePlayer(actor, scene.owner);
            }
            case WITHDRAWING, DEPARTING -> {
                moveAlong(scene, true, now);
                facePlayer(actor, scene.owner);
            }
            default -> { } // Host owns its existing windup, leap, grapple and camera recoil.
        }
        return true;
    }

    private static void moveAlong(Scene scene, boolean reverse, long now) {
        int index = reverse ? scene.retreatIndex : scene.pathIndex;
        if (index < 0 || index >= scene.plan.points().size()) return;
        Vec3 target = scene.plan.points().get(index);
        Vec3 current = scene.actor.position();
        // A waypoint already reached is bookkeeping, not a retreat towards the nearby owner.
        if (current.distanceToSqr(target) < 0.001) {
            if (reverse) scene.retreatIndex--;
            else scene.pathIndex++;
            return;
        }
        Vec3 next = PresencePlacement.stepToward(current, target, PresencePlacement.STEP);
        if (!PresencePlacement.safeSegment(scene.level, scene.actor, current, next)) {
            if (!reverse) scene.retreatIndex = Math.max(0, scene.pathIndex - 1);
            scene.session.depart();
            return;
        }
        if (!PresencePlacement.separationAllowsStep(current, next, scene.owner.position(), reverse)) {
            if (!reverse) scene.retreatIndex = Math.max(0, scene.pathIndex - 1);
            scene.session.depart();
            return;
        }
        scene.actor.setDeltaMovement(Vec3.ZERO);
        scene.actor.setPos(next);
        if (next.distanceToSqr(current) > 0.002 && now - scene.lastStep >= 18
                && PenumbraConfig.get().improvedSoundsEnabled) {
            PenumbraNetworking.sendSoundDirectPriority(scene.owner, PenumbraSounds.HOST_STEP,
                    next, reverse ? 0.34f : 0.45f, 0.82f + scene.owner.getRandom().nextFloat() * 0.12f);
            scene.lastStep = now;
        }
        if (next.distanceToSqr(target) < 0.001) {
            if (reverse) scene.retreatIndex--;
            else scene.pathIndex++;
        }
    }

    static boolean isCalm(Vec3 previous, Vec3 current, boolean swinging, boolean sprinting, boolean grounded) {
        return previous.distanceToSqr(current) <= 0.012 * 0.012 && !swinging && !sprinting && grounded;
    }

    private static boolean provoking(Scene scene) {
        if (scene.noticedOwner == null || scene.noticedActor == null || scene.actor.distanceToSqr(scene.owner) > 6.5 * 6.5) return false;
        return scene.owner.swinging || scene.owner.position().distanceTo(scene.noticedActor) + 1.25
                < scene.noticedOwner.distanceTo(scene.noticedActor);
    }

    private static boolean eligible(ServerPlayer player) {
        PenumbraConfig config = PenumbraConfig.get();
        return config.secondPresenceEnabled && config.hostEnabled && config.horrorIntensity > 0f
                && player.isAlive() && !player.isRemoved() && !player.isSpectator() && !player.isSleeping()
                && !player.isPassenger() && !player.isInWater() && !player.isInLava() && !player.isOnFire()
                && !player.isChangingDimension() && !player.isFallFlying() && !player.getAbilities().flying
                && player.level().getDifficulty() != Difficulty.PEACEFUL
                && !PenumbraC2S.isScreenBlocking(player) && !WarrickCassetteItem.isPlaying(player.getUUID())
                && !ConfrontationManager.isActive(player.getUUID());
    }

    private static boolean vulnerable(ServerPlayer player) {
        return player.getHealth() <= Math.max(6.0f, player.getMaxHealth() * 0.3f)
                || player.getFoodData().getFoodLevel() <= 6 || player.getAirSupply() < 100 || player.hurtTime > 0;
    }

    private static boolean valid(MinecraftServer server, Scene scene) {
        return server.getPlayerList().getPlayer(scene.owner.getUUID()) == scene.owner
                && scene.owner.level() == scene.level && eligible(scene.owner)
                && scene.owner.position().distanceToSqr(scene.origin) <= 28 * 28;
    }

    private static boolean competing(ServerPlayer player, HostEntity own) {
        return !player.level().getEntitiesOfClass(Entity.class, player.getBoundingBox().inflate(24), entity -> entity != own
                && entity.isAlive() && (entity instanceof HostEntity host && (!host.isPrivateToOwner() || host.isOwnedBy(player))
                || entity instanceof WatcherEntity watcher && watcher.isOwnedBy(player)
                || entity instanceof ListenerEntity listener && (listener.isOwnedBy(player) || PenumbraConfig.get().multiplayerContent)
                || entity instanceof RampantEntity rampant && rampant.isOwnedBy(player)
                || entity instanceof AbsentEntity absent && absent.isOwnedBy(player)
                || entity instanceof ScuttlerEntity scuttler && scuttler.isOwnedBy(player)
                || entity instanceof OriginelEntity originel && originel.isOwnedBy(player)
                || entity instanceof ContrefaitEntity contrefait && contrefait.isOwnedBy(player))).isEmpty();
    }

    public static void onPhotographed(ServerPlayer player, HostEntity actor) {
        State state = STATES.get(player.level().getServer());
        Scene scene = state == null ? null : state.scenes.get(player.getUUID());
        if (scene != null && scene.actor == actor && actor.isOwnedBy(player)) scene.session.recover();
    }

    /** Diagnostic only; never appears in ordinary player UI. */
    public static String snapshot(ServerPlayer player) {
        State state = STATES.get(player.level().getServer());
        Scene scene = state == null ? null : state.scenes.get(player.getUUID());
        if (scene == null) return "inactive";
        return String.format(Locale.ROOT, "%s, age=%dt, body=%.1f %.1f %.1f, route=%d/%d, witnessed=%s, mayAttack=%s",
                scene.session.phase().name().toLowerCase(Locale.ROOT), scene.session.age(player.level().getServer().overworld().getGameTime()),
                scene.actor.getX(), scene.actor.getY(), scene.actor.getZ(), scene.pathIndex,
                scene.plan.points().size(), scene.observationRecorded, scene.mayAttack);
    }

    public static void onPlayerDisconnect(MinecraftServer server, UUID owner) {
        State state = STATES.get(server);
        Scene scene = state == null ? null : state.scenes.remove(owner);
        if (scene != null) finish(scene, false);
    }

    public static void onServerStopped(MinecraftServer server) {
        State state = STATES.remove(server);
        if (state != null) state.scenes.values().forEach(scene -> finish(scene, false));
    }

    private static void finish(Scene scene, boolean connected) {
        boolean naturalEnd = scene.session.phase() == PresenceSession.Phase.DONE;
        if (naturalEnd && scene.actor.isAlive() && connected && scene.owner.level() == scene.level) {
            scene.level.sendParticles(scene.owner, PenumbraParticles.ASH_MOTE, false, false,
                    scene.actor.getX(), scene.actor.getY() + 1.2, scene.actor.getZ(), 16, 0.2, 0.6, 0.2, 0.005);
        }
        scene.session.cancel();
        scene.actor.discard();
        // Count the end as a perceptible beat: the director gives a proper recovery interval.
        if (connected && scene.owner.isAlive() && scene.owner.level() == scene.level) HorrorDirector.recordAutomaticScene(scene.owner);
    }

    private static void facePlayer(HostEntity actor, ServerPlayer player) {
        Vec3 delta = player.getEyePosition().subtract(actor.getEyePosition());
        float yaw = (float) (Math.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0f;
        actor.setYRot(yaw); actor.setYBodyRot(yaw); actor.setYHeadRot(yaw);
        actor.setXRot((float) (-Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)) * Mth.RAD_TO_DEG));
    }
}

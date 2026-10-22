package fr.penumbra.entity;

import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.entity.ai.ChasePlayerGoal;
import fr.penumbra.entity.ai.HiddenWatchGoal;
import fr.penumbra.entity.ai.ObservePlayerGoal;
import fr.penumbra.entity.ai.StalkPlayerGoal;
import fr.penumbra.entity.ai.VanishWhenSeenGoal;
import fr.penumbra.network.PenumbraNetworking;
import fr.penumbra.photo.PhotoEvidenceDetector;
import fr.penumbra.registry.PenumbraEntities;
import fr.penumbra.registry.PenumbraParticles;
import fr.penumbra.registry.PenumbraSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * L'Hôte — entité principale du mod Penumbra.
 * Silhouette hostile dont le comportement ({@link HostBehavior}) est piloté par
 * les goals d'IA et le directeur ; l'ordinal de l'état est synchronisé via TrackedData.
 */
public class HostEntity extends Monster {

    private static final int DEFAULT_MAX_LIFETIME_TICKS = 3600;
    private static final int OWNER_MISSING_GRACE_TICKS = 200;
    private static final int RETREAT_LIFETIME_TICKS = 100;
    private static final int COOLDOWN_LIFETIME_TICKS = 40;
    private static final int STARE_WINDUP_TICKS = 12;

    /** Ordinal du {@link HostBehavior} courant, synchronisé client/serveur. */
    private static final EntityDataAccessor<Integer> DATA_BEHAVIOR =
            SynchedEntityData.defineId(HostEntity.class, EntityDataSerializers.INT);
    /** Tick et durée du bond terminal, empaquetés pour le rendu client. */
    private static final EntityDataAccessor<Integer> DATA_LUNGE_STATE =
            SynchedEntityData.defineId(HostEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_WINDUP =
            SynchedEntityData.defineId(HostEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_RECOVERY =
            SynchedEntityData.defineId(HostEntity.class, EntityDataSerializers.INT);
    /** Zero is inactive; elapsed + 1 keeps the contact pose visible at tick zero. */
    private static final EntityDataAccessor<Integer> DATA_GRAPPLE =
            SynchedEntityData.defineId(HostEntity.class, EntityDataSerializers.INT);

    private UUID ownerUuid;
    private int lifetimeTicks;
    private int maxLifetimeTicks = DEFAULT_MAX_LIFETIME_TICKS;
    private int ownerMissingTicks;
    private int terminalTicks;
    private Vec3 lungeStartPosition;
    private Vec3 lungeTargetPosition;
    private int lungeWindupTicks;
    private Vec3 grappleStartPosition;
    private Vec3 grappleDirection;
    private Vec3 grapplePreviousAnchor;
    private boolean grappleThrown;
    private boolean autonomousStareEnabled = true;
    /**
     * Certaines scènes asymétriques montrent l'Hôte à un seul témoin. Ce
     * drapeau doit être armé avant {@code addFreshEntity}; les Hôtes ordinaires
     * restent, eux, visibles par tous les joueurs qui suivent le chunk.
     */
    private boolean privateToOwner;

    public HostEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    /**
     * Attributs de l'Hôte : HP 40, vitesse 0.23, attaque 6, followRange 48,
     * knockbackResistance 1.0, armure 2.
     */
    public static AttributeSupplier.Builder createHostAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.23)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ARMOR, 2.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_BEHAVIOR, HostBehavior.DORMANT.ordinal());
        builder.define(DATA_LUNGE_STATE, 0);
        builder.define(DATA_WINDUP, 0);
        builder.define(DATA_RECOVERY, 0);
        builder.define(DATA_GRAPPLE, 0);
    }

    public HostBehavior getBehavior() {
        return HostBehavior.byId(this.entityData.get(DATA_BEHAVIOR));
    }

    public void setBehavior(HostBehavior behavior) {
        if (behavior != this.getBehavior()) {
            this.terminalTicks = 0;
        }
        this.entityData.set(DATA_BEHAVIOR, behavior.ordinal());
    }

    /** Lie cette manifestation à un joueur précis. */
    public void setOwner(ServerPlayer owner) {
        this.setOwner(owner.getUUID());
    }

    public void setOwner(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
        this.ownerMissingTicks = 0;
    }

    public boolean hasOwner() {
        return this.ownerUuid != null;
    }

    public boolean isOwnedBy(Entity entity) {
        return this.ownerUuid != null && this.ownerUuid.equals(entity.getUUID());
    }

    /** Rend cette manifestation visible uniquement à son propriétaire. */
    public void setPrivateToOwner(boolean privateToOwner) {
        this.privateToOwner = privateToOwner;
    }

    public boolean isPrivateToOwner() {
        return this.privateToOwner;
    }

    @Override
    public boolean broadcastToPlayer(ServerPlayer player) {
        return (!this.privateToOwner || this.isOwnedBy(player))
                && super.broadcastToPlayer(player);
    }

    /** Retourne le propriétaire seulement s'il est connecté dans cette dimension. */
    public ServerPlayer getOwnerPlayer() {
        if (this.ownerUuid == null || !(this.level() instanceof ServerLevel level)) {
            return null;
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(this.ownerUuid);
        return owner != null && owner.level() == level ? owner : null;
    }

    public void setMaxLifetimeTicks(int maxLifetimeTicks) {
        this.maxLifetimeTicks = Math.max(20, maxLifetimeTicks);
    }

    public boolean isLunging() {
        return this.entityData.get(DATA_LUNGE_STATE) != 0;
    }

    public float getLungeProgress(float partialTick) {
        return StareLunge.progress(this.entityData.get(DATA_LUNGE_STATE), partialTick);
    }

    public boolean canReactToStare() {
        return this.autonomousStareEnabled && !this.isLunging() && !this.isGrappling() && !this.isRecovering()
                && HostLunge.canArmFrom(this.getBehavior());
    }

    /** A directed scene may reveal the body before explicitly releasing its charge. */
    public void setAutonomousStareEnabled(boolean enabled) {
        this.autonomousStareEnabled = enabled;
    }

    public boolean isRecovering() { return this.entityData.get(DATA_RECOVERY) > 0; }

    public boolean isGrappling() { return this.entityData.get(DATA_GRAPPLE) > 0; }

    public float getGrappleProgress(float partialTick) {
        int state = this.entityData.get(DATA_GRAPPLE);
        return state == 0 ? 0.0f : HostGrapple.progress(state - 1, partialTick);
    }

    public float getWindupProgress(float partialTick) {
        int left = this.entityData.get(DATA_WINDUP);
        return left <= 0 ? 0.0f : Mth.clamp((STARE_WINDUP_TICKS - left + partialTick)
                / STARE_WINDUP_TICKS, 0.0f, 1.0f);
    }

    public boolean isWindingUp() { return this.entityData.get(DATA_WINDUP) > 0; }

    public float getRecoveryProgress(float partialTick) {
        int elapsed = this.entityData.get(DATA_RECOVERY);
        return elapsed <= 0 ? 0.0f : Mth.clamp((elapsed - 1 + partialTick)
                / StareLunge.RECOVERY_TICKS, 0.0f, 1.0f);
    }

    /**
     * Remplace l'ancienne disparition sous le regard : l'Hôte se fige, puis
     * parcourt la distance jusqu'au visage avant de frapper et hurler.
     */
    public boolean beginStareLunge(ServerPlayer watcher) {
        if (this.isLunging() || this.isGrappling() || this.isRecovering() || !HostLunge.canArmFrom(this.getBehavior())
                || !PenumbraConfig.get().hostEnabled
                || watcher == null || !watcher.isAlive() || watcher.isRemoved()
                || watcher.isSpectator() || watcher.isSleeping()
                || watcher.isPassenger() || watcher.getAbilities().flying || watcher.isFallFlying()
                || watcher.level() != this.level() || !this.isOwnedBy(watcher)
                || !this.hasLineOfSight(watcher)) {
            return false;
        }
        int duration = StareLunge.MIN_DURATION_TICKS
                + this.random.nextInt(StareLunge.MAX_DURATION_TICKS
                - StareLunge.MIN_DURATION_TICKS + 1);
        this.lungeStartPosition = this.position();
        Vec3 targetEye = watcher.getEyePosition();
        Vec3 fromFace = this.getEyePosition().subtract(targetEye);
        if (fromFace.lengthSqr() < 1.0E-6) {
            fromFace = watcher.getLookAngle();
        }
        // Commit to the observed location. A lateral dodge must not be homed onto.
        Vec3 endEye = targetEye.add(fromFace.normalize().scale(0.68));
        this.lungeTargetPosition = new Vec3(endEye.x, watcher.getY(), endEye.z);
        this.lungeWindupTicks = STARE_WINDUP_TICKS;
        this.entityData.set(DATA_WINDUP, STARE_WINDUP_TICKS);
        this.getNavigation().stop();
        this.setTarget(null);
        this.setDeltaMovement(Vec3.ZERO);
        this.setNoAi(true);
        this.setNoGravity(true);
        this.setInvulnerable(true);
        this.noPhysics = false;
        this.entityData.set(DATA_LUNGE_STATE, StareLunge.pack(0, duration));
        PenumbraNetworking.sendSceneHush(watcher, STARE_WINDUP_TICKS + duration);
        this.playContactSound(watcher, PenumbraSounds.HOST_WHISPER, 0.42f, 0.8f);
        return true;
    }

    @Override
    protected void registerGoals() {
        // Phase 2 : ChasePlayerGoal en priorité 0, AVANT VanishWhenSeenGoal (SPEC2 §7) —
        // pendant CHASING/ATTACKING il détient MOVE|LOOK, ce qui bloque Vanish et
        // MeleeAttackGoal ; la seule attaque de mêlée passe par son télégraphe de 20t.
        this.goalSelector.addGoal(0, new ChasePlayerGoal(this));
        this.goalSelector.addGoal(0, new VanishWhenSeenGoal(this));
        this.goalSelector.addGoal(1, new ObservePlayerGoal(this));
        this.goalSelector.addGoal(2, new StalkPlayerGoal(this));
        // Phase 2 : guet dissimulé en HIDING (SPEC2 §7).
        this.goalSelector.addGoal(4, new HiddenWatchGoal(this));
        this.goalSelector.addGoal(6, new ControlledStrollGoal(this));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        // Ciblage uniquement en CHASING/ATTACKING et si les chasses sont activées.
        // 26.1 : le sélecteur est un TargetingConditions.Selector (test(LivingEntity, ServerLevel)).
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                (target, level) ->
                        (this.getBehavior() == HostBehavior.CHASING || this.getBehavior() == HostBehavior.ATTACKING)
                                && PenumbraConfig.get().chasesEnabled
                                && this.isOwnedBy(target)));
    }

    /**
     * Utilitaire Phase 2 (SPEC2 §7) : vrai si au moins un Hôte se trouve dans le
     * rayon donné autour de {@code pos}. Employé par les événements et le directeur
     * pour éviter les Hôtes doublons (ex. {@code binoculars_sighting} : jamais si
     * un Hôte existe déjà à moins de 100 blocs).
     */
    public static boolean hasAnyHostNearby(ServerLevel level, BlockPos pos, double radius) {
        return !level.getEntitiesOfClass(HostEntity.class, new AABB(pos).inflate(radius)).isEmpty();
    }

    /**
     * Variante multijoueur : seuls les Hôtes de ce joueur (et les anciennes
     * entités sans propriétaire) peuvent bloquer sa prochaine apparition.
     */
    public static boolean hasAnyHostNearby(ServerLevel level, BlockPos pos, double radius,
                                           ServerPlayer owner) {
        return !level.getEntitiesOfClass(HostEntity.class, new AABB(pos).inflate(radius),
                host -> host.isAlive() && host.isOwnedBy(owner)).isEmpty();
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide()) {
            // A killed body can remain for vanilla's death animation. It must
            // never finish a queued charge or apply contact during those ticks.
            if (!this.isAlive() || this.isRemoved()) {
                this.discard();
                return;
            }
            if ((this.isLunging() || this.isGrappling() || this.isRecovering())
                    && this.level() instanceof ServerLevel level) {
                ServerPlayer owner = this.getOwnerPlayer();
                if (owner == null || !owner.isAlive() || owner.isRemoved()
                        || owner.isSpectator() || owner.isSleeping() || owner.isChangingDimension()
                        || !PenumbraConfig.get().hostEnabled) {
                    this.discard();
                } else {
                    if (this.isRecovering()) this.tickRecovery(level, owner);
                    else if (this.isGrappling()) this.tickGrapple(level, owner);
                    else this.tickStareLunge(level, owner);
                }
                return;
            }
            if (++this.lifetimeTicks > this.maxLifetimeTicks) {
                this.discard();
                return;
            }

            ServerPlayer owner = this.getOwnerPlayer();
            if (!PenumbraConfig.get().hostEnabled || (owner != null
                    && (!owner.isAlive() || owner.isRemoved() || owner.isSpectator()
                    || owner.isSleeping()))) {
                this.getNavigation().stop();
                this.setTarget(null);
                this.discard();
                return;
            }

            if (this.ownerUuid != null && this.getOwnerPlayer() == null) {
                this.getNavigation().stop();
                this.setTarget(null);
                if (++this.ownerMissingTicks > OWNER_MISSING_GRACE_TICKS) {
                    this.discard();
                    return;
                }
            } else {
                this.ownerMissingTicks = 0;
            }

            HostBehavior behavior = this.getBehavior();
            if (behavior == HostBehavior.RETREATING || behavior == HostBehavior.COOLDOWN) {
                int limit = behavior == HostBehavior.RETREATING
                        ? RETREAT_LIFETIME_TICKS : COOLDOWN_LIFETIME_TICKS;
                if (++this.terminalTicks >= limit) {
                    this.discard();
                    return;
                }
            } else {
                this.terminalTicks = 0;
            }
        }
        // Filet de sécurité : toute cible résiduelle est effacée hors CHASING/ATTACKING.
        if (!this.level().isClientSide() && this.getTarget() != null
                && this.getBehavior() != HostBehavior.CHASING
                && this.getBehavior() != HostBehavior.ATTACKING) {
            this.setTarget(null);
        }
    }

    private void tickStareLunge(ServerLevel level, ServerPlayer owner) {
        if (!this.hasLineOfSight(owner) || this.lungeStartPosition == null
                || this.lungeTargetPosition == null) {
            this.beginRecovery(owner);
            return;
        }
        if (this.lungeWindupTicks > 0) {
            this.lungeWindupTicks--;
            this.entityData.set(DATA_WINDUP, this.lungeWindupTicks);
            this.setDeltaMovement(Vec3.ZERO);
            this.faceLungeTarget(owner);
            return;
        }
        int state = this.entityData.get(DATA_LUNGE_STATE);
        int duration = StareLunge.durationTicks(state);
        int elapsed = Math.min(duration, StareLunge.elapsedTicks(state) + 1);
        this.entityData.set(DATA_LUNGE_STATE, StareLunge.pack(elapsed, duration));

        double eased = StareLunge.easedProgress(elapsed, duration);
        Vec3 next = this.lungeStartPosition.lerp(this.lungeTargetPosition, eased)
                .add(0.0, HostGrapple.leapHeight(elapsed / (double) duration), 0.0);
        AABB swept = this.getBoundingBox().expandTowards(next.subtract(this.position()));
        if (!level.hasChunksAt(Mth.floor(swept.minX), Mth.floor(swept.minZ),
                Mth.floor(swept.maxX), Mth.floor(swept.maxZ)) || !level.noCollision(this, swept)) {
            this.beginRecovery(owner);
            return;
        }
        this.setPos(next);
        this.setDeltaMovement(Vec3.ZERO);
        this.faceLungeTarget(owner);

        if (elapsed >= duration) {
            if (!owner.isPassenger() && !owner.getAbilities().flying && !owner.isFallFlying()
                    && HostGrapple.canGrab(this.position(), owner.position(),
                    this.hasLineOfSight(owner))) this.beginGrapple(owner);
            else this.beginRecovery(owner);
        }
    }

    private void beginGrapple(ServerPlayer owner) {
        // The director can allow two manifestations. They must never pull the
        // same player towards competing anchors on alternating server ticks.
        if (this.level() instanceof ServerLevel level && !level.getEntitiesOfClass(HostEntity.class,
                owner.getBoundingBox().inflate(4.0), other -> other != this && other.isAlive()
                        && !other.isRemoved() && other.isOwnedBy(owner) && other.isGrappling()).isEmpty()) {
            this.beginRecovery(owner);
            return;
        }
        this.grappleStartPosition = owner.position();
        this.grapplePreviousAnchor = owner.position();
        this.grappleDirection = HostGrapple.direction(this.position(), owner.position(), this.getLookAngle());
        this.grappleThrown = false;
        this.entityData.set(DATA_LUNGE_STATE, 0);
        this.entityData.set(DATA_WINDUP, 0);
        this.entityData.set(DATA_GRAPPLE, 1);
        this.setDeltaMovement(Vec3.ZERO);
        if (PenumbraConfig.get().jumpscareFrequency > 0.0f) {
            this.playContactSound(owner, PenumbraConfig.get().screamersEnabled
                    ? PenumbraSounds.SCREAMER_SHRIEK : PenumbraSounds.HOST_STINGER, 1.0f, 0.94f);
        }
    }

    private void tickGrapple(ServerLevel level, ServerPlayer owner) {
        int elapsed = this.entityData.get(DATA_GRAPPLE) - 1;
        this.setDeltaMovement(Vec3.ZERO);
        this.faceLungeTarget(owner);
        if (elapsed >= HostGrapple.TOTAL_TICKS || this.grappleStartPosition == null
                || this.grappleDirection == null || owner.isPassenger()
                || owner.getAbilities().flying || owner.isFallFlying()) {
            this.beginRecovery(owner);
            return;
        }
        int nextTick = elapsed + 1;
        if (!this.grappleThrown) {
            if (!this.hasLineOfSight(owner)
                    || !HostGrapple.canContinueHolding(owner.position(), this.grapplePreviousAnchor)) {
                this.beginRecovery(owner);
                return;
            }
            if (nextTick < HostGrapple.THROW_START_TICK) {
                Vec3 next = HostGrapple.heldPosition(this.grappleStartPosition,
                        this.position(), this.grappleDirection, nextTick);
                if (!this.canMoveBody(level, owner, next.subtract(owner.position()))) {
                    this.beginRecovery(owner);
                    return;
                }
                // Vanilla position packet acknowledges each short move. Relative zero
                // rotation leaves the local camera free, including during network latency.
                // Never alter gravity, ability flags, riding state or movement attributes.
                owner.connection.teleport(new PositionMoveRotation(next, Vec3.ZERO, 0.0f, 0.0f),
                        Relative.ROTATION);
                owner.resetFallDistance();
                this.grapplePreviousAnchor = next;
            } else {
                this.grappleThrown = true;
                // Deal the existing attack once, then override vanilla knockback with
                // a bounded push. Subsequent motion uses vanilla block collisions.
                this.doHurtTarget(level, owner);
                Vec3 push = HostGrapple.throwVelocity(this.grappleDirection);
                if (!this.canMoveBody(level, owner, push)) push = Vec3.ZERO;
                owner.setDeltaMovement(push);
                owner.hurtMarked = true; // ServerEntity sends velocity to owner AND observers.
                if (PenumbraConfig.get().jumpscareFrequency > 0.0f) {
                    PenumbraNetworking.sendJumpscare(owner, "impact", 1.0f);
                }
            }
        }
        this.entityData.set(DATA_GRAPPLE, nextTick + 1);
    }

    /** All swept player/head space must be loaded and empty before moving the grab. */
    private boolean canMoveBody(ServerLevel level, Entity body, Vec3 movement) {
        AABB swept = body.getBoundingBox().expandTowards(movement);
        return level.hasChunksAt(Mth.floor(swept.minX), Mth.floor(swept.minZ),
                Mth.floor(swept.maxX), Mth.floor(swept.maxZ)) && level.noCollision(body, swept);
    }

    /** The camera ends this manifestation's attack; no second attempt after recoil. */
    public boolean repelByCamera(ServerPlayer photographer) {
        if (photographer == null || !this.isAlive() || this.isRecovering()
                || !this.isOwnedBy(photographer) || this.level() != photographer.level()
                || !PhotoEvidenceDetector.canFlashReach(photographer, this)) return false;
        this.beginRecovery(photographer);
        return true;
    }

    private void beginRecovery(ServerPlayer owner) {
        this.entityData.set(DATA_LUNGE_STATE, 0);
        this.entityData.set(DATA_WINDUP, 0);
        this.entityData.set(DATA_GRAPPLE, 0);
        this.entityData.set(DATA_RECOVERY, 1);
        this.grappleStartPosition = null;
        this.grappleDirection = null;
        this.grapplePreviousAnchor = null;
        this.lungeWindupTicks = 0;
        this.getNavigation().stop();
        this.setTarget(null);
        this.setNoAi(true);
        this.setNoGravity(false);
        this.noPhysics = false;
        this.setInvulnerable(true);
        this.setDeltaMovement(Vec3.ZERO);
        this.setBehavior(HostBehavior.RETREATING);
        this.faceLungeTarget(owner);
    }

    private void tickRecovery(ServerLevel level, ServerPlayer owner) {
        int elapsed = this.entityData.get(DATA_RECOVERY);
        this.faceLungeTarget(owner);
        Vec3 step = StareLunge.retreatStep(this.position(), owner.position(), owner.getLookAngle(), elapsed);
        Vec3 next = this.position().add(step);
        AABB swept = this.getBoundingBox().expandTowards(step);
        BlockPos ground = BlockPos.containing(next).below();
        if (level.hasChunksAt(Mth.floor(swept.minX), Mth.floor(swept.minZ),
                Mth.floor(swept.maxX), Mth.floor(swept.maxZ)) && level.getBlockState(ground).isSolidRender()
                && level.noCollision(this, swept) && level.getFluidState(BlockPos.containing(next)).isEmpty()) {
            this.setPos(next);
        }
        this.setDeltaMovement(Vec3.ZERO);
        if (elapsed >= StareLunge.RECOVERY_TICKS) {
            if (this.privateToOwner) {
                level.sendParticles(owner, PenumbraParticles.ASH_MOTE, false, false,
                        this.getX(), this.getY() + 1.3, this.getZ(), 24, 0.35, 0.8, 0.35, 0.012);
            } else {
                level.sendParticles(PenumbraParticles.ASH_MOTE,
                        this.getX(), this.getY() + 1.3, this.getZ(), 24, 0.35, 0.8, 0.35, 0.012);
            }
            this.discard();
        } else this.entityData.set(DATA_RECOVERY, elapsed + 1);
    }

    private void playContactSound(ServerPlayer owner, net.minecraft.sounds.SoundEvent sound,
                                  float volume, float pitch) {
        if (this.privateToOwner) PenumbraNetworking.sendSoundDirectPriority(owner, sound, this.getEyePosition(), volume, pitch);
        else PenumbraNetworking.sendSoundPrioritySpatial(owner, sound, this.getEyePosition(), volume, pitch);
    }

    private void faceLungeTarget(ServerPlayer owner) {
        Vec3 delta = owner.getEyePosition().subtract(this.getEyePosition());
        float yaw = (float) (Math.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0f;
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float pitch = (float) (-Math.atan2(delta.y, horizontal) * Mth.RAD_TO_DEG);
        this.setYRot(yaw);
        this.setYBodyRot(yaw);
        this.setYHeadRot(yaw);
        this.setXRot(pitch);
    }

    /** Une charge de quelques ticks ne doit jamais être sauvegardée à mi-trajectoire. */
    @Override
    public boolean shouldBeSaved() {
        return !this.privateToOwner && !this.isLunging() && !this.isGrappling()
                && !this.isRecovering() && super.shouldBeSaved();
    }

    /**
     * Neutralisé volontairement : la durée de vie de l'Hôte est gérée par les goals
     * (vanish, discard) et le directeur, jamais par le despawn vanilla.
     */
    @Override
    public void checkDespawn() {
        // Le TTL déterministe de tick() remplace le despawn vanilla.
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setBehavior(HostBehavior.byId(input.getIntOr(
                "PenumbraBehavior", HostBehavior.DORMANT.ordinal())));
        this.lifetimeTicks = Math.max(0, input.getIntOr("PenumbraLifetime", 0));
        this.maxLifetimeTicks = Math.max(20,
                input.getIntOr("PenumbraMaxLifetime", DEFAULT_MAX_LIFETIME_TICKS));
        String owner = input.getStringOr("PenumbraOwner", "");
        if (!owner.isBlank()) {
            try {
                this.ownerUuid = UUID.fromString(owner);
            } catch (IllegalArgumentException ignored) {
                this.ownerUuid = null;
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("PenumbraBehavior", this.getBehavior().ordinal());
        output.putInt("PenumbraLifetime", this.lifetimeTicks);
        output.putInt("PenumbraMaxLifetime", this.maxLifetimeTicks);
        if (this.ownerUuid != null) {
            output.putString("PenumbraOwner", this.ownerUuid.toString());
        }
    }

    /**
     * Fait apparaître un Hôte près de la cible, hors de son champ de vision.
     * 24 essais : angle biaisé derrière la cible, distance minDist..maxDist,
     * lumière &lt;= 4, sol solide + 2 blocs d'air, aucun liquide.
     *
     * @return l'Hôte invoqué, ou null si aucun emplacement correct n'a été trouvé
     *         (jamais d'apparition injuste en pleine vue).
     */
    public static HostEntity spawnNear(ServerLevel level, ServerPlayer target, double minDist, double maxDist) {
        RandomSource random = level.getRandom();
        Vec3 look = target.getLookAngle().normalize();
        double lookAngle = Math.atan2(look.z, look.x);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int attempt = 0; attempt < 24; attempt++) {
            // Angle biaisé dans le dos de la cible (±135° autour de l'arrière).
            double angle = lookAngle + Math.PI + (random.nextDouble() - 0.5) * (Math.PI * 1.5);
            double dist = minDist + random.nextDouble() * Math.max(1.0, maxDist - minDist);
            double dx = Math.cos(angle) * dist;
            double dz = Math.sin(angle) * dist;

            // Sécurité : on refuse tout emplacement encore dans le champ de vision (dot > 0.2).
            Vec3 flatLook = new Vec3(look.x, 0.0, look.z);
            if (flatLook.lengthSqr() > 1.0E-4) {
                double inv = 1.0 / dist;
                if (flatLook.normalize().dot(new Vec3(dx * inv, 0.0, dz * inv)) > 0.2) {
                    continue;
                }
            }

            int baseX = Mth.floor(target.getX() + dx);
            int baseZ = Mth.floor(target.getZ() + dz);
            int baseY = Mth.floor(target.getY());

            for (int dy = 4; dy >= -16; dy--) {
                cursor.set(baseX, baseY + dy, baseZ);
                if (!isValidSpawnSpot(level, cursor)) {
                    continue;
                }

                HostEntity host = new HostEntity(PenumbraEntities.HOST, level);
                host.setOwner(target);
                // 26.1 : Entity.moveTo(...) s'appelle snapTo(...) (vérifié dans les sources 26.1.2).
                host.snapTo(baseX + 0.5, cursor.getY(), baseZ + 0.5, random.nextFloat() * 360.0f, 0.0f);
                host.finalizeSpawn(level, level.getCurrentDifficultyAt(cursor), EntitySpawnReason.EVENT, null);
                if (level.addFreshEntity(host)) {
                    return host;
                }
                return null;
            }
        }
        return null;
    }

    /**
     * Emplacement d'apparition valide : chunk chargé, lumière &lt;= 4, sol solide,
     * 2 blocs d'air au-dessus, aucun liquide.
     */
    private static boolean isValidSpawnSpot(ServerLevel level, BlockPos feet) {
        if (!level.isLoaded(feet)) {
            return false;
        }
        if (level.getMaxLocalRawBrightness(feet) > 4) {
            return false;
        }
        BlockPos ground = feet.below();
        BlockPos head = feet.above();
        // 26.1 : isSolidRender() sans arguments (vérifié dans les sources 26.1.2).
        if (!level.getBlockState(ground).isSolidRender()) {
            return false;
        }
        if (!level.getBlockState(feet).isAir() || !level.getBlockState(head).isAir()) {
            return false;
        }
        return level.getFluidState(ground).isEmpty()
                && level.getFluidState(feet).isEmpty()
                && level.getFluidState(head).isEmpty();
    }

    /** Empêche la flânerie vanilla de déplacer les manifestations immobiles. */
    private static final class ControlledStrollGoal extends WaterAvoidingRandomStrollGoal {

        private final HostEntity host;

        private ControlledStrollGoal(HostEntity host) {
            super(host, 0.6);
            this.host = host;
        }

        private boolean canStrollForBehavior() {
            HostBehavior behavior = this.host.getBehavior();
            return behavior == HostBehavior.INVESTIGATING || behavior == HostBehavior.LURING;
        }

        @Override
        public boolean canUse() {
            return canStrollForBehavior() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return canStrollForBehavior() && super.canContinueToUse();
        }
    }
}

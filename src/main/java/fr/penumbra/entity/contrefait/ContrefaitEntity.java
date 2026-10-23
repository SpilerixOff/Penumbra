package fr.penumbra.entity.contrefait;

import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.network.PenumbraNetworking;
import fr.penumbra.registry.PenumbraSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.UUID;

/**
 * Le Contrefait (0.6.0) — la quatrième présence de Penumbra : une copie RATÉE
 * de la légende. Silhouette de joueur aux yeux blancs — presque le mythe,
 * mais tout est légèrement faux : un œil déplacé, un bras de la mauvaise
 * couleur, une démarche qui ne se résout pas. Il ne construit pas bien non
 * plus : ses totems n'ont pas de feu, ses tunnels font la mauvaise taille
 * ({@link ContrefaitManager}).
 * <p>
 * Comportement — il n'avance QUE quand personne ne le regarde :
 * <ul>
 *   <li>observé (à l'écran d'un joueur, ligne de vue dégagée) : il se FIGE,
 *       en pleine enjambée s'il le faut, et fixe le joueur ;</li>
 *   <li>non observé : il marche lentement vers le joueur le plus proche ;</li>
 *   <li>à moins de {@value #VANISH_DISTANCE} blocs, à l'instant où plus
 *       personne ne le regarde : il n'est plus là — murmure au joueur le plus
 *       proche, et 40 % de chance qu'un totem raté se dresse là où il se
 *       tenait ({@code worldModificationEnabled}) ;</li>
 *   <li>si son propriétaire soutient précisément le regard, bond 3D, frappe,
 *       screamer puis disparition ; hors de cette scène il reste invulnérable ;</li>
 *   <li>durée de vie bornée à {@value #MAX_LIFETIME_TICKS} ticks.</li>
 * </ul>
 * Pattern technique : goals d'IA (comme l'Hôte) — un unique goal de traque
 * {@link ApproachWhenUnseenGoal} ; l'observation est évaluée toutes les
 * {@value #OBSERVED_CHECK_INTERVAL} ticks (LOS par {@code Level.clip},
 * pattern vérifié de {@code WatcherEntity.isStaredAtBy}).
 * </p>
 */
public class ContrefaitEntity extends Monster {

    /** Tick courant et durée de la charge, synchronisés pour la pose client. */
    private static final EntityDataAccessor<Integer> DATA_LUNGE_STATE =
            SynchedEntityData.defineId(ContrefaitEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_WINDUP =
            SynchedEntityData.defineId(ContrefaitEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_RECOVERY =
            SynchedEntityData.defineId(ContrefaitEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_RECOVERY_POSE =
            SynchedEntityData.defineId(ContrefaitEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_OBSERVED =
            SynchedEntityData.defineId(ContrefaitEntity.class, EntityDataSerializers.BOOLEAN);

    /** Distance sous laquelle la disparition s'arme. */
    public static final double VANISH_DISTANCE = 10.0;
    /** Portée de détection des joueurs. */
    private static final double TRACK_RADIUS = 64.0;
    /** Durée de vie maximale (2 min). */
    private static final int MAX_LIFETIME_TICKS = 2400;
    /** Cadence de l'évaluation d'observation (ticks). */
    private static final int OBSERVED_CHECK_INTERVAL = 2;
    /** Cône d'observation : dot(view, versEntité) au-delà duquel il est « à l'écran ». */
    private static final double OBSERVED_DOT_THRESHOLD = 0.5;
    /** Regard volontaire précis : cône de quatre degrés, comme l'Observateur. */
    private static final double STARE_DOT_THRESHOLD = Math.cos(Math.toRadians(4.0));
    /** Vitesse de marche du goal (modificateur de navigation). */
    private static final double APPROACH_SPEED = 0.55;
    /** Chance de laisser un totem raté en disparaissant au contact. */
    private static final float PARTING_TOTEM_CHANCE = 0.4f;

    private int lifetimeTicks;
    private boolean observed;
    private UUID ownerUuid;
    private int stareTicks;
    private Vec3 lungeStartPosition;
    private Vec3 lungeTargetPosition;
    private Vec3 lungeDirection = Vec3.ZERO;

    public ContrefaitEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.setInvulnerable(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_LUNGE_STATE, 0);
        builder.define(DATA_WINDUP, 0);
        builder.define(DATA_RECOVERY, 0);
        builder.define(DATA_RECOVERY_POSE, 0.0f);
        builder.define(DATA_OBSERVED, false);
    }

    /** Attributs : HP 20, vitesse 0.25 (lente, délibérée), followRange 64, knockback ignoré. */
    public static AttributeSupplier.Builder createContrefaitAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.FOLLOW_RANGE, TRACK_RADIUS)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    /** Un unique goal : approcher quand personne ne regarde. Aucun goal vanilla. */
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new ApproachWhenUnseenGoal(this));
    }

    /** Désarmé : fin de vie gérée par {@link #tick()} exclusivement. */
    @Override
    public void checkDespawn() {
        // no-op intentionnel
    }

    /** Manifestation personnelle et temporaire : aucune reprise après redémarrage. */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    /** Knockback ignoré : rien ne le déplace, à part lui-même. */
    @Override
    public void knockback(double strength, double x, double z) {
        // no-op intentionnel
    }

    /** Ne peut pas être poussé. */
    @Override
    public boolean isPushable() {
        return false;
    }

    /** Vrai si un joueur a l'entité « à l'écran » (évalué 1/2 ticks, lu par le goal). */
    public boolean isObserved() {
        return this.entityData.get(DATA_OBSERVED);
    }

    /** Vrai des deux côtés pendant la charge terminale. */
    public boolean isLunging() {
        return this.entityData.get(DATA_LUNGE_STATE) != 0;
    }

    /** Progression interpolée 0–1 utilisée uniquement par le renderer. */
    public float getLungeProgress(float partialTick) {
        return this.isWindingUp() ? 0.0f
                : ContrefaitLunge.progress(this.entityData.get(DATA_LUNGE_STATE), partialTick);
    }

    public boolean isWindingUp() { return this.entityData.get(DATA_WINDUP) > 0; }
    public boolean isRecovering() { return this.entityData.get(DATA_RECOVERY) > 0; }
    public float getWindupProgress(float partialTick) {
        return Mth.clamp((this.entityData.get(DATA_WINDUP) - 1
                + Mth.clamp(partialTick, 0.0f, 1.0f)) / ContrefaitLunge.WINDUP_TICKS, 0.0f, 1.0f);
    }
    public float getRecoveryProgress(float partialTick) {
        return Mth.clamp((this.entityData.get(DATA_RECOVERY) - 1
                + Mth.clamp(partialTick, 0.0f, 1.0f)) / ContrefaitLunge.RECOVERY_TICKS, 0.0f, 1.0f);
    }
    public float getRecoveryPose() { return this.entityData.get(DATA_RECOVERY_POSE); }

    public void setOwner(ServerPlayer owner) {
        this.ownerUuid = owner.getUUID();
    }

    public boolean isOwnedBy(ServerPlayer player) {
        return this.ownerUuid != null && this.ownerUuid.equals(player.getUUID());
    }

    /** Une manifestation personnelle n'est envoyée qu'à son propriétaire. */
    @Override
    public boolean broadcastToPlayer(ServerPlayer player) {
        return this.isOwnedBy(player) && super.broadcastToPlayer(player);
    }

    private ServerPlayer ownerPlayer(ServerLevel level) {
        if (this.ownerUuid == null) {
            return null;
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(this.ownerUuid);
        return owner != null && owner.level() == level ? owner : null;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.lifetimeTicks = Math.max(0, input.getIntOr("PenumbraLifetime", 0));
        this.stareTicks = Math.max(0, input.getIntOr("PenumbraStareTicks", 0));
        // A transient charge cannot resume without its committed world-space path.
        this.entityData.set(DATA_LUNGE_STATE, 0);
        this.entityData.set(DATA_WINDUP, 0);
        this.entityData.set(DATA_RECOVERY, 0);
        this.setNoGravity(false);
        this.noPhysics = false;
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
        output.putInt("PenumbraLifetime", this.lifetimeTicks);
        output.putInt("PenumbraStareTicks", this.stareTicks);
        if (this.ownerUuid != null) {
            output.putString("PenumbraOwner", this.ownerUuid.toString());
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        if (!this.isAlive() || this.isRemoved()) {
            this.entityData.set(DATA_LUNGE_STATE, 0);
            this.entityData.set(DATA_WINDUP, 0);
            this.entityData.set(DATA_RECOVERY, 0);
            this.setNoGravity(false);
            this.noPhysics = false;
            this.setDeltaMovement(Vec3.ZERO);
            if (!this.isRemoved()) this.discard();
            return;
        }
        PenumbraConfig config = PenumbraConfig.get();
        if (!config.contrefaitEnabled || level.getDifficulty() == Difficulty.PEACEFUL) {
            this.discard(); // silencieux : la légende ne meurt pas, elle s'absente
            return;
        }

        ServerPlayer owner = ownerPlayer(level);
        if (owner == null || owner.isSpectator() || !owner.isAlive()
                || owner.isRemoved() || owner.isSleeping()) {
            this.discard();
            return;
        }

        // La charge est terminale : proximité, navigation et timeout ne doivent
        // jamais déclencher l'ancienne disparition avant le screamer.
        if (this.isRecovering()) {
            this.tickRecovery(level);
            return;
        }
        if (this.isLunging()) {
            this.tickLunge(level, owner);
            return;
        }

        if (++this.lifetimeTicks > MAX_LIFETIME_TICKS) {
            this.discard();
            return;
        }
        if (this.tickCount % OBSERVED_CHECK_INTERVAL == 0) {
            this.observed = computeObserved(level);
            this.entityData.set(DATA_OBSERVED, this.observed);
            this.stareTicks = ContrefaitLunge.nextStareTicks(this.stareTicks,
                    this.isPreciselyStaredAtBy(level, owner), OBSERVED_CHECK_INTERVAL);
            if (this.stareTicks >= ContrefaitLunge.stareThresholdTicks(config.watcherStareSeconds)) {
                this.startLunge(owner);
                return;
            }
        }
        if (!this.observed && this.distanceToSqr(owner) < VANISH_DISTANCE * VANISH_DISTANCE) {
            this.partingVanish(level, owner);
        }
    }

    /** Eight ticks of visible tension precede a committed, collision-checked charge. */
    private void startLunge(ServerPlayer owner) {
        int duration = ContrefaitLunge.MIN_DURATION_TICKS
                + this.random.nextInt(ContrefaitLunge.MAX_DURATION_TICKS
                - ContrefaitLunge.MIN_DURATION_TICKS + 1);
        this.lungeStartPosition = this.position();
        this.lungeTargetPosition = ContrefaitLunge.committedTarget(this.position(), owner.position());
        this.lungeDirection = this.lungeTargetPosition.subtract(this.position())
                .multiply(1.0, 0.0, 1.0).normalize();
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        this.setNoGravity(true);
        this.noPhysics = false;
        this.setNoAi(true);
        this.entityData.set(DATA_WINDUP, 1);
        this.entityData.set(DATA_LUNGE_STATE, ContrefaitLunge.pack(0, duration));
        this.faceCommittedDirection();
    }

    /**
     * The whole body sweeps along the captured route. Only physical contact
     * causes the single hit; a dodge or an obstacle leaves a visible recovery.
     */
    private void tickLunge(ServerLevel level, ServerPlayer owner) {
        if (!this.hasLineOfSight(owner) || this.lungeStartPosition == null
                || this.lungeTargetPosition == null) {
            this.beginRecovery();
            return;
        }
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        this.setNoGravity(true);
        this.noPhysics = false;
        this.faceCommittedDirection();

        int windup = this.entityData.get(DATA_WINDUP);
        if (windup > 0) {
            if (windup >= ContrefaitLunge.WINDUP_TICKS) this.entityData.set(DATA_WINDUP, 0);
            else this.entityData.set(DATA_WINDUP, windup + 1);
            return;
        }

        int state = this.entityData.get(DATA_LUNGE_STATE);
        int duration = ContrefaitLunge.durationTicks(state);
        int elapsed = Math.min(duration, ContrefaitLunge.elapsedTicks(state) + 1);
        this.entityData.set(DATA_LUNGE_STATE, ContrefaitLunge.pack(elapsed, duration));

        Vec3 next = ContrefaitLunge.position(this.lungeStartPosition,
                this.lungeTargetPosition, elapsed, duration);
        Vec3 travel = next.subtract(this.position());
        int steps = ContrefaitLunge.collisionSteps(travel.length());
        Vec3 step = travel.scale(1.0 / steps);
        for (int i = 0; i < steps; i++) {
            if (!this.canOccupySweep(level, this.getBoundingBox().expandTowards(step))) {
                this.beginRecovery();
                return;
            }
            this.setPos(this.position().add(step));
            if (ContrefaitLunge.canHit(this.getBoundingBox(), owner.getBoundingBox(),
                    this.hasLineOfSight(owner))) {
                this.doHurtTarget(level, owner);
                String type = PenumbraConfig.get().screamersEnabled
                        ? "lunge_screamer" : "apparition";
                PenumbraNetworking.sendJumpscare(owner, type, 1.0f);
                this.beginRecovery();
                return;
            }
        }
        if (elapsed >= duration) this.beginRecovery();
    }

    private boolean canOccupySweep(ServerLevel level, AABB swept) {
        return level.hasChunksAt(Mth.floor(swept.minX), Mth.floor(swept.minZ),
                Mth.floor(swept.maxX), Mth.floor(swept.maxZ))
                && level.getWorldBorder().isWithinBounds(swept)
                && level.noCollision(this, swept);
    }

    private void faceCommittedDirection() {
        float yaw = (float) (Mth.atan2(this.lungeDirection.z, this.lungeDirection.x)
                * Mth.RAD_TO_DEG) - 90.0f;
        this.setYRot(yaw);
        this.setYBodyRot(yaw);
        this.setYHeadRot(yaw);
        this.setXRot(0.0f);
    }

    private void beginRecovery() {
        this.entityData.set(DATA_RECOVERY_POSE, this.isWindingUp()
                ? -this.getWindupProgress(1.0f) : this.getLungeProgress(0.0f));
        this.entityData.set(DATA_LUNGE_STATE, 0);
        this.entityData.set(DATA_WINDUP, 0);
        this.entityData.set(DATA_RECOVERY, 1);
        this.setDeltaMovement(Vec3.ZERO);
        this.setNoGravity(false);
        this.noPhysics = false;
        this.getNavigation().stop();
    }

    private void tickRecovery(ServerLevel level) {
        int elapsed = this.entityData.get(DATA_RECOVERY);
        this.setDeltaMovement(Vec3.ZERO);
        if (elapsed > 4) {
            Vec3 step = this.lungeDirection.scale(-0.065);
            Vec3 next = this.position().add(step);
            if (this.canOccupySweep(level, this.getBoundingBox().expandTowards(step))
                    && level.getBlockState(BlockPos.containing(next).below()).isSolidRender()
                    && level.getFluidState(BlockPos.containing(next)).isEmpty()) this.setPos(next);
        }
        if (elapsed >= ContrefaitLunge.RECOVERY_TICKS) this.discard();
        else this.entityData.set(DATA_RECOVERY, elapsed + 1);
    }

    /** Disparition au contact : murmure, totem raté éventuel, discard. */
    private void partingVanish(ServerLevel level, ServerPlayer witness) {
        PenumbraNetworking.sendSound(witness, PenumbraSounds.whisperVariant(this.random),
                this.position(), 0.7f, 0.6f);
        PenumbraConfig config = PenumbraConfig.get();
        if (config.worldModificationEnabled && this.random.nextFloat() < PARTING_TOTEM_CHANCE) {
            BlockPos feet = this.blockPosition();
            if (level.getBlockState(feet).isAir()
                    && level.getBlockState(feet.above()).isAir()
                    && level.getBlockState(feet.above(2)).isAir()
                    && level.getBlockState(feet.above(3)).isAir()) {
                ContrefaitManager.buildFailedTotem(level, feet, this.random);
            }
        }
        this.discard();
    }

    /** Joueur non-spectateur le plus proche à ≤ {@value #TRACK_RADIUS} blocs, ou null. */
    ServerPlayer nearestPlayer(ServerLevel level) {
        ServerPlayer owner = ownerPlayer(level);
        if (owner != null) {
            return this.distanceToSqr(owner) <= TRACK_RADIUS * TRACK_RADIUS ? owner : null;
        }
        if (this.ownerUuid != null) {
            return null;
        }
        ServerPlayer nearest = null;
        double nearestSqr = TRACK_RADIUS * TRACK_RADIUS;
        for (ServerPlayer candidate : level.players()) {
            if (candidate.isSpectator() || !candidate.isAlive()) {
                continue;
            }
            double distSqr = this.distanceToSqr(candidate);
            if (distSqr <= nearestSqr) {
                nearestSqr = distSqr;
                nearest = candidate;
            }
        }
        return nearest;
    }

    /**
     * Vrai si AU MOINS un joueur a l'entité dans son cône de vision
     * (dot &gt; {@value #OBSERVED_DOT_THRESHOLD}, ≈ ±60°) avec une ligne de
     * vue dégagée ({@code Level.clip}, pattern WatcherEntity).
     */
    private boolean computeObserved(ServerLevel level) {
        Vec3 myEye = this.getEyePosition();
        for (ServerPlayer candidate : level.players()) {
            if (candidate.isSpectator() || !candidate.isAlive() || !this.isOwnedBy(candidate)
                    || this.distanceToSqr(candidate) > TRACK_RADIUS * TRACK_RADIUS) {
                continue;
            }
            Vec3 playerEye = candidate.getEyePosition();
            Vec3 toMe = myEye.subtract(playerEye);
            if (toMe.lengthSqr() < 1.0E-4) {
                return true;
            }
            if (candidate.getViewVector(1.0F).normalize().dot(toMe.normalize())
                    < OBSERVED_DOT_THRESHOLD) {
                continue; // hors écran
            }
            BlockHitResult hit = level.clip(new ClipContext(playerEye, myEye,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, candidate));
            if (hit.getType() == HitResult.Type.MISS) {
                return true;
            }
        }
        return false;
    }

    /** Regard précis, continu et strictement limité au propriétaire. */
    private boolean isPreciselyStaredAtBy(ServerLevel level, ServerPlayer owner) {
        if (!this.isOwnedBy(owner) || owner.isSleeping()) {
            return false;
        }
        Vec3 playerEye = owner.getEyePosition();
        Vec3 myEye = this.getEyePosition();
        Vec3 toMe = myEye.subtract(playerEye);
        if (toMe.lengthSqr() < 1.0E-4
                || owner.getViewVector(1.0f).normalize().dot(toMe.normalize()) < STARE_DOT_THRESHOLD) {
            return false;
        }
        BlockHitResult hit = level.clip(new ClipContext(playerEye, myEye,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        return hit.getType() == HitResult.Type.MISS;
    }

    /** Vrai si au moins un Contrefait est dans le rayon (miroir des autres entités). */
    public static boolean hasAnyContrefaitNearby(ServerLevel level, BlockPos pos, double radius) {
        return !level.getEntitiesOfClass(ContrefaitEntity.class, new AABB(pos).inflate(radius)).isEmpty();
    }

    public static boolean hasAnyContrefaitNearby(ServerLevel level, BlockPos pos, double radius,
                                                 ServerPlayer owner) {
        return !level.getEntitiesOfClass(ContrefaitEntity.class, new AABB(pos).inflate(radius),
                entity -> entity.isOwnedBy(owner)).isEmpty();
    }

    /**
     * Goal unique : approche le joueur le plus proche quand PERSONNE ne
     * regarde ; se fige totalement (navigation stoppée, regard fixé) dès
     * qu'il est observé. Même API Goal que {@code StalkPlayerGoal} (flags
     * MOVE + LOOK, canUse/tick).
     */
    static final class ApproachWhenUnseenGoal extends Goal {

        private final ContrefaitEntity mob;
        private ServerPlayer target;
        private int repathCooldown;

        ApproachWhenUnseenGoal(ContrefaitEntity mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (this.mob.isLunging()) {
                return false;
            }
            if (!(this.mob.level() instanceof ServerLevel level)) {
                return false;
            }
            this.target = this.mob.nearestPlayer(level);
            return this.target != null;
        }

        @Override
        public boolean canContinueToUse() {
            return !this.mob.isLunging() && this.target != null
                    && this.target.isAlive() && !this.target.isRemoved()
                    && this.mob.isOwnedBy(this.target)
                    && this.target.level() == this.mob.level();
        }

        @Override
        public void stop() {
            this.target = null;
            this.mob.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (this.target == null || this.mob.isLunging()) {
                return;
            }
            if (this.mob.isObserved()) {
                // Figé en pleine enjambée. Il fixe le joueur. Il attend.
                this.mob.getNavigation().stop();
                this.mob.getLookControl().setLookAt(this.target, 30.0f, 30.0f);
                this.repathCooldown = 0;
                return;
            }
            this.mob.getLookControl().setLookAt(this.target, 30.0f, 30.0f);
            if (--this.repathCooldown <= 0) {
                this.repathCooldown = 20;
                this.mob.getNavigation().moveTo(this.target, APPROACH_SPEED);
            }
        }
    }
}

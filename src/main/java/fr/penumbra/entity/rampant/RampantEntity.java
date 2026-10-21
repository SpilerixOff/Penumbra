package fr.penumbra.entity.rampant;

import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.network.PenumbraNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Le Rampant : prédateur territorial des grottes.
 *
 * <p>Il commence fixé à un plafond ou une paroi, se décroche lorsque sa proie
 * passe dessous, poursuit très vite au ras du sol puis bondit. Une lumière
 * forte interrompt sa chasse : il cherche une case plus sombre avant de
 * disparaître si le joueur maintient l'éclairage. Aucun bloc n'est modifié.</p>
 */
public final class RampantEntity extends Monster {

    public static final int PHASE_CLINGING = 0;
    public static final int PHASE_DROPPING = 1;
    public static final int PHASE_HUNTING = 2;
    public static final int PHASE_LEAPING = 3;
    public static final int PHASE_FLEEING_LIGHT = 4;
    public static final int PHASE_CLIMBING = 5;

    public static final int ATTACHMENT_FLOOR = 0;
    public static final int ATTACHMENT_CEILING = 1;
    public static final int ATTACHMENT_NORTH = 2;
    public static final int ATTACHMENT_SOUTH = 3;
    public static final int ATTACHMENT_WEST = 4;
    public static final int ATTACHMENT_EAST = 5;

    private static final EntityDataAccessor<Integer> DATA_PHASE =
            SynchedEntityData.defineId(RampantEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ATTACHMENT =
            SynchedEntityData.defineId(RampantEntity.class, EntityDataSerializers.INT);

    private static final int MAX_LIFETIME_TICKS = 20 * 75;
    private static final int LIGHT_ESCAPE_TICKS = 20 * 5;
    private static final int BLOCK_LIGHT_LIMIT = 7;
    private static final int TARGET_SCAN_INTERVAL = 10;
    private static final int REPATH_INTERVAL = 8;
    private static final int CLIMB_APPROACH_SCAN_INTERVAL = 16;
    private static final int MAX_SURFACE_MOVE_FAILURES = 3;
    private static final double CLIMB_APPROACH_RADIUS = 5.0;
    private static final double TRACK_RADIUS = 48.0;
    private static final double TERRITORY_RADIUS = 30.0;
    private static final double LEAP_MIN_DISTANCE = 2.4;
    private static final double LEAP_MAX_DISTANCE = 8.0;
    private static final double LEAP_HIT_DISTANCE = 1.65;

    private UUID ownerUuid;
    private BlockPos home = BlockPos.ZERO;
    private int phaseTicks;
    private int clientVisualPhase = -1;
    private int clientVisualPhaseTicks;
    private int lifetimeTicks;
    private int attackCooldown;
    private int brightTicks;
    private boolean firstScreamerTriggered;
    private Vec3 attachmentPosition;
    private Vec3 surfaceStartPosition;
    private int surfaceDirection = 1;
    private int surfaceMoveFailures;
    private boolean reperchRequested;
    private BlockPos climbApproach;
    private Direction climbApproachWall;
    private int climbApproachExpiresAt;
    private ServerPlayer target;

    public RampantEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PHASE, PHASE_HUNTING);
        builder.define(DATA_ATTACHMENT, ATTACHMENT_FLOOR);
    }

    public static AttributeSupplier.Builder createRampantAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 28.0)
                .add(Attributes.MOVEMENT_SPEED, 0.36)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.FOLLOW_RANGE, TRACK_RADIUS)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.35);
    }

    /** Le déplacement est piloté par les phases, aucun goal vanilla concurrent. */
    @Override
    protected void registerGoals() {
        // Intentionnel : tick() garde la chute, la fuite et le bond atomiques.
    }

    @Override
    public void checkDespawn() {
        // Durée de vie et éloignement sont bornés explicitement dans tick().
    }

    /** Une chasse ou escalade en cours ne reprend jamais après un reload. */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    public int getRampantPhase() {
        return this.entityData.get(DATA_PHASE);
    }

    /** Compteur visuel client remis a zero lors de chaque changement de phase. */
    public float getVisualPhaseTicks(float partialTick) {
        return this.clientVisualPhase < 0 ? 0.0f
                : this.clientVisualPhaseTicks + Mth.clamp(partialTick, 0.0f, 1.0f);
    }

    public int getAttachment() {
        return this.entityData.get(DATA_ATTACHMENT);
    }

    public boolean isClinging() {
        return getRampantPhase() == PHASE_CLINGING;
    }

    public boolean isLeaping() {
        return getRampantPhase() == PHASE_LEAPING;
    }

    public boolean isClimbing() {
        return getRampantPhase() == PHASE_CLIMBING;
    }

    public boolean isOwnedBy(ServerPlayer player) {
        return this.ownerUuid != null && this.ownerUuid.equals(player.getUUID());
    }

    /**
     * Initialise la scène sur la surface trouvée par {@link RampantManager}.
     * La collision et la gravité sont suspendues uniquement pendant l'affût.
     */
    public void beginClinging(ServerPlayer owner, Vec3 position, BlockPos territoryHome,
                              int attachment) {
        this.ownerUuid = owner.getUUID();
        this.target = owner;
        this.home = territoryHome.immutable();
        this.attachmentPosition = position;
        this.entityData.set(DATA_ATTACHMENT, Mth.clamp(
                attachment, ATTACHMENT_CEILING, ATTACHMENT_EAST));
        setPhase(PHASE_CLINGING);
        this.snapTo(position.x, position.y, position.z, owner.getYRot() + 180.0f, 0.0f);
        this.setNoGravity(true);
        this.noPhysics = true;
        this.setInvulnerable(true);
        this.setDeltaMovement(Vec3.ZERO);
        this.setTarget(owner);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.phaseTicks = Math.max(0, input.getIntOr("PenumbraPhaseTicks", 0));
        this.lifetimeTicks = Math.max(0, input.getIntOr("PenumbraLifetime", 0));
        this.attackCooldown = Math.max(0, input.getIntOr("PenumbraAttackCooldown", 0));
        this.brightTicks = Math.max(0, input.getIntOr("PenumbraBrightTicks", 0));
        this.firstScreamerTriggered = input.getIntOr("PenumbraScreamer", 0) != 0;
        this.surfaceDirection = input.getIntOr("PenumbraSurfaceDirection", 1) < 0 ? -1 : 1;
        this.reperchRequested = input.getIntOr("PenumbraReperch", 0) != 0;
        this.entityData.set(DATA_PHASE, Mth.clamp(input.getIntOr(
                "PenumbraPhase", PHASE_HUNTING), PHASE_CLINGING, PHASE_CLIMBING));
        this.entityData.set(DATA_ATTACHMENT, Mth.clamp(input.getIntOr(
                "PenumbraAttachment", ATTACHMENT_FLOOR), ATTACHMENT_FLOOR, ATTACHMENT_EAST));
        this.home = new BlockPos(
                input.getIntOr("PenumbraHomeX", this.blockPosition().getX()),
                input.getIntOr("PenumbraHomeY", this.blockPosition().getY()),
                input.getIntOr("PenumbraHomeZ", this.blockPosition().getZ()));
        String owner = input.getStringOr("PenumbraOwner", "");
        if (!owner.isBlank()) {
            try {
                this.ownerUuid = UUID.fromString(owner);
            } catch (IllegalArgumentException ignored) {
                this.ownerUuid = null;
            }
        }
        if (isClinging() || isClimbing()) {
            this.attachmentPosition = this.position();
            this.surfaceStartPosition = this.position();
            this.setNoGravity(true);
            this.noPhysics = true;
            this.setInvulnerable(isClinging());
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("PenumbraPhaseTicks", this.phaseTicks);
        output.putInt("PenumbraLifetime", this.lifetimeTicks);
        output.putInt("PenumbraAttackCooldown", this.attackCooldown);
        output.putInt("PenumbraBrightTicks", this.brightTicks);
        output.putInt("PenumbraScreamer", this.firstScreamerTriggered ? 1 : 0);
        output.putInt("PenumbraSurfaceDirection", this.surfaceDirection);
        output.putInt("PenumbraReperch", this.reperchRequested ? 1 : 0);
        output.putInt("PenumbraPhase", getRampantPhase());
        output.putInt("PenumbraAttachment", getAttachment());
        output.putInt("PenumbraHomeX", this.home.getX());
        output.putInt("PenumbraHomeY", this.home.getY());
        output.putInt("PenumbraHomeZ", this.home.getZ());
        if (this.ownerUuid != null) {
            output.putString("PenumbraOwner", this.ownerUuid.toString());
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            int phase = this.getRampantPhase();
            if (phase != this.clientVisualPhase) {
                this.clientVisualPhase = phase;
                this.clientVisualPhaseTicks = 0;
            } else {
                this.clientVisualPhaseTicks++;
            }
            return;
        }
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        if (level.getDifficulty() == Difficulty.PEACEFUL
                || !PenumbraConfig.get().undergroundEventsEnabled) {
            this.discard();
            return;
        }
        if (++this.lifetimeTicks > MAX_LIFETIME_TICKS) {
            this.discard();
            return;
        }
        if (this.attackCooldown > 0) {
            this.attackCooldown--;
        }

        ServerPlayer owner = owner(level);
        if (owner == null || !owner.isAlive() || owner.isSpectator()) {
            this.discard();
            return;
        }
        this.target = owner;
        this.setTarget(owner);

        double ownerFromHome = owner.position().distanceToSqr(Vec3.atCenterOf(this.home));
        if (ownerFromHome > TRACK_RADIUS * TRACK_RADIUS) {
            this.discard();
            return;
        }

        this.phaseTicks++;
        switch (getRampantPhase()) {
            case PHASE_CLINGING -> tickClinging(level, owner);
            case PHASE_DROPPING -> tickDropping();
            case PHASE_LEAPING -> tickLeaping(level, owner);
            case PHASE_FLEEING_LIGHT -> tickFleeingLight(level, owner);
            case PHASE_CLIMBING -> tickClimbing(level, owner);
            default -> tickHunting(level, owner);
        }
    }

    private void tickClinging(ServerLevel level, ServerPlayer owner) {
        if (this.attachmentPosition == null) {
            this.attachmentPosition = this.position();
        }
        this.setPos(this.attachmentPosition);
        this.setDeltaMovement(Vec3.ZERO);
        this.getLookControl().setLookAt(owner, 180.0f, 180.0f);

        Direction surface = attachmentDirection(getAttachment());
        if (surface == null || !supportsSurface(level, this.attachmentPosition, surface)) {
            releaseFromSurface();
            return;
        }

        if (blockLight() > BLOCK_LIGHT_LIMIT) {
            releaseFromSurface();
            this.brightTicks = 1;
            return;
        }
        if (this.phaseTicks == 12) {
            this.playSound(SoundEvents.SPIDER_AMBIENT, 0.8f, 0.42f);
        }
        if (RampantBehavior.shouldRelease(this.phaseTicks, this.distanceToSqr(owner))) {
            releaseFromSurface();
        }
    }

    private void releaseFromSurface() {
        Direction wall = attachmentDirection(getAttachment());
        Vec3 away = wall == null || wall == Direction.UP
                ? new Vec3(0.0, -0.48, 0.0)
                : new Vec3(-wall.getStepX() * 0.26, -0.20, -wall.getStepZ() * 0.26);
        this.noPhysics = false;
        this.setNoGravity(false);
        this.setInvulnerable(true);
        clearClimbApproach();
        this.setDeltaMovement(away);
        setPhase(PHASE_DROPPING);
        this.playSound(SoundEvents.SPIDER_STEP, 0.9f, 0.55f);
    }

    private void tickDropping() {
        if (this.onGround() || this.phaseTicks >= 24) {
            this.setInvulnerable(false);
            this.entityData.set(DATA_ATTACHMENT, ATTACHMENT_FLOOR);
            setPhase(PHASE_HUNTING);
        }
    }

    private void tickHunting(ServerLevel level, ServerPlayer owner) {
        this.noPhysics = false;
        this.setNoGravity(false);
        this.setInvulnerable(false);
        int light = blockLight();
        if (light > BLOCK_LIGHT_LIMIT || level.canSeeSky(this.blockPosition().above())) {
            setPhase(PHASE_FLEEING_LIGHT);
            tickFleeingLight(level, owner);
            return;
        }
        this.brightTicks = 0;

        if (this.position().distanceToSqr(Vec3.atCenterOf(this.home))
                > TERRITORY_RADIUS * TERRITORY_RADIUS) {
            clearClimbApproach();
            this.getNavigation().moveTo(
                    this.home.getX() + 0.5, this.home.getY(), this.home.getZ() + 0.5, 1.25);
            return;
        }

        double verticalDelta = owner.getY() - this.getY();
        if (this.phaseTicks >= 90) {
            // Une chasse au sol trop longue se termine par un retour en hauteur,
            // même si aucune attaque n'a encore porté.
            this.reperchRequested = true;
        }
        if (RampantSurfaceMotion.shouldSeekWall(
                this.phaseTicks, verticalDelta, this.reperchRequested)) {
            if (tickClimbApproach(level, owner)) {
                return;
            }
        } else {
            clearClimbApproach();
        }

        this.getLookControl().setLookAt(owner, 120.0f, 120.0f);
        if (this.tickCount % REPATH_INTERVAL == 0) {
            this.getNavigation().moveTo(owner, 1.38);
        }

        double distanceSqr = this.distanceToSqr(owner);
        if (this.attackCooldown <= 0
                && distanceSqr >= LEAP_MIN_DISTANCE * LEAP_MIN_DISTANCE
                && distanceSqr <= LEAP_MAX_DISTANCE * LEAP_MAX_DISTANCE
                && Math.abs(owner.getY() - this.getY()) <= 3.0
                && this.hasLineOfSight(owner)) {
            startLeap(owner);
        }
    }

    private void startLeap(ServerPlayer owner) {
        boolean launchedFromSurface = isClimbing() || isClinging();
        Vec3 toTarget = owner.getEyePosition().subtract(this.getEyePosition());
        Vec3 horizontal = new Vec3(toTarget.x, 0.0, toTarget.z);
        if (horizontal.lengthSqr() < 1.0E-5) {
            if (launchedFromSurface) {
                horizontal = Vec3.ZERO; // plongée verticale si la proie est exactement dessous
            } else {
                Vec3 look = owner.getLookAngle();
                horizontal = new Vec3(-look.x, 0.0, -look.z);
                if (horizontal.lengthSqr() < 1.0E-5) {
                    horizontal = new Vec3(1.0, 0.0, 0.0);
                }
                horizontal = horizontal.normalize();
            }
        } else {
            horizontal = horizontal.normalize();
        }
        double vertical = launchedFromSurface
                ? Mth.clamp(toTarget.y * 0.22, -0.76, 0.58)
                : Mth.clamp(toTarget.y * 0.18 + 0.42, 0.34, 0.78);
        this.getNavigation().stop();
        this.noPhysics = false;
        this.setDeltaMovement(horizontal.x * 0.92, vertical, horizontal.z * 0.92);
        this.setNoGravity(false);
        this.setInvulnerable(false);
        this.entityData.set(DATA_ATTACHMENT, ATTACHMENT_FLOOR);
        clearClimbApproach();
        setPhase(PHASE_LEAPING);
        this.playSound(SoundEvents.SPIDER_HURT, 1.25f, 0.42f);
    }

    private void tickLeaping(ServerLevel level, ServerPlayer owner) {
        this.getLookControl().setLookAt(owner, 180.0f, 180.0f);
        if (this.distanceToSqr(owner) <= LEAP_HIT_DISTANCE * LEAP_HIT_DISTANCE) {
            hitAfterLeap(level, owner);
            return;
        }
        if (RampantBehavior.leapExpired(this.phaseTicks)
                || (this.phaseTicks > 5 && this.onGround())) {
            this.attackCooldown = 30;
            this.noPhysics = false;
            this.setNoGravity(false);
            setPhase(PHASE_HUNTING);
        }
    }

    private void hitAfterLeap(ServerLevel level, ServerPlayer owner) {
        this.doHurtTarget(level, owner);
        this.playSound(SoundEvents.SPIDER_DEATH, 1.0f, 0.50f);
        if (!this.firstScreamerTriggered) {
            this.firstScreamerTriggered = true;
            String type = PenumbraConfig.get().screamersEnabled
                    ? "rampant_screamer" : "apparition";
            PenumbraNetworking.sendJumpscare(owner, type, 0.90f);
        }
        this.attackCooldown = 50;
        this.reperchRequested = true;
        Vec3 away = this.position().subtract(owner.position());
        if (away.lengthSqr() < 1.0E-5) {
            away = owner.getLookAngle().scale(-1.0);
        }
        away = away.normalize();
        this.setDeltaMovement(away.x * 0.36, 0.22, away.z * 0.36);
        setPhase(PHASE_HUNTING);
    }

    /**
     * Rejoint d'abord à navigation normale une case sûre contre un mur. Ce sas
     * évite tout téléport ou passage à travers un angle avant l'escalade.
     */
    private boolean tickClimbApproach(ServerLevel level, ServerPlayer owner) {
        if (this.climbApproach != null) {
            if (this.tickCount > this.climbApproachExpiresAt
                    || this.climbApproachWall == null
                    || !isValidClimbApproach(level, this.climbApproach, this.climbApproachWall)) {
                clearClimbApproach();
            } else {
                Vec3 targetPosition = Vec3.atBottomCenterOf(this.climbApproach);
                if (this.position().distanceToSqr(targetPosition) <= 0.75 * 0.75) {
                    Direction wall = this.climbApproachWall;
                    clearClimbApproach();
                    return startClimbing(level, owner, wall);
                }
                if (this.tickCount % REPATH_INTERVAL == 0) {
                    this.getNavigation().moveTo(
                            targetPosition.x, targetPosition.y, targetPosition.z, 1.42);
                }
                return true;
            }
        }

        if (this.tickCount % CLIMB_APPROACH_SCAN_INTERVAL != 0) {
            return false;
        }
        SurfaceStart start = findNearbyClimbStart(level);
        if (start == null) {
            return false;
        }
        this.climbApproach = start.air();
        this.climbApproachWall = start.wall();
        this.climbApproachExpiresAt = this.tickCount + 60;
        Vec3 targetPosition = Vec3.atBottomCenterOf(start.air());
        this.getNavigation().moveTo(targetPosition.x, targetPosition.y, targetPosition.z, 1.42);
        this.getLookControl().setLookAt(owner, 120.0f, 120.0f);
        return true;
    }

    private boolean startClimbing(ServerLevel level, ServerPlayer owner, Direction wall) {
        if (!wall.getAxis().isHorizontal() || !supportsSurface(level, this.position(), wall)) {
            return false;
        }
        BlockPos air = this.blockPosition();
        Vec3 aligned = new Vec3(air.getX() + 0.5, this.getY(), air.getZ() + 0.5);
        Vec3 alignment = aligned.subtract(this.position());
        if (!isCollisionFreeMove(level, alignment)) {
            return false;
        }
        this.setPos(aligned);
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        this.setNoGravity(true);
        this.noPhysics = true;
        this.setInvulnerable(false);
        this.entityData.set(DATA_ATTACHMENT, attachmentFor(wall));
        this.surfaceDirection = RampantSurfaceMotion.chooseWallDirection(
                owner.getY() - this.getY(), this.surfaceDirection, this.reperchRequested);
        this.surfaceStartPosition = this.position();
        this.surfaceMoveFailures = 0;
        setPhase(PHASE_CLIMBING);
        this.playSound(SoundEvents.SPIDER_STEP, 0.75f, 0.62f);
        return true;
    }

    private void tickClimbing(ServerLevel level, ServerPlayer owner) {
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        this.setNoGravity(true);
        this.noPhysics = true;
        this.setInvulnerable(false);
        this.getLookControl().setLookAt(owner, 180.0f, 180.0f);

        Direction surface = attachmentDirection(getAttachment());
        if (surface == null || !supportsSurface(level, this.position(), surface)) {
            releaseFromSurface();
            return;
        }
        if (blockLight() > BLOCK_LIGHT_LIMIT || level.canSeeSky(this.blockPosition().above())) {
            this.brightTicks = 1;
            releaseFromSurface();
            return;
        }
        if (this.phaseTicks > 8 && canLeapAt(owner)) {
            startLeap(owner);
            return;
        }

        if (surface == Direction.UP) {
            tickCeilingCrawl(level, owner);
        } else {
            tickWallClimb(level, owner, surface);
        }
    }

    private void tickWallClimb(ServerLevel level, ServerPlayer owner, Direction wall) {
        this.surfaceDirection = RampantSurfaceMotion.chooseWallDirection(
                owner.getY() - this.getY(), this.surfaceDirection, this.reperchRequested);
        RampantSurfaceMotion.Step pureStep =
                RampantSurfaceMotion.wallStep(this.surfaceDirection);
        Vec3 step = new Vec3(pureStep.x(), pureStep.y(), pureStep.z());

        if (trySurfaceMove(level, step, wall)) {
            this.surfaceMoveFailures = 0;
            if (this.tickCount % 10 == 0) {
                this.playSound(SoundEvents.SPIDER_STEP, 0.58f, 0.72f);
            }
            if (this.surfaceDirection < 0 && hasFloorBelow(level, this.position())) {
                landFromSurface();
                return;
            }
            double travelled = surfaceTravelled();
            if (RampantSurfaceMotion.shouldPerch(this.phaseTicks, travelled,
                    owner.getY() - this.getY(), this.reperchRequested)) {
                perchOnCurrentSurface();
            }
            return;
        }

        if (this.surfaceDirection > 0 && supportsSurface(level, this.position(), Direction.UP)) {
            this.entityData.set(DATA_ATTACHMENT, ATTACHMENT_CEILING);
            this.surfaceStartPosition = this.position();
            this.surfaceMoveFailures = 0;
            this.playSound(SoundEvents.SPIDER_STEP, 0.72f, 0.52f);
            return;
        }
        if (++this.surfaceMoveFailures >= MAX_SURFACE_MOVE_FAILURES) {
            if (supportsSurface(level, this.position(), wall)) {
                perchOnCurrentSurface();
            } else {
                releaseFromSurface();
            }
        }
    }

    private void tickCeilingCrawl(ServerLevel level, ServerPlayer owner) {
        RampantSurfaceMotion.Step desired = RampantSurfaceMotion.ceilingStep(
                owner.getX() - this.getX(), owner.getZ() - this.getZ());
        if (desired.length() < 1.0E-7) {
            perchOnCurrentSurface();
            return;
        }

        Vec3 forward = new Vec3(desired.x(), 0.0, desired.z());
        boolean moved = trySurfaceMove(level, forward, Direction.UP);
        if (!moved) {
            Vec3 left = new Vec3(-forward.z, 0.0, forward.x);
            moved = trySurfaceMove(level, left, Direction.UP);
        }
        if (!moved) {
            Vec3 right = new Vec3(forward.z, 0.0, -forward.x);
            moved = trySurfaceMove(level, right, Direction.UP);
        }
        if (!moved) {
            perchOnCurrentSurface();
            return;
        }

        this.surfaceMoveFailures = 0;
        if (this.tickCount % 10 == 0) {
            this.playSound(SoundEvents.SPIDER_STEP, 0.52f, 0.78f);
        }
        if (RampantSurfaceMotion.shouldPerch(this.phaseTicks, surfaceTravelled(),
                owner.getY() - this.getY(), this.reperchRequested)) {
            perchOnCurrentSurface();
        }
    }

    private boolean trySurfaceMove(ServerLevel level, Vec3 step, Direction surface) {
        if (step.lengthSqr() < 1.0E-10) {
            return false;
        }
        Vec3 targetPosition = this.position().add(step);
        if (!supportsSurface(level, targetPosition, surface)
                || targetPosition.distanceToSqr(Vec3.atCenterOf(this.home))
                > TERRITORY_RADIUS * TERRITORY_RADIUS
                || !isCollisionFreeMove(level, step)) {
            return false;
        }
        this.setPos(targetPosition);
        return true;
    }

    /** Vérifie le volume final ET le volume balayé : aucune traversée de mur. */
    private boolean isCollisionFreeMove(ServerLevel level, Vec3 step) {
        AABB current = this.getBoundingBox();
        AABB targetBox = current.move(step);
        AABB swept = current.expandTowards(step);
        return isAreaLoaded(level, targetBox)
                && isAreaLoaded(level, swept)
                && level.noCollision(this, targetBox)
                && level.noCollision(this, swept);
    }

    /** Les quatre coins suffisent ici : les pas sont plus courts qu'un bloc. */
    private boolean isAreaLoaded(ServerLevel level, AABB area) {
        int minX = Mth.floor(area.minX + 1.0E-7);
        int maxX = Mth.floor(area.maxX - 1.0E-7);
        int minZ = Mth.floor(area.minZ + 1.0E-7);
        int maxZ = Mth.floor(area.maxZ - 1.0E-7);
        int y = Mth.floor(area.minY + 1.0E-7);
        return level.isLoaded(new BlockPos(minX, y, minZ))
                && level.isLoaded(new BlockPos(minX, y, maxZ))
                && level.isLoaded(new BlockPos(maxX, y, minZ))
                && level.isLoaded(new BlockPos(maxX, y, maxZ));
    }

    private boolean supportsSurface(ServerLevel level, Vec3 position, Direction towardSurface) {
        BlockPos air = BlockPos.containing(position.x, position.y + 0.08, position.z);
        if (!level.isLoaded(air)
                || !level.getBlockState(air).isAir()
                || !level.getFluidState(air).isEmpty()) {
            return false;
        }
        BlockPos support = air.relative(towardSurface);
        return level.isLoaded(support)
                && level.getFluidState(support).isEmpty()
                && level.getBlockState(support)
                .isFaceSturdy(level, support, towardSurface.getOpposite());
    }

    private boolean canLeapAt(ServerPlayer owner) {
        double distanceSqr = this.distanceToSqr(owner);
        return this.attackCooldown <= 0
                && distanceSqr >= LEAP_MIN_DISTANCE * LEAP_MIN_DISTANCE
                && distanceSqr <= LEAP_MAX_DISTANCE * LEAP_MAX_DISTANCE
                && this.hasLineOfSight(owner);
    }

    private void perchOnCurrentSurface() {
        this.attachmentPosition = this.position();
        this.surfaceStartPosition = null;
        this.surfaceMoveFailures = 0;
        this.reperchRequested = false;
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        this.setNoGravity(true);
        this.noPhysics = true;
        this.setInvulnerable(false);
        setPhase(PHASE_CLINGING);
    }

    private void landFromSurface() {
        this.noPhysics = false;
        this.setNoGravity(false);
        this.setInvulnerable(false);
        this.entityData.set(DATA_ATTACHMENT, ATTACHMENT_FLOOR);
        this.surfaceStartPosition = null;
        this.reperchRequested = false;
        this.setDeltaMovement(Vec3.ZERO);
        setPhase(PHASE_HUNTING);
    }

    private double surfaceTravelled() {
        return this.surfaceStartPosition == null
                ? 0.0 : this.position().distanceTo(this.surfaceStartPosition);
    }

    private boolean hasFloorBelow(ServerLevel level, Vec3 position) {
        BlockPos feet = BlockPos.containing(position.x, position.y + 0.02, position.z);
        BlockPos floor = feet.below();
        return level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)
                && level.getFluidState(feet).isEmpty();
    }

    private SurfaceStart findNearbyClimbStart(ServerLevel level) {
        BlockPos origin = this.blockPosition();
        SurfaceStart best = null;
        double bestDistance = Double.MAX_VALUE;
        int radius = Mth.ceil(CLIMB_APPROACH_RADIUS);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > CLIMB_APPROACH_RADIUS * CLIMB_APPROACH_RADIUS) {
                    continue;
                }
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos air = origin.offset(dx, dy, dz);
                    for (Direction wall : Direction.Plane.HORIZONTAL) {
                        if (!isValidClimbApproach(level, air, wall)) {
                            continue;
                        }
                        double distance = air.distSqr(origin)
                                + level.getBrightness(LightLayer.BLOCK, air) * 2.0;
                        if (distance < bestDistance) {
                            bestDistance = distance;
                            best = new SurfaceStart(air.immutable(), wall);
                        }
                    }
                }
            }
        }
        return best;
    }

    private boolean isValidClimbApproach(ServerLevel level, BlockPos air, Direction wall) {
        if (!level.isLoaded(air)
                || air.distSqr(this.home) > TERRITORY_RADIUS * TERRITORY_RADIUS
                || level.canSeeSky(air.above())
                || level.getBrightness(LightLayer.BLOCK, air) > BLOCK_LIGHT_LIMIT
                || !level.getBlockState(air).isAir()
                || !level.getBlockState(air.above()).isAir()
                || !level.getFluidState(air).isEmpty()
                || !level.getBlockState(air.below())
                .isFaceSturdy(level, air.below(), Direction.UP)) {
            return false;
        }
        BlockPos support = air.relative(wall);
        BlockPos upperSupport = support.above();
        if (!level.isLoaded(support)
                || !level.isLoaded(upperSupport)
                || !level.getBlockState(support)
                .isFaceSturdy(level, support, wall.getOpposite())
                || !level.getBlockState(upperSupport)
                .isFaceSturdy(level, upperSupport, wall.getOpposite())) {
            return false;
        }
        Vec3 targetPosition = Vec3.atBottomCenterOf(air);
        Vec3 offset = targetPosition.subtract(this.position());
        return level.noCollision(this, this.getBoundingBox().move(offset));
    }

    private void clearClimbApproach() {
        this.climbApproach = null;
        this.climbApproachWall = null;
        this.climbApproachExpiresAt = 0;
    }

    private void tickFleeingLight(ServerLevel level, ServerPlayer owner) {
        this.brightTicks++;
        if (this.brightTicks > LIGHT_ESCAPE_TICKS
                || level.canSeeSky(this.blockPosition().above())) {
            this.discard();
            return;
        }
        if (blockLight() <= BLOCK_LIGHT_LIMIT - 2) {
            this.brightTicks = 0;
            setPhase(PHASE_HUNTING);
            return;
        }
        if (this.tickCount % TARGET_SCAN_INTERVAL == 0) {
            BlockPos refuge = findDarkRefuge(level, owner);
            if (refuge != null) {
                this.getNavigation().moveTo(
                        refuge.getX() + 0.5, refuge.getY(), refuge.getZ() + 0.5, 1.55);
            } else {
                Vec3 away = this.position().subtract(owner.position());
                if (away.lengthSqr() > 1.0E-5) {
                    away = away.normalize().scale(5.0);
                    this.getNavigation().moveTo(
                            this.getX() + away.x, this.getY(), this.getZ() + away.z, 1.45);
                }
            }
        }
    }

    private BlockPos findDarkRefuge(ServerLevel level, ServerPlayer owner) {
        BlockPos origin = this.blockPosition();
        BlockPos best = null;
        int bestLight = blockLight();
        for (int i = 0; i < 12; i++) {
            BlockPos candidate = origin.offset(
                    this.random.nextInt(13) - 6,
                    this.random.nextInt(5) - 2,
                    this.random.nextInt(13) - 6);
            if (!level.isLoaded(candidate)
                    || candidate.distSqr(this.home) > TERRITORY_RADIUS * TERRITORY_RADIUS
                    || level.canSeeSky(candidate.above())
                    || !level.getBlockState(candidate).isAir()
                    || !level.getBlockState(candidate.above()).isAir()
                    || !level.getBlockState(candidate.below())
                    .isFaceSturdy(level, candidate.below(), Direction.UP)) {
                continue;
            }
            int light = level.getBrightness(LightLayer.BLOCK, candidate);
            if (light < bestLight
                    || (light == bestLight && candidate.distSqr(owner.blockPosition())
                    > (best == null ? 0.0 : best.distSqr(owner.blockPosition())))) {
                best = candidate.immutable();
                bestLight = light;
            }
        }
        return best;
    }

    private int blockLight() {
        return this.level().getBrightness(LightLayer.BLOCK, this.blockPosition());
    }

    private ServerPlayer owner(ServerLevel level) {
        if (this.ownerUuid == null) {
            return null;
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(this.ownerUuid);
        return player != null && player.level() == level ? player : null;
    }

    private void setPhase(int phase) {
        this.entityData.set(DATA_PHASE, phase);
        this.phaseTicks = 0;
    }

    public static int attachmentFor(Direction direction) {
        return switch (direction) {
            case UP -> ATTACHMENT_CEILING;
            case NORTH -> ATTACHMENT_NORTH;
            case SOUTH -> ATTACHMENT_SOUTH;
            case WEST -> ATTACHMENT_WEST;
            case EAST -> ATTACHMENT_EAST;
            default -> ATTACHMENT_FLOOR;
        };
    }

    public static Direction attachmentDirection(int attachment) {
        return switch (attachment) {
            case ATTACHMENT_CEILING -> Direction.UP;
            case ATTACHMENT_NORTH -> Direction.NORTH;
            case ATTACHMENT_SOUTH -> Direction.SOUTH;
            case ATTACHMENT_WEST -> Direction.WEST;
            case ATTACHMENT_EAST -> Direction.EAST;
            default -> null;
        };
    }

    public static boolean hasOwnedRampantNearby(ServerLevel level, ServerPlayer owner, double radius) {
        AABB area = owner.getBoundingBox().inflate(radius);
        List<RampantEntity> nearby = level.getEntities(
                EntityTypeTest.forClass(RampantEntity.class), area,
                rampant -> rampant.isAlive() && rampant.isOwnedBy(owner));
        return !nearby.isEmpty();
    }

    /** Utilisé par les commandes de diagnostic pour retrouver la manifestation. */
    public static RampantEntity nearestOwned(ServerLevel level, ServerPlayer owner, double radius) {
        return level.getEntities(EntityTypeTest.forClass(RampantEntity.class),
                        owner.getBoundingBox().inflate(radius),
                        rampant -> rampant.isAlive() && rampant.isOwnedBy(owner))
                .stream()
                .min(Comparator.comparingDouble(rampant -> rampant.distanceToSqr(owner)))
                .orElse(null);
    }

    private record SurfaceStart(BlockPos air, Direction wall) {
    }
}

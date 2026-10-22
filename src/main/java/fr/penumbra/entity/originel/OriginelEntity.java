package fr.penumbra.entity.originel;

import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.network.PenumbraNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Manifestation personnelle de l'Originel. La scène distante conserve son
 * ancien langage (dos tourné et disparition à l'approche), tandis que la scène
 * de grotte fait face à son propriétaire et réagit à un regard soutenu par une
 * charge engagée : bascule rigide, contact éventuel, puis recul visible.
 */
public class OriginelEntity extends Monster {

    public enum ManifestationMode {
        DISTANT,
        CAVE_STARE,
        LUNGE,
        WINDUP,
        RECOVERY;

        static ManifestationMode byId(int id) {
            ManifestationMode[] values = values();
            return values[Math.max(0, Math.min(values.length - 1, id))];
        }
    }

    public static final double VANISH_DISTANCE = 32.0;
    private static final double TRACK_RADIUS = 128.0;
    private static final int DISTANT_MAX_LIFETIME_TICKS = 900;
    private static final float DISTANT_TURN_FACTOR = 0.04f;
    private static final double STARE_DOT_THRESHOLD = Math.cos(Math.toRadians(3.5));

    private static final EntityDataAccessor<Integer> DATA_MODE =
            SynchedEntityData.defineId(OriginelEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_LUNGE_PROGRESS =
            SynchedEntityData.defineId(OriginelEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_PHASE_TICKS =
            SynchedEntityData.defineId(OriginelEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LUNGE_DURATION =
            SynchedEntityData.defineId(OriginelEntity.class, EntityDataSerializers.INT);

    private int lifetimeTicks;
    private int caveSceneDurationTicks = 120;
    private int stareTicks;
    private int lungeTicks;
    private int lungeDurationTicks = 10;
    private Vec3 lungeStart = Vec3.ZERO;
    private Vec3 lungeTarget = Vec3.ZERO;
    private Vec3 lungeDirection = Vec3.ZERO;
    private UUID ownerUuid;

    private BlockPos temporaryTorchPos;
    private BlockState temporaryTorchOriginal;
    private BlockState temporaryTorchPlaced;
    private boolean restoringTorch;

    public OriginelEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.setNoAi(true);
        this.setInvulnerable(true);
    }

    public static AttributeSupplier.Builder createOriginelAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.FOLLOW_RANGE, TRACK_RADIUS)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_MODE, ManifestationMode.DISTANT.ordinal());
        builder.define(DATA_LUNGE_PROGRESS, 0.0f);
        builder.define(DATA_PHASE_TICKS, 0);
        builder.define(DATA_LUNGE_DURATION, 10);
    }

    @Override
    protected void registerGoals() {
        // Comportement entièrement piloté par tick().
    }

    @Override
    public void checkDespawn() {
        // Toutes les sorties sont bornées et gérées dans tick().
    }

    @Override
    public void knockback(double strength, double x, double z) {
        // Invulnérable et immobile hors charge scénarisée.
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /** Les manifestations et leur charge ne sont envoyées qu'au propriétaire. */
    @Override
    public boolean broadcastToPlayer(ServerPlayer player) {
        return this.isOwnedBy(player) && super.broadcastToPlayer(player);
    }

    /** Une apparition est transitoire et ne doit jamais survivre au rechargement d'un chunk. */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    public void setOwner(ServerPlayer owner) {
        UUID requestedOwner = owner.getUUID();
        if (this.ownerUuid == null) {
            this.ownerUuid = requestedOwner;
        } else if (!this.ownerUuid.equals(requestedOwner)) {
            throw new IllegalStateException("Originel owner cannot be reassigned");
        }
    }

    public boolean isOwnedBy(ServerPlayer player) {
        return this.ownerUuid != null && this.ownerUuid.equals(player.getUUID());
    }

    public ManifestationMode getManifestationMode() {
        return ManifestationMode.byId(this.entityData.get(DATA_MODE));
    }

    public boolean isCaveAnimation() {
        return this.getManifestationMode() != ManifestationMode.DISTANT;
    }

    public float getLungeProgress() {
        return this.entityData.get(DATA_LUNGE_PROGRESS);
    }

    public float getLungeProgress(float partialTick) {
        return getManifestationMode() == ManifestationMode.LUNGE
                ? phaseProgress(partialTick, this.entityData.get(DATA_LUNGE_DURATION))
                : getLungeProgress();
    }

    public float getWindupProgress(float partialTick) {
        return phaseProgress(partialTick, OriginelTiming.WINDUP_TICKS);
    }

    public float getRecoveryProgress(float partialTick) {
        return phaseProgress(partialTick, OriginelTiming.RECOVERY_TICKS);
    }

    private float phaseProgress(float partialTick, int duration) {
        return Mth.clamp((this.entityData.get(DATA_PHASE_TICKS)
                + Mth.clamp(partialTick, 0.0f, 1.0f)) / Math.max(1, duration), 0.0f, 1.0f);
    }

    public void configureDistantSighting() {
        this.entityData.set(DATA_MODE, ManifestationMode.DISTANT.ordinal());
        this.caveSceneDurationTicks = 120;
    }

    public void configureCaveSighting(int durationTicks) {
        this.entityData.set(DATA_MODE, ManifestationMode.CAVE_STARE.ordinal());
        this.caveSceneDurationTicks = OriginelTiming.caveSceneDurationTicks(
                durationTicks, PenumbraConfig.get().watcherStareSeconds);
    }

    /** Mémorise exactement les états à comparer/restaurer à la fin de la scène. */
    public void setTemporaryTorch(BlockPos pos, BlockState original, BlockState placed) {
        this.temporaryTorchPos = pos.immutable();
        this.temporaryTorchOriginal = original;
        this.temporaryTorchPlaced = placed;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.lifetimeTicks = Math.max(0, input.getIntOr("PenumbraLifetime", 0));
        this.caveSceneDurationTicks = Mth.clamp(input.getIntOr("PenumbraSceneDuration", 120),
                OriginelTiming.MIN_CAVE_SCENE_TICKS, OriginelTiming.MAX_CAVE_SCENE_TICKS);
        ManifestationMode savedMode = ManifestationMode.byId(input.getIntOr("PenumbraMode", 0));
        // A partially scripted attack has no resumable target in NBT.
        this.entityData.set(DATA_MODE, (savedMode == ManifestationMode.DISTANT
                ? savedMode : ManifestationMode.CAVE_STARE).ordinal());
        this.entityData.set(DATA_PHASE_TICKS, 0);
        this.entityData.set(DATA_LUNGE_PROGRESS, 0.0f);
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
        output.putInt("PenumbraSceneDuration", this.caveSceneDurationTicks);
        output.putInt("PenumbraMode", this.getManifestationMode().ordinal());
        output.putInt("PenumbraPhaseTicks", this.entityData.get(DATA_PHASE_TICKS));
        output.putInt("PenumbraLungeDuration", this.entityData.get(DATA_LUNGE_DURATION));
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
            // /kill may leave a dying LivingEntity ticking briefly. Never run
            // its committed attack again, and restore scene-owned blocks now.
            this.restoreTemporaryTorch();
            this.discard();
            return;
        }
        this.setDeltaMovement(Vec3.ZERO);

        PenumbraConfig config = PenumbraConfig.get();
        if (!config.originelEnabled || level.getDifficulty() == Difficulty.PEACEFUL) {
            this.discard();
            return;
        }

        ServerPlayer owner = this.resolveOwner(level);
        if (owner == null || owner.isSpectator() || !owner.isAlive()
                || owner.isRemoved() || owner.isSleeping()) {
            this.discard();
            return;
        }
        ManifestationMode mode = this.getManifestationMode();
        if (mode == ManifestationMode.WINDUP) {
            this.tickWindup(owner);
            return;
        }
        if (mode == ManifestationMode.LUNGE) {
            this.tickLunge(level, owner);
            return;
        }
        if (mode == ManifestationMode.RECOVERY) {
            this.tickRecovery(level, owner);
            return;
        }

        this.lifetimeTicks++;
        if (mode == ManifestationMode.CAVE_STARE) {
            this.tickCaveStare(level, owner);
        } else {
            this.tickDistant(owner);
        }
    }

    private void tickDistant(ServerPlayer player) {
        if (this.lifetimeTicks > DISTANT_MAX_LIFETIME_TICKS
                || this.distanceToSqr(player) < VANISH_DISTANCE * VANISH_DISTANCE) {
            this.discard();
            return;
        }
        this.faceAwayFrom(player);
    }

    private void tickCaveStare(ServerLevel level, ServerPlayer player) {
        // Le corps suit avec une légère inertie tandis que la tête rattrape plus
        // vite le joueur : le renderer reçoit ainsi une vraie rotation de cou.
        this.faceTowards(player, 0.18f);

        if (this.isPreciselyStaredAtBy(level, player)) {
            this.stareTicks++;
            int threshold = Math.max(20, PenumbraConfig.get().watcherStareSeconds * 20);
            if (this.stareTicks >= threshold) {
                this.beginWindup(player);
                return;
            }
        } else {
            this.stareTicks = 0;
        }

        if (this.lifetimeTicks >= this.caveSceneDurationTicks) {
            this.discard();
        }
    }

    private void beginWindup(ServerPlayer player) {
        this.entityData.set(DATA_MODE, ManifestationMode.WINDUP.ordinal());
        this.entityData.set(DATA_PHASE_TICKS, 0);
        this.entityData.set(DATA_LUNGE_PROGRESS, 0.0f);
        this.lungeTicks = 0;
        this.lungeDurationTicks = 8 + this.random.nextInt(5);
        this.entityData.set(DATA_LUNGE_DURATION, this.lungeDurationTicks);
        this.lungeStart = this.position();
        // Commit to the observed position before the warning, permitting a side step.
        Vec3 toTarget = player.position().subtract(this.position());
        if (toTarget.lengthSqr() > 32.0 * 32.0) {
            this.beginRecovery();
            return;
        }
        this.lungeTarget = player.position();
        this.lungeDirection = toTarget.multiply(1.0, 0.0, 1.0).normalize();
        this.noPhysics = false;
    }

    private void tickWindup(ServerPlayer player) {
        this.faceTowards(player, 0.18f);
        int elapsed = this.entityData.get(DATA_PHASE_TICKS) + 1;
        this.entityData.set(DATA_PHASE_TICKS, elapsed);
        if (elapsed >= OriginelTiming.WINDUP_TICKS) {
            this.lungeStart = this.position();
            this.entityData.set(DATA_MODE, ManifestationMode.LUNGE.ordinal());
            this.entityData.set(DATA_PHASE_TICKS, 0);
            this.setNoGravity(true);
        }
    }

    private void tickLunge(ServerLevel level, ServerPlayer player) {
        this.setNoGravity(true);
        this.noPhysics = false;
        this.lungeTicks++;
        float progress = Math.min(1.0f, (float) this.lungeTicks / this.lungeDurationTicks);
        this.entityData.set(DATA_LUNGE_PROGRESS, progress);
        this.entityData.set(DATA_PHASE_TICKS, this.lungeTicks);
        double eased = OriginelTiming.lungeEase(progress);
        Vec3 destination = this.lungeStart.scale(1.0 - eased).add(this.lungeTarget.scale(eased));
        Vec3 travel = destination.subtract(this.position());
        int steps = OriginelTiming.collisionSteps(travel.length());
        Vec3 step = travel.scale(1.0 / steps);
        for (int index = 0; index < steps; index++) {
            if (!this.canMoveThrough(level, step)) {
                this.beginRecovery();
                return;
            }
            this.setPos(this.position().add(step));
            // Close body contact, never a guaranteed hit at the end of the timer.
            if (this.getBoundingBox().inflate(0.12).intersects(player.getBoundingBox())
                    && this.hasLineOfSight(player)) {
                this.doHurtTarget(level, player);
                if (PenumbraConfig.get().jumpscareFrequency > 0.0f) {
                    PenumbraNetworking.sendJumpscare(player, "impact", 1.0f);
                }
                this.beginRecovery();
                return;
            }
        }
        this.faceTowards(player, 1.0f);
        if (this.lungeTicks >= this.lungeDurationTicks) {
            this.beginRecovery();
        }
    }

    private boolean canMoveThrough(ServerLevel level, Vec3 step) {
        AABB swept = this.getBoundingBox().expandTowards(step);
        return level.hasChunksAt(Mth.floor(swept.minX), Mth.floor(swept.minZ),
                Mth.floor(swept.maxX), Mth.floor(swept.maxZ))
                && level.getWorldBorder().isWithinBounds(swept)
                && level.noCollision(this, swept);
    }

    private void beginRecovery() {
        this.entityData.set(DATA_MODE, ManifestationMode.RECOVERY.ordinal());
        this.entityData.set(DATA_PHASE_TICKS, 0);
        this.setNoGravity(false);
        this.noPhysics = false;
        this.setDeltaMovement(Vec3.ZERO);
    }

    private void tickRecovery(ServerLevel level, ServerPlayer player) {
        int elapsed = this.entityData.get(DATA_PHASE_TICKS) + 1;
        this.entityData.set(DATA_PHASE_TICKS, elapsed);
        this.faceTowards(player, 0.18f);
        Vec3 step = this.lungeDirection.scale(-0.075 * Math.max(0.0, 1.0 - elapsed / 14.0));
        BlockPos feet = BlockPos.containing(this.position().add(step));
        if (canMoveThrough(level, step) && level.getBlockState(feet.below()).isSolidRender()
                && level.getFluidState(feet).isEmpty()) this.setPos(this.position().add(step));
        if (elapsed >= OriginelTiming.RECOVERY_TICKS) this.discard();
    }

    private ServerPlayer resolveOwner(ServerLevel level) {
        if (this.ownerUuid == null) {
            return null;
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(this.ownerUuid);
        return owner != null && owner.level() == level ? owner : null;
    }

    private boolean isPreciselyStaredAtBy(ServerLevel level, ServerPlayer player) {
        if (player.isSleeping()) {
            return false;
        }
        Vec3 playerEye = player.getEyePosition();
        Vec3 entityEye = this.getEyePosition();
        Vec3 toOriginel = entityEye.subtract(playerEye);
        if (toOriginel.lengthSqr() < 1.0E-4) {
            return false;
        }
        if (player.getViewVector(1.0f).normalize().dot(toOriginel.normalize())
                < STARE_DOT_THRESHOLD) {
            return false;
        }
        BlockHitResult hit = level.clip(new ClipContext(playerEye, entityEye,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS;
    }

    private void faceTowards(ServerPlayer player, float turnFactor) {
        Vec3 delta = player.getEyePosition().subtract(this.getEyePosition());
        float targetYaw = (float) (Math.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0f;
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float targetPitch = (float) (-Math.atan2(delta.y, horizontal) * Mth.RAD_TO_DEG);
        float bodyYaw = Mth.rotLerp(turnFactor, this.getYRot(), targetYaw);
        float headFactor = Math.min(1.0f, turnFactor * 2.5f);
        float headYaw = Mth.rotLerp(headFactor, this.getYHeadRot(), targetYaw);
        this.setYRot(bodyYaw);
        this.setYBodyRot(bodyYaw);
        this.setYHeadRot(headYaw);
        this.setXRot(Mth.rotLerp(headFactor, this.getXRot(), targetPitch));
    }

    private void faceAwayFrom(ServerPlayer player) {
        Vec3 delta = this.position().subtract(player.position());
        float awayYaw = (float) (Math.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0f;
        this.setYRot(Mth.rotLerp(DISTANT_TURN_FACTOR, this.getYRot(), awayYaw));
        this.setYBodyRot(this.getYRot());
        this.setYHeadRot(this.getYRot());
        this.setXRot(0.0f);
    }

    @Override
    public void onRemoval(Entity.RemovalReason reason) {
        if (!this.level().isClientSide()) {
            OriginelManager.release(this.ownerUuid, this.getUUID());
        }
        this.restoreTemporaryTorch();
        super.onRemoval(reason);
    }

    /**
     * Retire la torche uniquement si son état est encore strictement celui que
     * la scène a posé. Une torche cassée ou modifiée par un joueur est respectée.
     */
    private void restoreTemporaryTorch() {
        if (this.restoringTorch || this.temporaryTorchPos == null
                || this.temporaryTorchOriginal == null || this.temporaryTorchPlaced == null
                || !(this.level() instanceof ServerLevel level)
                || !level.isLoaded(this.temporaryTorchPos)) {
            return;
        }
        this.restoringTorch = true;
        if (level.getBlockState(this.temporaryTorchPos).equals(this.temporaryTorchPlaced)) {
            level.setBlock(this.temporaryTorchPos, this.temporaryTorchOriginal, Block.UPDATE_ALL);
        }
        this.temporaryTorchPos = null;
        this.temporaryTorchOriginal = null;
        this.temporaryTorchPlaced = null;
    }

    public static boolean hasAnyOriginelNearby(ServerLevel level, BlockPos pos, double radius) {
        return !level.getEntitiesOfClass(OriginelEntity.class, new AABB(pos).inflate(radius)).isEmpty();
    }

    public static boolean hasAnyOriginelNearby(ServerLevel level, BlockPos pos, double radius,
                                               ServerPlayer owner) {
        return !level.getEntitiesOfClass(OriginelEntity.class, new AABB(pos).inflate(radius),
                entity -> entity.isOwnedBy(owner)).isEmpty();
    }
}

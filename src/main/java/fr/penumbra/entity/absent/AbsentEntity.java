package fr.penumbra.entity.absent;

import com.mojang.authlib.GameProfile;
import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.network.PenumbraC2S;
import fr.penumbra.network.PenumbraNetworking;
import fr.penumbra.perception.PerceptionRules;
import fr.penumbra.registry.PenumbraParticles;
import fr.penumbra.registry.PenumbraSounds;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * L'Absent vole une identite de joueur complete avant de laisser sa demarche et
 * son comportement se desynchroniser. Le serveur ne synchronise qu'un profil,
 * une phase, une graine et une petite signature de marche ; les articulations
 * restent entierement procedurales cote client.
 */
public final class AbsentEntity extends Monster {

    public static final int PHASE_IMITATE = AbsentMimicLogic.IMITATE;
    /** Alias conserve pour les renderers et commandes des versions precedentes. */
    public static final int PHASE_WATCH = PHASE_IMITATE;
    public static final int PHASE_LURE = AbsentMimicLogic.LURE;
    public static final int PHASE_SUSPICION = AbsentMimicLogic.SUSPICION;
    public static final int PHASE_REVEAL = AbsentMimicLogic.REVEAL;
    public static final int PHASE_HUNT = AbsentMimicLogic.HUNT;

    private static final UUID FALLBACK_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000001");
    private static final GameProfile FALLBACK_PROFILE =
            new GameProfile(FALLBACK_ID, "Absent");

    private static final EntityDataAccessor<Integer> DATA_PHASE =
            SynchedEntityData.defineId(AbsentEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<ResolvableProfile> DATA_IMITATED_PROFILE =
            SynchedEntityData.defineId(AbsentEntity.class,
                    EntityDataSerializers.RESOLVABLE_PROFILE);
    private static final EntityDataAccessor<String> DATA_IMITATED_UUID =
            SynchedEntityData.defineId(AbsentEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Long> DATA_LOCOMOTION_SEED =
            SynchedEntityData.defineId(AbsentEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> DATA_GAIT_SIGNATURE =
            SynchedEntityData.defineId(AbsentEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_IMITATION_ERROR =
            SynchedEntityData.defineId(AbsentEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_REVEAL_PROGRESS =
            SynchedEntityData.defineId(AbsentEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_MODEL_PARTS =
            SynchedEntityData.defineId(AbsentEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_LUNGING =
            SynchedEntityData.defineId(AbsentEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_ATTACK_TICK =
            SynchedEntityData.defineId(AbsentEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_REVEAL_TICK =
            SynchedEntityData.defineId(AbsentEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FOLLOW_THROUGH =
            SynchedEntityData.defineId(AbsentEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_CONTACT_POSE =
            SynchedEntityData.defineId(AbsentEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_CONFRONTATION =
            SynchedEntityData.defineId(AbsentEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_SCENE_MODE =
            SynchedEntityData.defineId(AbsentEntity.class, EntityDataSerializers.INT);

    private static final List<EquipmentSlot> IMITATED_EQUIPMENT = List.of(
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST,
            EquipmentSlot.LEGS, EquipmentSlot.FEET);

    private static final double TRACK_RADIUS = 72.0;
    private static final int IMITATE_TICKS = 72;
    private static final int MIN_LURE_TICKS = 45;
    private static final int SUSPICION_TICKS = 42;
    private static final int REVEAL_TICKS = 30;
    private static final int HUNT_TIMEOUT_TICKS = 240;
    private static final int MAX_LIFETIME_TICKS = 20 * 85;
    private static final double FOLLOW_DISTANCE = 8.5;
    private static final double VANISH_DISTANCE = 80.0;
    private static final double DUPLICATE_EXPOSURE_DISTANCE = 25.0;
    private static final float SKIN_BREAK_THRESHOLD = 0.58f;
    private static final int ALL_MODEL_PARTS = 0x7F;
    private static final int FLASH_STUN_RECOVERY_TICKS = 40;

    private UUID ownerUuid;
    private int lifetimeTicks;
    private int phaseTicks;
    private int clientVisualPhase = -1;
    private int clientVisualPhaseTicks;
    private int lureMessageIndex;
    private int followTicks;
    private int stareTicks;
    private int forcedRevealTicks;
    private int cameraStunTicks;
    private int flashStunRecoveryTicks;
    private int ashBanishTicks;
    private int lungeTicks;
    private int lungeCooldownTicks;
    private float learnedSpeed;
    private float learnedTurn;
    private float lastImitatedYaw;
    private boolean hasImitatedYaw;
    private Vec3 lastImitatedPosition = Vec3.ZERO;
    private UUID lastMovementSourceUuid;
    private boolean hasImitatedPosition;
    private int perceptionLifetimeTicks = PerceptionRules.MAX_LIFETIME_TICKS;
    private int perceptionFocusTicks;
    private boolean perceptionTurned;
    private boolean confrontationDouble;

    public AbsentEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        this.setCanPickUpLoot(false);
    }

    public static AttributeSupplier.Builder createAbsentAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 24.0)
                .add(Attributes.MOVEMENT_SPEED, 0.30)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.FOLLOW_RANGE, TRACK_RADIUS)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.45);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PHASE, PHASE_IMITATE);
        builder.define(DATA_IMITATED_PROFILE,
                ResolvableProfile.createResolved(FALLBACK_PROFILE));
        builder.define(DATA_IMITATED_UUID, FALLBACK_ID.toString());
        builder.define(DATA_LOCOMOTION_SEED, 0L);
        builder.define(DATA_GAIT_SIGNATURE, 0);
        builder.define(DATA_IMITATION_ERROR, AbsentMimicLogic.baseError(PHASE_IMITATE));
        builder.define(DATA_REVEAL_PROGRESS, 0.0f);
        builder.define(DATA_MODEL_PARTS, ALL_MODEL_PARTS);
        builder.define(DATA_LUNGING, false);
        builder.define(DATA_ATTACK_TICK, 0);
        builder.define(DATA_REVEAL_TICK, 0);
        builder.define(DATA_FOLLOW_THROUGH, 0);
        builder.define(DATA_CONTACT_POSE, 0.0f);
        builder.define(DATA_CONFRONTATION, false);
        builder.define(DATA_SCENE_MODE, AbsentSceneMode.NORMAL.ordinal());
    }

    @Override
    protected void registerGoals() {
        // Machine d'etats volontairement fermee : aucun goal ne peut changer de cible.
    }

    @Override
    public void checkDespawn() {
        // Toutes les sorties sont explicites et bornees.
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    public int getAbsentPhase() {
        return this.entityData.get(DATA_PHASE);
    }

    /** Nom stable utilise par le nouveau renderer. */
    public int getMimicBehavior() {
        return this.getAbsentPhase();
    }

    public ResolvableProfile getImitatedProfile() {
        return this.entityData.get(DATA_IMITATED_PROFILE);
    }

    public UUID getImitatedUuid() {
        try {
            return UUID.fromString(this.entityData.get(DATA_IMITATED_UUID));
        } catch (IllegalArgumentException ignored) {
            return FALLBACK_ID;
        }
    }

    public String getImitatedName() {
        return this.getImitatedProfile().name().orElseGet(() -> {
            Component name = this.getCustomName();
            return name == null ? FALLBACK_PROFILE.name() : name.getString();
        });
    }

    public long getLocomotionSeed() {
        return this.entityData.get(DATA_LOCOMOTION_SEED);
    }

    /** Signature compacte : vitesse, rotation, sprint, accroupissement et saut. */
    public int getGaitSignature() {
        return this.entityData.get(DATA_GAIT_SIGNATURE);
    }

    public float getImitationError() {
        return this.entityData.get(DATA_IMITATION_ERROR);
    }

    public float getRevealProgress() {
        return this.entityData.get(DATA_REVEAL_PROGRESS);
    }

    public int getModelPartsMask() {
        return this.entityData.get(DATA_MODEL_PARTS);
    }

    public boolean isImitatedModelPartShown(PlayerModelPart part) {
        return part != null && (this.getModelPartsMask() & part.getMask()) != 0;
    }

    public boolean isLunging() {
        return this.entityData.get(DATA_LUNGING);
    }

    public float getAttackProgress(float partialTick) {
        return AbsentAttack.progress(this.entityData.get(DATA_ATTACK_TICK),
                partialTick, AbsentAttack.LUNGE_TICKS);
    }
    public float getRevealAnimationProgress(float partialTick) {
        return AbsentAttack.progress(this.entityData.get(DATA_REVEAL_TICK), partialTick, REVEAL_TICKS);
    }
    public boolean isFollowingThrough() { return this.entityData.get(DATA_FOLLOW_THROUGH) > 0; }
    public float getFollowThroughProgress(float partialTick) {
        return AbsentAttack.progress(this.entityData.get(DATA_FOLLOW_THROUGH),
                partialTick, AbsentAttack.FOLLOW_THROUGH_TICKS);
    }
    public float getContactPose() { return this.entityData.get(DATA_CONTACT_POSE); }

    private void clearAttackVisuals() {
        this.entityData.set(DATA_LUNGING, false);
        this.entityData.set(DATA_ATTACK_TICK, 0);
        this.entityData.set(DATA_REVEAL_TICK, 0);
        this.entityData.set(DATA_FOLLOW_THROUGH, 0);
        this.lungeTicks = 0;
    }

    public AbsentSceneMode getSceneMode() {
        return AbsentSceneMode.byId(this.entityData.get(DATA_SCENE_MODE));
    }

    public boolean isFalseCompanion() {
        return this.getSceneMode() == AbsentSceneMode.FALSE_COMPANION;
    }

    /**
     * Transforme l'entité, avant son ajout au niveau, en perception strictement
     * individuelle. Elle conserve le skin et la démarche copiés mais ne peut
     * jamais atteindre les phases agressives de L'Absent.
     */
    public void configureFalseCompanion(int lifetimeTicks) {
        this.clearAttackVisuals();
        this.entityData.set(DATA_SCENE_MODE, AbsentSceneMode.FALSE_COMPANION.ordinal());
        this.perceptionLifetimeTicks = Mth.clamp(lifetimeTicks,
                PerceptionRules.MIN_LIFETIME_TICKS,
                PerceptionRules.MAX_LIFETIME_TICKS);
        this.entityData.set(DATA_PHASE, PHASE_LURE);
        this.entityData.set(DATA_IMITATION_ERROR, 0.035f);
        this.entityData.set(DATA_REVEAL_PROGRESS, 0.0f);
        this.entityData.set(DATA_LUNGING, false);
        this.phaseTicks = 0;
        this.setTarget(null);
        this.setCanPickUpLoot(false);
        this.setNoGravity(false);
        this.setDeltaMovement(Vec3.ZERO);
        // Coupe les sons vanilla avant le premier paquet de tracking. Les seuls
        // sons de cette scène passent ensuite par un payload au propriétaire.
        this.setSilent(true);
    }

    public int getRemainingPerceptionTicks() {
        return this.isFalseCompanion()
                ? Math.max(0, this.perceptionLifetimeTicks - this.lifetimeTicks) : 0;
    }

    public float getVisualPhaseTicks(float partialTick) {
        return this.clientVisualPhase < 0 ? 0.0f
                : this.clientVisualPhaseTicks + Mth.clamp(partialTick, 0.0f, 1.0f);
    }

    public boolean isRevealed() {
        return this.getAbsentPhase() >= PHASE_REVEAL || this.getRevealProgress() >= 0.88f;
    }

    /** Lie la scene au temoin et copie le profil ainsi que l'equipement visible. */
    public void bindTo(ServerPlayer owner, ServerPlayer imitatedPlayer) {
        this.bindTo(owner, imitatedPlayer.getGameProfile());
        this.snapshotEquipment(imitatedPlayer);
        this.setLeftHanded(imitatedPlayer.getMainArm() == HumanoidArm.LEFT);
        this.entityData.set(DATA_MODEL_PARTS,
                imitatedPlayer.clientInformation().modelCustomisation() & ALL_MODEL_PARTS);
        this.lastImitatedYaw = imitatedPlayer.getYRot();
        this.hasImitatedYaw = true;
        this.lastImitatedPosition = imitatedPlayer.position();
        this.lastMovementSourceUuid = imitatedPlayer.getUUID();
        this.hasImitatedPosition = true;
    }

    /** Lie un profil complet, notamment un profil texture fourni par le serveur. */
    public void bindTo(ServerPlayer owner, GameProfile imitatedProfile) {
        this.ownerUuid = owner.getUUID();
        this.entityData.set(DATA_IMITATED_PROFILE,
                ResolvableProfile.createResolved(imitatedProfile));
        this.entityData.set(DATA_IMITATED_UUID, imitatedProfile.id().toString());
        this.entityData.set(DATA_LOCOMOTION_SEED,
                owner.getRandom().nextLong() ^ this.getUUID().getMostSignificantBits());
        this.setCustomName(Component.literal(imitatedProfile.name()));
        this.setCustomNameVisible(true);
        this.getLookControl().setLookAt(owner, 180.0f, 180.0f);
    }

    /**
     * Compatibilite des scenes/commandes qui ne fournissent encore qu'un nom.
     * Un joueur connecte est resolu en vrai profil ; sinon un UUID synthetique
     * stable conserve l'ancien comportement sans usurper un UUID reel.
     */
    public void bindTo(ServerPlayer owner, String imitatedName) {
        ServerPlayer online = ((ServerLevel) owner.level()).getServer()
                .getPlayerList().getPlayers().stream()
                .filter(player -> player.getGameProfile().name()
                        .equalsIgnoreCase(imitatedName))
                .findFirst()
                .orElse(null);
        if (online != null) {
            this.bindTo(owner, online);
            return;
        }
        UUID synthetic = UUID.nameUUIDFromBytes(
                ("PenumbraAbsent:" + imitatedName).getBytes(StandardCharsets.UTF_8));
        this.bindTo(owner, new GameProfile(synthetic, imitatedName));
    }

    public boolean isOwnedBy(ServerPlayer player) {
        return this.ownerUuid != null && this.ownerUuid.equals(player.getUUID());
    }

    /** Transient finale actor: the story manager owns movement and its lifetime. */
    public void configureConfrontation() {
        this.confrontationDouble = true;
        this.entityData.set(DATA_CONFRONTATION, true);
        this.clearAttackVisuals();
        this.setNoAi(true);
        this.setInvulnerable(true);
        this.setSilent(true);
        this.setTarget(null);
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        this.setConfrontationExposure(false, 0);
    }

    public boolean isConfrontationDouble() {
        return this.entityData.get(DATA_CONFRONTATION);
    }

    /** Uses existing synchronized visual channels; no screen flash is required. */
    public void setConfrontationExposure(boolean exposed, int fragments) {
        if (!this.confrontationDouble) return;
        int phase = exposed ? PHASE_REVEAL : PHASE_IMITATE;
        if (this.getAbsentPhase() != phase) this.setPhase(phase);
        this.entityData.set(DATA_LUNGING, false);
        this.entityData.set(DATA_REVEAL_PROGRESS, exposed ? 0.9f : 0.10f * fragments);
        this.entityData.set(DATA_IMITATION_ERROR, exposed ? 0.85f : 0.08f + 0.14f * fragments);
    }

    @Override
    public boolean broadcastToPlayer(ServerPlayer player) {
        return PerceptionRules.mayObserve(this.ownerUuid, player.getUUID(),
                this.isFalseCompanion()) && super.broadcastToPlayer(player);
    }

    @Override
    public boolean isPushable() {
        return !this.confrontationDouble && !this.isFalseCompanion() && super.isPushable();
    }

    @Override
    public boolean isPickable() {
        return !this.isFalseCompanion() && super.isPickable();
    }

    @Override
    public boolean canBeHitByProjectile() {
        return !this.isFalseCompanion() && super.canBeHitByProjectile();
    }

    @Override
    public boolean isAttackable() {
        return !this.isFalseCompanion() && super.isAttackable();
    }

    @Override
    public boolean attackable() {
        return !this.isFalseCompanion() && super.attackable();
    }

    @Override
    public boolean canCollideWith(Entity other) {
        return !this.isFalseCompanion() && super.canCollideWith(other);
    }

    @Override
    public boolean canBeCollidedWith(Entity other) {
        return !this.isFalseCompanion() && super.canBeCollidedWith(other);
    }

    @Override
    public boolean isPreventingPlayerRest(ServerLevel level, Player player) {
        return !this.confrontationDouble && !this.isFalseCompanion()
                && super.isPreventingPlayerRest(level, player);
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return this.isFalseCompanion() || super.isIgnoringBlockTriggers();
    }

    @Override
    public boolean dampensVibrations() {
        return this.isFalseCompanion() || super.dampensVibrations();
    }

    @Override
    public boolean canBeSeenAsEnemy() {
        return !this.isFalseCompanion() && super.canBeSeenAsEnemy();
    }

    private void snapshotEquipment(ServerPlayer source) {
        for (EquipmentSlot slot : IMITATED_EQUIPMENT) {
            ItemStack copy = source.getItemBySlot(slot).copy();
            this.setItemSlot(slot, copy);
            this.setDropChance(slot, 0.0f);
        }
    }

    private void clearImitatedEquipment() {
        for (EquipmentSlot slot : IMITATED_EQUIPMENT) {
            this.setItemSlot(slot, ItemStack.EMPTY);
            this.setDropChance(slot, 0.0f);
        }
    }

    private ServerPlayer owner(ServerLevel level) {
        if (this.ownerUuid == null) {
            return null;
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(this.ownerUuid);
        return owner != null && owner.level() == level ? owner : null;
    }

    private ServerPlayer imitatedPlayer(ServerLevel level) {
        ServerPlayer imitated = level.getServer().getPlayerList().getPlayer(this.getImitatedUuid());
        return imitated != null && imitated.level() == level ? imitated : null;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.clearAttackVisuals();
        this.lifetimeTicks = Math.max(0, input.getIntOr("PenumbraLifetime", 0));
        this.phaseTicks = Math.max(0, input.getIntOr("PenumbraPhaseTicks", 0));
        this.lureMessageIndex = Mth.clamp(input.getIntOr("PenumbraLureMessage", 0), 0, 3);
        this.followTicks = Math.max(0, input.getIntOr("PenumbraFollowTicks", 0));
        this.stareTicks = Math.max(0, input.getIntOr("PenumbraStareTicks", 0));
        this.entityData.set(DATA_PHASE,
                Mth.clamp(input.getIntOr("PenumbraPhase", PHASE_IMITATE),
                        PHASE_IMITATE, PHASE_HUNT));
        String owner = input.getStringOr("PenumbraOwner", "");
        if (!owner.isBlank()) {
            try {
                this.ownerUuid = UUID.fromString(owner);
            } catch (IllegalArgumentException ignored) {
                this.ownerUuid = null;
            }
        }
        String imitatedId = input.getStringOr("PenumbraImitatedUuid", FALLBACK_ID.toString());
        String imitatedName = input.getStringOr("PenumbraImitatedName", FALLBACK_PROFILE.name());
        try {
            UUID uuid = UUID.fromString(imitatedId);
            this.entityData.set(DATA_IMITATED_UUID, uuid.toString());
            this.entityData.set(DATA_IMITATED_PROFILE,
                    ResolvableProfile.createResolved(new GameProfile(uuid, imitatedName)));
        } catch (IllegalArgumentException ignored) {
            this.entityData.set(DATA_IMITATED_UUID, FALLBACK_ID.toString());
            this.entityData.set(DATA_IMITATED_PROFILE,
                    ResolvableProfile.createResolved(FALLBACK_PROFILE));
        }
        this.entityData.set(DATA_LOCOMOTION_SEED,
                input.getLongOr("PenumbraLocomotionSeed", 0L));
        this.entityData.set(DATA_GAIT_SIGNATURE,
                input.getIntOr("PenumbraGait", 0));
        this.entityData.set(DATA_IMITATION_ERROR,
                Mth.clamp(input.getFloatOr("PenumbraImitationError", 0.0f), 0.0f, 1.0f));
        this.entityData.set(DATA_REVEAL_PROGRESS,
                Mth.clamp(input.getFloatOr("PenumbraReveal", 0.0f), 0.0f, 1.0f));
        this.entityData.set(DATA_MODEL_PARTS,
                input.getIntOr("PenumbraModelParts", ALL_MODEL_PARTS) & ALL_MODEL_PARTS);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("PenumbraLifetime", this.lifetimeTicks);
        output.putInt("PenumbraPhaseTicks", this.phaseTicks);
        output.putInt("PenumbraLureMessage", this.lureMessageIndex);
        output.putInt("PenumbraFollowTicks", this.followTicks);
        output.putInt("PenumbraStareTicks", this.stareTicks);
        output.putInt("PenumbraPhase", this.getAbsentPhase());
        output.putString("PenumbraImitatedUuid", this.getImitatedUuid().toString());
        output.putString("PenumbraImitatedName", this.getImitatedName());
        output.putLong("PenumbraLocomotionSeed", this.getLocomotionSeed());
        output.putInt("PenumbraGait", this.getGaitSignature());
        output.putFloat("PenumbraImitationError", this.getImitationError());
        output.putFloat("PenumbraReveal", this.getRevealProgress());
        output.putInt("PenumbraModelParts", this.getModelPartsMask());
        if (this.ownerUuid != null) {
            output.putString("PenumbraOwner", this.ownerUuid.toString());
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            int phase = this.getAbsentPhase();
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
        PenumbraConfig config = PenumbraConfig.get();
        if (!this.isAlive() || this.isRemoved()) {
            this.clearAttackVisuals();
            this.getNavigation().stop();
            this.setDeltaMovement(Vec3.ZERO);
            if (!this.isRemoved()) this.discard();
            return;
        }
        boolean featureEnabled = this.isFalseCompanion()
                ? config.individualPerceptionsEnabled && config.falseCompanionsEnabled
                : config.absentEnabled;
        // The finale lasts up to 120 s; the ordinary 85 s TTL must not cut it off.
        // Keep a separate fail-safe in case its manager is ever interrupted.
        int lifetimeLimit = this.confrontationDouble ? 2600 : this.isFalseCompanion()
                ? this.perceptionLifetimeTicks : MAX_LIFETIME_TICKS;
        if (!featureEnabled
                || level.getDifficulty() == Difficulty.PEACEFUL
                || ++this.lifetimeTicks > lifetimeLimit) {
            this.vanish(level, false);
            return;
        }

        if (this.ashBanishTicks > 0) {
            this.tickAshBanishment(level);
            return;
        }

        ServerPlayer owner = this.owner(level);
        if (owner == null || !owner.isAlive() || owner.isSpectator()
                || owner.isRemoved() || owner.isSleeping()
                || this.distanceToSqr(owner) > VANISH_DISTANCE * VANISH_DISTANCE) {
            this.vanish(level, false);
            return;
        }

        if (this.confrontationDouble) {
            this.getNavigation().stop();
            this.setTarget(null);
            this.setDeltaMovement(Vec3.ZERO);
            return;
        }

        ServerPlayer imitated = this.imitatedPlayer(level);
        if (imitated == null || !imitated.isAlive() || imitated.isSpectator()) {
            imitated = owner;
        }
        this.learnMovement(imitated);
        if (this.isFalseCompanion()) {
            this.tickFalseCompanion(level, owner, imitated);
            return;
        }
        this.updateAttention(owner);
        this.updateVisualTruth(owner);

        this.phaseTicks++;
        if (this.flashStunRecoveryTicks > 0) {
            this.flashStunRecoveryTicks--;
        }
        if (this.cameraStunTicks > 0) {
            this.cameraStunTicks--;
            this.getNavigation().stop();
            this.setDeltaMovement(Vec3.ZERO);
            return;
        }

        switch (this.getAbsentPhase()) {
            case PHASE_IMITATE -> this.tickImitate(level, owner, imitated);
            case PHASE_LURE -> this.tickLure(level, owner);
            case PHASE_SUSPICION -> this.tickSuspicion(level, owner);
            case PHASE_REVEAL -> this.tickReveal(level, owner);
            case PHASE_HUNT -> this.tickHunt(level, owner);
            default -> this.setPhase(PHASE_IMITATE);
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (this.confrontationDouble) return false;
        if (this.isFalseCompanion()) {
            // Une explosion, une flèche ou un témoin qui ne voit pas la scène ne
            // doit ni la révéler ni la faire réagir.
            return false;
        }
        if (this.ashBanishTicks > 0) {
            return false;
        }
        if (this.getAbsentPhase() < PHASE_REVEAL) {
            this.setPhase(PHASE_REVEAL);
            this.getNavigation().stop();
            return false;
        }
        return super.hurtServer(level, source, amount);
    }

    private void learnMovement(ServerPlayer source) {
        if (!PenumbraConfig.get().absentMovementImitationEnabled) {
            if (this.getGaitSignature() != 0) {
                this.entityData.set(DATA_GAIT_SIGNATURE, 0);
            }
            return;
        }
        UUID sourceUuid = source.getUUID();
        Vec3 currentPosition = source.position();
        boolean sameSource = sourceUuid.equals(this.lastMovementSourceUuid);
        Vec3 travelled = sameSource && this.hasImitatedPosition
                ? currentPosition.subtract(this.lastImitatedPosition) : Vec3.ZERO;
        double horizontalDistance = Math.sqrt(
                travelled.x * travelled.x + travelled.z * travelled.z);
        // Une téléportation ou un changement de source n'est jamais appris comme une foulée.
        double speed = horizontalDistance <= 1.25 ? horizontalDistance : 0.0;
        this.lastImitatedPosition = currentPosition;
        this.lastMovementSourceUuid = sourceUuid;
        this.hasImitatedPosition = true;
        float yaw = source.getYRot();
        float turn = sameSource && this.hasImitatedYaw
                ? Math.abs(Mth.wrapDegrees(yaw - this.lastImitatedYaw)) : 0.0f;
        this.lastImitatedYaw = yaw;
        this.hasImitatedYaw = true;
        this.learnedSpeed += ((float) speed - this.learnedSpeed) * 0.12f;
        this.learnedTurn += (turn - this.learnedTurn) * 0.12f;
        if (this.lifetimeTicks % 5 == 0) {
            int gait = AbsentMimicLogic.packGait(
                    this.learnedSpeed, this.learnedTurn,
                    source.isSprinting(), source.isShiftKeyDown(), !source.onGround());
            if (gait != this.getGaitSignature()) {
                this.entityData.set(DATA_GAIT_SIGNATURE, gait);
            }
        }
    }

    /** Comportement borné du faux compagnon : marcher, remarquer le regard, disparaître. */
    private void tickFalseCompanion(ServerLevel level, ServerPlayer owner,
                                    ServerPlayer imitated) {
        // Garde explicite contre un NoGravity injecté par une commande ou un
        // autre mod : cette copie doit toujours employer la physique au sol.
        if (this.isNoGravity()) {
            this.setNoGravity(false);
        }
        this.phaseTicks++;
        this.entityData.set(DATA_REVEAL_PROGRESS, 0.0f);
        this.entityData.set(DATA_IMITATION_ERROR,
                approach(this.getImitationError(), 0.04f, 0.002f));
        this.entityData.set(DATA_LUNGING, false);
        this.setTarget(null);

        Vec3 toEcho = this.getEyePosition().subtract(owner.getEyePosition());
        double distanceSquared = toEcho.lengthSqr();
        boolean watched = distanceSquared > 0.01
                && distanceSquared <= 42.0 * 42.0
                && owner.getLookAngle().normalize().dot(toEcho.normalize()) >= 0.965
                && owner.hasLineOfSight(this);
        boolean cameraCheck = watched && PenumbraC2S.isZooming(owner.getUUID());
        this.perceptionFocusTicks = PerceptionRules.nextFocusTicks(
                this.perceptionFocusTicks, watched, cameraCheck);
        PerceptionRules.Verification verification = PerceptionRules.verification(
                this.perceptionFocusTicks, false);
        if (verification == PerceptionRules.Verification.DISMISS) {
            owner.sendOverlayMessage(Component.translatable(
                    "message.penumbra.perception.checked"));
            this.vanish(level, true);
            return;
        }
        if (verification == PerceptionRules.Verification.NOTICED) {
            this.entityData.set(DATA_PHASE, PHASE_SUSPICION);
            if (!this.perceptionTurned) {
                PenumbraNetworking.sendSoundDirect(owner,
                        PenumbraSounds.whisperVariant(this.random),
                        this.position(), 0.30f, 0.68f);
            }
            this.perceptionTurned = true;
            this.getNavigation().stop();
            this.setDeltaMovement(0.0, this.getDeltaMovement().y, 0.0);
            this.setSprinting(false);
            this.setShiftKeyDown(false);
            this.getLookControl().setLookAt(owner, 35.0f, 30.0f);
            return;
        }

        this.perceptionTurned = false;
        this.entityData.set(DATA_PHASE, PHASE_LURE);
        boolean imitateMovement = PenumbraConfig.get().absentMovementImitationEnabled;
        this.setSprinting(imitateMovement && imitated.isSprinting()
                && this.phaseTicks % 70 < 32);
        this.setShiftKeyDown(imitateMovement && imitated.isShiftKeyDown()
                && this.phaseTicks % 64 < 18);
        if (this.getNavigation().isDone() || this.phaseTicks % 54 == 1) {
            Vec3 forward = owner.getLookAngle().multiply(1.0, 0.0, 1.0);
            if (forward.lengthSqr() < 1.0E-5) {
                forward = new Vec3(0.0, 0.0, 1.0);
            } else {
                forward = forward.normalize();
            }
            Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
            if ((this.getLocomotionSeed() & 1L) == 0L) {
                side = side.scale(-1.0);
            }
            Vec3 destination = owner.position().add(side.scale(5.5))
                    .add(forward.scale(2.5));
            this.getNavigation().moveTo(destination.x, destination.y, destination.z,
                    Mth.clamp(0.72 + this.learnedSpeed * 0.75, 0.72, 1.04));
        }
        if (this.phaseTicks == 58 || this.phaseTicks == 126) {
            this.performPrivateFakeTask(level, owner);
        }
    }

    /** Geste et son envoyés uniquement au joueur qui hallucine. */
    private void performPrivateFakeTask(ServerLevel level, ServerPlayer owner) {
        BlockPos origin = this.blockPosition();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos target = origin.relative(direction);
            BlockState state = level.getBlockState(target);
            if (state.isAir() || !state.isSolidRender()) {
                continue;
            }
            Vec3 center = Vec3.atCenterOf(target);
            this.getLookControl().setLookAt(center.x, center.y, center.z, 25.0f, 25.0f);
            this.swing(InteractionHand.MAIN_HAND);
            PenumbraNetworking.sendSoundDirect(owner,
                    state.getSoundType().getHitSound(), center, 0.20f, 0.82f);
            return;
        }
        this.swing(InteractionHand.MAIN_HAND);
    }

    private void updateAttention(ServerPlayer owner) {
        Vec3 toAbsent = this.getEyePosition().subtract(owner.getEyePosition());
        double distanceSquared = toAbsent.lengthSqr();
        boolean watched = false;
        if (distanceSquared > 0.01 && distanceSquared <= 38.0 * 38.0) {
            double dot = owner.getLookAngle().normalize().dot(toAbsent.normalize());
            watched = dot >= 0.972 && owner.hasLineOfSight(this);
        }
        this.stareTicks = AbsentMimicLogic.nextStareTicks(this.stareTicks, watched);
    }

    private void updateVisualTruth(ServerPlayer owner) {
        float errorTarget = AbsentMimicLogic.baseError(this.getAbsentPhase());
        errorTarget += Math.min(0.24f, this.stareTicks / 80.0f * 0.24f);
        double distance = this.distanceTo(owner);
        if (distance < 12.0) {
            errorTarget += (float) ((12.0 - distance) / 12.0 * 0.18);
        }
        errorTarget = Mth.clamp(errorTarget, 0.0f, 1.0f);
        float currentError = this.getImitationError();
        float errorStep = errorTarget > currentError ? 0.025f : 0.008f;
        this.entityData.set(DATA_IMITATION_ERROR,
                approach(currentError, errorTarget, errorStep));

        float revealTarget = switch (this.getAbsentPhase()) {
            case PHASE_SUSPICION -> 0.18f
                    + Math.min(0.58f, this.phaseTicks / (float) SUSPICION_TICKS * 0.58f);
            case PHASE_REVEAL, PHASE_HUNT -> 1.0f;
            default -> 0.0f;
        };
        if (this.forcedRevealTicks > 0) {
            revealTarget = Math.max(revealTarget, this.forcedRevealTicks > 12 ? 0.92f : 0.68f);
            this.forcedRevealTicks--;
        }
        float currentReveal = this.getRevealProgress();
        float revealStep = revealTarget > currentReveal ? 0.11f : 0.035f;
        float nextReveal = approach(currentReveal, revealTarget, revealStep);
        this.entityData.set(DATA_REVEAL_PROGRESS, nextReveal);
        if (nextReveal >= SKIN_BREAK_THRESHOLD) {
            this.clearImitatedEquipment();
        }
    }

    private void tickImitate(ServerLevel level, ServerPlayer owner, ServerPlayer imitated) {
        boolean movementImitation = PenumbraConfig.get().absentMovementImitationEnabled;
        this.setSprinting(false);
        this.setShiftKeyDown(movementImitation && imitated.isShiftKeyDown()
                && this.phaseTicks % 48 < 16);

        if (movementImitation && this.phaseTicks > 24 && this.phaseTicks % 36 == 8) {
            Vec3 lateral = owner.getLookAngle().multiply(1.0, 0.0, 1.0);
            if (lateral.lengthSqr() > 1.0E-4) {
                lateral = new Vec3(-lateral.z, 0.0, lateral.x).normalize();
                if ((this.getLocomotionSeed() & 1L) == 0L) {
                    lateral = lateral.scale(-1.0);
                }
                Vec3 destination = this.position().add(lateral.scale(1.4));
                this.getNavigation().moveTo(destination.x, destination.y,
                        destination.z, 0.55 + this.learnedSpeed * 0.6);
            }
        } else if (!movementImitation || this.phaseTicks < 22) {
            this.getNavigation().stop();
            this.setDeltaMovement(Vec3.ZERO);
        }

        // Le regard arrive en retard, comme une imitation apprise plutot qu'un reflexe.
        if (this.phaseTicks % 41 >= 13) {
            this.getLookControl().setLookAt(owner, 10.0f, 8.0f);
        }
        if (this.phaseTicks == 28) {
            this.sendLureMessage(owner);
        }
        if (movementImitation && this.phaseTicks == 38) {
            this.performFakeMining(level);
        }
        if (movementImitation && this.phaseTicks == 58) {
            this.performFakeDoorInteraction(level);
        }
        if (this.duplicateExposed(owner)
                || (this.stareTicks >= 48 && this.phaseTicks >= 36)) {
            this.setPhase(PHASE_SUSPICION);
            return;
        }
        if (this.phaseTicks >= IMITATE_TICKS) {
            this.setPhase(PHASE_LURE);
            this.sendLureMessage(owner);
        }
    }

    /**
     * Rejoue un geste de minage sans jamais casser ni fissurer le bloc. Le son
     * vient du matériau réellement visé et reste donc crédible en grotte comme
     * dans une maison.
     */
    private void performFakeMining(ServerLevel level) {
        BlockPos origin = this.blockPosition();
        for (Direction direction : Direction.values()) {
            if (direction == Direction.UP) {
                continue;
            }
            BlockPos target = origin.relative(direction);
            BlockState state = level.getBlockState(target);
            if (state.isAir() || !state.isSolidRender()) {
                continue;
            }
            Vec3 center = Vec3.atCenterOf(target);
            this.getLookControl().setLookAt(center.x, center.y, center.z, 35.0f, 35.0f);
            this.swing(InteractionHand.MAIN_HAND);
            level.playSound(null, target, state.getSoundType().getHitSound(),
                    SoundSource.HOSTILE, 0.22f, 0.72f);
            return;
        }
        this.swing(InteractionHand.MAIN_HAND);
    }

    /**
     * Près d'une porte, mime une ouverture sans toucher à son état. L'absence
     * de mouvement du battant devient volontairement l'indice que la copie a
     * appris le son mais pas le geste.
     */
    private void performFakeDoorInteraction(ServerLevel level) {
        BlockPos origin = this.blockPosition();
        for (BlockPos target : BlockPos.betweenClosed(
                origin.offset(-3, -1, -3), origin.offset(3, 2, 3))) {
            if (!level.getBlockState(target).is(BlockTags.WOODEN_DOORS)) {
                continue;
            }
            Vec3 center = Vec3.atCenterOf(target);
            this.getLookControl().setLookAt(center.x, center.y, center.z, 35.0f, 35.0f);
            this.swing(InteractionHand.MAIN_HAND);
            level.playSound(null, target, SoundEvents.WOODEN_DOOR_OPEN,
                    SoundSource.HOSTILE, 0.30f, 0.76f);
            return;
        }
    }

    private void tickLure(ServerLevel level, ServerPlayer owner) {
        this.setShiftKeyDown(false);
        double distance = this.distanceTo(owner);
        Vec3 towardAbsent = this.position().subtract(owner.position()).multiply(1.0, 0.0, 1.0);
        Vec3 ownerMotion = owner.getDeltaMovement().multiply(1.0, 0.0, 1.0);
        boolean activelyFollowing = distance <= 28.0
                && towardAbsent.lengthSqr() > 1.0E-4
                && ownerMotion.dot(towardAbsent.normalize()) > 0.02;
        this.followTicks = activelyFollowing
                ? Math.min(60, this.followTicks + 1)
                : Math.max(0, this.followTicks - 2);

        if (AbsentMimicLogic.shouldEnterSuspicion(this.phaseTicks, distance,
                this.stareTicks, this.followTicks, this.duplicateExposed(owner))) {
            this.setPhase(PHASE_SUSPICION);
            this.getNavigation().stop();
            return;
        }
        if (this.phaseTicks % 80 == 0 && this.lureMessageIndex < 3) {
            this.sendLureMessage(owner);
        }

        boolean hitch = PenumbraConfig.get().absentMovementImitationEnabled
                && AbsentMimicLogic.movementHitch(
                        this.getLocomotionSeed(), this.lifetimeTicks,
                        this.getImitationError());
        if (hitch) {
            this.getNavigation().stop();
            this.setDeltaMovement(Vec3.ZERO);
            this.getLookControl().setLookAt(owner, 3.0f, 2.0f);
            return;
        }
        if (this.phaseTicks % 16 == 1 || this.getNavigation().isDone()) {
            Vec3 away = this.position().subtract(owner.position()).multiply(1.0, 0.0, 1.0);
            if (away.lengthSqr() < 1.0E-4) {
                double angle = this.random.nextDouble() * Mth.TWO_PI;
                away = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
            } else {
                away = away.normalize();
            }
            Vec3 side = new Vec3(-away.z, 0.0, away.x)
                    .scale((this.random.nextDouble() - 0.5) * 5.0);
            Vec3 destination = this.position().add(away.scale(11.0)).add(side);
            double speed = PenumbraConfig.get().absentMovementImitationEnabled
                    ? 0.70 + Math.min(0.22, this.learnedSpeed * 0.9)
                    : 0.82;
            this.getNavigation().moveTo(destination.x, destination.y, destination.z, speed);
        }
    }

    private void tickSuspicion(ServerLevel level, ServerPlayer owner) {
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        this.setSprinting(false);
        this.setShiftKeyDown(PenumbraConfig.get().absentMovementImitationEnabled
                && this.phaseTicks >= 11 && this.phaseTicks <= 18);
        // La tete rejoint le joueur avant le corps ; le renderer ajoute le retard final.
        if (this.phaseTicks >= 7) {
            this.getLookControl().setLookAt(owner, 7.0f, 22.0f);
        }
        if (this.phaseTicks == 1) {
            this.broadcastSound(level, PenumbraSounds.VOICE_HEY, 0.45f, 0.78f);
        }
        if (this.phaseTicks >= SUSPICION_TICKS) {
            this.setPhase(PHASE_REVEAL);
        }
    }

    private void tickReveal(ServerLevel level, ServerPlayer owner) {
        this.entityData.set(DATA_REVEAL_TICK, this.phaseTicks + 1);
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        this.setShiftKeyDown(false);
        this.getLookControl().setLookAt(owner, 35.0f, 35.0f);
        if (this.phaseTicks == 1) {
            this.sendDialogue(owner, "message.penumbra.absent.reveal."
                    + this.getRandom().nextInt(REVEAL_MESSAGE_VARIANTS));
            this.broadcastSound(level, PenumbraSounds.VOICE_HEY, 0.75f, 0.62f);
        }
        if (this.phaseTicks >= REVEAL_TICKS) {
            this.setPhase(PHASE_HUNT);
            this.setSprinting(true);
        }
    }

    private void tickHunt(ServerLevel level, ServerPlayer owner) {
        this.setShiftKeyDown(false);
        this.getLookControl().setLookAt(owner, 50.0f, 50.0f);
        if (this.isFollowingThrough()) {
            this.getNavigation().stop();
            this.setDeltaMovement(Vec3.ZERO);
            int elapsed = this.entityData.get(DATA_FOLLOW_THROUGH);
            if (elapsed >= AbsentAttack.FOLLOW_THROUGH_TICKS) this.vanish(level, true);
            else this.entityData.set(DATA_FOLLOW_THROUGH, elapsed + 1);
            return;
        }
        if (this.lungeCooldownTicks > 0) {
            this.lungeCooldownTicks--;
        }
        if (this.lungeTicks > 0) {
            this.lungeTicks--;
            this.getNavigation().stop();
            this.entityData.set(DATA_ATTACK_TICK, AbsentAttack.LUNGE_TICKS - this.lungeTicks + 1);
            if (this.lungeTicks == 0) {
                this.entityData.set(DATA_LUNGING, false);
                this.entityData.set(DATA_ATTACK_TICK, 0);
            }
        } else if (this.distanceToSqr(owner) >= 1.7 * 1.7
                && this.distanceToSqr(owner) <= 6.0 * 6.0
                && this.hasLineOfSight(owner) && this.onGround()
                && this.lungeCooldownTicks == 0) {
            Vec3 direction = owner.getEyePosition().subtract(this.position());
            Vec3 horizontal = new Vec3(direction.x, 0.0, direction.z);
            if (horizontal.lengthSqr() > 1.0E-5) {
                this.getNavigation().stop();
                this.entityData.set(DATA_LUNGING, true);
                this.entityData.set(DATA_ATTACK_TICK, 1);
                this.lungeTicks = AbsentAttack.LUNGE_TICKS;
                this.lungeCooldownTicks = 38;
                this.setDeltaMovement(horizontal.normalize().scale(0.975)
                        .add(0.0, 0.26, 0.0));
            }
        } else if (this.phaseTicks % 5 == 1 || this.getNavigation().isDone()) {
            this.getNavigation().moveTo(owner, 1.32);
        }
        boolean physicalContact = this.getBoundingBox().inflate(0.32)
                .intersects(owner.getBoundingBox()) && this.hasLineOfSight(owner);
        if (physicalContact) {
            this.doHurtTarget(level, owner);
            PenumbraConfig config = PenumbraConfig.get();
            if (config.screamersEnabled && config.jumpscareFrequency > 0.0f) {
                PenumbraNetworking.sendJumpscare(owner, "absent_screamer", 0.95f);
            } else {
                this.broadcastSound(level, PenumbraSounds.whisperVariant(this.random),
                        0.8f, 0.55f);
            }
            this.entityData.set(DATA_CONTACT_POSE, this.isLunging()
                    ? this.getAttackProgress(0.0f) : .25f);
            this.entityData.set(DATA_FOLLOW_THROUGH, 1);
            this.entityData.set(DATA_LUNGING, false);
            this.entityData.set(DATA_ATTACK_TICK, 0);
            this.lungeTicks = 0;
            this.getNavigation().stop();
            this.setDeltaMovement(Vec3.ZERO);
            return;
        }
        if (this.phaseTicks > HUNT_TIMEOUT_TICKS) {
            this.vanish(level, false);
        }
    }

    private boolean duplicateExposed(ServerPlayer owner) {
        if (!(this.level() instanceof ServerLevel level)) {
            return false;
        }
        ServerPlayer copied = this.imitatedPlayer(level);
        if (copied == null || copied.getUUID().equals(owner.getUUID())
                || !copied.isAlive() || copied.isSpectator()) {
            return false;
        }
        double thresholdSquared = DUPLICATE_EXPOSURE_DISTANCE * DUPLICATE_EXPOSURE_DISTANCE;
        boolean ownerSeesBoth = copied.distanceToSqr(owner) <= thresholdSquared
                && owner.hasLineOfSight(copied) && owner.hasLineOfSight(this);
        boolean twinsMeet = copied.distanceToSqr(this) <= 18.0 * 18.0
                && (copied.hasLineOfSight(this) || this.hasLineOfSight(copied));
        return ownerSeesBoth || twinsMeet;
    }

    private void setPhase(int phase) {
        int clamped = Mth.clamp(phase, PHASE_IMITATE, PHASE_HUNT);
        if (clamped == this.getAbsentPhase()) {
            return;
        }
        this.entityData.set(DATA_PHASE, clamped);
        this.clearAttackVisuals();
        this.phaseTicks = 0;
        if (clamped != PHASE_LURE) {
            this.followTicks = 0;
        }
        if (clamped >= PHASE_HUNT) {
            this.clearImitatedEquipment();
        }
    }

    /**
     * Reaction serveur a un flash deja valide par distance et ligne de vue.
     * Le flash donne un apercu temporaire ; une vraie photo pousse davantage la
     * scene vers la suspicion/revelation. Retourne false si le contre-jeu est
     * desactive ou si cette entite n'est plus reactive.
     */
    public boolean onCameraFlash(ServerPlayer photographer, boolean photographed) {
        if (this.confrontationDouble) return false;
        if (!(this.level() instanceof ServerLevel level)
                || photographer == null || !photographer.isAlive()) {
            return false;
        }
        if (this.isFalseCompanion()) {
            if (!this.isOwnedBy(photographer)) {
                return false;
            }
            photographer.sendOverlayMessage(Component.translatable(
                    "message.penumbra.perception.camera_clear"));
            this.vanish(level, true);
            return true;
        }
        if (!PenumbraConfig.get().absentEnabled
                || !PenumbraConfig.get().absentCameraCounterplayEnabled
                || !this.isAlive() || this.ashBanishTicks > 0) {
            return false;
        }
        if (this.flashStunRecoveryTicks <= 0) {
            this.cameraStunTicks = Math.max(this.cameraStunTicks, photographed ? 18 : 10);
            this.flashStunRecoveryTicks = FLASH_STUN_RECOVERY_TICKS;
            // The already-landed hit remains terminal; otherwise stop its visual clock.
            if (!this.isFollowingThrough()) this.clearAttackVisuals();
        }
        this.forcedRevealTicks = Math.max(this.forcedRevealTicks, photographed ? 22 : 12);
        this.entityData.set(DATA_REVEAL_PROGRESS,
                Math.max(this.getRevealProgress(), photographed ? 0.88f : 0.62f));
        this.entityData.set(DATA_IMITATION_ERROR,
                Math.max(this.getImitationError(), photographed ? 0.82f : 0.56f));
        if (this.getRevealProgress() >= SKIN_BREAK_THRESHOLD) {
            this.clearImitatedEquipment();
        }
        this.getNavigation().stop();
        if (photographed && this.getAbsentPhase() == PHASE_SUSPICION) {
            this.setPhase(PHASE_REVEAL);
        } else if (photographed && this.getAbsentPhase() < PHASE_SUSPICION) {
            this.setPhase(PHASE_SUSPICION);
        }
        // La réaction vient bien de la position du double : tous les joueurs
        // assez proches l'entendent, sans diffusion globale ni duplication.
        this.broadcastSound(level, PenumbraSounds.whisperVariant(this.random),
                photographed ? 0.58f : 0.38f,
                photographed ? 0.52f : 0.74f);
        return true;
    }

    /**
     * Commence une disparition rituelle differee. Le true indique a l'appelant
     * qu'il ne doit pas discard immediatement : la vraie forme reste visible
     * pendant quatorze ticks avant de tomber en cendres.
     */
    public boolean beginAshBanishment(ServerPlayer caster) {
        if (this.confrontationDouble) return false;
        if (!(this.level() instanceof ServerLevel) || !this.isAlive()
                || caster == null || !caster.isAlive() || this.ashBanishTicks > 0) {
            return false;
        }
        if (this.isFalseCompanion()) {
            this.vanish((ServerLevel) this.level(), true);
            return true;
        }
        this.ashBanishTicks = 14;
        this.cameraStunTicks = 0;
        this.forcedRevealTicks = 14;
        this.setPhase(PHASE_REVEAL);
        this.entityData.set(DATA_REVEAL_PROGRESS, 1.0f);
        this.entityData.set(DATA_IMITATION_ERROR, 1.0f);
        this.clearImitatedEquipment();
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        this.entityData.set(DATA_LUNGING, false);
        this.setInvulnerable(true);
        return true;
    }

    private void tickAshBanishment(ServerLevel level) {
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        this.setShiftKeyDown(false);
        this.entityData.set(DATA_REVEAL_PROGRESS, 1.0f);
        if (this.ashBanishTicks % 3 == 0) {
            level.sendParticles(PenumbraParticles.ASH_MOTE,
                    this.getX(), this.getY() + 0.9, this.getZ(),
                    7, 0.22, 0.55, 0.22, 0.018);
        }
        if (--this.ashBanishTicks <= 0) {
            this.vanish(level, false);
        }
    }

    /** Paliers du mensonge : d'abord banal, puis insistant, puis plus tout à fait humain. */
    private static final String[] LURE_TIERS = {"early", "mid", "late"};
    /** Répliques disponibles par palier (clés lang {@code absent.lure.<palier>.<n>}). */
    private static final int LURE_VARIANTS_PER_TIER = 8;
    /** Variantes de la réplique de révélation ({@code absent.reveal.<n>}). */
    private static final int REVEAL_MESSAGE_VARIANTS = 4;

    /**
     * Le premier message doit passer pour un vrai joueur qui écrit vite ; le
     * deuxième insiste un peu trop ; le troisième n'est plus tout à fait une
     * phrase humaine. La variante est tirée au hasard dans le palier pour que
     * deux rencontres ne se ressemblent jamais.
     */
    private void sendLureMessage(ServerPlayer owner) {
        int step = Math.min(this.lureMessageIndex++, LURE_TIERS.length - 1);
        int variant = this.getRandom().nextInt(LURE_VARIANTS_PER_TIER);
        this.sendDialogue(owner,
                "message.penumbra.absent.lure." + LURE_TIERS[step] + "." + variant);
    }

    private void sendDialogue(ServerPlayer owner, String key) {
        Component speaker = this.getCustomName() != null
                ? this.getCustomName()
                : Component.translatable("entity.penumbra.absent");
        owner.sendSystemMessage(Component.translatable(key, speaker)
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    private void broadcastSound(ServerLevel level, SoundEvent sound, float volume, float pitch) {
        ServerPlayer source = this.owner(level);
        if (source != null) {
            PenumbraNetworking.sendSoundPrioritySpatial(
                    source, sound, this.position(), volume, pitch);
        }
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        if (this.isFalseCompanion() && this.level() instanceof ServerLevel level) {
            ServerPlayer owner = this.owner(level);
            if (owner != null) {
                PenumbraNetworking.sendSoundDirect(owner,
                        state.getSoundType().getStepSound(), this.position(), 0.18f, 0.96f);
            }
            return;
        }
        super.playStepSound(pos, state);
    }

    private void vanish(ServerLevel level, boolean violent) {
        this.clearImitatedEquipment();
        if (this.isFalseCompanion()) {
            ServerPlayer owner = this.owner(level);
            if (owner != null) {
                level.sendParticles(owner, PenumbraParticles.ASH_MOTE,
                        false, false, this.getX(), this.getY() + 0.9, this.getZ(),
                        violent ? 7 : 2, 0.18, 0.42, 0.18, 0.008);
                if (violent) {
                    PenumbraNetworking.sendSoundDirect(owner, PenumbraSounds.TORCH_SPUTTER,
                            this.position(), 0.42f, 0.58f);
                }
            }
            this.discard();
            return;
        }
        level.sendParticles(PenumbraParticles.ASH_MOTE,
                this.getX(), this.getY() + 0.9, this.getZ(),
                violent ? 24 : 10, 0.28, 0.72, 0.28,
                violent ? 0.035 : 0.012);
        this.discard();
    }

    private static float approach(float value, float target, float amount) {
        if (value < target) {
            return Math.min(value + amount, target);
        }
        return Math.max(value - amount, target);
    }
}

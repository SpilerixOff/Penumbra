package fr.penumbra.entity.projectionist;

import fr.penumbra.network.PenumbraNetworking;
import fr.penumbra.registry.PenumbraParticles;
import fr.penumbra.registry.PenumbraSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Manifestation physique liée aux rares photographies hantées.
 *
 * <p>Le Projectionniste apparaît derrière son propriétaire, le fixe quatre à
 * six secondes, avance brièvement puis se dissout. Il ne blesse pas et sa durée
 * de vie est strictement bornée : aucune entité oubliée ne peut s'accumuler.</p>
 */
public final class ProjectionistEntity extends Monster {

    private static final EntityDataAccessor<Boolean> DATA_RUSHING =
            SynchedEntityData.defineId(ProjectionistEntity.class, EntityDataSerializers.BOOLEAN);
    private static final int MIN_STARE_TICKS = 80;
    private static final int MAX_STARE_TICKS = 120;
    private static final int RUSH_TICKS = 18;
    private static final double RUSH_SPEED = 0.15;
    private static final double VANISH_DISTANCE = 3.25;
    private static final double OWNER_RADIUS = 64.0;
    private static final int SPAWN_ATTEMPTS = 14;
    private static final int SCAN_UP = 8;
    private static final int SCAN_DOWN = 24;

    private UUID ownerUuid;
    private int lifetimeTicks;
    private int stareTicks = MIN_STARE_TICKS;
    private boolean clientWasRushing;
    private int clientRushTicks;

    public ProjectionistEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.setNoAi(true);
        this.setInvulnerable(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.ATTACK_DAMAGE, 0.0)
                .add(Attributes.FOLLOW_RANGE, OWNER_RADIUS)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_RUSHING, false);
    }

    @Override
    protected void registerGoals() {
        // Manifestation pilotée intégralement par tick().
    }

    @Override
    public void checkDespawn() {
        // La fin de vie bornée ci-dessous est la seule règle de despawn.
    }

    /** Cette apparition photographique ne persiste jamais entre deux sessions. */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void knockback(double strength, double x, double z) {
        // Une apparition photographique ne réagit pas à la physique des joueurs.
    }

    public boolean isRushing() {
        return this.entityData.get(DATA_RUSHING);
    }

    /** Temps client local depuis la transition de ruee synchronisee. */
    public float getVisualRushTicks(float partialTick) {
        return this.isRushing()
                ? this.clientRushTicks + Mth.clamp(partialTick, 0.0f, 1.0f)
                : 0.0f;
    }

    public boolean isOwnedBy(ServerPlayer player) {
        return ownerUuid != null && ownerUuid.equals(player.getUUID());
    }

    private void setOwner(ServerPlayer player) {
        ownerUuid = player.getUUID();
        stareTicks = MIN_STARE_TICKS
                + this.random.nextInt(MAX_STARE_TICKS - MIN_STARE_TICKS + 1);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        lifetimeTicks = Math.max(0, input.getIntOr("PenumbraLifetime", 0));
        stareTicks = Mth.clamp(input.getIntOr("PenumbraStareTicks", MIN_STARE_TICKS),
                MIN_STARE_TICKS, MAX_STARE_TICKS);
        String owner = input.getStringOr("PenumbraOwner", "");
        if (!owner.isBlank()) {
            try {
                ownerUuid = UUID.fromString(owner);
            } catch (IllegalArgumentException ignored) {
                ownerUuid = null;
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("PenumbraLifetime", lifetimeTicks);
        output.putInt("PenumbraStareTicks", stareTicks);
        if (ownerUuid != null) {
            output.putString("PenumbraOwner", ownerUuid.toString());
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            boolean rushing = this.isRushing();
            if (rushing != this.clientWasRushing) {
                this.clientWasRushing = rushing;
                this.clientRushTicks = 0;
            } else if (rushing) {
                this.clientRushTicks++;
            }
            return;
        }
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            vanish(level);
            return;
        }
        ServerPlayer owner = owner(level);
        if (owner == null || !owner.isAlive() || owner.isSpectator()
                || distanceToSqr(owner) > OWNER_RADIUS * OWNER_RADIUS) {
            vanish(level);
            return;
        }

        lifetimeTicks++;
        face(owner);
        if (lifetimeTicks <= stareTicks) {
            entityData.set(DATA_RUSHING, false);
            Vec3 movement = getDeltaMovement();
            setDeltaMovement(0.0, movement.y, 0.0);
            return;
        }

        if (lifetimeTicks <= stareTicks + RUSH_TICKS
                && distanceToSqr(owner) > VANISH_DISTANCE * VANISH_DISTANCE) {
            entityData.set(DATA_RUSHING, true);
            Vec3 horizontal = owner.position().subtract(position()).multiply(1.0, 0.0, 1.0);
            if (horizontal.lengthSqr() > 1.0E-6) {
                horizontal = horizontal.normalize().scale(RUSH_SPEED);
                setDeltaMovement(horizontal.x, getDeltaMovement().y, horizontal.z);
            }
            return;
        }
        vanish(level);
    }

    private ServerPlayer owner(ServerLevel level) {
        if (ownerUuid == null) {
            return null;
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerUuid);
        return owner != null && owner.level() == level ? owner : null;
    }

    private void face(ServerPlayer owner) {
        Vec3 delta = owner.getEyePosition().subtract(getEyePosition());
        float yaw = (float) (Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0f;
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float pitch = (float) (-Mth.atan2(delta.y, horizontal) * Mth.RAD_TO_DEG);
        setYRot(yaw);
        setYBodyRot(yaw);
        setYHeadRot(yaw);
        setXRot(pitch);
    }

    private void vanish(ServerLevel level) {
        level.sendParticles(PenumbraParticles.ASH_MOTE,
                getX(), getY() + 1.25, getZ(), 30, 0.45, 1.0, 0.45, 0.008);
        discard();
    }

    public static boolean hasManifestation(ServerLevel level, ServerPlayer owner) {
        AABB search = owner.getBoundingBox().inflate(OWNER_RADIUS);
        return !level.getEntitiesOfClass(ProjectionistEntity.class, search,
                entity -> entity.isOwnedBy(owner)).isEmpty();
    }

    /**
     * Cherche un sol chargé dans l'arc arrière du joueur et crée une seule
     * manifestation. Aucun chunk n'est forcé.
     */
    public static ProjectionistEntity spawnBehind(ServerLevel level, ServerPlayer owner) {
        if (hasManifestation(level, owner)) {
            return null;
        }
        Vec3 look = owner.getLookAngle().multiply(1.0, 0.0, 1.0);
        if (look.lengthSqr() < 1.0E-6) {
            look = new Vec3(0.0, 0.0, 1.0);
        } else {
            look = look.normalize();
        }
        double backAngle = Math.atan2(-look.z, -look.x);

        for (int attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
            double angle = backAngle + Math.toRadians(-42.0 + owner.getRandom().nextDouble() * 84.0);
            double distance = 8.0 + owner.getRandom().nextDouble() * 6.0;
            int x = Mth.floor(owner.getX() + Math.cos(angle) * distance);
            int z = Mth.floor(owner.getZ() + Math.sin(angle) * distance);
            BlockPos spot = findStandingSpot(level, x, Mth.floor(owner.getY()), z);
            if (spot == null) {
                continue;
            }

            ProjectionistEntity entity = new ProjectionistEntity(
                    ProjectionistEntities.PROJECTIONIST, level);
            entity.setOwner(owner);
            entity.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5,
                    owner.getYRot(), 0.0f);
            entity.face(owner);
            entity.finalizeSpawn(level, level.getCurrentDifficultyAt(spot),
                    EntitySpawnReason.EVENT, null);
            if (!owner.hasLineOfSight(entity)) {
                continue;
            }
            if (!level.addFreshEntity(entity)) {
                return null;
            }
            level.sendParticles(PenumbraParticles.ASH_MOTE,
                    entity.getX(), entity.getY() + 1.2, entity.getZ(),
                    26, 0.4, 0.9, 0.4, 0.008);
            PenumbraNetworking.sendSoundPriority(owner, PenumbraSounds.BREATH_BEHIND,
                    entity.position(), 0.8f, 0.62f);
            return entity;
        }
        return null;
    }

    private static BlockPos findStandingSpot(ServerLevel level, int x, int baseY, int z) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = SCAN_UP; dy >= -SCAN_DOWN; dy--) {
            cursor.set(x, baseY + dy, z);
            if (!level.isLoaded(cursor)) {
                return null;
            }
            if (isValidStandingSpot(level, cursor)) {
                return cursor.immutable();
            }
        }
        return null;
    }

    private static boolean isValidStandingSpot(ServerLevel level, BlockPos feet) {
        return level.getBlockState(feet.below()).isSolidRender()
                && level.getBlockState(feet).isAir()
                && level.getBlockState(feet.above()).isAir()
                && level.getBlockState(feet.above(2)).isAir()
                && level.getFluidState(feet.below()).isEmpty()
                && level.getFluidState(feet).isEmpty()
                && level.getFluidState(feet.above()).isEmpty()
                && level.getFluidState(feet.above(2)).isEmpty();
    }
}

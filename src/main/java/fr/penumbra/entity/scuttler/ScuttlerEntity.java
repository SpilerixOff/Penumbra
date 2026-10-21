package fr.penumbra.entity.scuttler;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
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
 * La Cavaleuse : petite araignée entièrement inoffensive et à durée de vie
 * très courte. Sa scène appartient à un joueur, mais l'entité et ses sons sont
 * synchronisés normalement et restent donc visibles/audibles par ses voisins.
 */
public final class ScuttlerEntity extends Monster {

    public static final int SCENE_CROSSING = 0;
    public static final int SCENE_CHASE = 1;

    private static final EntityDataAccessor<Integer> DATA_SCENE =
            SynchedEntityData.defineId(ScuttlerEntity.class, EntityDataSerializers.INT);

    /**
     * Filet de sécurité, pas une durée de scène : à 0,30 bloc par tick, la
     * traversée la plus longue (quinze blocs) demande cinquante ticks. À 48,
     * une traversée sur cent s'effaçait au bord du champ de vision — soit
     * exactement l'impression « je n'ai rien vu » qu'on cherche à éviter.
     */
    private static final int CROSSING_MAX_TICKS = 58;
    private static final int OWNER_RANGE = 48;
    private static final int SPAWN_ATTEMPTS = 18;
    private static final int GROUND_SCAN_UP = 5;
    private static final int GROUND_SCAN_DOWN = 12;
    private static final double CROSSING_SPEED = 1.90;
    /**
     * Avance par tick de la traversée, en blocs. À 0,30 la scène dure environ
     * une seconde et demie sur un trajet de dix blocs — assez pour être vue,
     * trop peu pour être détaillée. Un joueur qui court avance de 0,13.
     */
    private static final double CROSSING_STEP = 0.30;
    private static final double CHASE_SPEED = 1.55;

    private UUID ownerUuid;
    private int sceneTicks;
    private int lifetimeLimit = CROSSING_MAX_TICKS;
    private BlockPos crossingEnd = BlockPos.ZERO;

    public ScuttlerEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 4.0)
                .add(Attributes.MOVEMENT_SPEED, 0.43)
                .add(Attributes.ATTACK_DAMAGE, 0.0)
                .add(Attributes.FOLLOW_RANGE, OWNER_RANGE)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SCENE, SCENE_CROSSING);
    }

    /** Aucun goal d'attaque, de cible ou de modification du monde. */
    @Override
    protected void registerGoals() {
        // Les deux trajectoires courtes sont pilotées explicitement par tick().
    }

    @Override
    public void checkDespawn() {
        // La durée de vie bornée de la scène décide seule de la disparition.
    }

    /**
     * Une scène de moins de cinq secondes ne doit jamais réapparaître après le
     * rechargement d'un chunk ou une reconnexion du propriétaire.
     */
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
        // La petite silhouette ne peut pas être projetée dans un mur.
    }

    /** Garantie défensive : même appelée par un autre système, elle ne frappe jamais. */
    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        return false;
    }

    /** Un coup la fait fuir instantanément sans dégâts, loot ni riposte. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        this.playSound(SoundEvents.SPIDER_HURT, 0.32f, 1.75f);
        vanish(level);
        return false;
    }

    public int getScene() {
        return this.entityData.get(DATA_SCENE);
    }

    public boolean isOwnedBy(ServerPlayer player) {
        return ownerUuid != null && ownerUuid.equals(player.getUUID());
    }

    private void bindCrossing(ServerPlayer owner, BlockPos destination) {
        ownerUuid = owner.getUUID();
        crossingEnd = destination.immutable();
        lifetimeLimit = CROSSING_MAX_TICKS;
        entityData.set(DATA_SCENE, SCENE_CROSSING);
    }

    private void bindChase(ServerPlayer owner, int durationTicks) {
        ownerUuid = owner.getUUID();
        lifetimeLimit = Mth.clamp(durationTicks,
                ScuttlerBehavior.MIN_CHASE_TICKS, ScuttlerBehavior.MAX_CHASE_TICKS);
        entityData.set(DATA_SCENE, SCENE_CHASE);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        sceneTicks = Math.max(0, input.getIntOr("PenumbraSceneTicks", 0));
        lifetimeLimit = Mth.clamp(input.getIntOr(
                        "PenumbraLifetime", CROSSING_MAX_TICKS),
                CROSSING_MAX_TICKS, ScuttlerBehavior.MAX_CHASE_TICKS);
        entityData.set(DATA_SCENE, Mth.clamp(input.getIntOr(
                "PenumbraScene", SCENE_CROSSING), SCENE_CROSSING, SCENE_CHASE));
        crossingEnd = new BlockPos(
                input.getIntOr("PenumbraEndX", blockPosition().getX()),
                input.getIntOr("PenumbraEndY", blockPosition().getY()),
                input.getIntOr("PenumbraEndZ", blockPosition().getZ()));
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
        output.putInt("PenumbraSceneTicks", sceneTicks);
        output.putInt("PenumbraLifetime", lifetimeLimit);
        output.putInt("PenumbraScene", getScene());
        output.putInt("PenumbraEndX", crossingEnd.getX());
        output.putInt("PenumbraEndY", crossingEnd.getY());
        output.putInt("PenumbraEndZ", crossingEnd.getZ());
        if (ownerUuid != null) {
            output.putString("PenumbraOwner", ownerUuid.toString());
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        ServerPlayer owner = owner(level);
        if (level.getDifficulty() == Difficulty.PEACEFUL || owner == null
                || !owner.isAlive() || owner.isSpectator()
                || distanceToSqr(owner) > OWNER_RANGE * OWNER_RANGE
                || ++sceneTicks > lifetimeLimit) {
            vanish(level);
            return;
        }

        if (getScene() == SCENE_CHASE) {
            tickChase(owner);
        } else {
            tickCrossing(level);
        }

        // Petits frottements secs, diffusés par le moteur sonore vanilla à tous
        // les joueurs réellement proches de l'entité.
        if (sceneTicks % (getScene() == SCENE_CROSSING ? 3 : 5) == 0) {
            this.playSound(SoundEvents.SPIDER_STEP, 0.28f,
                    1.52f + random.nextFloat() * 0.28f);
        }
    }

    /**
     * Traversée en ligne droite, pilotée à la main (corrigé en 0.19.0).
     *
     * <p>La version précédente passait par {@code getNavigation().moveTo(...)}.
     * C'était une erreur de principe : une scène scriptée de deux secondes ne
     * peut pas dépendre du chemin que le pathfinder voudra bien trouver. Sur un
     * sol de grotte irrégulier il échouait souvent, et la méthode faisait alors
     * disparaître la Cavaleuse au bout de neuf ticks — le joueur ne voyait
     * rien, ou une forme qui s'évanouissait sur place.</p>
     *
     * <p>Maintenant : vitesse constante vers le point d'arrivée, orientation
     * alignée sur le déplacement, durée identique quelle que soit la longueur
     * de la traversée. La physique vanilla continue de gérer la gravité et les
     * collisions, donc elle ne traverse pas les murs.</p>
     */
    private void tickCrossing(ServerLevel level) {
        Vec3 end = Vec3.atBottomCenterOf(crossingEnd);
        double dx = end.x - getX();
        double dz = end.z - getZ();
        double remaining = Math.sqrt(dx * dx + dz * dz);
        if (remaining <= 0.55) {
            vanish(level);
            return;
        }

        // Pas constant : la traversée dure toujours le même temps à l'écran,
        // que le trajet fasse neuf blocs ou quinze.
        double step = Math.min(CROSSING_STEP, remaining);
        double scale = step / remaining;
        // Le client suit par les paquets de position (updateInterval 2), pas
        // par la vélocité : inutile de forcer une synchronisation de vitesse.
        setDeltaMovement(dx * scale, getDeltaMovement().y, dz * scale);

        float heading = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
        setYRot(heading);
        setYHeadRot(heading);
        yBodyRot = heading;

        // Elle s'est coincée contre un rebord : plutôt que de rester plantée
        // au milieu du champ de vision, elle s'efface.
        if (sceneTicks > 12 && horizontalCollision && onGround()) {
            vanish(level);
        }
    }

    private void tickChase(ServerPlayer owner) {
        Vec3 destination = owner.position();
        if (distanceToSqr(owner) < 2.1 * 2.1) {
            // À portée des chaussures, elle tourne autour au lieu d'attaquer.
            double angle = sceneTicks * 0.39;
            destination = destination.add(Math.cos(angle) * 1.9, 0.0,
                    Math.sin(angle) * 1.9);
        }
        if (sceneTicks == 1 || sceneTicks % 4 == 0 || getNavigation().isDone()) {
            getNavigation().moveTo(
                    destination.x, destination.y, destination.z, CHASE_SPEED);
        }
    }

    private ServerPlayer owner(ServerLevel level) {
        if (ownerUuid == null) {
            return null;
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerUuid);
        return owner != null && owner.level() == level ? owner : null;
    }

    private void vanish(ServerLevel level) {
        if (isRemoved()) {
            return;
        }
        getNavigation().stop();
        level.sendParticles(ParticleTypes.POOF,
                getX(), getY() + 0.16, getZ(), 7, 0.24, 0.12, 0.24, 0.015);
        level.sendParticles(ParticleTypes.SMOKE,
                getX(), getY() + 0.12, getZ(), 4, 0.18, 0.08, 0.18, 0.008);
        ScuttlerManager.release(ownerUuid, getUUID());
        discard();
    }

    /** Libère aussi la réservation lors d'un retrait externe (sceau, unload, arrêt). */
    @Override
    public void onRemoval(Entity.RemovalReason reason) {
        if (!level().isClientSide()) {
            ScuttlerManager.release(ownerUuid, getUUID());
        }
        super.onRemoval(reason);
    }

    public static boolean hasOwnedNearby(
            ServerLevel level, ServerPlayer owner, double radius) {
        return !level.getEntitiesOfClass(ScuttlerEntity.class,
                owner.getBoundingBox().inflate(radius),
                entity -> entity.isAlive() && entity.isOwnedBy(owner)).isEmpty();
    }

    /** Traverse rapidement le champ de vision de gauche à droite ou inversement. */
    public static boolean spawnCrossing(ServerLevel level, ServerPlayer owner) {
        if (!ScuttlerManager.mayCreate(owner)) {
            return false;
        }
        RandomGeometry geometry = crossingGeometry(owner);
        for (int attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
            double forwardDistance = 4.8 + owner.getRandom().nextDouble() * 3.8;
            double halfSpan = 4.7 + owner.getRandom().nextDouble() * 2.8;
            double jitter = (owner.getRandom().nextDouble() * 2.0 - 1.0) * 1.2;
            Vec3 center = owner.position()
                    .add(geometry.forward.scale(forwardDistance))
                    .add(geometry.right.scale(jitter));
            int side = owner.getRandom().nextBoolean() ? 1 : -1;
            Vec3 startGuess = center.add(geometry.right.scale(halfSpan * side));
            Vec3 endGuess = center.add(geometry.right.scale(-halfSpan * side));
            BlockPos start = findGround(level, startGuess, Mth.floor(owner.getY()));
            BlockPos end = findGround(level, endGuess, Mth.floor(owner.getY()));
            if (start == null || end == null || Math.abs(start.getY() - end.getY()) > 2) {
                continue;
            }
            if (create(level, owner, start, end, SCENE_CROSSING,
                    CROSSING_MAX_TICKS)) {
                return true;
            }
        }
        return false;
    }

    /** Court après son propriétaire trois à cinq secondes, sans contact offensif. */
    public static boolean spawnChase(ServerLevel level, ServerPlayer owner) {
        if (!ScuttlerManager.mayCreate(owner)) {
            return false;
        }
        Vec3 look = owner.getLookAngle().multiply(1.0, 0.0, 1.0);
        if (look.lengthSqr() < 1.0E-6) {
            look = new Vec3(0.0, 0.0, 1.0);
        } else {
            look = look.normalize();
        }
        double rearAngle = Math.atan2(-look.z, -look.x);
        for (int attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
            double angle = rearAngle + Math.toRadians(
                    -70.0 + owner.getRandom().nextDouble() * 140.0);
            double distance = 6.5 + owner.getRandom().nextDouble() * 5.5;
            Vec3 guess = owner.position().add(
                    Math.cos(angle) * distance, 0.0, Math.sin(angle) * distance);
            BlockPos start = findGround(level, guess, Mth.floor(owner.getY()));
            if (start == null) {
                continue;
            }
            int duration = ScuttlerBehavior.chaseDurationTicks(owner.getRandom().nextInt());
            if (create(level, owner, start, owner.blockPosition(), SCENE_CHASE, duration)) {
                return true;
            }
        }
        return false;
    }

    private static boolean create(ServerLevel level, ServerPlayer owner,
                                  BlockPos start, BlockPos destination,
                                  int scene, int duration) {
        ScuttlerEntity entity = new ScuttlerEntity(ScuttlerEntities.SCUTTLER, level);
        Vec3 startPos = Vec3.atBottomCenterOf(start);
        Vec3 endPos = Vec3.atBottomCenterOf(destination);
        Vec3 direction = endPos.subtract(startPos);
        float yaw = (float) (Mth.atan2(direction.z, direction.x) * Mth.RAD_TO_DEG) - 90.0f;
        entity.snapTo(startPos.x, startPos.y, startPos.z, yaw, 0.0f);
        entity.setYBodyRot(yaw);
        entity.setYHeadRot(yaw);
        if (scene == SCENE_CHASE) {
            entity.bindChase(owner, duration);
        } else {
            entity.bindCrossing(owner, destination);
        }
        if (!level.noCollision(entity)) {
            return false;
        }
        entity.finalizeSpawn(level, level.getCurrentDifficultyAt(start),
                EntitySpawnReason.EVENT, null);
        if (!level.addFreshEntity(entity)) {
            return false;
        }
        boolean pathStarted = scene == SCENE_CROSSING
                ? entity.getNavigation().moveTo(
                        endPos.x, endPos.y, endPos.z, CROSSING_SPEED)
                : entity.getNavigation().moveTo(owner, CHASE_SPEED);
        if (!pathStarted) {
            entity.discard();
            return false;
        }
        ScuttlerManager.track(owner.getUUID(), entity.getUUID());
        entity.playSound(SoundEvents.SPIDER_AMBIENT, 0.22f,
                scene == SCENE_CROSSING ? 1.82f : 1.58f);
        return true;
    }

    private static RandomGeometry crossingGeometry(ServerPlayer owner) {
        Vec3 forward = owner.getLookAngle().multiply(1.0, 0.0, 1.0);
        if (forward.lengthSqr() < 1.0E-6) {
            double yaw = owner.getYRot() * Mth.DEG_TO_RAD;
            forward = new Vec3(-Mth.sin((float) yaw), 0.0, Mth.cos((float) yaw));
        } else {
            forward = forward.normalize();
        }
        return new RandomGeometry(forward, new Vec3(-forward.z, 0.0, forward.x));
    }

    private static BlockPos findGround(
            ServerLevel level, Vec3 guess, int baseY) {
        int x = Mth.floor(guess.x);
        int z = Mth.floor(guess.z);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = GROUND_SCAN_UP; dy >= -GROUND_SCAN_DOWN; dy--) {
            cursor.set(x, baseY + dy, z);
            if (!level.isLoaded(cursor)) {
                return null;
            }
            BlockPos below = cursor.below();
            if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                    && level.getBlockState(cursor).isAir()
                    && level.getFluidState(below).isEmpty()
                    && level.getFluidState(cursor).isEmpty()) {
                return cursor.immutable();
            }
        }
        return null;
    }

    private record RandomGeometry(Vec3 forward, Vec3 right) {
    }
}

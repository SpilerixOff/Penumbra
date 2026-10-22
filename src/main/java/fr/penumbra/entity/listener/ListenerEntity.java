package fr.penumbra.entity.listener;

import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.director.HorrorDirector;
import fr.penumbra.network.PenumbraNetworking;
import fr.penumbra.registry.PenumbraParticles;
import fr.penumbra.registry.PenumbraSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * <b>L'Écouteur</b> — la seule créature de Penumbra qui ne vous voit pas.
 *
 * <p>Elle n'a pas d'yeux : deux pavillons d'oreille démesurés remplacent son
 * visage. Elle ne connaît du monde que ce qu'elle entend, et elle marche vers le
 * dernier bruit avec une erreur de visée d'environ 1,6 bloc. Sa tête est toujours
 * tournée vers ce bruit, jamais vers le joueur — c'est la signature visuelle qui
 * doit apprendre au joueur, sans une ligne de texte, ce qui la guide.</p>
 *
 * <p><b>Contre-jeu réel et documenté :</b> l'immobilité totale produit un silence
 * total ({@link ListenerNoise}). Marcher accroupi ne porte qu'à 2,5 blocs. Frapper
 * ou miner s'entend à 26 blocs, même immobile. Casser un bloc au loin puis se
 * figer ailleurs est donc un leurre parfaitement valide : elle ira fouiller le
 * bruit. Le joueur peut apprendre à disparaître ; c'est tout l'intérêt.</p>
 *
 * <p><b>Garde-fous :</b> aucune modification de bloc, aucune téléportation, durée
 * de vie bornée, jamais sauvegardée en cours de scène, disparition immédiate si
 * son propriétaire meurt, se déconnecte, change de dimension ou passe en
 * spectateur. Le contact ne frappe que le propriétaire de la scène et respecte
 * {@code listenerCanHarm}.</p>
 */
public final class ListenerEntity extends Monster {

    private static final EntityDataAccessor<Integer> DATA_BEHAVIOR =
            SynchedEntityData.defineId(ListenerEntity.class, EntityDataSerializers.INT);
    /** Angle (degrés) vers lequel la tête est tendue : le bruit, pas le joueur. */
    private static final EntityDataAccessor<Float> DATA_HEAR_YAW =
            SynchedEntityData.defineId(ListenerEntity.class, EntityDataSerializers.FLOAT);

    /** Durée de vie maximale d'une scène : 100 s. Se figer est long, pas infini. */
    private static final int MAX_LIFETIME_TICKS = 2000;
    /** Distance au-delà de laquelle la scène est abandonnée. */
    private static final double OWNER_RANGE = 64.0;
    /** Cadence de sondage du bruit : trois fois moins de travail par tick. */
    private static final int POLL_INTERVAL = 3;
    /** Distance à laquelle une destination est considérée atteinte. */
    private static final double ARRIVAL_DISTANCE = 1.35;
    private static final int MAX_LEAVING_TICKS = 100;

    private static final double SEEK_SPEED = 1.02;
    private static final double CLOSE_SPEED = 1.62;
    private static final double LEAVE_SPEED = 1.14;

    private static final int SPAWN_ATTEMPTS = 22;
    private static final int GROUND_SCAN_UP = 6;
    private static final int GROUND_SCAN_DOWN = 14;

    private UUID ownerUuid;
    private int sceneTicks;
    /** Point du dernier bruit entendu, déjà entaché de l'erreur de visée. */
    private Vec3 heardPoint;
    private int listenCountdown;
    private int emptySweeps;
    private final ListenerPursuit pursuit = new ListenerPursuit();
    private int leavingTicks;
    private int seekingTicks;
    private Vec3 leavePoint;
    private boolean contactDone;

    public ListenerEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 24.0)
                .add(Attributes.MOVEMENT_SPEED, 0.31)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.FOLLOW_RANGE, OWNER_RANGE)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.85);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_BEHAVIOR, ListenerBehavior.SEEKING.ordinal());
        builder.define(DATA_HEAR_YAW, 0.0f);
    }

    /** Aucun goal vanilla : la scène entière est pilotée par {@link #tick()}. */
    @Override
    protected void registerGoals() {
        // Volontairement vide — elle n'a ni cible ni regard, seulement une oreille.
    }

    @Override
    public void checkDespawn() {
        // La durée de vie bornée de la scène décide seule de la disparition.
    }

    /** Une scène de moins de deux minutes ne doit jamais survivre à un rechargement. */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /** Un coup autorisé devient un bruit ; il ne tue jamais la créature sur place. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (this.getBehavior() == ListenerBehavior.LEAVING) {
            return false;
        }
        // Le coup EST un bruit : elle sait maintenant très précisément où vous êtes.
        Entity attacker = source.getEntity();
        if (attacker instanceof ServerPlayer player && player.isAlive()
                && !player.isSpectator() && !player.isSleeping()
                && (this.isOwnedBy(player) || PenumbraConfig.get().multiplayerContent)) {
            this.hear(attacker.position(), true);
        }
        return false;
    }

    /**
     * Diffusion spatiale d'un son de scène. {@code PenumbraNetworking.sendSound}
     * déréférence sa source : un propriétaire déconnecté doit donc être filtré
     * ici, jamais transmis.
     */
    private static void playSceneSound(ServerPlayer owner, SoundEvent sound,
                                       Vec3 pos, float volume, float pitch) {
        if (owner != null && owner.isAlive()) {
            PenumbraNetworking.sendSoundPrioritySpatial(owner, sound, pos, volume, pitch);
        }
    }

    public ListenerBehavior getBehavior() {
        return ListenerBehavior.byId(this.entityData.get(DATA_BEHAVIOR));
    }

    /** Lacet (degrés) vers lequel la tête est tendue, lu par le modèle client. */
    public float getHearingYaw() {
        return this.entityData.get(DATA_HEAR_YAW);
    }

    public boolean isOwnedBy(ServerPlayer player) {
        return ownerUuid != null && ownerUuid.equals(player.getUUID());
    }

    private void setBehavior(ListenerBehavior behavior) {
        if (this.getBehavior() != behavior) {
            this.entityData.set(DATA_BEHAVIOR, behavior.ordinal());
            if (behavior == ListenerBehavior.SEEKING) this.seekingTicks = 0;
        }
    }

    // ===================== Boucle de scène =====================

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        if (!this.isAlive() || this.isRemoved()) {
            this.discard();
            return;
        }
        ServerPlayer owner = ownerPlayer(level);
        ++sceneTicks;
        if (!PenumbraConfig.get().listenerEnabled
                || level.getDifficulty() == Difficulty.PEACEFUL || owner == null
                || !owner.isAlive() || owner.isRemoved() || owner.isSpectator()
                || owner.isSleeping()) {
            vanish(level);
            return;
        }
        if (this.distanceToSqr(owner) > OWNER_RANGE * OWNER_RANGE
                || sceneTicks > MAX_LIFETIME_TICKS) {
            if (this.getBehavior() != ListenerBehavior.LEAVING) {
                beginLeaving(level, owner);
            }
            if (sceneTicks > MAX_LIFETIME_TICKS + MAX_LEAVING_TICKS) {
                vanish(level);
                return;
            }
        }

        if (sceneTicks % POLL_INTERVAL == 0
                && this.getBehavior() != ListenerBehavior.LEAVING) {
            pollNoise(level, owner);
        }

        switch (this.getBehavior()) {
            case SEEKING -> tickSeeking(level);
            case LISTENING -> tickListening(level);
            case CLOSING -> tickClosing(level, owner);
            case LEAVING -> tickLeaving(level);
        }

        tickVoice(level, owner);
        aimHeadAtNoise();
    }

    /**
     * Sonde le bruit des joueurs à portée. Le propriétaire est toujours écouté ;
     * les autres joueurs ne le sont que si {@code multiplayerContent} l'autorise,
     * pour qu'un tiers ne puisse pas attirer la créature sur autrui à son insu.
     */
    private void pollNoise(ServerLevel level, ServerPlayer owner) {
        boolean shared = PenumbraConfig.get().multiplayerContent;
        double loudest = 0.0;
        Vec3 loudestAt = null;
        boolean loudestLockable = false;

        List<ServerPlayer> candidates = shared
                ? level.players()
                : owner == null ? List.of() : List.of(owner);
        for (ServerPlayer player : candidates) {
            if (player == null || !player.isAlive() || player.isRemoved()
                    || player.isSpectator() || player.isSleeping()) {
                continue;
            }
            if (!shared && !this.isOwnedBy(player)) {
                continue;
            }
            double distance = this.position().distanceTo(player.position());
            if (distance > ListenerNoise.MAX_RADIUS) {
                continue;
            }
            double emitted = radiusOf(player);
            if (!ListenerNoise.audible(emitted, distance)) continue;
            boolean clearPath = this.hasLineOfSight(player);
            double radius = ListenerPursuit.heardRadius(emitted, clearPath);
            if (!ListenerNoise.audible(radius, distance)) {
                continue;
            }
            if (radius > loudest) {
                loudest = radius;
                loudestAt = player.position();
                loudestLockable = clearPath && ListenerNoise.lockable(radius, distance);
            }
        }

        if (loudestAt == null) {
            return;
        }
        hear(loudestAt, loudestLockable);
    }

    /** Portée du bruit produit par un joueur à cet instant. */
    private double radiusOf(ServerPlayer player) {
        double speed = player.getKnownMovement().horizontalDistance();
        double movement = ListenerNoise.movementRadius(
                speed, player.isSprinting(), player.isCrouching());
        boolean noisyHit = player.swinging || player.hurtTime > 0;
        return ListenerNoise.activityRadius(movement, noisyHit,
                player.isInWater(), player.isPassenger());
    }

    /**
     * Enregistre un bruit. L'erreur de visée est appliquée ici : elle ne connaît
     * jamais la position exacte, seulement une zone.
     */
    private void hear(Vec3 source, boolean lockable) {
        this.pursuit.heardNoise();
        RandomSource random = this.getRandom();
        this.heardPoint = new Vec3(
                source.x + ListenerNoise.aimOffset(random.nextDouble()),
                source.y,
                source.z + ListenerNoise.aimOffset(random.nextDouble()));
        this.emptySweeps = 0;
        if (lockable && this.level() instanceof ServerLevel level) {
            beginClosing(level);
        } else if (this.getBehavior() != ListenerBehavior.CLOSING) {
            setBehavior(ListenerBehavior.SEEKING);
            this.listenCountdown = 0;
        }
    }

    /** Signale un bruit ponctuel extérieur (bloc cassé, chute, dégât). */
    void hearBurst(Vec3 source, double radius) {
        if (this.getBehavior() == ListenerBehavior.LEAVING) {
            return;
        }
        double distance = this.position().distanceTo(source);
        if (!ListenerNoise.audible(radius, distance)
                || !this.level().isLoaded(BlockPos.containing(source))) return;
        boolean clearPath = this.level().clip(new ClipContext(this.getEyePosition(),
                source.add(0.0, 0.5, 0.0), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
        radius = ListenerPursuit.heardRadius(radius, clearPath);
        if (ListenerNoise.audible(radius, distance)) {
            hear(source, clearPath && ListenerNoise.lockable(radius, distance));
        }
    }

    private void tickSeeking(ServerLevel level) {
        if (++this.seekingTicks >= 120) {
            // A navigable but unreachable sound must not cause a minute-long pursuit.
            beginLeaving(level, ownerPlayer(level));
            return;
        }
        if (heardPoint == null) {
            beginListening(level);
            return;
        }
        if (this.position().distanceTo(heardPoint) <= ARRIVAL_DISTANCE) {
            emptySweeps++;
            beginListening(level);
            return;
        }
        if (!level.isLoaded(BlockPos.containing(heardPoint))) {
            emptySweeps++;
            beginListening(level);
            return;
        }
        if (sceneTicks % 5 == 0) {
            boolean started = this.getNavigation().moveTo(
                    heardPoint.x, heardPoint.y, heardPoint.z, SEEK_SPEED);
            if (!started && sceneTicks > 20) {
                // Bruit inatteignable (derrière un mur, en hauteur) : elle
                // s'arrête où elle est et écoute. Elle ne traverse rien.
                emptySweeps++;
                beginListening(level);
            }
        }
    }

    private void tickListening(ServerLevel level) {
        this.getNavigation().stop();
        this.setDeltaMovement(this.getDeltaMovement().multiply(0.0, 1.0, 0.0));
        if (--listenCountdown > 0) {
            return;
        }
        if (emptySweeps >= ListenerNoise.MAX_EMPTY_SWEEPS) {
            beginLeaving(level, ownerPlayer(level));
            return;
        }
        // Elle balaie les alentours du dernier bruit plutôt que de renoncer.
        Vec3 base = heardPoint != null ? heardPoint : this.position();
        RandomSource random = this.getRandom();
        double angle = random.nextDouble() * Math.PI * 2.0;
        double radius = 4.0 + random.nextDouble() * 6.0;
        this.heardPoint = base.add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
        // Count only when tickSeeking finishes or fails this search. Counting
        // its start too made two blocked paths exhaust the three-search budget.
        setBehavior(ListenerBehavior.SEEKING);
    }

    private void tickClosing(ServerLevel level, ServerPlayer owner) {
        this.pursuit.tick();
        if (this.pursuit.exhausted()) {
            beginLeaving(level, owner);
            return;
        }
        if (this.pursuit.lostSound()) {
            beginListening(level);
            return;
        }
        if (!this.pursuit.warningComplete()) {
            // The lock sound and forward pose precede any rush or contact.
            this.getNavigation().stop();
            this.setDeltaMovement(this.getDeltaMovement().multiply(0.0, 1.0, 0.0));
            return;
        }
        Vec3 destination = heardPoint != null ? heardPoint : this.position();
        if (!level.isLoaded(BlockPos.containing(destination))) {
            beginListening(level);
            return;
        }
        if (sceneTicks % 3 == 0) {
            this.getNavigation().moveTo(
                    destination.x, destination.y, destination.z, CLOSE_SPEED);
        }
        if (owner == null || contactDone) {
            return;
        }
        if (this.pursuit.canContact(radiusOf(owner), this.distanceTo(owner),
                this.hasLineOfSight(owner))) {
            contact(level, owner);
        }
    }

    private void tickLeaving(ServerLevel level) {
        if (++this.leavingTicks >= MAX_LEAVING_TICKS || leavePoint == null) {
            vanish(level);
            return;
        }
        if (this.position().distanceTo(leavePoint) <= 2.0) {
            vanish(level);
            return;
        }
        if (sceneTicks % 6 == 0 || this.getNavigation().isDone()) {
            if (!this.getNavigation().moveTo(
                    leavePoint.x, leavePoint.y, leavePoint.z, LEAVE_SPEED)) {
                vanish(level);
            }
        }
    }

    private void beginListening(ServerLevel level) {
        setBehavior(ListenerBehavior.LISTENING);
        this.listenCountdown = ListenerNoise.listenDuration(this.getRandom().nextInt());
        this.pursuit.start();
        this.getNavigation().stop();
        playSceneSound(ownerPlayer(level), PenumbraSounds.LISTENER_INHALE,
                this.getEyePosition(), 0.42f, 0.94f + this.getRandom().nextFloat() * 0.1f);
    }

    private void beginClosing(ServerLevel level) {
        if (this.getBehavior() == ListenerBehavior.CLOSING) {
            return;
        }
        setBehavior(ListenerBehavior.CLOSING);
        this.pursuit.start();
        this.getNavigation().stop();
        playSceneSound(ownerPlayer(level), PenumbraSounds.LISTENER_LOCK,
                this.getEyePosition(), 0.78f, 0.96f + this.getRandom().nextFloat() * 0.12f);
    }

    private void beginLeaving(ServerLevel level, ServerPlayer owner) {
        setBehavior(ListenerBehavior.LEAVING);
        this.leavingTicks = 0;
        this.getNavigation().stop();
        Vec3 away = owner != null
                ? this.position().subtract(owner.position())
                : this.getLookAngle();
        away = away.multiply(1.0, 0.0, 1.0);
        away = away.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
        this.leavePoint = this.position().add(away.scale(30.0));
        playSceneSound(owner, PenumbraSounds.LISTENER_LEAVE,
                this.position(), 0.5f, 0.92f);
    }

    /** Contact réel : dégâts optionnels, choc spatial, puis départ de la créature. */
    private void contact(ServerLevel level, ServerPlayer owner) {
        contactDone = true;
        PenumbraConfig config = PenumbraConfig.get();
        if (config.listenerCanHarm) {
            this.doHurtTarget(level, owner);
        }
        if (config.jumpscareFrequency > 0.0f) {
            playSceneSound(owner, PenumbraSounds.LISTENER_LOCK, this.getEyePosition(), 0.85f, 0.72f);
            PenumbraNetworking.sendJumpscare(owner, "impact", 0.65f);
        }
        level.sendParticles(PenumbraParticles.ASH_MOTE,
                this.getX(), this.getY() + 1.7, this.getZ(),
                28, 0.36, 0.9, 0.36, 0.014);
        HorrorDirector.noteManifestation(owner);
        beginLeaving(level, owner);
    }

    /** Clic sec d'écholocation, plus rapide pendant la charge. */
    private void tickVoice(ServerLevel level, ServerPlayer owner) {
        int interval = switch (this.getBehavior()) {
            case CLOSING -> 7;
            case LISTENING -> 26;
            case LEAVING -> 34;
            default -> 15;
        };
        if (sceneTicks % interval != 0) {
            return;
        }
        float pitch = this.getBehavior() == ListenerBehavior.CLOSING ? 1.28f : 1.0f;
        playSceneSound(owner, PenumbraSounds.LISTENER_CLICK,
                this.getEyePosition(), 0.36f,
                pitch + this.getRandom().nextFloat() * 0.14f);
    }

    /**
     * Oriente corps et tête vers le dernier bruit. Rien ne pointe vers le joueur :
     * c'est la seule information dont le joueur a besoin pour comprendre la règle.
     */
    private void aimHeadAtNoise() {
        Vec3 target = heardPoint;
        if (this.getBehavior() == ListenerBehavior.LEAVING && leavePoint != null) {
            target = leavePoint;
        }
        if (target == null) {
            return;
        }
        Vec3 delta = target.subtract(this.position());
        if (delta.horizontalDistanceSqr() < 1.0E-6) {
            return;
        }
        float yaw = (float) (Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0f;
        this.entityData.set(DATA_HEAR_YAW, Mth.wrapDegrees(yaw));
        this.setYRot(yaw);
        this.setYBodyRot(yaw);
        this.setYHeadRot(yaw);
    }

    private ServerPlayer ownerPlayer(ServerLevel level) {
        if (ownerUuid == null || level.getServer() == null) {
            return null;
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerUuid);
        return owner != null && owner.level() == level ? owner : null;
    }

    private void vanish(ServerLevel level) {
        if (this.isRemoved()) {
            return;
        }
        this.getNavigation().stop();
        level.sendParticles(ParticleTypes.SMOKE,
                this.getX(), this.getY() + 1.4, this.getZ(), 8, 0.22, 0.7, 0.22, 0.006);
        ListenerManager.release(ownerUuid, this.getUUID());
        this.discard();
    }

    @Override
    public void onRemoval(Entity.RemovalReason reason) {
        if (!this.level().isClientSide()) {
            ListenerManager.release(ownerUuid, this.getUUID());
        }
        super.onRemoval(reason);
    }

    // ===================== Fabrique =====================

    /** Vrai si une scène de L'Écouteur appartenant à ce joueur existe déjà à portée. */
    public static boolean hasOwnedNearby(
            ServerLevel level, ServerPlayer owner, double radius) {
        return !level.getEntitiesOfClass(ListenerEntity.class,
                owner.getBoundingBox().inflate(radius),
                entity -> entity.isAlive() && entity.isOwnedBy(owner)).isEmpty();
    }

    /** Toutes les scènes vivantes de ce joueur, pour les bruits ponctuels. */
    static List<ListenerEntity> ownedNear(
            ServerLevel level, ServerPlayer owner, double radius) {
        return level.getEntitiesOfClass(ListenerEntity.class,
                owner.getBoundingBox().inflate(radius),
                entity -> entity.isAlive() && entity.isOwnedBy(owner));
    }

    /**
     * Apparition hors du champ de vision, entre {@code minDistance} et
     * {@code maxDistance} blocs, sur un sol valide et sans collision. Elle
     * commence toujours par écouter : le joueur a le temps de ne rien faire.
     */
    public static boolean spawnNear(ServerLevel level, ServerPlayer owner,
                                    double minDistance, double maxDistance) {
        if (!ListenerManager.mayCreate(owner)) {
            return false;
        }
        RandomSource random = owner.getRandom();
        Vec3 look = owner.getLookAngle().multiply(1.0, 0.0, 1.0);
        look = look.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : look.normalize();
        double rearAngle = Math.atan2(-look.z, -look.x);
        double span = Math.max(1.0, maxDistance - minDistance);

        for (int attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
            double angle = rearAngle + Math.toRadians(-95.0 + random.nextDouble() * 190.0);
            double distance = minDistance + random.nextDouble() * span;
            Vec3 guess = owner.position().add(
                    Math.cos(angle) * distance, 0.0, Math.sin(angle) * distance);
            BlockPos ground = findGround(level, guess, Mth.floor(owner.getY()));
            if (ground == null) {
                continue;
            }
            if (create(level, owner, ground)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Apparition de présentation ({@code listener_passage}) : elle naît loin, déjà
     * occupée par un bruit situé de l'autre côté du joueur, et traverse donc le
     * champ visuel sans jamais s'en approcher. Sa tête est tournée vers sa
     * destination — le joueur apprend la règle en la regardant, pas en la lisant.
     */
    public static boolean spawnPassage(ServerLevel level, ServerPlayer owner,
                                       double minDistance, double maxDistance) {
        if (!ListenerManager.mayCreate(owner)) {
            return false;
        }
        RandomSource random = owner.getRandom();
        double span = Math.max(1.0, maxDistance - minDistance);
        for (int attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = minDistance + random.nextDouble() * span;
            Vec3 offset = new Vec3(Math.cos(angle) * distance, 0.0, Math.sin(angle) * distance);
            Vec3 guess = owner.position().add(offset);
            BlockPos ground = findGround(level, guess, Mth.floor(owner.getY()));
            if (ground == null) {
                continue;
            }
            // Destination diamétralement opposée au point d'apparition : la
            // trajectoire est tangente au joueur, jamais dirigée vers lui.
            Vec3 tangent = new Vec3(-offset.z, 0.0, offset.x).normalize().scale(22.0);
            Vec3 destination = guess.add(tangent);
            if (create(level, owner, ground, destination)) {
                return true;
            }
        }
        return false;
    }

    private static boolean create(ServerLevel level, ServerPlayer owner, BlockPos ground) {
        return create(level, owner, ground, null);
    }

    private static boolean create(ServerLevel level, ServerPlayer owner,
                                  BlockPos ground, Vec3 passageTarget) {
        ListenerEntity entity = new ListenerEntity(ListenerEntities.LISTENER, level);
        Vec3 pos = Vec3.atBottomCenterOf(ground);
        // Elle regarde toujours son bruit : le joueur pour une apparition normale,
        // sa destination tangente pour une présentation.
        Vec3 delta = (passageTarget != null ? passageTarget : owner.position()).subtract(pos);
        float yaw = delta.horizontalDistanceSqr() < 1.0E-6
                ? 0.0f
                : (float) (Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0f;
        entity.snapTo(pos.x, pos.y, pos.z, yaw, 0.0f);
        entity.setYBodyRot(yaw);
        entity.setYHeadRot(yaw);
        entity.ownerUuid = owner.getUUID();
        if (!level.noCollision(entity)) {
            return false;
        }
        entity.finalizeSpawn(level, level.getCurrentDifficultyAt(ground),
                EntitySpawnReason.EVENT, null);
        if (!level.addFreshEntity(entity)) {
            return false;
        }
        if (passageTarget != null) {
            // Présentation : elle part immédiatement vers un bruit qui n'est pas
            // le joueur et traverse le décor sans le chercher.
            entity.heardPoint = passageTarget;
            entity.setBehavior(ListenerBehavior.SEEKING);
        } else {
            // Elle n'arrive jamais en chasse : elle arrive en écoute. Le joueur a
            // toujours une fenêtre pour comprendre et s'immobiliser.
            entity.setBehavior(ListenerBehavior.LISTENING);
            entity.listenCountdown = ListenerNoise.listenDuration(owner.getRandom().nextInt());
            entity.heardPoint = pos;
        }
        ListenerManager.track(owner.getUUID(), entity.getUUID());
        playSceneSound(owner, PenumbraSounds.LISTENER_INHALE,
                entity.getEyePosition(), 0.5f, 0.9f);
        return true;
    }

    private static BlockPos findGround(ServerLevel level, Vec3 guess, int baseY) {
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
                    && level.getBlockState(cursor.above()).isAir()
                    && level.getFluidState(below).isEmpty()
                    && level.getFluidState(cursor).isEmpty()) {
                return cursor.immutable();
            }
        }
        return null;
    }
}

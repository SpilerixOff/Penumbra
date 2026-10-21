package fr.penumbra.entity.watcher;

import fr.penumbra.badge.PenumbraBadges;
import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.entity.HostEntity;
import fr.penumbra.network.PenumbraNetworking;
import fr.penumbra.registry.PenumbraParticles;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * L'Observateur ({@code penumbra:watcher}) — humanoïde de ~2,4 blocs qui apparaît
 * à distance et FIXE le joueur, immobile. Phase 4 (SPEC §contenu 3D).
 * <p>
 * AUCUN goal vanilla et AUCUN déplacement libre : toute la logique vit dans
 * {@link #tick()} côté serveur. {@code setNoAi(true)} est posé au constructeur —
 * acceptable ici car l'entité est à durée de vie bornée ({@value #MAX_LIFETIME_TICKS}
 * ticks) avec {@code discard()} garanti sur TOUS les chemins (regard soutenu, proximité,
 * timeout, mode paisible) : aucune fuite d'entité persistante n'est possible.
 * </p>
 * <p>
 * Comportement : le joueur le plus proche à ≤ {@value #TRACK_RADIUS} blocs est suivi
 * du regard (corps ET tête, interpolation ~10 ticks, tête légèrement plus lente pour un
 * effet mécanique). Si ce joueur (ou tout autre joueur à portée) soutient le regard —
 * angle &lt; 4° entre son vecteur de vue et la direction œil→Observateur, distance ≥ 8
 * blocs, ligne de vue dégagée — pendant {@code watcherStareSeconds} secondes, il reçoit
 * le badge {@code "you_see_me"} (UNE FOIS par joueur par Observateur), l'Observateur
 * bondit vers son visage pendant 8–12 ticks, frappe, déclenche un jumpscare,
 * puis disparaît en cendre. À moins de {@value #VANISH_DISTANCE}
 * blocs hors lunge : disparition immédiate (jamais de contact).
 * </p>
 * <p>
 * Budget CPU : recherche de joueur amortie 1/{@value #PLAYER_SCAN_INTERVAL} ticks,
 * détection de regard 1/{@value #STARE_CHECK_INTERVAL} ticks — aucun scan par tick.
 * </p>
 */
public class WatcherEntity extends Monster {

    /** Duree et tick de lunge encodes par {@link WatcherLunge}; 0 hors lunge. */
    private static final EntityDataAccessor<Integer> DATA_LUNGE_STATE =
            SynchedEntityData.defineId(WatcherEntity.class, EntityDataSerializers.INT);

    /** Rayon de suivi du joueur le plus proche (blocs). */
    private static final double TRACK_RADIUS = 48.0;
    /** Distance minimale pour qu'un regard compte (blocs). */
    private static final double STARE_MIN_DISTANCE = 8.0;
    /** En dessous de cette distance : disparition immédiate, jamais de contact. */
    private static final double VANISH_DISTANCE = 6.0;
    /** Seuil d'angle du regard soutenu : cosinus de 4°. */
    private static final double STARE_DOT_THRESHOLD = Math.cos(Math.toRadians(4.0));
    /** Durée de vie maximale : ~60 s, puis disparition en cendre silencieuse. */
    private static final int MAX_LIFETIME_TICKS = 20 * 60;
    /** Recherche du joueur le plus proche : 1 tick sur 10. */
    private static final int PLAYER_SCAN_INTERVAL = 10;
    /** Détection du regard soutenu : 1 tick sur 5 (amorti). */
    private static final int STARE_CHECK_INTERVAL = 5;
    /** Facteur d'interpolation du corps vers la cible (~10 ticks pour s'aligner). */
    private static final float BODY_TURN_FACTOR = 0.10f;
    /** Facteur d'interpolation de la tête — plus lent : suivi légèrement mécanique. */
    private static final float HEAD_TURN_FACTOR = 0.075f;
    /** Rayon du comptage de population (Hôtes + Observateurs) avant apparition. */
    private static final double POPULATION_RADIUS = 96.0;
    /** Essais de placement avant abandon d'une apparition. */
    private static final int SPAWN_ATTEMPTS = 12;
    /** Amplitude de la recherche verticale de sol (au-dessus / en dessous du joueur). */
    private static final int SCAN_UP = 8;
    private static final int SCAN_DOWN = 32;

    /** Joueur actuellement suivi du regard (état serveur transitoire, non persistant). */
    private ServerPlayer trackedPlayer;
    /** Ticks écoulés depuis l'apparition. */
    private int lifetimeTicks;
    private UUID ownerUuid;
    /** Progression du regard soutenu par joueur (ticks cumulés, réinitialisée dès que le regard se brise). */
    private final Map<UUID, Integer> stareProgress = new HashMap<>();
    /** Joueurs ayant déjà reçu le badge pour CET Observateur (une seule attribution par joueur). */
    private final Set<UUID> badgesGranted = new HashSet<>();
    /** Point de depart serveur de la trajectoire 3D (la cible finale suit le visage). */
    private Vec3 lungeStartPosition;

    public WatcherEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        // Aucune IA vanilla : l'entité est pilotée intégralement par tick(). Sûr ici :
        // durée de vie bornée + discard garanti sur tous les chemins (voir en-tête).
        this.setNoAi(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_LUNGE_STATE, 0);
    }

    /** Vrai sur les deux cotes pendant la lunge synchronisee. */
    public boolean isLunging() {
        return this.entityData.get(DATA_LUNGE_STATE) != 0;
    }

    /** Progression 0-1 interpolee pour le render state client. */
    public float getLungeProgress(float partialTick) {
        return WatcherLunge.progress(this.entityData.get(DATA_LUNGE_STATE), partialTick);
    }

    /**
     * Attributs de l'Observateur : HP 20, vitesse 0 (immobile), followRange 48,
     * knockbackResistance 1.0 et frappe de quatre points après la charge.
     */
    public static AttributeSupplier.Builder createWatcherAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.FOLLOW_RANGE, TRACK_RADIUS)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    /** Aucun goal vanilla : comportement entièrement dans {@link #tick()}. */
    @Override
    protected void registerGoals() {
        // volontairement vide — aucune IA vanilla (voir en-tête de classe)
    }

    /**
     * Désarmé volontairement, comme l'Hôte : la fin de vie de l'Observateur est gérée
     * exclusivement par {@link #tick()} (regard, proximité, timeout, paisible).
     */
    @Override
    public void checkDespawn() {
        // no-op intentionnel
    }

    /** Une scène courte ne doit jamais survivre à un unload ou redémarrage. */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    /** Knockback ignoré : l'Observateur ne bouge jamais. */
    @Override
    public void knockback(double strength, double x, double z) {
        // no-op intentionnel
    }

    /** Ne peut pas être poussé par d'autres entités. */
    @Override
    public boolean isPushable() {
        return false;
    }

    public void setOwner(ServerPlayer owner) {
        this.ownerUuid = owner.getUUID();
        this.trackedPlayer = owner;
    }

    /** Vrai uniquement pour le joueur auquel cette manifestation est liée. */
    public boolean isOwnedBy(Entity entity) {
        return this.ownerUuid != null && this.ownerUuid.equals(entity.getUUID());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.lifetimeTicks = Math.max(0, input.getIntOr("PenumbraLifetime", 0));
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
        // Immobile : toute vélocité résiduelle est annulée chaque tick.
        this.setDeltaMovement(Vec3.ZERO);

        // Despawn propre et silencieux si le monde passe en mode paisible.
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            this.discard();
            return;
        }

        if (this.ownerUuid != null) {
            ServerPlayer owner = this.ownerPlayer(level);
            if (owner == null || !owner.isAlive() || owner.isSpectator()
                    || owner.isRemoved() || owner.isSleeping()
                    || this.distanceToSqr(owner) > TRACK_RADIUS * TRACK_RADIUS) {
                this.vanishInAsh(level);
                return;
            }
            this.trackedPlayer = owner;
        }

        // La lunge est terminale et prioritaire : ni le timeout ni surtout la
        // regle de proximite ne doivent la couper lorsqu'elle arrive au visage.
        if (this.isLunging()) {
            this.tickLunge(level);
            return;
        }

        // Durée de vie bornée : disparition en cendre, silencieuse.
        if (++this.lifetimeTicks > MAX_LIFETIME_TICKS) {
            this.vanishInAsh(level);
            return;
        }

        // Recherche du joueur le plus proche, amortie (1/10 ticks) ; invalidation
        // immédiate si la cible suivie est retirée ou a changé de dimension.
        if (this.trackedPlayer != null
                && (this.trackedPlayer.isRemoved() || this.trackedPlayer.level() != level)) {
            this.trackedPlayer = null;
        }
        if (this.tickCount % PLAYER_SCAN_INTERVAL == 0) {
            this.trackedPlayer = findNearestPlayer(level);
        }

        ServerPlayer player = this.trackedPlayer;
        if (player != null) {
            double distSqr = this.distanceToSqr(player);
            // Jamais de contact : disparition en cendre immédiate.
            if (distSqr < VANISH_DISTANCE * VANISH_DISTANCE) {
                this.vanishInAsh(level);
                return;
            }
            if (distSqr <= TRACK_RADIUS * TRACK_RADIUS) {
                this.faceTowards(player);
            }
        }

        // Détection du regard soutenu, amortie (1/5 ticks).
        if (this.tickCount % STARE_CHECK_INTERVAL == 0) {
            this.updateStareDetection(level);
        }
    }

    /** Joueur non-spectateur le plus proche à ≤ {@value #TRACK_RADIUS} blocs, ou null. */
    private ServerPlayer findNearestPlayer(ServerLevel level) {
        if (this.ownerUuid != null) {
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(this.ownerUuid);
            return owner != null && owner.level() == level
                    && this.distanceToSqr(owner) <= TRACK_RADIUS * TRACK_RADIUS ? owner : null;
        }
        ServerPlayer nearest = null;
        double nearestSqr = TRACK_RADIUS * TRACK_RADIUS;
        for (ServerPlayer candidate : level.players()) {
            if (candidate.isSpectator()) {
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
     * Oriente corps ET tête vers le joueur, en interpolation douce (~10 ticks) ;
     * la tête converge un peu plus lentement (suivi légèrement mécanique).
     */
    private void faceTowards(ServerPlayer player) {
        Vec3 delta = player.getEyePosition().subtract(this.getEyePosition());
        float targetYaw = (float) (Math.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0f;
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float targetPitch = (float) (-Math.atan2(delta.y, horizontal) * Mth.RAD_TO_DEG);

        this.setYRot(Mth.rotLerp(BODY_TURN_FACTOR, this.getYRot(), targetYaw));
        this.setYBodyRot(this.getYRot());
        this.setYHeadRot(Mth.rotLerp(HEAD_TURN_FACTOR, this.getYHeadRot(), targetYaw));
        this.setXRot(Mth.rotLerp(HEAD_TURN_FACTOR, this.getXRot(), targetPitch));
    }

    /**
     * Regard soutenu (appelé 1/5 ticks) : pour chaque joueur à ≤ {@value #TRACK_RADIUS}
     * blocs, angle &lt; 4° entre {@code getViewVector(1.0F)} et la direction œil-joueur →
     * œil-Observateur, distance ≥ {@value #STARE_MIN_DISTANCE} blocs et ligne de vue
     * dégagée ({@code Level.clip} en 26.1.2 : {@code BlockGetter.clip(ClipContext)}).
     * Au seuil {@code watcherStareSeconds} : badge « you_see_me » (une fois par joueur),
     * puis lunge terminale, frappe et screamer vers le visage du propriétaire.
     */
    private void updateStareDetection(ServerLevel level) {
        int thresholdTicks = PenumbraConfig.get().watcherStareSeconds * 20;
        Vec3 watcherEye = this.getEyePosition();
        for (ServerPlayer candidate : level.players()) {
            if (candidate.isSpectator() || candidate.isSleeping()
                    || (this.ownerUuid != null && !this.ownerUuid.equals(candidate.getUUID()))) {
                continue;
            }
            if (this.distanceToSqr(candidate) > TRACK_RADIUS * TRACK_RADIUS) {
                this.stareProgress.remove(candidate.getUUID());
                continue;
            }
            if (!this.isStaredAtBy(candidate, watcherEye)) {
                this.stareProgress.remove(candidate.getUUID());
                continue;
            }
            int progress = this.stareProgress.merge(candidate.getUUID(), STARE_CHECK_INTERVAL, Integer::sum);
            if (progress >= thresholdTicks && this.badgesGranted.add(candidate.getUUID())) {
                PenumbraBadges.grant(candidate, "you_see_me");
                this.startLunge(candidate);
                return;
            }
        }
    }

    /** Arme la trajectoire terminale et publie immediatement son etat au client. */
    private void startLunge(ServerPlayer owner) {
        int duration = WatcherLunge.MIN_DURATION_TICKS
                + this.random.nextInt(WatcherLunge.MAX_DURATION_TICKS
                - WatcherLunge.MIN_DURATION_TICKS + 1);
        this.lungeStartPosition = this.position();
        // Un Observateur invoque sans proprietaire se lie au premier joueur qui
        // soutient son regard ; les apparitions normales conservent leur owner.
        this.setOwner(owner);
        this.stareProgress.clear();
        this.setNoGravity(true);
        this.setInvulnerable(true);
        this.noPhysics = true;
        this.entityData.set(DATA_LUNGE_STATE, WatcherLunge.pack(0, duration));
    }

    /**
     * Avance d'un tick selon une smoothstep 3D vers un point a 70 cm du visage.
     * La position finale suit le proprietaire s'il bouge, mais la direction reste
     * issue du point de depart pour eviter les oscillations. Aucun goal, attaque ou
     * collision n'est invoque pendant cette trajectoire.
     */
    private void tickLunge(ServerLevel level) {
        ServerPlayer owner = this.ownerPlayer(level);
        if (owner == null || !owner.isAlive() || owner.isSpectator()
                || owner.isRemoved() || owner.isSleeping()
                || !this.hasLineOfSight(owner)) {
            this.vanishInAsh(level);
            return;
        }

        int state = this.entityData.get(DATA_LUNGE_STATE);
        int duration = WatcherLunge.durationTicks(state);
        int elapsed = Math.min(duration, WatcherLunge.elapsedTicks(state) + 1);
        this.entityData.set(DATA_LUNGE_STATE, WatcherLunge.pack(elapsed, duration));

        if (this.lungeStartPosition == null) {
            this.lungeStartPosition = this.position();
        }
        Vec3 targetEye = owner.getEyePosition();
        Vec3 startEye = this.lungeStartPosition.add(0.0, this.getEyeHeight(), 0.0);
        Vec3 faceToStart = startEye.subtract(targetEye);
        if (faceToStart.lengthSqr() < 1.0E-6) {
            faceToStart = owner.getLookAngle();
        }
        Vec3 endEye = targetEye.add(faceToStart.normalize().scale(0.70));
        Vec3 endFeet = endEye.subtract(0.0, this.getEyeHeight(), 0.0);
        double eased = WatcherLunge.easedProgress(elapsed, duration);
        this.setPos(this.lungeStartPosition.lerp(endFeet, eased));
        this.setDeltaMovement(Vec3.ZERO);
        this.faceTowards(owner);

        if (elapsed >= duration) {
            this.doHurtTarget(level, owner);
            String type = PenumbraConfig.get().screamersEnabled
                    ? "lunge_screamer" : "apparition";
            PenumbraNetworking.sendJumpscare(owner, type, 1.0f);
            this.vanishInAsh(level);
        }
    }

    /** Proprietaire present dans la dimension de cette manifestation, ou null. */
    private ServerPlayer ownerPlayer(ServerLevel level) {
        if (this.ownerUuid == null) {
            return null;
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(this.ownerUuid);
        return owner != null && owner.level() == level ? owner : null;
    }

    /** Vrai si {@code candidate} fixe précisément l'Observateur (angle, distance, ligne de vue). */
    private boolean isStaredAtBy(ServerPlayer candidate, Vec3 watcherEye) {
        Vec3 playerEye = candidate.getEyePosition();
        if (playerEye.distanceTo(watcherEye) < STARE_MIN_DISTANCE) {
            return false;
        }
        Vec3 toWatcher = watcherEye.subtract(playerEye).normalize();
        Vec3 view = candidate.getViewVector(1.0F).normalize();
        if (view.dot(toWatcher) < STARE_DOT_THRESHOLD) {
            return false;
        }
        BlockHitResult hit = this.level().clip(new ClipContext(playerEye, watcherEye,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, candidate));
        return hit.getType() == HitResult.Type.MISS;
    }

    /**
     * Dissolution en cendre silencieuse (particules ASH_MOTE existantes, aucun son),
     * puis {@code discard()}. Employée pour le timeout, la proximité et la fin
     * de lunge après le jumpscare.
     */
    private void vanishInAsh(ServerLevel level) {
        level.sendParticles(PenumbraParticles.ASH_MOTE,
                this.getX(), this.getY() + 1.2, this.getZ(),
                24, 0.4, 0.9, 0.4, 0.01);
        this.discard();
    }

    /**
     * Vrai si au moins un Observateur se trouve dans le rayon donné autour de
     * {@code pos}. Miroir de {@code HostEntity.hasAnyHostNearby}, pour les événements
     * et le comptage de population de l'intégrateur.
     */
    public static boolean hasAnyWatcherNearby(ServerLevel level, BlockPos pos, double radius) {
        return !level.getEntitiesOfClass(WatcherEntity.class, new AABB(pos).inflate(radius)).isEmpty();
    }

    /**
     * Cap de population : vrai si Hôtes + Observateurs à ≤ {@value #POPULATION_RADIUS}
     * blocs du joueur cible atteignent {@code maxHostEntitiesPerPlayer}. Les événements
     * doivent refuser l'apparition dans ce cas.
     */
    public static boolean populationCapReached(ServerLevel level, ServerPlayer target) {
        AABB box = new AABB(target.blockPosition()).inflate(POPULATION_RADIUS);
        int count = level.getEntitiesOfClass(HostEntity.class, box,
                        host -> host.isOwnedBy(target)).size()
                + level.getEntitiesOfClass(WatcherEntity.class, box,
                        watcher -> watcher.isOwnedBy(target)).size();
        return count >= PenumbraConfig.get().maxHostEntitiesPerPlayer;
    }

    /**
     * Fait apparaître un Observateur à {@code minDist..maxDist} blocs du joueur, dans
     * le cône de regard horizontal (±{@code coneHalfAngleDegrees} autour du yaw du
     * joueur), sur sol solide avec 3 blocs d'air (l'entité mesure 2,4 blocs).
     * L'Observateur est orienté vers le joueur dès l'apparition.
     * Pattern repris de {@code BinocularsSightingEvent.spawnInGazeCone} : direction du
     * regard 26.1 {@code (-sin(yaw), cos(yaw))}, balayage vertical borné, jamais de
     * chunk forcé.
     *
     * @return l'Observateur invoqué (déjà ajouté au monde), ou {@code null} si aucun
     *         emplacement valide en {@value #SPAWN_ATTEMPTS} essais (événement annulé,
     *         aucun effet).
     */
    public static WatcherEntity spawnInGazeCone(ServerLevel level, ServerPlayer player,
                                                double minDist, double maxDist,
                                                float coneHalfAngleDegrees) {
        return spawnInGazeCone(level, player, minDist, maxDist,
                player.getYRot(), coneHalfAngleDegrees);
    }

    /**
     * Variante centrée sur un yaw arbitraire (arc de cercle de {@code watcher_group}).
     * {@code centerYawDegrees} remplace le yaw du joueur comme axe du cône.
     */
    public static WatcherEntity spawnInGazeCone(ServerLevel level, ServerPlayer player,
                                                double minDist, double maxDist,
                                                float centerYawDegrees, float coneHalfAngleDegrees) {
        RandomSource random = player.getRandom();
        for (int attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
            float yawDegrees = centerYawDegrees + (random.nextFloat() * 2.0f - 1.0f) * coneHalfAngleDegrees;
            double yaw = Math.toRadians(yawDegrees);
            double distance = minDist + random.nextDouble() * Math.max(1.0, maxDist - minDist);
            int baseX = Mth.floor(player.getX() - Mth.sin(yaw) * distance);
            int baseZ = Mth.floor(player.getZ() + Mth.cos(yaw) * distance);
            int baseY = Mth.floor(player.getY());

            BlockPos spot = findStandingSpot(level, baseX, baseY, baseZ);
            if (spot == null) {
                continue;
            }

            WatcherEntity watcher = new WatcherEntity(WatcherEntities.WATCHER, level);
            watcher.setOwner(player);
            double x = spot.getX() + 0.5;
            double z = spot.getZ() + 0.5;
            // Yaw orienté vers le joueur : (-sin(yaw), cos(yaw)) doit pointer sur lui.
            float faceYaw = (float) Math.toDegrees(Mth.atan2(-(player.getX() - x), player.getZ() - z));
            watcher.snapTo(x, spot.getY(), z, faceYaw, 0.0f);
            watcher.setYBodyRot(faceYaw);
            watcher.setYHeadRot(faceYaw);
            watcher.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), EntitySpawnReason.EVENT, null);
            if (level.addFreshEntity(watcher)) {
                return watcher;
            }
            return null;
        }
        return null;
    }

    /**
     * Emplacement debout valide à la verticale de (x, z) : chunk chargé, sol solide,
     * 3 blocs d'air (hitbox de 2,4 blocs de haut), aucun liquide. Balayage en ligne
     * borné à {@value #SCAN_UP}/{@value #SCAN_DOWN} blocs — jamais de scan volumique.
     */
    private static BlockPos findStandingSpot(ServerLevel level, int baseX, int baseY, int baseZ) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = SCAN_UP; dy >= -SCAN_DOWN; dy--) {
            cursor.set(baseX, baseY + dy, baseZ);
            if (!level.isLoaded(cursor)) {
                return null; // hors zone chargée : abandon
            }
            if (isValidStandingSpot(level, cursor)) {
                return cursor.immutable();
            }
        }
        return null;
    }

    /** Emplacement debout valide : sol solide, pieds/tête/chapeau à l'air, aucun fluide. */
    private static boolean isValidStandingSpot(ServerLevel level, BlockPos feet) {
        if (!level.getBlockState(feet.below()).isSolidRender()) {
            return false;
        }
        if (!level.getBlockState(feet).isAir()
                || !level.getBlockState(feet.above()).isAir()
                || !level.getBlockState(feet.above(2)).isAir()) {
            return false;
        }
        return level.getFluidState(feet.below()).isEmpty()
                && level.getFluidState(feet).isEmpty()
                && level.getFluidState(feet.above()).isEmpty()
                && level.getFluidState(feet.above(2)).isEmpty();
    }
}

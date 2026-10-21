package fr.penumbra.entity.ai;

import fr.penumbra.data.PenumbraPersistentState;
import fr.penumbra.director.PlayerHorrorProfile;
import fr.penumbra.entity.HostBehavior;
import fr.penumbra.entity.HostEntity;
import fr.penumbra.story.StoryManager;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * Observation passive : en OBSERVING ou DORMANT, l'Hôte reste immobile et fixe le
 * joueur le plus proche (≤ 32 blocs). Si le joueur s'approche à moins de 8 blocs,
 * l'Hôte recule de 6 blocs et passe en RETREATING ; s'il n'est pas observé pendant
 * 300 ticks cumulés, il passe en STALKING.
 * <p>
 * Phase 2 (SPEC2 §7) : au début d'une session d'observation, 30 % de chances de
 * gagner d'abord un point d'observation situé à 8–16 blocs d'un
 * {@code homePoint} ou d'une cellule fréquente du profil de la cible
 * (mémoire comportementale), si ce point est à moins de 48 blocs du joueur.
 * </p>
 */
public class ObservePlayerGoal extends Goal {

    private static final double DETECT_RANGE = 32.0;
    private static final double KEEP_RANGE = 40.0;
    private static final double RETREAT_DISTANCE = 8.0;
    private static final int STALK_AFTER_TICKS = 300;
    /** Chance d'exploiter la mémoire comportementale au début d'une observation. */
    private static final float MEMORY_POST_CHANCE = 0.30f;
    /** Distance minimale du point d'observation à l'ancre mémorisée. */
    private static final double POST_MIN_DIST = 8.0;
    /** Distance maximale du point d'observation à l'ancre mémorisée. */
    private static final double POST_MAX_DIST = 16.0;
    /** L'ancre mémorisée n'est exploitée que si elle est à moins de cette distance du joueur. */
    private static final double POST_PLAYER_RANGE = 48.0;
    /** Vitesse de déplacement vers le point d'observation. */
    private static final double POST_SPEED = 0.8;
    /** Essais de placement du point d'observation. */
    private static final int POST_ATTEMPTS = 8;

    private final HostEntity host;
    private ServerPlayer target;
    private int ticksSinceSeen;
    /** Vrai tant que l'Hôte se déplace vers un point d'observation mémorisé. */
    private boolean movingToPost;
    private boolean refugeDiscoveryNoted;

    public ObservePlayerGoal(HostEntity host) {
        this.host = host;
        this.setFlags(EnumSet.of(Goal.Flag.LOOK, Goal.Flag.MOVE));
    }

    private boolean isObservingBehavior() {
        HostBehavior behavior = this.host.getBehavior();
        return behavior == HostBehavior.OBSERVING || behavior == HostBehavior.DORMANT;
    }

    @Override
    public boolean canUse() {
        if (!isObservingBehavior() || !(this.host.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        ServerPlayer owner = this.host.getOwnerPlayer();
        if (owner != null) {
            this.target = owner.distanceTo(this.host) <= DETECT_RANGE ? owner : null;
            return this.target != null;
        }
        if (this.host.hasOwner()) {
            return false;
        }
        ServerPlayer nearest = null;
        double best = DETECT_RANGE;
        for (ServerPlayer player : serverLevel.players()) {
            if (player.isSpectator()) {
                continue;
            }
            double dist = player.distanceTo(this.host);
            if (dist < best) {
                best = dist;
                nearest = player;
            }
        }
        this.target = nearest;
        if (nearest != null) {
            this.host.setOwner(nearest);
        }
        return nearest != null;
    }

    @Override
    public boolean canContinueToUse() {
        return this.target != null
                && this.target.isAlive()
                && this.host.isOwnedBy(this.target)
                && this.target.level() == this.host.level()
                && isObservingBehavior()
                && this.target.distanceTo(this.host) <= KEEP_RANGE;
    }

    @Override
    public void start() {
        this.ticksSinceSeen = 0;
        this.refugeDiscoveryNoted = false;
        this.movingToPost = tryMoveToMemoryPost();
    }

    @Override
    public void stop() {
        this.target = null;
        this.movingToPost = false;
    }

    @Override
    public void tick() {
        if (this.target == null) {
            return;
        }
        if (this.movingToPost) {
            if (this.host.getNavigation().isDone()) {
                // Point atteint (ou inaccessible) : observation immobile classique.
                this.movingToPost = false;
            }
        } else {
            this.host.getNavigation().stop();
        }
        this.host.getLookControl().setLookAt(this.target, 30.0f, 30.0f);

        if (!this.movingToPost && !this.refugeDiscoveryNoted
                && this.target.hasLineOfSight(this.host)) {
            PlayerHorrorProfile profile = PenumbraPersistentState.get(
                    this.target.level().getServer()).profile(this.target.getUUID());
            if (profile.isNearRefuge(this.host.blockPosition(), POST_MAX_DIST + 4.0)) {
                StoryManager.noteDiscovery(this.target, StoryManager.KEY_REFUGE_HOST_SEEN);
                this.refugeDiscoveryNoted = true;
            }
        }

        // Joueur trop proche : recul de 6 blocs puis retrait.
        if (this.host.distanceTo(this.target) < RETREAT_DISTANCE) {
            Vec3 away = this.host.position().subtract(this.target.position());
            Vec3 flat = new Vec3(away.x, 0.0, away.z);
            if (flat.lengthSqr() < 1.0E-4) {
                flat = new Vec3(1.0, 0.0, 0.0);
            }
            flat = flat.normalize().scale(6.0);
            this.host.setBehavior(HostBehavior.RETREATING);
            this.host.getNavigation().moveTo(
                    this.host.getX() + flat.x, this.host.getY(), this.host.getZ() + flat.z, 1.2);
            return;
        }

        // Si l'Hôte n'est pas observé pendant 300 ticks cumulés, il commence à stalker.
        if (this.host.tickCount % 10 == 0) {
            if (VanishWhenSeenGoal.findWatcher(this.host, KEEP_RANGE) != null) {
                this.ticksSinceSeen = 0;
            } else {
                this.ticksSinceSeen += 10;
                if (this.ticksSinceSeen >= STALK_AFTER_TICKS) {
                    this.host.setBehavior(HostBehavior.STALKING);
                }
            }
        }
    }

    /**
     * Phase 2 : 30 % de chances de choisir un point d'observation à 8–16 blocs
     * d'un {@code homePoint} du profil de la cible (ou, à défaut, d'une cellule
     * fréquente), puis de s'y rendre par navigation vanilla. L'ancre n'est
     * exploitée que si elle se trouve à moins de 48 blocs du joueur (sinon le
     * déplacement n'aurait plus de lien avec l'observation en cours).
     *
     * @return true si un chemin vers un tel point a été trouvé.
     */
    private boolean tryMoveToMemoryPost() {
        if (this.target == null || !(this.host.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        if (this.host.getRandom().nextFloat() >= MEMORY_POST_CHANCE) {
            return false;
        }
        PlayerHorrorProfile profile = PenumbraPersistentState.get(serverLevel.getServer())
                .profile(this.target.getUUID());
        BlockPos anchor = null;
        List<BlockPos> homePoints = profile.homePoints();
        if (!homePoints.isEmpty()) {
            anchor = homePoints.get(this.host.getRandom().nextInt(homePoints.size()));
        } else {
            Optional<BlockPos> cell = profile.randomFrequentCell(this.host.getRandom());
            if (cell.isPresent()) {
                anchor = cell.get();
            }
        }
        if (anchor == null || !isNearTarget(anchor)) {
            return false;
        }

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int attempt = 0; attempt < POST_ATTEMPTS; attempt++) {
            double angle = this.host.getRandom().nextDouble() * (Math.PI * 2.0);
            double dist = POST_MIN_DIST
                    + this.host.getRandom().nextDouble() * (POST_MAX_DIST - POST_MIN_DIST);
            int baseX = Mth.floor(anchor.getX() + 0.5 + Math.cos(angle) * dist);
            int baseZ = Mth.floor(anchor.getZ() + 0.5 + Math.sin(angle) * dist);
            for (int dy = 4; dy >= -8; dy--) {
                cursor.set(baseX, anchor.getY() + dy, baseZ);
                if (isValidObservationPost(serverLevel, cursor)) {
                    boolean started = this.host.getNavigation().moveTo(
                            baseX + 0.5, cursor.getY(), baseZ + 0.5, POST_SPEED);
                    return started;
                }
            }
        }
        return false;
    }

    /** Vrai si l'ancre est à moins de {@link #POST_PLAYER_RANGE} blocs de la cible. */
    private boolean isNearTarget(BlockPos anchor) {
        double dx = anchor.getX() + 0.5 - this.target.getX();
        double dy = anchor.getY() - this.target.getY();
        double dz = anchor.getZ() + 0.5 - this.target.getZ();
        return dx * dx + dy * dy + dz * dz <= POST_PLAYER_RANGE * POST_PLAYER_RANGE;
    }

    /**
     * Emplacement d'observation valide : chunk chargé, sol solide, 2 blocs d'air,
     * aucun liquide. Pas de contrainte de luminosité : c'est une destination de
     * marche, pas une apparition.
     */
    private boolean isValidObservationPost(ServerLevel level, BlockPos feet) {
        if (!level.isLoaded(feet)) {
            return false;
        }
        BlockPos ground = feet.below();
        BlockPos head = feet.above();
        // 26.1 : isSolidRender() sans arguments (vérifié dans les sources 26.1.2).
        if (level.getBlockState(ground).isAir() || !level.getBlockState(ground).isSolidRender()) {
            return false;
        }
        if (!level.getBlockState(feet).isAir() || !level.getBlockState(head).isAir()) {
            return false;
        }
        return level.getFluidState(ground).isEmpty()
                && level.getFluidState(feet).isEmpty()
                && level.getFluidState(head).isEmpty();
    }
}

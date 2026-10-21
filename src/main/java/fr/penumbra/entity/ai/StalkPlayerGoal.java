package fr.penumbra.entity.ai;

import fr.penumbra.entity.HostBehavior;
import fr.penumbra.entity.HostEntity;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * Filature : en STALKING, l'Hôte maintient une distance de 12–20 blocs avec sa cible
 * (vitesse ×1.15), en contournant par les flancs. Il s'immobilise dès qu'il est
 * regardé (laissant {@link VanishWhenSeenGoal} agir) ; le chemin est recalculé
 * toutes les 40 ticks au maximum.
 */
public class StalkPlayerGoal extends Goal {

    private static final double DETECT_RANGE = 48.0;
    private static final double MIN_DISTANCE = 12.0;
    private static final double MAX_DISTANCE = 20.0;
    private static final double STANDOFF = 15.0;
    private static final double SPEED = 1.15;
    private static final int REPATH_TICKS = 40;

    private final HostEntity host;
    private ServerPlayer target;
    private int repathCooldown;
    private int flankSign = 1;

    public StalkPlayerGoal(HostEntity host) {
        this.host = host;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.host.getBehavior() != HostBehavior.STALKING
                || !(this.host.level() instanceof ServerLevel serverLevel)) {
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
        return this.host.getBehavior() == HostBehavior.STALKING
                && this.target != null
                && this.target.isAlive()
                && this.target.level() == this.host.level()
                && this.host.isOwnedBy(this.target)
                && this.target.distanceTo(this.host) <= DETECT_RANGE;
    }

    @Override
    public void start() {
        this.repathCooldown = 0;
    }

    @Override
    public void stop() {
        this.target = null;
    }

    @Override
    public void tick() {
        if (this.target == null) {
            return;
        }
        // S'arrête quand il est regardé : VanishWhenSeenGoal prend le relais.
        if (this.host.tickCount % 5 == 0 && VanishWhenSeenGoal.findWatcher(this.host, 40.0) != null) {
            this.host.getNavigation().stop();
            return;
        }
        if (--this.repathCooldown > 0) {
            return;
        }

        double dist = this.host.distanceTo(this.target);
        if (dist > MAX_DISTANCE || dist < MIN_DISTANCE) {
            // Hors de la bande 12–20 blocs : repositionnement flanquant, recalculé toutes les 40 ticks max.
            this.repathCooldown = REPATH_TICKS;
            if (this.host.getRandom().nextFloat() < 0.35f) {
                this.flankSign = -this.flankSign;
            }
            Vec3 toPlayer = this.target.position().subtract(this.host.position());
            Vec3 flat = new Vec3(toPlayer.x, 0.0, toPlayer.z);
            if (flat.lengthSqr() < 1.0E-4) {
                flat = new Vec3(1.0, 0.0, 0.0);
            }
            flat = flat.normalize();
            Vec3 side = new Vec3(-flat.z, 0.0, flat.x).scale(6.0 * this.flankSign);
            Vec3 dest = this.target.position().subtract(flat.scale(STANDOFF)).add(side);
            this.host.getNavigation().moveTo(dest.x, dest.y, dest.z, SPEED);
        } else {
            // Dans la bande : immobile, il observe.
            this.repathCooldown = 20;
            this.host.getNavigation().stop();
        }
    }
}

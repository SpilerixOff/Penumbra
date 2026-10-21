package fr.penumbra.entity.ai;

import fr.penumbra.entity.HostBehavior;
import fr.penumbra.entity.HostEntity;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * Guet dissimulé (Phase 2, SPEC2 §7) : en HIDING, l'Hôte reste immobile hors de la
 * ligne de vue directe, dans une bande de 12–24 blocs du joueur le plus proche
 * (il recule si le joueur s'approche sous 12 blocs, se rapproche discrètement
 * au-delà de 24 blocs). S'il est découvert (ligne de vue directe pendant 4 ticks
 * cumulés) : 60 % de dissolution en cendre, 40 % de passage en STALKING.
 * Après 2400 ticks sans découverte, disparition silencieuse (aucun effet).
 */
public class HiddenWatchGoal extends Goal {

    /** Portée de recherche du joueur guetté. */
    private static final double DETECT_RANGE = 48.0;
    /** Portée de la ligne de vue comptant pour la découverte. */
    private static final double LOS_RANGE = 40.0;
    /** Borne basse de la bande de guet. */
    private static final double BAND_MIN = 12.0;
    /** Borne haute de la bande de guet (l'Hôte reste à 24 blocs ou moins). */
    private static final double BAND_MAX = 24.0;
    /** Ticks cumulés de ligne de vue avant découverte. */
    private static final int LOS_TICKS_REQUIRED = 4;
    /** Durée maximale du guet avant disparition silencieuse. */
    private static final int SILENT_VANISH_TICKS = 2400;
    /** Probabilité de dissolution en cendre lors d'une découverte (sinon STALKING). */
    private static final float VANISH_CHANCE = 0.6f;
    /** Vitesse de repositionnement dans la bande. */
    private static final double REPOSITION_SPEED = 0.9;

    private final HostEntity host;
    private ServerPlayer watched;
    private int hiddenTicks;
    private int losTicks;

    public HiddenWatchGoal(HostEntity host) {
        this.host = host;
        // MOVE|LOOK : bloque la flânerie et le regard aléatoire pendant tout le guet.
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.host.getBehavior() != HostBehavior.HIDING
                || !(this.host.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        this.watched = findNearestPlayer(serverLevel);
        // Reste actif même sans joueur : le minuteur de disparition doit tourner.
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return this.host.getBehavior() == HostBehavior.HIDING;
    }

    @Override
    public void start() {
        this.hiddenTicks = 0;
        this.losTicks = 0;
    }

    @Override
    public void stop() {
        this.watched = null;
        this.host.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (!(this.host.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        // Disparition silencieuse après 2400 ticks sans découverte : aucun effet.
        if (++this.hiddenTicks >= SILENT_VANISH_TICKS) {
            this.host.discard();
            return;
        }
        if (this.watched == null || !this.watched.isAlive() || this.watched.isRemoved()
                || this.watched.level() != this.host.level()) {
            this.watched = findNearestPlayer(serverLevel);
            if (this.watched == null) {
                this.host.getNavigation().stop();
                this.losTicks = 0;
                return;
            }
        }

        double distance = this.host.distanceTo(this.watched);
        // Depuis sa cachette, l'Hôte fixe le joueur.
        this.host.getLookControl().setLookAt(this.watched, 30.0f, 30.0f);

        if (distance < BAND_MIN) {
            retreatFrom(serverLevel, distance);
        } else if (distance > BAND_MAX) {
            // Reste à 24 blocs ou moins : rapprochement discret, jamais hors zone chargée.
            if (serverLevel.isLoaded(this.watched.blockPosition())) {
                this.host.getNavigation().moveTo(this.watched.getX(), this.watched.getY(),
                        this.watched.getZ(), REPOSITION_SPEED);
            }
        } else {
            // Dans la bande : immobile, hors de la ligne de vue directe.
            this.host.getNavigation().stop();
        }

        // Découverte : ligne de vue directe pendant 4 ticks cumulés (réarmé si perdue).
        if (distance <= LOS_RANGE && this.watched.hasLineOfSight(this.host)) {
            if (++this.losTicks >= LOS_TICKS_REQUIRED) {
                onDiscovered(serverLevel);
            }
        } else {
            this.losTicks = 0;
        }
    }

    /** Recule hors de la bande basse, en restant dans les chunks chargés. */
    private void retreatFrom(ServerLevel serverLevel, double distance) {
        Vec3 away = this.host.position().subtract(this.watched.position());
        Vec3 flat = new Vec3(away.x, 0.0, away.z);
        if (flat.lengthSqr() < 1.0E-4) {
            flat = new Vec3(1.0, 0.0, 0.0);
        }
        Vec3 dest = this.host.position().add(flat.normalize().scale(BAND_MIN - distance + 4.0));
        if (serverLevel.isLoaded(BlockPos.containing(dest))) {
            this.host.getNavigation().moveTo(dest.x, dest.y, dest.z, REPOSITION_SPEED);
        }
    }

    /** Découverte : 60 % de dissolution en cendre, 40 % de passage en STALKING. */
    private void onDiscovered(ServerLevel serverLevel) {
        if (this.host.getRandom().nextFloat() < VANISH_CHANCE) {
            VanishWhenSeenGoal.vanishInAsh(serverLevel, this.host);
        } else {
            this.host.setBehavior(HostBehavior.STALKING);
        }
    }

    /** Joueur non-spectateur le plus proche dans la portée de guet. */
    private ServerPlayer findNearestPlayer(ServerLevel serverLevel) {
        ServerPlayer owner = this.host.getOwnerPlayer();
        if (owner != null) {
            return owner.distanceTo(this.host) <= DETECT_RANGE ? owner : null;
        }
        if (this.host.hasOwner()) {
            return null;
        }
        ServerPlayer nearest = null;
        double best = DETECT_RANGE;
        for (ServerPlayer player : serverLevel.players()) {
            if (player.isSpectator() || !player.isAlive()) {
                continue;
            }
            double dist = player.distanceTo(this.host);
            if (dist < best) {
                best = dist;
                nearest = player;
            }
        }
        if (nearest != null) {
            this.host.setOwner(nearest);
        }
        return nearest;
    }
}

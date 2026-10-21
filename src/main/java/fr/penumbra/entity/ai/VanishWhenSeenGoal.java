package fr.penumbra.entity.ai;

import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.entity.HostEntity;
import fr.penumbra.network.PenumbraNetworking;
import fr.penumbra.registry.PenumbraParticles;
import fr.penumbra.registry.PenumbraSounds;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * Goal prioritaire : si un joueur regarde l'Hôte (cône de vision dot &gt; 0.94,
 * ligne de vue libre, ≤ 40 blocs) pendant au moins {@code seuilTicksLos} ticks
 * cumulés (8 par défaut), l'Hôte annonce un bond vers la position observée.
 * Le joueur peut gagner un couvert ou esquiver pendant l'annonce.
 * Si {@code hostEnabled} est faux, l'Hôte est supprimé immédiatement.
 * Phase 2 : inactif pendant CHASING/ATTACKING (la poursuite ne s'interrompt pas
 * sous le regard) ; le constructeur à seuil réduit sert la fausse silhouette.
 */
public class VanishWhenSeenGoal extends Goal {

    private static final double VIEW_DOT_THRESHOLD = 0.94;
    private static final double RANGE = 40.0;
    /** Seuil de ticks de vision cumulés par défaut (comportement Phase 1). */
    private static final int DEFAULT_SEUIL_TICKS_LOS = 8;
    private static final int SCAN_INTERVAL = 5;

    private final HostEntity host;
    /** Seuil de ticks de vision cumulés déclenchant la disparition. */
    private final int seuilTicksLos;
    /** Cadence de scan réelle : resserrée si le seuil est inférieur à SCAN_INTERVAL. */
    private final int scanInterval;
    private int seenTicks;
    private boolean done;

    public VanishWhenSeenGoal(HostEntity host) {
        this(host, DEFAULT_SEUIL_TICKS_LOS);
    }

    /**
     * Variante à seuil réduit (fausse silhouette, SPEC2 §6) : la disparition se
     * déclenche après {@code seuilTicksLos} ticks de vision cumulés au lieu de 8.
     */
    public VanishWhenSeenGoal(HostEntity host, int seuilTicksLos) {
        this.host = host;
        this.seuilTicksLos = Math.max(1, seuilTicksLos);
        this.scanInterval = Math.min(SCAN_INTERVAL, this.seuilTicksLos);
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!PenumbraConfig.get().hostEnabled) {
            if (this.host.level() instanceof ServerLevel) {
                this.host.discard();
            }
            return false;
        }
        // Phase 2 : pendant une poursuite, l'Hôte ne disparaît plus quand on le
        // regarde — ChasePlayerGoal (priorité 0) pilote CHASING/ATTACKING.
        if (!this.host.canReactToStare()) {
            return false;
        }
        if (this.host.tickCount % this.scanInterval != 0) {
            return false;
        }
        return findWatcher(this.host, RANGE) != null;
    }

    @Override
    public boolean canContinueToUse() {
        return !this.done && this.host.canReactToStare();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.seenTicks = 0;
        this.done = false;
    }

    @Override
    public void tick() {
        ServerPlayer watcher = findWatcher(this.host, RANGE);
        if (watcher == null) {
            // Regard perdu : le compteur cumulé retombe à zéro et le goal s'arrête.
            this.seenTicks = 0;
            this.done = true;
            return;
        }
        this.seenTicks++;
        if (this.seenTicks >= this.seuilTicksLos) {
            this.done = true;
            if (this.host.level() instanceof ServerLevel) {
                triggerLunge(watcher);
            }
        }
    }

    /** Le regard soutenu arme désormais toujours le bond 3D avant l'attaque. */
    private void triggerLunge(ServerPlayer watcher) {
        this.host.beginStareLunge(watcher);
    }

    /**
     * Dissolution en cendre : particules ASH_MOTE, HOST_WHISPER aux joueurs proches
     * via le packet S2C, puis discard de l'entité. Helper statique partagé, réutilisé
     * par {@link HiddenWatchGoal} pour la fausse silhouette (SPEC2 §6/§7).
     */
    public static void vanishInAsh(ServerLevel serverLevel, HostEntity host) {
        serverLevel.sendParticles(PenumbraParticles.ASH_MOTE,
                host.getX(), host.getY() + 1.3, host.getZ(),
                24, 0.4, 0.8, 0.4, 0.01);
        Vec3 pos = host.position();
        float pitch = 0.9f + host.getRandom().nextFloat() * 0.2f;
        ServerPlayer owner = host.getOwnerPlayer();
        if (owner != null) {
            PenumbraNetworking.sendSound(owner, PenumbraSounds.HOST_WHISPER, pos, 0.8f, pitch);
        }
        host.discard();
    }

    /**
     * Trouve le joueur le plus proche (≤ maxDist) qui regarde actuellement l'Hôte :
     * dot(regard, directionHôte) &gt; 0.94 ET ligne de vue libre. Serveur uniquement.
     *
     * @return le joueur observateur, ou null si personne ne regarde.
     */
    public static ServerPlayer findWatcher(HostEntity host, double maxDist) {
        if (!(host.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        ServerPlayer best = null;
        double bestDist = maxDist;
        for (ServerPlayer player : serverLevel.players()) {
            if (!player.isAlive() || player.isRemoved() || player.isSpectator()
                    || player.isSleeping() || !host.isOwnedBy(player)) {
                continue;
            }
            double dist = player.distanceTo(host);
            if (dist > bestDist) {
                continue;
            }
            Vec3 look = player.getLookAngle().normalize();
            Vec3 toHost = host.getEyePosition().subtract(player.getEyePosition());
            double len = toHost.length();
            if (len < 1.0E-4) {
                continue;
            }
            if (look.dot(toHost.scale(1.0 / len)) > VIEW_DOT_THRESHOLD && player.hasLineOfSight(host)) {
                best = player;
                bestDist = dist;
            }
        }
        return best;
    }
}

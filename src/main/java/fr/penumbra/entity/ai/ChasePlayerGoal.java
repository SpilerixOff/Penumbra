package fr.penumbra.entity.ai;

import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.director.HorrorDirector;
import fr.penumbra.entity.HostBehavior;
import fr.penumbra.entity.HostEntity;
import fr.penumbra.network.PenumbraNetworking;
import fr.penumbra.registry.PenumbraSounds;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * Poursuite complète de l'Hôte (Phase 2, SPEC2 §7). Actif en CHASING et ATTACKING.
 * <ul>
 *   <li>mise en scène de début : arrêt 15 ticks face au joueur (le CHASE_STINGER
 *       est joué par {@code HorrorDirector.startChase}, pas ici) ;</li>
 *   <li>rampe de vitesse 1.15 → 1.45 sur 60 ticks, chemin recalculé au plus
 *       toutes les 10 ticks (et dès que le chemin courant est épuisé) ;</li>
 *   <li>navigation vanilla uniquement : jamais de chemin vers un chunk non chargé,
 *       abandon si la cible quitte la zone chargée ;</li>
 *   <li>attaque uniquement en ATTACKING après un télégraphe de 20 ticks
 *       (HOST_STEP rapprochés + avancée au ralenti) — la seule attaque de mêlée
 *       du mod ; {@code MeleeAttackGoal} reste bloqué par les flags MOVE|LOOK
 *       de ce goal pendant toute la poursuite ;</li>
 *   <li>abandon si distance &gt; 48 blocs pendant 100 ticks ou durée &gt; 1200 ticks
 *       → {@code HorrorDirector.endChase(player, random &lt; 0.4)}.</li>
 * </ul>
 */
public class ChasePlayerGoal extends Goal {

    /** Portée de détection de la cible (= followRange de l'Hôte). */
    private static final double DETECT_RANGE = 48.0;
    /** Durée de la mise en scène d'ouverture (arrêt face au joueur). */
    private static final int STAGING_TICKS = 15;
    /** Durée de la rampe de vitesse. */
    private static final int RAMP_TICKS = 60;
    /** Multiplicateur de vitesse en début de poursuite. */
    private static final double SPEED_START = 1.15;
    /** Multiplicateur de vitesse en fin de rampe. */
    private static final double SPEED_END = 1.45;
    /** Intervalle maximal entre deux recalculs de chemin. */
    private static final int REPATH_TICKS = 10;
    /** Durée maximale d'une poursuite avant abandon. */
    private static final int MAX_CHASE_TICKS = 1200;
    /** Distance au-delà de laquelle l'abandon se cumule. */
    private static final double FAR_DISTANCE = 48.0;
    /** Ticks consécutifs au-delà de FAR_DISTANCE avant abandon. */
    private static final int FAR_TICKS = 100;
    /** Distance déclenchant le télégraphe d'attaque. */
    private static final double TELEGRAPH_TRIGGER_RANGE = 4.0;
    /** Durée du télégraphe avant l'unique frappe. */
    private static final int TELEGRAPH_TICKS = 20;
    /** Vitesse d'avancée pendant le télégraphe (ralentissement visible). */
    private static final double TELEGRAPH_CREEP_SPEED = 0.35;
    /** Portée d'envoi des HOST_STEP du télégraphe. */
    private static final double STEP_SOUND_RANGE = 40.0;
    /** Intervalles décroissants entre les HOST_STEP (« rapprochés ») : pas à 0, 6, 11, 15, 18. */
    private static final int[] STEP_GAPS = {6, 5, 4, 3};

    /** Phases internes de la poursuite. */
    private enum Phase {
        /** Arrêt initial face au joueur. */
        STAGING,
        /** Poursuite active avec rampe de vitesse. */
        PURSUIT,
        /** Télégraphe d'attaque (ATTACKING), 20 ticks. */
        TELEGRAPH
    }

    private final HostEntity host;
    private ServerPlayer target;
    private Phase phase = Phase.STAGING;
    private int stagingTicks;
    private int chaseTicks;
    private int farTicks;
    private int repathCooldown;
    private int telegraphTicks;
    private int nextStepAt;
    private int stepIndex;

    public ChasePlayerGoal(HostEntity host) {
        this.host = host;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    /** Vrai si l'Hôte est dans un comportement de poursuite. */
    private boolean isChaseBehavior() {
        HostBehavior behavior = this.host.getBehavior();
        return behavior == HostBehavior.CHASING || behavior == HostBehavior.ATTACKING;
    }

    @Override
    public boolean canUse() {
        if (!isChaseBehavior() || !PenumbraConfig.get().chasesEnabled
                || !(this.host.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        this.target = findNearestPlayer(serverLevel);
        return this.target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return isChaseBehavior()
                && PenumbraConfig.get().chasesEnabled
                && this.target != null
                && this.target.isAlive()
                && !this.target.isRemoved()
                && this.host.isOwnedBy(this.target)
                && this.target.level() == this.host.level();
    }

    @Override
    public void start() {
        // Mise en scène de début : immobile face au joueur pendant STAGING_TICKS.
        this.phase = Phase.STAGING;
        this.stagingTicks = 0;
        this.chaseTicks = 0;
        this.farTicks = 0;
        this.repathCooldown = 0;
        this.telegraphTicks = 0;
        this.host.getNavigation().stop();
    }

    @Override
    public void stop() {
        this.host.getNavigation().stop();
        // Filet anti-attaque-sans-télégraphe : aucune cible résiduelle pour MeleeAttackGoal.
        this.host.setTarget(null);
        // Sortie non scénarisée (cible morte, déconnectée, chasses désactivées) :
        // l'Hôte retombe en COOLDOWN au lieu de rester coincé en CHASING/ATTACKING.
        if (isChaseBehavior()) {
            this.host.setBehavior(HostBehavior.COOLDOWN);
        }
        this.target = null;
    }

    @Override
    public void tick() {
        if (this.target == null || !(this.host.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        this.chaseTicks++;

        // Abandons intelligents : durée maximale, ou cible hors zone chargée.
        if (this.chaseTicks > MAX_CHASE_TICKS || !serverLevel.isLoaded(this.target.blockPosition())) {
            abandon(serverLevel);
            return;
        }
        double distance = this.host.distanceTo(this.target);
        if (distance > FAR_DISTANCE) {
            if (++this.farTicks >= FAR_TICKS) {
                abandon(serverLevel);
                return;
            }
        } else {
            this.farTicks = 0;
        }

        switch (this.phase) {
            case STAGING -> tickStaging();
            case PURSUIT -> tickPursuit(serverLevel, distance);
            case TELEGRAPH -> tickTelegraph(serverLevel);
        }
    }

    /** Phase d'ouverture : immobile, regard fixé sur le joueur. */
    private void tickStaging() {
        this.host.getNavigation().stop();
        this.host.getLookControl().setLookAt(this.target, 30.0f, 30.0f);
        if (++this.stagingTicks >= STAGING_TICKS) {
            this.phase = Phase.PURSUIT;
            this.repathCooldown = 0;
        }
    }

    /** Poursuite : rampe de vitesse 1.15 → 1.45, chemin recalculé toutes les 10 ticks max. */
    private void tickPursuit(ServerLevel serverLevel, double distance) {
        // Au contact et en ligne de vue : télégraphe, jamais de coup immédiat.
        if (distance <= TELEGRAPH_TRIGGER_RANGE && this.host.hasLineOfSight(this.target)) {
            this.phase = Phase.TELEGRAPH;
            this.telegraphTicks = 0;
            this.nextStepAt = 0;
            this.stepIndex = 0;
            this.host.setBehavior(HostBehavior.ATTACKING);
            this.host.getNavigation().stop();
            return;
        }
        this.host.getLookControl().setLookAt(this.target, 30.0f, 30.0f);
        int pursuitTicks = Math.max(0, this.chaseTicks - STAGING_TICKS);
        double ramp = Math.min(1.0, pursuitTicks / (double) RAMP_TICKS);
        double speed = SPEED_START + (SPEED_END - SPEED_START) * ramp;
        if (--this.repathCooldown <= 0 || this.host.getNavigation().isDone()) {
            this.repathCooldown = REPATH_TICKS;
            moveToward(serverLevel, this.target.getX(), this.target.getY(), this.target.getZ(), speed);
        }
    }

    /**
     * Télégraphe : 20 ticks d'avancée au ralenti, HOST_STEP de plus en plus
     * rapprochés (volume et pitch montants), puis la frappe.
     */
    private void tickTelegraph(ServerLevel serverLevel) {
        this.host.getLookControl().setLookAt(this.target, 60.0f, 60.0f);
        if (this.telegraphTicks % 5 == 0) {
            moveToward(serverLevel, this.target.getX(), this.target.getY(), this.target.getZ(),
                    TELEGRAPH_CREEP_SPEED);
        }
        if (this.telegraphTicks >= this.nextStepAt) {
            playStepSound(serverLevel);
            if (this.stepIndex < STEP_GAPS.length) {
                this.nextStepAt += STEP_GAPS[this.stepIndex];
                this.stepIndex++;
            } else {
                this.nextStepAt = Integer.MAX_VALUE;
            }
        }
        if (++this.telegraphTicks >= TELEGRAPH_TICKS) {
            strike(serverLevel);
        }
    }

    /**
     * L'unique attaque de mêlée du mod : après le télégraphe, en ATTACKING.
     * Si la cible s'est soustraite pendant le télégraphe, la poursuite reprend.
     */
    private void strike(ServerLevel serverLevel) {
        if (this.host.isWithinMeleeAttackRange(this.target) && this.host.hasLineOfSight(this.target)) {
            this.host.swing(InteractionHand.MAIN_HAND);
            this.host.doHurtTarget(serverLevel, this.target);
            // Anti-frustration : une frappe aboutie termine la poursuite.
            abandon(serverLevel);
        } else {
            this.host.setBehavior(HostBehavior.CHASING);
            this.phase = Phase.PURSUIT;
            this.repathCooldown = 0;
        }
    }

    /**
     * Fin de poursuite scénarisée : le directeur applique le cooldown global
     * ({@code fakeRetreat} à 40 % → l'Hôte passe HIDING et reste ≤ 24 blocs),
     * puis le comportement local est aligné (idempotent avec le directeur).
     */
    private void abandon(ServerLevel serverLevel) {
        boolean fakeRetreat = this.host.getRandom().nextFloat() < 0.4f;
        HorrorDirector.endChase(this.target, fakeRetreat);
        this.host.setBehavior(fakeRetreat ? HostBehavior.HIDING : HostBehavior.COOLDOWN);
        this.host.getNavigation().stop();
    }

    /** HOST_STEP positionné, audible par tout joueur assez proche de l'Hôte. */
    private void playStepSound(ServerLevel serverLevel) {
        Vec3 pos = this.host.position();
        float volume = Math.min(1.0f, 0.6f + 0.02f * this.telegraphTicks);
        float pitch = 0.9f + 0.01f * this.telegraphTicks;
        if (this.target.distanceTo(this.host) <= STEP_SOUND_RANGE) {
            PenumbraNetworking.sendSound(
                    this.target, PenumbraSounds.HOST_STEP, pos, volume, pitch);
        }
    }

    /** Déplace l'Hôte par navigation vanilla, jamais vers un chunk non chargé. */
    private void moveToward(ServerLevel serverLevel, double x, double y, double z, double speed) {
        if (serverLevel.isLoaded(BlockPos.containing(x, y, z))) {
            this.host.getNavigation().moveTo(x, y, z, speed);
        } else {
            this.host.getNavigation().stop();
        }
    }

    /** Joueur non-spectateur le plus proche dans la portée de détection. */
    private ServerPlayer findNearestPlayer(ServerLevel serverLevel) {
        ServerPlayer owner = this.host.getOwnerPlayer();
        if (owner != null) {
            return owner.distanceTo(this.host) <= DETECT_RANGE ? owner : null;
        }
        // Une manifestation déjà liée ne doit jamais changer de joueur.
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

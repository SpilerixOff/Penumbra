package fr.penumbra.event.night;

import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.director.HorrorDirector;
import fr.penumbra.director.PlayerHorrorProfile;
import fr.penumbra.director.TensionState;
import fr.penumbra.entity.HostBehavior;
import fr.penumbra.entity.HostEntity;
import fr.penumbra.event.HorrorEvent;
import fr.penumbra.registry.PenumbraEntities;
import fr.penumbra.registry.PenumbraParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;

/**
 * L'Hôte sur une élévation lointaine (événement nocturne {@code hill_host}).
 * <p>
 * Invoque un Hôte à {@value #MIN_DISTANCE}–{@value #MAX_DISTANCE} blocs, sur un
 * relief au moins au niveau du joueur (balayage vertical DESCENDANT : le premier
 * emplacement debout valide trouvé est le plus haut — biais « crête »), orienté
 * vers le joueur, comportement {@code OBSERVING}. À cette distance il est à
 * peine visible à l'œil nu : il se révèle surtout aux jumelles (pattern croisé
 * {@code DistantHostSightingEvent} × {@code BinocularsSightingEvent} ; la portée
 * de tracking entité couvre largement 100 blocs).
 * <br>
 * « Immobile » SANS {@code setNoAi} — compromis documenté : {@code NoAi} est
 * persisté dans le NBT ; si le chunk se déchargeait avant la fin de l'apparition,
 * l'Hôte resterait une statue permanente (son {@code checkDespawn} est neutralisé
 * par le mod). On laisse donc l'IA active : à &gt; 32 blocs l'{@code ObservePlayerGoal}
 * ne s'engage pas et seul un lent flâneur vanilla peut le faire légèrement dériver —
 * imperceptible à 60–100 blocs, et le {@code VanishWhenSeenGoal} reste actif
 * (regard à ≤ 40 blocs ⇒ disparition cendrée, fidèle au langage du mod).
 * <br>
 * Surveillance planifiée toutes les {@value #WATCH_INTERVAL_TICKS} ticks
 * (récursion {@code runLater}, pattern {@code HorrorDirector.mimicPostureStep}) :
 * si un joueur s'approche à moins de {@value #VANISH_APPROACH_DISTANCE} blocs,
 * l'Hôte disparaît dans un nuage de cendre ; au-delà de
 * {@value #MAX_LIFETIME_TICKS} ticks (60 s) il est retiré discrètement.
 * Jamais de Hôte doublon : {@code canRun} exige l'absence de tout Hôte à moins
 * de {@value #NO_HOST_RADIUS} blocs (précédent {@code BinocularsSightingEvent}).
 * Exige {@code hostEnabled}. Annulé sans AUCUN effet si aucun emplacement valide.
 * Gain de tension : +10 en cas de réussite uniquement.
 * </p>
 */
public final class HillHostEvent implements HorrorEvent {

    /** Distance min/max d'apparition (blocs). */
    private static final double MIN_DISTANCE = 60.0;
    private static final double MAX_DISTANCE = 100.0;
    /** Rayon d'exclusion : aucun Hôte existant à moins de cette distance. */
    private static final double NO_HOST_RADIUS = 120.0;
    /** Approche minimale déclenchant la disparition (blocs). */
    private static final double VANISH_APPROACH_DISTANCE = 40.0;
    /** Cadence de la surveillance d'approche (ticks). */
    private static final int WATCH_INTERVAL_TICKS = 20;
    /** Durée de vie maximale de l'apparition (60 s). */
    private static final int MAX_LIFETIME_TICKS = 1200;
    /** Nombre d'essais de placement avant abandon. */
    private static final int ATTEMPTS = 10;
    /** Amplitude du balayage vertical descendant (biais crête). */
    private static final int SCAN_UP = 40;
    private static final int SCAN_DOWN = 4;

    @Override
    public String id() {
        return "hill_host";
    }

    @Override
    public int weight(TensionState state) {
        return switch (state) {
            case CALM -> 0;
            case UNEASE -> 1;
            case WATCHED -> 2;
            case MANIFESTATION -> 3;
            case HUNT -> 3;
            case CRISIS -> 2;
            case FALSE_CALM -> 0;
        };
    }

    @Override
    public int cooldownTicks() {
        return 6000;
    }

    @Override
    public boolean canRun(ServerPlayer player, PlayerHorrorProfile profile) {
        if (!PenumbraConfig.get().hostEnabled || !NightSupport.commonNight(player)) {
            return false;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        return !HostEntity.hasAnyHostNearby(
                level, player.blockPosition(), NO_HOST_RADIUS, player);
    }

    @Override
    public boolean run(ServerPlayer player) {
        if (!PenumbraConfig.get().hostEnabled) {
            return false;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        HostEntity host = spawnOnElevation(level, player);
        if (host == null) {
            return false; // aucun point culminant valide : annulé, aucun effet
        }
        host.setBehavior(HostBehavior.OBSERVING);
        HorrorDirector.runLater(level, WATCH_INTERVAL_TICKS,
                () -> watchHost(level, host, WATCH_INTERVAL_TICKS));
        return true;
    }

    /**
     * Tente d'invoquer un Hôte sur une élévation à 60–100 blocs : angle et distance
     * libres à chaque essai, puis balayage vertical DESCENDANT (+{@value #SCAN_UP} /
     * -{@value #SCAN_DOWN}) — le premier emplacement debout valide est le plus haut
     * de la colonne, d'où le biais « crête ». Refuse les spots plus de 2 blocs sous
     * le joueur (l'apparition doit dominer ou égaler le point de vue).
     */
    private static HostEntity spawnOnElevation(ServerLevel level, ServerPlayer player) {
        RandomSource random = player.getRandom();
        int playerY = Mth.floor(player.getY());
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            double angle = random.nextDouble() * (Math.PI * 2.0);
            double distance = MIN_DISTANCE + random.nextDouble() * (MAX_DISTANCE - MIN_DISTANCE);
            int baseX = Mth.floor(player.getX() + Math.cos(angle) * distance);
            int baseZ = Mth.floor(player.getZ() + Math.sin(angle) * distance);

            BlockPos spot = NightSupport.findStandingSpot(level, baseX, playerY, baseZ, SCAN_UP, SCAN_DOWN);
            if (spot == null || spot.getY() < playerY - 2) {
                continue;
            }

            HostEntity host = new HostEntity(PenumbraEntities.HOST, level);
            host.setOwner(player);
            double x = spot.getX() + 0.5;
            double z = spot.getZ() + 0.5;
            host.snapTo(x, spot.getY(), z,
                    NightSupport.faceYaw(x, z, player.getX(), player.getZ()), 0.0f);
            host.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), EntitySpawnReason.EVENT, null);
            if (level.addFreshEntity(host)) {
                return host;
            }
            return null;
        }
        return null;
    }

    /**
     * Surveillance périodique de l'apparition : disparition cendrée si un joueur
     * s'approche à moins de {@value #VANISH_APPROACH_DISTANCE} blocs, retrait
     * discret après {@value #MAX_LIFETIME_TICKS} ticks, sinon re-planification.
     */
    private static void watchHost(ServerLevel level, HostEntity host, int elapsed) {
        if (host.isRemoved()) {
            return; // déjà parti (regard via VanishWhenSeenGoal, décharge, etc.)
        }
        double approachSqr = VANISH_APPROACH_DISTANCE * VANISH_APPROACH_DISTANCE;
        for (ServerPlayer other : level.players()) {
            if (host.isOwnedBy(other) && host.distanceToSqr(other) < approachSqr) {
                vanishInAsh(level, host);
                return;
            }
        }
        if (elapsed >= MAX_LIFETIME_TICKS) {
            host.discard();
            return;
        }
        HorrorDirector.runLater(level, WATCH_INTERVAL_TICKS,
                () -> watchHost(level, host, elapsed + WATCH_INTERVAL_TICKS));
    }

    /** Disparition théâtrale : nuage de cendre à la position de l'Hôte puis retrait. */
    private static void vanishInAsh(ServerLevel level, HostEntity host) {
        level.sendParticles(PenumbraParticles.ASH_MOTE,
                host.getX(), host.getY() + 1.0, host.getZ(), 20, 0.3, 0.6, 0.3, 0.01);
        host.discard();
    }
}

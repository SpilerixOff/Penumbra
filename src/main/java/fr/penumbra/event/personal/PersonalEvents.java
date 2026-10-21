package fr.penumbra.event.personal;

import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.director.HorrorDirector;
import fr.penumbra.director.PlayerHorrorProfile;
import fr.penumbra.director.TensionState;
import fr.penumbra.entity.HostBehavior;
import fr.penumbra.entity.HostEntity;
import fr.penumbra.entity.absent.AbsentManager;
import fr.penumbra.event.HorrorEvent;
import fr.penumbra.network.PenumbraC2S;
import fr.penumbra.network.PenumbraNetworking;
import fr.penumbra.registry.PenumbraEntities;
import fr.penumbra.registry.PenumbraParticles;
import fr.penumbra.registry.PenumbraSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * Événements « personnels » (0.6.0) — la peur qui utilise la mémoire du mod
 * contre le joueur. Cinq événements ajoutés au pool du directeur, tous gérés
 * par le toggle {@code personalEventsEnabled} :
 * <ul>
 *   <li>{@code echoed_path} : des pas rejouent le PROPRE trajet du joueur,
 *       ~40 s derrière lui (ruban de positions du profil) ;</li>
 *   <li>{@code absent_home} : la porte de SON refuge grince alors qu'il est
 *       loin — le trajet du retour est l'événement ;</li>
 *   <li>{@code door_knocks} : trois coups lents, la nuit, sur la vraie porte
 *       de son refuge ;</li>
 *   <li>{@code binoculars_reveal} : un Hôte invisible à l'œil nu, visible
 *       UNIQUEMENT aux jumelles (invisibilité vanilla pilotée par l'état de
 *       zoom C2S) ;</li>
 *   <li>{@code behind_other} : multijoueur — le joueur voit l'Hôte debout
 *       derrière un AUTRE joueur, qui lui ne voit rien.</li>
 * </ul>
 * Tous les messages joueur passent par des clés de traduction (fr_fr fournie).
 */
public final class PersonalEvents {

    private PersonalEvents() {
    }

    /** Instancie les événements personnels (singletons sans état). */
    public static List<HorrorEvent> create() {
        return List.of(new EchoedPathEvent(), new AbsentHomeEvent(), new DoorKnocksEvent(),
                new BinocularsRevealEvent(), new BehindOtherEvent(),
                new PairedEchoEvent(), new StolenNameEvent());
    }

    /** Toggle commun du groupe. */
    private static boolean enabled() {
        return PenumbraConfig.get().personalEventsEnabled;
    }

    // =====================================================================
    /**
     * {@code echoed_path} — le trajet rejoué. Prend le segment du ruban de
     * positions d'il y a 35–50 s et y rejoue 8 pas ({@code FOOTSTEP_MIMIC},
     * variante si {@code improvedSoundsEnabled}) espacés de 10–14 ticks :
     * quelqu'un refait exactement le chemin du joueur, un peu derrière lui.
     * Annulé sans effet si le segment est inaudible (&gt; 40 blocs) ou trop
     * court (joueur immobile).
     */
    static final class EchoedPathEvent implements HorrorEvent {

        private static final int STEPS = 8;
        private static final double MAX_AUDIBLE_DIST = 40.0;
        private static final double MIN_PATH_LENGTH = 12.0;

        @Override
        public String id() {
            return "echoed_path";
        }

        @Override
        public int weight(TensionState state) {
            return switch (state) {
                case CALM -> 0;
                case UNEASE -> 6;
                case WATCHED -> 8;
                case MANIFESTATION -> 7;
                case HUNT -> 3;
                case CRISIS -> 1;
                case FALSE_CALM -> 8;
            };
        }

        @Override
        public int cooldownTicks() {
            return 4800;
        }

        @Override
        public boolean canRun(ServerPlayer player, PlayerHorrorProfile profile) {
            if (!enabled()) {
                return false;
            }
            List<Vec3> path = profile.copyPath();
            if (path.size() < 55) {
                return false; // ruban trop court (connexion récente)
            }
            // Segment 35–50 s en arrière : audible, et le joueur a réellement bougé.
            List<Vec3> segment = path.subList(path.size() - 50, path.size() - 35);
            double length = 0.0;
            for (int i = 1; i < segment.size(); i++) {
                length += segment.get(i).distanceTo(segment.get(i - 1));
            }
            return length >= MIN_PATH_LENGTH
                    && segment.get(0).distanceTo(player.position()) <= MAX_AUDIBLE_DIST;
        }

        @Override
        public boolean run(ServerPlayer player) {
            if (!(player.level() instanceof ServerLevel level)) {
                return false;
            }
            List<Vec3> path = fr.penumbra.data.PenumbraPersistentState.get(level.getServer())
                    .profile(player.getUUID()).copyPath();
            if (path.size() < 55) {
                return false;
            }
            List<Vec3> segment = path.subList(path.size() - 50, path.size() - 35);
            RandomSource random = player.getRandom();
            UUID playerId = player.getUUID();
            int delay = 0;
            int stride = Math.max(1, segment.size() / STEPS);
            for (int i = 0; i < segment.size(); i += stride) {
                Vec3 at = segment.get(i);
                delay += 10 + random.nextInt(5);
                float pitch = 0.85f + random.nextFloat() * 0.2f;
                boolean variant = PenumbraConfig.get().improvedSoundsEnabled && random.nextBoolean();
                HorrorDirector.runLater(level, delay, () -> {
                    ServerPlayer target = level.getServer().getPlayerList().getPlayer(playerId);
                    if (target != null && target.isAlive() && target.level() == level
                            && target.position().distanceTo(at) <= MAX_AUDIBLE_DIST) {
                        PenumbraNetworking.sendSound(target,
                                variant ? PenumbraSounds.FOOTSTEP_MIMIC_2 : PenumbraSounds.FOOTSTEP_MIMIC,
                                at, 0.5f, pitch);
                    }
                });
            }
            return true;
        }
    }

    // =====================================================================
    /**
     * {@code absent_home} — la base qui vit sans lui. Joue le grincement de
     * porte ({@code DOOR_GROAN}) POSITIONNÉ au refuge réel du joueur, volume
     * 3.0 (portée étendue ≈ 48 blocs) : le son vient de chez lui, au loin.
     * Le retour paniqué — vers rien — est l'événement.
     */
    static final class AbsentHomeEvent implements HorrorEvent {

        private static final double MIN_DIST = 48.0;
        private static final double MAX_DIST = 110.0;

        @Override
        public String id() {
            return "absent_home";
        }

        @Override
        public int weight(TensionState state) {
            return switch (state) {
                case CALM -> 2;
                case UNEASE -> 5;
                case WATCHED -> 7;
                case MANIFESTATION -> 6;
                case HUNT -> 2;
                case CRISIS -> 0;
                case FALSE_CALM -> 7;
            };
        }

        @Override
        public int cooldownTicks() {
            return 7200;
        }

        @Override
        public boolean canRun(ServerPlayer player, PlayerHorrorProfile profile) {
            return enabled() && nearestHome(player, profile) != null;
        }

        @Override
        public boolean run(ServerPlayer player) {
            if (!(player.level() instanceof ServerLevel level)) {
                return false;
            }
            // 26.1 : pas de ServerPlayer.getServer() — le serveur se lit sur le niveau.
            PlayerHorrorProfile profile = fr.penumbra.data.PenumbraPersistentState
                    .get(level.getServer()).profile(player.getUUID());
            BlockPos home = nearestHome(player, profile);
            if (home == null) {
                return false;
            }
            Vec3 at = Vec3.atCenterOf(home);
            UUID playerId = player.getUUID();
            PenumbraNetworking.sendSound(player, PenumbraSounds.DOOR_GROAN, at, 3.0f, 0.9f);
            // Second son 3 s plus tard : la porte du refuge, encore. Il n'a pas rêvé.
            HorrorDirector.runLater(level, 60, () -> {
                ServerPlayer target = level.getServer().getPlayerList().getPlayer(playerId);
                if (target != null && target.isAlive() && target.level() == level) {
                    PenumbraNetworking.sendSound(target, PenumbraSounds.LURE_DOOR, at, 2.5f, 1.0f);
                }
            });
            return true;
        }

        /** Refuge connu le plus proche dans la fenêtre 48–110 blocs, ou null. */
        private static BlockPos nearestHome(ServerPlayer player, PlayerHorrorProfile profile) {
            BlockPos best = null;
            double bestDist = Double.MAX_VALUE;
            for (BlockPos home : profile.homePoints()) {
                double dist = Math.sqrt(home.distSqr(player.blockPosition()));
                if (dist >= MIN_DIST && dist <= MAX_DIST && dist < bestDist) {
                    best = home;
                    bestDist = dist;
                }
            }
            return best;
        }
    }

    // =====================================================================
    /**
     * {@code door_knocks} — trois coups à ta porte. La nuit, joueur au refuge :
     * trouve la vraie porte du refuge (scan borné 8 blocs) et y frappe trois
     * coups sourds ({@code STONE_HIT} grave, pattern {@code wall_knocks}),
     * espacés de 9–13 ticks. Annulé sans effet si aucune porte n'existe.
     */
    static final class DoorKnocksEvent implements HorrorEvent {

        private static final int SCAN_RADIUS = 8;
        private static final int KNOCKS = 3;

        @Override
        public String id() {
            return "door_knocks";
        }

        @Override
        public int weight(TensionState state) {
            return switch (state) {
                case CALM -> 0;
                case UNEASE -> 4;
                case WATCHED -> 7;
                case MANIFESTATION -> 8;
                case HUNT -> 4;
                case CRISIS -> 2;
                case FALSE_CALM -> 9;
            };
        }

        @Override
        public int cooldownTicks() {
            return 9600;
        }

        @Override
        public boolean canRun(ServerPlayer player, PlayerHorrorProfile profile) {
            // 26.1 : isDarkOutside() (pattern tickSleepWake).
            return enabled() && player.level().isDarkOutside()
                    && profile.isNearRefuge(player.blockPosition(), 10.0);
        }

        @Override
        public boolean run(ServerPlayer player) {
            if (!(player.level() instanceof ServerLevel level)) {
                return false;
            }
            BlockPos door = findRefugeDoor(level, player.blockPosition());
            if (door == null) {
                return false; // aucune porte : annulé, aucun effet
            }
            Vec3 at = Vec3.atCenterOf(door);
            RandomSource random = player.getRandom();
            UUID playerId = player.getUUID();
            int delay = 0;
            for (int i = 0; i < KNOCKS; i++) {
                delay += 9 + random.nextInt(5);
                float pitch = 0.4f + random.nextFloat() * 0.08f;
                HorrorDirector.runLater(level, delay, () -> {
                    ServerPlayer target = level.getServer().getPlayerList().getPlayer(playerId);
                    if (target != null && target.isAlive() && target.level() == level) {
                        PenumbraNetworking.sendSound(target, SoundEvents.STONE_HIT, at, 0.9f, pitch);
                    }
                });
            }
            return true;
        }

        /** Première porte (moitié basse) dans un cube borné autour du joueur, ou null. */
        private static BlockPos findRefugeDoor(ServerLevel level, BlockPos center) {
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
                for (int dy = -3; dy <= 3; dy++) {
                    for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                        cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                        var state = level.getBlockState(cursor);
                        if (state.getBlock() instanceof DoorBlock
                                && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
                            return cursor.immutable();
                        }
                    }
                }
            }
            return null;
        }
    }

    // =====================================================================
    /**
     * {@code binoculars_reveal} — visible uniquement aux jumelles. Un Hôte
     * invisible (invisibilité vanilla) apparaît dans le cône de regard à
     * 40–70 blocs. Un poll serveur (toutes les 10 ticks, ≤ 60 s) le rend
     * visible tant que le joueur zoome ({@code PenumbraC2S.isZooming}) ; au
     * premier dézoom APRÈS révélation, dissolution en cendre + murmure.
     * Jamais révélé ? Il n'a jamais été là (discard silencieux).
     * Cohérent avec le lore de la Lentille fissurée.
     */
    static final class BinocularsRevealEvent implements HorrorEvent {

        private static final int LIFETIME_POLLS = 120; // 120 × 10 ticks = 60 s
        private static final int REVEALED_MAX_POLLS = 12; // 12 s révélé max

        @Override
        public String id() {
            return "binoculars_reveal";
        }

        @Override
        public int weight(TensionState state) {
            return switch (state) {
                case CALM -> 0;
                case UNEASE -> 3;
                case WATCHED -> 6;
                case MANIFESTATION -> 7;
                case HUNT -> 5;
                case CRISIS -> 2;
                case FALSE_CALM -> 5;
            };
        }

        @Override
        public int cooldownTicks() {
            return 7200;
        }

        @Override
        public boolean canRun(ServerPlayer player, PlayerHorrorProfile profile) {
            if (!enabled() || !PenumbraConfig.get().binocularsEnabled
                    || !PenumbraConfig.get().hostEnabled) {
                return false;
            }
            return player.level() instanceof ServerLevel level
                    && !HostEntity.hasAnyHostNearby(
                            level, player.blockPosition(), 100.0, player);
        }

        @Override
        public boolean run(ServerPlayer player) {
            if (!(player.level() instanceof ServerLevel level)) {
                return false;
            }
            RandomSource random = player.getRandom();
            Vec3 look = player.getLookAngle();
            Vec3 flat = new Vec3(look.x, 0.0, look.z);
            if (flat.lengthSqr() < 1.0E-4) {
                return false;
            }
            flat = flat.normalize();
            double lookAngle = Math.atan2(flat.z, flat.x);
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (int attempt = 0; attempt < 16; attempt++) {
                double angle = lookAngle + (random.nextDouble() * 2.0 - 1.0) * Math.toRadians(20.0);
                double dist = 40.0 + random.nextDouble() * 30.0;
                int baseX = Mth.floor(player.getX() + Math.cos(angle) * dist);
                int baseZ = Mth.floor(player.getZ() + Math.sin(angle) * dist);
                int baseY = Mth.floor(player.getY());
                for (int dy = 16; dy >= -16; dy--) {
                    cursor.set(baseX, baseY + dy, baseZ);
                    if (!isStandable(level, cursor)) {
                        continue;
                    }
                    HostEntity host = new HostEntity(PenumbraEntities.HOST, level);
                    host.setOwner(player);
                    host.snapTo(baseX + 0.5, cursor.getY(), baseZ + 0.5,
                            random.nextFloat() * 360.0f, 0.0f);
                    host.finalizeSpawn(level, level.getCurrentDifficultyAt(cursor),
                            EntitySpawnReason.EVENT, null);
                    host.setBehavior(HostBehavior.DORMANT);
                    host.setInvisible(true);
                    if (!level.addFreshEntity(host)) {
                        return false;
                    }
                    pollReveal(level, player.getUUID(), host.getUUID(), 0, false, 0);
                    return true;
                }
            }
            return false;
        }

        /** Emplacement tenable : chargé, sol solide, 2 blocs d'air (lumière libre : il est invisible). */
        private static boolean isStandable(ServerLevel level, BlockPos feet) {
            return level.isLoaded(feet)
                    && level.getBlockState(feet.below()).isSolidRender()
                    && level.getBlockState(feet).isAir()
                    && level.getBlockState(feet.above()).isAir()
                    && level.getFluidState(feet).isEmpty();
        }

        /** Poll récursif (pattern mimicPostureStep) : révélation au zoom, cendre au dézoom. */
        private static void pollReveal(ServerLevel level, UUID playerId, UUID hostId,
                                       int poll, boolean revealed, int revealedPolls) {
            HorrorDirector.runLater(level, 10, () -> {
                Entity entity = level.getEntity(hostId);
                if (!(entity instanceof HostEntity host) || !host.isAlive()) {
                    return;
                }
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
                if (player == null || !player.isAlive() || poll >= LIFETIME_POLLS) {
                    host.discard(); // jamais confirmé : il n'a jamais été là
                    return;
                }
                boolean zooming = PenumbraC2S.isZooming(playerId);
                boolean nowRevealed = revealed;
                int nowRevealedPolls = revealedPolls;
                if (zooming && !host.isInvisible()) {
                    nowRevealedPolls++;
                } else if (zooming && host.isInvisible()) {
                    host.setInvisible(false); // les lentilles le trouvent
                    nowRevealed = true;
                } else if (!zooming && nowRevealed) {
                    // Dézoom après révélation : il sait que vous savez.
                    level.sendParticles(PenumbraParticles.ASH_MOTE,
                            host.getX(), host.getY() + 1.3, host.getZ(), 24, 0.4, 0.8, 0.4, 0.01);
                    PenumbraNetworking.sendSound(player,
                            PenumbraSounds.whisperVariant(player.getRandom()),
                            host.position(), 0.8f, 0.7f);
                    host.discard();
                    return;
                }
                if (nowRevealedPolls >= REVEALED_MAX_POLLS) {
                    // Trop longtemps observé : dissolution sous les lentilles.
                    level.sendParticles(PenumbraParticles.ASH_MOTE,
                            host.getX(), host.getY() + 1.3, host.getZ(), 24, 0.4, 0.8, 0.4, 0.01);
                    host.discard();
                    return;
                }
                pollReveal(level, playerId, hostId, poll + 1, nowRevealed, nowRevealedPolls);
            });
        }
    }

    // =====================================================================
    /**
     * {@code behind_other} — l'Hôte traverse le dos d'un joueur proche, mais
     * son paquet d'entité n'est envoyé qu'au témoin propriétaire. Le sujet de
     * la scène n'entend que les pas. Sans compagnon (ou si le contenu multi est
     * coupé), la même traversée se replie derrière le témoin.
     */
    static final class BehindOtherEvent implements HorrorEvent {

        private static final double MIN_OTHER_DIST = 8.0;
        private static final double MAX_OTHER_DIST = 24.0;
        private static final int CROSSING_TICKS = 28;

        @Override
        public String id() {
            return "behind_other";
        }

        @Override
        public int weight(TensionState state) {
            return switch (state) {
                case CALM -> 0;
                case UNEASE -> 3;
                case WATCHED -> 6;
                case MANIFESTATION -> 7;
                case HUNT -> 4;
                case CRISIS -> 2;
                case FALSE_CALM -> 6;
            };
        }

        @Override
        public int cooldownTicks() {
            return 9600;
        }

        @Override
        public boolean canRun(ServerPlayer player, PlayerHorrorProfile profile) {
            return enabled() && PenumbraConfig.get().hostEnabled && !player.isSleeping();
        }

        @Override
        public boolean run(ServerPlayer player) {
            if (!(player.level() instanceof ServerLevel level)) {
                return false;
            }
            ServerPlayer other = PenumbraConfig.get().multiplayerContent
                    ? findCompanion(player, MIN_OTHER_DIST, MAX_OTHER_DIST) : null;
            ServerPlayer subject = other == null ? player : other;
            CrossingPath path = findCrossingPath(level, subject, player.getRandom());
            if (path == null) {
                return false;
            }
            // Apparition dans le dos de L'AUTRE joueur, hors de SON champ de vision.
            HostEntity host = new HostEntity(PenumbraEntities.HOST, level);
            // La position utilise l'autre joueur comme repère, mais l'événement
            // et toute IA ultérieure restent strictement liés au témoin.
            host.setOwner(player);
            host.setPrivateToOwner(true);
            host.setBehavior(HostBehavior.DORMANT);
            host.setMaxLifetimeTicks(CROSSING_TICKS + 20);
            host.setNoAi(true);
            host.setInvulnerable(true);
            float yaw = movementYaw(path.start(), path.end());
            host.snapTo(path.start().x, path.start().y, path.start().z, yaw, 0.0f);
            host.setYBodyRot(yaw);
            host.setYHeadRot(yaw);
            host.finalizeSpawn(level,
                    level.getCurrentDifficultyAt(BlockPos.containing(path.start())),
                    EntitySpawnReason.EVENT, null);
            if (!level.noCollision(host) || !level.addFreshEntity(host)) {
                return false;
            }
            PenumbraNetworking.sendSoundDirect(player, player,
                    PenumbraSounds.whisperVariant(player.getRandom()),
                    path.start(), 0.55f, 0.68f);
            if (other != null) {
                PenumbraNetworking.sendSoundDirect(player, other,
                        PenumbraSounds.HOST_STEP, path.start(), 0.65f, 0.78f);
            }
            movePrivateCrossing(level, player.getUUID(),
                    other == null ? null : other.getUUID(), host.getUUID(),
                    path.start(), path.end(), 0);
            return true;
        }

    }

    /** Deux moitiés sonores différentes, déclenchées au même tick pour un duo. */
    static final class PairedEchoEvent implements HorrorEvent {

        private static final double MAX_PAIR_DISTANCE = 28.0;

        @Override
        public String id() {
            return "paired_echo";
        }

        @Override
        public int weight(TensionState state) {
            return switch (state) {
                case CALM -> 0;
                case UNEASE -> 4;
                case WATCHED -> 7;
                case MANIFESTATION -> 6;
                case HUNT -> 4;
                case CRISIS -> 2;
                case FALSE_CALM -> 8;
            };
        }

        @Override
        public int cooldownTicks() {
            return 5200;
        }

        @Override
        public boolean canRun(ServerPlayer player, PlayerHorrorProfile profile) {
            return enabled() && !player.isSleeping();
        }

        @Override
        public boolean run(ServerPlayer player) {
            if (!(player.level() instanceof ServerLevel level)) {
                return false;
            }
            ServerPlayer companion = PenumbraConfig.get().multiplayerContent
                    ? findCompanion(player, 0.0, MAX_PAIR_DISTANCE) : null;
            Vec3 behindWitness = behind(player, 1.8);
            if (companion == null) {
                PenumbraNetworking.sendSoundDirect(player, player,
                        PenumbraSounds.FOOTSTEP_MIMIC, behindWitness, 0.48f, 0.86f);
                continuePairedEcho(level, player.getUUID(), null);
                return true;
            }

            Vec3 behindCompanion = behind(companion, 1.8);
            PenumbraNetworking.sendSoundDirect(player, player,
                    PenumbraSounds.FOOTSTEP_MIMIC, behindCompanion, 0.50f, 0.84f);
            PenumbraNetworking.sendSoundDirect(player, companion,
                    PenumbraSounds.BREATH_BEHIND, behindWitness, 0.32f, 0.88f);
            continuePairedEcho(level, player.getUUID(), companion.getUUID());
            return true;
        }
    }

    /** L'Absent vole le nom exact du compagnon, mais poursuit uniquement le témoin. */
    static final class StolenNameEvent implements HorrorEvent {

        private static final double MAX_NAME_DISTANCE = 32.0;

        @Override
        public String id() {
            return "stolen_name";
        }

        @Override
        public int weight(TensionState state) {
            return switch (state) {
                case CALM -> 0;
                case UNEASE -> 1;
                case WATCHED -> 3;
                case MANIFESTATION -> 6;
                case HUNT -> 7;
                case CRISIS -> 3;
                case FALSE_CALM -> 5;
            };
        }

        @Override
        public int cooldownTicks() {
            return 12000;
        }

        @Override
        public boolean canRun(ServerPlayer player, PlayerHorrorProfile profile) {
            return enabled() && !player.isSleeping();
        }

        @Override
        public boolean run(ServerPlayer player) {
            ServerPlayer companion = PenumbraConfig.get().multiplayerContent
                    ? findCompanion(player, 0.0, MAX_NAME_DISTANCE) : null;
            if (companion == null) {
                return AbsentManager.forceSoloSighting(player);
            }
            return AbsentManager.forceSightingAs(
                    player, companion.getGameProfile().name());
        }
    }

    private static ServerPlayer findCompanion(ServerPlayer witness,
                                               double minimumDistance,
                                               double maximumDistance) {
        if (!(witness.level() instanceof ServerLevel level)) {
            return null;
        }
        ServerPlayer best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ServerPlayer candidate : level.players()) {
            double distance = candidate.distanceTo(witness);
            if (MultiplayerSceneRules.eligibleCompanion(
                    candidate == witness, candidate.level() == witness.level(),
                    candidate.isAlive(), candidate.isSpectator(), distance,
                    minimumDistance, maximumDistance)
                    && distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static Vec3 behind(ServerPlayer player, double distance) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        if (flat.lengthSqr() < 1.0E-4) {
            flat = new Vec3(0.0, 0.0, 1.0);
        }
        return player.getEyePosition().subtract(flat.normalize().scale(distance));
    }

    private static void continuePairedEcho(ServerLevel level, UUID witnessId,
                                           UUID companionId) {
        HorrorDirector.runLater(level, 16, () -> {
            ServerPlayer witness = level.getServer().getPlayerList().getPlayer(witnessId);
            if (!validScenePlayer(witness, level)) {
                return;
            }
            if (companionId == null) {
                PenumbraNetworking.sendSoundDirect(witness, witness,
                        PenumbraSounds.BREATH_BEHIND_2, behind(witness, 1.4),
                        0.34f, 0.78f);
                return;
            }
            ServerPlayer companion = level.getServer().getPlayerList().getPlayer(companionId);
            if (!validScenePlayer(companion, level)
                    || companion.distanceTo(witness) > 32.0) {
                return;
            }
            PenumbraNetworking.sendSoundDirect(witness, witness,
                    PenumbraSounds.BREATH_BEHIND_2, behind(witness, 1.4),
                    0.34f, 0.78f);
            PenumbraNetworking.sendSoundDirect(witness, companion,
                    PenumbraSounds.FOOTSTEP_MIMIC_2, behind(witness, 1.8),
                    0.48f, 0.80f);
        });
    }

    private static CrossingPath findCrossingPath(ServerLevel level,
                                                  ServerPlayer subject,
                                                  RandomSource random) {
        Vec3 look = subject.getLookAngle();
        Vec3 forward = new Vec3(look.x, 0.0, look.z);
        if (forward.lengthSqr() < 1.0E-4) {
            forward = new Vec3(0.0, 0.0, 1.0);
        }
        forward = forward.normalize();
        Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
        int baseY = Mth.floor(subject.getY());
        for (int attempt = 0; attempt < 8; attempt++) {
            double rearDistance = 3.0 + random.nextDouble() * 1.5;
            double halfSpan = 1.7 + random.nextDouble() * 1.1;
            double sign = random.nextBoolean() ? 1.0 : -1.0;
            Vec3 center = subject.position().subtract(forward.scale(rearDistance));
            Vec3 startHint = center.add(side.scale(halfSpan * sign));
            Vec3 endHint = center.subtract(side.scale(halfSpan * sign));
            BlockPos startFeet = findStandingSpot(level, startHint, baseY);
            BlockPos endFeet = findStandingSpot(level, endHint, baseY);
            if (startFeet == null || endFeet == null || startFeet.equals(endFeet)) {
                continue;
            }
            Vec3 start = Vec3.atBottomCenterOf(startFeet);
            Vec3 end = Vec3.atBottomCenterOf(endFeet);
            BlockHitResult hit = level.clip(new ClipContext(
                    start.add(0.0, 1.0, 0.0), end.add(0.0, 1.0, 0.0),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, subject));
            if (hit.getType() == HitResult.Type.MISS) {
                return new CrossingPath(start, end);
            }
        }
        return null;
    }

    private static BlockPos findStandingSpot(ServerLevel level, Vec3 hint, int baseY) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int x = Mth.floor(hint.x);
        int z = Mth.floor(hint.z);
        for (int dy = 3; dy >= -6; dy--) {
            cursor.set(x, baseY + dy, z);
            if (!level.isLoaded(cursor)) {
                return null;
            }
            if (level.getBlockState(cursor.below()).isSolidRender()
                    && level.getBlockState(cursor).isAir()
                    && level.getBlockState(cursor.above()).isAir()
                    && level.getFluidState(cursor.below()).isEmpty()
                    && level.getFluidState(cursor).isEmpty()
                    && level.getFluidState(cursor.above()).isEmpty()) {
                return cursor.immutable();
            }
        }
        return null;
    }

    private static void movePrivateCrossing(ServerLevel level, UUID witnessId,
                                            UUID companionId, UUID hostId,
                                            Vec3 start, Vec3 end, int step) {
        HorrorDirector.runLater(level, 1, () -> {
            Entity entity = level.getEntity(hostId);
            ServerPlayer witness = level.getServer().getPlayerList().getPlayer(witnessId);
            if (!(entity instanceof HostEntity host) || !host.isAlive()
                    || !host.isPrivateToOwner()
                    || witness == null || !host.isOwnedBy(witness)
                    || !validScenePlayer(witness, level)) {
                if (entity != null) {
                    entity.discard();
                }
                return;
            }
            ServerPlayer companion = null;
            if (companionId != null) {
                companion = level.getServer().getPlayerList().getPlayer(companionId);
                if (!validScenePlayer(companion, level)
                        || companion.distanceTo(witness) > 32.0) {
                    host.discard();
                    return;
                }
            }
            int nextStep = step + 1;
            double progress = Math.min(1.0,
                    (double) nextStep / BehindOtherEvent.CROSSING_TICKS);
            Vec3 position = start.lerp(end, progress);
            float yaw = movementYaw(start, end);
            host.snapTo(position.x, position.y, position.z, yaw, 0.0f);
            host.setYBodyRot(yaw);
            host.setYHeadRot(yaw);

            if (companion != null && nextStep == BehindOtherEvent.CROSSING_TICKS / 2) {
                PenumbraNetworking.sendSoundDirect(witness, companion,
                        PenumbraSounds.HOST_STEP, position, 0.62f, 0.74f);
            }
            if (nextStep >= BehindOtherEvent.CROSSING_TICKS) {
                host.discard();
                return;
            }
            movePrivateCrossing(level, witnessId, companionId, hostId,
                    start, end, nextStep);
        });
    }

    private static boolean validScenePlayer(ServerPlayer player, ServerLevel level) {
        return player != null && player.level() == level && player.isAlive()
                && !player.isSpectator();
    }

    private static float movementYaw(Vec3 start, Vec3 end) {
        Vec3 delta = end.subtract(start);
        return (float) (Math.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0f;
    }

    private record CrossingPath(Vec3 start, Vec3 end) {
    }
}

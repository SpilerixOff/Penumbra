package fr.penumbra.entity.originel;

import fr.penumbra.PenumbraMod;
import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.director.HorrorDirector;
import fr.penumbra.network.PenumbraC2S;
import fr.penumbra.network.PenumbraNetworking;
import fr.penumbra.registry.PenumbraSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Gestionnaire de l'Originel (0.5.0) — branche {@code originelEnabled} et
 * {@code originelTracesEnabled}. VOLONTAIREMENT hors économie de tension :
 * le directeur rend l'horreur prévisible-dans-sa-logique ; l'Originel est
 * l'exception qui ne suit aucune règle apprise. Il peut apparaître au 3e jour
 * comme jamais — c'est le récit des joueurs entre eux qui fait le reste.
 * <ul>
 *   <li><b>Apparitions</b> : délai planifié de 8–12 min par joueur à fréquence
 *       100 %. En surface, silhouette historique à 60–90 blocs ; en grotte,
 *       scène frontale personnelle à 8–18 blocs. Un seul Originel appartenant
 *       à ce joueur à la fois par zone ;</li>
 *   <li><b>Traces</b> ({@code originelTracesEnabled} + {@code worldModificationEnabled}) :
 *       un petit cairn — deux pierres profondes taillées surmontées d'une
 *       torche des âmes — posé sur le chemin du joueur, au plus un par jour
 *       de jeu par joueur. Les traces PERSISTENT (aucune restauration) :
 *       c'est leur raison d'être — la preuve du passage ;</li>
 *   <li>respect de la période de grâce configurable du directeur.</li>
 * </ul>
 * Coût : une itération des joueurs toutes les 200 ticks, scans bornés.
 */
public final class OriginelManager {

    /** Cadence des décisions (10 s). */
    private static final int CHECK_INTERVAL_TICKS = 200;
    private static final int CHECK_OFFSET_TICKS = 20;
    /** La caméra vérifie le cadrage chaque seconde et invoque après 3 s de zoom continu. */
    private static final int CAMERA_CHECK_INTERVAL_TICKS = 20;
    private static final int CAMERA_FOCUS_TICKS = 60;
    private static final long CAMERA_COOLDOWN_TICKS = 90L * 20L;
    private static final double CAMERA_SPAWN_MIN_DIST = 10.0;
    private static final double CAMERA_SPAWN_MAX_DIST = 28.0;
    private static final double CAMERA_CONE_HALF_ANGLE = Math.toRadians(18.0);
    private static final int CAMERA_SPAWN_ATTEMPTS = 40;
    /** Fenêtre d'apparition en grotte devant le joueur. */
    private static final double CAVE_SPAWN_MIN_DIST = 8.0;
    private static final double CAVE_SPAWN_MAX_DIST = 18.0;
    private static final double CAVE_CONE_HALF_ANGLE = Math.toRadians(18.0);
    private static final int CAVE_SPAWN_ATTEMPTS = 24;
    /** Chance de trace par joueur et par échéance. */
    private static final int TRACE_ONE_IN = 900;
    /** Distance minimale d'apparition (blocs). */
    private static final double SPAWN_MIN_DIST = 28.0;
    /** Distance maximale d'apparition (blocs). */
    private static final double SPAWN_MAX_DIST = 52.0;
    /** Demi-angle du cône d'apparition autour du regard (±25°). */
    private static final double SPAWN_CONE_HALF_ANGLE = Math.toRadians(25.0);
    /** Essais de positions par apparition. */
    private static final int SPAWN_ATTEMPTS = 32;
    /** Rayon d'exclusivité : jamais deux Originels proches. */
    private static final double EXCLUSION_RADIUS = 128.0;
    /** Budget serveur : une manifestation par propriétaire, douze au total. */
    private static final int MAX_ACTIVE = 12;
    /** Distance de pose d'une trace devant le joueur (blocs). */
    private static final double TRACE_MIN_DIST = 20.0;
    /** Distance maximale de pose d'une trace (blocs). */
    private static final double TRACE_MAX_DIST = 35.0;
    /** Intervalle minimal entre deux traces d'un même joueur (1 jour de jeu). */
    private static final long TRACE_COOLDOWN_TICKS = 24000L;

    /** Dernier tick de pose d'une trace, par joueur (purgé à la déconnexion). */
    private static final Map<UUID, Long> LAST_TRACE_TICKS = new HashMap<>();
    /** Prochaine manifestation planifiée, par joueur. */
    private static final Map<UUID, Long> NEXT_SIGHTING_TICKS = new HashMap<>();
    /** Temps de cadrage continu et prochain instant caméra autorisé, par joueur. */
    private static final Map<UUID, Integer> CAMERA_FOCUS = new HashMap<>();
    private static final Map<UUID, Long> NEXT_CAMERA_SIGHTING_TICKS = new HashMap<>();
    /** Réservation exacte des manifestations actives, indépendante de leur dimension. */
    private static final Map<UUID, UUID> ACTIVE_BY_OWNER = new HashMap<>();

    private OriginelManager() {
    }

    /** Boucle serveur (enregistrée dans {@code PenumbraMod.onInitialize}). */
    public static void onServerTick(MinecraftServer server) {
        if (server.getTickCount() % CHECK_INTERVAL_TICKS == 0) {
            pruneInactive(server);
        }
        PenumbraConfig config = PenumbraConfig.get();
        if (!config.originelEnabled || config.eventFrequency <= 0.0f) {
            return;
        }
        if (server.getTickCount() % CAMERA_CHECK_INTERVAL_TICKS == 0) {
            tickCameraSightings(server);
        }
        if (Math.floorMod(server.getTickCount() - CHECK_OFFSET_TICKS,
                CHECK_INTERVAL_TICKS) != 0) {
            return;
        }
        for (ServerPlayer player : HorrorDirector.fairAutomaticPlayers(
                server, CHECK_INTERVAL_TICKS, 0)) {
            if (player.isSpectator() || !player.isAlive()) {
                continue;
            }
            if (HorrorDirector.effectiveContentStage(player) < 4) {
                continue; // l'Originel n'entre qu'au stade SUBSTITUTION
            }
            if (!(player.level() instanceof ServerLevel level)
                    || !level.dimension().equals(Level.OVERWORLD)
                    || level.getDifficulty() == Difficulty.PEACEFUL) {
                continue;
            }
            RandomSource random = player.getRandom();
            long now = level.getGameTime();
            long nextSighting = NEXT_SIGHTING_TICKS.computeIfAbsent(player.getUUID(), ignored ->
                    now + nextSightingDelay(config.eventFrequency, random));
            if (now >= nextSighting) {
                if (HorrorDirector.automaticSceneAvailable(player)) {
                    if (trySighting(level, player)) {
                        HorrorDirector.recordAutomaticScene(player);
                        HorrorDirector.noteManifestation(player);
                    }
                    NEXT_SIGHTING_TICKS.put(player.getUUID(),
                            now + nextSightingDelay(config.eventFrequency, random));
                } else {
                    NEXT_SIGHTING_TICKS.put(player.getUUID(), now + CHECK_INTERVAL_TICKS);
                }
            }
            if (config.originelTracesEnabled && config.worldModificationEnabled
                    && random.nextInt(TRACE_ONE_IN) == 0) {
                tryTrace(level, player);
            }
        }
    }

    /** Seul le cadrage en cours est transitoire ; les échéances survivent à la reconnexion. */
    public static void onPlayerDisconnect(UUID playerId) {
        CAMERA_FOCUS.remove(playerId);
        // Les cooldowns, la planification et la réservation restent valides pendant
        // la session. L'entité libère sa place en se supprimant au tick suivant.
    }

    public static void onServerStopped(MinecraftServer server) {
        LAST_TRACE_TICKS.clear();
        NEXT_SIGHTING_TICKS.clear();
        CAMERA_FOCUS.clear();
        NEXT_CAMERA_SIGHTING_TICKS.clear();
        ACTIVE_BY_OWNER.clear();
    }

    private static int nextSightingDelay(float frequency, RandomSource random) {
        int window = OriginelTiming.MAX_SIGHTING_DELAY_TICKS
                - OriginelTiming.MIN_SIGHTING_DELAY_TICKS;
        return OriginelTiming.sightingDelayTicks(frequency, random.nextInt(window + 1));
    }

    /**
     * Une utilisation volontaire de la caméra est un déclencheur fiable : trois
     * secondes de cadrage continu suffisent, puis un cooldown de 90 secondes
     * évite qu'une longue prise de vue ne fabrique une file de manifestations.
     */
    private static void tickCameraSightings(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID playerId = player.getUUID();
            if (player.isSpectator() || !player.isAlive() || player.isSleeping()
                    || !PenumbraC2S.isZooming(playerId)) {
                CAMERA_FOCUS.remove(playerId);
                continue;
            }
            if (!(player.level() instanceof ServerLevel level)
                    || !level.dimension().equals(Level.OVERWORLD)
                    || level.getDifficulty() == Difficulty.PEACEFUL) {
                CAMERA_FOCUS.remove(playerId);
                continue;
            }
            int focus = CAMERA_FOCUS.merge(playerId, CAMERA_CHECK_INTERVAL_TICKS, Integer::sum);
            long now = level.getGameTime();
            if (focus < CAMERA_FOCUS_TICKS
                    || now < NEXT_CAMERA_SIGHTING_TICKS.getOrDefault(playerId, 0L)) {
                continue;
            }
            if (HorrorDirector.automaticSceneAvailable(player)
                    && forceCameraSighting(player)) {
                CAMERA_FOCUS.put(playerId, 0);
                NEXT_CAMERA_SIGHTING_TICKS.put(playerId, now + CAMERA_COOLDOWN_TICKS);
                HorrorDirector.recordAutomaticScene(player);
                HorrorDirector.noteManifestation(player);
            }
        }
    }

    // ================= Apparitions =================

    /**
     * Tente une apparition en pleine vue : position dans le cône de regard à
     * 60–90 blocs, ciel visible, sol solide + 2 blocs d'air. L'Originel
     * apparaît dos tourné. Les sons sont distribués spatialement par la couche
     * réseau ; la manifestation visuelle reste personnelle à son propriétaire.
     */
    private static boolean trySighting(ServerLevel level, ServerPlayer player) {
        if (!HorrorDirector.physicalManifestationAllowed(player)) {
            return false;
        }
        if (!mayCreate(player)) {
            return false;
        }
        if (OriginelEntity.hasAnyOriginelNearby(
                level, player.blockPosition(), EXCLUSION_RADIUS, player)) {
            return false;
        }
        if (!HorrorDirector.isGraceComplete(player)) {
            return false;
        }

        return spawnForContext(level, player);
    }

    /**
     * Point d'appel sûr pour une commande ou un protocole de test. Contrairement
     * au planning naturel, il ne dépend ni du stade narratif ni de la grâce,
     * mais conserve tous les garde-fous physiques : option Originel, joueur
     * valide, Overworld non paisible, propriété et rayon d'exclusivité.
     *
     * @return {@code true} si une manifestation a réellement été ajoutée.
     */
    public static boolean forceSighting(ServerPlayer player) {
        PenumbraConfig config = PenumbraConfig.get();
        if (!config.originelEnabled || player.isSpectator() || !player.isAlive()
                || !(player.level() instanceof ServerLevel level)
                || !level.dimension().equals(Level.OVERWORLD)
                || level.getDifficulty() == Difficulty.PEACEFUL
                || !HorrorDirector.physicalManifestationAllowed(player)
                || !mayCreate(player)
                || OriginelEntity.hasAnyOriginelNearby(
                        level, player.blockPosition(), EXCLUSION_RADIUS, player)) {
            return false;
        }
        return spawnForContext(level, player);
    }

    /** Apparition rapprochée et visible déclenchée par un cadrage caméra continu. */
    public static boolean forceCameraSighting(ServerPlayer player) {
        PenumbraConfig config = PenumbraConfig.get();
        if (!config.originelEnabled || player.isSpectator() || !player.isAlive()
                || !(player.level() instanceof ServerLevel level)
                || !level.dimension().equals(Level.OVERWORLD)
                || level.getDifficulty() == Difficulty.PEACEFUL
                || !HorrorDirector.physicalManifestationAllowed(player)
                || !mayCreate(player)
                || OriginelEntity.hasAnyOriginelNearby(
                        level, player.blockPosition(), EXCLUSION_RADIUS, player)) {
            return false;
        }
        return tryCameraSighting(level, player);
    }

    /**
     * Invocation explicite depuis le totem hanté. Le rituel ignore le stade de
     * l'histoire et la période de grâce, mais conserve les garde-fous serveur.
     */
    public static boolean forceTotemSighting(ServerPlayer player, BlockPos totemTop) {
        PenumbraConfig config = PenumbraConfig.get();
        if (!config.originelEnabled || player.isSpectator() || !player.isAlive()
                || !(player.level() instanceof ServerLevel level)
                || !level.dimension().equals(Level.OVERWORLD)
                || level.getDifficulty() == Difficulty.PEACEFUL
                || !HorrorDirector.physicalManifestationAllowed(player)
                || !mayCreate(player)
                || OriginelEntity.hasAnyOriginelNearby(
                        level, player.blockPosition(), EXCLUSION_RADIUS, player)) {
            return false;
        }
        return tryTotemSighting(level, player, totemTop);
    }

    private static boolean spawnForContext(ServerLevel level, ServerPlayer player) {
        // En souterrain, la scène courte et frontale est prioritaire. Si le
        // couloir n'offre aucune position sûre et visible, la silhouette
        // lointaine historique reste disponible en solution de repli.
        if (!level.canSeeSky(player.blockPosition()) && tryCaveSighting(level, player)) {
            return true;
        }
        return tryDistantSighting(level, player);
    }

    /**
     * Cherche quarante positions différentes dans le cadre, à 10–28 blocs.
     * La ligne de vue finale est contrôlée avec le vrai vecteur 3D de la caméra,
     * ce qui fonctionne aussi dans une galerie irrégulière.
     */
    private static boolean tryCameraSighting(ServerLevel level, ServerPlayer player) {
        RandomSource random = player.getRandom();
        Vec3 view = player.getViewVector(1.0f).normalize();
        Vec3 flatView = new Vec3(view.x, 0.0, view.z);
        if (flatView.lengthSqr() < 1.0E-4) {
            return false;
        }
        flatView = flatView.normalize();
        double lookAngle = Math.atan2(flatView.z, flatView.x);
        double minimumDot = Math.cos(Math.toRadians(24.0));
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int attempt = 0; attempt < CAMERA_SPAWN_ATTEMPTS; attempt++) {
            double angle = lookAngle
                    + (random.nextDouble() * 2.0 - 1.0) * CAMERA_CONE_HALF_ANGLE;
            double dist = CAMERA_SPAWN_MIN_DIST
                    + random.nextDouble() * (CAMERA_SPAWN_MAX_DIST - CAMERA_SPAWN_MIN_DIST);
            int x = Mth.floor(player.getX() + Math.cos(angle) * dist);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * dist);
            int baseY = Mth.floor(player.getY());

            for (int dy = 10; dy >= -16; dy--) {
                cursor.set(x, baseY + dy, z);
                if (!isValidSpot(level, cursor)) {
                    continue;
                }
                BlockPos spot = cursor.immutable();
                Vec3 eye = Vec3.atBottomCenterOf(spot).add(0.0, 1.62, 0.0);
                Vec3 toApparition = eye.subtract(player.getEyePosition());
                if (toApparition.lengthSqr() < 1.0E-4
                        || view.dot(toApparition.normalize()) < minimumDot
                        || !hasClearView(level, player, eye)) {
                    continue;
                }
                return spawnCloseSighting(level, player, spot,
                        160 + random.nextInt(61), 1.25f, 0.68f);
            }
        }
        return false;
    }

    /** Apparition visible à 4–8 blocs autour du totem, avec repli dans le cadre du joueur. */
    private static boolean tryTotemSighting(ServerLevel level, ServerPlayer player,
                                            BlockPos totemTop) {
        RandomSource random = player.getRandom();
        BlockPos center = totemTop.below(2);
        Vec3 toTotem = Vec3.atCenterOf(center).subtract(player.position());
        Vec3 flat = new Vec3(toTotem.x, 0.0, toTotem.z);
        double baseAngle = flat.lengthSqr() < 1.0E-4
                ? random.nextDouble() * Mth.TWO_PI
                : Math.atan2(flat.z, flat.x);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int attempt = 0; attempt < 32; attempt++) {
            double spread = attempt == 0 ? 0.0
                    : (random.nextDouble() * 2.0 - 1.0) * Math.PI;
            double angle = baseAngle + spread;
            double dist = 4.0 + random.nextDouble() * 4.0;
            int x = Mth.floor(center.getX() + 0.5 + Math.cos(angle) * dist);
            int z = Mth.floor(center.getZ() + 0.5 + Math.sin(angle) * dist);
            for (int dy = 5; dy >= -5; dy--) {
                cursor.set(x, center.getY() + dy, z);
                if (!isValidSpot(level, cursor)) {
                    continue;
                }
                BlockPos spot = cursor.immutable();
                Vec3 eye = Vec3.atBottomCenterOf(spot).add(0.0, 1.62, 0.0);
                if (!hasClearView(level, player, eye)) {
                    continue;
                }
                return spawnCloseSighting(level, player, spot, 200, 1.4f, 0.62f);
            }
        }
        return tryCameraSighting(level, player);
    }

    /** Construit une scène rapprochée personnelle qui fixe immédiatement son propriétaire. */
    private static boolean spawnCloseSighting(ServerLevel level, ServerPlayer player,
                                              BlockPos spot, int durationTicks,
                                              float volume, float pitch) {
        OriginelEntity originel = new OriginelEntity(OriginelEntities.ORIGINEL, level);
        originel.setOwner(player);
        originel.configureCaveSighting(durationTicks);
        double spawnX = spot.getX() + 0.5;
        double spawnZ = spot.getZ() + 0.5;
        float faceYaw = yawTowards(spawnX, spawnZ, player.getX(), player.getZ());
        originel.snapTo(spawnX, spot.getY(), spawnZ, faceYaw, 0.0f);
        originel.setYBodyRot(faceYaw);
        originel.setYHeadRot(faceYaw);
        originel.finalizeSpawn(level, level.getCurrentDifficultyAt(spot),
                EntitySpawnReason.EVENT, null);
        if (!reserve(player.getUUID(), originel.getUUID(), level.getServer())) {
            return false;
        }
        if (!level.addFreshEntity(originel)) {
            release(player.getUUID(), originel.getUUID());
            return false;
        }
        attachTemporaryTorch(level, originel, player.getRandom());
        PenumbraNetworking.sendSound(player, PenumbraSounds.ORIGINEL_HUM,
                originel.position(), volume, pitch);
        return true;
    }

    private static boolean tryDistantSighting(ServerLevel level, ServerPlayer player) {
        RandomSource random = player.getRandom();
        Vec3 look = player.getLookAngle();
        Vec3 flatLook = new Vec3(look.x, 0.0, look.z);
        if (flatLook.lengthSqr() < 1.0E-4) {
            return false; // regard vertical : aucune ligne d'horizon à hanter
        }
        flatLook = flatLook.normalize();
        double lookAngle = Math.atan2(flatLook.z, flatLook.x);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
            double angle = lookAngle + (random.nextDouble() * 2.0 - 1.0) * SPAWN_CONE_HALF_ANGLE;
            double dist = SPAWN_MIN_DIST + random.nextDouble() * (SPAWN_MAX_DIST - SPAWN_MIN_DIST);
            int baseX = Mth.floor(player.getX() + Math.cos(angle) * dist);
            int baseZ = Mth.floor(player.getZ() + Math.sin(angle) * dist);
            int baseY = Mth.floor(player.getY());

            for (int dy = 24; dy >= -24; dy--) {
                cursor.set(baseX, baseY + dy, baseZ);
                if (!isValidSpot(level, cursor) || !level.canSeeSky(cursor)) {
                    continue;
                }
                OriginelEntity originel = new OriginelEntity(OriginelEntities.ORIGINEL, level);
                originel.setOwner(player);
                originel.configureDistantSighting();
                // 26.1 : Entity.moveTo(...) s'appelle snapTo(...) (pattern HostEntity.spawnNear).
                float awayYaw = (float) (Math.atan2(cursor.getZ() + 0.5 - player.getZ(),
                        cursor.getX() + 0.5 - player.getX()) * Mth.RAD_TO_DEG) - 90.0f;
                originel.snapTo(baseX + 0.5, cursor.getY(), baseZ + 0.5, awayYaw, 0.0f);
                originel.setYBodyRot(awayYaw);
                originel.setYHeadRot(awayYaw);
                originel.finalizeSpawn(level, level.getCurrentDifficultyAt(cursor),
                        EntitySpawnReason.EVENT, null);
                if (!reserve(player.getUUID(), originel.getUUID(), level.getServer())) {
                    return false;
                }
                if (!level.addFreshEntity(originel)) {
                    release(player.getUUID(), originel.getUUID());
                    return false;
                }
                attachTemporaryTorch(level, originel, random);
                PenumbraNetworking.sendSound(player, PenumbraSounds.ORIGINEL_HUM,
                        originel.position(), 0.5f, 1.0f);
                if (PenumbraConfig.get().debugLogging) {
                    PenumbraMod.LOGGER.info("[Penumbra] Originel aperçu par {} à {} blocs",
                            player.getGameProfile().name(), Math.round(dist));
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Apparition personnelle à 8–18 blocs dans le cône de regard. La cible doit
     * tenir dans deux blocs d'air et être réellement visible depuis les yeux du
     * joueur : aucun spawn derrière la paroi d'un tunnel.
     */
    private static boolean tryCaveSighting(ServerLevel level, ServerPlayer player) {
        RandomSource random = player.getRandom();
        Vec3 look = player.getLookAngle();
        Vec3 flatLook = new Vec3(look.x, 0.0, look.z);
        if (flatLook.lengthSqr() < 1.0E-4) {
            return false;
        }
        flatLook = flatLook.normalize();
        double lookAngle = Math.atan2(flatLook.z, flatLook.x);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int attempt = 0; attempt < CAVE_SPAWN_ATTEMPTS; attempt++) {
            double angle = lookAngle
                    + (random.nextDouble() * 2.0 - 1.0) * CAVE_CONE_HALF_ANGLE;
            double dist = CAVE_SPAWN_MIN_DIST
                    + random.nextDouble() * (CAVE_SPAWN_MAX_DIST - CAVE_SPAWN_MIN_DIST);
            int x = Mth.floor(player.getX() + Math.cos(angle) * dist);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * dist);
            int baseY = Mth.floor(player.getY());

            for (int dy = 4; dy >= -8; dy--) {
                cursor.set(x, baseY + dy, z);
                if (!isValidSpot(level, cursor) || level.canSeeSky(cursor)) {
                    continue;
                }
                BlockPos spot = cursor.immutable();
                Vec3 apparitionEye = Vec3.atBottomCenterOf(spot).add(0.0, 1.62, 0.0);
                if (!hasClearView(level, player, apparitionEye)) {
                    continue;
                }

                OriginelEntity originel = new OriginelEntity(OriginelEntities.ORIGINEL, level);
                originel.setOwner(player);
                originel.configureCaveSighting(80 + random.nextInt(61));
                double spawnX = spot.getX() + 0.5;
                double spawnZ = spot.getZ() + 0.5;
                float faceYaw = yawTowards(spawnX, spawnZ, player.getX(), player.getZ());
                originel.snapTo(spawnX, spot.getY(), spawnZ, faceYaw, 0.0f);
                originel.setYBodyRot(faceYaw);
                originel.setYHeadRot(faceYaw);
                originel.finalizeSpawn(level, level.getCurrentDifficultyAt(spot),
                        EntitySpawnReason.EVENT, null);
                if (!reserve(player.getUUID(), originel.getUUID(), level.getServer())) {
                    return false;
                }
                if (!level.addFreshEntity(originel)) {
                    release(player.getUUID(), originel.getUUID());
                    return false;
                }
                attachTemporaryTorch(level, originel, random);
                PenumbraNetworking.sendSound(player, PenumbraSounds.ORIGINEL_HUM,
                        originel.position(), 1.2f, 0.72f + random.nextFloat() * 0.08f);
                if (PenumbraConfig.get().debugLogging) {
                    PenumbraMod.LOGGER.info("[Penumbra] Originel en grotte devant {} à {} blocs",
                            player.getGameProfile().name(), Math.round(dist));
                }
                return true;
            }
        }
        return false;
    }

    private static boolean hasClearView(ServerLevel level, ServerPlayer player, Vec3 target) {
        BlockHitResult hit = level.clip(new ClipContext(player.getEyePosition(), target,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS;
    }

    private static float yawTowards(double fromX, double fromZ, double toX, double toZ) {
        return (float) (Math.atan2(toZ - fromZ, toX - fromX) * Mth.RAD_TO_DEG) - 90.0f;
    }

    /** Pose et confie à l'entité une torche de redstone temporaire. */
    private static void attachTemporaryTorch(ServerLevel level, OriginelEntity originel,
                                             RandomSource random) {
        if (!PenumbraConfig.get().worldModificationEnabled) {
            return;
        }
        BlockPos origin = originel.blockPosition();
        int start = random.nextInt(8);
        BlockState torch = Blocks.REDSTONE_TORCH.defaultBlockState();
        // Deux anneaux et un petit balayage vertical : cela couvre aussi les
        // tunnels d'un bloc de large et les sols irréguliers sans s'éloigner de
        // la manifestation.
        int[][] directions = {
                {1, 0}, {1, 1}, {0, 1}, {-1, 1},
                {-1, 0}, {-1, -1}, {0, -1}, {1, -1}
        };
        for (int radius = 1; radius <= 2; radius++) {
            for (int dy = 1; dy >= -2; dy--) {
                for (int i = 0; i < directions.length; i++) {
                    int[] direction = directions[(start + i) % directions.length];
                    BlockPos pos = origin.offset(direction[0] * radius, dy,
                            direction[1] * radius);
                    if (!level.isLoaded(pos) || !level.getBlockState(pos).isAir()
                            || !torch.canSurvive(level, pos)) {
                        continue;
                    }
                    BlockState original = level.getBlockState(pos);
                    if (level.setBlock(pos, torch, Block.UPDATE_ALL)) {
                        originel.setTemporaryTorch(pos, original, torch);
                    }
                    return;
                }
            }
        }
    }

    /** Emplacement valide : chunk chargé, sol solide, 2 blocs d'air, aucun liquide. */
    private static boolean isValidSpot(ServerLevel level, BlockPos feet) {
        if (!level.isLoaded(feet)) {
            return false;
        }
        BlockPos ground = feet.below();
        BlockPos head = feet.above();
        // 26.1 : isSolidRender() sans arguments (pattern HostEntity.isValidSpawnSpot).
        return level.getBlockState(ground).isSolidRender()
                && level.getBlockState(feet).isAir()
                && level.getBlockState(head).isAir()
                && level.getFluidState(ground).isEmpty()
                && level.getFluidState(feet).isEmpty()
                && level.getFluidState(head).isEmpty();
    }

    /** Vérifie le quota sans le réserver, avant les recherches de position coûteuses. */
    private static boolean mayCreate(ServerPlayer owner) {
        MinecraftServer server = owner.level().getServer();
        pruneInactive(server);
        return !ACTIVE_BY_OWNER.containsKey(owner.getUUID())
                && ACTIVE_BY_OWNER.size() < MAX_ACTIVE;
    }

    /** Réserve atomiquement la place d'une entité avant son ajout au monde. */
    private static boolean reserve(UUID ownerId, UUID entityId, MinecraftServer server) {
        if (ownerId == null || entityId == null) {
            return false;
        }
        UUID current = ACTIVE_BY_OWNER.get(ownerId);
        if (entityId.equals(current)) {
            return true;
        }
        if (current != null) {
            Entity active = find(server, current);
            if (active instanceof OriginelEntity && active.isAlive()) {
                return false;
            }
            ACTIVE_BY_OWNER.remove(ownerId, current);
        }
        pruneInactive(server);
        if (ACTIVE_BY_OWNER.size() >= MAX_ACTIVE) {
            return false;
        }
        ACTIVE_BY_OWNER.put(ownerId, entityId);
        return true;
    }

    static void release(UUID ownerId, UUID entityId) {
        if (ownerId != null && entityId != null) {
            ACTIVE_BY_OWNER.remove(ownerId, entityId);
        }
    }

    private static void pruneInactive(MinecraftServer server) {
        ACTIVE_BY_OWNER.entrySet().removeIf(entry -> {
            Entity entity = find(server, entry.getValue());
            return !(entity instanceof OriginelEntity) || !entity.isAlive();
        });
    }

    private static Entity find(MinecraftServer server, UUID entityId) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(entityId);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    // ================= Traces =================

    /**
     * Pose un cairn de l'Originel 20–35 blocs devant le joueur (direction de
     * déplacement, sinon de regard) : deux pierres profondes taillées + une
     * torche des âmes. Sol naturellement solide, cible à l'air libre. Au plus
     * une trace par jour de jeu et par joueur. AUCUNE restauration : la trace
     * est faite pour être retrouvée — aujourd'hui, ou dans cent heures.
     * (0.6.0 : les traces « Herobrine raté » — totems, tunnels, panneaux —
     * appartiennent désormais au Contrefait, quatrième présence du mod ;
     * le cairn minéral et muet reste la signature de l'Originel.)
     */
    private static void tryTrace(ServerLevel level, ServerPlayer player) {
        long gameTime = level.getGameTime();
        Long last = LAST_TRACE_TICKS.get(player.getUUID());
        if (last != null && gameTime - last < TRACE_COOLDOWN_TICKS) {
            return;
        }
        RandomSource random = player.getRandom();
        Vec3 motion = player.getDeltaMovement();
        Vec3 dir = new Vec3(motion.x, 0.0, motion.z);
        if (dir.lengthSqr() < 1.0E-4) {
            Vec3 look = player.getLookAngle();
            dir = new Vec3(look.x, 0.0, look.z);
        }
        if (dir.lengthSqr() < 1.0E-4) {
            return;
        }
        dir = dir.normalize();
        double dist = TRACE_MIN_DIST + random.nextDouble() * (TRACE_MAX_DIST - TRACE_MIN_DIST);
        double side = (random.nextDouble() - 0.5) * 12.0; // légèrement à l'écart du chemin
        int baseX = Mth.floor(player.getX() + dir.x * dist - dir.z * side);
        int baseZ = Mth.floor(player.getZ() + dir.z * dist + dir.x * side);
        int baseY = Mth.floor(player.getY());
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dy = 12; dy >= -12; dy--) {
            cursor.set(baseX, baseY + dy, baseZ);
            BlockPos feet = cursor.immutable();
            if (!level.isLoaded(feet)
                    || !level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)
                    || !level.getBlockState(feet).isAir()
                    || !level.getBlockState(feet.above()).isAir()
                    || !level.getBlockState(feet.above(2)).isAir()) {
                continue;
            }
            level.setBlock(feet, Blocks.COBBLED_DEEPSLATE.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(feet.above(), Blocks.COBBLED_DEEPSLATE.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(feet.above(2), Blocks.SOUL_TORCH.defaultBlockState(), Block.UPDATE_ALL);
            LAST_TRACE_TICKS.put(player.getUUID(), gameTime);
            if (PenumbraConfig.get().debugLogging) {
                PenumbraMod.LOGGER.info("[Penumbra] Trace de l'Originel posée près de {} ({})",
                        player.getGameProfile().name(), feet.toShortString());
            }
            return;
        }
    }
}

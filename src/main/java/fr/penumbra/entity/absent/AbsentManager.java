package fr.penumbra.entity.absent;

import fr.penumbra.PenumbraMod;
import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.director.HorrorDirector;
import fr.penumbra.event.personal.MultiplayerSceneRules;
import fr.penumbra.perception.PerceptionRules;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Apparitions naturelles de L'Absent.
 *
 * <p>La boucle, la purge et les déconnexions sont branchées depuis
 * {@code PenumbraMod}. La commande de diagnostic est intégrée à l'arbre commun
 * {@code /penumbra absent test}.</p>
 */
public final class AbsentManager {

    private static final int CHECK_INTERVAL_TICKS = 100;
    private static final int CHECK_OFFSET_TICKS = 80;
    private static final int BASE_ONE_IN = 80;
    private static final long PLAYER_COOLDOWN_TICKS = 20L * 60L * 8L;
    private static final double EXCLUSION_RADIUS = 112.0;
    private static final int MINIMUM_CONTENT_STAGE = 2;
    private static final int SPAWN_ATTEMPTS = 20;
    private static final double SPAWN_MIN_DISTANCE = 18.0;
    private static final double SPAWN_MAX_DISTANCE = 34.0;
    private static final int REFUGE_SCAN_RADIUS = 10;
    private static final int MAX_LOCAL_ABSENTS = 2;

    private static final Map<UUID, Long> LAST_SIGHTING_TICKS = new HashMap<>();

    private AbsentManager() {
    }

    public static void onServerTick(MinecraftServer server) {
        if (!PenumbraConfig.get().absentEnabled) {
            return;
        }
        if (Math.floorMod(server.getTickCount() - CHECK_OFFSET_TICKS,
                CHECK_INTERVAL_TICKS) != 0) {
            return;
        }
        PenumbraConfig config = PenumbraConfig.get();
        float effectiveFrequency = Math.max(0.0f, config.eventFrequency)
                * Math.max(0.0f, config.horrorIntensity);
        if (effectiveFrequency <= 0.0f) {
            return;
        }
        int oneIn = Math.max(1, Math.round(BASE_ONE_IN / effectiveFrequency));
        long now = server.getTickCount();
        for (ServerPlayer player : HorrorDirector.fairAutomaticPlayers(
                server, CHECK_INTERVAL_TICKS, 3)) {
            if (!canSpawnNaturally(player, now) || player.getRandom().nextInt(oneIn) != 0) {
                continue;
            }
            if (!HorrorDirector.automaticSceneAvailable(player)) {
                continue;
            }
            if (spawnFor(player, null) != null) {
                LAST_SIGHTING_TICKS.put(player.getUUID(), now);
                HorrorDirector.recordAutomaticScene(player);
                HorrorDirector.noteManifestation(player);
            }
        }
    }

    private static boolean canSpawnNaturally(ServerPlayer player, long now) {
        if (!player.isAlive() || player.isSpectator() || player.isSleeping()
                || player.level().getDifficulty() == Difficulty.PEACEFUL) {
            return false;
        }
        Long last = LAST_SIGHTING_TICKS.get(player.getUUID());
        if (last != null && now - last < PLAYER_COOLDOWN_TICKS) {
            return false;
        }
        if (!HorrorDirector.isGraceComplete(player)
                || !HorrorDirector.physicalManifestationAllowed(player)
                || HorrorDirector.effectiveContentStage(player) < MINIMUM_CONTENT_STAGE) {
            return false;
        }
        ServerLevel level = (ServerLevel) player.level();
        return !hasAbsentNearby(level, player.blockPosition(), EXCLUSION_RADIUS, player)
                && countAbsentsNearby(level, player.blockPosition(), EXCLUSION_RADIUS)
                < MAX_LOCAL_ABSENTS;
    }

    /**
     * Commande/API de test : ignore grâce, stade, chance et cooldown, mais garde
     * toutes les validations de placement.
     */
    public static boolean forceSighting(ServerPlayer player) {
        if (!PenumbraConfig.get().absentEnabled
                || !player.isAlive() || player.isSpectator()
                || player.level().getDifficulty() == Difficulty.PEACEFUL) {
            return false;
        }
        return spawnFor(player, null) != null;
    }

    /**
     * Variante scénarisée : vole exactement le nom du compagnon déjà validé
     * par l'événement. L'entité reste exclusivement liée à {@code player} pour
     * le ciblage, même si elle porte le nom d'un autre joueur.
     */
    public static boolean forceSightingAs(ServerPlayer player, String imitatedName) {
        if (!PenumbraConfig.get().absentEnabled
                || !player.isAlive() || player.isSpectator()
                || player.level().getDifficulty() == Difficulty.PEACEFUL
                || imitatedName == null || imitatedName.isBlank()) {
            return false;
        }
        String safeName = imitatedName.strip();
        if (safeName.length() > 32) {
            safeName = safeName.substring(0, 32);
        }
        return spawnFor(player, safeName) != null;
    }

    /**
     * Apparition solo : L'Absent porte l'identité d'un autre.
     *
     * <p>Copiait le témoin lui-même jusqu'en 0.19.0. C'était l'erreur de fond de
     * la scène : on reconnaît son propre skin instantanément, et le doute — qui
     * est toute la mécanique — n'a jamais le temps de s'installer. Le choix
     * passe maintenant par {@link KnownIdentities}, qui rend un ancien visiteur
     * du monde ou l'inconnu de cette graine, jamais le témoin.</p>
     */
    public static boolean forceSoloSighting(ServerPlayer player) {
        if (!PenumbraConfig.get().absentEnabled
                || !player.isAlive() || player.isSpectator()
                || player.level().getDifficulty() == Difficulty.PEACEFUL) {
            return false;
        }
        return spawnFor(player, null) != null;
    }

    /**
     * Crée un faux compagnon privé. Le profil copié est choisi parmi les joueurs
     * réels cachés au regard ; sans personne d'autre en ligne, il vient du
     * registre des identités du monde — jamais du témoin (0.19.0).
     */
    public static AbsentEntity forceFalseCompanion(ServerPlayer owner) {
        PenumbraConfig config = PenumbraConfig.get();
        if (!config.individualPerceptionsEnabled || !config.falseCompanionsEnabled
                || !owner.isAlive() || owner.isSpectator()
                || owner.level().getDifficulty() == Difficulty.PEACEFUL) {
            return null;
        }
        ServerLevel level = owner.level();
        if (hasAbsentNearby(level, owner.blockPosition(), EXCLUSION_RADIUS, owner)) {
            return null;
        }
        ServerPlayer copied = config.multiplayerContent
                ? chooseImitatedPlayer(owner,
                level.getServer().getPlayerList().getPlayers(), owner.getRandom())
                : null;
        return spawnFalseCompanion(level, owner, copied);
    }

    private static AbsentEntity spawnFalseCompanion(
            ServerLevel level, ServerPlayer owner, ServerPlayer copied) {
        BlockPos feet = findPerceptionSpot(level, owner, owner.getRandom());
        if (feet == null) {
            return null;
        }
        AbsentEntity echo = new AbsentEntity(AbsentEntities.FALSE_COMPANION, level);
        if (copied != null) {
            echo.bindTo(owner, copied);
        } else {
            // Un faux compagnon qui a votre propre visage n'est pas un compagnon,
            // c'est un miroir — et un miroir ne trompe personne (0.19.0).
            echo.bindTo(owner, KnownIdentities.pick(level.getServer(), owner,
                    owner.getRandom(), PenumbraConfig.get().multiplayerContent));
        }
        float facingYaw = (float) (Math.atan2(owner.getZ() - (feet.getZ() + 0.5),
                owner.getX() - (feet.getX() + 0.5)) * Mth.RAD_TO_DEG) - 90.0f;
        echo.snapTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5,
                facingYaw, 0.0f);
        echo.setYBodyRot(facingYaw);
        echo.setYHeadRot(facingYaw);
        if (!level.noCollision(echo)) {
            return null;
        }
        echo.finalizeSpawn(level, level.getCurrentDifficultyAt(feet),
                EntitySpawnReason.EVENT, null);
        echo.configureFalseCompanion(
                PerceptionRules.lifetimeTicks(owner.getRandom().nextLong()));
        if (!level.addFreshEntity(echo)) {
            return null;
        }
        if (configDebug()) {
            PenumbraMod.LOGGER.info(
                    "[Penumbra] Faux compagnon '{}' perçu uniquement par {} à {}",
                    echo.getImitatedName(), owner.getGameProfile().name(), feet.toShortString());
        }
        return echo;
    }

    private static boolean configDebug() {
        return PenumbraConfig.get().debugLogging;
    }

    private static AbsentEntity spawnFor(ServerPlayer owner, String forcedImitatedName) {
        ServerLevel level = (ServerLevel) owner.level();
        if (hasAbsentNearby(level, owner.blockPosition(), EXCLUSION_RADIUS, owner)
                || countAbsentsNearby(level, owner.blockPosition(), EXCLUSION_RADIUS)
                >= MAX_LOCAL_ABSENTS) {
            return null;
        }

        RandomSource random = owner.getRandom();
        BlockPos refuge = findRefugeAnchor(level, owner.blockPosition(), REFUGE_SCAN_RADIUS);
        BlockPos feet = refuge == null
                ? findDistantSpot(level, owner, random)
                : findSpotAround(level, owner, refuge, 9.0, 17.0, random);
        if (feet == null && refuge != null) {
            feet = findDistantSpot(level, owner, random);
        }
        if (feet == null) {
            return null;
        }

        AbsentEntity absent = new AbsentEntity(AbsentEntities.ABSENT, level);
        boolean multiplayer = PenumbraConfig.get().multiplayerContent;
        ServerPlayer imitatedPlayer = forcedImitatedName == null
                ? (multiplayer
                        ? chooseImitatedPlayer(owner,
                                level.getServer().getPlayerList().getPlayers(), random)
                        : null)
                : findOnlinePlayer(level, forcedImitatedName);
        if (imitatedPlayer != null) {
            // Joueur réellement connecté : on copie aussi son équipement et sa
            // posture, c'est la version la plus troublante de la scène.
            absent.bindTo(owner, imitatedPlayer);
        } else if (forcedImitatedName != null) {
            // Scène qui impose un alias : on l'honore même s'il n'est pas connecté.
            absent.bindTo(owner, forcedImitatedName);
        } else {
            // Personne d'autre en ligne. On prend une identité du registre du
            // monde — un ancien visiteur, ou l'inconnu de cette graine. Jamais
            // le témoin : se voir soi-même n'a jamais fait peur à personne.
            absent.bindTo(owner, KnownIdentities.pick(
                    level.getServer(), owner, random, multiplayer));
        }
        float facingYaw = (float) (Math.atan2(owner.getZ() - (feet.getZ() + 0.5),
                owner.getX() - (feet.getX() + 0.5)) * Mth.RAD_TO_DEG) - 90.0f;
        absent.snapTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, facingYaw, 0.0f);
        absent.setYBodyRot(facingYaw);
        absent.setYHeadRot(facingYaw);
        if (!level.noCollision(absent)) {
            return null;
        }
        absent.finalizeSpawn(level, level.getCurrentDifficultyAt(feet),
                EntitySpawnReason.EVENT, null);
        if (!level.addFreshEntity(absent)) {
            return null;
        }
        if (PenumbraConfig.get().debugLogging) {
            PenumbraMod.LOGGER.info("[Penumbra] L'Absent imite '{}' pour {} à {}",
                    absent.getCustomName() == null ? "?" : absent.getCustomName().getString(),
                    owner.getGameProfile().name(), feet.toShortString());
        }
        return absent;
    }

    /** Compatibilité des anciens tests/outils qui ne demandaient que le nom. */
    static String chooseImitatedName(ServerPlayer owner, List<ServerPlayer> players,
                                     RandomSource random) {
        return chooseImitatedPlayer(owner, players, random).getGameProfile().name();
    }

    /**
     * Choisit toujours un autre profil connecté en multijoueur. Les compagnons
     * cachés dans la même dimension sont prioritaires, puis tous les joueurs de
     * cette dimension, même au-delà de 64 blocs. Une autre dimension reste un
     * meilleur repli que l'auto-copie, réservée au vrai solo.
     */
    static ServerPlayer chooseImitatedPlayer(ServerPlayer owner,
                                              List<ServerPlayer> players,
                                              RandomSource random) {
        List<ServerPlayer> candidates = new ArrayList<>();
        int bestPriority = -1;
        for (ServerPlayer candidate : players) {
            boolean sameDimension = candidate.level() == owner.level();
            double distance = sameDimension
                    ? candidate.distanceTo(owner) : Double.POSITIVE_INFINITY;
            boolean clearlyVisible = false;
            if (sameDimension && !candidate.getUUID().equals(owner.getUUID())) {
                Vec3 toCandidate = candidate.getEyePosition().subtract(owner.getEyePosition());
                clearlyVisible = toCandidate.lengthSqr() > 1.0E-5
                        && owner.hasLineOfSight(candidate)
                        && owner.getLookAngle().dot(toCandidate.normalize()) >= 0.55;
            }
            int priority = MultiplayerSceneRules.imitationSourcePriority(
                    candidate.getUUID().equals(owner.getUUID()), sameDimension,
                    candidate.isAlive(), candidate.isSpectator(), distance,
                    clearlyVisible);
            if (priority < 0 || priority < bestPriority) {
                continue;
            }
            if (priority > bestPriority) {
                candidates.clear();
                bestPriority = priority;
            }
            candidates.add(candidate);
        }
        // Retourne null plutôt que le témoin (corrigé en 0.19.0) : l'auto-copie
        // était le repli, donc le cas courant en solo, et se reconnaître soi-même
        // évente la scène instantanément. L'appelant passe alors par
        // KnownIdentities, qui ne rend jamais le témoin.
        return candidates.isEmpty()
                ? null : candidates.get(random.nextInt(candidates.size()));
    }

    private static ServerPlayer findOnlinePlayer(ServerLevel level, String name) {
        for (ServerPlayer candidate : level.getServer().getPlayerList().getPlayers()) {
            if (candidate.level() == level
                    && candidate.getGameProfile().name().equalsIgnoreCase(name)) {
                return candidate;
            }
        }
        return null;
    }

    private static BlockPos findRefugeAnchor(ServerLevel level, BlockPos origin, int radius) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int r = 1; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -Math.min(r, 5); dy <= Math.min(r, 5); dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                            continue;
                        }
                        cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                        if (isRefugeMarker(level.getBlockState(cursor))) {
                            return cursor.immutable();
                        }
                    }
                }
            }
        }
        return null;
    }

    private static boolean isRefugeMarker(BlockState state) {
        return state.is(BlockTags.BEDS)
                || state.is(BlockTags.WOODEN_DOORS)
                || state.getBlock() instanceof ChestBlock;
    }

    private static BlockPos findDistantSpot(ServerLevel level, ServerPlayer owner,
                                            RandomSource random) {
        return findSpotAround(level, owner, owner.blockPosition(),
                SPAWN_MIN_DISTANCE, SPAWN_MAX_DISTANCE, random);
    }

    /** Cherche un point chargé dans la vision périphérique, sans forcer de chunk. */
    private static BlockPos findPerceptionSpot(ServerLevel level, ServerPlayer owner,
                                               RandomSource random) {
        Vec3 look = owner.getLookAngle().multiply(1.0, 0.0, 1.0);
        if (look.lengthSqr() < 1.0E-5) {
            look = new Vec3(0.0, 0.0, 1.0);
        } else {
            look = look.normalize();
        }
        double baseAngle = Math.atan2(look.z, look.x);
        // Davantage d'essais et une distance minimale plus courte rendent la
        // scène réellement plaçable dans les galeries étroites, sans jamais
        // forcer de chunk ni apparaître au contact du joueur.
        for (int attempt = 0; attempt < 40; attempt++) {
            double side = random.nextBoolean() ? 1.0 : -1.0;
            double offset = Math.toRadians(42.0 + random.nextDouble() * 78.0) * side;
            double angle = baseAngle + offset;
            double distance = 6.5 + random.nextDouble() * 11.5;
            int x = Mth.floor(owner.getX() + Math.cos(angle) * distance);
            int z = Mth.floor(owner.getZ() + Math.sin(angle) * distance);
            BlockPos feet = findStandingSpot(level, x, owner.blockPosition().getY(), z, 9, 16);
            if (feet == null) {
                continue;
            }
            Vec3 target = Vec3.atBottomCenterOf(feet).add(0.0, 1.45, 0.0);
            if (level.clip(new ClipContext(owner.getEyePosition(), target,
                    ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, owner))
                    .getType() == HitResult.Type.MISS) {
                return feet;
            }
        }
        return null;
    }

    private static BlockPos findSpotAround(ServerLevel level, ServerPlayer owner, BlockPos center,
                                           double minDistance, double maxDistance,
                                           RandomSource random) {
        for (int attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
            double angle = random.nextDouble() * Mth.TWO_PI;
            double distance = minDistance + random.nextDouble() * (maxDistance - minDistance);
            int x = Mth.floor(center.getX() + Math.cos(angle) * distance);
            int z = Mth.floor(center.getZ() + Math.sin(angle) * distance);
            BlockPos feet = findStandingSpot(level, x, owner.blockPosition().getY(), z, 12, 24);
            if (feet != null && feet.distSqr(owner.blockPosition()) >= 10.0 * 10.0) {
                return feet;
            }
        }
        return null;
    }

    private static BlockPos findStandingSpot(ServerLevel level, int x, int baseY, int z,
                                             int up, int down) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = up; dy >= -down; dy--) {
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

    public static boolean hasAbsentNearby(ServerLevel level, BlockPos center, double radius,
                                           ServerPlayer owner) {
        return !level.getEntitiesOfClass(AbsentEntity.class, new AABB(center).inflate(radius),
                absent -> absent.isAlive() && absent.isOwnedBy(owner)).isEmpty();
    }

    private static int countAbsentsNearby(ServerLevel level, BlockPos center, double radius) {
        return level.getEntitiesOfClass(AbsentEntity.class, new AABB(center).inflate(radius),
                absent -> absent.isAlive() && !absent.isFalseCompanion()).size();
    }

    public static void onPlayerDisconnect(UUID playerId) {
        // Le cooldown individuel survit à une reconnexion pendant la session.
    }

    /** Purge les cooldowns de session lors de l'arrêt du serveur. */
    public static void onServerStopped(MinecraftServer server) {
        LAST_SIGHTING_TICKS.clear();
    }
}

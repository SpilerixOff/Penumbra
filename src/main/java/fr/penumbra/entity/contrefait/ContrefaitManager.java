package fr.penumbra.entity.contrefait;

import fr.penumbra.PenumbraMod;
import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.director.HorrorDirector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Gestionnaire du Contrefait (0.6.0) — branche {@code contrefaitEnabled}.
 * Comme l'Originel, VOLONTAIREMENT hors économie de tension : la légende
 * n'obéit pas au directeur — et sa copie ratée non plus.
 * <ul>
 *   <li><b>Apparitions</b> : chance de base {@code 1/150} par joueur toutes les
 *       200 ticks (~1 fois toutes les 25 min à fréquence 100 %), à 30–50 blocs
 *       dans le cône de regard, FACE au joueur (il fixe,
 *       comme la légende), en silence total. Puis il applique sa règle : il
 *       n'avance que quand on ne le regarde pas ({@link ContrefaitEntity}) ;</li>
 *   <li><b>Traces « légende ratée »</b> ({@code worldModificationEnabled}) :
 *       totem raté sans feu propagateur ou panneau fautif (« JE TE VOIT »,
 *       clés {@code penumbra.sign.originel_1..4}). Les traces ne retirent plus
 *       aucun bloc existant. Au plus une trace par jour et par joueur ;</li>
 *   <li>respect de la grâce configurable et du mode paisible.</li>
 * </ul>
 * L'imitation imparfaite est le thème du mod : l'Hôte apprend à être VOUS,
 * le Contrefait apprend à être un MYTHE — et les deux se trompent.
 */
public final class ContrefaitManager {

    /** Cadence des décisions (10 s). */
    private static final int CHECK_INTERVAL_TICKS = 200;
    private static final int CHECK_OFFSET_TICKS = 40;
    /** Base : moyenne de 25 min à fréquence 100 %, modulée par eventFrequency. */
    private static final int SIGHTING_ONE_IN = 150;
    /** Chance de trace par joueur et par échéance. */
    private static final int TRACE_ONE_IN = 900;
    /** Distance minimale d'apparition (blocs). */
    private static final double SPAWN_MIN_DIST = 30.0;
    /** Distance maximale d'apparition (blocs). */
    private static final double SPAWN_MAX_DIST = 50.0;
    /** Demi-angle du cône d'apparition autour du regard (±30°). */
    private static final double SPAWN_CONE_HALF_ANGLE = Math.toRadians(30.0);
    /** Essais de positions par apparition. */
    private static final int SPAWN_ATTEMPTS = 16;
    /** Rayon d'exclusivité : jamais deux Contrefaits proches. */
    private static final double EXCLUSION_RADIUS = 128.0;
    /** Distance de pose d'une trace devant le joueur (blocs). */
    private static final double TRACE_MIN_DIST = 20.0;
    /** Distance maximale de pose d'une trace (blocs). */
    private static final double TRACE_MAX_DIST = 35.0;
    /** Intervalle minimal entre deux traces d'un même joueur (1 jour de jeu). */
    private static final long TRACE_COOLDOWN_TICKS = 24000L;

    /** Dernier tick de pose d'une trace, par joueur (purgé à la déconnexion). */
    private static final Map<UUID, Long> LAST_TRACE_TICKS = new HashMap<>();

    private ContrefaitManager() {
    }

    /** Boucle serveur (enregistrée dans {@code PenumbraMod.onInitialize}). */
    public static void onServerTick(MinecraftServer server) {
        if (Math.floorMod(server.getTickCount() - CHECK_OFFSET_TICKS,
                CHECK_INTERVAL_TICKS) != 0) {
            return;
        }
        PenumbraConfig config = PenumbraConfig.get();
        if (!config.contrefaitEnabled || config.eventFrequency <= 0.0f) {
            return;
        }
        for (ServerPlayer player : HorrorDirector.fairAutomaticPlayers(
                server, CHECK_INTERVAL_TICKS, 1)) {
            if (player.isSpectator() || !player.isAlive()) {
                continue;
            }
            if (HorrorDirector.effectiveContentStage(player) < 5) {
                continue; // le Contrefait est une manifestation tardive (RECONNAISSANCE)
            }
            if (!(player.level() instanceof ServerLevel level)
                    || !level.dimension().equals(Level.OVERWORLD)
                    || level.getDifficulty() == Difficulty.PEACEFUL) {
                continue;
            }
            RandomSource random = player.getRandom();
            int sightingOneIn = Math.max(1, Math.round(
                    SIGHTING_ONE_IN / Math.max(0.25f, config.eventFrequency)));
            if (random.nextInt(sightingOneIn) == 0
                    && HorrorDirector.automaticSceneAvailable(player)) {
                trySighting(level, player);
            }
            if (config.worldModificationEnabled && random.nextInt(TRACE_ONE_IN) == 0) {
                tryTrace(level, player);
            }
        }
    }

    /** Purge de l'état transitoire d'un joueur (appelée à sa déconnexion). */
    public static void onPlayerDisconnect(UUID playerId) {
        // Le cooldown doit survivre aux reconnexions pendant toute la session serveur.
    }

    public static void onServerStopped(MinecraftServer server) {
        LAST_TRACE_TICKS.clear();
    }

    // ================= Apparitions =================

    /**
     * Apparition dans le cône de regard à 30–50 blocs, FACE au joueur, en
     * silence — la légende ne s'annonce pas. Respecte la grâce du profil,
     * le kill-switch multijoueur et l'exclusivité de zone.
     */
    private static void trySighting(ServerLevel level, ServerPlayer player) {
        if (!HorrorDirector.physicalManifestationAllowed(player)) {
            return;
        }
        if (ContrefaitEntity.hasAnyContrefaitNearby(
                level, player.blockPosition(), EXCLUSION_RADIUS, player)) {
            return;
        }
        if (!HorrorDirector.isGraceComplete(player)) {
            return;
        }

        RandomSource random = player.getRandom();
        Vec3 look = player.getLookAngle();
        Vec3 flatLook = new Vec3(look.x, 0.0, look.z);
        if (flatLook.lengthSqr() < 1.0E-4) {
            return;
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

            for (int dy = 16; dy >= -16; dy--) {
                cursor.set(baseX, baseY + dy, baseZ);
                if (!isValidSpot(level, cursor)) {
                    continue;
                }
                ContrefaitEntity mob = new ContrefaitEntity(ContrefaitEntities.CONTREFAIT, level);
                mob.setOwner(player);
                // Face au joueur : il fixe, comme la légende (26.1 : snapTo).
                float facingYaw = (float) (Math.atan2(player.getZ() - (cursor.getZ() + 0.5),
                        player.getX() - (cursor.getX() + 0.5)) * Mth.RAD_TO_DEG) - 90.0f;
                mob.snapTo(baseX + 0.5, cursor.getY(), baseZ + 0.5, facingYaw, 0.0f);
                mob.setYBodyRot(facingYaw);
                mob.setYHeadRot(facingYaw);
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(cursor),
                        EntitySpawnReason.EVENT, null);
                if (!level.addFreshEntity(mob)) {
                    return;
                }
                HorrorDirector.recordAutomaticScene(player);
                HorrorDirector.noteManifestation(player);
                if (PenumbraConfig.get().debugLogging) {
                    PenumbraMod.LOGGER.info("[Penumbra] Contrefait apparu pour {} à {} blocs",
                            player.getGameProfile().name(), Math.round(dist));
                }
                return;
            }
        }
    }

    /** Emplacement valide : chargé, sol solide, 2 blocs d'air, aucun liquide. */
    private static boolean isValidSpot(ServerLevel level, BlockPos feet) {
        if (!level.isLoaded(feet)) {
            return false;
        }
        BlockPos ground = feet.below();
        BlockPos head = feet.above();
        return level.getBlockState(ground).isSolidRender()
                && level.getBlockState(feet).isAir()
                && level.getBlockState(head).isAir()
                && level.getFluidState(ground).isEmpty()
                && level.getFluidState(feet).isEmpty()
                && level.getFluidState(head).isEmpty();
    }

    // ================= Traces « légende ratée » =================

    /** Choisit et pose une trace ratée sur le chemin du joueur (≤ 1/jour de jeu). */
    private static void tryTrace(ServerLevel level, ServerPlayer player) {
        long gameTime = level.getGameTime();
        Long last = LAST_TRACE_TICKS.get(player.getUUID());
        if (last != null && gameTime - last < TRACE_COOLDOWN_TICKS) {
            return;
        }
        RandomSource random = player.getRandom();
        BlockPos feet = findTraceSpot(level, player, random);
        if (feet == null) {
            return;
        }
        // Les traces permanentes ne retirent plus aucun bloc existant : les anciens
        // tunnels et arbres effeuillés pouvaient toucher des constructions de joueurs.
        boolean built = switch (random.nextInt(4)) {
            case 0, 3 -> buildFailedTotem(level, feet, random);
            default -> buildFailedSign(level, feet, player, random);
        };
        if (built) {
            LAST_TRACE_TICKS.put(player.getUUID(), gameTime);
            if (PenumbraConfig.get().debugLogging) {
                PenumbraMod.LOGGER.info("[Penumbra] Trace du Contrefait posée près de {} ({})",
                        player.getGameProfile().name(), feet.toShortString());
            }
        }
    }

    /** Sol solide + 4 blocs d'air, 20–35 blocs devant le joueur, ou null. */
    private static BlockPos findTraceSpot(ServerLevel level, ServerPlayer player, RandomSource random) {
        Vec3 motion = player.getDeltaMovement();
        Vec3 dir = new Vec3(motion.x, 0.0, motion.z);
        if (dir.lengthSqr() < 1.0E-4) {
            Vec3 look = player.getLookAngle();
            dir = new Vec3(look.x, 0.0, look.z);
        }
        if (dir.lengthSqr() < 1.0E-4) {
            return null;
        }
        dir = dir.normalize();
        double dist = TRACE_MIN_DIST + random.nextDouble() * (TRACE_MAX_DIST - TRACE_MIN_DIST);
        double side = (random.nextDouble() - 0.5) * 12.0;
        int baseX = Mth.floor(player.getX() + dir.x * dist - dir.z * side);
        int baseZ = Mth.floor(player.getZ() + dir.z * dist + dir.x * side);
        int baseY = Mth.floor(player.getY());
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = 12; dy >= -12; dy--) {
            cursor.set(baseX, baseY + dy, baseZ);
            BlockPos feet = cursor.immutable();
            if (level.isLoaded(feet)
                    && level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)
                    && level.getBlockState(feet).isAir()
                    && level.getBlockState(feet.above()).isAir()
                    && level.getBlockState(feet.above(2)).isAir()
                    && level.getBlockState(feet.above(3)).isAir()) {
                return feet;
            }
        }
        return null;
    }

    /**
     * Totem raté : trois variantes, toutes fausses d'une seule façon.
     * Public : appelé aussi par {@link ContrefaitEntity} à sa disparition.
     */
    public static boolean buildFailedTotem(ServerLevel level, BlockPos feet, RandomSource random) {
        switch (random.nextInt(3)) {
            case 0 -> { // sable + sable + netherrack : le feu est OUBLIÉ.
                level.setBlock(feet, Blocks.SAND.defaultBlockState(), Block.UPDATE_ALL);
                level.setBlock(feet.above(), Blocks.SAND.defaultBlockState(), Block.UPDATE_ALL);
                level.setBlock(feet.above(2), Blocks.NETHERRACK.defaultBlockState(), Block.UPDATE_ALL);
            }
            case 1 -> { // terre + terre tordue + netherrack + lumière froide sans propagation.
                Direction lean = Direction.Plane.HORIZONTAL.getRandomDirection(random);
                BlockPos bent = feet.above().relative(lean);
                if (!level.getBlockState(bent).isAir() || !level.getBlockState(bent.above()).isAir()
                        || !level.getBlockState(bent.above(2)).isAir()) {
                    return buildSafeTotemFallback(level, feet);
                }
                level.setBlock(feet, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
                level.setBlock(bent, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
                level.setBlock(bent.above(), Blocks.NETHERRACK.defaultBlockState(), Block.UPDATE_ALL);
                level.setBlock(bent.above(2), Blocks.SOUL_TORCH.defaultBlockState(), Block.UPDATE_ALL);
            }
            default -> { // sable + sable + pierre taillée + TORCHE : mauvais sommet.
                level.setBlock(feet, Blocks.SAND.defaultBlockState(), Block.UPDATE_ALL);
                level.setBlock(feet.above(), Blocks.SAND.defaultBlockState(), Block.UPDATE_ALL);
                level.setBlock(feet.above(2), Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL);
                level.setBlock(feet.above(3), Blocks.TORCH.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        return true;
    }

    /** Variante sans récursion et sans feu propagateur. */
    private static boolean buildSafeTotemFallback(ServerLevel level, BlockPos feet) {
        if (!level.getBlockState(feet).isAir()
                || !level.getBlockState(feet.above()).isAir()
                || !level.getBlockState(feet.above(2)).isAir()
                || !level.getBlockState(feet.above(3)).isAir()) {
            return false;
        }
        level.setBlock(feet, Blocks.SAND.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(feet.above(), Blocks.SAND.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(feet.above(2), Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(feet.above(3), Blocks.SOUL_TORCH.defaultBlockState(), Block.UPDATE_ALL);
        return true;
    }

    /** Panneau fautif : rotation « presque » vers le joueur, message avec une faute. */
    private static boolean buildFailedSign(ServerLevel level, BlockPos feet, ServerPlayer player,
                                           RandomSource random) {
        float toPlayer = (float) (Math.atan2(player.getZ() - (feet.getZ() + 0.5),
                player.getX() - (feet.getX() + 0.5)) * Mth.RAD_TO_DEG) - 90.0f;
        int rotation = Mth.floor(((toPlayer + 180.0f) * 16.0f / 360.0f) + 0.5f) & 15;
        rotation = (rotation + (random.nextBoolean() ? 1 : -1)) & 15;
        BlockState sign = Blocks.OAK_SIGN.defaultBlockState()
                .setValue(StandingSignBlock.ROTATION, rotation);
        if (!level.setBlock(feet, sign, Block.UPDATE_ALL)) {
            return false;
        }
        if (level.getBlockEntity(feet) instanceof SignBlockEntity signEntity) {
            // API 26.1.2 (pattern vérifié de CopyBuilder.placeSign).
            String key = "penumbra.sign.originel_" + (1 + random.nextInt(4));
            signEntity.setText(signEntity.getFrontText()
                    .setMessage(1, Component.translatable(key)), true);
            signEntity.setChanged();
        }
        return true;
    }

}

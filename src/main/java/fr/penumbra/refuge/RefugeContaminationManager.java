package fr.penumbra.refuge;

import com.mojang.math.Transformation;
import fr.penumbra.content.NarrativeItems;
import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.network.PenumbraNetworking;
import fr.penumbra.registry.PenumbraParticles;
import fr.penumbra.registry.PenumbraSounds;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Gestionnaire serveur isolé de la contamination d'un refuge.
 *
 * <p>Contrat de sécurité :</p>
 * <ul>
 *   <li>aucun inventaire n'est lu, trié, vidé ou remplacé ;</li>
 *   <li>aucun bloc du monde n'est posé, cassé ou modifié ;</li>
 *   <li>les faux objets/blocs sont des {@link Display} sans collision ni drop ;</li>
 *   <li>toute recherche abandonne si le petit carré de chunks n'est pas déjà chargé ;</li>
 *   <li>le nettoyage persistant exige UUID, type Display et jeton exacts.</li>
 * </ul>
 *
 * <p>L'intégrateur appelle {@link #tryContaminate(ServerPlayer, BlockPos,
 * RefugeContaminationModel.Audience)} au retour dans un refuge appris, puis
 * {@link #onServerTick(MinecraftServer)} depuis la fin du tick serveur. La
 * configuration et le pacing restent volontairement à la charge du directeur.</p>
 */
public final class RefugeContaminationManager {

    private static final int SCAN_RADIUS = 5;
    private static final double OWNER_TRIGGER_RADIUS = 24.0;
    private static final int CLEANUP_INTERVAL_TICKS = 20;
    private static final int MAX_ACTIVE_VISUALS_PER_OWNER = 24;

    public enum TriggerStatus {
        SUCCESS,
        INVALID_CONTEXT,
        OWNER_TOO_FAR,
        CHUNKS_UNLOADED,
        COOLDOWN,
        BUDGET_EXHAUSTED
    }

    /** Résultat explicite, utile aux commandes de debug et tests d'intégration. */
    public record TriggerResult(TriggerStatus status,
                                RefugeContaminationModel.Stage previousStage,
                                RefugeContaminationModel.Stage newStage,
                                RefugeContaminationModel.Effect effect,
                                RefugeContaminationModel.Audience audience,
                                int spawnedVisuals) {

        public boolean succeeded() {
            return this.status == TriggerStatus.SUCCESS;
        }
    }

    private RefugeContaminationManager() {
    }

    /**
     * Tente une étape de contamination pour le propriétaire et l'ancre
     * donnés. Cette méthode doit être appelée sur le thread serveur.
     */
    public static TriggerResult tryContaminate(ServerPlayer owner, BlockPos refugeAnchor,
                                               RefugeContaminationModel.Audience audience) {
        RefugeContaminationModel.Audience safeAudience = audience == null
                ? RefugeContaminationModel.Audience.OWNER_ONLY : audience;
        PenumbraConfig config = PenumbraConfig.get();
        if (owner == null || refugeAnchor == null || !owner.isAlive()
                || owner.isSpectator() || !(owner.level() instanceof ServerLevel level)
                || !level.isInWorldBounds(refugeAnchor)
                || !config.refugeContaminationEnabled
                || config.refugeContaminationMaxLevel <= 0) {
            return rejected(TriggerStatus.INVALID_CONTEXT,
                    RefugeContaminationModel.State.initial(), safeAudience);
        }

        RefugeContaminationState data = RefugeContaminationState.get(level.getServer());
        RefugeContaminationModel.State current = data
                .find(owner.getUUID(), level.dimension(), refugeAnchor)
                .orElse(RefugeContaminationModel.State.initial());
        double radiusSqr = OWNER_TRIGGER_RADIUS * OWNER_TRIGGER_RADIUS;
        if (owner.blockPosition().distSqr(refugeAnchor) > radiusSqr) {
            return rejected(TriggerStatus.OWNER_TOO_FAR, current, safeAudience);
        }
        if (!loadedSquare(level, refugeAnchor, SCAN_RADIUS)) {
            return rejected(TriggerStatus.CHUNKS_UNLOADED, current, safeAudience);
        }

        boolean visualBudget = safeAudience == RefugeContaminationModel.Audience.OWNER_ONLY
                || data.activeVisualCount(owner.getUUID()) < MAX_ACTIVE_VISUALS_PER_OWNER
                && data.visualCount() < RefugeContaminationState.MAX_TRACKED_VISUALS;
        long now = level.getServer().overworld().getGameTime();
        if (!visualBudget) {
            return rejected(TriggerStatus.BUDGET_EXHAUSTED, current, safeAudience);
        }
        if (!RefugeContaminationModel.canTrigger(current, now, true, true, true)) {
            return rejected(TriggerStatus.COOLDOWN, current, safeAudience);
        }

        // Réserve d'abord l'entrée persistante : aucun visuel orphelin n'est
        // créé si le registre de refuges est saturé.
        if (!data.put(owner.getUUID(), level.dimension(), refugeAnchor, current)) {
            return rejected(TriggerStatus.BUDGET_EXHAUSTED, current, safeAudience);
        }

        RefugeContaminationModel.Effect effect =
                RefugeContaminationModel.chooseEffect(current);
        int visuals = performEffect(level, owner, refugeAnchor, effect,
                safeAudience, data, now);
        RefugeContaminationModel.State next =
                RefugeContaminationModel.afterSuccessfulEffect(current, now,
                        config.refugeContaminationMaxLevel,
                        (long) config.refugeContaminationIntervalSeconds * 20L);
        data.put(owner.getUUID(), level.dimension(), refugeAnchor, next);
        return new TriggerResult(TriggerStatus.SUCCESS, current.stage(), next.stage(),
                effect, safeAudience, visuals);
    }

    /** Nettoyage amorti (une fois par seconde), sans aucun chargement de chunk. */
    public static int onServerTick(MinecraftServer server) {
        if (server == null || server.getTickCount() % CLEANUP_INTERVAL_TICKS != 0) {
            return 0;
        }
        return cleanup(server, null, false);
    }

    /**
     * Retire immédiatement les visuels chargés d'un propriétaire. Ceux des
     * chunks déchargés restent suivis et seront retirés à leur retour.
     */
    public static int restoreLoadedForOwner(MinecraftServer server, UUID owner) {
        return server == null || owner == null ? 0 : cleanup(server, owner, true);
    }

    private static TriggerResult rejected(TriggerStatus status,
                                          RefugeContaminationModel.State state,
                                          RefugeContaminationModel.Audience audience) {
        return new TriggerResult(status, state.stage(), state.stage(), null, audience, 0);
    }

    private static int performEffect(ServerLevel level, ServerPlayer owner,
                                     BlockPos anchor,
                                     RefugeContaminationModel.Effect effect,
                                     RefugeContaminationModel.Audience audience,
                                     RefugeContaminationState data, long now) {
        Vec3 soundPos = Vec3.atCenterOf(anchor);
        playSound(owner, audience, soundFor(effect), soundPos,
                audience == RefugeContaminationModel.Audience.LOCAL_SHARED ? 1.5f : 0.65f,
                0.70f + effect.ordinal() * 0.035f);
        sendAsh(level, owner, audience, soundPos, effect ==
                RefugeContaminationModel.Effect.IMPOSSIBLE_ROOM ? 28 : 10);

        // Une audience privée ne doit jamais recevoir une entité serveur,
        // visible par d'autres clients : paquets/particules ciblés uniquement.
        if (audience == RefugeContaminationModel.Audience.OWNER_ONLY) {
            return 0;
        }

        int salt = Math.max(0, data.find(owner.getUUID(), level.dimension(), anchor)
                .orElse(RefugeContaminationModel.State.initial()).successfulEvents());
        return switch (effect) {
            case WHISPER -> 0;
            case ASH_TRACES -> spawnAshTraces(level, owner, anchor, data, now, salt);
            case MOVED_OBJECT_ECHO -> spawnItemEcho(level, owner, anchor,
                    new ItemStack(Items.CLOCK), 1.0f, effect, 700L, data, now, salt);
            case RED_TORCH_ECHO -> spawnRedTorchEcho(level, owner, anchor,
                    data, now, salt);
            case ABANDONED_PHOTO -> spawnItemEcho(level, owner, anchor,
                    new ItemStack(NarrativeItems.IMPOSSIBLE_PHOTO), 1.15f,
                    effect, 1_200L, data, now, salt);
            case FALSE_CHEST -> spawnItemEcho(level, owner, anchor,
                    new ItemStack(Items.CHEST), 2.0f, effect, 700L, data, now, salt);
            case IMPOSSIBLE_ROOM -> spawnImpossibleRoom(level, owner, anchor,
                    data, now, salt);
        };
    }

    private static int spawnAshTraces(ServerLevel level, ServerPlayer owner,
                                      BlockPos anchor, RefugeContaminationState data,
                                      long now, int salt) {
        List<BlockPos> spots = floorSpots(level, owner, anchor);
        if (spots.isEmpty()) {
            return 0;
        }
        int count = Math.min(4, spots.size());
        int spawned = 0;
        for (int index = 0; index < count; index++) {
            BlockPos spot = spots.get(Math.floorMod(salt + index * 3, spots.size()));
            spawned += spawnBlockDisplay(level, owner, spot, Blocks.BLACK_CARPET.defaultBlockState(),
                    RefugeContaminationModel.Effect.ASH_TRACES, 900L, data, now) ? 1 : 0;
        }
        return spawned;
    }

    private static int spawnRedTorchEcho(ServerLevel level, ServerPlayer owner,
                                         BlockPos anchor, RefugeContaminationState data,
                                         long now, int salt) {
        BlockPos spot = chooseFloorSpot(level, owner, anchor, salt);
        if (spot == null) {
            return 0;
        }
        return spawnBlockDisplay(level, owner, spot,
                Blocks.REDSTONE_TORCH.defaultBlockState(),
                RefugeContaminationModel.Effect.RED_TORCH_ECHO,
                900L, data, now) ? 1 : 0;
    }

    private static int spawnItemEcho(ServerLevel level, ServerPlayer owner,
                                     BlockPos anchor, ItemStack stack, float scale,
                                     RefugeContaminationModel.Effect effect,
                                     long lifetime, RefugeContaminationState data,
                                     long now, int salt) {
        BlockPos spot = chooseFloorSpot(level, owner, anchor, salt);
        if (spot == null) {
            return 0;
        }
        Display.ItemDisplay display = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);
        display.setItemStack(stack.copyWithCount(1));
        display.setItemTransform(ItemDisplayContext.FIXED);
        display.setTransformation(new Transformation(
                new Vector3f(0.0f, 0.0f, 0.0f), new Quaternionf(),
                new Vector3f(scale, scale, scale), new Quaternionf()));
        display.snapTo(spot.getX() + 0.5, spot.getY() + 0.32,
                spot.getZ() + 0.5, owner.getYRot() + 180.0f, 0.0f);
        configureDisplay(display);
        return addTrackedDisplay(level, owner, display, effect, lifetime, data, now) ? 1 : 0;
    }

    /** Mur noir 3×3 purement visuel : le joueur peut le traverser, puis il disparaît. */
    private static int spawnImpossibleRoom(ServerLevel level, ServerPlayer owner,
                                           BlockPos anchor, RefugeContaminationState data,
                                           long now, int salt) {
        List<BlockPos> floors = floorSpots(level, owner, anchor);
        if (floors.isEmpty()) {
            return 0;
        }
        Direction forward = owner.getDirection();
        Direction side = forward.getClockWise();
        BlockPos center = null;
        for (int attempt = 0; attempt < floors.size(); attempt++) {
            BlockPos candidate = floors.get(Math.floorMod(salt + attempt, floors.size()));
            if (roomPlaneClear(level, candidate, side)) {
                center = candidate;
                break;
            }
        }
        if (center == null) {
            return 0;
        }
        int spawned = 0;
        for (int horizontal = -1; horizontal <= 1; horizontal++) {
            for (int vertical = 0; vertical <= 2; vertical++) {
                BlockPos pos = center.relative(side, horizontal).above(vertical);
                spawned += spawnBlockDisplay(level, owner, pos,
                        Blocks.BLACK_CONCRETE.defaultBlockState(),
                        RefugeContaminationModel.Effect.IMPOSSIBLE_ROOM,
                        300L, data, now) ? 1 : 0;
            }
        }
        return spawned;
    }

    private static boolean roomPlaneClear(ServerLevel level, BlockPos center, Direction side) {
        for (int horizontal = -1; horizontal <= 1; horizontal++) {
            for (int vertical = 0; vertical <= 2; vertical++) {
                BlockPos pos = center.relative(side, horizontal).above(vertical);
                if (!level.isLoaded(pos) || !level.getBlockState(pos).isAir()
                        || !level.getFluidState(pos).isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean spawnBlockDisplay(ServerLevel level, ServerPlayer owner,
                                             BlockPos pos, BlockState shownState,
                                             RefugeContaminationModel.Effect effect,
                                             long lifetime,
                                             RefugeContaminationState data, long now) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        Display.BlockDisplay display = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, level);
        display.setBlockState(shownState);
        display.snapTo(pos.getX(), pos.getY() + 0.01, pos.getZ(),
                owner.getYRot(), 0.0f);
        configureDisplay(display);
        return addTrackedDisplay(level, owner, display, effect, lifetime, data, now);
    }

    private static void configureDisplay(Display display) {
        // 0.4 × la portée vanilla des displays ≈ audience locale, jamais le niveau entier.
        display.setViewRange(0.4f);
        display.setShadowRadius(0.15f);
        display.setShadowStrength(0.3f);
        display.setTransformationInterpolationDuration(10);
    }

    private static boolean addTrackedDisplay(ServerLevel level, ServerPlayer owner,
                                             Display display,
                                             RefugeContaminationModel.Effect effect,
                                             long lifetime,
                                             RefugeContaminationState data, long now) {
        if (data.visualCount() >= RefugeContaminationState.MAX_TRACKED_VISUALS
                || data.activeVisualCount(owner.getUUID()) >= MAX_ACTIVE_VISUALS_PER_OWNER) {
            return false;
        }
        String token = RefugeContaminationState.tokenFor(display.getUUID());
        display.addTag(token);
        if (!level.addFreshEntity(display)) {
            return false;
        }
        RefugeContaminationState.VisualEntry entry =
                new RefugeContaminationState.VisualEntry(
                        display.getUUID(), owner.getUUID(), level.dimension(),
                        display.blockPosition(), token, effect,
                        RefugeContaminationModel.Audience.LOCAL_SHARED,
                        saturatingAdd(now, lifetime));
        if (data.addVisual(entry)) {
            return true;
        }
        display.discard();
        return false;
    }

    /** Liste bornée de sols libres ; toutes les lectures restent dans des chunks chargés. */
    private static List<BlockPos> floorSpots(ServerLevel level, ServerPlayer owner,
                                             BlockPos anchor) {
        List<BlockPos> result = new ArrayList<>();
        for (int radius = 0; radius <= SCAN_RADIUS && result.size() < 32; radius++) {
            for (int dx = -radius; dx <= radius && result.size() < 32; dx++) {
                for (int dz = -radius; dz <= radius && result.size() < 32; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    for (int dy = -2; dy <= 2; dy++) {
                        BlockPos pos = anchor.offset(dx, dy, dz);
                        if (validFloorSpot(level, owner, pos)) {
                            result.add(pos.immutable());
                            break;
                        }
                    }
                }
            }
        }
        return result;
    }

    private static BlockPos chooseFloorSpot(ServerLevel level, ServerPlayer owner,
                                            BlockPos anchor, int salt) {
        List<BlockPos> spots = floorSpots(level, owner, anchor);
        return spots.isEmpty() ? null : spots.get(Math.floorMod(salt, spots.size()));
    }

    private static boolean validFloorSpot(ServerLevel level, ServerPlayer owner, BlockPos pos) {
        if (!level.isInWorldBounds(pos) || !level.isLoaded(pos)
                || !level.isLoaded(pos.below()) || !level.isLoaded(pos.above())
                || owner.distanceToSqr(Vec3.atCenterOf(pos)) < 2.25 * 2.25) {
            return false;
        }
        BlockPos below = pos.below();
        return level.getBlockState(pos).isAir()
                && level.getBlockState(pos.above()).isAir()
                && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                && level.getFluidState(pos).isEmpty()
                && level.getFluidState(pos.above()).isEmpty();
    }

    private static boolean loadedSquare(ServerLevel level, BlockPos center, int radius) {
        return level.hasChunksAt(center.getX() - radius, center.getZ() - radius,
                center.getX() + radius, center.getZ() + radius);
    }

    private static void sendAsh(ServerLevel level, ServerPlayer owner,
                                RefugeContaminationModel.Audience audience,
                                Vec3 pos, int count) {
        if (audience == RefugeContaminationModel.Audience.OWNER_ONLY) {
            level.sendParticles(owner, PenumbraParticles.ASH_MOTE,
                    false, false, pos.x, pos.y, pos.z,
                    count, 0.8, 0.3, 0.8, 0.01);
        } else {
            level.sendParticles(PenumbraParticles.ASH_MOTE,
                    pos.x, pos.y, pos.z, count, 0.8, 0.3, 0.8, 0.01);
        }
    }

    private static void playSound(ServerPlayer owner,
                                  RefugeContaminationModel.Audience audience,
                                  SoundEvent sound, Vec3 pos,
                                  float volume, float pitch) {
        if (audience == RefugeContaminationModel.Audience.OWNER_ONLY) {
            ServerPlayNetworking.send(owner,
                    new PenumbraNetworking.HorrorSoundS2CPacket(
                            sound.location(), pos.x, pos.y, pos.z, volume, pitch));
        } else {
            PenumbraNetworking.sendSoundPrioritySpatial(owner, sound, pos, volume, pitch);
        }
    }

    private static SoundEvent soundFor(RefugeContaminationModel.Effect effect) {
        return switch (effect) {
            case WHISPER, ABANDONED_PHOTO -> PenumbraSounds.HOST_WHISPER_2;
            case ASH_TRACES, MOVED_OBJECT_ECHO -> PenumbraSounds.HOST_STEP;
            case RED_TORCH_ECHO -> PenumbraSounds.TORCH_SPUTTER;
            case FALSE_CHEST -> PenumbraSounds.DOOR_GROAN;
            case IMPOSSIBLE_ROOM -> PenumbraSounds.SILENCE_DRONE;
        };
    }

    private static int cleanup(MinecraftServer server, UUID ownerFilter, boolean force) {
        RefugeContaminationState data = RefugeContaminationState.get(server);
        long now = server.overworld().getGameTime();
        int resolved = 0;
        for (RefugeContaminationState.VisualEntry entry : data.visuals()) {
            if (ownerFilter != null && !ownerFilter.equals(entry.owner())) {
                continue;
            }
            if (!force && now < entry.expiresAt()) {
                continue;
            }
            ServerLevel level = server.getLevel(entry.dimension());
            boolean loaded = level != null && level.isLoaded(entry.position());
            Entity entity = loaded ? level.getEntity(entry.entityId()) : null;
            RefugeContaminationModel.RestorationAction action =
                    RefugeContaminationModel.restorationAction(
                            loaded, entity != null, entity instanceof Display,
                            entity != null && entity.entityTags().contains(entry.token()));
            switch (action) {
                case WAIT_FOR_LOADED_CHUNK -> {
                    continue;
                }
                case REMOVE_MATCHING_VISUAL -> entity.discard();
                case FORGET_MISSING_VISUAL, LEAVE_CHANGED_VISUAL -> {
                    // Compare-and-set : rien à modifier dans le monde.
                }
            }
            if (data.removeVisual(entry.entityId())) {
                resolved++;
            }
        }
        return resolved;
    }

    private static long saturatingAdd(long left, long right) {
        return right > 0L && left > Long.MAX_VALUE - right
                ? Long.MAX_VALUE : left + right;
    }
}

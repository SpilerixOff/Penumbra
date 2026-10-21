package fr.penumbra.story;

import fr.penumbra.badge.PenumbraBadges;
import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.data.PenumbraPersistentState;
import fr.penumbra.director.HorrorDirector;
import fr.penumbra.entity.absent.AbsentEntity;
import fr.penumbra.entity.absent.AbsentManager;
import fr.penumbra.network.PenumbraNetworking;
import fr.penumbra.registry.PenumbraParticles;
import fr.penumbra.registry.PenumbraSounds;
import fr.penumbra.registry.PenumbraItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.Difficulty;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * La Confrontation (1.0) — « Il n'y a plus de place pour deux. »
 *
 * <p>Au stade final, accomplir le rituel du Sanctuaire de cendre ne libère
 * plus : cela <b>convoque</b>. Votre propre copie — l'Absent portant votre
 * skin — se dresse de l'autre côté du cercle et reflète chacun de vos
 * déplacements par petits pas sûrs autour du centre. Observer sans bruit
 * ouvre une brèche de trois secondes ; un flash cadré l'entame, puis elle
 * reprend son souffle. Trois brèches gagnées la dissolvent. Une brèche
 * manquée ne retire pas les fragments déjà obtenus.</p>
 *
 * <p>Victoire : cendres, libération, badge {@code confrontation}, longue
 * accalmie réelle — et l'épilogue honnête du mod : il réapprendra.
 * Abandon (fuite à plus de 40 blocs, mort, déconnexion) : la copie s'efface
 * sans un mot et le rituel pourra être retenté. Le duel est un one-shot par
 * joueur et par monde une fois gagné.</p>
 *
 * <p>Le déplacement de la copie est assumé comme non naturel : elle glisse
 * par téléportations courtes — c'est un reflet, pas une créature.</p>
 */
public final class ConfrontationManager {

    /** Cadence de pilotage du reflet. */
    private static final int TICK_INTERVAL = 2;
    /** Flashs cadrés requis pour dissoudre la copie. */
    public static final int FLASHES_TO_WIN = ConfrontationSequence.FLASHES_TO_WIN;
    /** Distance d'abandon du duel. */
    private static final double ABORT_DISTANCE = 40.0;
    /** Le reflet s'arrête plutôt que de traverser le joueur. */
    private static final double MIN_MIRROR_DISTANCE = 5.0;
    /** Portée et cône du flash pendant le duel. */
    private static final double FLASH_RANGE = 24.0;
    private static final double FLASH_CONE_DOT = 0.5;
    /** Accalmie réelle offerte par la victoire (40 min). */
    private static final long VICTORY_CALM_TICKS = 48000L;

    private static final class Duel {
        final ServerLevel level;
        final BlockPos center;
        final UUID doubleId;
        final ConfrontationSequence sequence = new ConfrontationSequence();
        Vec3 lastPlayerPosition;
        long nextFeedbackTick;

        Duel(ServerLevel level, BlockPos center, UUID doubleId, Vec3 playerPosition) {
            this.level = level;
            this.center = center;
            this.doubleId = doubleId;
            this.lastPlayerPosition = playerPosition;
        }
    }

    private static final Map<UUID, Duel> DUELS = new HashMap<>();

    private ConfrontationManager() {
    }

    private static String oneShotKey(ServerPlayer player) {
        return "confrontation_done:" + player.getUUID();
    }

    /** Le duel a-t-il déjà été gagné dans ce monde ? */
    public static boolean isWon(ServerPlayer player) {
        return PenumbraPersistentState.get(player.level().getServer())
                .isOneShotUsed(oneShotKey(player));
    }

    /**
     * Tente d'ouvrir la Confrontation à la clôture du rituel. Retourne false
     * (rituel normal) hors stade final, si déjà gagnée, si un duel est en
     * cours ou si la copie ne peut pas apparaître.
     */
    public static boolean tryBegin(ServerLevel level, ServerPlayer player, BlockPos center) {
        if (player.level() != level || StoryManager.getStage(player) < StoryStage.CONFRONTATION.index()
                || isWon(player) || DUELS.containsKey(player.getUUID())) {
            return false;
        }
        AbsentEntity copy = AbsentManager.createConfrontationDouble(player,
                candidate -> ConfrontationPlacement.safeInitialPlacement(level, candidate));
        if (copy == null) {
            return false;
        }
        DUELS.put(player.getUUID(), new Duel(level, center.immutable(), copy.getUUID(), player.position()));
        PenumbraNetworking.sendSound(player, PenumbraSounds.SILENCE_DRONE,
                Vec3.atCenterOf(center), 0.8f, 0.7f);
        player.sendSystemMessage(Component.translatable("penumbra.confrontation.begins")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        return true;
    }

    public static boolean isActive(UUID playerId) {
        return DUELS.containsKey(playerId);
    }

    /** Boucle serveur (enregistrée dans {@code PenumbraMod}). */
    public static void onServerTick(MinecraftServer server) {
        if (DUELS.isEmpty() || server.getTickCount() % TICK_INTERVAL != 0) {
            return;
        }
        Iterator<Map.Entry<UUID, Duel>> iterator = DUELS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Duel> entry = iterator.next();
            Duel duel = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Entity entity = duel.level.getEntity(duel.doubleId);
            if (player == null || !player.isAlive() || player.level() != duel.level
                    || player.isSpectator() || player.isSleeping() || player.isRemoved()
                    || !PenumbraConfig.get().absentEnabled
                    || duel.level.getDifficulty() == Difficulty.PEACEFUL
                    || !(entity instanceof AbsentEntity copy) || !copy.isAlive()
                    || !copy.isOwnedBy(player) || !copy.isConfrontationDouble()
                    || player.distanceToSqr(Vec3.atCenterOf(duel.center))
                            > ABORT_DISTANCE * ABORT_DISTANCE) {
                abort(duel);
                iterator.remove();
                continue;
            }
            if (tickDuel(duel, player, copy)) {
                iterator.remove();
            }
        }
    }

    private static boolean tickDuel(Duel duel, ServerPlayer player, AbsentEntity copy) {
        ConfrontationSequence.Phase before = duel.sequence.phase();
        boolean quiet = player.position().distanceToSqr(duel.lastPlayerPosition) <= 0.0025
                && player.onGround() && !player.swinging && player.hurtTime == 0
                && !player.isInWater() && !player.isPassenger();
        duel.lastPlayerPosition = player.position();
        copy.getNavigation().stop();
        copy.setDeltaMovement(Vec3.ZERO);
        if (before == ConfrontationSequence.Phase.MIRROR) {
            Vec3 mirrored = mirrorPosition(Vec3.atCenterOf(duel.center),
                    player.position(), MIN_MIRROR_DISTANCE);
            ConfrontationPlacement.tryMove(duel.level, copy, mirrored,
                    player.position(), MIN_MIRROR_DISTANCE);
        }
        float yaw = yawTowards(copy.position(), player.position());
        copy.setYRot(yaw);
        copy.setYBodyRot(yaw);
        copy.setYHeadRot(yaw);
        boolean holdingCamera = player.getMainHandItem().is(PenumbraItems.BINOCULARS);
        duel.sequence.advance(TICK_INTERVAL, quiet && holdingCamera && isFramed(player, copy));
        ConfrontationSequence.Phase after = duel.sequence.phase();
        if (after == ConfrontationSequence.Phase.EXPIRED) {
            abort(duel);
            player.sendOverlayMessage(Component.translatable("penumbra.confrontation.retry"));
            return true;
        }
        if (after != before) {
            copy.setConfrontationExposure(after == ConfrontationSequence.Phase.EXPOSED,
                    duel.sequence.flashes());
            String key = switch (after) {
                case MIRROR -> "observe";
                case EXPOSED -> "ready";
                case RECOVERY -> "window_closed";
                default -> null;
            };
            if (key != null) player.sendOverlayMessage(Component.translatable(
                    "penumbra.confrontation." + key));
            if (after == ConfrontationSequence.Phase.EXPOSED) {
                PenumbraNetworking.sendSound(player, PenumbraSounds.HOST_WHISPER,
                        copy.position(), 0.5f, 0.8f - 0.1f * duel.sequence.flashes());
            }
        }
        if (duel.level.getGameTime() % 10 == 0) {
            if (after == ConfrontationSequence.Phase.MIRROR) {
                player.sendOverlayMessage(Component.translatable(
                        "penumbra.confrontation.focus", duel.sequence.focusTicks() / 2,
                        duel.sequence.requiredFocusTicks() / 2));
            } else if (after == ConfrontationSequence.Phase.EXPOSED) {
                player.sendOverlayMessage(Component.translatable("penumbra.confrontation.ready"));
                duel.level.sendParticles(PenumbraParticles.ASH_MOTE,
                        copy.getX(), copy.getY() + 1.2, copy.getZ(), 3, 0.3, 0.5, 0.3, 0.004);
            } else if (after == ConfrontationSequence.Phase.ARRIVAL) {
                Vec3 center = Vec3.atCenterOf(duel.center);
                duel.level.sendParticles(PenumbraParticles.ASH_MOTE,
                        center.x, center.y + 1, center.z, 4, 1.6, 0.4, 1.6, 0.004);
            }
        }
        return false;
    }

    private static boolean isFramed(ServerPlayer player, AbsentEntity copy) {
        Vec3 toCopy = copy.getEyePosition().subtract(player.getEyePosition());
        return toCopy.lengthSqr() > 1.0E-6 && toCopy.lengthSqr() <= FLASH_RANGE * FLASH_RANGE
                && player.getViewVector(1.0f).dot(toCopy.normalize()) >= FLASH_CONE_DOT
                && player.hasLineOfSight(copy);
    }

    /**
     * Réflexion pure du joueur autour du centre (x/z), avec écartement
     * minimal : si le reflet arrive trop près du joueur (croisement au
     * centre), il est repoussé le long de l'axe joueur→reflet.
     */
    static Vec3 mirrorPosition(Vec3 center, Vec3 playerPos, double minDistance) {
        Vec3 mirrored = new Vec3(2.0 * center.x - playerPos.x, center.y,
                2.0 * center.z - playerPos.z);
        Vec3 axis = mirrored.subtract(playerPos);
        Vec3 flat = new Vec3(axis.x, 0.0, axis.z);
        double distance = flat.length();
        if (distance >= minDistance) {
            return mirrored;
        }
        Vec3 direction = distance < 1.0E-4
                ? new Vec3(1.0, 0.0, 0.0) : flat.scale(1.0 / distance);
        Vec3 pushed = playerPos.add(direction.scale(minDistance + 1.0));
        return new Vec3(pushed.x, center.y, pushed.z);
    }

    static float yawTowards(Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from);
        return (float) (Math.atan2(delta.z, delta.x) * (180.0 / Math.PI)) - 90.0f;
    }

    /** Le double du duel de n'importe quel joueur ? (exemption du flash Absent normal) */
    public static boolean isDuelDouble(UUID entityId) {
        for (Duel duel : DUELS.values()) {
            if (duel.doubleId.equals(entityId)) {
                return true;
            }
        }
        return false;
    }

    /** Flash de la Caméra VHS pendant un duel : la seule arme qui compte. */
    public static void onCameraFlash(ServerPlayer player) {
        Duel duel = DUELS.get(player.getUUID());
        if (duel == null || player.level() != duel.level
                || !player.isAlive() || player.isSpectator() || player.isSleeping()) {
            return;
        }
        if (!(duel.level.getEntity(duel.doubleId) instanceof AbsentEntity copy)
                || !copy.isAlive() || !copy.isOwnedBy(player) || !copy.isConfrontationDouble()) {
            return;
        }
        boolean framed = isFramed(player, copy);
        boolean exposed = duel.sequence.phase() == ConfrontationSequence.Phase.EXPOSED;
        if (!duel.sequence.flash(framed)) {
            long now = duel.level.getGameTime();
            if (now >= duel.nextFeedbackTick) {
                player.sendOverlayMessage(Component.translatable(exposed
                        ? "penumbra.confrontation.bad_frame" : "penumbra.confrontation.wait"));
                duel.nextFeedbackTick = now + 40;
            }
            return;
        }
        copy.setConfrontationExposure(false, duel.sequence.flashes());
        Vec3 pos = copy.position();
        duel.level.sendParticles(PenumbraParticles.ASH_MOTE,
                pos.x, pos.y + 1.2, pos.z, 10, 0.3, 0.5, 0.3, 0.01);
        // Son murmure descend d'un ton à chaque flash : elle perd sa voix.
        PenumbraNetworking.sendSound(player, PenumbraSounds.HOST_WHISPER,
                pos, 0.6f, 0.92f - 0.14f * duel.sequence.flashes());
        if (duel.sequence.phase() == ConfrontationSequence.Phase.COMPLETE) {
            win(duel, player, copy);
            DUELS.remove(player.getUUID());
        } else {
            player.sendOverlayMessage(Component.translatable("penumbra.confrontation.fragment",
                    duel.sequence.flashes(), FLASHES_TO_WIN));
        }
    }

    private static void win(Duel duel, ServerPlayer player, AbsentEntity copy) {
        Vec3 pos = copy.position();
        duel.level.sendParticles(PenumbraParticles.ASH_MOTE,
                pos.x, pos.y + 1.0, pos.z, 32, 0.5, 0.9, 0.5, 0.02);
        copy.discard();
        PenumbraNetworking.sendSoundPriority(player, PenumbraSounds.RITUAL_RELEASE,
                Vec3.atCenterOf(duel.center), 1.0f, 1.0f);
        PenumbraPersistentState.get(duel.level.getServer())
                .markOneShot(oneShotKey(player));
        StoryManager.noteDiscovery(player, "confrontation");
        PenumbraBadges.grant(player, "confrontation");
        HorrorDirector.applyPostRitualCalm(player, VICTORY_CALM_TICKS);
        player.sendSystemMessage(Component.translatable("penumbra.confrontation.won")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        player.sendSystemMessage(Component.translatable("penumbra.confrontation.epilogue")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    /** La copie s'efface sans un mot ; le rituel pourra être retenté. */
    private static void abort(Duel duel) {
        if (duel.level.getEntity(duel.doubleId) instanceof AbsentEntity copy
                && copy.isAlive()) {
            Vec3 pos = copy.position();
            duel.level.sendParticles(PenumbraParticles.ASH_MOTE,
                    pos.x, pos.y + 1.0, pos.z, 8, 0.3, 0.6, 0.3, 0.01);
            copy.discard();
        }
    }

    public static void onPlayerDisconnect(UUID playerId) {
        Duel duel = DUELS.remove(playerId);
        if (duel != null) {
            abort(duel);
        }
    }

    public static void onServerStopped(MinecraftServer server) {
        for (Duel duel : DUELS.values()) {
            abort(duel);
        }
        DUELS.clear();
    }
}

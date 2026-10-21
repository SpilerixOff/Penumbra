package fr.penumbra.story;

import fr.penumbra.badge.PenumbraBadges;
import fr.penumbra.data.PenumbraPersistentState;
import fr.penumbra.director.HorrorDirector;
import fr.penumbra.entity.absent.AbsentEntity;
import fr.penumbra.entity.absent.AbsentManager;
import fr.penumbra.network.PenumbraNetworking;
import fr.penumbra.registry.PenumbraParticles;
import fr.penumbra.registry.PenumbraSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
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
 * déplacements par symétrie autour du centre. Elle ne peut pas être frappée :
 * s'approcher échange les côtés. La seule arme est celle de tout le mod —
 * <b>la Caméra VHS</b> : trois flashs correctement cadrés la dissolvent.
 * Chaque flash réussi rapproche son murmure.</p>
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

    /** Ticks de silence avant que la copie ne commence à refléter. */
    private static final int SILENCE_TICKS = 60;
    /** Durée maximale du duel (2 min) avant qu'elle ne se lasse. */
    private static final int DUEL_TIMEOUT_TICKS = 2400;
    /** Cadence de pilotage du reflet. */
    private static final int TICK_INTERVAL = 2;
    /** Flashs cadrés requis pour dissoudre la copie. */
    public static final int FLASHES_TO_WIN = 3;
    /** Distance d'abandon du duel. */
    private static final double ABORT_DISTANCE = 40.0;
    /** Distance minimale reflet-joueur (elle échange les côtés en dessous). */
    private static final double MIN_MIRROR_DISTANCE = 5.0;
    /** Portée et cône du flash pendant le duel. */
    private static final double FLASH_RANGE = 24.0;
    private static final double FLASH_CONE_DOT = 0.5;
    /** Accalmie réelle offerte par la victoire (40 min). */
    private static final long VICTORY_CALM_TICKS = 48000L;

    private enum Phase { SILENCE, MIRROR }

    private static final class Duel {
        final ServerLevel level;
        final BlockPos center;
        final UUID doubleId;
        Phase phase = Phase.SILENCE;
        int phaseTicks;
        int flashes;

        Duel(ServerLevel level, BlockPos center, UUID doubleId) {
            this.level = level;
            this.center = center;
            this.doubleId = doubleId;
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
        if (StoryManager.getStage(player) < StoryStage.CONFRONTATION.index()
                || isWon(player) || DUELS.containsKey(player.getUUID())) {
            return false;
        }
        if (!AbsentManager.forceSoloSighting(player)) {
            return false;
        }
        AbsentEntity copy = nearestOwnCopy(level, player);
        if (copy == null) {
            return false;
        }
        DUELS.put(player.getUUID(), new Duel(level, center, copy.getUUID()));
        PenumbraNetworking.sendSound(player, PenumbraSounds.SILENCE_DRONE,
                Vec3.atCenterOf(center), 0.8f, 0.7f);
        player.sendSystemMessage(Component.translatable("penumbra.confrontation.begins")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        return true;
    }

    private static AbsentEntity nearestOwnCopy(ServerLevel level, ServerPlayer player) {
        AbsentEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (AbsentEntity absent : level.getEntitiesOfClass(AbsentEntity.class,
                player.getBoundingBox().inflate(64.0),
                entity -> entity.isAlive() && !entity.isFalseCompanion())) {
            double distSq = absent.distanceToSqr(player);
            if (distSq < best) {
                best = distSq;
                nearest = absent;
            }
        }
        return nearest;
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
                    || !(entity instanceof AbsentEntity copy) || !copy.isAlive()
                    || player.distanceToSqr(Vec3.atCenterOf(duel.center))
                            > ABORT_DISTANCE * ABORT_DISTANCE) {
                abort(duel);
                iterator.remove();
                continue;
            }
            duel.phaseTicks += TICK_INTERVAL;
            if (duel.phase == Phase.SILENCE) {
                tickSilence(duel, player);
            } else if (tickMirror(duel, player, copy)) {
                iterator.remove();
            }
        }
    }

    private static void tickSilence(Duel duel, ServerPlayer player) {
        Vec3 center = Vec3.atCenterOf(duel.center);
        duel.level.sendParticles(PenumbraParticles.ASH_MOTE,
                center.x, center.y + 1.0, center.z, 4, 1.6, 0.4, 1.6, 0.004);
        if (duel.phaseTicks >= SILENCE_TICKS) {
            duel.phase = Phase.MIRROR;
            duel.phaseTicks = 0;
        }
    }

    /** @return true si le duel est terminé (temps écoulé). */
    private static boolean tickMirror(Duel duel, ServerPlayer player, AbsentEntity copy) {
        Vec3 center = Vec3.atCenterOf(duel.center);
        Vec3 mirrored = mirrorPosition(center, player.position(), MIN_MIRROR_DISTANCE);
        copy.getNavigation().stop();
        float yaw = yawTowards(mirrored, player.position());
        copy.snapTo(mirrored.x, duel.center.getY(), mirrored.z, yaw, 0.0f);
        if (duel.phaseTicks >= DUEL_TIMEOUT_TICKS) {
            abort(duel);
            return true;
        }
        return false;
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
        if (duel == null || duel.phase != Phase.MIRROR
                || player.level() != duel.level) {
            return;
        }
        if (!(duel.level.getEntity(duel.doubleId) instanceof AbsentEntity copy)
                || !copy.isAlive()) {
            return;
        }
        Vec3 toCopy = copy.getEyePosition().subtract(player.getEyePosition());
        if (toCopy.lengthSqr() > FLASH_RANGE * FLASH_RANGE
                || player.getViewVector(1.0f).dot(toCopy.normalize()) < FLASH_CONE_DOT
                || !player.hasLineOfSight(copy)) {
            return;
        }
        duel.flashes++;
        Vec3 pos = copy.position();
        duel.level.sendParticles(PenumbraParticles.ASH_MOTE,
                pos.x, pos.y + 1.2, pos.z, 10, 0.3, 0.5, 0.3, 0.01);
        // Son murmure descend d'un ton à chaque flash : elle perd sa voix.
        PenumbraNetworking.sendSound(player, PenumbraSounds.HOST_WHISPER,
                pos, 0.6f, 0.92f - 0.14f * duel.flashes);
        if (duel.flashes >= FLASHES_TO_WIN) {
            win(duel, player, copy);
            DUELS.remove(player.getUUID());
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

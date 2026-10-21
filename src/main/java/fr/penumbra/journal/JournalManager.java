package fr.penumbra.journal;

import fr.penumbra.PenumbraMod;
import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.network.PenumbraNetworking;
import fr.penumbra.registry.PenumbraSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;

/**
 * Alimente le Relevé à partir de ce que le joueur voit réellement (0.18.0).
 *
 * <p>Le carnet ne peut pas raconter une créature que le joueur n'a jamais
 * croisée : ce serait un spoiler déguisé en objet. Une page ne se remplit donc
 * que lorsque les trois conditions sont réunies au même instant — la créature
 * est à moins de {@value #SIGHT_RANGE} blocs, elle est <b>devant</b> le joueur
 * dans un cône de 70°, et la ligne de vue est libre. C'est délibérément la même
 * définition que « voir » : rien ne se débloque en passant à côté les yeux
 * fermés.</p>
 *
 * <p>Coût borné : un seul balayage toutes les {@value #SCAN_INTERVAL_TICKS}
 * ticks, limité aux entités vivantes dans une boîte de 40 blocs, et arrêté dès
 * que toutes les créatures sont connues du joueur.</p>
 */
public final class JournalManager {

    /** Distance maximale d'une observation qui compte. */
    private static final double SIGHT_RANGE = 34.0;
    /** Cosinus du demi-cône de vision retenu (~70° d'ouverture totale). */
    private static final double SIGHT_CONE_COS = 0.57;
    /** Cadence du balayage (2 s). */
    private static final int SCAN_INTERVAL_TICKS = 40;
    private static final int SCAN_OFFSET_TICKS = 17;

    private JournalManager() {
    }

    /** À brancher sur {@code ServerTickEvents.END_SERVER_TICK}. */
    public static void onServerTick(MinecraftServer server) {
        if (Math.floorMod(server.getTickCount() - SCAN_OFFSET_TICKS,
                SCAN_INTERVAL_TICKS) != 0) {
            return;
        }
        if (!PenumbraConfig.get().journalEnabled) {
            return;
        }
        JournalKnowledge knowledge = JournalKnowledge.get(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isAlive() || player.isSpectator()
                    || !(player.level() instanceof ServerLevel level)) {
                continue;
            }
            scan(level, player, knowledge);
        }
    }

    private static void scan(ServerLevel level, ServerPlayer player, JournalKnowledge knowledge) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(SIGHT_RANGE),
                entity -> entity != player && entity.isAlive());
        for (LivingEntity entity : nearby) {
            JournalEntry target = entryFor(entity.getType());
            if (target == null) {
                continue;
            }
            if (!isVisiblyInFront(player, entity, eye, look)) {
                continue;
            }
            if (knowledge.markSeen(player.getUUID(), target)) {
                announce(player, target);
            }
        }
    }

    /** Devant le joueur, à portée, et sans obstacle : la définition de « voir ». */
    private static boolean isVisiblyInFront(ServerPlayer player, Entity entity,
                                            Vec3 eye, Vec3 look) {
        Vec3 toEntity = entity.getEyePosition().subtract(eye);
        double distance = toEntity.length();
        if (distance < 1.0E-4 || distance > SIGHT_RANGE) {
            return false;
        }
        if (look.dot(toEntity.scale(1.0 / distance)) < SIGHT_CONE_COS) {
            return false;
        }
        return player.hasLineOfSight(entity);
    }

    /**
     * Une ligne discrète, en italique et en gris : le carnet se remplit tout
     * seul, mais il ne doit jamais interrompre la scène en cours.
     */
    private static void announce(ServerPlayer player, JournalEntry entry) {
        player.sendSystemMessage(
                Component.translatable("journal.penumbra.filled",
                                Component.translatable(entry.titleKey()))
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC),
                true);
        PenumbraNetworking.sendSound(player, PenumbraSounds.PAGE_TURN,
                player.position(), 0.28f, 0.94f);
    }

    /**
     * Notifie une découverte hors du balayage visuel (créature vanilla possédée
     * du Faux-Semblant, qui n'a pas de type d'entité propre).
     */
    public static void noteSeen(ServerPlayer player, JournalEntry entry) {
        if (player == null || !PenumbraConfig.get().journalEnabled) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        if (JournalKnowledge.get(server).markSeen(player.getUUID(), entry)) {
            announce(player, entry);
        }
    }

    /**
     * Associe un type d'entité à sa page. Le faux compagnon partage la page de
     * L'Absent : c'est la même créature, seule sa mise en scène diffère.
     */
    private static JournalEntry entryFor(EntityType<?> type) {
        Identifier key = EntityType.getKey(type);
        if (!PenumbraMod.MOD_ID.equals(key.getNamespace())) {
            return null;
        }
        return switch (key.getPath()) {
            case "host" -> JournalEntry.HOST;
            case "watcher" -> JournalEntry.WATCHER;
            case "originel" -> JournalEntry.ORIGINEL;
            case "contrefait" -> JournalEntry.CONTREFAIT;
            case "projectionist" -> JournalEntry.PROJECTIONIST;
            case "rampant" -> JournalEntry.RAMPANT;
            case "absent", "false_companion" -> JournalEntry.ABSENT;
            case "scuttler" -> JournalEntry.SCUTTLER;
            case "listener" -> JournalEntry.LISTENER;
            default -> null;
        };
    }

    /** État complet du carnet pour ce joueur, prêt à partir en paquet. */
    public static Map<JournalEntry, JournalEntry.State> states(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return Map.of();
        }
        return JournalKnowledge.get(server).stateFor(player.getUUID());
    }
}

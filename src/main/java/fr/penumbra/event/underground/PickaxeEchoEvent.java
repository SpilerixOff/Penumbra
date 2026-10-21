package fr.penumbra.event.underground;

import fr.penumbra.data.PenumbraPersistentState;
import fr.penumbra.director.HorrorDirector;
import fr.penumbra.director.PlayerHorrorProfile;
import fr.penumbra.director.TensionState;
import fr.penumbra.network.PenumbraNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Écho de pioche lointain (Phase 3, {@code pickaxe_echo}).
 * <p>
 * Un bruit de pioche ({@code STONE_BREAK}, pitch 0.55–0.75, volume 0.3) résonne 10 à 20
 * blocs à travers la roche, 4 à 8 coups espacés de 8 à 14 ticks, démarrant
 * {@value #START_DELAY_TICKS} ticks (~0,5 s) après que le joueur a CESSÉ de miner.
 * Déclenchement : hook Fabric {@code PlayerBlockBreakEvents.AFTER} enregistré par
 * {@link UndergroundEvents#register()} → {@link #noteMined(ServerPlayer, BlockPos)} ;
 * l'événement n'est candidat que si le dernier bloc miné par le joueur remonte à
 * {@value #MIN_SILENCE_TICKS}–{@value #MAX_SILENCE_TICKS} ticks (le joueur vient de
 * s'arrêter). La mémoire de minage est transitoire (Map statique bornée, purgée des
 * entrées de plus de {@value #MEMORY_TTL_TICKS} ticks). Aucune modification du monde.
 * Gain de tension : +4.
 * </p>
 */
public final class PickaxeEchoEvent extends AbstractUndergroundEvent {

    /** Délai avant le premier coup d'écho (~0,5 s après l'arrêt du minage). */
    private static final int START_DELAY_TICKS = 10;
    /** Silence de minage minimal pour être candidat (ticks). */
    private static final long MIN_SILENCE_TICKS = 10L;
    /** Silence de minage maximal pour être candidat (ticks). */
    private static final long MAX_SILENCE_TICKS = 100L;
    /** Durée de vie d'une entrée de mémoire de minage (ticks). */
    private static final long MEMORY_TTL_TICKS = 2400L;
    /** Taille maximale de la mémoire de minage (joueurs). */
    private static final int MEMORY_MAX = 64;
    /** Distance minimale de la source d'écho (blocs). */
    private static final double MIN_DISTANCE = 10.0;
    /** Distance maximale de la source d'écho (blocs). */
    private static final double MAX_DISTANCE = 20.0;

    /** Dernier minage connu par joueur : tick (horloge overworld) et position. */
    private record MineInfo(long tick, BlockPos pos) {
    }

    /** Mémoire transitoire des minages récents (non persistée, bornée). */
    private static final Map<UUID, MineInfo> LAST_MINE = new HashMap<>();

    /**
     * Appelé par le hook {@code PlayerBlockBreakEvents.AFTER} (enregistré dans
     * {@link UndergroundEvents#register()}) : mémorise le tick et la position du dernier
     * bloc cassé par ce joueur. Ne filtre pas par dimension : le canRun impose déjà
     * d'être sous terre. Coût constant, aucun scan.
     */
    public static void noteMined(ServerPlayer player, BlockPos pos) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        long now = UndergroundRestorationTracker.gameTime(level);
        LAST_MINE.put(player.getUUID(), new MineInfo(now, pos.immutable()));
        if (LAST_MINE.size() > MEMORY_MAX) {
            Iterator<Map.Entry<UUID, MineInfo>> it = LAST_MINE.entrySet().iterator();
            while (it.hasNext()) {
                if (now - it.next().getValue().tick() > MEMORY_TTL_TICKS) {
                    it.remove();
                }
            }
            // Beaucoup de joueurs peuvent miner dans la même fenêtre de deux
            // minutes : le nettoyage temporel seul ne garantissait pas le cap.
            it = LAST_MINE.entrySet().iterator();
            while (LAST_MINE.size() > MEMORY_MAX && it.hasNext()) {
                it.next();
                it.remove();
            }
        }
    }

    /** Oublie une mémoire éphémère à la déconnexion. */
    public static void onPlayerDisconnect(UUID playerId) {
        LAST_MINE.remove(playerId);
    }

    /** Purge défensive entre deux mondes ouverts dans la même JVM. */
    public static void onServerStopped(MinecraftServer server) {
        LAST_MINE.clear();
    }

    @Override
    public String id() {
        return "pickaxe_echo";
    }

    @Override
    public int weight(TensionState state) {
        return switch (state) {
            case CALM -> 4;
            case UNEASE -> 6;
            case WATCHED -> 7;
            case MANIFESTATION -> 4;
            case HUNT -> 2;
            case CRISIS -> 1;
            case FALSE_CALM -> 6;
        };
    }

    @Override
    public int cooldownTicks() {
        return 4800;
    }

    @Override
    public boolean canRun(ServerPlayer player, PlayerHorrorProfile profile) {
        if (!super.canRun(player, profile)) {
            return false;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        MineInfo info = LAST_MINE.get(player.getUUID());
        if (info == null) {
            return false;
        }
        long silence = UndergroundRestorationTracker.gameTime(level) - info.tick();
        return silence >= MIN_SILENCE_TICKS && silence <= MAX_SILENCE_TICKS;
    }

    @Override
    public boolean run(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        UndergroundRestorationTracker.checkDeadlines(level); // maintenance amortie
        RandomSource random = player.getRandom();

        // Source d'écho : 10–20 blocs dans une direction horizontale aléatoire, à travers
        // la roche (aucune ligne de vue requise — le son traverse la pierre, c'est voulu).
        double angle = random.nextDouble() * (Math.PI * 2.0);
        double distance = MIN_DISTANCE + random.nextDouble() * (MAX_DISTANCE - MIN_DISTANCE);
        Vec3 source = player.position().add(
                Math.cos(angle) * distance,
                (random.nextDouble() - 0.5) * 4.0, // ±2 blocs de dénivelé
                Math.sin(angle) * distance);

        int strikes = 4 + random.nextInt(5); // 4..8
        int delay = START_DELAY_TICKS;
        for (int i = 0; i < strikes; i++) {
            float pitch = 0.55f + random.nextFloat() * 0.2f; // 0.55..0.75
            int at = delay;
            HorrorDirector.runLater(level, at, () -> {
                if (player.isAlive() && player.level() == level) {
                    PenumbraNetworking.sendSound(player, SoundEvents.STONE_BREAK,
                            source, 0.3f, pitch);
                }
            });
            delay += 8 + random.nextInt(7); // 8..14 ticks entre coups
        }
        return true;
    }
}

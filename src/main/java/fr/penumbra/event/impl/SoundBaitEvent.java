package fr.penumbra.event.impl;

import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.director.HorrorDirector;
import fr.penumbra.director.PlayerHorrorProfile;
import fr.penumbra.director.TensionState;
import fr.penumbra.entity.listener.ListenerManager;
import fr.penumbra.event.HorrorEvent;
import fr.penumbra.network.PenumbraNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * {@code sound_bait} (0.17.0) — quelqu'un creuse, et ce n'est pas vous.
 *
 * <p>Cinq à sept coups de pioche, à la cadence exacte d'un joueur qui mine, à
 * douze ou vingt-deux blocs. Aucun bloc n'est touché : ce sont uniquement des
 * sons spatialisés à une position réelle et atteignable. Puis plus rien.</p>
 *
 * <p>L'événement est écrit pour le joueur curieux : il ne punit pas
 * l'exploration, il l'utilise. Aller voir ne déclenche aucun screamer et ne
 * révèle rien — il n'y a rien. Mais le trajet éloigne le joueur de son refuge,
 * et il découvre plus tard, seul, que c'était le but. Et si L'Écouteur est déjà
 * dans les environs, la position du leurre l'intéresse réellement : il ira
 * fouiller ce bruit-là, pas le joueur.</p>
 *
 * <p>Le tirage exige {@code luresEnabled}. Aucune modification de bloc n'est
 * effectuée, même si {@code worldModificationEnabled} est actif.</p>
 */
public final class SoundBaitEvent implements HorrorEvent {

    private static final int MIN_HITS = 5;
    private static final int MAX_HITS = 7;
    /** Cadence de minage vanilla (à peu près un coup toutes les 0,25 s). */
    private static final int HIT_INTERVAL_TICKS = 6;
    private static final int HIT_JITTER_TICKS = 3;

    private static final double MIN_DISTANCE = 12.0;
    private static final double MAX_DISTANCE = 22.0;
    private static final int PLACEMENT_ATTEMPTS = 14;
    private static final int SCAN_UP = 5;
    private static final int SCAN_DOWN = 12;

    @Override
    public String id() {
        return "sound_bait";
    }

    @Override
    public int weight(TensionState state) {
        return switch (state) {
            case CALM -> 6;
            case UNEASE -> 12;
            case WATCHED -> 11;
            case MANIFESTATION -> 6;
            case HUNT -> 0;
            case CRISIS -> 0;
            case FALSE_CALM -> 10;
        };
    }

    @Override
    public int cooldownTicks() {
        return 4200;
    }

    @Override
    public boolean canRun(ServerPlayer player, PlayerHorrorProfile profile) {
        return PenumbraConfig.get().luresEnabled
                && !player.isSleeping() && !player.isSpectator();
    }

    @Override
    public boolean run(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        BlockPos site = findSite(level, player);
        if (site == null) {
            return false;
        }
        RandomSource random = player.getRandom();
        int hits = MIN_HITS + random.nextInt(MAX_HITS - MIN_HITS + 1);
        Vec3 origin = Vec3.atCenterOf(site);
        schedule(level, player.getUUID(), origin, hits, 1);
        return true;
    }

    /** Chaîne de coups ; chaque maillon revérifie le joueur et le chunk. */
    private void schedule(ServerLevel level, UUID playerId, Vec3 origin,
                          int remaining, int delay) {
        if (remaining <= 0) {
            return;
        }
        HorrorDirector.runLater(level, Math.max(1, delay), () -> {
            MinecraftServer server = level.getServer();
            ServerPlayer player = server == null
                    ? null : server.getPlayerList().getPlayer(playerId);
            if (player == null || !player.isAlive() || player.level() != level
                    || player.isSpectator()) {
                return;
            }
            RandomSource random = player.getRandom();
            PenumbraNetworking.sendSoundPrioritySpatial(player, SoundEvents.STONE_HIT,
                    origin, 0.72f, 0.86f + random.nextFloat() * 0.16f);
            // 0.17.0 : L'Écouteur entend ce leurre comme n'importe quel bruit et
            // partira le fouiller — c'est la contre-mesure la plus élégante du mod.
            ListenerManager.reportBurst(level, player, origin,
                    ListenerManager.BLOCK_BREAK_RADIUS);
            schedule(level, playerId, origin, remaining - 1,
                    HIT_INTERVAL_TICKS + random.nextInt(HIT_JITTER_TICKS + 1));
        });
    }

    /** Sol valide, chargé et atteignable, à bonne distance et hors du regard. */
    private static BlockPos findSite(ServerLevel level, ServerPlayer player) {
        RandomSource random = player.getRandom();
        Vec3 look = player.getLookAngle().multiply(1.0, 0.0, 1.0);
        look = look.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : look.normalize();
        double rearAngle = Math.atan2(-look.z, -look.x);
        for (int attempt = 0; attempt < PLACEMENT_ATTEMPTS; attempt++) {
            double angle = rearAngle + Math.toRadians(-120.0 + random.nextDouble() * 240.0);
            double distance = MIN_DISTANCE + random.nextDouble() * (MAX_DISTANCE - MIN_DISTANCE);
            int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (int dy = SCAN_UP; dy >= -SCAN_DOWN; dy--) {
                cursor.set(x, Mth.floor(player.getY()) + dy, z);
                if (!level.isLoaded(cursor)) {
                    break;
                }
                BlockPos below = cursor.below();
                if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                        && level.getBlockState(cursor).isAir()
                        && level.getFluidState(cursor).isEmpty()) {
                    return cursor.immutable();
                }
            }
        }
        return null;
    }
}

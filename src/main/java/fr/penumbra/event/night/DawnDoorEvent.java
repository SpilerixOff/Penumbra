package fr.penumbra.event.night;

import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.data.PenumbraPersistentState;
import fr.penumbra.director.PlayerHorrorProfile;
import fr.penumbra.director.TensionState;
import fr.penumbra.event.HorrorEvent;
import fr.penumbra.network.PenumbraNetworking;
import fr.penumbra.registry.PenumbraSounds;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Porte du refuge retrouvée ouverte à l'aube (événement nocturne {@code dawn_door}).
 * <p>
 * Candidat uniquement en FIN DE NUIT (fenêtre {@value #DAWN_START}–24000 sur
 * l'horloge overworld, même API {@code getOverworldClockTime} que
 * {@code FootstepMimicEvent}) et si un refuge appris ({@code homePoints} du
 * profil, ≤ 4 entrées — coût O(4)) se trouve à moins de {@value #REFUGE_RADIUS}
 * blocs du joueur. Ouvre la porte en bois fermée la plus proche du refuge
 * (scan borné, coquilles de Chebyshev rayon {@value #DOOR_SCAN_RADIUS}) et joue
 * un {@code DOOR_GROAN} très discret. RIEN d'autre ne se passe : c'est un faux
 * sentiment. La porte N'EST PAS refermée automatiquement — elle est refermable
 * par le joueur, modification réversible par conception (précédent établi :
 * {@code DoorAnomalyEvent} ne restaure pas non plus ; la règle « jamais près
 * d'un lit/coffre » ne s'applique pas, la porte visée est celle du refuge
 * lui-même). Le joueur dormeur N'EST PAS exclu : se réveiller et trouver sa
 * porte ouverte est précisément l'effet recherché.
 * Exige {@code nightEventsEnabled} et {@code worldModificationEnabled}.
 * Gain de tension : +5 en cas de réussite uniquement.
 * </p>
 */
public final class DawnDoorEvent implements HorrorEvent {

    /** Début de la fenêtre d'aube (ticks de la journée, fin de nuit). */
    private static final long DAWN_START = 23200L;
    /** Distance maximale du joueur à un refuge pour être candidat (blocs). */
    private static final double REFUGE_RADIUS = 24.0;
    /** Rayon Chebyshev du scan de porte autour du refuge : 17³ = 4 913 positions. */
    private static final int DOOR_SCAN_RADIUS = 8;

    @Override
    public String id() {
        return "dawn_door";
    }

    @Override
    public int weight(TensionState state) {
        return switch (state) {
            case CALM -> 1;
            case UNEASE -> 2;
            case WATCHED -> 2;
            case MANIFESTATION -> 2;
            case HUNT -> 1;
            case CRISIS -> 1;
            case FALSE_CALM -> 3;
        };
    }

    @Override
    public int cooldownTicks() {
        // Une fois par nuit au plus : la fenêtre d'aube dure 800 ticks.
        return 7200;
    }

    @Override
    public boolean canRun(ServerPlayer player, PlayerHorrorProfile profile) {
        PenumbraConfig config = PenumbraConfig.get();
        if (!config.nightEventsEnabled || !config.worldModificationEnabled) {
            return false;
        }
        if (!(player.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return false;
        }
        long dayTime = level.getOverworldClockTime() % 24000L;
        if (dayTime < DAWN_START) {
            return false;
        }
        return nearestRefuge(player, profile) != null;
    }

    @Override
    public boolean run(ServerPlayer player) {
        PenumbraConfig config = PenumbraConfig.get();
        if (!config.nightEventsEnabled || !config.worldModificationEnabled) {
            return false;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        PlayerHorrorProfile profile =
                PenumbraPersistentState.get(level.getServer()).profile(player.getUUID());
        BlockPos refuge = nearestRefuge(player, profile);
        if (refuge == null) {
            return false; // aucun refuge appris à portée : annulé, aucun effet
        }
        BlockPos doorPos = NightSupport.findNearest(level, refuge, DOOR_SCAN_RADIUS,
                state -> state.is(BlockTags.WOODEN_DOORS) && !state.getValue(DoorBlock.OPEN));
        if (doorPos == null) {
            return false; // aucune porte en bois fermée au refuge : annulé, aucun effet
        }
        RandomSource random = player.getRandom();
        BlockState doorState = level.getBlockState(doorPos);
        // Flag 10 = clients + mise à jour immédiate, identique à l'ouverture vanilla
        // (pattern DoorAnomalyEvent) ; la moitié haute se synchronise via updateShape.
        level.setBlock(doorPos, doorState.setValue(DoorBlock.OPEN, true), 10);
        PenumbraNetworking.sendSound(player, PenumbraSounds.DOOR_GROAN, Vec3.atCenterOf(doorPos),
                0.4f, 0.65f + random.nextFloat() * 0.1f);
        return true;
    }

    /**
     * Refuge appris le plus proche du joueur (≤ {@value #REFUGE_RADIUS} blocs),
     * ou {@code null}. Coût O(4) : {@code homePoints} est borné à 4 entrées.
     */
    private static BlockPos nearestRefuge(ServerPlayer player, PlayerHorrorProfile profile) {
        List<BlockPos> homes = profile.homePoints();
        BlockPos nearest = null;
        double best = REFUGE_RADIUS * REFUGE_RADIUS;
        for (BlockPos home : homes) {
            double dist = home.distSqr(player.blockPosition());
            if (dist <= best) {
                best = dist;
                nearest = home;
            }
        }
        return nearest;
    }
}

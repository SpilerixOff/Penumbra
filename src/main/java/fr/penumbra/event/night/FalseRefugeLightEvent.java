package fr.penumbra.event.night;

import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.director.HorrorDirector;
import fr.penumbra.director.PlayerHorrorProfile;
import fr.penumbra.director.TensionState;
import fr.penumbra.event.HorrorEvent;
import fr.penumbra.event.underground.UndergroundRestorationTracker;
import fr.penumbra.network.PenumbraNetworking;
import fr.penumbra.registry.PenumbraSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Lueur chaude de faux refuge (événement nocturne {@code false_refuge_light}).
 * <p>
 * Pose 1 à 2 blocs lumineux temporaires — une lanterne au sol (plus « refuge »
 * qu'une torche ; {@code HANGING=false} par défaut, {@code canSurvive} vérifie
 * le support) et parfois une torche accolée — à {@value #MIN_DISTANCE}–
 * {@value #MAX_DISTANCE} blocs du joueur, puis joue la nappe {@code DISTANT_HUM}
 * (pattern {@code DistantLightEvent}). Discipline des modifications du monde :
 * chaque pose exige un bloc d'air, {@code canSurvive}, un chunk chargé, et
 * l'absence de lit/coffre à moins de {@value #HAZARD_RADIUS} blocs ; chaque
 * état d'origine est capturé et restauré après {@value #MIN_RESTORE_TICKS}–
 * {@value #MAX_RESTORE_TICKS} ticks (3–6 min), SEULEMENT si le bloc est resté
 * celui posé par l'événement — jamais d'écrasement d'une pose ultérieure du
 * joueur. Maximum 2 blocs modifiés (≤ 6). Approcher ne révèle rien : seule la
 * lumière, qui finit par s'éteindre d'elle-même.
 * Exige {@code worldModificationEnabled}. Annulé sans AUCUN effet (ni hum) si
 * aucun emplacement valide n'existe. Gain de tension : +5.
 * </p>
 */
public final class FalseRefugeLightEvent implements HorrorEvent {

    /** Distance min/max de la lueur (blocs). */
    private static final double MIN_DISTANCE = 30.0;
    private static final double MAX_DISTANCE = 60.0;
    /** Délai avant suppression des lumières (3–6 min). */
    private static final int MIN_RESTORE_TICKS = 3600;
    private static final int MAX_RESTORE_TICKS = 7200;
    /** Rayon d'exclusion autour des lits/coffres (discipline modifications du monde). */
    private static final int HAZARD_RADIUS = 8;
    /** Amplitude de la recherche verticale d'un emplacement (au-dessus / en dessous). */
    private static final int SCAN_UP = 6;
    private static final int SCAN_DOWN = 24;
    /** Offsets horizontaux candidats pour la seconde lumière (torche accolée). */
    private static final int[][] SECOND_OFFSETS = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};

    @Override
    public String id() {
        return "false_refuge_light";
    }

    @Override
    public int weight(TensionState state) {
        return switch (state) {
            case CALM -> 1;
            case UNEASE -> 2;
            case WATCHED -> 3;
            case MANIFESTATION -> 2;
            case HUNT -> 1;
            case CRISIS -> 1;
            case FALSE_CALM -> 3;
        };
    }

    @Override
    public int cooldownTicks() {
        return 5400;
    }

    @Override
    public boolean canRun(ServerPlayer player, PlayerHorrorProfile profile) {
        // L'effet est une modification du monde : interdit si la config la refuse.
        return PenumbraConfig.get().worldModificationEnabled && NightSupport.commonNight(player);
    }

    @Override
    public boolean run(ServerPlayer player) {
        if (!PenumbraConfig.get().worldModificationEnabled) {
            return false;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        RandomSource random = player.getRandom();

        double angle = random.nextDouble() * (Math.PI * 2.0);
        double distance = MIN_DISTANCE + random.nextDouble() * (MAX_DISTANCE - MIN_DISTANCE);
        int baseX = Mth.floor(player.getX() + Math.cos(angle) * distance);
        int baseZ = Mth.floor(player.getZ() + Math.sin(angle) * distance);
        int baseY = Mth.floor(player.getY());

        BlockPos spot = findLanternSpot(level, baseX, baseY, baseZ);
        if (spot == null) {
            return false; // aucun emplacement valide : annulé, aucun effet (ni hum)
        }

        // Lumière principale : lanterne au sol, état d'origine capturé puis restauration trackée.
        if (!placeTracked(level, player, spot, Blocks.LANTERN.defaultBlockState(), random)) {
            return false;
        }

        // Seconde lumière optionnelle (50 %) : torche accolée, mêmes règles de pose.
        if (random.nextBoolean()) {
            for (int[] offset : SECOND_OFFSETS) {
                BlockPos second = spot.offset(offset[0], 0, offset[1]);
                if (level.isLoaded(second) && level.getBlockState(second).isAir()
                        && Blocks.TORCH.defaultBlockState().canSurvive(level, second)
                        && !NightSupport.nearBedOrChest(level, second, HAZARD_RADIUS)) {
                    placeTracked(level, player, second, Blocks.TORCH.defaultBlockState(), random);
                    break;
                }
            }
        }

        float pitch = 0.85f + random.nextFloat() * 0.15f; // 0.85..1.0
        PenumbraNetworking.sendSound(player, PenumbraSounds.DISTANT_HUM, Vec3.atCenterOf(spot), 0.35f, pitch);
        return true;
    }

    /**
     * Cherche un emplacement de lanterne valide à la verticale de (x, z) : bloc
     * d'air, chunk chargé, {@code canSurvive}, et aucun lit/coffre à moins de
     * {@value #HAZARD_RADIUS} blocs. Balayage en ligne borné — aucun scan volumique
     * (pattern {@code DistantLightEvent.findTorchSpot}).
     */
    private static BlockPos findLanternSpot(ServerLevel level, int baseX, int baseY, int baseZ) {
        BlockState lantern = Blocks.LANTERN.defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = SCAN_UP; dy >= -SCAN_DOWN; dy--) {
            cursor.set(baseX, baseY + dy, baseZ);
            if (!level.isLoaded(cursor)) {
                return null; // hors zone chargée : jamais de chunk forcé
            }
            if (!level.getBlockState(cursor).isAir() || !lantern.canSurvive(level, cursor)) {
                continue;
            }
            if (NightSupport.nearBedOrChest(level, cursor, HAZARD_RADIUS)) {
                continue; // discipline : jamais près d'un lit/coffre du joueur
            }
            return cursor.immutable();
        }
        return null;
    }

    /**
     * Pose un bloc lumineux en trackant l'état d'origine : restauration après
     * {@value #MIN_RESTORE_TICKS}–{@value #MAX_RESTORE_TICKS} ticks, seulement si
     * le bloc est resté celui posé ici (jamais d'écrasement — pattern
     * {@code DistantLightEvent.run}).
     */
    private static boolean placeTracked(ServerLevel level, ServerPlayer player, BlockPos pos,
                                        BlockState light, RandomSource random) {
        BlockState original = level.getBlockState(pos);
        if (!level.setBlock(pos, light, 3)) {
            return false;
        }
        int restoreDelay = MIN_RESTORE_TICKS + random.nextInt(MAX_RESTORE_TICKS - MIN_RESTORE_TICKS + 1);
        UndergroundRestorationTracker.get(level.getServer()).add(level,
                new UndergroundRestorationTracker.Entry(
                        pos.immutable(), level.dimension(), original, light,
                        UndergroundRestorationTracker.TYPE_TEMPORARY_EFFECT,
                        UndergroundRestorationTracker.gameTime(level) + restoreDelay,
                        player.getUUID().toString()));
        return true;
    }
}

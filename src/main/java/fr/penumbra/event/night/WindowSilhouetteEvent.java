package fr.penumbra.event.night;

import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.director.HorrorDirector;
import fr.penumbra.director.PlayerHorrorProfile;
import fr.penumbra.director.TensionState;
import fr.penumbra.entity.HostBehavior;
import fr.penumbra.entity.HostEntity;
import fr.penumbra.event.HorrorEvent;
import fr.penumbra.registry.PenumbraEntities;
import fr.penumbra.registry.PenumbraParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;

/**
 * Silhouette devant une ouverture (événement nocturne {@code window_silhouette}).
 * <p>
 * Trouve la fenêtre/porte la plus proche (scan borné, coquilles de Chebyshev
 * rayon {@value #OPENING_SCAN_RADIUS}) puis invoque un Hôte à
 * {@value #MIN_DISTANCE}–{@value #MAX_DISTANCE} blocs AU-DELÀ de l'ouverture,
 * dans l'axe joueur → ouverture, comportement {@code HIDING} et face au joueur
 * (pattern d'invocation manuelle de {@code BinocularsSightingEvent} :
 * {@code HostEntity.spawnNear} est inutilisable ici car il impose l'obscurité et
 * évite le champ de vision — or la silhouette doit être VISIBLE à travers
 * l'ouverture). Disparition : dès qu'un joueur la regarde ~0,4 s (goals propres
 * de l'Hôte, pattern {@code FakeSilhouetteEvent}), ou forfaitairement après
 * {@value #FORCED_VANISH_TICKS} ticks (&lt; 2 s) si elle est encore là en
 * {@code HIDING} — si le goal Vanish l'a entre-temps téléportée en STALKING,
 * son cycle de vie normal reprend (cohérent avec FakeSilhouetteEvent).
 * Apparition silencieuse. Exige {@code hostEnabled}. Annulée sans AUCUN effet
 * si aucune ouverture ni aucun emplacement valide n'est trouvé.
 * Gain de tension : +8 en cas de réussite uniquement.
 * </p>
 */
public final class WindowSilhouetteEvent implements HorrorEvent {

    /** Rayon Chebyshev du scan d'ouverture borné : 17³ = 4 913 positions. */
    private static final int OPENING_SCAN_RADIUS = 8;
    /** Distance min/max de la silhouette au-delà de l'ouverture (blocs). */
    private static final double MIN_DISTANCE = 8.0;
    private static final double MAX_DISTANCE = 15.0;
    /** Durée maximale de l'apparition avant disparition forfaitaire (< 2 s). */
    private static final int FORCED_VANISH_TICKS = 38;
    /** Amplitude de la recherche verticale d'un emplacement debout. */
    private static final int SCAN_UP = 8;
    private static final int SCAN_DOWN = 24;

    @Override
    public String id() {
        return "window_silhouette";
    }

    @Override
    public int weight(TensionState state) {
        // Poids faibles : événement rare (contrat Agent_NightEvents).
        return switch (state) {
            case CALM -> 0;
            case UNEASE -> 2;
            case WATCHED -> 3;
            case MANIFESTATION -> 4;
            case HUNT -> 3;
            case CRISIS -> 2;
            case FALSE_CALM -> 2;
        };
    }

    @Override
    public int cooldownTicks() {
        return 4800;
    }

    @Override
    public boolean canRun(ServerPlayer player, PlayerHorrorProfile profile) {
        return PenumbraConfig.get().hostEnabled && NightSupport.commonNight(player);
    }

    @Override
    public boolean run(ServerPlayer player) {
        if (!PenumbraConfig.get().hostEnabled) {
            return false;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        BlockPos opening = NightSupport.findNearest(level, player.blockPosition(),
                OPENING_SCAN_RADIUS, NightSupport::isOpening);
        if (opening == null) {
            return false; // aucune fenêtre ni porte à portée : événement annulé, aucun effet
        }
        RandomSource random = player.getRandom();
        Vec3 playerPos = player.position();
        Vec3 openingCenter = Vec3.atCenterOf(opening);
        Vec3 dir = NightSupport.horizontalDir(playerPos, openingCenter);
        double distance = MIN_DISTANCE + random.nextDouble() * (MAX_DISTANCE - MIN_DISTANCE);

        int baseX = Mth.floor(openingCenter.x + dir.x * distance);
        int baseZ = Mth.floor(openingCenter.z + dir.z * distance);
        BlockPos spot = NightSupport.findStandingSpot(level, baseX, Mth.floor(playerPos.y), baseZ,
                SCAN_UP, SCAN_DOWN);
        if (spot == null) {
            return false; // aucun emplacement debout valide : annulé, aucun effet
        }

        HostEntity host = new HostEntity(PenumbraEntities.HOST, level);
        host.setOwner(player);
        double x = spot.getX() + 0.5;
        double z = spot.getZ() + 0.5;
        host.snapTo(x, spot.getY(), z, NightSupport.faceYaw(x, z, playerPos.x, playerPos.z), 0.0f);
        host.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), EntitySpawnReason.EVENT, null);
        if (!level.addFreshEntity(host)) {
            return false;
        }
        host.setBehavior(HostBehavior.HIDING);

        // Disparition forfaitaire < 2 s : uniquement si l'Hôte est toujours là ET
        // toujours en HIDING (une téléportation Vanish → STALKING suit son cycle normal).
        HorrorDirector.runLater(level, FORCED_VANISH_TICKS, () -> {
            if (!host.isRemoved() && host.getBehavior() == HostBehavior.HIDING) {
                Vec3 center = host.position().add(0.0, 1.0, 0.0);
                level.sendParticles(PenumbraParticles.ASH_MOTE, center.x, center.y, center.z,
                        10, 0.3, 0.5, 0.3, 0.01);
                host.discard();
            }
        });

        return true;
    }
}

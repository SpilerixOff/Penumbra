package fr.penumbra.event.night;

import fr.penumbra.director.HorrorDirector;
import fr.penumbra.director.PlayerHorrorProfile;
import fr.penumbra.director.TensionState;
import fr.penumbra.event.HorrorEvent;
import fr.penumbra.network.PenumbraNetworking;
import fr.penumbra.registry.PenumbraSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Ombre traversant une ouverture (événement nocturne {@code shadow_crossing}).
 * <p>
 * Trouve la fenêtre/porte la plus proche (même détection bornée que
 * {@code WindowSilhouetteEvent}) puis fait balayer une bande de fumée sombre
 * (vanilla {@code LARGE_SMOKE}, vérifié javap 26.1.2) PERPENDICULAIREMENT à
 * l'axe joueur → ouverture : {@value #BURSTS} rafales de quelques particules,
 * une toutes les {@value #BURST_INTERVAL_TICKS} ticks — traversée complète en
 * ~0,55 s (&lt; 1 s). Un très faible {@code BREATH_BEHIND} (volume
 * {@value #SOUND_VOLUME}) au début du balayage. AUCUNE entité, AUCUNE
 * modification du monde. Annulé sans effet si aucune ouverture n'est trouvée.
 * Gain de tension : +4.
 * </p>
 */
public final class ShadowCrossingEvent implements HorrorEvent {

    /** Rayon Chebyshev du scan d'ouverture borné : 17³ = 4 913 positions. */
    private static final int OPENING_SCAN_RADIUS = 8;
    /** Nombre de rafales du balayage et intervalle (ticks) — total < 1 s. */
    private static final int BURSTS = 6;
    private static final int BURST_INTERVAL_TICKS = 2;
    /** Demi-amplitude du balayage de part et d'autre de l'ouverture (blocs). */
    private static final double SWEEP_HALF_WIDTH = 1.5;
    /** Volume du souffle d'accompagnement (très faible). */
    private static final float SOUND_VOLUME = 0.2f;

    @Override
    public String id() {
        return "shadow_crossing";
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
            case FALSE_CALM -> 2;
        };
    }

    @Override
    public int cooldownTicks() {
        return 3600;
    }

    @Override
    public boolean canRun(ServerPlayer player, PlayerHorrorProfile profile) {
        return NightSupport.commonNight(player);
    }

    @Override
    public boolean run(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        BlockPos opening = NightSupport.findNearest(level, player.blockPosition(),
                OPENING_SCAN_RADIUS, NightSupport::isOpening);
        if (opening == null) {
            return false; // aucune ouverture à portée : annulé, aucun effet
        }
        RandomSource random = player.getRandom();
        Vec3 center = Vec3.atCenterOf(opening);

        // Axe de balayage : perpendiculaire horizontal à l'axe joueur → ouverture.
        Vec3 dir = NightSupport.horizontalDir(player.position(), center);
        Vec3 sweep = new Vec3(-dir.z, 0.0, dir.x);

        // Souffle très faible au début du balayage.
        PenumbraNetworking.sendSound(player, PenumbraSounds.BREATH_BEHIND, center,
                SOUND_VOLUME, 0.55f + random.nextFloat() * 0.1f);

        for (int i = 0; i < BURSTS; i++) {
            double offset = -SWEEP_HALF_WIDTH
                    + (2.0 * SWEEP_HALF_WIDTH) * i / (BURSTS - 1); // -1.5 → +1.5
            Vec3 pos = center.add(sweep.x * offset, 0.2, sweep.z * offset);
            HorrorDirector.runLater(level, i * BURST_INTERVAL_TICKS, () -> burst(level, pos));
        }
        return true;
    }

    /** Rafale sombre du balayage : 4 particules LARGE_SMOKE, dispersion minimale. */
    private static void burst(ServerLevel level, Vec3 pos) {
        level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y, pos.z, 4, 0.15, 0.15, 0.15, 0.0);
    }
}

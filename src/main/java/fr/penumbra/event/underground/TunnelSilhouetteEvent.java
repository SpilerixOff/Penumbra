package fr.penumbra.event.underground;

import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.director.HorrorDirector;
import fr.penumbra.director.PlayerHorrorProfile;
import fr.penumbra.director.TensionState;
import fr.penumbra.entity.HostBehavior;
import fr.penumbra.entity.HostEntity;
import fr.penumbra.registry.PenumbraEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;

/**
 * Silhouette dans une galerie souterraine ({@code tunnel_silhouette}).
 * Une recherche bornée suit l'air praticable de la grotte au lieu d'exiger
 * quinze blocs parfaitement rectilignes. L'Hôte apparaît à 8–22 blocs, de
 * préférence dans le cône visible, reste assez longtemps pour être remarqué
 * et peut servir d'avertissement à une poursuite à forte tension.
 */
public final class TunnelSilhouetteEvent extends AbstractUndergroundEvent {

    private static final double MIN_DISTANCE = 8.0;
    private static final double MAX_DISTANCE = 22.0;
    /** Huit secondes : le prochain jet naturel de poursuite peut voir cet Hôte. */
    private static final int VISIBLE_TICKS = 160;
    /** Exclusion uniquement pour les Hôtes appartenant au même joueur. */
    private static final double HOST_EXCLUSION_RADIUS = 100.0;

    @Override
    public String id() {
        return "tunnel_silhouette";
    }

    @Override
    public int weight(TensionState state) {
        return switch (state) {
            case CALM -> 2;
            case UNEASE -> 6;
            case WATCHED -> 10;
            case MANIFESTATION -> 12;
            case HUNT -> 10;
            case CRISIS -> 10;
            case FALSE_CALM -> 5;
        };
    }

    @Override
    public int cooldownTicks() {
        return 2400;
    }

    @Override
    public boolean canRun(ServerPlayer player, PlayerHorrorProfile profile) {
        return super.canRun(player, profile) && PenumbraConfig.get().hostEnabled;
    }

    @Override
    public boolean run(ServerPlayer player) {
        if (!PenumbraConfig.get().hostEnabled
                || !(player.level() instanceof ServerLevel level)) {
            return false;
        }

        BlockPos spawnFeet = CaveSpawnLocator.find(
                level, player, MIN_DISTANCE, MAX_DISTANCE, player.getRandom());
        if (spawnFeet == null
                || HostEntity.hasAnyHostNearby(
                        level, spawnFeet, HOST_EXCLUSION_RADIUS, player)) {
            return false;
        }

        RandomSource random = level.getRandom();
        HostEntity host = new HostEntity(PenumbraEntities.HOST, level);
        host.setOwner(player);
        host.snapTo(spawnFeet.getX() + 0.5, spawnFeet.getY(), spawnFeet.getZ() + 0.5,
                random.nextFloat() * 360.0f, 0.0f);
        host.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnFeet),
                EntitySpawnReason.EVENT, null);
        if (!level.addFreshEntity(host)) {
            return false;
        }
        host.setBehavior(HostBehavior.OBSERVING);
        host.setMaxLifetimeTicks(VISIBLE_TICKS + 20);
        HorrorDirector.runLater(level, VISIBLE_TICKS, () -> {
            if (!host.isRemoved()
                    && host.getBehavior() != HostBehavior.CHASING
                    && host.getBehavior() != HostBehavior.ATTACKING) {
                host.discard();
            }
        });
        return true;
    }
}

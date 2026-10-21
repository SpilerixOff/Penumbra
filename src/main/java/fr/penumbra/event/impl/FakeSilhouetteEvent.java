package fr.penumbra.event.impl;

import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.data.PenumbraPersistentState;
import fr.penumbra.director.PlayerHorrorProfile;
import fr.penumbra.director.TensionState;
import fr.penumbra.entity.HostBehavior;
import fr.penumbra.entity.HostEntity;
import fr.penumbra.event.HorrorEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fausse silhouette tapie (SPEC2 §6).
 * <p>
 * {@code HostEntity.spawnNear(level, player, 28, 40)} puis comportement forcé à
 * {@code HIDING}. L'entité disparaît dès qu'un joueur l'a en ligne de vue pendant 4 ticks
 * cumulés ou s'approche à moins de 16 blocs : ce retrait est assuré côté entité par les
 * goals de l'Hôte ({@code HiddenWatchGoal} / {@code VanishWhenSeenGoal} à seuil réduit,
 * périmètre Agent_ChaseAI). Si {@code spawnNear} retourne {@code null}, l'événement est
 * annulé : retour immédiat, AUCUN effet (ni tension), il ne compte pas comme un événement.
 * Apparition silencieuse : aucun son. Exige {@code luresEnabled} et {@code hostEnabled}.
 * Gain de tension : +10 en cas de réussite uniquement.
 * </p>
 */
public final class FakeSilhouetteEvent implements HorrorEvent {

    @Override
    public String id() {
        return "fake_silhouette";
    }

    @Override
    public int weight(TensionState state) {
        return switch (state) {
            case CALM -> 2;
            case UNEASE -> 3;
            case WATCHED -> 6;
            case MANIFESTATION -> 10;
            case HUNT -> 8;
            case CRISIS -> 6;
            case FALSE_CALM -> 4;
        };
    }

    @Override
    public int cooldownTicks() {
        return 2400;
    }

    @Override
    public boolean canRun(ServerPlayer player, PlayerHorrorProfile profile) {
        PenumbraConfig config = PenumbraConfig.get();
        return config.luresEnabled && config.hostEnabled && !player.isSleeping();
    }

    @Override
    public boolean run(ServerPlayer player) {
        PenumbraConfig config = PenumbraConfig.get();
        if (!config.luresEnabled || !config.hostEnabled) {
            return false;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        HostEntity host = HostEntity.spawnNear(level, player, 28.0, 40.0);
        if (host == null) {
            return false; // apparition impossible sans être injuste : événement annulé, aucun effet
        }
        host.setBehavior(HostBehavior.HIDING);
        return true;
    }
}

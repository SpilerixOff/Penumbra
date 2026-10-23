package fr.penumbra.event;

import fr.penumbra.director.HorrorDirector;
import fr.penumbra.event.personal.ActionEchoLog;
import fr.penumbra.event.impl.AnticlimaxEvent;
import fr.penumbra.event.impl.BinocularsSightingEvent;
import fr.penumbra.event.impl.BreathBehindEvent;
import fr.penumbra.event.impl.DistantHostSightingEvent;
import fr.penumbra.event.impl.DistantLightEvent;
import fr.penumbra.event.impl.DoorAnomalyEvent;
import fr.penumbra.event.impl.FakeCreeperEvent;
import fr.penumbra.event.impl.FakeSilhouetteEvent;
import fr.penumbra.event.impl.FootstepMimicEvent;
import fr.penumbra.event.impl.HeldBreathEvent;
import fr.penumbra.event.impl.ListenerPassageEvent;
import fr.penumbra.event.impl.FalseCompanionEvent;
import fr.penumbra.event.impl.LureDoorEvent;
import fr.penumbra.event.impl.MiningBaitEvent;
import fr.penumbra.event.impl.MimicIntrusionEvent;
import fr.penumbra.event.impl.RefugeIntrusionEvent;
import fr.penumbra.event.impl.ActionEchoEvent;
import fr.penumbra.event.impl.FauxCreatureEvent;
import fr.penumbra.event.impl.SilenceEvent;
import fr.penumbra.event.impl.SoundBaitEvent;
import fr.penumbra.event.impl.SecondPresenceEvent;
import fr.penumbra.event.impl.SleepInterruptEvent;
import fr.penumbra.event.impl.TorchSputterEvent;
import fr.penumbra.event.impl.WrongFootfallEvent;
import fr.penumbra.event.night.NightEvents;
import fr.penumbra.event.personal.PersonalEvents;
import fr.penumbra.event.underground.UndergroundEvents;
import fr.penumbra.event.watcher.WatcherEvents;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;

/**
 * Registre statique des événements d'horreur (SPEC §4.6, étendu SPEC2 §6).
 * Ordre et ids contractuels : footsteps, breath, fake_creeper, torch_sputter,
 * door_anomaly, distant_sighting, silence, sleep_interrupt — puis Phase 2 :
 * lure_door, fake_silhouette, distant_light, anticlimax, binoculars_sighting.
 * Phase 3 : + 8 événements nocturnes ({@code NightEvents}) et 8 souterrains
 * ({@code UndergroundEvents}), ajoutés au pool de sélection du directeur.
 * 0.17.0 : + held_breath, wrong_footfall, sound_bait et listener_passage.
 */
public final class HorrorEvents {

    /** Liste immuable de tous les événements, instanciés une seule fois (pas d'allocation par tick). */
    public static final List<HorrorEvent> ALL = buildAll();

    /**
     * Construit le pool complet : événements Phase 1/2, puis Phase 3 —
     * nocturnes ({@link NightEvents}) et souterrains ({@link UndergroundEvents}),
     * qui sont des singletons sans état partagés avec leurs commandes dédiées.
     */
    private static List<HorrorEvent> buildAll() {
        List<HorrorEvent> all = new ArrayList<>(List.of(
                new FootstepMimicEvent(),
                new BreathBehindEvent(),
                new FakeCreeperEvent(),
                new TorchSputterEvent(),
                new DoorAnomalyEvent(),
                new DistantHostSightingEvent(),
                new SilenceEvent(),
                new SleepInterruptEvent(),
                // Phase 2 (SPEC2 §6)
                new LureDoorEvent(),
                new FakeSilhouetteEvent(),
                new DistantLightEvent(),
                new AnticlimaxEvent(),
                new BinocularsSightingEvent(),
                // 0.13.0 : perception individuelle, sans verrou de groupe.
                new FalseCompanionEvent(),
                // 0.6.0 : screamer au retournement (leurre de minage).
                new MiningBaitEvent(),
                // 0.12.0 : conséquences visuelles contextuelles et repli fiable.
                new RefugeIntrusionEvent(),
                new MimicIntrusionEvent(),
                // 0.15.0 : créature vanilla possédée, au défaut minuscule — ou parfaite.
                new FauxCreatureEvent(),
                // 0.16.0 : il rejoue ta journée — portes, trappes et coffres notés.
                new ActionEchoEvent(),
                // 0.17.0 : le bruit devient la matière première de l'horreur.
                new HeldBreathEvent(),
                new WrongFootfallEvent(),
                new SoundBaitEvent(),
                new ListenerPassageEvent(),
                new SecondPresenceEvent()
        ));
        // Phase 3 : événements nocturnes et souterrains.
        all.addAll(NightEvents.create());
        all.addAll(UndergroundEvents.create());
        // Phase 4 : événements de l'Observateur (watcher_stare, watcher_group).
        all.addAll(WatcherEvents.create());
        // 0.6.0 : événements personnels (echoed_path, absent_home, door_knocks,
        // binoculars_reveal, behind_other).
        all.addAll(PersonalEvents.create());
        return List.copyOf(all);
    }

    private HorrorEvents() {
    }

    /**
     * Enregistre les hooks Fabric de la Phase 2 ; appelée une fois à l'initialisation
     * du mod. Actuellement : {@code UseBlockCallback} notifiant
     * {@code HorrorDirector.noteChestOpened} à chaque clic-droit d'un joueur sur un coffre
     * (déclencheur retardé {@code chest_scare}, SPEC2 §6). Le hook ne consomme jamais
     * l'interaction ({@code PASS}) : l'ouverture vanilla du coffre est préservée. Couvre
     * les coffres simples, piégés et toutes les variantes de cuivre
     * ({@code CopperChestBlock extends ChestBlock}).
     */
    public static void register() {
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                var block = level.getBlockState(hitResult.getBlockPos()).getBlock();
                long gameTime = level.getGameTime();
                if (block instanceof ChestBlock) {
                    HorrorDirector.noteChestOpened(serverPlayer, hitResult.getBlockPos());
                    ActionEchoLog.note(serverPlayer.getUUID(),
                            ActionEchoLog.Action.CHEST, gameTime);
                } else if (block instanceof DoorBlock) {
                    // 0.16.0 : chaque porte manipulée nourrit l'écho d'actions.
                    ActionEchoLog.note(serverPlayer.getUUID(),
                            ActionEchoLog.Action.DOOR, gameTime);
                } else if (block instanceof TrapDoorBlock) {
                    ActionEchoLog.note(serverPlayer.getUUID(),
                            ActionEchoLog.Action.TRAPDOOR, gameTime);
                }
            }
            return InteractionResult.PASS;
        });
    }

    /**
     * Retourne l'événement portant cet id, ou {@code null} si inconnu (SPEC §4.6).
     */
    public static HorrorEvent byId(String id) {
        for (HorrorEvent event : ALL) {
            if (event.id().equals(id)) {
                return event;
            }
        }
        return null;
    }
}

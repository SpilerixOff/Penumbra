package fr.penumbra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.penumbra.badge.PenumbraBadges;
import fr.penumbra.command.PenumbraCommands;
import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.content.NarrativeItems;
import fr.penumbra.director.DelayedTaskScheduler;
import fr.penumbra.director.HorrorDirector;
import fr.penumbra.director.sequence.LinkedSceneManager;
import fr.penumbra.entity.absent.AbsentEntities;
import fr.penumbra.entity.absent.AbsentManager;
import fr.penumbra.entity.absent.KnownIdentities;
import fr.penumbra.entity.contrefait.ContrefaitEntities;
import fr.penumbra.entity.contrefait.ContrefaitManager;
import fr.penumbra.entity.fauxmob.FauxMobManager;
import fr.penumbra.entity.listener.ListenerEntities;
import fr.penumbra.entity.listener.ListenerManager;
import fr.penumbra.entity.originel.OriginelEntities;
import fr.penumbra.entity.originel.HauntedTotem;
import fr.penumbra.entity.originel.OriginelManager;
import fr.penumbra.entity.projectionist.ProjectionistEntities;
import fr.penumbra.entity.rampant.RampantEntities;
import fr.penumbra.entity.rampant.RampantManager;
import fr.penumbra.entity.scuttler.ScuttlerEntities;
import fr.penumbra.entity.scuttler.ScuttlerManager;
import fr.penumbra.entity.watcher.WatcherEntities;
import fr.penumbra.event.HorrorEvents;
import fr.penumbra.journal.JournalItems;
import fr.penumbra.journal.JournalManager;
import fr.penumbra.journal.JournalNetworking;
import fr.penumbra.journal.PyreManager;
import fr.penumbra.event.personal.PersonalMoments;
import fr.penumbra.event.underground.PickaxeEchoEvent;
import fr.penumbra.event.underground.UndergroundEvents;
import fr.penumbra.event.underground.UndergroundRestorationTracker;
import fr.penumbra.network.PenumbraC2S;
import fr.penumbra.network.PenumbraNetworking;
import fr.penumbra.network.PhotoNetworking;
import fr.penumbra.photo.PhotoContent;
import fr.penumbra.perception.IndividualPerceptionManager;
import fr.penumbra.prop.FoundObjects;
import fr.penumbra.prop.PropEntities;
import fr.penumbra.registry.PenumbraEntities;
import fr.penumbra.registry.PenumbraItems;
import fr.penumbra.registry.PenumbraParticles;
import fr.penumbra.registry.PenumbraSounds;
import fr.penumbra.refuge.RefugeContaminationManager;
import fr.penumbra.story.ConfrontationManager;
import fr.penumbra.story.StoryManager;
import fr.penumbra.story.StoryOnboarding;
import fr.penumbra.structure.PenumbraStructures;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;

/**
 * Point d'entrée principal du mod Penumbra : configuration, registres,
 * payloads réseau, commandes et boucles de tick du directeur d'horreur.
 */
public class PenumbraMod implements ModInitializer {

    public static final String MOD_ID = "penumbra";
    public static final Logger LOGGER = LoggerFactory.getLogger("Penumbra");

    /**
     * Construit un identifiant dans l'espace de nom du mod.
     * En 26.1 (mappings officiels Mojang), la classe historique {@code ResourceLocation}
     * s'appelle {@link Identifier} — vérifié dans les sources 26.1.2.
     */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        // Ordre imposé par SPEC §4.1.
        PenumbraConfig.load();
        PenumbraItems.register();
        PhotoContent.register();
        PenumbraParticles.register();
        PenumbraSounds.register();
        PenumbraEntities.register();
        // ——— Phase 4 : Observateur + badges (registres, avant les événements) ———
        WatcherEntities.register();
        PenumbraBadges.register();
        // ——— 0.5.0 : l'Originel (registre, avant les événements) ———
        OriginelEntities.register();
        HauntedTotem.register();
        // ——— 0.6.0 : le Contrefait (registre, avant les événements) ———
        ContrefaitEntities.register();
        // ——— 0.7.0/0.8.0 : trio principal puis Cavaleuse ———
        ProjectionistEntities.register();
        RampantEntities.register();
        AbsentEntities.register();
        ScuttlerEntities.register();
        // ——— 0.17.0 : L'Écouteur (registre, avant les événements) ———
        ListenerEntities.register();
        // ——— 0.18.0 : le Relevé et ses pages arrachées ———
        JournalItems.register();
        // ——— 0.19.0 : accessoires de décor porteurs d'un maillage 3D ———
        PropEntities.register();
        PenumbraNetworking.registerPayloads();
        PenumbraC2S.registerPayloads();
        PenumbraC2S.registerServerReceivers();
        PhotoNetworking.registerPayloads();
        PhotoNetworking.registerServerReceivers();
        JournalNetworking.registerPayloads();
        PenumbraCommands.register();
        HorrorEvents.register();
        // 0.17.0 : hook de bruit de L'Écouteur (blocs cassés).
        ListenerManager.register();
        FauxMobManager.init();
        ServerTickEvents.END_SERVER_TICK.register(FauxMobManager::onServerTick);
        ServerLifecycleEvents.SERVER_STOPPING.register(FauxMobManager::onServerStopping);
        ServerTickEvents.END_SERVER_TICK.register(ConfrontationManager::onServerTick);
        ServerTickEvents.END_SERVER_TICK.register(HorrorDirector::onServerTick);
        ServerTickEvents.END_SERVER_TICK.register(LinkedSceneManager::onServerTick);
        ServerTickEvents.END_SERVER_TICK.register(DelayedTaskScheduler::onServerTick);
        ServerTickEvents.END_SERVER_TICK.register(IndividualPerceptionManager::onServerTick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            HorrorDirector.onServerStopped(server);
            LinkedSceneManager.onServerStopped(server);
            DelayedTaskScheduler.onServerStopped(server);
            IndividualPerceptionManager.onServerStopped(server);
            PenumbraC2S.clearAll();
            PenumbraNetworking.onServerStopped();
            PhotoNetworking.clearAll();
            OriginelManager.onServerStopped(server);
            ContrefaitManager.onServerStopped(server);
            RampantManager.onServerStopped(server);
            AbsentManager.onServerStopped(server);
            ScuttlerManager.onServerStopped(server);
            PyreManager.forget();
            FoundObjects.onServerStopped(server);
            ListenerManager.onServerStopped(server);
            PersonalMoments.onServerStopped(server);
            PenumbraStructures.onServerStopped(server);
            UndergroundRestorationTracker.onServerStopped(server);
            PickaxeEchoEvent.onServerStopped(server);
            FauxMobManager.onServerStopped(server);
            ConfrontationManager.onServerStopped(server);
        });
        // ——— Phase 3 : histoire, structures, objets narratifs, événements souterrains ———
        StoryManager.register();
        PenumbraStructures.register();
        NarrativeItems.register();
        UndergroundEvents.register();
        ServerTickEvents.END_SERVER_TICK.register(PenumbraStructures::onServerTick);
        ServerTickEvents.END_SERVER_TICK.register(UndergroundRestorationTracker::onServerTick);
        ServerTickEvents.END_SERVER_TICK.register(RefugeContaminationManager::onServerTick);
        // ——— 0.5.0 : Originel (hors économie de tension) + purge anti-fuite ———
        ServerTickEvents.END_SERVER_TICK.register(OriginelManager::onServerTick);
        // ——— 0.6.0 : moments personnels (one-shots, coffre retrié) + Contrefait ———
        ServerTickEvents.END_SERVER_TICK.register(PersonalMoments::onServerTick);
        ServerTickEvents.END_SERVER_TICK.register(ContrefaitManager::onServerTick);
        ServerTickEvents.END_SERVER_TICK.register(RampantManager::onServerTick);
        ServerTickEvents.END_SERVER_TICK.register(AbsentManager::onServerTick);
        ServerTickEvents.END_SERVER_TICK.register(ScuttlerManager::onServerTick);
        // ——— 0.17.0 : planification de L'Écouteur ———
        ServerTickEvents.END_SERVER_TICK.register(ListenerManager::onServerTick);
        // ——— 0.18.0 : remplissage du carnet et panache du Bûcher ———
        ServerTickEvents.END_SERVER_TICK.register(JournalManager::onServerTick);
        ServerTickEvents.END_SERVER_TICK.register(PyreManager::onServerTick);
        // 0.19.0 : le mobilier qui n'a rien à faire là.
        ServerTickEvents.END_SERVER_TICK.register(FoundObjects::onServerTick);
        // Chaque arrivée alimente le registre d'identités que porte L'Absent :
        // sur un monde qui a déjà vu passer du monde, il apparaîtra sous un nom
        // familier plutôt que sous celui du témoin (0.19.0).
        ServerPlayConnectionEvents.JOIN.register(
                (handler, sender, server) -> KnownIdentities.remember(handler.player));
        StoryOnboarding.register();
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            HorrorDirector.onPlayerDisconnect(handler.player.getUUID());
            LinkedSceneManager.onPlayerDisconnect(server, handler.player.getUUID());
            IndividualPerceptionManager.onPlayerDisconnect(server, handler.player.getUUID());
            OriginelManager.onPlayerDisconnect(handler.player.getUUID());
            PersonalMoments.onPlayerDisconnect(handler.player.getUUID());
            ConfrontationManager.onPlayerDisconnect(handler.player.getUUID());
            ContrefaitManager.onPlayerDisconnect(handler.player.getUUID());
            RampantManager.onPlayerDisconnect(handler.player.getUUID());
            AbsentManager.onPlayerDisconnect(handler.player.getUUID());
            ScuttlerManager.onPlayerDisconnect(handler.player.getUUID());
            FoundObjects.onPlayerDisconnect(handler.player.getUUID());
            ListenerManager.onPlayerDisconnect(handler.player.getUUID());
            PenumbraNetworking.clear(handler.player.getUUID());
            PhotoNetworking.clear(handler.player.getUUID());
            PickaxeEchoEvent.onPlayerDisconnect(handler.player.getUUID());
        });
        LOGGER.info("Penumbra initialisé — le directeur d'horreur est en marche.");
    }
}

package fr.penumbra;

import fr.penumbra.client.light.DynamicLights;
import fr.penumbra.client.config.ClientComfortScreen;
import fr.penumbra.client.PenumbraClientNet;
import fr.penumbra.client.ambient.AmbientFearManager;
import fr.penumbra.client.ambient.PerceptionOverlayRenderer;
import fr.penumbra.client.ambient.TensionVeilRenderer;
import fr.penumbra.client.binoculars.BinocularsManager;
import fr.penumbra.client.entity.AbsentClient;
import fr.penumbra.client.entity.ContrefaitClient;
import fr.penumbra.client.entity.OriginelClient;
import fr.penumbra.client.entity.ProjectionistClient;
import fr.penumbra.client.entity.WatcherClient;
import fr.penumbra.client.entity.listener.ListenerClient;
import fr.penumbra.client.journal.JournalClient;
import fr.penumbra.client.mesh.MeshPropRenderer;
import fr.penumbra.client.entity.rampant.RampantClient;
import fr.penumbra.client.entity.scuttler.ScuttlerClient;
import fr.penumbra.client.jumpscare.JumpscareManager;
import fr.penumbra.client.particle.AshMoteParticle;
import fr.penumbra.client.photo.PhotoClientManager;
import fr.penumbra.client.photo.PhotoFrameRenderer;
import fr.penumbra.client.photo.PhotoItemRendering;
import fr.penumbra.client.render.HostEntityModel;
import fr.penumbra.client.render.HostEntityRenderer;
import fr.penumbra.registry.PenumbraEntities;
import fr.penumbra.registry.PenumbraParticles;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

/**
 * Point d'entrée client de Penumbra : renderer et modèle de l'Hôte,
 * particule de cendre, récepteurs réseau S2C et gestionnaire de jumpscares.
 */
public class PenumbraClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientComfortScreen.register();
        PhotoItemRendering.register();
        // Ordre imposé par SPEC §4.2.
        EntityRendererRegistry.register(PenumbraEntities.HOST, HostEntityRenderer::new);
        EntityRendererRegistry.register(PenumbraEntities.PHOTO_FRAME, PhotoFrameRenderer::new);
        ModelLayerRegistry.registerModelLayer(HostEntityModel.LAYER, HostEntityModel::createBodyLayer);
        // Phase 4 : renderer et modèle de l'Observateur (même pattern que l'Hôte).
        WatcherClient.register();
        // 0.5.0 : renderer et modèle de l'Originel (même pattern).
        OriginelClient.register();
        // 0.6.0 : renderer et modèle du Contrefait (même pattern).
        ContrefaitClient.register();
        // 0.7.0/0.8.0 : trio principal puis Cavaleuse et animations articulées.
        ProjectionistClient.register();
        RampantClient.register();
        AbsentClient.register();
        ScuttlerClient.register();
        // 0.17.0 : L'Écouteur (même pattern autonome).
        ListenerClient.register();
        // 0.19.0 : accessoires porteurs d'un maillage .obj.
        EntityRendererRegistry.register(
                fr.penumbra.prop.PropEntities.MESH_PROP, MeshPropRenderer::new);
        ParticleProviderRegistry.getInstance().register(PenumbraParticles.ASH_MOTE, AshMoteParticle.Factory::new);
        PenumbraClientNet.register();
        // 0.18.0 : ouverture du Relevé sur paquet serveur.
        JournalClient.register();
        PhotoClientManager.register();
        ClientTickEvents.END_CLIENT_TICK.register(JumpscareManager::tick);
        // 0.19.0 : la torche tenue en main éclaire, et vacille sous la tension.
        ClientTickEvents.END_CLIENT_TICK.register(DynamicLights::onClientTick);
        // Phase 2 (SPEC2 §8) : jumelles — touche de zoom + tick client.
        KeyMappingHelper.registerKeyMapping(BinocularsManager.ZOOM_KEY);
        ClientTickEvents.END_CLIENT_TICK.register(BinocularsManager::tick);
        // 0.5.0 : couche ambiante (nappe sonore, voile périphérique, dérive de FOV).
        AmbientFearManager.register();
        TensionVeilRenderer.register();
        // 0.17.0 : grain, lignes de balayage et rémanence périphérique.
        // Le tick de la couche est déjà porté par AmbientFearManager.
        PerceptionOverlayRenderer.register();
    }
}

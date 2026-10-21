package fr.penumbra.client.entity.listener;

import fr.penumbra.entity.listener.ListenerEntities;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

/** Hook client autonome de L'Écouteur : renderer et couche du modèle. */
@Environment(EnvType.CLIENT)
public final class ListenerClient {

    private ListenerClient() {
    }

    /** À appeler depuis {@code PenumbraClient.onInitializeClient()}. */
    public static void register() {
        EntityRendererRegistry.register(ListenerEntities.LISTENER, ListenerRenderer::new);
        ModelLayerRegistry.registerModelLayer(
                ListenerModel.LAYER, ListenerModel::createBodyLayer);
    }
}

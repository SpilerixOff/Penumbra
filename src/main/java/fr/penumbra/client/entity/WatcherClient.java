package fr.penumbra.client.entity;

import fr.penumbra.entity.watcher.WatcherEntities;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

/**
 * Enregistrement client de l'Observateur : renderer + couche de modèle.
 * <p>
 * Reproduit EXACTEMENT le pattern d'enregistrement 26.1.2 de {@code PenumbraClient}
 * pour l'Hôte ({@code EntityRendererRegistry.register} puis
 * {@code ModelLayerRegistry.registerModelLayer}). L'intégrateur appelle
 * {@link #register()} depuis {@code PenumbraClient.onInitializeClient}.
 * </p>
 */
@Environment(EnvType.CLIENT)
public final class WatcherClient {

    private WatcherClient() {
    }

    /** Enregistre le renderer de l'Observateur puis la couche de son modèle. */
    public static void register() {
        EntityRendererRegistry.register(WatcherEntities.WATCHER, WatcherRenderer::new);
        ModelLayerRegistry.registerModelLayer(WatcherModel.LAYER, WatcherModel::createBodyLayer);
    }
}

package fr.penumbra.client.entity;

import fr.penumbra.entity.originel.OriginelEntities;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

/**
 * Enregistrement client de l'Originel (0.5.0) : renderer + couche de modèle.
 * Reproduit EXACTEMENT le pattern 26.1.2 de {@code WatcherClient}.
 * L'intégrateur appelle {@link #register()} depuis
 * {@code PenumbraClient.onInitializeClient}.
 */
@Environment(EnvType.CLIENT)
public final class OriginelClient {

    private OriginelClient() {
    }

    /** Enregistre le renderer de l'Originel puis la couche de son modèle. */
    public static void register() {
        EntityRendererRegistry.register(OriginelEntities.ORIGINEL, OriginelRenderer::new);
        ModelLayerRegistry.registerModelLayer(OriginelModel.LAYER, OriginelModel::createBodyLayer);
    }
}

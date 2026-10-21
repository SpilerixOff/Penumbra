package fr.penumbra.client.entity;

import fr.penumbra.entity.contrefait.ContrefaitEntities;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

/**
 * Enregistrement client du Contrefait (0.6.0) : renderer + couche de modèle.
 * Reproduit EXACTEMENT le pattern 26.1.2 de {@code WatcherClient} /
 * {@code OriginelClient}. Appelé depuis {@code PenumbraClient.onInitializeClient}.
 */
@Environment(EnvType.CLIENT)
public final class ContrefaitClient {

    private ContrefaitClient() {
    }

    /** Enregistre le renderer du Contrefait puis la couche de son modèle. */
    public static void register() {
        EntityRendererRegistry.register(ContrefaitEntities.CONTREFAIT, ContrefaitRenderer::new);
        ModelLayerRegistry.registerModelLayer(ContrefaitModel.LAYER, ContrefaitModel::createBodyLayer);
    }
}

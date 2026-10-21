package fr.penumbra.client.entity.rampant;

import fr.penumbra.entity.rampant.RampantEntities;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

/** Enregistrement client autonome du renderer et de sa couche de modèle. */
@Environment(EnvType.CLIENT)
public final class RampantClient {

    private RampantClient() {
    }

    /** À appeler depuis {@code PenumbraClient.onInitializeClient()}. */
    public static void register() {
        EntityRendererRegistry.register(RampantEntities.RAMPANT, RampantRenderer::new);
        ModelLayerRegistry.registerModelLayer(RampantModel.LAYER, RampantModel::createBodyLayer);
    }
}

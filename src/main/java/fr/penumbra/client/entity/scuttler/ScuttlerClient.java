package fr.penumbra.client.entity.scuttler;

import fr.penumbra.entity.scuttler.ScuttlerEntities;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

/** Hook client autonome : renderer et couche du modèle à huit pattes. */
@Environment(EnvType.CLIENT)
public final class ScuttlerClient {

    private ScuttlerClient() {
    }

    /** À appeler depuis {@code PenumbraClient.onInitializeClient()}. */
    public static void register() {
        EntityRendererRegistry.register(ScuttlerEntities.SCUTTLER, ScuttlerRenderer::new);
        ModelLayerRegistry.registerModelLayer(
                ScuttlerModel.LAYER, ScuttlerModel::createBodyLayer);
    }
}

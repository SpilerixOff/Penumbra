package fr.penumbra.client.entity;

import fr.penumbra.entity.absent.AbsentEntities;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

/** Enregistrement client du renderer et du modèle de L'Absent. */
@Environment(EnvType.CLIENT)
public final class AbsentClient {

    private AbsentClient() {
    }

    public static void register() {
        EntityRendererRegistry.register(AbsentEntities.ABSENT, AbsentRenderer::new);
        EntityRendererRegistry.register(
                AbsentEntities.FALSE_COMPANION, AbsentRenderer::new);
        ModelLayerRegistry.registerModelLayer(AbsentModel.LAYER, AbsentModel::createBodyLayer);
    }
}

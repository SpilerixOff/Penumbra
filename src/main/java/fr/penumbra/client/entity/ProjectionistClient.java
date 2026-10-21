package fr.penumbra.client.entity;

import fr.penumbra.entity.projectionist.ProjectionistEntities;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

/** Hook client autonome : renderer et couche de modèle du Projectionniste. */
@Environment(EnvType.CLIENT)
public final class ProjectionistClient {

    private ProjectionistClient() {
    }

    public static void register() {
        EntityRendererRegistry.register(
                ProjectionistEntities.PROJECTIONIST, ProjectionistRenderer::new);
        ModelLayerRegistry.registerModelLayer(
                ProjectionistModel.LAYER, ProjectionistModel::createBodyLayer);
    }
}

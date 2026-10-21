package fr.penumbra.client.mesh;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.penumbra.PenumbraMod;
import fr.penumbra.prop.MeshPropEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;

/**
 * Rend l'accessoire de décor et son maillage (0.19.0).
 *
 * <p>Chemin complet du système : le serveur pose une {@code MeshPropEntity} en
 * lui donnant un nom de maillage, ce nom arrive ici par synchronisation,
 * {@link MeshLibrary} lit le fichier {@code .obj} correspondant à la première
 * image qui en a besoin, et {@link MeshRenderer} l'envoie au GPU. Aucun mixin
 * nulle part.</p>
 *
 * <p>La texture suit une convention simple : si l'entité n'en nomme aucune, on
 * prend {@code textures/entity/prop/<maillage>.png}. Un accessoire ne demande
 * donc qu'un fichier {@code .obj} et un {@code .png} portant le même nom.</p>
 */
@Environment(EnvType.CLIENT)
public final class MeshPropRenderer
        extends EntityRenderer<MeshPropEntity, MeshPropRenderState> {

    public MeshPropRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.4f;
    }

    @Override
    public MeshPropRenderState createRenderState() {
        return new MeshPropRenderState();
    }

    @Override
    public void extractRenderState(MeshPropEntity entity, MeshPropRenderState state,
                                   float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.mesh = entity.meshName();
        state.texture = entity.textureName();
        state.scale = entity.scale();
        state.yaw = entity.getYRot();
    }

    @Override
    public void submit(MeshPropRenderState state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState cameraState) {
        if (state.mesh == null || state.mesh.isEmpty()) {
            return;
        }
        ObjMesh mesh = MeshLibrary.get(state.mesh);
        if (mesh.isEmpty()) {
            return;
        }

        poseStack.pushPose();
        // L'entité est posée au sol : le maillage garde son propre repère, on
        // ne le recentre pas. C'est à l'auteur du .obj de poser son objet sur
        // le plan Y = 0, comme il le ferait dans Blender.
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.yaw));
        poseStack.scale(state.scale, state.scale, state.scale);
        MeshRenderer.submit(collector, poseStack, mesh, texture(state), state.lightCoords);
        poseStack.popPose();

        super.submit(state, poseStack, collector, cameraState);
    }

    private static Identifier texture(MeshPropRenderState state) {
        String name = state.texture == null || state.texture.isEmpty()
                ? state.mesh : state.texture;
        return PenumbraMod.id("textures/entity/prop/" + name + ".png");
    }
}

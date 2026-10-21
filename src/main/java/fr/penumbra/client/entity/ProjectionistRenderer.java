package fr.penumbra.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.penumbra.PenumbraMod;
import fr.penumbra.client.animation.MonsterAnimationMath;
import fr.penumbra.entity.projectionist.ProjectionistEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

/** Renderer opaque de la manifestation physique du Projectionniste. */
@Environment(EnvType.CLIENT)
public final class ProjectionistRenderer extends MobRenderer<
        ProjectionistEntity, ProjectionistRenderState, ProjectionistModel> {

    private static final Identifier TEXTURE =
            PenumbraMod.id("textures/entity/projectionist.png");

    public ProjectionistRenderer(EntityRendererProvider.Context context) {
        super(context, new ProjectionistModel(context.bakeLayer(ProjectionistModel.LAYER)), 0.38f);
    }

    @Override
    public Identifier getTextureLocation(ProjectionistRenderState state) {
        return TEXTURE;
    }

    @Override
    public ProjectionistRenderState createRenderState() {
        return new ProjectionistRenderState();
    }

    @Override
    public void extractRenderState(ProjectionistEntity entity,
                                   ProjectionistRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.rushing = entity.isRushing();
        state.rushTicks = entity.getVisualRushTicks(partialTick);
    }

    @Override
    protected void scale(ProjectionistRenderState state, PoseStack poseStack) {
        super.scale(state, poseStack);
        if (!state.rushing) {
            return;
        }
        float collapse = MonsterAnimationMath.proximity(
                state.distanceToCameraSq, 3.25f, 5.5f);
        poseStack.scale(1.0f + collapse * 0.12f,
                1.0f - collapse * 0.18f,
                1.0f + collapse * 0.12f);
    }
}

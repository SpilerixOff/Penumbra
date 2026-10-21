package fr.penumbra.client.entity.rampant;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.penumbra.PenumbraMod;
import fr.penumbra.client.animation.MonsterAnimationMath;
import fr.penumbra.entity.rampant.RampantEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

/** Renderer 3D du Rampant, y compris son orientation sur plafond et parois. */
@Environment(EnvType.CLIENT)
public final class RampantRenderer
        extends MobRenderer<RampantEntity, RampantRenderState, RampantModel> {

    private static final Identifier TEXTURE =
            PenumbraMod.id("textures/entity/rampant.png");

    public RampantRenderer(EntityRendererProvider.Context context) {
        super(context, new RampantModel(context.bakeLayer(RampantModel.LAYER)), 0.35f);
    }

    @Override
    public Identifier getTextureLocation(RampantRenderState state) {
        return TEXTURE;
    }

    @Override
    public RampantRenderState createRenderState() {
        return new RampantRenderState();
    }

    @Override
    public void extractRenderState(
            RampantEntity entity, RampantRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.phase = entity.getRampantPhase();
        state.attachment = entity.getAttachment();
        state.phaseTicks = entity.getVisualPhaseTicks(partialTick);
    }

    @Override
    protected void setupRotations(
            RampantRenderState state, PoseStack poseStack, float bodyRotation, float scale) {
        super.setupRotations(state, poseStack, bodyRotation, scale);
        boolean attached = state.phase == RampantEntity.PHASE_CLINGING
                || state.phase == RampantEntity.PHASE_CLIMBING;
        boolean dropping = state.phase == RampantEntity.PHASE_DROPPING;
        if (!attached && !dropping) {
            return;
        }
        float attachmentBlend = attached ? 1.0f
                : 1.0f - MonsterAnimationMath.smootherstep(state.phaseTicks / 8.0f);
        switch (state.attachment) {
            case RampantEntity.ATTACHMENT_CEILING ->
                    poseStack.mulPose(Axis.ZP.rotationDegrees(180.0f * attachmentBlend));
            case RampantEntity.ATTACHMENT_NORTH ->
                    poseStack.mulPose(Axis.XP.rotationDegrees(90.0f * attachmentBlend));
            case RampantEntity.ATTACHMENT_SOUTH ->
                    poseStack.mulPose(Axis.XP.rotationDegrees(-90.0f * attachmentBlend));
            case RampantEntity.ATTACHMENT_WEST ->
                    poseStack.mulPose(Axis.ZP.rotationDegrees(-90.0f * attachmentBlend));
            case RampantEntity.ATTACHMENT_EAST ->
                    poseStack.mulPose(Axis.ZP.rotationDegrees(90.0f * attachmentBlend));
            default -> {
                // Invocation technique sans attache : pose au sol normale.
            }
        }
    }

    @Override
    protected float getShadowRadius(RampantRenderState state) {
        if (state.phase == RampantEntity.PHASE_DROPPING) {
            return super.getShadowRadius(state)
                    * MonsterAnimationMath.smootherstep(state.phaseTicks / 8.0f);
        }
        return state.phase == RampantEntity.PHASE_CLINGING
                || state.phase == RampantEntity.PHASE_CLIMBING
                ? 0.0f : super.getShadowRadius(state);
    }
}

package fr.penumbra.client.entity;

import fr.penumbra.PenumbraMod;
import com.mojang.blaze3d.vertex.PoseStack;
import fr.penumbra.entity.contrefait.ContrefaitEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

/**
 * Renderer du Contrefait (0.6.0) : texture
 * {@code penumbra:textures/entity/contrefait.png}, ombre 0.5f — lui A une
 * ombre, contrairement à l'Originel : la copie est presque réussie, et c'est
 * ce « presque » qui la trahit. Même pattern 26.1.2 que les autres entités.
 */
@Environment(EnvType.CLIENT)
public class ContrefaitRenderer extends MobRenderer<ContrefaitEntity, ContrefaitRenderState, ContrefaitModel> {

    private static final Identifier TEXTURE = PenumbraMod.id("textures/entity/contrefait.png");

    public ContrefaitRenderer(EntityRendererProvider.Context context) {
        super(context, new ContrefaitModel(context.bakeLayer(ContrefaitModel.LAYER)), 0.5f);
    }

    @Override
    public Identifier getTextureLocation(ContrefaitRenderState state) {
        return TEXTURE;
    }

    @Override
    public ContrefaitRenderState createRenderState() {
        return new ContrefaitRenderState();
    }

    @Override
    protected void scale(ContrefaitRenderState state, PoseStack poseStack) {
        super.scale(state, poseStack);
        // Authored feet at zero, while vanilla LivingEntityRenderer uses 24 pixels.
        poseStack.translate(0.0f, 1.5f, 0.0f);
    }

    @Override
    public void extractRenderState(ContrefaitEntity entity, ContrefaitRenderState state,
                                   float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.lunging = entity.isLunging();
        state.lungeProgress = entity.getLungeProgress(partialTick);
        state.observed = entity.isObserved();
        state.windingUp = entity.isWindingUp();
        state.windupProgress = entity.getWindupProgress(partialTick);
        state.recovering = entity.isRecovering();
        state.recoveryProgress = entity.getRecoveryProgress(partialTick);
        state.recoveryPose = entity.getRecoveryPose();
    }
}

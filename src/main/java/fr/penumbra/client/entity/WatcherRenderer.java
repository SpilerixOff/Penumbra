package fr.penumbra.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.penumbra.PenumbraMod;
import fr.penumbra.entity.watcher.WatcherEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

/**
 * Renderer de l'Observateur : texture {@code penumbra:textures/entity/watcher.png},
 * ombre 0.45f. Reproduit le pattern 26.1.2 du renderer de l'Hôte ({@code MobRenderer}
 * + {@code LivingEntityRenderState} + {@code getTextureLocation(state)}).
 */
@Environment(EnvType.CLIENT)
public class WatcherRenderer extends MobRenderer<WatcherEntity, WatcherRenderState, WatcherModel> {

    private static final Identifier TEXTURE = PenumbraMod.id("textures/entity/watcher.png");

    public WatcherRenderer(EntityRendererProvider.Context context) {
        super(context, new WatcherModel(context.bakeLayer(WatcherModel.LAYER)), 0.45f);
    }

    @Override
    public Identifier getTextureLocation(WatcherRenderState state) {
        return TEXTURE;
    }

    @Override
    public WatcherRenderState createRenderState() {
        return new WatcherRenderState();
    }

    @Override
    protected void scale(WatcherRenderState state, PoseStack poseStack) {
        // Feet are authored at y=0, rather than vanilla's y=24px plane.
        super.scale(state, poseStack);
        poseStack.translate(0.0f, 1.5f, 0.0f);
    }

    @Override
    public void extractRenderState(WatcherEntity entity, WatcherRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.lunging = entity.isLunging();
        state.lungeProgress = entity.getLungeProgress(partialTick);
        state.windingUp = entity.isWindingUp();
        state.windupProgress = entity.getWindupProgress(partialTick);
        state.recovering = entity.isRecovering();
        state.recoveryProgress = entity.getRecoveryProgress(partialTick);
    }
}

package fr.penumbra.client.entity.scuttler;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.penumbra.PenumbraMod;
import fr.penumbra.entity.scuttler.ScuttlerEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

/** Renderer du petit modèle arachnéen de la Cavaleuse. */
@Environment(EnvType.CLIENT)
public final class ScuttlerRenderer
        extends MobRenderer<ScuttlerEntity, ScuttlerRenderState, ScuttlerModel> {

    private static final Identifier TEXTURE =
            PenumbraMod.id("textures/entity/scuttler.png");

    /**
     * Plancher d'éclairage de la Cavaleuse (0.19.0).
     *
     * <p>Elle n'apparaît que là où la lumière de bloc est basse — c'est la
     * condition de la scène. Sans ce plancher, une créature sombre rendue à
     * l'éclairage 0 dans une grotte noire est <b>littéralement invisible</b> :
     * la scène se jouait, le son se jouait, et le joueur ne voyait rien. Les
     * conditions d'apparition et les conditions de visibilité se
     * contredisaient.</p>
     *
     * <p>Cinq, c'est le niveau où une silhouette se détache du fond sans
     * qu'on lise ses détails — exactement ce qu'on veut d'une chose qui
     * traverse le champ de vision en une seconde et demie. Vanilla fait la
     * même chose, en plus franc, pour les blazes et les cubes de magma.</p>
     */
    private static final int MIN_BLOCK_LIGHT = 5;

    public ScuttlerRenderer(EntityRendererProvider.Context context) {
        super(context, new ScuttlerModel(context.bakeLayer(ScuttlerModel.LAYER)), 0.24f);
    }

    @Override
    protected int getBlockLightLevel(ScuttlerEntity entity, BlockPos pos) {
        return Math.max(super.getBlockLightLevel(entity, pos), MIN_BLOCK_LIGHT);
    }

    @Override
    public Identifier getTextureLocation(ScuttlerRenderState state) {
        return TEXTURE;
    }

    @Override
    public ScuttlerRenderState createRenderState() {
        return new ScuttlerRenderState();
    }

    @Override
    protected void scale(ScuttlerRenderState state, PoseStack poseStack) {
        super.scale(state, poseStack);
        // This tiny rig's feet are at y=0, not vanilla's y=24px plane.
        poseStack.translate(0.0f, 1.5f, 0.0f);
    }

    @Override
    public void extractRenderState(
            ScuttlerEntity entity, ScuttlerRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.scene = entity.getScene();
        state.feinting = entity.isFeinting();
        state.feintTicks = entity.getFeintTicks(partialTick);
    }
}

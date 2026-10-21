package fr.penumbra.client.entity;

import fr.penumbra.PenumbraMod;
import fr.penumbra.entity.originel.OriginelEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

/**
 * Renderer de l'Originel (0.5.0) : texture {@code penumbra:textures/entity/originel.png},
 * ombre 0.0f — l'Originel N'A PAS d'ombre au sol. Personne ne le remarque
 * consciemment à 80 blocs ; tout le monde le sent. Même pattern 26.1.2 que
 * l'Hôte et l'Observateur ({@code MobRenderer} + {@code LivingEntityRenderState}).
 */
@Environment(EnvType.CLIENT)
public class OriginelRenderer extends MobRenderer<OriginelEntity, OriginelRenderState, OriginelModel> {

    private static final Identifier TEXTURE = PenumbraMod.id("textures/entity/originel.png");

    public OriginelRenderer(EntityRendererProvider.Context context) {
        super(context, new OriginelModel(context.bakeLayer(OriginelModel.LAYER)), 0.0f);
    }

    @Override
    public Identifier getTextureLocation(OriginelRenderState state) {
        return TEXTURE;
    }

    @Override
    public OriginelRenderState createRenderState() {
        return new OriginelRenderState();
    }

    @Override
    public void extractRenderState(OriginelEntity entity, OriginelRenderState state,
                                   float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.caveAnimation = entity.isCaveAnimation();
        state.lungeProgress = entity.getLungeProgress();
    }
}

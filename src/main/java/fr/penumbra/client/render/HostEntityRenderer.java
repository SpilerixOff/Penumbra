package fr.penumbra.client.render;

import fr.penumbra.PenumbraMod;
import fr.penumbra.entity.HostEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

/**
 * Renderer de l'Hôte (SPEC §4.9) : texture {@code penumbra:textures/entity/host.png}, ombre 0.4f.
 * Mappings officiels : {@code MobRenderer} (nom Yarn historique : {@code MobEntityRenderer}).
 */
@Environment(EnvType.CLIENT)
public class HostEntityRenderer extends MobRenderer<HostEntity, HostRenderState, HostEntityModel> {

    private static final Identifier TEXTURE = PenumbraMod.id("textures/entity/host.png");

    public HostEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new HostEntityModel(context.bakeLayer(HostEntityModel.LAYER)), 0.4f);
    }

    @Override
    public Identifier getTextureLocation(HostRenderState state) {
        return TEXTURE;
    }

    @Override
    public HostRenderState createRenderState() {
        return new HostRenderState();
    }

    @Override
    public void extractRenderState(HostEntity entity, HostRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.behavior = entity.getBehavior().ordinal();
        state.lunging = entity.isLunging();
        state.lungeProgress = entity.getLungeProgress(partialTick);
    }
}

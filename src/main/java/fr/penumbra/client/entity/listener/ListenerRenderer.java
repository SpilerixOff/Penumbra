package fr.penumbra.client.entity.listener;

import fr.penumbra.PenumbraMod;
import fr.penumbra.entity.listener.ListenerEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

/** Renderer de L'Écouteur : ombre serrée sous une silhouette de 2,9 blocs. */
@Environment(EnvType.CLIENT)
public final class ListenerRenderer
        extends MobRenderer<ListenerEntity, ListenerRenderState, ListenerModel> {

    private static final Identifier TEXTURE =
            PenumbraMod.id("textures/entity/listener.png");

    public ListenerRenderer(EntityRendererProvider.Context context) {
        super(context, new ListenerModel(context.bakeLayer(ListenerModel.LAYER)), 0.34f);
    }

    @Override
    public Identifier getTextureLocation(ListenerRenderState state) {
        return TEXTURE;
    }

    @Override
    public ListenerRenderState createRenderState() {
        return new ListenerRenderState();
    }

    @Override
    public void extractRenderState(
            ListenerEntity entity, ListenerRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.behavior = entity.getBehavior().ordinal();
        state.hearingYaw = entity.getHearingYaw();
    }
}

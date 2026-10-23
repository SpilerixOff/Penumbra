package fr.penumbra.client.entity.scuttler;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** État visuel minimal de la Cavaleuse. */
@Environment(EnvType.CLIENT)
public final class ScuttlerRenderState extends LivingEntityRenderState {
    public int scene;
    public boolean feinting;
    public float feintTicks;
}

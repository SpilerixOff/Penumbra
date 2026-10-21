package fr.penumbra.client.entity.listener;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** État visuel de L'Écouteur : sa posture et l'angle du bruit qu'elle poursuit. */
@Environment(EnvType.CLIENT)
public final class ListenerRenderState extends LivingEntityRenderState {
    public int behavior;
    public float hearingYaw;
}

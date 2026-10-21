package fr.penumbra.client.entity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** État visuel minimal de la brève avancée du Projectionniste. */
@Environment(EnvType.CLIENT)
public final class ProjectionistRenderState extends LivingEntityRenderState {
    public boolean rushing;
    public float rushTicks;
}

package fr.penumbra.client.entity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** État client minimal nécessaire aux poses spéciales de l'Originel. */
@Environment(EnvType.CLIENT)
public final class OriginelRenderState extends LivingEntityRenderState {
    public boolean caveAnimation;
    public float lungeProgress;
}

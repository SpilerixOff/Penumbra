package fr.penumbra.client.entity.rampant;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** État client minimal synchronisé du Rampant. */
@Environment(EnvType.CLIENT)
public final class RampantRenderState extends LivingEntityRenderState {
    public int phase;
    public int attachment;
    public float phaseTicks;
    public boolean ambushWarning;
    public float warningProgress;
    public float dropRelease;
}

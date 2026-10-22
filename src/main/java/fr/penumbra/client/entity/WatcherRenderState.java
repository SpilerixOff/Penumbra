package fr.penumbra.client.entity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** Donnees client minimales propres a la lunge 3D de l'Observateur. */
@Environment(EnvType.CLIENT)
public final class WatcherRenderState extends LivingEntityRenderState {
    public boolean lunging;
    public float lungeProgress;
    public boolean windingUp;
    public float windupProgress;
    public boolean recovering;
    public float recoveryProgress;
}

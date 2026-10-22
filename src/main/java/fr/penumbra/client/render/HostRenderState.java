package fr.penumbra.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** Donnees client minimales de la charge 3D synchronisee de l'Hote. */
@Environment(EnvType.CLIENT)
public final class HostRenderState extends LivingEntityRenderState {
    public int behavior;
    public boolean lunging;
    public float lungeProgress;
    public boolean windingUp;
    public float windupProgress;
    public boolean recovering;
    public float recoveryProgress;
    public boolean grappling;
    public float grappleProgress;
}

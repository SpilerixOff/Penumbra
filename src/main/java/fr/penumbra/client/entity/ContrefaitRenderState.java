package fr.penumbra.client.entity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** Données client de la charge 3D synchronisée du Contrefait. */
@Environment(EnvType.CLIENT)
public final class ContrefaitRenderState extends LivingEntityRenderState {
    public boolean lunging;
    public float lungeProgress;
    public boolean observed;
    public boolean windingUp;
    public float windupProgress;
    public boolean recovering;
    public float recoveryProgress;
    public float recoveryPose;
}

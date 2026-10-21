package fr.penumbra.client.mesh;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

/** Ce que le renderer d'accessoire a besoin de savoir : rien de plus. */
@Environment(EnvType.CLIENT)
public final class MeshPropRenderState extends EntityRenderState {
    public String mesh = "";
    public String texture = "";
    public float scale = 1.0f;
    public float yaw;
}

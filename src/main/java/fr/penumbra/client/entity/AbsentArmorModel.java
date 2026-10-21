package fr.penumbra.client.entity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

/** Armure du faux joueur : géométrie vanilla, pose procédurale de L'Absent. */
@Environment(EnvType.CLIENT)
final class AbsentArmorModel extends HumanoidModel<AbsentRenderState> {

    private final AbsentModel parent;

    AbsentArmorModel(ModelPart root, AbsentModel parent) {
        super(root);
        this.parent = parent;
    }

    @Override
    public void setupAnim(AbsentRenderState state) {
        this.parent.setupAnim(state);
        this.parent.copyHumanoidPoseTo(this);
    }
}

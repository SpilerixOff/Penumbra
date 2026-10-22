package fr.penumbra.client.entity.scuttler;

import fr.penumbra.PenumbraMod;
import fr.penumbra.client.animation.ScuttlerAnimation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Mini-araignée complète : abdomen, céphalothorax et quatre paires de pattes,
 * chaque patte possédant une articulation proximale et une section distale.
 */
@Environment(EnvType.CLIENT)
public final class ScuttlerModel extends EntityModel<ScuttlerRenderState> {

    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(PenumbraMod.id("scuttler"), "main");

    private static final float[] RIGHT_BASE_YAW = {-0.78f, -0.34f, 0.22f, 0.66f};
    private static final float[] LEFT_BASE_YAW = {0.78f, 0.34f, -0.22f, -0.66f};

    private final ModelPart abdomen;
    private final ModelPart carapace;
    private final ModelPart thorax;
    private final ModelPart[] rightLegs = new ModelPart[4];
    private final ModelPart[] leftLegs = new ModelPart[4];
    private final ModelPart[] rightTips = new ModelPart[4];
    private final ModelPart[] leftTips = new ModelPart[4];

    public ScuttlerModel(ModelPart root) {
        super(root);
        carapace = root.getChild("carapace");
        abdomen = carapace.getChild("abdomen");
        thorax = carapace.getChild("thorax");
        for (int index = 0; index < 4; index++) {
            rightLegs[index] = carapace.getChild("right_leg_" + index);
            leftLegs[index] = carapace.getChild("left_leg_" + index);
            rightTips[index] = rightLegs[index].getChild("tip");
            leftTips[index] = leftLegs[index].getChild("tip");
        }
    }

    /**
     * UV 64×64 : abdomen (0,0), thorax (0,15), face (26,15),
     * segments proximaux réutilisés (0,27), distaux réutilisés (18,27).
     */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition carapace = root.addOrReplaceChild("carapace",
                CubeListBuilder.create(), PartPose.ZERO);

        carapace.addOrReplaceChild("abdomen",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-4.0f, -2.5f, -4.5f, 8.0f, 5.0f, 9.0f),
                PartPose.offset(0.0f, -3.2f, 3.2f));
        PartDefinition thorax = carapace.addOrReplaceChild("thorax",
                CubeListBuilder.create().texOffs(0, 15)
                        .addBox(-3.0f, -2.0f, -3.0f, 6.0f, 4.0f, 6.0f),
                PartPose.offset(0.0f, -3.0f, -3.2f));
        thorax.addOrReplaceChild("face",
                CubeListBuilder.create().texOffs(26, 15)
                        .addBox(-2.0f, -1.0f, -3.0f, 4.0f, 2.0f, 3.0f),
                PartPose.offset(0.0f, 0.15f, -2.8f));

        float[] pivotsZ = {-5.0f, -2.1f, 0.9f, 3.8f};
        for (int index = 0; index < 4; index++) {
            PartDefinition right = carapace.addOrReplaceChild("right_leg_" + index,
                    CubeListBuilder.create().texOffs(0, 27)
                            .addBox(-6.0f, -1.0f, -1.0f, 6.0f, 2.0f, 2.0f),
                    PartPose.offset(-2.7f, -2.8f, pivotsZ[index]));
            right.addOrReplaceChild("tip",
                    CubeListBuilder.create().texOffs(18, 27)
                            .addBox(-5.0f, -0.5f, -0.5f, 5.0f, 1.0f, 1.0f),
                    PartPose.offset(-6.0f, 0.0f, 0.0f));

            PartDefinition left = carapace.addOrReplaceChild("left_leg_" + index,
                    CubeListBuilder.create().texOffs(0, 27)
                            .addBox(0.0f, -1.0f, -1.0f, 6.0f, 2.0f, 2.0f),
                    PartPose.offset(2.7f, -2.8f, pivotsZ[index]));
            left.addOrReplaceChild("tip",
                    CubeListBuilder.create().texOffs(18, 27)
                            .addBox(0.0f, -0.5f, -0.5f, 5.0f, 1.0f, 1.0f),
                    PartPose.offset(6.0f, 0.0f, 0.0f));
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(ScuttlerRenderState state) {
        super.setupAnim(state);
        float cycle = state.walkAnimationPos * 2.6f;
        float movement = Mth.clamp(state.walkAnimationSpeed * 4.0f, 0.0f, 1.0f);
        if (state.feinting) movement = 0.0f;

        abdomen.y = -3.2f + Mth.abs(Mth.sin(cycle * 0.5f)) * 0.42f * movement;
        abdomen.xRot = Mth.sin(cycle * 0.25f) * 0.035f;
        thorax.y = -3.0f + Mth.abs(Mth.cos(cycle * 0.5f)) * 0.20f * movement;
        thorax.yRot = state.yRot * Mth.DEG_TO_RAD * 0.18f;

        for (int index = 0; index < 4; index++) {
            float rightPhase = ScuttlerAnimation.legPhase(state.walkAnimationPos, index, true);
            float leftPhase = ScuttlerAnimation.legPhase(state.walkAnimationPos, index, false);
            animateLeg(rightLegs[index], rightTips[index], RIGHT_BASE_YAW[index],
                    rightPhase, movement, true);
            animateLeg(leftLegs[index], leftTips[index], LEFT_BASE_YAW[index],
                    leftPhase, movement, false);
        }
        if (state.feinting) applyFeint(state.feintTicks);
    }

    private static void animateLeg(ModelPart leg, ModelPart tip, float baseYaw,
                                   float phase, float speed, boolean right) {
        float stride = Mth.sin(phase) * 0.43f * speed;
        float lift = Math.max(0.0f, Mth.cos(phase)) * 0.22f * speed;
        leg.yRot = baseYaw + stride;
        leg.xRot = Mth.sin(phase + Mth.HALF_PI) * 0.08f * speed;
        leg.zRot = (right ? 0.12f : -0.12f) + (right ? lift : -lift);
        tip.yRot = -stride * 0.24f;
        tip.zRot = (right ? -0.895f : 0.895f)
                + (right ? lift * 0.72f : -lift * 0.72f);
    }

    private void applyFeint(float ticks) {
        ScuttlerAnimation.Feint pose = ScuttlerAnimation.feint(ticks);
        carapace.y = pose.crouch() * 0.35f - pose.rise() * 0.50f;
        carapace.xRot = -pose.rise() * 0.10f + pose.slap() * 0.045f;
        abdomen.xRot = pose.crouch() * 0.12f;
        thorax.xRot = -pose.rise() * 0.22f;
        thorax.yRot = 0.0f;
        for (int index = 0; index < 2; index++) {
            float lift = pose.rise() * (index == 0 ? 1.0f : 0.62f);
            rightLegs[index].zRot += 0.85f * lift;
            leftLegs[index].zRot -= 0.85f * lift;
            rightLegs[index].yRot -= 0.36f * lift;
            leftLegs[index].yRot += 0.36f * lift;
            rightTips[index].zRot += 0.62f * lift;
            leftTips[index].zRot -= 0.62f * lift;
        }
    }
}

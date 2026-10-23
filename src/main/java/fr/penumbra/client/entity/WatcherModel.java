package fr.penumbra.client.entity;

import fr.penumbra.PenumbraMod;
import fr.penumbra.client.animation.MonsterAnimationMath;
import fr.penumbra.client.animation.WatcherAttackAnimation;
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

/** Grounded statue with a connected spine and an asymmetric, single-arm strike. */
@Environment(EnvType.CLIENT)
public final class WatcherModel extends EntityModel<WatcherRenderState> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(PenumbraMod.id("watcher"), "main");
    private final ModelPart hips, torso, head, rightArm, rightForearm, leftArm, leftForearm;
    private final ModelPart rightLeg, rightShin, leftLeg, leftShin;

    public WatcherModel(ModelPart root) {
        super(root);
        hips = root.getChild("hips");
        torso = hips.getChild("torso");
        head = torso.getChild("head");
        rightArm = torso.getChild("right_arm");
        rightForearm = rightArm.getChild("forearm");
        leftArm = torso.getChild("left_arm");
        leftForearm = leftArm.getChild("forearm");
        rightLeg = hips.getChild("right_leg");
        rightShin = rightLeg.getChild("shin");
        leftLeg = hips.getChild("left_leg");
        leftShin = leftLeg.getChild("shin");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition hips = mesh.getRoot().addOrReplaceChild("hips",
                CubeListBuilder.create(), PartPose.offset(0, -16, 0));
        PartDefinition torso = hips.addOrReplaceChild("torso",
                CubeListBuilder.create().texOffs(16, 16).addBox(-4, -14, -2, 8, 14, 4), PartPose.ZERO);
        torso.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4, -8, -4, 8, 8, 8),
                PartPose.offset(0, -14, 0));
        arm(torso, "right_arm", -5.5f, 40);
        arm(torso, "left_arm", 5.5f, 52);
        leg(hips, "right_leg", -1.7f, 0);
        leg(hips, "left_leg", 1.7f, 12);
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static void arm(PartDefinition torso, String name, float x, int u) {
        PartDefinition upper = torso.addOrReplaceChild(name,
                CubeListBuilder.create().texOffs(u, 16).addBox(-1.5f, 0, -1.5f, 3, 6, 3),
                PartPose.offset(x, -13, 0));
        upper.addOrReplaceChild("forearm",
                CubeListBuilder.create().texOffs(u, 22).addBox(-1.5f, 0, -1.5f, 3, 6, 3),
                PartPose.offset(0, 6, 0));
    }

    private static void leg(PartDefinition hips, String name, float x, int u) {
        PartDefinition thigh = hips.addOrReplaceChild(name,
                CubeListBuilder.create().texOffs(u, 34).addBox(-1.5f, 0, -1.5f, 3, 8, 3),
                PartPose.offset(x, 0, 0));
        thigh.addOrReplaceChild("shin",
                CubeListBuilder.create().texOffs(u, 42).addBox(-1.5f, 0, -1.5f, 3, 8, 3),
                PartPose.offset(0, 8, 0));
    }

    @Override
    public void setupAnim(WatcherRenderState state) {
        super.setupAnim(state);
        WatcherAttackAnimation.Pose pose;
        if (state.recovering) pose = WatcherAttackAnimation.recovery(state.recoveryProgress, state.recoveryPose);
        else if (state.windingUp) pose = WatcherAttackAnimation.windup(state.windupProgress);
        else if (state.lunging) pose = WatcherAttackAnimation.strike(state.lungeProgress);
        else {
            float proximity = MonsterAnimationMath.proximity(state.distanceToCameraSq, 6, 18);
            pose = WatcherAttackAnimation.windup(proximity * .14f);
        }
        hips.y = WatcherAttackAnimation.hipY(pose.bend());
        rightLeg.xRot = leftLeg.xRot = -pose.bend();
        rightShin.xRot = leftShin.xRot = 2 * pose.bend();
        torso.xRot = pose.lean();
        torso.yRot = pose.twist();
        head.yRot = state.yRot * Mth.DEG_TO_RAD - pose.twist();
        head.xRot = state.xRot * Mth.DEG_TO_RAD - pose.lean() * .85f;
        head.zRot = pose.headTilt();
        rightArm.xRot = pose.rightArm();
        rightArm.yRot = -pose.twist() * .80f;
        rightArm.zRot = .05f + pose.twist() * .36f;
        rightForearm.xRot = pose.rightElbow();
        leftArm.xRot = pose.leftArm();
        leftArm.zRot = -.05f;
        leftForearm.xRot = pose.leftElbow();
    }
}

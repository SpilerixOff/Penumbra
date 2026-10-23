package fr.penumbra.client.entity;

import fr.penumbra.PenumbraMod;
import fr.penumbra.client.animation.ContrefaitAnimation;
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

/** Player-sized articulated puppet; upper joints remain attached to the torso. */
@Environment(EnvType.CLIENT)
public final class ContrefaitModel extends EntityModel<ContrefaitRenderState> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(PenumbraMod.id("contrefait"), "main");
    private final ModelPart hips, body, head, rightArm, leftArm, rightLeg, leftLeg;

    public ContrefaitModel(ModelPart root) {
        super(root);
        this.hips = root.getChild("hips");
        this.body = hips.getChild("body");
        this.head = body.getChild("head");
        this.rightArm = body.getChild("right_arm");
        this.leftArm = body.getChild("left_arm");
        this.rightLeg = hips.getChild("right_leg");
        this.leftLeg = hips.getChild("left_leg");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition hips = mesh.getRoot().addOrReplaceChild("hips",
                CubeListBuilder.create(), PartPose.offset(0, -12, 0));
        PartDefinition body = hips.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(16, 16).addBox(-4, -12, -2, 8, 12, 4), PartPose.ZERO);
        body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4, -8, -4, 8, 8, 8), PartPose.offset(0, -12, 0));
        addLimb(body, "right_arm", -6, -12, 40, 16);
        addLimb(body, "left_arm", 6, -12, 40, 32);
        addLimb(hips, "right_leg", -2, 0, 0, 16);
        addLimb(hips, "left_leg", 2, 0, 0, 32);
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static void addLimb(PartDefinition parent, String name,
                                float x, float y, int u, int v) {
        PartDefinition upper = parent.addOrReplaceChild(name, CubeListBuilder.create()
                .texOffs(u, v).addBox(-2, 0, -2, 4, 6, 4), PartPose.offset(x, y, 0));
        upper.addOrReplaceChild("joint", CubeListBuilder.create().texOffs(u, v + 6)
                .addBox(-2, 0, -2, 4, 6, 4), PartPose.offset(0, 6, 0));
    }

    @Override
    public void setupAnim(ContrefaitRenderState state) {
        super.setupAnim(state);
        if (state.recovering) {
            apply(ContrefaitAnimation.recovery(state.recoveryProgress, state.recoveryPose));
        } else if (state.windingUp) {
            apply(ContrefaitAnimation.windup(state.windupProgress));
        } else if (state.lunging) {
            apply(ContrefaitAnimation.strike(state.lungeProgress));
        } else {
            float speed = state.observed ? 0 : Math.min(state.walkAnimationSpeed, 1);
            float stride = state.walkAnimationPos * .62f;
            this.rightLeg.xRot = Mth.cos(stride) * speed * .65f;
            this.leftLeg.xRot = Mth.cos(stride + .5f) * speed * .50f;
            this.rightLeg.getChild("joint").xRot = Math.max(0, -Mth.sin(stride)) * speed * .6f;
            this.leftLeg.getChild("joint").xRot = Math.max(0, -Mth.sin(stride + .5f)) * speed * .5f;
            this.body.yRot = (Mth.cos(stride) - Mth.cos(stride + .5f)) * speed * .08f;
            this.head.yRot = state.yRot * Mth.DEG_TO_RAD - this.body.yRot;
            this.head.xRot = state.xRot * Mth.DEG_TO_RAD;
            this.head.zRot = .10f;
            this.rightArm.xRot = -.025f;
            this.rightArm.getChild("joint").xRot = -.12f;
            this.leftArm.getChild("joint").xRot = -.04f;
        }
    }

    private void apply(ContrefaitAnimation.Pose pose) {
        this.hips.y = pose.hipsY();
        this.body.xRot = pose.torsoPitch();
        this.body.yRot = pose.torsoYaw();
        this.body.zRot = pose.torsoRoll();
        this.head.xRot = pose.headPitch() - pose.torsoPitch();
        this.head.yRot = pose.headYaw();
        this.head.zRot = pose.headRoll() - pose.torsoRoll();
        limb(this.rightArm, pose.rightArm());
        limb(this.leftArm, pose.leftArm());
        limb(this.rightLeg, pose.rightLeg());
        limb(this.leftLeg, pose.leftLeg());
    }

    private static void limb(ModelPart part, ContrefaitAnimation.Limb pose) {
        part.xRot = pose.pitch();
        part.yRot = pose.yaw();
        part.zRot = pose.roll();
        part.getChild("joint").xRot = pose.joint();
    }
}

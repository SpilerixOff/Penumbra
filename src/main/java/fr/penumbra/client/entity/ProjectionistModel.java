package fr.penumbra.client.entity;

import fr.penumbra.PenumbraMod;
import fr.penumbra.client.animation.MonsterAnimationMath;
import fr.penumbra.client.animation.ProjectionistAnimation;
import fr.penumbra.entity.projectionist.ProjectionistTiming;
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

/** Silhouette mecanique avec objectif, bobines et pans de manteau mobiles. */
@Environment(EnvType.CLIENT)
public final class ProjectionistModel extends EntityModel<ProjectionistRenderState> {

    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(PenumbraMod.id("projectionist"), "main");

    private final ModelPart head;
    private final ModelPart lens;
    private final ModelPart leftReel;
    private final ModelPart rightReel;
    private final ModelPart body;
    private final ModelPart rightTail;
    private final ModelPart leftTail;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public ProjectionistModel(ModelPart root) {
        super(root);
        ModelPart hips = root.getChild("hips");
        this.body = hips.getChild("body");
        this.head = this.body.getChild("head");
        this.lens = this.head.getChild("lens");
        this.leftReel = this.head.getChild("left_reel");
        this.rightReel = this.head.getChild("right_reel");
        this.rightTail = this.body.getChild("right_tail");
        this.leftTail = this.body.getChild("left_tail");
        this.rightArm = this.body.getChild("right_arm");
        this.leftArm = this.body.getChild("left_arm");
        this.rightLeg = hips.getChild("right_leg");
        this.leftLeg = hips.getChild("left_leg");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition hips = root.addOrReplaceChild("hips", CubeListBuilder.create(),
                PartPose.offset(0.0f, -14.0f, 0.0f));
        PartDefinition body = hips.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 18)
                        .addBox(-5.0f, -18.0f, -2.5f, 10.0f, 18.0f, 5.0f),
                PartPose.ZERO);

        PartDefinition head = body.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-5.0f, -7.0f, -5.0f, 10.0f, 7.0f, 10.0f),
                PartPose.offset(0.0f, -18.0f, 0.0f));
        head.addOrReplaceChild("lens",
                CubeListBuilder.create().texOffs(40, 0)
                        .addBox(-2.0f, -2.0f, -3.0f, 4.0f, 4.0f, 3.0f),
                PartPose.offset(0.0f, -3.5f, -5.0f));
        // Les bobines reutilisent l'atlas de la tete : silhouette enrichie sans PNG.
        head.addOrReplaceChild("right_reel",
                CubeListBuilder.create().texOffs(30, 0)
                        .addBox(-2.0f, -2.5f, -2.5f, 2.0f, 5.0f, 5.0f),
                PartPose.offset(-5.0f, -3.4f, 1.0f));
        head.addOrReplaceChild("left_reel",
                CubeListBuilder.create().texOffs(30, 0)
                        .addBox(0.0f, -2.5f, -2.5f, 2.0f, 5.0f, 5.0f),
                PartPose.offset(5.0f, -3.4f, 1.0f));

        body.addOrReplaceChild("right_tail",
                CubeListBuilder.create().texOffs(0, 29)
                        .addBox(-4.5f, 0.0f, -0.75f, 4.0f, 11.0f, 1.0f),
                PartPose.offset(0.0f, -1.0f, 1.7f));
        body.addOrReplaceChild("left_tail",
                CubeListBuilder.create().texOffs(10, 29)
                        .addBox(0.5f, 0.0f, -0.75f, 4.0f, 11.0f, 1.0f),
                PartPose.offset(0.0f, -1.0f, 1.7f));

        body.addOrReplaceChild("right_arm",
                CubeListBuilder.create().texOffs(32, 18)
                        .addBox(-1.5f, 0.0f, -1.5f, 3.0f, 18.0f, 3.0f),
                PartPose.offset(-6.4f, -17.0f, 0.0f));
        body.addOrReplaceChild("left_arm",
                CubeListBuilder.create().texOffs(44, 18)
                        .addBox(-1.5f, 0.0f, -1.5f, 3.0f, 18.0f, 3.0f),
                PartPose.offset(6.4f, -17.0f, 0.0f));
        hips.addOrReplaceChild("right_leg",
                CubeListBuilder.create().texOffs(0, 42)
                        .addBox(-1.5f, 0.0f, -1.5f, 3.0f, 14.0f, 3.0f),
                PartPose.offset(-1.8f, 0.0f, 0.0f));
        hips.addOrReplaceChild("left_leg",
                CubeListBuilder.create().texOffs(12, 42)
                        .addBox(-1.5f, 0.0f, -1.5f, 3.0f, 14.0f, 3.0f),
                PartPose.offset(1.8f, 0.0f, 0.0f));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(ProjectionistRenderState state) {
        super.setupAnim(state);
        float time = state.ageInTicks;
        float breath = MonsterAnimationMath.organicBreath(time, 0.025f);

        this.head.yRot = state.yRot * Mth.DEG_TO_RAD;
        this.head.xRot = state.xRot * Mth.DEG_TO_RAD;
        this.head.zRot = Mth.sin(time * 0.035f) * 0.025f;
        this.body.xRot = breath * 0.008f;
        this.rightArm.zRot = 0.025f;
        this.leftArm.zRot = -0.025f;
        this.lens.z = -5.0f;
        this.lens.zRot = Mth.sin(time * 0.17f) * 0.035f;
        this.rightReel.xRot = Mth.sin(time * 0.025f) * 0.05f;
        this.leftReel.xRot = -Mth.sin(time * 0.021f) * 0.05f;
        this.rightTail.xRot = 0.035f + breath * 0.015f;
        this.leftTail.xRot = 0.055f - breath * 0.012f;

        // Tiny mechanism movement while waiting; the major gesture is server-timed.
        float click = MonsterAnimationMath.twitchPulse(time, 79.0f, 3.0f, 43);
        this.lens.z -= click * 0.35f;
        if (state.actionStarted) {
            applyAction(state);
        }
    }

    private void applyAction(ProjectionistRenderState state) {
        ProjectionistAnimation.Pose pose = ProjectionistAnimation.pose(state.actionTicks);
        this.body.xRot = pose.torsoPitch();
        this.body.zRot = pose.torsoRoll();
        this.head.xRot = state.xRot * Mth.DEG_TO_RAD - pose.torsoPitch() + pose.headPitch();
        this.head.zRot = -pose.torsoRoll();
        this.lens.z = -5.0f - pose.lensExtension();
        this.lens.zRot = -0.18f * pose.lensExtension();
        float reels = ProjectionistAnimation.reelRotation(state.actionTicks);
        float idleTime = state.ageInTicks - state.actionTicks;
        this.rightReel.xRot = Mth.sin(idleTime * 0.025f) * 0.05f + reels;
        this.leftReel.xRot = -Mth.sin(idleTime * 0.021f) * 0.05f - reels * 0.83f;
        this.rightArm.xRot = pose.rightArmPitch();
        this.leftArm.xRot = pose.leftArmPitch();
        this.rightArm.zRot = pose.rightArmRoll();
        this.leftArm.zRot = pose.leftArmRoll();
        // One long mechanical step, with a delayed second leg, not a running loop.
        float advance = MonsterAnimationMath.clamp01((state.actionTicks - ProjectionistTiming.ARM_TICKS)
                / ProjectionistTiming.ADVANCE_TICKS);
        float step = Mth.sin(advance * Mth.PI);
        this.rightLeg.xRot = pose.stride() * step;
        this.leftLeg.xRot = -pose.stride() * step * 0.70f;
        this.rightTail.xRot = -0.65f * pose.coatOpen();
        this.leftTail.xRot = -0.36f * pose.coatOpen();
        this.rightTail.zRot = 0.35f * pose.coatOpen();
        this.leftTail.zRot = -0.20f * pose.coatOpen();
    }
}

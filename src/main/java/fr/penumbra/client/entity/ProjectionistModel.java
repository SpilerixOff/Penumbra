package fr.penumbra.client.entity;

import fr.penumbra.PenumbraMod;
import fr.penumbra.client.animation.MonsterAnimationMath;
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
        this.head = root.getChild("head");
        this.lens = this.head.getChild("lens");
        this.leftReel = this.head.getChild("left_reel");
        this.rightReel = this.head.getChild("right_reel");
        this.body = root.getChild("body");
        this.rightTail = this.body.getChild("right_tail");
        this.leftTail = this.body.getChild("left_tail");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition head = root.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-5.0f, -7.0f, -5.0f, 10.0f, 7.0f, 10.0f),
                PartPose.offset(0.0f, -32.0f, 0.0f));
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

        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 18)
                        .addBox(-5.0f, 0.0f, -2.5f, 10.0f, 18.0f, 5.0f),
                PartPose.offset(0.0f, -32.0f, 0.0f));
        body.addOrReplaceChild("right_tail",
                CubeListBuilder.create().texOffs(0, 29)
                        .addBox(-4.5f, 0.0f, -0.75f, 4.0f, 11.0f, 1.0f),
                PartPose.offset(0.0f, 17.0f, 1.7f));
        body.addOrReplaceChild("left_tail",
                CubeListBuilder.create().texOffs(10, 29)
                        .addBox(0.5f, 0.0f, -0.75f, 4.0f, 11.0f, 1.0f),
                PartPose.offset(0.0f, 17.0f, 1.7f));

        root.addOrReplaceChild("right_arm",
                CubeListBuilder.create().texOffs(32, 18)
                        .addBox(-1.5f, 0.0f, -1.5f, 3.0f, 18.0f, 3.0f),
                PartPose.offset(-6.4f, -31.0f, 0.0f));
        root.addOrReplaceChild("left_arm",
                CubeListBuilder.create().texOffs(44, 18)
                        .addBox(-1.5f, 0.0f, -1.5f, 3.0f, 18.0f, 3.0f),
                PartPose.offset(6.4f, -31.0f, 0.0f));
        root.addOrReplaceChild("right_leg",
                CubeListBuilder.create().texOffs(0, 42)
                        .addBox(-1.5f, 0.0f, -1.5f, 3.0f, 14.0f, 3.0f),
                PartPose.offset(-1.8f, -14.0f, 0.0f));
        root.addOrReplaceChild("left_leg",
                CubeListBuilder.create().texOffs(12, 42)
                        .addBox(-1.5f, 0.0f, -1.5f, 3.0f, 14.0f, 3.0f),
                PartPose.offset(1.8f, -14.0f, 0.0f));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(ProjectionistRenderState state) {
        super.setupAnim(state);
        float time = state.ageInTicks;
        float proximity = MonsterAnimationMath.proximity(
                state.distanceToCameraSq, 3.25f, 15.0f);
        float breath = MonsterAnimationMath.organicBreath(time, 0.025f);

        this.head.yRot = state.yRot * Mth.DEG_TO_RAD;
        this.head.xRot = state.xRot * Mth.DEG_TO_RAD;
        this.head.zRot = Mth.sin(time * 0.035f) * 0.025f;
        this.body.xRot = breath * 0.008f;
        this.body.y = -32.0f - breath * 0.10f;
        this.rightArm.zRot = 0.025f;
        this.leftArm.zRot = -0.025f;
        this.lens.z = -5.0f - proximity * 1.15f;
        this.lens.zRot = Mth.sin(time * 0.17f) * 0.035f;
        this.rightReel.zRot = time * 0.018f;
        this.leftReel.zRot = -time * 0.013f;
        this.rightTail.xRot = 0.035f + breath * 0.015f;
        this.leftTail.xRot = 0.055f - breath * 0.012f;

        // Declic irregulier : objectif et bobines ne sautent pas ensemble.
        if (((int) time % 53) < 2) {
            this.head.zRot += 0.075f;
            this.lens.z -= 0.45f;
        }
        if (((int) (time + 17.0f) % 79) < 2) {
            this.rightReel.zRot += 0.18f;
            this.leftReel.zRot -= 0.11f;
        }

        if (state.rushing) {
            applyRush(state, time, proximity);
        }
    }

    private void applyRush(ProjectionistRenderState state, float time, float proximity) {
        float progress = MonsterAnimationMath.clamp01(state.rushTicks / 18.0f);
        MonsterAnimationMath.AttackPose pose =
                MonsterAnimationMath.attackPose(progress);
        float preparation = pose.anticipation();
        float strike = pose.strike();
        float stride = Mth.sin(time * 1.35f);
        float collapse = proximity * MonsterAnimationMath.smoothstep(
                (progress - 0.45f) / 0.55f);

        this.body.xRot = -0.10f * preparation + 0.34f * strike;
        this.body.zRot = Mth.sin(time * 0.51f) * 0.035f * strike;
        this.head.xRot -= 0.12f * preparation + 0.20f * strike;
        this.head.zRot += Mth.sin(time * 2.8f) * 0.045f * strike;
        this.lens.z = -5.0f - 0.55f * preparation - 1.55f * strike;
        this.rightLeg.xRot = stride * 0.82f * strike;
        this.leftLeg.xRot = -stride * 0.82f * strike;
        this.rightArm.xRot = 0.22f * preparation - 0.72f * strike;
        this.leftArm.xRot = 0.18f * preparation - 0.72f * strike;
        this.rightArm.zRot = 0.04f + 0.18f * strike;
        this.leftArm.zRot = -0.04f - 0.18f * strike;
        this.rightTail.xRot = -0.12f - Mth.abs(stride) * 0.20f * strike
                + collapse * 0.42f;
        this.leftTail.xRot = -0.18f - Mth.abs(stride) * 0.16f * strike
                + collapse * 0.36f;
        this.rightTail.zRot = stride * 0.055f * strike;
        this.leftTail.zRot = -stride * 0.055f * strike;
    }
}

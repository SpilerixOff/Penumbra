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

/** L'Originel conserve exactement les proportions d'un joueur. */
@Environment(EnvType.CLIENT)
public final class OriginelModel extends EntityModel<OriginelRenderState> {

    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(PenumbraMod.id("originel"), "main");

    private static final float TWITCH_PERIOD_TICKS = 240.0f;
    private static final float TWITCH_DURATION_TICKS = 3.0f;

    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public OriginelModel(ModelPart root) {
        super(root);
        this.head = root.getChild("head");
        this.body = root.getChild("body");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-4.0f, -8.0f, -4.0f, 8.0f, 8.0f, 8.0f),
                PartPose.offset(0.0f, -24.0f, 0.0f));
        root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(16, 16)
                        .addBox(-4.0f, 0.0f, -2.0f, 8.0f, 12.0f, 4.0f),
                PartPose.offset(0.0f, -24.0f, 0.0f));
        root.addOrReplaceChild("right_arm",
                CubeListBuilder.create().texOffs(40, 16)
                        .addBox(-4.0f, 0.0f, -2.0f, 4.0f, 12.0f, 4.0f),
                PartPose.offset(-4.0f, -24.0f, 0.0f));
        root.addOrReplaceChild("left_arm",
                CubeListBuilder.create().texOffs(40, 32)
                        .addBox(0.0f, 0.0f, -2.0f, 4.0f, 12.0f, 4.0f),
                PartPose.offset(4.0f, -24.0f, 0.0f));
        root.addOrReplaceChild("right_leg",
                CubeListBuilder.create().texOffs(0, 16)
                        .addBox(-2.0f, 0.0f, -2.0f, 4.0f, 12.0f, 4.0f),
                PartPose.offset(-2.0f, -12.0f, 0.0f));
        root.addOrReplaceChild("left_leg",
                CubeListBuilder.create().texOffs(0, 32)
                        .addBox(-2.0f, 0.0f, -2.0f, 4.0f, 12.0f, 4.0f),
                PartPose.offset(2.0f, -12.0f, 0.0f));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(OriginelRenderState state) {
        super.setupAnim(state);
        float time = state.ageInTicks;
        if (!state.caveAnimation) {
            applyDistantPose(time);
            return;
        }

        float breath = MonsterAnimationMath.organicBreath(time, 0.033f);
        float proximity = MonsterAnimationMath.proximity(
                state.distanceToCameraSq, 1.0f, 16.0f);
        this.head.yRot = state.yRot * Mth.DEG_TO_RAD;
        this.head.xRot = state.xRot * Mth.DEG_TO_RAD - proximity * 0.035f;
        this.head.zRot = Mth.sin(time * 0.012f) * 0.018f;
        this.body.xRot = breath * 0.012f - proximity * 0.025f;
        this.body.y = -24.0f - breath * 0.12f;
        this.body.zRot = Mth.sin(time * 0.009f + 0.7f) * 0.009f;
        this.rightArm.zRot = 0.035f + proximity * 0.025f;
        this.leftArm.zRot = -0.035f - proximity * 0.025f;
        this.rightArm.yRot = -0.015f;
        this.leftArm.yRot = 0.015f;
        this.rightLeg.xRot = proximity * 0.025f;
        this.leftLeg.xRot = -proximity * 0.020f;

        if (state.lungeProgress > 0.0f) {
            applyLunge(state.lungeProgress, time);
        }
    }

    private void applyDistantPose(float time) {
        // Aucune respiration visible a distance. Le mouvement se produit en
        // trois images : torsion, sur-correction, retour parfait.
        float cycle = time % TWITCH_PERIOD_TICKS;
        if (cycle < TWITCH_DURATION_TICKS) {
            if (cycle < 1.0f) {
                this.head.yRot = 0.12f;
                this.head.zRot = 0.08f;
            } else if (cycle < 2.0f) {
                this.head.yRot = -0.045f;
                this.head.zRot = -0.025f;
            }
        }
    }

    private void applyLunge(float progress, float time) {
        MonsterAnimationMath.AttackPose pose =
                MonsterAnimationMath.attackPose(progress);
        float preparation = pose.anticipation();
        float strike = pose.strike();
        float impact = pose.impact();
        this.body.xRot = 0.20f * preparation - 0.76f * strike;
        this.body.y = -24.0f + 0.30f * preparation - 0.46f * strike;
        this.head.xRot += 0.22f * preparation - 0.25f * strike;
        this.head.zRot += Mth.sin(time * 4.6f) * 0.15f * impact;
        this.rightArm.xRot = 0.34f * preparation - 2.56f * strike;
        this.leftArm.xRot = 0.34f * preparation - 2.56f * strike;
        this.rightArm.yRot = -0.16f * strike;
        this.leftArm.yRot = 0.16f * strike;
        this.rightArm.zRot = 0.04f + 0.23f * strike;
        this.leftArm.zRot = -0.04f - 0.23f * strike;
        this.rightLeg.xRot = -0.26f * preparation + 0.34f * strike;
        this.leftLeg.xRot = 0.24f * preparation - 0.34f * strike;
    }
}

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

/**
 * Copie aux proportions exactes d'un joueur. L'etrangete vient entierement de
 * son squelette procedural : pas presque en phase, bassin en retard et bras
 * qui se plient seulement lors de la charge.
 */
@Environment(EnvType.CLIENT)
public final class ContrefaitModel extends EntityModel<ContrefaitRenderState> {

    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(PenumbraMod.id("contrefait"), "main");

    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public ContrefaitModel(ModelPart root) {
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
    public void setupAnim(ContrefaitRenderState state) {
        super.setupAnim(state);
        float time = state.ageInTicks;
        float walkPos = state.walkAnimationPos;
        float walkSpeed = Math.min(state.walkAnimationSpeed, 1.0f);
        float rightStride = Mth.cos(walkPos * 0.62f);
        float leftStride = Mth.cos(walkPos * 0.62f + 0.5f);
        float proximity = MonsterAnimationMath.proximity(
                state.distanceToCameraSq, 10.0f, 24.0f);
        float breath = MonsterAnimationMath.organicBreath(time, 0.027f);

        this.rightLeg.xRot = rightStride * walkSpeed;
        this.leftLeg.xRot = leftStride * 0.80f * walkSpeed;
        this.rightLeg.zRot = rightStride * 0.035f * walkSpeed;
        this.leftLeg.zRot = -leftStride * 0.025f * walkSpeed;
        this.body.yRot = (rightStride - leftStride) * 0.045f * walkSpeed;
        this.body.zRot = (rightStride + leftStride) * 0.028f * walkSpeed;
        this.body.xRot = breath * 0.009f;

        // Aucun balancement humain : les mains pendent, legerement tournees.
        this.rightArm.xRot = -0.02f;
        this.leftArm.xRot = 0.015f;
        this.rightArm.yRot = -0.025f;
        this.leftArm.yRot = 0.018f;
        this.rightArm.zRot = 0.03f;
        this.leftArm.zRot = -0.03f;

        this.head.yRot = state.yRot * Mth.DEG_TO_RAD - this.body.yRot * 0.55f;
        this.head.xRot = state.xRot * Mth.DEG_TO_RAD;
        this.head.zRot = 0.09f + Mth.sin(time * 0.011f) * 0.018f;

        // A l'arret (souvent parce qu'il est observe), la respiration s'eteint
        // et la silhouette se contracte avant sa disparition au contact.
        if (walkSpeed < 0.025f && !state.lunging) {
            this.body.xRot = -0.035f - proximity * 0.12f;
            this.body.zRot = proximity * 0.035f;
            this.head.xRot -= proximity * 0.10f;
            this.head.zRot += proximity * 0.08f;
            this.rightArm.xRot = -proximity * 0.16f;
            this.leftArm.xRot = -proximity * 0.11f;
            this.rightLeg.xRot = 0.06f * proximity;
            this.leftLeg.xRot = -0.045f * proximity;
        }

        if (state.lunging) {
            applyLunge(state.lungeProgress, time);
        }
    }

    private void applyLunge(float progress, float time) {
        MonsterAnimationMath.AttackPose pose =
                MonsterAnimationMath.attackPose(progress);
        float preparation = pose.anticipation();
        float strike = pose.strike();
        float impact = pose.impact();
        this.body.xRot = 0.16f * preparation - 0.48f * strike;
        this.body.zRot = -0.09f * preparation + 0.05f * strike;
        this.head.xRot += 0.14f * preparation - 0.32f * strike;
        this.head.yRot += 0.12f * preparation;
        this.head.zRot += Mth.sin(time * 3.4f)
                * (0.04f * strike + 0.14f * impact);
        this.rightArm.xRot = 0.24f * preparation - 1.78f * strike;
        this.leftArm.xRot = 0.34f * preparation - 1.50f * strike;
        this.rightArm.yRot = -0.18f * strike;
        this.leftArm.yRot = 0.13f * strike;
        this.rightArm.zRot = 0.06f + 0.20f * strike;
        this.leftArm.zRot = -0.04f - 0.27f * strike;
        this.rightLeg.xRot = -0.24f * preparation + 0.46f * strike;
        this.leftLeg.xRot = 0.20f * preparation - 0.39f * strike;
    }
}

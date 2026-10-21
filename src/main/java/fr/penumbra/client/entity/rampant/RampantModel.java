package fr.penumbra.client.entity.rampant;

import fr.penumbra.PenumbraMod;
import fr.penumbra.client.animation.MonsterAnimationMath;
import fr.penumbra.entity.rampant.RampantEntity;
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
 * Rampant 0.8.0 : quatre pattes réellement articulées, chacune composée d'un
 * segment proximal et d'un segment distal. Les huit segments réutilisent les
 * quatre anciennes zones de membres dans l'atlas 64×64 : aucun nouvel UV ni
 * changement de texture n'est requis.
 */
@Environment(EnvType.CLIENT)
public final class RampantModel extends EntityModel<RampantRenderState> {

    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(PenumbraMod.id("rampant"), "main");

    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart frontRightUpper;
    private final ModelPart frontRightLower;
    private final ModelPart frontLeftUpper;
    private final ModelPart frontLeftLower;
    private final ModelPart rearRightUpper;
    private final ModelPart rearRightLower;
    private final ModelPart rearLeftUpper;
    private final ModelPart rearLeftLower;
    private final ModelPart spineFront;
    private final ModelPart spineMiddle;
    private final ModelPart spineRear;

    public RampantModel(ModelPart root) {
        super(root);
        this.head = root.getChild("head");
        this.body = root.getChild("body");
        this.frontRightUpper = root.getChild("front_right_upper");
        this.frontRightLower = this.frontRightUpper.getChild("lower");
        this.frontLeftUpper = root.getChild("front_left_upper");
        this.frontLeftLower = this.frontLeftUpper.getChild("lower");
        this.rearRightUpper = root.getChild("rear_right_upper");
        this.rearRightLower = this.rearRightUpper.getChild("lower");
        this.rearLeftUpper = root.getChild("rear_left_upper");
        this.rearLeftLower = this.rearLeftUpper.getChild("lower");
        this.spineFront = root.getChild("spine_front");
        this.spineMiddle = root.getChild("spine_middle");
        this.spineRear = root.getChild("spine_rear");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-4.0f, -2.5f, -7.0f, 8.0f, 5.0f, 14.0f),
                PartPose.offset(0.0f, -4.5f, 0.0f));
        root.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 20)
                        .addBox(-4.0f, -3.5f, -6.0f, 8.0f, 7.0f, 7.0f),
                PartPose.offset(0.0f, -4.0f, -7.0f));

        PartDefinition frontRight = root.addOrReplaceChild("front_right_upper",
                CubeListBuilder.create().texOffs(32, 20)
                        .addBox(-1.5f, -1.5f, -6.0f, 3.0f, 3.0f, 6.0f),
                PartPose.offset(-4.0f, -3.2f, -2.5f));
        frontRight.addOrReplaceChild("lower",
                CubeListBuilder.create().texOffs(32, 20)
                        .addBox(-1.0f, -1.0f, -7.0f, 2.0f, 2.0f, 7.0f),
                PartPose.offset(0.0f, 0.0f, -6.0f));

        PartDefinition frontLeft = root.addOrReplaceChild("front_left_upper",
                CubeListBuilder.create().texOffs(32, 34)
                        .addBox(-1.5f, -1.5f, -6.0f, 3.0f, 3.0f, 6.0f),
                PartPose.offset(4.0f, -3.2f, -2.5f));
        frontLeft.addOrReplaceChild("lower",
                CubeListBuilder.create().texOffs(32, 34)
                        .addBox(-1.0f, -1.0f, -7.0f, 2.0f, 2.0f, 7.0f),
                PartPose.offset(0.0f, 0.0f, -6.0f));

        PartDefinition rearRight = root.addOrReplaceChild("rear_right_upper",
                CubeListBuilder.create().texOffs(0, 36)
                        .addBox(-1.5f, -1.5f, 0.0f, 3.0f, 3.0f, 6.0f),
                PartPose.offset(-4.0f, -3.2f, 2.5f));
        rearRight.addOrReplaceChild("lower",
                CubeListBuilder.create().texOffs(0, 36)
                        .addBox(-1.0f, -1.0f, 0.0f, 2.0f, 2.0f, 7.0f),
                PartPose.offset(0.0f, 0.0f, 6.0f));

        PartDefinition rearLeft = root.addOrReplaceChild("rear_left_upper",
                CubeListBuilder.create().texOffs(0, 50)
                        .addBox(-1.5f, -1.5f, 0.0f, 3.0f, 3.0f, 6.0f),
                PartPose.offset(4.0f, -3.2f, 2.5f));
        rearLeft.addOrReplaceChild("lower",
                CubeListBuilder.create().texOffs(0, 50)
                        .addBox(-1.0f, -1.0f, 0.0f, 2.0f, 2.0f, 7.0f),
                PartPose.offset(0.0f, 0.0f, 6.0f));

        root.addOrReplaceChild("spine_front",
                CubeListBuilder.create().texOffs(28, 50)
                        .addBox(-1.0f, -3.0f, -1.0f, 2.0f, 3.0f, 2.0f),
                PartPose.offset(0.0f, -7.0f, -4.0f));
        root.addOrReplaceChild("spine_middle",
                CubeListBuilder.create().texOffs(36, 50)
                        .addBox(-1.0f, -3.0f, -1.0f, 2.0f, 3.0f, 2.0f),
                PartPose.offset(0.0f, -7.0f, 0.0f));
        root.addOrReplaceChild("spine_rear",
                CubeListBuilder.create().texOffs(44, 50)
                        .addBox(-1.0f, -3.0f, -1.0f, 2.0f, 3.0f, 2.0f),
                PartPose.offset(0.0f, -7.0f, 4.0f));

        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(RampantRenderState state) {
        super.setupAnim(state);
        float time = state.ageInTicks;
        float walk = state.walkAnimationPos * 1.35f;
        float speed = Mth.clamp(state.walkAnimationSpeed * 1.8f, 0.0f, 1.0f);
        resetRampantPose();

        this.head.yRot = state.yRot * Mth.DEG_TO_RAD * 0.40f;
        this.head.xRot = state.xRot * Mth.DEG_TO_RAD * 0.25f;

        switch (state.phase) {
            case RampantEntity.PHASE_CLINGING -> poseClinging(time, state.phaseTicks);
            case RampantEntity.PHASE_CLIMBING -> poseClimbing(time, state.attachment);
            case RampantEntity.PHASE_DROPPING -> poseDropping(time, state.phaseTicks);
            case RampantEntity.PHASE_LEAPING -> poseLeaping(time, state.phaseTicks);
            case RampantEntity.PHASE_FLEEING_LIGHT -> poseRunning(walk, speed, time, 1.75f, true);
            default -> poseRunning(walk, speed, time, 1.0f, false);
        }
        applyTwitchBurst(time);
    }

    private void poseClinging(float time, float phaseTicks) {
        float breath = MonsterAnimationMath.organicBreath(time, 0.075f) * 0.045f;
        float grip = MonsterAnimationMath.smootherstep(phaseTicks / 10.0f);
        setSpread(0.48f + grip * 0.12f);
        this.frontRightUpper.xRot = 0.38f + breath;
        this.frontLeftUpper.xRot = -0.30f - breath;
        this.rearRightUpper.xRot = -0.36f + breath;
        this.rearLeftUpper.xRot = 0.28f - breath;
        this.frontRightLower.xRot = 0.82f;
        this.frontLeftLower.xRot = 0.72f;
        this.rearRightLower.xRot = -0.78f;
        this.rearLeftLower.xRot = -0.68f;
        this.body.y = -4.5f - grip * 0.18f + breath * 0.20f;
        this.body.xRot = breath * 0.16f;
        this.head.zRot = Mth.sin(time * 0.11f) * 0.08f;
        this.spineFront.xRot = -breath * 0.9f;
        this.spineMiddle.xRot = breath * 0.55f;
        this.spineRear.xRot = -breath * 0.35f;
    }

    private void poseClimbing(float time, int attachment) {
        boolean ceiling = attachment == RampantEntity.ATTACHMENT_CEILING;
        float pace = ceiling ? 0.66f : 0.54f;
        float cycle = Mth.sin(time * pace);
        float opposite = Mth.sin(time * pace + Mth.PI);
        setSpread(ceiling ? 0.52f : 0.39f);
        this.frontRightUpper.xRot = cycle * 0.62f;
        this.rearLeftUpper.xRot = cycle * 0.62f;
        this.frontLeftUpper.xRot = opposite * 0.62f;
        this.rearRightUpper.xRot = opposite * 0.62f;
        this.frontRightLower.xRot = 0.48f + Math.max(0.0f, -cycle) * 0.72f;
        this.rearLeftLower.xRot = -0.48f - Math.max(0.0f, cycle) * 0.72f;
        this.frontLeftLower.xRot = 0.48f + Math.max(0.0f, -opposite) * 0.72f;
        this.rearRightLower.xRot = -0.48f - Math.max(0.0f, opposite) * 0.72f;
        this.body.y = -4.5f + Mth.abs(cycle) * (ceiling ? 0.28f : 0.48f);
        this.body.xRot = ceiling ? Mth.sin(time * 0.22f) * 0.025f : -0.08f;
        this.body.zRot = Mth.sin(time * 0.29f) * (ceiling ? 0.045f : 0.028f);
        this.head.zRot = -this.body.zRot * 1.8f;
        this.spineFront.zRot = cycle * 0.055f;
        this.spineMiddle.zRot = -cycle * 0.035f;
        this.spineRear.zRot = opposite * 0.055f;
    }

    private void poseDropping(float time, float phaseTicks) {
        float release = MonsterAnimationMath.smootherstep(phaseTicks / 8.0f);
        setSpread(0.78f - release * 0.36f);
        this.frontRightUpper.xRot = -0.30f + release * 0.22f;
        this.frontLeftUpper.xRot = -0.30f + release * 0.18f;
        this.rearRightUpper.xRot = 0.34f - release * 0.20f;
        this.rearLeftUpper.xRot = 0.34f - release * 0.24f;
        this.frontRightLower.xRot = 1.02f - release * 0.46f;
        this.frontLeftLower.xRot = 1.02f - release * 0.42f;
        this.rearRightLower.xRot = -0.94f + release * 0.40f;
        this.rearLeftLower.xRot = -0.94f + release * 0.44f;
        this.body.xRot = -0.14f * release;
        this.body.zRot = Mth.sin(time * 0.72f) * (0.08f + release * 0.04f);
        this.head.xRot -= 0.18f * release;
    }

    private void poseLeaping(float time, float phaseTicks) {
        MonsterAnimationMath.AttackPose pose = MonsterAnimationMath.attackPose(
                MonsterAnimationMath.clamp01(phaseTicks / 12.0f));
        float preparation = pose.anticipation();
        float strike = pose.strike();
        this.body.xRot = 0.18f * preparation - 0.30f * strike;
        this.head.xRot += 0.16f * preparation - 0.50f * strike;
        this.frontRightUpper.xRot = 0.24f * preparation - 0.96f * strike;
        this.frontLeftUpper.xRot = 0.20f * preparation - 0.96f * strike;
        this.frontRightLower.xRot = 0.62f * preparation - 0.36f * strike;
        this.frontLeftLower.xRot = 0.62f * preparation - 0.36f * strike;
        this.rearRightUpper.xRot = -0.44f * preparation + 0.76f * strike;
        this.rearLeftUpper.xRot = -0.44f * preparation + 0.76f * strike;
        this.rearRightLower.xRot = -0.30f * preparation - 0.82f * strike;
        this.rearLeftLower.xRot = -0.30f * preparation - 0.82f * strike;
        setSpread(0.42f * preparation + 0.16f * strike);
        this.body.zRot = Mth.sin(time * 2.4f) * 0.055f * strike;
        this.spineFront.xRot = -0.12f * strike;
        this.spineMiddle.xRot = 0.08f * strike;
        this.spineRear.xRot = -0.06f * strike;
    }

    private void poseRunning(float walk, float speed, float time, float pace, boolean fleeing) {
        float diagonalA = Mth.cos(walk * pace);
        float diagonalB = Mth.cos(walk * pace + Mth.PI);
        float amplitude = (fleeing ? 0.68f : 0.50f) * speed;
        setSpread(fleeing ? 0.50f : 0.34f);
        this.frontRightUpper.xRot = diagonalA * amplitude;
        this.rearLeftUpper.xRot = diagonalA * amplitude;
        this.frontLeftUpper.xRot = diagonalB * amplitude;
        this.rearRightUpper.xRot = diagonalB * amplitude;
        this.frontRightLower.xRot = 0.28f + Mth.abs(diagonalA) * 0.62f * speed;
        this.frontLeftLower.xRot = 0.28f + Mth.abs(diagonalB) * 0.62f * speed;
        this.rearRightLower.xRot = -0.28f - Mth.abs(diagonalB) * 0.62f * speed;
        this.rearLeftLower.xRot = -0.28f - Mth.abs(diagonalA) * 0.62f * speed;
        this.body.y = -4.5f + Mth.abs(Mth.sin(walk * pace)) * 0.68f * speed;
        this.head.zRot = Mth.sin(time * (fleeing ? 0.43f : 0.19f))
                * (fleeing ? 0.06f : 0.025f);
        this.spineFront.zRot = Mth.sin(time * 0.13f) * 0.04f;
        this.spineMiddle.xRot = Mth.sin(walk * pace + Mth.HALF_PI) * 0.035f * speed;
        this.spineRear.zRot = -this.spineFront.zRot;
    }

    private void setSpread(float amount) {
        this.frontRightUpper.yRot = -amount;
        this.rearRightUpper.yRot = amount;
        this.frontLeftUpper.yRot = amount;
        this.rearLeftUpper.yRot = -amount;
    }

    /** Salves irrégulières : immobilité longue, puis contraction nerveuse. */
    private void applyTwitchBurst(float time) {
        float cycle = time % 97.0f;
        if (cycle >= 6.0f) {
            return;
        }
        float twitch = Mth.sin(time * 4.1f) * 0.085f;
        this.head.zRot += twitch;
        this.frontRightLower.zRot += twitch;
        this.rearLeftLower.zRot -= twitch;
        this.spineMiddle.zRot += twitch * 0.55f;
    }

    private void resetRampantPose() {
        ModelPart[] all = {
                this.head, this.body,
                this.frontRightUpper, this.frontRightLower,
                this.frontLeftUpper, this.frontLeftLower,
                this.rearRightUpper, this.rearRightLower,
                this.rearLeftUpper, this.rearLeftLower,
                this.spineFront, this.spineMiddle, this.spineRear
        };
        for (ModelPart part : all) {
            part.xRot = 0.0f;
            part.yRot = 0.0f;
            part.zRot = 0.0f;
        }
        this.body.y = -4.5f;
    }
}

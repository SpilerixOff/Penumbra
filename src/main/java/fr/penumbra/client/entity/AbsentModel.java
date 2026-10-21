package fr.penumbra.client.entity;

import fr.penumbra.PenumbraMod;
import fr.penumbra.client.animation.MonsterAnimationMath;
import fr.penumbra.entity.absent.AbsentEntity;
import fr.penumbra.entity.absent.AbsentMimicLogic;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;

/**
 * Faux joueur à atlas Minecraft 64x64 complet.
 *
 * <p>Le modèle contient simultanément les bras larges et fins : changer de
 * victime ne rebake donc jamais un layer en jeu. Les six surcouches modernes
 * (chapeau, veste, manches et pantalon) suivent directement leur membre
 * parent et reproduisent le skin complet.</p>
 */
@Environment(EnvType.CLIENT)
public final class AbsentModel extends HumanoidModel<AbsentRenderState> {

    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(PenumbraMod.id("absent"), "main");

    private final ModelPart head;
    private final ModelPart hat;
    private final ModelPart body;
    private final ModelPart jacket;
    private final ModelPart rightArm;
    private final ModelPart rightArmSlim;
    private final ModelPart leftArm;
    private final ModelPart leftArmSlim;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;
    private final ModelPart rightPants;
    private final ModelPart leftPants;

    public AbsentModel(ModelPart root) {
        super(root);
        this.head = root.getChild("head");
        this.hat = this.head.getChild("hat");
        this.body = root.getChild("body");
        this.jacket = this.body.getChild("jacket");
        this.rightArm = root.getChild("right_arm");
        this.rightArmSlim = root.getChild("right_arm_slim");
        this.leftArm = root.getChild("left_arm");
        this.leftArmSlim = root.getChild("left_arm_slim");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
        this.rightPants = this.rightLeg.getChild("right_pants");
        this.leftPants = this.leftLeg.getChild("left_pants");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition head = root.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-4.0f, -8.0f, -4.0f, 8.0f, 8.0f, 8.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        head.addOrReplaceChild("hat",
                CubeListBuilder.create().texOffs(32, 0)
                        .addBox(-4.0f, -8.0f, -4.0f, 8.0f, 8.0f, 8.0f,
                                new CubeDeformation(0.5f)),
                PartPose.ZERO);

        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(16, 16)
                        .addBox(-4.0f, 0.0f, -2.0f, 8.0f, 12.0f, 4.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        body.addOrReplaceChild("jacket",
                CubeListBuilder.create().texOffs(16, 32)
                        .addBox(-4.0f, 0.0f, -2.0f, 8.0f, 12.0f, 4.0f,
                                new CubeDeformation(0.25f)),
                PartPose.ZERO);

        PartDefinition rightArm = root.addOrReplaceChild("right_arm",
                CubeListBuilder.create().texOffs(40, 16)
                        .addBox(-3.0f, -2.0f, -2.0f, 4.0f, 12.0f, 4.0f),
                PartPose.offset(-5.0f, 2.0f, 0.0f));
        rightArm.addOrReplaceChild("right_sleeve",
                CubeListBuilder.create().texOffs(40, 32)
                        .addBox(-3.0f, -2.0f, -2.0f, 4.0f, 12.0f, 4.0f,
                                new CubeDeformation(0.25f)),
                PartPose.ZERO);

        PartDefinition leftArm = root.addOrReplaceChild("left_arm",
                CubeListBuilder.create().texOffs(32, 48)
                        .addBox(-1.0f, -2.0f, -2.0f, 4.0f, 12.0f, 4.0f),
                PartPose.offset(5.0f, 2.0f, 0.0f));
        leftArm.addOrReplaceChild("left_sleeve",
                CubeListBuilder.create().texOffs(48, 48)
                        .addBox(-1.0f, -2.0f, -2.0f, 4.0f, 12.0f, 4.0f,
                                new CubeDeformation(0.25f)),
                PartPose.ZERO);

        PartDefinition rightSlim = root.addOrReplaceChild("right_arm_slim",
                CubeListBuilder.create().texOffs(40, 16)
                        .addBox(-2.0f, -2.0f, -2.0f, 3.0f, 12.0f, 4.0f),
                PartPose.offset(-5.0f, 2.0f, 0.0f));
        rightSlim.addOrReplaceChild("right_sleeve",
                CubeListBuilder.create().texOffs(40, 32)
                        .addBox(-2.0f, -2.0f, -2.0f, 3.0f, 12.0f, 4.0f,
                                new CubeDeformation(0.25f)),
                PartPose.ZERO);

        PartDefinition leftSlim = root.addOrReplaceChild("left_arm_slim",
                CubeListBuilder.create().texOffs(32, 48)
                        .addBox(-1.0f, -2.0f, -2.0f, 3.0f, 12.0f, 4.0f),
                PartPose.offset(5.0f, 2.0f, 0.0f));
        leftSlim.addOrReplaceChild("left_sleeve",
                CubeListBuilder.create().texOffs(48, 48)
                        .addBox(-1.0f, -2.0f, -2.0f, 3.0f, 12.0f, 4.0f,
                                new CubeDeformation(0.25f)),
                PartPose.ZERO);

        PartDefinition rightLeg = root.addOrReplaceChild("right_leg",
                CubeListBuilder.create().texOffs(0, 16)
                        .addBox(-2.0f, 0.0f, -2.0f, 4.0f, 12.0f, 4.0f),
                PartPose.offset(-1.9f, 12.0f, 0.0f));
        rightLeg.addOrReplaceChild("right_pants",
                CubeListBuilder.create().texOffs(0, 32)
                        .addBox(-2.0f, 0.0f, -2.0f, 4.0f, 12.0f, 4.0f,
                                new CubeDeformation(0.25f)),
                PartPose.ZERO);

        PartDefinition leftLeg = root.addOrReplaceChild("left_leg",
                CubeListBuilder.create().texOffs(16, 48)
                        .addBox(-2.0f, 0.0f, -2.0f, 4.0f, 12.0f, 4.0f),
                PartPose.offset(1.9f, 12.0f, 0.0f));
        leftLeg.addOrReplaceChild("left_pants",
                CubeListBuilder.create().texOffs(0, 48)
                        .addBox(-2.0f, 0.0f, -2.0f, 4.0f, 12.0f, 4.0f,
                                new CubeDeformation(0.25f)),
                PartPose.ZERO);

        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(AbsentRenderState state) {
        super.setupAnim(state);
        this.configureSkinParts(state);

        float speed = Math.min(state.walkAnimationSpeed, 1.0f);
        float stride = state.walkAnimationPos * 0.6662f;
        this.head.xRot = state.xRot * Mth.DEG_TO_RAD;
        this.head.yRot = state.yRot * Mth.DEG_TO_RAD;

        if (!state.movementImitationEnabled) {
            this.setupLegacyAnim(state, stride, speed);
        } else {
            switch (state.absentPhase) {
                case AbsentEntity.PHASE_WATCH -> this.poseImitate(state);
                case AbsentEntity.PHASE_LURE -> this.poseLure(state, stride, speed);
                case AbsentEntity.PHASE_SUSPICION -> this.poseSuspicion(state, stride, speed);
                case AbsentEntity.PHASE_REVEAL -> this.poseReveal(state);
                default -> this.poseHunt(state, stride, speed);
            }
            if (state.absentPhase < AbsentEntity.PHASE_REVEAL) {
                this.applyLearnedPosture(state);
            }
        }

        this.copyArmPose(this.rightArm, this.rightArmSlim);
        this.copyArmPose(this.leftArm, this.leftArmSlim);
    }

    private void configureSkinParts(AbsentRenderState state) {
        this.rightArm.visible = !state.slimArms;
        this.leftArm.visible = !state.slimArms;
        this.rightArmSlim.visible = state.slimArms;
        this.leftArmSlim.visible = state.slimArms;
        this.hat.visible = shown(state, PlayerModelPart.HAT);
        this.jacket.visible = shown(state, PlayerModelPart.JACKET);
        boolean rightSleeve = shown(state, PlayerModelPart.RIGHT_SLEEVE);
        boolean leftSleeve = shown(state, PlayerModelPart.LEFT_SLEEVE);
        this.rightArm.getChild("right_sleeve").visible = rightSleeve;
        this.leftArm.getChild("left_sleeve").visible = leftSleeve;
        this.rightArmSlim.getChild("right_sleeve").visible = rightSleeve;
        this.leftArmSlim.getChild("left_sleeve").visible = leftSleeve;
        this.rightPants.visible = shown(state, PlayerModelPart.RIGHT_PANTS_LEG);
        this.leftPants.visible = shown(state, PlayerModelPart.LEFT_PANTS_LEG);
    }

    private static boolean shown(AbsentRenderState state, PlayerModelPart part) {
        return state.usingPlayerSkin && (state.modelPartsMask & part.getMask()) != 0;
    }

    private void poseImitate(AbsentRenderState state) {
        float settle = MonsterAnimationMath.smootherstep(state.phaseTicks / 18.0f);
        float breath = MonsterAnimationMath.organicBreath(state.ageInTicks, 0.024f);
        float error = this.effectiveError(state);
        float twitch = glitchPulse(state, 0.071f, 11) * error;
        float drift = seedSigned(state.locomotionSeed, 3);

        this.head.yRot = this.head.yRot * settle
                + Mth.sin(state.ageInTicks * 0.021f + seedAngle(state.locomotionSeed, 1))
                * 0.018f * error;
        this.head.xRot *= settle;
        this.head.yRot += twitch * (0.24f + Math.abs(drift) * 0.12f) * Mth.sign(drift);
        this.head.zRot = Mth.sin(state.ageInTicks * 0.010f) * 0.010f
                + twitch * 0.055f * -Mth.sign(drift);
        this.body.xRot = breath * 0.005f;
        this.body.y = -breath * 0.07f;
        this.body.yRot = -this.head.yRot * 0.035f * error;
        this.rightArm.zRot = 0.012f + twitch * 0.025f;
        this.leftArm.zRot = -0.012f;
        // Une marche interrompue trop proprement est le premier indice.
        this.rightLeg.xRot = (1.0f - settle) * (0.035f + drift * 0.012f);
        this.leftLeg.xRot = (1.0f - settle) * -0.025f;
    }

    private void poseLure(AbsentRenderState state, float stride, float speed) {
        float error = this.effectiveError(state);
        float style = seedSigned(state.locomotionSeed, 7);
        float learnedSpeed = AbsentMimicLogic.unpackSpeed(state.gaitSignature);
        float learnedTurn = AbsentMimicLogic.unpackTurn(state.gaitSignature);
        float gaitAmplitude = 0.86f + learnedSpeed * 0.28f;
        if (AbsentMimicLogic.isSprinting(state.gaitSignature)) {
            gaitAmplitude += 0.12f;
        }
        float cadence = 0.96f + learnedSpeed * 0.09f
                + seedSigned(state.locomotionSeed, 8) * 0.025f;
        float phase = stride * cadence;
        float rightStep = Mth.cos(phase + Mth.PI);
        float leftStep = Mth.cos(phase);
        float hitch = glitchPulse(state, 0.19f, 17) * speed * error;
        float armDelay = error * (0.04f + Math.abs(style) * 0.035f);
        float delayedLeft = Mth.cos(phase - armDelay);

        this.rightArm.xRot = rightStep * (1.18f + style * 0.06f)
                * gaitAmplitude * speed;
        this.leftArm.xRot = delayedLeft * (1.18f - style * 0.04f)
                * gaitAmplitude * speed;
        this.rightLeg.xRot = leftStep * 1.34f * gaitAmplitude * speed;
        this.leftLeg.xRot = rightStep * 1.34f * gaitAmplitude * speed - hitch * 0.22f;
        this.leftLeg.zRot = hitch * 0.028f;
        this.body.yRot = (rightStep - leftStep) * 0.026f * speed
                + style * learnedTurn * 0.035f;
        this.body.zRot = Mth.sin(phase * 0.5f) * 0.018f * speed + hitch * 0.035f;
        this.body.y = Mth.abs(Mth.sin(phase)) * 0.12f * speed;

        // Le regard accuse un retard discret, puis rattrape sa cible d'un coup.
        float headCatchUp = glitchPulse(state, 0.083f, 23) * error;
        this.head.yRot += Mth.sin(state.ageInTicks * 0.031f
                + seedAngle(state.locomotionSeed, 4)) * 0.045f * error;
        this.head.yRot += headCatchUp * (0.16f + Math.abs(style) * 0.08f) * Mth.sign(style);
        this.head.yRot += style * learnedTurn * 0.055f;
        this.head.zRot = -0.012f * error + headCatchUp * -0.04f * Mth.sign(style);
    }

    private void poseSuspicion(AbsentRenderState state, float stride, float speed) {
        float error = Math.max(0.32f, this.effectiveError(state));
        float freeze = MonsterAnimationMath.smootherstep(state.phaseTicks / 9.0f);
        float snap = glitchPulse(state, 0.115f, 31) * error;
        float side = seedSigned(state.locomotionSeed, 12);
        float planted = Mth.cos(stride) * speed * (1.0f - freeze);

        this.rightLeg.xRot = planted * 0.72f + 0.045f * side * freeze;
        this.leftLeg.xRot = -planted * 0.72f - 0.035f * side * freeze;
        this.rightArm.xRot = -planted * 0.55f;
        this.leftArm.xRot = planted * 0.55f;
        this.body.xRot = 0.025f + freeze * 0.035f;
        this.body.yRot = side * 0.035f * freeze;
        this.head.yRot += side * (0.07f + snap * 0.34f);
        this.head.xRot -= snap * 0.08f;
        this.head.zRot = -side * (0.018f + snap * 0.09f);
        this.rightArm.zRot = 0.018f + snap * 0.045f;
        this.leftArm.zRot = -0.018f;
    }

    private void poseReveal(AbsentRenderState state) {
        float snap = Math.max(MonsterAnimationMath.smootherstep(state.phaseTicks / 28.0f),
                Mth.clamp(state.revealProgress, 0.0f, 1.0f));
        float shake = Mth.sin(state.ageInTicks * (1.25f + snap * 2.2f)
                + seedAngle(state.locomotionSeed, 19));
        float convulsion = glitchPulse(state, 0.31f, 41) * snap;

        this.body.xRot = 0.10f * (1.0f - snap) - 0.48f * snap;
        this.body.zRot = shake * (0.018f + 0.055f * snap) + convulsion * 0.08f;
        this.head.xRot += 0.12f * (1.0f - snap) - 0.55f * snap;
        this.head.yRot *= 1.0f + snap * 0.22f;
        this.head.zRot = shake * 0.12f * snap + convulsion * 0.16f;
        this.head.xScale = 1.0f - snap * 0.04f;
        this.head.yScale = 1.0f + snap * 0.07f;
        this.rightArm.xRot = 0.18f * (1.0f - snap) - 1.44f * snap;
        this.leftArm.xRot = 0.24f * (1.0f - snap) - 1.31f * snap;
        this.rightArm.yRot = -0.18f * snap;
        this.leftArm.yRot = 0.18f * snap;
        this.rightArm.zRot = 0.22f * snap + convulsion * 0.08f;
        this.leftArm.zRot = -0.22f * snap;
        this.rightArm.yScale = 1.0f + 0.12f * snap;
        this.leftArm.yScale = 1.0f + 0.08f * snap;
        this.rightLeg.xRot = 0.12f * (1.0f - snap);
        this.leftLeg.xRot = -0.09f * (1.0f - snap);
    }

    private void poseHunt(AbsentRenderState state, float stride, float speed) {
        float phase = stride * 1.36f;
        float rightStep = Mth.cos(phase + Mth.PI);
        float leftStep = Mth.cos(phase);
        float proximity = MonsterAnimationMath.proximity(
                state.distanceToCameraSq, 2.0f, 10.0f);
        float bounce = Mth.abs(Mth.sin(phase)) * speed;
        float lunge = state.lunging
                ? 1.0f : glitchPulse(state, 0.23f, 53) * proximity;
        float side = seedSigned(state.locomotionSeed, 27);

        this.body.xRot = 0.46f + proximity * 0.21f + lunge * 0.11f;
        this.body.y = bounce * 0.32f - lunge * 0.16f;
        this.body.zRot = Mth.sin(phase * 0.5f) * 0.052f * speed + side * lunge * 0.07f;
        this.head.xRot -= 0.25f + proximity * 0.15f + lunge * 0.10f;
        this.head.zRot = Mth.sin(state.ageInTicks * 0.36f) * 0.042f
                - side * lunge * 0.10f;
        this.rightArm.xRot = -1.10f + rightStep * 0.31f * speed
                - proximity * 0.25f - lunge * 0.18f;
        this.leftArm.xRot = -1.06f + leftStep * 0.31f * speed
                - proximity * 0.25f - lunge * 0.12f;
        this.rightArm.yRot = -0.14f * proximity;
        this.leftArm.yRot = 0.14f * proximity;
        this.rightArm.zRot = 0.09f + proximity * 0.10f;
        this.leftArm.zRot = -0.09f - proximity * 0.10f;
        this.rightLeg.xRot = leftStep * 1.58f * speed;
        this.leftLeg.xRot = rightStep * 1.58f * speed;
    }

    /** Gestuelle 0.10 conservée pour le bouton de compatibilité. */
    private void setupLegacyAnim(AbsentRenderState state, float stride, float speed) {
        switch (state.absentPhase) {
            case AbsentEntity.PHASE_WATCH -> this.poseLegacyWatch(state);
            case AbsentEntity.PHASE_LURE -> this.poseLegacyLure(state, stride, speed);
            case AbsentEntity.PHASE_SUSPICION, AbsentEntity.PHASE_REVEAL ->
                    this.poseLegacyReveal(state);
            default -> this.poseLegacyHunt(state, stride, speed);
        }
    }

    private void poseLegacyWatch(AbsentRenderState state) {
        float settle = MonsterAnimationMath.smootherstep(state.phaseTicks / 18.0f);
        float breath = MonsterAnimationMath.organicBreath(state.ageInTicks, 0.024f);
        this.head.yRot *= settle;
        this.head.xRot *= settle;
        this.head.zRot = Mth.sin(state.ageInTicks * 0.010f) * 0.012f;
        this.body.xRot = breath * 0.006f;
        this.body.y = -breath * 0.08f;
        this.rightArm.zRot = 0.015f;
        this.leftArm.zRot = -0.015f;
        this.rightLeg.xRot = 0.035f * (1.0f - settle);
        this.leftLeg.xRot = -0.025f * (1.0f - settle);
    }

    private void poseLegacyLure(AbsentRenderState state, float stride, float speed) {
        float rightStep = Mth.cos(stride + Mth.PI);
        float leftStep = Mth.cos(stride);
        float hitch = Math.max(0.0f, Mth.sin(stride * 2.0f + 0.6f));
        this.rightArm.xRot = rightStep * 1.22f * speed;
        this.leftArm.xRot = leftStep * 1.30f * speed;
        this.rightLeg.xRot = leftStep * 1.38f * speed;
        this.leftLeg.xRot = rightStep * 1.28f * speed - hitch * 0.12f * speed;
        this.body.yRot = (rightStep - leftStep) * 0.035f * speed;
        this.body.zRot = hitch * 0.025f * speed;
        this.body.y = hitch * 0.20f * speed;
        float lookBack = MonsterAnimationMath.smootherstep(state.phaseTicks / 20.0f);
        this.head.yRot += Mth.sin(state.ageInTicks * 0.035f) * 0.18f + lookBack * 0.11f;
        this.head.zRot = -0.025f * lookBack;
    }

    private void poseLegacyReveal(AbsentRenderState state) {
        float snap = MonsterAnimationMath.smootherstep(state.phaseTicks / 28.0f);
        float shake = Mth.sin(state.ageInTicks * (1.2f + snap * 1.8f));
        this.body.xRot = 0.12f * (1.0f - snap) - 0.42f * snap;
        this.body.zRot = shake * 0.025f * snap;
        this.head.xRot += 0.16f * (1.0f - snap) - 0.48f * snap;
        this.head.yRot *= 1.0f + snap * 0.18f;
        this.head.zRot = shake * 0.10f * snap;
        this.rightArm.xRot = 0.20f * (1.0f - snap) - 1.36f * snap;
        this.leftArm.xRot = 0.26f * (1.0f - snap) - 1.24f * snap;
        this.rightArm.yRot = -0.14f * snap;
        this.leftArm.yRot = 0.14f * snap;
        this.rightArm.zRot = 0.19f * snap;
        this.leftArm.zRot = -0.19f * snap;
        this.rightLeg.xRot = 0.13f * (1.0f - snap);
        this.leftLeg.xRot = -0.10f * (1.0f - snap);
    }

    private void poseLegacyHunt(AbsentRenderState state, float stride, float speed) {
        float rightStep = Mth.cos(stride * 1.28f + Mth.PI);
        float leftStep = Mth.cos(stride * 1.28f);
        float proximity = MonsterAnimationMath.proximity(state.distanceToCameraSq, 2.0f, 9.0f);
        float bounce = Mth.abs(Mth.sin(stride * 1.28f)) * speed;
        this.body.xRot = 0.42f + proximity * 0.18f;
        this.body.y = bounce * 0.28f;
        this.body.zRot = Mth.sin(stride * 0.64f) * 0.045f * speed;
        this.head.xRot -= 0.22f + proximity * 0.12f;
        this.head.zRot = Mth.sin(state.ageInTicks * 0.32f) * 0.035f;
        this.rightArm.xRot = -1.05f + rightStep * 0.28f * speed - proximity * 0.22f;
        this.leftArm.xRot = -1.05f + leftStep * 0.28f * speed - proximity * 0.22f;
        this.rightArm.yRot = -0.12f * proximity;
        this.leftArm.yRot = 0.12f * proximity;
        this.rightArm.zRot = 0.08f + proximity * 0.08f;
        this.leftArm.zRot = -0.08f - proximity * 0.08f;
        this.rightLeg.xRot = leftStep * 1.52f * speed;
        this.leftLeg.xRot = rightStep * 1.52f * speed;
    }

    private float effectiveError(AbsentRenderState state) {
        float proximity = MonsterAnimationMath.proximity(state.distanceToCameraSq, 4.0f, 18.0f);
        return Mth.clamp(state.imitationError + proximity * 0.16f, 0.0f, 1.0f);
    }

    /**
     * Rejoue les trois postures apprises sans toucher à la collision : cela
     * suffit à rendre l'imitation lisible sans faire sauter l'entité serveur.
     */
    private void applyLearnedPosture(AbsentRenderState state) {
        if (AbsentMimicLogic.isCrouching(state.gaitSignature)) {
            this.head.y += 1.65f;
            this.body.y += 1.65f;
            this.rightArm.y += 1.65f;
            this.leftArm.y += 1.65f;
            this.body.xRot += 0.10f;
            this.rightLeg.xRot -= 0.06f;
            this.leftLeg.xRot -= 0.06f;
        }
        if (AbsentMimicLogic.isSprinting(state.gaitSignature)) {
            this.body.xRot += 0.13f;
            this.head.xRot -= 0.04f;
        }
        if (AbsentMimicLogic.isAirborne(state.gaitSignature)) {
            float side = seedSigned(state.locomotionSeed, 61);
            this.rightLeg.xRot = -0.18f + side * 0.035f;
            this.leftLeg.xRot = 0.24f - side * 0.035f;
            this.rightArm.xRot -= 0.12f;
            this.leftArm.xRot -= 0.12f;
        }
    }

    private static float glitchPulse(AbsentRenderState state, float frequency, int salt) {
        float clock = state.ageInTicks * frequency + seedAngle(state.locomotionSeed, salt);
        float wave = (Mth.sin(clock) - 0.76f) / 0.24f;
        float pulse = Mth.clamp(wave, 0.0f, 1.0f);
        return pulse * pulse * (3.0f - 2.0f * pulse);
    }

    private static float seedAngle(long seed, int salt) {
        return (seedUnit(seed, salt) * Mth.TWO_PI);
    }

    private static float seedSigned(long seed, int salt) {
        return seedUnit(seed, salt) * 2.0f - 1.0f;
    }

    private static float seedUnit(long seed, int salt) {
        long value = seed + 0x9E3779B97F4A7C15L * (salt + 1L);
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (float) ((value >>> 40) & 0xFFFFFFL) / 16777215.0f;
    }

    private void copyArmPose(ModelPart source, ModelPart target) {
        target.x = source.x;
        target.y = source.y;
        target.z = source.z;
        target.xRot = source.xRot;
        target.yRot = source.yRot;
        target.zRot = source.zRot;
        target.xScale = source.xScale;
        target.yScale = source.yScale;
        target.zScale = source.zScale;
    }

    /** Rejoue exactement les pivots du corps sur les modèles d'armure. */
    void copyHumanoidPoseTo(HumanoidModel<AbsentRenderState> target) {
        target.head.loadPose(this.head.storePose());
        target.hat.loadPose(this.hat.storePose());
        target.body.loadPose(this.body.storePose());
        target.rightArm.loadPose((this.rightArm.visible ? this.rightArm : this.rightArmSlim).storePose());
        target.leftArm.loadPose((this.leftArm.visible ? this.leftArm : this.leftArmSlim).storePose());
        target.rightLeg.loadPose(this.rightLeg.storePose());
        target.leftLeg.loadPose(this.leftLeg.storePose());
    }

    /** Aligne l'objet copié sur la géométrie fine quand le skin utilise des bras slim. */
    @Override
    public void translateToHand(AbsentRenderState state, HumanoidArm arm, PoseStack poseStack) {
        this.root.translateAndRotate(poseStack);
        ModelPart selected = state.slimArms
                ? (arm == HumanoidArm.LEFT ? this.leftArmSlim : this.rightArmSlim)
                : (arm == HumanoidArm.LEFT ? this.leftArm : this.rightArm);
        if (state.slimArms) {
            float offset = 0.5f * (arm == HumanoidArm.RIGHT ? 1.0f : -1.0f);
            selected.x += offset;
            selected.translateAndRotate(poseStack);
            selected.x -= offset;
        } else {
            selected.translateAndRotate(poseStack);
        }
    }
}

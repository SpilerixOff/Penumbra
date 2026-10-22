package fr.penumbra.client.render;

import fr.penumbra.PenumbraMod;
import fr.penumbra.client.animation.HostAttackAnimation;
import fr.penumbra.client.animation.MonsterAnimationMath;
import fr.penumbra.entity.HostBehavior;
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
 * Silhouette articulee de l'Hote, haute de 2,75 blocs.
 *
 * <p>0.14.0 : cou etire, mains trop longues (les doigts frolent les cuisses),
 * epaules desaxees — l'epaule droite pend 1 px plus bas que la gauche. La
 * silhouette de face parait presque humaine ; c'est de profil ou en mouvement
 * que les proportions se revelent fausses.</p>
 */
@Environment(EnvType.CLIENT)
public final class HostEntityModel extends EntityModel<HostRenderState> {

    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(PenumbraMod.id("host"), "main");

    /** Periode moyenne des spasmes ; leur instant exact varie a chaque cycle. */
    private static final float TREMOR_PERIOD_TICKS = 220.0f;
    private static final float TREMOR_DURATION_TICKS = 7.0f;
    private static final int TREMOR_SEED = 1013;

    private final ModelPart hips;
    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart rightArm;
    private final ModelPart rightForearm;
    private final ModelPart rightHand;
    private final ModelPart leftArm;
    private final ModelPart leftForearm;
    private final ModelPart leftHand;
    private final ModelPart rightLeg;
    private final ModelPart rightShin;
    private final ModelPart leftLeg;
    private final ModelPart leftShin;
    private final ModelPart[] rightFingers;
    private final ModelPart[] leftFingers;
    private final ModelPart rightThumb;
    private final ModelPart leftThumb;

    public HostEntityModel(ModelPart root) {
        super(root);
        this.hips = root.getChild("hips");
        this.body = this.hips.getChild("body");
        this.head = this.body.getChild("head");
        this.rightArm = this.body.getChild("right_arm");
        this.rightForearm = this.rightArm.getChild("forearm");
        this.rightHand = this.rightForearm.getChild("hand");
        this.leftArm = this.body.getChild("left_arm");
        this.leftForearm = this.leftArm.getChild("forearm");
        this.leftHand = this.leftForearm.getChild("hand");
        this.rightLeg = this.hips.getChild("right_leg");
        this.rightShin = this.rightLeg.getChild("shin");
        this.leftLeg = this.hips.getChild("left_leg");
        this.leftShin = this.leftLeg.getChild("shin");
        this.rightFingers = fingers(this.rightHand);
        this.leftFingers = fingers(this.leftHand);
        this.rightThumb = this.rightHand.getChild("thumb");
        this.leftThumb = this.leftHand.getChild("thumb");
    }

    /**
     * Bras en trois os (bras, avant-bras, main) et cou apparent. Le cou occupe
     * texOffs(24,18), les mains texOffs(52,0) et (52,8) : zones libres de
     * l'atlas 64x64 historique, peintes par tools/generate_skins.py.
     */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition hips = mesh.getRoot().addOrReplaceChild("hips",
                CubeListBuilder.create(), PartPose.offset(0.0f, -14.0f, 0.0f));
        // Tout le haut du corps pivote autour du bassin. Le cou, la tete et
        // les epaules restent solidaires, meme lorsque le torse plonge a 60°.
        PartDefinition body = hips.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 20)
                        .addBox(-4.0f, -20.0f, -2.0f, 8.0f, 20.0f, 4.0f),
                PartPose.ZERO);
        // Cou trop fin, solidaire du torse (il suit la respiration du corps).
        body.addOrReplaceChild("neck",
                CubeListBuilder.create().texOffs(24, 18)
                        .addBox(-2.0f, -3.0f, -2.0f, 4.0f, 3.0f, 4.0f),
                PartPose.offset(0.0f, -20.0f, 0.0f));
        body.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-4.0f, -8.0f, -5.0f, 8.0f, 8.0f, 10.0f),
                PartPose.offset(0.0f, -22.0f, 0.0f));

        // Epaules desaxees : la droite pend 1 px plus bas que la gauche.
        PartDefinition rightArm = body.addOrReplaceChild("right_arm",
                CubeListBuilder.create().texOffs(40, 0)
                        .addBox(-1.5f, 0.0f, -1.5f, 3.0f, 8.0f, 3.0f),
                PartPose.offset(-5.0f, -19.4f, 0.0f));
        PartDefinition rightForearm = rightArm.addOrReplaceChild("forearm",
                CubeListBuilder.create().texOffs(40, 8)
                        .addBox(-1.5f, 0.0f, -1.5f, 3.0f, 8.0f, 3.0f),
                PartPose.offset(0.0f, 8.0f, 0.0f));
        PartDefinition rightHand = rightForearm.addOrReplaceChild("hand",
                CubeListBuilder.create().texOffs(52, 0)
                        .addBox(-1.5f, 0.0f, -1.0f, 3.0f, 2.2f, 2.0f),
                PartPose.offset(0.0f, 8.0f, 0.0f));
        addFingers(rightHand, true);

        PartDefinition leftArm = body.addOrReplaceChild("left_arm",
                CubeListBuilder.create().texOffs(40, 20)
                        .addBox(-1.5f, 0.0f, -1.5f, 3.0f, 8.0f, 3.0f),
                PartPose.offset(5.0f, -18.4f, 0.0f));
        PartDefinition leftForearm = leftArm.addOrReplaceChild("forearm",
                CubeListBuilder.create().texOffs(40, 28)
                        .addBox(-1.5f, 0.0f, -1.5f, 3.0f, 8.0f, 3.0f),
                PartPose.offset(0.0f, 8.0f, 0.0f));
        PartDefinition leftHand = leftForearm.addOrReplaceChild("hand",
                CubeListBuilder.create().texOffs(52, 8)
                        .addBox(-1.5f, 0.0f, -1.0f, 3.0f, 2.2f, 2.0f),
                PartPose.offset(0.0f, 8.0f, 0.0f));
        addFingers(leftHand, false);

        PartDefinition rightLeg = hips.addOrReplaceChild("right_leg",
                CubeListBuilder.create().texOffs(0, 46)
                        .addBox(-1.5f, 0.0f, -1.5f, 3.0f, 7.0f, 3.0f),
                PartPose.offset(-1.5f, 0.0f, 0.0f));
        rightLeg.addOrReplaceChild("shin",
                CubeListBuilder.create().texOffs(0, 53)
                        .addBox(-1.5f, 0.0f, -1.5f, 3.0f, 7.0f, 3.0f),
                PartPose.offset(0.0f, 7.0f, 0.0f));
        PartDefinition leftLeg = hips.addOrReplaceChild("left_leg",
                CubeListBuilder.create().texOffs(14, 46)
                        .addBox(-1.5f, 0.0f, -1.5f, 3.0f, 7.0f, 3.0f),
                PartPose.offset(1.5f, 0.0f, 0.0f));
        leftLeg.addOrReplaceChild("shin",
                CubeListBuilder.create().texOffs(14, 53)
                        .addBox(-1.5f, 0.0f, -1.5f, 3.0f, 7.0f, 3.0f),
                PartPose.offset(0.0f, 7.0f, 0.0f));

        return LayerDefinition.create(mesh, 64, 64);
    }

    private static void addFingers(PartDefinition hand, boolean right) {
        int textureY = right ? 0 : 8;
        for (int index = 0; index < 3; index++) {
            PartDefinition finger = hand.addOrReplaceChild("finger_" + index,
                    CubeListBuilder.create().texOffs(52, textureY)
                            .addBox(-0.35f, 0.0f, -0.4f, 0.7f, 1.5f, 0.8f),
                    PartPose.offset(index - 1.0f, 2.1f, 0.0f));
            finger.addOrReplaceChild("tip",
                    CubeListBuilder.create().texOffs(54, textureY)
                            .addBox(-0.3f, 0.0f, -0.35f, 0.6f, 1.2f, 0.7f),
                    PartPose.offset(0.0f, 1.5f, 0.0f));
        }
        hand.addOrReplaceChild("thumb",
                CubeListBuilder.create().texOffs(52, textureY)
                        .addBox(-0.4f, 0.0f, -0.4f, 0.8f, 1.8f, 0.8f),
                PartPose.offset(right ? 1.45f : -1.45f, 0.7f, 0.0f));
    }

    private static ModelPart[] fingers(ModelPart hand) {
        return new ModelPart[] {hand.getChild("finger_0"), hand.getChild("finger_1"),
                hand.getChild("finger_2")};
    }

    @Override
    public void setupAnim(HostRenderState state) {
        super.setupAnim(state);
        float walkPos = state.walkAnimationPos;
        float walkSpeed = Math.min(state.walkAnimationSpeed, 1.0f);
        float time = state.ageInTicks;
        HostBehavior behavior = HostBehavior.byId(state.behavior);
        boolean chase = behavior == HostBehavior.CHASING
                || behavior == HostBehavior.ATTACKING;
        // Immobilite de statue : tant qu'il dort ou observe, l'Hote ne respire
        // pas, ne tangue pas, ne tressaille pas. Seule la tete suit le joueur.
        // Le contraste avec la chasse — ou tout son corps s'anime — fait le reste.
        boolean statue = behavior == HostBehavior.DORMANT
                || behavior == HostBehavior.OBSERVING;
        float life = statue ? 0.0f : 1.0f;
        float breath = life * MonsterAnimationMath.organicBreath(
                time, chase ? 0.085f : 0.031f);
        float gait = chase ? 0.86f : 0.60f;
        float rightStride = Mth.cos(walkPos * gait);
        float leftStride = Mth.cos(walkPos * gait + 2.8f);

        // Boitillement asymetrique, maintenant accompagne d'un bassin instable.
        this.rightLeg.xRot = rightStride * (chase ? 1.34f : 1.15f) * walkSpeed;
        this.leftLeg.xRot = leftStride * (chase ? 1.16f : 0.85f) * walkSpeed;
        this.rightLeg.zRot = 0.025f + rightStride * 0.035f * walkSpeed;
        this.leftLeg.zRot = -0.025f - leftStride * 0.025f * walkSpeed;
        this.rightShin.xRot = Math.max(0.0f, -rightStride) * 0.28f * walkSpeed;
        this.leftShin.xRot = Math.max(0.0f, -leftStride) * 0.28f * walkSpeed;

        // Les epaules pendent; seuls les coudes trahissent la respiration.
        this.rightArm.zRot = 0.04f;
        this.leftArm.zRot = -0.04f;
        this.rightForearm.xRot = -0.035f + breath * 0.018f;
        this.leftForearm.xRot = -0.055f - breath * 0.013f;
        // Mains recourbees vers l'interieur ; la chasse crispe les doigts.
        this.rightHand.xRot = -0.16f - (chase ? 0.34f : 0.0f) + breath * 0.02f;
        this.leftHand.xRot = -0.12f - (chase ? 0.30f : 0.0f) - breath * 0.015f;

        this.body.xRot = chase ? 0.31f : breath * 0.012f;
        this.body.zRot = life * Mth.sin(time * 0.014f + 0.8f) * 0.012f;
        this.hips.y = -14.0f - breath * (chase ? 0.08f : 0.18f);
        this.head.yRot = state.yRot * Mth.DEG_TO_RAD * 1.12f
                + life * Mth.sin(time * 0.013f) * 0.05f;
        this.head.xRot = state.xRot * Mth.DEG_TO_RAD * 1.12f
                - (chase ? 0.13f : 0.0f);
        // Tete legerement inclinee en permanence : jamais tout a fait droite.
        this.head.zRot = 0.035f + life * Mth.sin(time * 0.008f) * 0.04f;

        applyBehaviorPose(behavior, rightStride, leftStride, walkSpeed, time);
        // Ces orientations sont exprimees dans le monde, pas relativement au
        // nouveau torse parent : la tete continue a regarder le joueur.
        this.head.xRot -= this.body.xRot;
        this.rightArm.xRot -= this.body.xRot;
        this.leftArm.xRot -= this.body.xRot;
        setGrip(chase ? 0.65f : 0.18f);
        if (!statue) {
            applyTremor(time);
        }
        if (state.grappling) {
            applyAttackPose(HostAttackAnimation.grapple(state.grappleProgress));
        } else if (state.recovering) {
            applyAttackPose(HostAttackAnimation.recovery(state.recoveryProgress));
        } else if (state.windingUp) {
            applyAttackPose(HostAttackAnimation.windup(state.windupProgress));
        } else if (state.lunging) {
            applyAttackPose(HostAttackAnimation.lunge(state.lungeProgress));
        }
    }

    private void applyAttackPose(HostAttackAnimation.Pose pose) {
        this.hips.y = pose.hipsY();
        this.body.xRot = pose.torsoPitch();
        this.body.zRot = 0.0f;
        this.head.xRot = pose.headPitch() - pose.torsoPitch();
        this.head.zRot = 0.035f;
        this.rightLeg.xRot = pose.thighPitch();
        this.leftLeg.xRot = pose.thighPitch();
        this.rightLeg.zRot = 0.0f;
        this.leftLeg.zRot = 0.0f;
        this.rightShin.xRot = pose.kneePitch();
        this.leftShin.xRot = pose.kneePitch();
        applyArm(this.rightArm, this.rightForearm, this.rightHand,
                pose.rightArm(), pose.torsoPitch());
        applyArm(this.leftArm, this.leftForearm, this.leftHand,
                pose.leftArm(), pose.torsoPitch());
        this.rightArm.zRot = pose.spread();
        this.leftArm.zRot = -pose.spread();
        setGrip(pose.grip());
    }

    private static void applyArm(ModelPart arm, ModelPart forearm, ModelPart hand,
                                 HostAttackAnimation.Arms pose, float torsoPitch) {
        arm.xRot = pose.shoulderPitch() - torsoPitch;
        arm.yRot = 0.0f;
        forearm.xRot = pose.elbowPitch();
        forearm.yRot = 0.0f;
        hand.xRot = pose.wristPitch();
    }

    private void setGrip(float grip) {
        float closed = MonsterAnimationMath.clamp01(grip);
        for (int index = 0; index < 3; index++) {
            float curl = -closed * (1.10f + index * 0.05f);
            this.rightFingers[index].xRot = curl;
            this.leftFingers[index].xRot = curl;
            this.rightFingers[index].getChild("tip").xRot = -0.90f * closed;
            this.leftFingers[index].getChild("tip").xRot = -0.90f * closed;
            this.rightFingers[index].zRot = (index - 1.0f) * 0.14f * (1.0f - closed);
            this.leftFingers[index].zRot = (index - 1.0f) * 0.14f * (1.0f - closed);
        }
        this.rightThumb.zRot = 0.55f - 1.15f * closed;
        this.leftThumb.zRot = -0.55f + 1.15f * closed;
        this.rightThumb.xRot = -0.85f * closed;
        this.leftThumb.xRot = -0.85f * closed;
    }

    private void applyBehaviorPose(HostBehavior behavior, float rightStride,
                                   float leftStride, float walkSpeed, float time) {
        switch (behavior) {
            case STALKING, INVESTIGATING, LURING -> {
                float listen = 0.5f + 0.5f * Mth.sin(time * 0.021f);
                this.body.xRot += 0.08f * listen;
                this.head.zRot += 0.055f * listen;
                this.rightForearm.xRot -= 0.12f * listen;
            }
            case HIDING, RETREATING -> {
                this.body.zRot -= 0.08f;
                this.head.yRot += 0.16f;
                this.rightArm.zRot = 0.12f;
                this.leftArm.zRot = -0.02f;
            }
            case ATTACKING -> {
                this.rightArm.xRot = -0.82f;
                this.leftArm.xRot = -0.58f;
                this.rightForearm.xRot = -0.74f;
                this.leftForearm.xRot = -0.48f;
            }
            case CHASING -> {
                this.rightArm.xRot = -0.44f + leftStride * 0.12f * walkSpeed;
                this.leftArm.xRot = -0.44f + rightStride * 0.12f * walkSpeed;
                this.rightForearm.xRot = -0.62f;
                this.leftForearm.xRot = -0.62f;
            }
            default -> {
                // Silhouette presque immobile hors chasse.
            }
        }
    }

    /**
     * Spasme bref a instant imprevisible : {@code twitchPulse} place le
     * tressaillement a une position pseudo-aleatoire differente a chaque cycle,
     * la ou l'ancien tremor tombait toujours au debut de la periode.
     */
    private void applyTremor(float time) {
        float envelope = MonsterAnimationMath.twitchPulse(
                time, TREMOR_PERIOD_TICKS, TREMOR_DURATION_TICKS, TREMOR_SEED);
        if (envelope <= 0.0f) {
            return;
        }
        float tremor = Mth.sin(time * 3.1f) * 0.05f * envelope;
        this.head.zRot += tremor * 1.4f;
        this.rightArm.zRot += tremor * 0.6f;
        this.leftArm.zRot -= tremor * 0.6f;
        this.rightForearm.yRot += tremor * 0.8f;
        this.rightHand.xRot -= Math.abs(tremor) * 1.6f;
    }

}

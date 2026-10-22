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

/** Observateur articule : statue au repos, predateur pendant le bond. */
@Environment(EnvType.CLIENT)
public final class WatcherModel extends EntityModel<WatcherRenderState> {

    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(PenumbraMod.id("watcher"), "main");

    private static final float TILT_CYCLE_TICKS = 200.0f;
    private static final float TILT_SNAP_TICKS = 2.0f;

    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart rightArm;
    private final ModelPart rightForearm;
    private final ModelPart leftArm;
    private final ModelPart leftForearm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public WatcherModel(ModelPart root) {
        super(root);
        this.head = root.getChild("head");
        this.body = root.getChild("body");
        this.rightArm = root.getChild("right_arm");
        this.rightForearm = this.rightArm.getChild("forearm");
        this.leftArm = root.getChild("left_arm");
        this.leftForearm = this.leftArm.getChild("forearm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-4.0f, -8.0f, -4.0f, 8.0f, 8.0f, 8.0f),
                PartPose.offset(0.0f, -30.0f, 0.0f));
        root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(16, 16)
                        .addBox(-4.0f, 0.0f, -2.0f, 8.0f, 14.0f, 4.0f),
                PartPose.offset(0.0f, -30.0f, 0.0f));

        PartDefinition rightArm = root.addOrReplaceChild("right_arm",
                CubeListBuilder.create().texOffs(40, 16)
                        .addBox(-1.5f, 0.0f, -1.5f, 3.0f, 6.0f, 3.0f),
                PartPose.offset(-5.5f, -29.0f, 0.0f));
        rightArm.addOrReplaceChild("forearm",
                CubeListBuilder.create().texOffs(40, 22)
                        .addBox(-1.5f, 0.0f, -1.5f, 3.0f, 6.0f, 3.0f),
                PartPose.offset(0.0f, 6.0f, 0.0f));

        PartDefinition leftArm = root.addOrReplaceChild("left_arm",
                CubeListBuilder.create().texOffs(52, 16)
                        .addBox(-1.5f, 0.0f, -1.5f, 3.0f, 6.0f, 3.0f),
                PartPose.offset(5.5f, -29.0f, 0.0f));
        leftArm.addOrReplaceChild("forearm",
                CubeListBuilder.create().texOffs(52, 22)
                        .addBox(-1.5f, 0.0f, -1.5f, 3.0f, 6.0f, 3.0f),
                PartPose.offset(0.0f, 6.0f, 0.0f));

        root.addOrReplaceChild("right_leg",
                CubeListBuilder.create().texOffs(0, 34)
                        .addBox(-1.5f, 0.0f, -1.5f, 3.0f, 16.0f, 3.0f),
                PartPose.offset(-1.7f, -16.0f, 0.0f));
        root.addOrReplaceChild("left_leg",
                CubeListBuilder.create().texOffs(12, 34)
                        .addBox(-1.5f, 0.0f, -1.5f, 3.0f, 16.0f, 3.0f),
                PartPose.offset(1.7f, -16.0f, 0.0f));

        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(WatcherRenderState state) {
        super.setupAnim(state);
        float time = state.ageInTicks;
        this.head.yRot = state.yRot * Mth.DEG_TO_RAD;
        this.head.xRot = state.xRot * Mth.DEG_TO_RAD;

        if (state.recovering) {
            applyRecovery(state.recoveryProgress);
            return;
        }
        if (state.windingUp) {
            applyWindup(state.windupProgress);
            return;
        }
        if (state.lunging) {
            applyLunge(state.lungeProgress, time);
            return;
        }

        float breath = MonsterAnimationMath.organicBreath(time, 0.019f);
        float proximity = MonsterAnimationMath.proximity(
                state.distanceToCameraSq, 6.0f, 18.0f);
        float cycle = time % TILT_CYCLE_TICKS;
        this.head.zRot = cycle < TILT_CYCLE_TICKS - TILT_SNAP_TICKS
                ? 0.11f * (cycle / (TILT_CYCLE_TICKS - TILT_SNAP_TICKS))
                : 0.0f;

        // Plus le joueur approche de la distance de disparition, plus la statue
        // se contracte sans jamais faire un vrai pas.
        this.body.xRot = breath * 0.007f - proximity * 0.10f;
        this.body.y = -30.0f - breath * 0.10f + proximity * 0.18f;
        this.head.xRot -= proximity * 0.07f;
        this.head.yRot *= 1.0f + proximity * 0.08f;
        this.rightArm.zRot = 0.03f + proximity * 0.07f;
        this.leftArm.zRot = -0.03f - proximity * 0.07f;
        this.rightForearm.xRot = -0.025f - proximity * 0.42f;
        this.leftForearm.xRot = -0.018f - proximity * 0.38f;
        this.rightLeg.xRot = proximity * 0.035f;
        this.leftLeg.xRot = -proximity * 0.025f;
    }

    private void applyWindup(float progress) {
        this.head.zRot = 0.12f + 0.46f * progress;
        this.head.xRot -= 0.22f * progress;
        this.body.xRot = -0.16f * progress;
        this.rightArm.xRot = -0.32f * progress;
        this.leftArm.xRot = -0.40f * progress;
        this.rightForearm.xRot = -0.70f * progress;
        this.leftForearm.xRot = -0.64f * progress;
    }

    private void applyRecovery(float progress) {
        float recoil = 1.0f - progress * 0.65f;
        this.body.xRot = -0.34f * recoil;
        this.head.xRot -= 0.26f * recoil;
        this.head.zRot = -0.34f * recoil;
        this.rightArm.xRot = -1.05f * recoil;
        this.leftArm.xRot = -0.86f * recoil;
        this.rightForearm.xRot = -0.92f * recoil;
        this.leftForearm.xRot = -1.12f * recoil;
        this.rightLeg.xRot = Mth.sin(progress * 18.0f) * 0.34f;
        this.leftLeg.xRot = -this.rightLeg.xRot;
    }

    private void applyLunge(float progress, float time) {
        MonsterAnimationMath.AttackPose pose =
                MonsterAnimationMath.attackPose(progress);
        float preparation = pose.anticipation();
        float strike = pose.strike();
        float impact = pose.impact();

        // La tete recule d'abord, puis reste verrouillee sur le visage.
        this.head.z = 0.65f * preparation - 2.4f * strike;
        this.head.xRot += 0.18f * preparation - 0.20f * strike;
        this.head.zRot = Mth.sin(time * 4.2f) * 0.10f * impact;
        this.body.xRot = -0.16f * preparation + 0.78f * strike;
        this.body.z = 0.35f * preparation - 1.45f * strike;
        this.body.zRot = Mth.sin(time * 2.7f) * 0.025f * strike;

        this.rightArm.xRot = 0.28f * preparation - 1.32f * strike;
        this.leftArm.xRot = 0.24f * preparation - 1.38f * strike;
        this.rightForearm.xRot = -0.34f * preparation - 0.92f * strike;
        this.leftForearm.xRot = -0.30f * preparation - 0.98f * strike;
        this.rightArm.yRot = -0.19f * strike;
        this.leftArm.yRot = 0.19f * strike;
        this.rightArm.zRot = 0.03f + 0.17f * strike;
        this.leftArm.zRot = -0.03f - 0.17f * strike;
        this.rightArm.z = -1.55f * strike;
        this.leftArm.z = -1.55f * strike;
        this.rightLeg.xRot = -0.20f * preparation - 0.18f * strike;
        this.leftLeg.xRot = 0.17f * preparation + 0.13f * strike;
    }
}

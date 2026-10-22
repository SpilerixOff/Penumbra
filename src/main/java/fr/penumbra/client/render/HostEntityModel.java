package fr.penumbra.client.render;

import fr.penumbra.PenumbraMod;
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

    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart rightArm;
    private final ModelPart rightForearm;
    private final ModelPart rightHand;
    private final ModelPart leftArm;
    private final ModelPart leftForearm;
    private final ModelPart leftHand;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public HostEntityModel(ModelPart root) {
        super(root);
        this.head = root.getChild("head");
        this.body = root.getChild("body");
        this.rightArm = root.getChild("right_arm");
        this.rightForearm = this.rightArm.getChild("forearm");
        this.rightHand = this.rightForearm.getChild("hand");
        this.leftArm = root.getChild("left_arm");
        this.leftForearm = this.leftArm.getChild("forearm");
        this.leftHand = this.leftForearm.getChild("hand");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
    }

    /**
     * Bras en trois os (bras, avant-bras, main) et cou apparent. Le cou occupe
     * texOffs(24,18), les mains texOffs(52,0) et (52,8) : zones libres de
     * l'atlas 64x64 historique, peintes par tools/generate_skins.py.
     */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Tete relevee de 2 px : le cou se voit, la silhouette culmine a 2,75 blocs.
        root.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-4.0f, -18.0f, -5.0f, 8.0f, 8.0f, 10.0f),
                PartPose.offset(0.0f, -26.0f, 0.0f));
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 20)
                        .addBox(-4.0f, 0.0f, -2.0f, 8.0f, 20.0f, 4.0f),
                PartPose.offset(0.0f, -34.0f, 0.0f));
        // Cou trop fin, solidaire du torse (il suit la respiration du corps).
        body.addOrReplaceChild("neck",
                CubeListBuilder.create().texOffs(24, 18)
                        .addBox(-2.0f, -3.0f, -2.0f, 4.0f, 3.0f, 4.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));

        // Epaules desaxees : la droite pend 1 px plus bas que la gauche.
        PartDefinition rightArm = root.addOrReplaceChild("right_arm",
                CubeListBuilder.create().texOffs(40, 0)
                        .addBox(-1.5f, 0.0f, -1.5f, 3.0f, 8.0f, 3.0f),
                PartPose.offset(-5.0f, -33.4f, 0.0f));
        PartDefinition rightForearm = rightArm.addOrReplaceChild("forearm",
                CubeListBuilder.create().texOffs(40, 8)
                        .addBox(-1.5f, 0.0f, -1.5f, 3.0f, 8.0f, 3.0f),
                PartPose.offset(0.0f, 8.0f, 0.0f));
        rightForearm.addOrReplaceChild("hand",
                CubeListBuilder.create().texOffs(52, 0)
                        .addBox(-1.5f, 0.0f, -1.5f, 3.0f, 3.0f, 3.0f),
                PartPose.offset(0.0f, 8.0f, 0.0f));

        PartDefinition leftArm = root.addOrReplaceChild("left_arm",
                CubeListBuilder.create().texOffs(40, 20)
                        .addBox(-1.5f, 0.0f, -1.5f, 3.0f, 8.0f, 3.0f),
                PartPose.offset(5.0f, -32.4f, 0.0f));
        PartDefinition leftForearm = leftArm.addOrReplaceChild("forearm",
                CubeListBuilder.create().texOffs(40, 28)
                        .addBox(-1.5f, 0.0f, -1.5f, 3.0f, 8.0f, 3.0f),
                PartPose.offset(0.0f, 8.0f, 0.0f));
        leftForearm.addOrReplaceChild("hand",
                CubeListBuilder.create().texOffs(52, 8)
                        .addBox(-1.5f, 0.0f, -1.5f, 3.0f, 3.0f, 3.0f),
                PartPose.offset(0.0f, 8.0f, 0.0f));

        root.addOrReplaceChild("right_leg",
                CubeListBuilder.create().texOffs(0, 46)
                        .addBox(-1.5f, 0.0f, -1.5f, 3.0f, 14.0f, 3.0f),
                PartPose.offset(-1.5f, -14.0f, 0.0f));
        root.addOrReplaceChild("left_leg",
                CubeListBuilder.create().texOffs(14, 46)
                        .addBox(-1.5f, 0.0f, -1.5f, 3.0f, 14.0f, 3.0f),
                PartPose.offset(1.5f, -14.0f, 0.0f));

        return LayerDefinition.create(mesh, 64, 64);
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
        this.body.y = -34.0f - breath * (chase ? 0.08f : 0.18f);
        this.head.yRot = state.yRot * Mth.DEG_TO_RAD * 1.12f
                + life * Mth.sin(time * 0.013f) * 0.05f;
        this.head.xRot = state.xRot * Mth.DEG_TO_RAD * 1.12f
                - (chase ? 0.13f : 0.0f);
        // Tete legerement inclinee en permanence : jamais tout a fait droite.
        this.head.zRot = 0.035f + life * Mth.sin(time * 0.008f) * 0.04f;

        applyBehaviorPose(behavior, rightStride, leftStride, walkSpeed, time);
        if (!statue) {
            applyTremor(time);
        }
        if (state.recovering) {
            applyRecovery(state.recoveryProgress);
        } else if (state.windingUp) {
            applyWindup(state.windupProgress);
        } else if (state.lunging) {
            applyLunge(state.lungeProgress, time);
        }
    }

    private void applyWindup(float progress) {
        this.head.zRot = 0.12f + 0.42f * progress;
        this.head.xRot -= 0.20f * progress;
        this.body.xRot = -0.18f * progress;
        this.rightArm.xRot = -0.38f * progress;
        this.leftArm.xRot = -0.24f * progress;
        this.rightForearm.xRot = -0.72f * progress;
        this.leftForearm.xRot = -0.55f * progress;
        this.rightHand.xRot = 0.36f * progress;
        this.leftHand.xRot = 0.30f * progress;
    }

    private void applyRecovery(float progress) {
        float recoil = 1.0f - progress * 0.65f;
        this.body.xRot = -0.32f * recoil;
        this.head.xRot -= 0.28f * recoil;
        this.head.zRot = -0.30f * recoil;
        this.rightArm.xRot = -1.12f * recoil;
        this.leftArm.xRot = -0.84f * recoil;
        this.rightForearm.xRot = -1.05f * recoil;
        this.leftForearm.xRot = -0.92f * recoil;
        this.rightHand.xRot = 0.38f;
        this.leftHand.xRot = 0.30f;
        this.rightLeg.xRot = Mth.sin(progress * 18.0f) * 0.32f;
        this.leftLeg.xRot = -this.rightLeg.xRot;
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

    private void applyLunge(float progress, float time) {
        MonsterAnimationMath.AttackPose pose =
                MonsterAnimationMath.attackPose(progress);
        float preparation = pose.anticipation();
        float strike = pose.strike();
        float impact = pose.impact();
        this.body.xRot = 0.18f * preparation - 0.62f * strike;
        this.body.y = -34.0f + 0.55f * preparation - 0.35f * strike;
        this.head.xRot += 0.20f * preparation - 0.38f * strike;
        this.head.zRot += Mth.sin(time * 3.8f)
                * (0.04f * strike + 0.13f * impact);
        this.rightArm.xRot = 0.36f * preparation - 1.70f * strike;
        this.leftArm.xRot = 0.30f * preparation - 1.78f * strike;
        this.rightForearm.xRot = -0.28f * preparation - 0.82f * strike;
        this.leftForearm.xRot = -0.36f * preparation - 0.74f * strike;
        // Les doigts se deplient completement au moment de la frappe.
        this.rightHand.xRot = -0.16f + 0.55f * strike - 0.30f * impact;
        this.leftHand.xRot = -0.12f + 0.50f * strike - 0.26f * impact;
        this.rightArm.yRot = -0.14f * strike;
        this.leftArm.yRot = 0.14f * strike;
        this.rightArm.zRot = 0.08f + 0.23f * strike;
        this.leftArm.zRot = -0.08f - 0.23f * strike;
        this.rightLeg.xRot = -0.22f * preparation + 0.56f * strike;
        this.leftLeg.xRot = 0.18f * preparation - 0.50f * strike;
    }
}

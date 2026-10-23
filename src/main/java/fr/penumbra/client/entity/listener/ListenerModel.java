package fr.penumbra.client.entity.listener;

import fr.penumbra.PenumbraMod;
import fr.penumbra.client.animation.MonsterAnimationMath;
import fr.penumbra.client.animation.ListenerAttackAnimation;
import fr.penumbra.entity.listener.ListenerBehavior;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Silhouette de L'Écouteur — 46 px, soit 2,875 blocs : la plus haute du mod.
 *
 * <p>Le visage est vide. À sa place, deux pavillons d'oreille plats de 7 px
 * s'ouvrent vers l'avant comme des paraboles, et une mâchoire décrochée pend
 * en permanence. La lecture est immédiate même en contre-jour : une tige verticale
 * surmontée d'un Y. Aucune paire d'yeux ne cherche le joueur — et c'est
 * précisément ce vide qui apprend la règle du jeu.</p>
 *
 * <p>Trois signatures d'animation portent la mécanique :</p>
 * <ul>
 *   <li><b>LISTENING</b> : immobilité minérale totale, sauf un balayage de tête
 *       de ±32° sur 3,5 s et de brefs tics d'oreille. Rien ne respire.</li>
 *   <li><b>SEEKING</b> : marche lente, <em>bras absolument morts</em> — ils ne
 *       balancent jamais. C'est le détail qui met mal à l'aise sans qu'on sache
 *       pourquoi.</li>
 *   <li><b>CLOSING</b> : torse cassé à 35° vers l'avant, tête <em>rejetée en
 *       arrière</em> pour tendre les pavillons devant elle, mâchoire grande
 *       ouverte, foulée longue.</li>
 * </ul>
 *
 * <p>Atlas 256×256 pour quatre texels par unité de modèle. Les {@code texOffs}
 * restent exprimés dans le repère 64×64 historique — {@code TEX_SCALE} fait la
 * conversion — donc la carte UV de {@code docs/ASSETS.md} reste valable : il
 * suffit d'y multiplier chaque coordonnée par quatre.</p>
 */
@Environment(EnvType.CLIENT)
public final class ListenerModel extends EntityModel<ListenerRenderState> {

    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(PenumbraMod.id("listener"), "main");

    /**
     * Densité de texture de l'atlas. Vérifié dans le bytecode de
     * {@code CubeDefinition.bake} en 26.1.2 : la taille de texture transmise au
     * cube vaut {@code atlas × texScale}, et le polygone <em>divise</em> ses UV
     * par cette valeur. Une échelle de {@code 0.25} avec un atlas de 256 donne
     * donc exactement la même disposition qu'un atlas de 64, mais avec quatre
     * fois plus de texels par face — et {@code texOffs} reste exprimé dans
     * l'ancien repère 64×64, ce qui laisse la carte UV documentée intacte.
     */
    private static final float TEX_SCALE = 0.25f;
    /** Côté de l'atlas : 64 / TEX_SCALE. */
    private static final int ATLAS = 256;

    /** Période du balayage d'écoute (3,5 s) et amplitude (±32°). */
    private static final float SWEEP_SPEED = 0.0299f;
    private static final float SWEEP_AMPLITUDE = 0.56f;

    /** Tic d'oreille : rare, très bref, jamais au même instant du cycle. */
    private static final float EAR_TWITCH_PERIOD = 96.0f;
    private static final float EAR_TWITCH_DURATION = 4.0f;
    private static final int EAR_TWITCH_SEED = 7717;

    private static final float BODY_PIVOT_Y = -37.0f;

    private final ModelPart head;
    private final ModelPart torso;
    private final ModelPart rightEar;
    private final ModelPart leftEar;
    private final ModelPart jaw;
    private final ModelPart body;
    private final ModelPart rightArm;
    private final ModelPart rightForearm;
    private final ModelPart rightHand;
    private final ModelPart leftArm;
    private final ModelPart leftForearm;
    private final ModelPart leftHand;
    private final ModelPart rightThigh;
    private final ModelPart rightShin;
    private final ModelPart leftThigh;
    private final ModelPart leftShin;

    public ListenerModel(ModelPart root) {
        super(root);
        this.torso = root.getChild("torso");
        this.head = this.torso.getChild("head");
        this.rightEar = this.head.getChild("right_ear");
        this.leftEar = this.head.getChild("left_ear");
        this.jaw = this.head.getChild("jaw");
        this.body = this.torso.getChild("body");
        this.rightArm = this.torso.getChild("right_arm");
        this.rightForearm = this.rightArm.getChild("forearm");
        this.rightHand = this.rightForearm.getChild("hand");
        this.leftArm = this.torso.getChild("left_arm");
        this.leftForearm = this.leftArm.getChild("forearm");
        this.leftHand = this.leftForearm.getChild("hand");
        this.rightThigh = root.getChild("right_leg");
        this.rightShin = this.rightThigh.getChild("shin");
        this.leftThigh = root.getChild("left_leg");
        this.leftShin = this.leftThigh.getChild("shin");
    }

    /**
     * Jambes en deux os, bras en trois os, tête portant deux oreilles et une
     * mâchoire. Occupation de l'atlas, en repère 64 (×4 dans le fichier réel) :
     * tête (0,0) 32×14 · oreilles (32,0) et (46,0) 14×13 · mâchoire (0,14) 24×9 ·
     * cou (24,14) 12×6 · mains (36,14) et (44,14) 8×6 · torse (0,24) 24×22 ·
     * bras et avant-bras (24..56, 24) 8×11 · cuisses et tibias (0..40, 46) 12×12 et 8×12.
     */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // All upper-body parts rotate around the same hips. The old detached
        // root-level shoulders could not follow a lateral sweep coherently.
        PartDefinition torso = root.addOrReplaceChild("torso", CubeListBuilder.create(),
                PartPose.offset(0.0f, -19.0f, 0.0f));

        // ——— Tête sans visage, surmontée de deux paraboles ———
        PartDefinition head = torso.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-4.0f, -6.0f, -4.0f, 8.0f, 6.0f, 8.0f,
                                CubeDeformation.NONE, TEX_SCALE, TEX_SCALE),
                PartPose.offset(0.0f, -21.0f, 0.0f));
        head.addOrReplaceChild("right_ear",
                CubeListBuilder.create().texOffs(32, 0)
                        .addBox(-1.0f, -7.0f, -3.0f, 1.0f, 7.0f, 6.0f,
                                CubeDeformation.NONE, TEX_SCALE, TEX_SCALE),
                PartPose.offsetAndRotation(-4.0f, -3.0f, 0.0f, 0.0f, 0.0f, -0.22f));
        head.addOrReplaceChild("left_ear",
                CubeListBuilder.create().texOffs(46, 0)
                        .addBox(0.0f, -7.0f, -3.0f, 1.0f, 7.0f, 6.0f,
                                CubeDeformation.NONE, TEX_SCALE, TEX_SCALE),
                PartPose.offsetAndRotation(4.0f, -3.0f, 0.0f, 0.0f, 0.0f, 0.26f));
        // Mâchoire décrochée : elle ne se referme jamais complètement.
        head.addOrReplaceChild("jaw",
                CubeListBuilder.create().texOffs(0, 14)
                        .addBox(-3.0f, 0.0f, -4.0f, 6.0f, 3.0f, 6.0f,
                                CubeDeformation.NONE, TEX_SCALE, TEX_SCALE),
                PartPose.offsetAndRotation(0.0f, -1.0f, -1.5f, 0.16f, 0.0f, 0.0f));

        PartDefinition body = torso.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 24)
                        .addBox(-4.0f, 0.0f, -2.0f, 8.0f, 18.0f, 4.0f,
                                CubeDeformation.NONE, TEX_SCALE, TEX_SCALE),
                PartPose.offset(0.0f, BODY_PIVOT_Y + 19.0f, 0.0f));
        body.addOrReplaceChild("neck",
                CubeListBuilder.create().texOffs(24, 14)
                        .addBox(-1.5f, -3.0f, -1.5f, 3.0f, 3.0f, 3.0f,
                                CubeDeformation.NONE, TEX_SCALE, TEX_SCALE),
                PartPose.offset(0.0f, 0.0f, 0.0f));

        // ——— Bras trop longs : les mains arrivent au niveau des genoux ———
        PartDefinition rightArm = torso.addOrReplaceChild("right_arm",
                CubeListBuilder.create().texOffs(24, 24)
                        .addBox(-1.0f, 0.0f, -1.0f, 2.0f, 9.0f, 2.0f,
                                CubeDeformation.NONE, TEX_SCALE, TEX_SCALE),
                PartPose.offset(-4.5f, -16.4f, 0.0f));
        PartDefinition rightForearm = rightArm.addOrReplaceChild("forearm",
                CubeListBuilder.create().texOffs(32, 24)
                        .addBox(-1.0f, 0.0f, -1.0f, 2.0f, 9.0f, 2.0f,
                                CubeDeformation.NONE, TEX_SCALE, TEX_SCALE),
                PartPose.offset(0.0f, 9.0f, 0.0f));
        rightForearm.addOrReplaceChild("hand",
                CubeListBuilder.create().texOffs(36, 14)
                        .addBox(-1.0f, 0.0f, -1.0f, 2.0f, 4.0f, 2.0f,
                                CubeDeformation.NONE, TEX_SCALE, TEX_SCALE),
                PartPose.offset(0.0f, 9.0f, 0.0f));

        // Épaule gauche 1 px plus haute : la silhouette n'est jamais symétrique.
        PartDefinition leftArm = torso.addOrReplaceChild("left_arm",
                CubeListBuilder.create().texOffs(40, 24)
                        .addBox(-1.0f, 0.0f, -1.0f, 2.0f, 9.0f, 2.0f,
                                CubeDeformation.NONE, TEX_SCALE, TEX_SCALE),
                PartPose.offset(4.5f, -17.4f, 0.0f));
        PartDefinition leftForearm = leftArm.addOrReplaceChild("forearm",
                CubeListBuilder.create().texOffs(48, 24)
                        .addBox(-1.0f, 0.0f, -1.0f, 2.0f, 9.0f, 2.0f,
                                CubeDeformation.NONE, TEX_SCALE, TEX_SCALE),
                PartPose.offset(0.0f, 9.0f, 0.0f));
        leftForearm.addOrReplaceChild("hand",
                CubeListBuilder.create().texOffs(44, 14)
                        .addBox(-1.0f, 0.0f, -1.0f, 2.0f, 4.0f, 2.0f,
                                CubeDeformation.NONE, TEX_SCALE, TEX_SCALE),
                PartPose.offset(0.0f, 9.0f, 0.0f));

        // ——— Jambes en deux os, genou légèrement inversé ———
        PartDefinition rightThigh = root.addOrReplaceChild("right_leg",
                CubeListBuilder.create().texOffs(0, 46)
                        .addBox(-1.5f, 0.0f, -1.5f, 3.0f, 9.0f, 3.0f,
                                CubeDeformation.NONE, TEX_SCALE, TEX_SCALE),
                PartPose.offset(-2.0f, -19.0f, 0.0f));
        rightThigh.addOrReplaceChild("shin",
                CubeListBuilder.create().texOffs(12, 46)
                        .addBox(-1.0f, 0.0f, -1.0f, 2.0f, 10.0f, 2.0f,
                                CubeDeformation.NONE, TEX_SCALE, TEX_SCALE),
                PartPose.offset(0.0f, 9.0f, 0.0f));
        PartDefinition leftThigh = root.addOrReplaceChild("left_leg",
                CubeListBuilder.create().texOffs(20, 46)
                        .addBox(-1.5f, 0.0f, -1.5f, 3.0f, 9.0f, 3.0f,
                                CubeDeformation.NONE, TEX_SCALE, TEX_SCALE),
                PartPose.offset(2.0f, -19.0f, 0.0f));
        leftThigh.addOrReplaceChild("shin",
                CubeListBuilder.create().texOffs(32, 46)
                        .addBox(-1.0f, 0.0f, -1.0f, 2.0f, 10.0f, 2.0f,
                                CubeDeformation.NONE, TEX_SCALE, TEX_SCALE),
                PartPose.offset(0.0f, 9.0f, 0.0f));

        return LayerDefinition.create(mesh, ATLAS, ATLAS);
    }

    @Override
    public void setupAnim(ListenerRenderState state) {
        super.setupAnim(state);
        ListenerBehavior behavior = ListenerBehavior.byId(state.behavior);
        float time = state.ageInTicks;
        float walkPos = state.walkAnimationPos;
        float walkSpeed = Math.min(state.walkAnimationSpeed, 1.0f);
        boolean listening = behavior == ListenerBehavior.LISTENING;
        boolean closing = behavior == ListenerBehavior.CLOSING;

        // Elle ne respire que lorsqu'elle se déplace. À l'écoute, rien ne bouge :
        // le contraste avec sa charge est toute la mise en scène.
        float life = listening ? 0.0f : 1.0f;
        float breath = life * MonsterAnimationMath.organicBreath(
                time, closing ? 0.094f : 0.026f);

        applyLegs(behavior, walkPos, walkSpeed);
        applyArms(behavior, walkPos, walkSpeed, breath);
        applyTorso(behavior, breath, time, life);
        applyHead(behavior, state, time, listening, closing);
        applyEars(time, listening, closing);
        // The ears orient before the head, which itself leads the server body.
        float earAim = Mth.clamp(state.hearingOffset * Mth.DEG_TO_RAD, -0.65f, 0.65f);
        this.rightEar.yRot = earAim;
        this.leftEar.yRot = earAim * 0.72f;
        if (closing && state.warningProgress < 1.0f) applyWarning(state.warningProgress);
        if (behavior == ListenerBehavior.STRIKING) applyStrike(state.strikeTicks);
        // Counter-rotate the head locally so its pitch stays readable as the
        // hip-parent leans. It remains attached to the same neck throughout.
        this.head.xRot -= this.torso.xRot;
    }

    private void applyLegs(ListenerBehavior behavior, float walkPos, float walkSpeed) {
        float gait = behavior == ListenerBehavior.CLOSING ? 0.74f : 0.52f;
        float amplitude = switch (behavior) {
            case CLOSING -> 1.42f;
            case LEAVING -> 0.78f;
            case LISTENING, STRIKING -> 0.0f;
            default -> 0.92f;
        };
        float rightStride = Mth.cos(walkPos * gait);
        float leftStride = Mth.cos(walkPos * gait + Mth.PI);

        this.rightThigh.xRot = rightStride * amplitude * walkSpeed;
        // Boiterie : la jambe gauche traîne toujours un peu, plus encore en fuite.
        float limp = behavior == ListenerBehavior.LEAVING ? 0.58f : 0.82f;
        this.leftThigh.xRot = leftStride * amplitude * limp * walkSpeed;
        // Genou inversé : le tibia se replie vers l'arrière, jamais vers l'avant.
        this.rightShin.xRot = Math.max(0.0f, -rightStride) * amplitude * 0.86f * walkSpeed;
        this.leftShin.xRot = Math.max(0.0f, -leftStride) * amplitude * 0.74f * walkSpeed;
        this.rightThigh.zRot = 0.035f;
        this.leftThigh.zRot = -0.055f;
    }

    /**
     * Bras morts hors charge : ils ne balancent pas d'un degré. Un humain qui
     * marche balance les bras ; elle non — c'est le premier signal d'anomalie.
     */
    private void applyArms(ListenerBehavior behavior, float walkPos,
                           float walkSpeed, float breath) {
        if (behavior == ListenerBehavior.CLOSING) {
            // Bras rejetés loin derrière, mains ouvertes vers l'avant.
            float swing = Mth.cos(walkPos * 0.74f) * 0.18f * walkSpeed;
            this.rightArm.xRot = 0.92f + swing;
            this.leftArm.xRot = 0.86f - swing;
            this.rightArm.zRot = 0.28f;
            this.leftArm.zRot = -0.24f;
            this.rightForearm.xRot = -0.52f;
            this.leftForearm.xRot = -0.46f;
            this.rightHand.xRot = -0.74f;
            this.leftHand.xRot = -0.68f;
            this.rightHand.zRot = 0.36f;
            this.leftHand.zRot = -0.3f;
            return;
        }
        this.rightArm.xRot = 0.0f;
        this.leftArm.xRot = 0.0f;
        this.rightArm.zRot = 0.055f;
        this.leftArm.zRot = -0.04f;
        this.rightForearm.xRot = -0.02f + breath * 0.012f;
        this.leftForearm.xRot = -0.035f - breath * 0.01f;
        // Doigts refermés vers l'intérieur, comme une main qui vient de lâcher.
        this.rightHand.xRot = -0.2f;
        this.leftHand.xRot = -0.15f;
        this.rightHand.zRot = 0.12f;
        this.leftHand.zRot = -0.08f;
    }

    private void applyTorso(ListenerBehavior behavior, float breath,
                            float time, float life) {
        float lean = switch (behavior) {
            case CLOSING -> 0.62f;
            case SEEKING -> 0.14f;
            case LEAVING -> 0.34f;
            case LISTENING, STRIKING -> 0.0f;
        };
        this.torso.xRot = lean + breath * 0.014f;
        this.torso.zRot = life * Mth.sin(time * 0.011f) * 0.014f;
        this.torso.y = -19.0f - breath * (behavior == ListenerBehavior.CLOSING ? 0.1f : 0.16f);
    }

    /**
     * La tête ne suit jamais le joueur. Le serveur aligne déjà corps et tête sur
     * le dernier bruit ; le modèle n'ajoute que le balayage d'écoute et, en
     * charge, un rejet en arrière qui tend les pavillons vers l'avant.
     */
    private void applyHead(ListenerBehavior behavior, ListenerRenderState state,
                           float time, boolean listening, boolean closing) {
        float sweep = listening ? Mth.sin(time * SWEEP_SPEED) * SWEEP_AMPLITUDE : 0.0f;
        // state.yRot vaut l'écart tête/corps : il reste proche de zéro par
        // construction, mais on le compose pour rester correct en interpolation.
        this.head.yRot = state.yRot * Mth.DEG_TO_RAD * 0.5f + sweep;
        if (closing) {
            // Tête rejetée en arrière, mâchoire grande ouverte : elle « voit » par
            // les oreilles et n'a plus aucune raison de faire semblant.
            this.head.xRot = -0.46f;
            this.jaw.xRot = 0.78f;
            this.head.zRot = Mth.sin(time * 0.42f) * 0.05f;
            return;
        }
        // Inclinaison permanente : la tête n'est jamais tout à fait droite.
        this.head.xRot = 0.09f + (listening ? Mth.sin(time * 0.019f) * 0.07f : 0.0f);
        this.head.zRot = 0.11f + (listening ? Mth.cos(time * SWEEP_SPEED) * 0.09f : 0.0f);
        this.jaw.xRot = 0.16f + (behavior == ListenerBehavior.LEAVING ? 0.1f : 0.0f);
    }

    /** Tics d'oreille : brefs, rares, décorrélés de tout le reste. */
    private void applyEars(float time, boolean listening, boolean closing) {
        if (closing) {
            // Pavillons grands ouverts vers l'avant pendant la charge.
            this.rightEar.zRot = -0.44f;
            this.leftEar.zRot = 0.48f;
            this.rightEar.xRot = -0.2f;
            this.leftEar.xRot = -0.2f;
            return;
        }
        float twitch = MonsterAnimationMath.twitchPulse(
                time, EAR_TWITCH_PERIOD, EAR_TWITCH_DURATION, EAR_TWITCH_SEED);
        float amount = twitch <= 0.0f ? 0.0f : twitch * (listening ? 0.24f : 0.13f);
        this.rightEar.zRot = -0.22f - amount;
        this.leftEar.zRot = 0.26f + amount * 0.7f;
        this.rightEar.xRot = amount * 0.4f;
        this.leftEar.xRot = 0.0f;
    }

    private void applyWarning(float progress) {
        ListenerAttackAnimation.Warning cue = ListenerAttackAnimation.warning(progress);
        // Stop the gait during the same twelve ticks in which the server stops
        // navigation. Ear -> head -> trunk, then the held pose breaks into a rush.
        this.rightThigh.xRot = 0.0f;
        this.leftThigh.xRot = 0.0f;
        this.rightShin.xRot = 0.0f;
        this.leftShin.xRot = 0.0f;
        this.torso.xRot = 0.62f * cue.torso();
        this.torso.zRot = -0.09f * cue.torso() * (1.0f - cue.torso());
        this.head.xRot = 0.09f - 0.55f * cue.head();
        this.head.zRot = 0.11f * (1.0f - cue.head());
        this.jaw.xRot = 0.16f + 0.62f * cue.torso();
        this.rightArm.xRot = 0.92f * cue.torso();
        this.leftArm.xRot = 0.86f * cue.torso();
        this.rightForearm.xRot = -0.02f - 0.50f * cue.torso();
        this.leftForearm.xRot = -0.035f - 0.425f * cue.torso();
        this.rightEar.zRot = -0.22f - 0.22f * cue.ear();
        this.leftEar.zRot = 0.26f + 0.22f * cue.ear();
        this.rightEar.xRot = -0.20f * cue.ear();
        this.leftEar.xRot = -0.20f * cue.ear();
    }

    private void applyStrike(float ticks) {
        ListenerAttackAnimation.Sweep pose = ListenerAttackAnimation.sweep(ticks);
        float reach = pose.reach();
        float active = pose.active();
        this.torso.xRot = pose.torsoPitch();
        this.torso.yRot = pose.torsoYaw();
        this.torso.zRot = pose.torsoRoll();
        this.torso.y = -19.0f;
        this.head.xRot = 0.09f - 0.55f * active;
        this.head.yRot = -pose.torsoYaw() * 0.82f;
        this.head.zRot = 0.11f - 0.22f * reach;
        this.jaw.xRot = 0.26f + 0.52f * active;
        // Right arm sweeps across the chest; the other arm trails like a dead
        // weight. Neither hand attaches to or moves the player.
        this.rightArm.xRot = 0.92f * active - 2.40f * reach;
        this.rightArm.yRot = -0.38f * active + 1.10f * reach;
        this.rightArm.zRot = 0.055f + 0.52f * reach;
        this.rightForearm.xRot = -0.02f - 0.50f * active + 0.40f * reach;
        this.rightHand.xRot = -0.20f - 0.54f * active + 0.60f * reach;
        this.rightHand.zRot = 0.12f + 0.34f * reach;
        this.leftArm.xRot = 0.86f * active + 0.22f * reach;
        this.leftArm.zRot = -0.04f - 0.25f * active;
        this.leftForearm.xRot = -0.035f - 0.425f * active;
        this.leftHand.xRot = -0.15f - 0.53f * active;
        this.rightEar.zRot = -0.22f - 0.22f * active;
        this.leftEar.zRot = 0.26f + 0.22f * active;
        this.rightEar.xRot = -0.20f * active;
        this.leftEar.xRot = -0.20f * active;
        this.rightEar.yRot = 0.0f;
        this.leftEar.yRot = 0.0f;
    }
}

package fr.penumbra.client.entity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/**
 * Snapshot client de L'Absent.
 *
 * <p>La texture est résolue une seule fois par profil par le renderer, puis
 * transportée ici afin que le modèle ne consulte jamais le réseau pendant
 * l'animation. Les paramètres de locomotion sont tous synchronisés par le
 * serveur : deux clients voient donc les mêmes erreurs dans l'imitation.</p>
 */
@Environment(EnvType.CLIENT)
public final class AbsentRenderState extends HumanoidRenderState {
    public int absentPhase;
    public float phaseTicks;
    public int mimicBehavior;
    public int gaitSignature;
    public long locomotionSeed;
    public float revealProgress;
    public float imitationError;
    public boolean movementImitationEnabled;
    public boolean falseCompanion;
    public boolean usingPlayerSkin;
    public boolean slimArms;
    public int modelPartsMask;
    public boolean lunging;
    public Identifier skinTexture;
}

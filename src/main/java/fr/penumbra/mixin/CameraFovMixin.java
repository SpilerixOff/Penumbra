package fr.penumbra.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import fr.penumbra.client.ambient.AmbientFearManager;
import fr.penumbra.client.binoculars.BinocularsManager;
import net.minecraft.client.Camera;

/**
 * Cœur du zoom des jumelles (SPEC2 §8 — CORRIGÉ en 0.5.0).
 * <p>
 * <b>Pourquoi l'ancienne injection ne zoomait pas</b> (bug « touche C sans
 * effet ») : l'ancien mixin divisait les champs {@code fov}/{@code hudFov} au
 * {@code RETURN} de {@code Camera.update(DeltaTracker)}. Or le désassemblage
 * du bytecode 26.1.2 (jar loom-cache {@code minecraft-merged 26.1.2}) montre
 * que {@code update} construit la matrice de projection AVANT de rendre la
 * main : {@code calculateFov → PUTFIELD fov → … → GETFIELD fov →
 * setupPerspective(largeur, hauteur, depthFar, fov, …)} — le tout À
 * L'INTÉRIEUR de {@code update}. La division au RETURN arrivait donc APRÈS
 * que la projection ait été figée : le champ divisé n'était plus jamais relu
 * pour le rendu du monde (seul {@code hudFov}, copié plus tard par
 * {@code extractRenderState}, voyait la division — d'où une main légèrement
 * affectée et un monde strictement inchangé).
 * </p>
 * <p>
 * <b>Correctif</b> : injection au {@code RETURN} de {@code calculateFov(F)F}
 * — la valeur de retour est divisée À LA SOURCE, avant {@code PUTFIELD fov},
 * donc avant {@code setupPerspective} ET avant
 * {@code createProjectionMatrixForCulling} : projection et frustum de culling
 * restent cohérents. {@code calculateHudFov} n'est volontairement PAS touché :
 * la main garde son FOV vanilla pendant le zoom (comportement de la longue-vue
 * vanilla ; {@code binocularsHideHand} reste disponible pour la masquer).
 * </p>
 * <p>
 * Mêmes garanties qu'avant : la valeur n'est jamais persistée — aucune
 * écriture dans {@code options.fov()}, jamais {@code options.save()}. Dès que
 * le facteur composé (zoom jumelles × dérive de tension 0.5.0) revient à
 * exactement {@code 1.0f}, la frame suivante retrouve le FOV vanilla exact.
 * Les autres modificateurs (sprint, effets, fluide, mort) sont appliqués par
 * {@code modifyFovBasedOnDeathOrFluid} DANS {@code calculateFov} avant notre
 * RETURN : la division se compose proprement par-dessus.
 * </p>
 */
@Mixin(Camera.class)
public abstract class CameraFovMixin {

    /**
     * Divise le FOV calculé par le facteur composé : zoom jumelles
     * (≈ ×4 à progression pleine, défaut config) × dérive de tension
     * ({@link AmbientFearManager#getFovDriftFactor()}, ±0,4–0,8 % en
     * HUNT/CRISIS si {@code distortionEnabled}). Sans AUCUN effet quand le
     * produit vaut exactement {@code 1.0f} : hors zoom, hors HUNT/CRISIS, ou
     * options désactivées.
     */
    @Inject(method = "calculateFov(F)F", at = @At("RETURN"), cancellable = true)
    private void penumbra$applyZoomToFov(float partialTick, CallbackInfoReturnable<Float> cir) {
        float factor = BinocularsManager.getZoomFactor() * AmbientFearManager.getFovDriftFactor();
        if (factor != 1.0f) {
            cir.setReturnValue(cir.getReturnValueF() / factor);
        }
    }
}

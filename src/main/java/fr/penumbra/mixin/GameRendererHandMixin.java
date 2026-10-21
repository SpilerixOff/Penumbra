package fr.penumbra.mixin;

import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fr.penumbra.client.binoculars.BinocularsManager;
import fr.penumbra.config.PenumbraConfig;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;

/**
 * Option « cacher la main » des jumelles ({@code binocularsHideHand}).
 * <p>
 * Méthode cible vérifiée par javap en 26.1.2 :
 * {@code private void GameRenderer.renderItemInHand(net.minecraft.client.renderer.state.level.CameraRenderState, float, org.joml.Matrix4fc)}.
 * Elle est annulée en HEAD uniquement quand le zoom est actif ET l'option est
 * activée ; dans tous les autres cas le rendu vanilla de la main est inchangé.
 * L'annulation est sans état : dès que le zoom se termine, la main réapparaît
 * à la frame suivante.
 * </p>
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererHandMixin {

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void penumbra$hideHandWhileZoomed(CameraRenderState cameraState, float tickDelta,
                                              Matrix4fc matrix, CallbackInfo ci) {
        if (PenumbraConfig.get().binocularsHideHand && BinocularsManager.isZoomActive()) {
            ci.cancel();
        }
    }
}

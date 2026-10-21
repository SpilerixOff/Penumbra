package fr.penumbra.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fr.penumbra.client.binoculars.BinocularsManager;
import net.minecraft.client.MouseHandler;

/**
 * Zoom à la molette pendant que les jumelles sont actives (SPEC2 §8).
 * <p>
 * Cible vérifiée en 26.1.2 (javap) :
 * {@code private void MouseHandler.onScroll(long window, double xOffset, double yOffset)},
 * qui gère notamment le défilement de la barre d'inventaire. Quand
 * {@link BinocularsManager#handleScroll(double)} consomme l'événement (zoom
 * actif + {@code binocularsScrollZoom}), le callback est annulé en HEAD : la
 * barre d'inventaire ne change plus pendant le zoom. Hors zoom, la méthode
 * retourne {@code false} et le comportement vanilla est strictement inchangé.
 * </p>
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerScrollMixin {

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void penumbra$binocularsScrollZoom(long window, double xOffset, double yOffset,
                                               CallbackInfo ci) {
        if (BinocularsManager.handleScroll(yOffset)) {
            ci.cancel();
        }
    }
}

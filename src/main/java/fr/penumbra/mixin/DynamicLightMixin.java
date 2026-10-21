package fr.penumbra.mixin;

import fr.penumbra.client.light.DynamicLights;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndLightGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Injecte la lumière des sources mobiles dans le rendu (0.19.0).
 *
 * <p><b>Pourquoi cette méthode-là.</b> {@code LevelRenderer.getLightCoords} est
 * le point de passage unique de tout ce qui est éclairé : le terrain y arrive
 * par {@code ModelBlockRenderer}, les entités par
 * {@code EntityRenderer.getPackedLightCoords}, et les particules directement.
 * Un seul point d'injection éclaire donc la scène entière — sol, murs,
 * créatures et fumée comprises. Vérifié au désassemblage du jar 26.1.2 : trente-
 * quatre classes appellent cette méthode.</p>
 *
 * <p><b>Format de la valeur.</b> La lumière est empaquetée en un seul entier :
 * la lumière de bloc occupe les bits 4 à 7, la lumière du ciel les bits 20 à 23,
 * chacune multipliée par seize. La constante vanilla du rendu émissif,
 * {@code 15728880}, se relit d'ailleurs comme {@code 15 << 4 | 15 << 20} — ce
 * qui confirme la disposition sans avoir à la deviner.</p>
 *
 * <p>On ne fait que <b>relever</b> la lumière de bloc : jamais la baisser,
 * jamais toucher au ciel. Une zone déjà mieux éclairée que la torche reste
 * exactement comme elle était.</p>
 */
@Mixin(LevelRenderer.class)
public abstract class DynamicLightMixin {

    private static final int BLOCK_LIGHT_SHIFT = 4;
    private static final int BLOCK_LIGHT_MASK = 0xF << BLOCK_LIGHT_SHIFT;

    @Inject(
            method = "getLightCoords(Lnet/minecraft/client/renderer/LevelRenderer$BrightnessGetter;"
                    + "Lnet/minecraft/world/level/BlockAndLightGetter;"
                    + "Lnet/minecraft/world/level/block/state/BlockState;"
                    + "Lnet/minecraft/core/BlockPos;)I",
            at = @At("RETURN"),
            cancellable = true)
    private static void penumbra$addDynamicLight(
            LevelRenderer.BrightnessGetter brightnessGetter,
            BlockAndLightGetter level,
            BlockState state,
            BlockPos pos,
            CallbackInfoReturnable<Integer> info) {
        int dynamic = DynamicLights.blockLightAt(pos);
        if (dynamic <= 0) {
            return;
        }
        int packed = info.getReturnValueI();
        int current = (packed & BLOCK_LIGHT_MASK) >> BLOCK_LIGHT_SHIFT;
        if (dynamic <= current) {
            return;
        }
        info.setReturnValue((packed & ~BLOCK_LIGHT_MASK)
                | (dynamic << BLOCK_LIGHT_SHIFT));
    }
}

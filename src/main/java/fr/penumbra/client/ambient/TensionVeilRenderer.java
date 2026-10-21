package fr.penumbra.client.ambient;

import fr.penumbra.PenumbraMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * Voile périphérique de tension (0.5.0) — implémentation de {@code fogEnabled}.
 * <p>
 * Trois bandes concentriques d'assombrissement le long des bords de l'écran,
 * d'alpha décroissant vers le centre (même technique de vignette validée que
 * {@code BinocularsOverlayRenderer} et même API {@code HudElementRegistry}
 * que le flash de {@code JumpscareManager} — aucun nouvel accès API 26.1.2).
 * L'intensité vient de {@link AmbientFearManager#getVeilIntensity()} (lissée
 * côté tick) : le rétrécissement du champ visuel est progressif et jamais
 * commenté — le joueur doute d'abord de son écran, ensuite de lui.
 * Alpha maximal total ≈ 0x30 par bord en CRISIS : un voile, pas un tunnel.
 * </p>
 */
@Environment(EnvType.CLIENT)
public final class TensionVeilRenderer {

    /** Épaisseurs des trois bandes, en fraction du petit côté de l'écran. */
    private static final float[] BAND_FRACTIONS = {0.020f, 0.045f, 0.080f};
    /** Alphas des trois bandes à intensité 1.0, du bord vers l'intérieur. */
    private static final int[] BAND_ALPHAS = {0x30, 0x1C, 0x0C};

    private TensionVeilRenderer() {
    }

    /** Enregistre l'élément de HUD. Appelé par {@code PenumbraClient.onInitializeClient}. */
    public static void register() {
        HudElementRegistry.addLast(PenumbraMod.id("tension_veil"), (extractor, deltaTracker) -> {
            // 0.17.0 : en CRISIS, le voile respire au rythme du battement cardiaque
            // (PerceptionLayer.getVeilPulse() vaut exactement 1.0 partout ailleurs).
            float intensity = AmbientFearManager.getVeilIntensity()
                    * PerceptionLayer.getVeilPulse();
            if (intensity <= 0.01f) {
                return;
            }
            intensity = Math.min(1.25f, intensity);
            Minecraft client = Minecraft.getInstance();
            int width = client.getWindow().getGuiScaledWidth();
            int height = client.getWindow().getGuiScaledHeight();
            int shortSide = Math.min(width, height);
            int inset = 0;
            for (int band = 0; band < BAND_FRACTIONS.length; band++) {
                int thickness = Math.max(1, Math.round(shortSide * BAND_FRACTIONS[band] * intensity));
                int alpha = Mth.clamp(Math.round(BAND_ALPHAS[band] * intensity), 0, 0xFF);
                if (alpha <= 0) {
                    continue;
                }
                int color = alpha << 24; // noir, alpha seul
                // Quatre bandes de cadre à l'inset courant (sans recouvrement des coins).
                extractor.fill(inset, inset, width - inset, inset + thickness, color);
                extractor.fill(inset, height - inset - thickness, width - inset, height - inset, color);
                extractor.fill(inset, inset + thickness, inset + thickness, height - inset - thickness, color);
                extractor.fill(width - inset - thickness, inset + thickness,
                        width - inset, height - inset - thickness, color);
                inset += thickness;
            }
        });
    }
}

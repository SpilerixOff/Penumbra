package fr.penumbra.client.ambient;

import fr.penumbra.PenumbraMod;
import fr.penumbra.client.config.ClientComfort;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Grain and scanlines only. All creatures are rendered as world entities. */
@Environment(EnvType.CLIENT)
public final class PerceptionOverlayRenderer {

    private PerceptionOverlayRenderer() {
    }

    /** Enregistre le grain du HUD. Appelé par {@code PenumbraClient}. */
    public static void register() {
        HudElementRegistry.addLast(PenumbraMod.id("perception_grain"),
                (extractor, deltaTracker) -> {
                    if (!ClientComfort.grainEnabled() || PerceptionLayer.grainIntensity() <= 0.01f) {
                        return;
                    }
                    Minecraft client = Minecraft.getInstance();
                    int width = client.getWindow().getGuiScaledWidth();
                    int height = client.getWindow().getGuiScaledHeight();
                    drawScanlines(extractor, width, height);
                    drawGrain(extractor, width, height);
                });

        // Creatures are now rendered in the world, including peripheral encounters.
    }

    /**
     * Lignes horizontales une ligne sur quatre. Alpha très faible et fixe : la
     * grille ne se déplace pas d'une frame à l'autre, il n'y a donc aucun
     * papillotement — seulement une texture d'image usée.
     */
    private static void drawScanlines(
            GuiGraphicsExtractor extractor,
            int width, int height) {
        int alpha = PerceptionLayer.scanlineAlpha();
        if (alpha <= 0) {
            return;
        }
        int color = alpha << 24;
        int spacing = PerceptionLayer.scanlineSpacing();
        for (int y = 0; y < height; y += spacing) {
            extractor.fill(0, y, width, y + 1, color);
        }
    }

    /** Grain fixe pendant trois ticks, réparti sur tout le cadre. */
    private static void drawGrain(
            GuiGraphicsExtractor extractor,
            int width, int height) {
        int alpha = PerceptionLayer.grainAlpha();
        if (alpha <= 0) {
            return;
        }
        int color = alpha << 24;
        int count = PerceptionLayer.grainCount();
        for (int index = 0; index < count; index++) {
            int size = PerceptionLayer.grainSize(index);
            int x = Math.round(PerceptionLayer.grainX(index) * (width - size));
            int y = Math.round(PerceptionLayer.grainY(index) * (height - size));
            extractor.fill(x, y, x + size, y + size, color);
        }
    }

}

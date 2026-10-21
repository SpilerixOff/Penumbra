package fr.penumbra.client.ambient;

import fr.penumbra.PenumbraMod;
import fr.penumbra.client.config.ClientComfort;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Rendu de la {@link PerceptionLayer} : grain, lignes de balayage et silhouette
 * rémanente périphérique. Même technique que {@code TensionVeilRenderer}
 * ({@code extractor.fill} + {@code HudElementRegistry.addLast}) : aucun nouvel
 * accès API en 26.1.2, aucune texture supplémentaire.
 *
 * <p>Ordre d'empilement voulu : le grain et les lignes passent <em>sous</em> le
 * voile périphérique, la rémanence <em>par-dessus</em> — une ombre au bord de
 * l'écran doit rester lisible même quand la vision s'est déjà rétrécie.</p>
 */
@Environment(EnvType.CLIENT)
public final class PerceptionOverlayRenderer {

    /** Largeur de la silhouette rémanente, en fraction de la largeur d'écran. */
    private static final float SILHOUETTE_WIDTH = 0.055f;
    /** Hauteur totale de la silhouette, en fraction de la hauteur d'écran. */
    private static final float SILHOUETTE_HEIGHT = 0.30f;

    private PerceptionOverlayRenderer() {
    }

    /** Enregistre les deux éléments de HUD. Appelé par {@code PenumbraClient}. */
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

        HudElementRegistry.addLast(PenumbraMod.id("perception_afterimage"),
                (extractor, deltaTracker) -> {
                    if (!PerceptionLayer.afterimageVisible()) {
                        return;
                    }
                    int alpha = PerceptionLayer.afterimageAlpha();
                    if (alpha <= 0) {
                        return;
                    }
                    Minecraft client = Minecraft.getInstance();
                    drawSilhouette(extractor,
                            client.getWindow().getGuiScaledWidth(),
                            client.getWindow().getGuiScaledHeight(),
                            alpha);
                });
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

    /**
     * Silhouette humanoïde grossière (tête, épaules, torse fuselé) plaquée sur
     * le bord de l'écran du bon côté. Cinq rectangles suffisent : à cette taille
     * et à cet alpha, le cerveau complète la forme tout seul — c'est exactement
     * ce que l'on veut lui faire faire.
     */
    private static void drawSilhouette(
            GuiGraphicsExtractor extractor,
            int width, int height, int alpha) {
        int color = alpha << 24;
        int bodyWidth = Math.max(6, Math.round(width * SILHOUETTE_WIDTH));
        int bodyHeight = Math.max(24, Math.round(height * SILHOUETTE_HEIGHT));
        int centerY = Math.round(height * PerceptionLayer.afterimageHeight());
        int top = centerY - bodyHeight / 2;

        // Moitié de la silhouette collée au bord : elle « sort » du cadre.
        boolean right = PerceptionLayer.afterimageSide() >= 0;
        int outerX = right ? width - bodyWidth : 0;

        int headHeight = Math.max(4, bodyHeight / 5);
        int headWidth = Math.max(4, bodyWidth * 2 / 3);
        int headX = right ? width - headWidth : 0;
        int shoulderHeight = Math.max(2, bodyHeight / 12);

        // Tête, puis épaules légèrement plus larges, puis torse qui s'affine.
        extractor.fill(headX, top, headX + headWidth, top + headHeight, color);
        extractor.fill(outerX, top + headHeight,
                outerX + bodyWidth, top + headHeight + shoulderHeight, color);
        int trunkTop = top + headHeight + shoulderHeight;
        int trunkBottom = top + bodyHeight;
        int narrow = Math.max(1, bodyWidth / 4);
        int trunkWidth = bodyWidth - narrow;
        int trunkX = right ? width - trunkWidth : 0;
        extractor.fill(trunkX, trunkTop, trunkX + trunkWidth, trunkBottom, color);

        // Un bras trop long qui dépasse sous le torse, comme les vraies créatures.
        int armWidth = Math.max(2, bodyWidth / 5);
        int armX = right ? width - trunkWidth : trunkWidth - armWidth;
        extractor.fill(armX, trunkTop + shoulderHeight,
                armX + armWidth, trunkBottom + bodyHeight / 8, color);
    }
}

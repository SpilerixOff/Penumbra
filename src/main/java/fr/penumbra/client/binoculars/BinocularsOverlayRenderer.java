package fr.penumbra.client.binoculars;

import java.util.Locale;

import fr.penumbra.config.PenumbraConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Overlay de caméscope analogique des jumelles.
 *
 * <p>La fenêtre visible conserve toujours un ratio 4:3 et s'adapte à la
 * résolution ainsi qu'à l'échelle de GUI. Le rendu ne dépend d'aucune texture :
 * masque arrondi, boîtier, scanlines, grain, dropout, viseur, REC, timecode,
 * batterie et zoom sont composés avec de petits rectangles et le texte vanilla.
 * Le coût reste borné par la hauteur du viseur et ne crée aucun objet dans les
 * boucles par frame.</p>
 *
 * <p>Accessibilité : lorsque {@link PenumbraConfig#flashesEnabled} est
 * désactivé, le témoin REC reste fixe, le grain devient statique et les
 * dropouts animés disparaissent. Aucune variation ne remplit l'écran et les
 * effets analogiques conservent volontairement un faible contraste.</p>
 */
@Environment(EnvType.CLIENT)
public final class BinocularsOverlayRenderer {

    private static final float PROGRESS_EPSILON = 0.001f;
    private static final double VIEW_ASPECT = 4.0 / 3.0;

    private static final int RGB_MASK = 0x030405;
    private static final int RGB_HOUSING = 0x25282B;
    private static final int RGB_HOUSING_EDGE = 0x45494D;
    private static final int RGB_HUD = 0xD8E1D5;
    private static final int RGB_REC = 0xE83939;
    private static final int RGB_BATTERY_GOOD = 0x8ED67A;
    private static final int RGB_BATTERY_LOW = 0xE5A34B;
    private static final int RGB_BATTERY_CRITICAL = 0xE85B50;

    private static long cachedTimecodeSecond = Long.MIN_VALUE;
    private static String cachedTimecode = "00:00:00";
    private static int cachedZoomTenth = Integer.MIN_VALUE;
    private static String cachedZoom = "GROSS. 4,0×";

    private BinocularsOverlayRenderer() {
    }

    /**
     * Dessine la vue caméscope pour la frame courante.
     *
     * @param g        extracteur graphique du HUD
     * @param width    largeur GUI mise à l'échelle
     * @param height   hauteur GUI mise à l'échelle
     * @param progress progression lissée d'ouverture (0..1)
     * @param timeMs   horloge monotone du client
     * @param cfg      configuration active
     */
    public static void render(GuiGraphicsExtractor g, int width, int height,
                              float progress, long timeMs, PenumbraConfig cfg) {
        if (g == null || cfg == null || !cfg.binocularsOverlayEnabled
                || progress <= PROGRESS_EPSILON || width < 8 || height < 8) {
            return;
        }

        float p = Math.min(1.0f, progress);
        int maskAlpha = Math.round(255.0f * p);
        if (maskAlpha <= 0) {
            return;
        }

        // Un cadre 4:3 aussi grand que possible, sans jamais toucher les bords.
        int maxViewWidth = Math.max(4, Math.round(width * 0.88f));
        int maxViewHeight = Math.max(3, Math.round(height * 0.86f));
        int targetWidth = Math.min(maxViewWidth, (int) Math.floor(maxViewHeight * VIEW_ASPECT));
        int targetHeight = Math.max(3, (int) Math.floor(targetWidth / VIEW_ASPECT));
        if (targetHeight > maxViewHeight) {
            targetHeight = maxViewHeight;
            targetWidth = Math.max(4, (int) Math.floor(targetHeight * VIEW_ASPECT));
        }

        // L'entrée ressemble à l'ouverture d'un écran orientable de caméscope.
        float openingScale = 0.92f + 0.08f * p;
        int viewWidth = Math.max(4, Math.round(targetWidth * openingScale));
        int viewHeight = Math.max(3, Math.round(viewWidth / (float) VIEW_ASPECT));
        int centerX = Math.round(width * 0.5f + BinocularsManager.getBreathOffsetX());
        int centerY = Math.round(height * 0.5f + BinocularsManager.getBreathOffsetY());
        int left = clamp(centerX - viewWidth / 2, 0, Math.max(0, width - viewWidth));
        int top = clamp(centerY - viewHeight / 2, 0, Math.max(0, height - viewHeight));
        int right = Math.min(width, left + viewWidth);
        int bottom = Math.min(height, top + viewHeight);
        viewWidth = right - left;
        viewHeight = bottom - top;
        if (viewWidth < 4 || viewHeight < 3) {
            return;
        }

        int radius = clamp(Math.round(Math.min(viewWidth, viewHeight) * 0.035f), 2, 8);
        int maskColor = argb(RGB_MASK, maskAlpha);

        drawRoundedOutsideMask(g, width, height, left, top, right, bottom, radius, maskColor);
        drawCamcorderHousing(g, width, height, left, top, right, bottom, radius, maskAlpha);
        drawRoundedFrame(g, left, top, right, bottom, radius, 2,
                argb(RGB_HOUSING_EDGE, scaleAlpha(230, maskAlpha)));
        drawVignette(g, left, top, right, bottom, maskAlpha);
        drawScanlines(g, left, top, right, bottom, radius, maskAlpha);
        drawAnalogNoise(g, left, top, right, bottom, radius, timeMs, maskAlpha,
                cfg.flashesEnabled);

        if (cfg.binocularsLensDirt) {
            drawStaticLensDust(g, left, top, right, bottom, radius, maskAlpha);
        }

        drawFocusBrackets(g, left, top, right, bottom, maskAlpha);
        drawReadout(g, left, top, right, bottom, timeMs, maskAlpha, cfg.flashesEnabled);
    }

    /**
     * Masque tout ce qui se trouve hors du rectangle arrondi. Deux fills par
     * ligne du viseur suffisent ; les zones haute et basse sont dessinées en une
     * seule fois.
     */
    private static void drawRoundedOutsideMask(GuiGraphicsExtractor g, int width, int height,
                                               int left, int top, int right, int bottom,
                                               int radius, int color) {
        if (top > 0) {
            g.fill(0, 0, width, top, color);
        }
        if (bottom < height) {
            g.fill(0, bottom, width, height, color);
        }
        for (int y = top; y < bottom; y++) {
            int inset = roundedInset(y, top, bottom, radius);
            int rowLeft = left + inset;
            int rowRight = right - inset;
            if (rowLeft > 0) {
                g.fill(0, y, rowLeft, y + 1, color);
            }
            if (rowRight < width) {
                g.fill(rowRight, y, width, y + 1, color);
            }
        }
    }

    /**
     * Silhouette du boîtier : rails métalliques, poignée latérale et petites
     * vis. Elle reste dans les marges noires et donne l'impression de tenir un
     * écran de caméscope plutôt que de regarder dans deux lentilles.
     */
    private static void drawCamcorderHousing(GuiGraphicsExtractor g, int width, int height,
                                             int left, int top, int right, int bottom,
                                             int radius, int maskAlpha) {
        int rail = clamp((right - left) / 64, 2, 5);
        int housing = argb(RGB_HOUSING, scaleAlpha(235, maskAlpha));
        int edge = argb(RGB_HOUSING_EDGE, scaleAlpha(210, maskAlpha));

        g.fill(Math.max(0, left - rail), top + radius, left, bottom - radius, housing);
        g.fill(right, top + radius, Math.min(width, right + rail), bottom - radius, housing);
        g.fill(left + radius, Math.max(0, top - rail), right - radius, top, housing);
        g.fill(left + radius, bottom, right - radius, Math.min(height, bottom + rail), housing);

        int rightMargin = width - right;
        if (rightMargin >= rail + 7) {
            int gripLeft = right + rail + 1;
            int gripRight = Math.min(width, gripLeft + Math.max(4, rail + 2));
            int gripTop = top + (bottom - top) / 3;
            int gripBottom = bottom - (bottom - top) / 4;
            g.fill(gripLeft, gripTop, gripRight, gripBottom, housing);
            g.fill(gripLeft, gripTop, gripRight, Math.min(gripBottom, gripTop + 1), edge);
            int buttonSize = Math.min(2, Math.max(1, gripRight - gripLeft - 1));
            g.fill(gripLeft + 1, gripTop + 3, gripLeft + 1 + buttonSize,
                    gripTop + 3 + buttonSize, argb(RGB_REC, scaleAlpha(170, maskAlpha)));
        }

        int screwColor = argb(0x777B7D, scaleAlpha(135, maskAlpha));
        drawScrew(g, left - rail, top + radius, width, height, screwColor);
        drawScrew(g, right + rail - 1, bottom - radius - 1, width, height, screwColor);
    }

    private static void drawScrew(GuiGraphicsExtractor g, int x, int y,
                                  int width, int height, int color) {
        if (x >= 0 && x < width && y >= 0 && y < height) {
            g.fill(x, y, Math.min(width, x + 1), Math.min(height, y + 1), color);
        }
    }

    /** Dessine un contour arrondi de {@code thickness} pixels à l'intérieur. */
    private static void drawRoundedFrame(GuiGraphicsExtractor g, int left, int top,
                                         int right, int bottom, int radius,
                                         int thickness, int color) {
        for (int y = top; y < bottom; y++) {
            int inset = roundedInset(y, top, bottom, radius);
            int rowLeft = left + inset;
            int rowRight = right - inset;
            boolean cap = y < top + thickness || y >= bottom - thickness;
            if (cap) {
                if (rowRight > rowLeft) {
                    g.fill(rowLeft, y, rowRight, y + 1, color);
                }
            } else {
                g.fill(rowLeft, y, Math.min(rowRight, rowLeft + thickness), y + 1, color);
                g.fill(Math.max(rowLeft, rowRight - thickness), y, rowRight, y + 1, color);
            }
        }
    }

    /** Vignette rectangulaire douce, limitée aux bords du viseur. */
    private static void drawVignette(GuiGraphicsExtractor g, int left, int top,
                                     int right, int bottom, int maskAlpha) {
        int maxBand = Math.max(1, Math.min(right - left, bottom - top) / 28);
        for (int band = 0; band < 3; band++) {
            int inset = band * maxBand;
            int thickness = maxBand;
            int alpha = scaleAlpha(30 - band * 8, maskAlpha);
            int color = argb(0x000000, alpha);
            int x1 = left + inset;
            int y1 = top + inset;
            int x2 = right - inset;
            int y2 = bottom - inset;
            if (x2 - x1 <= thickness * 2 || y2 - y1 <= thickness * 2) {
                break;
            }
            g.fill(x1, y1, x2, y1 + thickness, color);
            g.fill(x1, y2 - thickness, x2, y2, color);
            g.fill(x1, y1 + thickness, x1 + thickness, y2 - thickness, color);
            g.fill(x2 - thickness, y1 + thickness, x2, y2 - thickness, color);
        }
    }

    /** Une ligne sombre très légère tous les trois pixels, sans animation. */
    private static void drawScanlines(GuiGraphicsExtractor g, int left, int top,
                                      int right, int bottom, int radius, int maskAlpha) {
        int alpha = scaleAlpha(13, maskAlpha);
        int color = argb(0x071012, alpha);
        for (int y = top + 2; y < bottom - 2; y += 3) {
            int inset = roundedInset(y, top, bottom, radius) + 2;
            int x1 = left + inset;
            int x2 = right - inset;
            if (x2 > x1) {
                g.fill(x1, y, x2, y + 1, color);
            }
        }
    }

    /**
     * Grain analogique à faible contraste. Avec les flashs coupés, le seed est
     * fixe : la texture ne scintille pas. Avec les flashs actifs, il change six
     * fois par seconde et un dropout d'une frame apparaît environ toutes les
     * trois secondes.
     */
    private static void drawAnalogNoise(GuiGraphicsExtractor g, int left, int top,
                                        int right, int bottom, int radius, long timeMs,
                                        int maskAlpha, boolean animated) {
        int innerWidth = right - left - 8;
        int innerHeight = bottom - top - 8;
        if (innerWidth <= 0 || innerHeight <= 0) {
            return;
        }

        long slice = animated ? timeMs / 160L : 0L;
        int seed = mix((int) (slice ^ (slice >>> 32)), right - left + (bottom - top) * 31);
        int area = (right - left) * (bottom - top);
        int specks = clamp(area / 2400, 6, 18);
        int noiseColor = argb(0xCAD7CF, scaleAlpha(13, maskAlpha));
        for (int i = 0; i < specks; i++) {
            int h = mix(seed, i * 0x9E3779B9);
            int y = top + 4 + Math.floorMod(h >>> 10, innerHeight);
            int rowInset = roundedInset(y, top, bottom, radius) + 3;
            int rowWidth = Math.max(1, right - left - rowInset * 2);
            int x = left + rowInset + Math.floorMod(h, rowWidth);
            int length = 1 + Math.floorMod(h >>> 24, 3);
            g.fill(x, y, Math.min(right - rowInset, x + length), y + 1, noiseColor);
        }

        // Aucun dropout si flashesEnabled=false : comportement sans scintillement.
        if (animated && Math.floorMod(seed, 19) == 0) {
            int y = top + 6 + Math.floorMod(seed >>> 8, Math.max(1, bottom - top - 12));
            int inset = roundedInset(y, top, bottom, radius) + 3;
            int x1 = left + inset;
            int x2 = right - inset;
            if (x2 > x1) {
                int dropoutColor = argb(0xC9D8D3, scaleAlpha(29, maskAlpha));
                g.fill(x1, y, x2, y + 1, dropoutColor);
                int fragment = Math.max(6, (x2 - x1) / 5);
                int fragmentX = x1 + Math.floorMod(seed >>> 16, Math.max(1, x2 - x1 - fragment));
                g.fill(fragmentX, Math.min(bottom - 1, y + 1),
                        Math.min(x2, fragmentX + fragment), Math.min(bottom, y + 2),
                        argb(0xE7E0D3, scaleAlpha(18, maskAlpha)));
            }
        }
    }

    /** Quelques salissures fixes, minuscules, sans masquer le centre du viseur. */
    private static void drawStaticLensDust(GuiGraphicsExtractor g, int left, int top,
                                           int right, int bottom, int radius,
                                           int maskAlpha) {
        int w = right - left;
        int h = bottom - top;
        if (w < 40 || h < 30) {
            return;
        }
        int color = argb(0x17100B, scaleAlpha(19, maskAlpha));
        for (int i = 0; i < 7; i++) {
            int hash = mix(w * 31 + h, i * 0x45D9F3B);
            int x = left + 6 + Math.floorMod(hash, Math.max(1, w - 12));
            int y = top + 6 + Math.floorMod(hash >>> 12, Math.max(1, h - 12));
            int nx = x - (left + w / 2);
            int ny = y - (top + h / 2);
            if (nx * nx * 9 < w * w && ny * ny * 9 < h * h) {
                x = left + 5 + Math.floorMod(hash >>> 5, Math.max(1, w / 5));
            }
            int inset = roundedInset(y, top, bottom, radius) + 3;
            x = clamp(x, left + inset, Math.max(left + inset, right - inset - 1));
            int size = 1 + Math.floorMod(hash >>> 27, 2);
            g.fill(x, y, Math.min(right - inset, x + size), Math.min(bottom, y + size), color);
        }
    }

    /** Quatre petits angles de mise au point, volontairement loin du réticule. */
    private static void drawFocusBrackets(GuiGraphicsExtractor g, int left, int top,
                                          int right, int bottom, int maskAlpha) {
        int w = right - left;
        int h = bottom - top;
        if (w < 72 || h < 54) {
            return;
        }
        int cx = (left + right) / 2;
        int cy = (top + bottom) / 2;
        int halfW = Math.max(12, w / 9);
        int halfH = Math.max(8, h / 9);
        int arm = clamp(Math.min(w, h) / 24, 3, 8);
        int color = argb(RGB_HUD, scaleAlpha(126, maskAlpha));

        drawCorner(g, cx - halfW, cy - halfH, arm, 1, 1, color);
        drawCorner(g, cx + halfW, cy - halfH, arm, -1, 1, color);
        drawCorner(g, cx - halfW, cy + halfH, arm, 1, -1, color);
        drawCorner(g, cx + halfW, cy + halfH, arm, -1, -1, color);
    }

    private static void drawCorner(GuiGraphicsExtractor g, int x, int y, int arm,
                                   int horizontalDirection, int verticalDirection, int color) {
        int hx = x + horizontalDirection * arm;
        int vy = y + verticalDirection * arm;
        g.fill(Math.min(x, hx), y, Math.max(x, hx) + 1, y + 1, color);
        g.fill(x, Math.min(y, vy), x + 1, Math.max(y, vy) + 1, color);
    }

    /** REC, timecode, mode VHS, batterie et facteur de zoom. */
    private static void drawReadout(GuiGraphicsExtractor g, int left, int top,
                                    int right, int bottom, long timeMs,
                                    int maskAlpha, boolean animated) {
        int w = right - left;
        int h = bottom - top;
        if (w < 96 || h < 58) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        Font font = client.font;
        int pad = clamp(Math.min(w, h) / 24, 4, 8);
        int textColor = argb(RGB_HUD, scaleAlpha(226, maskAlpha));
        int shadowColor = argb(0x000000, scaleAlpha(72, maskAlpha));

        boolean recVisible = !animated || ((timeMs / 650L) & 1L) == 0L;
        if (recVisible) {
            int dot = h >= 90 ? 3 : 2;
            int recY = top + pad + Math.max(0, (font.lineHeight - dot) / 2);
            g.fill(left + pad, recY, left + pad + dot, recY + dot,
                    argb(RGB_REC, scaleAlpha(245, maskAlpha)));
            drawReadableText(g, font, "ENR.", left + pad + dot + 3, top + pad,
                    argb(RGB_REC, scaleAlpha(245, maskAlpha)), shadowColor);
        }

        String timecode = timecode(BinocularsManager.getRecordingElapsedMillis());
        int timeX = right - pad - font.width(timecode);
        drawReadableText(g, font, timecode, timeX, top + pad, textColor, shadowColor);

        if (w >= 150) {
            String mode = "VHS-C  SP";
            int modeX = (left + right - font.width(mode)) / 2;
            drawReadableText(g, font, mode, modeX, top + pad, textColor, shadowColor);
        }

        String zoom = zoomText(BinocularsManager.getSessionZoom());
        drawReadableText(g, font, zoom, left + pad, bottom - pad - font.lineHeight,
                textColor, shadowColor);
        drawBattery(g, right - pad, bottom - pad - 7,
                BinocularsManager.getBatteryLevel(), maskAlpha);
    }

    private static void drawReadableText(GuiGraphicsExtractor g, Font font, String text,
                                         int x, int y, int color, int backdrop) {
        int width = font.width(text);
        g.fill(x - 1, y - 1, x + width + 1, y + font.lineHeight, backdrop);
        g.text(font, text, x, y, color, true);
    }

    /** Batterie segmentée, alignée par son bord droit. */
    private static void drawBattery(GuiGraphicsExtractor g, int right, int top,
                                    float level, int maskAlpha) {
        int width = 22;
        int height = 7;
        int left = right - width;
        int outline = argb(RGB_HUD, scaleAlpha(218, maskAlpha));
        g.fill(left, top, right, top + 1, outline);
        g.fill(left, top + height - 1, right, top + height, outline);
        g.fill(left, top + 1, left + 1, top + height - 1, outline);
        g.fill(right - 1, top + 1, right, top + height - 1, outline);
        g.fill(right, top + 2, right + 2, top + height - 2, outline);

        float clamped = Math.max(0.0f, Math.min(1.0f, level));
        int segments = Math.max(0, Math.min(4, (int) Math.ceil(clamped * 4.0f - 0.001f)));
        int rgb = clamped <= 0.20f ? RGB_BATTERY_CRITICAL
                : clamped <= 0.45f ? RGB_BATTERY_LOW : RGB_BATTERY_GOOD;
        int fill = argb(rgb, scaleAlpha(225, maskAlpha));
        for (int i = 0; i < segments; i++) {
            int x = left + 2 + i * 5;
            g.fill(x, top + 2, x + 4, top + height - 2, fill);
        }
    }

    private static String timecode(long elapsedMs) {
        long second = Math.max(0L, elapsedMs / 1000L);
        if (second == cachedTimecodeSecond) {
            return cachedTimecode;
        }
        cachedTimecodeSecond = second;
        long hours = (second / 3600L) % 100L;
        long minutes = (second / 60L) % 60L;
        long seconds = second % 60L;
        cachedTimecode = String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds);
        return cachedTimecode;
    }

    private static String zoomText(double zoom) {
        int tenth = Math.max(0, (int) Math.round(zoom * 10.0));
        if (tenth == cachedZoomTenth) {
            return cachedZoom;
        }
        cachedZoomTenth = tenth;
        cachedZoom = "GROSS. " + (tenth / 10) + "," + (tenth % 10) + "×";
        return cachedZoom;
    }

    /**
     * Décalage horizontal du bord arrondi à la ligne donnée. Le calcul se fait
     * sur le centre du pixel afin de ne laisser aucun trou dans les coins.
     */
    private static int roundedInset(int y, int top, int bottom, int radius) {
        if (radius <= 0) {
            return 0;
        }
        double dy;
        if (y < top + radius) {
            dy = top + radius - (y + 0.5);
        } else if (y >= bottom - radius) {
            dy = (y + 0.5) - (bottom - radius);
        } else {
            return 0;
        }
        double chord = Math.sqrt(Math.max(0.0, radius * radius - dy * dy));
        return Math.max(0, (int) Math.ceil(radius - chord));
    }

    private static int argb(int rgb, int alpha) {
        return (clamp(alpha, 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    private static int scaleAlpha(int baseAlpha, int maskAlpha) {
        return (baseAlpha * maskAlpha + 127) / 255;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Petit mélangeur entier déterministe de style Murmur3. */
    private static int mix(int a, int b) {
        int h = a ^ b ^ 0x7F4A7C15;
        h ^= h >>> 16;
        h *= 0x7FEB352D;
        h ^= h >>> 15;
        h *= 0x846CA68B;
        h ^= h >>> 16;
        return h;
    }
}

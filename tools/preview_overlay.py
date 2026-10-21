#!/usr/bin/env python3
"""Maquette exacte de l'overlay jumelles Penumbra 0.3.0.
Porte la geometrie de BinocularsOverlayRenderer.java (memes formules) :
  gap = 0.03*w ; rx = (0.70*w - gap)/4 ; ry = 0.38*h
  centres cx +/- (rx + gap/2), cy = h/2 ; scale = 1.0 (progress = 1)
  masque noir opaque dehors, lisere 2px #0A0A0A, vignette interne, taches lensDirt.
"""
import math, random
from PIL import Image, ImageDraw, ImageFilter

def scene(w, h, zoom):
    """Fausse scene Minecraft-esque : ciel, sol, arbre lointain."""
    img = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(img)
    for y in range(h):
        t = y / h
        if t < 0.55:  # ciel couchant
            r = int(90 + 60 * t); g_ = int(70 + 50 * t); b = int(80 + 20 * t)
        else:         # sol
            r, g_, b = 45, 38, 32
        d.line([(0, y), (w, y)], fill=(r, g_, b))
    # colline + arbre lointain (zoom simule par la taille)
    s = zoom
    tx, ty = w * 0.62, h * 0.55
    d.rectangle([tx - 4*s, ty - 40*s, tx + 4*s, ty], fill=(30, 24, 18))          # tronc
    d.ellipse([tx - 26*s, ty - 90*s, tx + 26*s, ty - 34*s], fill=(22, 40, 24))   # feuillage
    d.ellipse([w*0.28-16*s, ty-8*s, w*0.28+16*s, ty+8*s], fill=(38, 34, 28))     # rocher
    return img.filter(ImageFilter.GaussianBlur(1.2))

def overlay_geometry(w, h):
    gap = 0.03 * w
    rx = (0.70 * w - gap) / 4.0
    ry = 0.38 * h
    cx, cy = w / 2.0, h / 2.0
    return rx, ry, cx - (rx + gap / 2), cx + (rx + gap / 2), cy

def render(out, w, h):
    base = scene(w, h, zoom=4.0)
    rx, ry, cxL, cxR, cy = overlay_geometry(w, h)
    img = Image.new("RGBA", (w, h))
    img.paste(base.convert("RGBA"), (0, 0))
    px = img.load()
    ring = 2.0
    rng = random.Random(42)
    specks = []
    for side, cxL_ in ((0, cxL), (1, cxR)):
        n = 6
        for i in range(n):
            rho = 0.25 + 0.55 * rng.random()
            a = rng.uniform(0, 2 * math.pi)
            specks.append((cxL_ + rho * rx * 0.92 * math.cos(a),
                           cy + rho * ry * 0.92 * math.sin(a),
                           rng.choice([2, 3, 4]), rng.choice([(0, 0, 0, 12), (20, 11, 4, 16)])))
    for y in range(h):
        for x in range(w):
            best = 1e9
            inside = False
            for cx in (cxL, cxR):
                dxn = (x + 0.5 - cx) / rx
                dyn = (y + 0.5 - cy) / ry
                r2 = dxn * dxn + dyn * dyn
                if r2 <= 1.0:
                    inside = True
                    edge = math.sqrt(r2)          # 1.0 = bord
                    dist_px = (1.0 - edge) * min(rx, ry)
                    if dist_px < 9.0:             # vignette interne
                        if dist_px < 2: a = 0x18
                        elif dist_px < 7: a = 0x10
                        else: a = 0x08
                        p = px[x, y]
                        px[x, y] = (0, 0, 0, 0) if False else (p[0]*(255-a)//255, p[1]*(255-a)//255, p[2]*(255-a)//255, 255)
                    continue
                dxn2 = (x + 0.5 - cx) / (rx + ring)
                dyn2 = (y + 0.5 - cy) / (ry + ring)
                if dxn2*dxn2 + dyn2*dyn2 <= 1.0:
                    best = min(best, 1)            # lisere
                else:
                    best = min(best, 0)            # masque
            if not inside:
                px[x, y] = (10, 10, 10, 255) if best == 1 else (0, 0, 0, 255)
    d = ImageDraw.Draw(img)
    for sx, sy, sr, col in specks:
        d.ellipse([sx - sr/2, sy - sr/2, sx + sr/2, sy + sr/2], fill=col)
    img.convert("RGB").save(out)
    print("OK", out, f"{w}x{h}", f"rx={rx:.0f} ry={ry:.0f} gap={0.03*w:.0f}")

import os
os.makedirs("docs/screenshots", exist_ok=True)
render("docs/screenshots/overlay_preview_1920x1080.png", 1920, 1080)
render("docs/screenshots/overlay_preview_2560x1080_21-9.png", 2560, 1080)
render("docs/screenshots/overlay_preview_1440x900_16-10.png", 1440, 900)

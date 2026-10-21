#!/usr/bin/env python3
"""Peint les skins d'entités Penumbra (0.14.0) au texel près.

Identité visuelle : « Quelque chose apprend à être vous. » Chaque créature est
une imitation imparfaite — peau cireuse gris-beige, coutures d'assemblage,
vestige de tunique turquoise délavée, asymétries délibérées. Les zones UV
correspondent exactement aux modèles Java (HostEntityModel, WatcherModel…).

Usage :  python3 tools/generate_skins.py [--out src/main/resources/assets/penumbra/textures/entity]
Déterministe (seeds fixes) : relancer produit exactement les mêmes fichiers.
"""

import argparse
import os
import random

from PIL import Image, ImageDraw

# ---------------------------------------------------------------- palettes --
WAX = (188, 182, 172)          # peau cireuse
WAX_DARK = (156, 149, 140)
SEAM = (118, 110, 102)         # coutures d'assemblage
PIT = (28, 25, 24)             # orbites creuses
GLINT = (232, 230, 224)        # reflet d'iris minuscule
TEAL = (58, 104, 100)          # vestige de tunique (turquoise volée, délavée)
TEAL_WORN = (96, 118, 114)
DENIM = (56, 60, 72)           # souvenir de jeans
DENIM_WORN = (78, 80, 90)
CHAR = (42, 40, 46)            # charbon de l'Observateur
CHAR_LIGHT = (58, 56, 64)
EYE_WHITE = (238, 240, 234)
ANCIENT = (168, 158, 138)      # chair momifiée de l'Originel
ANCIENT_DARK = (120, 110, 92)
CRACK = (66, 58, 48)
SICK = (172, 178, 152)         # teint verdâtre du Contrefait
SICK_DARK = (134, 140, 116)
FLESH = (176, 162, 154)        # chair du Rampant
FLESH_DARK = (136, 122, 116)
SPINE = (94, 82, 78)
CHITIN = (52, 46, 50)          # Cavaleuse
CHITIN_LIGHT = (74, 66, 72)
APPARATUS = (54, 48, 44)       # Projectionniste
APPARATUS_LIGHT = (82, 74, 66)
BRASS = (140, 118, 76)
LENS_GLOW = (214, 206, 178)


def faces(u, v, w, h, d):
    """Rectangles (x0, y0, x1exclusif, y1exclusif) des 6 faces d'un addBox."""
    return {
        "top": (u + d, v, u + d + w, v + d),
        "bottom": (u + d + w, v, u + d + 2 * w, v + d),
        "right": (u, v + d, u + d, v + d + h),
        "front": (u + d, v + d, u + d + w, v + d + h),
        "left": (u + d + w, v + d, u + d + w + d, v + d + h),
        "back": (u + d + w + d, v + d, u + 2 * d + 2 * w, v + d + h),
    }


def noise_fill(px, rect, base, jitter, rng, dark_edge=0):
    x0, y0, x1, y1 = rect
    for y in range(y0, y1):
        for x in range(x0, x1):
            j = rng.randint(-jitter, jitter)
            r, g, b = (max(0, min(255, c + j)) for c in base)
            if dark_edge and (x == x0 or x == x1 - 1 or y == y0 or y == y1 - 1):
                r, g, b = (max(0, c - dark_edge) for c in (r, g, b))
            px[x, y] = (r, g, b, 255)


def fill_all(px, boxes, base, jitter, rng, dark_edge=0):
    for box in boxes:
        for rect in faces(*box).values():
            noise_fill(px, rect, base, jitter, rng, dark_edge)


def vline(px, x, y0, y1, color):
    for y in range(y0, y1):
        px[x, y] = (*color, 255)


def hline(px, x0, x1, y, color):
    for x in range(x0, x1):
        px[x, y] = (*color, 255)


def dot(px, x, y, color):
    px[x, y] = (*color, 255)


def new_canvas():
    return Image.new("RGBA", (64, 64), (0, 0, 0, 0))


# ------------------------------------------------------------------- l'Hôte --
def paint_host():
    """HostEntityModel : tête 8x8x10, corps 8x20x4, cou 4x3x4 (24,18),
    bras 3x8x3 (40,0/8/20/28), mains 3x3x3 (52,0/8), jambes 3x14x3 (0,46/14,46)."""
    img = new_canvas()
    px = img.load()
    rng = random.Random(20260722)

    head = (0, 0, 8, 8, 10)
    body = (0, 20, 8, 20, 4)
    neck = (24, 18, 4, 3, 4)
    arms = [(40, 0, 3, 8, 3), (40, 8, 3, 8, 3), (40, 20, 3, 8, 3), (40, 28, 3, 8, 3)]
    hands = [(52, 0, 3, 3, 3), (52, 8, 3, 3, 3)]
    legs = [(0, 46, 3, 14, 3), (14, 46, 3, 14, 3)]

    # Peau cireuse partout.
    fill_all(px, [head, neck] + arms + hands, WAX, 7, rng)
    fill_all(px, [body], WAX, 6, rng)
    fill_all(px, legs, WAX, 6, rng)

    f = faces(*head)
    fx, fy = f["front"][0], f["front"][1]          # (10, 10), 8x8
    # Orbites creuses asymétriques : l'œil droit du modèle est 1 px plus bas.
    dot(px, fx + 1, fy + 2, WAX_DARK); dot(px, fx + 2, fy + 2, WAX_DARK)   # arcade
    dot(px, fx + 5, fy + 3, WAX_DARK); dot(px, fx + 6, fy + 3, WAX_DARK)
    dot(px, fx + 1, fy + 3, PIT); dot(px, fx + 2, fy + 3, PIT)
    dot(px, fx + 5, fy + 4, PIT); dot(px, fx + 6, fy + 4, PIT)
    dot(px, fx + 1, fy + 3, GLINT)                 # un seul reflet d'iris
    # Cernes : la peau s'affaisse sous les orbites.
    dot(px, fx + 1, fy + 4, WAX_DARK); dot(px, fx + 2, fy + 4, WAX_DARK)
    dot(px, fx + 5, fy + 5, WAX_DARK); dot(px, fx + 6, fy + 5, WAX_DARK)
    # Bouche : trait fin, un peu trop large, qui tombe d'un côté.
    hline(px, fx + 2, fx + 7, fy + 6, SEAM)
    dot(px, fx + 7, fy + 7, SEAM)
    # Couture verticale sur le front : le visage assemblé en deux moitiés.
    vline(px, fx + 3, fy, fy + 2, SEAM)
    # Coutures du crâne (dessus) et de la nuque.
    t = f["top"]
    hline(px, t[0], t[2], t[1] + 4, SEAM)
    vline(px, t[0] + 4, t[1], t[3], SEAM)
    b = f["back"]
    vline(px, b[0] + 4, b[1], b[3], SEAM)

    # Torse : vestige de tunique turquoise, rongé sur les bords.
    bf = faces(*body)
    for name in ("front", "back", "left", "right"):
        x0, y0, x1, y1 = bf[name]
        depth = 12 if name in ("front", "back") else 12
        for y in range(y0, min(y0 + depth, y1)):
            for x in range(x0, x1):
                worn = rng.random()
                if y - y0 > 8 and worn < 0.34 + 0.2 * (y - y0 - 8):
                    continue                       # trous : la peau réapparaît
                color = TEAL if worn > 0.22 else TEAL_WORN
                j = rng.randint(-6, 6)
                px[x, y] = tuple(max(0, min(255, c + j)) for c in color) + (255,)
    # Couture centrale du torse, du col au ventre.
    x0, y0, x1, y1 = bf["front"]
    vline(px, x0 + 4, y0, y1, SEAM)
    hline(px, x0, x1, y0 + 13, SEAM)               # cicatrice horizontale sous la tunique

    # Coutures d'épaule et de coude (anneau sombre en haut des segments).
    for (u, v, w, h, d) in arms:
        af = faces(u, v, w, h, d)
        for name in ("front", "back", "left", "right"):
            rx0, ry0, rx1, _ = af[name]
            hline(px, rx0, rx1, ry0, SEAM)
    # Mains : jointures marquées, plus sombres que le bras.
    for (u, v, w, h, d) in hands:
        hf = faces(u, v, w, h, d)
        for name in ("front", "back", "left", "right"):
            rx0, ry0, rx1, ry1 = hf[name]
            noise_fill(px, (rx0, ry0, rx1, ry1), WAX_DARK, 6, rng)
            hline(px, rx0, rx1, ry1 - 1, SEAM)

    # Jambes : souvenir de jeans en haut, peau nue en bas, pas de chaussures.
    for (u, v, w, h, d) in legs:
        lf = faces(u, v, w, h, d)
        for name in ("front", "back", "left", "right"):
            rx0, ry0, rx1, ry1 = lf[name]
            for y in range(ry0, ry0 + 7):
                for x in range(rx0, rx1):
                    color = DENIM if rng.random() > 0.3 else DENIM_WORN
                    j = rng.randint(-5, 5)
                    px[x, y] = tuple(max(0, min(255, c + j)) for c in color) + (255,)
            hline(px, rx0, rx1, ry0 + 7, SEAM)
    return img


# ------------------------------------------------------------ l'Observateur --
def paint_watcher():
    """WatcherModel : tête 8x8x8 (0,0), corps 8x14x4 (16,16),
    bras 3x6x3 (40,16/22 et 52,16/22), jambes 3x16x3 (0,34/12,34)."""
    img = new_canvas()
    px = img.load()
    rng = random.Random(20260723)

    head = (0, 0, 8, 8, 8)
    body = (16, 16, 8, 14, 4)
    arms = [(40, 16, 3, 6, 3), (40, 22, 3, 6, 3), (52, 16, 3, 6, 3), (52, 22, 3, 6, 3)]
    legs = [(0, 34, 3, 16, 3), (12, 34, 3, 16, 3)]

    fill_all(px, [head, body] + arms + legs, CHAR, 6, rng, dark_edge=8)

    # Le visage : deux yeux blancs immenses, écartés, pas de bouche.
    f = faces(*head)
    fx, fy = f["front"][0], f["front"][1]
    for (ex, ey) in ((1, 2), (2, 2), (1, 3), (2, 3), (5, 2), (6, 2), (5, 3), (6, 3)):
        dot(px, fx + ex, fy + ey, EYE_WHITE)
    dot(px, fx + 2, fy + 3, (196, 198, 192)); dot(px, fx + 5, fy + 3, (196, 198, 192))
    # Une déchirure claire descend du crâne sur le front.
    vline(px, fx + 4, fy, fy + 2, CHAR_LIGHT)
    t = f["top"]
    vline(px, t[0] + 4, t[1] + 4, t[3], CHAR_LIGHT)
    # Les longues jambes s'éclaircissent vers le sol : silhouette d'échassier.
    for (u, v, w, h, d) in legs:
        lf = faces(u, v, w, h, d)
        for name in ("front", "back", "left", "right"):
            rx0, ry0, rx1, ry1 = lf[name]
            for y in range(ry1 - 5, ry1):
                for x in range(rx0, rx1):
                    j = rng.randint(-4, 4)
                    lift = (y - (ry1 - 5)) * 6
                    px[x, y] = tuple(max(0, min(255, c + j + lift)) for c in CHAR) + (255,)
    return img


# ------------------------------------------------------------- le Contrefait --
def paint_contrefait():
    """Layout humanoïde vanilla : tête 8x8x8 (0,0), corps 8x12x4 (16,16),
    bras 4x12x4 (40,16/40,32), jambes 4x12x4 (0,16/0,32)."""
    img = new_canvas()
    px = img.load()
    rng = random.Random(20260724)

    head = (0, 0, 8, 8, 8)
    body = (16, 16, 8, 12, 4)
    arms = [(40, 16, 4, 12, 4), (40, 32, 4, 12, 4)]
    legs = [(0, 16, 4, 12, 4), (0, 32, 4, 12, 4)]

    fill_all(px, [head] + arms, SICK, 7, rng)
    fill_all(px, [body], TEAL_WORN, 6, rng)
    fill_all(px, legs, DENIM_WORN, 6, rng)

    # Corruption : des blocs de texels copiés au mauvais endroit — jamais sur
    # le visage (la zone (8,8)-(16,16) reste intouchée pour rester lisible).
    f = faces(*head)
    fx, fy = f["front"][0], f["front"][1]
    for _ in range(9):
        sx, sy = rng.randint(0, 56), rng.randint(16, 40)
        w = rng.choice((2, 3))
        block = [px[sx + i % w, sy + i // w] for i in range(w * w)]
        tx, ty = rng.randint(0, 60 - w), rng.randint(16, 44 - w)
        for i, c in enumerate(block):
            if c[3] > 0 and px[tx + i % w, ty + i // w][3] > 0:
                px[tx + i % w, ty + i // w] = c

    # Visage fondu : yeux larges étirés vers le bas, l'un plus que l'autre.
    for dy in range(3):
        dot(px, fx + 1, fy + 2 + dy, PIT); dot(px, fx + 2, fy + 2 + dy, PIT)
        dot(px, fx + 5, fy + 3 + dy, PIT); dot(px, fx + 6, fy + 3 + dy, PIT)
    # Traînées : les yeux coulent sur les joues.
    dot(px, fx + 2, fy + 5, SICK_DARK); dot(px, fx + 1, fy + 5, SICK_DARK)
    dot(px, fx + 5, fy + 6, SICK_DARK); dot(px, fx + 6, fy + 6, SICK_DARK)
    dot(px, fx + 5, fy + 7, SICK_DARK)
    # Une amorce de bouche, abandonnée à mi-chemin.
    dot(px, fx + 3, fy + 7, SEAM); dot(px, fx + 4, fy + 7, SEAM)
    return img


# --------------------------------------------------------------- l'Originel --
def paint_originel():
    """Layout humanoïde vanilla, momifié : la légende dont tout est copié."""
    img = new_canvas()
    px = img.load()
    rng = random.Random(20260725)

    head = (0, 0, 8, 8, 8)
    body = (16, 16, 8, 12, 4)
    arms = [(40, 16, 4, 12, 4), (40, 32, 4, 12, 4)]
    legs = [(0, 16, 4, 12, 4), (0, 32, 4, 12, 4)]

    fill_all(px, [head] + arms, ANCIENT, 8, rng)
    # Tunique blanchie par les siècles, jambes presque poussière.
    fill_all(px, [body], (142, 150, 144), 7, rng)
    fill_all(px, legs, (96, 94, 90), 6, rng)

    # Craquelures sur toute la surface.
    for _ in range(26):
        x, y = rng.randint(1, 62), rng.randint(1, 62)
        if px[x, y][3] > 0:
            dot(px, x, y, CRACK)
            if rng.random() > 0.5 and px[x, y + 1][3] > 0:
                dot(px, x, y + 1, CRACK)

    # Le visage : yeux d'un blanc plein, sans pupille — la seule lumière.
    f = faces(*head)
    fx, fy = f["front"][0], f["front"][1]
    for ex in (1, 2, 5, 6):
        dot(px, fx + ex, fy + 3, EYE_WHITE)
    dot(px, fx + 1, fy + 4, (210, 212, 206)); dot(px, fx + 6, fy + 4, (210, 212, 206))
    # Bouche : fissure tombante.
    hline(px, fx + 2, fx + 6, fy + 6, CRACK)
    dot(px, fx + 6, fy + 7, CRACK)
    # Barbe d'ombre : le souvenir d'un visage familier.
    for ex in (2, 3, 4, 5):
        dot(px, fx + ex, fy + 7, ANCIENT_DARK)
    return img


# ---------------------------------------------------------------- le Rampant --
def paint_rampant():
    """RampantModel : corps 8x5x14 (0,0), tête 8x7x7 (0,20),
    pattes 3x3x6 (32,20/32,34/0,36/16,36) + segments 2x2x7 réutilisant les offs."""
    img = new_canvas()
    px = img.load()
    rng = random.Random(20260726)

    body = (0, 0, 8, 5, 14)
    head = (0, 20, 8, 7, 7)
    limbs = [(32, 20, 3, 3, 6), (32, 34, 3, 3, 6), (0, 36, 3, 3, 6), (16, 36, 3, 3, 6),
             (32, 20, 2, 2, 7), (32, 34, 2, 2, 7), (0, 36, 2, 2, 7), (16, 36, 2, 2, 7)]

    fill_all(px, [body, head], FLESH, 8, rng)
    fill_all(px, limbs, FLESH_DARK, 7, rng)

    # Échine sombre le long du dos (face top du corps), vertèbres saillantes.
    bf = faces(*body)
    tx0, ty0, tx1, ty1 = bf["top"]
    mid = (ty0 + ty1) // 2
    hline(px, tx0, tx1, mid, SPINE)
    for x in range(tx0, tx1, 2):
        dot(px, x, mid - 1, SPINE)

    # Visage humain sur un corps qui ne l'est plus : yeux cireux asymétriques.
    hf = faces(*head)
    fx, fy = hf["front"][0], hf["front"][1]
    dot(px, fx + 2, fy + 2, PIT); dot(px, fx + 5, fy + 3, PIT)
    dot(px, fx + 2, fy + 2, GLINT)
    hline(px, fx + 2, fx + 7, fy + 5, SEAM)        # bouche trop large
    vline(px, fx + 4, fy, fy + 2, SEAM)            # couture frontale
    return img


# --------------------------------------------------------------- la Cavaleuse --
def paint_scuttler():
    """ScuttlerModel : abdomen 8x5x9 (0,0), thorax 6x4x6 (0,15),
    face 4x2x3 (26,15), pattes 6x2x2 (0,27) + pointes 5x1x1 (18,27)."""
    img = new_canvas()
    px = img.load()
    rng = random.Random(20260727)

    parts = [(0, 0, 8, 5, 9), (0, 15, 6, 4, 6)]
    legs = [(0, 27, 6, 2, 2), (18, 27, 5, 1, 1)]
    face = (26, 15, 4, 2, 3)

    fill_all(px, parts, CHITIN, 7, rng, dark_edge=6)
    fill_all(px, legs, CHITIN_LIGHT, 6, rng)
    # La « face » : une bande de peau cireuse volée, deux fentes d'yeux.
    fill_all(px, [face], WAX, 6, rng)
    ff = faces(*face)
    fx, fy = ff["front"][0], ff["front"][1]
    dot(px, fx, fy, PIT); dot(px, fx + 3, fy + 1, PIT)
    # Mouchetures pâles sur l'abdomen, comme des jointures sous la chitine.
    af = faces(*parts[0])
    for name in ("left", "right", "top"):
        x0, y0, x1, y1 = af[name]
        for _ in range(6):
            dot(px, rng.randint(x0, x1 - 1), rng.randint(y0, y1 - 1), FLESH_DARK)
    return img


# ---------------------------------------------------------- le Projectionniste --
def paint_projectionist():
    """ProjectionistModel : tête 10x7x10 (0,0), lentille 4x4x3 (40,0),
    bobines 2x5x5 (30,0), corps 10x18x5 (0,18), pans 4x11x1 (0,29/10,29),
    bras 3x18x3 (32,18/44,18), jambes (0,42)…"""
    img = new_canvas()
    px = img.load()
    rng = random.Random(20260728)

    head = (0, 0, 10, 7, 10)
    lens = (40, 0, 4, 4, 3)
    reels = [(30, 0, 2, 5, 5)]
    body = (0, 18, 10, 18, 5)
    tails = [(0, 29, 4, 11, 1), (10, 29, 4, 11, 1)]
    arms = [(32, 18, 3, 18, 3), (44, 18, 3, 18, 3)]
    legs = [(0, 42, 3, 12, 3), (12, 42, 3, 12, 3)]

    fill_all(px, [head, body] + arms + legs + reels, APPARATUS, 7, rng, dark_edge=6)
    fill_all(px, tails, (38, 34, 32), 5, rng)
    # Lentille : anneau de laiton, verre pâle qui luit.
    fill_all(px, [lens], BRASS, 8, rng)
    lf = faces(*lens)
    x0, y0, x1, y1 = lf["front"]
    for y in range(y0 + 1, y1 - 1):
        for x in range(x0 + 1, x1 - 1):
            px[x, y] = (*LENS_GLOW, 255)
    # Perforations de pellicule le long du corps.
    bf = faces(*body)
    for name in ("left", "right"):
        x0, y0, x1, y1 = bf[name]
        cx = (x0 + x1) // 2
        for y in range(y0 + 1, y1 - 1, 3):
            dot(px, cx, y, APPARATUS_LIGHT)
    return img


PAINTERS = {
    "host.png": paint_host,
    "watcher.png": paint_watcher,
    "contrefait.png": paint_contrefait,
    "originel.png": paint_originel,
    "rampant.png": paint_rampant,
    "scuttler.png": paint_scuttler,
    "projectionist.png": paint_projectionist,
}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default="src/main/resources/assets/penumbra/textures/entity")
    parser.add_argument("--preview", default=None, help="PNG de contrôle agrandi x8")
    args = parser.parse_args()

    os.makedirs(args.out, exist_ok=True)
    images = {}
    for name, painter in PAINTERS.items():
        img = painter()
        img.save(os.path.join(args.out, name))
        images[name] = img
        print(f"écrit {name}")

    if args.preview:
        cols = len(images)
        sheet = Image.new("RGBA", (cols * 64 * 8 + (cols - 1) * 16, 64 * 8 + 40), (24, 24, 28, 255))
        draw = ImageDraw.Draw(sheet)
        for i, (name, img) in enumerate(images.items()):
            x = i * (64 * 8 + 16)
            sheet.paste(img.resize((64 * 8, 64 * 8), Image.NEAREST), (x, 40))
            draw.text((x + 4, 12), name, fill=(220, 220, 220, 255))
        sheet.save(args.preview)
        print(f"aperçu : {args.preview}")


if __name__ == "__main__":
    main()

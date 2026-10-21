#!/usr/bin/env python3
"""Génère les textures placeholder de Penumbra (SPEC §4.12).

Palette imposée :
  - corps de l'Hôte : #c9c4bb (pâle)
  - membres         : #8f8a82 (assombris)
  - yeux braise     : #d97b3f (deux points, zone tête)
  - icône           : fond #141216, monogramme P #c9c4bb

UV host.png (64x64, alignée sur HostEntityModel.getTexturedModelData) :
  head   : offset (0,0)   boîte 8x8x10  -> face avant (10,10)-(18,18)
  body   : offset (0,20)  boîte 8x20x4  -> face avant (4,24)-(12,44)
  arm_r  : offset (40,0)  boîte 3x16x3
  arm_l  : offset (40,20) boîte 3x16x3
  leg_r  : offset (0,46)  boîte 3x14x3
  leg_l  : offset (14,46) boîte 3x14x3
Pour une boîte (w,h,d) à l'offset (u,v), l'emprise UV complète est :
  largeur 2*(d+w), hauteur d+h ; face avant = (u+d, v+d) -> (u+d+w, v+d+h).
"""

import os
import random

from PIL import Image, ImageDraw

RANDOM_SEED = 20260101
random.seed(RANDOM_SEED)

BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
TEX = os.path.join(BASE, "assets", "penumbra", "textures")

PALE = (0xC9, 0xC4, 0xBB, 255)      # #c9c4bb
LIMB = (0x8F, 0x8A, 0x82, 255)      # #8f8a82
EMBER = (0xD9, 0x7B, 0x3F, 255)     # #d97b3f
EMBER_DIM = (0xD9, 0x7B, 0x3F, 90)  # halo braise
ICON_BG = (0x14, 0x12, 0x16, 255)   # #141216
TUBE = (0x2B, 0x2B, 0x30, 255)      # #2b2b30 — tubes des jumelles (Phase 2)


def grain(color, amount=8):
    """Applique un léger bruit de luminosité à une couleur RGBA."""
    d = random.randint(-amount, amount)
    return (
        max(0, min(255, color[0] + d)),
        max(0, min(255, color[1] + d)),
        max(0, min(255, color[2] + d)),
        color[3],
    )


def fill_box(img, u, v, w, h, d, color):
    """Remplit l'emprise UV complète d'une boîte (toutes faces) avec grain."""
    px = img.load()
    total_w = 2 * (d + w)
    total_h = d + h
    for y in range(v, v + total_h):
        for x in range(u, u + total_w):
            if 0 <= x < img.width and 0 <= y < img.height:
                px[x, y] = grain(color)


def gen_host():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    # Corps et tête pâles, membres assombris (SPEC §4.12, UV = HostEntityModel).
    fill_box(img, 0, 0, 8, 8, 10, PALE)     # head
    fill_box(img, 0, 20, 8, 20, 4, PALE)    # body
    fill_box(img, 40, 0, 3, 16, 3, LIMB)    # arm_r
    fill_box(img, 40, 20, 3, 16, 3, LIMB)   # arm_l
    fill_box(img, 0, 46, 3, 14, 3, LIMB)    # leg_r
    fill_box(img, 14, 46, 3, 14, 3, LIMB)   # leg_l

    px = img.load()
    # Deux points braise sur la face avant de la tête (10,10)-(18,18).
    for ex, ey in ((12, 14), (15, 14)):
        for dy in (0, 1):
            px[ex, ey + dy] = EMBER
        # halo discret autour
        for hx, hy in ((ex - 1, ey), (ex + 1, ey), (ex, ey - 1), (ex, ey + 2)):
            if px[hx, hy][3] != 0:
                px[hx, hy] = EMBER_DIM
    out = os.path.join(TEX, "entity", "host.png")
    img.save(out)
    print("écrit", out)


def gen_ash_sigil():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # Anneau de cendre.
    d.ellipse((2, 2, 13, 13), outline=PALE, width=1)
    # Rune intérieure braise : barre verticale + deux traverses obliques.
    d.line((8, 4, 8, 11), fill=EMBER, width=1)
    d.line((5, 6, 8, 8), fill=EMBER, width=1)
    d.line((10, 6, 8, 8), fill=EMBER, width=1)
    # Point de cendre au sommet.
    d.point((8, 3), fill=PALE)
    out = os.path.join(TEX, "item", "ash_sigil.png")
    img.save(out)
    print("écrit", out)


def gen_ash_mote():
    img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
    px = img.load()
    cx, cy, maxd = 3.5, 3.5, 4.0
    for y in range(8):
        for x in range(8):
            dist = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if dist < maxd:
                a = int(255 * max(0.0, 1.0 - dist / maxd) ** 1.6)
                # cœur légèrement plus clair que PALE
                v = min(255, 0xC9 + int(30 * (1.0 - dist / maxd)))
                px[x, y] = (v, v - 3, v - 8, a)
    out = os.path.join(TEX, "particle", "ash_mote.png")
    img.save(out)
    print("écrit", out)


def gen_icon():
    img = Image.new("RGBA", (128, 128), ICON_BG)
    d = ImageDraw.Draw(img)
    # Monogramme P sobre : fût + panse en anneau.
    d.rectangle((44, 28, 58, 100), fill=PALE)            # fût
    d.ellipse((52, 28, 102, 78), outline=PALE, width=13)  # panse
    d.rectangle((44, 64, 70, 100), fill=ICON_BG)          # ouvre le bas de la panse
    d.rectangle((44, 28, 58, 100), fill=PALE)            # repasse le fût
    out = os.path.join(BASE, "assets", "penumbra", "icon.png")
    img.save(out)
    print("écrit", out)


def gen_binoculars():
    """Jumelles 16x16 (Phase 2, SPEC2 §9) : deux tubes sombres #2b2b30
    cerclés #8f8a82, vus de trois-quarts face, pont central."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # Deux objectifs (face avant) : anneaux #8f8a82, verre sombre #2b2b30.
    for cx in (4, 11):
        d.ellipse((cx - 3, 6, cx + 3, 12), outline=LIMB, width=1)
        d.ellipse((cx - 2, 7, cx + 2, 11), fill=TUBE)
    # Corps des tubes vers l'arrière (haut de l'icône).
    d.rectangle((2, 2, 5, 6), fill=TUBE)
    d.rectangle((10, 2, 13, 6), fill=TUBE)
    # Cerclage supérieur des tubes.
    d.line((2, 2, 5, 2), fill=LIMB)
    d.line((10, 2, 13, 2), fill=LIMB)
    d.line((1, 3, 1, 5), fill=LIMB)
    d.line((14, 3, 14, 5), fill=LIMB)
    # Pont central reliant les deux tubes.
    d.rectangle((6, 3, 9, 5), fill=TUBE)
    d.line((7, 2, 8, 2), fill=LIMB)
    # Reflets discrets sur les verres (gris pâle, pas de couleur chaude).
    d.point((3, 8), fill=PALE)
    d.point((10, 8), fill=PALE)
    out = os.path.join(TEX, "item", "binoculars.png")
    img.save(out)
    print("écrit", out)


if __name__ == "__main__":
    os.makedirs(os.path.join(TEX, "entity"), exist_ok=True)
    os.makedirs(os.path.join(TEX, "item"), exist_ok=True)
    os.makedirs(os.path.join(TEX, "particle"), exist_ok=True)
    gen_host()
    gen_ash_sigil()
    gen_ash_mote()
    gen_icon()
    gen_binoculars()
    print("Textures placeholder générées.")

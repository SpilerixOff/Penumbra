#!/usr/bin/env python3
"""Génère la texture de l'Observateur (penumbra:watcher) — Phase 4.

Palette imposée :
  - silhouette      : #08080a (quasi noire, grain très faible)
  - visage          : #121216 (à peine plus clair que le corps)
  - yeux            : #8e949e (deux points 1 px, très faiblement gris-blanc)

UV watcher.png (64x64, alignée sur WatcherModel.createBodyLayer) :
  head   : offset (0,0)   boîte 8x8x8   -> face avant (8,8)-(16,16)
  body   : offset (16,16) boîte 8x14x4  -> face avant (20,20)-(28,34)
  arm_r  : offset (40,16) boîte 3x12x3
  arm_l  : offset (52,16) boîte 3x12x3
  leg_r  : offset (0,34)  boîte 3x16x3
  leg_l  : offset (12,34) boîte 3x16x3
Pour une boîte (w,h,d) à l'offset (u,v), l'emprise UV complète est :
  largeur 2*(d+w), hauteur d+h ; face avant = (u+d, v+d) -> (u+d+w, v+d+h).

Yeux : face avant de la tête (8x8 px), colonnes locales 2 et 5, ligne locale 3,
soit les pixels absolus (10,11) et (13,11).
"""

import os
import random

from PIL import Image

RANDOM_SEED = 20260401
random.seed(RANDOM_SEED)

BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
TEX = os.path.join(BASE, "assets", "penumbra", "textures", "entity")

DARK = (0x08, 0x08, 0x0A, 255)      # #08080a — silhouette quasi noire
FACE = (0x12, 0x12, 0x16, 255)      # #121216 — visage à peine plus clair
EYE = (0x8E, 0x94, 0x9E, 255)       # #8e949e — points oculaires très faibles


def grain(color, amount=4):
    """Applique un grain très léger à une couleur RGBA."""
    delta = random.randint(-amount, amount)
    return (
        max(0, min(255, color[0] + delta)),
        max(0, min(255, color[1] + delta)),
        max(0, min(255, color[2] + delta)),
        color[3],
    )


def main():
    os.makedirs(TEX, exist_ok=True)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = img.load()

    # Fond : silhouette quasi noire avec grain (les zones hors emprises UV
    # restent sombres — invisibles en jeu, mais cohérentes à l'ouverture du PNG).
    for y in range(64):
        for x in range(64):
            px[x, y] = grain(DARK)

    # Visage : face avant de la tête (8,8)-(16,16), à peine plus claire.
    for y in range(8, 16):
        for x in range(8, 16):
            px[x, y] = grain(FACE)

    # Deux points oculaires très faiblement gris-blanc (1 px chacun).
    px[10, 11] = EYE
    px[13, 11] = EYE

    out = os.path.join(TEX, "watcher.png")
    img.save(out)
    print(f"watcher.png généré : {out}")


if __name__ == "__main__":
    main()

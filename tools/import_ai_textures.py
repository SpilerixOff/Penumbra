#!/usr/bin/env python3
"""Importe les visuels IA fournis par l'utilisateur (0.6.0).

Dépose les fichiers dans penumbra/ai/ sous ces noms :
  ai/v1.png    -> visage du screamer   -> textures/gui/screamer_host.png   (512x512)
  ai/v1_2.png  -> variante du screamer -> textures/gui/screamer_host_2.png (512x512)
  ai/v2.png    -> icône du mod         -> assets/penumbra/icon.png         (128x128)

Puis : python3 tools/import_ai_textures.py
Recadrage carré centré si besoin, conversion RGBA, redimensionnement LANCZOS.
"""

import os

from PIL import Image

BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
AI = os.path.join(BASE, "ai")
ASSETS = os.path.join(BASE, "src", "main", "resources", "assets", "penumbra")

TARGETS = [
    (("v1.png",), os.path.join(ASSETS, "textures", "gui", "screamer_host.png"), 512),
    (("v1_2.png", "v1.2.png"), os.path.join(ASSETS, "textures", "gui", "screamer_host_2.png"), 512),
    (("v2.png",), os.path.join(ASSETS, "icon.png"), 128),
]


def square_center(img):
    w, h = img.size
    if w == h:
        return img
    side = min(w, h)
    left = (w - side) // 2
    top = (h - side) // 2
    return img.crop((left, top, left + side, top + side))


def main():
    done = 0
    for src_names, dest, size in TARGETS:
        src = next((os.path.join(AI, n) for n in src_names
                    if os.path.isfile(os.path.join(AI, n))), None)
        if src is None:
            print(f"absent : ai/{src_names[0]} (ignoré)")
            continue
        img = Image.open(src).convert("RGBA")
        img = square_center(img).resize((size, size), Image.LANCZOS)
        os.makedirs(os.path.dirname(dest), exist_ok=True)
        img.save(dest)
        print(f"{os.path.basename(src)} -> {os.path.relpath(dest, BASE)} ({size}x{size})")
        done += 1
    print(f"{done}/3 texture(s) importée(s).")


if __name__ == "__main__":
    main()

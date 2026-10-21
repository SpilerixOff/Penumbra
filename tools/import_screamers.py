#!/usr/bin/env python3
"""Importe les visages de screamers générés (GPT Image 2 ou autre).

Prend un dossier d'images nommées soit `01.png`…`15.png` (ordre des packs de
prompts docs/PROMPTS_SCREAMERS.md), soit directement du nom cible
(`screamer_host.png`, `screamer_rampant.png`…). Pour chaque image :

1. recadrage centré au carré ;
2. redimensionnement en 1024×1024 (LANCZOS) ;
3. vignette noire progressive sur les bords — le rendu « cover » du client
   agrandit parfois l'image (styles CREEP_ZOOM/CLOSE_CRAWL) : des bords fondus
   au noir pur garantissent qu'aucune bordure dure n'apparaît jamais ;
4. installation dans assets/penumbra/textures/gui/ avec sauvegarde des anciens.

Usage : python3 tools/import_screamers.py <dossier> [--no-vignette]
"""

import argparse
import os
import shutil
import sys

from PIL import Image

TARGETS = [
    "screamer_host.png",
    "screamer_host_2.png",
    "screamer_host_3.png",
    "screamer_host_4.png",
    "screamer_host_5.png",
    "screamer_host_6.png",
    "screamer_host_7.png",
    "screamer_host_8.png",
    "screamer_host_9.png",
    "screamer_rampant.png",
    "screamer_absent.png",
    "screamer_chorus.png",
    "screamer_refuge.png",
    "screamer_mimic.png",
    # 0.17.0 : le visage sans yeux de L'Écouteur.
    "screamer_listener.png"
]

GUI_DIR = os.path.join("src", "main", "resources", "assets", "penumbra", "textures", "gui")
BACKUP_DIR = os.path.join("build", "backup_screamers")
SIZE = 1024
VIGNETTE_FRACTION = 0.12  # largeur de la zone de fondu sur chaque bord


def center_square(img):
    w, h = img.size
    side = min(w, h)
    left = (w - side) // 2
    top = (h - side) // 2
    return img.crop((left, top, left + side, top + side))


def vignette(img):
    """Fond progressivement les bords vers le noir pur (multiplication)."""
    px = img.load()
    size = img.size[0]
    margin = int(size * VIGNETTE_FRACTION)
    if margin <= 0:
        return img
    for y in range(size):
        for x in range(size):
            edge = min(x, y, size - 1 - x, size - 1 - y)
            if edge >= margin:
                continue
            k = edge / margin
            k = k * k * (3 - 2 * k)  # smoothstep
            r, g, b, a = px[x, y]
            px[x, y] = (int(r * k), int(g * k), int(b * k), 255)
    return img


def resolve_source(folder, index, target):
    for candidate in (target, f"{index + 1:02d}.png", f"{index + 1}.png",
                      f"{index + 1:02d}.jpg", f"{index + 1}.jpg"):
        path = os.path.join(folder, candidate)
        if os.path.isfile(path):
            return path
    return None


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("folder", help="dossier contenant les images générées")
    parser.add_argument("--no-vignette", action="store_true")
    args = parser.parse_args()

    if not os.path.isdir(args.folder):
        sys.exit(f"dossier introuvable : {args.folder}")
    if not os.path.isdir(GUI_DIR):
        sys.exit(f"lance ce script depuis la racine du projet (introuvable : {GUI_DIR})")

    os.makedirs(BACKUP_DIR, exist_ok=True)
    done, missing = 0, []
    for index, target in enumerate(TARGETS):
        source = resolve_source(args.folder, index, target)
        if source is None:
            missing.append(f"{index + 1:02d} ({target})")
            continue
        img = Image.open(source).convert("RGBA")
        img = center_square(img).resize((SIZE, SIZE), Image.LANCZOS)
        if not args.no_vignette:
            img = vignette(img)
        destination = os.path.join(GUI_DIR, target)
        if os.path.isfile(destination):
            shutil.copy2(destination, os.path.join(BACKUP_DIR, target))
        img.save(destination)
        print(f"installé {target}  (depuis {os.path.basename(source)})")
        done += 1

    print(f"\n{done}/{len(TARGETS)} visages installés.")
    if missing:
        print("manquants : " + ", ".join(missing))
        print("(les anciens fichiers restent en place pour ceux-là)")
    if done:
        print(f"anciens visages sauvegardés dans {BACKUP_DIR}/")


if __name__ == "__main__":
    main()

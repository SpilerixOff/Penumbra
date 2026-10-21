#!/usr/bin/env python3
"""Importe les quinze croquis du Relevé générés à l'IA (GPT Image 2 ou autre).

Le problème à résoudre : un modèle d'image rend de l'encre **noire sur papier
blanc**, alors que l'atlas du carnet a besoin d'encre **sépia sur transparent**
— c'est le parchemin de `journal_spread.png` qui doit transparaître derrière
les traits. Ce script fait exactement cette conversion :

  1. recadrage centré au carré ;
  2. passage en luminance, puis **luminance → alpha** : plus un pixel est
     sombre, plus il est opaque. Le papier blanc de l'image générée devient
     donc parfaitement transparent, et seuls les traits subsistent ;
  3. courbe de contraste (seuil bas / seuil haut) pour nettoyer le gris de
     compression JPEG sans manger les hachures fines ;
  4. teinte sépia unique, celle de l'encre du reste du carnet ;
  5. redimensionnement en 128×128 (LANCZOS) et collage dans la bonne case.

L'ordre des cases est celui de `JournalEntry.portraitIndex()` — il est figé,
n'y touchez pas sans changer l'enum.

Usage :
    python3 tools/import_journal_portraits.py <dossier>
    python3 tools/import_journal_portraits.py <dossier> --keep-missing
    python3 tools/import_journal_portraits.py <dossier> --low 0.30 --high 0.86

Le dossier peut contenir les fichiers nommés `01.png` … `15.png` (ordre du pack
de prompts) ou directement par identifiant (`host.png`, `listener.png`…). Les
cases sans fichier gardent le croquis procédural existant, sauf si vous passez
`--keep-missing` à `false` implicitement en régénérant tout.
"""

import argparse
import os
import shutil
import sys

from PIL import Image, ImageOps

# Ordre figé, identique à JournalEntry.portraitIndex() (0 → 14).
ORDER = [
    "frontispiece", "host", "watcher", "scuttler",
    "contrefait", "rampant", "projectionist", "faux_creature",
    "absent", "listener", "originel", "method_camera",
    "method_refuge", "method_stillness", "last_page",
]

CELL = 128
GRID = 4
ATLAS = os.path.join("src", "main", "resources", "assets", "penumbra",
                     "textures", "gui", "journal_portraits.png")
BACKUP_DIR = os.path.join("build", "backup_portraits")

# Encre du carnet, identique à INK dans gen_journal_assets.py.
INK = (44, 34, 24)


def resolve_source(folder, index, name):
    """Accepte `NN.png`, `NN.jpg`, `<id>.png`… dans n'importe quel ordre."""
    candidates = []
    for extension in (".png", ".jpg", ".jpeg", ".webp"):
        candidates.append(f"{index + 1:02d}{extension}")
        candidates.append(f"{index + 1}{extension}")
        candidates.append(f"{name}{extension}")
    for candidate in candidates:
        path = os.path.join(folder, candidate)
        if os.path.isfile(path):
            return path
    return None


def center_square(image):
    width, height = image.size
    side = min(width, height)
    left = (width - side) // 2
    top = (height - side) // 2
    return image.crop((left, top, left + side, top + side))


def to_ink(image, low, high, ink=INK):
    """Encre sépia sur transparent, à partir d'un dessin noir sur blanc.

    `low` et `high` sont les seuils de la courbe appliquée à l'obscurité :
    en dessous de `low` le pixel devient totalement transparent (papier), au
    dessus de `high` totalement opaque (plein trait). Élargir l'écart conserve
    les hachures grises ; le resserrer donne un trait plus net et plus dur.
    """
    grey = ImageOps.grayscale(center_square(image.convert("RGB")))
    # Obscurité = 255 - luminance. C'est elle qui devient l'alpha.
    darkness = ImageOps.invert(grey)
    low_level = int(max(0.0, min(1.0, low)) * 255)
    high_level = int(max(0.0, min(1.0, high)) * 255)
    if high_level <= low_level:
        high_level = min(255, low_level + 1)
    span = high_level - low_level

    alpha = darkness.point(
        lambda value: 0 if value <= low_level
        else 255 if value >= high_level
        else int((value - low_level) * 255 / span))

    result = Image.new("RGBA", alpha.size, (*ink, 0))
    result.putalpha(alpha)
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("folder", help="dossier contenant les croquis générés")
    parser.add_argument("--low", type=float, default=0.28,
                        help="seuil de transparence (défaut 0.28)")
    parser.add_argument("--high", type=float, default=0.82,
                        help="seuil d'opacité totale (défaut 0.82)")
    parser.add_argument("--blank", action="store_true",
                        help="repartir d'un atlas vide au lieu de conserver "
                             "les croquis procéduraux existants")
    args = parser.parse_args()

    if not os.path.isdir(args.folder):
        sys.exit(f"dossier introuvable : {args.folder}")
    if not os.path.isdir(os.path.dirname(ATLAS)):
        sys.exit("lance ce script depuis la racine du projet "
                 f"(introuvable : {os.path.dirname(ATLAS)})")

    if args.blank or not os.path.isfile(ATLAS):
        atlas = Image.new("RGBA", (CELL * GRID, CELL * GRID), (0, 0, 0, 0))
    else:
        os.makedirs(BACKUP_DIR, exist_ok=True)
        shutil.copy2(ATLAS, os.path.join(BACKUP_DIR, "journal_portraits.png"))
        atlas = Image.open(ATLAS).convert("RGBA")

    done, missing = 0, []
    for index, name in enumerate(ORDER):
        source = resolve_source(args.folder, index, name)
        if source is None:
            missing.append(f"{index + 1:02d} ({name})")
            continue
        cell = to_ink(Image.open(source), args.low, args.high)
        cell = cell.resize((CELL, CELL), Image.LANCZOS)
        box = ((index % GRID) * CELL, (index // GRID) * CELL)
        # Case effacée avant collage : sinon l'ancien croquis reste sous le neuf.
        atlas.paste((0, 0, 0, 0), (box[0], box[1], box[0] + CELL, box[1] + CELL))
        atlas.paste(cell, box, cell)
        print(f"  case {index:2d} · {name:<18} ← {os.path.basename(source)}")
        done += 1

    atlas.save(ATLAS)
    print(f"\n{done}/{len(ORDER)} croquis importés dans {ATLAS}")
    if missing:
        print("manquants : " + ", ".join(missing))
        print("(ces cases conservent leur croquis actuel)")
    if done and not args.blank:
        print(f"ancien atlas sauvegardé dans {BACKUP_DIR}/")
    print("\nAperçu conseillé : ouvre l'atlas sur un fond #CEBE9E — c'est la "
          "couleur exacte du parchemin derrière lui.")


if __name__ == "__main__":
    main()

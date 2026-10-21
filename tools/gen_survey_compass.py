#!/usr/bin/env python3
"""Fabrique la Boussole d'arpenteur (0.19.0) : 32 images, 32 modèles, 1 définition.

Une boussole de Minecraft n'est pas une image mais **trente-deux** : le jeu
choisit celle dont l'aiguille pointe dans la bonne direction. La convention est
imposée par vanilla et se lit dans ses propres fichiers :

  - la définition d'objet est un `range_dispatch` sur `minecraft:compass`,
    échelle 32, avec 33 seuils (0 puis 0,5 · 1,5 · … · 31,5) ;
  - la valeur 0 correspond à **l'aiguille vers le haut**, et chaque cran
    suivant la tourne de 11,25° dans le sens horaire.

Vérifié en lisant les textures vanilla : `compass_16` pointe en haut,
`compass_24` à droite, `compass_00` en bas, `compass_08` à gauche. Nos images
sont numérotées directement dans l'ordre de rotation — la frame N pointe à
N × 11,25° — ce qui rend la liste de seuils triviale.

Le dessin : laiton patiné, cadran bleu-noir, aiguille rouge et os. Rendu en
128×128 puis réduit en 16×16, pour des bords propres qu'on n'obtient pas en
posant les pixels un par un.

Déterministe. Usage : python3 tools/gen_survey_compass.py
"""

import json
import math
import os
import random

from PIL import Image, ImageDraw

BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..",
                    "src", "main", "resources", "assets", "penumbra")

NAME = "survey_compass"
FRAMES = 32
SIZE = 16
SUPER = 8               # facteur de suréchantillonnage
S = SIZE * SUPER

BRASS_LIGHT = (196, 156, 84)
BRASS = (154, 116, 56)
BRASS_DARK = (96, 70, 32)
PATINA = (108, 132, 96)
DIAL = (26, 28, 36)
DIAL_EDGE = (46, 48, 60)
NEEDLE_RED = (168, 46, 42)
NEEDLE_BONE = (214, 206, 186)


def case(draw, rng):
    """Boîtier : anneau de laiton, ombre portée, taches de patine."""
    margin = 0.6 * SUPER
    draw.ellipse([margin, margin, S - margin, S - margin],
                 fill=BRASS, outline=BRASS_DARK, width=SUPER)
    # Biseau : un arc clair en haut à gauche, un arc sombre en bas à droite.
    draw.arc([margin, margin, S - margin, S - margin], 170, 330,
             fill=BRASS_LIGHT, width=int(SUPER * 1.2))
    draw.arc([margin, margin, S - margin, S - margin], 350, 150,
             fill=BRASS_DARK, width=int(SUPER * 1.1))

    for _ in range(14):
        angle = rng.random() * math.tau
        radius = (0.30 + rng.random() * 0.12) * S
        cx = S / 2 + math.cos(angle) * radius
        cy = S / 2 + math.sin(angle) * radius
        r = rng.uniform(0.4, 1.1) * SUPER
        draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=PATINA)


def dial(draw):
    """Cadran sombre et quatre repères cardinaux."""
    inset = 3.0 * SUPER
    draw.ellipse([inset, inset, S - inset, S - inset],
                 fill=DIAL, outline=DIAL_EDGE, width=max(1, SUPER // 2))
    for index in range(4):
        angle = index * math.pi / 2
        outer = S / 2 - inset * 0.55
        inner = outer - 1.15 * SUPER
        cx, cy = S / 2, S / 2
        draw.line([(cx + math.sin(angle) * inner, cy - math.cos(angle) * inner),
                   (cx + math.sin(angle) * outer, cy - math.cos(angle) * outer)],
                  fill=BRASS_LIGHT, width=max(1, int(SUPER * 0.7)))


def needle(draw, degrees):
    """Aiguille : moitié rouge vers la cible, moitié os à l'opposé."""
    angle = math.radians(degrees)
    cx, cy = S / 2, S / 2
    length = S * 0.27
    width = S * 0.055

    def point(distance, side):
        return (cx + math.sin(angle) * distance + math.cos(angle) * side,
                cy - math.cos(angle) * distance + math.sin(angle) * side)

    draw.polygon([point(length, 0), point(0, -width), point(0, width)],
                 fill=NEEDLE_RED)
    draw.polygon([point(-length * 0.82, 0), point(0, -width), point(0, width)],
                 fill=NEEDLE_BONE)
    pivot = SUPER * 0.8
    draw.ellipse([cx - pivot, cy - pivot, cx + pivot, cy + pivot],
                 fill=BRASS_LIGHT, outline=BRASS_DARK)


def frame(index):
    image = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image, "RGBA")
    # Même graine pour toutes les frames : le boîtier ne doit pas scintiller
    # d'une image à l'autre, seule l'aiguille bouge.
    case(draw, random.Random(2409))
    dial(draw)
    needle(draw, index * 360.0 / FRAMES)
    return image.resize((SIZE, SIZE), Image.LANCZOS)


def main():
    textures = os.path.join(BASE, "textures", "item")
    models = os.path.join(BASE, "models", "item")
    items = os.path.join(BASE, "items")
    for folder in (textures, models, items):
        os.makedirs(folder, exist_ok=True)

    for index in range(FRAMES):
        stem = f"{NAME}_{index:02d}"
        frame(index).save(os.path.join(textures, stem + ".png"))
        with open(os.path.join(models, stem + ".json"), "w", encoding="utf-8") as out:
            json.dump({"parent": "minecraft:item/generated",
                       "textures": {"layer0": f"penumbra:item/{stem}"}},
                      out, indent=2)
            out.write("\n")

    # 33 seuils : 0 puis 0,5 · 1,5 · … · 31,5, le dernier revenant à la frame 0.
    entries = [{"threshold": 0.0,
                "model": {"type": "minecraft:model",
                          "model": f"penumbra:item/{NAME}_00"}}]
    for index in range(1, FRAMES + 1):
        entries.append({
            "threshold": index - 0.5,
            "model": {"type": "minecraft:model",
                      "model": f"penumbra:item/{NAME}_{index % FRAMES:02d}"},
        })

    dispatch = {
        "type": "minecraft:range_dispatch",
        "property": "minecraft:compass",
        "target": "lodestone",
        "scale": 32.0,
        "entries": entries,
    }
    definition = {
        "model": {
            "type": "minecraft:condition",
            "property": "minecraft:has_component",
            "component": "minecraft:lodestone_tracker",
            "on_true": dispatch,
            # Sans cible — autre dimension, ou objet tout juste sorti de la
            # table — l'aiguille se fige au lieu de tourner dans le vide.
            "on_false": {"type": "minecraft:model",
                         "model": f"penumbra:item/{NAME}_00"},
        }
    }
    with open(os.path.join(items, NAME + ".json"), "w", encoding="utf-8") as out:
        json.dump(definition, out, indent=2)
        out.write("\n")

    print(f"  {FRAMES} textures 16x16, {FRAMES} modèles, 1 définition d'objet")
    print(f"  seuils : {entries[0]['threshold']} … {entries[-1]['threshold']}"
          f" ({len(entries)} entrées)")


if __name__ == "__main__":
    main()

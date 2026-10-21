#!/usr/bin/env python3
"""Repeint les huit textures d'objets encore provisoires (0.17.0).

Les sept objets narratifs et le disque « Mémoire » étaient les derniers assets
plats du mod : aplats sans contour, sans volume, sans lumière. Ils sont ici
repeints au texel près en 16x16 avec la grammaire du reste de Penumbra —
contour sombre, trois valeurs de modelé, une seule source de lumière en haut à
gauche, et un détail qui cloche sur chaque objet.

  ash_sigil             sceau de cendre ébréché, glyphe incisé
  blackened_plumb_bob   fil à plomb noirci, cordon effiloché
  impossible_photo      tirage instantané avec une silhouette de trop
  incomplete_journal    carnet dont il manque des pages
  observation_fragment  tesson gravé de bâtons de comptage
  silhouette_glass      verre cerclé contenant une ombre qui n'est pas un reflet
  cracked_lens          lentille de caméra fendue
  memoire_disc          disque vinyle à étiquette turquoise

Déterministe. Usage : python3 tools/gen_narrative_items.py
"""

import argparse
import os
import random

from PIL import Image

OUT_DEFAULT = os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources",
    "assets", "penumbra", "textures", "item")

# ——————————————————————————— palette commune ———————————————————————————
OUTLINE = (18, 16, 18)
SHADOW = (44, 40, 42)

ASH = (206, 202, 192)
ASH_MID = (166, 160, 150)
ASH_DARK = (118, 112, 104)
BONE = (214, 206, 186)
BONE_MID = (176, 166, 146)
BONE_DARK = (126, 116, 98)

IRON = (108, 108, 116)
IRON_LIGHT = (152, 152, 162)
IRON_DARK = (62, 62, 70)
SOOT = (38, 36, 40)
SOOT_LIGHT = (66, 62, 68)
BRASS = (150, 124, 76)
BRASS_LIGHT = (196, 168, 106)
BRASS_DARK = (98, 80, 48)

LEATHER = (92, 62, 44)
LEATHER_LIGHT = (126, 88, 62)
LEATHER_DARK = (58, 38, 28)
PAPER = (226, 220, 202)
PAPER_MID = (196, 188, 168)
PAPER_DARK = (150, 142, 124)

TEAL = (58, 104, 100)
TEAL_LIGHT = (96, 146, 140)
TEAL_DARK = (34, 68, 66)
GLASS = (128, 158, 158)
GLASS_LIGHT = (186, 210, 208)
GLASS_DARK = (74, 98, 100)
VINYL = (28, 26, 30)
VINYL_LIGHT = (56, 52, 58)
HIGHLIGHT = (240, 238, 230)


def canvas():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def put(px, x, y, color):
    if 0 <= x < 16 and 0 <= y < 16:
        px[x, y] = (*color, 255)


def rect(px, x0, y0, x1, y1, color):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            put(px, x, y, color)


def frame(px, x0, y0, x1, y1, color):
    for x in range(x0, x1 + 1):
        put(px, x, y0, color)
        put(px, x, y1, color)
    for y in range(y0, y1 + 1):
        put(px, x0, y, color)
        put(px, x1, y, color)


def disc(px, cx, cy, radius, color, inner=None):
    """Disque plein ; {@code inner} évide le centre pour obtenir un anneau."""
    for y in range(16):
        for x in range(16):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d <= radius and (inner is None or d > inner):
                put(px, x, y, color)


def speckle(px, region, color, chance, rng):
    for (x, y) in region:
        if rng.random() < chance:
            put(px, x, y, color)


def opaque(px):
    return [(x, y) for y in range(16) for x in range(16) if px[x, y][3] > 0]


# ——————————————————————————— objets ———————————————————————————

def ash_sigil():
    """Sceau de cendre : disque pressé, glyphe incisé, un éclat manquant."""
    rng = random.Random(101)
    image = canvas()
    px = image.load()
    disc(px, 7.5, 8.0, 6.4, OUTLINE)
    disc(px, 7.5, 8.0, 5.5, ASH_DARK)
    disc(px, 7.5, 7.6, 5.0, ASH_MID)
    disc(px, 6.9, 6.9, 4.0, ASH)

    # Glyphe : une verticale et deux barres décalées — jamais une croix propre.
    for y in range(3, 13):
        put(px, 7, y, SHADOW)
    for x in range(4, 9):
        put(px, x, 6, SHADOW)
    for x in range(7, 12):
        put(px, x, 10, SHADOW)
    put(px, 4, 5, SHADOW)
    put(px, 11, 11, SHADOW)

    # Éclat manquant en bas à droite, et sa tranche claire.
    for x, y in ((11, 11), (12, 11), (11, 12), (12, 12), (10, 12), (11, 13)):
        px[x, y] = (0, 0, 0, 0)
    put(px, 10, 11, ASH)
    put(px, 10, 13, ASH_DARK)

    # Poussière de cendre qui s'échappe encore.
    put(px, 13, 9, ASH_MID)
    put(px, 14, 12, ASH_DARK)
    put(px, 2, 13, ASH_MID)
    speckle(px, opaque(px), ASH_DARK, 0.10, rng)
    put(px, 5, 4, HIGHLIGHT)
    return image


def blackened_plumb_bob():
    """Fil à plomb noirci : cordon effiloché, cône de plomb, pointe usée."""
    image = canvas()
    px = image.load()
    # Cordon, légèrement dévié : il ne pend pas droit, et c'est le problème.
    for index, y in enumerate(range(0, 6)):
        put(px, 8 - (index > 3), y, LEATHER_DARK)
    put(px, 9, 1, LEATHER)
    put(px, 7, 4, LEATHER)

    # Œillet de fixation.
    frame(px, 6, 5, 9, 7, OUTLINE)
    rect(px, 7, 6, 8, 6, IRON_LIGHT)

    # Cône : franchement large en haut, pointe fine en bas, éclairé à gauche.
    widths = ((3, 12, 8), (2, 13, 9), (2, 13, 10), (3, 12, 11), (4, 11, 12),
              (5, 10, 13), (6, 9, 14), (7, 8, 15))
    for x0, x1, y in widths:
        rect(px, x0, y, x1, y, SOOT)
        put(px, x0, y, OUTLINE)
        put(px, x1, y, OUTLINE)
    # Arête de lumière sur la génératrice gauche du cône.
    for offset, y in enumerate(range(9, 15)):
        put(px, 3 + offset, y, SOOT_LIGHT)
    put(px, 3, 9, IRON_LIGHT)
    put(px, 4, 10, IRON_LIGHT)
    put(px, 5, 11, IRON)
    # Flanc droit dans l'ombre.
    for offset, y in enumerate(range(9, 15)):
        put(px, 12 - offset, y, OUTLINE)
    # Pointe : le plomb a été limé, il brille.
    put(px, 7, 15, IRON_LIGHT)
    put(px, 8, 15, IRON_DARK)
    return image


def impossible_photo():
    """Tirage instantané : cadre blanc, image sombre, une silhouette de trop."""
    rng = random.Random(303)
    image = canvas()
    px = image.load()
    frame(px, 2, 1, 13, 14, OUTLINE)
    rect(px, 3, 2, 12, 13, PAPER)
    rect(px, 3, 11, 12, 13, PAPER_MID)          # marge basse plus large
    frame(px, 4, 3, 11, 10, PAPER_DARK)
    # Émulsion grise : assez claire pour que la silhouette se détache en noir,
    # comme sur un vrai tirage sous-exposé.
    rect(px, 5, 4, 10, 9, (118, 118, 124))
    rect(px, 5, 4, 10, 5, (142, 142, 148))      # ciel délavé
    rect(px, 5, 8, 10, 9, (78, 78, 84))         # sol plus sombre
    for x in range(5, 11):
        if rng.random() < 0.5:
            put(px, x, 7, (96, 96, 102))

    # La silhouette : une tête, un tronc, un bras trop long. Rien d'autre.
    put(px, 8, 4, (34, 32, 36))
    put(px, 8, 5, (22, 20, 24))
    put(px, 7, 5, (48, 46, 50))
    put(px, 8, 6, (22, 20, 24))
    put(px, 8, 7, (30, 28, 32))
    put(px, 9, 6, (52, 50, 54))
    put(px, 9, 7, (44, 42, 46))

    # Coin corné en bas à droite, et l'ombre qu'il projette.
    px[12, 13] = (0, 0, 0, 0)
    put(px, 11, 13, PAPER_DARK)
    put(px, 12, 12, PAPER_MID)
    put(px, 3, 2, HIGHLIGHT)
    return image


def incomplete_journal():
    """Carnet incomplet : dos cousu, pages arrachées, un vide au milieu."""
    rng = random.Random(404)
    image = canvas()
    px = image.load()
    frame(px, 2, 1, 13, 14, OUTLINE)
    rect(px, 3, 2, 12, 13, LEATHER)
    rect(px, 3, 2, 4, 13, LEATHER_DARK)          # dos
    rect(px, 5, 2, 12, 3, LEATHER_LIGHT)         # reflet sur le plat

    # Tranche de pages à droite, avec deux cahiers manquants.
    for y in range(3, 13):
        colour = PAPER if y % 2 else PAPER_MID
        rect(px, 10, y, 12, y, colour)
    for y in (6, 7, 10):
        rect(px, 10, y, 12, y, LEATHER_DARK)     # le vide laissé par l'arrachage
    put(px, 13, 6, PAPER_DARK)

    # Deux pages qui dépassent, arrachées de travers.
    put(px, 13, 4, PAPER)
    put(px, 14, 4, PAPER_MID)
    put(px, 13, 9, PAPER)
    put(px, 14, 10, PAPER_DARK)

    # Cordon de fermeture, dénoué.
    for y in range(2, 14):
        put(px, 8, y, LEATHER_DARK)
    put(px, 8, 14, LEATHER_LIGHT)
    put(px, 9, 15, LEATHER_DARK)

    # Coutures du dos et usure.
    for y in range(3, 13, 3):
        put(px, 3, y, PAPER_DARK)
    speckle(px, [(x, y) for y in range(2, 14) for x in range(5, 10)],
            LEATHER_DARK, 0.12, rng)
    put(px, 5, 2, LEATHER_LIGHT)
    return image


def observation_fragment():
    """Tesson gravé : bâtons de comptage, un dernier trait inachevé."""
    rng = random.Random(505)
    image = canvas()
    px = image.load()
    shape = [
        (5, 9, 2), (4, 11, 3), (3, 12, 4), (2, 12, 5), (2, 13, 6), (2, 13, 7),
        (3, 12, 8), (3, 12, 9), (4, 11, 10), (5, 10, 11), (6, 9, 12), (7, 8, 13),
    ]
    for x0, x1, y in shape:
        rect(px, x0, y, x1, y, BONE_MID)
        put(px, x0, y, OUTLINE)
        put(px, x1, y, OUTLINE)
    for x0, x1, y in shape[:4]:
        rect(px, x0 + 1, y, min(x1 - 1, x0 + 3), y, BONE)
    for x0, x1, y in shape[-4:]:
        rect(px, max(x0 + 1, x1 - 3), y, x1 - 1, y, BONE_DARK)
    for x in range(shape[0][0], shape[0][1] + 1):
        put(px, x, 1, OUTLINE)
    for x in range(shape[-1][0], shape[-1][1] + 1):
        put(px, x, 14, OUTLINE)

    # Comptage : quatre traits barrés, puis un cinquième groupe interrompu.
    for x in (4, 6, 8):
        for y in range(5, 9):
            put(px, x, y, SHADOW)
    for x in range(4, 9):
        put(px, x, 7, SHADOW)
    put(px, 10, 5, SHADOW)
    put(px, 10, 6, SHADOW)          # le dernier trait s'arrête à mi-hauteur

    # Arête fraîche : le tesson vient d'être détaché.
    put(px, 3, 4, HIGHLIGHT)
    put(px, 4, 3, BONE)
    speckle(px, opaque(px), BONE_DARK, 0.08, rng)
    return image


def silhouette_glass():
    """Verre cerclé : l'ombre à l'intérieur n'a rien à refléter dehors."""
    image = canvas()
    px = image.load()
    disc(px, 7.5, 7.0, 6.6, OUTLINE)
    disc(px, 7.5, 7.0, 5.9, IRON)                 # cerclage
    disc(px, 7.5, 7.0, 5.0, IRON_DARK)
    disc(px, 7.5, 7.0, 4.4, GLASS)
    disc(px, 6.6, 6.0, 3.0, GLASS_LIGHT)
    # Manche court, riveté.
    rect(px, 7, 13, 8, 15, OUTLINE)
    put(px, 7, 13, IRON_LIGHT)
    put(px, 8, 14, IRON_DARK)

    # La silhouette dans le verre : centrée, immobile, sans reflet associé.
    put(px, 8, 5, SHADOW)
    put(px, 8, 6, OUTLINE)
    put(px, 7, 6, SHADOW)
    put(px, 8, 7, OUTLINE)
    put(px, 8, 8, SHADOW)
    put(px, 9, 8, SHADOW)

    # Reflet du cerclage, en haut à gauche seulement.
    put(px, 4, 3, HIGHLIGHT)
    put(px, 3, 4, IRON_LIGHT)
    put(px, 11, 10, IRON_DARK)
    return image


def cracked_lens():
    """Lentille de caméra fendue : laiton, verre profond, fêlure vive."""
    image = canvas()
    px = image.load()
    disc(px, 7.5, 7.5, 7.0, OUTLINE)
    disc(px, 7.5, 7.5, 6.3, BRASS_DARK)
    disc(px, 7.5, 7.5, 5.6, BRASS)
    disc(px, 7.5, 7.5, 4.6, OUTLINE)
    disc(px, 7.5, 7.5, 4.1, TEAL_DARK)
    disc(px, 6.8, 6.6, 2.6, TEAL)
    disc(px, 6.2, 6.0, 1.3, TEAL_LIGHT)

    # Fêlure : une diagonale brisée, éclats vifs de part et d'autre.
    for x, y in ((3, 11), (4, 10), (5, 10), (6, 9), (7, 8), (8, 8), (9, 7),
                 (10, 6), (11, 5)):
        put(px, x, y, GLASS_LIGHT)
    for x, y in ((5, 11), (7, 9), (9, 8), (10, 5)):
        put(px, x, y, GLASS_DARK)
    put(px, 8, 11, GLASS_LIGHT)
    put(px, 9, 12, GLASS_DARK)

    # Filetage du barillet et reflet.
    put(px, 4, 3, BRASS_LIGHT)
    put(px, 3, 5, BRASS_LIGHT)
    put(px, 12, 10, BRASS_DARK)
    put(px, 10, 13, BRASS_DARK)
    return image


def memoire_disc():
    """Disque « Mémoire » : vinyle, sillons, étiquette turquoise ébréchée."""
    rng = random.Random(707)
    image = canvas()
    px = image.load()
    disc(px, 7.5, 7.5, 7.4, OUTLINE)
    disc(px, 7.5, 7.5, 6.8, VINYL)
    # Sillons concentriques : un anneau clair sur deux.
    for radius in (6.3, 5.4, 4.5):
        disc(px, 7.5, 7.5, radius, VINYL_LIGHT, inner=radius - 0.6)
    disc(px, 7.5, 7.5, 3.4, TEAL_DARK)
    disc(px, 7.5, 7.5, 2.9, TEAL)
    disc(px, 6.9, 6.9, 1.8, TEAL_LIGHT)
    put(px, 7, 7, OUTLINE)                       # trou central
    put(px, 8, 7, OUTLINE)

    # Reflet d'une source unique, en haut à gauche.
    for x, y in ((4, 3), (5, 2), (3, 4), (6, 2)):
        put(px, x, y, VINYL_LIGHT)
    put(px, 5, 3, (96, 92, 100))
    # Griffure : le disque a été joué trop souvent.
    for x, y in ((10, 11), (11, 10), (12, 9)):
        put(px, x, y, (84, 80, 88))
    speckle(px, opaque(px), VINYL_LIGHT, 0.05, rng)
    return image


ITEMS = {
    "ash_sigil": ash_sigil,
    "blackened_plumb_bob": blackened_plumb_bob,
    "impossible_photo": impossible_photo,
    "incomplete_journal": incomplete_journal,
    "observation_fragment": observation_fragment,
    "silhouette_glass": silhouette_glass,
    "cracked_lens": cracked_lens,
    "memoire_disc": memoire_disc,
}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default=OUT_DEFAULT)
    args = parser.parse_args()
    os.makedirs(args.out, exist_ok=True)
    for name, builder in ITEMS.items():
        path = os.path.join(args.out, name + ".png")
        builder().save(path)
        print(f"  {name}.png  16x16")


if __name__ == "__main__":
    main()

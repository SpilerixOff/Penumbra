#!/usr/bin/env python3
"""Peint les assets du Relevé (0.18.0).

  1. textures/gui/journal_spread.png     512x288  double page de parchemin
  2. textures/gui/journal_portraits.png  512x512  atlas 4x4 de croquis à l'encre
  3. textures/item/survey_journal.png    16x16
  4. textures/item/torn_page.png         16x16

Les quinze croquis sont dessinés au trait, à l'encre sépia, avec des hachures :
c'est un carnet de terrain, pas un bestiaire imprimé. Chaque sujet est réduit à
sa silhouette la plus reconnaissable — ce que quelqu'un dessinerait de mémoire,
vite, en tremblant un peu.

Déterministe. Usage : python3 tools/gen_journal_assets.py
"""

import argparse
import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter

BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..",
                    "src", "main", "resources", "assets", "penumbra", "textures")

# ——————————————————————————— palettes ———————————————————————————
PAPER = (206, 190, 158)
PAPER_LIGHT = (222, 209, 180)
PAPER_DARK = (176, 158, 126)
STAIN = (150, 126, 88)
FOLD = (146, 128, 100)
INK = (44, 34, 24, 255)
INK_SOFT = (74, 58, 42, 210)
INK_PALE = (96, 78, 58, 150)

CELL = 128
GRID = 4


# ——————————————————————————— parchemin ———————————————————————————

def journal_spread(width=512, height=288):
    """Double page : fibres, auréoles d'humidité, pliure douce, bords mangés.

    Les taches sont peintes sur un calque à part, fortement floutées puis
    composées à très faible opacité : une auréole d'humidité se devine, elle ne
    se voit pas. Une tache franche donnerait un fond sale, pas du vieux papier.
    """
    rng = random.Random(1809)
    image = Image.new("RGBA", (width, height), (*PAPER, 255))
    draw = ImageDraw.Draw(image, "RGBA")

    # Fibres : très nombreuses, très faibles, jamais alignées.
    for _ in range(3400):
        x = rng.randrange(width)
        y = rng.randrange(height)
        length = rng.randint(2, 7)
        shade = PAPER_LIGHT if rng.random() < 0.6 else PAPER_DARK
        draw.line([(x, y), (x + length, y)], fill=(*shade, 26))

    # Auréoles : calque flouté, opacité finale très basse.
    stains = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    stain_draw = ImageDraw.Draw(stains, "RGBA")
    for _ in range(22):
        radius = rng.randint(18, 58)
        cx = rng.randrange(width)
        cy = rng.randrange(height)
        stain_draw.ellipse([cx - radius, cy - radius, cx + radius, cy + radius],
                           fill=(*STAIN, rng.randint(24, 44)))
    stains = stains.filter(ImageFilter.GaussianBlur(radius=26))
    image = Image.alpha_composite(image, stains)
    draw = ImageDraw.Draw(image, "RGBA")

    # Réglure du feuillet de droite : il écrivait droit, sur des lignes tracées
    # à la règle. Le feuillet de gauche reste vierge, il y collait ses croquis.
    middle = width // 2
    for y in range(40, height - 44, 12):
        draw.line([(middle + 22, y), (width - 26, y)], fill=(*FOLD, 26))

    # Pliure : deux ombres douces convergentes, pas une barre.
    fold = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    fold_draw = ImageDraw.Draw(fold, "RGBA")
    for offset in range(0, 16):
        alpha = int(52 * (1.0 - offset / 16.0) ** 1.4)
        if alpha <= 0:
            continue
        fold_draw.line([(middle - offset, 0), (middle - offset, height)],
                       fill=(*FOLD, alpha))
        fold_draw.line([(middle + offset, 0), (middle + offset, height)],
                       fill=(*FOLD, alpha))
    fold = fold.filter(ImageFilter.GaussianBlur(radius=5))
    image = Image.alpha_composite(image, fold)

    # Salissure des coins : un carnet manipulé pendant quatre ans.
    shadow = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    shadow_draw = ImageDraw.Draw(shadow, "RGBA")
    shadow_draw.rectangle([0, 0, width - 1, height - 1],
                          outline=(66, 50, 34, 120), width=10)
    shadow = shadow.filter(ImageFilter.GaussianBlur(radius=11))
    image = Image.alpha_composite(image, shadow)

    # Bords mangés, en dernier : le contour d'un vieux papier n'est jamais net.
    edges = ImageDraw.Draw(image, "RGBA")
    for x in range(width):
        edges.line([(x, 0), (x, rng.randint(0, 3))], fill=(0, 0, 0, 0))
        edges.line([(x, height - rng.randint(0, 3)), (x, height)], fill=(0, 0, 0, 0))
    for y in range(height):
        edges.line([(0, y), (rng.randint(0, 3), y)], fill=(0, 0, 0, 0))
        edges.line([(width - rng.randint(0, 3), y), (width, y)], fill=(0, 0, 0, 0))
    return image


# ——————————————————————————— croquis à l'encre ———————————————————————————

def hatch(draw, box, rng, spacing=5, alpha=120, angle=1):
    """Hachures diagonales dans une boîte : l'ombre d'un croquis à la plume."""
    x0, y0, x1, y1 = box
    span = (x1 - x0) + (y1 - y0)
    for offset in range(0, span, spacing):
        if angle > 0:
            start = (x0 + offset, y0)
            end = (x0, y0 + offset)
        else:
            start = (x1 - offset, y0)
            end = (x1, y0 + offset)
        jitter = rng.randint(-1, 1)
        draw.line([(start[0], start[1] + jitter), (end[0], end[1] + jitter)],
                  fill=(*INK[:3], alpha), width=1)


def shaky(draw, points, rng, width=2, alpha=255, closed=False):
    """Polyligne tremblée : aucun trait de ce carnet n'est droit."""
    jittered = [(x + rng.randint(-1, 1), y + rng.randint(-1, 1)) for x, y in points]
    if closed:
        jittered.append(jittered[0])
    draw.line(jittered, fill=(*INK[:3], alpha), width=width, joint="curve")


def cell_canvas():
    return Image.new("RGBA", (CELL, CELL), (0, 0, 0, 0))


def p_frontispiece(rng):
    """Le Bûcher : la cheminée et son panache. La page de garde."""
    image = cell_canvas()
    d = ImageDraw.Draw(image, "RGBA")
    shaky(d, [(48, 118), (48, 46), (80, 46), (80, 118)], rng, 3)
    for y in range(52, 118, 9):
        shaky(d, [(48, y), (80, y)], rng, 1, 120)
    hatch(d, (50, 60, 78, 116), rng, 6, 70)
    # Panache : trois volutes qui s'écartent en montant.
    for index, sway in enumerate((0, 7, -6)):
        points = []
        for step in range(9):
            t = step / 8.0
            points.append((64 + sway * t + math.sin(step + index * 2) * 6 * t,
                           44 - step * 4.6))
        shaky(d, points, rng, 2, 150 - index * 30)
    shaky(d, [(28, 120), (100, 120)], rng, 2, 200)
    return image


def p_host(rng):
    """L'Hôte : très haut, tête minuscule, bras jusqu'aux genoux."""
    image = cell_canvas()
    d = ImageDraw.Draw(image, "RGBA")
    d.ellipse([56, 14, 74, 36], outline=INK, width=2)
    shaky(d, [(65, 36), (65, 44)], rng, 2)
    shaky(d, [(52, 44), (78, 44), (74, 92), (56, 92)], rng, 2, closed=True)
    shaky(d, [(52, 46), (44, 88), (46, 104)], rng, 2)
    shaky(d, [(78, 46), (86, 90), (84, 106)], rng, 2)
    shaky(d, [(59, 92), (57, 122)], rng, 2)
    shaky(d, [(71, 92), (74, 122)], rng, 2)
    hatch(d, (54, 50, 76, 90), rng, 7, 80)
    return image


def p_watcher(rng):
    """L'Observateur : une masse noire, aucun détail, jambes écartées."""
    image = cell_canvas()
    d = ImageDraw.Draw(image, "RGBA")
    d.ellipse([54, 20, 76, 44], fill=(*INK[:3], 235))
    d.polygon([(50, 46), (80, 46), (86, 96), (44, 96)], fill=(*INK[:3], 225))
    shaky(d, [(56, 96), (50, 124)], rng, 3)
    shaky(d, [(74, 96), (80, 124)], rng, 3)
    return image


def p_scuttler(rng):
    """La Cavaleuse : basse, huit pattes, en pleine course."""
    image = cell_canvas()
    d = ImageDraw.Draw(image, "RGBA")
    d.ellipse([44, 62, 88, 84], outline=INK, width=2)
    hatch(d, (48, 64, 84, 82), rng, 5, 90)
    for index in range(4):
        x = 48 + index * 11
        shaky(d, [(x, 64), (x - 8, 44), (x - 14, 36)], rng, 2, 200)
        shaky(d, [(x + 2, 82), (x - 6, 100), (x - 12, 110)], rng, 2, 200)
    # Trainée de vitesse : elle n'est jamais dessinée à l'arrêt.
    for y in (66, 72, 78):
        shaky(d, [(96, y), (118, y)], rng, 1, 90)
    return image


def p_contrefait(rng):
    """Le Contrefait : proportions ratées, un côté plus lourd que l'autre."""
    image = cell_canvas()
    d = ImageDraw.Draw(image, "RGBA")
    d.ellipse([50, 16, 80, 42], outline=INK, width=2)
    shaky(d, [(46, 44), (84, 48), (78, 96), (52, 92)], rng, 2, closed=True)
    shaky(d, [(46, 48), (34, 82)], rng, 3)
    shaky(d, [(84, 50), (94, 74), (90, 86)], rng, 2)
    shaky(d, [(56, 94), (52, 124)], rng, 3)
    shaky(d, [(72, 96), (78, 120)], rng, 2)
    hatch(d, (50, 50, 80, 92), rng, 5, 100, angle=-1)
    # Le point d'interrogation qu'il a fini par écrire à côté.
    d.text((96, 100), "?", fill=(*INK[:3], 180))
    return image


def p_rampant(rng):
    """Le Rampant : plaqué contre un plafond, membres écartés."""
    image = cell_canvas()
    d = ImageDraw.Draw(image, "RGBA")
    shaky(d, [(14, 26), (114, 26)], rng, 3, 230)
    for x in range(18, 112, 8):
        shaky(d, [(x, 26), (x - 4, 18)], rng, 1, 110)
    d.ellipse([56, 30, 74, 48], outline=INK, width=2)
    shaky(d, [(58, 48), (72, 48), (76, 76), (54, 76)], rng, 2, closed=True)
    for dx, dy in ((-1, 1), (1, 1), (-1, -1), (1, -1)):
        shaky(d, [(65 + dx * 8, 56 + dy * 6),
                  (65 + dx * 30, 40 + dy * 22),
                  (65 + dx * 40, 34 + dy * 30)], rng, 2)
    return image


def p_projectionist(rng):
    """Le Projectionniste : une tête-objectif et deux bobines."""
    image = cell_canvas()
    d = ImageDraw.Draw(image, "RGBA")
    d.rectangle([46, 22, 82, 52], outline=INK, width=2)
    d.ellipse([56, 30, 72, 46], outline=INK, width=2)
    d.ellipse([61, 35, 67, 41], fill=(*INK[:3], 220))
    d.ellipse([34, 20, 48, 34], outline=INK, width=2)
    d.ellipse([80, 20, 94, 34], outline=INK, width=2)
    shaky(d, [(50, 52), (78, 52), (74, 100), (54, 100)], rng, 2, closed=True)
    hatch(d, (52, 56, 76, 98), rng, 6, 80)
    shaky(d, [(58, 100), (56, 124)], rng, 2)
    shaky(d, [(70, 100), (73, 124)], rng, 2)
    # Faisceau : le cône de lumière projeté vers l'avant.
    shaky(d, [(72, 36), (112, 20)], rng, 1, 120)
    shaky(d, [(72, 40), (112, 56)], rng, 1, 120)
    return image


def p_faux_creature(rng):
    """Le Faux-Semblant : une tête de vache, et un œil qui ne cligne pas."""
    image = cell_canvas()
    d = ImageDraw.Draw(image, "RGBA")
    shaky(d, [(38, 44), (90, 44), (94, 88), (34, 88)], rng, 2, closed=True)
    d.ellipse([26, 48, 42, 68], outline=INK, width=2)
    d.ellipse([86, 48, 102, 68], outline=INK, width=2)
    shaky(d, [(48, 92), (80, 92), (78, 108), (50, 108)], rng, 2, closed=True)
    d.ellipse([55, 96, 60, 102], fill=(*INK[:3], 200))
    d.ellipse([68, 96, 73, 102], fill=(*INK[:3], 200))
    # Les deux yeux : le gauche normal, le droit trop rond et trop fixe.
    d.ellipse([46, 58, 58, 68], outline=INK, width=2)
    d.ellipse([50, 61, 54, 65], fill=(*INK[:3], 230))
    d.ellipse([70, 56, 86, 70], outline=INK, width=3)
    d.ellipse([76, 61, 80, 65], fill=(*INK[:3], 255))
    hatch(d, (40, 48, 88, 86), rng, 8, 55)
    return image


def p_absent(rng):
    """L'Absent : deux fois la même silhouette, dont une plus pâle."""
    image = cell_canvas()
    d = ImageDraw.Draw(image, "RGBA")

    def figure(x, alpha, width):
        d.ellipse([x - 10, 22, x + 10, 44], outline=(*INK[:3], alpha), width=width)
        shaky(d, [(x - 13, 46), (x + 13, 46), (x + 10, 92), (x - 10, 92)],
              rng, width, alpha, closed=True)
        shaky(d, [(x - 13, 48), (x - 20, 86)], rng, width, alpha)
        shaky(d, [(x + 13, 48), (x + 20, 86)], rng, width, alpha)
        shaky(d, [(x - 6, 92), (x - 8, 122)], rng, width, alpha)
        shaky(d, [(x + 6, 92), (x + 8, 122)], rng, width, alpha)

    figure(40, 255, 2)
    figure(88, 110, 1)
    # Le trait qui les relie, tracé après coup, plus appuyé.
    shaky(d, [(52, 68), (76, 68)], rng, 1, 150)
    return image


def p_listener(rng):
    """L'Écouteur : deux pavillons, aucun œil, mâchoire décrochée."""
    image = cell_canvas()
    d = ImageDraw.Draw(image, "RGBA")
    d.ellipse([50, 16, 80, 54], outline=INK, width=2)
    # Pavillons : deux grandes ellipses inclinées, nervurées.
    for side, cx, rot in ((-1, 30, 0.3), (1, 100, -0.25)):
        d.ellipse([cx - 20, 16, cx + 20, 62], outline=INK, width=2)
        for scale in (0.72, 0.46):
            d.ellipse([cx - 20 * scale, 39 - 23 * scale,
                       cx + 20 * scale, 39 + 23 * scale],
                      outline=(*INK[:3], 130), width=1)
        d.ellipse([cx - 4, 35, cx + 4, 45], fill=(*INK[:3], 230))
    # Les deux coutures à la place des yeux.
    for y, x0, x1 in ((30, 55, 64), (33, 67, 76)):
        shaky(d, [(x0, y), (x1, y)], rng, 2, 240)
        for x in range(x0, x1, 3):
            shaky(d, [(x, y - 3), (x, y + 3)], rng, 1, 200)
    # Mâchoire ouverte.
    d.ellipse([57, 44, 73, 60], fill=(*INK[:3], 210))
    shaky(d, [(56, 58), (74, 58), (72, 100), (58, 100)], rng, 2, closed=True)
    shaky(d, [(56, 60), (46, 104)], rng, 2)
    shaky(d, [(74, 60), (84, 106)], rng, 2)
    return image


def p_originel(rng):
    """L'Originel : de face, au centre, entouré de blanc. Trois traits seulement."""
    image = cell_canvas()
    d = ImageDraw.Draw(image, "RGBA")
    d.ellipse([18, 18, 110, 110], outline=(*INK[:3], 90), width=1)
    d.ellipse([48, 34, 80, 72], outline=INK, width=3)
    shaky(d, [(50, 74), (78, 74), (74, 112), (54, 112)], rng, 3, closed=True)
    # Il n'a pas osé dessiner le visage : trois traits, puis il a arrêté.
    shaky(d, [(56, 48), (62, 48)], rng, 2, 200)
    shaky(d, [(68, 48), (74, 48)], rng, 2, 200)
    shaky(d, [(58, 60), (72, 60)], rng, 1, 120)
    return image


def p_method_camera(rng):
    """La page du flash : un appareil et son éclat."""
    image = cell_canvas()
    d = ImageDraw.Draw(image, "RGBA")
    shaky(d, [(30, 52), (98, 52), (98, 96), (30, 96)], rng, 3, closed=True)
    d.ellipse([52, 60, 78, 88], outline=INK, width=2)
    d.ellipse([59, 67, 71, 81], outline=INK, width=1)
    shaky(d, [(34, 44), (52, 44), (52, 52), (34, 52)], rng, 2, closed=True)
    hatch(d, (32, 54, 96, 94), rng, 9, 45)
    for angle in range(0, 360, 30):
        radian = math.radians(angle)
        d.line([(43, 48),
                (43 + math.cos(radian) * 26, 48 + math.sin(radian) * 26)],
               fill=(*INK[:3], 150), width=1)
    return image


def p_method_refuge(rng):
    """La page des refuges : deux maisons, une fenêtre de trop."""
    image = cell_canvas()
    d = ImageDraw.Draw(image, "RGBA")

    def house(x, y, windows, alpha):
        shaky(d, [(x, y), (x + 40, y), (x + 40, y + 34), (x, y + 34)],
              rng, 2, alpha, closed=True)
        shaky(d, [(x - 5, y), (x + 20, y - 18), (x + 45, y)], rng, 2, alpha)
        for index in range(windows):
            wx = x + 6 + index * 11
            d.rectangle([wx, y + 9, wx + 7, y + 17], outline=(*INK[:3], alpha), width=1)

    house(16, 42, 2, 255)
    house(70, 52, 3, 150)
    d.text((104, 84), "!", fill=(*INK[:3], 210))
    return image


def p_method_stillness(rng):
    """La page de l'immobilité : une figure, et le son qui la contourne."""
    image = cell_canvas()
    d = ImageDraw.Draw(image, "RGBA")
    d.ellipse([56, 30, 72, 48], outline=INK, width=2)
    shaky(d, [(56, 50), (72, 50), (70, 92), (58, 92)], rng, 2, closed=True)
    shaky(d, [(56, 52), (52, 88)], rng, 2)
    shaky(d, [(72, 52), (76, 88)], rng, 2)
    shaky(d, [(61, 92), (60, 118)], rng, 2)
    shaky(d, [(67, 92), (69, 118)], rng, 2)
    # Ondes qui passent de part et d'autre sans la toucher.
    for radius in (26, 38, 50):
        d.arc([64 - radius, 64 - radius, 64 + radius, 64 + radius],
              start=200, end=340, fill=(*INK[:3], 120), width=1)
        d.arc([64 - radius, 64 - radius, 64 + radius, 64 + radius],
              start=20, end=160, fill=(*INK[:3], 120), width=1)
    return image


def p_last_page(rng):
    """La dernière page : la margelle du puits, et la corde qui descend."""
    image = cell_canvas()
    d = ImageDraw.Draw(image, "RGBA")
    d.ellipse([28, 46, 100, 74], outline=INK, width=3)
    shaky(d, [(28, 60), (30, 104)], rng, 2)
    shaky(d, [(100, 60), (98, 104)], rng, 2)
    d.arc([30, 88, 98, 116], start=0, end=180, fill=INK, width=2)
    hatch(d, (34, 62, 94, 100), rng, 7, 60)
    # La corde : elle descend dans le noir et le trait s'arrête net.
    points = [(64, 56)]
    for step in range(1, 9):
        points.append((64 + math.sin(step * 1.1) * 3, 56 + step * 8))
    shaky(d, points, rng, 2, 220)
    d.ellipse([52, 52, 76, 66], fill=(*INK[:3], 235))
    return image


PORTRAITS = [
    p_frontispiece, p_host, p_watcher, p_scuttler,
    p_contrefait, p_rampant, p_projectionist, p_faux_creature,
    p_absent, p_listener, p_originel, p_method_camera,
    p_method_refuge, p_method_stillness, p_last_page,
]


def journal_portraits():
    """Atlas 4×4 de 128×128 : quinze croquis, la seizième case reste vide."""
    atlas = Image.new("RGBA", (CELL * GRID, CELL * GRID), (0, 0, 0, 0))
    for index, painter in enumerate(PORTRAITS):
        rng = random.Random(4200 + index * 37)
        cell = painter(rng)
        # Léger flou : une plume ne produit jamais un bord parfaitement net.
        cell = cell.filter(ImageFilter.GaussianBlur(radius=0.4))
        atlas.paste(cell, ((index % GRID) * CELL, (index // GRID) * CELL), cell)
    return atlas


# ——————————————————————————— objets 16×16 ———————————————————————————

def survey_journal():
    """Carnet épais, cuir gonflé, cordon dénoué, un tirage qui dépasse."""
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = image.load()

    def put(x, y, color):
        if 0 <= x < 16 and 0 <= y < 16:
            px[x, y] = (*color, 255)

    def rect(x0, y0, x1, y1, color):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                put(x, y, color)

    outline = (20, 15, 12)
    leather = (84, 56, 38)
    leather_light = (116, 80, 54)
    leather_dark = (52, 34, 24)
    paper = (218, 206, 178)
    paper_dark = (172, 160, 136)
    photo = (196, 190, 176)

    rect(2, 1, 13, 14, outline)
    rect(3, 2, 12, 13, leather)
    rect(3, 2, 4, 13, leather_dark)              # dos, gonflé
    rect(5, 2, 12, 3, leather_light)             # lumière sur le plat
    # Tranche : le carnet ne ferme plus, les pages débordent.
    for y in range(3, 13):
        rect(10, y, 13, y, paper if y % 2 else paper_dark)
    put(14, 5, paper)
    put(14, 6, paper_dark)
    put(14, 10, paper)
    # Cordon dénoué qui pend.
    for y in range(2, 14):
        put(7, y, leather_dark)
    put(7, 14, leather_light)
    put(8, 15, leather_dark)
    # Un tirage photographique glissé entre deux pages.
    rect(11, 7, 14, 9, photo)
    put(12, 8, (40, 40, 46))
    put(13, 7, (230, 226, 214))
    return image


def torn_page():
    """Feuillet arraché : bord droit dentelé, deux lignes d'écriture."""
    rng = random.Random(1811)
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = image.load()
    paper = (222, 210, 182)
    paper_dark = (176, 164, 138)
    ink = (58, 46, 34)
    outline = (120, 108, 88)

    for y in range(2, 15):
        jag = rng.randint(0, 3)
        for x in range(2, 13 - jag):
            px[x, y] = (*(paper if (x + y) % 5 else paper_dark), 255)
        px[2, y] = (*outline, 255)
        px[12 - jag, y] = (*outline, 255)
    for x in range(2, 12):
        px[x, 2] = (*outline, 255)
        px[x, 14] = (*outline, 255)
    # Deux lignes d'écriture, dont une coupée par la déchirure.
    for y in (5, 8, 11):
        length = rng.randint(4, 8)
        for x in range(4, 4 + length):
            if px[x, y][3] > 0:
                px[x, y] = (*ink, 255)
    return image


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default=BASE)
    args = parser.parse_args()
    gui = os.path.join(args.out, "gui")
    item = os.path.join(args.out, "item")
    os.makedirs(gui, exist_ok=True)
    os.makedirs(item, exist_ok=True)

    journal_spread().save(os.path.join(gui, "journal_spread.png"))
    print("  journal_spread.png     512x288")
    journal_portraits().save(os.path.join(gui, "journal_portraits.png"))
    print("  journal_portraits.png  512x512")
    survey_journal().save(os.path.join(item, "survey_journal.png"))
    print("  survey_journal.png     16x16")
    torn_page().save(os.path.join(item, "torn_page.png"))
    print("  torn_page.png          16x16")


if __name__ == "__main__":
    main()

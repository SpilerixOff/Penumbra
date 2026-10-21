#!/usr/bin/env python3
"""Génère les textures 16x16 des six objets narratifs de Penumbra.

Palette imposée : sombre désaturée (gris, brun, noir, touche de blanc cendré),
contour 1px plus foncé, lisible à petite taille, cohérent Minecraft mais
inquiétant. Reprend les conventions de tools/gen_textures.py (seed fixe,
helper grain, sortie dans assets/penumbra/textures/item/).

Objets :
  cracked_lens         : cercle de verre sombre brisé, fissure éclairée
  incomplete_journal   : couverture brune usée, tranche de pages, coin corné
  observation_fragment : parchemin déchiré, taches de cendre, gribouillis
  blackened_plumb_bob  : corde verticale + masse noircie suspendue
  impossible_photo     : carte claire, zone photo sombre, silhouette floue
  silhouette_glass     : éclat translucide + reflet + silhouette sombre
"""

import os
import random

from PIL import Image, ImageDraw

RANDOM_SEED = 20260717
random.seed(RANDOM_SEED)

BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
TEX_ITEM = os.path.join(BASE, "assets", "penumbra", "textures", "item")

# ——— Palette sombre désaturée ———
K = (0x17, 0x15, 0x1A, 255)   # contour quasi noir #17151a
D = (0x2F, 0x34, 0x38, 255)   # verre sombre #2f3438
G = (0x3A, 0x37, 0x40, 255)   # gris foncé #3a3740
M = (0x5B, 0x56, 0x60, 255)   # gris moyen #5b5660
L = (0xC9, 0xC4, 0xBB, 255)   # blanc cendré #c9c4bb
B = (0x4B, 0x3B, 0x2C, 255)   # brun #4b3b2c
b = (0x6B, 0x55, 0x40, 255)   # brun clair #6b5540
S = (0x2E, 0x25, 0x1D, 255)   # suie / ombre brune #2e251d
P = (0xB8, 0xAC, 0x93, 255)   # parchemin #b8ac93
p = (0x8C, 0x7E, 0x68, 255)   # parchemin ombré
PAPER = (0xB2, 0xAC, 0xA0, 255)  # carte photo vieillie
VOID = (0x1A, 0x18, 0x1E, 255)   # zone photo #1a181e
FIG = (0x0A, 0x09, 0x0C, 255)    # silhouette quasi noire
GLASS = (0x9A, 0xA3, 0xA8, 70)   # verre translucide
GLINT = (0xC9, 0xC4, 0xBB, 130)  # reflet de verre
SHADE = (0x0A, 0x09, 0x0C, 190)  # silhouette dans le verre


def save(img, name):
    out = os.path.join(TEX_ITEM, name + ".png")
    img.save(out)
    print("écrit", out)


def new():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def gen_cracked_lens():
    """Lentille : disque de verre sombre cerclé noir, fissure brisée en éclair
    dont un côté est éclairé au blanc cendré (verre fendu qui accroche la lumière)."""
    img = new()
    d = ImageDraw.Draw(img)
    d.ellipse((2, 2, 13, 13), outline=K, width=1)
    d.ellipse((3, 3, 12, 12), fill=D)
    # Reflet froid atténué dans le quart haut-gauche.
    d.arc((4, 4, 11, 11), start=140, end=250, fill=M, width=1)
    # Fissure principale : du bord supérieur vers le centre, puis vers le bas.
    crack = [(6, 3), (7, 4), (6, 5), (7, 6), (8, 7), (7, 8), (8, 9), (9, 10), (8, 11), (9, 12)]
    branch = [(8, 7), (9, 6), (10, 7), (11, 6)]
    px = img.load()
    for x, y in crack + branch:
        px[x, y] = K
    # Lisière claire de la fissure (décalée d'un pixel) : le verre brisé luit.
    for x, y in crack:
        if x + 1 <= 13 and px[x + 1, y] != (0, 0, 0, 0):
            px[x + 1, y] = M
    px[7, 7] = L   # point d'impact lumineux au croisement
    px[8, 8] = L
    px[5, 5] = M   # éclat résiduel haut-gauche
    save(img, "cracked_lens")


def gen_incomplete_journal():
    """Journal : couverture brune cerclée noir, dos plus sombre, tranche de
    pages cendrées à droite avec pages arrachées, coin corné, pastille titre."""
    img = new()
    d = ImageDraw.Draw(img)
    # Tranche de pages (droite), posée avant la couverture.
    d.rectangle((11, 3, 12, 12), fill=P)
    # Couverture.
    d.rectangle((3, 2, 11, 13), fill=B)
    d.rectangle((3, 2, 11, 13), outline=K, width=1)
    # Dos du livre plus sombre.
    d.line((4, 3, 4, 12), fill=S)
    # Pastille de titre usée.
    d.rectangle((6, 5, 9, 8), outline=b, width=1)
    px = img.load()
    px[7, 6] = S
    px[8, 7] = S
    # Coins cornés / usure du cuir.
    px[5, 3] = b
    px[10, 12] = b
    px[10, 3] = b
    # Pages arrachées : encoches franches dans la tranche + une page qui dépasse.
    px[12, 5] = (0, 0, 0, 0)
    px[12, 6] = (0, 0, 0, 0)
    px[12, 10] = (0, 0, 0, 0)
    px[12, 13] = P
    px[11, 13] = P
    save(img, "incomplete_journal")


def gen_observation_fragment():
    """Fragment : parchemin déchiré (bords irréguliers), taches de cendre,
    lignes d'écriture interrompue."""
    img = new()
    d = ImageDraw.Draw(img)
    d.polygon([(4, 2), (9, 2), (12, 3), (13, 6), (12, 9), (13, 12), (10, 14),
               (6, 13), (3, 11), (4, 8), (3, 5)], fill=P, outline=p)
    px = img.load()
    # Déchirures : encoches transparentes dans les bords.
    for x, y in [(4, 2), (12, 3), (13, 6), (3, 8), (10, 14), (6, 13), (12, 9)]:
        px[x, y] = (0, 0, 0, 0)
    # Taches de cendre aux coins.
    for x, y in [(5, 3), (6, 3), (5, 4), (11, 11), (12, 11), (11, 12), (10, 4)]:
        px[x, y] = S
    px[6, 4] = G
    px[10, 11] = G
    # Gribouillis : deux lignes suivies, une interrompue net.
    d.line((5, 6, 10, 6), fill=K)
    d.line((5, 8, 9, 8), fill=K)
    d.line((5, 10, 7, 10), fill=K)
    px[8, 10] = p
    save(img, "observation_fragment")


def gen_blackened_plumb_bob():
    """Fil à plomb : boucle et corde verticales, masse noircie en losange
    suspendue, un seul reflet métallique, poussière de suie."""
    img = new()
    d = ImageDraw.Draw(img)
    # Boucle d'attache.
    px = img.load()
    px[7, 1] = K
    px[8, 0] = K
    px[9, 1] = K
    # Corde (gris foncé, un pixel).
    d.line((8, 2, 8, 6), fill=G)
    # Masse en losange/tear-drop noirci (fill suie, cerclage noir).
    rows = {7: [8], 8: [7, 8, 9], 9: [6, 7, 8, 9, 10], 10: [6, 7, 8, 9, 10],
            11: [6, 7, 8, 9, 10], 12: [7, 8, 9], 13: [8]}
    for y, xs in rows.items():
        for x in xs:
            edge = x in (xs[0], xs[-1]) or y in (7, 13)
            px[x, y] = K if edge else S
    # Corps métallique grisé sous la suie + unique reflet.
    for x, y in [(7, 9), (8, 9), (9, 9), (7, 10), (8, 10), (9, 10), (8, 11), (8, 8)]:
        px[x, y] = G
    px[7, 8] = M   # reflet
    px[9, 9] = S   # suie incrustée côté ombre
    # Poussière de suie qui tombe.
    px[10, 13] = S
    save(img, "blackened_plumb_bob")


def gen_impossible_photo():
    """Photo : carte claire cerclée noir, zone image quasi noire, silhouette
    humaine floue décentrée (halo irrégulier), grain argentique."""
    img = new()
    d = ImageDraw.Draw(img)
    # Carte.
    d.rectangle((2, 2, 13, 13), fill=PAPER)
    d.rectangle((2, 2, 13, 13), outline=K, width=1)
    # Zone image.
    d.rectangle((4, 4, 11, 10), fill=VOID)
    px = img.load()
    # Silhouette floue : noyau sombre + halo gris irrégulier (tête, épaules, corps).
    core = [(8, 5), (7, 6), (8, 6), (7, 7), (8, 7), (7, 8), (8, 8), (7, 9), (8, 9)]
    halo = [(8, 4), (6, 6), (9, 5), (9, 7), (6, 8), (9, 9), (6, 7)]
    for x, y in halo:
        px[x, y] = G
    for x, y in core:
        px[x, y] = FIG
    # Grain argentique.
    for x, y in [(5, 5), (10, 4), (5, 9), (10, 8), (6, 4)]:
        px[x, y] = M
    # Bandeau inférieur : trace de doigt sale.
    px[4, 12] = p
    px[5, 12] = p
    save(img, "impossible_photo")


def gen_silhouette_glass():
    """Morceau de verre : éclat translucide irrégulier cerclé sombre, reflet
    diagonal clair, et — presque rien — une silhouette sombre au centre."""
    img = new()
    d = ImageDraw.Draw(img)
    d.polygon([(5, 1), (10, 3), (13, 8), (10, 14), (4, 12), (2, 7)],
              fill=GLASS, outline=K)
    px = img.load()
    # Reflet diagonal clair sur le bord haut-gauche.
    for x, y in [(4, 3), (5, 4), (6, 5), (7, 6)]:
        px[x, y] = GLINT
    px[3, 5] = GLINT
    # Silhouette sombre diffuse au centre (le « reflet » impossible).
    for x, y in [(8, 6), (7, 7), (8, 7), (7, 8), (8, 8), (7, 9), (8, 9)]:
        px[x, y] = SHADE
    px[9, 7] = (0x0A, 0x09, 0x0C, 110)
    px[9, 8] = (0x0A, 0x09, 0x0C, 110)
    save(img, "silhouette_glass")


def gen_preview(names):
    """Planche de contrôle ×16 (plus proche voisin) pour vérification visuelle."""
    scale = 16
    sheet = Image.new("RGBA", (16 * scale * len(names), 16 * scale), (0x24, 0x22, 0x28, 255))
    for i, name in enumerate(names):
        icon = Image.open(os.path.join(TEX_ITEM, name + ".png")).resize(
            (16 * scale, 16 * scale), Image.NEAREST)
        sheet.paste(icon, (i * 16 * scale, 0), icon)
    out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "preview_items.png")
    sheet.save(out)
    print("écrit", out)


if __name__ == "__main__":
    os.makedirs(TEX_ITEM, exist_ok=True)
    names = ["cracked_lens", "incomplete_journal", "observation_fragment",
             "blackened_plumb_bob", "impossible_photo", "silhouette_glass"]
    gen_cracked_lens()
    gen_incomplete_journal()
    gen_observation_fragment()
    gen_blackened_plumb_bob()
    gen_impossible_photo()
    gen_silhouette_glass()
    gen_preview(names)
    print("Textures des objets narratifs générées.")

#!/usr/bin/env python3
"""Textures d'entités v2 (0.5.0) — refonte visuelle des personnages.

Direction artistique :
  - L'HÔTE quitte le gris pâle du placeholder pour rejoindre son propre lore :
    un corps CALCINÉ. Base noir-cendre (#141216), marbrures de charbon,
    fines fissures de braise (#d97b3f) sur les coutures du corps — la cendre
    dans laquelle il se dissout est celle dont il est fait. Yeux braise
    ASYMÉTRIQUES (l'œil droit un pixel plus bas) : l'erreur que l'œil humain
    détecte sans pouvoir la nommer.
  - L'OBSERVATEUR garde sa silhouette quasi noire ; les yeux gris pâle
    deviennent asymétriques (1 px), le visage à peine plus clair est réduit.
  - L'ORIGINEL : proportions de joueur, peau blafarde délavée, chemise et
    pantalon sombres désaturés — un joueur, de loin. De près : PAS DE VISAGE.
    Toutes les faces de la tête sont la même peau nue, avec deux ombres à
    peine plus foncées là où des yeux devraient être.

UV (emprise d'une boîte w×h×d à l'offset (u,v) : largeur 2·(d+w), hauteur d+h ;
face avant = (u+d, v+d) → (u+d+w, v+d+h)) :

host.png (64×64, HostEntityModel) :
  head (0,0) 8×8×10 · body (0,20) 8×20×4 · arm_r (40,0) 3×16×3 ·
  arm_l (40,20) 3×16×3 · leg_r (0,46) 3×14×3 · leg_l (14,46) 3×14×3
watcher.png (64×64, WatcherModel) :
  head (0,0) 8×8×8 · body (16,16) 8×14×4 · arm_r (40,16) 3×12×3 ·
  arm_l (52,16) 3×12×3 · leg_r (0,34) 3×16×3 · leg_l (12,34) 3×16×3
originel.png (64×64, OriginelModel) :
  head (0,0) 8×8×8 · leg_r (0,16) 4×12×4 · leg_l (0,32) 4×12×4 ·
  body (16,16) 8×12×4 · arm_r (40,16) 4×12×4 · arm_l (40,32) 4×12×4
"""

import os
import random

from PIL import Image

RANDOM_SEED = 20260717
random.seed(RANDOM_SEED)

BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
TEX = os.path.join(BASE, "assets", "penumbra", "textures", "entity")

# ---------------------------------------------------------------------------
# Palettes v3 (0.6.0) — relevées sur les planches de référence IA fournies
# par l'utilisateur (v3 Hôte, v4 Observateur, v5 Originel).
# ---------------------------------------------------------------------------

# --- Palette de l'Hôte (planche v3 : charbon veiné de braise) ---
CHAR_BASE = (0x0E, 0x0C, 0x10)     # charbon quasi noir
CHAR_MOTTLE = (0x1C, 0x1A, 0x1F)   # marbrure charbon
CHAR_DEEP = (0x09, 0x08, 0x0B)     # creux les plus sombres
EMBER = (0xE8, 0x81, 0x3C)         # braise vive (veines)
EMBER_DIM = (0x7A, 0x45, 0x26)     # braise mourante
EMBER_HOT = (0xFF, 0xB0, 0x54)     # points incandescents épars
SMOKE_LO = (0x3A, 0x37, 0x40)      # fumée grise du visage (sombre)
SMOKE_HI = (0x55, 0x51, 0x5C)      # fumée grise du visage (claire)
EYE_CORE = (0xFF, 0xAC, 0x4E)      # œil : cœur incandescent
EYE_HALO = (0xB0, 0x5C, 0x28)      # œil : halo

# --- Palette de l'Observateur (planche v4 : noir uniforme, deux yeux pâles) ---
W_DARK = (0x06, 0x06, 0x08)
W_EYE = (0x9A, 0xA0, 0xAA)

# --- Palette de l'Originel (planche v5 : peau blafarde, chemise sarcelle, jean marine) ---
O_SKIN = (0xB5, 0xB1, 0xA9)        # peau grise chaude délavée
O_SKIN_SHADE = (0x9E, 0x9A, 0x92)  # ombre de peau
O_SHIRT = (0x3E, 0x4A, 0x4E)       # chemise sarcelle-grise
O_SHIRT_DK = (0x35, 0x40, 0x46)    # bas de chemise assombri
O_PANTS = (0x25, 0x28, 0x38)       # jean marine éteint
O_PANTS_HI = (0x2B, 0x2E, 0x40)    # reflet du jean
O_EYE_SHADOW = (0xA2, 0x9E, 0x96)  # « presque des yeux » : à peine plus foncé


def emprise(u, v, w, h, d):
    """Rectangle UV complet d'une boîte w×h×d posée à (u, v)."""
    return (u, v, u + 2 * (d + w), v + d + h)


def front(u, v, w, h, d):
    """Rectangle de la face avant."""
    return (u + d, v + d, u + d + w, v + d + h)


def clamp(c):
    return max(0, min(255, c))


def jitter(color, amount):
    dlt = random.randint(-amount, amount)
    return (clamp(color[0] + dlt), clamp(color[1] + dlt), clamp(color[2] + dlt), 255)


def fill_noise(img, rect, base, mottle, mottle_chance=0.22, amount=6):
    """Remplit un rectangle avec la base bruitée + marbrures éparses."""
    x0, y0, x1, y1 = rect
    for x in range(x0, x1):
        for y in range(y0, y1):
            color = mottle if random.random() < mottle_chance else base
            img.putpixel((x, y), jitter(color, amount))


def sprinkle(img, rect, color, chance):
    """Parsème un rectangle de pixels isolés d'une couleur."""
    x0, y0, x1, y1 = rect
    for x in range(x0, x1):
        for y in range(y0, y1):
            if random.random() < chance:
                img.putpixel((x, y), jitter(color, 8))


def crack(img, rect, color, length):
    """Trace une fine fissure verticale irrégulière dans un rectangle."""
    x0, y0, x1, y1 = rect
    if x1 - x0 < 2 or y1 - y0 < 3:
        return
    x = random.randint(x0, x1 - 1)
    y = random.randint(y0, max(y0, y1 - length - 1))
    for i in range(length):
        if y + i >= y1:
            break
        img.putpixel((x, y + i), jitter(color, 10))
        if random.random() < 0.35:
            x = max(x0, min(x1 - 1, x + random.choice((-1, 1))))


def vein(img, rect):
    """
    Veine de braise (planche v3) : serpente verticalement sur TOUTE la hauteur
    du rectangle, majoritairement mourante ({@code EMBER_DIM}), avec des
    segments vifs ({@code EMBER}) et de rares points incandescents
    ({@code EMBER_HOT}) — la lave sous la croûte, jamais uniforme.
    """
    x0, y0, x1, y1 = rect
    if x1 - x0 < 2 or y1 - y0 < 4:
        return
    x = random.randint(x0, x1 - 1)
    for y in range(y0, y1):
        roll = random.random()
        color = EMBER_HOT if roll < 0.08 else (EMBER if roll < 0.40 else EMBER_DIM)
        img.putpixel((x, y), jitter(color, 12))
        if random.random() < 0.45:
            x = max(x0, min(x1 - 1, x + random.choice((-1, 1))))


def gen_host():
    """Planche v3 : charbon quasi noir, VEINES de braise sur toute la longueur
    des membres (avant ET arrière), veine centrale du torse, visage de fumée
    grise marbrée, deux yeux carrés incandescents asymétriques à halo."""
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    parts = {
        "head": (0, 0, 8, 8, 10),
        "body": (0, 20, 8, 20, 4),
        "arm_r": (40, 0, 3, 16, 3),
        "arm_l": (40, 20, 3, 16, 3),
        "leg_r": (0, 46, 3, 14, 3),
        "leg_l": (14, 46, 3, 14, 3),
    }
    # Base : charbon marbré, membres plus profonds.
    for name, (u, v, w, h, d) in parts.items():
        base = CHAR_DEEP if name.startswith(("arm", "leg")) else CHAR_BASE
        fill_noise(img, emprise(u, v, w, h, d), base, CHAR_MOTTLE)

    # Membres : une veine pleine hauteur sur la face AVANT et une sur la face
    # ARRIÈRE (planche v3 : la braise court le long des bras et des jambes).
    # Boîte w×h×d à (u,v) : faces latérales aux lignes v+d..v+d+h ; colonnes :
    # droite u..u+d, avant u+d..u+d+w, gauche u+d+w..u+2d+w, arrière ensuite.
    for u, v, w, h, d in (parts["arm_r"], parts["arm_l"], parts["leg_r"], parts["leg_l"]):
        vein(img, (u + d, v + d, u + d + w, v + d + h))              # face avant
        vein(img, (u + 2 * d + w, v + d, u + 2 * (d + w), v + d + h))  # face arrière
    # Torse : veine centrale avant (pleine hauteur) + veine arrière + 2 fissures.
    bu, bv, bw, bh, bd = parts["body"]
    vein(img, (bu + bd + 2, bv + bd, bu + bd + bw - 2, bv + bd + bh))
    vein(img, (bu + 2 * bd + bw + 2, bv + bd, bu + 2 * (bd + bw) - 2, bv + bd + bh))
    crack(img, emprise(bu, bv, bw, bh, bd), EMBER_DIM, 6)
    crack(img, emprise(bu, bv, bw, bh, bd), EMBER_DIM, 5)

    # Tête : face avant en fumée grise marbrée (volutes plus claires au centre).
    head_front = front(0, 0, 8, 8, 10)
    hx, hy = head_front[0], head_front[1]
    fill_noise(img, head_front, SMOKE_LO, SMOKE_HI, mottle_chance=0.35, amount=6)
    for x in range(2, 6):  # cœur de volute plus clair, décentré
        for y in range(1, 5):
            if random.random() < 0.5:
                img.putpixel((hx + x, hy + y), jitter(SMOKE_HI, 8))
    # Crâne et faces latérales : poussière de fumée éparse.
    sprinkle(img, (0, 0, 36, 10), SMOKE_LO, 0.08)
    # Yeux incandescents ASYMÉTRIQUES : gauche (2,3), droit (5,4) — halo autour.
    for ex, ey in ((2, 3), (5, 4)):
        img.putpixel((hx + ex, hy + ey), EYE_CORE + (255,))
        for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            px, py = hx + ex + dx, hy + ey + dy
            if head_front[0] <= px < head_front[2] and head_front[1] <= py < head_front[3]:
                if random.random() < 0.7:
                    img.putpixel((px, py), jitter(EYE_HALO, 12))
    return img


def gen_watcher():
    """Planche v4 : noir UNIFORME absorbant — aucun visage, aucun détail.
    Les deux yeux gris pâle asymétriques sont la seule chose qui existe."""
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    parts = {
        "head": (0, 0, 8, 8, 8),
        "body": (16, 16, 8, 14, 4),
        "arm_r": (40, 16, 3, 12, 3),
        "arm_l": (52, 16, 3, 12, 3),
        "leg_r": (0, 34, 3, 16, 3),
        "leg_l": (12, 34, 3, 16, 3),
    }
    for _, (u, v, w, h, d) in parts.items():
        fill_noise(img, emprise(u, v, w, h, d), W_DARK, W_DARK, mottle_chance=0.0, amount=2)
    # Yeux gris pâle ASYMÉTRIQUES (planche v4 : le droit plus bas) — rien d'autre.
    hx, hy = front(0, 0, 8, 8, 8)[0], front(0, 0, 8, 8, 8)[1]
    img.putpixel((hx + 2, hy + 3), W_EYE + (255,))
    img.putpixel((hx + 5, hy + 4), W_EYE + (255,))
    return img


def gen_originel():
    """Planche v5 : peau blafarde délavée (tête ET avant-bras nus), chemise
    sarcelle-grise à col en V dégradée vers le sombre, jean marine éteint.
    Aucun visage — deux ombres de peau là où des yeux devraient être."""
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    head = (0, 0, 8, 8, 8)
    body = (16, 16, 8, 12, 4)
    arm_r = (40, 16, 4, 12, 4)
    arm_l = (40, 32, 4, 12, 4)
    leg_r = (0, 16, 4, 12, 4)
    leg_l = (0, 32, 4, 12, 4)
    # Tête : peau nue sur TOUTES les faces — il n'a pas de visage.
    fill_noise(img, emprise(*head), O_SKIN, O_SKIN_SHADE, mottle_chance=0.3, amount=4)
    # Corps : chemise, moitié basse assombrie (dégradé de la planche v5).
    bu, bv, bw, bh, bd = body
    rect = emprise(*body)
    fill_noise(img, rect, O_SHIRT, O_SHIRT_DK, mottle_chance=0.25, amount=4)
    fill_noise(img, (rect[0], bv + bd + bh // 2, rect[2], rect[3]),
               O_SHIRT_DK, O_SHIRT_DK, mottle_chance=0.0, amount=4)
    # Col en V : trois pixels de peau au centre haut de la face avant du torse.
    fbx, fby = front(*body)[0], front(*body)[1]
    img.putpixel((fbx + 3, fby), jitter(O_SKIN, 4))
    img.putpixel((fbx + 4, fby), jitter(O_SKIN, 4))
    img.putpixel((fbx + 3, fby + 1), jitter(O_SKIN_SHADE, 4))
    # Bras : manches courtes — chemise sur la moitié haute, PEAU sur la moitié
    # basse (avant-bras nus de la planche v5).
    for u, v, w, h, d in (arm_r, arm_l):
        arect = emprise(u, v, w, h, d)
        fill_noise(img, arect, O_SHIRT, O_SHIRT_DK, mottle_chance=0.25, amount=4)
        fill_noise(img, (arect[0], v + d + h // 2, arect[2], arect[3]),
                   O_SKIN, O_SKIN_SHADE, mottle_chance=0.3, amount=4)
    # Jambes : jean marine éteint avec reflets épars.
    for u, v, w, h, d in (leg_r, leg_l):
        fill_noise(img, emprise(u, v, w, h, d), O_PANTS, O_PANTS_HI, mottle_chance=0.3, amount=4)
    # « Presque des yeux » : deux ombres 1 px à peine plus foncées, symétriques —
    # la seule symétrie parfaite du mod, et c'est la plus fausse.
    fx, fy = front(*head)[0], front(*head)[1]
    img.putpixel((fx + 2, fy + 3), O_EYE_SHADOW + (255,))
    img.putpixel((fx + 5, fy + 3), O_EYE_SHADOW + (255,))
    return img


# --- Palette du Contrefait (planche v6 : la légende mal copiée) ---
C_SKIN = (0xC6, 0x9A, 0x78)        # peau chaude de joueur (planche v6)
C_SKIN_SHADE = (0xA8, 0x7F, 0x60)  # ombre de peau
C_SKIN_BAD = (0x8E, 0x86, 0x80)    # plaques grisâtres : la copie a mal chargé
C_HAIR = (0x2E, 0x20, 0x16)        # cheveux brun très sombre
C_HAIR_HI = (0x4A, 0x34, 0x22)     # mèches plus claires
C_BEARD = (0x5A, 0x40, 0x2C)       # barbe/bouche brune (planche v6)
C_SHIRT = (0x2A, 0xA7, 0x99)       # sarcelle SATURÉE — presque LA chemise
C_SHIRT_DK = (0x1F, 0x8C, 0x82)
C_PANTS = (0x32, 0x32, 0xA8)       # jambe bleu roi
C_PANTS_OFF = (0x5A, 0x4A, 0xA8)   # l'AUTRE jambe : violette — la teinte à côté
C_SHOE = (0x8A, 0x8A, 0x8A)        # chaussures grises
C_EYE = (0xFF, 0xFF, 0xFF)         # les yeux blancs de la légende
C_EYE_GLOW = (0xE4, 0xE4, 0xEE)    # halo froid des yeux


def gen_contrefait():
    """Planche v6 : chemise sarcelle saturée à col en V avec la peau qui
    perce aux coutures, œil GAUCHE large et brûlant (2 px + halo), œil droit
    petit et 1 px trop haut, barbe brune, plaque chauve à l'arrière, un bras
    entièrement manché, jambes bleu roi / violette, chaussures grises."""
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    head = (0, 0, 8, 8, 8)
    body = (16, 16, 8, 12, 4)
    arm_r = (40, 16, 4, 12, 4)
    arm_l = (40, 32, 4, 12, 4)
    leg_r = (0, 16, 4, 12, 4)
    leg_l = (0, 32, 4, 12, 4)
    hu, hv, hw, hh, hd = head
    # Tête : peau à plaques grisâtres.
    fill_noise(img, emprise(*head), C_SKIN, C_SKIN_BAD, mottle_chance=0.15, amount=5)
    # Cheveux : dessus entier + 2 rangées hautes des 4 faces, mèches claires.
    fill_noise(img, (hu + hd, hv, hu + hd + hw, hv + hd), C_HAIR, C_HAIR_HI,
               mottle_chance=0.25, amount=6)
    fill_noise(img, (hu, hv + hd, hu + 2 * (hd + hw), hv + hd + 2), C_HAIR, C_HAIR_HI,
               mottle_chance=0.25, amount=6)
    # Face ARRIÈRE (cols 24–32) : cheveux pleins sur 5 rangées…
    fill_noise(img, (24, hv + hd, 32, hv + hd + 5), C_HAIR, C_HAIR_HI,
               mottle_chance=0.25, amount=6)
    # …avec la PLAQUE CHAUVE ovale au centre (planche v6 : trou de peau 3×3).
    fill_noise(img, (26, hv + hd + 1, 29, hv + hd + 4), C_SKIN, C_SKIN_SHADE,
               mottle_chance=0.3, amount=4)
    fx, fy = front(*head)[0], front(*head)[1]
    # Yeux DÉPAREILLÉS : gauche LARGE et brûlant (2 px + halo latéral),
    # droit petit, 1 px trop haut.
    img.putpixel((fx + 1, fy + 4), C_EYE + (255,))
    img.putpixel((fx + 2, fy + 4), C_EYE + (255,))
    img.putpixel((fx + 0, fy + 4), C_EYE_GLOW + (255,))
    img.putpixel((fx + 6, fy + 3), C_EYE + (255,))
    # Barbe/bouche brune sur le bas du visage (planche v6).
    for x in range(2, 6):
        img.putpixel((fx + x, fy + 6), jitter(C_BEARD, 8))
    img.putpixel((fx + 3, fy + 7), jitter(C_BEARD, 8))
    img.putpixel((fx + 4, fy + 7), jitter(C_BEARD, 8))
    # Corps : chemise saturée, bas assombri, la peau perce aux coutures.
    rect = emprise(*body)
    bu, bv, bw, bh, bd = body
    fill_noise(img, rect, C_SHIRT, C_SHIRT_DK, mottle_chance=0.25, amount=6)
    fill_noise(img, (rect[0], bv + bd + bh - 3, rect[2], rect[3]), C_SHIRT_DK, C_SHIRT_DK,
               mottle_chance=0.0, amount=6)
    sprinkle(img, (rect[0], rect[3] - 2, rect[2], rect[3]), C_SKIN_BAD, 0.18)
    sprinkle(img, (rect[0], bv + bd, rect[2], bv + bd + 1), C_SKIN, 0.15)
    # Col en V : trois pixels de peau au centre haut de la face avant.
    fbx, fby = front(*body)[0], front(*body)[1]
    img.putpixel((fbx + 3, fby), jitter(C_SKIN, 4))
    img.putpixel((fbx + 4, fby), jitter(C_SKIN, 4))
    img.putpixel((fbx + 4, fby + 1), jitter(C_SKIN_SHADE, 4))
    # Bras droit : PEAU à plaques (correct). Bras gauche : entièrement CHEMISE.
    fill_noise(img, emprise(*arm_r), C_SKIN, C_SKIN_BAD, mottle_chance=0.18, amount=5)
    fill_noise(img, emprise(*arm_l), C_SHIRT, C_SHIRT_DK, mottle_chance=0.25, amount=6)
    # Jambes : bleu roi / violette + chaussures grises (2 dernières rangées).
    for (u, v, w, h, d), pants in ((leg_r, C_PANTS), (leg_l, C_PANTS_OFF)):
        lrect = emprise(u, v, w, h, d)
        fill_noise(img, lrect, pants, pants, mottle_chance=0.2, amount=6)
        fill_noise(img, (lrect[0], lrect[3] - 2, lrect[2], lrect[3]), C_SHOE, C_SHOE,
                   mottle_chance=0.0, amount=5)
    return img


def main():
    os.makedirs(TEX, exist_ok=True)
    for name, fn in (("host", gen_host), ("watcher", gen_watcher),
                     ("originel", gen_originel), ("contrefait", gen_contrefait)):
        path = os.path.join(TEX, name + ".png")
        fn().save(path)
        print(f"{name}.png -> {path}")
    print("Textures v2 générées.")


if __name__ == "__main__":
    main()

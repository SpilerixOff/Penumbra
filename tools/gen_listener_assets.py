#!/usr/bin/env python3
"""Peint les deux assets visuels de L'Écouteur (0.17.0).

  1. textures/entity/listener.png       64x64, UV exactement calqué sur ListenerModel
  2. textures/gui/screamer_listener.png 1024x1024, quinzième visage de la banque

Identité reprise de tools/generate_skins.py : peau cireuse, coutures
d'assemblage, vestige de tunique turquoise, asymétries délibérées. La
particularité de L'Écouteur est l'absence totale d'yeux — deux coutures
tendues à leur place — et deux pavillons d'oreille démesurés.

Déterministe (seeds fixes) : relancer produit exactement les mêmes fichiers.
Usage : python3 tools/gen_listener_assets.py
"""

import argparse
import math
import os
import random

import numpy as np
from PIL import Image, ImageFilter

# ——————————————————————————— palette ———————————————————————————
# Volontairement plus grise et plus froide que WAX : cette chose n'a jamais
# essayé d'imiter une couleur de peau, seulement une silhouette.
SKIN = (196, 190, 178)
SKIN_DARK = (158, 152, 142)
SKIN_DEEP = (118, 112, 104)
SEAM = (104, 96, 90)
VEIN = (126, 130, 142)
CANAL = (24, 21, 20)           # l'intérieur des oreilles et de la bouche
CANAL_RIM = (72, 62, 58)
CARTILAGE = (214, 204, 190)    # face interne des pavillons, presque translucide
TEAL = (58, 104, 100)          # vestige de tunique
TEAL_WORN = (96, 118, 114)
NAIL = (206, 198, 180)

BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..",
                    "src", "main", "resources", "assets", "penumbra", "textures")


# ——————————————————————————— utilitaires 64x64 ———————————————————————————

def faces(u, v, w, h, d):
    """Rectangles (x0, y0, x1 exclu, y1 exclu) des 6 faces d'un addBox."""
    return {
        "top": (u + d, v, u + d + w, v + d),
        "bottom": (u + d + w, v, u + d + 2 * w, v + d),
        "right": (u, v + d, u + d, v + d + h),
        "front": (u + d, v + d, u + d + w, v + d + h),
        "left": (u + d + w, v + d, u + d + w + d, v + d + h),
        "back": (u + d + w + d, v + d, u + 2 * d + 2 * w, v + d + h),
    }


def noise_fill(px, rect, base, jitter, rng, dark_edge=0):
    x0, y0, x1, y1 = rect
    for y in range(y0, y1):
        for x in range(x0, x1):
            j = rng.randint(-jitter, jitter)
            r, g, b = (max(0, min(255, c + j)) for c in base)
            if dark_edge and (x == x0 or x == x1 - 1 or y == y0 or y == y1 - 1):
                r, g, b = (max(0, c - dark_edge) for c in (r, g, b))
            px[x, y] = (r, g, b, 255)


def fill_all(px, boxes, base, jitter, rng, dark_edge=0):
    for box in boxes:
        for rect in faces(*box).values():
            noise_fill(px, rect, base, jitter, rng, dark_edge)


def hline(px, x0, x1, y, color):
    for x in range(x0, x1):
        px[x, y] = (*color, 255)


def vline(px, x, y0, y1, color):
    for y in range(y0, y1):
        px[x, y] = (*color, 255)


def dot(px, x, y, color):
    px[x, y] = (*color, 255)


# ——————————————————————————— skin 64x64 ———————————————————————————

HEAD = (0, 0, 8, 6, 8)
EAR_R = (32, 0, 1, 7, 6)
EAR_L = (46, 0, 1, 7, 6)
JAW = (0, 14, 6, 3, 6)
NECK = (24, 14, 3, 3, 3)
HAND_R = (36, 14, 2, 4, 2)
HAND_L = (44, 14, 2, 4, 2)
BODY = (0, 24, 8, 18, 4)
ARM_R = (24, 24, 2, 9, 2)
FOREARM_R = (32, 24, 2, 9, 2)
ARM_L = (40, 24, 2, 9, 2)
FOREARM_L = (48, 24, 2, 9, 2)
THIGH_R = (0, 46, 3, 9, 3)
SHIN_R = (12, 46, 2, 10, 2)
THIGH_L = (20, 46, 3, 9, 3)
SHIN_L = (32, 46, 2, 10, 2)


def paint_listener():
    rng = random.Random(1717)
    image = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = image.load()

    # Base : tout le corps en peau cireuse, arêtes assombries.
    fill_all(px, [HEAD, JAW, NECK, HAND_R, HAND_L], SKIN, 6, rng, dark_edge=10)
    fill_all(px, [BODY], SKIN, 5, rng, dark_edge=12)
    fill_all(px, [ARM_R, FOREARM_R, ARM_L, FOREARM_L], SKIN_DARK, 6, rng, dark_edge=8)
    fill_all(px, [THIGH_R, SHIN_R, THIGH_L, SHIN_L], SKIN_DARK, 6, rng, dark_edge=8)

    paint_head(px, rng)
    paint_ears(px, rng)
    paint_jaw(px, rng)
    paint_body(px, rng)
    paint_limbs(px, rng)
    return image


def paint_head(px, rng):
    f = faces(*HEAD)
    fx0, fy0, fx1, fy1 = f["front"]

    # Aucun œil. Deux coutures tendues, horizontales, là où ils devraient être :
    # la peau a été tirée par-dessus et recousue. C'est toute la créature.
    for y in (fy0 + 2, fy0 + 3):
        pass
    seam_y = fy0 + 2
    for x in range(fx0 + 1, fx0 + 4):
        dot(px, x, seam_y, SEAM)
    for x in range(fx0 + 5, fx0 + 8):
        dot(px, x, seam_y + 1, SEAM)          # asymétrie : la droite pend plus bas
    # Points de suture visibles, un texel plus clair au-dessus de chaque couture.
    for x in (fx0 + 1, fx0 + 3):
        dot(px, x, seam_y - 1, SKIN_DEEP)
    for x in (fx0 + 5, fx0 + 7):
        dot(px, x, seam_y, SKIN_DEEP)

    # Arêtes du crâne : le front est creusé, la boîte est trop étroite.
    hline(px, fx0, fx1, fy0, SKIN_DEEP)
    for x in range(fx0, fx1):
        if rng.random() < 0.45:
            dot(px, x, fy0 + 1, SKIN_DARK)
    # Veines froides sur les tempes, là où l'oreille s'attache.
    vline(px, fx0, fy0 + 1, fy0 + 5, VEIN)
    vline(px, fx1 - 1, fy0 + 2, fy0 + 6, VEIN)

    # Une seule narine, décalée : le nez est un accident, pas un organe.
    dot(px, fx0 + 4, fy0 + 4, CANAL_RIM)

    # Faces latérales : l'attache des pavillons est une cicatrice circulaire.
    for key in ("right", "left"):
        x0, y0, x1, y1 = f[key]
        cx = (x0 + x1) // 2
        cy = y0 + 2
        for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            dot(px, cx + dx, cy + dy, SEAM)
        dot(px, cx, cy, CANAL_RIM)

    # Sommet du crâne : cuir tendu, taches sombres.
    x0, y0, x1, y1 = f["top"]
    for y in range(y0, y1):
        for x in range(x0, x1):
            if rng.random() < 0.2:
                dot(px, x, y, SKIN_DEEP)
    # Arrière : la couture d'assemblage principale, verticale.
    x0, y0, x1, y1 = f["back"]
    vline(px, (x0 + x1) // 2, y0, y1, SEAM)


def paint_ears(px, rng):
    """Pavillons : face interne pâle et nervurée, face externe veinée et sombre."""
    for box, mirrored in ((EAR_R, False), (EAR_L, True)):
        f = faces(*box)
        # Les grandes faces d'un pavillon de 1 px d'épaisseur sont right/left.
        inner_key = "left" if mirrored else "right"
        outer_key = "right" if mirrored else "left"

        x0, y0, x1, y1 = f[inner_key]
        noise_fill(px, (x0, y0, x1, y1), CARTILAGE, 7, rng)
        # Nervures concentriques : trois arcs qui convergent vers le canal.
        for index, offset in enumerate((1, 3, 5)):
            shade = (CANAL_RIM if index == 2 else SKIN_DARK)
            for y in range(y0 + offset, min(y1, y0 + offset + 1)):
                for x in range(x0 + 1, x1 - 1):
                    if (x + y + index) % 2 == 0:
                        dot(px, x, y, shade)
        # Le canal auditif : un trou noir au bas du pavillon.
        cx = (x0 + x1) // 2
        for y in range(y1 - 3, y1 - 1):
            for x in range(cx - 1, cx + 1):
                dot(px, x, y, CANAL)
        dot(px, cx - 1, y1 - 3, CANAL_RIM)

        x0, y0, x1, y1 = f[outer_key]
        noise_fill(px, (x0, y0, x1, y1), SKIN_DARK, 6, rng, dark_edge=14)
        for _ in range(7):
            vx = rng.randrange(x0 + 1, x1 - 1)
            vy = rng.randrange(y0, y1 - 2)
            vline(px, vx, vy, min(y1, vy + 3), VEIN)

        # Le bord du pavillon (top) est translucide, presque blanc.
        x0, y0, x1, y1 = f["top"]
        noise_fill(px, (x0, y0, x1, y1), CARTILAGE, 4, rng)


def paint_jaw(px, rng):
    """Mâchoire décrochée : le dessus est l'intérieur de la bouche, ouvert."""
    f = faces(*JAW)
    x0, y0, x1, y1 = f["top"]
    noise_fill(px, (x0, y0, x1, y1), CANAL, 5, rng)
    # Un anneau de gencive autour du vide.
    hline(px, x0, x1, y0, CANAL_RIM)
    hline(px, x0, x1, y1 - 1, CANAL_RIM)
    vline(px, x0, y0, y1, CANAL_RIM)
    vline(px, x1 - 1, y0, y1, CANAL_RIM)

    # Face avant : le menton, et deux dents inférieures qui dépassent.
    x0, y0, x1, y1 = f["front"]
    noise_fill(px, (x0, y0, x1, y1), SKIN_DARK, 5, rng, dark_edge=12)
    dot(px, x0 + 1, y0, NAIL)
    dot(px, x0 + 4, y0, NAIL)
    hline(px, x0, x1, y1 - 1, SEAM)


def paint_body(px, rng):
    f = faces(*BODY)
    x0, y0, x1, y1 = f["front"]

    # Vestige de tunique turquoise : un col et une bande déchirée en travers.
    for y in range(y0, y0 + 3):
        noise_fill(px, (x0, y, x1, y + 1), TEAL, 8, rng)
    for y in range(y0 + 3, y0 + 9):
        for x in range(x0, x1):
            # Déchirure diagonale : le tissu manque de plus en plus vers la droite.
            if x - x0 < 8 - (y - y0):
                dot(px, x, y, TEAL if rng.random() < 0.7 else TEAL_WORN)
    # Côtes saillantes sous la peau nue, en dessous de la tunique.
    for index, y in enumerate(range(y0 + 10, y0 + 17, 2)):
        for x in range(x0 + 1, x1 - 1):
            if (x + index) % 2 == 0:
                dot(px, x, y, SKIN_DEEP)
    # Couture verticale du sternum : elle traverse tout le torse.
    vline(px, x0 + 4, y0, y1, SEAM)

    # Dos : la grande couture d'assemblage et deux omoplates trop hautes.
    x0, y0, x1, y1 = f["back"]
    noise_fill(px, (x0, y0, x1, y1), SKIN_DARK, 5, rng, dark_edge=12)
    vline(px, (x0 + x1) // 2, y0, y1, SEAM)
    for x in (x0 + 2, x1 - 3):
        vline(px, x, y0 + 1, y0 + 6, SKIN_DEEP)

    # Épaules (top) : cuir tendu, un peu de turquoise résiduel.
    x0, y0, x1, y1 = f["top"]
    noise_fill(px, (x0, y0, x1, y1), TEAL_WORN, 9, rng)


def paint_limbs(px, rng):
    """Articulations cousues, mains trop longues aux ongles clairs."""
    for box in (ARM_R, FOREARM_R, ARM_L, FOREARM_L, THIGH_R, THIGH_L,
                SHIN_R, SHIN_L):
        f = faces(*box)
        for key in ("front", "back", "right", "left"):
            x0, y0, x1, y1 = f[key]
            hline(px, x0, x1, y0, SEAM)             # couture à l'épaule/hanche
            hline(px, x0, x1, y1 - 1, SEAM)         # couture au coude/genou
            for _ in range(2):
                vy = rng.randrange(y0 + 1, max(y0 + 2, y1 - 2))
                dot(px, rng.randrange(x0, x1), vy, VEIN)

    for box in (HAND_R, HAND_L):
        f = faces(*box)
        x0, y0, x1, y1 = f["front"]
        noise_fill(px, (x0, y0, x1, y1), SKIN, 5, rng, dark_edge=10)
        # Trois doigts trop longs, terminés par un ongle pâle.
        for x in range(x0, x1):
            vline(px, x, y0 + 1, y1 - 1, SKIN_DEEP if (x - x0) % 2 else SKIN_DARK)
            dot(px, x, y1 - 1, NAIL)
        x0, y0, x1, y1 = f["bottom"]
        noise_fill(px, (x0, y0, x1, y1), NAIL, 6, rng)


# ——————————————————————————— screamer 1024x1024 ———————————————————————————

def ellipse_distance(size, cx, cy, rx, ry):
    """Distance normalisée au centre : < 1 = intérieur de l'ellipse."""
    ys, xs = np.mgrid[0:size, 0:size]
    return np.sqrt(((xs - cx) / rx) ** 2 + ((ys - cy) / ry) ** 2)


def mask(size, cx, cy, rx, ry, softness=0.03):
    """Masque dur 0/1 avec un bord adouci de {@code softness} : une vraie forme."""
    d = ellipse_distance(size, cx, cy, rx, ry)
    return np.clip((1.0 - d) / max(softness, 1e-6), 0.0, 1.0)


def radial(size, cx, cy, rx, ry, power=1.0):
    """Champ 0..1 décroissant depuis (cx, cy), pour les dégradés internes."""
    d = ellipse_distance(size, cx, cy, rx, ry)
    return np.clip(1.0 - d, 0.0, 1.0) ** power


def blob(size, cx, cy, rx, ry, rotation=0.0, wobble=(), softness=0.02, taper=0.0):
    """Ellipse perturbée angulairement : un contour organique, jamais géométrique.

    {@code wobble} est une suite de (fréquence, amplitude, phase) : chaque terme
    déforme le rayon. {@code taper} resserre la moitié basse de la forme, ce qui
    transforme un œuf en mâchoire.
    """
    ys, xs = np.mgrid[0:size, 0:size]
    dx = xs - cx
    dy = ys - cy
    cos_r, sin_r = math.cos(rotation), math.sin(rotation)
    ux = (dx * cos_r + dy * sin_r) / rx
    uy = (-dx * sin_r + dy * cos_r) / ry
    if taper:
        # Le rayon horizontal diminue vers le bas : joues qui fuient vers le menton.
        ux = ux / np.clip(1.0 - taper * np.clip(uy, 0.0, 1.0), 0.35, 1.0)
    theta = np.arctan2(uy, ux)
    perturbation = np.ones_like(theta)
    for frequency, amplitude, phase in wobble:
        perturbation = perturbation + amplitude * np.sin(frequency * theta + phase)
    distance = np.sqrt(ux * ux + uy * uy)
    return np.clip((perturbation - distance) / max(softness, 1e-6), 0.0, 1.0)


def fractal_noise(size, generator, octaves=(8, 24, 72, 220)):
    """Bruit lisse multi-octave 0..1, pour les marbrures de la peau."""
    total = np.zeros((size, size), dtype=np.float64)
    weight = 0.0
    for index, cells in enumerate(octaves):
        gain = 1.0 / (index + 1)
        low = generator.random((cells, cells))
        layer = np.asarray(
            Image.fromarray((low * 255).astype(np.uint8), mode="L")
            .resize((size, size), Image.BICUBIC), dtype=np.float64) / 255.0
        total += layer * gain
        weight += gain
    return total / max(weight, 1e-9)


def paint_screamer(size=1024):
    """Gros plan décentré : pas de regard à croiser, seulement des oreilles.

    Construit en flottant puis grainé : le rendu doit rester lisible même
    recadré en « cover » par JumpscareManager sur un écran 21/9.
    """
    generator = np.random.default_rng(1717)
    ys, xs = np.mgrid[0:size, 0:size]
    mottle = fractal_noise(size, generator)

    # Fond : noir franc, remontée de lumière depuis le bas du cadre uniquement.
    luminance = 0.015 + 0.075 * np.clip((ys / size - 0.55) / 0.45, 0.0, 1.0) ** 2

    # ——— Crâne : contour irrégulier, tempes creusées, mâchoire resserrée ———
    head_cx, head_cy = size * 0.455, size * 0.475
    head_rx, head_ry = size * 0.335, size * 0.470
    head = blob(size, head_cx, head_cy, head_rx, head_ry,
                rotation=0.055,
                wobble=((2, 0.055, 1.1), (3, 0.034, -0.4), (5, 0.021, 2.2),
                        (7, 0.013, 0.7), (11, 0.008, 3.0)),
                softness=0.010, taper=0.34)

    # Source unique par le bas : le front reste noir, la mâchoire est brûlée.
    lighting = 0.10 + 1.25 * np.clip((ys - size * 0.30) / (size * 0.62), 0.0, 1.0) ** 1.25
    # Modelé : les joues fuient vers l'ombre, l'arête du nez capte la lumière.
    modelling = 0.30 + 0.80 * radial(size, head_cx, head_cy,
                                     head_rx * 1.05, head_ry * 1.05, power=1.35)
    nose_ridge = blob(size, head_cx + size * 0.008, size * 0.545,
                      size * 0.045, size * 0.145,
                      wobble=((3, 0.10, 0.5),), softness=0.35)
    skin = head * lighting * modelling
    skin = skin * (0.80 + 0.34 * mottle)              # marbrures de peau cireuse
    skin = np.maximum(skin, head * nose_ridge * lighting * 0.55)
    luminance = np.maximum(luminance, skin)

    # ——— Pavillons : deux formes franchement différentes, inclinées ———
    ear_specs = (
        # (côté, cx, cy, rx, ry, rotation)
        (-1, 0.030, 0.360, 0.268, 0.330, -0.20),
        (1, 0.968, 0.408, 0.242, 0.305, 0.27),
    )
    for side, cx_f, cy_f, rx_f, ry_f, rotation in ear_specs:
        cx, cy = size * cx_f, size * cy_f
        ear_rx, ear_ry = size * rx_f, size * ry_f
        ear = blob(size, cx, cy, ear_rx, ear_ry, rotation=rotation,
                   wobble=((2, 0.045, 0.3 * side), (3, 0.030, 1.6),
                           (5, 0.018, -0.9), (9, 0.010, 2.4)),
                   softness=0.012)
        inward = np.clip(1.0 - np.abs(xs - size * 0.5) / (size * 0.56), 0.0, 1.0)
        ear_light = 0.16 + 0.95 * inward * np.clip(
            (ys - size * 0.10) / (size * 0.66), 0.0, 1.0) ** 1.1
        ear_skin = ear * ear_light * (0.78 + 0.40 * mottle)
        luminance = np.maximum(luminance, ear_skin)

        # Nervures concentriques irrégulières, décalées vers le canal.
        for scale, gain, phase in ((0.56, 0.26, 0.4), (0.74, 0.19, 1.9),
                                   (0.90, 0.13, 3.1)):
            inner = blob(size, cx + side * size * 0.010, cy,
                         ear_rx * scale, ear_ry * scale, rotation=rotation,
                         wobble=((3, 0.05, phase), (5, 0.03, -phase)), softness=0.016)
            outer = blob(size, cx + side * size * 0.010, cy,
                         ear_rx * (scale + 0.055), ear_ry * (scale + 0.055),
                         rotation=rotation,
                         wobble=((3, 0.05, phase), (5, 0.03, -phase)), softness=0.016)
            luminance = np.maximum(luminance, (outer - inner) * ear * gain * ear_light)

        # Canal auditif : trou noir franc, liseré clair d'un seul côté.
        canal_cx = cx + side * size * 0.028
        canal = blob(size, canal_cx, size * 0.455, size * 0.058, size * 0.098,
                     rotation=rotation,
                     wobble=((3, 0.09, 1.2),), softness=0.05)
        rim = blob(size, canal_cx, size * 0.455, size * 0.074, size * 0.118,
                   rotation=rotation, wobble=((3, 0.09, 1.2),), softness=0.05)
        luminance = np.maximum(luminance, (rim - canal) * ear_light * 0.7)
        luminance = luminance * (1.0 - canal)

        # Veines froides sur la face externe, vers le bord du cadre.
        for index in range(5):
            vein = blob(size, cx + side * size * (0.10 + 0.03 * index),
                        cy + size * (-0.10 + 0.05 * index),
                        size * 0.004, size * (0.05 + 0.012 * index),
                        rotation=rotation + 0.4 * index,
                        softness=0.5)
            luminance = luminance * (1.0 - vein * ear * 0.22)

    # ——— Orbites cousues : aucun regard à croiser ———
    for cx, cy, rx, ry, rot in ((0.348, 0.406, 0.101, 0.055, -0.07),
                                (0.578, 0.430, 0.091, 0.049, 0.11)):
        socket = blob(size, size * cx, size * cy, size * rx, size * ry,
                      rotation=rot, wobble=((2, 0.09, 0.6), (5, 0.05, 2.0)),
                      softness=0.07)
        # Bourrelet clair au-dessus : la peau a été tirée puis recousue.
        ridge = blob(size, size * cx, size * cy - size * 0.030,
                     size * rx * 1.04, size * ry * 0.72, rotation=rot,
                     wobble=((3, 0.08, 1.4),), softness=0.16)
        luminance = np.maximum(luminance, ridge * head * lighting * 0.42)
        luminance = luminance * (1.0 - socket * 0.965)
        # Le fil : un trait clair tendu en travers, avec ses nœuds.
        stitch = blob(size, size * cx, size * cy, size * rx * 1.02, size * 0.0042,
                      rotation=rot, softness=0.4)
        luminance = np.maximum(luminance, stitch * head * 0.85)
        for step in range(-4, 5):
            knot = blob(size, size * (cx + step * rx * 0.21),
                        size * (cy + step * 0.0018), size * 0.0052, size * 0.0125,
                        rotation=rot + 0.3, softness=0.45)
            luminance = np.maximum(luminance, knot * head * 0.72)

    # ——— Bouche décrochée, béante, asymétrique ———
    mouth_cx, mouth_cy = size * 0.470, size * 0.782
    mouth = blob(size, mouth_cx, mouth_cy, size * 0.121, size * 0.150,
                 rotation=0.06,
                 wobble=((2, 0.07, 0.9), (3, 0.05, -1.7), (7, 0.02, 2.5)),
                 softness=0.035)
    lip = blob(size, mouth_cx, mouth_cy, size * 0.140, size * 0.172,
               rotation=0.06,
               wobble=((2, 0.07, 0.9), (3, 0.05, -1.7), (7, 0.02, 2.5)),
               softness=0.05)
    # Lèvre éclairée par le bas, salie par les marbrures : un bourrelet de chair,
    # pas un contour dessiné.
    luminance = np.maximum(luminance,
                           (lip - mouth) * lighting * 0.46 * (0.7 + 0.5 * mottle))
    luminance = luminance * (1.0 - mouth * 0.982)
    throat = radial(size, mouth_cx, mouth_cy - size * 0.035,
                    size * 0.058, size * 0.066, power=2.1)
    luminance = np.maximum(luminance, throat * mouth * 0.065)
    # Dents inférieures irrégulières, en pleine lumière.
    for offset, height, tilt in ((-0.072, 0.031, -0.14), (-0.036, 0.021, -0.05),
                                 (0.002, 0.036, 0.02), (0.038, 0.018, 0.09),
                                 (0.072, 0.026, 0.17)):
        tooth = blob(size, mouth_cx + size * offset, size * 0.906,
                     size * 0.0125, size * height, rotation=tilt,
                     wobble=((3, 0.10, 0.8),), softness=0.14)
        luminance = np.maximum(luminance, tooth * (0.58 + 0.22 * mottle))

    # ——— Coutures d'assemblage du crâne ———
    for cy, ry, strength, rot in ((0.240, 0.0055, 0.55, -0.05),
                                  (0.652, 0.0042, 0.40, 0.04)):
        seam = blob(size, head_cx, size * cy, size * 0.30, size * ry,
                    rotation=rot, wobble=((5, 0.22, 1.1),), softness=0.4)
        luminance = luminance * (1.0 - seam * head * strength)

    # ——— Grain argentique, vignettage, courbe en S ———
    grain = generator.standard_normal((size, size)) * 0.036
    luminance = np.clip(luminance + grain, 0.0, 1.0)
    vignette = radial(size, size * 0.50, size * 0.52, size * 0.90, size * 0.92, 0.62)
    luminance = luminance * (0.20 + 0.80 * vignette)
    luminance = np.clip(luminance, 0.0, 1.0)
    luminance = luminance * luminance * (3.0 - 2.0 * luminance)
    luminance = np.clip(luminance * 1.30, 0.0, 1.0)

    # Gris cireux très légèrement froid, jamais rose.
    red = np.clip(luminance * 1.00, 0, 1)
    green = np.clip(luminance * 0.980, 0, 1)
    blue = np.clip(luminance * 0.944 + 0.009, 0, 1)
    rgb = (np.dstack([red, green, blue]) * 255.0).astype(np.uint8)
    image = Image.fromarray(rgb, mode="RGB")
    return image.filter(ImageFilter.GaussianBlur(radius=0.9)).convert("RGB")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default=BASE)
    args = parser.parse_args()

    entity_dir = os.path.join(args.out, "entity")
    gui_dir = os.path.join(args.out, "gui")
    os.makedirs(entity_dir, exist_ok=True)
    os.makedirs(gui_dir, exist_ok=True)

    skin_path = os.path.join(entity_dir, "listener.png")
    paint_listener().save(skin_path)
    print(f"  {skin_path}  64x64")

    face_path = os.path.join(gui_dir, "screamer_listener.png")
    paint_screamer().save(face_path)
    print(f"  {face_path}  1024x1024")


if __name__ == "__main__":
    main()

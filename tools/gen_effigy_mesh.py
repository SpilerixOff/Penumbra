#!/usr/bin/env python3
"""Génère l'effigie de démonstration du système de maillages (0.19.0).

  assets/penumbra/meshes/effigy.obj                 maillage
  assets/penumbra/textures/entity/prop/effigy.png   texture

C'est une **pierre dressée grossièrement taillée en silhouette** : un fût de
section carrée, tourné sur lui-même en montant, dont la largeur suit un profil
d'épaules, de cou et de tête. Rien de tout cela n'est réalisable avec des
boîtes alignées sur les axes — c'est précisément l'intérêt de la démonstration :
si tu vois des faces obliques et une torsion continue, le pipeline fonctionne.

Le maillage est posé sur le plan Y = 0 et mesure environ deux blocs, dans le
repère de Minecraft (1 unité = 1 bloc). Il est exporté avec ses normales et ses
coordonnées de texture.

Déterministe. Usage : python3 tools/gen_effigy_mesh.py
"""

import math
import os
import random

from PIL import Image, ImageFilter

BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..",
                    "src", "main", "resources", "assets", "penumbra")

SIDES = 8          # section octogonale : assez ronde, encore taillée
RINGS = 28         # anneaux verticaux
HEIGHT = 2.05      # blocs


def profile(t):
    """Demi-largeur du fût à la hauteur relative t (0 au sol, 1 au sommet).

    Le profil dessine, de bas en haut : un socle évasé, un fût qui se resserre,
    des épaules qui s'élargissent brusquement, un cou étranglé, et une tête.
    C'est une silhouette humaine réduite à quatre accidents de largeur.
    """
    if t < 0.06:                     # socle
        return 0.34 - t * 0.6
    if t < 0.52:                     # fût, très légèrement fuselé
        return 0.30 - (t - 0.06) * 0.14
    if t < 0.68:                     # épaules : élargissement franc
        return 0.24 + (t - 0.52) * 0.75
    if t < 0.76:                     # cou : étranglement
        return 0.36 - (t - 0.68) * 2.1
    if t < 0.94:                     # tête
        return 0.19 + math.sin((t - 0.76) / 0.18 * math.pi) * 0.06
    return max(0.02, 0.19 - (t - 0.94) * 2.6)   # sommet arrondi


def twist(t):
    """Rotation du fût en montant : 34° au total, plus marquée au milieu."""
    return math.radians(34.0) * (t ** 1.4)


def build_rings(rng):
    """Anneaux de sommets, avec une irrégularité de taille sur chaque point."""
    rings = []
    for ring in range(RINGS + 1):
        t = ring / RINGS
        radius = profile(t)
        angle_offset = twist(t)
        y = t * HEIGHT
        points = []
        for side in range(SIDES):
            angle = angle_offset + side / SIDES * math.tau
            # Éclat de taille : chaque sommet est écarté de sa position idéale.
            chip = 1.0 + (rng.random() - 0.5) * 0.09
            points.append((math.cos(angle) * radius * chip,
                           y + (rng.random() - 0.5) * 0.012,
                           math.sin(angle) * radius * chip))
        rings.append(points)

    # L'irrégularité de taille fait descendre quelques sommets sous zéro : la
    # statue s'enfoncerait de trois millimètres dans le sol. On repose l'objet.
    floor = min(point[1] for ring in rings for point in ring)
    if floor != 0.0:
        rings = [[(x, y - floor, z) for x, y, z in ring] for ring in rings]
    return rings


def write_obj(path, rings):
    """Écrit le maillage : quads latéraux triangulés, plus deux couvercles."""
    vertices = []
    uvs = []
    faces = []

    # Sommets et UV : la texture s'enroule autour du fût, U suit le tour et
    # V suit la hauteur. Une colonne de sommets est dupliquée à la couture
    # pour que U puisse aller de 0 à 1 sans revenir en arrière.
    for ring_index, ring in enumerate(rings):
        v = 1.0 - ring_index / RINGS
        for side in range(SIDES + 1):
            point = ring[side % SIDES]
            vertices.append(point)
            uvs.append((side / SIDES, v))

    stride = SIDES + 1

    def vertex_index(ring, side):
        return ring * stride + side + 1     # OBJ compte à partir de 1

    for ring in range(RINGS):
        for side in range(SIDES):
            a = vertex_index(ring, side)
            b = vertex_index(ring, side + 1)
            c = vertex_index(ring + 1, side + 1)
            d = vertex_index(ring + 1, side)
            faces.append((a, b, c))
            faces.append((a, c, d))

    # Couvercle du bas : un éventail autour du centre du premier anneau.
    base_center = len(vertices) + 1
    vertices.append((0.0, rings[0][0][1], 0.0))
    uvs.append((0.5, 1.0))
    for side in range(SIDES):
        faces.append((base_center, vertex_index(0, side + 1), vertex_index(0, side)))

    top_center = len(vertices) + 1
    vertices.append((0.0, rings[RINGS][0][1], 0.0))
    uvs.append((0.5, 0.0))
    for side in range(SIDES):
        faces.append((top_center, vertex_index(RINGS, side), vertex_index(RINGS, side + 1)))

    with open(path, "w", encoding="utf-8") as out:
        out.write("# Penumbra — effigie de démonstration du système de maillages\n")
        out.write(f"# {len(vertices)} sommets, {len(faces)} triangles\n")
        out.write("# Repère Minecraft, 1 unite = 1 bloc, pose sur Y = 0.\n")
        for x, y, z in vertices:
            out.write(f"v {x:.5f} {y:.5f} {z:.5f}\n")
        for u, v in uvs:
            # OBJ compte V depuis le bas : on inverse, le chargeur ré-inverse.
            out.write(f"vt {u:.5f} {1.0 - v:.5f}\n")
        # Pas de vn : le chargeur recalcule les normales par produit vectoriel.
        # C'est volontaire, ça teste ce chemin du parseur.
        for a, b, c in faces:
            out.write(f"f {a}/{a} {b}/{b} {c}/{c}\n")
    return len(vertices), len(faces)


def write_texture(path, size=256):
    """Pierre grise piquetée, avec des traces d'outil verticales et de la mousse."""
    rng = random.Random(4711)
    image = Image.new("RGBA", (size, size), (118, 114, 108, 255))
    px = image.load()

    # Grain de pierre.
    for y in range(size):
        for x in range(size):
            shade = rng.randint(-16, 16)
            px[x, y] = (max(0, min(255, 118 + shade)),
                        max(0, min(255, 114 + shade)),
                        max(0, min(255, 108 + shade)), 255)

    # Coups de ciseau verticaux : ils suivent la hauteur, donc l'axe V.
    for _ in range(90):
        x = rng.randrange(size)
        y0 = rng.randrange(size)
        length = rng.randint(12, 70)
        dark = rng.randint(-34, -14)
        for y in range(y0, min(size, y0 + length)):
            for dx in (0, 1):
                if x + dx < size:
                    r, g, b, _ = px[x + dx, y]
                    px[x + dx, y] = (max(0, r + dark), max(0, g + dark),
                                     max(0, b + dark), 255)

    # Mousse dans le tiers bas (V proche de 1 = bas du fût).
    for _ in range(260):
        x = rng.randrange(size)
        y = rng.randrange(int(size * 0.62), size)
        radius = rng.randint(2, 7)
        for dy in range(-radius, radius + 1):
            for dx in range(-radius, radius + 1):
                if dx * dx + dy * dy > radius * radius:
                    continue
                tx, ty = (x + dx) % size, y + dy
                if 0 <= ty < size:
                    r, g, b, _ = px[tx, ty]
                    px[tx, ty] = (max(0, r - 22), min(255, g + 12),
                                  max(0, b - 26), 255)

    image = image.filter(ImageFilter.GaussianBlur(radius=0.4))
    image.save(path)


def main():
    rng = random.Random(1907)
    meshes = os.path.join(BASE, "meshes")
    textures = os.path.join(BASE, "textures", "entity", "prop")
    os.makedirs(meshes, exist_ok=True)
    os.makedirs(textures, exist_ok=True)

    obj_path = os.path.join(meshes, "effigy.obj")
    vertices, faces = write_obj(obj_path, build_rings(rng))
    print(f"  effigy.obj  {vertices} sommets, {faces} triangles, "
          f"{os.path.getsize(obj_path) // 1024} Ko")

    texture_path = os.path.join(textures, "effigy.png")
    write_texture(texture_path)
    print(f"  effigy.png  256x256")


if __name__ == "__main__":
    main()

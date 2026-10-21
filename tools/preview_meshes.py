#!/usr/bin/env python3
"""Rend une planche-contact des maillages installés, sans lancer Minecraft.

Un rasteriseur logiciel minimal : projection orthographique trois-quarts,
tampon de profondeur, texture échantillonnée en coordonnées barycentriques,
éclairage diffus. Ce n'est pas le rendu du jeu — pas de brouillard, pas
d'ombre portée, pas de lumière de bloc — mais c'est la même géométrie, la même
texture et les mêmes UV. Si un objet est à l'envers, mal texturé ou troué, ça
se voit ici avant de charger un monde.

Usage :
    python3 tools/preview_meshes.py                    tous les maillages
    python3 tools/preview_meshes.py theodolite chaise   une sélection
"""

import glob
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..",
                    "src", "main", "resources", "assets", "penumbra")

TILE = 300
YAW = math.radians(34.0)          # trois-quarts : on voit deux faces
PITCH = math.radians(-16.0)       # légère plongée, comme un joueur debout
LIGHT = np.array([-0.45, 0.78, 0.44])
LIGHT /= np.linalg.norm(LIGHT)
BACKGROUND = (26, 26, 30)


def load_obj(path):
    positions, uvs, normals = [], [], []
    tri_p, tri_t, tri_n = [], [], []

    def index(field, count):
        if not field:
            return -1
        value = int(field)
        return count + value if value < 0 else value - 1

    for line in open(path, errors="replace"):
        parts = line.split()
        if not parts or parts[0] == "#":
            continue
        tag = parts[0]
        if tag == "v":
            positions.append([float(parts[1]), float(parts[2]), float(parts[3])])
        elif tag == "vt":
            uvs.append([float(parts[1]), float(parts[2])])
        elif tag == "vn":
            normals.append([float(parts[1]), float(parts[2]), float(parts[3])])
        elif tag == "f":
            corners = [(part.split("/") + ["", ""])[:3] for part in parts[1:]]
            for k in range(1, len(corners) - 1):
                for c in (corners[0], corners[k], corners[k + 1]):
                    tri_p.append(index(c[0], len(positions)))
                    tri_t.append(index(c[1], len(uvs)))
                    tri_n.append(index(c[2], len(normals)))

    positions = np.array(positions, dtype=np.float32)
    uvs = np.array(uvs, dtype=np.float32) if uvs else np.zeros((1, 2), np.float32)
    normals = np.array(normals, dtype=np.float32) if normals else np.zeros((1, 3), np.float32)
    return (positions[np.array(tri_p)].reshape(-1, 3, 3),
            uvs[np.clip(np.array(tri_t), 0, len(uvs) - 1)].reshape(-1, 3, 2),
            normals[np.clip(np.array(tri_n), 0, len(normals) - 1)].reshape(-1, 3, 3),
            np.array(tri_n).reshape(-1, 3))


def render(mesh_path, texture_path, size=TILE):
    tri, uv, nor, nor_index = load_obj(mesh_path)

    # Repère caméra : rotation Y puis X. Le modèle est posé sur Y = 0, on vise
    # le milieu de sa hauteur.
    cy, sy = math.cos(YAW), math.sin(YAW)
    cp, sp = math.cos(PITCH), math.sin(PITCH)
    rot_y = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]], np.float32)
    rot_x = np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]], np.float32)
    matrix = rot_x @ rot_y

    points = tri.reshape(-1, 3)
    height = points[:, 1].max()
    centered = points - np.array([0.0, height * 0.5, 0.0], np.float32)
    view = centered @ matrix.T

    span = max(view[:, 0].max() - view[:, 0].min(),
               view[:, 1].max() - view[:, 1].min()) * 1.12 or 1.0
    scale = size / span
    sx = view[:, 0] * scale + size * 0.5
    sy_ = size * 0.5 - view[:, 1] * scale
    depth = view[:, 2]

    screen = np.stack([sx, sy_, depth], 1).reshape(-1, 3, 3)

    # Normales : celles du fichier si présentes, sinon la normale géométrique.
    normals = nor.copy()
    missing = (nor_index < 0).any(axis=1)
    if missing.any():
        edge1 = tri[missing, 1] - tri[missing, 0]
        edge2 = tri[missing, 2] - tri[missing, 0]
        face = np.cross(edge1, edge2)
        norm = np.linalg.norm(face, axis=1, keepdims=True)
        face = np.divide(face, np.where(norm == 0, 1, norm))
        normals[missing] = face[:, None, :]

    texture = np.asarray(Image.open(texture_path).convert("RGB"), np.float32) / 255.0
    th, tw = texture.shape[:2]

    color = np.zeros((size, size, 3), np.float32)
    color[:] = np.array(BACKGROUND, np.float32) / 255.0
    zbuffer = np.full((size, size), np.inf, np.float32)

    for index in range(len(screen)):
        a, b, c = screen[index]
        min_x = max(int(min(a[0], b[0], c[0])), 0)
        max_x = min(int(max(a[0], b[0], c[0])) + 1, size - 1)
        min_y = max(int(min(a[1], b[1], c[1])), 0)
        max_y = min(int(max(a[1], b[1], c[1])) + 1, size - 1)
        if min_x > max_x or min_y > max_y:
            continue

        area = (b[0] - a[0]) * (c[1] - a[1]) - (c[0] - a[0]) * (b[1] - a[1])
        if abs(area) < 1e-9:
            continue

        ys, xs = np.mgrid[min_y:max_y + 1, min_x:max_x + 1]
        px = xs + 0.5
        py = ys + 0.5
        w0 = ((b[0] - a[0]) * (py - a[1]) - (px - a[0]) * (b[1] - a[1])) / area
        w1 = ((px - a[0]) * (c[1] - a[1]) - (c[0] - a[0]) * (py - a[1])) / area
        w2 = 1.0 - w0 - w1
        inside = (w0 >= 0) & (w1 >= 0) & (w2 >= 0)
        if not inside.any():
            continue

        z = w2 * a[2] + w1 * b[2] + w0 * c[2]
        target = zbuffer[min_y:max_y + 1, min_x:max_x + 1]
        visible = inside & (z < target)
        if not visible.any():
            continue

        u = w2 * uv[index, 0, 0] + w1 * uv[index, 1, 0] + w0 * uv[index, 2, 0]
        v = w2 * uv[index, 0, 1] + w1 * uv[index, 1, 1] + w0 * uv[index, 2, 1]
        # L'OBJ compte V depuis le bas ; l'image depuis le haut.
        tx = np.clip((u % 1.0) * (tw - 1), 0, tw - 1).astype(np.int32)
        ty = np.clip((1.0 - (v % 1.0)) * (th - 1), 0, th - 1).astype(np.int32)
        texel = texture[ty, tx]

        normal = (w2[..., None] * normals[index, 0]
                  + w1[..., None] * normals[index, 1]
                  + w0[..., None] * normals[index, 2])
        length = np.linalg.norm(normal, axis=-1, keepdims=True)
        normal = np.divide(normal, np.where(length == 0, 1, length))
        lambert = np.abs(normal @ LIGHT)[..., None]
        shade = 0.32 + 0.68 * lambert

        target[visible] = z[visible]
        patch = color[min_y:max_y + 1, min_x:max_x + 1]
        patch[visible] = np.clip(texel * shade, 0, 1)[visible]

    return Image.fromarray((color * 255).astype(np.uint8))


def main():
    names = sys.argv[1:]
    paths = sorted(glob.glob(os.path.join(BASE, "meshes", "*.obj")))
    if names:
        paths = [p for p in paths if os.path.basename(p)[:-4] in names]

    tiles = []
    for path in paths:
        name = os.path.basename(path)[:-4]
        texture = os.path.join(BASE, "textures", "entity", "prop", name + ".png")
        if not os.path.exists(texture):
            print(f"  {name} : texture absente, ignoré")
            continue
        tiles.append((name, render(path, texture)))
        print(f"  {name} rendu")

    if not tiles:
        return
    columns = 4
    rows = (len(tiles) + columns - 1) // columns
    label = 26
    sheet = Image.new("RGB", (columns * TILE, rows * (TILE + label)), BACKGROUND)
    draw = ImageDraw.Draw(sheet)
    for index, (name, tile) in enumerate(tiles):
        x = (index % columns) * TILE
        y = (index // columns) * (TILE + label)
        sheet.paste(tile, (x, y))
        draw.text((x + 8, y + TILE + 7), name, fill=(198, 190, 178))

    out = os.path.join(os.path.dirname(BASE), "..", "..", "..", "..",
                       "apercu_maillages.png")
    out = os.path.normpath(out)
    sheet.save(out)
    print(f"\nplanche : {out}  ({sheet.width}x{sheet.height})")


if __name__ == "__main__":
    main()

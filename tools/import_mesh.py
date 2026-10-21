#!/usr/bin/env python3
"""Installe un modèle 3D téléchargé (Meshy, Sketchfab, Blender…) dans Penumbra.

Un `.obj` sorti d'un générateur 3D n'est jamais utilisable tel quel : il flotte
au-dessus de son origine, il mesure une unité au lieu de deux blocs, il traîne
un `.mtl`, des quads, parfois des n-gones, et sa texture porte un nom de fichier
imprononçable. Ce script fait toutes ces corrections d'un coup et dépose les
deux fichiers finaux au bon endroit dans le mod.

Ce qu'il corrige, dans l'ordre :

  1. **Repère.** Conversion Z-haut → Y-haut si demandé (`--axe z`).
  2. **Échelle.** Le modèle est mis à la hauteur voulue, en blocs.
  3. **Pose.** Il est centré en X et Z, et son point le plus bas est amené
     exactement sur Y = 0 — le plan de contact avec le sol.
  4. **Triangulation.** Les faces à plus de trois sommets sont découpées en
     éventail, donc le fichier final ne contient que des triangles.
  5. **Nettoyage.** Matériaux, groupes, lissage et lignes sont retirés.
  6. **Texture.** Repérée via le `.mtl` s'il existe, sinon la plus grande image
     du dossier ; convertie en PNG RGBA et ramenée à une taille puissance de
     deux (1024 au maximum).

Usage :

    # un seul modèle
    python3 tools/import_mesh.py <dossier_du_zip_extrait> theodolite --hauteur 1.6

    # tout un dossier de dépôt : un sous-dossier = un accessoire
    python3 tools/import_mesh.py <dossier_de_depot> --tout

Options utiles :
    --hauteur 2.0     hauteur finale en blocs (défaut : 2.0)
    --axe z           le modèle est en Z-haut (Sketchfab, 3ds Max, certains CAD)
    --reduire 20000   allègement d'urgence par regroupement de sommets
    --garder-echelle  n'applique aucune mise à l'échelle
"""

import argparse
import math
import os
import re
import shutil
import sys

from PIL import Image

BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..",
                    "src", "main", "resources", "assets", "penumbra")

MESH_DIR = os.path.join(BASE, "meshes")
TEXTURE_DIR = os.path.join(BASE, "textures", "entity", "prop")

IMAGE_EXTENSIONS = (".png", ".jpg", ".jpeg", ".webp", ".bmp", ".tga")

# Le chargeur Java refuse au-delà de ce seuil ; on s'arrête avant pour pouvoir
# afficher un message compréhensible au lieu d'un plantage en jeu.
MAX_TRIANGLES = 200_000

# Au-delà, ça reste chargeable mais c'est du gâchis sur un décor : on prévient.
COMFORTABLE_TRIANGLES = 40_000


# ————————————————————————————————— lecture —————————————————————————————————

def parse_index(field, count):
    """Résout un index OBJ : compte à partir de 1, négatif = depuis la fin."""
    if not field:
        return None
    value = int(field)
    resolved = count + value if value < 0 else value - 1
    return resolved if 0 <= resolved < count else None


def read_obj(path):
    """Lit un OBJ et rend (positions, uv, normales, faces).

    Une face est une liste de triplets (index position, index uv, index normale),
    chaque index pouvant valoir None. La triangulation vient plus tard : à ce
    stade on conserve les polygones tels quels, pour pouvoir compter ce que le
    fichier contenait vraiment.
    """
    positions, uvs, normals, faces = [], [], [], []
    skipped = 0

    with open(path, "r", encoding="utf-8", errors="replace") as handle:
        for line in handle:
            line = line.strip()
            if not line or line[0] == "#":
                continue
            parts = line.split()
            tag = parts[0]
            try:
                if tag == "v":
                    positions.append([float(parts[1]), float(parts[2]), float(parts[3])])
                elif tag == "vt":
                    uvs.append([float(parts[1]), float(parts[2])])
                elif tag == "vn":
                    normals.append([float(parts[1]), float(parts[2]), float(parts[3])])
                elif tag == "f":
                    corners = []
                    for token in parts[1:]:
                        fields = (token.split("/") + ["", ""])[:3]
                        corners.append((
                            parse_index(fields[0], len(positions)),
                            parse_index(fields[1], len(uvs)),
                            parse_index(fields[2], len(normals)),
                        ))
                    if len(corners) >= 3 and all(c[0] is not None for c in corners):
                        faces.append(corners)
                    else:
                        skipped += 1
            except (ValueError, IndexError):
                skipped += 1

    return positions, uvs, normals, faces, skipped


def find_obj(folder):
    """Le .obj d'un dossier téléchargé. Le plus gros gagne s'il y en a plusieurs."""
    candidates = []
    for root, _, files in os.walk(folder):
        for name in files:
            if name.lower().endswith(".obj"):
                path = os.path.join(root, name)
                candidates.append((os.path.getsize(path), path))
    if not candidates:
        return None
    return max(candidates)[1]


def find_texture(obj_path):
    """Texture du modèle : d'abord le map_Kd du .mtl, sinon la plus grande image.

    Passer par le .mtl compte : un export Meshy contient souvent quatre cartes
    (couleur, rugosité, métal, normale) et « la plus grande image » risque de
    tomber sur la carte de normales, qui donnerait un objet violet fluo.
    """
    folder = os.path.dirname(obj_path)

    mtl_names = []
    with open(obj_path, "r", encoding="utf-8", errors="replace") as handle:
        for line in handle:
            if line.startswith("mtllib"):
                mtl_names.extend(line.split()[1:])

    for mtl_name in mtl_names:
        mtl_path = os.path.join(folder, mtl_name)
        if not os.path.isfile(mtl_path):
            continue
        with open(mtl_path, "r", encoding="utf-8", errors="replace") as handle:
            for line in handle:
                if not line.lower().startswith("map_kd"):
                    continue
                # map_Kd peut porter des options (-s 1 1 1) avant le fichier.
                token = line.split()[-1]
                candidate = os.path.join(folder, os.path.basename(token))
                if os.path.isfile(candidate):
                    return candidate, "désignée par le .mtl"

    # Repli : la plus grande image, en écartant les cartes techniques dont le
    # nom est reconnaissable.
    rejected = re.compile(r"normal|roughness|metal|specular|height|displace|ao|occlusion",
                          re.IGNORECASE)
    best = None
    for root, _, files in os.walk(folder):
        for name in files:
            if not name.lower().endswith(IMAGE_EXTENSIONS):
                continue
            if rejected.search(name):
                continue
            path = os.path.join(root, name)
            size = os.path.getsize(path)
            if best is None or size > best[0]:
                best = (size, path)
    if best:
        return best[1], "plus grande image du dossier"
    return None, None


# ——————————————————————————————— géométrie ———————————————————————————————

def convert_axis(positions, normals):
    """Z-haut → Y-haut : (x, y, z) devient (x, z, -y)."""
    for point in positions:
        x, y, z = point
        point[0], point[1], point[2] = x, z, -y
    for normal in normals:
        x, y, z = normal
        normal[0], normal[1], normal[2] = x, z, -y


def bounds(positions):
    lows = [min(p[axis] for p in positions) for axis in range(3)]
    highs = [max(p[axis] for p in positions) for axis in range(3)]
    return lows, highs


def normalise(positions, target_height, keep_scale):
    """Met le modèle à la bonne taille, le centre, et le pose sur Y = 0."""
    lows, highs = bounds(positions)
    height = highs[1] - lows[1]

    factor = 1.0
    if not keep_scale:
        if height < 1e-6:
            print("  ! le modèle est plat : mise à l'échelle ignorée")
        else:
            factor = target_height / height

    center_x = (lows[0] + highs[0]) * 0.5
    center_z = (lows[2] + highs[2]) * 0.5

    for point in positions:
        point[0] = (point[0] - center_x) * factor
        point[1] = (point[1] - lows[1]) * factor
        point[2] = (point[2] - center_z) * factor

    return factor


def triangulate(faces):
    """Découpe en éventail. Correct pour tout polygone convexe."""
    triangles = []
    for corners in faces:
        for index in range(1, len(corners) - 1):
            triangles.append((corners[0], corners[index], corners[index + 1]))
    return triangles


def reduce_mesh(positions, uvs, normals, triangles, target):
    """Allègement d'urgence par regroupement de sommets sur une grille.

    Méthode volontairement grossière : les sommets tombant dans la même cellule
    fusionnent, les triangles devenus dégénérés disparaissent. C'est brutal — les
    coutures de texture bavent un peu — mais ça sauve un modèle trop lourd sans
    dépendre d'un logiciel externe.

    **Le bon réflexe reste de baisser le nombre de polygones dans Meshy avant
    d'exporter.** Cette fonction n'est là que pour un fichier déjà téléchargé.
    """
    if len(triangles) <= target:
        return positions, uvs, normals, triangles

    lows, highs = bounds(positions)
    diagonal = math.dist(lows, highs) or 1.0

    def cluster(step):
        """Fusionne les sommets par cellule et rend (sommets, remap)."""
        clusters = {}
        remap = []
        for point in positions:
            key = (round(point[0] / step), round(point[1] / step), round(point[2] / step))
            entry = clusters.get(key)
            if entry is None:
                entry = clusters[key] = [len(clusters), [0.0, 0.0, 0.0], 0]
            for axis in range(3):
                entry[1][axis] += point[axis]
            entry[2] += 1
            remap.append(entry[0])
        merged = [None] * len(clusters)
        for entry in clusters.values():
            merged[entry[0]] = [value / entry[2] for value in entry[1]]
        return merged, remap

    def rebuild(remap):
        """Reconstruit les triangles, en jetant ceux devenus dégénérés."""
        kept = []
        for triangle in triangles:
            a, b, c = (remap[corner[0]] for corner in triangle)
            if a == b or b == c or a == c:
                continue
            # Les index d'UV sont conservés tels quels : ils pointent vers la
            # table d'origine, qu'on ne touche pas. Les normales sont laissées
            # au chargeur, qui les recalculera sur la géométrie fusionnée.
            kept.append(((a, triangle[0][1], None),
                         (b, triangle[1][1], None),
                         (c, triangle[2][1], None)))
        return kept

    # Le lien entre finesse de grille et nombre de triangles n'a pas de formule
    # fiable — il dépend de la répartition des sommets. On le cherche par
    # dichotomie sur une vingtaine d'essais, ce qui est instantané et sûr.
    low, high = 2.0, 4096.0
    best = None
    for _ in range(20):
        cells = (low + high) * 0.5
        merged, remap = cluster(diagonal / cells)
        kept = rebuild(remap)
        # Plus de cellules = grille plus fine = plus de triangles conservés.
        # On garde donc la grille la plus fine qui tienne encore sous la cible :
        # c'est celle qui abîme le moins le modèle.
        if len(kept) <= target:
            best = (merged, kept)
            low = cells
        else:
            high = cells
        if high - low < 0.5:
            break

    if best is None:
        print("  ! allègement impossible sans détruire le modèle : "
              "réexporte plus léger depuis ton logiciel 3D")
        return positions, uvs, normals, triangles

    merged, kept = best
    print(f"  allègement : {len(triangles)} → {len(kept)} triangles "
          f"({len(positions)} → {len(merged)} sommets)")
    return merged, uvs, [], kept


def compact(positions, uvs, normals, triangles):
    """Retire tout ce qu'aucun triangle n'utilise et renumérote.

    Les exports 3D traînent régulièrement des sommets orphelins, et l'allègement
    en crée d'office. Sans ce passage, le fichier final contiendrait des milliers
    de lignes que le jeu chargerait en mémoire pour ne jamais les dessiner.
    """
    def prune(table, slot):
        used = sorted({corner[slot] for triangle in triangles for corner in triangle
                       if corner[slot] is not None})
        return [table[index] for index in used], {old: new for new, old in enumerate(used)}

    positions, position_map = prune(positions, 0)
    uvs, uv_map = prune(uvs, 1)
    normals, normal_map = prune(normals, 2)

    triangles = [tuple((position_map[corner[0]],
                        None if corner[1] is None else uv_map[corner[1]],
                        None if corner[2] is None else normal_map[corner[2]])
                       for corner in triangle)
                 for triangle in triangles]
    return positions, uvs, normals, triangles


def write_obj(path, name, positions, uvs, normals, triangles, source):
    """Écrit le fichier final : uniquement v, vt, vn et f triangulaires."""
    used_uvs = any(corner[1] is not None for triangle in triangles for corner in triangle)
    used_normals = any(corner[2] is not None for triangle in triangles for corner in triangle)

    with open(path, "w", encoding="utf-8") as out:
        out.write(f"# Penumbra — {name}\n")
        out.write(f"# importé de {os.path.basename(source)} par tools/import_mesh.py\n")
        out.write("# repère Minecraft, 1 unite = 1 bloc, pose sur Y = 0\n")
        out.write(f"# {len(positions)} sommets, {len(triangles)} triangles\n")

        for x, y, z in positions:
            out.write(f"v {x:.5f} {y:.5f} {z:.5f}\n")
        if used_uvs:
            for u, v in uvs:
                out.write(f"vt {u:.6f} {v:.6f}\n")
        if used_normals:
            for x, y, z in normals:
                out.write(f"vn {x:.5f} {y:.5f} {z:.5f}\n")

        for triangle in triangles:
            tokens = []
            for position_index, uv_index, normal_index in triangle:
                token = str(position_index + 1)
                if used_uvs or used_normals:
                    token += "/" + ("" if uv_index is None else str(uv_index + 1))
                if used_normals:
                    token += "/" + ("" if normal_index is None else str(normal_index + 1))
                tokens.append(token)
            out.write("f " + " ".join(tokens) + "\n")

    return used_uvs, used_normals


# ——————————————————————————————— texture ———————————————————————————————

def power_of_two(value, maximum=1024):
    value = min(value, maximum)
    return 1 << max(4, int(math.log2(value)))


def write_texture(source, destination, warnings):
    """Convertit la texture en PNG RGBA, taille puissance de deux, 1024 maximum."""
    if source is None:
        # Sans texture, un gris uni vaut mieux qu'un objet noir : au moins la
        # forme se lit, et on voit tout de suite qu'il manque quelque chose.
        Image.new("RGBA", (64, 64), (128, 124, 118, 255)).save(destination)
        warnings.append("aucune texture trouvée — un gris uni a été posé à la place")
        return "64x64 (gris de remplacement)"

    image = Image.open(source).convert("RGBA")
    width, height = image.size
    target = (power_of_two(width), power_of_two(height))
    if target != (width, height):
        image = image.resize(target, Image.LANCZOS)
    image.save(destination)
    return f"{width}x{height} → {target[0]}x{target[1]}"


# ——————————————————————————————— import ———————————————————————————————

def import_one(source, name, height, up_axis, reduce_to, keep_scale):
    print(f"\n=== {name} ===")

    obj_path = source if source.lower().endswith(".obj") else find_obj(source)
    if not obj_path:
        print(f"  ! aucun fichier .obj trouvé dans {source}")
        return False
    print(f"  source : {os.path.relpath(obj_path, os.path.dirname(source) or '.')}")

    positions, uvs, normals, faces, skipped = read_obj(obj_path)
    if not positions or not faces:
        print("  ! fichier vide ou illisible")
        return False

    polygons = len(faces)
    n_gons = sum(1 for face in faces if len(face) > 3)
    warnings = []
    if skipped:
        warnings.append(f"{skipped} lignes ignorées à la lecture")

    if up_axis == "z":
        convert_axis(positions, normals)

    factor = normalise(positions, height, keep_scale)
    triangles = triangulate(faces)

    if reduce_to:
        positions, uvs, normals, triangles = reduce_mesh(
            positions, uvs, normals, triangles, reduce_to)
        # Le regroupement déplace les sommets vers le centre de leur cellule :
        # le modèle rétrécit et décolle du sol. On le repose.
        factor *= normalise(positions, height, keep_scale)

    positions, uvs, normals, triangles = compact(positions, uvs, normals, triangles)
    lows, highs = bounds(positions)

    if len(triangles) > MAX_TRIANGLES:
        print(f"  ! {len(triangles)} triangles : au-dessus de la limite du "
              f"chargeur ({MAX_TRIANGLES}). Relance avec --reduire 40000.")
        return False

    os.makedirs(MESH_DIR, exist_ok=True)
    os.makedirs(TEXTURE_DIR, exist_ok=True)

    mesh_path = os.path.join(MESH_DIR, name + ".obj")
    used_uvs, used_normals = write_obj(mesh_path, name, positions, uvs, normals,
                                       triangles, obj_path)

    texture_source, how = find_texture(obj_path)
    texture_path = os.path.join(TEXTURE_DIR, name + ".png")
    texture_report = write_texture(texture_source, texture_path, warnings)

    if not used_uvs:
        warnings.append("le modèle n'a aucune coordonnée de texture : "
                        "il prendra la couleur du premier texel")
    if len(triangles) > COMFORTABLE_TRIANGLES:
        warnings.append(f"{len(triangles)} triangles, c'est lourd pour un décor — "
                        "réexporte plus léger depuis Meshy si le jeu rame")

    print(f"  géométrie : {polygons} polygones ({n_gons} à plus de 3 côtés) "
          f"→ {len(triangles)} triangles")
    print(f"  échelle   : ×{factor:.4f}")
    print(f"  dimensions: {highs[0] - lows[0]:.2f} large × "
          f"{highs[1] - lows[1]:.2f} haut × {highs[2] - lows[2]:.2f} profond (blocs)")
    print(f"  normales  : {'conservées' if used_normals else 'recalculées en jeu'}")
    if texture_source:
        print(f"  texture   : {os.path.basename(texture_source)} "
              f"({how}) — {texture_report}")
    else:
        print(f"  texture   : {texture_report}")
    print(f"  écrit     : assets/penumbra/meshes/{name}.obj")
    print(f"              assets/penumbra/textures/entity/prop/{name}.png")

    for warning in warnings:
        print(f"  ! {warning}")

    print(f"  en jeu    : /penumbra prop {name}")
    return True


def slugify(text):
    """Nom de fichier sûr pour Minecraft : minuscules, chiffres, tiret bas."""
    text = text.lower().replace("-", "_").replace(" ", "_")
    text = re.sub(r"[^a-z0-9_]", "", text)
    text = re.sub(r"_+", "_", text).strip("_")
    return text or "prop"


def main():
    parser = argparse.ArgumentParser(
        description="Installe un modèle 3D téléchargé dans Penumbra.")
    parser.add_argument("source", help="dossier extrait, fichier .obj, ou dossier de dépôt")
    parser.add_argument("nom", nargs="?", help="nom du maillage en jeu")
    parser.add_argument("--tout", action="store_true",
                        help="traite chaque sous-dossier comme un accessoire")
    parser.add_argument("--hauteur", type=float, default=2.0,
                        help="hauteur finale en blocs (défaut : 2.0)")
    parser.add_argument("--axe", choices=("y", "z"), default="y",
                        help="axe vertical du fichier source (défaut : y)")
    parser.add_argument("--reduire", type=int, default=0,
                        help="allègement d'urgence vers ce nombre de triangles")
    parser.add_argument("--garder-echelle", action="store_true", dest="keep_scale",
                        help="n'applique aucune mise à l'échelle")
    args = parser.parse_args()

    if not os.path.exists(args.source):
        sys.exit(f"introuvable : {args.source}")

    if args.tout:
        entries = sorted(entry for entry in os.listdir(args.source)
                         if os.path.isdir(os.path.join(args.source, entry))
                         and not entry.startswith("."))
        if not entries:
            sys.exit(f"aucun sous-dossier dans {args.source}")
        done = 0
        for entry in entries:
            if import_one(os.path.join(args.source, entry), slugify(entry),
                          args.hauteur, args.axe, args.reduire, args.keep_scale):
                done += 1
        print(f"\n{done} accessoire(s) sur {len(entries)} installés.")
        return

    name = slugify(args.nom or os.path.basename(os.path.normpath(args.source)))
    if not import_one(args.source, name, args.hauteur, args.axe,
                      args.reduire, args.keep_scale):
        sys.exit(1)


if __name__ == "__main__":
    main()

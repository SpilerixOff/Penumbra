#!/usr/bin/env python3
"""Projette des rendus orthographiques sur l'atlas UV d'une entité Penumbra.

Le problème : Meshy produit un modèle 3D, Minecraft veut un atlas de faces de
boîtes. Ce script fait le pont — de la « projection painting » :

  1. il connaît la géométrie exacte du modèle (table BOXES ci-dessous, recopiée
     du code Java) ;
  2. pour chaque face de chaque boîte, il sait quelle vue orthographique la
     montre (avant, arrière, gauche, droite, dessus, dessous) et à quel endroit ;
  3. il découpe cette région dans ton rendu et la colle dans l'atlas.

Le cadrage n'a pas besoin d'être parfait : le script détecte lui-même la boîte
englobante du sujet dans chaque rendu (par l'alpha si le fond est transparent,
sinon par contraste avec la couleur des coins) et la met en correspondance avec
la boîte englobante du modèle.

Usage :
    python3 tools/bake_entity_texture.py listener <dossier_des_rendus>
    python3 tools/bake_entity_texture.py listener <dossier> --scale 4 --preview

Le dossier doit contenir, en PNG, autant de ces vues que possible :
    front.png  back.png  left.png  right.png  top.png  bottom.png
Une vue absente laisse les faces correspondantes telles quelles dans l'atlas
existant : tu peux donc n'en fournir que deux ou trois pour commencer.
"""

import argparse
import os
import sys

from PIL import Image

# ————————————————————————————————————————————————————————————————
# Géométrie des modèles, recopiée du code Java.
#
# Chaque boîte : (texOffsU, texOffsV, x0, y0, z0, largeur, hauteur, profondeur)
# en coordonnées MONDE du modèle (hiérarchie déjà résolue). Repère Minecraft :
# X vers la droite, Y vers le BAS, Z vers l'arrière, origine aux pieds.
# Les texOffs restent dans le repère 64 — le script les multiplie par --scale,
# exactement comme TEX_SCALE le fait côté Java.
# ————————————————————————————————————————————————————————————————

MODELS = {
    "listener": {
        "atlas": 64,                 # repère des texOffs, avant mise à l'échelle
        "boxes": [
            # tête et ce qui s'y accroche
            ("head",          0,  0,  -4.0, -46.0, -4.0,  8, 6, 8),
            ("right_ear",    32,  0,  -5.0, -50.0, -3.0,  1, 7, 6),
            ("left_ear",     46,  0,   4.0, -50.0, -3.0,  1, 7, 6),
            ("jaw",           0, 14,  -3.0, -41.0, -5.5,  6, 3, 6),
            # tronc
            ("body",          0, 24,  -4.0, -37.0, -2.0,  8, 18, 4),
            ("neck",         24, 14,  -1.5, -40.0, -1.5,  3, 3, 3),
            # bras droits (trois os)
            ("right_arm",    24, 24,  -5.5, -35.4, -1.0,  2, 9, 2),
            ("right_fore",   32, 24,  -5.5, -26.4, -1.0,  2, 9, 2),
            ("right_hand",   36, 14,  -5.5, -17.4, -1.0,  2, 4, 2),
            # bras gauches (épaule 1 px plus haute)
            ("left_arm",     40, 24,   3.5, -36.4, -1.0,  2, 9, 2),
            ("left_fore",    48, 24,   3.5, -27.4, -1.0,  2, 9, 2),
            ("left_hand",    44, 14,   3.5, -18.4, -1.0,  2, 4, 2),
            # jambes
            ("right_thigh",   0, 46,  -3.5, -19.0, -1.5,  3, 9, 3),
            ("right_shin",   12, 46,  -3.0, -10.0, -1.0,  2, 10, 2),
            ("left_thigh",   20, 46,   0.5, -19.0, -1.5,  3, 9, 3),
            ("left_shin",    32, 46,   1.0, -10.0, -1.0,  2, 10, 2),
        ],
    },
}


def faces_of(u, v, w, h, d):
    """Rectangles UV des six faces d'une boîte, convention Minecraft."""
    return {
        "top":    (u + d,         v,     w, d),
        "bottom": (u + d + w,     v,     w, d),
        "right":  (u,             v + d, d, h),
        "front":  (u + d,         v + d, w, h),
        "left":   (u + d + w,     v + d, d, h),
        "back":   (u + 2 * d + w, v + d, w, h),
    }


# Pour chaque face : la vue qui la montre, et comment lire ses coordonnées.
# axes = (axe horizontal du rendu, axe vertical du rendu), flip = miroir horizontal.
FACE_VIEWS = {
    "front":  ("front",  "x", "y", False),
    "back":   ("back",   "x", "y", True),
    "left":   ("left",   "z", "y", True),
    "right":  ("right",  "z", "y", False),
    "top":    ("top",    "x", "z", False),
    "bottom": ("bottom", "x", "z", False),
}


def subject_bounds(image):
    """Boîte englobante du sujet : par l'alpha, sinon par contraste avec le fond."""
    rgba = image.convert("RGBA")
    alpha = rgba.split()[3]
    if alpha.getextrema()[0] < 250:
        box = alpha.point(lambda a: 255 if a > 16 else 0).getbbox()
        if box:
            return box
    # Fond opaque : on prend la couleur du coin comme référence.
    rgb = rgba.convert("RGB")
    background = rgb.getpixel((0, 0))
    diff = Image.new("L", rgb.size)
    diff.putdata([255 if max(abs(p[0] - background[0]), abs(p[1] - background[1]),
                             abs(p[2] - background[2])) > 26 else 0
                  for p in rgb.getdata()])
    return diff.getbbox() or (0, 0, rgb.width, rgb.height)


class View:
    """Un rendu orthographique, avec la correspondance modèle → pixels."""

    def __init__(self, image, model_min, model_max, h_axis, v_axis):
        self.image = image.convert("RGBA")
        self.x0, self.y0, self.x1, self.y1 = subject_bounds(self.image)
        self.model_min = model_min
        self.model_max = model_max
        self.h_axis = h_axis
        self.v_axis = v_axis

    def to_pixels(self, h_lo, h_hi, v_lo, v_hi):
        """Convertit un rectangle en coordonnées modèle en rectangle pixels."""
        hmin, hmax = self.model_min[self.h_axis], self.model_max[self.h_axis]
        vmin, vmax = self.model_min[self.v_axis], self.model_max[self.v_axis]
        span_h = max(hmax - hmin, 1e-6)
        span_v = max(vmax - vmin, 1e-6)
        width = self.x1 - self.x0
        height = self.y1 - self.y0
        left = self.x0 + (h_lo - hmin) / span_h * width
        right = self.x0 + (h_hi - hmin) / span_h * width
        top = self.y0 + (v_lo - vmin) / span_v * height
        bottom = self.y0 + (v_hi - vmin) / span_v * height
        return (int(round(left)), int(round(top)),
                max(int(round(right)), int(round(left)) + 1),
                max(int(round(bottom)), int(round(top)) + 1))


def bake(model_name, folder, scale, output, preview):
    model = MODELS[model_name]
    boxes = model["boxes"]
    base = model["atlas"]
    side = base * scale

    # Boîte englobante du modèle, tous axes.
    # Indices du tuple : 0 nom, 1 texU, 2 texV, 3 x, 4 y, 5 z, 6 w, 7 h, 8 d.
    mins = {"x": min(b[3] for b in boxes), "y": min(b[4] for b in boxes),
            "z": min(b[5] for b in boxes)}
    maxs = {"x": max(b[3] + b[6] for b in boxes), "y": max(b[4] + b[7] for b in boxes),
            "z": max(b[5] + b[8] for b in boxes)}
    print(f"modèle « {model_name} » : {len(boxes)} boîtes, "
          f"emprise X {mins['x']:.1f}→{maxs['x']:.1f}  "
          f"Y {mins['y']:.1f}→{maxs['y']:.1f}  Z {mins['z']:.1f}→{maxs['z']:.1f}")

    views = {}
    for face, (name, h_axis, v_axis, _) in FACE_VIEWS.items():
        for extension in (".png", ".jpg", ".jpeg", ".webp"):
            path = os.path.join(folder, name + extension)
            if os.path.isfile(path) and name not in views:
                views[name] = View(Image.open(path), mins, maxs, h_axis, v_axis)
                v = views[name]
                print(f"  vue « {name} » : {v.image.size}, "
                      f"sujet détecté {v.x1 - v.x0}×{v.y1 - v.y0} px")
                break
    if not views:
        sys.exit(f"aucune vue trouvée dans {folder} "
                 "(attendu : front.png, back.png, left.png, right.png, top.png, bottom.png)")

    atlas = (Image.open(output).convert("RGBA").resize((side, side), Image.NEAREST)
             if os.path.isfile(output)
             else Image.new("RGBA", (side, side), (0, 0, 0, 0)))

    painted = 0
    for name, u, v, x, y, z, w, h, d in boxes:
        rects = faces_of(u, v, w, h, d)
        for face, (fu, fv, fw, fh) in rects.items():
            view_name, h_axis, v_axis, flip = FACE_VIEWS[face]
            view = views.get(view_name)
            if view is None:
                continue
            lo = {"x": x, "y": y, "z": z}
            hi = {"x": x + w, "y": y + h, "z": z + d}
            crop = view.to_pixels(lo[h_axis], hi[h_axis], lo[v_axis], hi[v_axis])
            piece = view.image.crop(crop)
            if flip:
                piece = piece.transpose(Image.FLIP_LEFT_RIGHT)
            piece = piece.resize((fw * scale, fh * scale), Image.LANCZOS)
            atlas.paste(piece, (fu * scale, fv * scale))
            painted += 1

    atlas.save(output)
    print(f"\n{painted} faces peintes dans {output} ({side}×{side})")
    if preview:
        path = os.path.splitext(output)[0] + "_preview.png"
        background = Image.new("RGB", atlas.size, (32, 30, 34))
        background.paste(atlas, (0, 0), atlas)
        background.save(path)
        print(f"aperçu : {path}")
    print("\nRappel : ListenerModel utilise TEX_SCALE = "
          f"{1.0 / scale:.2f} et un atlas de {side}. Si tu changes --scale ici, "
          "change les deux là-bas.")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("model", choices=sorted(MODELS))
    parser.add_argument("folder")
    parser.add_argument("--scale", type=int, default=4,
                        help="texels par unité de modèle (défaut 4 → atlas 256)")
    parser.add_argument("--out", default=None)
    parser.add_argument("--preview", action="store_true")
    args = parser.parse_args()

    output = args.out or os.path.join(
        "src", "main", "resources", "assets", "penumbra", "textures", "entity",
        args.model + ".png")
    if not os.path.isdir(os.path.dirname(output)):
        sys.exit("lance ce script depuis la racine du projet "
                 f"(introuvable : {os.path.dirname(output)})")
    bake(args.model, args.folder, args.scale, output, args.preview)


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Génère les atlas 64×64 et la silhouette photo des trois monstres 0.7.0.

Les palettes et la silhouette du Projectionniste proviennent des concepts
ImageGen conservés dans tools/image_sources. Les atlas restent déterministes et
respectent exactement les UV déclarés par les modèles Java.
"""

from __future__ import annotations

import random
from pathlib import Path

from PIL import Image, ImageEnhance


ROOT = Path(__file__).resolve().parents[1]
SOURCES = ROOT / "tools" / "image_sources"
TEXTURES = ROOT / "src" / "main" / "resources" / "assets" / "penumbra" / "textures" / "entity"
RNG = random.Random(0x50524F4A)


def box_rect(u: int, v: int, w: int, h: int, d: int) -> tuple[int, int, int, int]:
    return u, v, u + 2 * (d + w), v + d + h


def front(u: int, v: int, w: int, h: int, d: int) -> tuple[int, int, int, int]:
    return u + d, v + d, u + d + w, v + d + h


def shade(color: tuple[int, int, int], delta: int) -> tuple[int, int, int, int]:
    return tuple(max(0, min(255, value + delta)) for value in color) + (255,)


def fill_noise(image: Image.Image, rect: tuple[int, int, int, int],
               base: tuple[int, int, int], accent: tuple[int, int, int],
               accent_chance: float = 0.18, noise: int = 5) -> None:
    x0, y0, x1, y1 = rect
    for y in range(max(0, y0), min(image.height, y1)):
        for x in range(max(0, x0), min(image.width, x1)):
            color = accent if RNG.random() < accent_chance else base
            image.putpixel((x, y), shade(color, RNG.randint(-noise, noise)))


def line(image: Image.Image, points: list[tuple[int, int]], color: tuple[int, int, int]) -> None:
    for x, y in points:
        if 0 <= x < image.width and 0 <= y < image.height:
            image.putpixel((x, y), color + (255,))


def projectionist_texture() -> Image.Image:
    image = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    metal = (38, 34, 31)
    bronze = (77, 65, 52)
    coal = (13, 14, 15)
    cloth = (27, 26, 27)
    tape = (7, 9, 10)

    head = (0, 0, 10, 7, 10)
    lens = (40, 0, 4, 4, 3)
    body = (0, 18, 10, 18, 5)
    right_arm = (32, 18, 3, 18, 3)
    left_arm = (44, 18, 3, 18, 3)
    right_leg = (0, 42, 3, 14, 3)
    left_leg = (12, 42, 3, 14, 3)

    fill_noise(image, box_rect(*head), metal, bronze, 0.24, 7)
    fill_noise(image, box_rect(*lens), (177, 159, 126), (245, 232, 196), 0.38, 5)
    fill_noise(image, box_rect(*body), cloth, bronze, 0.10, 5)
    for part in (right_arm, left_arm, right_leg, left_leg):
        fill_noise(image, box_rect(*part), coal, bronze, 0.12, 4)

    # Panneaux, vis et fentes de la vieille caméra.
    hx0, hy0, hx1, hy1 = front(*head)
    line(image, [(x, hy0 + 1) for x in range(hx0 + 1, hx1 - 1)], bronze)
    line(image, [(hx0 + 1, y) for y in range(hy0 + 2, hy1 - 1)], (15, 15, 15))
    for x, y in ((hx0 + 1, hy0 + 1), (hx1 - 2, hy0 + 1), (hx0 + 1, hy1 - 2)):
        image.putpixel((x, y), (112, 96, 74, 255))

    # Lentille chaude, avec cœur blanc et anneau sale.
    lx0, ly0, lx1, ly1 = front(*lens)
    for y in range(ly0, ly1):
        for x in range(lx0, lx1):
            edge = x in (lx0, lx1 - 1) or y in (ly0, ly1 - 1)
            image.putpixel((x, y), (102, 82, 57, 255) if edge else (244, 230, 190, 255))

    # Boutonnage vertical et bandes de pellicule sur le manteau.
    bx0, by0, bx1, by1 = front(*body)
    for y in range(by0 + 2, by1 - 1, 3):
        image.putpixel(((bx0 + bx1) // 2, y), (126, 100, 70, 255))
    for x in (bx0 + 1, bx1 - 2):
        for y in range(by0, by1):
            image.putpixel((x, y), tape + (255,))
            if (y - by0) % 3 == 1:
                image.putpixel((x, y), (91, 79, 67, 255))
    return image


def absent_texture() -> Image.Image:
    image = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    skin = (177, 173, 169)
    skin_shadow = (135, 132, 132)
    hair = (34, 28, 27)
    coat = (48, 65, 76)
    coat_shadow = (31, 43, 51)
    trousers = (43, 42, 43)
    boot = (30, 25, 23)

    head = (0, 0, 8, 8, 8)
    body = (16, 16, 8, 12, 4)
    right_arm = (40, 16, 4, 12, 4)
    right_leg = (0, 16, 4, 12, 4)
    left_arm = (32, 48, 4, 12, 4)
    left_leg = (16, 48, 4, 12, 4)

    fill_noise(image, box_rect(*head), skin, skin_shadow, 0.17, 4)
    fill_noise(image, box_rect(*body), coat, coat_shadow, 0.28, 5)
    for arm in (right_arm, left_arm):
        fill_noise(image, box_rect(*arm), coat, coat_shadow, 0.28, 5)
        x0, y0, x1, y1 = box_rect(*arm)
        fill_noise(image, (x0, y1 - 4, x1, y1), skin, skin_shadow, 0.25, 4)
    for leg in (right_leg, left_leg):
        fill_noise(image, box_rect(*leg), trousers, (56, 54, 55), 0.18, 4)
        x0, y0, x1, y1 = box_rect(*leg)
        fill_noise(image, (x0, y1 - 3, x1, y1), boot, (50, 40, 34), 0.20, 3)

    # Chevelure trop symétrique, puis visage décalé d'un pixel.
    fill_noise(image, (8, 0, 16, 8), hair, (51, 40, 36), 0.25, 3)
    fill_noise(image, (0, 8, 32, 11), hair, (51, 40, 36), 0.22, 3)
    fx0, fy0, _, _ = front(*head)
    image.putpixel((fx0 + 2, fy0 + 3), (12, 15, 17, 255))
    image.putpixel((fx0 + 6, fy0 + 4), (12, 15, 17, 255))
    line(image, [(fx0 + x, fy0 + 6) for x in range(2, 7)], (76, 62, 62))
    image.putpixel((fx0 + 6, fy0 + 5), (116, 105, 103, 255))

    # Couture impossible verticale et col en V inspirés du concept.
    bx0, by0, bx1, by1 = front(*body)
    for y in range(by0, by1):
        image.putpixel(((bx0 + bx1) // 2, y), (12, 14, 16, 255))
    line(image, [(bx0 + 2, by0), (bx0 + 3, by0 + 1), (bx0 + 4, by0 + 2)], skin_shadow)
    line(image, [(bx1 - 3, by0), (bx1 - 4, by0 + 1)], skin_shadow)
    return image


def rampant_texture() -> Image.Image:
    image = Image.new("RGBA", (64, 64), (8, 8, 10, 255))
    mineral = (102, 97, 94)
    pale = (154, 148, 143)
    joint = (31, 31, 35)
    vein = (68, 70, 75)

    torso = (0, 0, 8, 5, 14)
    skull = (0, 20, 8, 7, 7)
    front_right = (32, 20, 3, 3, 11)
    front_left = (32, 34, 3, 3, 11)
    rear_right = (0, 36, 3, 3, 11)
    rear_left = (0, 50, 3, 3, 11)
    spike = (28, 50, 2, 3, 2)

    fill_noise(image, box_rect(*torso), mineral, joint, 0.20, 8)
    fill_noise(image, box_rect(*skull), pale, mineral, 0.32, 7)
    for limb in (front_right, front_left, rear_right, rear_left):
        fill_noise(image, box_rect(*limb), mineral, joint, 0.30, 7)
        x0, y0, x1, y1 = box_rect(*limb)
        line(image, [(x, y0 + (x - x0) % max(1, y1 - y0)) for x in range(x0, x1)], vein)
    fill_noise(image, box_rect(*spike), joint, pale, 0.18, 5)

    # Crâne aveugle : seulement une fente respiratoire trop sombre.
    sx0, sy0, sx1, sy1 = front(*skull)
    line(image, [(x, sy1 - 2) for x in range(sx0 + 1, sx1 - 1)], (18, 18, 21))
    # Nervures minérales répétées sur le torse.
    tx0, ty0, tx1, ty1 = front(*torso)
    for y in range(ty0 + 1, ty1 - 1, 2):
        line(image, [(x, y) for x in range(tx0 + 1, tx1 - 1)], vein)
    return image


def projectionist_photo() -> Image.Image:
    source = Image.open(SOURCES / "projectionist_concept_rgba.png").convert("RGBA")
    alpha = source.getchannel("A")
    bounds = alpha.getbbox()
    if bounds is None:
        raise RuntimeError("Le concept Projectionniste ne contient aucun pixel opaque")
    cropped = source.crop(bounds)
    scale = min(68 / cropped.height, 45 / cropped.width)
    size = max(1, round(cropped.width * scale)), max(1, round(cropped.height * scale))
    cropped = cropped.resize(size, Image.Resampling.LANCZOS)
    cropped = ImageEnhance.Contrast(cropped).enhance(1.15)

    # Transparence spectrale et scanlines, mais lentille conservée lumineuse.
    pixels = cropped.load()
    for y in range(cropped.height):
        for x in range(cropped.width):
            r, g, b, a = pixels[x, y]
            if a == 0:
                continue
            factor = 0.64 if y % 3 == 1 else 0.82
            pixels[x, y] = (r, g, b, round(a * factor))

    canvas = Image.new("RGBA", (128, 72), (0, 0, 0, 0))
    x = 78 - cropped.width // 2
    y = 71 - cropped.height
    canvas.alpha_composite(cropped, (x, y))
    return canvas


def main() -> None:
    TEXTURES.mkdir(parents=True, exist_ok=True)
    outputs = {
        "projectionist.png": projectionist_texture(),
        "projectionist_photo.png": projectionist_photo(),
        "rampant.png": rampant_texture(),
        "absent.png": absent_texture(),
    }
    for name, image in outputs.items():
        path = TEXTURES / name
        image.save(path, optimize=True)
        print(f"Wrote {path} ({image.width}x{image.height})")


if __name__ == "__main__":
    main()

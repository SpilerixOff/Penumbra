#!/usr/bin/env python3
"""Produit les textures 0.8.0 depuis les concepts ImageGen conservés.

- Sceau de bannissement : icône 64×64 à fond transparent.
- Cavaleuse : atlas UV déterministe 64×64.
- Screamers 7–9 : carrés 512×512 validés par Gradle.
"""

from __future__ import annotations

import random
from pathlib import Path

from PIL import Image, ImageFilter


ROOT = Path(__file__).resolve().parents[1]
SOURCES = ROOT / "tools" / "image_sources"
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "penumbra" / "textures"
RNG = random.Random(0x0800CAFE)


def clamp(value: int) -> int:
    return max(0, min(255, value))


def quantized_rgba(pixel: tuple[int, int, int, int]) -> tuple[int, int, int, int]:
    r, g, b, a = pixel
    if a == 0:
        return 0, 0, 0, 0
    step = 8
    return (
        clamp(round(r / step) * step),
        clamp(round(g / step) * step),
        clamp(round(b / step) * step),
        a,
    )


def ash_ward_icon() -> Image.Image:
    source = Image.open(SOURCES / "ash_ward_concept_rgba.png").convert("RGBA")
    bounds = source.getchannel("A").getbbox()
    if bounds is None:
        raise RuntimeError("Concept du Sceau de bannissement entièrement transparent")
    crop = source.crop(bounds)
    crop.thumbnail((58, 58), Image.Resampling.LANCZOS)
    crop = crop.filter(ImageFilter.UnsharpMask(radius=0.7, percent=145, threshold=3))
    # Pillow 14 remplace getdata() par get_flattened_data(); ce repli conserve
    # le script reproductible avec les versions encore livrées avec certains JDK.
    pixels = (crop.get_flattened_data()
              if hasattr(crop, "get_flattened_data") else crop.getdata())
    crop.putdata([quantized_rgba(pixel) for pixel in pixels])
    icon = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    icon.alpha_composite(crop, ((64 - crop.width) // 2, (64 - crop.height) // 2))
    return icon


def box_rect(u: int, v: int, w: int, h: int, d: int) -> tuple[int, int, int, int]:
    return u, v, u + 2 * (d + w), v + d + h


def front_rect(u: int, v: int, w: int, h: int, d: int) -> tuple[int, int, int, int]:
    return u + d, v + d, u + d + w, v + d + h


def fill_noise(image: Image.Image, rect: tuple[int, int, int, int],
               base: tuple[int, int, int], accent: tuple[int, int, int],
               chance: float, noise: int) -> None:
    x0, y0, x1, y1 = rect
    for y in range(max(0, y0), min(image.height, y1)):
        for x in range(max(0, x0), min(image.width, x1)):
            color = accent if RNG.random() < chance else base
            delta = RNG.randint(-noise, noise)
            image.putpixel((x, y), tuple(clamp(c + delta) for c in color) + (255,))


def scuttler_atlas() -> Image.Image:
    image = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    abdomen = (0, 0, 8, 5, 9)
    thorax = (0, 15, 6, 4, 6)
    face = (26, 15, 4, 2, 3)
    proximal = (0, 27, 6, 2, 2)
    distal = (18, 27, 5, 1, 1)

    fill_noise(image, box_rect(*abdomen), (29, 26, 25), (61, 45, 38), 0.25, 5)
    fill_noise(image, box_rect(*thorax), (42, 31, 27), (72, 49, 38), 0.24, 5)
    fill_noise(image, box_rect(*face), (13, 14, 16), (35, 29, 27), 0.16, 3)
    fill_noise(image, box_rect(*proximal), (29, 24, 23), (66, 43, 34), 0.28, 4)
    fill_noise(image, box_rect(*distal), (18, 18, 20), (52, 35, 29), 0.25, 3)

    # Bande de chitine usée sur l'abdomen et deux reflets minuscules sur la face.
    ax0, ay0, ax1, ay1 = front_rect(*abdomen)
    for x in range(ax0 + 1, ax1 - 1):
        if (x - ax0) % 3 == 0:
            image.putpixel((x, ay0 + 2), (83, 59, 45, 255))
    fx0, fy0, fx1, _ = front_rect(*face)
    if fx1 - fx0 >= 4:
        image.putpixel((fx0 + 1, fy0), (211, 209, 190, 255))
        image.putpixel((fx1 - 2, fy0), (184, 55, 45, 255))
    return image


def square_screamer(name: str) -> Image.Image:
    source = Image.open(SOURCES / name).convert("RGB")
    edge = min(source.width, source.height)
    left = (source.width - edge) // 2
    top = (source.height - edge) // 2
    crop = source.crop((left, top, left + edge, top + edge))
    return crop.resize((512, 512), Image.Resampling.LANCZOS).filter(
        ImageFilter.UnsharpMask(radius=0.8, percent=115, threshold=4))


def main() -> None:
    item_dir = ASSETS / "item"
    entity_dir = ASSETS / "entity"
    gui_dir = ASSETS / "gui"
    for directory in (item_dir, entity_dir, gui_dir):
        directory.mkdir(parents=True, exist_ok=True)

    outputs = {
        item_dir / "ash_ward.png": ash_ward_icon(),
        entity_dir / "scuttler.png": scuttler_atlas(),
        gui_dir / "screamer_host_7.png": square_screamer("screamer_v7_projectionist.png"),
        gui_dir / "screamer_host_8.png": square_screamer("screamer_v8_rampant.png"),
        gui_dir / "screamer_host_9.png": square_screamer("screamer_v9_absent.png"),
    }
    for path, image in outputs.items():
        image.save(path, optimize=True)
        print(f"Wrote {path.relative_to(ROOT)} ({image.width}x{image.height})")


if __name__ == "__main__":
    main()

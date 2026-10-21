"""Build the camera/photo sprites from the preserved ImageGen source files.

The generated source images stay in tools/image_sources.  This script only performs
deterministic production post-processing: alpha-safe crop, Minecraft-sized
downscale, and the four chemical-development colour stages.
"""

from __future__ import annotations

from pathlib import Path
import random

from PIL import Image, ImageDraw, ImageEnhance


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "tools" / "image_sources"
ITEM_OUT = ROOT / "src" / "main" / "resources" / "assets" / "penumbra" / "textures" / "item"
BLOCK_OUT = ROOT / "src" / "main" / "resources" / "assets" / "penumbra" / "textures" / "block"


def cropped(image: Image.Image) -> Image.Image:
    rgba = image.convert("RGBA")
    box = rgba.getchannel("A").getbbox()
    if box is None:
        raise ValueError("generated asset contains no opaque pixels")
    return rgba.crop(box)


def fit_sprite(image: Image.Image, extent: int = 60) -> Image.Image:
    source = cropped(image)
    ratio = min(extent / source.width, extent / source.height)
    width = max(1, round(source.width * ratio))
    height = max(1, round(source.height * ratio))
    source = source.resize((width, height), Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    canvas.alpha_composite(source, ((64 - width) // 2, (64 - height) // 2))
    return canvas


def development_stage(source: Image.Image, stage: int) -> Image.Image:
    """Treat only the emulsion window; the aged paper stays recognisable."""
    photo = cropped(source)
    if stage == 3:
        return photo

    # The ImageGen master is an instant print: its emulsion occupies the upper
    # central rectangle and the lower quarter is the characteristic paper lip.
    left = round(photo.width * 0.075)
    top = round(photo.height * 0.060)
    right = round(photo.width * 0.925)
    bottom = round(photo.height * 0.765)

    result = ImageEnhance.Color(photo).enhance(0.72 if stage == 2 else 0.42)
    pixels = result.load()
    rng = random.Random(0x50484F54 + stage)
    for y in range(top, bottom):
        for x in range(left, right):
            r, g, b, a = pixels[x, y]
            if a == 0:
                continue
            luminance = (r * 54 + g * 183 + b * 19) // 256
            grain = rng.randrange(-4, 5)
            if stage == 0:
                value = max(0, min(255, 3 + luminance * 2 // 100 + grain))
                pixels[x, y] = (value * 3 // 4, value * 7 // 8, value, a)
            elif stage == 1:
                value = max(0, min(255, 8 + luminance * 17 // 100 + grain))
                pixels[x, y] = (value * 4 // 5, value * 9 // 10, value, a)
            else:
                value = max(0, min(255, 24 + luminance * 56 // 100 + grain))
                pixels[x, y] = (
                    min(255, value * 11 // 10),
                    min(255, value * 9 // 10),
                    min(255, value * 13 // 20),
                    a,
                )
    return result


def frame_side_texture() -> Image.Image:
    image = Image.new("RGBA", (16, 16), (47, 30, 24, 255))
    draw = ImageDraw.Draw(image)
    for y, colour in ((1, (87, 54, 37, 255)), (5, (35, 24, 22, 255)),
                      (10, (75, 44, 31, 255)), (14, (28, 24, 24, 255))):
        draw.line((0, y, 15, y), fill=colour)
    for x, y in ((2, 3), (7, 11), (12, 7), (14, 2)):
        draw.point((x, y), fill=(112, 69, 42, 255))
        if x + 1 < 16:
            draw.point((x + 1, y), fill=(26, 20, 19, 255))
    return image


def main() -> None:
    ITEM_OUT.mkdir(parents=True, exist_ok=True)
    BLOCK_OUT.mkdir(parents=True, exist_ok=True)

    photo_master = Image.open(SOURCE / "instant_photo_rgba.png")
    names = ("camera_photo_black", "camera_photo_faint", "camera_photo_sepia", "camera_photo")
    for stage, name in enumerate(names):
        fit_sprite(development_stage(photo_master, stage)).save(ITEM_OUT / f"{name}.png")

    frame_master = Image.open(SOURCE / "photo_frame_rgba.png")
    frame = fit_sprite(frame_master, extent=62)
    frame.save(ITEM_OUT / "photo_frame.png")
    frame.save(BLOCK_OUT / "photo_frame_front.png")
    frame_side_texture().save(BLOCK_OUT / "photo_frame_side.png")


if __name__ == "__main__":
    main()

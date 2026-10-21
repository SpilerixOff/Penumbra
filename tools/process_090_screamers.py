"""Rebuild the three Penumbra 0.9.0 screamer textures from ImageGen sources."""

from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
SOURCE_DIR = ROOT / "tools" / "image_sources"
OUTPUT_DIR = (
    ROOT / "src" / "main" / "resources" / "assets" / "penumbra" / "textures" / "gui"
)

ASSETS = {
    "screamer_v10_rampant.png": "screamer_rampant.png",
    "screamer_v11_absent.png": "screamer_absent.png",
    "screamer_v12_chorus.png": "screamer_chorus.png",
}


def main() -> None:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    for source_name, output_name in ASSETS.items():
        source = SOURCE_DIR / source_name
        output = OUTPUT_DIR / output_name
        if not source.is_file():
            raise FileNotFoundError(source)
        with Image.open(source) as image:
            converted = image.convert("RGB")
            resized = converted.resize((512, 512), Image.Resampling.LANCZOS)
            resized.save(output, optimize=True)
        print(f"{output.relative_to(ROOT)}: 512x512 RGB")


if __name__ == "__main__":
    main()

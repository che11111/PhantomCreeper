#!/usr/bin/env python3
"""Rebuild the PhantomCreeper atlas from Minecraft's creeper/phantom palettes."""

from __future__ import annotations

import argparse
import base64
import io
import json
import zipfile
from collections import Counter
from pathlib import Path

from PIL import Image


def load_texture(jar: Path, path: str) -> Image.Image:
    with zipfile.ZipFile(jar) as archive:
        return Image.open(io.BytesIO(archive.read(path))).convert("RGBA")


def opaque_palette(image: Image.Image) -> list[tuple[int, int, int]]:
    counts = Counter(image.getdata())
    return [
        color[:3]
        for color, count in counts.items()
        if color[3] > 0 and count >= 2
    ]


def nearest_color(
    color: tuple[int, int, int],
    palette: list[tuple[int, int, int]],
) -> tuple[int, int, int]:
    return min(
        palette,
        key=lambda candidate: sum(
            (candidate[index] - color[index]) ** 2 for index in range(3)
        ),
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("minecraft_jar", type=Path)
    parser.add_argument("model", type=Path)
    parser.add_argument("output_png", type=Path)
    args = parser.parse_args()

    creeper = load_texture(
        args.minecraft_jar,
        "assets/minecraft/textures/entity/creeper/creeper.png",
    )
    phantom = load_texture(
        args.minecraft_jar,
        "assets/minecraft/textures/entity/phantom/phantom.png",
    )
    creeper_palette = opaque_palette(creeper)
    phantom_palette = opaque_palette(phantom)

    model_data = json.loads(args.model.read_text(encoding="utf-8"))
    source = model_data["textures"][0]["source"]
    encoded = source.split(",", 1)[1]
    current = Image.open(io.BytesIO(base64.b64decode(encoded))).convert("RGBA")
    output = Image.new("RGBA", current.size, (0, 0, 0, 0))

    for y in range(current.height):
        for x in range(current.width):
            pixel = current.getpixel((x, y))
            if pixel[3] == 0:
                continue

            # The model's head UVs exactly match the creeper head block.
            if x < 32 and y < 16:
                output.putpixel((x, y), creeper.getpixel((x, y)))
                continue

            # Green atlas areas are the creeper head/body. Lower atlas areas
            # contain the phantom tail and both segmented wings.
            palette = creeper_palette if y < 32 else phantom_palette
            mapped = nearest_color(pixel[:3], palette)
            output.putpixel((x, y), (*mapped, pixel[3]))

    args.output_png.parent.mkdir(parents=True, exist_ok=True)
    output.save(args.output_png)
    png_data = io.BytesIO()
    output.save(png_data, format="PNG")
    model_data["textures"][0]["source"] = (
        "data:image/png;base64,"
        + base64.b64encode(png_data.getvalue()).decode("ascii")
    )
    args.model.write_text(
        json.dumps(model_data, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Generate CrossLauncher public-safe wave background gradient BMPs.

The generated files are deterministic procedural art. They are not sampled
from platform firmware assets, screenshots, wallpapers, or third-party packs.
"""

from __future__ import annotations

import argparse
import colorsys
import math
import struct
from pathlib import Path


WIDTH = 60
HEIGHT = 34
PRESET_COUNT = 34


def clamp(value: float, lo: float = 0.0, hi: float = 1.0) -> float:
    return max(lo, min(hi, value))


def mix(a: tuple[float, float, float], b: tuple[float, float, float], t: float) -> tuple[float, float, float]:
    return tuple((a[i] * (1.0 - t)) + (b[i] * t) for i in range(3))


def smoothstep(edge0: float, edge1: float, x: float) -> float:
    if edge0 == edge1:
        return 0.0
    t = clamp((x - edge0) / (edge1 - edge0))
    return t * t * (3.0 - (2.0 * t))


def palette(index: int) -> tuple[tuple[float, float, float], tuple[float, float, float], tuple[float, float, float]]:
    hue = ((index - 1) * 0.61803398875) % 1.0
    accent_hue = (hue + 0.14 + ((index % 5) * 0.017)) % 1.0
    shadow_hue = (hue - 0.08) % 1.0
    top = colorsys.hsv_to_rgb(shadow_hue, 0.34 + ((index % 4) * 0.035), 0.16 + ((index % 3) * 0.025))
    mid = colorsys.hsv_to_rgb(hue, 0.48 + ((index % 5) * 0.03), 0.44 + ((index % 4) * 0.025))
    glow = colorsys.hsv_to_rgb(accent_hue, 0.50 + ((index % 6) * 0.025), 0.72 + ((index % 3) * 0.035))
    return top, mid, glow


def pixel(index: int, x: int, y: int) -> tuple[int, int, int]:
    u = x / (WIDTH - 1)
    v = y / (HEIGHT - 1)
    top, mid, glow = palette(index)

    base = mix(top, mid, smoothstep(0.0, 1.0, v))

    # Soft diagonal bloom, deliberately abstract and off-brand.
    diagonal = smoothstep(-0.25, 0.80, 1.0 - abs((u * 0.95) + (v * 0.55) - (0.78 + ((index % 7) * 0.035))))
    base = mix(base, glow, diagonal * 0.24)

    # Subtle vignette and a tiny procedural grain prevent flat color banding.
    dx = u - 0.5
    dy = v - 0.52
    vignette = clamp(1.0 - ((dx * dx * 1.35) + (dy * dy * 1.90)))
    wave = math.sin((u * (4.0 + (index % 4))) + (v * 2.2) + (index * 0.47)) * 0.018
    grain = ((((x * 17) + (y * 31) + (index * 43)) % 19) - 9) / 255.0

    rgb = []
    for c in base:
        value = (c * (0.84 + (vignette * 0.22))) + wave + grain
        rgb.append(int(round(clamp(value) * 255.0)))
    return rgb[0], rgb[1], rgb[2]


def write_bmp(path: Path, pixels: list[tuple[int, int, int]]) -> None:
    row_stride = ((24 * WIDTH + 31) // 32) * 4
    pixel_data_size = row_stride * HEIGHT
    file_size = 54 + pixel_data_size
    header = bytearray()
    header += b"BM"
    header += struct.pack("<IHHI", file_size, 0, 0, 54)
    header += struct.pack("<IiiHHIIiiII", 40, WIDTH, HEIGHT, 1, 24, 0, pixel_data_size, 2835, 2835, 0, 0)

    rows = bytearray()
    padding = b"\x00" * (row_stride - (WIDTH * 3))
    for y in range(HEIGHT - 1, -1, -1):
        for x in range(WIDTH):
            r, g, b = pixels[(y * WIDTH) + x]
            rows += bytes((b, g, r))
        rows += padding

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(bytes(header) + bytes(rows))


def generate(output_dir: Path) -> None:
    for index in range(1, PRESET_COUNT + 1):
        pixels = [pixel(index, x, y) for y in range(HEIGHT) for x in range(WIDTH)]
        write_bmp(output_dir / f"{index:02d}_raw.bmp", pixels)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--out",
        type=Path,
        default=Path("launcher_app/src/main/assets/public_xmb_gradients/raw_60x34"),
        help="Output directory for generated BMP gradient assets.",
    )
    args = parser.parse_args()
    generate(args.out)
    print(f"Generated {PRESET_COUNT} public-safe gradients in {args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

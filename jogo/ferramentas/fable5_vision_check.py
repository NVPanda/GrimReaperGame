#!/usr/bin/env python3
"""Audit local GrimReap asset dimensions, alpha channels, and normal-map pairs."""

import argparse
import json
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parent.parent
SIZES = {
    "character_standard": (120, 160),
    "character_hd": (240, 320),
    "prop": (80, 80),
    "prop_large": (160, 160),
    "tileset": (320, 320),
    "icon": (40, 40),
}


def audit_bakin_assets(manifest_path: Path, assets_dir: Path) -> tuple[list[dict[str, object]], bool]:
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    report: list[dict[str, object]] = []
    passed = True
    for item in manifest.get("assets", []):
        name = item["name"]
        folder = item.get("folder", "")
        image_path = assets_dir / folder / f"{name}.png"
        expected_size = SIZES.get(item.get("category"))
        record: dict[str, object] = {
            "name": name,
            "path": str(image_path),
            "expected_resolution": f"{expected_size[0]}x{expected_size[1]}" if expected_size else None,
        }
        if expected_size is None:
            record["status"] = "UNKNOWN_CATEGORY"
            passed = False
            report.append(record)
            continue
        if not image_path.is_file():
            record["status"] = "MISSING"
            passed = False
            report.append(record)
            continue
        try:
            with Image.open(image_path) as image:
                record["resolution"] = f"{image.width}x{image.height}"
                record["mode"] = image.mode
                size_ok = image.size == expected_size
                alpha_ok = image.mode == "RGBA" and "A" in image.getbands()
                alpha = image.getchannel("A")
                alpha_min, alpha_max = alpha.getextrema()
                transparency_ok = (
                    alpha_min == 255
                    if item.get("category") == "tileset"
                    else alpha_min == 0 and alpha_max > 0
                )
                pixels = image.load()
                alpha_clean = all(
                    pixels[x, y][3] != 0
                    or (pixels[x, y][0] == 0 and pixels[x, y][1] == 0 and pixels[x, y][2] == 0)
                    for y in range(image.height)
                    for x in range(image.width)
                )
                record["alpha_range"] = [alpha_min, alpha_max]
                record["transparent_pixels_clean"] = alpha_clean
                grid_ok = True
                if item.get("category", "").startswith("character_"):
                    cell_w, cell_h = image.width // 3, image.height // 4
                    populated_cells = 0
                    for row in range(4):
                        for column in range(3):
                            cell = alpha.crop(
                                (column * cell_w, row * cell_h, (column + 1) * cell_w, (row + 1) * cell_h)
                            )
                            populated_cells += cell.getextrema()[1] > 0
                    grid_ok = populated_cells == 12
                    record["sprite_grid"] = f"{populated_cells}/12 populated cells"
        except OSError as error:
            record["status"] = "UNREADABLE"
            record["error"] = str(error)
            passed = False
            report.append(record)
            continue

        normal_ok = True
        if item.get("normal_map", True):
            normal_path = image_path.with_name(f"{name}_n.png")
            record["normal_map"] = str(normal_path)
            if not normal_path.is_file():
                normal_ok = False
                record["normal_map_status"] = "MISSING"
            else:
                try:
                    with Image.open(normal_path) as normal:
                        normal_ok = normal.size == expected_size and normal.mode == "RGBA" and "A" in normal.getbands()
                        record["normal_map_resolution"] = f"{normal.width}x{normal.height}"
                        record["normal_map_mode"] = normal.mode
                        record["normal_map_status"] = "OK" if normal_ok else "INVALID"
                except OSError as error:
                    normal_ok = False
                    record["normal_map_status"] = "UNREADABLE"
                    record["normal_map_error"] = str(error)

        record["status"] = (
            "OK"
            if size_ok and alpha_ok and transparency_ok and alpha_clean and grid_ok and normal_ok
            else "INVALID"
        )
        record["alpha"] = "OK" if alpha_ok else "MISSING"
        passed = passed and size_ok and alpha_ok and transparency_ok and alpha_clean and grid_ok and normal_ok
        report.append(record)
    return report, passed


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, default=ROOT / "assets" / "bakin_manifest.json")
    parser.add_argument("--assets-dir", type=Path, default=ROOT / "assets" / "bakin_pixelart")
    args = parser.parse_args()
    report, passed = audit_bakin_assets(args.manifest, args.assets_dir)
    print(json.dumps(report, indent=2, ensure_ascii=False))
    return 0 if passed else 1


if __name__ == "__main__":
    raise SystemExit(main())

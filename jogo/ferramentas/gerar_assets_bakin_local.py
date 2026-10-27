#!/usr/bin/env python3
"""Generate original GrimReap pixel-art assets locally, without external API calls."""

import argparse
import json
import math
from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parent.parent
SIZES = {
    "character_standard": (120, 160),
    "character_hd": (240, 320),
    "prop": (80, 80),
    "prop_large": (160, 160),
    "tileset": (320, 320),
    "icon": (40, 40),
}


def character_sheet(name: str, size: tuple[int, int]) -> Image.Image:
    width, height = size
    cell_w, cell_h = width // 3, height // 4
    image = Image.new("RGBA", size)
    draw = ImageDraw.Draw(image)
    slime = name == "monster_forest_slime"
    alchemist = name == "npc_village_alchemist"
    tunic = "#6cc5b4" if not alchemist else "#805c92"
    cloak = "#984b36" if not alchemist else "#382f54"
    hair = "#4b302a" if not alchemist else "#d5b457"
    dark = "#253039"

    for direction in range(4):
        for frame in range(3):
            x = frame * cell_w
            y = direction * cell_h
            cx = x + cell_w // 2
            unit = min(cell_w, cell_h)
            offset = (frame - 1) * max(1, unit // 18)
            draw.ellipse(
                (cx - unit * 0.27, y + cell_h * 0.80, cx + unit * 0.27, y + cell_h * 0.89),
                fill=(20, 24, 31, 105),
            )
            if name == "hero_adventurer":
                draw_red_panda_adventurer(draw, cx, y, frame, direction, offset)
                continue
            if slime:
                top = y + int(cell_h * 0.34) + (abs(frame - 1) * max(1, unit // 24))
                draw.ellipse(
                    (cx - unit * 0.31, top, cx + unit * 0.31, y + cell_h * 0.79),
                    fill="#176d50",
                    outline="#143d38",
                    width=max(1, unit // 20),
                )
                draw.ellipse(
                    (cx - unit * 0.25, top + unit * 0.04, cx + unit * 0.24, y + cell_h * 0.73),
                    fill="#47c783",
                )
                eye_y = y + int(cell_h * 0.57)
                eye_dx = max(2, unit // 10)
                if direction == 0:
                    draw.rectangle((cx - eye_dx - 1, eye_y, cx - eye_dx, eye_y + max(2, unit // 12)), fill="#f7d56b")
                    draw.rectangle((cx + eye_dx, eye_y, cx + eye_dx + 1, eye_y + max(2, unit // 12)), fill="#f7d56b")
                elif direction in (1, 2):
                    draw.rectangle((cx + (1 if direction == 2 else -2), eye_y, cx + (3 if direction == 2 else 0), eye_y + 2), fill="#f7d56b")
                continue

            leg_y = y + int(cell_h * 0.68)
            leg_width = max(3, unit // 6)
            spread = offset if frame != 1 else 0
            draw.rectangle((cx - leg_width - spread, leg_y, cx - 1 - spread, y + int(cell_h * 0.83)), fill=dark)
            draw.rectangle((cx + 1 + spread, leg_y, cx + leg_width + spread, y + int(cell_h * 0.83)), fill=dark)
            draw.rectangle((cx - leg_width - spread, y + int(cell_h * 0.81), cx - 1 - spread, y + int(cell_h * 0.86)), fill="#d4b786")
            draw.rectangle((cx + 1 + spread, y + int(cell_h * 0.81), cx + leg_width + spread, y + int(cell_h * 0.86)), fill="#d4b786")

            torso_top = y + int(cell_h * 0.43)
            draw.polygon(
                [
                    (cx - unit * 0.24, torso_top),
                    (cx + unit * 0.24, torso_top),
                    (cx + unit * 0.20, y + cell_h * 0.73),
                    (cx - unit * 0.20, y + cell_h * 0.73),
                ],
                fill=cloak,
                outline=dark,
            )
            draw.rectangle((cx - unit * 0.17, torso_top + unit * 0.02, cx + unit * 0.17, y + cell_h * 0.70), fill=tunic)
            arm_y = torso_top + unit * 0.08
            draw.rectangle((cx - unit * 0.31, arm_y, cx - unit * 0.20, arm_y + unit * 0.24), fill=cloak, outline=dark)
            draw.rectangle((cx + unit * 0.20, arm_y, cx + unit * 0.31, arm_y + unit * 0.24), fill=cloak, outline=dark)
            head_y = y + int(cell_h * 0.16)
            draw.ellipse(
                (cx - unit * 0.21, head_y, cx + unit * 0.21, y + cell_h * 0.49),
                fill="#e7bb91",
                outline=dark,
                width=max(1, unit // 28),
            )
            hair_y = y + int(cell_h * 0.13)
            draw.pieslice((cx - unit * 0.23, hair_y, cx + unit * 0.23, y + cell_h * 0.43), 180, 360, fill=hair)
            if direction == 0:
                eye_y = y + int(cell_h * 0.34)
                draw.rectangle((cx - unit * 0.10, eye_y, cx - unit * 0.06, eye_y + max(2, unit // 24)), fill=dark)
                draw.rectangle((cx + unit * 0.06, eye_y, cx + unit * 0.10, eye_y + max(2, unit // 24)), fill=dark)
            elif direction in (1, 2):
                eye_x = cx + (unit * 0.08 if direction == 2 else -unit * 0.12)
                draw.rectangle((eye_x, y + cell_h * 0.34, eye_x + max(2, unit // 20), y + cell_h * 0.38), fill=dark)

            if alchemist:
                vial_x = cx + unit * 0.22
                draw.rectangle((vial_x, y + cell_h * 0.53, vial_x + unit * 0.10, y + cell_h * 0.65), fill="#7ce0dc", outline=dark)
                draw.rectangle((vial_x + unit * 0.02, y + cell_h * 0.49, vial_x + unit * 0.08, y + cell_h * 0.54), fill="#d6bd73")
            else:
                sword_x = cx + unit * 0.24
                draw.line((sword_x, y + cell_h * 0.46, sword_x + unit * 0.15, y + cell_h * 0.70), fill="#d9edf0", width=max(1, unit // 16))
                draw.line((sword_x - unit * 0.02, y + cell_h * 0.54, sword_x + unit * 0.08, y + cell_h * 0.49), fill="#d0a65f", width=max(1, unit // 18))
    return image


def draw_red_panda_adventurer(
    draw: ImageDraw.ImageDraw,
    cx: int,
    y: int,
    frame: int,
    direction: int,
    offset: int,
) -> None:
    shadow = "#171921"
    coat = "#252b36"
    coat_light = "#3b4654"
    orange = "#c75c32"
    orange_light = "#eb8750"
    cream = "#f3e6ca"
    brass = "#c99b55"
    cyan = "#68edf2"
    step = (frame - 1) * 2
    leg_shift = step if frame != 1 else 0

    if direction == 3:
        draw.line((cx - 8, y + 55, cx - 20 + offset, y + 43, cx - 18, y + 31), fill=shadow, width=15)
        draw.line((cx - 8, y + 55, cx - 20 + offset, y + 43, cx - 18, y + 31), fill=orange, width=11)
        draw.rectangle((cx - 24 + offset, y + 38, cx - 20 + offset, y + 43), fill=cream)
        draw.rectangle((cx - 21, y + 45, cx - 16, y + 49), fill=cream)
    else:
        draw.line((cx - 8, y + 55, cx - 21 + offset, y + 43, cx - 18, y + 31), fill=shadow, width=15)
        draw.line((cx - 8, y + 55, cx - 21 + offset, y + 43, cx - 18, y + 31), fill=orange, width=11)
        draw.rectangle((cx - 24 + offset, y + 38, cx - 20 + offset, y + 43), fill=cream)
        draw.rectangle((cx - 21, y + 45, cx - 16, y + 49), fill=cream)

    draw.rectangle((cx - 13 - leg_shift, y + 62, cx - 3 - leg_shift, y + 76), fill=shadow)
    draw.rectangle((cx + 3 + leg_shift, y + 62, cx + 13 + leg_shift, y + 76), fill=shadow)
    draw.rectangle((cx - 15 - leg_shift, y + 73, cx - 2 - leg_shift, y + 78), fill=coat_light)
    draw.rectangle((cx + 2 + leg_shift, y + 73, cx + 15 + leg_shift, y + 78), fill=coat_light)
    draw.rectangle((cx - 13 - leg_shift, y + 71, cx - 4 - leg_shift, y + 73), fill=brass)
    draw.rectangle((cx + 4 + leg_shift, y + 71, cx + 13 + leg_shift, y + 73), fill=brass)

    draw.polygon(
        [(cx - 19, y + 43), (cx + 19, y + 43), (cx + 17, y + 68), (cx + 10, y + 72),
         (cx - 11, y + 72), (cx - 17, y + 67)],
        fill=shadow,
    )
    draw.polygon(
        [(cx - 16, y + 45), (cx + 16, y + 45), (cx + 13, y + 66), (cx + 8, y + 69),
         (cx - 9, y + 69), (cx - 14, y + 66)],
        fill=coat,
    )
    draw.rectangle((cx - 15, y + 49, cx - 11, y + 63), fill=orange)
    draw.rectangle((cx + 11, y + 49, cx + 15, y + 63), fill=orange)
    draw.rectangle((cx - 4, y + 47, cx + 4, y + 66), fill=coat_light)
    draw.rectangle((cx - 2, y + 52, cx + 2, y + 58), fill=brass)
    draw.line((cx - 7, y + 60, cx + 7, y + 60), fill=cyan, width=2)
    draw.rectangle((cx - 20, y + 49, cx - 16, y + 60), fill=orange_light)
    draw.rectangle((cx + 16, y + 49, cx + 20, y + 60), fill=orange_light)
    draw.rectangle((cx - 22, y + 58, cx - 16, y + 62), fill=brass)
    draw.rectangle((cx + 16, y + 58, cx + 22, y + 62), fill=brass)
    draw.rectangle((cx - 19, y + 63, cx - 15, y + 66), fill=cream)
    draw.rectangle((cx + 15, y + 63, cx + 19, y + 66), fill=cream)

    if direction == 3:
        draw.polygon(
            [(cx - 22, y + 24), (cx - 18, y + 10), (cx - 7, y + 17), (cx + 5, y + 14),
             (cx + 19, y + 22), (cx + 18, y + 43), (cx - 17, y + 43)],
            fill=shadow,
        )
        draw.polygon(
            [(cx - 18, y + 24), (cx - 15, y + 14), (cx - 6, y + 20), (cx + 5, y + 17),
             (cx + 15, y + 24), (cx + 14, y + 40), (cx - 14, y + 40)],
            fill=orange,
        )
        draw.rectangle((cx - 10, y + 25, cx + 10, y + 38), fill=coat)
        draw.rectangle((cx - 6, y + 27, cx + 6, y + 29), fill=brass)
        draw.rectangle((cx - 2, y + 31, cx + 2, y + 36), fill=cyan)
        draw.rectangle((cx - 22, y + 21, cx - 17, y + 26), fill=cream)
        draw.rectangle((cx + 14, y + 24, cx + 19, y + 29), fill=cream)
        return

    draw.polygon([(cx - 18, y + 26), (cx - 16, y + 9), (cx - 5, y + 17), (cx - 3, y + 29)], fill=shadow)
    draw.polygon([(cx + 3, y + 29), (cx + 5, y + 17), (cx + 16, y + 9), (cx + 18, y + 26)], fill=shadow)
    draw.polygon([(cx - 15, y + 24), (cx - 14, y + 13), (cx - 7, y + 18), (cx - 6, y + 27)], fill=orange_light)
    draw.polygon([(cx + 6, y + 27), (cx + 7, y + 18), (cx + 14, y + 13), (cx + 15, y + 24)], fill=orange_light)
    draw.polygon(
        [(cx - 19, y + 31), (cx - 13, y + 20), (cx - 5, y + 18), (cx, y + 21),
         (cx + 5, y + 18), (cx + 13, y + 20), (cx + 19, y + 31),
         (cx + 16, y + 44), (cx + 8, y + 49), (cx - 8, y + 49), (cx - 16, y + 44)],
        fill=shadow,
    )
    draw.polygon(
        [(cx - 15, y + 31), (cx - 10, y + 23), (cx - 4, y + 22), (cx, y + 25),
         (cx + 4, y + 22), (cx + 10, y + 23), (cx + 15, y + 31),
         (cx + 13, y + 41), (cx + 7, y + 45), (cx - 7, y + 45), (cx - 13, y + 41)],
        fill=orange,
    )
    if direction == 0:
        draw.polygon([(cx - 13, y + 33), (cx - 7, y + 29), (cx - 2, y + 32), (cx - 3, y + 38), (cx - 9, y + 40)], fill=cream)
        draw.polygon([(cx + 13, y + 33), (cx + 7, y + 29), (cx + 2, y + 32), (cx + 3, y + 38), (cx + 9, y + 40)], fill=cream)
        draw.polygon([(cx - 10, y + 32), (cx - 6, y + 31), (cx - 4, y + 35), (cx - 8, y + 37)], fill=shadow)
        draw.polygon([(cx + 10, y + 32), (cx + 6, y + 31), (cx + 4, y + 35), (cx + 8, y + 37)], fill=shadow)
        draw.rectangle((cx - 7, y + 33, cx - 5, y + 35), fill=cyan)
        draw.rectangle((cx + 5, y + 33, cx + 7, y + 35), fill=cyan)
        draw.polygon([(cx - 5, y + 39), (cx, y + 37), (cx + 5, y + 39), (cx + 4, y + 43), (cx, y + 45), (cx - 4, y + 43)], fill=cream)
        draw.polygon([(cx - 2, y + 39), (cx, y + 38), (cx + 2, y + 39), (cx, y + 41)], fill=shadow)
    else:
        eye_x = cx + (7 if direction == 2 else -9)
        draw.polygon([(eye_x - 4, y + 32), (eye_x + 2, y + 30), (eye_x + 5, y + 34), (eye_x + 2, y + 38), (eye_x - 3, y + 37)], fill=shadow)
        draw.rectangle((eye_x, y + 33, eye_x + 2, y + 35), fill=cyan)
        draw.polygon([(cx - 5, y + 39), (cx, y + 38), (cx + 4, y + 40), (cx + 2, y + 43), (cx - 2, y + 43)], fill=cream)


def prop_image(name: str) -> Image.Image:
    image = Image.new("RGBA", (80, 80))
    draw = ImageDraw.Draw(image)
    if name == "prop_treasure_chest":
        draw.ellipse((13, 62, 68, 73), fill=(17, 22, 24, 100))
        draw.rounded_rectangle((17, 35, 64, 64), radius=5, fill="#75422f", outline="#28242d", width=3)
        draw.pieslice((17, 21, 64, 52), 180, 360, fill="#9b5b38", outline="#28242d", width=3)
        draw.rectangle((20, 42, 61, 47), fill="#c38a44")
        draw.rectangle((38, 35, 45, 59), fill="#d8b55e")
        draw.ellipse((37, 45, 46, 54), fill="#5de0cf", outline="#30343b", width=2)
    elif name == "prop_glowing_crystal":
        draw.ellipse((12, 64, 68, 74), fill=(17, 22, 24, 100))
        draw.polygon([(40, 9), (59, 30), (52, 62), (40, 72), (24, 55), (21, 29)], fill="#177d91", outline="#27323d")
        draw.polygon([(40, 9), (43, 37), (21, 29)], fill="#9df9ed")
        draw.polygon([(40, 9), (59, 30), (43, 37)], fill="#44dce1")
        draw.polygon([(43, 37), (52, 62), (40, 72)], fill="#4370ad")
        draw.polygon([(43, 37), (24, 55), (21, 29)], fill="#58adc8")
        draw.line((40, 12, 40, 68), fill="#e1ffff", width=2)
    elif name == "prop_town_streetlamp":
        draw.ellipse((21, 143, 65, 154), fill=(17, 22, 24, 100))
        draw.rectangle((38, 50, 46, 145), fill="#3b3c45")
        draw.rectangle((35, 140, 49, 148), fill="#876348")
        draw.line((42, 49, 42, 20), fill="#53505b", width=5)
        draw.line((42, 23, 59, 23), fill="#53505b", width=4)
        draw.rectangle((50, 21, 66, 50), fill="#4a4d55", outline="#252932", width=2)
        draw.rectangle((54, 27, 62, 44), fill="#ffd66b", outline="#dd8445")
        draw.polygon([(48, 21), (58, 12), (68, 21)], fill="#6e5960", outline="#252932")
    elif name == "prop_foliage_bush":
        draw.ellipse((10, 62, 70, 74), fill=(17, 22, 24, 100))
        for box, color in [
            ((12, 34, 43, 67), "#275c3b"),
            ((27, 19, 59, 65), "#367b49"),
            ((44, 34, 72, 66), "#245b3a"),
            ((20, 30, 54, 59), "#57a55b"),
            ((39, 25, 64, 54), "#75b95c"),
        ]:
            draw.ellipse(box, fill=color, outline="#243c35", width=2)
        draw.rectangle((39, 55, 45, 66), fill="#73533c")
        draw.ellipse((28, 39, 33, 44), fill="#e48a58")
        draw.ellipse((57, 46, 62, 51), fill="#edb066")
    elif name in ("icon_hp_potion", "icon_mana_ether"):
        liquid = "#e94353" if name == "icon_hp_potion" else "#39bde5"
        draw.ellipse((7, 67, 73, 77), fill=(17, 22, 24, 95))
        draw.rectangle((31, 8, 49, 18), fill="#806547", outline="#252932", width=2)
        draw.rectangle((35, 17, 45, 24), fill="#9fd3d4", outline="#30343b", width=2)
        draw.rounded_rectangle((19, 23, 61, 68), radius=11, fill="#8ec9cd", outline="#28333d", width=3)
        draw.polygon([(23, 44), (57, 44), (55, 63), (25, 63)], fill=liquid)
        draw.rectangle((26, 31, 31, 43), fill="#ecffff")
        draw.line((24, 42, 56, 42), fill="#e7ffff", width=2)
    elif name == "tile_cobblestone_grass":
        image = Image.new("RGBA", (80, 80), "#527c43")
        draw = ImageDraw.Draw(image)
        tile_colors = ("#a3a890", "#899481", "#bdba9e", "#707e74")
        for row in range(4):
            for col in range(4):
                x = col * 20 + (10 if row % 2 else 0)
                y = row * 20
                if x >= 80:
                    continue
                color = tile_colors[(row * 3 + col) % len(tile_colors)]
                draw.rounded_rectangle((x + 1, y + 2, min(x + 18, 79), y + 17), radius=3, fill=color, outline="#536752", width=1)
        for x, y in ((5, 5), (74, 5), (7, 70), (72, 66), (43, 75)):
            draw.line((x, y, x + 3, y - 5), fill="#b7d26b", width=2)
        return image
    return image


def normal_map(image: Image.Image) -> Image.Image:
    gray = image.convert("L")
    alpha = image.getchannel("A")
    width, height = image.size
    pixels = gray.load()
    alpha_pixels = alpha.load()
    result = Image.new("RGBA", image.size)
    output = result.load()
    for y in range(height):
        for x in range(width):
            if not alpha_pixels[x, y]:
                output[x, y] = (128, 128, 255, 0)
                continue
            dx = (pixels[min(x + 1, width - 1), y] - pixels[max(x - 1, 0), y]) / 255
            dy = (pixels[x, min(y + 1, height - 1)] - pixels[x, max(y - 1, 0)]) / 255
            nx, ny, nz = -dx * 2, -dy * 2, 1
            length = math.sqrt(nx * nx + ny * ny + nz * nz)
            output[x, y] = (
                round((nx / length * 0.5 + 0.5) * 255),
                round((ny / length * 0.5 + 0.5) * 255),
                round((nz / length * 0.5 + 0.5) * 255),
                alpha_pixels[x, y],
            )
    return result


def generate(manifest_path: Path, output_dir: Path) -> None:
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    for item in manifest.get("assets", []):
        name = item["name"]
        category = item["category"]
        if category not in SIZES:
            raise ValueError(f"Unsupported Bakin asset category: {category}")
        size = SIZES[category]
        image = character_sheet(name, size) if category.startswith("character_") else prop_image(name)
        if image.size != size:
            image = image.resize(size, Image.Resampling.NEAREST)
        destination = output_dir / item.get("folder", "") / f"{name}.png"
        destination.parent.mkdir(parents=True, exist_ok=True)
        image.save(destination)
        if item.get("normal_map", True):
            normal_map(image).save(destination.with_name(f"{name}_n.png"))
        print(f"{destination.relative_to(output_dir)} {size[0]}x{size[1]}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, default=ROOT / "assets" / "bakin_manifest.json")
    parser.add_argument("--out", type=Path, default=ROOT / "assets" / "bakin_pixelart")
    args = parser.parse_args()
    generate(args.manifest, args.out)


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Извлекает whitelist UI-текстур из официального Minecraft client JAR.

Использование:
    python3 mc_assets.py <client.jar> <output-dir>

Требует Pillow. Скрипт идемпотентен: перезаписывает только файлы из whitelist.
Список файлов и правила обработки зафиксированы в EXTRACT ниже.
"""

from __future__ import annotations

import hashlib
import json
import sys
import zipfile
from io import BytesIO
from pathlib import Path

from PIL import Image

TEXTURES = "assets/minecraft/textures/"

# (jar path, output relative path, scale) — scale 1 = как есть, 3 = nearest-neighbor x3
GUI_1X = [
    ("gui/sprites/widget/button.png", "gui/button.png"),
    ("gui/sprites/widget/button.png.mcmeta", "gui/button.png.mcmeta"),
    ("gui/sprites/widget/button_disabled.png", "gui/button_disabled.png"),
    ("gui/sprites/widget/button_disabled.png.mcmeta", "gui/button_disabled.png.mcmeta"),
    ("gui/sprites/widget/button_highlighted.png", "gui/button_highlighted.png"),
    ("gui/sprites/widget/button_highlighted.png.mcmeta", "gui/button_highlighted.png.mcmeta"),
    ("gui/sprites/widget/tab.png", "gui/tab.png"),
    ("gui/sprites/widget/tab.png.mcmeta", "gui/tab.png.mcmeta"),
    ("gui/sprites/widget/tab_selected.png", "gui/tab_selected.png"),
    ("gui/sprites/widget/tab_selected.png.mcmeta", "gui/tab_selected.png.mcmeta"),
    ("gui/sprites/widget/tab_highlighted.png", "gui/tab_highlighted.png"),
    ("gui/sprites/widget/tab_highlighted.png.mcmeta", "gui/tab_highlighted.png.mcmeta"),
    ("gui/sprites/widget/text_field.png", "gui/text_field.png"),
    ("gui/sprites/widget/text_field.png.mcmeta", "gui/text_field.png.mcmeta"),
    ("gui/sprites/widget/text_field_highlighted.png", "gui/text_field_highlighted.png"),
    ("gui/sprites/widget/text_field_highlighted.png.mcmeta", "gui/text_field_highlighted.png.mcmeta"),
    ("gui/sprites/container/slot.png", "gui/slot.png"),
    ("gui/sprites/widget/slot_frame.png", "gui/slot_frame.png"),
    ("gui/sprites/hud/hotbar.png", "gui/hotbar.png"),
    ("gui/sprites/hud/hotbar_selection.png", "gui/hotbar_selection.png"),
    ("gui/sprites/hud/experience_bar_background.png", "gui/xp_bg.png"),
    ("gui/sprites/hud/experience_bar_progress.png", "gui/xp_progress.png"),
    ("gui/sprites/advancements/title_box.png", "gui/title_box.png"),
    ("gui/sprites/advancements/title_box.png.mcmeta", "gui/title_box.png.mcmeta"),
    ("gui/sprites/advancements/box_obtained.png", "gui/box_obtained.png"),
    ("gui/sprites/advancements/box_obtained.png.mcmeta", "gui/box_obtained.png.mcmeta"),
    ("gui/sprites/advancements/box_unobtained.png", "gui/box_unobtained.png"),
    ("gui/sprites/advancements/box_unobtained.png.mcmeta", "gui/box_unobtained.png.mcmeta"),
    ("gui/sprites/advancements/task_frame_obtained.png", "gui/task_frame_obtained.png"),
    ("gui/sprites/advancements/challenge_frame_obtained.png", "gui/challenge_frame_obtained.png"),
    ("gui/sprites/toast/system.png", "gui/toast_system.png"),
    ("gui/sprites/toast/system.png.mcmeta", "gui/toast_system.png.mcmeta"),
    ("misc/enchanted_glint_item.png", "gui/enchanted_glint.png"),
    ("misc/enchanted_glint_item.png.mcmeta", "gui/enchanted_glint.png.mcmeta"),
    ("misc/vignette.png", "gui/vignette.png"),
]

SLOT_ICONS = [
    ("gui/sprites/container/slot/helmet.png", "slot-icons/helmet.png"),
    ("gui/sprites/container/slot/chestplate.png", "slot-icons/chestplate.png"),
    ("gui/sprites/container/slot/leggings.png", "slot-icons/leggings.png"),
    ("gui/sprites/container/slot/boots.png", "slot-icons/boots.png"),
    ("gui/sprites/container/slot/shield.png", "slot-icons/shield.png"),
    ("gui/sprites/container/slot/sword.png", "slot-icons/sword.png"),
]

ITEMS = [
    "book", "writable_book", "written_book", "knowledge_book", "paper", "filled_map",
    "compass_00", "clock_00", "emerald", "gold_ingot", "amethyst_shard", "redstone",
    "barrier", "armor_stand", "name_tag", "enchanted_book", "experience_bottle",
    "nether_star", "ender_pearl", "end_crystal", "bundle", "chest_minecart",
    "lantern", "soul_lantern", "spyglass", "bell", "item_frame", "glow_item_frame",
    "iron_chestplate", "diamond_chestplate", "netherite_chestplate",
    "wooden_sword", "stone_sword", "iron_sword", "golden_sword", "diamond_sword",
    "netherite_sword",
]

BLOCKS = [
    "dirt", "grass_block_side", "stone", "deepslate_tiles", "obsidian",
    "dark_oak_planks", "amethyst_block", "redstone_lamp", "redstone_lamp_on",
    "end_stone_bricks", "barrel_side", "beacon", "observer_front", "lapis_block",
    "coal_block", "emerald_block", "redstone_block", "gold_block", "diamond_block",
    "command_block_front", "end_portal_frame_top", "end_portal_frame_eye",
]

PARTICLES = ["glint", "enchanted_hit", "flame", "soul_fire_flame"]

# Декоративные элементы, которые дополнительно сохраняются в big/ с апскейлом x3
BIG_3X = [
    "item/nether_star.png", "item/end_crystal.png", "item/chest_minecart.png",
    "item/knowledge_book.png", "item/experience_bottle.png", "item/lantern.png",
    "item/soul_lantern.png", "block/beacon.png", "block/amethyst_block.png",
    "block/obsidian.png", "block/end_portal_frame_top.png", "block/end_portal_frame_eye.png",
    "block/command_block_front.png", "item/netherite_sword.png", "item/enchanted_book.png",
    "item/armor_stand.png", "item/name_tag.png",
]

# Первый кадр анимированной полоски портала (16xN) для CSS-частиц
PORTAL_STRIP = ("block/nether_portal.png", "particle/portal.png")


def save_png(img: Image.Image, dest: Path) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    img.save(dest, format="PNG", optimize=True)


def main() -> int:
    jar_path, out_dir = Path(sys.argv[1]), Path(sys.argv[2])
    out_dir.mkdir(parents=True, exist_ok=True)
    zf = zipfile.ZipFile(jar_path)
    names = set(zf.namelist())

    extracted: list[str] = []
    missing: list[str] = []

    def raw(jar_rel: str, out_rel: str) -> None:
        src = TEXTURES + jar_rel
        if src not in names:
            missing.append(jar_rel)
            return
        data = zf.read(src)
        dest = out_dir / out_rel
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_bytes(data)
        extracted.append(out_rel)

    def scaled(jar_rel: str, out_rel: str, factor: int) -> None:
        src = TEXTURES + jar_rel
        if src not in names:
            missing.append(jar_rel)
            return
        img = Image.open(BytesIO(zf.read(src)))
        img = img.resize((img.width * factor, img.height * factor), Image.Resampling.NEAREST)
        save_png(img, out_dir / out_rel)
        extracted.append(out_rel)

    for jar_rel, out_rel in GUI_1X + SLOT_ICONS:
        raw(jar_rel, out_rel)
    for item in ITEMS:
        raw(f"item/{item}.png", f"item/{item}.png")
    for block in BLOCKS:
        raw(f"block/{block}.png", f"block/{block}.png")
    for particle in PARTICLES:
        raw(f"particle/{particle}.png", f"particle/{particle}.png")
    for jar_rel in BIG_3X:
        name = jar_rel.split("/", 1)[1]
        scaled(jar_rel, f"big/{name}", 3)

    # первый кадр полоски портала
    src = TEXTURES + PORTAL_STRIP[0]
    if src in names:
        img = Image.open(BytesIO(zf.read(src)))
        frame = img.crop((0, 0, img.width, img.width))
        save_png(frame, out_dir / PORTAL_STRIP[1])
        extracted.append(PORTAL_STRIP[1])
    else:
        missing.append(PORTAL_STRIP[0])

    # контрольные суммы
    sums = []
    for rel in sorted(extracted):
        digest = hashlib.sha256((out_dir / rel).read_bytes()).hexdigest()
        sums.append(f"{digest}  {rel}")
    (out_dir / "SHA256SUMS").write_text("\n".join(sums) + "\n", encoding="utf-8")

    print(f"extracted: {len(extracted)} files")
    if missing:
        print("MISSING (пропущено):")
        for entry in missing:
            print(f"  {entry}")
    return 0


if __name__ == "__main__":
    sys.exit(main())

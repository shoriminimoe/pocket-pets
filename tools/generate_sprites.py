#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.11"
# dependencies = ["Pillow>=10"]
# ///
"""Generate decor PNGs (poop, room background, food bowl).

Deterministic: re-running yields identical bytes. Outputs to
app/src/main/res/drawable-nodpi/. Cat sprites are sourced separately
by tools/fetch_cat_sprites.py.
"""
from __future__ import annotations
from PIL import Image, ImageDraw
from pathlib import Path

OUT = Path(__file__).parent.parent / "app" / "src" / "main" / "res" / "drawable-nodpi"
OUT.mkdir(parents=True, exist_ok=True)


def blank(w=64, h=64):
    return Image.new("RGBA", (w, h), (0, 0, 0, 0))


def rect(d, x, y, w, h, color):
    if w <= 0 or h <= 0:
        return
    d.rectangle([x, y, x + w - 1, y + h - 1], fill=color)


def px(d, x, y, color):
    d.point((x, y), fill=color)


def render_poop():
    img = blank(32, 32)
    d = ImageDraw.Draw(img)
    base = (90, 60, 30, 255)
    mid = (130, 90, 50, 255)
    hi = (170, 130, 80, 255)
    rect(d, 8, 22, 16, 4, base)
    rect(d, 10, 18, 12, 4, mid)
    rect(d, 12, 14, 8, 4, mid)
    rect(d, 14, 11, 4, 3, hi)
    px(d, 13, 18, hi)
    return img


def render_room_bg():
    img = Image.new("RGBA", (240, 320), (233, 201, 182, 255))
    d = ImageDraw.Draw(img)
    for y in range(0, 240, 16):
        for x in range(0, 240, 16):
            px(d, x + 8, y + 8, (210, 175, 155, 255))
    rect(d, 0, 240, 240, 80, (139, 90, 60, 255))
    for y in range(248, 320, 8):
        rect(d, 0, y, 240, 1, (110, 70, 45, 255))
    for x in range(0, 240, 32):
        rect(d, x, 240, 1, 80, (110, 70, 45, 255))
    rect(d, 0, 236, 240, 4, (90, 55, 35, 255))
    return img


def render_bowl():
    img = blank(32, 16)
    d = ImageDraw.Draw(img)
    rect(d, 4, 6, 24, 8, (160, 160, 170, 255))
    rect(d, 4, 6, 24, 2, (200, 200, 215, 255))
    rect(d, 6, 8, 20, 4, (110, 110, 120, 255))
    return img


def render_food():
    img = blank(48, 48)
    d = ImageDraw.Draw(img)
    sack = (140, 100, 60, 255)
    sack_dark = (100, 70, 35, 255)
    sack_hi = (170, 130, 90, 255)
    label = (220, 220, 215, 255)
    fish = (240, 130, 60, 255)
    fish_dark = (180, 80, 30, 255)
    rect(d, 12, 14, 24, 30, sack)
    rect(d, 12, 14, 1, 30, sack_dark)
    rect(d, 35, 14, 1, 30, sack_dark)
    rect(d, 14, 16, 20, 2, sack_hi)
    rect(d, 14, 8, 20, 6, sack_dark)
    rect(d, 18, 10, 12, 4, sack)
    rect(d, 16, 22, 16, 12, label)
    rect(d, 19, 25, 10, 6, fish)
    rect(d, 19, 25, 10, 1, fish_dark)
    rect(d, 28, 26, 1, 4, fish_dark)
    return img


def render_scoop():
    img = blank(48, 48)
    d = ImageDraw.Draw(img)
    handle = (90, 60, 40, 255)
    handle_hi = (140, 100, 70, 255)
    metal = (200, 200, 210, 255)
    metal_dark = (130, 130, 140, 255)
    rect(d, 6, 6, 18, 4, handle)
    rect(d, 6, 6, 18, 1, handle_hi)
    rect(d, 22, 10, 4, 18, metal_dark)
    rect(d, 18, 26, 22, 14, metal)
    rect(d, 18, 26, 22, 1, metal_dark)
    rect(d, 18, 39, 22, 1, metal_dark)
    for x in range(20, 40, 4):
        rect(d, x, 30, 1, 8, metal_dark)
    return img


def render_toy():
    img = blank(48, 48)
    d = ImageDraw.Draw(img)
    yarn = (220, 80, 100, 255)
    yarn_dark = (160, 50, 70, 255)
    yarn_hi = (245, 130, 150, 255)
    rect(d, 14, 14, 20, 20, yarn)
    rect(d, 14, 14, 20, 2, yarn_hi)
    rect(d, 14, 32, 20, 2, yarn_dark)
    rect(d, 14, 14, 2, 20, yarn_hi)
    rect(d, 32, 14, 2, 20, yarn_dark)
    for offset in range(-6, 8, 4):
        rect(d, 14 + offset + 6, 16, 2, 16, yarn_dark)
    rect(d, 32, 18, 12, 1, yarn)
    rect(d, 36, 22, 8, 1, yarn)
    return img


def render_bowl_full():
    img = blank(32, 16)
    d = ImageDraw.Draw(img)
    rect(d, 4, 6, 24, 8, (160, 160, 170, 255))
    rect(d, 4, 6, 24, 2, (200, 200, 215, 255))
    rect(d, 6, 8, 20, 4, (110, 110, 120, 255))
    kibble = (130, 90, 50, 255)
    kibble_hi = (170, 130, 80, 255)
    rect(d, 7, 5, 18, 3, kibble)
    rect(d, 9, 4, 14, 2, kibble)
    rect(d, 11, 3, 10, 2, kibble_hi)
    rect(d, 14, 2, 4, 2, kibble)
    return img


def main():
    render_poop().save(OUT / "poop.png")
    render_room_bg().save(OUT / "room_bg.png")
    render_bowl().save(OUT / "bowl.png")
    render_bowl_full().save(OUT / "bowl_full.png")
    render_food().save(OUT / "food.png")
    render_scoop().save(OUT / "scoop.png")
    render_toy().save(OUT / "toy.png")
    print(f"Wrote decor sprites to {OUT}")


if __name__ == "__main__":
    main()

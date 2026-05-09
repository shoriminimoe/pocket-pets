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


def main():
    render_poop().save(OUT / "poop.png")
    render_room_bg().save(OUT / "room_bg.png")
    render_bowl().save(OUT / "bowl.png")
    print(f"Wrote decor sprites to {OUT}")


if __name__ == "__main__":
    main()

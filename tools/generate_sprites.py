#!/usr/bin/env python3
"""Generate 16-bit-style cat sprites and decor PNGs.

Deterministic: re-running yields identical bytes. Outputs to
app/src/main/res/drawable-nodpi/. Requires Pillow.

Style: 64x64 sprite for pets, ~24-color palette per pet, integer-scale-ready
(no anti-aliasing). Uses simple shapes assembled from solid pixel rects.
This is placeholder-quality but functional 16-bit-style art.
"""
from __future__ import annotations
from PIL import Image, ImageDraw
from pathlib import Path

OUT = Path(__file__).parent.parent / "app" / "src" / "main" / "res" / "drawable-nodpi"
OUT.mkdir(parents=True, exist_ok=True)

# Cat palette (orange tabby): outline, dark fur, mid fur, light fur,
# belly, nose, eye, eye-shine, mouth, blush, etc.
PAL_CAT = {
    "outline": (40, 22, 14, 255),
    "dark":    (180, 100, 50, 255),
    "mid":     (220, 140, 80, 255),
    "light":   (245, 200, 140, 255),
    "belly":   (255, 235, 205, 255),
    "nose":    (220, 110, 130, 255),
    "eye":     (35, 30, 40, 255),
    "shine":   (255, 255, 255, 255),
    "mouth":   (90, 50, 35, 255),
    "blush":   (255, 170, 170, 200),
    "tongue":  (240, 130, 150, 255),
    "stripe":  (140, 80, 40, 255),
    "z":       (90, 90, 110, 255),
    "tear":    (140, 200, 240, 255),
    "tear_d":  (90, 150, 200, 255),
}

def blank(w=64, h=64):
    return Image.new("RGBA", (w, h), (0, 0, 0, 0))

def rect(d, x, y, w, h, color):
    if w <= 0 or h <= 0:
        return
    d.rectangle([x, y, x + w - 1, y + h - 1], fill=color)

def px(d, x, y, color):
    d.point((x, y), fill=color)

def draw_cat_body(d, scale, mood, frame, palette=PAL_CAT):
    """Draw a cat centered in a 64x64 image."""
    cx = 32
    base_y = 56
    body_w = max(12, int(36 * scale))
    body_h = max(10, int(28 * scale))
    head_r = max(6, int(13 * scale))
    bx = cx - body_w // 2
    by = base_y - body_h
    hx = cx - head_r
    hy = by - head_r * 2 + 4

    bounce = 0
    if mood == "happy":
        bounce = [0, -2, -3, -1][min(frame, 3)]
    by += bounce
    hy += bounce

    # shadow
    rect(d, cx - body_w // 2 + 2, base_y, body_w - 4, 2, (0, 0, 0, 60))

    # body
    rect(d, bx, by, body_w, body_h, palette["mid"])
    # rounded corners (clear)
    rect(d, bx, by, 2, 2, (0, 0, 0, 0))
    rect(d, bx + body_w - 2, by, 2, 2, (0, 0, 0, 0))
    rect(d, bx, by + body_h - 2, 2, 2, (0, 0, 0, 0))
    rect(d, bx + body_w - 2, by + body_h - 2, 2, 2, (0, 0, 0, 0))

    # belly highlight
    rect(d, bx + 4, by + body_h // 2, body_w - 8, body_h // 2 - 2, palette["belly"])
    # back stripes
    for i in range(0, max(1, body_w - 8), 6):
        rect(d, bx + 4 + i, by + 2, 3, 2, palette["stripe"])

    # legs
    rect(d, bx + 2, base_y - 4, 4, 4, palette["dark"])
    rect(d, bx + body_w - 6, base_y - 4, 4, 4, palette["dark"])

    # tail
    tail_x = bx + body_w
    if mood == "happy":
        rect(d, tail_x, by + 4, 2, body_h - 8, palette["mid"])
        rect(d, tail_x + 2, by + 4, 2, 4, palette["mid"])
    else:
        offset = [0, 0, 1, 0][frame % 4] if mood == "idle" else 0
        rect(d, tail_x, by + 6 + offset, 2, body_h - 10, palette["mid"])

    # head
    rect(d, hx, hy, head_r * 2, head_r * 2, palette["mid"])
    # ears
    rect(d, hx, hy - 3, 4, 4, palette["mid"])
    rect(d, hx + 2, hy - 5, 2, 2, palette["mid"])
    rect(d, hx + head_r * 2 - 4, hy - 3, 4, 4, palette["mid"])
    rect(d, hx + head_r * 2 - 4, hy - 5, 2, 2, palette["mid"])
    # inner ears
    rect(d, hx + 1, hy - 1, 2, 2, palette["nose"])
    rect(d, hx + head_r * 2 - 3, hy - 1, 2, 2, palette["nose"])

    # eyes
    eye_y = hy + head_r - 2
    left_eye_x = hx + 4
    right_eye_x = hx + head_r * 2 - 6
    if mood == "sleep":
        rect(d, left_eye_x, eye_y + 1, 3, 1, palette["outline"])
        rect(d, right_eye_x, eye_y + 1, 3, 1, palette["outline"])
    elif mood == "idle" and frame == 2:
        rect(d, left_eye_x, eye_y + 1, 3, 1, palette["outline"])
        rect(d, right_eye_x, eye_y + 1, 3, 1, palette["outline"])
    else:
        rect(d, left_eye_x, eye_y, 3, 3, palette["eye"])
        rect(d, right_eye_x, eye_y, 3, 3, palette["eye"])
        px(d, left_eye_x + 2, eye_y, palette["shine"])
        px(d, right_eye_x + 2, eye_y, palette["shine"])

    # nose + mouth
    nose_x = hx + head_r - 1
    nose_y = eye_y + 4
    rect(d, nose_x, nose_y, 2, 1, palette["nose"])
    if mood == "happy":
        rect(d, nose_x - 2, nose_y + 2, 2, 1, palette["mouth"])
        rect(d, nose_x + 2, nose_y + 2, 2, 1, palette["mouth"])
        rect(d, nose_x, nose_y + 3, 2, 1, palette["mouth"])
    elif mood == "sad":
        rect(d, nose_x - 2, nose_y + 3, 2, 1, palette["mouth"])
        rect(d, nose_x + 2, nose_y + 3, 2, 1, palette["mouth"])
        rect(d, nose_x, nose_y + 2, 2, 1, palette["mouth"])
        rect(d, left_eye_x + 1, eye_y + 4, 1, 2, palette["tear"])
        px(d, left_eye_x + 1, eye_y + 6, palette["tear_d"])
    elif mood == "hungry":
        rect(d, nose_x, nose_y + 2, 2, 2, palette["mouth"])
        px(d, nose_x, nose_y + 3, palette["tongue"])
    elif mood == "eat":
        if frame % 2 == 0:
            rect(d, nose_x, nose_y + 2, 2, 2, palette["mouth"])
        else:
            rect(d, nose_x, nose_y + 2, 2, 1, palette["mouth"])
            px(d, nose_x, nose_y + 3, palette["tongue"])
    elif mood == "dirty":
        rect(d, nose_x, nose_y + 2, 1, 1, palette["mouth"])
        rect(d, nose_x + 1, nose_y + 3, 1, 1, palette["mouth"])
        for i, x in enumerate([cx - 4, cx - 2, cx, cx + 2, cx + 4]):
            rect(d, x, hy - 8 + (i % 2), 1, 1, palette["outline"])
    else:  # idle
        rect(d, nose_x, nose_y + 2, 2, 1, palette["mouth"])

    if mood == "happy":
        rect(d, hx + 2, eye_y + 3, 2, 1, palette["blush"])
        rect(d, hx + head_r * 2 - 4, eye_y + 3, 2, 1, palette["blush"])

    if mood == "sleep":
        z_x = hx + head_r * 2 + 2
        z_y_offsets = [0, -2, -4, -3]
        zy = hy - 6 + z_y_offsets[frame % 4]
        for cy in [zy, zy + 1, zy + 2]:
            rect(d, z_x, cy, 4, 1, palette["z"])
        rect(d, z_x + 3, zy + 1, 1, 1, palette["z"])
        rect(d, z_x + 0, zy + 1, 1, 1, palette["z"])

def render_pet_sheet(stage, mood):
    scale = {"baby": 0.55, "juvenile": 0.75, "adult": 1.0}[stage]
    frames = {
        "idle": 4, "eat": 4, "happy": 3, "sleep": 4,
        "hungry": 1, "dirty": 1, "sad": 1,
    }[mood]
    img = Image.new("RGBA", (64 * frames, 64), (0, 0, 0, 0))
    for f in range(frames):
        sub = blank(64, 64)
        d = ImageDraw.Draw(sub)
        draw_cat_body(d, scale, mood, f)
        img.paste(sub, (f * 64, 0))
    return img

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
    stages = ["baby", "juvenile", "adult"]
    moods = ["idle", "eat", "happy", "sleep", "hungry", "dirty", "sad"]
    for stage in stages:
        for mood in moods:
            sheet = render_pet_sheet(stage, mood)
            sheet.save(OUT / f"cat_{stage}_{mood}.png")
    render_poop().save(OUT / "poop.png")
    render_room_bg().save(OUT / "room_bg.png")
    render_bowl().save(OUT / "bowl.png")
    print(f"Wrote sprites to {OUT}")

if __name__ == "__main__":
    main()

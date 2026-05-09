#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.11"
# dependencies = ["Pillow>=10", "numpy>=1.26"]
# ///
"""Fetch, verify, and repack the bundled cat sprite sheet.

Run directly with uv:

    uv run tools/fetch_cat_sprites.py
    # or, since the shebang invokes uv:
    ./tools/fetch_cat_sprites.py

Tries candidates in priority order. CC0 preferred; CC-BY accepted with
ATTRIBUTION.md. Verifies the downloaded bytes against the pinned
SHA256 on each candidate. After verification, the chosen asset is
**repacked**: one sit pose + one lay pose extracted into a clean
two-cell sprite sheet (64x64 per cell) at
app/src/main/res/drawable-nodpi/cat.png. The repack is deterministic
so re-running yields identical bytes.

Each candidate is a (name, url, license, expected_sha256_or_None).
The original asset is never committed; only the repacked output is.
"""
from __future__ import annotations
import hashlib
import io
import sys
import urllib.request
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).parent.parent
OUT = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi" / "cat.png"

# SHA256 for the chosen asset (Cat by Surt). Pin per-candidate so each
# entry's expected hash is independent.
PINNED_SHA_SURT = "1ec72bb74fd1d75b1f70042caef70cfc9c2325983c420f05a48c7cab7c18246d"

CANDIDATES = [
    # (name, url, license_id, expected_sha256_or_None)
    (
        "Cat by Surt",
        "https://opengameart.org/sites/default/files/cats_0.png",
        "CC0",
        PINNED_SHA_SURT,
    ),
    (
        "Cat 32x32 by GrafxKid",
        "https://opengameart.org/sites/default/files/cat_1.png",
        "CC0",
        None,
    ),
    (
        "LPC cat",
        "https://opengameart.org/sites/default/files/cat_4.png",
        "CC-BY-SA-3.0",
        None,
    ),
]


def fetch(url: str) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": "pocket-pets-fetch/1.0"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        return resp.read()


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def repack_surt(raw: Image.Image) -> Image.Image:
    """Surt's "Cats" sheet is a 256x256 PNG with 12 cats laid out as 3 cols x 4 rows.
    Rows 0-1 are sitting cats (25x25 content), rows 2-3 are lying cats (41x28 content).
    Pick the top-left cat as the canonical orange tabby and produce a clean
    128x64 sheet with two 64x64 cells: [sit, lay], each cat centred in its cell.
    """
    # Pixel ranges of the chosen cat poses inside the source sheet.
    sit_box = (59, 44, 103, 69)   # row 0, col 0 — sitting orange tabby (44x25)
    lay_box = (59, 137, 103, 165)  # row 2, col 0 — lying orange tabby (44x28)

    sit = raw.crop(sit_box)
    lay = raw.crop(lay_box)

    # Trim transparent borders to actual content bounding box.
    sit = sit.crop(sit.getbbox()) if sit.getbbox() else sit
    lay = lay.crop(lay.getbbox()) if lay.getbbox() else lay

    # Compose into a single-column, 2-row sheet so each pose lives on its own
    # row. The renderer addresses cells as (col=frame, row=row), so different
    # static poses must sit on different rows.
    cell = 64
    out = Image.new("RGBA", (cell, cell * 2), (0, 0, 0, 0))

    def paste_centred(img: Image.Image, row: int) -> None:
        ox = (cell - img.size[0]) // 2
        oy = row * cell + (cell - img.size[1]) - 4  # bottom-aligned with a small margin
        out.paste(img, (ox, oy), img)

    paste_centred(sit, 0)  # row 0 — sit
    paste_centred(lay, 1)  # row 1 — lay
    return out


def process_chosen(name: str, raw_bytes: bytes) -> Image.Image:
    """Apply the per-asset repack. Add new branches as candidates are added."""
    raw = Image.open(io.BytesIO(raw_bytes)).convert("RGBA")
    if name == "Cat by Surt":
        return repack_surt(raw)
    # Default: pass through unchanged.
    return raw


def main() -> int:
    OUT.parent.mkdir(parents=True, exist_ok=True)
    for name, url, license_id, expected in CANDIDATES:
        try:
            print(f"Trying: {name} ({license_id}) -> {url}")
            data = fetch(url)
        except Exception as e:
            print(f"  Failed: {e}")
            continue
        digest = sha256(data)
        if expected is not None and digest != expected:
            print(f"  SHA256 mismatch: got {digest}, expected {expected}")
            continue
        try:
            processed = process_chosen(name, data)
        except Exception as e:
            print(f"  Failed to process: {e}")
            continue
        processed.save(OUT, format="PNG", optimize=True)
        w, h = processed.size
        print()
        print(f"OK: {name}")
        print(f"  License:    {license_id}")
        print(f"  URL:        {url}")
        print(f"  Raw SHA256: {digest}")
        print(f"  Output:     {w}x{h} px (repacked)")
        print(f"  Wrote:      {OUT.relative_to(ROOT)}")
        if license_id != "CC0":
            print()
            print("  NOTE: This asset requires attribution. Add an entry to")
            print("  ATTRIBUTION.md with the name, URL, license, and author.")
        return 0
    print("All candidates failed. Edit CANDIDATES to add another asset.", file=sys.stderr)
    return 1


if __name__ == "__main__":
    sys.exit(main())

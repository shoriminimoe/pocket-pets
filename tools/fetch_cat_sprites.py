#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.11"
# dependencies = ["Pillow>=10"]
# ///
"""Fetch and verify the bundled cat sprite sheet.

Run directly with uv:

    uv run tools/fetch_cat_sprites.py
    # or, since the shebang invokes uv:
    ./tools/fetch_cat_sprites.py

Tries candidates in priority order. CC0 preferred; CC-BY accepted with
ATTRIBUTION.md. Writes the chosen PNG to
app/src/main/res/drawable-nodpi/cat.png and prints the asset's
dimensions so the implementer can wire CatAnimations.

Each candidate is a (name, url, license, expected_sha256_or_None).
Set expected_sha256 to None for the first run, then re-run after
inspecting the file to pin the hash.
"""
from __future__ import annotations
import hashlib
import sys
import urllib.request
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).parent.parent
OUT = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi" / "cat.png"

CANDIDATES = [
    # (name, url, license_id, expected_sha256_or_None)
    (
        "Cat 32x32 by GrafxKid",
        "https://opengameart.org/sites/default/files/cat_1.png",
        "CC0",
        None,
    ),
    (
        "Cat by Surt",
        "https://opengameart.org/sites/default/files/cats_0.png",
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
        OUT.write_bytes(data)
        try:
            img = Image.open(OUT)
            w, h = img.size
        except Exception as e:
            print(f"  Not a valid image: {e}")
            OUT.unlink(missing_ok=True)
            continue
        print()
        print(f"OK: {name}")
        print(f"  License: {license_id}")
        print(f"  URL:     {url}")
        print(f"  SHA256:  {digest}")
        print(f"  Size:    {w}x{h} px")
        print(f"  Wrote:   {OUT.relative_to(ROOT)}")
        if license_id != "CC0":
            print()
            print("  NOTE: This asset requires attribution. Add an entry to")
            print("  ATTRIBUTION.md with the name, URL, license, and author.")
        return 0
    print("All candidates failed. Edit CANDIDATES to add another asset.", file=sys.stderr)
    return 1


if __name__ == "__main__":
    sys.exit(main())

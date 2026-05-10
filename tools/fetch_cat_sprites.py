#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.11"
# dependencies = ["Pillow>=10"]
# ///
"""Verify the bundled cat sprite sheet.

Run directly with uv:

    uv run tools/fetch_cat_sprites.py
    # or, since the shebang invokes uv:
    ./tools/fetch_cat_sprites.py

The cat sheet at app/src/main/res/drawable-nodpi/cat.png is the canonical
LPC-style layout the Compose renderer addresses by (row, col):

    row 0..3: walk South / North / West / East (4 frames each, 64x64)
    row 4 col 0: sit pose
    row 5 col 0: lay pose

The sheet is an AI-generated, original asset bundled in-tree — there is
no upstream URL to fetch from. This script is a supply-chain check: it
opens the bundled file, asserts the dimensions, mode and SHA256 match
the pinned values, and exits non-zero if anything is off.

To replace the sheet (e.g. regenerate the cat with new colors or a
better walk cycle):
  1. Drop the new 256x384 RGBA PNG at app/src/main/res/drawable-nodpi/cat.png.
  2. Run this script; it will print the new SHA on mismatch.
  3. Update PINNED_SHA below to the printed digest, commit both together.
"""
from __future__ import annotations

import hashlib
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).parent.parent
CAT_PNG = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi" / "cat.png"

EXPECTED_WIDTH = 256
EXPECTED_HEIGHT = 384
EXPECTED_MODE = "RGBA"
PINNED_SHA = "8634a16bb74ab2b3d80186f0b14e06552bacb71e9f69a9b41474fac2111d7cb5"


def sha256_of(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


def main() -> int:
    if not CAT_PNG.exists():
        print(f"Missing: {CAT_PNG.relative_to(ROOT)}", file=sys.stderr)
        return 1

    digest = sha256_of(CAT_PNG)
    with Image.open(CAT_PNG) as img:
        width, height = img.size
        mode = img.mode

    problems: list[str] = []
    if (width, height) != (EXPECTED_WIDTH, EXPECTED_HEIGHT):
        problems.append(
            f"  dimensions: got {width}x{height}, expected {EXPECTED_WIDTH}x{EXPECTED_HEIGHT}"
        )
    if mode != EXPECTED_MODE:
        problems.append(f"  mode: got {mode}, expected {EXPECTED_MODE}")
    if digest != PINNED_SHA:
        problems.append(f"  sha256: got {digest}, expected {PINNED_SHA}")

    if problems:
        print(f"cat.png does not match pinned values:", file=sys.stderr)
        for p in problems:
            print(p, file=sys.stderr)
        print(
            "\nIf you intentionally changed the sheet, update PINNED_SHA in this "
            "script to the digest above and commit both together.",
            file=sys.stderr,
        )
        return 2

    print(f"OK: {CAT_PNG.relative_to(ROOT)}")
    print(f"  {width}x{height} {mode}")
    print(f"  sha256: {digest}")
    return 0


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""Zip BP/ and RP/ into dist/HorrorStalkers.mcaddon (and an identical .zip)."""
import shutil
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DIST = ROOT / "dist"
NAME = "HorrorStalkers"


def main():
    DIST.mkdir(exist_ok=True)
    addon = DIST / f"{NAME}.mcaddon"
    with zipfile.ZipFile(addon, "w", zipfile.ZIP_DEFLATED) as zf:
        for pack in ("BP", "RP"):
            for path in sorted((ROOT / pack).rglob("*")):
                if path.is_file():
                    zf.write(path, f"{NAME}_{pack}/{path.relative_to(ROOT / pack).as_posix()}")
    shutil.copyfile(addon, DIST / f"{NAME}.zip")
    print("built", addon)


if __name__ == "__main__":
    main()

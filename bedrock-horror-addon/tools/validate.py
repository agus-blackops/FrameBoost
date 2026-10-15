#!/usr/bin/env python3
"""Static checks for the addon: JSON validity, manifests and BP<->RP references."""
import json
import re
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BP, RP = ROOT / "BP", ROOT / "RP"
UUID = re.compile(r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
errors = []


def check(cond, msg):
    if not cond:
        errors.append(msg)


def load(path):
    try:
        return json.loads(path.read_text())
    except Exception as exc:  # noqa: BLE001
        errors.append(f"{path.relative_to(ROOT)}: invalid JSON ({exc})")
        return None


def png_size(path):
    data = path.read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        return None
    return int.from_bytes(data[16:20], "big"), int.from_bytes(data[20:24], "big")


def main():
    for path in list(BP.rglob("*.json")) + list(RP.rglob("*.json")):
        load(path)

    bpm, rpm = load(BP / "manifest.json"), load(RP / "manifest.json")
    uuids = []
    for m in (bpm, rpm):
        if not m:
            continue
        uuids.append(m["header"]["uuid"])
        uuids += [mod["uuid"] for mod in m["modules"]]
    check(len(uuids) == len(set(uuids)), "duplicate UUIDs across manifests")
    check(all(UUID.match(u) for u in uuids), "malformed UUID in a manifest")
    if bpm and rpm:
        check(any(d.get("uuid") == rpm["header"]["uuid"] for d in bpm["dependencies"]),
              "BP does not depend on RP")
        check(any(d.get("uuid") == bpm["header"]["uuid"] for d in rpm["dependencies"]),
              "RP does not depend on BP")
        for mod in bpm["modules"]:
            if mod["type"] == "script":
                check((BP / mod["entry"]).is_file(), f"script entry {mod['entry']} missing")

    geometries, controllers = set(), set()
    for path in (RP / "models" / "entity").glob("*.geo.json"):
        g = load(path)
        if g:
            for geo in g["minecraft:geometry"]:
                geometries.add(geo["description"]["identifier"])
                size = geo["description"]["texture_width"]
                for bone in geo["bones"]:
                    for c in bone.get("cubes", []):
                        w, h, d = c["size"]
                        u, v = c["uv"]
                        check(u + 2 * (w + d) <= size and v + h + d <= size,
                              f"{path.name}: UV of bone {bone['name']} outside atlas")
    for path in (RP / "render_controllers").glob("*.json"):
        r = load(path)
        if r:
            controllers |= set(r["render_controllers"])
    animations = set()
    for path in (RP / "animations").glob("*.json"):
        a = load(path)
        if a:
            animations |= set(a["animations"])

    lang = (RP / "texts" / "en_US.lang").read_text()
    bp_ids = set()
    for path in sorted((BP / "entities").glob("*.json")):
        e = load(path)
        if e:
            ident = e["minecraft:entity"]["description"]["identifier"]
            bp_ids.add(ident)
            check(f"entity.{ident}.name=" in lang, f"{ident}: missing lang name")
            check((BP / "spawn_rules" / path.name).is_file(), f"{ident}: missing spawn rules")
    client_ids = set()
    for path in sorted((RP / "entity").glob("*.entity.json")):
        c = load(path)
        if not c:
            continue
        d = c["minecraft:client_entity"]["description"]
        client_ids.add(d["identifier"])
        check(d["geometry"]["default"] in geometries, f"{d['identifier']}: unknown geometry")
        for rc in d["render_controllers"]:
            check(rc in controllers, f"{d['identifier']}: unknown render controller {rc}")
        for anim in d["animations"].values():
            check(anim in animations, f"{d['identifier']}: unknown animation {anim}")
        tex = RP / (d["textures"]["default"] + ".png")
        check(tex.is_file(), f"{d['identifier']}: texture {tex.name} missing")
        if tex.is_file():
            check(png_size(tex) == (64, 64), f"{tex.name}: not a 64x64 PNG")
    check(bp_ids == client_ids, f"BP/RP entity mismatch: {bp_ids ^ client_ids}")

    for icon in (BP / "pack_icon.png", RP / "pack_icon.png"):
        check(icon.is_file(), f"{icon.relative_to(ROOT)} missing")

    addon = ROOT / "dist" / "HorrorStalkers.mcaddon"
    if addon.is_file():
        names = zipfile.ZipFile(addon).namelist()
        for pack in ("BP", "RP"):
            check(f"HorrorStalkers_{pack}/manifest.json" in names, f"{pack} manifest not in mcaddon")

    if errors:
        print("\n".join("FAIL: " + e for e in errors))
        sys.exit(1)
    print(f"OK: {len(bp_ids)} creatures, all references resolve")


if __name__ == "__main__":
    main()

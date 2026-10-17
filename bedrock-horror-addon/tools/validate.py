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
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:  # noqa: BLE001
        errors.append(f"{path.relative_to(ROOT)}: invalid JSON ({exc})")
        return None


def png_size(path):
    data = path.read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        return None
    return int.from_bytes(data[16:20], "big"), int.from_bytes(data[20:24], "big")


def check_manifests():
    bpm, rpm = load(BP / "manifest.json"), load(RP / "manifest.json")
    if not (bpm and rpm):
        return
    uuids = []
    for m in (bpm, rpm):
        uuids.append(m["header"]["uuid"])
        uuids += [mod["uuid"] for mod in m["modules"]]
    check(len(uuids) == len(set(uuids)), "duplicate UUIDs across manifests")
    check(all(UUID.match(u) for u in uuids), "malformed UUID in a manifest")
    check(any(d.get("uuid") == rpm["header"]["uuid"] and d["version"] == rpm["header"]["version"]
              for d in bpm["dependencies"]), "BP does not depend on the current RP version")
    check(any(d.get("uuid") == bpm["header"]["uuid"] and d["version"] == bpm["header"]["version"]
              for d in rpm["dependencies"]), "RP does not depend on the current BP version")
    for mod in bpm["modules"]:
        if mod["type"] == "script":
            check((BP / mod["entry"]).is_file(), f"script entry {mod['entry']} missing")


def check_scripts(lang_keys):
    scripts = BP / "scripts"
    for js in scripts.glob("*.js"):
        text = js.read_text()
        for rel in re.findall(r'from\s+"(\.[^"]+)"', text):
            check((scripts / rel).resolve().is_file(), f"{js.name}: import {rel} not found")
        for key in re.findall(r'translate:\s*"([^"]+)"', text):
            check(key in lang_keys, f"{js.name}: lang key {key} missing")
        for fog in re.findall(r"push (horror:[a-z_]+)", text):
            check(fog in fog_ids(), f"{js.name}: fog {fog} not defined")


def fog_ids():
    ids = set()
    for path in (RP / "fogs").glob("*.json"):
        f = load(path)
        if f:
            ids.add(f["minecraft:fog_settings"]["description"]["identifier"])
    return ids


def lang_tables():
    tables = {}
    for lang in load(RP / "texts" / "languages.json") or []:
        path = RP / "texts" / f"{lang}.lang"
        check(path.is_file(), f"{lang}.lang missing")
        if path.is_file():
            tables[lang] = dict(line.split("=", 1) for line in path.read_text(encoding="utf-8").splitlines() if "=" in line)
    keysets = [set(t) for t in tables.values()]
    check(all(k == keysets[0] for k in keysets), "language files define different keys")
    return tables


def main():
    for path in list(BP.rglob("*.json")) + list(RP.rglob("*.json")):
        load(path)
    check_manifests()

    geometries, controllers, animations = {}, set(), set()
    for path in (RP / "models" / "entity").glob("*.geo.json"):
        g = load(path)
        for geo in (g or {}).get("minecraft:geometry", []):
            desc = geo["description"]
            size = (desc["texture_width"], desc["texture_height"])
            geometries[desc["identifier"]] = size
            for bone in geo["bones"]:
                for c in bone.get("cubes", []):
                    w, h, d = c["size"]
                    u, v = c["uv"]
                    check(u + 2 * (w + d) <= size[0] and v + h + d <= size[1],
                          f"{path.name}: UV of bone {bone['name']} outside atlas")
    for path in (RP / "render_controllers").glob("*.json"):
        controllers |= set((load(path) or {}).get("render_controllers", {}))
    for path in (RP / "animations").glob("*.json"):
        animations |= set((load(path) or {}).get("animations", {}))

    tables = lang_tables()
    en = tables.get("en_US", {})
    check_scripts(set(en))

    bp_ids = set()
    for path in sorted((BP / "entities").glob("*.json")):
        e = load(path)
        if not e:
            continue
        ent = e["minecraft:entity"]
        ident = ent["description"]["identifier"]
        bp_ids.add(ident)
        check(f"entity.{ident}.name" in en, f"{ident}: missing lang name")
        loot = ent["components"].get("minecraft:loot")
        if loot:
            check((BP / loot["table"]).is_file(), f"{ident}: loot table {loot['table']} missing")
        rules = BP / "spawn_rules" / path.name
        if rules.is_file():
            r = load(rules)
            check(r and r["minecraft:spawn_rules"]["description"]["identifier"] == ident,
                  f"{rules.name}: identifier mismatch")
        for event in ent.get("events", {}).values():
            for group in event.get("add", {}).get("component_groups", []):
                check(group in ent.get("component_groups", {}), f"{ident}: unknown component group {group}")

    client_ids = set()
    for path in sorted((RP / "entity").glob("*.entity.json")):
        c = load(path)
        if not c:
            continue
        d = c["minecraft:client_entity"]["description"]
        ident = d["identifier"]
        client_ids.add(ident)
        geo = d["geometry"]["default"]
        check(geo in geometries, f"{ident}: unknown geometry")
        for rc in d["render_controllers"]:
            check(rc in controllers, f"{ident}: unknown render controller {rc}")
        for anim in d["animations"].values():
            check(anim in animations, f"{ident}: unknown animation {anim}")
        for name in d["scripts"]["animate"]:
            check(name in d["animations"], f"{ident}: animates undeclared {name}")
        tex = RP / (d["textures"]["default"] + ".png")
        check(tex.is_file(), f"{ident}: texture {tex.name} missing")
        if tex.is_file() and geo in geometries:
            check(png_size(tex) == geometries[geo], f"{tex.name}: size does not match its geometry")
    check(bp_ids == client_ids, f"BP/RP entity mismatch: {bp_ids ^ client_ids}")

    snd = load(RP / "sounds.json") or {}
    for ident in snd.get("entity_sounds", {}).get("entities", {}):
        check(ident in bp_ids, f"sounds.json: unknown entity {ident}")

    for icon in (BP / "pack_icon.png", RP / "pack_icon.png"):
        check(icon.is_file(), f"{icon.relative_to(ROOT)} missing")

    addon = ROOT / "dist" / "HorrorStalkers.mcaddon"
    if addon.is_file():
        names = set(zipfile.ZipFile(addon).namelist())
        for pack, root in (("BP", BP), ("RP", RP)):
            for f in root.rglob("*"):
                if f.is_file():
                    check(f"HorrorStalkers_{pack}/{f.relative_to(root).as_posix()}" in names,
                          f"{pack}/{f.relative_to(root)} not in mcaddon (rebuild)")

    if errors:
        print("\n".join("FAIL: " + e for e in errors))
        sys.exit(1)
    print(f"OK: {len(bp_ids)} creatures, all references resolve")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""
Empaqueta una skin 64x64 como paquete de skins de Minecraft Bedrock (.mcpack).

Uso:  python3 make_bedrock_pack.py skin.png "Nombre" salida.mcpack
"""
import json
import sys
import uuid
import zipfile

png, name, out = sys.argv[1:4]
slug = "".join(c for c in name if c.isalnum()) or "skin"
manifest = {
    "format_version": 1,
    "header": {"name": name, "uuid": str(uuid.uuid4()), "version": [1, 0, 0]},
    "modules": [{"type": "skin_pack", "uuid": str(uuid.uuid4()), "version": [1, 0, 0]}],
}
skins = {
    "serialize_name": slug,
    "localization_name": slug,
    "skins": [{"localization_name": slug, "geometry": "geometry.humanoid.custom",
               "texture": "skin.png", "type": "free"}],
}
lang = f"skinpack.{slug}={name}\nskin.{slug}.{slug}={name}\n"
with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
    z.writestr("manifest.json", json.dumps(manifest, indent=2))
    z.writestr("skins.json", json.dumps(skins, indent=2))
    z.writestr("texts/en_US.lang", lang)
    z.writestr("texts/languages.json", json.dumps(["en_US"]))
    z.write(png, "skin.png")
print("ok ->", out)

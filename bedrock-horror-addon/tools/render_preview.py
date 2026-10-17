"""Preview renderer: front + side orthographic views of each creature, textured.

Usage: python3 tools/render_preview.py . docs/preview.png [bind|hunt]
"""
import json
import math
import sys
import zlib
import struct
from pathlib import Path

ADDON = Path(sys.argv[1])
OUT = Path(sys.argv[2])
POSE = sys.argv[3] if len(sys.argv) > 3 else "bind"
NAMES = ["stalker", "woodbeast", "whisperer", "watcher"]
PX = 5  # screen pixels per model pixel

# Hand-written test poses (degrees) to check joints bend the right way.
POSES = {
    "hunt": {
        "stalker": {"arm_r": [-38, 0, 6], "arm_l": [-30, 0, -6], "forearm_r": [-28, 0, 0],
                    "forearm_l": [-22, 0, 0], "jaw": [26, 0, 0], "head": [0, 0, 24],
                    "leg_r": [-30, 0, 0], "shin_r": [45, 0, 0]},
        "woodbeast": {"neck": [16, 0, 0], "jaw": [30, 0, 0], "tail1": [-22, 0, 0],
                      "arm_r": [-30, 0, 0], "forearm_r": [40, 0, 0], "leg_l": [-25, 0, 0], "shin_l": [-25, 0, 0]},
        "whisperer": {"arm_r": [-72, 0, 10], "arm_l": [-66, 0, -10], "jaw": [34, 0, 0], "head": [-8, 0, 16],
                      "strip1": [25, 0, 0], "strip2": [25, 0, 0]},
        "watcher": {"head": [0, 0, 28], "neck": [0, 0, 8]},
    }
}


def read_png(p):
    d = p.read_bytes(); i = 8; idat = b""
    while i < len(d):
        n = struct.unpack(">I", d[i:i + 4])[0]; t = d[i + 4:i + 8]
        if t == b"IHDR":
            w, h = struct.unpack(">II", d[i + 8:i + 16])
        if t == b"IDAT":
            idat += d[i + 8:i + 8 + n]
        i += 12 + n
    raw = zlib.decompress(idat); s = w * 4 + 1
    rows = []
    for r in range(h):
        line = raw[r * s + 1:(r + 1) * s]  # filter 0 only (our writer)
        rows.append([tuple(line[x * 4:x * 4 + 4]) for x in range(w)])
    return rows


def rot(p, ang, pivot):
    x, y, z = (p[i] - pivot[i] for i in range(3))
    ax, ay, az = (math.radians(a) for a in ang)
    y, z = y * math.cos(ax) + z * math.sin(ax), -y * math.sin(ax) + z * math.cos(ax)
    x, z = x * math.cos(ay) - z * math.sin(ay), x * math.sin(ay) + z * math.cos(ay)
    x, y = x * math.cos(az) - y * math.sin(az), x * math.sin(az) + y * math.cos(az)
    return (x + pivot[0], y + pivot[1], z + pivot[2])


def render(name, pose):
    geo = json.loads((ADDON / "RP/models/entity" / f"{name}.geo.json").read_text())["minecraft:geometry"][0]
    tex = read_png(ADDON / "RP/textures/entity" / f"{name}.png")
    S = len(tex[0]) // geo["description"]["texture_width"]
    bones = {b["name"]: b for b in geo["bones"]}

    def chain(bname):
        out = []
        while bname:
            b = bones[bname]
            ang = [a + o for a, o in zip(b.get("rotation", [0, 0, 0]), pose.get(bname, [0, 0, 0]))]
            out.append((ang, b["pivot"]))
            bname = b.get("parent")
        return out

    pts = []  # (world point, colour)
    for b in geo["bones"]:
        ch = chain(b["name"])
        for cb in b.get("cubes", []):
            (x0, y0, z0), (w, h, d) = cb["origin"], cb["size"]
            u, v = cb["uv"]
            x1, y1, z1 = x0 + w, y0 + h, z0 + d
            faces = {  # (uv rect, mapping texel(s,t in 0..1) -> 3D)
                "front": ((u + d, v + d, w, h), lambda s, t: (x0 + s * w, y1 - t * h, z0)),
                "back": ((u + 2 * d + w, v + d, w, h), lambda s, t: (x1 - s * w, y1 - t * h, z1)),
                "east": ((u, v + d, d, h), lambda s, t: (x0, y1 - t * h, z1 - s * d)),
                "west": ((u + d + w, v + d, d, h), lambda s, t: (x1, y1 - t * h, z0 + s * d)),
                "up": ((u + d, v, w, d), lambda s, t: (x0 + s * w, y1, z1 - t * d)),
                "down": ((u + d + w, v, w, d), lambda s, t: (x0 + s * w, y0, z0 + t * d)),
            }
            for (fu, fv, fw, fh), fn in faces.values():
                tw, th = fw * S, fh * S
                for j in range(int(th)):
                    for i in range(int(tw)):
                        r, g, bb, a = tex[int(fv * S) + j][int(fu * S) + i]
                        p = fn((i + 0.5) / tw, (j + 0.5) / th)
                        for ang, piv in ch:
                            p = rot(p, ang, piv)
                        if name == "whisperer" and a == 0:
                            continue
                        if a == 0:  # emissive eye: show bright
                            col = (min(255, r + 60), min(255, g + 60), min(255, bb + 60))
                        else:
                            k = a / 255
                            col = tuple(int(c * k + 70 * (1 - k)) for c in (r, g, bb))
                        pts.append((p, col))
    return pts


def draw(views, W, H):
    img = [[(70, 70, 76)] * W for _ in range(H)]
    for pts, ox, axis in views:
        if axis == "front":
            proj = [((p[0], p[1]), p[2], c) for p, c in pts]
        else:
            proj = [((p[2], p[1]), -p[0], c) for p, c in pts]
        proj.sort(key=lambda q: -q[1])  # far first
        for (sx, sy), _, col in proj:
            X, Y = int(ox + sx * PX), int(H - 12 - sy * PX)
            for yy in range(Y - 2, Y + 2):
                for xx in range(X - 2, X + 2):
                    if 0 <= xx < W and 0 <= yy < H:
                        img[yy][xx] = col
    return img


views, W = [], 0
for n in NAMES:
    pts = render(n, POSES.get(POSE, {}).get(n, {}))
    views.append((pts, W + 90, "front")); views.append((pts, W + 260, "side"))
    W += 360
img = draw(views, W, 360)
raw = b"".join(b"\x00" + bytes(ch for px in row for ch in (*px, 255)) for row in img)
def chunk(t, d):
    return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
OUT.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", W, 360, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(raw, 6)) + chunk(b"IEND", b""))
print("wrote", OUT)

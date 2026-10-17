"""Procedural, face-aware texture painting for box-UV models.

Textures are painted at SCALE texels per model pixel (an HD texture: the
geometry's UV space stays in model pixels, Bedrock samples the larger image).
"""
import math
import random
import struct
import zlib

SCALE = 2
MOUTH = (34, 3, 5)
BLOOD = (92, 6, 8)

# Brightness per face, as if lit from above and slightly in front.
FACE_LIGHT = {"up": 1.18, "down": 0.55, "front": 1.0, "back": 0.72, "east": 0.84, "west": 0.84}


def box_faces(u, v, w, h, d):
    """Box-UV face rectangles (x, y, width, height) in UV units."""
    return {
        "up": (u + d, v, w, d), "down": (u + d + w, v, w, d),
        "east": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "west": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
    }


# ------------------------------------------------------------------ noise
def _hash(ix, iy, seed):
    n = (ix * 374761393 + iy * 668265263 + seed * 144269504) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def vnoise(x, y, seed):
    ix, iy = math.floor(x), math.floor(y)
    fx, fy = x - ix, y - iy
    sx, sy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a, b = _hash(ix, iy, seed), _hash(ix + 1, iy, seed)
    c, d = _hash(ix, iy + 1, seed), _hash(ix + 1, iy + 1, seed)
    return a + (b - a) * sx + (c - a) * sy + (a - b - c + d) * sx * sy


def fbm(x, y, seed):
    return (0.55 * vnoise(x, y, seed) + 0.3 * vnoise(x * 2.1, y * 2.1, seed + 1)
            + 0.15 * vnoise(x * 4.3, y * 4.3, seed + 2))


def clamp(v):
    return max(0, min(255, int(v)))


def scale(col, k):
    return tuple(clamp(ch * k) for ch in col)


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


# -------------------------------------------------------------- materials
def material(mat, pal, gx, gy, lx, seed):
    """Base colour of material `mat` at global texel (gx, gy); lx is the face-local x."""
    base = pal[mat]
    n = fbm(gx / 7, gy / 7, seed)
    if mat == "skin":
        col = scale(base, 0.75 + 0.45 * n)
        if abs(fbm(gx / 11 + 40, gy / 11, seed + 9) - 0.5) < 0.02:   # veins
            col = mix(col, pal["vein"], 0.75)
        if vnoise(gx / 3, gy / 3, seed + 5) > 0.86:                    # bruises
            col = mix(col, pal["vein"], 0.3)
        return col
    if mat == "bone":
        col = scale(base, 0.82 + 0.25 * n)
        if abs(vnoise(gx / 4, gy / 4, seed + 7) - 0.5) < 0.025:      # cracks
            col = scale(col, 0.5)
        return col
    if mat == "cloth":
        fold = 0.5 + 0.5 * math.sin(lx * 0.8 + n * 5)
        return scale(base, 0.62 + 0.38 * fold + 0.2 * (n - 0.5))
    if mat == "fur":
        strand = vnoise(gx / 1.1, gy / 4.5, seed + 3)
        return scale(base, 0.6 + 0.65 * strand * (0.7 + 0.6 * n))
    if mat == "antler":
        grain = 0.5 + 0.5 * math.sin(gy * 0.9 + n * 6)
        return scale(base, 0.7 + 0.4 * grain)
    if mat == "void":
        return scale(base, 0.7 + 0.8 * n)
    raise ValueError(f"unknown material {mat}")


# --------------------------------------------------------------- features
def rect(px, x, y, w, h, col, alpha=255):
    for yy in range(y, y + h):
        for xx in range(x, x + w):
            if 0 <= yy < len(px) and 0 <= xx < len(px[0]):
                px[yy][xx] = (*col, alpha)


EYE_STYLES = {
    # core w, h; socket padding; socket colour
    "slit": ((2, 4), 1, (6, 2, 3)),
    "socket": ((2, 2), 2, (8, 5, 4)),
    "pinpoint": ((1, 1), 0, None),
    "wide": ((3, 3), 1, (0, 0, 0)),
}


def draw_eyes(px, fx, fy, fw, fh, style, eye, emissive):
    (ew, eh), pad, socket = EYE_STYLES[style]
    alpha = 0 if emissive else 255  # alpha 0 glows under entity_emissive_alpha
    cy = fy + int(fh * 0.38) - eh // 2
    for cx in (fx + round(fw * 0.3) - ew // 2, fx + round(fw * 0.7) - (ew + 1) // 2):
        if socket:
            rect(px, cx - pad, cy - pad, ew + 2 * pad, eh + 2 * pad, socket)
        rect(px, cx, cy, ew, eh, eye, alpha)
        rect(px, cx, cy, 1, 1, mix(eye, (255, 255, 255), 0.6), alpha)  # hot core
        if style == "pinpoint":  # faint halo
            for ox, oy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
                rect(px, cx + ox, cy + oy, 1, 1, scale(eye, 0.35))


def draw_teeth(px, fx, y, fw, tooth_col, downward):
    """Pointed teeth growing inward from a face edge at row y, over a dark mouth."""
    step = 1 if downward else -1
    for x in range(fx, fx + fw):
        k = (x - fx) % 4
        length = 0 if k == 3 else (3 if k == 1 else 2)
        for i in range(3):
            rect(px, x, y + step * i, 1, 1, tooth_col if i < length else MOUTH)


def draw_ribs(px, fx, fy, fw, fh):
    for ry in range(fy + 3, fy + fh - 2, 3 * SCALE):
        for rx in range(fx + max(1, fw // 7), fx + fw - max(1, fw // 7)):
            r, g, b, a = px[ry][rx]
            px[ry][rx] = (*scale((r, g, b), 0.45), a)
            r, g, b, a = px[ry - 1][rx]
            px[ry - 1][rx] = (*scale((r, g, b), 1.3), a)


def draw_blood(px, fx, fy, fw, fh, rng):
    for _ in range(max(1, fw // 3)):
        x = fx + rng.randrange(fw)
        length = rng.randint(2, max(3, int(fh * 0.7)))
        for i in range(length):
            if fy + i >= fy + fh:
                break
            r, g, b, a = px[fy + i][x]
            px[fy + i][x] = (*mix((r, g, b), BLOOD, 0.85 - 0.4 * i / length), a)


# ------------------------------------------------------------------ paint
def paint(atlas_uv, cubes, pal, material_name, seed_key):
    """Paint every cube of a model into a SCALE*atlas_uv square RGBA image."""
    size = atlas_uv * SCALE
    rng = random.Random(seed_key)
    seed = rng.randrange(1, 1 << 16)
    alpha = pal.get("alpha", 255)
    emissive = material_name == "entity_emissive_alpha"
    px = [[(0, 0, 0, 0)] * size for _ in range(size)]
    for cube in cubes:
        w, h, d = cube["size"]
        u, v = cube["uv"]
        for face, (x, y, fw, fh) in box_faces(u, v, w, h, d).items():
            x, y, fw, fh = int(x * SCALE), int(y * SCALE), fw * SCALE, fh * SCALE
            side = face not in ("up", "down")
            for ly in range(fh):
                t = ly / max(1, fh - 1)
                for lx in range(fw):
                    gx, gy = x + lx, y + ly
                    col = material(cube["mat"], pal, gx, gy, lx, seed)
                    k = FACE_LIGHT[face]
                    if side:
                        k *= 1.08 - 0.3 * t           # darker toward the ground
                    if lx in (0, fw - 1) or ly in (0, fh - 1):
                        k *= 0.8                      # crease at the edges
                    a = alpha
                    if "fade" in cube and side:
                        a = int(alpha + (cube["fade"] - alpha) * t)
                        if t > 0.8 and rng.random() < 0.4:
                            a = 0                     # tattered hem
                    px[gy][gx] = (*scale(col, k), a)
        fx, fy, fw, fh = (int(n * SCALE) for n in box_faces(u, v, w, h, d)["front"])
        if cube.get("ribs"):
            draw_ribs(px, fx, fy, fw, fh)
            for side in ("east", "west"):
                sx, sy, sw, sh = (int(n * SCALE) for n in box_faces(u, v, w, h, d)[side])
                draw_ribs(px, sx, sy, sw, sh)
        if cube.get("bloody"):
            draw_blood(px, fx, fy, fw, fh, rng)
        if cube.get("teeth"):
            draw_teeth(px, fx, fy, fw, pal["bone"], downward=True)
        if cube.get("upper_teeth"):
            draw_teeth(px, fx, fy + fh - 1, fw, pal["bone"], downward=False)
        if cube.get("eyes"):
            draw_eyes(px, fx, fy, fw, fh, cube["eyes"], pal["eye"], emissive)
    return px


def write_png(path, rows):
    height, width = len(rows), len(rows[0])
    raw = b"".join(b"\x00" + bytes(ch for px in row for ch in px) for row in rows)

    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(raw, 9))
           + chunk(b"IEND", b""))
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png)

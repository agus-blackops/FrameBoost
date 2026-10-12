"""Genera las entidades aliadas (BP + RP): definición, modelo de cubos y textura."""
import json, math, os, zlib, struct

# paleta: índice -> color. Cada cubo apunta a un "swatch" de 4x4 px de la textura (fila superior).
def box(o, s, c): return {"o": o, "s": s, "c": c}

ENT = {
 "warden_final": dict(glow=[2], w=1.8, h=3.0, vida=2048, dano=120, vel=0.32,
   pal=[(18, 36, 46), (30, 90, 100), (80, 255, 240), (200, 200, 190)],
   cubes=[box([-9, 0, -4], [7, 16, 8], 0), box([2, 0, -4], [7, 16, 8], 0),
          box([-10, 16, -6], [20, 24, 12], 0), box([-6, 22, -6.4], [12, 2, .5], 2), box([-6, 28, -6.4], [12, 2, .5], 2),
          box([-8, 40, -7], [16, 12, 14], 1), box([-5, 44, -7.4], [3, 3, .5], 2), box([2, 44, -7.4], [3, 3, .5], 2),
          box([-18, 18, -4], [8, 22, 8], 1), box([10, 18, -4], [8, 22, 8], 1),
          box([-9, 52, -2], [4, 5, 4], 3), box([5, 52, -2], [4, 5, 4], 3)]),
 "clawd": dict(fur=[0, 3], w=.9, h=.9, vida=400, dano=30, vel=0.38,
   pal=[(217, 119, 87), (255, 160, 120), (20, 14, 14), (160, 80, 55)],
   cubes=[box([-8, 4, -5], [16, 10, 10], 0), box([-8, 12, -5], [16, 2, 10], 1),
          box([-5, 8, -5.4], [2, 4, .5], 2), box([3, 8, -5.4], [2, 4, .5], 2),
          box([-11, 6, -2], [3, 4, 4], 3), box([8, 6, -2], [3, 4, 4], 3),
          box([-7, 0, -3], [2, 4, 2], 3), box([-3, 0, -3], [2, 4, 2], 3), box([1, 0, -3], [2, 4, 2], 3), box([5, 0, -3], [2, 4, 2], 3)]),
 "convergencia": dict(glow=[0, 1, 2, 3, 4, 5], stars=[6], w=1.2, h=2.2, vida=1000, dano=80, vel=0.3,
   pal=[(255, 255, 255), (255, 60, 200), (60, 220, 255), (255, 210, 60), (150, 80, 255), (255, 70, 70), (20, 10, 40)],
   cubes=[box([-5, 18, -5], [10, 10, 10], 0)] +
         [box([round(11 * math.cos(k * math.pi / 3)) - 3, 19 + (k % 2) * 4, round(11 * math.sin(k * math.pi / 3)) - 3], [6, 6, 6], 1 + k % 5) for k in range(6)] +
         [box([-2, 28, -2], [4, 10, 4], 3), box([-2, 6, -2], [4, 10, 4], 4), box([-1, 34, -1], [2, 6, 2], 0), box([-1, 0, -1], [2, 6, 2], 2)]),
 "chatgpt_logo": dict(glow=[0], w=1.0, h=2.2, vida=600, dano=35, vel=0.3, logo=True,
   pal=[(16, 163, 127), (255, 255, 255), (40, 40, 40)],
   cubes=[box([-4, 0, -2], [3, 10, 4], 2), box([1, 0, -2], [3, 10, 4], 2)]),
}
LOGO_BOX = {"o": [-14, 10, -1.5], "s": [28, 28, 3]}

LUZ = {"up": 1.18, "north": 1.0, "south": .85, "east": .9, "west": .9, "down": .6}
def hsh(x, y, k): return ((x * 73856093) ^ (y * 19349663) ^ (k * 83492791)) & 255
def lit(c, f): return tuple(max(0, min(255, int(v * f))) for v in c)

def paint(face, w, h, col, kind, k):
    px = [[None] * w for _ in range(h)]
    for y in range(h):
        for x in range(w):
            n = (hsh(x, y, k) / 255 - .5) * .14                       # grano
            ed = min(x, y, w - 1 - x, h - 1 - y)
            f = LUZ[face] * (1.08 - .16 * (y / max(1, h - 1)) if face not in ("up", "down") else LUZ[face] / LUZ[face])
            if face in ("up", "down"): f = LUZ[face]
            if ed == 0 and w > 3 and h > 3: f *= .86                    # borde oscuro (volumen)
            c = col
            if kind == "glow":
                d = math.hypot((x - (w - 1) / 2) / max(1, w / 2), (y - (h - 1) / 2) / max(1, h / 2))
                t = max(0, 1 - d)
                c = tuple(int(col[i] + (255 - col[i]) * .55 * t) for i in range(3)); f = 1.0 + .15 * t; n *= .4
            elif kind == "stars":
                if hsh(x, y, k + 7) > 238: c = (255, 255, 255); f = 1.3; n = 0
                elif hsh(x, y, k + 3) > 228: c = (170, 110, 255); f = 1.0; n = 0
                else: c = tuple(int(col[i] + 25 * math.sin((x + y) * .5)) % 256 for i in range(3))
            elif kind == "fur":
                n = (hsh(x // 2, y, k) / 255 - .5) * .22
                if y % 3 == 0: f *= .93
            px[y][x] = lit(c, f * (1 + n)) + (255,)
    return px

def build_atlas(e, k0):
    cubes = list(e["cubes"])
    jobs = []  # (ci, face, w, h)
    for ci, c in enumerate(cubes):
        sx, sy, sz = c["s"]
        for face, (w, h) in {"north": (sx, sy), "south": (sx, sy), "east": (sz, sy), "west": (sz, sy), "up": (sx, sz), "down": (sx, sz)}.items():
            jobs.append((ci, face, max(1, math.ceil(w)), max(1, math.ceil(h))))
    extra = []
    if e.get("logo"):
        extra = [("logo", 28, 28)]
    jobs.sort(key=lambda j: -j[3])
    W = 256; x = y = rowh = 0; place = {}
    for ci, face, w, h in jobs + [(None, n, w, h) for n, w, h in extra]:
        if x + w + 1 > W: x = 0; y += rowh + 1; rowh = 0
        place[(ci, face)] = (x, y, w, h); x += w + 1; rowh = max(rowh, h)
    H = 64
    while H < y + rowh + 1: H *= 2
    px = [[(0, 0, 0, 0)] * W for _ in range(H)]
    return cubes, place, px, W, H

def logo_pixels(S=32, SS=4):
    """Símbolo aproximado (seis lazos entrelazados sobre círculo verde), con supersampling para bordes suaves."""
    big = S * SS; cx = cy = (big - 1) / 2; out = [[(0, 0, 0, 0)] * S for _ in range(S)]
    def blanco(x, y):
        for k in range(6):
            ang = k * math.pi / 3
            ox, oy = cx + big * .20 * math.cos(ang), cy + big * .20 * math.sin(ang)
            t = ang + math.pi / 2 + .55
            dx, dy = x - ox, y - oy
            u, v = dx * math.cos(t) + dy * math.sin(t), -dx * math.sin(t) + dy * math.cos(t)
            if .70 <= math.hypot(u / (big * .30), v / (big * .115)) <= 1.0: return True
        return False
    for y in range(S):
        for x in range(S):
            acc = [0, 0, 0, 0]
            for sy in range(SS):
                for sx in range(SS):
                    X, Y = x * SS + sx, y * SS + sy
                    if math.hypot(X - cx, Y - cy) > big / 2 - 1: c = (0, 0, 0, 0)
                    elif blanco(X, Y): c = (255, 255, 255, 255)
                    else: c = (16, 163, 127, 255)
                    a = c[3]; acc[0] += c[0] * a; acc[1] += c[1] * a; acc[2] += c[2] * a; acc[3] += a
            n = SS * SS
            out[y][x] = (acc[0] // acc[3], acc[1] // acc[3], acc[2] // acc[3], 255 if acc[3] > 128 * n else 0) if acc[3] else (0, 0, 0, 0)
    return out

def png(path, w, h, px):
    raw = b"".join(b"\x00" + b"".join(bytes(c) for c in row) for row in px)
    def ch(t, d): return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    open(path, "wb").write(b"\x89PNG\r\n\x1a\n" + ch(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                           + ch(b"IDAT", zlib.compress(raw)) + ch(b"IEND", b""))

def build(root):
    for d in ("BP/entities", "RP/entity", "RP/models/entity", "RP/textures/entity"): os.makedirs(f"{root}/{d}", exist_ok=True)
    for name, e in ENT.items():
        ident = f"fb:{name}"
        comps = {
            "minecraft:type_family": {"family": ["fb_ally", "fb_" + name]},
            "minecraft:collision_box": {"width": e["w"], "height": e["h"]},
            "minecraft:health": {"value": e["vida"], "max": e["vida"]},
            "minecraft:attack": {"damage": e["dano"]},
            "minecraft:movement": {"value": e["vel"]},
            "minecraft:movement.basic": {}, "minecraft:jump.static": {},
            "minecraft:navigation.walk": {"can_path_over_water": True, "avoid_damage_blocks": True, "can_pass_doors": True},
            "minecraft:physics": {}, "minecraft:pushable": {"is_pushable": True, "is_pushable_by_piston": True},
            "minecraft:knockback_resistance": {"value": 1.0}, "minecraft:fire_immune": {},
            "minecraft:persistent": {}, "minecraft:nameable": {},
            "minecraft:behavior.float": {"priority": 0},
            "minecraft:behavior.melee_attack": {"priority": 3, "speed_multiplier": 1.3, "track_target": True},
            "minecraft:behavior.nearest_attackable_target": {"priority": 2, "must_see": False, "reselect_targets": True,
                "entity_types": [{"filters": {"test": "is_family", "subject": "other", "value": "monster"}, "max_dist": 24}]},
            "minecraft:behavior.look_at_player": {"priority": 7, "look_distance": 8},
            "minecraft:behavior.random_look_around": {"priority": 9},
        }
        json.dump({"format_version": "1.21.0", "minecraft:entity": {"description": {"identifier": ident, "is_spawnable": False,
                  "is_summonable": True}, "components": comps}},
                  open(f"{root}/BP/entities/{name}.json", "w"), indent=2)
        cu, place, px, W, H = build_atlas(e, hash(name) & 255)
        glow, stars, fur = set(e.get("glow", [])), set(e.get("stars", [])), set(e.get("fur", []))
        cubes = []
        for ci, c in enumerate(cu):
            kind = "glow" if c["c"] in glow else "stars" if c["c"] in stars else "fur" if c["c"] in fur else "solid"
            uv = {}
            for face in ("north", "south", "east", "west", "up", "down"):
                x0, y0, w, h = place[(ci, face)]
                for yy, row in enumerate(paint(face, w, h, e["pal"][c["c"]], kind, ci * 7 + len(name))):
                    for xx, v in enumerate(row): px[y0 + yy][x0 + xx] = v
                uv[face] = {"uv": [x0, y0], "uv_size": [w, h]}
            cubes.append({"origin": c["o"], "size": c["s"], "uv": uv})
        if e.get("logo"):
            sx, sy, sz = LOGO_BOX["s"]; lx, ly, lw, lh = place[(None, "logo")]
            for yy, row in enumerate(logo_pixels(28)):
                for xx, v in enumerate(row): px[ly + yy][lx + xx] = v
            lc = []
            uv = {}
            for face, (w, h) in {"north": (sx, sy), "south": (sx, sy), "east": (sz, sy), "west": (sz, sy), "up": (sx, sz), "down": (sx, sz)}.items():
                uv[face] = {"uv": [lx, ly], "uv_size": [28, 28]} if face in ("north", "south") else {"uv": [lx, ly], "uv_size": [1, 1]}
            cubes.append({"origin": LOGO_BOX["o"], "size": LOGO_BOX["s"], "uv": uv})
        geo = {"format_version": "1.12.0", "minecraft:geometry": [{"description": {"identifier": f"geometry.fb_{name}",
               "texture_width": W, "texture_height": H, "visible_bounds_width": 4, "visible_bounds_height": 5,
               "visible_bounds_offset": [0, 1.5, 0]}, "bones": [{"name": "root", "pivot": [0, 0, 0], "cubes": cubes}]}]}
        json.dump(geo, open(f"{root}/RP/models/entity/{name}.geo.json", "w"), indent=2)
        json.dump({"format_version": "1.10.0", "minecraft:client_entity": {"description": {"identifier": ident,
                  "materials": {"default": "entity_alphatest"}, "textures": {"default": f"textures/entity/fb_{name}"},
                  "geometry": {"default": f"geometry.fb_{name}"}, "render_controllers": ["controller.render.default"]}}},
                  open(f"{root}/RP/entity/{name}.entity.json", "w"), indent=2)
        png(f"{root}/RP/textures/entity/fb_{name}.png", W, H, px)

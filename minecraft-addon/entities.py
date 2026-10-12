"""Genera las entidades aliadas (BP + RP): definición, modelo de cubos y textura."""
import json, math, os, zlib, struct

# paleta: índice -> color. Cada cubo apunta a un "swatch" de 4x4 px de la textura (fila superior).
def box(o, s, c): return {"o": o, "s": s, "c": c}

ENT = {
 "warden_final": dict(w=1.8, h=3.0, vida=2048, dano=120, vel=0.32,
   pal=[(18, 36, 46), (30, 90, 100), (80, 255, 240), (200, 200, 190)],
   cubes=[box([-9, 0, -4], [7, 16, 8], 0), box([2, 0, -4], [7, 16, 8], 0),
          box([-10, 16, -6], [20, 24, 12], 0), box([-6, 22, -6.4], [12, 2, .5], 2), box([-6, 28, -6.4], [12, 2, .5], 2),
          box([-8, 40, -7], [16, 12, 14], 1), box([-5, 44, -7.4], [3, 3, .5], 2), box([2, 44, -7.4], [3, 3, .5], 2),
          box([-18, 18, -4], [8, 22, 8], 1), box([10, 18, -4], [8, 22, 8], 1),
          box([-9, 52, -2], [4, 5, 4], 3), box([5, 52, -2], [4, 5, 4], 3)]),
 "clawd": dict(w=.9, h=.9, vida=400, dano=30, vel=0.38,
   pal=[(217, 119, 87), (255, 160, 120), (20, 14, 14), (160, 80, 55)],
   cubes=[box([-8, 4, -5], [16, 10, 10], 0), box([-8, 12, -5], [16, 2, 10], 1),
          box([-5, 8, -5.4], [2, 4, .5], 2), box([3, 8, -5.4], [2, 4, .5], 2),
          box([-11, 6, -2], [3, 4, 4], 3), box([8, 6, -2], [3, 4, 4], 3),
          box([-7, 0, -3], [2, 4, 2], 3), box([-3, 0, -3], [2, 4, 2], 3), box([1, 0, -3], [2, 4, 2], 3), box([5, 0, -3], [2, 4, 2], 3)]),
 "convergencia": dict(w=1.2, h=2.2, vida=1000, dano=80, vel=0.3,
   pal=[(255, 255, 255), (255, 60, 200), (60, 220, 255), (255, 210, 60), (150, 80, 255), (255, 70, 70), (20, 10, 40)],
   cubes=[box([-5, 18, -5], [10, 10, 10], 0)] +
         [box([round(11 * math.cos(k * math.pi / 3)) - 3, 19 + (k % 2) * 4, round(11 * math.sin(k * math.pi / 3)) - 3], [6, 6, 6], 1 + k % 5) for k in range(6)] +
         [box([-2, 28, -2], [4, 10, 4], 3), box([-2, 6, -2], [4, 10, 4], 4), box([-1, 34, -1], [2, 6, 2], 0), box([-1, 0, -1], [2, 6, 2], 2)]),
 "chatgpt_logo": dict(w=1.0, h=2.2, vida=600, dano=35, vel=0.3, logo=True,
   pal=[(16, 163, 127), (255, 255, 255), (40, 40, 40)],
   cubes=[box([-4, 0, -2], [3, 10, 4], 2), box([1, 0, -2], [3, 10, 4], 2)]),
}
LOGO_BOX = {"o": [-14, 10, -1.5], "s": [28, 28, 3]}

def faces(c, logo=False):
    u = c * 4 + 1
    f = {k: {"uv": [u, 1], "uv_size": [1, 1]} for k in ("north", "south", "east", "west", "up", "down")}
    return f

def logo_pixels(S=32):
    px = [[(0, 0, 0, 0)] * S for _ in range(S)]
    cx = cy = (S - 1) / 2
    for y in range(S):
        for x in range(S):
            r = math.hypot(x - cx, y - cy)
            if r <= S / 2 - .5:
                px[y][x] = (16, 163, 127, 255)
                for k in range(6):  # seis lazos del símbolo
                    ang = k * math.pi / 3
                    ox, oy = cx + 6.2 * math.cos(ang), cy + 6.2 * math.sin(ang)
                    dx, dy = x - ox, y - oy
                    t = ang + math.pi / 2 + .5
                    u, v = dx * math.cos(t) + dy * math.sin(t), -dx * math.sin(t) + dy * math.cos(t)
                    e = math.hypot(u / 9.2, v / 3.6)
                    if .72 <= e <= 1.0: px[y][x] = (255, 255, 255, 255)
    return px

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
        cubes = [{"origin": c["o"], "size": c["s"], "uv": faces(c["c"])} for c in e["cubes"]]
        if e.get("logo"):
            lf = {k: {"uv": [0, 8], "uv_size": [32, 32]} for k in ("north", "south")}
            lf.update({k: {"uv": [9, 1], "uv_size": [1, 1]} for k in ("east", "west", "up", "down")})
            cubes.append({"origin": LOGO_BOX["o"], "size": LOGO_BOX["s"], "uv": lf})
        geo = {"format_version": "1.12.0", "minecraft:geometry": [{"description": {"identifier": f"geometry.fb_{name}",
               "texture_width": 64, "texture_height": 64, "visible_bounds_width": 4, "visible_bounds_height": 5,
               "visible_bounds_offset": [0, 1.5, 0]}, "bones": [{"name": "root", "pivot": [0, 0, 0], "cubes": cubes}]}]}
        json.dump(geo, open(f"{root}/RP/models/entity/{name}.geo.json", "w"), indent=2)
        json.dump({"format_version": "1.10.0", "minecraft:client_entity": {"description": {"identifier": ident,
                  "materials": {"default": "entity_alphatest"}, "textures": {"default": f"textures/entity/fb_{name}"},
                  "geometry": {"default": f"geometry.fb_{name}"}, "render_controllers": ["controller.render.default"]}}},
                  open(f"{root}/RP/entity/{name}.entity.json", "w"), indent=2)
        px = [[(0, 0, 0, 0)] * 64 for _ in range(64)]
        for i, col in enumerate(e["pal"]):
            for y in range(4):
                for x in range(4): px[y][i * 4 + x] = col + (255,)
        if e.get("logo"):
            for y, row in enumerate(logo_pixels()):
                for x, c in enumerate(row): px[8 + y][x] = c
        png(f"{root}/RP/textures/entity/fb_{name}.png", 64, 64, px)

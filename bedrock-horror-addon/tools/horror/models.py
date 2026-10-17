"""Jointed box models. Units are pixels (16 = 1 block), Y up, faces look toward -Z.

Every cube carries a material name (painted by textures.py) and optional
feature flags: eyes, teeth, upper_teeth, ribs, bloody, fade (bottom alpha).
"""


def c(origin, size, mat, **flags):
    assert all(float(s).is_integer() for s in size), "cube sizes must be whole pixels"
    return {"origin": origin, "size": [int(s) for s in size], "mat": mat, **flags}


def bone(name, pivot, parent=None, cubes=(), rotation=None):
    b = {"name": name, "pivot": pivot, "cubes": list(cubes)}
    if parent:
        b["parent"] = parent
    if rotation:
        b["rotation"] = rotation
    return b


def mirror_cube(cube):
    (x, y, z), w = cube["origin"], cube["size"][0]
    return {**cube, "origin": [-x - w, y, z]}


def mirror(bones):
    """Left-side copies of right-side bones (names ending in _r)."""
    out = []
    for b in bones:
        m = {**b, "name": b["name"][:-2] + "_l", "pivot": [-b["pivot"][0], *b["pivot"][1:]],
             "cubes": [mirror_cube(cb) for cb in b["cubes"]]}
        if b.get("parent", "").endswith("_r"):
            m["parent"] = b["parent"][:-2] + "_l"
        if "rotation" in b:
            x, y, z = b["rotation"]
            m["rotation"] = [x, -y, -z]
        out.append(m)
    return bones + out


def stalker():
    legs = [
        bone("leg_r", [-1.5, 22, 0], "root", [c([-2.5, 12, -1], [2, 10, 2], "skin")]),
        bone("shin_r", [-1.5, 12, 0], "leg_r", [c([-2.5, 1, -1], [2, 11, 2], "skin"),
                                                  c([-2.5, 0, -3], [2, 1, 4], "bone")]),
    ]
    arms = [
        # Counter the chest's hunch so the long arms hang straight down.
        bone("arm_r", [-4, 37, 0], "chest", [c([-5.5, 25, -1], [2, 13, 2], "skin")], rotation=[-14, 0, 0]),
        bone("forearm_r", [-4.5, 25, 0], "arm_r", [c([-5.5, 13, -1], [2, 12, 2], "skin")]),
        bone("hand_r", [-4.5, 13, 0], "forearm_r", [
            c([-5.5, 10, -1], [2, 3, 2], "skin", bloody=True),
            c([-5.5, 3, -1], [1, 7, 1], "bone", bloody=True),
            c([-4.5, 4, -1], [1, 6, 1], "bone", bloody=True),
            c([-5.5, 5, 0], [1, 5, 1], "bone"),
        ]),
    ]
    return [
        bone("root", [0, 0, 0]),
        bone("waist", [0, 22, 0], "root", [c([-3, 22, -1.5], [6, 3, 3], "skin")]),
        bone("chest", [0, 25, 0], "waist", [
            c([-2.5, 25, -1.5], [5, 6, 3], "skin", ribs=True),
            c([-3.5, 31, -2], [7, 8, 4], "skin", ribs=True),
            c([-0.5, 27, 1.5], [1, 11, 1], "bone"),          # spine
        ], rotation=[10, 0, 0]),
        bone("neck", [0, 39, -0.5], "chest", [c([-1, 39, -1.5], [2, 4, 2], "skin")], rotation=[12, 0, 0]),
        bone("head", [0, 43, -1], "neck", [c([-2.5, 43, -4], [5, 7, 5], "skin", eyes="slit", upper_teeth=True)]),
        bone("jaw", [0, 43, 0], "head", [c([-2, 40, -3.8], [4, 3, 4], "skin", teeth=True, bloody=True)]),
        *mirror(legs),
        *mirror(arms),
    ]


def watcher():
    legs = [bone("leg_r", [-1.5, 26, 0], "root", [c([-2, 0, -0.5], [1, 26, 1], "void")])]
    arms = [
        bone("arm_r", [-3, 43, 0], "torso", [c([-4, 8, -0.5], [1, 35, 1], "void")]),
        bone("hand_r", [-3.5, 8, 0], "arm_r", [c([-4, 2, -0.5], [1, 6, 1], "void"),
                                                c([-4, 3, 0.5], [1, 5, 1], "void")]),
    ]
    return [
        bone("root", [0, 0, 0]),
        bone("torso", [0, 26, 0], "root", [c([-2.5, 26, -1], [5, 18, 2], "void", ribs=True)]),
        bone("neck", [0, 44, 0], "torso", [c([-0.5, 44, -0.5], [1, 6, 1], "void")]),
        bone("head", [0, 50, 0], "neck", [c([-2.5, 50, -2.5], [5, 6, 5], "void", eyes="wide")]),
        *mirror(legs),
        *mirror(arms),
    ]


def woodbeast():
    antler = [c([-3, 21, -15], [1, 5, 1], "antler"), c([-6, 24, -15], [3, 1, 1], "antler"),
              c([-6, 25, -15], [1, 4, 1], "antler"), c([-4, 26, -15], [1, 3, 1], "antler")]
    front = [
        bone("arm_r", [-3, 13, -5], "chest", [c([-5, 6, -6], [3, 8, 3], "fur")]),
        bone("forearm_r", [-3.5, 6, -4.5], "arm_r", [c([-5, 1, -6], [3, 5, 3], "fur"),
                                                      c([-5, 0, -7], [3, 1, 4], "bone", bloody=True)]),
    ]
    hind = [
        bone("leg_r", [-3, 14, 5], "body", [c([-5, 7, 3], [3, 7, 4], "fur")]),
        bone("shin_r", [-3.5, 7, 5], "leg_r", [c([-5, 1, 4], [3, 6, 3], "fur"),
                                                c([-5, 0, 3], [3, 1, 4], "bone")]),
    ]
    return [
        bone("root", [0, 0, 0]),
        bone("body", [0, 14, 4], "root", [
            c([-4, 12, 0], [8, 8, 8], "fur"),
            c([-1, 20, 3], [2, 3, 2], "bone"),
        ]),
        bone("chest", [0, 15, 0], "body", [
            c([-4.5, 11, -8], [9, 10, 8], "skin", ribs=True),
            c([-1, 21, -6], [2, 3, 2], "bone"),
            c([-1, 21, -2], [2, 4, 2], "bone"),
        ]),
        bone("neck", [0, 18, -8], "chest", [c([-2, 15, -13], [4, 5, 5], "fur")]),
        bone("head", [0, 18, -12], "neck", [
            c([-3, 15, -18], [6, 6, 6], "bone", eyes="socket"),
            c([-2, 15, -22], [4, 3, 4], "bone", upper_teeth=True),
            *antler, *[mirror_cube(a) for a in antler],
        ]),
        bone("jaw", [0, 15, -16], "head", [c([-2, 13, -21.8], [4, 2, 6], "bone", teeth=True, bloody=True)]),
        bone("tail1", [0, 18, 8], "body", [c([-1, 17, 8], [2, 2, 5], "fur")]),
        bone("tail2", [0, 18, 13], "tail1", [c([-1, 17, 13], [2, 2, 5], "fur")]),
        bone("tail3", [0, 18, 18], "tail2", [c([-1, 17, 18], [2, 2, 4], "bone")]),
        *mirror(front),
        *mirror(hind),
    ]


def whisperer():
    arms = [
        bone("arm_r", [-5, 31, 0], "robe", [c([-7, 20, -1.5], [3, 11, 3], "cloth")]),
        bone("forearm_r", [-5.5, 20, 0], "arm_r", [c([-7, 10, -1], [2, 10, 2], "skin")]),
        bone("hand_r", [-6, 10, 0], "forearm_r", [
            c([-7, 4, -1], [1, 6, 1], "bone"),
            c([-6, 5, -1], [1, 5, 1], "bone"),
            c([-7, 6, 0], [1, 4, 1], "bone"),
        ]),
    ]
    strips = [
        bone("strip1", [-3.5, 8, -2.5], "skirt", [c([-4.5, 0, -3], [2, 8, 1], "cloth", fade=30)]),
        bone("strip2", [1.5, 8, -2.5], "skirt", [c([0.5, 1, -3], [2, 7, 1], "cloth", fade=30)]),
        bone("strip3", [-1.5, 8, 2.5], "skirt", [c([-2.5, 0, 2], [2, 8, 1], "cloth", fade=30)]),
        bone("strip4", [3.5, 8, 2.5], "skirt", [c([2.5, 2, 2], [2, 6, 1], "cloth", fade=30)]),
    ]
    return [
        bone("root", [0, 0, 0]),
        bone("robe", [0, 20, 0], "root", [c([-4, 20, -2], [8, 12, 4], "cloth")]),
        bone("skirt", [0, 20, 0], "robe", [c([-5, 8, -3], [10, 12, 6], "cloth", fade=90)]),
        *strips,
        bone("head", [0, 32, 0], "robe", [
            c([-3, 32, -3], [6, 7, 6], "void", eyes="pinpoint"),
            c([-4, 31, -2], [8, 10, 6], "cloth"),           # hood
            c([-4, 39, -4], [8, 2, 2], "cloth"),            # brim
            c([-2, 36, 4], [4, 4, 2], "cloth"),             # hood tail
        ]),
        bone("jaw", [0, 33, 0], "head", [c([-2, 30, -2.8], [4, 2, 4], "void", teeth=True)]),
        *mirror(arms),
    ]

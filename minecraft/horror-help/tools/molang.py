"""A tiny Molang evaluator, enough for the procedural animations in
animations.py, so previews can show creatures posed by their own animations
(a frozen frame at a given time and walk phase)."""

import math
import re


class _M:
    sin = staticmethod(lambda d: math.sin(math.radians(d)))
    cos = staticmethod(lambda d: math.cos(math.radians(d)))
    abs = staticmethod(abs)
    max = staticmethod(max)
    min = staticmethod(min)
    clamp = staticmethod(lambda v, lo, hi: max(lo, min(hi, v)))
    mod = staticmethod(lambda a, b: math.fmod(a, b))
    floor = staticmethod(math.floor)
    random = staticmethod(lambda lo, hi: lo)  # previews: no random jolts


_TERNARY = re.compile(r"\(([^()?]*(?:\([^()]*\)[^()?]*)*)\?([^():]*(?:\([^()]*\)[^():]*)*):([^()]*)\)")


def _python(expr):
    s = expr
    while True:
        new = _TERNARY.sub(lambda m: f"(({m.group(2)}) if ({m.group(1)}) else ({m.group(3)}))", s)
        if new == s:
            break
        s = new
    return s.replace("math.", "M.")


def evaluate(expr, env):
    if isinstance(expr, (int, float)):
        return float(expr)
    s = _python(expr)
    for k in sorted(env, key=len, reverse=True):
        s = s.replace(k, repr(env[k]))
    return float(eval(s, {"M": _M}))


def pose(animations, names, t=1.0, dist=0.0, speed=0.0, attack=0.0, hurt=0.0, target=(0.0, 0.0)):
    """Sum the rotations (and positions, scales) the named animations give
    every bone at that moment, as a pose for geometry.render."""
    env = {"query.life_time": t, "query.modified_distance_moved": dist, "query.modified_move_speed": speed,
           "variable.attack_time": attack, "query.hurt_time": hurt,
           "query.target_x_rotation": target[0], "query.target_y_rotation": target[1]}
    out = {}
    for name in names:
        for bone, channels in animations[name]["bones"].items():
            p = out.setdefault(bone, {"rotation": [0.0, 0.0, 0.0], "position": [0.0, 0.0, 0.0], "scale": [1.0, 1.0, 1.0]})
            for ch in ("rotation", "position"):
                if ch in channels:
                    for i in range(3):
                        p[ch][i] += evaluate(channels[ch][i], env)
            if "scale" in channels:
                sc = channels["scale"]
                sc = sc if isinstance(sc, list) else [sc] * 3
                for i in range(3):
                    p["scale"][i] *= evaluate(sc[i], env)
    return {b: {k: tuple(v) for k, v in p.items()} for b, p in out.items()}

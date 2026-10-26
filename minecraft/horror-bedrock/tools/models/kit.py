"""Shared kit for the creature models: double-detail building helpers, the
texel painter, and the material ramps every creature can use.

Every model is built at four times its in-game size and painted against
that (box UVs map one texel per unit, so a head gets a 32x32 face instead of
8x8), then written out at its real size with the texture size in the JSON
divided by four (see Model.detail). Coordinates in the model files are
ordinary in-game pixels (quarter-pixel steps allowed); B() and C() scale
them.

The painters were designed on a 2x grid (a head face is 16x16 "design
pixels"): a Texel gives them design coordinates (x, y, fw, fh, X, Y) for
patterns and shapes, while noise, lighting and dithering run on the real,
finer texels, so every surface gets twice the detail it was drawn with.
"""

import math

from geometry import Bone, Cube, Model
from paint import Noise, Ramp, form, glow
from pixels import Canvas, box_faces, paint_box

K = 4  # texels per in-game pixel
DESIGN = 2  # texels per in-game pixel the painters are written for
SUB = K // DESIGN
CLEAR = (0, 0, 0, 0)
BLACK = (0x08, 0x07, 0x09)
EYE = glow("#ffffff", 70)

__all__ = ["K", "CLEAR", "BLACK", "EYE", "B", "C", "hd_model", "side_x", "Texel", "paint_model", "rows", "glow",
           "Ramp", "math", "arc", "BONE", "BLOOD", "GUT", "MUSCLE", "BURN", "BLISTER", "TEETH", "IRON", "WOOD", "HAIR",
           "STONE", "FUR", "CLOTH_DARK"]


def _k(v):
    return tuple(x * K for x in v) if v is not None else None


def B(name, parent, pivot, rotation=None, cubes=()):
    return Bone(name, parent, _k(pivot), rotation, cubes)


def C(origin, size, tag, inflate=0.0, pivot=None, rotation=None):
    return Cube(_k(origin), _k(size), None, inflate * K, _k(pivot), rotation, share=tag)


def hd_model(identifier, bones, bounds, width=256):
    return Model(identifier, bones, bounds=bounds, detail=K).pack_uv(width * SUB)


def side_x(side, a, b):
    """Origin X of a cube spanning |x| in [a, b] on the model's right (-1) or
    left (+1) side."""
    return -b if side < 0 else a


def arc(n, radius, center, a0, a1, size, tag, z, spike=None):
    """Cubes along a circular arc in the XY plane (a crown, a spine, a horn).
    Angles in degrees, 0 = straight up, positive towards +x."""
    out = []
    cx, cy = center
    for i in range(n):
        a = math.radians(a0 + (a1 - a0) * i / max(1, n - 1))
        x, y = cx + radius * math.sin(a), cy + radius * math.cos(a)
        out.append(C((x - size / 2, y - size / 2, z), (size, size, size), tag))
    return out


class Texel:
    """One texel being painted: which part (tag), which face, where on the
    face and on the sheet, and helpers to paint it with a ramp.

    x, y, fw, fh, X, Y are design coordinates (the 2x grid the painters
    were drawn on); fx, fy, ffw, ffh, FX, FY are the real texels; sx, sy are
    the real position in design units (for smooth noise)."""

    __slots__ = ("tag", "face", "x", "y", "fw", "fh", "X", "Y", "fx", "fy", "ffw", "ffh", "FX", "FY", "sx", "sy",
                 "noise")

    def lit(self):
        v = form(self.face, self.fx, self.fy, self.ffw, self.ffh)
        # A bevel: the top edge of every side catches the light.
        if self.face in ("right", "left", "front", "back") and self.fy == 0 and self.ffh >= 4:
            v += 0.14
        return v

    def n(self, scale=3.0, octaves=2):
        return self.noise(self.sx, self.sy, scale, octaves)

    def n2(self, scale=3.0, octaves=2):
        return self.noise(self.sx + 911, self.sy + 377, scale, octaves)

    def ridge(self, scale=4.0, width=0.06):
        return self.noise.ridge(self.sx, self.sy, scale, width)

    def grain(self):
        """Fine surface grain, one texel across."""
        return self.noise(self.FX * 1.7 + 5000, self.FY * 1.7 + 5000, 1.0, 1)

    def paint(self, ramp, base=0.55, rough=0.3, scale=3.0, extra=0.0):
        v = base + self.lit() + (self.n(scale) - 0.5) * rough + extra + (self.grain() - 0.5) * 0.08
        return ramp(v, self.FX, self.FY)

    @property
    def side(self):
        return self.face in ("right", "left", "front", "back")

    @property
    def u(self):
        """0..1 across the face."""
        return self.fx / max(1, self.ffw - 1)

    @property
    def v(self):
        """0..1 down the face."""
        return self.fy / max(1, self.ffh - 1)


def paint_model(model, seed, fn):
    """fn(t: Texel) -> colour or None for every texel of every cube."""
    cv = Canvas(*model.texture)
    t = Texel()
    t.noise = Noise(seed)
    for bone in model.bones:
        for cube in bone.cubes:
            w, h, d = (int(s) for s in cube.size)
            origins = box_faces(*cube.uv, w, h, d)

            def paint(face, x, y, fw, fh, tag=cube.share, o=origins):
                t.tag, t.face = tag, face
                t.fx, t.fy, t.ffw, t.ffh = x, y, fw, fh
                t.fw, t.fh = max(1, fw // SUB), max(1, fh // SUB)
                t.x, t.y = min(x // SUB, t.fw - 1), min(y // SUB, t.fh - 1)
                t.FX, t.FY = o[face][0] + x, o[face][1] + y
                t.X, t.Y = t.FX // SUB, t.FY // SUB
                t.sx, t.sy = t.FX / SUB, t.FY / SUB
                return fn(t)
            paint_box(cv, *cube.uv, w, h, d, paint)
    return cv


def rows(pattern, palette, t, fallback):
    c = palette.get(pattern[t.y][t.x])
    return fallback() if c is None else c


# Shared materials.
BONE = Ramp(colors=["#5e5242", "#8a7c64", "#b3a384", "#d2c4a2", "#e9dfc2", "#f6f0dc"])
BLOOD = Ramp(colors=["#1e0203", "#3c0507", "#5c0a0c", "#7e1414", "#9c2420"])
GUT = Ramp(colors=["#4a141c", "#72283a", "#9a3e4c", "#be5a64", "#d8808a", "#eaa6ac"])
MUSCLE = Ramp(colors=["#3a0a0c", "#5e1418", "#86222a", "#a8363a", "#c8584e", "#e08a78"])
BURN = Ramp(colors=["#1a0605", "#3a0c08", "#64160e", "#8e2416", "#b43c26", "#d46a4c"])
BLISTER = Ramp(colors=["#a88c5a", "#cdb47e", "#e8d4a0", "#f6eac4"])
TEETH = Ramp(colors=["#6a5c44", "#a8966e", "#cfbf98", "#e8dcbc", "#f8f0dc"])
IRON = Ramp(colors=["#2a2a2e", "#46464c", "#66666e", "#8c8c94", "#b4b4bc", "#dcdce2"])
WOOD = Ramp(colors=["#2e1e10", "#4a3018", "#664424", "#80582e", "#9a6e3c"])
HAIR = Ramp(colors=["#0e0906", "#1c120a", "#2c1c10", "#402a18", "#563a22"])
STONE = Ramp(colors=["#2e2e32", "#48484e", "#626268", "#7e7e84", "#9a9aa0", "#b8b8bc"])
FUR = Ramp(colors=["#1a120c", "#2c2016", "#40301f", "#56422c", "#6e563a"])
CLOTH_DARK = Ramp(colors=["#08080a", "#121216", "#1c1c22", "#28282e", "#36363e"])

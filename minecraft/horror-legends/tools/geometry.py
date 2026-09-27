"""Bedrock entity geometry, defined in Python.

Models are written once here and emitted as `minecraft:geometry` JSON, so the
texture painters and the geometry share the same cube list. `check_uv` makes
sure no two cubes paint over each other's texture, and `render` draws a model
with a small software rasteriser, used for previews and the pack icon.

Coordinates follow Bedrock's JSON (the model looks towards -Z). Rendering
converts to Blockbench's view space: X mirrored, and rotations applied as
Rz(z) * Ry(-y) * Rx(-x), which matches how the game poses bones (a limb
hanging down with rotation x = -90 points forwards).
"""

import math

from pixels import Canvas, clamp


class Cube:
    def __init__(self, origin, size, uv, inflate=0.0, pivot=None, rotation=None):
        self.origin, self.size, self.uv = origin, size, uv
        self.inflate, self.pivot, self.rotation = inflate, pivot, rotation

    def to_json(self):
        c = {"origin": list(self.origin), "size": list(self.size), "uv": list(self.uv)}
        if self.inflate:
            c["inflate"] = self.inflate
        if self.rotation:
            c["pivot"] = list(self.pivot)
            c["rotation"] = list(self.rotation)
        return c


class Bone:
    def __init__(self, name, parent=None, pivot=(0, 0, 0), rotation=None, cubes=()):
        self.name, self.parent, self.pivot = name, parent, pivot
        self.rotation, self.cubes = rotation, list(cubes)

    def to_json(self):
        b = {"name": self.name, "pivot": list(self.pivot)}
        if self.parent:
            b["parent"] = self.parent
        if self.rotation:
            b["rotation"] = list(self.rotation)
        if self.cubes:
            b["cubes"] = [c.to_json() for c in self.cubes]
        return b


class Model:
    def __init__(self, identifier, bones, texture=(64, 64), bounds=(2.0, 3.0, (0, 1.5, 0))):
        self.identifier, self.bones, self.texture, self.bounds = identifier, bones, texture, bounds

    def bone(self, name):
        return next(b for b in self.bones if b.name == name)

    def to_json(self):
        w, h, offset = self.bounds
        return {
            "description": {
                "identifier": self.identifier,
                "texture_width": self.texture[0],
                "texture_height": self.texture[1],
                "visible_bounds_width": w,
                "visible_bounds_height": h,
                "visible_bounds_offset": list(offset),
            },
            "bones": [b.to_json() for b in self.bones],
        }

    def check_uv(self):
        """Every face of every cube must sit inside the texture, and cubes
        with different UV boxes must not share a single texel."""
        tw, th = self.texture
        owners = {}
        errors = []
        for bone in self.bones:
            for cube in bone.cubes:
                w, h, d = (int(s) for s in cube.size)
                key = (tuple(cube.uv), w, h, d)
                u, v = cube.uv
                for face, (x0, y0, fw, fh) in faces(u, v, w, h, d).items():
                    if x0 < 0 or y0 < 0 or x0 + fw > tw or y0 + fh > th:
                        errors.append(f"{self.identifier} {bone.name}: {face} face outside the texture")
                    for x in range(x0, x0 + fw):
                        for y in range(y0, y0 + fh):
                            other = owners.setdefault((x, y), (key, bone.name))
                            if other[0] != key:
                                errors.append(f"{self.identifier}: {bone.name} and {other[1]} overlap at {x},{y}")
        return sorted(set(errors))[:10]


def faces(u, v, w, h, d):
    return {
        "up": (u + d, v, w, d),
        "down": (u + d + w, v, w, d),
        "east": (u, v + d, d, h),
        "north": (u + d, v + d, w, h),
        "west": (u + d + w, v + d, d, h),
        "south": (u + 2 * d + w, v + d, w, h),
    }


# --------------------------------------------------------------------------- #
# Rendering
# --------------------------------------------------------------------------- #

def _mm(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def _mv(m, v):
    return tuple(sum(m[i][k] * v[k] for k in range(3)) for i in range(3))


def _rot(rx, ry, rz):
    """Bedrock bone rotation (degrees) as a view-space matrix."""
    a, b, c = math.radians(-rx), math.radians(-ry), math.radians(rz)
    x = [[1, 0, 0], [0, math.cos(a), -math.sin(a)], [0, math.sin(a), math.cos(a)]]
    y = [[math.cos(b), 0, math.sin(b)], [0, 1, 0], [-math.sin(b), 0, math.cos(b)]]
    z = [[math.cos(c), -math.sin(c), 0], [math.sin(c), math.cos(c), 0], [0, 0, 1]]
    return _mm(z, _mm(y, x))


def _about(pivot, rot):
    """Affine transform rotating about `pivot` (view space)."""
    rp = _mv(rot, pivot)
    return rot, tuple(pivot[i] - rp[i] for i in range(3))


def _compose(a, b):
    """a after b."""
    (ra, ta), (rb, tb) = a, b
    t = _mv(ra, tb)
    return _mm(ra, rb), tuple(t[i] + ta[i] for i in range(3))


def _apply(t, p):
    r, off = t
    q = _mv(r, p)
    return tuple(q[i] + off[i] for i in range(3))


def _mirror(p):
    return (-p[0], p[1], p[2])


def _add(a, b):
    return tuple((a or (0, 0, 0))[i] + (b or (0, 0, 0))[i] for i in range(3))


def bone_transforms(model, pose=None):
    pose = pose or {}
    ident = ([[1, 0, 0], [0, 1, 0], [0, 0, 1]], (0, 0, 0))
    out = {}
    for bone in model.bones:  # parents are always listed before children
        parent = out[bone.parent] if bone.parent else ident
        rx, ry, rz = _add(bone.rotation, pose.get(bone.name))
        local = _about(_mirror(bone.pivot), _rot(rx, ry, rz))
        out[bone.name] = _compose(parent, local)
    return out


def _cube_quads(cube, transform, texture):
    """Texel-sized quads (4 view-space corners, colour, outward normal)."""
    w, h, d = (int(s) for s in cube.size)
    inf = cube.inflate
    ox, oy, oz = cube.origin
    # View space: X is mirrored.
    x0, x1 = -(ox + cube.size[0]) - inf, -ox + inf
    y0, y1 = oy - inf, oy + cube.size[1] + inf
    z0, z1 = oz - inf, oz + cube.size[2] + inf
    if cube.rotation:
        transform = _compose(transform, _about(_mirror(cube.pivot), _rot(*cube.rotation)))
    corners = {
        # face: (top-left, top-right, bottom-right, bottom-left) seen from outside
        "north": ((x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)),
        "south": ((x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)),
        "east": ((x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)),
        "west": ((x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)),
        "up": ((x1, y1, z1), (x0, y1, z1), (x0, y1, z0), (x1, y1, z0)),
        "down": ((x1, y0, z0), (x0, y0, z0), (x0, y0, z1), (x1, y0, z1)),
    }
    u, v = cube.uv
    for face, (tx, ty, fw, fh) in faces(u, v, w, h, d).items():
        if fw <= 0 or fh <= 0:
            continue
        tl, tr, br, bl = (_apply(transform, p) for p in corners[face])
        e1 = tuple(tr[i] - tl[i] for i in range(3))
        e2 = tuple(bl[i] - tl[i] for i in range(3))
        normal = (e2[1] * e1[2] - e2[2] * e1[1], e2[2] * e1[0] - e2[0] * e1[2], e2[0] * e1[1] - e2[1] * e1[0])
        for j in range(fh):
            for i in range(fw):
                c = texture.get(tx + i, ty + j)
                if c[3] == 0:
                    continue
                pts = []
                for (a, b) in ((i, j), (i + 1, j), (i + 1, j + 1), (i, j + 1)):
                    s, t = a / fw, b / fh
                    pts.append(tuple(
                        tl[k] * (1 - s) * (1 - t) + tr[k] * s * (1 - t) + br[k] * s * t + bl[k] * (1 - s) * t
                        for k in range(3)))
                yield pts, c, normal


def _fill(canvas, pts, color):
    xs = [p[0] for p in pts]
    ys = [p[1] for p in pts]
    area = sum(pts[i][0] * pts[(i + 1) % 4][1] - pts[(i + 1) % 4][0] * pts[i][1] for i in range(4))
    if abs(area) < 1e-9:
        return
    sign = 1 if area > 0 else -1
    for py in range(max(0, int(min(ys))), min(canvas.h, int(max(ys)) + 1)):
        for px in range(max(0, int(min(xs))), min(canvas.w, int(max(xs)) + 1)):
            cx, cy = px + 0.5, py + 0.5
            if all(
                sign * ((pts[(i + 1) % 4][0] - pts[i][0]) * (cy - pts[i][1]) - (pts[(i + 1) % 4][1] - pts[i][1]) * (cx - pts[i][0])) >= -1e-9
                for i in range(4)
            ):
                canvas.set(px, py, color)


def render(model, texture, size=(160, 200), yaw=25, pitch=-15, pose=None, background=(0, 0, 0, 0), light=True):
    """Orthographic render of `model` with `texture` (a Canvas). Yaw turns the
    model to the viewer's left; negative pitch looks down on it."""
    transforms = bone_transforms(model, pose)
    cam = _mm(_rot(-pitch, 0, 0), _rot(0, yaw, 0))
    quads = []
    for bone in model.bones:
        for cube in bone.cubes:
            for pts, c, normal in _cube_quads(cube, transforms[bone.name], texture):
                cp = [_mv(cam, p) for p in pts]
                n = _mv(cam, normal)
                if n[2] >= 0:  # facing away (the viewer looks along +Z)
                    continue
                quads.append((sum(p[2] for p in cp) / 4, cp, c, n))
    canvas = Canvas(*size, background)
    if not quads:
        return canvas
    xs = [-p[0] for _, cp, _, _ in quads for p in cp]
    ys = [-p[1] for _, cp, _, _ in quads for p in cp]
    scale = min((size[0] - 8) / max(max(xs) - min(xs), 1e-6), (size[1] - 8) / max(max(ys) - min(ys), 1e-6))
    cx = size[0] / 2 - scale * (max(xs) + min(xs)) / 2
    cy = size[1] / 2 - scale * (max(ys) + min(ys)) / 2
    lx, ly, lz = (-0.35, 0.65, -0.68)
    for _, cp, c, n in sorted(quads, key=lambda q: -q[0]):
        if c[3] < 255:  # emissive texels ignore lighting
            color = c[:3]
        elif light:
            nl = math.sqrt(n[0] ** 2 + n[1] ** 2 + n[2] ** 2) or 1
            k = 0.5 + 0.55 * max(0.0, (n[0] * lx + n[1] * ly + n[2] * lz) / nl)
            color = tuple(clamp(ch * k) for ch in c[:3])
        else:
            color = c[:3]
        _fill(canvas, [(cx - p[0] * scale, cy - p[1] * scale) for p in cp], color)
    return canvas

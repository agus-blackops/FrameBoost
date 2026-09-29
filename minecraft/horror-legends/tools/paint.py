"""Hand-painted-looking pixel art, procedurally.

Every material is a short colour ramp (dark to light) with hue shifting:
shadows lean cool and saturated, highlights warm and pale, the way pixel
artists pick their shades. A painter works out a light value for each texel
(the form of the face, soft noise, the detail it is drawing) and the ramp
turns it into a colour, with ordered dithering between neighbouring shades
instead of random speckle.
"""

import colorsys
import math
import random

BAYER = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]


def _hex(c):
    if isinstance(c, str):
        c = c.lstrip("#")
        return tuple(int(c[i:i + 2], 16) for i in (0, 2, 4))
    return tuple(c[:3])


class Ramp:
    """`n` shades around a base colour. `spread` is how far the ends go in
    lightness, `hue` how far they swing in hue (towards blue in the shadows,
    towards yellow in the light)."""

    def __init__(self, base=None, n=6, spread=0.5, hue=0.05, sat=0.25, colors=None):
        if colors:
            self.colors = [_hex(c) for c in colors]
            return
        r, g, b = (v / 255 for v in _hex(base))
        h, l, s = colorsys.rgb_to_hls(r, g, b)
        out = []
        for i in range(n):
            t = i / (n - 1) * 2 - 1  # -1 darkest .. 1 lightest
            lh = min(0.97, max(0.02, l + t * spread * (l if t < 0 else 1 - l)))
            hh = (h - hue * t * (1 if h < 0.45 or h > 0.9 else -1)) % 1.0
            ss = min(1.0, max(0.0, s * (1 - sat * t)))
            out.append(tuple(round(v * 255) for v in colorsys.hls_to_rgb(hh, lh, ss)))
        self.colors = out

    def __call__(self, v, x=0, y=0):
        """Colour for light value v (0..1) at texel (x, y), dithered."""
        n = len(self.colors)
        f = min(max(v, 0.0), 0.9999) * (n - 1)
        i = int(f)
        if i >= n - 1:
            return self.colors[-1]
        threshold = (BAYER[y % 4][x % 4] + 0.5) / 16
        return self.colors[i + 1] if f - i > threshold else self.colors[i]


def form(face, x, y, fw, fh, top=0.08, rim=0.12):
    """How much light a texel catches from the shape of its face: brighter
    towards the top, darker round the rim (ambient occlusion), top faces
    lifted and bottom faces sunk."""
    v = top * (1 - 2 * y / max(1, fh - 1)) if face not in ("top", "bottom") else 0.0
    edge = min(x, fw - 1 - x, y, fh - 1 - y)
    if fw >= 4 and fh >= 4:
        v -= rim if edge == 0 else rim * 0.4 if edge == 1 else 0.0
    return v + {"top": 0.12, "bottom": -0.22}.get(face, 0.0)


class Noise:
    """Smooth value noise in 0..1."""

    def __init__(self, seed):
        rng = random.Random(seed)
        self.table = [rng.random() for _ in range(4096)]

    def _at(self, ix, iy):
        return self.table[hash((ix, iy, 11)) % 4096]

    def __call__(self, x, y, scale=4.0, octaves=2):
        total, amp, norm = 0.0, 1.0, 0.0
        for _ in range(octaves):
            fx, fy = x / scale, y / scale
            ix, iy = math.floor(fx), math.floor(fy)
            tx, ty = fx - ix, fy - iy
            tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
            a = self._at(ix, iy) * (1 - tx) + self._at(ix + 1, iy) * tx
            b = self._at(ix, iy + 1) * (1 - tx) + self._at(ix + 1, iy + 1) * tx
            total += (a * (1 - ty) + b * ty) * amp
            norm += amp
            amp *= 0.5
            scale /= 2
        return total / norm

    def ridge(self, x, y, scale=4.0, width=0.06):
        """1 on thin winding lines (cracks, veins), 0 elsewhere."""
        return max(0.0, 1 - abs(self(x, y, scale, 1) - 0.5) / width)


def glow(c, alpha=40):
    """A colour that glows under entity_emissive_alpha (alpha < 255)."""
    return (*_hex(c), alpha)

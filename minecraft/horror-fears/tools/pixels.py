"""Pixel-art primitives: an RGBA canvas with a minimal PNG encoder, colour
helpers, and box-UV painting for Bedrock entity textures. Standard library
only."""

import struct
import zlib


class Canvas:
    def __init__(self, w, h, fill=(0, 0, 0, 0)):
        self.w, self.h = w, h
        self.px = [fill] * (w * h)

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y * self.w + x] = c if len(c) == 4 else (*c, 255)

    def get(self, x, y):
        return self.px[y * self.w + x]

    def rect(self, x, y, w, h, c):
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                self.set(xx, yy, c)

    def blend(self, x, y, c, a):
        """Mix colour `c` over the pixel at (x, y) with opacity `a` (0..1)."""
        if 0 <= x < self.w and 0 <= y < self.h:
            base = self.get(x, y)
            self.px[y * self.w + x] = (*mix(base, c, a), base[3] if len(base) == 4 else 255)

    def paste(self, other, ox, oy):
        """Alpha-composite another canvas onto this one."""
        for y in range(other.h):
            for x in range(other.w):
                c = other.get(x, y)
                if c[3]:
                    self.blend(ox + x, oy + y, c[:3], c[3] / 255)

    def save(self, path):
        raw = bytearray()
        for y in range(self.h):
            raw.append(0)
            for x in range(self.w):
                raw.extend(self.px[y * self.w + x])

        def chunk(tag, data):
            body = tag + data
            return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

        png = b"\x89PNG\r\n\x1a\n"
        png += chunk(b"IHDR", struct.pack(">IIBBBBB", self.w, self.h, 8, 6, 0, 0, 0))
        png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
        png += chunk(b"IEND", b"")
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(png)


def clamp(v):
    return max(0, min(255, int(round(v))))


def shade(c, f):
    return tuple(clamp(v * f) for v in c[:3])


def mix(a, b, t):
    return tuple(clamp(a[i] * (1 - t) + b[i] * t) for i in range(3))


def jitter(rng, c, amount):
    d = rng.randint(-amount, amount)
    return tuple(clamp(v + d) for v in c[:3])


def line(cv, x0, y0, x1, y1, c):
    steps = max(abs(x1 - x0), abs(y1 - y0), 1)
    for i in range(steps + 1):
        cv.set(round(x0 + (x1 - x0) * i / steps), round(y0 + (y1 - y0) * i / steps), c)


def box_faces(u, v, w, h, d):
    """Box-UV regions of a w x h x d cube whose texture starts at (u, v).
    `front` is the -Z face (where Bedrock models look), `right` is the model's
    right side."""
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + 2 * d + w, v + d, w, h),
    }


def paint_box(cv, u, v, w, h, d, fn):
    """Paint every face of a box-UV cube. `fn(face, x, y, fw, fh)` returns a
    colour, or None to leave the pixel as it is."""
    for face, (x0, y0, fw, fh) in box_faces(u, v, w, h, d).items():
        for y in range(fh):
            for x in range(fw):
                c = fn(face, x, y, fw, fh)
                if c is not None:
                    cv.set(x0 + x, y0 + y, c)

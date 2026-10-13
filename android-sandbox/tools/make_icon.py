#!/usr/bin/env python3
"""Trastazo launcher icon: a ragdoll blasted out of a starburst. Plain Python, no dependencies."""
import math, os, struct, sys, zlib

def lerp(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(len(a)))

def render(size):
    S = 3
    N = size * S
    u = N / 100.0
    img = [[(0.0, 0.0, 0.0, 0.0)] * N for _ in range(N)]

    def blend(x, y, c):
        if not (0 <= x < N and 0 <= y < N):
            return
        r, g, b, a = img[y][x]
        ca = c[3]
        na = ca + a * (1 - ca)
        if na <= 0:
            return
        img[y][x] = ((c[0] * ca + r * a * (1 - ca)) / na, (c[1] * ca + g * a * (1 - ca)) / na,
                     (c[2] * ca + b * a * (1 - ca)) / na, na)

    # rounded tile with a radial gradient (deep violet -> navy)
    rad = N * 0.23
    cx0, cy0 = N * 0.5, N * 0.42
    for y in range(N):
        for x in range(N):
            dx = max(rad - x, x - (N - 1 - rad), 0)
            dy = max(rad - y, y - (N - 1 - rad), 0)
            if dx * dx + dy * dy <= rad * rad:
                t = min(1.0, math.hypot(x - cx0, y - cy0) / (N * 0.75))
                c = lerp((0.36, 0.17, 0.55), (0.07, 0.08, 0.18), t)
                img[y][x] = (c[0], c[1], c[2], 1.0)

    def poly_fill(pts, col):
        xs = [p[0] for p in pts]; ys = [p[1] for p in pts]
        for y in range(int(min(ys)), int(max(ys)) + 1):
            for x in range(int(min(xs)), int(max(xs)) + 1):
                inside = False
                j = len(pts) - 1
                for i in range(len(pts)):
                    xi, yi = pts[i]; xj, yj = pts[j]
                    if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi + 1e-9) + xi:
                        inside = not inside
                    j = i
                if inside and img[y][x][3] > 0:
                    blend(x, y, col)

    def star(cx, cy, r0, r1, n, col, rot=0.0):
        pts = []
        for i in range(n * 2):
            a = rot + i * math.pi / n
            r = r1 if i % 2 == 0 else r0
            pts.append((cx + math.cos(a) * r, cy + math.sin(a) * r))
        poly_fill(pts, col)

    # starburst (blast) behind the figure
    star(50 * u, 46 * u, 20 * u, 44 * u, 11, (0.95, 0.35, 0.18, 1.0), 0.15)
    star(50 * u, 46 * u, 15 * u, 32 * u, 11, (1.0, 0.62, 0.15, 1.0), 0.45)
    star(50 * u, 46 * u, 10 * u, 19 * u, 9, (1.0, 0.9, 0.45, 1.0), 0.1)

    def disc(cx, cy, r, col):
        for y in range(int(cy - r - 1), int(cy + r + 2)):
            for x in range(int(cx - r - 1), int(cx + r + 2)):
                d = math.hypot(x - cx, y - cy)
                if d <= r and 0 <= x < N and 0 <= y < N and img[y][x][3] > 0:
                    blend(x, y, col)

    def capsule(x0, y0, x1, y1, r, col):
        n = int(math.hypot(x1 - x0, y1 - y0) / max(1.0, r * 0.35)) + 2
        for i in range(n + 1):
            t = i / n
            disc(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, r, col)

    # tumbling ragdoll (white with a dark outline), flying up-right
    P = {k: (v[0] * u, v[1] * u) for k, v in {
        'head': (63, 25), 'chest': (54, 37), 'pelvis': (43, 49),
        'eL': (44, 30), 'hL': (36, 21), 'eR': (66, 44), 'hR': (76, 38),
        'kL': (35, 60), 'fL': (25, 59), 'kR': (51, 61), 'fR': (52, 74)}.items()}
    bones = [('pelvis', 'kL', 5.2), ('kL', 'fL', 4.6), ('pelvis', 'kR', 5.2), ('kR', 'fR', 4.6),
             ('chest', 'eL', 4.0), ('eL', 'hL', 3.6), ('chest', 'pelvis', 7.4), ('chest', 'eR', 4.0), ('eR', 'hR', 3.6)]
    outline, body = (0.08, 0.06, 0.14, 1.0), (0.98, 0.97, 1.0, 1.0)
    for passno in range(2):
        for a, b, r in bones:
            capsule(*P[a], *P[b], (r + (1.3 if passno == 0 else 0)) * u, outline if passno == 0 else body)
        disc(*P['head'], (10.2 + (1.3 if passno == 0 else 0)) * u, outline if passno == 0 else body)
    # X eyes
    hx, hy = P['head']
    for sgn in (-1, 1):
        capsule(hx + 1.6 * u, hy - 3.2 * u, hx + 5.6 * u, hy + 0.6 * u if sgn > 0 else hy - 3.2 * u, 0.9 * u, outline) if False else None
    capsule(hx + 1.5 * u, hy - 3.5 * u, hx + 5.5 * u, hy + 0.5 * u, 0.95 * u, outline)
    capsule(hx + 1.5 * u, hy + 0.5 * u, hx + 5.5 * u, hy - 3.5 * u, 0.95 * u, outline)
    # motion streaks
    for (x0, y0, x1, y1) in ((18, 70, 30, 64), (14, 58, 24, 53), (30, 82, 40, 75)):
        capsule(x0 * u, y0 * u, x1 * u, y1 * u, 1.4 * u, (1.0, 1.0, 1.0, 0.55))
    # flying debris
    for (x, y, r, c) in ((80, 22, 2.6, (1.0, 0.8, 0.3, 1)), (84, 60, 2.0, (0.95, 0.35, 0.2, 1)), (20, 26, 2.2, (1.0, 0.65, 0.2, 1))):
        disc(x * u, y * u, r * u, c)

    rows = []
    for y in range(size):
        row = bytearray([0])
        for x in range(size):
            r = g = b = a = 0.0
            for j in range(S):
                for i in range(S):
                    pr, pg, pb, pa = img[y * S + j][x * S + i]
                    r += pr * pa; g += pg * pa; b += pb * pa; a += pa
            if a > 0:
                row += bytes((int(r / a * 255), int(g / a * 255), int(b / a * 255), int(a / (S * S) * 255)))
            else:
                row += bytes((0, 0, 0, 0))
        rows.append(bytes(row))
    return rows

def png(path, size):
    rows = render(size)
    def chunk(t, d):
        c = struct.pack('>I', len(d)) + t + d
        return c + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    data = (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', size, size, 8, 6, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(b''.join(rows), 9)) + chunk(b'IEND', b''))
    with open(path, 'wb') as f:
        f.write(data)

if __name__ == '__main__':
    out = sys.argv[1]
    for name, size in (('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)):
        os.makedirs(f'{out}/mipmap-{name}', exist_ok=True)
        png(f'{out}/mipmap-{name}/ic_launcher.png', size)
    if len(sys.argv) > 2:
        png(sys.argv[2], 512)

#!/usr/bin/env python3
"""Draws the launcher icon (a ragdoll on a dark tile) with plain Python, no dependencies."""
import math, struct, zlib, sys

def render(size):
    S = 4  # supersampling
    N = size * S
    px = [[(0, 0, 0, 0)] * N for _ in range(N)]

    def put(x, y, c):
        if 0 <= x < N and 0 <= y < N:
            px[y][x] = c

    def circle(cx, cy, r, c):
        for y in range(int(cy - r), int(cy + r) + 1):
            for x in range(int(cx - r), int(cx + r) + 1):
                if (x - cx) ** 2 + (y - cy) ** 2 <= r * r:
                    put(x, y, c)

    def capsule(x0, y0, x1, y1, r, c):
        n = int(math.hypot(x1 - x0, y1 - y0) / (r * 0.4)) + 2
        for i in range(n + 1):
            t = i / n
            circle(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, r, c)

    # rounded dark tile with a floor
    rad = N * 0.22
    for y in range(N):
        for x in range(N):
            dx = max(rad - x, x - (N - 1 - rad), 0)
            dy = max(rad - y, y - (N - 1 - rad), 0)
            if dx * dx + dy * dy <= rad * rad:
                t = y / N
                px[y][x] = (int(46 - 14 * t), int(56 - 14 * t), int(72 - 16 * t), 255)
    floor_y = int(N * 0.8)
    for y in range(floor_y, N):
        for x in range(N):
            if px[y][x][3]:
                px[y][x] = (84, 90, 102, 255)

    u = N / 100.0
    skin, shirt, pants, shoe, outline = (241, 199, 160, 255), (233, 80, 70, 255), (44, 62, 96, 255), (40, 40, 46, 255), (20, 22, 28, 255)
    # a tumbling ragdoll
    pts = {
        'head': (58 * u, 30 * u), 'chest': (49 * u, 45 * u), 'pelvis': (41 * u, 60 * u),
        'elbowL': (33 * u, 48 * u), 'handL': (24 * u, 40 * u), 'elbowR': (64 * u, 52 * u), 'handR': (76 * u, 46 * u),
        'kneeL': (52 * u, 70 * u), 'footL': (64 * u, 76 * u), 'kneeR': (30 * u, 72 * u), 'footR': (22 * u, 77 * u),
    }
    parts = [
        ('pelvis', 'kneeR', 5.4, pants), ('kneeR', 'footR', 4.6, pants), ('pelvis', 'kneeL', 5.4, pants), ('kneeL', 'footL', 4.6, pants),
        ('chest', 'elbowL', 4, shirt), ('elbowL', 'handL', 3.6, skin), ('chest', 'pelvis', 7.5, shirt),
        ('chest', 'elbowR', 4, shirt), ('elbowR', 'handR', 3.6, skin),
    ]
    for pass_ in range(2):
        for a, b, r, col in parts:
            capsule(*pts[a], *pts[b], (r + (1.1 if pass_ == 0 else 0)) * u, outline if pass_ == 0 else col)
    for f in ('footL', 'footR'):
        circle(*pts[f], 4.6 * u, shoe)
    circle(*pts['head'], 10.6 * u, outline)
    circle(*pts['head'], 9.4 * u, skin)
    circle(pts['head'][0] - 1.5 * u, pts['head'][1] - 3 * u, 8 * u, (42, 29, 20, 255))
    circle(pts['head'][0] + 2.5 * u, pts['head'][1] + 0.5 * u, 6.2 * u, skin)
    circle(pts['head'][0] + 4.6 * u, pts['head'][1] - 0.4 * u, 1.2 * u, (20, 20, 20, 255))
    # blood splat
    for dx, dy, r in ((70, 79, 3.2), (78, 80, 2), (62, 80, 1.6), (50, 81, 2.4), (14, 80, 2)):
        circle(dx * u, dy * u, r * u, (183, 20, 20, 255))

    # downsample
    rows = []
    for y in range(size):
        row = bytearray([0])
        for x in range(size):
            r = g = b = a = 0
            for j in range(S):
                for i in range(S):
                    c = px[y * S + j][x * S + i]
                    r += c[0] * c[3]; g += c[1] * c[3]; b += c[2] * c[3]; a += c[3]
            if a:
                row += bytes((r // a, g // a, b // a, a // (S * S)))
            else:
                row += bytes((0, 0, 0, 0))
        rows.append(bytes(row))
    return rows

def png(path, size):
    rows = render(size)
    def chunk(t, d):
        c = struct.pack('>I', len(d)) + t + d
        return c + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    data = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', size, size, 8, 6, 0, 0, 0)) \
        + chunk(b'IDAT', zlib.compress(b''.join(rows), 9)) + chunk(b'IEND', b'')
    open(path, 'wb').write(data)

if __name__ == '__main__':
    out = sys.argv[1]
    for name, size in (('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)):
        import os
        os.makedirs(f'{out}/mipmap-{name}', exist_ok=True)
        png(f'{out}/mipmap-{name}/ic_launcher.png', size)

#!/usr/bin/env python3
"""Fondo animado de Clawd (Galaxy A51, 1080x2400, 10 s en bucle perfecto).

Uso:  python3 clawd-theme/tools/animate.py            # video + GIF + 3 fotos fijas
      python3 clawd-theme/tools/animate.py --frame 0.625 salida.png   # un solo cuadro
Requiere Pillow, numpy y ffmpeg.
"""
import math, os, subprocess, sys
from multiprocessing import Pool
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
W, H = 1080, 2400
FPS, DUR = 30, 10
N = FPS * DUR
YH = 1450            # horizonte
S = 40               # tamaño del pixel de Clawd
FEET = 1790          # suelo de Clawd
SUN_C, SUN_R = (540, 1210), 340

ORANGE, ORANGE_DARK, EYE, OUTLINE = (217, 119, 87), (178, 90, 62), (24, 22, 21), (36, 10, 60)

# --------------------------------------------------------------------------- temas de color
THEMES = [
    dict(name="atardecer",
         sky=[(0, (35, 10, 80)), (.5, (120, 20, 120)), (.8, (240, 60, 110)), (.95, (255, 140, 90)), (1, (255, 205, 100))],
         ground=((60, 12, 90), (14, 4, 40)), sun=((255, 235, 100), (255, 55, 125)), grid=(255, 70, 210),
         mount=(70, 16, 96), hline=(255, 240, 150)),
    dict(name="aurora",
         sky=[(0, (6, 16, 60)), (.5, (10, 70, 135)), (.8, (20, 165, 170)), (.95, (110, 235, 170)), (1, (190, 255, 210))],
         ground=((8, 40, 80), (3, 10, 32)), sun=((205, 255, 235), (70, 190, 255)), grid=(70, 255, 225),
         mount=(6, 42, 88), hline=(190, 255, 240)),
    dict(name="ultravioleta",
         sky=[(0, (10, 20, 95)), (.5, (62, 62, 225)), (.8, (165, 100, 255)), (.95, (255, 150, 235)), (1, (255, 200, 240))],
         ground=((40, 18, 120), (8, 4, 40)), sun=((255, 250, 175), (255, 110, 215)), grid=(130, 205, 255),
         mount=(26, 16, 110), hline=(255, 215, 250)),
]


def smoothstep(x):
    x = min(1.0, max(0.0, x))
    return x * x * (3 - 2 * x)


def weights(t):
    s = (t % 1.0) * 3
    k = int(s) % 3
    f = smoothstep((s - int(s) - 0.25) / 0.5)
    w = [0.0, 0.0, 0.0]
    w[k], w[(k + 1) % 3] = 1 - f, f
    return w


def _rows(theme):
    ys = np.arange(YH) / YH
    sky = np.stack([np.interp(ys, [p for p, _ in theme["sky"]], [c[i] for _, c in theme["sky"]]) for i in range(3)], 1)
    g0, g1 = np.array(theme["ground"][0], float), np.array(theme["ground"][1], float)
    gt = (np.arange(H - YH) / (H - YH))[:, None]
    return np.concatenate([sky, g0 * (1 - gt) + g1 * gt])


def _sun_rows(theme):
    a, b = np.array(theme["sun"][0], float), np.array(theme["sun"][1], float)
    t = (np.arange(SUN_C[1] - SUN_R, YH) - (SUN_C[1] - SUN_R)) / (2 * SUN_R)
    return a * (1 - t[:, None]) + b * t[:, None]


ROWS = [_rows(t) for t in THEMES]
SUN_ROWS = [_sun_rows(t) for t in THEMES]
SUN_Y0, SUN_X0, SUN_X1 = SUN_C[1] - SUN_R, SUN_C[0] - SUN_R, SUN_C[0] + SUN_R
_yy, _xx = np.mgrid[SUN_Y0:YH, SUN_X0:SUN_X1]
SUN_MASK = (_yy - SUN_C[1]) ** 2 + (_xx - SUN_C[0]) ** 2 <= SUN_R ** 2

# montañas en bloques de 12 px
_b = np.arange(W // 12 + 1)
_h = 70 + 55 * np.sin(_b * .31 + 1) + 40 * np.sin(_b * .13 + 2) + 25 * np.sin(_b * .77)
MOUNT_H = (np.maximum(_h, 20) // 12 * 12).astype(int)
MOUNT_COL_H = np.repeat(MOUNT_H, 12)[:W]

rng = np.random.default_rng(7)
STARS = []
for _ in range(120):
    x, y = int(rng.integers(20, W - 20)), int(rng.integers(40, 1380))
    if (x - SUN_C[0]) ** 2 + (y - SUN_C[1]) ** 2 < (SUN_R + 14) ** 2:
        continue
    STARS.append((x, y, int(rng.integers(1, 4)), float(rng.random()), int(rng.integers(3, 6)) * (1 if rng.random() < .8 else 2)))
CLOUDS = [(100, 780, 22, 1, 70), (600, 960, 16, 2, 60), (300, 1180, 26, 1, 80), (850, 700, 14, 1, 55),
          (-50, 1300, 18, 2, 65), (700, 1110, 20, 1, 75)]
PCOLS = [(255, 214, 90), (255, 120, 200), (110, 240, 255), (255, 160, 80), (200, 160, 255)]
PARTS = []
for i in range(30):
    PARTS.append((int(rng.integers(60, W - 60)), float(rng.random()), int(rng.integers(1, 3)),
                  [8, 12, 16][i % 3], PCOLS[i % 5], int(rng.integers(15, 50)), int(rng.integers(1, 3)), float(rng.random())))


def mixed(arrs, w):
    return sum(wk * a for wk, a in zip(w, arrs))


def base_frame(t, w):
    rows = mixed(ROWS, w)
    arr = np.empty((H, W, 3), np.uint8)
    arr[:] = rows[:, None, :].astype(np.uint8)
    # sol con rendijas que bajan
    P = 48.0
    ys = np.arange(SUN_Y0, YH)
    rel = np.clip((ys - 1150) / 300.0, 0, 1)
    phase = ((ys - t * P) % P) / P
    cut = (ys >= 1150) & (phase < 0.10 + 0.5 * rel)
    sun = SUN_MASK & ~cut[:, None]
    reg = arr[SUN_Y0:YH, SUN_X0:SUN_X1]
    col = mixed(SUN_ROWS, w).astype(np.uint8)
    reg[sun] = np.broadcast_to(col[:, None, :], reg.shape)[sun]
    # montañas
    mc = mixed([np.array(th["mount"], float) for th in THEMES], w).astype(np.uint8)
    ys2 = np.arange(YH - 200, YH)[:, None]
    mm = ys2 >= (YH - MOUNT_COL_H)[None, :]
    arr[YH - 200:YH][mm] = mc
    hl = mixed([np.array(th["hline"], float) for th in THEMES], w).astype(np.uint8)
    arr[YH - 2:YH + 3] = hl
    return Image.fromarray(arr), mixed([np.array(th["grid"], float) for th in THEMES], w)


def draw_grid(img, t, gc):
    d = ImageDraw.Draw(img)
    gc = tuple(int(v) for v in gc)
    glow = tuple(int(v * 0.32) for v in gc)
    NL = 22
    lines = []
    for k in range(NL):
        u = (k + t) / NL
        lines.append((YH + (H - YH) * u ** 2, 2 + int(8 * u)))
    for j in range(-12, 13):
        xe = W / 2 + j * 210
        d.line([(W / 2, YH + 6), (xe, H)], fill=glow, width=11)
    for y, wd in lines:
        d.line([(0, y), (W, y)], fill=glow, width=wd * 3 + 2)
    for j in range(-12, 13):
        xe = W / 2 + j * 210
        d.line([(W / 2, YH + 6), (xe, H)], fill=gc, width=3)
    for y, wd in lines:
        d.line([(0, y), (W, y)], fill=gc, width=wd)


def cloud(d, x, y, u, a):
    col = (255, 232, 248, a)
    for dx, dy, wc, hc in [(0, 2, 8, 2), (1, 1, 6, 1), (2, 0, 3, 1), (4, 0, 2, 1)]:
        d.rectangle([x + dx * u, y + dy * u, x + (dx + wc) * u - 1, y + (dy + hc) * u - 1], fill=col)


def plus(d, x, y, s, col):
    for dx, dy in [(0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)]:
        d.rectangle([x + dx * s, y + dy * s, x + dx * s + s - 1, y + dy * s + s - 1], fill=col)


# --------------------------------------------------------------------------- Clawd
POSES = {}


def clawd_img(left_up, right_up, blink):
    key = (left_up, right_up, blink)
    if key in POSES:
        return POSES[key]
    rows = ["..XXXXXXXXXXX..", "..XXXXXXXXXXX..", "..XXEXXXXXEXX..", "..XXEXXXXXEXX..",
            "..XXXXXXXXXXX..", "..XXXXXXXXXXX..", "...X.X...X.X...", "...X.X...X.X..."]
    grid = [list(r) for r in rows]
    for cols, up in (((0, 1), left_up), ((13, 14), right_up)):
        for c in cols:
            for r in ((0, 1) if up else (3, 4)):
                grid[r][c] = "X"
    pad, o = 12, 8
    img = Image.new("RGBA", (15 * S + pad * 2, 8 * S + pad * 2), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for r, row in enumerate(grid):
        for c, ch in enumerate(row):
            if ch == ".":
                continue
            x, y = pad + c * S, pad + r * S
            eye = ch == "E" and not blink
            d.rectangle([x, y, x + S - 1, y + S - 1], fill=EYE if eye else ORANGE)
    if blink:
        for c in (4, 10):
            d.rectangle([pad + c * S - 6, pad + 2.5 * S + 6, pad + c * S + S + 5, pad + 2.5 * S + 16], fill=EYE)
    for c in range(2, 13):
        x = pad + c * S
        d.rectangle([x, pad + 6 * S - 10, x + S - 1, pad + 6 * S - 1], fill=ORANGE_DARK)
    alpha = img.getchannel("A").filter(ImageFilter.MaxFilter(2 * o + 1))
    out = Image.new("RGBA", img.size, OUTLINE + (0,))
    out.putalpha(alpha)
    out.alpha_composite(img)
    POSES[key] = out
    return out


def clawd_state(time):
    p = (time % 2.5) / 2.5
    if p < 0.5:
        x = p / 0.5
        hop, sq = 4 * x * (1 - x), 0.0
        left = right = True
    else:
        hop = 0.0
        sq = 0.16 * (1 - (p - 0.5) / 0.08) if p < 0.58 else (0.10 * (p - 0.94) / 0.06 if p > 0.94 else 0.0)
        left = False
        right = 0.62 < p < 0.92 and int((p - 0.62) / 0.05) % 2 == 0
    blink = (1.0 <= time < 1.18) or (6.0 <= time < 6.18)
    return hop, sq, left, right, blink


# --------------------------------------------------------------------------- cuadro
def render(time):
    t = (time % DUR) / DUR
    w = weights(t)
    img, gc = base_frame(t, w)
    draw_grid(img, t, gc)
    img = img.convert("RGBA")

    ov = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(ov)
    for x, y, k, ph, sz in STARS:
        tw = 0.25 + 0.75 * (0.5 + 0.5 * math.sin(2 * math.pi * (k * t + ph)))
        fade = min(1.0, max(0.0, (1340 - y) / 500))
        a = int(255 * tw * fade)
        if a > 8:
            if sz >= 6:
                plus(d, x, y, sz // 2, (255, 255, 255, a))
            else:
                d.rectangle([x, y, x + sz - 1, y + sz - 1], fill=(255, 255, 255, a))
    for x0, y0, u, loops, a in CLOUDS:
        x = ((x0 + t * loops * (W + 400)) % (W + 400)) - 200
        cloud(d, int(x), y0, u, a)
    img = Image.alpha_composite(img, ov)

    hop, sq, lu, ru, blink = clawd_state(time)
    spr = clawd_img(lu, ru, blink)
    sw, sh = spr.size
    nw, nh = int(sw * (1 + sq * 0.9)), int(sh * (1 - sq))
    if (nw, nh) != (sw, sh):
        spr = spr.resize((nw, nh), Image.NEAREST)
    # sombra
    sh_ov = Image.new("RGBA", (800, 200), (0, 0, 0, 0))
    k = 1 - 0.35 * hop
    ImageDraw.Draw(sh_ov).ellipse([400 - 250 * k, 100 - 34 * k, 400 + 250 * k, 100 + 34 * k],
                                  fill=(0, 0, 20, int(200 - 70 * hop)))
    sh_ov = sh_ov.filter(ImageFilter.GaussianBlur(12))
    img.alpha_composite(sh_ov, (W // 2 - 400, FEET + 14 - 100))
    top = int(FEET - 12 - nh + 12 - hop * 190 + 0)
    img.alpha_composite(spr, ((W - nw) // 2, top))

    ov2 = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d2 = ImageDraw.Draw(ov2)
    for x0, y0, loops, sz, col, amp, sk, ph in PARTS:
        fr = (y0 + t * loops) % 1.0
        y = 1900 - fr * 1600
        x = x0 + amp * math.sin(2 * math.pi * (sk * t + ph))
        a = int(255 * math.sin(math.pi * fr) ** 0.6)
        plus(d2, int(x), int(y), sz // 2, col + (a,))
    img = Image.alpha_composite(img, ov2)
    return img.convert("RGB")


def _job(i):
    return render(i / FPS).tobytes()


def run_ffmpeg(args, stdin=None):
    return subprocess.Popen(["ffmpeg", "-y", "-loglevel", "error"] + args, stdin=stdin)


def main():
    if len(sys.argv) >= 4 and sys.argv[1] == "--frame":
        render(float(sys.argv[2])).save(sys.argv[3])
        return
    adir = os.path.join(ROOT, "animated")
    os.makedirs(adir, exist_ok=True)
    mp4 = os.path.join(adir, "clawd-neon-1080x2400.mp4")
    ff = run_ffmpeg(["-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-r", str(FPS), "-i", "-",
                     "-c:v", "libx264", "-preset", "slow", "-crf", "19", "-pix_fmt", "yuv420p",
                     "-movflags", "+faststart", mp4], stdin=subprocess.PIPE)
    with Pool(4) as pool:
        for i, raw in enumerate(pool.imap(_job, range(N), chunksize=2)):
            ff.stdin.write(raw)
            if i % 60 == 0:
                print("cuadro", i, "/", N, flush=True)
    ff.stdin.close()
    ff.wait()
    # versión liviana para la vista previa web y GIF para compartir
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", mp4, "-vf", "scale=450:1000:flags=neighbor",
                    "-c:v", "libx264", "-crf", "29", "-preset", "slow", "-pix_fmt", "yuv420p", "-an",
                    "-movflags", "+faststart", os.path.join(adir, "vista-previa.mp4")], check=True)
    pal = os.path.join(adir, "_pal.png")
    vf = "fps=15,scale=360:800:flags=neighbor"
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", mp4, "-vf", vf + ",palettegen=max_colors=128", pal], check=True)
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", mp4, "-i", pal, "-lavfi",
                    vf + "[x];[x][1:v]paletteuse=dither=none", "-loop", "0",
                    os.path.join(adir, "clawd-neon-360x800.gif")], check=True)
    os.remove(pal)
    for name, tm in (("07-atardecer", 0.625), ("08-aurora", 3.125), ("09-ultravioleta", 5.625)):
        render(tm).save(os.path.join(ROOT, "wallpapers", f"{name}-1080x2400.png"), optimize=True)
    print("listo")


if __name__ == "__main__":
    main()

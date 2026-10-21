#!/usr/bin/env python3
"""Fondo animado de Clawd (Galaxy A51, 1080x2400, 15 s en bucle perfecto).

Guion del bucle (los colores cambian cada 5 s: atardecer -> aurora -> ultravioleta):
  0-5 s    Clawd salta dos veces (levanta los brazos, se aplasta al caer, levanta polvo)
  5-10 s   camina en el lugar, mira a los lados, saluda y escribe "> hola_" en un globo
  10-15 s  vuelve a saltar dos veces
Fondo: estrellas que titilan, estrellas fugaces, aurora, sol con rendijas, dos cordilleras
con paralaje, nubes, cuadrícula neón con el reflejo de Clawd, destellos y resplandor (bloom).

Uso:  python3 clawd-theme/tools/animate.py                      # video + GIF + 3 fotos fijas
      python3 clawd-theme/tools/animate.py --frame 6.5 salida.png  # un solo cuadro
Requiere Pillow, numpy y ffmpeg.
"""
import math, os, subprocess, sys
from multiprocessing import Pool
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
W, H = 1080, 2400
FPS, DUR = 30, 15
N = FPS * DUR
YH = 1450            # horizonte
S = 40               # tamaño del pixel de Clawd
FEET = 1790          # suelo de Clawd
SUN_C, SUN_R = (540, 1210), 340
MONO_B = "/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf"

ORANGE, ORANGE_DARK, ORANGE_LIGHT = (217, 119, 87), (178, 90, 62), (244, 158, 122)
EYE, OUTLINE, CREAM = (24, 22, 21), (36, 10, 60), (250, 244, 232)

# --------------------------------------------------------------------------- temas de color
THEMES = [
    dict(name="atardecer",
         sky=[(0, (35, 10, 80)), (.5, (120, 20, 120)), (.8, (240, 60, 110)), (.95, (255, 140, 90)), (1, (255, 205, 100))],
         ground=((60, 12, 90), (14, 4, 40)), sun=((255, 235, 100), (255, 55, 125)), grid=(255, 70, 210),
         mount=(70, 16, 96), far=(150, 40, 120), hline=(255, 240, 150), aurora=((255, 120, 200), (255, 190, 90))),
    dict(name="aurora",
         sky=[(0, (6, 16, 60)), (.5, (10, 70, 135)), (.8, (20, 165, 170)), (.95, (110, 235, 170)), (1, (190, 255, 210))],
         ground=((8, 40, 80), (3, 10, 32)), sun=((205, 255, 235), (70, 190, 255)), grid=(70, 255, 225),
         mount=(6, 42, 88), far=(20, 100, 130), hline=(190, 255, 240), aurora=((90, 255, 170), (150, 110, 255))),
    dict(name="ultravioleta",
         sky=[(0, (10, 20, 95)), (.5, (62, 62, 225)), (.8, (165, 100, 255)), (.95, (255, 150, 235)), (1, (255, 200, 240))],
         ground=((40, 18, 120), (8, 4, 40)), sun=((255, 250, 175), (255, 110, 215)), grid=(130, 205, 255),
         mount=(26, 16, 110), far=(100, 70, 200), hline=(255, 215, 250), aurora=((120, 200, 255), (255, 120, 230))),
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


def mixed(arrs, w):
    return sum(wk * np.asarray(a, float) for wk, a in zip(w, arrs))


def mixc(key, w):
    return mixed([th[key] for th in THEMES], w)


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

# cordilleras periódicas en 90 bloques de 12 px, para que el desplazamiento cierre el bucle
NB = W // 12
_b = np.arange(NB)
NEAR_H = (np.maximum(70 + 50 * np.sin(2 * np.pi * 3 * _b / NB + 1) + 38 * np.sin(2 * np.pi * 7 * _b / NB + 2)
                     + 22 * np.sin(2 * np.pi * 13 * _b / NB), 20) // 12 * 12).astype(int)
FAR_H = (np.maximum(150 + 70 * np.sin(2 * np.pi * 2 * _b / NB + 4) + 45 * np.sin(2 * np.pi * 5 * _b / NB + .5)
                    + 20 * np.sin(2 * np.pi * 11 * _b / NB + 3), 40) // 12 * 12).astype(int)

# viñeta suave
_vy, _vx = np.mgrid[0:H, 0:W]
VIGNETTE = (1 - 0.28 * (((_vx - W / 2) / (W / 2)) ** 2 * 0.6 + ((_vy - H * 0.55) / (H * 0.55)) ** 2 * 0.5)).clip(0.6, 1)
VIGNETTE = VIGNETTE.astype(np.float32)[:, :, None]
del _vy, _vx

rng = np.random.default_rng(7)
STARS = []
for _ in range(150):
    x, y = int(rng.integers(20, W - 20)), int(rng.integers(40, 1380))
    if (x - SUN_C[0]) ** 2 + (y - SUN_C[1]) ** 2 < (SUN_R + 14) ** 2:
        continue
    STARS.append((x, y, int(rng.integers(1, 5)), float(rng.random()),
                  int(rng.integers(3, 6)) * (1 if rng.random() < .82 else 2)))
CLOUDS = [(100, 780, 22, 1, 70), (600, 960, 16, 2, 60), (300, 1180, 26, 1, 80), (850, 700, 14, 1, 55),
          (-50, 1300, 18, 2, 65), (700, 1110, 20, 1, 75)]
PCOLS = [(255, 214, 90), (255, 120, 200), (110, 240, 255), (255, 160, 80), (200, 160, 255)]
PARTS = []
for i in range(36):
    PARTS.append((int(rng.integers(60, W - 60)), float(rng.random()), int(rng.integers(1, 4)),
                  [8, 12, 16][i % 3], PCOLS[i % 5], int(rng.integers(15, 50)), int(rng.integers(1, 3)), float(rng.random())))
SHOOTING = [(3.3, 900, 260), (8.1, 700, 420), (11.7, 1000, 330)]   # (inicio s, x, y)


def mountains(arr, heights, color, shift, top_band):
    cols = ((np.arange(W) + int(shift)) // 12) % NB
    ch = heights[cols]
    ys = np.arange(YH - top_band, YH)[:, None]
    m = ys >= (YH - ch)[None, :]
    arr[YH - top_band:YH][m] = color


def aurora_layer(t, w):
    """Cintas de aurora a baja resolución; se suavizan al escalar."""
    lw, lh = 270, 380                     # cubre y = 0..1520 a 4 px por celda
    ys = np.arange(lh)[:, None] * 4.0
    xs = np.arange(lw)[None, :]
    c1 = mixed([th["aurora"][0] for th in THEMES], w)
    c2 = mixed([th["aurora"][1] for th in THEMES], w)
    inten = 0.30 + 0.55 * w[1]
    out = np.zeros((lh, lw, 3), np.float32)
    for k, (base, amp, kx, kt, ph, col, th) in enumerate([
            (470, 70, 1, 1, 0.0, c1, 55), (640, 90, 2, -1, 1.7, c2, 45), (330, 50, 1, 2, 3.1, c1, 35)]):
        yc = base + amp * np.sin(2 * np.pi * (xs / lw * kx + t * kt + ph / 6.283)) \
            + 25 * np.sin(2 * np.pi * (xs / lw * 3 + t * 2 + ph))
        d = (ys - yc)
        a = np.exp(-(d / th) ** 2) * (d < 0) + np.exp(-(d / (th * 0.35)) ** 2) * (d >= 0)
        curtain = 0.6 + 0.25 * np.sin(xs * 0.11 + 2 * np.pi * t * (k + 1) + ph) + 0.15 * np.sin(xs * 0.047 - 2 * np.pi * t + ph)
        out += (a * curtain * inten)[:, :, None] * col[None, None, :] / 255.0
    img = Image.fromarray((np.clip(out, 0, 1) * 255).astype(np.uint8)).resize((W, lh * 4), Image.BILINEAR)
    return np.asarray(img, np.float32)


def base_frame(t, w):
    rows = mixed(ROWS, w)
    arr = np.empty((H, W, 3), np.float32)
    arr[:] = rows[:, None, :]
    au = aurora_layer(t, w)
    arr[:au.shape[0]] += au * (1 - np.clip((np.arange(au.shape[0]) - 1250) / 200, 0, 1))[:, None, None]
    arr = np.clip(arr, 0, 255).astype(np.uint8)
    # sol con rendijas que bajan; late un poco
    P = 48.0
    ys = np.arange(SUN_Y0, YH)
    rel = np.clip((ys - 1150) / 300.0, 0, 1)
    phase = ((ys - t * P * 3) % P) / P
    cut = (ys >= 1150) & (phase < 0.10 + 0.5 * rel)
    sun = SUN_MASK & ~cut[:, None]
    reg = arr[SUN_Y0:YH, SUN_X0:SUN_X1]
    col = np.clip(mixed(SUN_ROWS, w) * (1.0 + 0.04 * math.sin(2 * math.pi * t * 6)), 0, 255).astype(np.uint8)
    reg[sun] = np.broadcast_to(col[:, None, :], reg.shape)[sun]
    # dos cordilleras con paralaje
    mountains(arr, FAR_H, mixc("far", w).astype(np.uint8), t * W, 260)
    mountains(arr, NEAR_H, mixc("mount", w).astype(np.uint8), t * W * 2, 200)
    hl = mixc("hline", w).astype(np.uint8)
    arr[YH - 2:YH + 3] = hl
    return Image.fromarray(arr), mixc("grid", w), hl


def draw_grid(img, t, gc):
    d = ImageDraw.Draw(img)
    gc = tuple(int(v) for v in gc)
    glow = tuple(int(v * 0.32) for v in gc)
    NL = 22
    lines = []
    for k in range(NL):
        u = ((k + t * 3 * 2) % NL) / NL
        lines.append((YH + (H - YH) * u ** 2, 2 + int(8 * u)))
    for j in range(-12, 13):
        d.line([(W / 2, YH + 6), (W / 2 + j * 210, H)], fill=glow, width=11)
    for y, wd in lines:
        d.line([(0, y), (W, y)], fill=glow, width=wd * 3 + 2)
    for j in range(-12, 13):
        d.line([(W / 2, YH + 6), (W / 2 + j * 210, H)], fill=gc, width=3)
    for y, wd in lines:
        d.line([(0, y), (W, y)], fill=gc, width=wd)


def horizon_fog(img, hl):
    """Bruma de color sobre el horizonte para suavizar la cuadrícula lejana."""
    band = 170
    a = np.linspace(1, 0, band) ** 1.6 * 200
    fog = np.zeros((band, W, 4), np.uint8)
    fog[:, :, :3] = hl
    fog[:, :, 3] = a[:, None].astype(np.uint8)
    img.alpha_composite(Image.fromarray(fog, "RGBA"), (0, YH + 2))


def cloud(d, x, y, u, a):
    col = (255, 232, 248, a)
    for dx, dy, wc, hc in [(0, 2, 8, 2), (1, 1, 6, 1), (2, 0, 3, 1), (4, 0, 2, 1)]:
        d.rectangle([x + dx * u, y + dy * u, x + (dx + wc) * u - 1, y + (dy + hc) * u - 1], fill=col)


def plus(d, x, y, s, col):
    for dx, dy in [(0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)]:
        d.rectangle([x + dx * s, y + dy * s, x + dx * s + s - 1, y + dy * s + s - 1], fill=col)


# --------------------------------------------------------------------------- Clawd
POSES = {}
PAD = 12


def clawd_img(lu, ru, blink, eye_dx, leg):
    key = (lu, ru, blink, eye_dx, leg)
    if key in POSES:
        return POSES[key]
    rows = ["..XXXXXXXXXXX..", "..XXXXXXXXXXX..", "..XXXXXXXXXXX..", "..XXXXXXXXXXX..",
            "..XXXXXXXXXXX..", "..XXXXXXXXXXX..", "...............", "..............."]
    g = [list(r) for r in rows]
    for c in (4 + eye_dx, 10 + eye_dx):
        g[2][c] = g[3][c] = "E"
    for cols, up in (((0, 1), lu), ((13, 14), ru)):
        for c in cols:
            for r in ((0, 1) if up else (3, 4)):
                g[r][c] = "X"
    # patas: leg 0 = quietas, 1/2 = levanta un par al caminar
    for i, c in enumerate((3, 5, 9, 11)):
        lifted = (leg == 1 and i in (0, 2)) or (leg == 2 and i in (1, 3))
        g[6][c] = "X"
        if not lifted:
            g[7][c] = "X"
    img = Image.new("RGBA", (15 * S + PAD * 2, 8 * S + PAD * 2), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for r, row in enumerate(g):
        for c, ch in enumerate(row):
            if ch == ".":
                continue
            x, y = PAD + c * S, PAD + r * S
            d.rectangle([x, y, x + S - 1, y + S - 1], fill=EYE if (ch == "E" and not blink) else ORANGE)
    if blink:
        for c in (4 + eye_dx, 10 + eye_dx):
            d.rectangle([PAD + c * S - 6, PAD + 2.5 * S + 6, PAD + c * S + S + 5, PAD + 2.5 * S + 16], fill=EYE)
    # luz arriba, sombra abajo
    for c in range(2, 13):
        x = PAD + c * S
        d.rectangle([x, PAD, x + S - 1, PAD + 7], fill=ORANGE_LIGHT)
        d.rectangle([x, PAD + 6 * S - 10, x + S - 1, PAD + 6 * S - 1], fill=ORANGE_DARK)
    for cols, up in (((0, 1), lu), ((13, 14), ru)):
        r = 0 if up else 3
        for c in cols:
            d.rectangle([PAD + c * S, PAD + r * S, PAD + c * S + S - 1, PAD + r * S + 7], fill=ORANGE_LIGHT)
    alpha = img.getchannel("A").filter(ImageFilter.MaxFilter(17))
    out = Image.new("RGBA", img.size, OUTLINE + (0,))
    out.putalpha(alpha)
    out.alpha_composite(img)
    POSES[key] = out
    return out


def hop_state(tt, st, allow_anticipation=True):
    p = (tt % 2.5) / 2.5
    if p < 0.5:
        x = p / 0.5
        st["hop"] = 4 * x * (1 - x)
        st["lu"] = st["ru"] = True
        st["sq"] = -0.08 * math.sin(math.pi * x)          # se estira en el aire
    else:
        if p < 0.58:
            st["sq"] = 0.18 * (1 - (p - 0.5) / 0.08)
        elif p > 0.94 and allow_anticipation:
            st["sq"] = 0.12 * (p - 0.94) / 0.06
        if 0.5 <= p < 0.72:
            st["dust"] = (p - 0.5) / 0.22
        st["ru"] = 0.62 < p < 0.92 and int((p - 0.62) / 0.05) % 2 == 0


def clawd_state(time):
    st = dict(hop=0.0, sq=0.0, lu=False, ru=False, blink=False, eye=0, leg=0, bob=0, bubble=None, dust=None)
    if time < 5:
        hop_state(time, st, allow_anticipation=time < 2.5)
    elif time >= 10:
        hop_state(time - 10, st)
    else:
        tw = time - 5
        if tw < 4.8:
            step = int(tw / 0.25)
            st["leg"] = 1 + step % 2
            st["bob"] = -6 if step % 2 else 0
        else:
            st["sq"] = 0.12 * (tw - 4.8) / 0.2
        st["eye"] = -1 if 1.2 <= tw < 2.1 else (1 if 2.1 <= tw < 3.0 else 0)
        if 3.2 <= tw < 4.4:
            st["ru"] = int((tw - 3.2) / 0.15) % 2 == 0
        if 0.3 <= tw < 4.6:
            st["bubble"] = tw - 0.3
    st["blink"] = any(a <= time < a + 0.18 for a in (1.0, 7.4, 12.0))
    return st


BUBBLE_TXT = "> hola_"


def draw_bubble(img, el, cx, top):
    f = ImageFont.truetype(MONO_B, 46)
    n = min(len(BUBBLE_TXT) - 1, int(max(0, el - 0.2) * 7))
    txt = BUBBLE_TXT[:n] + ("_" if int(el * 3) % 2 == 0 else " ")
    pop = min(1.0, el / 0.15)
    bw, bh = int(300 * pop), int(110 * pop)
    if bw < 20:
        return
    x0, y0 = cx + 140 - bw // 2, top - 40 - bh
    d = ImageDraw.Draw(img)
    u = 8
    for (a, b, c, e, col) in [(x0 - u, y0, x0 + bw + u, y0 + bh, OUTLINE), (x0, y0 - u, x0 + bw, y0 + bh + u, OUTLINE),
                              (x0, y0, x0 + bw, y0 + bh, CREAM)]:
        d.rectangle([a, b, c, e], fill=col)
    # colita en escalera hacia Clawd
    tx = x0 + 70
    for i in range(3):
        d.rectangle([tx - i * 16 - u, y0 + bh + i * 16, tx - i * 16 + 24 + u, y0 + bh + (i + 1) * 16 + u], fill=OUTLINE)
    for i in range(3):
        d.rectangle([tx - i * 16, y0 + bh - 2 + i * 16, tx - i * 16 + 24, y0 + bh + (i + 1) * 16 - 2], fill=CREAM)
    if pop >= 1:
        d.text((x0 + 30, y0 + 26), txt, font=f, fill=EYE)


def shooting_star(d, time):
    for t0, x0, y0 in SHOOTING:
        e = (time - t0) / 0.7
        if 0 <= e < 1:
            hx, hy = x0 - e * 700, y0 + e * 300
            for i in range(14):
                k = i / 14
                a = int(255 * (1 - k) * math.sin(math.pi * e))
                px, py = hx + k * 260, hy - k * 111
                d.rectangle([px, py, px + 8 - k * 4, py + 8 - k * 4], fill=(255, 250, 230, a))


def bloom(img):
    small = img.resize((W // 4, H // 4), Image.BILINEAR)
    a = np.asarray(small, np.float32)
    lum = a @ np.array([0.3, 0.59, 0.11], np.float32)
    m = np.clip((lum - 180) / 90, 0, 1)[:, :, None]
    g = Image.fromarray((a * m).astype(np.uint8)).filter(ImageFilter.GaussianBlur(9)).resize((W, H), Image.BILINEAR)
    return np.asarray(g, np.float32)


# --------------------------------------------------------------------------- cuadro
def render(time):
    time %= DUR
    t = time / DUR
    w = weights(t)
    img, gc, hl = base_frame(t, w)
    draw_grid(img, t, gc)
    img = img.convert("RGBA")
    horizon_fog(img, hl)

    ov = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(ov)
    for x, y, k, ph, sz in STARS:
        tw = 0.25 + 0.75 * (0.5 + 0.5 * math.sin(2 * math.pi * (k * t * 1.5 + ph)))
        fade = min(1.0, max(0.0, (1340 - y) / 500))
        a = int(255 * tw * fade)
        if a > 8:
            if sz >= 6:
                plus(d, x, y, sz // 2, (255, 255, 255, a))
            else:
                d.rectangle([x, y, x + sz - 1, y + sz - 1], fill=(255, 255, 255, a))
    shooting_star(d, time)
    for x0, y0, u, loops, a in CLOUDS:
        x = ((x0 + t * loops * (W + 400)) % (W + 400)) - 200
        cloud(d, int(x), y0, u, a)
    img = Image.alpha_composite(img, ov)

    st = clawd_state(time)
    spr = clawd_img(st["lu"], st["ru"], st["blink"], st["eye"], st["leg"])
    sw, sh = spr.size
    sq = st["sq"]
    nw, nh = int(sw * (1 + sq * 0.9)), int(sh * (1 - sq))
    if (nw, nh) != (sw, sh):
        spr = spr.resize((nw, nh), Image.NEAREST)
    lift = st["hop"] * 190
    top = int(FEET - nh - lift + st["bob"])

    # reflejo sobre la cuadrícula
    refl = spr.transpose(Image.FLIP_TOP_BOTTOM).resize((nw, nh // 2), Image.NEAREST)
    ra = np.asarray(refl.getchannel("A"), np.float32) * np.linspace(0.30, 0, nh // 2)[:, None]
    refl.putalpha(Image.fromarray(ra.astype(np.uint8)))
    img.alpha_composite(refl, ((W - nw) // 2, int(FEET - PAD + lift * 0.6)))

    # sombra
    sh_ov = Image.new("RGBA", (800, 200), (0, 0, 0, 0))
    k = 1 - 0.35 * st["hop"]
    ImageDraw.Draw(sh_ov).ellipse([400 - 250 * k, 100 - 34 * k, 400 + 250 * k, 100 + 34 * k],
                                  fill=(0, 0, 20, int(200 - 70 * st["hop"])))
    sh_ov = sh_ov.filter(ImageFilter.GaussianBlur(12))
    img.alpha_composite(sh_ov, (W // 2 - 400, FEET + 14 - 100))

    img.alpha_composite(spr, ((W - nw) // 2, top))

    ov2 = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d2 = ImageDraw.Draw(ov2)
    if st["dust"] is not None:
        e = st["dust"]
        for side in (-1, 1):
            for i, (dx, dy, sz) in enumerate([(0, 0, 18), (60, -30, 14), (120, -10, 10), (30, -60, 12)]):
                x = W / 2 + side * (230 + dx + e * 160)
                y = FEET - 30 + dy - e * 50
                plus(d2, int(x), int(y), int(sz * (1 - e * 0.5)) // 2 + 2,
                     (255, 240, 225, int(220 * (1 - e))))
    for x0, y0, loops, sz, col, amp, sk, ph in PARTS:
        fr = (y0 + t * loops) % 1.0
        y = 1900 - fr * 1600
        x = x0 + amp * math.sin(2 * math.pi * (sk * t + ph))
        a = int(255 * math.sin(math.pi * fr) ** 0.6)
        plus(d2, int(x), int(y), sz // 2, col + (a,))
    img = Image.alpha_composite(img, ov2)
    if st["bubble"] is not None:
        draw_bubble(img, st["bubble"], W // 2, top + PAD)

    rgb = img.convert("RGB")
    a = np.asarray(rgb, np.float32)
    a = (a + bloom(rgb) * 0.55) * VIGNETTE
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8))


def _job(i):
    return render(i / FPS).tobytes()


def main():
    if len(sys.argv) >= 4 and sys.argv[1] == "--frame":
        render(float(sys.argv[2])).save(sys.argv[3])
        return
    adir = os.path.join(ROOT, "animated")
    os.makedirs(adir, exist_ok=True)
    mp4 = os.path.join(adir, "clawd-neon-1080x2400.mp4")
    ff = subprocess.Popen(["ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24",
                           "-s", f"{W}x{H}", "-r", str(FPS), "-i", "-", "-c:v", "libx264", "-preset", "slow",
                           "-crf", "18", "-tune", "animation", "-pix_fmt", "yuv420p", "-movflags", "+faststart", mp4],
                          stdin=subprocess.PIPE)
    with Pool(4) as pool:
        for i, raw in enumerate(pool.imap(_job, range(N), chunksize=2)):
            ff.stdin.write(raw)
            if i % 90 == 0:
                print("cuadro", i, "/", N, flush=True)
    ff.stdin.close()
    ff.wait()
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", mp4, "-vf", "scale=450:1000:flags=bicubic",
                    "-c:v", "libx264", "-crf", "30", "-preset", "slow", "-pix_fmt", "yuv420p", "-an",
                    "-movflags", "+faststart", os.path.join(adir, "vista-previa.mp4")], check=True)
    pal = os.path.join(adir, "_pal.png")
    vf = "fps=15,scale=360:800:flags=bicubic"
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", mp4, "-vf", vf + ",palettegen=max_colors=160", pal],
                   check=True)
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", mp4, "-i", pal, "-lavfi",
                    vf + "[x];[x][1:v]paletteuse=dither=bayer:bayer_scale=4", "-loop", "0",
                    os.path.join(adir, "clawd-neon-360x800.gif")], check=True)
    os.remove(pal)
    for name, tm in (("07-atardecer", 0.625), ("08-aurora", 6.6), ("09-ultravioleta", 10.625)):
        render(tm).save(os.path.join(ROOT, "wallpapers", f"{name}-1080x2400.png"), optimize=True)
    print("listo")


if __name__ == "__main__":
    main()

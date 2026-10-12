#!/usr/bin/env python3
"""Genera el paquete de tema Clawd para Samsung Galaxy A51 (1080x2400).

Uso:  python3 clawd-theme/tools/build.py
Salida: wallpapers/, icons/, palette/, bootanimation/, preview.html y clawd-theme-a51.zip
"""
import base64, io, json, unicodedata, math, os, random, shutil, zipfile
from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
W, H = 1080, 2400

ORANGE = (217, 119, 87)
ORANGE_DARK = (178, 90, 62)
CREAM = (240, 238, 230)
CHARCOAL = (31, 30, 29)
EYE = (24, 22, 21)
SAND = (212, 162, 127)
WARM_GRAY = (138, 130, 121)

MONO = "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf"
MONO_B = "/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf"

SPRITE = [
    "..XXXXXXXXXXX..",
    "..XXXXXXXXXXX..",
    "..XXEXXXXXEXX..",
    "XXXXEXXXXXEXXXX",
    "XXXXXXXXXXXXXXX",
    "..XXXXXXXXXXX..",
    "...X.X...X.X...",
    "...X.X...X.X...",
]
SW, SH = len(SPRITE[0]), len(SPRITE)


def out(*p):
    path = os.path.join(ROOT, *p)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    return path


def gradient(w, h, top, bot):
    col = Image.new("RGB", (1, h))
    for y in range(h):
        t = y / (h - 1)
        col.putpixel((0, y), tuple(int(top[i] * (1 - t) + bot[i] * t) for i in range(3)))
    return col.resize((w, h), Image.NEAREST).convert("RGBA")


def layer(img, draw_fn, blur=0):
    ov = Image.new("RGBA", img.size, (0, 0, 0, 0))
    draw_fn(ImageDraw.Draw(ov))
    if blur:
        ov = ov.filter(ImageFilter.GaussianBlur(blur))
    return Image.alpha_composite(img, ov)


def draw_clawd(d, ox, oy, S, body, shade, eye, closed=False):
    for r, row in enumerate(SPRITE):
        for c, ch in enumerate(row):
            if ch == ".":
                continue
            x, y = ox + c * S, oy + r * S
            d.rectangle([x, y, x + S - 1, y + S - 1], fill=eye if (ch == "E" and not closed) else body)
    if closed:
        for c in (4, 10):
            x, y = ox + c * S, oy + 3 * S
            d.rectangle([x - S // 4, y + S // 2 - S // 8, x + S + S // 4 - 1, y + S // 2 + S // 8], fill=eye)
    if shade:
        for c in range(2, 13):
            x = ox + c * S
            d.rectangle([x, oy + 6 * S - max(4, S // 5), x + S - 1, oy + 6 * S - 1], fill=shade)


def spark(d, x, y, s, col):
    for dx, dy in [(0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)]:
        d.rectangle([x + dx * s, y + dy * s, x + dx * s + s - 1, y + dy * s + s - 1], fill=col)


# --------------------------------------------------------------------------- fondos
SPARKS = [(-370, -100, 12, 230), (360, -140, 16, 255), (280, 600, 10, 200), (-330, 520, 14, 220),
          (-420, 200, 8, 150), (420, 280, 8, 150), (0, -260, 10, 170)]

CLASSIC = dict(bg=((38, 36, 33), (20, 19, 18)), body=ORANGE, shade=ORANGE_DARK, eye=EYE, glow=ORANGE, glow_k=1.0,
               dot=(255, 255, 255, 14), star=(255, 255, 255), spark=ORANGE, S=52, oy=900,
               text="> hola, soy clawd_", text_col=(236, 230, 220), sub="claude code", sub_col=ORANGE, stars=60)

WALLPAPERS = [
    ("01-clasico", "Clásico", "dark", dict(CLASSIC)),
    ("02-naranja", "Naranja", "dark", dict(
        CLASSIC, bg=((228, 130, 97), (186, 94, 65)), body=CHARCOAL, shade=(12, 12, 12), eye=CREAM,
        glow=(255, 225, 205), glow_k=0.7, dot=(255, 255, 255, 28), star=(255, 255, 255), spark=CREAM,
        text_col=(31, 30, 29), sub_col=(255, 240, 230))),
    ("03-crema", "Crema", "light", dict(
        CLASSIC, bg=((245, 243, 236), (226, 222, 209)), glow_k=0.55, dot=(0, 0, 0, 16), star=(0, 0, 0),
        text_col=(31, 30, 29), sub_col=ORANGE_DARK)),
    ("04-noche", "Noche", "dark", dict(
        CLASSIC, bg=((24, 26, 42), (8, 8, 15)), body=(201, 112, 82), shade=(150, 80, 58), glow=(90, 100, 200),
        glow_k=0.8, dot=(255, 255, 255, 0), spark=(150, 160, 230), stars=130, closed=True, zzz=True, sparks=False,
        text="> shh... clawd duerme_", sub_col=(150, 160, 230))),
    ("05-minimal", "Minimal", "dark", dict(
        CLASSIC, S=30, oy=1560, text=None, sub=None, glow_k=0.6, stars=40, sparks=False)),
    ("06-terminal", "Terminal", "dark", dict(
        CLASSIC, oy=560, text=None, sub=None, terminal=True, sparks=False, stars=40)),
]


def render_wallpaper(s):
    img = gradient(W, H, *s["bg"])
    dot = s["dot"]
    if dot[3]:
        def dots(d):
            for gy in range(60, H, 60):
                for gx in range(60, W, 60):
                    d.rectangle([gx, gy, gx + 2, gy + 2], fill=dot)
        img = layer(img, dots)

    S, oy = s["S"], s["oy"]
    sw, sh = SW * S, SH * S
    ox = (W - sw) // 2
    cx, cy = W // 2, oy + sh // 2
    k = s["glow_k"]
    g = s["glow"]

    def glow(d):
        for r, a in [(620, 30), (470, 45), (330, 60)]:
            d.ellipse([cx - r * S / 52, cy - r * S / 52, cx + r * S / 52, cy + r * S / 52],
                      fill=g + (int(a * k),))
    img = layer(img, glow, blur=120)

    shadow_col = (0, 0, 0, 150) if s["bg"][0][0] < 120 else (60, 40, 30, 90)
    img = layer(img, lambda d: d.ellipse([cx - 330 * S / 52, oy + sh + 30 * S / 52, cx + 330 * S / 52,
                                         oy + sh + 90 * S / 52], fill=shadow_col), blur=18)

    rnd = random.Random(51)

    def stars(d):
        for _ in range(s["stars"]):
            x, y = rnd.randrange(40, W - 40), rnd.randrange(150, H - 300)
            if abs(x - cx) < 420 and oy - 100 < y < oy + sh + 150:
                continue
            d.rectangle([x, y, x + 3, y + 3], fill=s["star"] + (rnd.choice([40, 70, 110]),))
    img = layer(img, stars)

    d = ImageDraw.Draw(img)
    draw_clawd(d, ox, oy, S, s["body"], s["shade"], s["eye"], closed=s.get("closed", False))

    if s.get("sparks", True):
        for dx, dy, sz, a in SPARKS:
            spark(d, cx + dx - 6, oy + dy, sz, s["spark"] + (a,))

    if s.get("zzz"):
        for (dx, dy, sz) in [(260, -60, 56), (340, -170, 74), (440, -300, 96)]:
            d.text((cx + dx, oy + dy), "z", font=ImageFont.truetype(MONO_B, sz), fill=(220, 224, 250, 220))

    font = ImageFont.truetype(MONO, 46)
    small = ImageFont.truetype(MONO, 30)
    if s.get("text"):
        tw = d.textlength(s["text"], font=font)
        d.text(((W - tw) / 2, oy + sh + 190), s["text"], font=font, fill=s["text_col"])
    if s.get("sub"):
        sw2 = d.textlength(s["sub"], font=small)
        d.text(((W - sw2) / 2, oy + sh + 265), s["sub"], font=small, fill=s["sub_col"])

    if s.get("terminal"):
        x0, y0, x1, y1 = 90, oy + sh + 150, W - 90, oy + sh + 150 + 470
        d.rounded_rectangle([x0, y0, x1, y1], radius=28, fill=(14, 13, 12, 215), outline=(78, 73, 66), width=3)
        for i, col in enumerate([(236, 106, 94), (244, 191, 79), (97, 197, 84)]):
            d.ellipse([x0 + 34 + i * 44, y0 + 30, x0 + 58 + i * 44, y0 + 54], fill=col)
        f = ImageFont.truetype(MONO, 38)
        lines = [("$ ", WARM_GRAY, "claude", (236, 230, 220)),
                 ("> ", ORANGE, "hola, soy clawd", (236, 230, 220)),
                 ("> ", ORANGE, "¿en qué te ayudo hoy?", (236, 230, 220)),
                 ("* ", ORANGE, "pensando...", WARM_GRAY),
                 ("> ", ORANGE, "_", (236, 230, 220))]
        y = y0 + 110
        for pre, pc, txt, tc in lines:
            d.text((x0 + 44, y), pre, font=f, fill=pc)
            d.text((x0 + 44 + d.textlength(pre, font=f), y), txt, font=f, fill=tc)
            y += 66
    return img.convert("RGB")


# --------------------------------------------------------------------------- iconos
BITMAPS = {
    "Teléfono": ["XXXX........", "XXXXX.......", "XXXXX.......", "XXXX........", ".XXX........", "..XXX.......",
                 "...XXXX.....", "....XXXXXX..", ".....XXXXXX.", ".....XXXXXX.", "......XXXXX.", "......XXXX.."],
    "Mensajes": ["............", ".XXXXXXXXXX.", "XXXXXXXXXXXX", "XXXXXXXXXXXX", "XX.XX.XX.XXX", "XXXXXXXXXXXX",
                 "XXXXXXXXXXXX", ".XXXXXXXXXX.", "...XXX......", "...XX.......", "...X........", "............"],
    "Cámara": ["............", "....XXXX....", "XXXXXXXXXXXX", "XXXXXXXXXXXX", "XXXX....XXXX", "XXX.XXXX.XXX",
               "XXX.XXXX.XXX", "XXXX....XXXX", "XXXXXXXXXXXX", "XXXXXXXXXXXX", "............", "............"],
    "Galería": ["XXXXXXXXXXXX", "X..........X", "X.......XX.X", "X.......XX.X", "X..........X", "X....X.....X",
                "X...XXX....X", "X..XXXXX.X.X", "X.XXXXXXXXXX", "X.XXXXXXXXXX", "XXXXXXXXXXXX", "............"],
    "Música": [".....XXXXXX.", ".....XXXXXX.", ".....X....X.", ".....X....X.", ".....X....X.", ".....X....X.",
               "..XXXX..XXX.", ".XXXXX.XXXX.", ".XXXXX.XXXX.", "..XXX...XX..", "............", "............"],
    "Calendario": ["..X......X..", "XXXXXXXXXXXX", "XXXXXXXXXXXX", "X..........X", "X.XX.XX.XX.X", "X.XX.XX.XX.X",
                   "X..........X", "X.XX.XX.XX.X", "X.XX.XX.XX.X", "X..........X", "XXXXXXXXXXXX", "............"],
    "Archivos": ["............", ".XXXX.......", "XXXXXXXXXXXX", "XXXXXXXXXXXX", "X..........X", "X..........X",
                 "X..........X", "X..........X", "X..........X", "XXXXXXXXXXXX", "............", "............"],
    "Correo": ["............", "XXXXXXXXXXXX", "XX........XX", "X.X......X.X", "X..X....X..X", "X...X..X...X",
               "X....XX....X", "X..........X", "X..........X", "XXXXXXXXXXXX", "............", "............"],
}


def _round_bitmap(fn):
    rows = []
    for y in range(12):
        rows.append("".join("X" if fn(x - 5.5, y - 5.5, x, y) else "." for x in range(12)))
    return rows


BITMAPS["Internet"] = ["...XXXXXX...", "..X..XX..X..", ".X...XX...X.", "X....XX....X", "X....XX....X", "XXXXXXXXXXXX",
                       "XXXXXXXXXXXX", "X....XX....X", "X....XX....X", ".X...XX...X.", "..X..XX..X..", "...XXXXXX..."]
BITMAPS["Ajustes"] = _round_bitmap(lambda dx, dy, x, y: (
    1.9 < math.hypot(dx, dy) < 4.2 or
    (4.2 <= math.hypot(dx, dy) < 5.8 and math.cos(8 * math.atan2(dy, dx)) > 0.15)))
BITMAPS["Reloj"] = _round_bitmap(lambda dx, dy, x, y: (
    abs(math.hypot(dx, dy) - 5.1) < 0.7 or (x in (5, 6) and 2 <= y <= 6) or (y in (5, 6) and 5 <= x <= 8)))

ICON_ORDER = ["Teléfono", "Mensajes", "Internet", "Cámara", "Ajustes", "Galería", "Música", "Reloj",
              "Calendario", "Archivos", "Correo", "Clawd"]
ICON_STYLES = {"naranja": (ORANGE, CREAM), "oscuro": (CHARCOAL, ORANGE)}


def render_icon(name, style):
    bg, fg = ICON_STYLES[style]
    size = 512
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, size - 1, size - 1], radius=130, fill=bg)
    if name == "Clawd":
        S = 26
        ox, oy = (size - SW * S) // 2, (size - SH * S) // 2 + 6
        draw_clawd(d, ox, oy, S, CHARCOAL if style == "naranja" else ORANGE, None, bg)
    else:
        pix = 30
        off = (size - 12 * pix) // 2
        for y, row in enumerate(BITMAPS[name]):
            for x, ch in enumerate(row):
                if ch == "X":
                    d.rectangle([off + x * pix, off + y * pix, off + (x + 1) * pix - 1, off + (y + 1) * pix - 1],
                                fill=fg)
    return img


def build_icons():
    sheets = {}
    for style in ICON_STYLES:
        for name in ICON_ORDER:
            render_icon(name, style).save(out("icons", style, unicodedata.normalize("NFD", name.lower()).encode("ascii", "ignore").decode() + ".png"))
        sheet = Image.new("RGB", (4 * 200 + 20, 3 * 200 + 20), (92, 87, 80))
        for i, name in enumerate(ICON_ORDER):
            ic = render_icon(name, style).resize((180, 180), Image.LANCZOS)
            sheet.paste(ic, (20 + (i % 4) * 200, 20 + (i // 4) * 200), ic)
        sheet.save(out("icons", f"hoja-{style}.png"))
        sheets[style] = {n: render_icon(n, style).resize((128, 128), Image.LANCZOS) for n in ICON_ORDER}
    return sheets


# --------------------------------------------------------------------------- paleta
PALETTE = [
    ("Naranja Clawd", ORANGE, "Color principal del cuerpo"),
    ("Naranja oscuro", ORANGE_DARK, "Sombras y estados pulsados"),
    ("Arena", SAND, "Acentos suaves"),
    ("Crema", CREAM, "Fondos claros y texto sobre naranja"),
    ("Carbón", CHARCOAL, "Fondos oscuros y ojos"),
    ("Gris cálido", WARM_GRAY, "Texto secundario"),
]


def hexc(c):
    return "#%02X%02X%02X" % c


def build_palette():
    with open(out("palette", "palette.json"), "w") as f:
        json.dump([{"nombre": n, "hex": hexc(c), "uso": u} for n, c, u in PALETTE], f, ensure_ascii=False, indent=2)
    img = Image.new("RGB", (1080, 1200), CHARCOAL)
    d = ImageDraw.Draw(img)
    f1, f2 = ImageFont.truetype(MONO_B, 44), ImageFont.truetype(MONO, 30)
    for i, (n, c, u) in enumerate(PALETTE):
        y = i * 200
        d.rectangle([0, y, 1080, y + 199], fill=c)
        ink = CHARCOAL if sum(c) > 380 else CREAM
        d.text((60, y + 50), n, font=f1, fill=ink)
        d.text((60, y + 115), f"{hexc(c)}  ·  {u}", font=f2, fill=ink)
    img.save(out("palette", "paleta.png"))


# --------------------------------------------------------------------------- animación de arranque
BW, BH = 720, 1600
BS = 24


def boot_frame(y, closed=False, squash=1.0, typed=0, cursor=True):
    img = Image.new("RGB", (BW, BH), (18, 17, 16))
    sprite = Image.new("RGBA", (SW * BS, SH * BS), (0, 0, 0, 0))
    draw_clawd(ImageDraw.Draw(sprite), 0, 0, BS, ORANGE, ORANGE_DARK, EYE, closed=closed)
    if squash != 1.0:
        w2, h2 = int(sprite.width * (2 - squash)), int(sprite.height * squash)
        sprite = sprite.resize((w2, h2), Image.NEAREST)
        y += (SH * BS - h2)
    img.paste(sprite, ((BW - sprite.width) // 2, int(y)), sprite)
    d = ImageDraw.Draw(img)
    txt = "> clawd"[:typed] + ("_" if cursor and typed > 0 else "")
    f = ImageFont.truetype(MONO, 40)
    d.text(((BW - d.textlength("> clawd_", font=f)) / 2, 1010), txt, font=f, fill=(236, 230, 220))
    return img


def build_boot():
    base_y = 700
    p0, p1 = [], []
    n0 = 40
    for i in range(n0):
        if i < 22:
            t = i / 21
            y = -260 + (base_y + 260) * t * t
            p0.append(boot_frame(y))
        elif i < 25:
            p0.append(boot_frame(base_y, squash=0.78 if i == 22 else (0.9 if i == 23 else 1.0)))
        else:
            typed = min(7, (i - 25) // 2 + 1)
            p0.append(boot_frame(base_y, typed=typed, cursor=False))
    for i in range(30):
        closed = 10 <= i < 14
        bob = -BS // 2 if 16 <= i < 24 else 0
        p1.append(boot_frame(base_y + bob, closed=closed, typed=7, cursor=(i // 8) % 2 == 0))
    bdir = out("bootanimation")
    shutil.rmtree(bdir, ignore_errors=True)
    for part, frames in (("part0", p0), ("part1", p1)):
        os.makedirs(os.path.join(bdir, part), exist_ok=True)
        for i, fr in enumerate(frames):
            fr.save(os.path.join(bdir, part, f"{i:04d}.png"), optimize=True)
    desc = f"{BW} {BH} 30\np 1 0 part0\np 0 0 part1\n"
    with open(os.path.join(bdir, "desc.txt"), "w") as f:
        f.write(desc)
    with zipfile.ZipFile(os.path.join(bdir, "bootanimation.zip"), "w", zipfile.ZIP_STORED) as z:
        z.write(os.path.join(bdir, "desc.txt"), "desc.txt")
        for part in ("part0", "part1"):
            for fn in sorted(os.listdir(os.path.join(bdir, part))):
                z.write(os.path.join(bdir, part, fn), f"{part}/{fn}")
    # los PNG sueltos ya van dentro del zip; se conservan solo zip y desc.txt
    for part in ("part0", "part1"):
        shutil.rmtree(os.path.join(bdir, part))
    seq = p0 + p1 * 2
    small = [fr.resize((270, 600), Image.LANCZOS).quantize(32) for fr in seq]
    gif = out("bootanimation", "vista-previa.gif")
    small[0].save(gif, save_all=True, append_images=small[1:], duration=33, loop=0, optimize=True)
    return gif


# --------------------------------------------------------------------------- vista previa
def data_uri(raw, mime):
    return f"data:{mime};base64," + base64.b64encode(raw).decode()


def jpeg_uri(img, size):
    b = io.BytesIO()
    img.resize(size, Image.LANCZOS).save(b, "JPEG", quality=86)
    return data_uri(b.getvalue(), "image/jpeg")


def png_uri(img):
    b = io.BytesIO()
    img.save(b, "PNG", optimize=True)
    return data_uri(b.getvalue(), "image/png")


def build_preview(wall_imgs, icon_imgs, gif_path):
    with open(os.path.join(HERE, "preview.template.html"), encoding="utf-8") as f:
        tpl = f.read()
    data = {
        "wallpapers": [{"id": i, "name": n, "tone": t, "src": jpeg_uri(im, (360, 800))} for (i, n, t, _), im in wall_imgs],
        "icons": {st: [{"name": n, "src": png_uri(icon_imgs[st][n].resize((96, 96), Image.LANCZOS))} for n in ICON_ORDER]
                  for st in icon_imgs},
        "boot": data_uri(open(gif_path, "rb").read(), "image/gif"),
        "palette": [{"name": n, "hex": hexc(c), "use": u} for n, c, u in PALETTE],
    }
    body = tpl.replace("__DATA__", json.dumps(data, ensure_ascii=False))
    with open(out("preview-artifact.html"), "w", encoding="utf-8") as f:
        f.write(body)
    wrapped = ("<!doctype html><html lang=\"es\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" "
               "content=\"width=device-width,initial-scale=1,viewport-fit=cover\"></head><body>" + body + "</body></html>")
    with open(out("preview.html"), "w", encoding="utf-8") as f:
        f.write(wrapped)


def main():
    wall_imgs = []
    for fid, name, tone, spec in WALLPAPERS:
        im = render_wallpaper(spec)
        im.save(out("wallpapers", f"{fid}-1080x2400.png"), optimize=True)
        wall_imgs.append(((fid, name, tone, spec), im))
        print("fondo", fid)
    icon_imgs = build_icons()
    build_palette()
    gif = build_boot()
    build_preview(wall_imgs, icon_imgs, gif)

    zpath = out("clawd-theme-a51.zip")
    if os.path.exists(zpath):
        os.remove(zpath)
    with zipfile.ZipFile(zpath, "w", zipfile.ZIP_DEFLATED) as z:
        for folder in ("wallpapers", "icons", "palette", "bootanimation"):
            for dp, _, fns in os.walk(os.path.join(ROOT, folder)):
                for fn in fns:
                    full = os.path.join(dp, fn)
                    z.write(full, os.path.join("clawd-theme-a51", os.path.relpath(full, ROOT)))
        for fn in ("README.md", "preview.html"):
            if os.path.exists(os.path.join(ROOT, fn)):
                z.write(os.path.join(ROOT, fn), os.path.join("clawd-theme-a51", fn))
    print("zip", zpath, os.path.getsize(zpath) // 1024, "KB")


if __name__ == "__main__":
    main()

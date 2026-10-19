"""Motor de arte del addon: materiales, texturas con UV de caja, geometría
Bedrock y un pequeño renderizador para vistas previas e iconos.

Convenciones (las de Bedrock): 16 unidades = 1 bloque, el frente del mob mira
a -Z y su lado derecho está en -X.
"""
import math
import random

from PIL import Image, ImageDraw

# Alfa de los píxeles que brillan en la oscuridad (material entity_emissive_alpha:
# cuanto menor el alfa, más brilla el píxel).
BRILLO = 50


def escalar(c, f):
    return tuple(max(0, min(255, int(round(v * f)))) for v in c[:3])


def mezclar(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] * (1 - t) + b[i] * t)) for i in range(3))


# --------------------------------------------------------------------------
# Materiales
# --------------------------------------------------------------------------

class Mat:
    """Color base + patrón procedural con el que se pinta cada cara."""

    def __init__(self, color, patron="liso", acento=None, ruido=0.04, contorno=0.86, brilla=False):
        self.color, self.patron, self.acento = color, patron, acento or (255, 255, 255)
        self.ruido, self.contorno, self.brilla = ruido, contorno, brilla


def _mascara_grietas(w, h, rng):
    mascara = set()
    for _ in range(max(1, (w * h) // 22)):
        x, y = rng.randrange(w), rng.randrange(h)
        for _ in range(rng.randint(3, 7)):
            mascara.add((x, y))
            x = max(0, min(w - 1, x + rng.choice((-1, 0, 1))))
            y = max(0, min(h - 1, y + rng.choice((0, 1, 1))))
    return mascara


def _pixel(mat, i, j, w, h, ctx, rng):
    """Color (rgb, brilla) del texel (i, j) de una cara de w x h."""
    base, p = mat.color, mat.patron
    t = j / max(1, h - 1)
    if p == "tela":
        c = escalar(base, 1 + 0.08 * math.cos(i * math.pi / 2) - (0.18 if j == h - 1 and h > 3 else 0))
    elif p == "metal":
        c = escalar(mezclar(base, (255, 255, 235), max(0.0, 0.4 - 0.7 * t)), 1.12 - 0.3 * t)
        if (i * 7 + j * 3) % 11 == 0:
            c = mezclar(c, (255, 255, 255), 0.35)
    elif p == "armadura":
        fila = j % 5
        c = escalar(base, 1.25 if fila == 0 else 0.75 if fila == 4 else 1.0)
        if fila == 2 and i % 4 == 1:
            c = mat.acento
    elif p == "escamas":
        fila = j // 2
        k = (i + (fila % 2) * 2) % 4
        c = escalar(base, 1.15 if (k == 0 and j % 2 == 0) else 0.8 if (k == 3 and j % 2 == 1) else 1.0)
    elif p == "pelo":
        c = escalar(base, 1 + 0.14 * ctx["col"][i] - (0.12 if (j + ctx["fase"][i]) % 4 == 0 else 0))
    elif p == "hueso":
        c = escalar(base, 1.08 - 0.28 * t - (0.08 if j % 3 == 2 else 0))
    elif p == "grietas":
        if (i, j) in ctx["grietas"]:
            return mezclar(mat.acento, (255, 255, 160), 0.2 * rng.random()), True
        c = escalar(base, 1 + 0.08 * ctx["col"][i])
    elif p == "sombra":
        if rng.random() < 0.015:
            return mat.acento, True
        c = escalar(base, 1 + 0.18 * ctx["col"][i] - 0.15 * t)
    elif p == "gema":
        d = math.hypot((i + 0.5) / w - 0.4, (j + 0.5) / h - 0.35) * 1.6
        c = mezclar(mezclar(base, (255, 255, 255), 0.55), escalar(base, 0.6), d)
    elif p == "piel":
        c = escalar(base, 1 + 0.03 * math.sin(i * 1.7 + j * 2.3))
    else:
        c = base
    return c, mat.brilla


SOMBRA_CARA = {"top": 1.12, "bottom": 0.7, "front": 1.0, "back": 0.86, "right": 0.92, "left": 0.92}


def pintar_cara(img, rect, cara, mat, rng):
    x, y, w, h = rect
    ctx = {"col": [rng.uniform(-1, 1) for _ in range(w)], "fase": [rng.randrange(4) for _ in range(w)]}
    if mat.patron == "grietas":
        ctx["grietas"] = _mascara_grietas(w, h, rng)
    for j in range(h):
        for i in range(w):
            c, brilla = _pixel(mat, i, j, w, h, ctx, rng)
            if not brilla:
                f = SOMBRA_CARA[cara]
                if cara not in ("top", "bottom") and h > 3:
                    f *= 1.06 - 0.14 * j / (h - 1)
                if w >= 3 and h >= 3 and (i in (0, w - 1) or j in (0, h - 1)):
                    f *= mat.contorno
                c = escalar(c, f * (1 + rng.uniform(-mat.ruido, mat.ruido)))
            img.putpixel((x + i, y + j), c + ((BRILLO if brilla else 255),))


# --------------------------------------------------------------------------
# Dibujos en pixel art (caras, emblemas) sobre una cara del cubo
# --------------------------------------------------------------------------

def dibujo(lineas, paleta, dy=0, dx=0):
    """Pinta un dibujo ASCII centrado horizontalmente. '.' y ' ' no pintan.
    Un color de la paleta con 4 componentes brilla en la oscuridad."""
    def pintar(img, x, y, w, h):
        for j, linea in enumerate(lineas):
            x0 = x + (w - len(linea)) // 2 + dx
            for i, ch in enumerate(linea):
                px, py = x0 + i, y + dy + j
                if ch in ". " or not (x <= px < x + w and y <= py < y + h):
                    continue
                c = paleta[ch]
                img.putpixel((px, py), tuple(c[:3]) + ((BRILLO if len(c) > 3 else 255),))
    return pintar


def borde(color, grosor=1):
    def pintar(img, x, y, w, h):
        for j in range(h):
            for i in range(w):
                if i < grosor or j < grosor or i >= w - grosor or j >= h - grosor:
                    img.putpixel((x + i, y + j), tuple(color) + (255,))
    return pintar


def franja(color, fila, grosor=1):
    def pintar(img, x, y, w, h):
        for j in range(fila, min(h, fila + grosor)):
            for i in range(w):
                img.putpixel((x + i, y + j), tuple(color[:3]) + ((BRILLO if len(color) > 3 else 255),))
    return pintar


def varias(*fns):
    def pintar(img, x, y, w, h):
        for f in fns:
            f(img, x, y, w, h)
    return pintar


# --------------------------------------------------------------------------
# Cubos y huesos
# --------------------------------------------------------------------------

def cubo(origen, tam, mat, caras=None, rot=None, piv=None, inflar=0):
    return {"origin": list(origen), "size": list(tam), "mat": mat, "caras": caras or {},
            "rot": rot, "piv": piv, "inflar": inflar}


def espejo(c):
    """El mismo cubo reflejado al otro lado (X -> -X)."""
    n = dict(c)
    n["origin"] = [-(c["origin"][0] + c["size"][0])] + c["origin"][1:]
    if c["piv"]:
        n["piv"] = [-c["piv"][0]] + c["piv"][1:]
    if c["rot"]:
        n["rot"] = [c["rot"][0], -c["rot"][1], -c["rot"][2]]
    n["caras"] = {{"right": "left", "left": "right"}.get(k, k): v for k, v in c["caras"].items()}
    return n


def hueso(pivote, cubos=None, padre="root", rot=None, loc=None):
    return {"pivot": list(pivote), "cubos": cubos or [], "padre": padre, "rot": rot, "loc": loc or {}}


def caras_uv(u, v, w, h, d):
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
    }


def generar_modelo(clave, huesos, ns, rng):
    """Empaqueta la textura, la pinta y devuelve (geometría, imagen)."""
    cubos = [c for b in huesos.values() for c in b["cubos"]]
    ancho_tex = 128 if len(cubos) < 40 else 256
    orden = sorted(cubos, key=lambda c: -(c["size"][2] + c["size"][1]))
    x = y = fila_h = 0
    for c in orden:
        w, h, d = (int(round(s)) for s in c["size"])
        rw, rh = 2 * (d + w), d + h
        if x + rw > ancho_tex:
            x, y, fila_h = 0, y + fila_h, 0
        c["uv"] = [x, y]
        x += rw
        fila_h = max(fila_h, rh)
    alto_tex = 32
    while alto_tex < y + fila_h:
        alto_tex *= 2

    img = Image.new("RGBA", (ancho_tex, alto_tex), (0, 0, 0, 0))
    for c in cubos:
        w, h, d = (int(round(s)) for s in c["size"])
        for nombre, rect in caras_uv(*c["uv"], w, h, d).items():
            if rect[2] and rect[3]:
                pintar_cara(img, rect, nombre, c["mat"], rng)
                if nombre in c["caras"]:
                    c["caras"][nombre](img, *rect)

    bones = [{"name": "root", "pivot": [0, 0, 0]}]
    alcance, alto_px = 8, 16
    for nombre, b in huesos.items():
        hb = {"name": nombre, "parent": b["padre"], "pivot": b["pivot"]}
        if b["rot"]:
            hb["rotation"] = b["rot"]
        if b["loc"]:
            hb["locators"] = b["loc"]
        cs = []
        for c in b["cubos"]:
            jc = {"origin": c["origin"], "size": c["size"], "uv": c["uv"]}
            if c["inflar"]:
                jc["inflate"] = c["inflar"]
            if c["rot"]:
                jc["rotation"], jc["pivot"] = c["rot"], c["piv"] or c["origin"]
            cs.append(jc)
            alcance = max(alcance, abs(c["origin"][0]), abs(c["origin"][0] + c["size"][0]),
                          abs(c["origin"][2]), abs(c["origin"][2] + c["size"][2]))
            alto_px = max(alto_px, c["origin"][1] + c["size"][1])
        if cs:
            hb["cubes"] = cs
        bones.append(hb)

    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {
            "identifier": f"geometry.{ns}.{clave}",
            "texture_width": ancho_tex, "texture_height": alto_tex,
            "visible_bounds_width": round(2 * alcance / 16 + 1, 2),
            "visible_bounds_height": round(alto_px / 16 + 1, 2),
            "visible_bounds_offset": [0, round(alto_px / 32, 2), 0],
        },
        "bones": bones,
    }]}
    return geo, img


# --------------------------------------------------------------------------
# Renderizador (vistas previas e icono del pack)
# --------------------------------------------------------------------------

def _rotar(v, rot, piv):
    """Rotación de Bedrock: X e Y van en sentido contrario al estándar."""
    x, y, z = v[0] - piv[0], v[1] - piv[1], v[2] - piv[2]
    rx, ry, rz = (math.radians(a) for a in rot)
    rx, ry = -rx, -ry
    x, y = x * math.cos(rz) - y * math.sin(rz), x * math.sin(rz) + y * math.cos(rz)
    x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)
    y, z = y * math.cos(rx) - z * math.sin(rx), y * math.sin(rx) + z * math.cos(rx)
    return (x + piv[0], y + piv[1], z + piv[2])


def renderizar(geo, tex, yaw=-30, pitch=12, escala=6, fondo=None, lienzo=None):
    """Dibuja el modelo en reposo con proyección ortográfica."""
    g = geo["minecraft:geometry"][0]
    tw = g["description"]["texture_width"]
    k = tex.width / tw
    huesos = {b["name"]: b for b in g["bones"]}

    def cadena(nombre):
        out = []
        while nombre:
            b = huesos[nombre]
            if b.get("rotation"):
                out.append((b["rotation"], b["pivot"]))
            nombre = b.get("parent")
        return out

    quads = []
    for b in g["bones"]:
        transf = cadena(b["name"])
        for c in b.get("cubes", []):
            inf = c.get("inflate", 0)
            (x0, y0, z0), (w, h, d) = c["origin"], c["size"]
            x0, y0, z0, x1, y1, z1 = x0 - inf, y0 - inf, z0 - inf, x0 + w + inf, y0 + h + inf, z0 + d + inf
            W, H, D = x1 - x0, y1 - y0, z1 - z0
            iw, ih, idd = (int(round(s)) for s in (w, h, d))
            rects = caras_uv(*c["uv"], iw, ih, idd)
            caras = {
                "front": ((x0, y1, z0), (W, 0, 0), (0, -H, 0)),
                "back": ((x1, y1, z1), (-W, 0, 0), (0, -H, 0)),
                "right": ((x0, y1, z1), (0, 0, -D), (0, -H, 0)),
                "left": ((x1, y1, z0), (0, 0, D), (0, -H, 0)),
                "top": ((x0, y1, z1), (W, 0, 0), (0, 0, -D)),
                "bottom": ((x0, y0, z1), (W, 0, 0), (0, 0, -D)),
            }

            def tr(p):
                if c.get("rotation"):
                    p = _rotar(p, c["rotation"], c["pivot"])
                for rot, piv in transf:
                    p = _rotar(p, rot, piv)
                p = _rotar(p, [0, yaw, 0], [0, 0, 0])
                return _rotar(p, [pitch, 0, 0], [0, 0, 0])

            for nombre, (s, U, V) in caras.items():
                rx, ry, rw, rh = rects[nombre]
                if not rw or not rh:
                    continue
                for a in range(rw):
                    for bb in range(rh):
                        pts = []
                        for (sa, sb) in ((a, bb), (a + 1, bb), (a + 1, bb + 1), (a, bb + 1)):
                            p = tuple(s[i] + U[i] * sa / rw + V[i] * sb / rh for i in range(3))
                            pts.append(tr(p))
                        (ax, ay, _), (bx, by, _), (cx, cy, _) = pts[0], pts[1], pts[3]
                        if (bx - ax) * (ay - cy) - (by - ay) * (ax - cx) <= 0:
                            continue
                        col = tex.getpixel((int((rx + a) * k), int((ry + bb) * k)))
                        quads.append((sum(p[2] for p in pts) / 4, pts, col))

    if not quads:
        return lienzo
    xs = [p[0] for q in quads for p in q[1]]
    ys = [p[1] for q in quads for p in q[1]]
    if lienzo is None:
        ancho = int((max(xs) - min(xs)) * escala) + 20
        alto = int((max(ys) - min(ys)) * escala) + 20
        lienzo = Image.new("RGBA", (ancho, alto), fondo or (0, 0, 0, 0))
        ox, oy = 10 - min(xs) * escala, 10 + max(ys) * escala
    else:
        ox = lienzo.width / 2 - (max(xs) + min(xs)) / 2 * escala
        oy = lienzo.height / 2 + (max(ys) + min(ys)) / 2 * escala
    dib = ImageDraw.Draw(lienzo)
    for _, pts, col in sorted(quads, key=lambda q: -q[0]):
        poly = [(ox + p[0] * escala, oy - p[1] * escala) for p in pts]
        c = col[:3]
        if col[3] < 255:  # emisivo: un poco más vivo
            c = mezclar(c, (255, 255, 255), 0.15)
        dib.polygon(poly, fill=c + (255,))
    return lienzo

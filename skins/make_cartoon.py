#!/usr/bin/env python3
"""
Ilustracion estilo caricatura/animacion del "Demonio de Obsidiana".
Genera un SVG y lo renderiza a PNG con Chromium headless.

Uso:  python3 make_cartoon.py [carpeta_salida]
"""
import math
import os
import random
import subprocess
import sys

W = H = 1200
OUT = "#0a0810"          # contorno
SW = 7                   # grosor de contorno
random.seed(7)


def tongue(x, y, w, h, lean=0.0):
    """Lengua de llama estilo caricatura: base en (x,y), punta arriba."""
    return (f"M{x - w / 2:.1f},{y:.1f} "
            f"C{x - w / 2:.1f},{y - h * .45:.1f} {x + lean * .3 - w * .12:.1f},{y - h * .62:.1f} {x + lean:.1f},{y - h:.1f} "
            f"C{x + lean + w * .14:.1f},{y - h * .56:.1f} {x + w / 2:.1f},{y - h * .42:.1f} {x + w / 2:.1f},{y:.1f} Z")


def flame_cluster(cx, base, spec, outline=True):
    """spec: lista de (dx, w, h, lean). Dibuja 3 capas de color por lengua."""
    out = []
    for dx, w, h, lean in spec:
        x = cx + dx
        st = f' stroke="{OUT}" stroke-width="{SW - 2}" stroke-linejoin="round"' if outline else ""
        out.append(f'<path d="{tongue(x, base, w, h, lean)}" fill="url(#fl_out)"{st}/>')
    for dx, w, h, lean in spec:
        x = cx + dx
        out.append(f'<path d="{tongue(x, base + 2, w * .66, h * .72, lean * .7)}" fill="#ff9a00"/>')
    for dx, w, h, lean in spec:
        x = cx + dx
        out.append(f'<path d="{tongue(x, base + 4, w * .34, h * .42, lean * .45)}" fill="#ffe066"/>')
    return "\n".join(out)


def mirror(x):
    return 1200 - x


def build_svg():
    s = []
    a = s.append
    a(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" width="{W}" height="{H}">')
    a('''<defs>
  <radialGradient id="bg" cx="50%" cy="46%" r="75%">
    <stop offset="0" stop-color="#7a1a0c"/><stop offset=".45" stop-color="#2c0c18"/><stop offset="1" stop-color="#0d0714"/>
  </radialGradient>
  <radialGradient id="sun" cx="50%" cy="50%" r="50%">
    <stop offset="0" stop-color="#ffb020" stop-opacity=".95"/><stop offset=".55" stop-color="#ff5a10" stop-opacity=".55"/>
    <stop offset="1" stop-color="#ff3b00" stop-opacity="0"/>
  </radialGradient>
  <linearGradient id="fl_out" x1="0" y1="1" x2="0" y2="0">
    <stop offset="0" stop-color="#ff7a00"/><stop offset="1" stop-color="#ff2a00"/>
  </linearGradient>
  <radialGradient id="lava" cx="40%" cy="35%" r="75%">
    <stop offset="0" stop-color="#fff1a0"/><stop offset=".35" stop-color="#ffb000"/>
    <stop offset=".75" stop-color="#ff5a00"/><stop offset="1" stop-color="#d81800"/>
  </radialGradient>
  <linearGradient id="floor" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#3a0d12"/><stop offset="1" stop-color="#120509"/>
  </linearGradient>
  <linearGradient id="title" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#fff08a"/><stop offset=".5" stop-color="#ff9a00"/><stop offset="1" stop-color="#ff3b00"/>
  </linearGradient>
  <linearGradient id="obs" x1="0" y1="0" x2="1" y2="1">
    <stop offset="0" stop-color="#34323c"/><stop offset="1" stop-color="#14121c"/>
  </linearGradient>
  <linearGradient id="hood" x1="0" y1="0" x2="1" y2="1">
    <stop offset="0" stop-color="#2a2832"/><stop offset=".5" stop-color="#15131c"/><stop offset="1" stop-color="#0b0911"/>
  </linearGradient>
  <radialGradient id="eye" cx="50%" cy="55%" r="60%">
    <stop offset="0" stop-color="#fff4b0"/><stop offset=".4" stop-color="#ffc400"/><stop offset=".8" stop-color="#ff6a00"/><stop offset="1" stop-color="#ff3b00"/>
  </radialGradient>
  <filter id="glow" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="14"/></filter>
  <filter id="glow2" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="5"/></filter>
  <filter id="soft" x="-20%" y="-20%" width="140%" height="140%"><feGaussianBlur stdDeviation="2"/></filter>
</defs>''')

    # ---------- fondo
    a(f'<rect width="{W}" height="{H}" fill="url(#bg)"/>')
    # rayos estilo anime
    cx, cy = 600, 560
    for i in range(28):
        a0 = i * 2 * math.pi / 28
        a1 = a0 + math.pi / 28
        p = [(cx, cy)] + [(cx + 1400 * math.cos(t), cy + 1400 * math.sin(t)) for t in (a0, a1)]
        a(f'<polygon points="{" ".join(f"{x:.0f},{y:.0f}" for x, y in p)}" fill="#ff7a1a" opacity=".07"/>')
    a(f'<circle cx="{cx}" cy="{cy}" r="470" fill="url(#sun)"/>')
    a(f'<circle cx="{cx}" cy="{cy}" r="400" fill="none" stroke="#ffb020" stroke-width="5" opacity=".35" stroke-dasharray="4 18" stroke-linecap="round"/>')
    a(f'<circle cx="{cx}" cy="{cy}" r="450" fill="none" stroke="#ff5a10" stroke-width="3" opacity=".4"/>')

    # ---------- suelo de lava
    a(f'<path d="M0,985 Q220,955 450,975 T900,968 T1200,975 V1200 H0 Z" fill="url(#floor)" stroke="{OUT}" stroke-width="{SW}"/>')
    a('<ellipse cx="600" cy="1010" rx="330" ry="40" fill="#ff5a00" opacity=".55" filter="url(#glow)"/>')
    for pts in ([(30, 1030), (130, 1060), (200, 1042), (310, 1085)], [(1170, 1040), (1070, 1062), (1010, 1050), (900, 1090)],
                [(250, 1130), (370, 1112), (460, 1140), (560, 1120)], [(960, 1128), (860, 1110), (770, 1140), (700, 1122)],
                [(120, 1160), (210, 1150), (290, 1175)], [(1080, 1165), (990, 1150), (920, 1178)]):
        d = "M" + " L".join(f"{x},{y}" for x, y in pts)
        a(f'<path d="{d}" fill="none" stroke="#ff4a00" stroke-width="14" stroke-linecap="round" stroke-linejoin="round" filter="url(#glow2)"/>')
        a(f'<path d="{d}" fill="none" stroke="#ffb000" stroke-width="7" stroke-linecap="round" stroke-linejoin="round"/>')
        a(f'<path d="{d}" fill="none" stroke="#fff1a0" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>')

    # ---------- sombra
    a('<ellipse cx="600" cy="1006" rx="190" ry="26" fill="#000" opacity=".55"/>')

    # ---------- piernas y botas
    for x0 in (500, 625):
        a(f'<rect x="{x0}" y="850" width="75" height="120" rx="14" fill="url(#obs)" stroke="{OUT}" stroke-width="{SW}"/>')
        a(f'<path d="M{x0 + 22},880 L{x0 + 34},905 L{x0 + 24},930" fill="none" stroke="#ff6a00" stroke-width="5" stroke-linecap="round" stroke-linejoin="round"/>')
        a(f'<rect x="{x0 - 14}" y="925" width="103" height="78" rx="30" fill="#2b292e" stroke="{OUT}" stroke-width="{SW}"/>')
        a(f'<path d="M{x0 - 4},945 Q{x0 + 38},932 {x0 + 80},945" fill="none" stroke="#59585f" stroke-width="6" stroke-linecap="round"/>')
        a(f'<rect x="{x0 - 14}" y="983" width="103" height="22" rx="11" fill="url(#lava)" stroke="{OUT}" stroke-width="{SW - 2}"/>')
    a(flame_cluster(537, 1004, [(-34, 26, 44, -6), (-8, 30, 62, 4), (22, 28, 50, 8), (44, 22, 34, 6)]))
    a(flame_cluster(662, 1004, [(-44, 22, 34, -6), (-20, 28, 50, -8), (8, 30, 62, -4), (36, 26, 44, 6)]))

    # ---------- brazo izquierdo (puno bajado)
    def arm(path, w=72):
        a(f'<path d="{path}" fill="none" stroke="{OUT}" stroke-width="{w + 14}" stroke-linecap="round" stroke-linejoin="round"/>')
        a(f'<path d="{path}" fill="none" stroke="#1e1c26" stroke-width="{w}" stroke-linecap="round" stroke-linejoin="round"/>')
        a(f'<path d="{path}" fill="none" stroke="#2f2d38" stroke-width="{w * .28}" stroke-linecap="round" transform="translate(-14,-4)" opacity=".8"/>')

    arm("M462,700 Q430,760 408,800")
    # brazo derecho (levantado con bola de fuego)
    arm("M738,700 Q880,740 936,640")

    # ---------- cuerpo
    a(f'<path d="M472,648 Q462,850 498,884 L702,884 Q738,850 728,648 Q600,622 472,648 Z" fill="#1e1c26" stroke="{OUT}" stroke-width="{SW}" stroke-linejoin="round"/>')
    a('<path d="M480,660 Q470,800 500,860 L520,860 Q496,780 506,665 Z" fill="#34323c"/>')
    a('<path d="M600,640 Q728,640 724,700 Q730,850 702,880 L650,880 Q690,780 660,650 Z" fill="#000" opacity=".28"/>')
    # peto
    a(f'<path d="M508,664 Q600,646 692,664 L684,790 Q600,826 516,790 Z" fill="url(#obs)" stroke="{OUT}" stroke-width="{SW - 1}" stroke-linejoin="round"/>')
    a('<path d="M516,672 Q600,656 684,672" fill="none" stroke="#6d6c75" stroke-width="5" stroke-linecap="round" opacity=".8"/>')
    # nucleo de lava
    a('<circle cx="600" cy="738" r="62" fill="#ff5a00" opacity=".75" filter="url(#glow)"/>')
    for ang, ln in ((200, 78), (250, 90), (300, 80), (340, 74), (160, 74), (110, 62), (70, 62)):
        r = math.radians(ang)
        x1, y1 = 600 + 40 * math.cos(r), 738 + 40 * math.sin(r)
        x2, y2 = 600 + ln * math.cos(r), 738 + ln * math.sin(r)
        xm, ym = (x1 + x2) / 2 + 6 * math.sin(r), (y1 + y2) / 2 - 6 * math.cos(r)
        a(f'<path d="M{x1:.0f},{y1:.0f} L{xm:.0f},{ym:.0f} L{x2:.0f},{y2:.0f}" fill="none" stroke="#ff7a00" stroke-width="5" stroke-linecap="round" stroke-linejoin="round"/>')
    a(f'<circle cx="600" cy="738" r="44" fill="url(#lava)" stroke="{OUT}" stroke-width="{SW - 1}"/>')
    a('<ellipse cx="585" cy="722" rx="14" ry="9" fill="#fffbe0" opacity=".9" transform="rotate(-30 585 722)"/>')
    # cinturon
    a(f'<rect x="480" y="846" width="240" height="38" rx="10" fill="#3b3a41" stroke="{OUT}" stroke-width="{SW - 1}"/>')
    a('<path d="M490,856 H710" stroke="#6d6c75" stroke-width="4" stroke-linecap="round" opacity=".7"/>')
    a(f'<rect x="572" y="846" width="56" height="38" rx="8" fill="url(#lava)" stroke="{OUT}" stroke-width="{SW - 1}"/>')
    # faldon roto
    a(f'<path d="M488,884 L506,918 L528,886 L552,924 L574,886 L600,930 L626,886 L648,922 L672,886 L694,916 L712,884 Z" fill="#14121c" stroke="{OUT}" stroke-width="{SW - 1}" stroke-linejoin="round"/>')
    a('<path d="M500,904 L506,918 L516,898 M590,908 L600,930 L612,906 M680,902 L694,916" fill="none" stroke="#ff5a00" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/>')

    # ---------- hombreras con pinchos
    for sx, ox in ((455, -1), (745, 1)):
        a(f'<path d="M{sx + ox * 22},{658} L{sx + ox * 62},{566} L{sx + ox * -2},{618} Z" fill="#2b292e" stroke="{OUT}" stroke-width="{SW - 1}" stroke-linejoin="round"/>')
        a(f'<path d="M{sx + ox * 54},{582} L{sx + ox * 62},{566} L{sx + ox * 36},{590} Z" fill="url(#lava)"/>')
        a(f'<circle cx="{sx}" cy="676" r="60" fill="url(#obs)" stroke="{OUT}" stroke-width="{SW}"/>')
        a(f'<path d="M{sx - 44},{660} Q{sx - 18},{624} {sx + 22},{628}" fill="none" stroke="#7a7983" stroke-width="7" stroke-linecap="round"/>')
        a(f'<path d="M{sx - 46},{698} Q{sx},{738} {sx + 46},{698}" fill="none" stroke="#ff6a00" stroke-width="7" stroke-linecap="round"/>')
        a(f'<circle cx="{sx}" cy="676" r="11" fill="url(#lava)" stroke="{OUT}" stroke-width="4"/>')

    # ---------- punos de lava
    # izquierdo
    a(f'<path d="M376,786 Q410,770 444,790 L440,800 Q410,790 380,802 Z" fill="#3b3a41"/>')
    a(f'<rect x="370" y="768" width="82" height="38" rx="14" fill="#3b3a41" stroke="{OUT}" stroke-width="{SW - 1}" transform="rotate(-20 411 787)"/>')
    a(f'<circle cx="402" cy="830" r="52" fill="url(#lava)" stroke="{OUT}" stroke-width="{SW}"/>')
    a(f'<path d="M376,826 H430 M388,846 H424" stroke="#c43a00" stroke-width="5" stroke-linecap="round"/>')
    a(flame_cluster(402, 800, [(-26, 22, 40, -6), (0, 26, 56, 2), (26, 22, 38, 8)]))
    # derecho + bola de fuego
    a(f'<rect x="896" y="612" width="76" height="36" rx="14" fill="#3b3a41" stroke="{OUT}" stroke-width="{SW - 1}" transform="rotate(-30 934 630)"/>')
    a(f'<circle cx="950" cy="590" r="50" fill="url(#lava)" stroke="{OUT}" stroke-width="{SW}"/>')
    a('<circle cx="958" cy="470" r="120" fill="#ff5a00" opacity=".55" filter="url(#glow)"/>')
    a(flame_cluster(958, 520, [(-50, 38, 90, -14), (-22, 44, 130, -6), (8, 48, 160, 6), (38, 42, 122, 14), (60, 28, 70, 18)]))
    a(f'<circle cx="958" cy="522" r="62" fill="url(#lava)" stroke="{OUT}" stroke-width="{SW}"/>')
    a('<ellipse cx="936" cy="500" rx="20" ry="12" fill="#fffbe0" opacity=".9" transform="rotate(-35 936 500)"/>')
    a(f'<path d="M918,572 Q956,598 994,570" fill="none" stroke="#ff3b00" stroke-width="6" stroke-linecap="round" opacity=".7"/>')

    # ---------- cabeza: cuernos
    for sx in (1, -1):
        bx = 600 + sx * 190
        a(f'<path d="M{bx},300 C{bx + sx * 70},270 {bx + sx * 100},190 {bx + sx * 78},120 '
          f'C{bx + sx * 46},190 {bx + sx * 36},230 {bx - sx * 8},250 Z" fill="url(#obs)" stroke="{OUT}" stroke-width="{SW}" stroke-linejoin="round"/>')
        a(f'<path d="M{bx + sx * 66},150 C{bx + sx * 86},190 {bx + sx * 82},160 {bx + sx * 78},120 C{bx + sx * 70},130 {bx + sx * 66},140 {bx + sx * 66},150 Z" fill="url(#lava)"/>')
        a(f'<path d="M{bx + sx * 8},280 C{bx + sx * 50},262 {bx + sx * 70},210 {bx + sx * 62},170" fill="none" stroke="#59585f" stroke-width="5" stroke-linecap="round" opacity=".8"/>')

    # ---------- llamas sobre la capucha
    a('<ellipse cx="600" cy="170" rx="210" ry="120" fill="#ff5a00" opacity=".55" filter="url(#glow)"/>')
    a(flame_cluster(600, 232, [(-130, 54, 120, -22), (-80, 66, 170, -14), (-30, 72, 214, -6), (22, 74, 236, 8),
                                (74, 68, 180, 14), (126, 56, 124, 22), (0, 40, 120, 0)]))

    # ---------- capucha
    hood = ("M385,612 C340,470 360,270 488,206 C540,178 572,150 600,118 C628,150 660,178 712,206 "
            "C840,270 860,470 815,612 C780,668 420,668 385,612 Z")
    a(f'<path d="{hood}" fill="url(#hood)" stroke="{OUT}" stroke-width="{SW + 2}" stroke-linejoin="round"/>')
    # luz de borde (naranja desde la lava + gris desde arriba)
    a('<path d="M398,560 C350,450 372,290 486,222" fill="none" stroke="#6d6c75" stroke-width="9" stroke-linecap="round" opacity=".7"/>')
    a('<path d="M420,640 Q600,690 780,640" fill="none" stroke="#ff6a00" stroke-width="9" stroke-linecap="round" opacity=".9"/>')
    # pliegues
    a('<path d="M450,250 Q470,330 440,420 M750,250 Q730,330 760,420 M600,150 L600,205" fill="none" stroke="#000" stroke-width="5" stroke-linecap="round" opacity=".5"/>')
    # grietas de lava en la capucha
    for d in ("M404,500 L430,478 L420,452 L446,430", "M796,510 L772,486 L782,458 L758,436"):
        a(f'<path d="{d}" fill="none" stroke="#ff4a00" stroke-width="9" stroke-linecap="round" stroke-linejoin="round" filter="url(#glow2)"/>')
        a(f'<path d="{d}" fill="none" stroke="#ffb000" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/>')

    # interior de la capucha y rostro vacio
    a('<path d="M432,430 C432,330 500,300 600,300 C700,300 768,330 768,430 C768,540 700,596 600,596 C500,596 432,540 432,430 Z" fill="#33060e" stroke="#0a0810" stroke-width="6"/>')
    a('<path d="M448,430 C448,342 506,318 600,318 C694,318 752,342 752,430 C752,528 694,580 600,580 C506,580 448,528 448,430 Z" fill="#050408"/>')
    a('<path d="M470,350 Q520,326 600,326" fill="none" stroke="#1d1a26" stroke-width="6" stroke-linecap="round" opacity=".9"/>')

    # cejas
    for m in (1, -1):
        def X(x):
            return x if m == 1 else mirror(x)
        a(f'<path d="M{X(468)},388 L{X(594)},432 L{X(590)},450 L{X(466)},408 Z" fill="#d81800" stroke="{OUT}" stroke-width="4" stroke-linejoin="round"/>')
    # ojos
    for m in (1, -1):
        def X(x):
            return x if m == 1 else mirror(x)
        eye = f"M{X(488)},424 Q{X(526)},394 {X(576)},446 Q{X(548)},482 {X(514)},478 Q{X(486)},462 {X(488)},424 Z"
        a(f'<path d="{eye}" fill="#ff6a00" filter="url(#glow)" opacity=".95"/>')
        a(f'<path d="{eye}" fill="url(#eye)" stroke="{OUT}" stroke-width="5" stroke-linejoin="round"/>')
        a(f'<ellipse cx="{X(524)}" cy="446" rx="13" ry="9" fill="#fffef0" transform="rotate({-28 * m} {X(524)} 446)"/>')
        a(f'<circle cx="{X(548)}" cy="462" r="4.5" fill="#fffef0"/>')
    # sonrisa
    smile = "M492,520 Q600,628 708,520 Q600,548 492,520 Z"
    a(f'<path d="{smile}" fill="#ff6a00" filter="url(#glow2)"/>')
    a(f'<path d="{smile}" fill="#ff7a00" stroke="{OUT}" stroke-width="5" stroke-linejoin="round"/>')
    for t in (0.1, 0.24, 0.38, 0.5, 0.62, 0.76, 0.9):
        x = 492 + 216 * t
        yu = 520 + 2 * 14 * t * (1 - t)
        hh = 22 if abs(t - .5) < .13 else 17
        a(f'<path d="M{x - 9:.0f},{yu:.0f} L{x + 9:.0f},{yu:.0f} L{x:.0f},{yu + hh:.0f} Z" fill="#fff1c0" stroke="{OUT}" stroke-width="3" stroke-linejoin="round"/>')

    # ---------- brasas
    for _ in range(70):
        x, y = random.uniform(40, 1160), random.uniform(60, 1000)
        r = random.choice([2, 3, 3, 4, 5, 6])
        col = random.choice(["#ffd84a", "#ff9a00", "#ff6a00", "#ff3b00"])
        a(f'<circle cx="{x:.0f}" cy="{y:.0f}" r="{r}" fill="{col}" opacity="{random.uniform(.5, 1):.2f}"/>')
    for _ in range(10):
        x, y = random.uniform(80, 1120), random.uniform(120, 940)
        a(f'<circle cx="{x:.0f}" cy="{y:.0f}" r="9" fill="#ff7a00" opacity=".5" filter="url(#glow2)"/>')

    # ---------- titulo
    a(f'<text x="600" y="1128" text-anchor="middle" font-family="DejaVu Sans, Inter, Arial Black, sans-serif" font-weight="900" '
      f'font-size="96" letter-spacing="3" fill="none" stroke="{OUT}" stroke-width="22" stroke-linejoin="round">OBSIDIAN DEMON</text>')
    a('<text x="600" y="1128" text-anchor="middle" font-family="DejaVu Sans, Inter, Arial Black, sans-serif" font-weight="900" '
      'font-size="96" letter-spacing="3" fill="url(#title)">OBSIDIAN DEMON</text>')
    a('</svg>')
    return "\n".join(s)


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.abspath(__file__))
    os.makedirs(out, exist_ok=True)
    svg_path = os.path.join(out, "obsidian_demon_cartoon.svg")
    png_path = os.path.join(out, "obsidian_demon_cartoon.png")
    with open(svg_path, "w") as f:
        f.write(build_svg())
    chrome = "/opt/pw-browsers/chromium-1194/chrome-linux/chrome"
    html = os.path.join(out, "_render.html")
    with open(html, "w") as f:
        f.write(f'<html><body style="margin:0;background:#000">{open(svg_path).read()}</body></html>')
    subprocess.run([chrome, "--headless", "--no-sandbox", "--disable-gpu", "--hide-scrollbars",
                    f"--window-size={W},{H + 200}", f"--screenshot={png_path}", f"file://{html}"],
                   check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    os.remove(html)
    from PIL import Image
    Image.open(png_path).crop((0, 0, W, H)).save(png_path)
    print("ok ->", png_path)


if __name__ == "__main__":
    main()

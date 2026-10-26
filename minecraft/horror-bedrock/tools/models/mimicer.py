"""The Mimicer.

From far away it is Steve. Closer, it is three blocks tall: the legs are too
long, the arms hang past its knees, the body is stretched like a skin pulled
over something bigger. The colours are Steve's, washed out, as if copied from
a photo of a photo. The face is the worst copy: where the eyes should be,
two big empty black sockets; the mouth hangs open, far too long, and the jaw
(its own bone) drops lower when it whispers. It copies voices."""

from models.kit import *
from models.humanoid import humanoid

PREVIEW = {}


def model():
    bones, h = humanoid(head=9, body=17, arm=27, leg=22, width=8.5, limb=3.5)
    neck = h["neck"]
    for b in bones:
        if b.name == "head":
            b.rotation = (8, 0, -12)
        if b.name == "body":
            b.rotation = (-8, 0, 0)
    # The jaw hangs out of the face, far below where a chin should be.
    bones.append(B("jaw", "head", (0, neck + 1, -4.5), rotation=(14, 0, 0), cubes=[
        C((-3, neck - 5, -5.25), (6, 6, 1.25), "jaw"),
        C((-3, neck - 5, -4), (6, 1, 3), "jaw")]))
    return hd_model("geometry.hb.mimicer", bones, bounds=(2.0, 3.6, (0, 1.6, 0)))


def paint(seed, m, **_):
    skin = Ramp(colors=["#5e5048", "#7c6a5e", "#9a8678", "#b29e8e", "#c8b4a2", "#d8c6b4"])
    hair = Ramp(colors=["#14100c", "#201810", "#2e2216", "#3c2e1e"])
    shirt = Ramp(colors=["#2a3e44", "#36505a", "#44646e", "#547882", "#668c94"])
    pants = Ramp(colors=["#1c1c34", "#262646", "#323258", "#3e3e6a"])
    hole = (0x01, 0x01, 0x02)
    gum = Ramp(colors=["#1a0406", "#2c080c", "#420e14"])

    def copy_noise(t):
        # A copy of a copy: blocky smears where the colours didn't come through.
        return t.n2(1.2) > 0.82

    def fn(t):
        tag, f = t.tag, t.face
        if tag == "head":
            if f == "front":
                # 18x18 design px: two big empty sockets, deep black, and below
                # them the top of the mouth, hanging open.
                for cx in (5, 12.5):
                    if (t.x - cx) ** 2 / 12 + (t.y - 8) ** 2 / 18 <= 1:
                        return hole
                if t.y >= 13 and 5 <= t.x <= 12:
                    return hole if t.y > 13 or t.x % 2 else t.paint(TEETH, 0.6, 0.2)
                if t.y < 4:
                    return t.paint(hair, 0.5, 0.3)
                return t.paint(skin, 0.5 if not copy_noise(t) else 0.25, 0.25)
            if f == "top" or (t.side and t.y < 5) or f == "back":
                return t.paint(hair, 0.45, 0.35, 1.5)
            return t.paint(skin, 0.45, 0.25)
        if tag == "jaw":
            if f == "top":
                return t.paint(gum, 0.5, 0.4)
            if f == "front":
                # The open mouth, long, and a row of teeth at the bottom of it.
                if t.y >= t.fh - 3 and 1 <= t.x <= t.fw - 2:
                    return t.paint(TEETH, 0.55, 0.2) if t.x % 2 else hole
                if 1 <= t.x <= t.fw - 2:
                    return hole
            return t.paint(skin, 0.42, 0.25)
        if tag.endswith("+"):
            return CLEAR
        if tag == "body":
            return t.paint(shirt, 0.5 if not copy_noise(t) else 0.2, 0.35)
        if tag == "arm":
            return t.paint(shirt if t.y < 10 or f == "top" else skin, 0.45, 0.3)
        if tag == "forearm":
            # The hands at the end, long and grey.
            return t.paint(skin, 0.45 - (0.2 if t.y > t.fh - 6 else 0), 0.25)
        if tag in ("leg", "shin"):
            if tag == "shin" and t.y >= t.fh - 3:
                return t.paint(CLOTH_DARK, 0.5, 0.2)
            return t.paint(pants, 0.5 if not copy_noise(t) else 0.2, 0.3)
        return None

    return paint_model(m, seed, fn)

"""Entity animations for Horror Legends, written out by build.py.

Every animation is procedural Molang, layered in the client entity files
(content.py says which play when): a base cycle (walk / idle), secondary
motion (tails, coats, hair, smoke) and state overlays driven by entity
properties (a chase, a scream, a pose). Rotations follow Bedrock's
conventions: for a limb hanging down, negative X swings it forwards; for a
bone pointing up (a tail, a pole), positive X tips it forwards.
"""

D = "query.modified_distance_moved"
S = "query.modified_move_speed"
T = "query.life_time"
A = "variable.attack_time"
H = "query.hurt_time"

def cos(freq, amp, phase=""):
    return f"math.cos({D} * {freq}{phase}) * {amp} * {S}"

def sin(freq, amp):
    return f"math.sin({D} * {freq}) * {amp} * {S}"

def lift(freq, amp, sign=1):
    """Only the positive half of a sine: a knee that bends, a foot that lifts."""
    s = "" if sign > 0 else "-"
    return f"math.max(0.0, {s}math.sin({D} * {freq})) * {amp} * {S}"

def wave(rate, amp, offset=0.0, phase=0.0):
    """A slow idle wave over time."""
    base = f"{offset} + " if offset else ""
    ph = f" + {phase}" if phase else ""
    return f"{base}math.sin({T} * {rate}{ph}) * {amp}"

def jolt(p, lo, hi):
    """Usually nothing; now and then a random jerk."""
    return f"(math.random(0.0, 1.0) > {p} ? math.random({lo}, {hi}) : 0.0)"

def breathe(rate, depth):
    return [1.0, f"1.0 + math.sin({T} * {rate}) * {depth}", 1.0]

def swing(amp, axis=0):
    """An attack swing: 0 -> amp -> 0 over the attack."""
    return f"math.sin({A} * 180.0) * {amp}"

def rot(x=0, y=0, z=0):
    return {"rotation": [x, y, z]}

def tail(bones, rate, amp, lag=40.0, sway=0.0):
    """A chain of bones undulating one after another (a tail, a spine)."""
    out = {}
    for i, b in enumerate(bones):
        out[b] = rot(wave(rate, amp, phase=-lag * i), wave(rate * 0.7, sway, phase=-lag * i) if sway else 0, 0)
    return out

LOOK = {"head": rot("query.target_x_rotation", "query.target_y_rotation")}

EMERGE = f"math.max(0.0, 1.0 - {T} * 1.6)"  # 1 -> 0 over the first 0.6 s of its life

def emerge(depth, bones=("root",), tilt=0.0):
    """Climbing up out of the ground (or the water) when it appears."""
    out = {}
    for b in bones:
        out[b] = {"position": [0, f"-{EMERGE} * {EMERGE} * {depth}", 0]}
        if tilt:
            out[b]["rotation"] = [f"{EMERGE} * {tilt}", 0, 0]
    return out

def flinch(bones, amount=1.4):
    """A sharp recoil when hurt (hurt_time counts down from 10)."""
    return {b: rot(f"-{H} * {amount}", 0, f"{H} * {amount * 0.6}" if i == 0 else 0) for i, b in enumerate(bones)}

def look(bone="head", k=1.0, pitch=True):
    x = f"query.target_x_rotation * {k}" if pitch else 0
    return {"loop": True, "bones": {bone: rot(x, f"query.target_y_rotation * {k}")}}

ANIMATIONS = {
    # ------------------------------------------------------------------ #
    # The Mandela Catalogue
    # ------------------------------------------------------------------ #
    # The Intruder: stooped in the corner, swaying very slightly.
    "animation.hh.intruder.look": look("head", 0.9),
    "animation.hh.intruder.idle": {"loop": True, "bones": {
        "body": rot(f"10.0 + {wave(20.0, 1.5)}", 0, wave(14.0, 1.5)),
        "head": rot(0, 0, f"{wave(11.0, 6.0)} + {jolt(0.99, -20.0, 20.0)}"),
        "rightForearm": rot(wave(25.0, 4.0)), "leftForearm": rot(wave(22.0, -4.0))}},
    # Seen: it straightens up, the head tilts all the way over, the arms rise.
    "animation.hh.intruder.stare": {"loop": True, "bones": {
        "body": rot(-4.0), "neck": rot(-14.0), "head": rot(-10.0, 0, f"38.0 + {jolt(0.9, -6.0, 6.0)}"),
        "rightArm": rot(-35.0, 0, 10.0), "leftArm": rot(-35.0, 0, -10.0)}},

    # The Alternate: an ordinary walk... and then the head comes up out of it.
    "animation.hh.alternate.look": look("head", 0.9),
    "animation.hh.alternate.idle": {"loop": True, "bones": {
        "body": {"scale": breathe(90.0, 0.01)},
        "rightArm": rot(0, 0, wave(36.0, 2.0)), "leftArm": rot(0, 0, wave(36.0, -2.0))}},
    "animation.hh.alternate.walk": {"loop": True, "bones": {
        "rightLeg": rot(cos(36, 28.0)), "leftLeg": rot(cos(36, -28.0)),
        "rightArm": rot(cos(36, -24.0)), "leftArm": rot(cos(36, 24.0))}},
    "animation.hh.alternate.reveal": {"loop": True, "bones": {
        "trueHead": rot(f"{jolt(0.93, -18.0, 18.0)}", 0, f"{wave(40.0, 10.0)} + {jolt(0.93, -25.0, 25.0)}"),
        "trueJaw": rot(f"10.0 + math.abs(math.sin({T} * 400.0)) * 18.0"),
        "rightArm": rot(-30.0), "leftArm": rot(-30.0)}},
    "animation.hh.alternate.attack": {"loop": True, "bones": {
        "rightArm": rot(f"-{swing(100.0)}"), "leftArm": rot(f"-{swing(80.0)}"), "trueJaw": rot(swing(30.0))}},

    # Gabriel: floating, the wings slowly breathing, the halo turning.
    "animation.hh.gabriel.float": {"loop": True, "bones": {
        "root": {"position": [0, f"6.0 + {wave(50.0, 1.5)}", 0]},
        "halo": {"rotation": [0, f"{T} * 40.0", 0]},
        "rags": rot(wave(60.0, 6.0), 0, wave(45.0, 4.0)),
        "wingRightUpper": rot(0, wave(30.0, 6.0), 0), "wingLeftUpper": rot(0, wave(30.0, -6.0), 0),
        "wingRightLower": rot(0, wave(30.0, 4.0, 0, 40.0), 0), "wingLeftLower": rot(0, wave(30.0, -4.0, 0, 40.0), 0)}},
    "animation.hh.gabriel.look": look("head", 1.0),
    # Revealed: the wings spread wide and the arms open, as if to embrace.
    "animation.hh.gabriel.open": {"loop": True, "bones": {
        "wingRightUpper": rot(0, -30.0, 0), "wingLeftUpper": rot(0, 30.0, 0),
        "wingRightLower": rot(0, -25.0, 0), "wingLeftLower": rot(0, 25.0, 0),
        "rightArm": rot(-20.0, 0, 70.0), "leftArm": rot(-20.0, 0, -70.0),
        "head": rot(0, 0, f"{jolt(0.85, -12.0, 12.0)}")}},

    # ------------------------------------------------------------------ #
    # Local 58: the moon, enormous
    # ------------------------------------------------------------------ #
    "animation.hh.moon.hang": {"loop": True, "bones": {
        "root": {"scale": 6.0}, "moon": rot(0, 0, wave(4.0, 2.0))}},

    # ------------------------------------------------------------------ #
    # The Whistle Occurrence: Flowers
    # ------------------------------------------------------------------ #
    "animation.hh.flowers.look": look("head", 1.0),
    "animation.hh.flowers.idle": {"loop": True, "bones": {
        "head": rot(0, 0, f"{wave(15.0, 8.0)} + {jolt(0.97, -40.0, 40.0)}"),
        "rightUpperArm": rot(jolt(0.98, -30.0, 30.0)), "leftUpperArm": rot(jolt(0.98, -30.0, 30.0)),
        "upperTorso": rot(0, jolt(0.985, -25.0, 25.0), 0)}},
    # Stretching: up and up, the neck and the limbs pulling out.
    "animation.hh.flowers.stretch": {"loop": True, "bones": {
        "root": {"scale": [1.0, f"1.0 + math.min({T} * 0.12, 0.7)", 1.0]},
        "neck": {"scale": [1.0, f"1.0 + math.min({T} * 0.5, 2.5)", 1.0]},
        "rightUpperArm": rot(f"-{wave(200.0, 40.0)}", 0, 20.0), "leftUpperArm": rot(wave(170.0, 40.0), 0, -20.0)}},

    # ------------------------------------------------------------------ #
    # The Sun Vanished: a Strobe, hanging in the air
    # ------------------------------------------------------------------ #
    "animation.hh.strobe.hover": {"loop": True, "bones": {
        "root": {"position": [0, f"{wave(70.0, 1.5)}", 0]},
        "hull": rot(wave(23.0, 3.0), 0, wave(31.0, 4.0))}},
    "animation.hh.strobe.look": look("hull", 1.0),

    # ------------------------------------------------------------------ #
    # Siren Head
    # ------------------------------------------------------------------ #
    "animation.hh.siren.look": {"loop": True, "bones": {
        "sirens": rot("query.target_x_rotation * 0.3", "query.target_y_rotation * 0.8")}},
    "animation.hh.siren.idle": {"loop": True, "bones": {
        "pole": rot(wave(18.0, 1.5), 0, wave(13.0, 2.0)),
        "sirens": rot(0, wave(9.0, 10.0), 0),
        "chest": {"scale": breathe(20.0, 0.015)},
        "rightHand": rot(jolt(0.97, -12.0, 12.0)),
        "leftHand": rot(jolt(0.97, -12.0, 12.0))}},
    "animation.hh.siren.walk": {"loop": True, "bones": {
        "rightLeg": rot(cos(14, 26.0)), "leftLeg": rot(cos(14, -26.0)),
        "rightShin": rot(lift(14, 30.0)), "leftShin": rot(lift(14, 30.0, -1)),
        "rightArm": rot(cos(14, -16.0)), "leftArm": rot(cos(14, 16.0)),
        "body": rot(0, sin(14.0, 4.0), sin(14.0, 3.0)),
        "pole": rot(f"math.abs(math.sin({D} * 28.0)) * 3.0 * {S}")}},
    "animation.hh.siren.hunt": {"loop": True, "bones": {
        "sirens": rot(f"-10.0 + math.sin({T} * 1300.0) * 3.0", 0, f"math.cos({T} * 1100.0) * 3.0"),
        "pole": rot(10.0), "chest": rot(6.0)}},
    "animation.hh.siren.attack": {"loop": True, "bones": {
        "rightArm": rot(f"-{swing(110.0)}"), "leftArm": rot(f"-{swing(100.0)}"),
        "rightForearm": rot(f"-{swing(40.0)}"), "leftForearm": rot(f"-{swing(40.0)}"),
        "chest": rot(swing(18.0))}},

    # ------------------------------------------------------------------ #
    # Cartoon Cat: rubber hose
    # ------------------------------------------------------------------ #
    "animation.hh.cat.look": look("head", 0.9),
    # Bouncing on the spot like an old cartoon, the tail swinging.
    "animation.hh.cat.idle": {"loop": True, "bones": {
        "body": {"position": [0, f"math.abs(math.sin({T} * 180.0)) * 0.8", 0]},
        "head": rot(0, 0, f"{wave(180.0, 6.0)}"),
        "tail1": rot(0, wave(90.0, 20.0), 0), "tail2": rot(0, wave(90.0, 25.0, 0, 60.0), 0),
        "rightArm": rot(0, 0, wave(180.0, 8.0)), "leftArm": rot(0, 0, wave(180.0, -8.0)),
        "rightForearm": rot(wave(180.0, 12.0)), "leftForearm": rot(wave(180.0, -12.0))}},
    # Running: legs windmilling, arms flailing, head thrown forward.
    "animation.hh.cat.run": {"loop": True, "bones": {
        "body": rot(18.0),
        "rightLeg": rot(cos(28, 50.0)), "leftLeg": rot(cos(28, -50.0)),
        "rightShin": rot(lift(28, 50.0)), "leftShin": rot(lift(28, 50.0, -1)),
        "rightArm": rot(f"-60.0 + {cos(28, -50.0)}"), "leftArm": rot(f"-60.0 + {cos(28, 50.0)}"),
        "rightForearm": rot(cos(28, 40.0)), "leftForearm": rot(cos(28, -40.0)),
        "head": rot(-16.0, 0, sin(28.0, 10.0))}},
    "animation.hh.cat.attack": {"loop": True, "bones": {
        "rightArm": rot(f"-{swing(130.0)}"), "leftArm": rot(f"-{swing(110.0)}"), "head": rot(swing(25.0))}},
}

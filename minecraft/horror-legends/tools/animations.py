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


def look(bone="head", k=1.0, pitch=True):
    x = f"query.target_x_rotation * {k}" if pitch else 0
    return {"loop": True, "bones": {bone: rot(x, f"query.target_y_rotation * {k}")}}


ANIMATIONS = {
    # ------------------------------------------------------------------ #
    # HerobrineGamer788: moves like any other player
    # ------------------------------------------------------------------ #
    "animation.hl.player.look": look(),
    "animation.hl.player.walk": {"loop": True, "bones": {
        "rightLeg": rot(cos(38.17, 50.0)),
        "leftLeg": rot(cos(38.17, -50.0)),
        "rightArm": rot(cos(38.17, -40.0)),
        "leftArm": rot(cos(38.17, 40.0)),
        "body": rot(0, sin(38.17, 2.0))}},
    "animation.hl.player.idle": {"loop": True, "bones": {
        "rightArm": rot(wave(76.8, 2.9), 0, f"math.cos({T} * 103.2) * 2.9 + 2.9"),
        "leftArm": rot(wave(76.8, -2.9), 0, f"-math.cos({T} * 103.2) * 2.9 - 2.9"),
        "body": {"scale": breathe(90.0, 0.012)}}},
    "animation.hl.player.sneak": {"loop": True, "bones": {
        "root": {"position": [0, -1.5, 0]},
        "body": rot(28.0),
        "rightLeg": {"position": [0, 0, 4.0]},
        "leftLeg": {"position": [0, 0, 4.0]},
        "rightArm": rot(-12.0),
        "leftArm": rot(-12.0)}},
    # Chopping at a block: quick arm swings, a small twist of the body.
    "animation.hl.player.swing": {"loop": True, "bones": {
        "rightArm": rot(f"-65.0 + math.sin({T} * 1400.0) * 38.0"),
        "body": rot(0, f"math.sin({T} * 1400.0) * 6.0"),
        "head": rot(12.0)}},
    # Something is wrong: arms dead at his sides, head cocked, twitching.
    "animation.hl.player.stare": {"loop": True, "bones": {
        "rightArm": rot(0, 0, 2.0),
        "leftArm": rot(0, 0, -2.0),
        "head": rot(6.0, 0, f"14.0 + {jolt(0.96, -12.0, 12.0)}")}},
    "animation.hl.player.air": {"loop": True, "bones": {
        "rightLeg": rot(-12.0), "leftLeg": rot(8.0),
        "rightArm": rot(0, 0, 8.0), "leftArm": rot(0, 0, -8.0)}},

    # ------------------------------------------------------------------ #
    # Herobrine: the scorpion tail never stops moving
    # ------------------------------------------------------------------ #
    "animation.hl.herobrine.look": look("head", 0.6),
    "animation.hl.herobrine.idle": {"loop": True, "bones": {
        "body": rot(wave(25.0, 1.2)),
        "chest": {"scale": breathe(25.0, 0.025)},
        "jaw": rot(f"{wave(50.0, 6.0)} + {jolt(0.92, 0.0, 14.0)}", 0, wave(20.0, 3.0)),
        "gutHang": rot(wave(70.0, 11.0), 0, f"math.cos({T} * 55.0) * 7.0"),
        "drip": {"scale": [1.0, f"1.0 + math.sin({T} * 90.0) * 0.3", 1.0]},
        "leftHand": rot(wave(33.0, 4.0)),
        "rightHand": rot(wave(29.0, 3.0))}},
    # The tail sways from side to side and flexes; the sting twitches.
    "animation.hl.herobrine.tail": {"loop": True, "bones": {
        **tail(["tail1", "tail2", "tail3", "tail4"], 45.0, 5.0, 35.0, 6.0),
        "sting": rot(f"{wave(45.0, 8.0, phase=-140.0)} + {jolt(0.93, -20.0, 10.0)}", 0, 0)}},
    "animation.hl.herobrine.twitch": {"loop": True, "bones": {
        "head": rot(jolt(0.94, -16.0, 16.0), jolt(0.95, -28.0, 28.0), jolt(0.9, -22.0, 22.0)),
        "chest": rot(0, 0, jolt(0.97, -8.0, 8.0)),
        "rightArm": rot(jolt(0.97, -12.0, 12.0)),
        "leftForearm": rot(jolt(0.96, -18.0, 6.0))}},
    # A dragging limp: the right leg steps, the left one follows stiff.
    "animation.hl.herobrine.walk": {"loop": True, "bones": {
        "rightThigh": rot(cos(26, 30.0)),
        "rightShin": rot(lift(26, 36.0)),
        "leftThigh": rot(cos(26, -18.0)),
        "leftShin": rot(lift(26, 10.0, -1)),
        "body": rot(0, 0, sin(26.0, 5.0)),
        "leftArm": rot(cos(26, 16.0)),
        "rightArm": rot(cos(26, -8.0))}},
    # The sting whips over his head at you.
    "animation.hl.herobrine.strike": {"loop": True, "bones": {
        "tail2": rot(swing(20.0)), "tail3": rot(swing(25.0)), "tail4": rot(swing(30.0)),
        "sting": rot(swing(40.0)), "chest": rot(swing(-10.0))}},

    # ------------------------------------------------------------------ #
    # The Hollow Miner (the boss)
    # ------------------------------------------------------------------ #
    "animation.hl.boss.look": look("head", 0.7),
    "animation.hl.boss.idle": {"loop": True, "bones": {
        "chest": {"scale": breathe(30.0, 0.025)},
        "jaw": rot(wave(40.0, 8.0), 0, wave(17.0, 3.0)),
        "head": rot(0, 0, wave(20.0, 3.0)),
        "fist": rot(wave(50.0, 4.0)),
        "pick": rot(0, 0, wave(23.0, 3.0)),
        **tail(["tail1", "tail2", "tail3"], 35.0, 4.0, 40.0, 5.0),
        "sting": rot(wave(35.0, 10.0, phase=-120.0))}},
    "animation.hl.boss.walk": {"loop": True, "bones": {
        "rightLeg": rot(cos(22, 28.0)),
        "leftLeg": rot(cos(22, -28.0)),
        "rightShin": rot(lift(22, 28.0)),
        "leftShin": rot(lift(22, 28.0, -1)),
        "rightArm": rot(cos(22, -14.0)),
        "leftArm": rot(cos(22, 14.0)),
        "body": rot(0, sin(22.0, 4.0), sin(22.0, 3.0)),
        "head": rot(f"math.abs(math.sin({D} * 44.0)) * 4.0 * {S}")}},
    # The pickaxe comes up over his head and down; the fist and the sting follow.
    "animation.hl.boss.attack": {"loop": True, "bones": {
        "rightArm": rot(f"-{swing(150.0)}", 0, swing(10.0)),
        "rightForearm": rot(f"-{swing(30.0)}"),
        "chest": rot(swing(12.0), f"-{swing(15.0)}"),
        "jaw": rot(swing(20.0)),
        "sting": rot(swing(50.0)), "tail3": rot(swing(20.0))}},
    # Arms up, head back, the core flaring: calling something down.
    "animation.hl.boss.cast": {"loop": True, "bones": {
        "rightArm": rot(-150.0, 0, wave(400.0, 4.0, 20.0)),
        "leftArm": rot(-150.0, 0, f"-20.0 - math.sin({T} * 400.0) * 4.0"),
        "head": rot(-25.0), "jaw": rot(35.0), "chest": rot(-8.0),
        "sting": rot(-30.0), "tail2": rot(-15.0)}},
    "animation.hl.boss.rage": {"loop": True, "bones": {
        "head": rot(jolt(0.9, -18.0, 18.0), jolt(0.92, -30.0, 30.0), jolt(0.9, -20.0, 20.0)),
        "chest": rot(0, 0, jolt(0.94, -6.0, 6.0)),
        "fist": rot(jolt(0.93, -15.0, 15.0)),
        "tail1": rot(0, 0, jolt(0.9, -10.0, 10.0))}},

    # ------------------------------------------------------------------ #
    # null: the slices slip, the head skips frames
    # ------------------------------------------------------------------ #
    "animation.hl.null.look": look(),
    "animation.hl.null.glitch": {"loop": True, "bones": {
        "root": {"position": [jolt(0.93, -1.5, 1.5), 0, jolt(0.93, -1.5, 1.5)]},
        "torso": {"position": [jolt(0.85, -1.5, 1.5), 0, 0]},
        "rightLeg": {"position": [jolt(0.9, -1.0, 1.0), 0, 0]},
        "head": {"rotation": [0, jolt(0.96, -45.0, 45.0), 0],
                 "position": [jolt(0.88, -1.5, 1.5), 0, 0],
                 "scale": [1.0, f"1.0 + {jolt(0.97, -0.2, 0.3)}", 1.0]},
        "leftForearm": {"position": [0, f"{wave(90.0, 0.8)} + {jolt(0.9, -1.0, 1.0)}", 0]},
        "halo": {"position": ["math.random(-0.6, 0.6)", "math.random(-0.6, 0.6)", "math.random(-0.6, 0.6)"],
                 "rotation": [0, f"{T} * 80.0", 0]},
        "hitbox": {"scale": [1.0, f"1.0 + {jolt(0.97, -0.05, 0.05)}", 1.0]}}},

    # ------------------------------------------------------------------ #
    # The Man From The Fog
    # ------------------------------------------------------------------ #
    "animation.hl.fog_man.look": look("head", 0.8),
    "animation.hl.fog_man.walk": {"loop": True, "bones": {
        "rightLeg": rot(cos(28, 32.0)),
        "leftLeg": rot(cos(28, -32.0)),
        "rightShin": rot(lift(28, 40.0)),
        "leftShin": rot(lift(28, 40.0, -1)),
        "rightArm": rot(cos(28, -18.0)),
        "leftArm": rot(cos(28, 18.0)),
        "body": rot(0, 0, sin(28.0, 3.0)),
        "coatBack": rot(f"math.abs(math.sin({D} * 28.0)) * 10.0 * {S}"),
        "coatRight": rot(lift(28, 16.0, -1)),
        "coatLeft": rot(lift(28, 16.0)),
        "coatSideR": rot(lift(28, 10.0, -1)),
        "coatSideL": rot(lift(28, 10.0))}},
    # Watching: perfectly still but for the head tilting and the coat moving
    # in a wind that isn't there.
    "animation.hl.fog_man.watch": {"loop": True, "bones": {
        "chest": {"scale": breathe(30.0, 0.02)},
        "head": rot(0, 0, wave(20.0, 8.0)),
        "jaw": rot(wave(60.0, 2.0)),
        "rightHand": rot(jolt(0.95, -15.0, 15.0)),
        "leftHand": rot(jolt(0.95, -15.0, 15.0)),
        "coatBack": rot(wave(45.0, 3.0)),
        "coatRight": rot(wave(50.0, 4.0)),
        "coatLeft": rot(wave(47.0, 4.0, phase=60.0)),
        "hood": rot(wave(35.0, 2.0))}},
    # Chasing: bent double, the grin wide open, arms swinging to his knees.
    "animation.hl.fog_man.chase": {"loop": True, "bones": {
        "body": rot(22.0), "chest": rot(8.0), "neck": rot(-16.0), "head": rot(-18.0, 0, -10.0),
        "jaw": rot(f"28.0 + math.sin({T} * 1100.0) * 6.0"),
        "rightArm": rot(f"40.0 + math.cos({D} * 48.0) * 40.0"),
        "leftArm": rot(f"40.0 - math.cos({D} * 48.0) * 40.0"),
        "rightForearm": rot(-25.0), "leftForearm": rot(-25.0),
        "coatBack": rot(f"38.0 + math.sin({T} * 900.0) * 6.0"),
        "coatRight": rot(f"30.0 + math.sin({T} * 800.0) * 8.0"),
        "coatLeft": rot(f"32.0 + math.cos({T} * 850.0) * 8.0"),
        "coatSideR": rot(20.0), "coatSideL": rot(20.0), "hood": rot(20.0)}},
    "animation.hl.fog_man.attack": {"loop": True, "bones": {
        "chest": rot(swing(14.0)),
        "rightArm": rot(f"-{swing(130.0)}"),
        "leftArm": rot(f"-math.sin(math.clamp({A} * 1.4, 0.0, 1.0) * 180.0) * 105.0"),
        "jaw": rot(swing(30.0))}},
    "animation.hl.fog_man.hurt": {"loop": True, "bones": {
        "chest": rot(f"-{H} * 1.6"), "head": rot(f"-{H} * 1.4", 0, f"{H} * 1.2")}},

    # ------------------------------------------------------------------ #
    # The Cave Dweller
    # ------------------------------------------------------------------ #
    # A skitter: each long limb swings round (y) and lifts (z) in turn.
    "animation.hl.dweller.walk": {"loop": True, "bones": {
        "armRight": rot(0, cos(40, 16.0), lift(40, -14.0)),
        "armLeft": rot(0, cos(40, 16.0), lift(40, 14.0, -1)),
        "thighRight": rot(0, cos(40, -16.0), lift(40, -12.0, -1)),
        "thighLeft": rot(0, cos(40, -16.0), lift(40, 12.0)),
        "ribcage": rot(0, sin(40.0, 4.0), sin(80.0, 2.0)),
        "pelvis": rot(0, sin(40.0, -6.0))}},
    "animation.hl.dweller.idle": {"loop": True, "bones": {
        "ribcage": {"scale": breathe(120.0, 0.04)},
        "handRight": rot(0, 0, jolt(0.95, -10.0, 10.0)),
        "handLeft": rot(0, 0, jolt(0.95, -10.0, 10.0))}},
    "animation.hl.dweller.look": {"loop": True, "bones": {
        "neck": rot("query.target_x_rotation * 0.4", "query.target_y_rotation * 0.4"),
        "head": rot("query.target_x_rotation * 0.4", "query.target_y_rotation * 0.5")}},
    "animation.hl.dweller.twitch": {"loop": True, "bones": {
        "head": rot(0, 0, jolt(0.9, -35.0, 35.0))}},
    # The snout breathing: the four lips part a little and close.
    "animation.hl.dweller.lips_idle": {"loop": True, "bones": {
        "lipTop": rot(f"-math.max(0.0, math.sin({T} * 60.0)) * 8.0"),
        "lipBottom": rot(f"math.max(0.0, math.sin({T} * 60.0)) * 8.0"),
        "lipRight": rot(0, f"-math.max(0.0, math.sin({T} * 60.0)) * 8.0"),
        "lipLeft": rot(0, f"math.max(0.0, math.sin({T} * 60.0)) * 8.0")}},
    # Hunting: the snout peels open like a flower and quivers.
    "animation.hl.dweller.lips_open": {"loop": True, "bones": {
        "lipTop": rot(f"-70.0 + math.sin({T} * 900.0) * 8.0"),
        "lipBottom": rot(f"70.0 - math.sin({T} * 900.0) * 8.0"),
        "lipRight": rot(0, f"-70.0 + math.cos({T} * 900.0) * 8.0"),
        "lipLeft": rot(0, f"70.0 - math.cos({T} * 900.0) * 8.0"),
        "head": rot(-12.0)}},
    "animation.hl.dweller.attack": {"loop": True, "bones": {
        "ribcage": rot(swing(12.0)),
        "neck": rot(swing(-24.0)),
        "armRight": rot(f"-{swing(50.0)}"),
        "armLeft": rot(f"-{swing(30.0)}")}},
    "animation.hl.dweller.hurt": {"loop": True, "bones": {
        "ribcage": rot(f"-{H} * 1.5"), "head": rot(f"-{H} * 1.5", 0, f"{H} * 2.0")}},

    # ------------------------------------------------------------------ #
    # Siren Head
    # ------------------------------------------------------------------ #
    "animation.hl.siren.look": {"loop": True, "bones": {
        "sirens": rot("query.target_x_rotation * 0.3", "query.target_y_rotation * 0.8")}},
    "animation.hl.siren.idle": {"loop": True, "bones": {
        "pole": rot(wave(18.0, 1.5), 0, wave(13.0, 2.0)),
        "sirens": rot(0, wave(9.0, 10.0), 0),
        "chest": {"scale": breathe(20.0, 0.015)},
        "rightHand": rot(jolt(0.97, -12.0, 12.0)),
        "leftHand": rot(jolt(0.97, -12.0, 12.0))}},
    # Huge, slow strides.
    "animation.hl.siren.walk": {"loop": True, "bones": {
        "rightLeg": rot(cos(14, 26.0)),
        "leftLeg": rot(cos(14, -26.0)),
        "rightShin": rot(lift(14, 30.0)),
        "leftShin": rot(lift(14, 30.0, -1)),
        "rightArm": rot(cos(14, -16.0)),
        "leftArm": rot(cos(14, 16.0)),
        "body": rot(0, sin(14.0, 4.0), sin(14.0, 3.0)),
        "pole": rot(f"math.abs(math.sin({D} * 28.0)) * 3.0 * {S}")}},
    # Blaring: the sirens shake, the pole leans towards you.
    "animation.hl.siren.hunt": {"loop": True, "bones": {
        "sirens": rot(f"-10.0 + math.sin({T} * 1300.0) * 3.0", 0, f"math.cos({T} * 1100.0) * 3.0"),
        "pole": rot(10.0), "chest": rot(6.0)}},
    "animation.hl.siren.attack": {"loop": True, "bones": {
        "rightArm": rot(f"-{swing(110.0)}"), "leftArm": rot(f"-{swing(100.0)}"),
        "rightForearm": rot(f"-{swing(40.0)}"), "leftForearm": rot(f"-{swing(40.0)}"),
        "chest": rot(swing(18.0))}},

    # ------------------------------------------------------------------ #
    # The Goatman
    # ------------------------------------------------------------------ #
    "animation.hl.goatman.look": look("head", 0.7),
    "animation.hl.goatman.idle": {"loop": True, "bones": {
        "torso": {"scale": breathe(40.0, 0.02)},
        "head": rot(0, 0, f"{wave(15.0, 10.0)} + {jolt(0.95, -20.0, 20.0)}"),
        "jaw": rot(f"math.max(0.0, math.sin({T} * 30.0)) * 6.0"),
        "rightForearm": rot(wave(25.0, 4.0)), "leftForearm": rot(wave(22.0, 4.0))}},
    # Goat legs: the thigh swings, the long shin kicks back.
    "animation.hl.goatman.walk": {"loop": True, "bones": {
        "rightLeg": rot(cos(32, 30.0)),
        "leftLeg": rot(cos(32, -30.0)),
        "rightShin": rot(lift(32, -30.0)),
        "leftShin": rot(lift(32, -30.0, -1)),
        "rightArm": rot(cos(32, -20.0)),
        "leftArm": rot(cos(32, 20.0)),
        "body": rot(0, sin(32.0, 5.0))}},
    # Running: bent low, arms thrown back.
    "animation.hl.goatman.run": {"loop": True, "bones": {
        "torso": rot(20.0), "neck": rot(-20.0),
        "rightArm": rot(f"50.0 + {cos(40, 25.0)}"), "leftArm": rot(f"50.0 - {cos(40, 25.0)}")}},
    # The scream: head thrown back, jaw wide.
    "animation.hl.goatman.scream": {"loop": True, "bones": {
        "head": rot(f"-35.0 + math.sin({T} * 1500.0) * 3.0", 0, f"math.cos({T} * 1300.0) * 4.0"),
        "jaw": rot(35.0), "torso": rot(-10.0),
        "rightArm": rot(0, 0, 25.0), "leftArm": rot(0, 0, -25.0)}},
    "animation.hl.goatman.attack": {"loop": True, "bones": {
        "rightArm": rot(f"-{swing(120.0)}"), "leftArm": rot(f"-{swing(90.0)}"),
        "torso": rot(swing(15.0)), "jaw": rot(swing(30.0))}},

    # ------------------------------------------------------------------ #
    # The Weeping Statue: it never moves while seen, so the poses snap
    # ------------------------------------------------------------------ #
    # Hands lowered from the face, reaching a little.
    "animation.hl.statue.pose1": {"loop": True, "bones": {
        "rightArm": rot(-15.0, 0, -8.0), "leftArm": rot(-15.0, 0, 8.0),
        "rightForearm": rot(80.0, 0, 20.0), "leftForearm": rot(80.0, 0, -20.0),
        "head": rot(-18.0, 0, 10.0)}},
    # Both arms out for you, head back, mouth open.
    "animation.hl.statue.pose2": {"loop": True, "bones": {
        "rightArm": rot(-60.0, 0, 0), "leftArm": rot(-60.0, 0, 0),
        "rightForearm": rot(140.0, 0, 20.0), "leftForearm": rot(140.0, 0, -20.0),
        "rightHand": rot(-20.0), "leftHand": rot(-20.0),
        "head": rot(-40.0), "wingRight": rot(0, 30.0, 0), "wingLeft": rot(0, -30.0, 0)}},

    # ------------------------------------------------------------------ #
    # The Mimic
    # ------------------------------------------------------------------ #
    # Awake: up on its legs, the lid chattering, the tongue wagging.
    "animation.hl.mimic.awake": {"loop": True, "bones": {
        "body": {"position": [0, 11.0, 0], "rotation": [wave(300.0, 2.0), 0, wave(250.0, 2.0)]},
        "lid": rot(f"-25.0 - math.abs(math.sin({T} * 400.0)) * 25.0"),
        "tongue": rot(f"25.0 + {wave(300.0, 8.0)}", wave(200.0, 12.0)),
        "tongueTip": rot(f"20.0 + {wave(330.0, 15.0)}")}},
    "animation.hl.mimic.walk": {"loop": True, "bones": {
        "legFrontRight": rot(0, cos(60, 20.0), lift(60, 15.0)),
        "legMidLeft": rot(0, cos(60, 20.0), lift(60, -15.0)),
        "legBackRight": rot(0, cos(60, 20.0), lift(60, 15.0)),
        "legFrontLeft": rot(0, cos(60, -20.0), lift(60, -15.0, -1)),
        "legMidRight": rot(0, cos(60, -20.0), lift(60, 15.0, -1)),
        "legBackLeft": rot(0, cos(60, -20.0), lift(60, -15.0, -1))}},
    "animation.hl.mimic.attack": {"loop": True, "bones": {
        "lid": rot(f"-{swing(80.0)}"), "body": rot(swing(-15.0))}},
    # Asleep, it breathes. Barely.
    "animation.hl.mimic.asleep": {"loop": True, "bones": {
        "lid": rot(f"-math.max(0.0, math.sin({T} * 12.0) - 0.9) * 40.0")}},

    # ------------------------------------------------------------------ #
    # The Shadow
    # ------------------------------------------------------------------ #
    "animation.hl.shadow.look": look("head", 0.8),
    "animation.hl.shadow.idle": {"loop": True, "bones": {
        "smoke": {"scale": [f"1.0 + {wave(80.0, 0.06)}", 1.0, f"1.0 + {wave(70.0, 0.06, phase=90.0)}"],
                  "rotation": [0, f"{T} * 30.0", 0]},
        "wisps": {"position": [wave(40.0, 1.0), f"math.mod({T} * 3.0, 6.0)", wave(35.0, 1.0)],
                  "rotation": [0, f"{T} * -45.0", 0]},
        "rightForearm": rot(wave(20.0, 3.0)), "leftForearm": rot(wave(18.0, 3.0)),
        "head": rot(0, 0, wave(12.0, 5.0))}},
    # It glides: nothing but the smoke trails.
    "animation.hl.shadow.walk": {"loop": True, "bones": {
        "smoke": rot(f"math.min(25.0, {S} * 60.0)"),
        "body": rot(f"math.min(8.0, {S} * 20.0)")}},

    # ------------------------------------------------------------------ #
    # The Skinwalker
    # ------------------------------------------------------------------ #
    "animation.hl.skinwalker.cow_walk": {"loop": True, "bones": {
        "cowLegFR": rot(cos(38.17, 40.0)), "cowLegBL": rot(cos(38.17, 40.0)),
        "cowLegFL": rot(cos(38.17, -40.0)), "cowLegBR": rot(cos(38.17, -40.0))}},
    # The cow's head always turns to you. All the way.
    "animation.hl.skinwalker.cow_stare": {"loop": True, "bones": {
        "cowHead": rot("query.target_x_rotation", "query.target_y_rotation * 1.4")}},
    "animation.hl.skinwalker.idle": {"loop": True, "bones": {
        "torso": {"scale": breathe(35.0, 0.03)},
        "skull": rot(jolt(0.94, -15.0, 15.0), jolt(0.95, -30.0, 30.0), f"{wave(15.0, 12.0)}"),
        "jaw": rot(f"math.max(0.0, math.sin({T} * 45.0)) * 10.0"),
        "hide": rot(wave(30.0, 3.0), 0, wave(22.0, 2.0))}},
    "animation.hl.skinwalker.walk": {"loop": True, "bones": {
        "rightLeg": rot(cos(30, 36.0)), "leftLeg": rot(cos(30, -36.0)),
        "rightShin": rot(lift(30, 30.0)), "leftShin": rot(lift(30, 30.0, -1)),
        "rightArm": rot(cos(30, -26.0)), "leftArm": rot(cos(30, 26.0)),
        "hide": rot(f"math.abs(math.sin({D} * 30.0)) * 14.0 * {S}")}},
    # Revealed and running: nearly on all fours.
    "animation.hl.skinwalker.run": {"loop": True, "bones": {
        "torso": rot(30.0), "skull": rot(-30.0),
        "rightArm": rot(f"-30.0 + {cos(40, 50.0)}"), "leftArm": rot(f"-30.0 - {cos(40, 50.0)}"),
        "hide": rot(35.0)}},
    "animation.hl.skinwalker.look": look("skull", 0.8),
    "animation.hl.skinwalker.attack": {"loop": True, "bones": {
        "rightArm": rot(f"-{swing(120.0)}"), "leftArm": rot(f"-{swing(100.0)}"), "jaw": rot(swing(35.0))}},

    # ------------------------------------------------------------------ #
    # The Wendigo
    # ------------------------------------------------------------------ #
    "animation.hl.wendigo.look": look("skull", 0.7),
    "animation.hl.wendigo.idle": {"loop": True, "bones": {
        "torso": {"scale": breathe(40.0, 0.03)},
        "skull": rot(0, 0, f"{wave(12.0, 10.0)} + {jolt(0.96, -25.0, 25.0)}"),
        "jaw": rot(f"math.max(0.0, math.sin({T} * 200.0)) * 10.0"),  # clicking
        "rightForearm": rot(wave(20.0, 5.0)), "leftForearm": rot(wave(17.0, 5.0))}},
    "animation.hl.wendigo.walk": {"loop": True, "bones": {
        "rightLeg": rot(cos(26, 30.0)), "leftLeg": rot(cos(26, -30.0)),
        "rightShin": rot(lift(26, -26.0)), "leftShin": rot(lift(26, -26.0, -1)),
        "rightArm": rot(cos(26, -20.0)), "leftArm": rot(cos(26, 20.0)),
        "hips": rot(0, sin(26.0, 5.0))}},
    # The charge: bent over, arms reaching, on the edge of falling forward.
    "animation.hl.wendigo.charge": {"loop": True, "bones": {
        "torso": rot(18.0), "neck": rot(-10.0),
        "rightArm": rot(f"-50.0 + {cos(34, 30.0)}"), "leftArm": rot(f"-50.0 - {cos(34, 30.0)}"),
        "jaw": rot(f"30.0 + math.sin({T} * 1200.0) * 6.0")}},
    "animation.hl.wendigo.attack": {"loop": True, "bones": {
        "rightArm": rot(f"-{swing(120.0)}"), "leftArm": rot(f"-math.sin(math.clamp({A} * 1.3, 0.0, 1.0) * 180.0) * 110.0"),
        "torso": rot(swing(15.0)), "jaw": rot(swing(35.0))}},

    # ------------------------------------------------------------------ #
    # Entity 303
    # ------------------------------------------------------------------ #
    "animation.hl.e303.look": look(),
    # He floats: rising and sinking, the rags stirring, the code circling.
    "animation.hl.e303.float": {"loop": True, "bones": {
        "root": {"position": [0, f"4.0 + {wave(60.0, 1.2)}", 0]},
        "tatters": rot(wave(80.0, 8.0), 0, wave(65.0, 5.0)),
        "rightArm": rot(wave(40.0, 4.0), 0, 6.0), "leftArm": rot(wave(40.0, -4.0), 0, -6.0),
        "ring": {"rotation": [wave(30.0, 8.0), f"{T} * 60.0", 0],
                 "scale": [f"1.0 + {wave(50.0, 0.08)}", 1.0, f"1.0 + {wave(50.0, 0.08)}"]}}},
    # Hands up: something is being done to your world.
    "animation.hl.e303.cast": {"loop": True, "bones": {
        "rightArm": rot(-160.0, 0, wave(500.0, 6.0, 15.0)),
        "leftArm": rot(-160.0, 0, f"-15.0 - math.sin({T} * 500.0) * 6.0"),
        "head": rot(-15.0),
        "ring": {"rotation": [0, f"{T} * 400.0", 0], "scale": [1.4, 1.4, 1.4]}}},

    # ------------------------------------------------------------------ #
    # The Drowned One
    # ------------------------------------------------------------------ #
    "animation.hl.drowned.idle": {"loop": True, "bones": {
        "hairFront": rot(wave(25.0, 3.0), 0, wave(18.0, 2.0)),
        "hairBack": rot(wave(22.0, -3.0)),
        "rightKelp": rot(wave(40.0, 12.0)), "leftKelp": rot(wave(36.0, 12.0)),
        "head": rot(0, 0, jolt(0.96, -12.0, 12.0)),
        "rightArm": rot(wave(20.0, 4.0)), "leftArm": rot(wave(23.0, -4.0))}},
    # A shamble: the legs drag, the whole body rocks.
    "animation.hl.drowned.walk": {"loop": True, "bones": {
        "rightLeg": rot(cos(24, 26.0)), "leftLeg": rot(cos(24, -26.0)),
        "body": rot(0, sin(24.0, 6.0), sin(24.0, 6.0)),
        "hairFront": rot(f"math.abs(math.sin({D} * 24.0)) * 10.0 * {S}")}},
    # In the water it swims face down, hair streaming, legs trailing.
    "animation.hl.drowned.swim": {"loop": True, "bones": {
        "root": {"rotation": [75.0, 0, 0], "position": [0, 4.0, 0]},
        "rightLeg": rot(f"math.sin({T} * 300.0) * 20.0"), "leftLeg": rot(f"-math.sin({T} * 300.0) * 20.0"),
        "rightArm": rot(-110.0), "leftArm": rot(-110.0),
        "hairFront": rot(-60.0), "hairBack": rot(-40.0)}},
    "animation.hl.drowned.attack": {"loop": True, "bones": {
        "rightArm": rot(f"-{swing(60.0)}"), "leftArm": rot(f"-{swing(50.0)}"), "body": rot(swing(12.0))}},
}

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

    "animation.hl.player.swing": {"loop": True, "bones": {
        "rightArm": rot(f"-65.0 + math.sin({T} * 1400.0) * 38.0"),
        "body": rot(0, f"math.sin({T} * 1400.0) * 6.0"),
        "head": rot(12.0)}},

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

    "animation.hl.herobrine.tail": {"loop": True, "bones": {
        **tail(["tail1", "tail2", "tail3", "tail4"], 45.0, 5.0, 35.0, 6.0),
        "sting": rot(f"{wave(45.0, 8.0, phase=-140.0)} + {jolt(0.93, -20.0, 10.0)}", 0, 0)}},
    "animation.hl.herobrine.twitch": {"loop": True, "bones": {
        "head": rot(jolt(0.94, -16.0, 16.0), jolt(0.95, -28.0, 28.0), jolt(0.9, -22.0, 22.0)),
        "chest": rot(0, 0, jolt(0.97, -8.0, 8.0)),
        "rightArm": rot(jolt(0.97, -12.0, 12.0)),
        "leftForearm": rot(jolt(0.96, -18.0, 6.0))}},

    "animation.hl.herobrine.walk": {"loop": True, "bones": {
        "rightThigh": rot(cos(26, 30.0)),
        "rightShin": rot(lift(26, 36.0)),
        "leftThigh": rot(cos(26, -18.0)),
        "leftShin": rot(lift(26, 10.0, -1)),
        "body": rot(0, 0, sin(26.0, 5.0)),
        "leftArm": rot(cos(26, 16.0)),
        "rightArm": rot(cos(26, -8.0))}},

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

    "animation.hl.boss.attack": {"loop": True, "bones": {
        "rightArm": rot(f"-{swing(150.0)}", 0, swing(10.0)),
        "rightForearm": rot(f"-{swing(30.0)}"),
        "chest": rot(swing(12.0), f"-{swing(15.0)}"),
        "jaw": rot(swing(20.0)),
        "sting": rot(swing(50.0)), "tail3": rot(swing(20.0))}},

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
    # Knuckle-walking: the arms swing, the legs kick, the whole spine rolls.
    "animation.hl.dweller.walk": {"loop": True, "bones": {
        "armRight": rot(cos(34, -38.0)), "armLeft": rot(cos(34, 38.0)),
        "foreRight": rot(lift(34, -22.0)), "foreLeft": rot(lift(34, -22.0, -1)),
        "thighRight": rot(cos(34, 34.0)), "thighLeft": rot(cos(34, -34.0)),
        "shinRight": rot(lift(34, -30.0, -1)), "shinLeft": rot(lift(34, -30.0)),
        "ribcage": rot(0, sin(34.0, 6.0), sin(68.0, 2.0)),
        "pelvis": rot(0, sin(34.0, -8.0))}},
    "animation.hl.dweller.idle": {"loop": True, "bones": {
        "ribcage": {"scale": breathe(120.0, 0.04)},
        "handRight": rot(0, 0, jolt(0.95, -10.0, 10.0)),
        "handLeft": rot(0, 0, jolt(0.95, -10.0, 10.0))}},
    "animation.hl.dweller.look": {"loop": True, "bones": {
        "neck": rot("query.target_x_rotation * 0.4", "query.target_y_rotation * 0.4"),
        "head": rot("query.target_x_rotation * 0.4", "query.target_y_rotation * 0.5")}},
    "animation.hl.dweller.twitch": {"loop": True, "bones": {
        "head": rot(0, 0, jolt(0.9, -35.0, 35.0))}},

    "animation.hl.dweller.lips_idle": {"loop": True, "bones": {
        "lipTop": rot(f"-math.max(0.0, math.sin({T} * 60.0)) * 8.0"),
        "lipBottom": rot(f"math.max(0.0, math.sin({T} * 60.0)) * 8.0"),
        "lipRight": rot(0, f"-math.max(0.0, math.sin({T} * 60.0)) * 8.0"),
        "lipLeft": rot(0, f"math.max(0.0, math.sin({T} * 60.0)) * 8.0")}},

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
    # The Nameless Villager
    # ------------------------------------------------------------------ #
    "animation.hl.villager.look": {"loop": True, "bones": {
        "head": rot("query.target_x_rotation * 0.4", f"query.target_y_rotation * 0.5")}},
    # Standing, swaying very slightly, the head always a little too far over.
    "animation.hl.villager.idle": {"loop": True, "bones": {
        "body": {"scale": breathe(60.0, 0.015)},
        "head": rot(0, 0, f"{wave(8.0, 5.0)} + {jolt(0.985, -20.0, 20.0)}"),
        "rightHand": rot(wave(25.0, 4.0)), "leftHand": rot(wave(22.0, 4.0))}},
    # It has seen you looking: the right arm comes up and points.
    "animation.hl.villager.point": {"loop": True, "bones": {
        "rightArm": rot(-88.0, 0, -6.0), "rightHand": rot(0), "head": rot(-8.0, 0, -22.0),
        "body": rot(f"-6.0 + {wave(30.0, 1.5)}")}},
    "animation.hl.villager.walk": {"loop": True, "bones": {
        "rightLeg": rot(cos(26, 24.0)), "leftLeg": rot(cos(26, -24.0)),
        "rightArm": rot(cos(26, -10.0)), "leftArm": rot(cos(26, 10.0)),
        "body": rot(0, sin(26.0, 4.0))}},

    # ------------------------------------------------------------------ #
    # The Faceless Enderman
    # ------------------------------------------------------------------ #
    "animation.hl.ender.look": {"loop": True, "bones": {
        "head": rot("query.target_x_rotation * 0.6", "query.target_y_rotation * 0.7")}},
    # Standing too still, then a twitch; the motes circling.
    "animation.hl.ender.idle": {"loop": True, "bones": {
        "body": {"scale": breathe(70.0, 0.012)},
        "head": rot(0, 0, f"{wave(6.0, 7.0)} + {jolt(0.97, -25.0, 25.0)}"),
        "rightHand": rot(f"{wave(35.0, 8.0)}"), "leftHand": rot(f"{wave(30.0, -8.0)}"),
        "motes": {"rotation": [0, f"{T} * 40.0", 0], "position": [0, wave(50.0, 1.5), 0]}}},
    # It is looking at you: the head turns all the way and the arms lift.
    "animation.hl.ender.stare": {"loop": True, "bones": {
        "head": rot(-12.0, 0, f"-18.0 + math.sin({T} * 900.0) * 2.0"),
        "rightArm": rot(-18.0, 0, -14.0), "leftArm": rot(-18.0, 0, 14.0)}},
    # Blinking into place: it squashes thin and tall and snaps back.
    "animation.hl.ender.blink": {"loop": True, "bones": {
        "root": {"scale": [f"1.0 - {EMERGE} * 0.85", f"1.0 + {EMERGE} * 0.5", f"1.0 - {EMERGE} * 0.85"]}}},

    # ------------------------------------------------------------------ #
    # The False Tree
    # ------------------------------------------------------------------ #
    "animation.hl.tree.look": {"loop": True, "bones": {
        "head": rot("query.target_x_rotation * 0.3", "query.target_y_rotation * 0.4")}},
    # Standing, creaking: the plate sways on its neck, the fingers curl.
    "animation.hl.tree.idle": {"loop": True, "bones": {
        "chest": {"scale": breathe(80.0, 0.01)},
        "head": rot(wave(7.0, 3.0), wave(5.0, 5.0), f"{wave(9.0, 4.0)} + {jolt(0.985, -14.0, 14.0)}"),
        "rightHand": rot(wave(30.0, 8.0)), "leftHand": rot(wave(26.0, -8.0)),
        "rightForearm": rot(wave(14.0, 4.0)), "leftForearm": rot(wave(12.0, -4.0))}},
    # Waving: the arm it already holds up lifts higher, the twiggy hand
    # waggling at the end of it, the way it does in the photo.
    "animation.hl.tree.wave": {"loop": True, "bones": {
        "leftArm": rot(0, 0, -12.0), "leftForearm": rot(0, 0, f"math.sin({T} * 420.0) * 24.0"),
        "leftHand": rot(0, 0, f"math.sin({T} * 420.0 + 60.0) * 22.0"),
        "head": rot(-6.0, 0, f"-14.0 + math.sin({T} * 60.0) * 3.0")}},
    "animation.hl.tree.walk": {"loop": True, "bones": {
        "rightLeg": rot(cos(22, 18.0)), "leftLeg": rot(cos(22, -18.0)),
        "rightShin": rot(lift(22, -16.0, -1)), "leftShin": rot(lift(22, -16.0)),
        "rightArm": rot(cos(22, -10.0)), "leftArm": rot(cos(22, 10.0))}},

    # ------------------------------------------------------------------ #
    # Entity 303
    # ------------------------------------------------------------------ #
    "animation.hl.e303.look": look(),

    "animation.hl.e303.float": {"loop": True, "bones": {
        "root": {"position": [0, f"4.0 + {wave(60.0, 1.2)}", 0]},
        "tatters": rot(wave(80.0, 8.0), 0, wave(65.0, 5.0)),
        "rightArm": rot(wave(40.0, 4.0), 0, 6.0), "leftArm": rot(wave(40.0, -4.0), 0, -6.0),
        "ring": {"rotation": [wave(30.0, 8.0), f"{T} * 60.0", 0],
                 "scale": [f"1.0 + {wave(50.0, 0.08)}", 1.0, f"1.0 + {wave(50.0, 0.08)}"]}}},

    "animation.hl.e303.cast": {"loop": True, "bones": {
        "rightArm": rot(-160.0, 0, wave(500.0, 6.0, 15.0)),
        "leftArm": rot(-160.0, 0, f"-15.0 - math.sin({T} * 500.0) * 6.0"),
        "head": rot(-15.0),
        "ring": {"rotation": [0, f"{T} * 400.0", 0], "scale": [1.4, 1.4, 1.4]}}},

    # ------------------------------------------------------------------ #
    # Remastered: appearing, recoiling, and more life in every step
    # ------------------------------------------------------------------ #
    "animation.hl.dweller.emerge": {"loop": True, "bones": emerge(12, tilt=-40.0)},
    "animation.hl.boss.emerge": {"loop": True, "bones": emerge(70)},
    "animation.hl.null.materialize": {"loop": True, "bones": {
        "root": {"scale": [f"1.0 + {EMERGE} * math.random(-0.8, 0.8)", f"1.0 - {EMERGE} * 0.9", 1.0]}}},
    "animation.hl.boss.hurt": {"loop": True, "bones": flinch(["chest", "head"], 0.9)},

    # Secondary motion layered on the walks: heads that bob, tails and hair
    # that swing, jaws that work.
    "animation.hl.herobrine.walk_extra": {"loop": True, "bones": {
        "head": rot(f"math.abs(math.sin({D} * 26.0)) * 8.0 * {S}", 0, sin(13.0, 6.0)),
        "jaw": rot(f"math.abs(math.sin({D} * 26.0)) * 10.0 * {S}"),
        "tail2": rot(0, 0, sin(13.0, 8.0)), "tail3": rot(0, 0, sin(13.0, 10.0)), "sting": rot(cos(26, 12.0)),
        "gutHang": rot(cos(26, 25.0))}},
    "animation.hl.fog_man.walk_extra": {"loop": True, "bones": {
        "head": rot(0, sin(14.0, 10.0), sin(28.0, 4.0)), "hood": rot(f"math.abs(math.sin({D} * 28.0)) * 6.0 * {S}"),
        "rightHand": rot(cos(28, 15.0)), "leftHand": rot(cos(28, -15.0))}},
    "animation.hl.dweller.walk_extra": {"loop": True, "bones": {
        "head": rot(sin(80.0, 6.0), sin(40.0, 12.0)), "neck": rot(0, sin(40.0, -8.0)),
        "handRight": rot(lift(34, 24.0)), "handLeft": rot(lift(34, 24.0, -1))}},
    "animation.hl.boss.walk_extra": {"loop": True, "bones": {
        "tail1": rot(0, 0, sin(11.0, 6.0)), "tail2": rot(0, 0, sin(11.0, 8.0)), "sting": rot(cos(22, 15.0)),
        "pick": rot(cos(22, 10.0)), "fist": rot(cos(22, -10.0)), "jaw": rot(f"math.abs(math.sin({D} * 22.0)) * 8.0 * {S}")}},
    "animation.hl.player.walk_extra": {"loop": True, "bones": {
        "head": rot(f"math.abs(math.sin({D} * 38.17)) * 2.0 * {S}"), "body": rot(sin(76.34, 1.5))}},
}

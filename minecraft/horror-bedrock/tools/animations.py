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

def gait(freq, leg, knee, arm, elbow=0.0, bob=0.0, sway=0.0, body="body"):
    """A walk cycle for a two-legged body with knees and elbows: the legs
    swing, the shin bends as the foot lifts, the arms swing opposite and the
    forearm lags behind, the body bobs twice a stride and sways."""
    bones = {
        "rightLeg": rot(cos(freq, leg)), "leftLeg": rot(cos(freq, -leg)),
        "rightShin": rot(lift(freq, knee)), "leftShin": rot(lift(freq, knee, -1)),
        "rightArm": rot(cos(freq, -arm)), "leftArm": rot(cos(freq, arm)),
    }
    if elbow:
        bones["rightForearm"] = rot(f"-{elbow} * {S} + {cos(freq, -elbow * 0.5)}")
        bones["leftForearm"] = rot(f"-{elbow} * {S} + {cos(freq, elbow * 0.5)}")
    if bob or sway:
        bones[body] = {"position": [0, f"math.abs(math.cos({D} * {freq})) * {bob} * {S}", 0],
                       "rotation": [0, sin(freq, sway), sin(freq, sway * 0.5)]}
    return {"loop": True, "bones": bones}


def merge(*anims):
    """Several bone dicts into one looping animation (later ones win)."""
    bones = {}
    for a in anims:
        for k, v in (a["bones"] if "bones" in a else a).items():
            bones.setdefault(k, {}).update(v)
    return {"loop": True, "bones": bones}


ANIMATIONS = {
    # ------------------------------------------------------------------ #
    # Green Steve: moves exactly like a player, which is the worst part
    # ------------------------------------------------------------------ #
    "animation.hb.steve.look": look("head", 1.0),
    "animation.hb.steve.idle": {"loop": True, "bones": {
        "body": {"scale": breathe(110.0, 0.012)},
        "rightArm": rot(wave(60.0, 2.5), 0, wave(45.0, 2.0, 2.0)),
        "leftArm": rot(wave(60.0, -2.5), 0, wave(45.0, -2.0, -2.0))}},
    "animation.hb.steve.walk": gait(38, 34.0, 18.0, 30.0, elbow=10.0, bob=0.6),
    "animation.hb.steve.attack": {"loop": True, "bones": {
        "rightArm": rot(f"-{swing(95.0)}", 0, f"{swing(-12.0)}"), "body": rot(0, swing(14.0), 0)}},

    # ------------------------------------------------------------------ #
    # The Mimicer
    # ------------------------------------------------------------------ #
    "animation.hb.mimicer.look": look("head", 0.8),
    "animation.hb.mimicer.idle": {"loop": True, "bones": {
        "body": rot(f"-6.0 + {wave(30.0, 2.0)}", 0, wave(22.0, 2.0)),
        "head": rot(0, 0, f"{wave(17.0, 6.0)} + {jolt(0.985, -25.0, 25.0)}"),
        "jaw": rot(f"6.0 + {wave(90.0, 4.0)}"),
        "rightArm": rot(wave(25.0, 4.0)), "leftArm": rot(wave(21.0, -4.0)),
        "rightForearm": rot(wave(31.0, 6.0)), "leftForearm": rot(wave(27.0, -6.0))}},
    "animation.hb.mimicer.walk": gait(22, 26.0, 26.0, 18.0, elbow=6.0, bob=1.0, sway=4.0),
    # Running: bent forward, arms thrown back, jaw hanging open.
    "animation.hb.mimicer.run": merge(gait(26, 48.0, 52.0, 10.0, bob=1.6), {
        "body": rot(22.0), "head": rot(-18.0),
        "rightArm": rot(f"45.0 + {cos(26, 12.0)}", 0, 10.0), "leftArm": rot(f"45.0 + {cos(26, -12.0)}", 0, -10.0),
        "jaw": rot(f"28.0 + math.sin({T} * 900.0) * 6.0")}),
    # Whispering in your voice: the jaw works too fast.
    "animation.hb.mimicer.talk": {"loop": True, "bones": {
        "jaw": rot(f"10.0 + math.abs(math.sin({T} * 720.0)) * 26.0"), "head": rot(0, 0, wave(300.0, 4.0))}},
    "animation.hb.mimicer.attack": {"loop": True, "bones": {
        "rightArm": rot(f"-{swing(120.0)}"), "leftArm": rot(f"-{swing(100.0)}"), "jaw": rot(swing(35.0))}},

    # ------------------------------------------------------------------ #
    # The Knocker
    # ------------------------------------------------------------------ #
    "animation.hb.knocker.look": look("head", 0.7),
    "animation.hb.knocker.idle": {"loop": True, "bones": {
        "body": {"scale": breathe(160.0, 0.025)},
        "head": rot(0, 0, f"{wave(13.0, 5.0)} + {jolt(0.99, -12.0, 12.0)}"),
        # Fingers drumming, always, as if on a door.
        "rightHand": rot(f"math.abs(math.sin({T} * 520.0)) * 14.0"), "leftHand": rot(wave(40.0, 6.0))}},
    # A limping, hunched walk: one leg drags.
    "animation.hb.knocker.walk": merge(gait(24, 26.0, 30.0, 14.0, elbow=10.0, bob=1.0, sway=6.0), {
        "leftLeg": rot(cos(24, -14.0)), "head": rot(sin(48.0, 4.0))}),
    "animation.hb.knocker.run": merge(gait(32, 44.0, 50.0, 0.0, bob=1.4), {
        "body": rot(18.0), "rightArm": rot(f"-70.0 + {cos(32, 20.0)}"), "leftArm": rot(f"-70.0 + {cos(32, -20.0)}")}),
    # Knocking: the right hand comes up and raps, again and again.
    "animation.hb.knocker.knock": {"loop": True, "bones": {
        "rightArm": rot(-70.0, 0, 10.0),
        # (raps for two seconds, then a pause, then again)
        "rightForearm": rot(f"-40.0 + math.abs(math.sin({T} * 1080.0)) * 30.0 * math.max(0.0, math.sin({T} * 90.0))")}},
    "animation.hb.knocker.attack": {"loop": True, "bones": {
        "rightArm": rot(f"-{swing(120.0)}"), "leftArm": rot(f"-{swing(110.0)}"), "head": rot(swing(30.0))}},

    # ------------------------------------------------------------------ #
    # The Lick
    # ------------------------------------------------------------------ #
    "animation.hb.lick.look": look("head", 1.0),
    # Perfectly still... except the tongue.
    "animation.hb.lick.idle": {"loop": True, "bones": {
        "tongue": rot(wave(40.0, 6.0), 0, wave(31.0, 8.0)),
        "head": rot(0, 0, jolt(0.995, -30.0, 30.0))}},
    "animation.hb.lick.run": merge(gait(30, 40.0, 40.0, 40.0, elbow=20.0, bob=1.0), {
        "body": rot(10.0), "tongue": rot(f"25.0 + {wave(500.0, 15.0)}")}),
    "animation.hb.lick.attack": {"loop": True, "bones": {
        "rightArm": rot(f"-{swing(110.0)}"), "leftArm": rot(f"-{swing(110.0)}"), "head": rot(swing(-20.0))}},

    # ------------------------------------------------------------------ #
    # The Boiled One
    # ------------------------------------------------------------------ #
    "animation.hb.boiled.idle": {"loop": True, "bones": {
        "body": rot(wave(12.0, 1.5), 0, wave(9.0, 2.0, 2.0)),
        "head": rot(0, 0, f"{wave(7.0, 4.0)} + {jolt(0.993, -15.0, 15.0)}"),
        "rightArm": rot(wave(15.0, 2.0)), "leftArm": rot(wave(13.0, -2.0))}},
    # It has noticed you: the head turns, slowly, all the way.
    "animation.hb.boiled.stare": {"loop": True, "bones": {
        "head": rot("query.target_x_rotation * 0.6", "query.target_y_rotation", -10.0),
        "neck": rot(-8.0)}},

    # ------------------------------------------------------------------ #
    # Slenderman
    # ------------------------------------------------------------------ #
    "animation.hb.slender.look": look("head", 1.0),
    "animation.hb.slender.idle": {"loop": True, "bones": {
        "body": rot(0, 0, wave(8.0, 1.0)),
        "rightForearm": rot(wave(11.0, 3.0)), "leftForearm": rot(wave(10.0, -3.0))}},
    # The tendrils, curling and uncurling behind him.
    "animation.hb.slender.tendrils": {"loop": True, "bones": {
        f"tendril{n}_{k}": rot(f"{-10.0 - k * 6.0} + math.sin({T} * {60.0 + n * 7.0} - {k * 45.0 + n * 30.0}) * {10.0 + k * 6.0}",
                              0, f"math.cos({T} * {44.0 + n * 5.0} - {k * 40.0}) * {8.0 + k * 5.0}")
        for n in range(4) for k in range(4)}},
    # Folded away (scaled down into his back) when he isn't reaching.
    "animation.hb.slender.fold": {"loop": True, "bones": {f"tendril{n}_0": {"scale": 0.0} for n in range(4)}},

    # ------------------------------------------------------------------ #
    # Smile Dog
    # ------------------------------------------------------------------ #
    "animation.hb.dog.look": look("head", 0.9),
    # Sitting, smiling, the tail sweeping the floor.
    "animation.hb.dog.sit": {"loop": True, "bones": {
        "body": {"rotation": [-28.0, 0, 0], "position": [0, -2.0, 0]},
        "rightBackLeg": {"rotation": [-62.0, 0, 0], "position": [0, -2.0, -2.0]},
        "leftBackLeg": {"rotation": [-62.0, 0, 0], "position": [0, -2.0, -2.0]},
        "rightFrontLeg": rot(0.0), "leftFrontLeg": rot(0.0),
        "head": rot(f"20.0 + {wave(20.0, 3.0)}", 0, f"{wave(14.0, 8.0)}"),
        "tail": rot(40.0, wave(160.0, 25.0), 0)}},
    "animation.hb.dog.run": {"loop": True, "bones": {
        "rightFrontLeg": rot(cos(40, 50.0)), "leftFrontLeg": rot(cos(40, 50.0, " + 30.0")),
        "rightBackLeg": rot(cos(40, -50.0)), "leftBackLeg": rot(cos(40, -50.0, " + 30.0")),
        "body": {"position": [0, f"math.abs(math.sin({D} * 40.0)) * 1.2 * {S}", 0], "rotation": [sin(40.0, 4.0), 0, 0]},
        "head": rot(sin(40.0, -6.0)), "tail": rot(-10.0, wave(300.0, 20.0), 0)}},
    "animation.hb.dog.attack": {"loop": True, "bones": {
        "head": rot(f"-{swing(30.0)}"), "body": rot(swing(-12.0))}},

    # ------------------------------------------------------------------ #
    # Jeff
    # ------------------------------------------------------------------ #
    "animation.hb.jeff.look": look("head", 1.0),
    "animation.hb.jeff.idle": {"loop": True, "bones": {
        "body": {"scale": breathe(90.0, 0.012)},
        "head": rot(0, 0, f"{wave(10.0, 6.0, 8.0)} + {jolt(0.99, -10.0, 10.0)}"),
        "hair": rot(wave(22.0, 2.0), 0, wave(17.0, 2.0)),
        "rightArm": rot(wave(30.0, 3.0)), "rightForearm": rot(f"-15.0 + {wave(70.0, 4.0)}"), "knife": rot(wave(35.0, 6.0))}},
    "animation.hb.jeff.walk": merge(gait(36, 30.0, 18.0, 24.0, elbow=10.0, bob=0.5), {"hair": rot(sin(72.0, 6.0))}),
    # Running with the knife up.
    "animation.hb.jeff.run": merge(gait(42, 50.0, 46.0, 40.0, bob=1.0), {
        "body": rot(12.0), "rightArm": rot(-110.0, 0, 6.0), "rightForearm": rot(-20.0),
        "hair": rot(f"20.0 + {sin(84.0, 8.0)}")}),
    "animation.hb.jeff.attack": {"loop": True, "bones": {
        "rightArm": rot(f"-150.0 + {swing(140.0)}"), "body": rot(swing(18.0), swing(-15.0), 0)}},
}


# Client animation controllers: idle and movement blend into each other
# instead of snapping (blend_transition, in seconds). `run` (if any) plays
# instead of `walk` while `fast` is true.
def flow(name, idle, walk, run=None, fast=None, blend=0.3):
    moving = "query.modified_move_speed > 0.05"
    move_anims = [walk] if not run else [{walk: f"!({fast})"}, {run: fast}]
    return f"controller.animation.hb.{name}.flow", {
        "initial_state": "still",
        "states": {
            "still": {"animations": [idle], "transitions": [{"moving": moving}], "blend_transition": blend},
            "moving": {"animations": move_anims, "transitions": [{"still": f"!({moving})"}], "blend_transition": blend},
        }}

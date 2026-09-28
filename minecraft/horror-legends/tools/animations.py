"""Entity animations for Horror Legends, written out by build.py.

Every animation is procedural Molang, layered in the client entity files:
a base cycle (walk / idle), secondary motion (hair, coat, tails, guts), and
state overlays (sneak, sprint, attack, hurt). Rotations follow Bedrock's
conventions: for a limb hanging down, negative X swings it forwards.
"""

D = "query.modified_distance_moved"
S = "query.modified_move_speed"
T = "query.life_time"
A = "variable.attack_time"
H = "query.hurt_time"


def cos(freq, amp, phase=""):
    return f"math.cos({D} * {freq}{phase}) * {amp} * {S}"


def lift(freq, amp, sign=1):
    """Only the positive half of a sine: a knee that bends, a foot that lifts."""
    s = "" if sign > 0 else "-"
    return f"math.max(0.0, {s}math.sin({D} * {freq})) * {amp} * {S}"


def jolt(p, lo, hi):
    """Usually nothing; now and then a random jerk."""
    return f"math.random(0.0, 1.0) > {p} ? math.random({lo}, {hi}) : 0.0"


def breathe(rate, depth):
    return [1.0, f"1.0 + math.sin({T} * {rate}) * {depth}", 1.0]


ANIMATIONS = {
    "animation.hl.look_at_target": {"loop": True, "bones": {
        "head": {"rotation": ["query.target_x_rotation", "query.target_y_rotation", 0]}}},

    # ------------------------------------------------------------------ #
    # HerobrineGamer788: moves like any other player
    # ------------------------------------------------------------------ #
    "animation.hl.player.walk": {"loop": True, "bones": {
        "rightLeg": {"rotation": [cos(38.17, 50.0), 0, 0]},
        "leftLeg": {"rotation": [cos(38.17, -50.0), 0, 0]},
        "rightArm": {"rotation": [cos(38.17, -40.0), 0, 0]},
        "leftArm": {"rotation": [cos(38.17, 40.0), 0, 0]},
        "body": {"rotation": [0, f"math.sin({D} * 38.17) * 2.0 * {S}", 0]}}},
    "animation.hl.player.idle": {"loop": True, "bones": {
        "rightArm": {"rotation": [f"math.sin({T} * 76.8) * 2.9", 0, f"math.cos({T} * 103.2) * 2.9 + 2.9"]},
        "leftArm": {"rotation": [f"-math.sin({T} * 76.8) * 2.9", 0, f"-math.cos({T} * 103.2) * 2.9 - 2.9"]},
        "body": {"scale": breathe(90.0, 0.012)}}},
    "animation.hl.player.sneak": {"loop": True, "bones": {
        "root": {"position": [0, -1.5, 0]},
        "body": {"rotation": [28.0, 0, 0]},
        "rightLeg": {"position": [0, 0, 4.0]},
        "leftLeg": {"position": [0, 0, 4.0]},
        "rightArm": {"rotation": [-12.0, 0, 0]},
        "leftArm": {"rotation": [-12.0, 0, 0]}}},
    # Chopping at a block: quick arm swings, a small twist of the body.
    "animation.hl.player.swing": {"loop": True, "bones": {
        "rightArm": {"rotation": [f"-65.0 + math.sin({T} * 1400.0) * 38.0", 0, 0]},
        "body": {"rotation": [0, f"math.sin({T} * 1400.0) * 6.0", 0]},
        "head": {"rotation": [12.0, 0, 0]}}},
    # Something is wrong: arms dead at his sides, head cocked.
    "animation.hl.player.stare": {"loop": True, "bones": {
        "rightArm": {"rotation": [f"-math.sin({T} * 76.8) * 2.9", 0, f"-math.cos({T} * 103.2) * 2.9 - 2.9"]},
        "leftArm": {"rotation": [f"math.sin({T} * 76.8) * 2.9", 0, f"math.cos({T} * 103.2) * 2.9 + 2.9"]},
        "head": {"rotation": [6.0, 0, f"12.0 + {jolt(0.97, -10.0, 10.0)}"]}}},
    "animation.hl.player.air": {"loop": True, "bones": {
        "rightLeg": {"rotation": [-12.0, 0, 0]},
        "leftLeg": {"rotation": [8.0, 0, 0]},
        "rightArm": {"rotation": [0, 0, 8.0]},
        "leftArm": {"rotation": [0, 0, -8.0]}}},

    # ------------------------------------------------------------------ #
    # Herobrine: breathing wetly, twitching, jaw working on its own
    # ------------------------------------------------------------------ #
    "animation.hl.herobrine.idle": {"loop": True, "bones": {
        "body": {"rotation": [f"math.sin({T} * 25.0) * 1.2", 0, 0]},
        "chest": {"scale": breathe(25.0, 0.025)},
        "leftUpperArm": {"rotation": [0, 0, f"math.sin({T} * 18.0) * 2.0"]},
        "leftHand": {"rotation": [f"math.sin({T} * 33.0) * 4.0", 0, 0]},
        "jaw": {"rotation": [f"math.sin({T} * 50.0) * 6.0 + {jolt(0.92, 0.0, 12.0)}", 0, f"math.sin({T} * 20.0) * 3.0"]},
        "gutHang": {"rotation": [f"math.sin({T} * 70.0) * 11.0", 0, f"math.cos({T} * 55.0) * 7.0"]},
        "dripL": {"scale": [1.0, f"1.0 + math.sin({T} * 90.0) * 0.28", 1.0]},
        "dripR": {"scale": [1.0, f"1.0 + math.cos({T} * 80.0) * 0.32", 1.0]},
        "pickaxe": {"rotation": [f"math.sin({T} * 20.0) * 2.0", 0, f"math.cos({T} * 15.0) * 1.5"]}}},
    "animation.hl.herobrine.twitch": {"loop": True, "bones": {
        "head": {"rotation": [jolt(0.94, -16.0, 16.0), jolt(0.95, -28.0, 28.0), jolt(0.9, -22.0, 22.0)]},
        "chest": {"rotation": [0, 0, jolt(0.97, -8.0, 8.0)]},
        "rightUpperArm": {"rotation": [jolt(0.97, -12.0, 12.0), 0, 0]},
        "leftForearm": {"rotation": [jolt(0.96, -18.0, 6.0), 0, 0]}}},
    # A dragging limp: the right leg steps, the left one follows stiff.
    "animation.hl.herobrine.walk": {"loop": True, "bones": {
        "rightThigh": {"rotation": [cos(26, 30.0), 0, 0]},
        "rightShin": {"rotation": [lift(26, 36.0), 0, 0]},
        "leftThigh": {"rotation": [cos(26, -18.0), 0, 0]},
        "leftShin": {"rotation": [lift(26, 10.0, -1), 0, 0]},
        "body": {"rotation": [0, 0, f"math.sin({D} * 26.0) * 5.0 * {S}"]},
        "leftUpperArm": {"rotation": [cos(26, 16.0), 0, 0]},
        "rightUpperArm": {"rotation": [cos(26, -8.0), 0, 0]},
        "head": {"rotation": [f"math.abs(math.sin({D} * 26.0)) * 6.0 * {S}", 0, 0]}}},

    # ------------------------------------------------------------------ #
    # null
    # ------------------------------------------------------------------ #
    "animation.hl.null.glitch": {"loop": True, "bones": {
        "root": {"position": [jolt(0.93, -1.5, 1.5), 0, jolt(0.93, -1.5, 1.5)]},
        "torsoMid": {"position": [jolt(0.85, -2.0, 2.0), 0, 0]},
        "torsoHigh": {"position": [jolt(0.88, -2.5, 2.5), 0, jolt(0.95, -1.0, 1.0)]},
        "head": {"rotation": [0, jolt(0.96, -45.0, 45.0), 0],
                 "scale": [1.0, f"1.0 + ({jolt(0.97, -0.2, 0.3)})", 1.0]},
        "leftArm": {"rotation": [jolt(0.95, -20.0, 20.0), 0, 0]},
        "halo": {"position": ["math.random(-0.6, 0.6)", "math.random(-0.6, 0.6)", "math.random(-0.6, 0.6)"],
                 "rotation": [0, f"{T} * 80.0", 0]}}},

    # ------------------------------------------------------------------ #
    # The Man From The Fog
    # ------------------------------------------------------------------ #
    "animation.hl.fog_man.walk": {"loop": True, "bones": {
        "rightLeg": {"rotation": [cos(30, 34.0), 0, 0]},
        "leftLeg": {"rotation": [cos(30, -34.0), 0, 0]},
        "rightShin": {"rotation": [lift(30, 42.0), 0, 0]},
        "leftShin": {"rotation": [lift(30, 42.0, -1), 0, 0]},
        "rightArm": {"rotation": [cos(30, -22.0), 0, 0]},
        "leftArm": {"rotation": [cos(30, 22.0), 0, 0]},
        "rightForearm": {"rotation": [f"-{lift(30, 18.0, -1)}", 0, 0]},
        "leftForearm": {"rotation": [f"-{lift(30, 18.0)}", 0, 0]},
        "body": {"rotation": [0, 0, f"math.sin({D} * 30.0) * 3.0 * {S}"]},
        "neck": {"rotation": [f"math.abs(math.sin({D} * 60.0)) * -4.0 * {S}", 0, 0]},
        "hair": {"rotation": [f"math.abs(math.sin({D} * 30.0)) * 10.0 * {S}", 0, 0]},
        "coatBack": {"rotation": [f"math.abs(math.sin({D} * 30.0)) * 12.0 * {S}", 0, 0]},
        "coatRight": {"rotation": [lift(30, 20.0, -1), 0, 0]},
        "coatLeft": {"rotation": [lift(30, 20.0), 0, 0]}}},
    "animation.hl.fog_man.watch": {"loop": True, "bones": {
        "body": {"rotation": [f"4.0 + math.sin({T} * 30.0) * 1.5", 0, 0]},
        "chest": {"scale": breathe(30.0, 0.03)},
        "head": {"rotation": [0, 0, f"math.sin({T} * 40.0) * 4.0"]},
        "jaw": {"rotation": [f"math.sin({T} * 60.0) * 3.0", 0, 0]},
        "hair": {"rotation": [f"math.sin({T} * 30.0) * 3.0", 0, f"math.cos({T} * 22.0) * 2.0"]},
        "rightHand": {"rotation": [jolt(0.95, -15.0, 15.0), 0, 0]},
        "leftHand": {"rotation": [jolt(0.95, -15.0, 15.0), 0, 0]},
        "coatBack": {"rotation": [f"math.sin({T} * 45.0) * 2.0", 0, 0]}}},
    "animation.hl.fog_man.sprint": {"loop": True, "bones": {
        "body": {"rotation": [28.0, 0, 0]},
        "chest": {"rotation": [8.0, 0, 0]},
        "neck": {"rotation": [-18.0, 0, 0]},
        "head": {"rotation": [-22.0, 0, -12.0]},
        "jaw": {"rotation": [f"26.0 + math.sin({T} * 1100.0) * 6.0", 0, 0]},
        "hair": {"rotation": [f"50.0 + math.sin({T} * 900.0) * 8.0", 0, 0]},
        "rightArm": {"rotation": [f"45.0 + math.cos({D} * 50.0) * 45.0", 0, 0]},
        "leftArm": {"rotation": [f"45.0 - math.cos({D} * 50.0) * 45.0", 0, 0]},
        "rightForearm": {"rotation": [-25.0, 0, 0]},
        "leftForearm": {"rotation": [-25.0, 0, 0]},
        "coatBack": {"rotation": [f"40.0 + math.sin({T} * 900.0) * 6.0", 0, 0]},
        "coatRight": {"rotation": [f"32.0 + math.sin({T} * 800.0) * 8.0", 0, 0]},
        "coatLeft": {"rotation": [f"34.0 + math.cos({T} * 850.0) * 8.0", 0, 0]}}},
    # A lunge: the chest drives forward, both arms come down, the mouth opens.
    "animation.hl.fog_man.attack": {"loop": True, "bones": {
        "chest": {"rotation": [f"math.sin({A} * 180.0) * 14.0", 0, 0]},
        "rightArm": {"rotation": [f"-math.sin({A} * 180.0) * 130.0", 0, 0]},
        "leftArm": {"rotation": [f"-math.sin(math.clamp({A} * 1.4, 0.0, 1.0) * 180.0) * 105.0", 0, 0]},
        "jaw": {"rotation": [f"math.sin({A} * 180.0) * 28.0", 0, 0]}}},
    "animation.hl.fog_man.hurt": {"loop": True, "bones": {
        "chest": {"rotation": [f"-{H} * 1.6", 0, 0]},
        "head": {"rotation": [f"-{H} * 1.4", 0, f"{H} * 1.2"]}}},

    # ------------------------------------------------------------------ #
    # The Cave Dweller
    # ------------------------------------------------------------------ #
    # A skitter: each long limb swings round (y) and lifts (z) in turn, so
    # the hands and feet stay on the ground while they push.
    "animation.hl.dweller.walk": {"loop": True, "bones": {
        "armRight": {"rotation": [0, cos(40, 16.0), lift(40, 14.0)]},
        "armLeft": {"rotation": [0, cos(40, 16.0), f"-{lift(40, 14.0, -1)}"]},
        "thighRight": {"rotation": [0, cos(40, -16.0), lift(40, 12.0, -1)]},
        "thighLeft": {"rotation": [0, cos(40, -16.0), f"-{lift(40, 12.0)}"]},
        "body": {"position": [0, f"math.abs(math.sin({D} * 40.0)) * 0.5 * {S}", 0],
                 "rotation": [0, 0, f"math.sin({D} * 40.0) * 3.0 * {S}"]},
        "head": {"rotation": [0, f"math.sin({D} * 40.0) * 6.0 * {S}", 0]}}},
    "animation.hl.dweller.idle": {"loop": True, "bones": {
        "ribcage": {"scale": breathe(120.0, 0.04)}}},
    "animation.hl.dweller.look": {"loop": True, "bones": {
        "neck": {"rotation": ["query.target_x_rotation * 0.4", "query.target_y_rotation * 0.4", 0]},
        "head": {"rotation": ["query.target_x_rotation * 0.4", "query.target_y_rotation * 0.5", 0]}}},
    "animation.hl.dweller.twitch": {"loop": True, "bones": {
        "head": {"rotation": [0, 0, jolt(0.9, -35.0, 35.0)]}}},
    "animation.hl.dweller.jaw_idle": {"loop": True, "bones": {
        "jaw": {"rotation": [f"math.sin({T} * 80.0) * 4.0", 0, 0]}}},
    "animation.hl.dweller.jaw_snap": {"loop": True, "bones": {
        "jaw": {"rotation": [f"26.0 + math.sin({T} * 900.0) * 14.0", 0, 0]},
        "head": {"rotation": [-14.0, 0, 0]}}},
    "animation.hl.dweller.attack": {"loop": True, "bones": {
        "ribcage": {"rotation": [f"math.sin({A} * 180.0) * 12.0", 0, 0]},
        "neck": {"rotation": [f"math.sin({A} * 180.0) * 18.0", 0, 0]},
        "jaw": {"rotation": [f"math.sin({A} * 180.0) * 34.0", 0, 0]},
        "armRight": {"rotation": [f"-math.sin({A} * 180.0) * 50.0", 0, 0]},
        "armLeft": {"rotation": [f"-math.sin(math.clamp({A} * 1.3, 0.0, 1.0) * 180.0) * 45.0", 0, 0]}}},
    "animation.hl.dweller.hurt": {"loop": True, "bones": {
        "ribcage": {"rotation": [f"-{H} * 1.5", 0, 0]},
        "head": {"rotation": [f"-{H} * 1.5", 0, f"{H} * 2.0"]}}},
}

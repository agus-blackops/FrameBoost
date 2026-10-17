"""Molang animations, blended by weight in each client entity's `animate` list.

Shared per-frame variables (set in pre_animation):
  variable.speed  smoothed walk speed 0..1    -> blends walk vs idle
  variable.hunt   smoothed "has a target" 0..1 -> blends the hunting pose
  variable.atk    attack swing progress 0..1 (engine's variable.attack_time)
  variable.still  1 while a Stalker is frozen by a player's gaze
Rotations are degrees; +X tilts a bone's front downward / swings a limb back.
"""

T = "query.life_time"


def pre_animation(frozen_property=False):
    still = "query.property('horror:frozen')" if frozen_property else "0"
    return [
        "variable.dt = math.min(query.delta_time, 0.1);",
        "variable.speed = math.lerp(variable.speed ?? 0, math.min(query.modified_move_speed * 1.4, 1), "
        "math.min(variable.dt * 8, 1));",
        "variable.hunt = math.lerp(variable.hunt ?? 0, query.has_target, math.min(variable.dt * 2.5, 1));",
        "variable.atk = variable.attack_time ?? 0;",
        f"variable.still = {still};",
    ]


def sin(freq, amp, phase=0, offset=0):
    """`offset + sin(life_time * freq + phase) * amp` as Molang."""
    ph = f" + {phase}" if phase else ""
    off = f"{offset} + " if offset else ""
    return f"{off}math.sin({T} * {freq}{ph}) * {amp}"


def rot(x=0, y=0, z=0):
    return {"rotation": [x, y, z]}


def anim(bones):
    return {"loop": True, "bones": bones}


def look_at(neck="neck"):
    """Head tracking, shared with the neck when there is one for a smoother turn."""
    if neck is None:
        return anim({"head": rot("query.target_x_rotation", "query.target_y_rotation")})
    return anim({
        neck: rot("query.target_x_rotation * 0.35", "query.target_y_rotation * 0.4"),
        "head": rot("query.target_x_rotation * 0.65", "query.target_y_rotation * 0.6"),
    })


def twitch(period, bone="head"):
    """A violent jerk for a fraction of a second, every `period` seconds."""
    return anim({bone: rot(
        f"math.mod({T}, {period}) < 0.16 ? math.sin({T} * 2400) * 9 : 0",
        f"math.mod({T} + 1.3, {period * 1.7:.2f}) < 0.2 ? math.sin({T} * 1800) * 14 : 0",
        f"math.mod({T}, {period}) < 0.16 ? math.sin({T} * 2900) * 16 : 0",
    )})


SWING = "math.sin(variable.atk * 180)"


# ---------------------------------------------------------------- creatures
def stalker():
    p = "query.modified_distance_moved * 22"  # long legs: slow cadence
    return {
        "walk": anim({
            "leg_r": rot(f"math.cos({p}) * 34"),
            "shin_r": rot(f"math.max(0, math.sin({p})) * 48"),
            "leg_l": rot(f"-math.cos({p}) * 34"),
            "shin_l": rot(f"math.max(0, -math.sin({p})) * 48"),
            "arm_r": rot(f"-math.cos({p}) * 24"),
            "forearm_r": rot(f"-12 - math.max(0, -math.sin({p})) * 22"),
            "arm_l": rot(f"math.cos({p}) * 24"),
            "forearm_l": rot(f"-12 - math.max(0, math.sin({p})) * 22"),
            "waist": {"position": [0, f"-math.abs(math.cos({p})) * 0.9", 0],
                      "rotation": [0, 0, f"math.cos({p}) * 3"]},
            "chest": rot(0, f"math.cos({p}) * 7"),
            "head": rot(0, f"-math.cos({p}) * 5"),
        }),
        "idle": anim({
            "chest": rot(sin(50, 1.5)),
            "arm_r": rot(sin(33, 3), 0, sin(40, 2, offset=3)),
            "arm_l": rot(sin(33, 3, 90), 0, sin(40, -2, offset=-3)),
            "forearm_r": rot(sin(27, 4, 40, -6)),
            "forearm_l": rot(sin(27, 4, 130, -6)),
            "hand_r": rot(sin(70, 6)),
            "hand_l": rot(sin(70, 6, 60)),
            "head": rot(0, f"{sin(17, 8)} + {sin(41, 3)}"),
            "jaw": rot(sin(60, 1.5, offset=2)),
        }),
        "hunt": anim({
            "chest": rot(10),
            "head": rot(0, 0, 24),
            "jaw": rot(sin(240, 4, offset=22)),
            "arm_r": rot(-38, 0, 6),
            "arm_l": rot(-30, 0, -6),
            "forearm_r": rot(-28),
            "forearm_l": rot(-22),
            "hand_r": rot(0, 0, 12),
            "hand_l": rot(0, 0, -12),
        }),
        "attack": anim({
            "arm_r": rot(f"-{SWING} * 105"),
            "arm_l": rot("-math.sin(math.clamp(variable.atk * 1.25 - 0.25, 0, 1) * 180) * 95"),
            "chest": rot(f"{SWING} * 16"),
            "jaw": rot(f"{SWING} * 38"),
        }),
        "look_at": look_at(),
        "twitch": twitch(4.3),
    }, [
        "look_at",
        {"walk": "variable.speed"},
        {"idle": "(1 - variable.speed) * (1 - variable.still)"},
        {"hunt": "variable.hunt"},
        {"twitch": "1 - variable.still"},
        "attack",
    ]


def watcher():
    return {
        "idle": anim({
            "torso": rot(sin(30, 1.2), 0, sin(9, 2)),
            "neck": rot(0, 0, sin(7, 6, offset=8)),
            "head": rot(0, 0, f"{sin(5, 14, offset=16)}"),     # slow, unnatural head tilt
            "arm_r": rot(sin(21, 2), 0, sin(13, 1.5, offset=2)),
            "arm_l": rot(sin(21, 2, 120), 0, sin(13, -1.5, offset=-2)),
            "hand_r": rot(sin(45, 8)),
            "hand_l": rot(sin(45, 8, 90)),
        }),
        "look_at": look_at(),
        "twitch": twitch(6.7),
    }, ["look_at", "idle", "twitch"]


def woodbeast():
    p = "query.modified_distance_moved * 30"
    return {
        "walk": anim({
            # Diagonal pairs move together: front-right with hind-left.
            "arm_r": rot(f"math.cos({p}) * 32"),
            "forearm_r": rot(f"math.max(0, math.sin({p})) * 40"),
            "leg_l": rot(f"math.cos({p}) * 30"),
            "shin_l": rot(f"-math.max(0, math.sin({p})) * 30"),
            "arm_l": rot(f"-math.cos({p}) * 32"),
            "forearm_l": rot(f"math.max(0, -math.sin({p})) * 40"),
            "leg_r": rot(f"-math.cos({p}) * 30"),
            "shin_r": rot(f"-math.max(0, -math.sin({p})) * 30"),
            "body": {"position": [0, f"-math.abs(math.sin({p})) * 0.8", 0]},
            "chest": rot(f"math.sin({p} * 2) * 2.5"),
            "neck": rot(f"-math.sin({p} * 2) * 4"),
            "tail1": rot(f"math.sin({p} * 2) * 6"),
        }),
        "idle": anim({
            "chest": rot(sin(45, 1.2)),
            "neck": rot(sin(30, 3, offset=4)),
            # Short sniffing bursts.
            "head": rot(f"math.mod({T}, 3.5) < 0.6 ? math.sin({T} * 900) * 3 : 0", sin(13, 10)),
            "jaw": rot(sin(70, 1.5, offset=2)),
        }),
        "tail": anim({
            "tail1": rot(sin(45, 4, offset=-12), sin(90, 14)),
            "tail2": rot(sin(45, 4, 40, 4), sin(90, 14, -50)),
            "tail3": rot(sin(45, 5, 80, 6), sin(90, 18, -100)),
        }),
        "hunt": anim({
            "chest": rot(6),
            "neck": rot(16),
            "head": rot(-6),
            "jaw": rot(sin(260, 4, offset=24)),
            "tail1": rot(-22),
        }),
        "attack": anim({
            "chest": rot(f"-{SWING} * 18"),
            "neck": rot(f"{SWING} * 26"),
            "jaw": rot(f"{SWING} * 42"),
            "arm_r": rot(f"-{SWING} * 65"),
            "forearm_r": rot(f"{SWING} * 30"),
        }),
        "look_at": look_at(),
    }, [
        "look_at",
        {"walk": "variable.speed"},
        {"idle": "1 - variable.speed"},
        "tail",
        {"hunt": "variable.hunt"},
        "attack",
    ]


def whisperer():
    def strip(phase):
        return rot(f"{sin(110, 8, phase)} + variable.speed * 28", 0, sin(70, 4, phase))

    return {
        "float": anim({
            "root": {"position": [0, sin(70, 1.2, offset=2.5), 0]},
            "robe": rot("variable.speed * 14", 0, sin(35, 2)),
        }),
        "cloth": anim({
            "skirt": rot(f"variable.speed * 10 + {sin(55, 2.5)}"),
            "strip1": strip(0),
            "strip2": strip(60),
            "strip3": strip(120),
            "strip4": strip(180),
        }),
        "idle": anim({
            "arm_r": rot(sin(40, 6, offset=-10), 0, sin(25, 3, offset=8)),
            "arm_l": rot(sin(40, 6, 100, -10), 0, sin(25, -3, offset=-8)),
            "forearm_r": rot(sin(32, 6, 30, -20)),
            "forearm_l": rot(sin(32, 6, 150, -20)),
            "hand_r": rot(sin(60, 10)),
            "hand_l": rot(sin(60, 10, 70)),
            "head": rot(0, 0, sin(23, 10)),
            "jaw": rot(sin(50, 3, offset=4)),
        }),
        "hunt": anim({
            "arm_r": rot(-72, 0, 10),
            "arm_l": rot(-66, 0, -10),
            "forearm_r": rot(-8),
            "forearm_l": rot(-12),
            "hand_r": rot(0, 0, 14),
            "hand_l": rot(0, 0, -14),
            "jaw": rot(sin(300, 5, offset=34)),   # a trembling silent scream
            "head": rot(-8, 0, 16),
        }),
        "attack": anim({
            "arm_r": rot(f"-{SWING} * 45"),
            "arm_l": rot(f"-{SWING} * 45"),
            "forearm_r": rot(f"{SWING} * 25"),
            "forearm_l": rot(f"{SWING} * 25"),
            "jaw": rot(f"{SWING} * 30"),
        }),
        "look_at": look_at(neck=None),
        "twitch": twitch(5.1),
    }, [
        "look_at",
        "float",
        "cloth",
        {"idle": "1 - variable.hunt"},
        {"hunt": "variable.hunt"},
        "twitch",
        "attack",
    ]


ALL = {"stalker": stalker, "watcher": watcher, "woodbeast": woodbeast, "whisperer": whisperer}

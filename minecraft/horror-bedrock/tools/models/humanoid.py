"""A player-shaped body, shared by the creatures that pass for players.

Built like the standard skin (head 8, body 8x12x4, limbs 4x12x4, each with
its outer layer) but every length can be stretched, so the same skeleton
makes an ordinary Steve or something that only looks like one from far
away. Arms and legs are split at the elbow and knee so the animations can
bend them; the tags are "head", "body", "arm", "forearm", "leg", "shin"
(plus "+" for the outer layer), and the painters decide what each looks
like."""

from models.kit import B, C


def humanoid(head=8.0, body=12.0, arm=12.0, leg=12.0, width=8.0, limb=4.0, outer=True, root="root"):
    """Bones for a player-like body standing on y=0. Returns (bones, heights)
    where heights has the y of the hips, shoulders and neck."""
    hips = leg
    neck = hips + body
    shoulder = neck - 2
    half = width / 2
    upper, lower = arm / 2, arm - arm / 2
    thigh, shin = leg / 2, leg - leg / 2

    def box(origin, size, tag, inflate):
        out = [C(origin, size, tag)]
        if outer:
            out.append(C(origin, size, tag + "+", inflate))
        return out

    bones = [
        B(root, None, (0, 0, 0)),
        B("body", root, (0, hips, 0), cubes=box((-half, hips, -2), (width, body, 4), "body", 0.25)),
        B("head", "body", (0, neck, 0), cubes=box((-head / 2, neck, -head / 2), (head, head, head), "head", 0.5)),
    ]
    for side, name in ((-1, "right"), (1, "left")):
        x0 = -half - limb if side < 0 else half
        bones += [
            B(f"{name}Arm", "body", (side * (half + limb / 4), shoulder, 0),
              cubes=box((x0, shoulder - upper + 2, -limb / 2), (limb, upper, limb), "arm", 0.25)),
            B(f"{name}Forearm", f"{name}Arm", (side * (half + limb / 2), shoulder - upper + 2, 0),
              cubes=box((x0, shoulder - arm + 2, -limb / 2), (limb, lower, limb), "forearm", 0.25)),
            B(f"{name}Leg", root, (side * limb / 2, hips, 0),
              cubes=box((side * limb / 2 - limb / 2, hips - thigh, -limb / 2), (limb, thigh, limb), "leg", 0.25)),
            B(f"{name}Shin", f"{name}Leg", (side * limb / 2, shin, 0),
              cubes=box((side * limb / 2 - limb / 2, 0, -limb / 2), (limb, shin, limb), "shin", 0.25)),
        ]
    return bones, {"hips": hips, "neck": neck, "shoulder": shoulder}

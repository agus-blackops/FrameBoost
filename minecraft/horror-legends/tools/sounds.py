#!/usr/bin/env python3
"""Synthesise the add-on's own sounds.

    python3 tools/sounds.py

Every sound is made from scratch here (oscillators, filtered noise and
envelopes), so the add-on ships no recorded audio. Writes Ogg Vorbis files
to resource_pack/sounds/hl/ and resource_pack/sounds/sound_definitions.json.

Needs numpy and soundfile (pip install numpy soundfile). The generated files
are committed, so tools/build.py does not need either.
"""

import json
from pathlib import Path

import numpy as np
import soundfile as sf

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "resource_pack" / "sounds" / "hl"
RATE = 22050
rng = np.random.default_rng(1313)


# --------------------------------------------------------------------------- #
# Building blocks
# --------------------------------------------------------------------------- #

def t_axis(seconds):
    return np.arange(int(seconds * RATE)) / RATE


def noise(seconds):
    return rng.uniform(-1, 1, int(seconds * RATE))


def one_pole(x, cutoff, high=False):
    """Gentle 6 dB/octave low-pass (or the high-pass left over)."""
    a = np.exp(-2 * np.pi * cutoff / RATE)
    y = np.empty_like(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc = (1 - a) * v + a * acc
        y[i] = acc
    return x - y if high else y


def resonator(x, freq, q):
    """Two-pole band-pass: a formant, a body resonance, a ringing wall."""
    freq = np.broadcast_to(np.asarray(freq, dtype=float), x.shape)
    r = np.exp(-np.pi * freq / (q * RATE))
    y = np.zeros_like(x)
    y1 = y2 = 0.0
    for i in range(len(x)):
        c = 2 * r[i] * np.cos(2 * np.pi * freq[i] / RATE)
        v = (1 - r[i]) * x[i] + c * y1 - r[i] * r[i] * y2
        y2, y1 = y1, v
        y[i] = v
    return y


def env(n, attack, release, curve=2.0):
    """Attack/release envelope over n samples (times in seconds)."""
    e = np.ones(n)
    a, r = int(attack * RATE), int(release * RATE)
    if a:
        e[:a] = np.linspace(0, 1, a) ** curve
    if r:
        e[-r:] *= np.linspace(1, 0, r) ** curve
    return e


def decay(n, seconds):
    return np.exp(-np.arange(n) / (seconds * RATE))


def place(buf, sound, at):
    i = int(at * RATE)
    end = min(len(buf), i + len(sound))
    buf[i:end] += sound[: end - i]


def saw(freq, seconds):
    phase = np.cumsum(np.broadcast_to(freq, int(seconds * RATE)) / RATE)
    return 2 * (phase % 1.0) - 1


def sine(freq, seconds):
    phase = np.cumsum(np.broadcast_to(np.asarray(freq, dtype=float), int(seconds * RATE)) / RATE)
    return np.sin(2 * np.pi * phase)


def soft_clip(x, drive=1.0):
    return np.tanh(x * drive) / np.tanh(drive)


def finish(x, peak=0.9):
    x = x - np.mean(x)
    m = np.max(np.abs(x)) or 1.0
    fade = min(len(x) // 4, int(0.02 * RATE))
    x = x * env(len(x), 0.002, fade / RATE, 1.0)
    return (x / m * peak).astype(np.float32)


# --------------------------------------------------------------------------- #
# The sounds
# --------------------------------------------------------------------------- #

def heartbeat():
    """One heavy beat, lub-dub, felt more than heard."""
    out = np.zeros(int(1.0 * RATE))
    for at, amp, f0 in ((0.0, 1.0, 62), (0.26, 0.7, 54)):
        n = int(0.32 * RATE)
        f = f0 * (1 + 0.6 * decay(n, 0.02))
        thump = sine(f, 0.32) * decay(n, 0.07) * env(n, 0.004, 0.0)
        thump += one_pole(noise(0.32), 180) * decay(n, 0.03) * 2
        place(out, thump * amp, at)
    return finish(soft_clip(out, 1.6))


def breath():
    """Slow, wet breathing right behind you: in, out."""
    out = np.zeros(int(3.4 * RATE))
    for at, dur, bright, amp in ((0.05, 1.2, 1800, 0.7), (1.5, 1.7, 1100, 1.0)):
        n = int(dur * RATE)
        s = noise(dur)
        s = resonator(s, np.linspace(bright, bright * 0.7, n), 2.5) + 0.3 * resonator(s, 600, 4)
        rasp = 1 + 0.35 * sine(38 + 6 * rng.random(), dur)  # something catching in the throat
        s *= np.sin(np.linspace(0, np.pi, n)) ** 1.6 * rasp
        place(out, s * amp, at)
    return finish(one_pole(out, 3500))


def whisper():
    """Words that almost make sense."""
    length = 2.8
    out = np.zeros(int(length * RATE))
    at = 0.1
    while at < length - 0.35:
        dur = rng.uniform(0.12, 0.3)
        n = int(dur * RATE)
        s = noise(dur)
        if rng.random() < 0.35:  # "s", "sh"
            s = resonator(s, rng.uniform(3500, 6000), 3)
        else:  # a breathy vowel: two formants
            f1, f2 = rng.uniform(350, 800), rng.uniform(1000, 2400)
            s = resonator(s, f1, 6) + 0.6 * resonator(s, f2, 8)
        s *= np.sin(np.linspace(0, np.pi, n)) ** 1.2 * rng.uniform(0.5, 1.0)
        place(out, s, at)
        at += dur + rng.uniform(0.02, 0.18)
    return finish(out, 0.8)


def static():
    """A broken signal: crackle, bit-crushed hiss, dropouts, a mains hum."""
    length = 1.3
    n = int(length * RATE)
    x = noise(length)
    hold = np.repeat(rng.uniform(-1, 1, n // 6 + 1), 6)[:n]  # sample-and-hold crush
    gate = np.repeat(rng.random(n // 800 + 1) > 0.25, 800)[:n].astype(float)
    x = (0.6 * x + 0.8 * hold) * gate
    x = np.round(x * 6) / 6
    x += 0.35 * np.sign(sine(50, length)) * (rng.random() * 0.5 + 0.5)
    return finish(x * env(n, 0.005, 0.15), 0.75)


def stinger():
    """The jumpscare: a dissonant scream of strings and a boom under it."""
    length = 2.6
    n = int(length * RATE)
    out = np.zeros(n)
    for f in (233.1, 246.9, 311.1, 329.6, 466.2, 493.9, 659.3, 698.5):
        vib = 1 + 0.006 * sine(rng.uniform(5, 7), length)
        out += saw(f * vib * rng.uniform(0.996, 1.004), length) * rng.uniform(0.6, 1.0)
    out = resonator(out, 1400, 1.2) + 0.4 * out
    out *= decay(n, 0.9) * env(n, 0.003, 0.4)
    boom = sine(48 * (1 + 1.5 * decay(n, 0.05)), length) * decay(n, 0.5) * 2.5
    hit = one_pole(noise(length), 2500) * decay(n, 0.08) * 1.5
    return finish(soft_clip(out * 0.25 + boom + hit, 2.2))


def ringing():
    """What your ears do after."""
    length = 5.0
    n = int(length * RATE)
    tone = sine(4180 * (1 + 0.002 * sine(0.7, length)), length) * 0.5
    tone += sine(4190, length) * 0.2
    muffled = one_pole(noise(length), 300) * 0.8
    shape = np.minimum(1, np.arange(n) / (0.1 * RATE)) * np.exp(-np.arange(n) / (1.8 * RATE))
    return finish((tone + muffled) * shape, 0.6)


def drone():
    """Dread: low, beating, rising and sinking like something breathing."""
    length = 9.0
    n = int(length * RATE)
    out = np.zeros(n)
    for f, a in ((41.2, 1.0), (41.9, 0.8), (58.3, 0.6), (61.7, 0.5), (87.3, 0.3), (123.5, 0.15)):
        out += sine(f, length) * a
    # A dissonant cluster higher up, so it still reads on small speakers.
    for f, a in ((164.8, 0.35), (174.6, 0.3), (233.1, 0.22), (246.9, 0.18)):
        out += soft_clip(sine(f * (1 + 0.003 * sine(rng.uniform(0.1, 0.3), length)), length), 2.0) * a
    wind = resonator(noise(length), 420 + 160 * sine(0.13, length), 3) * 1.2
    swell = 0.55 + 0.45 * sine(0.17, length)
    return finish((out + wind) * swell * env(n, 2.0, 2.5, 1.5), 0.8)


MELODY = [  # a lullaby in E minor that has been sitting in the dark too long
    (76, 1), (79, 1), (83, 2), (81, 1), (79, 1), (78, 2),
    (76, 1), (79, 1), (83, 1), (84, 1), (83, 2), (81, 2),
    (76, 1), (74, 1), (76, 2), (71, 3),
]


def musicbox():
    """A music box, out of tune, winding down."""
    beat = 0.36
    ends = 0.1 + sum(d * beat * (1 + 0.45 * (k / len(MELODY)) ** 2) for k, (_, d) in enumerate(MELODY))
    length = ends + 1.8
    out = np.zeros(int(length * RATE))
    at = 0.1
    for k, (note, dur) in enumerate(MELODY):
        slow = 1 + 0.45 * (k / len(MELODY)) ** 2  # the spring running out
        f = 440 * 2 ** ((note - 69) / 12) * (1 + rng.uniform(-0.012, 0.012) - 0.02 * (k / len(MELODY)))
        tine_len = 2.0
        m = int(tine_len * RATE)
        tone = (sine(f, tine_len) + 0.35 * sine(f * 3.01, tine_len) * decay(m, 0.25)
                + 0.12 * sine(f * 5.43, tine_len) * decay(m, 0.08))
        tone *= decay(m, 0.7) * env(m, 0.002, 0.0)
        place(out, tone, at)
        at += dur * beat * slow
    wow = 1 + 0.004 * np.sin(np.linspace(0, 40, len(out)))
    out = np.interp(np.cumsum(wow) - 1, np.arange(len(out)), out)
    out += one_pole(noise(length), 3000) * 0.015  # the hiss of an old recording
    return finish(out, 0.7)


def scream():
    """The Cave Dweller: a human throat doing something it shouldn't."""
    length = 1.7
    n = int(length * RATE)
    tt = np.linspace(0, 1, n)
    pitch = 700 + 900 * np.sin(np.pi * tt ** 0.7) * (1 - 0.5 * tt)
    pitch *= 1 + 0.03 * sine(23, length)
    src = saw(pitch, length) + 0.4 * noise(length)
    voice = resonator(src, 900, 5) + 0.8 * resonator(src, 2400, 7) + 0.5 * resonator(src, 3600, 9)
    voice *= env(n, 0.03, 0.45)
    return finish(soft_clip(voice * 0.6, 3.0), 0.85)


def chitter():
    """Clicks in the dark, too fast, too many."""
    length = 1.0
    out = np.zeros(int(length * RATE))
    at = 0.02
    while at < length - 0.05:
        m = int(0.012 * RATE)
        click = resonator(noise(0.012), rng.uniform(2500, 4200), 8) * decay(m, 0.003)
        place(out, click * rng.uniform(0.5, 1.0), at)
        at += rng.choice([0.03, 0.035, 0.045, 0.09])
    return finish(out, 0.8)


def knock():
    """Three heavy knocks on a wooden door."""
    out = np.zeros(int(1.6 * RATE))
    for at, amp in ((0.0, 1.0), (0.42, 0.9), (0.84, 1.1)):
        m = int(0.4 * RATE)
        hit = noise(0.4) * decay(m, 0.008)
        body = resonator(hit, 110, 18) * 1.5 + resonator(hit, 190, 14) + 0.6 * resonator(hit, 330, 12)
        place(out, (body + hit * 0.4) * amp, at)
    return finish(soft_clip(out, 1.5))



def boss():
    """The fight: war drums at 100 bpm under a low dissonant drone, a string
    stab every two bars. 12 seconds, played in a loop by the script."""
    length = 12.0
    n = int(length * RATE)
    out = np.zeros(n)
    beat = 60 / 100
    for i in range(int(length / beat)):
        at = i * beat
        m = int(0.5 * RATE)
        f = 55 * (1 + 1.2 * decay(m, 0.03))
        drum = sine(f, 0.5) * decay(m, 0.12) + one_pole(noise(0.5), 400) * decay(m, 0.02) * 1.5
        place(out, drum * (1.0 if i % 4 == 0 else 0.6), at)
        if i % 2 == 1:
            m2 = int(0.3 * RATE)
            tom = sine(110 * (1 + 0.5 * decay(m2, 0.02)), 0.3) * decay(m2, 0.08)
            place(out, tom * 0.5, at + beat / 2)
    for f, a in ((41.2, 0.5), (43.6, 0.4), (61.7, 0.3)):
        out += soft_clip(saw(f, length), 1.5) * a * 0.3
    for bar in range(0, int(length / (beat * 8))):
        at = bar * beat * 8
        m = int(1.2 * RATE)
        stab = sum(saw(fr, 1.2) for fr in (233.1, 246.9, 349.2, 370.0)) * decay(m, 0.35) * env(m, 0.01, 0.1)
        place(out, resonator(stab, 1200, 1.5) * 0.4, at)
    return finish(soft_clip(out, 1.4))


def page():
    """Old paper, turned."""
    out = np.zeros(int(0.7 * RATE))
    for at in (0.0, 0.12, 0.3):
        dur = rng.uniform(0.12, 0.25)
        m = int(dur * RATE)
        crackle = resonator(noise(dur), rng.uniform(2500, 4500), 1.5) * env(m, 0.01, dur * 0.7)
        crackle *= (rng.random(m) > 0.6) * 0.8 + 0.2
        place(out, crackle, at)
    return finish(out, 0.7)


def click():
    """A flashlight switch."""
    m = int(0.12 * RATE)
    tick = resonator(noise(0.12), 3200, 12) * decay(m, 0.006)
    thunk = sine(180, 0.12) * decay(m, 0.02) * 0.6
    return finish(tick + thunk, 0.8)


def dawn():
    """Relief: a warm chord opening up, with bells."""
    length = 7.0
    n = int(length * RATE)
    out = np.zeros(n)
    for f in (130.8, 196.0, 261.6, 329.6, 392.0, 587.3):
        out += sine(f * (1 + 0.002 * sine(rng.uniform(0.2, 0.5), length)), length) * 0.25
    out *= env(n, 2.5, 3.0, 1.2)
    for k, f in enumerate((1046.5, 1318.5, 1568.0, 2093.0)):
        m = int(3.0 * RATE)
        bell = (sine(f, 3.0) + 0.3 * sine(f * 2.76, 3.0)) * decay(m, 0.8) * env(m, 0.003, 0.0)
        place(out, bell * 0.3, 1.5 + k * 0.6)
    return finish(out, 0.75)

SOUNDS = {
    # name: (generator, category, min_distance, max_distance, volume)
    "heartbeat": (heartbeat, "hostile", 0.5, 8, 1.0),
    "breath": (breath, "hostile", 0.5, 12, 1.0),
    "whisper": (whisper, "hostile", 0.5, 14, 0.9),
    "static": (static, "hostile", 1, 24, 0.9),
    "stinger": (stinger, "hostile", 1, 32, 1.0),
    "ringing": (ringing, "hostile", 0.5, 8, 0.7),
    "drone": (drone, "ambient", 1, 64, 1.0),
    "musicbox": (musicbox, "ambient", 1, 20, 1.0),
    "scream": (scream, "hostile", 2, 48, 1.0),
    "chitter": (chitter, "hostile", 1, 20, 0.9),
    "knock": (knock, "hostile", 1, 24, 1.0),
    "boss": (boss, "hostile", 0.5, 16, 0.8),
    "page": (page, "player", 0.5, 8, 0.9),
    "click": (click, "player", 0.5, 8, 0.8),
    "dawn": (dawn, "ambient", 0.5, 16, 1.0),
}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    definitions = {"format_version": "1.14.0", "sound_definitions": {}}
    for name, (make, category, near, far, volume) in SOUNDS.items():
        data = make()
        sf.write(OUT / f"{name}.ogg", data, RATE, format="OGG", subtype="VORBIS")
        definitions["sound_definitions"][f"hl.{name}"] = {
            "category": category,
            "min_distance": near,
            "max_distance": far,
            "sounds": [{"name": f"sounds/hl/{name}", "volume": volume, "load_on_low_memory": True}],
        }
        print(f"hl.{name}: {len(data) / RATE:.1f} s, {(OUT / f'{name}.ogg').stat().st_size} bytes")
    path = ROOT / "resource_pack" / "sounds" / "sound_definitions.json"
    path.write_text(json.dumps(definitions, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()

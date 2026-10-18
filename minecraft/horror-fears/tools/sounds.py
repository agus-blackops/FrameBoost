#!/usr/bin/env python3
"""Synthesise the add-on's own sounds.

    python3 tools/sounds.py

Every sound is made from scratch here (oscillators, filtered noise and
envelopes), so the add-on ships no recorded audio. Writes Ogg Vorbis files
to resource_pack/sounds/hl/ and resource_pack/sounds/sound_definitions.json.

Horror Legends: Fears masters everything LOUD: heavy saturation and
normalisation to full scale, so the sounds are dense and hard to ignore, and
four new sounds were added (boom, screech, wail, shatter). The add-on has a
"Loud sounds" switch in the journal for those who want it quieter.

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


def loud(x, drive=3.0, peak=0.62):
    """Master a sound LOUD: saturate it so the quiet parts come up, then
    normalise it to full scale. The result is much denser than the original."""
    x = np.asarray(x, dtype=float)
    x = x - np.mean(x)
    x = np.tanh(x / (np.max(np.abs(x)) or 1.0) * drive)
    # A second, gentler stage glues the saturated peaks together.
    x = np.tanh(x * 1.4) / np.tanh(1.4)
    return finish(x, peak)


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


def _static():
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


def static():
    return loud(_static(), 3.0)


def _stinger():
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


def stinger():
    return loud(_stinger(), 3.0)


def _ringing():
    """What your ears do after."""
    length = 5.0
    n = int(length * RATE)
    tone = sine(4180 * (1 + 0.002 * sine(0.7, length)), length) * 0.5
    tone += sine(4190, length) * 0.2
    muffled = one_pole(noise(length), 300) * 0.8
    shape = np.minimum(1, np.arange(n) / (0.1 * RATE)) * np.exp(-np.arange(n) / (1.8 * RATE))
    return finish((tone + muffled) * shape, 0.6)


def ringing():
    return loud(_ringing(), 3.0)


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


def _scream():
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


def scream():
    return loud(_scream(), 3.0)


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


def _knock():
    """Three heavy knocks on a wooden door."""
    out = np.zeros(int(1.6 * RATE))
    for at, amp in ((0.0, 1.0), (0.42, 0.9), (0.84, 1.1)):
        m = int(0.4 * RATE)
        hit = noise(0.4) * decay(m, 0.008)
        body = resonator(hit, 110, 18) * 1.5 + resonator(hit, 190, 14) + 0.6 * resonator(hit, 330, 12)
        place(out, (body + hit * 0.4) * amp, at)
    return finish(soft_clip(out, 1.5))



def knock():
    return loud(_knock(), 3.0)


def _boss():
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


def boss():
    return loud(_boss(), 3.0)


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


def _siren():
    """Siren Head: an air-raid siren winding up and down, torn by distortion."""
    length = 6.0
    t = t_axis(length)
    pitch = 260 + 420 * (0.5 - 0.5 * np.cos(2 * np.pi * t / length * 1.5))
    tone = saw(pitch, length) * 0.5 + saw(pitch * 1.505, length) * 0.35 + sine(pitch * 0.5, length) * 0.3
    tone = resonator(tone, 900, 1.2) * 0.6 + tone * 0.4
    wobble = 1 + 0.25 * sine(7.0, length)
    out = soft_clip(tone * wobble * env(len(t), 0.6, 1.5, 1.2), 3.0)
    return finish(out, 0.85)


def siren():
    return loud(_siren(), 3.0)


def _voice():
    """A voice through the sirens: babbling formants over radio static,
    words that are nearly words."""
    length = 3.5
    n = int(length * RATE)
    t = t_axis(length)
    pitch = 110 + 30 * sine(0.9, length) + 25 * sine(5.3, length)
    buzz = saw(pitch, length)
    f1 = 500 + 350 * np.sign(sine(3.1, length)) * np.abs(sine(1.7, length))
    f2 = 1500 + 700 * sine(2.3, length)
    speech = resonator(buzz, np.abs(f1) + 250, 6) + 0.6 * resonator(buzz, np.abs(f2) + 400, 8)
    gate = (sine(4.2, length) + 0.3 * sine(9.7, length)) > -0.2
    radio = resonator(speech * gate, 1400, 0.8)
    hiss = one_pole(noise(length), 2000, high=True) * 0.25
    crackle = (rng.random(n) > 0.995) * rng.uniform(-1, 1, n)
    return finish(soft_clip(radio * 2 + hiss + crackle, 2.5) * env(n, 0.05, 0.3), 0.8)


def voice():
    return loud(_voice(), 3.0)


def _stone():
    """The statue moves: stone grinding on stone, a short heavy scrape."""
    length = 0.9
    n = int(length * RATE)
    grit = noise(length) * (rng.random(n) > 0.4)
    body = resonator(grit, 180, 2.0) * 1.5 + resonator(grit, 520, 3) * 0.6 + one_pole(grit, 900) * 0.4
    return finish(body * env(n, 0.04, 0.35, 1.5), 0.9)


def stone():
    return loud(_stone(), 3.0)


def gurgle():
    """The Drowned One: water in a throat, bubbling up."""
    length = 2.2
    n = int(length * RATE)
    out = np.zeros(n)
    for _ in range(40):
        at = rng.uniform(0, length - 0.2)
        dur = rng.uniform(0.04, 0.12)
        f0 = rng.uniform(250, 700)
        m = int(dur * RATE)
        bub = sine(np.linspace(f0, f0 * 1.8, m), dur) * decay(m, dur / 3)
        place(out, bub * rng.uniform(0.3, 1.0), at)
    throat = resonator(saw(70 + 10 * sine(3, length), length), 400, 3) * 0.4
    return finish((out + throat) * env(n, 0.1, 0.5), 0.8)


def _shriek():
    """The Wendigo: a high, broken shriek, half animal, half wind."""
    length = 2.0
    n = int(length * RATE)
    t = t_axis(length)
    f = 1100 + 500 * np.sin(2 * np.pi * 0.6 * t) + 90 * sine(23, length)
    tone = saw(f, length) * 0.5 + saw(f * 1.33, length) * 0.4
    tone = resonator(tone, 2600, 2) + resonator(tone, 1300, 3) * 0.5
    air = resonator(noise(length), 3000, 1.5) * 0.5
    return finish(soft_clip((tone + air) * env(n, 0.05, 0.9, 1.3), 2.0), 0.85)


def shriek():
    return loud(_shriek(), 3.0)


def whistle():
    """Something whistling in the trees, one falling note, too long."""
    length = 2.5
    n = int(length * RATE)
    f = np.linspace(1500, 900, n) * (1 + 0.01 * sine(6, length))
    tone = sine(f, length) + 0.15 * resonator(noise(length), 1200, 4)
    return finish(tone * env(n, 0.3, 1.0, 1.5), 0.6)


def _hack():
    """Entity 303: data being torn apart, beeps and bit-crushed bursts."""
    length = 1.6
    n = int(length * RATE)
    out = np.zeros(n)
    for k in range(14):
        at = k * length / 14 + rng.uniform(0, 0.03)
        dur = rng.uniform(0.03, 0.09)
        f = rng.choice([220, 440, 880, 1760, 3520]) * rng.uniform(0.98, 1.02)
        beep = np.sign(sine(f, dur)) * 0.6
        place(out, beep, at)
    crush = np.round(noise(length) * 3) / 3 * (rng.random(n) > 0.7) * 0.3
    return finish(out + crush, 0.75)


def hack():
    return loud(_hack(), 3.0)


def _chomp():
    """The Mimic's lid slamming shut on something."""
    length = 0.5
    n = int(length * RATE)
    slam = resonator(noise(length), 160, 3) * decay(n, 0.05) * 2
    wood = resonator(noise(length), 900, 6) * decay(n, 0.03)
    teeth = resonator(noise(length), 3500, 4) * decay(n, 0.015) * 0.6
    return finish(soft_clip(slam + wood + teeth, 1.5), 0.95)


def chomp():
    return loud(_chomp(), 3.0)


def boom():
    """A hit you feel in your chest: a sub-bass drop under a wall of noise."""
    length = 2.4
    n = int(length * RATE)
    sub = sine(38 * (1 + 2.5 * decay(n, 0.06)), length) * decay(n, 0.7) * 3.0
    thump = sine(80 * (1 + 1.2 * decay(n, 0.04)), length) * decay(n, 0.25) * 1.6
    slam = one_pole(noise(length), 3500) * decay(n, 0.12) * 2.2
    rumble = one_pole(noise(length), 120) * decay(n, 0.9) * 2.0
    return loud(sub + thump + slam + rumble, 3.5)


def screech():
    """Metal torn open: two detuned, falling shrieks, with grit in them."""
    length = 2.2
    n = int(length * RATE)
    t = t_axis(length)
    f = 2200 - 700 * np.minimum(1, t / 1.6) + 140 * sine(31, length)
    tone = saw(f, length) + saw(f * 1.07, length) * 0.8 + saw(f * 0.5, length) * 0.5
    tone = resonator(tone, 3000, 2) + 0.5 * resonator(tone, 1700, 3)
    grit = (np.sign(sine(f * 0.5, length)) * rng.uniform(0.4, 1.0, n)) * 0.3
    return loud((tone + grit) * env(n, 0.01, 0.8, 1.4), 4.0)


def wail():
    """A long scream that doesn't stop for breath, cracking at the top."""
    length = 3.4
    n = int(length * RATE)
    tt = np.linspace(0, 1, n)
    pitch = 520 + 1250 * np.sin(np.pi * tt ** 0.8) ** 0.8
    pitch *= 1 + 0.045 * sine(6.5, length) + 0.02 * sine(41, length)
    src = saw(pitch, length) * 0.9 + 0.7 * noise(length) * (0.4 + 0.6 * tt)
    voice = resonator(src, 850, 4) + 0.9 * resonator(src, 2300, 6) + 0.6 * resonator(src, 3700, 8)
    voice *= env(n, 0.02, 1.1, 1.3)
    return loud(voice, 4.5)


def shatter():
    """A window going at once: a crack, then a long rain of glass."""
    length = 2.0
    out = np.zeros(int(length * RATE))
    place(out, resonator(noise(0.05), 5200, 2) * decay(int(0.05 * RATE), 0.01) * 3.0, 0.0)
    at = 0.03
    while at < length - 0.1:
        m = int(0.05 * RATE)
        shard = resonator(noise(0.05), rng.uniform(2800, 7000), 18) * decay(m, 0.012)
        place(out, shard * rng.uniform(0.3, 1.0) * np.exp(-at * 1.6), at)
        at += rng.uniform(0.008, 0.07)
    return loud(out, 2.5)


# --------------------------------------------------------------------------- #
# 1.2: voices and footsteps that come FROM the creature (all mono, so the game
# places them in 3D: direction and distance)
# --------------------------------------------------------------------------- #

def laugh():
    """Herobrine: a low, slowed-down laugh, a few ha's that don't belong to a mouth."""
    length = 2.6
    out = np.zeros(int(length * RATE))
    at = 0.1
    f0 = 95.0
    for k in range(5):
        dur = 0.22
        n = int(dur * RATE)
        f = f0 * (1.0 - 0.04 * k)
        src = saw(f * (1 + 0.04 * sine(7, dur)), dur) + 0.3 * noise(dur)
        v = resonator(src, 700, 4) + 0.7 * resonator(src, 1100, 6)
        v *= np.sin(np.linspace(0, np.pi, n)) ** 0.8
        place(out, v, at + k * 0.38)
    return loud(out * env(len(out), 0.01, 0.8, 1.2), 3.0)


def growl():
    """The Cave Dweller: a wet, rattling growl that stays in the chest."""
    length = 2.2
    n = int(length * RATE)
    t = t_axis(length)
    f = 70 + 30 * np.sin(2 * np.pi * 0.9 * t) + 14 * sine(26, length)
    src = saw(f, length) * 0.8 + noise(length) * 0.35
    rattle = 0.55 + 0.45 * np.sign(sine(32, length))
    v = (resonator(src, 220, 3) + 0.8 * resonator(src, 560, 5)) * rattle
    return loud(v * env(n, 0.08, 0.7, 1.4), 3.2)


def moan():
    """The Nameless Villager's 'hrmm', stretched out and falling, too slow."""
    length = 2.4
    n = int(length * RATE)
    tt = np.linspace(0, 1, n)
    f = 190 * (1.0 - 0.45 * tt) * (1 + 0.012 * sine(5, length))
    src = saw(f, length) + 0.15 * noise(length)
    v = resonator(src, 420, 7) + 0.9 * resonator(src, 1100, 9) + 0.3 * resonator(src, 2200, 11)
    gate = np.sin(np.pi * tt) ** 0.7
    return loud(v * gate, 2.8)


def step():
    """One heavy footstep: a thud, the scuff of a foot, and a little stone."""
    length = 0.55
    n = int(length * RATE)
    thud = sine(70 * (1 + 0.8 * decay(n, 0.03)), length) * decay(n, 0.09) * 2.2
    scuff = one_pole(noise(length), 1800) * decay(n, 0.045) * 1.2
    grit = resonator(noise(length), 3200, 6) * decay(n, 0.02) * 0.4
    return loud(thud + scuff + grit, 3.0, 0.7)


def creak():
    """A door, a board, a rope: wood complaining under weight."""
    length = 1.9
    n = int(length * RATE)
    t = t_axis(length)
    f = 150 + 90 * np.sin(2 * np.pi * 0.7 * t) ** 2 + 25 * sine(11, length)
    src = saw(f, length) * (0.5 + 0.5 * np.sign(sine(f / 6, length)))
    v = resonator(src, 900, 12) + 0.6 * resonator(src, 1700, 14)
    return loud(v * env(n, 0.1, 0.5, 1.2), 2.5, 0.7)


def glitch():
    """A sound that skips: slices of noise and tone repeated, cut and pitched wrong."""
    length = 1.6
    out = np.zeros(int(length * RATE))
    at = 0.0
    while at < length - 0.1:
        dur = rng.choice([0.02, 0.03, 0.05, 0.08, 0.12])
        n = int(dur * RATE)
        f = rng.choice([220, 440, 880, 1760, 3520])
        piece = sine(f, dur) if rng.random() < 0.6 else noise(dur) * 0.8
        piece = np.sign(piece) * 0.7 if rng.random() < 0.4 else piece
        reps = int(rng.integers(1, 4))
        for r in range(reps):
            if at + (r + 1) * dur >= length:
                break
            place(out, piece * rng.uniform(0.5, 1.0), at + r * dur)
        at += dur * reps + rng.choice([0.0, 0.0, 0.03, 0.06])
    return loud(out, 3.0, 0.45)


def roar():
    """The Hollow Miner: stone grinding into a voice, huge and slow."""
    length = 3.2
    n = int(length * RATE)
    t = t_axis(length)
    f = 55 + 35 * np.sin(np.pi * np.minimum(1, t / 2.2)) + 10 * sine(19, length)
    src = saw(f, length) + 0.5 * noise(length)
    v = resonator(src, 160, 3) + resonator(src, 420, 4) + 0.6 * resonator(src, 900, 6)
    rumble = one_pole(noise(length), 140) * 2.0
    return loud((v + rumble) * env(n, 0.15, 1.0, 1.3), 3.5)


def howl():
    """Wind with a voice in it: one long note that doesn't belong to a throat."""
    length = 4.0
    n = int(length * RATE)
    t = t_axis(length)
    f = 330 + 160 * np.sin(2 * np.pi * 0.35 * t) + 12 * sine(5, length)
    src = saw(f, length) * 0.5 + noise(length) * 0.7
    v = resonator(src, 700, 14) + resonator(src, 1450, 16) * 0.8
    air = resonator(noise(length), 2200, 1.5) * 0.4
    return loud((v + air) * env(n, 0.8, 1.6, 1.2), 2.5, 0.7)


def warp():
    """The Faceless Enderman moving: a rising warble that folds in on itself."""
    length = 1.1
    n = int(length * RATE)
    t = t_axis(length)
    up = 300 * (1 + 7 * (t / length) ** 2)
    v = sine(up, length) * 0.7 + sine(up * 1.5 * (1 + 0.02 * sine(31, length)), length) * 0.5
    v += resonator(noise(length), 3000, 4) * 0.3 * (t / length)
    v *= np.sin(np.pi * np.minimum(1, t / length * 1.05)) ** 0.7
    return loud(v, 2.2, 0.7)


SOUNDS = {
    # name: (generator, category, min_distance, max_distance, volume)
    "heartbeat": (heartbeat, "hostile", 0.5, 8, 1.0),
    "breath": (breath, "hostile", 0.5, 12, 1.0),
    "whisper": (whisper, "hostile", 0.5, 14, 0.9),
    "static": (static, "hostile", 1, 24, 0.9),
    "stinger": (stinger, "hostile", 1, 32, 1.0),
    "ringing": (ringing, "hostile", 0.5, 8, 0.7),
    "drone": (drone, "ambient", 1, 64, 1.0),
    "scream": (scream, "hostile", 2, 48, 1.0),
    "chitter": (chitter, "hostile", 1, 20, 0.9),
    "knock": (knock, "hostile", 1, 24, 1.0),
    "boss": (boss, "hostile", 0.5, 16, 0.8),
    "page": (page, "player", 0.5, 8, 0.9),
    "click": (click, "player", 0.5, 8, 0.8),
    "dawn": (dawn, "ambient", 0.5, 16, 1.0),
    "gurgle": (gurgle, "hostile", 1, 20, 1.0),
    "shriek": (shriek, "hostile", 4, 96, 1.0),
    "hack": (hack, "hostile", 1, 24, 0.9),
    "boom": (boom, "hostile", 2, 64, 1.0),
    "screech": (screech, "hostile", 2, 64, 1.0),
    "wail": (wail, "hostile", 2, 96, 1.0),
    "shatter": (shatter, "hostile", 1, 48, 1.0),
    # 1.2: voices and footsteps placed in 3D
    "laugh": (laugh, "hostile", 2, 72, 1.0),
    "growl": (growl, "hostile", 2, 56, 1.0),
    "moan": (moan, "hostile", 2, 64, 1.0),
    "step": (step, "hostile", 1, 40, 1.0),
    "creak": (creak, "hostile", 1, 32, 1.0),
    "glitch": (glitch, "hostile", 1, 48, 1.0),
    "roar": (roar, "hostile", 3, 96, 1.0),
    "howl": (howl, "hostile", 6, 128, 1.0),
    "warp": (warp, "hostile", 1, 56, 1.0),
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

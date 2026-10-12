package com.frameboost.ragdoll;

/**
 * Original soundtrack, composed note by note and rendered at start-up by a small synthesizer
 * (PolyBLEP oscillators, envelopes, filters, echo and reverb). No audio files are shipped.
 *
 * Melodies use "note/length" tokens, length in 16th notes ("e5/4" = E5 for a quarter, "r/2" = rest).
 * Every bar is 16 steps. Loops are seamless: note tails and effect tails wrap around.
 */
final class Music {
    static final int RATE = 32000;
    static final int TITLE = 0, LAB = 1, CHALLENGE = 2, COUNT = 3;
    static final String[] NAMES = {"Trastazo (tema principal)", "Laboratorio", "Desafío"};

    /** A rendered loop. stems[0] always plays; stems[1] (if any) is the "action" layer. */
    static final class Track {
        final short[][] stems;
        final int length;
        Track(short[][] s) { stems = s; length = s[0].length; }
    }

    // =========================================================================== composition ===

    private static final class Section {
        String[] chords;     // one per bar
        String melody;       // lead line
        String drums;        // "light", "full", "none", "drive"
        boolean lead = true, arp = true, pad = true, bass = true;
        Section(String chords, String melody, String drums) {
            this.chords = chords.trim().split("\\s+");
            this.melody = melody;
            this.drums = drums;
        }
    }

    private static final class Song {
        float bpm;
        Section[] sections;
        String bassPat;      // per bar, 16 chars: x root, o octave, 5 fifth, - hold, . rest
        boolean swing, stems;
        float leadVol = 0.32f, padVol = 0.16f, arpVol = 0.07f, bassVol = 0.34f, drumVol = 0.55f;
        int leadWave;        // 0 pulse, 1 saw, 2 triangle
    }

    private static Song song(int id) {
        Song s = new Song();
        switch (id) {
            case TITLE: {
                // A minor, 100 BPM. Heroic but cheeky: the main theme.
                s.bpm = 100; s.bassPat = "x-.x-.x-x-.x-.o-"; s.leadWave = 0;
                Section a = new Section("Am F C G Am F G E",
                        "e5/4 a5/4 g5/2 e5/2 c5/4 | f5/6 e5/2 c5/4 a4/4 | g5/4 e5/2 g5/2 c6/6 b5/2 | d6/8 b5/4 g5/4 |"
                      + "e5/4 a5/4 b5/2 c6/2 b5/2 a5/2 | c6/6 a5/2 f5/4 a5/4 | g5/4 b5/4 d6/4 b5/4 | g#5/8 b5/4 e5/4", "light");
                Section b = new Section("F G Em Am Dm G C E",
                        "a5/4 c6/4 a5/2 g5/2 f5/4 | g5/4 b5/4 d6/6 r/2 | e6/4 d6/2 b5/2 g5/4 e5/4 | a5/12 r/4 |"
                      + "f5/4 a5/4 d6/4 c6/2 a5/2 | b5/4 d6/4 g6/6 f6/2 | e6/6 d6/2 c6/4 g5/4 | g#5/4 b5/4 e6/8", "full");
                Section intro = new Section("Am F C E", "r/16 | r/16 | r/16 | r/16", "none");
                intro.lead = false;
                s.sections = new Section[]{intro, a, b, a};
                break;
            }
            case LAB: {
                // D dorian, 108 BPM, lo-fi swing for building things. Two stems: calm + action.
                s.bpm = 108; s.bassPat = "x--.x.5-x--.o-5."; s.swing = true; s.stems = true; s.leadWave = 2;
                s.leadVol = 0.36f; s.padVol = 0.15f;
                Section a1 = new Section("Dm9 G13 Cmaj7 Am7",
                        "a5/3 c6/3 a5/2 f5/4 e5/4 | d5/4 e5/2 f5/2 g5/4 b5/4 | c6/6 b5/2 g5/4 e5/4 | a5/8 g5/4 e5/4", "full");
                Section a2 = new Section("Dm9 G13 Cmaj7 Am7",
                        "d6/3 c6/3 a5/2 c6/4 d6/4 | e6/4 d6/2 b5/2 g5/8 | e6/2 d6/2 c6/2 b5/2 g5/4 e5/4 | a5/12 r/4", "full");
                Section b1 = new Section("Bbmaj7 C Am7 D",
                        "f5/4 a5/4 d6/4 c6/4 | e5/4 g5/4 c6/4 e6/4 | d6/6 c6/2 a5/4 g5/4 | f#5/8 a5/4 d6/4", "full");
                Section b2 = new Section("Bbmaj7 C Am7 D",
                        "d6/4 c6/2 a5/2 f5/8 | g5/2 a5/2 c6/4 e6/8 | d6/4 c6/4 a5/4 e5/4 | f#5/4 a5/4 d6/8", "full");
                s.sections = new Section[]{a1, a2, b1, b2};
                break;
            }
            default: {
                // E minor, 132 BPM, driving: challenges against the clock.
                s.bpm = 132; s.bassPat = "xoxoxoxoxoxoxo5o"; s.leadWave = 1;
                s.leadVol = 0.24f; s.bassVol = 0.3f; s.arpVol = 0.06f;
                Section a = new Section("Em C D Bm Em C Am B",
                        "e5/2 e5/2 g5/2 b5/2 e6/4 d6/2 b5/2 | c6/4 b5/2 g5/2 e5/4 g5/4 | f#5/2 a5/2 d6/4 a5/2 f#5/2 d5/4 | b5/6 a5/2 f#5/4 d5/4 |"
                      + "e6/4 b5/4 g5/4 e5/4 | g5/2 a5/2 b5/2 c6/2 e6/8 | c6/4 a5/4 e5/4 a5/4 | d#6/8 b5/4 f#5/4", "drive");
                Section b = new Section("C D Em Em C D B B",
                        "g5/4 e5/4 c6/8 | a5/4 f#5/4 d6/8 | b5/2 c6/2 b5/2 a5/2 g5/4 e5/4 | e5/16 |"
                      + "e6/4 c6/4 g5/4 c6/4 | f#6/4 d6/4 a5/4 d6/4 | d#6/4 b5/4 f#5/4 b5/4 | d#6/8 f#6/8", "drive");
                Section brk = new Section("Em C D B", "r/16 | r/16 | r/16 | r/16", "light");
                brk.lead = false;
                s.sections = new Section[]{a, b, brk, a};
                break;
            }
        }
        return s;
    }

    // ============================================================================= theory ====

    static int midi(String n) {
        n = n.toLowerCase();
        int[] base = {9, 11, 0, 2, 4, 5, 7};  // a b c d e f g
        int semi = base[n.charAt(0) - 'a'];
        int i = 1;
        if (i < n.length() && n.charAt(i) == '#') { semi++; i++; }
        else if (i < n.length() && n.charAt(i) == 'b' && i + 1 < n.length()) { semi--; i++; }
        int oct = Integer.parseInt(n.substring(i));
        return 12 * (oct + 1) + semi;
    }

    static float freq(float m) { return (float) (440.0 * Math.pow(2, (m - 69) / 12.0)); }

    /** Root semitone (0..11) + chord intervals. */
    static int[] chord(String c) {
        int[] base = {9, 11, 0, 2, 4, 5, 7};
        int root = base[Character.toLowerCase(c.charAt(0)) - 'a'];
        int i = 1;
        if (i < c.length() && c.charAt(i) == '#') { root++; i++; }
        else if (i < c.length() && c.charAt(i) == 'b') { root--; i++; }
        String q = c.substring(i);
        int[] iv;
        switch (q) {
            case "m": iv = new int[]{0, 3, 7}; break;
            case "7": iv = new int[]{0, 4, 7, 10}; break;
            case "m7": iv = new int[]{0, 3, 7, 10}; break;
            case "maj7": iv = new int[]{0, 4, 7, 11}; break;
            case "m9": iv = new int[]{0, 3, 7, 10, 14}; break;
            case "13": iv = new int[]{0, 4, 10, 14, 21}; break;
            default: iv = new int[]{0, 4, 7}; break;
        }
        int[] out = new int[iv.length + 1];
        out[0] = (root + 12) % 12;
        System.arraycopy(iv, 0, out, 1, iv.length);
        return out;
    }

    // ============================================================================ rendering ===

    private static float[] buf;   // current target buffer (wraps)
    private static int len;
    private static long seed = 12345;

    private static float noise() {
        seed = seed * 6364136223846793005L + 1442695040888963407L;
        return ((seed >>> 33) / (float) (1L << 31)) * 2f - 1f;
    }

    private static float blep(float t, float dt) {
        if (t < dt) { t /= dt; return t + t - t * t - 1f; }
        if (t > 1f - dt) { t = (t - 1f) / dt; return t * t + t + t + 1f; }
        return 0f;
    }

    /** One synthesized note added into buf (wrapping). wave: 0 pulse, 1 saw, 2 triangle, 3 sine. */
    private static void note(int start, float durSec, float f, float vol, int wave, float duty,
                             float a, float d, float sus, float rel, float cutoff, float vib, float detune) {
        int n = (int) ((durSec + rel) * RATE);
        float ph = 0f, ph2 = 0.37f, lp = 0f, lp2 = 0f, tri = 0f;
        float kc = (float) (1 - Math.exp(-2 * Math.PI * cutoff / RATE));
        for (int i = 0; i < n; i++) {
            float t = i / (float) RATE;
            float env;
            if (t < a) env = t / a;
            else if (t < a + d) env = 1f - (1f - sus) * (t - a) / d;
            else env = sus;
            if (t > durSec) env *= Math.max(0f, 1f - (t - durSec) / rel);
            float ff = f;
            if (vib > 0 && t > 0.18f) ff *= 1f + vib * (float) Math.sin(2 * Math.PI * 5.5 * t) * Math.min(1f, (t - 0.18f) * 4f);
            float dt = ff / RATE;
            float v;
            switch (wave) {
                case 0: {
                    float sq = ph < duty ? 1f : -1f;
                    sq += blep(ph, dt);
                    float p2 = ph - duty; if (p2 < 0) p2 += 1f;
                    sq -= blep(p2, dt);
                    v = sq;
                    break;
                }
                case 1: {
                    v = 2f * ph - 1f - blep(ph, dt);
                    if (detune != 0f) {
                        float dt2 = dt * (1f + detune);
                        v += 2f * ph2 - 1f - blep(ph2, dt2);
                        v *= 0.5f;
                        ph2 += dt2; if (ph2 >= 1f) ph2 -= 1f;
                    }
                    break;
                }
                case 2: {
                    // integrated band-limited square -> triangle
                    float sq = ph < 0.5f ? 1f : -1f;
                    sq += blep(ph, dt);
                    float p2 = ph - 0.5f; if (p2 < 0) p2 += 1f;
                    sq -= blep(p2, dt);
                    tri = tri * 0.999f + sq * dt * 4f;
                    v = tri;
                    break;
                }
                default:
                    v = (float) Math.sin(2 * Math.PI * ph);
            }
            ph += dt; if (ph >= 1f) ph -= 1f;
            lp += (v - lp) * kc;
            lp2 += (lp - lp2) * kc;
            int idx = (start + i) % len;
            if (idx < 0) idx += len;
            buf[idx] += lp2 * env * vol;
        }
    }

    private static void kick(int start, float vol) {
        int n = (int) (0.35f * RATE);
        double ph = 0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) RATE;
            float f = 45f + 110f * (float) Math.exp(-t * 28f);
            ph += 2 * Math.PI * f / RATE;
            float v = (float) Math.sin(ph) * (float) Math.exp(-t * 9f);
            if (i < 60) v += noise() * 0.3f * (1f - i / 60f);
            buf[(start + i) % len] += v * vol;
        }
    }

    private static void snare(int start, float vol) {
        int n = (int) (0.22f * RATE);
        float hp = 0f, prev = 0f;
        double ph = 0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) RATE;
            float w = noise();
            hp = 0.85f * (hp + w - prev); prev = w;
            ph += 2 * Math.PI * 190 / RATE;
            float v = hp * (float) Math.exp(-t * 18f) * 0.8f + (float) Math.sin(ph) * (float) Math.exp(-t * 30f) * 0.5f;
            buf[(start + i) % len] += v * vol;
        }
    }

    private static void hat(int start, float vol, boolean open) {
        int n = (int) ((open ? 0.25f : 0.05f) * RATE);
        float hp = 0f, prev = 0f;
        for (int i = 0; i < n; i++) {
            float t = i / (float) RATE;
            float w = noise();
            hp = 0.6f * (hp + w - prev); prev = w;
            buf[(start + i) % len] += hp * (float) Math.exp(-t * (open ? 12f : 70f)) * vol;
        }
    }

    /** Echo (feedback delay); runs two passes so the loop point is seamless. */
    private static void echo(float[] b, float delaySec, float fb, float mix) {
        int d = (int) (delaySec * RATE);
        float[] line = new float[d];
        float[] out = new float[b.length];
        int p = 0;
        for (int pass = 0; pass < 2; pass++)
            for (int i = 0; i < b.length; i++) {
                float y = line[p];
                line[p] = b[i] + y * fb;
                p = (p + 1) % d;
                if (pass == 1) out[i] = b[i] + y * mix;
            }
        System.arraycopy(out, 0, b, 0, b.length);
    }

    /** Small Schroeder reverb (4 combs + 2 allpasses), two passes for a seamless loop. */
    private static void reverb(float[] b, float mix) {
        int[] cl = {1116, 1188, 1277, 1356};
        int[] al = {556, 441};
        float[][] combs = new float[4][];
        float[] cf = new float[4];
        int[] cp = new int[4];
        for (int i = 0; i < 4; i++) combs[i] = new float[cl[i] * RATE / 44100];
        float[][] alls = new float[2][];
        int[] ap = new int[2];
        for (int i = 0; i < 2; i++) alls[i] = new float[al[i] * RATE / 44100];
        float[] out = new float[b.length];
        for (int pass = 0; pass < 2; pass++)
            for (int i = 0; i < b.length; i++) {
                float in = b[i] * 0.3f, acc = 0f;
                for (int c = 0; c < 4; c++) {
                    float y = combs[c][cp[c]];
                    cf[c] = y * 0.8f + cf[c] * 0.2f;
                    combs[c][cp[c]] = in + cf[c] * 0.82f;
                    cp[c] = (cp[c] + 1) % combs[c].length;
                    acc += y;
                }
                for (int a = 0; a < 2; a++) {
                    float y = alls[a][ap[a]];
                    float x = acc + y * 0.5f;
                    alls[a][ap[a]] = x;
                    ap[a] = (ap[a] + 1) % alls[a].length;
                    acc = y - x * 0.5f;
                }
                if (pass == 1) out[i] = b[i] + acc * mix;
            }
        System.arraycopy(out, 0, b, 0, b.length);
    }

    private static int[] parseMelody(String m, int[] lens) {
        String[] tok = m.replace("|", " ").trim().split("\\s+");
        int[] notes = new int[tok.length];
        for (int i = 0; i < tok.length; i++) {
            String[] p = tok[i].split("/");
            notes[i] = p[0].equals("r") ? -1 : midi(p[0]);
            lens[i] = Integer.parseInt(p[1]);
        }
        return notes;
    }

    static Track render(int id) {
        Song s = song(id);
        int bars = 0;
        for (Section sec : s.sections) bars += sec.chords.length;
        float step = 60f / s.bpm / 4f;
        int stepS = (int) (step * RATE);
        len = bars * 16 * stepS;
        float[] base = new float[len], act = s.stems ? new float[len] : base;
        float[] leadBus = new float[len], padBus = new float[len];
        seed = 777 + id;

        int bar0 = 0;
        for (Section sec : s.sections) {
            // ---- lead melody
            if (sec.lead) {
                buf = leadBus;
                int[] lens = new int[256];
                int[] notes = parseMelody(sec.melody, lens);
                int pos = bar0 * 16;
                for (int i = 0; i < notes.length; i++) {
                    if (notes[i] >= 0) {
                        float accent = pos % 4 == 0 ? 1f : 0.85f;
                        float dur = lens[i] * step * 0.92f;
                        if (s.leadWave == 1)
                            note(pos * stepS, dur, freq(notes[i]), s.leadVol * accent, 1, 0, 0.01f, 0.1f, 0.7f, 0.12f, 3200, 0.004f, 0.006f);
                        else if (s.leadWave == 2)
                            note(pos * stepS, dur, freq(notes[i]), s.leadVol * accent, 2, 0, 0.015f, 0.2f, 0.55f, 0.2f, 2600, 0.005f, 0);
                        else
                            note(pos * stepS, dur, freq(notes[i]), s.leadVol * accent, 0, 0.25f, 0.008f, 0.12f, 0.6f, 0.12f, 3600, 0.004f, 0);
                    }
                    pos += lens[i];
                }
            }
            for (int bi = 0; bi < sec.chords.length; bi++) {
                int bar = bar0 + bi;
                int[] ch = chord(sec.chords[bi]);
                int root = ch[0];
                int barStart = bar * 16 * stepS;
                // ---- pad: chord voiced around C4
                if (sec.pad) {
                    buf = padBus;
                    for (int k = 1; k < Math.min(ch.length, 5); k++) {
                        int m = 48 + root + ch[k];
                        while (m > 67) m -= 12;
                        while (m < 52) m += 12;
                        note(barStart, 16 * step, freq(m), s.padVol / 3f, 1, 0, 0.35f, 0.4f, 0.8f, 0.5f, 1300, 0, 0.004f);
                    }
                }
                // ---- bass
                if (sec.bass) {
                    buf = base;
                    String pat = s.bassPat;
                    for (int st = 0; st < 16; st++) {
                        char c = pat.charAt(st);
                        if (c == '.' || c == '-') continue;
                        int hold = 1;
                        while (st + hold < 16 && pat.charAt(st + hold) == '-') hold++;
                        int m = 33 + root + (c == 'o' ? 12 : (c == '5' ? 7 : 0));
                        if (m < 35) m += 12;
                        note(barStart + st * stepS, hold * step * 0.9f, freq(m), s.bassVol, 2, 0, 0.005f, 0.15f, 0.7f, 0.06f, 900, 0, 0);
                        note(barStart + st * stepS, hold * step * 0.9f, freq(m - 12), s.bassVol * 0.6f, 3, 0, 0.005f, 0.15f, 0.7f, 0.06f, 400, 0, 0);
                    }
                }
                // ---- arpeggio (16ths through the chord, an octave up)
                if (sec.arp) {
                    buf = base;
                    int[] order = {1, 2, 3, 2};
                    for (int st = 0; st < 16; st++) {
                        int k = order[st % 4];
                        if (k >= ch.length) k = 1;
                        int m = 60 + root + ch[k];
                        while (m > 79) m -= 12;
                        int sw = s.swing && (st & 1) == 1 ? (int) (stepS * 0.16f) : 0;
                        note(barStart + st * stepS + sw, step * 0.5f, freq(m), s.arpVol * (st % 4 == 0 ? 1f : 0.7f), 0, 0.125f, 0.003f, 0.08f, 0.2f, 0.05f, 2400, 0, 0);
                    }
                }
                // ---- drums
                for (int st = 0; st < 16; st++) {
                    int at = barStart + st * stepS + (s.swing && (st & 1) == 1 ? (int) (stepS * 0.16f) : 0);
                    String dr = sec.drums;
                    if (dr.equals("none")) continue;
                    buf = s.stems ? act : base;
                    float dv = s.drumVol;
                    if (dr.equals("light")) {
                        if (st == 0 || st == 10) kick(at, dv * 0.8f);
                        buf = base;
                        if ((st & 1) == 0) hat(at, dv * 0.18f, false);
                    } else if (dr.equals("full")) {
                        if (st == 0 || st == 7 || st == 10) kick(at, dv);
                        if (st == 4 || st == 12) snare(at, dv * 0.7f);
                        buf = base;
                        if ((st & 1) == 0) hat(at, dv * 0.2f, st == 14);
                        else hat(at, dv * 0.08f, false);
                    } else {   // drive
                        if (st % 4 == 0) kick(at, dv);
                        if (st == 4 || st == 12 || (bi % 4 == 3 && st >= 13)) snare(at, dv * 0.7f);
                        hat(at, dv * 0.16f, (st & 3) == 2);
                    }
                }
            }
            bar0 += sec.chords.length;
        }
        // effects
        echo(leadBus, step * 3, 0.32f, 0.3f);
        reverb(leadBus, 0.35f);
        reverb(padBus, 0.45f);
        float[] leadDest = s.stems ? act : base;
        for (int i = 0; i < len; i++) { leadDest[i] += leadBus[i]; base[i] += padBus[i]; }
        short[][] stems = s.stems ? new short[][]{master(base, act, false), master(act, base, true)} : new short[][]{master(base, null, false)};
        buf = null;
        return new Track(stems);
    }

    /** Soft-clips and scales to 16 bit; the gain is chosen from the full mix so stems stay balanced. */
    private static short[] master(float[] b, float[] other, boolean isLayer) {
        float peak = 1e-6f;
        for (int i = 0; i < b.length; i++) {
            float v = Math.abs(b[i] + (other != null ? other[i] : 0f));
            if (v > peak) peak = v;
        }
        float g = (other != null ? 0.82f : 0.9f) / Math.max(0.6f, peak);
        short[] out = new short[b.length];
        for (int i = 0; i < b.length; i++) {
            float v = b[i] * g;
            v = (float) Math.tanh(v * 1.1f) / (float) Math.tanh(1.1f);
            out[i] = (short) (Math.max(-1f, Math.min(1f, v)) * 30000);
        }
        return out;
    }
}

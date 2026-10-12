package com.frameboost.ragdoll;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import java.util.Random;

/** Tiny synthesized sound effects (no audio assets needed). */
final class Sfx {
    private static final int RATE = 22050;
    boolean enabled = true;
    float volume = 0.9f;

    private final Clip boom = new Clip(), shot = new Clip(), thud = new Clip(), splat = new Clip(),
            pop = new Clip(), zap = new Clip(), launch = new Clip(), click = new Clip();
    private long lastBoom, lastShot, lastThud, lastSplat, lastMisc;

    private static final class Clip {
        AudioTrack[] t = new AudioTrack[3];
        int n;
        int i;
        float master = 1f;
        void play(float vol) {
            AudioTrack a = t[i];
            i = (i + 1) % t.length;
            if (a == null) return;
            try {
                a.stop();
                a.setPlaybackHeadPosition(0);
                a.setStereoVolume(vol * master, vol * master);
                a.play();
            } catch (Throwable ignored) { }
        }
    }

    Sfx() {
        try {
            Random r = new Random(7);
            build(boom, noise(r, 0.9f, 1.0f, 9f, 170f, 0.55f));
            build(shot, noise(r, 0.16f, 1.0f, 28f, 900f, 0.85f));
            build(thud, noise(r, 0.12f, 0.8f, 30f, 110f, 0.2f));
            build(splat, noise(r, 0.14f, 0.7f, 24f, 600f, 0.1f));
            build(pop, noise(r, 0.07f, 0.9f, 55f, 1500f, 0.6f));
            build(zap, noise(r, 0.35f, 0.9f, 8f, 2400f, 0.95f));
            build(launch, noise(r, 0.4f, 0.7f, 5f, 700f, 0.7f));
            build(click, noise(r, 0.03f, 0.5f, 90f, 2600f, 0.1f));
        } catch (Throwable t) {
            enabled = false;
        }
    }

    /** Decaying noise burst with a low-pass tone body. */
    private static short[] noise(Random r, float secs, float amp, float decay, float freq, float noiseMix) {
        int n = (int) (secs * RATE);
        short[] out = new short[n];
        float lp = 0f;
        double ph = 0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) RATE;
            float env = (float) Math.exp(-decay * t);
            float white = r.nextFloat() * 2f - 1f;
            float k = Math.min(0.95f, 0.05f + freq / 9000f);
            lp += (white - lp) * k;
            ph += 2 * Math.PI * (freq * 0.25f * (1f - t * 0.6f)) / RATE;
            float tone = (float) Math.sin(ph);
            float v = (lp * noiseMix + tone * (1f - noiseMix)) * env * amp;
            if (v > 1f) v = 1f; else if (v < -1f) v = -1f;
            out[i] = (short) (v * 30000);
        }
        return out;
    }

    private void build(Clip c, short[] data) {
        for (int k = 0; k < c.t.length; k++) {
            AudioTrack a = new AudioTrack(AudioManager.STREAM_MUSIC, RATE, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, data.length * 2, AudioTrack.MODE_STATIC);
            a.write(data, 0, data.length);
            c.t[k] = a;
        }
    }

    private static long now() { return System.currentTimeMillis(); }

    void boom(float size) {
        vol();
        if (!enabled || now() - lastBoom < 70) return;
        lastBoom = now();
        boom.play(0.5f + 0.5f * size);
    }

    void shot() {
        if (!enabled || now() - lastShot < 50) return;
        lastShot = now();
        shot.play(0.8f);
    }

    void thud(float v) {
        if (!enabled || now() - lastThud < 90) return;
        lastThud = now();
        thud.play(0.25f + 0.5f * v);
    }

    void splat() {
        if (!enabled || now() - lastSplat < 80) return;
        lastSplat = now();
        splat.play(0.6f);
    }

    private void vol() {
        Clip[] all = {boom, shot, thud, splat, pop, zap, launch, click};
        for (Clip c : all) c.master = volume;
    }

    void click() { vol(); if (enabled) click.play(0.5f); }

    void pop() { if (enabled && now() - lastMisc > 40) { lastMisc = now(); pop.play(0.7f); } }
    void zap() { if (enabled && now() - lastMisc > 40) { lastMisc = now(); zap.play(0.8f); } }
    void launch() { if (enabled && now() - lastMisc > 40) { lastMisc = now(); launch.play(0.7f); } }

    void release() {
        Clip[] all = {boom, shot, thud, splat, pop, zap, launch, click};
        for (Clip c : all) for (AudioTrack a : c.t) if (a != null) { try { a.release(); } catch (Throwable ignored) { } }
    }
}

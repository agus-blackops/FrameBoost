package com.frameboost.ragdoll;

import android.media.AudioAttributes;
import android.media.SoundPool;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Random;

/**
 * Synthesized sound effects played through a SoundPool (one shared mixer, no per-sound tracks).
 * The sounds are generated once as small WAV files in the cache folder.
 */
final class Sfx {
    private static final int RATE = 22050;
    boolean enabled = true;
    float volume = 0.9f;

    private SoundPool pool;
    private final int[] ids = new int[9];
    private static final int BOOM = 0, SHOT = 1, THUD = 2, SPLAT = 3, POP = 4, ZAP = 5, LAUNCH = 6, CLICK = 7, CRUNCH = 8;
    private final long[] last = new long[9];
    private final Random rnd = new Random();

    Sfx(File cacheDir) {
        try {
            pool = new SoundPool.Builder().setMaxStreams(12).setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
            Random r = new Random(7);
            short[][] data = {
                noise(r, 1.1f, 1.0f, 7f, 160f, 0.55f),
                noise(r, 0.18f, 1.0f, 26f, 1100f, 0.85f),
                noise(r, 0.14f, 0.9f, 28f, 120f, 0.15f),
                noise(r, 0.16f, 0.75f, 22f, 650f, 0.35f),
                noise(r, 0.08f, 0.9f, 55f, 1600f, 0.6f),
                noise(r, 0.4f, 0.9f, 8f, 2500f, 0.95f),
                noise(r, 0.5f, 0.7f, 4f, 700f, 0.7f),
                noise(r, 0.035f, 0.55f, 90f, 2600f, 0.1f),
                noise(r, 0.22f, 0.9f, 16f, 380f, 0.7f),
            };
            for (int i = 0; i < data.length; i++) {
                File f = new File(cacheDir, "sfx" + i + ".wav");
                writeWav(f, data[i]);
                ids[i] = pool.load(f.getAbsolutePath(), 1);
            }
        } catch (Throwable t) {
            pool = null;
        }
    }

    /** Decaying noise burst with a tonal body. */
    static short[] noise(Random r, float secs, float amp, float decay, float freq, float noiseMix) {
        int n = (int) (secs * RATE);
        short[] out = new short[n];
        float lp = 0f;
        double ph = 0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) RATE;
            float env = (float) Math.exp(-decay * t) * Math.min(1f, i / 40f);
            float white = r.nextFloat() * 2f - 1f;
            float k = Math.min(0.95f, 0.05f + freq / 9000f);
            lp += (white - lp) * k;
            ph += 2 * Math.PI * (freq * 0.25f * (1f - t * 0.6f)) / RATE;
            float v = (lp * noiseMix + (float) Math.sin(ph) * (1f - noiseMix)) * env * amp;
            out[i] = (short) (Math.max(-1f, Math.min(1f, v)) * 30000);
        }
        return out;
    }

    private static void writeWav(File f, short[] d) throws Exception {
        DataOutputStream o = new DataOutputStream(new FileOutputStream(f));
        int bytes = d.length * 2;
        o.writeBytes("RIFF"); o.writeInt(Integer.reverseBytes(36 + bytes)); o.writeBytes("WAVEfmt ");
        o.writeInt(Integer.reverseBytes(16)); o.writeShort(Short.reverseBytes((short) 1)); o.writeShort(Short.reverseBytes((short) 1));
        o.writeInt(Integer.reverseBytes(RATE)); o.writeInt(Integer.reverseBytes(RATE * 2));
        o.writeShort(Short.reverseBytes((short) 2)); o.writeShort(Short.reverseBytes((short) 16));
        o.writeBytes("data"); o.writeInt(Integer.reverseBytes(bytes));
        byte[] b = new byte[bytes];
        for (int i = 0; i < d.length; i++) { b[i * 2] = (byte) d[i]; b[i * 2 + 1] = (byte) (d[i] >> 8); }
        o.write(b);
        o.close();
    }

    private void play(int s, float vol, long minGapMs, float pitchVar) {
        if (!enabled || pool == null || ids[s] == 0) return;
        long now = System.currentTimeMillis();
        if (now - last[s] < minGapMs) return;
        last[s] = now;
        float v = Math.max(0f, Math.min(1f, vol * volume));
        float rate = 1f + (rnd.nextFloat() * 2f - 1f) * pitchVar;
        try { pool.play(ids[s], v, v, 1, 0, rate); } catch (Throwable ignored) { }
    }

    void boom(float size) { play(BOOM, 0.55f + 0.45f * size, 60, 0.12f); }
    void shot() { play(SHOT, 0.8f, 45, 0.08f); }
    void thud(float v) { play(THUD, 0.3f + 0.6f * v, 70, 0.2f); }
    void splat() { play(SPLAT, 0.65f, 70, 0.15f); }
    void crunch() { play(CRUNCH, 0.7f, 80, 0.15f); }
    void pop() { play(POP, 0.7f, 40, 0.15f); }
    void zap() { play(ZAP, 0.8f, 60, 0.1f); }
    void launch() { play(LAUNCH, 0.7f, 60, 0.05f); }
    void click() { play(CLICK, 0.45f, 30, 0.03f); }

    void release() { if (pool != null) { try { pool.release(); } catch (Throwable ignored) { } pool = null; } }
}

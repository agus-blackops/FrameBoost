package com.frameboost.ragdoll;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

/**
 * Streams the rendered soundtrack: renders tracks in the background, crossfades between them
 * and mixes the "action" layer of the sandbox theme according to how much is going on.
 */
final class MusicPlayer implements Runnable {
    private final Music.Track[] tracks = new Music.Track[Music.COUNT];
    private volatile int want = Music.TITLE;
    private volatile boolean running, paused;
    volatile float volume = 0.7f;
    private volatile float intensityTarget;
    private Thread thread;
    private final java.io.File cacheDir;
    private static final int CACHE_VERSION = 2;   // bump when the compositions change

    MusicPlayer(java.io.File cacheDir) { this.cacheDir = cacheDir; }

    void start() {
        if (running) return;
        running = true;
        thread = new Thread(this, "music");
        thread.setPriority(Thread.MAX_PRIORITY);
        thread.start();
        // render the rest after the title theme so music starts quickly
        new Thread(new Runnable() {
            @Override public void run() {
                int[] order = {Music.TITLE, Music.LAB, Music.CHALLENGE};
                for (int id : order) {
                    try { Music.Track t = render(id); synchronized (tracks) { tracks[id] = t; } } catch (Throwable ignored) { }
                }
            }
        }, "music-render").start();
    }

    /** Renders a track once and keeps it in the cache folder, so later launches start instantly. */
    private synchronized Music.Track render(int id) {
        java.io.File f = cacheDir == null ? null : new java.io.File(cacheDir, "music" + CACHE_VERSION + "_" + id + ".pcm");
        if (f != null && f.exists()) {
            try {
                java.io.DataInputStream in = new java.io.DataInputStream(new java.io.BufferedInputStream(new java.io.FileInputStream(f), 65536));
                int stems = in.readInt(), len = in.readInt();
                short[][] s = new short[stems][len];
                byte[] buf = new byte[len * 2];
                for (int k = 0; k < stems; k++) {
                    in.readFully(buf);
                    for (int i = 0; i < len; i++) s[k][i] = (short) ((buf[i * 2] & 0xFF) | (buf[i * 2 + 1] << 8));
                }
                in.close();
                return new Music.Track(s);
            } catch (Throwable ignored) { f.delete(); }
        }
        Music.Track t = Music.render(id);
        if (f != null) {
            try {
                java.io.DataOutputStream out = new java.io.DataOutputStream(new java.io.BufferedOutputStream(new java.io.FileOutputStream(f), 65536));
                out.writeInt(t.stems.length); out.writeInt(t.length);
                byte[] buf = new byte[t.length * 2];
                for (short[] st : t.stems) {
                    for (int i = 0; i < t.length; i++) { buf[i * 2] = (byte) st[i]; buf[i * 2 + 1] = (byte) (st[i] >> 8); }
                    out.write(buf);
                }
                out.close();
            } catch (Throwable ignored) { f.delete(); }
        }
        return t;
    }

    void play(int id) { want = id; }
    void setPaused(boolean p) { paused = p; }
    /** 0..1: how much action there is (explosions, fights). */
    void excite(float amount) { intensityTarget = Math.min(1f, Math.max(intensityTarget, amount)); }
    void stop() { running = false; }

    @Override public void run() {
        int minBuf = AudioTrack.getMinBufferSize(Music.RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        AudioTrack at;
        try {
            at = new AudioTrack(AudioManager.STREAM_MUSIC, Music.RATE, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, Math.max(minBuf, 8192) * 2, AudioTrack.MODE_STREAM);
            at.play();
        } catch (Throwable t) { running = false; return; }
        final int chunk = 1024;
        short[] out = new short[chunk];
        int cur = -1, pos = 0, prev = -1, prevPos = 0;
        float fade = 1f, intensity = 0f, vol = 0f;
        while (running) {
            if (paused) {
                try { at.pause(); while (paused && running) Thread.sleep(80); at.play(); } catch (Throwable ignored) { }
                continue;
            }
            int w = want;
            Music.Track tw;
            synchronized (tracks) { tw = tracks[w]; }
            if (w != cur && tw != null) {
                prev = cur; prevPos = pos; cur = w; pos = 0; fade = 0f;
            }
            Music.Track tc, tp;
            synchronized (tracks) { tc = cur >= 0 ? tracks[cur] : null; tp = prev >= 0 ? tracks[prev] : null; }
            for (int i = 0; i < chunk; i++) {
                if ((i & 63) == 0) {
                    intensityTarget = Math.max(0f, intensityTarget - 0.0009f);
                    intensity += (intensityTarget - intensity) * 0.02f;
                    vol += (volume - vol) * 0.02f;
                    if (fade < 1f) fade = Math.min(1f, fade + 0.0042f);
                }
                float v = 0f;
                if (tc != null) {
                    v = tc.stems[0][pos];
                    if (tc.stems.length > 1) v += tc.stems[1][pos] * (0.25f + 0.75f * intensity);
                    v *= fade;
                    pos = (pos + 1) % tc.length;
                }
                if (tp != null && fade < 1f) {
                    float pv = tp.stems[0][prevPos];
                    if (tp.stems.length > 1) pv += tp.stems[1][prevPos] * 0.5f;
                    v += pv * (1f - fade);
                    prevPos = (prevPos + 1) % tp.length;
                }
                v *= vol;
                out[i] = (short) Math.max(-32767, Math.min(32767, v));
            }
            try { at.write(out, 0, chunk); } catch (Throwable t) { break; }
        }
        try { at.stop(); at.release(); } catch (Throwable ignored) { }
    }
}

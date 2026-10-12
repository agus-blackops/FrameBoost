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

    private static synchronized Music.Track render(int id) { return Music.render(id); }

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

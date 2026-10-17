package android.media;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

/**
 * Desktop version of SoundPool: loads the game's 16-bit mono WAV files and mixes the playing voices
 * (with volume and playback rate) into one audio line on a background thread.
 */
public class SoundPool implements Runnable {
    public static class Builder {
        private int max = 12;
        public Builder setMaxStreams(int n) { max = n; return this; }
        public Builder setAudioAttributes(AudioAttributes a) { return this; }
        public SoundPool build() { return new SoundPool(max); }
    }

    private static final int RATE = 22050;
    private final ArrayList<short[]> samples = new ArrayList<>();
    private final ArrayList<float[]> voices = new ArrayList<>();      // {sample id, position, rate, volume}
    private final int max;
    private volatile boolean running = true;
    private SourceDataLine line;

    SoundPool(int max) {
        this.max = max;
        try {
            AudioFormat f = new AudioFormat(RATE, 16, 1, true, false);
            line = AudioSystem.getSourceDataLine(f);
            line.open(f, 2048 * 2);
            line.start();
            Thread t = new Thread(this, "sfx-mixer");
            t.setDaemon(true);
            t.start();
        } catch (Throwable e) { line = null; }
    }

    public int load(String path, int prio) {
        try {
            File f = new File(path);
            byte[] b = new byte[(int) f.length()];
            DataInputStream in = new DataInputStream(new FileInputStream(f));
            in.readFully(b);
            in.close();
            int n = (b.length - 44) / 2;
            short[] s = new short[Math.max(0, n)];
            for (int i = 0; i < n; i++) s[i] = (short) ((b[44 + i * 2] & 0xFF) | (b[45 + i * 2] << 8));
            synchronized (samples) { samples.add(s); return samples.size(); }
        } catch (Throwable e) { return 0; }
    }

    public int play(int id, float l, float r, int prio, int loop, float rate) {
        if (line == null || id <= 0) return 0;
        synchronized (voices) {
            if (voices.size() >= max) voices.remove(0);
            voices.add(new float[]{id - 1, 0f, rate, (l + r) * 0.5f});
        }
        return 1;
    }

    @Override public void run() {
        final int n = 512;
        float[] mix = new float[n];
        byte[] out = new byte[n * 2];
        while (running) {
            java.util.Arrays.fill(mix, 0f);
            synchronized (voices) {
                for (int v = voices.size() - 1; v >= 0; v--) {
                    float[] vo = voices.get(v);
                    short[] s;
                    synchronized (samples) { s = samples.get((int) vo[0]); }
                    float pos = vo[1], rate = vo[2], vol = vo[3];
                    for (int i = 0; i < n; i++) {
                        int p0 = (int) pos;
                        if (p0 + 1 >= s.length) { pos = s.length; break; }
                        float t = pos - p0;
                        mix[i] += (s[p0] * (1f - t) + s[p0 + 1] * t) * vol;
                        pos += rate;
                    }
                    vo[1] = pos;
                    if (pos >= s.length - 1) voices.remove(v);
                }
            }
            for (int i = 0; i < n; i++) {
                float x = mix[i];
                if (x > 32767f) x = 32767f; else if (x < -32768f) x = -32768f;
                int v = (int) x;
                out[i * 2] = (byte) v; out[i * 2 + 1] = (byte) (v >> 8);
            }
            line.write(out, 0, out.length);      // blocks at the audio rate
        }
    }

    public void release() { running = false; if (line != null) { line.stop(); line.close(); } }
}

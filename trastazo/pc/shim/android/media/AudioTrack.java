package android.media;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

/** Desktop version of AudioTrack (16-bit mono streaming) on a Java Sound line. */
public class AudioTrack {
    public static final int MODE_STATIC = 0, MODE_STREAM = 1;
    public static int getMinBufferSize(int a, int b, int c) { return 4096; }
    private final SourceDataLine line;
    private float vol = 1f;
    private byte[] buf = new byte[0];

    public AudioTrack(int stream, int rate, int ch, int enc, int bufBytes, int mode) {
        try {
            AudioFormat f = new AudioFormat(rate, 16, 1, true, false);
            line = AudioSystem.getSourceDataLine(f);
            line.open(f, Math.max(bufBytes, 8192));
        } catch (Exception e) { throw new IllegalStateException("no audio output", e); }
    }

    public int write(short[] d, int o, int n) {
        if (buf.length < n * 2) buf = new byte[n * 2];
        for (int i = 0; i < n; i++) {
            int v = (int) (d[o + i] * vol);
            buf[i * 2] = (byte) v; buf[i * 2 + 1] = (byte) (v >> 8);
        }
        line.write(buf, 0, n * 2);
        return n;
    }

    public void play() { line.start(); }
    public void pause() { line.stop(); }
    public void stop() { line.stop(); line.flush(); }
    public void release() { line.close(); }
    public void setPlaybackHeadPosition(int p) {}
    public int setStereoVolume(float l, float r) { vol = Math.max(0f, Math.min(1f, (l + r) * 0.5f)); return 0; }
}

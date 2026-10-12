package com.frameboost.ragdoll;

import java.io.*;

public class MusicTest {
    static void wav(String path, short[] a, short[] b, float mixB) throws IOException {
        int n = a.length;
        DataOutputStream o = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(path)));
        int bytes = n * 2;
        o.writeBytes("RIFF"); o.writeInt(Integer.reverseBytes(36 + bytes)); o.writeBytes("WAVEfmt ");
        o.writeInt(Integer.reverseBytes(16)); o.writeShort(Short.reverseBytes((short) 1)); o.writeShort(Short.reverseBytes((short) 1));
        o.writeInt(Integer.reverseBytes(Music.RATE)); o.writeInt(Integer.reverseBytes(Music.RATE * 2));
        o.writeShort(Short.reverseBytes((short) 2)); o.writeShort(Short.reverseBytes((short) 16));
        o.writeBytes("data"); o.writeInt(Integer.reverseBytes(bytes));
        for (int i = 0; i < n; i++) {
            int v = a[i] + (b != null ? (int) (b[i] * mixB) : 0);
            v = Math.max(-32767, Math.min(32767, v));
            o.writeShort(Short.reverseBytes((short) v));
        }
        o.close();
    }

    public static void main(String[] args) throws Exception {
        String out = args[0];
        new File(out).mkdirs();
        for (int id = 0; id < Music.COUNT; id++) {
            long t0 = System.nanoTime();
            Music.Track t = Music.render(id);
            long t1 = System.nanoTime();
            short[] a = t.stems[0], b = t.stems.length > 1 ? t.stems[1] : null;
            int peak = 0; double rms = 0; int clip = 0;
            for (int i = 0; i < a.length; i++) {
                int v = a[i] + (b != null ? b[i] : 0);
                peak = Math.max(peak, Math.abs(v)); rms += (double) v * v;
                if (Math.abs(v) > 32767) clip++;
            }
            rms = Math.sqrt(rms / a.length);
            int seam = Math.abs(a[a.length - 1] - a[0]);
            System.out.printf("%-28s %.1fs  stems=%d  render=%.0f ms  peak=%d  rms=%.0f  overflowSamples=%d  seamJump=%d%n",
                    Music.NAMES[id], a.length / (float) Music.RATE, t.stems.length, (t1 - t0) / 1e6, peak, rms, clip, seam);
            String name = new String[]{"tema-principal", "laboratorio", "desafio"}[id];
            wav(out + "/" + name + ".wav", a, b, 1f);
            if (b != null) wav(out + "/" + name + "-calma.wav", a, null, 0f);
        }
    }
}

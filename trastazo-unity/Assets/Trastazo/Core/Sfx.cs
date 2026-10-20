using System;

namespace Trastazo
{
    /** Where the synthesized effects are played (Unity: AudioSource pool; tests: nothing). */
    public interface ISoundBackend
    {
        int load(short[] pcm, int rate);
        void play(int id, float volume, float rate);
        void release();
    }

    /**
     * Synthesized sound effects. The waveforms are generated once at start (same recipes as the Java/Android
     * version) and handed to the backend, which plays them with a volume and a playback rate.
     */
    public sealed class Sfx
    {
        public const int RATE = 22050;
        public bool enabled = true;
        public float volume = 0.9f;

        private ISoundBackend backend;
        private readonly int[] ids = new int[12];
        private const int BOOM = 0, SHOT = 1, THUD = 2, SPLAT = 3, POP = 4, ZAP = 5, LAUNCH = 6, CLICK = 7, CRUNCH = 8,
                BARK = 9, FART = 10, CHOMP = 11;
        private readonly long[] last = new long[12];
        private readonly Random rnd = new Random();

        public Sfx(ISoundBackend backend)
        {
            this.backend = backend;
            if (backend == null) return;
            try
            {
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
                    voice(0.2f, 520f, 300f),
                    fart(r, 0.75f),
                    noise(r, 0.09f, 0.8f, 40f, 900f, 0.8f),
                };
                for (int i = 0; i < data.Length; i++) ids[i] = backend.load(data[i], RATE) + 1;
            }
            catch (Exception) { this.backend = null; }
        }

        /** Decaying noise burst with a tonal body. */
        public static short[] noise(Random r, float secs, float amp, float decay, float freq, float noiseMix)
        {
            int n = (int)(secs * RATE);
            short[] outp = new short[n];
            float lp = 0f;
            double ph = 0;
            for (int i = 0; i < n; i++)
            {
                float t = i / (float)RATE;
                float env = (float)Math.exp(-decay * t) * Math.min(1f, i / 40f);
                float white = r.nextFloat() * 2f - 1f;
                float k = Math.min(0.95f, 0.05f + freq / 9000f);
                lp += (white - lp) * k;
                ph += 2 * Math.PI * (freq * 0.25f * (1f - t * 0.6f)) / RATE;
                float v = (lp * noiseMix + (float)Math.sin(ph) * (1f - noiseMix)) * env * amp;
                outp[i] = (short)(Math.max(-1f, Math.min(1f, v)) * 30000);
            }
            return outp;
        }

        /** A short animal call: a falling tone with a few harmonics (barks, oinks and squeaks by playback rate). */
        public static short[] voice(float secs, float f0, float f1)
        {
            int n = (int)(secs * RATE);
            short[] outp = new short[n];
            double ph = 0;
            for (int i = 0; i < n; i++)
            {
                float t = i / (float)n;
                float env = (float)Math.sin(Math.PI * Math.min(1f, t * 1.15f)) * (1f - t * 0.4f);
                ph += 2 * Math.PI * (f0 + (f1 - f0) * t) / RATE;
                float v = (float)(Math.sin(ph) * 0.6 + Math.sin(ph * 2) * 0.3 + Math.sin(ph * 3) * 0.18 + Math.signum(Math.sin(ph)) * 0.12) * env;
                outp[i] = (short)(Math.max(-1f, Math.min(1f, v * 0.8f)) * 30000);
            }
            return outp;
        }

        /** A low, fluttering raspberry. */
        public static short[] fart(Random r, float secs)
        {
            int n = (int)(secs * RATE);
            short[] outp = new short[n];
            double ph = 0;
            float lp = 0f;
            for (int i = 0; i < n; i++)
            {
                float t = i / (float)n;
                float env = Math.min(1f, t * 12f) * (1f - t) * (0.75f + 0.25f * (float)Math.sin(t * 60f));
                float f = 95f - 35f * t + 12f * (float)Math.sin(t * 23f);
                ph += 2 * Math.PI * f / RATE;
                float pulse = (float)(ph % (2 * Math.PI)) < 1.2f ? 1f : -0.35f;
                lp += ((r.nextFloat() * 2f - 1f) - lp) * 0.08f;
                float v = (pulse * 0.75f + lp * 0.6f) * env;
                outp[i] = (short)(Math.max(-1f, Math.min(1f, v * 0.7f)) * 30000);
            }
            return outp;
        }

        private void play(int s, float vol, long minGapMs, float pitchVar) { play(s, vol, minGapMs, pitchVar, 1f); }

        private void play(int s, float vol, long minGapMs, float pitchVar, float basePitch)
        {
            if (!enabled || backend == null || ids[s] == 0) return;
            long now = Sys.currentTimeMillis();
            if (now - last[s] < minGapMs) return;
            last[s] = now;
            float v = Math.max(0f, Math.min(1f, vol * volume));
            float rate = Math.max(0.5f, Math.min(2f, basePitch * (1f + (rnd.nextFloat() * 2f - 1f) * pitchVar)));
            try { backend.play(ids[s] - 1, v, rate); } catch (Exception) { }
        }

        public void boom(float size) { play(BOOM, 0.55f + 0.45f * size, 60, 0.12f); }
        public void shot() { play(SHOT, 0.8f, 45, 0.08f); }
        public void thud(float v) { play(THUD, 0.3f + 0.6f * v, 70, 0.2f); }
        public void splat() { play(SPLAT, 0.65f, 70, 0.15f); }
        public void crunch() { play(CRUNCH, 0.7f, 80, 0.15f); }
        public void pop() { play(POP, 0.7f, 40, 0.15f); }
        public void zap() { play(ZAP, 0.8f, 60, 0.1f); }
        public void launch() { play(LAUNCH, 0.7f, 60, 0.05f); }
        public void click() { play(CLICK, 0.45f, 30, 0.03f); }
        /** Animal call at a pitch (1 = dog, lower = pig, higher = rat). */
        public void call(float pitch) { play(BARK, 0.7f, 120, 0.05f, pitch); }
        public void fart() { play(FART, 0.85f, 300, 0.12f); }
        public void chomp() { play(CHOMP, 0.6f, 90, 0.2f); }

        public void release() { if (backend != null) { try { backend.release(); } catch (Exception) { } backend = null; } }
    }
}

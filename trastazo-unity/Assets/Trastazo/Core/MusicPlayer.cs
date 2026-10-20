using System;
using System.Threading;

namespace Trastazo
{
    /**
     * The soundtrack mixer: renders the tracks in the background (caching them on disk), crossfades between
     * them and mixes the "action" stem according to how much is going on. The platform pulls mono samples
     * with fill() from its audio thread (Unity: AudioClip with a PCM reader callback).
     */
    public sealed class MusicPlayer
    {
        public static int SampleRate { get { return Music.RATE; } }
        private readonly Music.Track[] tracks = new Music.Track[Music.COUNT];
        private volatile int want = Music.TITLE;
        private volatile bool running, paused;
        public volatile float volume = 0.7f;
        private volatile float intensityTarget;
        private readonly File cacheDir;
        private const int CACHE_VERSION = 2;   // bump when the compositions change
        private int cur = -1, pos, prev = -1, prevPos;
        private float fade = 1f, intensity, vol;
        private int tick;

        public MusicPlayer(File cacheDir) { this.cacheDir = cacheDir; }

        public void start()
        {
            if (running) return;
            running = true;
            // render after the title theme so music starts quickly
            Thread t = new Thread(() =>
            {
                int[] order = { Music.TITLE, Music.LAB, Music.CHALLENGE };
                foreach (int id in order)
                {
                    if (!running) return;
                    try { Music.Track tr = render(id); lock (tracks) { tracks[id] = tr; } } catch (Exception) { }
                }
            });
            t.IsBackground = true;
            t.Name = "music-render";
            t.Start();
        }

        /** Renders a track once and keeps it in the cache folder, so later launches start instantly. */
        private Music.Track render(int id)
        {
            string path = cacheDir == null ? null : System.IO.Path.Combine(cacheDir.getPath(), "music" + CACHE_VERSION + "_" + id + ".pcm");
            if (path != null && System.IO.File.Exists(path))
            {
                try
                {
                    using (System.IO.BinaryReader r = new System.IO.BinaryReader(System.IO.File.OpenRead(path)))
                    {
                        int stems = r.ReadInt32(), len = r.ReadInt32();
                        short[][] s = new short[stems][];
                        for (int k = 0; k < stems; k++)
                        {
                            byte[] buf = r.ReadBytes(len * 2);
                            if (buf.Length != len * 2) throw new System.IO.IOException("short cache file");
                            s[k] = new short[len];
                            Buffer.BlockCopy(buf, 0, s[k], 0, buf.Length);
                        }
                        return new Music.Track(s);
                    }
                }
                catch (Exception) { try { System.IO.File.Delete(path); } catch (Exception) { } }
            }
            Music.Track t = Music.render(id);
            if (path != null)
            {
                try
                {
                    using (System.IO.BinaryWriter w = new System.IO.BinaryWriter(System.IO.File.Create(path)))
                    {
                        w.Write(t.stems.Length); w.Write(t.Length);
                        foreach (short[] st in t.stems)
                        {
                            byte[] buf = new byte[t.Length * 2];
                            Buffer.BlockCopy(st, 0, buf, 0, buf.Length);
                            w.Write(buf);
                        }
                    }
                }
                catch (Exception) { try { System.IO.File.Delete(path); } catch (Exception) { } }
            }
            return t;
        }

        public void play(int id) { want = id; }
        public void setPaused(bool p) { paused = p; }
        /** 0..1: how much action there is (explosions, fights). */
        public void excite(float amount) { intensityTarget = Math.min(1f, Math.max(intensityTarget, amount)); }
        public void stop() { running = false; }

        /** Mixes the next n mono samples (-1..1) into buf. Called from the audio thread. */
        public void fill(float[] buf, int n)
        {
            if (paused || !running) { for (int i = 0; i < n; i++) buf[i] = 0f; return; }
            int w = want;
            Music.Track tw, tc, tp;
            lock (tracks)
            {
                tw = tracks[w];
                if (w != cur && tw != null) { prev = cur; prevPos = pos; cur = w; pos = 0; fade = 0f; }
                tc = cur >= 0 ? tracks[cur] : null;
                tp = prev >= 0 ? tracks[prev] : null;
            }
            for (int i = 0; i < n; i++)
            {
                if ((tick++ & 63) == 0)
                {
                    intensityTarget = Math.max(0f, intensityTarget - 0.0009f);
                    intensity += (intensityTarget - intensity) * 0.02f;
                    vol += (volume - vol) * 0.02f;
                    if (fade < 1f) fade = Math.min(1f, fade + 0.0042f);
                }
                float v = 0f;
                if (tc != null)
                {
                    v = tc.stems[0][pos];
                    if (tc.stems.Length > 1) v += tc.stems[1][pos] * (0.25f + 0.75f * intensity);
                    v *= fade;
                    pos = (pos + 1) % tc.Length;
                }
                if (tp != null && fade < 1f)
                {
                    float pv = tp.stems[0][prevPos];
                    if (tp.stems.Length > 1) pv += tp.stems[1][prevPos] * 0.5f;
                    v += pv * (1f - fade);
                    prevPos = (prevPos + 1) % tp.Length;
                }
                v *= vol;
                buf[i] = Math.max(-1f, Math.min(1f, v / 32767f));
            }
        }
    }
}

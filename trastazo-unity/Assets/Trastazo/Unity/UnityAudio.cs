using System.Collections.Generic;
using UnityEngine;

namespace TrastazoUnity
{
    /** Plays the synthesized effects through a small pool of AudioSources (volume and pitch per shot). */
    public sealed class UnitySoundBackend : Trastazo.ISoundBackend
    {
        private readonly List<AudioClip> clips = new List<AudioClip>();
        private readonly AudioSource[] voices;
        private int next;

        public UnitySoundBackend(GameObject host, int voiceCount)
        {
            voices = new AudioSource[voiceCount];
            for (int i = 0; i < voiceCount; i++)
            {
                GameObject g = new GameObject("sfx" + i);
                g.transform.SetParent(host.transform, false);
                AudioSource s = g.AddComponent<AudioSource>();
                s.playOnAwake = false;
                s.spatialBlend = 0f;
                voices[i] = s;
            }
        }

        public int load(short[] pcm, int rate)
        {
            float[] f = new float[pcm.Length];
            for (int i = 0; i < f.Length; i++) f[i] = pcm[i] / 32768f;
            AudioClip c = AudioClip.Create("sfx" + clips.Count, f.Length, 1, rate, false);
            c.SetData(f, 0);
            clips.Add(c);
            return clips.Count - 1;
        }

        public void play(int id, float volume, float rate)
        {
            if (id < 0 || id >= clips.Count) return;
            AudioSource s = null;
            for (int i = 0; i < voices.Length; i++)
            {
                AudioSource c = voices[(next + i) % voices.Length];
                if (!c.isPlaying) { s = c; next = (next + i + 1) % voices.Length; break; }
            }
            if (s == null) { s = voices[next]; next = (next + 1) % voices.Length; }   // steal the oldest voice
            s.clip = clips[id];
            s.volume = volume;
            s.pitch = rate;
            s.Play();
        }

        public void release()
        {
            for (int i = 0; i < voices.Length; i++) if (voices[i] != null) voices[i].Stop();
            for (int i = 0; i < clips.Count; i++) if (clips[i] != null) Object.Destroy(clips[i]);
            clips.Clear();
        }
    }

    /** The soundtrack: a streaming AudioClip whose samples come from Trastazo.MusicPlayer.fill on the audio thread. */
    public sealed class UnityMusic
    {
        private readonly Trastazo.MusicPlayer player;
        private readonly AudioSource source;
        private AudioClip clip;

        public UnityMusic(GameObject host, Trastazo.MusicPlayer player)
        {
            this.player = player;
            int rate = Trastazo.MusicPlayer.SampleRate;
            clip = AudioClip.Create("music", rate * 30, 1, rate, true, OnRead, OnSetPosition);
            source = host.AddComponent<AudioSource>();
            source.clip = clip;
            source.loop = true;
            source.spatialBlend = 0f;
            source.playOnAwake = false;
            source.volume = 1f;
            source.Play();
        }

        private void OnRead(float[] data) { player.fill(data, data.Length); }
        private void OnSetPosition(int position) { }

        public void Stop() { if (source != null) source.Stop(); }
    }
}

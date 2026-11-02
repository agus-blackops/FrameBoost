using System;
using System.Collections.Generic;
using Godot;

namespace TrastazoGodot
{
    /** Plays the synthesized effects through a small pool of AudioStreamPlayers (volume and pitch per shot). */
    public sealed class GodotSoundBackend : Trastazo.ISoundBackend
    {
        private readonly List<AudioStreamWav> clips = new List<AudioStreamWav>();
        private readonly AudioStreamPlayer[] voices;
        private int next;

        public GodotSoundBackend(Node host, int voiceCount)
        {
            voices = new AudioStreamPlayer[voiceCount];
            for (int i = 0; i < voiceCount; i++)
            {
                AudioStreamPlayer p = new AudioStreamPlayer();
                p.Name = "sfx" + i;
                host.AddChild(p);
                voices[i] = p;
            }
        }

        public int load(short[] pcm, int rate)
        {
            byte[] data = new byte[pcm.Length * 2];
            for (int i = 0; i < pcm.Length; i++)
            {
                data[i * 2] = (byte)(pcm[i] & 0xFF);
                data[i * 2 + 1] = (byte)((pcm[i] >> 8) & 0xFF);
            }
            AudioStreamWav w = new AudioStreamWav();
            w.Format = AudioStreamWav.FormatEnum.Format16Bits;
            w.MixRate = rate;
            w.Stereo = false;
            w.Data = data;
            clips.Add(w);
            return clips.Count - 1;
        }

        public void play(int id, float volume, float rate)
        {
            if (id < 0 || id >= clips.Count) return;
            AudioStreamPlayer s = null;
            for (int i = 0; i < voices.Length; i++)
            {
                AudioStreamPlayer c = voices[(next + i) % voices.Length];
                if (!c.Playing) { s = c; next = (next + i + 1) % voices.Length; break; }
            }
            if (s == null) { s = voices[next]; next = (next + 1) % voices.Length; }   // steal the oldest voice
            s.Stream = clips[id];
            s.VolumeDb = Mathf.LinearToDb(Math.Max(0.0001f, volume));
            s.PitchScale = Math.Max(0.05f, rate);
            s.Play();
        }

        public void release()
        {
            for (int i = 0; i < voices.Length; i++) if (voices[i] != null) voices[i].Stop();
            clips.Clear();
        }
    }

    /** The soundtrack: an AudioStreamGenerator topped up every frame with the samples of Trastazo.MusicPlayer.fill. */
    public sealed class GodotMusic
    {
        private readonly Trastazo.MusicPlayer player;
        private readonly AudioStreamPlayer source;
        private AudioStreamGeneratorPlayback playback;
        private float[] mono = new float[4096];
        private Vector2[] frames = new Vector2[4096];

        public GodotMusic(Node host, Trastazo.MusicPlayer player)
        {
            this.player = player;
            AudioStreamGenerator gen = new AudioStreamGenerator();
            gen.MixRate = Trastazo.MusicPlayer.SampleRate;
            gen.BufferLength = 0.4f;
            source = new AudioStreamPlayer();
            source.Name = "music";
            source.Stream = gen;
            host.AddChild(source);
            source.Play();
            playback = (AudioStreamGeneratorPlayback)source.GetStreamPlayback();
        }

        /** Call every frame (main thread): fills whatever room the generator has. */
        public void Pump()
        {
            if (playback == null) return;
            int room = playback.GetFramesAvailable();
            while (room > 0)
            {
                int n = Math.Min(room, mono.Length);
                player.fill(mono, n);
                if (frames.Length < n) frames = new Vector2[n];
                for (int i = 0; i < n; i++) frames[i] = new Vector2(mono[i], mono[i]);
                playback.PushBuffer(frames.AsSpan(0, n).ToArray());
                room -= n;
            }
        }

        public void Stop() { if (source != null) source.Stop(); }
    }
}

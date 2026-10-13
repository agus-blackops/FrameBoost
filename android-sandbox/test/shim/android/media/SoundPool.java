package android.media;
public class SoundPool {
    public static class Builder {
        public Builder setMaxStreams(int n) { return this; }
        public Builder setAudioAttributes(AudioAttributes a) { return this; }
        public SoundPool build() { return new SoundPool(); }
    }
    public int load(String path, int prio) { return 1; }
    public int play(int id, float l, float r, int prio, int loop, float rate) { return 1; }
    public void release() {}
}

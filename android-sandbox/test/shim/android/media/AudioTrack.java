package android.media;
public class AudioTrack {
    public static final int MODE_STATIC = 0;
    public AudioTrack(int a, int b, int c, int d, int e, int f) {}
    public int write(short[] d, int o, int n) { return n; }
    public void stop() {} public void play() {} public void release() {}
    public void setPlaybackHeadPosition(int p) {}
    public int setStereoVolume(float l, float r) { return 0; }
}

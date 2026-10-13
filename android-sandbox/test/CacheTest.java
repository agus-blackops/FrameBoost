package com.frameboost.ragdoll;
import java.io.File;
public class CacheTest { public static void main(String[] a) throws Exception {
    File dir = new File("/tmp/rpg-cache"); dir.mkdirs(); for (File f : dir.listFiles()) f.delete();
    MusicPlayer mp = new MusicPlayer(dir);
    java.lang.reflect.Method m = MusicPlayer.class.getDeclaredMethod("render", int.class); m.setAccessible(true);
    long t0 = System.nanoTime(); Music.Track a1 = (Music.Track) m.invoke(mp, 1); long t1 = System.nanoTime();
    Music.Track a2 = (Music.Track) m.invoke(mp, 1); long t2 = System.nanoTime();
    boolean same = a1.length == a2.length && a1.stems.length == a2.stems.length;
    for (int k = 0; same && k < a1.stems.length; k++) same = java.util.Arrays.equals(a1.stems[k], a2.stems[k]);
    System.out.printf("render %.0f ms, cached load %.0f ms, identical=%s%n", (t1 - t0) / 1e6, (t2 - t1) / 1e6, same);
}}

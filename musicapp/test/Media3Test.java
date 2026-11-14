package com.agus.sonora;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.net.Uri;
import android.os.Looper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The real Media3 player (not the simulator) playing real WAV files: it loads them, reports the
 * duration, advances, and moves into the preloaded next song on its own.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class Media3Test {

    Application app;

    @Before
    public void setUp() throws Exception {
        for (Class<?> k : new Class<?>[]{Library.class, PlayerEngine.class}) {
            java.lang.reflect.Field f = k.getDeclaredField("sInstance");
            f.setAccessible(true);
            f.set(null, null);
        }
        PlaybackService.running = false;
        Net.fetcher = new Fixtures();
        app = RuntimeEnvironment.getApplication();
        PlayerEngine.audioFactory = ExoAudio.FACTORY;
    }

    @After
    public void tearDown() {
        PlayerEngine.get(app).stop();
    }

    /** A mono 16-bit WAV with a tone. */
    static File wav(File dir, String name, double seconds, int freq) throws Exception {
        int rate = 8000;
        int n = (int) (rate * seconds);
        byte[] b = new byte[44 + n * 2];
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(b).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        bb.put("RIFF".getBytes()).putInt(36 + n * 2).put("WAVE".getBytes()).put("fmt ".getBytes())
                .putInt(16).putShort((short) 1).putShort((short) 1).putInt(rate).putInt(rate * 2)
                .putShort((short) 2).putShort((short) 16).put("data".getBytes()).putInt(n * 2);
        for (int i = 0; i < n; i++) bb.putShort((short) (Math.sin(2 * Math.PI * freq * i / rate) * 8000));
        File f = new File(dir, name);
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(b);
        }
        return f;
    }

    static Track local(File f, String title) {
        return new Track(Uri.fromFile(f).toString(), title, "Prueba", "Media3", 0, 0, false, 0);
    }

    /** Runs the main looper while Media3's own playback thread works in real time. */
    static void waitFor(String what, long ms, Check c) throws Exception {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20));
            if (c.ok()) return;
            Thread.sleep(20);
        }
        throw new AssertionError("timed out waiting for: " + what);
    }

    interface Check {
        boolean ok();
    }

    @Test
    public void realExoPlayerPlaysWavFilesAndMovesToTheNextOneOnItsOwn() throws Exception {
        File dir = app.getCacheDir();
        List<Track> tracks = new ArrayList<>();
        tracks.add(local(wav(dir, "a.wav", 2.0, 440), "Primera"));
        tracks.add(local(wav(dir, "b.wav", 3.0, 660), "Segunda"));
        final PlayerEngine p = PlayerEngine.get(app);
        // Robolectric's audio output takes the sound as fast as it is written, so the songs go by
        // faster than in real life: record what happens instead of looking at fixed moments.
        final List<String> seen = new ArrayList<>();
        final List<Integer> durations = new ArrayList<>();
        p.addListener(new PlayerEngine.Listener() {
            @Override
            public void onPlayerChanged() {
                Track t = p.current();
                String now = t == null ? "-" : t.title + (p.isActive() ? "" : " (pausa)");
                if (seen.isEmpty() || !seen.get(seen.size() - 1).equals(now)) seen.add(now);
                if (t != null && !p.isBuffering() && p.duration() > 0 && !durations.contains(p.duration())) durations.add(p.duration());
            }
        });
        p.playList(tracks, 0, "Prueba");

        waitFor("the queue to play through and rewind", 30000, new Check() {
            public boolean ok() {
                return seen.contains("Segunda") && "Primera (pausa)".equals(seen.get(seen.size() - 1));
            }
        });
        assertTrue("both songs played in order: " + seen, seen.indexOf("Primera") < seen.indexOf("Segunda"));
        assertEquals("Media3 moved into the preloaded song by itself", 1, p.gaplessTransitions);
        assertTrue("durations read from the WAV headers: " + durations, durations.contains(2000) && durations.contains(3000));
        assertEquals("at the end it rewinds to the first song, paused", 0, p.queueIndex());
        assertFalse(p.isPlaying());

        // seeking on the real player, paused and while the song is still loading
        p.seekTo(1500);
        waitFor("the seek", 5000, new Check() {
            public boolean ok() {
                return Math.abs(p.position() - 1500) < 100;
            }
        });
        assertEquals("Primera", p.current().title);
    }

    @Test
    public void aBrokenFileIsReportedAndSkipped() throws Exception {
        File dir = app.getCacheDir();
        File bad = new File(dir, "roto.wav");
        try (FileOutputStream out = new FileOutputStream(bad)) {
            out.write("esto no es audio".getBytes());
        }
        List<Track> tracks = new ArrayList<>();
        tracks.add(local(bad, "Rota"));
        tracks.add(local(wav(dir, "ok.wav", 2.0, 440), "Buena"));
        final PlayerEngine p = PlayerEngine.get(app);
        p.playList(tracks, 0, "Prueba");
        waitFor("skipping to the good file", 15000, new Check() {
            public boolean ok() {
                return "Buena".equals(p.current().title) && p.isPlaying();
            }
        });
        assertTrue(org.robolectric.shadows.ShadowToast.getTextOfLatestToast().contains("No se pudo reproducir \"Rota\""));
    }
}

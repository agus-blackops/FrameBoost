package com.agus.sonora;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Intent;
import android.net.Uri;
import android.os.Looper;
import android.view.View;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 2.0: crossfade between songs. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class CrossfadeTest {

    Fixtures net;
    Application app;
    final List<String> opened = new ArrayList<>();
    final Map<String, Long> lengths = new HashMap<>();

    @Before
    public void reset() throws Exception {
        for (Class<?> k : new Class<?>[]{Library.class, PlayerEngine.class, Downloads.class}) {
            java.lang.reflect.Field f = k.getDeclaredField("sInstance");
            f.setAccessible(true);
            f.set(null, null);
        }
        Effects.resetForTest();
        PlaybackService.running = false;
        Locale.setDefault(Locale.US);
        net = new Fixtures();
        Net.fetcher = net;
        Net.resolver = net;
        app = RuntimeEnvironment.getApplication();
        SimAudio.lastFade = -1;
        PlayerEngine.audioFactory = SimAudio.factory(new SimAudio.Durations() {
            @Override
            public long of(String uri) {
                for (Map.Entry<String, Long> e : lengths.entrySet()) if (uri.contains(e.getKey())) return e.getValue();
                return 30000;
            }
        }, opened);
    }

    static void idleFor(int ms) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));
    }

    /** Moves the clock until the current song reaches {@code ms} (settle() also moves it). */
    static void playUntil(PlayerEngine p, int ms) {
        for (int i = 0; i < 2000 && p.position() < ms && p.queueIndex() == 0; i++) idleFor(100);
    }

    @Test
    public void math() {
        assertEquals(1f, Crossfade.out(0, 6000), 1e-6);
        assertEquals(0f, Crossfade.in(0, 6000), 1e-6);
        assertEquals(0f, Crossfade.out(6000, 6000), 1e-6);
        assertEquals(1f, Crossfade.in(6000, 6000), 1e-6);
        // equal power: the sum of the squares stays 1, no dip in the middle
        for (long t = 0; t <= 6000; t += 500) {
            float a = Crossfade.out(t, 6000), b = Crossfade.in(t, 6000);
            assertEquals(1f, a * a + b * b, 1e-5);
        }
        assertEquals(0.7071f, Crossfade.in(3000, 6000), 1e-3);
        assertEquals("setting", 8000, Crossfade.length(8000, 200000, 180000));
        assertEquals("at most a third of a 30 s preview", 10000, Crossfade.length(12000, 30000, 200000));
        assertEquals("nor of the next song", 5000, Crossfade.length(12000, 200000, 15000));
        assertEquals(0, Crossfade.length(0, 200000, 200000));
        assertTrue(Crossfade.shouldStart(194000, 200000, 6000));
        assertFalse(Crossfade.shouldStart(193000, 200000, 6000));
        assertFalse(Crossfade.shouldStart(199000, 200000, 0));
    }

    @Test
    public void sameAlbumOrConcertKeepsItsTransitions() {
        Track a = Track.full("ia:live-1999/01.mp3", "Uno", "B", "En vivo", 0, null, null, "u");
        Track b = Track.full("ia:live-1999/02.mp3", "Dos", "B", "En vivo", 0, null, null, "u");
        Track c = Track.full("ia:otro/01.mp3", "Tres", "B", "Otro", 0, null, null, "u");
        Track au1 = Track.full("au:1", "X", "A", "Electronic", 0, null, null, "u");
        Track au2 = Track.full("au:2", "Y", "A", "Electronic", 0, null, null, "u");
        Track l1 = new Track("content://1", "L1", "A", "Mi disco", 0, 0, false, 0);
        Track l2 = new Track("content://2", "L2", "A", "Mi disco", 0, 0, false, 0);
        Track l3 = new Track("content://3", "L3", "A", null, 0, 0, false, 0);
        Track l4 = new Track("content://4", "L4", "A", null, 0, 0, false, 0);
        assertTrue(Crossfade.sameRecording(a, b));
        assertFalse(Crossfade.sameRecording(a, c));
        assertFalse("an Audius genre is not an album", Crossfade.sameRecording(au1, au2));
        assertTrue(Crossfade.sameRecording(l1, l2));
        assertFalse("unknown album", Crossfade.sameRecording(l3, l4));
        assertFalse(Crossfade.sameRecording(l1, au1));
    }

    List<Track> audius() throws Exception {
        Library lib = Library.get(app);
        Library.Section s = lib.section("full-trending");
        lib.loadSection(s);
        SmokeTest.settle();
        return s.tracks;
    }

    @Test
    public void theNextSongStartsBeforeTheEndWithTheChosenFade() throws Exception {
        List<Track> songs = audius();
        lengths.put("/tracks/t0/", 60000L);
        lengths.put("/tracks/t1/", 90000L);
        PlayerEngine p = PlayerEngine.get(app);
        p.setCrossfade(8000);
        assertEquals(8000, p.crossfadeMs());
        p.playList(songs, 0, "x");
        SmokeTest.settle();
        assertTrue(p.isPlaying());
        // 60 s song with an 8 s fade: the second one takes over at 52 s
        playUntil(p, 51000);
        assertEquals(0, p.queueIndex());
        assertTrue(p.position() >= 51000);
        idleFor(1500);
        assertEquals(1, p.queueIndex());
        assertEquals(8000, SimAudio.lastFade);
        assertEquals("Tema completo 2", p.current().title);
        assertTrue(p.isPlaying());
        assertEquals(1, p.gaplessTransitions);
    }

    @Test
    public void shortSongsGetAShorterFadeAndAlbumsNone() throws Exception {
        PlayerEngine p = PlayerEngine.get(app);
        p.setCrossfade(12000);
        // a 30 s preview: at most 10 s
        Library lib = Library.get(app);
        Library.Section top = lib.section("top");
        lib.loadSection(top);
        SmokeTest.settle();
        p.playList(top.tracks, 0, "Top");
        SmokeTest.settle();
        for (int i = 0; i < 40 && p.queueIndex() == 0; i++) idleFor(1000);
        assertEquals(1, p.queueIndex());
        assertEquals(10000, SimAudio.lastFade);

        // an Internet Archive album: its songs follow without a fade
        Library.Section archive = lib.section("archive");
        lib.loadSection(archive);
        SmokeTest.settle();
        Library.Item album = archive.items.get(0);
        lib.loadItemTracks(album);
        SmokeTest.settle();
        lengths.put("archive.org", 40000L);
        SimAudio.lastFade = -1;
        p.playList(lib.itemTracks(album), 0, album.title);
        SmokeTest.settle();
        for (int i = 0; i < 60 && p.queueIndex() == 0; i++) idleFor(1000);
        assertEquals(1, p.queueIndex());
        assertEquals("gapless inside an album", 0, SimAudio.lastFade);
    }

    @Test
    public void changingTheSettingReplansTheNextSong() throws Exception {
        List<Track> songs = audius();
        lengths.put("/tracks/t0/", 30000L);
        PlayerEngine p = PlayerEngine.get(app);
        p.playList(songs, 0, "x");
        SmokeTest.settle();
        playUntil(p, 20000); // inside the preload window: next song queued gapless
        assertEquals(0, p.queueIndex());
        p.setCrossfade(6000);
        for (int i = 0; i < 20 && p.queueIndex() == 0; i++) idleFor(1000);
        assertEquals(1, p.queueIndex());
        assertEquals(6000, SimAudio.lastFade);
    }

    @Test
    public void ajustesOffersTheFade() throws Exception {
        ActivityController<SettingsActivity> c = Robolectric.buildActivity(SettingsActivity.class,
                new Intent(app, SettingsActivity.class)).setup();
        View root = c.get().getWindow().getDecorView();
        SmokeTest.layout(root);
        assertNotNull(SmokeTest.findText(root, "Fundido entre canciones"));
        assertNotNull(SmokeTest.findText(root, "Sin fundido (las canciones se siguen sin pausa)"));
        SmokeTest.clickText(root, "Fundido entre canciones");
        android.app.AlertDialog dlg = (android.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
        dlg.getListView().performItemClick(null, 3, 3); // 6 s
        SmokeTest.settle();
        assertEquals(6000, PlayerEngine.get(app).crossfadeMs());
        SmokeTest.layout(root);
        assertNotNull(SmokeTest.findText(root, "6 segundos"));
        c.pause().stop().destroy();
    }

    /** The real Media3 players: the second song is loaded on the other player and takes over. */
    @Test
    public void realPlayersCrossIntoTheNextSong() throws Exception {
        PlayerEngine.audioFactory = ExoAudio.FACTORY;
        File dir = app.getCacheDir();
        List<Track> tracks = new ArrayList<>();
        tracks.add(new Track(Uri.fromFile(Media3Test.wav(dir, "x.wav", 4.0, 440)).toString(), "Primera", "A", "Disco A", 0, 0, false, 0));
        tracks.add(new Track(Uri.fromFile(Media3Test.wav(dir, "y.wav", 3.0, 660)).toString(), "Segunda", "B", "Disco B", 0, 0, false, 0));
        final PlayerEngine p = PlayerEngine.get(app);
        p.setCrossfade(2000);
        final List<String> seen = new ArrayList<>();
        p.addListener(new PlayerEngine.Listener() {
            @Override
            public void onPlayerChanged() {
                Track t = p.current();
                String now = t == null ? "-" : t.title + (p.isActive() ? "" : " (pausa)");
                if (seen.isEmpty() || !seen.get(seen.size() - 1).equals(now)) seen.add(now);
            }
        });
        p.playList(tracks, 0, "Prueba");
        Media3Test.waitFor("both songs and the rewind", 30000, new Media3Test.Check() {
            public boolean ok() {
                return seen.contains("Segunda") && "Primera (pausa)".equals(seen.get(seen.size() - 1));
            }
        });
        assertTrue(seen.toString(), seen.indexOf("Primera") < seen.indexOf("Segunda"));
        assertEquals("the second player took over by itself", 1, p.gaplessTransitions);
        assertFalse(org.robolectric.shadows.ShadowToast.shownToastCount() > 0);
        p.stop();
    }
}

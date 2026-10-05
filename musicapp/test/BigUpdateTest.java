package com.agus.sonora;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Application;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowMediaPlayer;
import org.robolectric.shadows.util.DataSource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Tests for the 1.5 features: sleep timer, speed, queue, session, history, lyrics, equalizer. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class BigUpdateTest {

    Fixtures net;
    Application app;

    @Before
    public void reset() throws Exception {
        for (Class<?> k : new Class<?>[]{Library.class, PlayerEngine.class}) {
            java.lang.reflect.Field f = k.getDeclaredField("sInstance");
            f.setAccessible(true);
            f.set(null, null);
        }
        Effects.resetForTest();
        Lyrics.clearCache();
        PlaybackService.running = false;
        net = new Fixtures();
        Net.fetcher = net;
        app = RuntimeEnvironment.getApplication();
        shadowOf(app).grantPermissions(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS);
        ShadowMediaPlayer.setMediaInfoProvider(new ShadowMediaPlayer.MediaInfoProvider() {
            @Override
            public ShadowMediaPlayer.MediaInfo get(DataSource ds) {
                return new ShadowMediaPlayer.MediaInfo(30000, 50);
            }
        });
    }

    static void forget(Class<?> k) throws Exception {
        java.lang.reflect.Field f = k.getDeclaredField("sInstance");
        f.setAccessible(true);
        f.set(null, null);
    }

    static void idle() {
        SmokeTest.idle();
    }

    static void settle() throws Exception {
        SmokeTest.settle();
    }

    List<Track> chart() throws Exception {
        Library lib = Library.get(app);
        lib.loadOnline();
        settle();
        return lib.section("top").tracks;
    }

    // ------------------------------------------------------------------ sleep timer

    @Test
    public void sleepTimerFadesAndPauses() throws Exception {
        ShadowMediaPlayer.setMediaInfoProvider(new ShadowMediaPlayer.MediaInfoProvider() {
            @Override
            public ShadowMediaPlayer.MediaInfo get(DataSource ds) {
                return new ShadowMediaPlayer.MediaInfo(3600000, 50); // long song: it won't end on its own
            }
        });
        List<Track> tracks = chart();
        PlayerEngine p = PlayerEngine.get(app);
        p.playList(tracks, 0, "Top");
        settle();
        assertTrue(p.isPlaying());

        p.setSleepTimer(15);
        assertEquals(PlayerEngine.SLEEP_TIMED, p.sleepMode());
        assertTrue(p.sleepRemainingMs() > 14 * 60000L);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(14));
        assertTrue("still playing before the time is up", p.isPlaying());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(1));
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5)); // fade-out
        assertFalse("paused when the timer ends", p.isPlaying());
        assertEquals(PlayerEngine.SLEEP_OFF, p.sleepMode());

        // resume, set and cancel: it must not fire afterwards
        p.play();
        idle();
        assertTrue(p.isPlaying());
        p.setSleepTimer(5);
        p.cancelSleep();
        assertEquals(PlayerEngine.SLEEP_OFF, p.sleepMode());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(10));
        assertTrue("cancelled timer does nothing", p.isPlaying());
    }

    @Test
    public void sleepAtEndOfTrackStopsOnNextSongPaused() throws Exception {
        List<Track> tracks = chart();
        PlayerEngine p = PlayerEngine.get(app);
        p.playList(tracks, 0, "Top");
        settle();
        p.sleepAtEndOfTrack();
        assertEquals(PlayerEngine.SLEEP_END_OF_TRACK, p.sleepMode());
        p.seekTo(29990);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
        settle();
        assertEquals("next song is lined up", tracks.get(1), p.current());
        assertFalse("but not playing", p.isPlaying());
        assertEquals(PlayerEngine.SLEEP_OFF, p.sleepMode());
        p.play();
        settle();
        assertTrue("can start it again", p.isPlaying());
    }

    // ------------------------------------------------------------------ speed

    @Test
    public void speedIsRememberedAcrossRestarts() throws Exception {
        List<Track> tracks = chart();
        PlayerEngine p = PlayerEngine.get(app);
        assertEquals(1f, p.speed(), 0f);
        p.playList(tracks, 0, "Top");
        settle();
        p.setSpeed(1.5f);
        assertEquals(1.5f, p.speed(), 0f);
        assertTrue(p.isPlaying());
        p.setSpeed(9f); // clamps
        assertEquals(2f, p.speed(), 0f);
        p.setSpeed(1.25f);
        forget(PlayerEngine.class);
        assertEquals(1.25f, PlayerEngine.get(app).speed(), 0f);
        assertEquals("1.25×", NowPlayingActivity.speedText(1.25f));
        assertEquals("2×", NowPlayingActivity.speedText(2f));
    }

    // ------------------------------------------------------------------ queue

    @Test
    public void queueEditing() throws Exception {
        List<Track> tracks = new ArrayList<>(chart().subList(0, 6));
        PlayerEngine p = PlayerEngine.get(app);
        p.playList(tracks, 1, "Top");
        settle();
        assertEquals(6, p.queueTracks().size());
        assertEquals(1, p.queueIndex());

        p.jumpTo(3);
        settle();
        assertEquals(tracks.get(3), p.current());
        assertTrue(p.isPlaying());

        p.removeAt(0); // before the current song: it keeps playing
        assertEquals(tracks.get(3), p.current());
        assertEquals(2, p.queueIndex());
        assertEquals(5, p.queueTracks().size());

        p.removeAt(4); // last
        assertEquals(tracks.get(3), p.current());
        assertEquals(4, p.queueTracks().size());

        p.removeAt(p.queueIndex()); // current one: next takes its place and plays
        settle();
        assertEquals(tracks.get(4), p.current());
        assertTrue(p.isPlaying());
        assertEquals(3, p.queueTracks().size());

        p.clearUpcoming();
        assertEquals(1, p.queueTracks().size() - p.queueIndex());
        assertEquals(tracks.get(4), p.current());

        p.removeAt(0);
        p.removeAt(0);
        assertEquals(tracks.get(4), p.current());
        p.removeAt(0); // everything gone
        idle();
        assertNull(p.current());
    }

    @Test
    public void queueRemovalWithShuffleKeepsQueueConsistent() throws Exception {
        List<Track> tracks = new ArrayList<>(chart().subList(0, 8));
        PlayerEngine p = PlayerEngine.get(app);
        p.toggleShuffle();
        p.playList(tracks, 2, "Top");
        settle();
        Track first = p.current();
        assertEquals(tracks.get(2), first);
        List<Track> order = p.queueTracks();
        Track victim = order.get(5);
        p.removeAt(5);
        List<Track> after = p.queueTracks();
        assertEquals(7, after.size());
        assertFalse(after.contains(victim));
        assertEquals(first, p.current());
        p.toggleShuffle(); // rebuild order from the queue: victim must stay gone
        assertEquals(7, p.queueTracks().size());
        assertFalse(p.queueTracks().contains(victim));
    }

    @Test
    public void queueScreen() throws Exception {
        List<Track> tracks = new ArrayList<>(chart().subList(0, 5));
        PlayerEngine p = PlayerEngine.get(app);
        p.playList(tracks, 1, "Top 50 mundial");
        settle();
        ActivityController<QueueActivity> c = Robolectric.buildActivity(QueueActivity.class).setup();
        View root = c.get().getWindow().getDecorView();
        SmokeTest.layout(root);
        assertNotNull(SmokeTest.findText(root, "Reproduciendo ahora"));
        assertNotNull(SmokeTest.findText(root, "A continuación"));
        assertNotNull(SmokeTest.findPrefix(root, "Top 50 mundial · 3 canciones a continuación"));
        ListView list = SmokeTest.findList(root);
        assertEquals(6, list.getCount()); // header, current, header, 3 upcoming
        // tap the last upcoming song -> jumps there
        list.performItemClick(list.getAdapter().getView(5, null, list), 5, 5);
        settle();
        assertEquals(tracks.get(4), p.current());
        SmokeTest.layout(root);
        c.pause().stop().destroy();
    }

    // ------------------------------------------------------------------ session

    @Test
    public void sessionIsRestoredPausedWithoutNetwork() throws Exception {
        List<Track> tracks = chart();
        PlayerEngine p = PlayerEngine.get(app);
        p.toggleShuffle();
        p.cycleRepeat();
        p.playList(tracks, 2, "Top 50 mundial");
        settle();
        p.seekTo(12000);
        p.saveSession();
        Track playing = p.current();
        List<Track> order = p.queueTracks();

        forget(PlayerEngine.class); // simulates the app being killed
        forget(Library.class);
        int requests = net.urls.size();
        PlayerEngine q = PlayerEngine.get(app);
        assertNull(q.current());
        q.restoreSession();
        idle();
        assertEquals(playing.key, q.current().key);
        assertEquals("Top 50 mundial", q.queueName());
        assertFalse(q.isPlaying());
        assertEquals(12000, q.position(), 300);
        assertTrue(q.isShuffle());
        assertEquals(PlayerEngine.REPEAT_ALL, q.repeatMode());
        assertEquals(order.size(), q.queueTracks().size());
        assertEquals(order.get(0).key, q.queueTracks().get(0).key);
        assertEquals("restoring touches no network", requests, net.urls.size());

        // the mini player shows it and pressing play resumes near where it was
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
        settle();
        View root = c.get().getWindow().getDecorView();
        SmokeTest.layout(root);
        assertNotNull(SmokeTest.findText(root, playing.title));
        q.play();
        settle();
        assertTrue(q.isPlaying());
        assertTrue("resumed at the saved position: " + q.position(), q.position() >= 12000);
        assertTrue(shadowOf(app).getNextStartedService() != null);
        c.pause().stop().destroy();

        // closing clears the session
        q.stop();
        forget(PlayerEngine.class);
        PlayerEngine r = PlayerEngine.get(app);
        r.restoreSession();
        assertNull(r.current());
    }

    @Test
    public void localAndRadioSessionsRestoreToo() throws Exception {
        Library lib = Library.get(app);
        lib.loadOnline();
        settle();
        List<Track> radios = lib.section("radio").tracks;
        PlayerEngine p = PlayerEngine.get(app);
        p.playList(radios, 1, "Radios en vivo");
        settle();
        p.saveSession();
        forget(PlayerEngine.class);
        PlayerEngine q = PlayerEngine.get(app);
        q.restoreSession();
        assertTrue(q.current().isLive());
        assertEquals("Los 40 Argentina", q.current().title);
        assertEquals(0, q.position());

        Track local = new Track("content://media/external/audio/media/42", "Mi canción", "Yo", "Mi álbum", 3, 200000, false, 5);
        List<Track> one = new ArrayList<>();
        one.add(local);
        forget(PlayerEngine.class);
        PlayerEngine r = PlayerEngine.get(app);
        r.playList(one, 0, "Tu música");
        idle();
        r.saveSession();
        forget(PlayerEngine.class);
        PlayerEngine s = PlayerEngine.get(app);
        s.restoreSession();
        assertEquals("Mi canción", s.current().title);
        assertFalse(s.current().remote);
        assertEquals(local.key, s.current().key);
    }

    // ------------------------------------------------------------------ history

    @Test
    public void historyIsRecordedShownAndPersisted() throws Exception {
        List<Track> tracks = chart();
        Library lib = Library.get(app);
        PlayerEngine p = PlayerEngine.get(app);
        assertTrue(lib.recentlyPlayed(10).isEmpty());
        p.playList(tracks, 0, "Top");
        settle();
        p.next();
        settle();
        p.next();
        settle();
        List<Track> recent = lib.recentlyPlayed(10);
        assertEquals(3, recent.size());
        assertEquals(tracks.get(2), recent.get(0));
        assertEquals(tracks.get(0), recent.get(2));
        p.jumpTo(1); // play an earlier song of the queue again
        settle();
        assertEquals("replaying moves it to the top, no duplicates", 3, lib.recentlyPlayed(10).size());
        assertEquals(tracks.get(1), lib.recentlyPlayed(10).get(0));

        // pause/resume of the same song doesn't duplicate it
        p.pause();
        p.play();
        idle();
        assertEquals(3, lib.recentlyPlayed(10).size());

        // survives a restart without internet
        net.offline = true;
        forget(Library.class);
        Library lib2 = Library.get(app);
        assertEquals(3, lib2.recentlyPlayed(10).size());
        assertEquals(tracks.get(1).key, lib2.recentlyPlayed(10).get(0).key);
        assertEquals(tracks.get(1).title, lib2.recentlyPlayed(10).get(0).title);

        // shows up on Home and in the Library tab
        net.offline = false;
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
        settle();
        View root = c.get().getWindow().getDecorView();
        SmokeTest.layout(root);
        assertNotNull(SmokeTest.findText(root, "Escuchado recientemente"));
        SmokeTest.clickText(root, "Tu biblioteca");
        assertNotNull(SmokeTest.findText(root, "Escuchado recientemente"));
        SmokeTest.clickText(root, "Escuchado recientemente");
        settle();
        SmokeTest.layout(root);
        ListView lv = SmokeTest.findList(root);
        assertEquals("header + 3 songs + footer", 5, lv.getCount());
        c.pause().stop().destroy();

        lib2.clearHistory();
        assertTrue(lib2.recentlyPlayed(10).isEmpty());
    }

    @Test
    public void historyIsCapped() throws Exception {
        List<Track> tracks = chart();
        Library lib = Library.get(app);
        for (int i = 0; i < 50; i++) lib.recordPlayed(tracks.get(i));
        for (int i = 0; i < 4; i++) {
            lib.recordPlayed(new Track("content://x/" + i, "Local " + i, "A", "B", 0, 1000, false, 0));
        }
        assertEquals(Library.HISTORY_MAX, lib.recentlyPlayed(100).size());
        assertEquals("Local 3", lib.recentlyPlayed(1).get(0).title);
    }

    // ------------------------------------------------------------------ search history + rename

    @Test
    public void searchHistory() throws Exception {
        Library lib = Library.get(app);
        lib.addSearch("a"); // too short
        lib.addSearch("  bad bunny ");
        lib.addSearch("Shakira");
        lib.addSearch("BAD BUNNY"); // same search, moves to the top
        assertEquals(2, lib.searchHistory().size());
        assertEquals("BAD BUNNY", lib.searchHistory().get(0));
        for (int i = 0; i < 15; i++) lib.addSearch("query " + i);
        assertEquals(10, lib.searchHistory().size());
        assertEquals("query 14", lib.searchHistory().get(0));
        forget(Library.class);
        assertEquals(10, Library.get(app).searchHistory().size());
        lib = Library.get(app);
        lib.clearSearchHistory();
        assertTrue(lib.searchHistory().isEmpty());
    }

    @Test
    public void searchScreenShowsRecentSearchesAndRemembersPlayedOnes() throws Exception {
        Library lib = Library.get(app);
        lib.addSearch("shakira");
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
        settle();
        View root = c.get().getWindow().getDecorView();
        SmokeTest.clickText(root, "Buscar");
        assertNotNull(SmokeTest.findText(root, "Búsquedas recientes"));
        SmokeTest.clickText(root, "shakira"); // chip fills in the search box
        settle();
        SmokeTest.layout(root);
        List<View> all = new ArrayList<>();
        SmokeTest.collect(root, all);
        EditText input = null;
        for (View v : all) if (v instanceof EditText) input = (EditText) v;
        assertEquals("shakira", input.getText().toString());
        ListView results = SmokeTest.findList(root);
        assertTrue(results.getAdapter().getCount() > 0);
        input.setText("despacito");
        settle();
        SmokeTest.layout(root);
        results = SmokeTest.findList(root);
        results.performItemClick(results.getAdapter().getView(0, null, results), 0, 0);
        settle();
        assertEquals("despacito", lib.searchHistory().get(0));
        input.setText("");
        idle();
        SmokeTest.layout(root);
        assertNotNull(SmokeTest.findText(root, "despacito"));
        SmokeTest.clickText(root, "Borrar");
        assertTrue(lib.searchHistory().isEmpty());
        SmokeTest.layout(root);
        assertNull(SmokeTest.findText(root, "Búsquedas recientes"));
        c.pause().stop().destroy();
    }

    @Test
    public void renamePlaylist() throws Exception {
        Library lib = Library.get(app);
        lib.createPlaylist("Uno");
        lib.createPlaylist("Dos");
        lib.createPlaylist("Tres");
        List<Track> tracks = chart();
        lib.addToPlaylist("Dos", tracks.get(0));
        assertFalse(lib.renamePlaylist("Dos", "Tres"));
        assertFalse(lib.renamePlaylist("Dos", "  "));
        assertFalse(lib.renamePlaylist("No existe", "X"));
        assertTrue(lib.renamePlaylist("Dos", "Para correr"));
        assertEquals("order is kept", java.util.Arrays.asList("Uno", "Para correr", "Tres"), lib.playlistNames());
        assertEquals(1, lib.playlist("Para correr").size());
        forget(Library.class);
        assertEquals(java.util.Arrays.asList("Uno", "Para correr", "Tres"), Library.get(app).playlistNames());
    }

    @Test
    public void renamePlaylistFromTheScreen() throws Exception {
        Library lib = Library.get(app);
        lib.createPlaylist("Vieja");
        lib.addToPlaylist("Vieja", chart().get(0));
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity a = c.get();
        settle();
        View root = a.getWindow().getDecorView();
        SmokeTest.clickText(root, "Tu biblioteca");
        SmokeTest.clickText(root, "Vieja");
        settle();
        SmokeTest.layout(root);
        assertNotNull(SmokeTest.findText(root, "Vieja"));
        Dialogs.rename(a, "Vieja", new Dialogs.OnName() {
            @Override
            public void onName(String name) {
            }
        });
        AlertDialog dlg = ShadowAlertDialog.getLatestAlertDialog();
        EditText input = null;
        List<View> all = new ArrayList<>();
        SmokeTest.collect(dlg.getWindow().getDecorView(), all);
        for (View v : all) if (v instanceof EditText) input = (EditText) v;
        assertEquals("Vieja", input.getText().toString());
        input.setText("Nueva");
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
        assertTrue(lib.playlistNames().contains("Nueva"));
        assertFalse(lib.playlistNames().contains("Vieja"));
        c.pause().stop().destroy();
    }

    // ------------------------------------------------------------------ lyrics

    @Test
    public void lrcParsing() throws Exception {
        List<Lyrics.Line> l = Lyrics.parseLrc("[ar:X]\n[00:01.50] Hola\n[01:02.5]Chau\n[00:05.123][00:30.00] Coro\r\n[00:07] sin fracción\nsin marca");
        assertEquals(5, l.size());
        assertEquals(1500, l.get(0).ms);
        assertEquals("Hola", l.get(0).text);
        assertEquals(5123, l.get(1).ms);
        assertEquals("Coro", l.get(1).text);
        assertEquals(7000, l.get(2).ms);
        assertEquals(30000, l.get(3).ms);
        assertEquals("sorted by time even with repeated chorus stamps", 62500, l.get(4).ms);
        assertEquals(-1, Lyrics.currentLine(l, 0));
        assertEquals(0, Lyrics.currentLine(l, 1400)); // 200 ms early highlight
        assertEquals(1, Lyrics.currentLine(l, 5000));
        assertEquals(4, Lyrics.currentLine(l, 999999));
        assertEquals(-1, Lyrics.currentLine(new ArrayList<Lyrics.Line>(), 5));
    }

    ActivityController<NowPlayingActivity> openNowPlaying(Track t, List<Track> queue) throws Exception {
        PlayerEngine.get(app).playList(queue, queue.indexOf(t), "Top");
        settle();
        ActivityController<NowPlayingActivity> c = Robolectric.buildActivity(NowPlayingActivity.class).setup();
        SmokeTest.layout(c.get().getWindow().getDecorView());
        return c;
    }

    @Test
    public void syncedLyricsFollowThePlaybackPosition() throws Exception {
        List<Track> tracks = chart();
        Track despacito = tracks.get(2);
        ActivityController<NowPlayingActivity> c = openNowPlaying(despacito, tracks);
        View root = c.get().getWindow().getDecorView();
        assertNull("lyrics hidden by default", SmokeTest.findPrefix(root, "Primera línea"));
        SmokeTest.clickText(root, "Letra");
        settle();
        SmokeTest.layout(root);
        TextView l1 = SmokeTest.findText(root, "Primera línea");
        TextView l2 = SmokeTest.findText(root, "Segunda línea");
        assertNotNull(l1);
        assertNotNull(l2);
        assertNotNull(SmokeTest.findText(root, "Última línea"));
        assertNotNull("empty synced line shows a note", SmokeTest.findText(root, "♪"));
        PlayerEngine p = PlayerEngine.get(app);
        p.seekTo(700);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        assertEquals("first line lit", 0xFFFFFFFF, l1.getCurrentTextColor());
        p.seekTo(4500);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        assertEquals("second line lit", 0xFFFFFFFF, l2.getCurrentTextColor());
        assertNotEquals("first line dimmed", 0xFFFFFFFF, l1.getCurrentTextColor());
        // tapping a line seeks there
        TextView l3 = SmokeTest.findText(root, "Tercera línea");
        l3.performClick();
        idle();
        assertEquals(8250, p.position(), 800);
        // lookup is cached per song: going to the next and back doesn't re-request
        int before = net.lyricLookups.get();
        p.next();
        settle();
        p.jumpTo(p.queueIndex() - 1);
        settle();
        assertEquals(despacito, p.current());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        assertNotNull(SmokeTest.findText(root, "Primera línea"));
        assertEquals("only the new song was looked up", before + 1, net.lyricLookups.get());
        // switching back to the cover
        SmokeTest.clickText(root, "Letra");
        assertNull(SmokeTest.findText(root, "Primera línea"));
        c.pause().stop().destroy();
    }

    @Test
    public void plainLyricsViaSearchFallback() throws Exception {
        List<Track> tracks = chart();
        Track hips = tracks.get(1);
        ActivityController<NowPlayingActivity> c = openNowPlaying(hips, tracks);
        View root = c.get().getWindow().getDecorView();
        SmokeTest.clickText(root, "Letra");
        settle();
        SmokeTest.layout(root);
        assertNotNull(SmokeTest.findPrefix(root, "Ladies up in here tonight"));
        c.pause().stop().destroy();
    }

    @Test
    public void lyricsMissingInstrumentalRadioAndOffline() throws Exception {
        List<Track> tracks = chart();
        // not found anywhere
        Track flowers = tracks.get(5);
        ActivityController<NowPlayingActivity> c = openNowPlaying(flowers, tracks);
        View root = c.get().getWindow().getDecorView();
        SmokeTest.clickText(root, "Letra");
        settle();
        SmokeTest.layout(root);
        assertNotNull(SmokeTest.findText(root, "No encontramos la letra de esta canción"));
        // instrumental
        PlayerEngine p = PlayerEngine.get(app);
        p.playList(tracks, 0, "Top"); // Monaco
        settle();
        SmokeTest.layout(root);
        assertNotNull(SmokeTest.findText(root, "Instrumental ♪"));
        // offline: message with retry, then works once online again
        net.offline = true;
        p.playList(tracks, 3, "Top");
        settle();
        SmokeTest.layout(root);
        TextView err = SmokeTest.findPrefix(root, "No se pudo cargar la letra");
        assertNotNull(err);
        net.offline = false;
        err.performClick();
        settle();
        SmokeTest.layout(root);
        assertNotNull(SmokeTest.findText(root, "Primera línea"));
        // radio
        Library lib = Library.get(app);
        p.playList(lib.section("radio").tracks, 0, "Radios");
        settle();
        SmokeTest.layout(root);
        assertNotNull(SmokeTest.findText(root, "La radio en vivo no tiene letra"));
        c.pause().stop().destroy();
    }

    // ------------------------------------------------------------------ equalizer

    @Test
    public void equalizerSettingsAndPresets() throws Exception {
        Effects.overrideInfo(new Effects.Info(5, -15f, 15f, new int[]{60, 230, 910, 3600, 14000}));
        Effects fx = Effects.get(app);
        assertFalse(fx.isEnabled());
        assertEquals(0, fx.preset());
        for (float v : fx.levelsDb()) assertEquals(0f, v, 0f);

        int rock = java.util.Arrays.asList(Effects.PRESET_NAMES).indexOf("Rock");
        fx.setPreset(rock);
        assertArrayEquals(new float[]{4, 2, -2, 3, 5}, fx.levelsDb(), 0.001f);
        fx.setBand(2, 6f);
        assertEquals("manual change leaves the preset", -1, fx.preset());
        assertArrayEquals(new float[]{4, 2, 6, 3, 5}, fx.levelsDb(), 0.001f);
        fx.setBand(0, 99f); // clamped to the device range
        assertEquals(15f, fx.levelsDb()[0], 0f);
        fx.setBass(5000);
        fx.setSurround(-4);
        fx.setEnabled(true);
        assertEquals(1000, fx.bass());
        assertEquals(0, fx.surround());

        Effects.resetInstanceForTest();
        Effects again = Effects.get(app);
        assertTrue(again.isEnabled());
        assertEquals(-1, again.preset());
        assertArrayEquals(new float[]{15, 2, 6, 3, 5}, again.levelsDb(), 0.001f);
        assertEquals(1000, again.bass());

        // the same curves spread over a different number of bands (e.g. a 10-band device)
        Effects.overrideInfo(new Effects.Info(3, -10f, 10f, new int[]{60, 1000, 14000}));
        again.setPreset(rock);
        assertArrayEquals(new float[]{4, -2, 5}, again.levelsDb(), 0.001f);
        // and clamped when the device range is smaller than the curve
        Effects.overrideInfo(new Effects.Info(5, -3f, 3f, new int[]{60, 230, 910, 3600, 14000}));
        assertArrayEquals(new float[]{3, 2, -2, 3, 3}, again.levelsDb(), 0.001f);
    }

    @Test
    public void equalizerScreen() throws Exception {
        Effects.overrideInfo(new Effects.Info(5, -15f, 15f, new int[]{60, 230, 910, 3600, 14000}));
        ActivityController<EqualizerActivity> c = Robolectric.buildActivity(EqualizerActivity.class).setup();
        View root = c.get().getWindow().getDecorView();
        SmokeTest.layout(root);
        assertNotNull(SmokeTest.findText(root, "Ecualizador"));
        assertNotNull(SmokeTest.findText(root, "60 Hz"));
        assertNotNull(SmokeTest.findText(root, "3.6 kHz"));
        assertNotNull(SmokeTest.findText(root, "14 kHz"));
        assertNotNull(SmokeTest.findText(root, "Rock"));
        Effects fx = Effects.get(app);
        SmokeTest.clickText(root, "Rock"); // picking a preset switches the equalizer on
        assertTrue(fx.isEnabled());
        assertEquals(java.util.Arrays.asList(Effects.PRESET_NAMES).indexOf("Rock"), fx.preset());
        List<View> all = new ArrayList<>();
        SmokeTest.collect(root, all);
        List<SeekBar> bars = new ArrayList<>();
        for (View v : all) if (v instanceof SeekBar) bars.add((SeekBar) v);
        assertEquals("5 bands + bass + surround", 7, bars.size());
        assertEquals(Math.round((4f + 15f) * 2), bars.get(0).getProgress());
        assertNotNull(SmokeTest.findText(root, "+4.0 dB"));
        c.pause().stop().destroy();

        // device without equalizer support
        Effects.overrideInfo(null);
        ActivityController<EqualizerActivity> c2 = Robolectric.buildActivity(EqualizerActivity.class).setup();
        View root2 = c2.get().getWindow().getDecorView();
        SmokeTest.layout(root2);
        assertNotNull(SmokeTest.findPrefix(root2, "Este dispositivo no permite ajustar el ecualizador"));
        c2.pause().stop().destroy();
    }

    @Test
    public void nowPlayingActionsAndTimerLabel() throws Exception {
        List<Track> tracks = chart();
        ActivityController<NowPlayingActivity> c = openNowPlaying(tracks.get(0), tracks);
        NowPlayingActivity a = c.get();
        View root = a.getWindow().getDecorView();
        PlayerEngine p = PlayerEngine.get(app);
        for (String label : new String[]{"Temporizador", "Velocidad", "Letra", "Cola", "Ecualizador"}) {
            assertNotNull(label, SmokeTest.findText(root, label));
        }
        assertEquals("1×", SmokeTest.findPrefix(root, "1×").getText().toString());
        p.setSleepTimer(30);
        idle();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        SmokeTest.layout(root);
        assertNull("countdown replaces the label", SmokeTest.findText(root, "Temporizador"));
        p.sleepAtEndOfTrack();
        idle();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        assertNotNull(SmokeTest.findText(root, "Fin canción"));
        p.cancelSleep();
        // dialogs open
        SmokeTest.clickText(root, "Temporizador");
        AlertDialog d = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull(d);
        shadowOf(d).clickOnItem(2); // 10 min
        idle();
        assertEquals(PlayerEngine.SLEEP_TIMED, p.sleepMode());
        assertTrue(p.sleepRemainingMs() > 9 * 60000L);
        SmokeTest.clickText(root, "Velocidad");
        d = ShadowAlertDialog.getLatestAlertDialog();
        shadowOf(d).clickOnItem(4); // 1.5x
        idle();
        assertEquals(1.5f, p.speed(), 0f);
        // radio: speed not available
        Library lib = Library.get(app);
        p.playList(lib.section("radio").tracks, 0, "Radios");
        settle();
        SmokeTest.clickText(root, "Velocidad");
        assertEquals("1.5 stays", 1.5f, p.speed(), 0f);
        // queue and equalizer screens launch
        SmokeTest.clickText(root, "Cola");
        assertEquals(QueueActivity.class.getName(), shadowOf(app).getNextStartedActivity().getComponent().getClassName());
        SmokeTest.clickText(root, "Ecualizador");
        assertEquals(EqualizerActivity.class.getName(), shadowOf(app).getNextStartedActivity().getComponent().getClassName());
        c.pause().stop().destroy();
    }
}

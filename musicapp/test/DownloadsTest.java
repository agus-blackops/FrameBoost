package com.agus.sonora;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.content.Intent;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowToast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 2.0: offline downloads of complete songs and podcast episodes, and the Ajustes screen. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class DownloadsTest {

    Fixtures net;
    Application app;
    final List<String> opened = new ArrayList<>();

    @Before
    public void reset() throws Exception {
        for (Class<?> k : new Class<?>[]{Library.class, PlayerEngine.class, Downloads.class}) {
            java.lang.reflect.Field f = k.getDeclaredField("sInstance");
            f.setAccessible(true);
            f.set(null, null);
        }
        Downloads.metered = new Downloads.Metered() {
            @Override
            public boolean now(android.content.Context c) {
                return false;
            }
        };
        Effects.resetForTest();
        PlaybackService.running = false;
        Locale.setDefault(Locale.US);
        net = new Fixtures();
        Net.fetcher = net;
        Net.resolver = net;
        Net.saver = net;
        app = RuntimeEnvironment.getApplication();
        shadowOf(app).grantPermissions(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS);
        PlayerEngine.audioFactory = SimAudio.factory(new SimAudio.Durations() {
            @Override
            public long of(String uri) {
                return 200000;
            }
        }, opened);
    }

    static void settle() throws Exception {
        SmokeTest.settle();
    }

    List<Track> audius() throws Exception {
        Library lib = Library.get(app);
        Library.Section s = lib.section("full-trending");
        lib.loadSection(s);
        settle();
        return s.tracks;
    }

    List<Track> episodes() throws Exception {
        Library lib = Library.get(app);
        Library.Item pod = new Library.Item(Library.Item.PODCAST, 201, "Noticias al día", "Voces", null, "https://feeds.example.com/201.xml");
        lib.loadItemTracks(pod);
        settle();
        return lib.itemTracks(pod);
    }

    @Test
    public void onlyCompleteFreeSongsAndEpisodesCanBeDownloaded() throws Exception {
        Track full = audius().get(0);
        Track episode = episodes().get(0);
        Library lib = Library.get(app);
        Library.Section top = lib.section("top");
        lib.loadSection(top);
        Library.Section radio = lib.section("radio");
        lib.loadSection(radio);
        settle();
        assertTrue(Downloads.canDownload(full));
        assertTrue(Downloads.canDownload(episode));
        assertFalse("30-second previews are not downloaded", Downloads.canDownload(top.tracks.get(0)));
        assertFalse(Downloads.canDownload(radio.tracks.get(0)));
        assertFalse(Downloads.canDownload(new Track("content://media/1", "Local", "A", "B", 0, 1000, false, 0)));
        Downloads d = Downloads.get(app);
        d.download(top.tracks.get(0));
        assertEquals(Downloads.NONE, d.state(top.tracks.get(0)));
    }

    @Test
    public void aDownloadedSongIsSavedAndPlaysFromTheFileEvenOffline() throws Exception {
        List<Track> songs = audius();
        Track t = songs.get(0);
        Downloads d = Downloads.get(app);
        d.download(t);
        assertTrue(d.state(t) == Downloads.RUNNING || d.state(t) == Downloads.QUEUED);
        settle();
        assertEquals(Downloads.DONE, d.state(t));
        assertEquals(100, d.percent(t));
        File f = d.file(t);
        assertNotNull(f);
        assertEquals(300 * 1024, f.length());
        assertTrue("inside the app's own folder", f.getPath().contains(app.getPackageName()));
        assertTrue(f.getName().endsWith("-tema-completo-1.mp3"));
        assertEquals(300 * 1024, d.bytesUsed());
        assertEquals(1, d.tracks().size());
        assertEquals("https://api.audius.co/v1/tracks/t0/stream?app_name=Sonora", net.saved.get(0));

        net.offline = true;
        PlayerEngine p = PlayerEngine.get(app);
        p.playList(songs, 0, "x");
        settle();
        assertTrue(p.isPlaying());
        assertEquals("the file, not the internet", android.net.Uri.fromFile(f).toString(), opened.get(opened.size() - 1));

        d.remove(t);
        assertFalse(f.exists());
        assertEquals(Downloads.NONE, d.state(t));
        assertEquals(0, d.bytesUsed());
    }

    @Test
    public void downloadedEpisodesNeedNoRedirectLookup() throws Exception {
        List<Track> eps = episodes();
        Downloads d = Downloads.get(app);
        Track ep = eps.get(0);
        d.download(ep);
        settle();
        assertEquals(Downloads.DONE, d.state(ep));
        int lookups = net.resolved.size();
        net.offline = true;
        PlayerEngine p = PlayerEngine.get(app);
        p.playList(eps, 0, "Noticias al día");
        settle();
        assertTrue(p.isPlaying());
        assertEquals(lookups, net.resolved.size());
        assertTrue(opened.get(opened.size() - 1).startsWith("file:"));
    }

    @Test
    public void downloadAllQueuesThemOneAfterAnother() throws Exception {
        List<Track> songs = audius();
        Downloads d = Downloads.get(app);
        assertEquals(3, d.downloadAll(songs.subList(0, 3)));
        assertEquals("one at a time", 1, count(d, songs, Downloads.RUNNING));
        assertEquals(2, count(d, songs, Downloads.QUEUED));
        settle();
        assertEquals(3, count(d, songs, Downloads.DONE));
        assertEquals("in the order they were asked", "https://api.audius.co/v1/tracks/t0/stream?app_name=Sonora", net.saved.get(0));
        assertEquals("https://api.audius.co/v1/tracks/t2/stream?app_name=Sonora", net.saved.get(2));
        assertEquals("nothing new to add", 0, d.downloadAll(songs.subList(0, 3)));
        assertEquals("newest first", songs.get(2), d.tracks().get(0));
    }

    static int count(Downloads d, List<Track> l, int state) {
        int n = 0;
        for (Track t : l) if (d.state(t) == state) n++;
        return n;
    }

    @Test
    public void failedDownloadsCanBeRetriedAndWiFiOnlyWaits() throws Exception {
        Track t = audius().get(1);
        Downloads d = Downloads.get(app);
        net.offline = true;
        d.download(t);
        settle();
        assertEquals(Downloads.FAILED, d.state(t));
        net.offline = false;
        d.download(t);
        settle();
        assertEquals(Downloads.DONE, d.state(t));

        // on mobile data with "only on Wi-Fi": it waits, then goes when allowed
        Downloads.metered = new Downloads.Metered() {
            @Override
            public boolean now(android.content.Context c) {
                return true;
            }
        };
        Track u = audius().get(2);
        assertTrue(d.wifiOnly());
        d.download(u);
        settle();
        assertEquals(Downloads.WAITING_WIFI, d.state(u));
        d.setWifiOnly(false);
        settle();
        assertEquals(Downloads.DONE, d.state(u));
    }

    @Test
    public void cancellingMidwayLeavesNoFile() throws Exception {
        Track t = audius().get(3);
        Downloads d = Downloads.get(app);
        net.holdDownloads = new java.util.concurrent.CountDownLatch(1);
        d.download(t);
        for (int i = 0; i < 40 && d.percent(t) < 50; i++) settle();
        assertEquals(Downloads.RUNNING, d.state(t));
        assertEquals(50, d.percent(t));
        d.remove(t);
        net.holdDownloads.countDown();
        settle();
        assertEquals(Downloads.NONE, d.state(t));
        File[] left = d.dir().listFiles();
        assertEquals("no file or half file left", 0, left == null ? 0 : left.length);
    }

    @Test
    public void downloadsSurviveARestartAndForgetDeletedFiles() throws Exception {
        List<Track> songs = audius();
        Downloads d = Downloads.get(app);
        d.download(songs.get(0));
        d.download(songs.get(1));
        settle();
        File gone = d.file(songs.get(1));
        net.holdDownloads = new java.util.concurrent.CountDownLatch(1); // a third one is left half way
        d.download(songs.get(2));
        settle();
        // the app closes: the file of the second one is deleted by the system meanwhile
        ContentTest.forget(Downloads.class);
        assertTrue(gone.delete());
        net.holdDownloads.countDown();
        net.holdDownloads = null;
        Downloads again = Downloads.get(app);
        assertEquals(Downloads.DONE, again.state(songs.get(0)));
        assertNotNull(again.file(songs.get(0)));
        assertEquals(Downloads.NONE, again.state(songs.get(1)));
        assertEquals("unfinished: queued again", Downloads.QUEUED, again.state(songs.get(2)));
        again.resume();
        settle();
        assertEquals(Downloads.DONE, again.state(songs.get(2)));
        assertEquals("Tema completo 1", again.tracks().get(again.tracks().size() - 1).title);
    }

    @Test
    public void libraryShowsDownloadsAndPagesCanDownloadEverything() throws Exception {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
        settle();
        MainActivity a = c.get();
        View root = a.getWindow().getDecorView();
        java.lang.reflect.Method open = null;
        for (java.lang.reflect.Method m : MainActivity.class.getDeclaredMethods()) if (m.getName().equals("open")) open = m;
        open.setAccessible(true);
        Class<?> colCls = null;
        for (Class<?> k : MainActivity.class.getDeclaredClasses()) if (k.getSimpleName().equals("Collection")) colCls = k;
        java.lang.reflect.Constructor<?> ctor = colCls.getDeclaredConstructor(MainActivity.class, int.class, String.class);
        ctor.setAccessible(true);
        open.invoke(a, ctor.newInstance(a, 2, "full-trending"));
        settle();
        SmokeTest.layout(root);
        View dl = null;
        List<View> all = new ArrayList<>();
        SmokeTest.collect(root, all);
        for (View v : all) if (v.getContentDescription() != null && "Descargar todo".contentEquals(v.getContentDescription())) dl = v;
        assertNotNull("download-all button", dl);
        assertEquals(View.VISIBLE, dl.getVisibility());
        dl.performClick();
        assertEquals("Descargando 12 canciones", ShadowToast.getTextOfLatestToast());
        settle();
        assertEquals(12, Downloads.get(app).tracks().size());
        SmokeTest.layout(root);
        assertNotNull("rows say it", SmokeTest.findText(root, "Descargada · Completa · Productora 1"));

        a.onBackPressed();
        java.lang.reflect.Method sel = MainActivity.class.getDeclaredMethod("selectTab", int.class);
        sel.setAccessible(true);
        sel.invoke(a, 3);
        settle();
        SmokeTest.layout(root);
        assertNotNull("Tu biblioteca lists the downloads", SmokeTest.findText(root, "Descargas"));
        c.pause().stop().destroy();
    }

    @Test
    public void settingsShowSpaceAndCanDeleteEverything() throws Exception {
        List<Track> songs = audius();
        Downloads d = Downloads.get(app);
        d.downloadAll(songs.subList(0, 4));
        settle();
        ActivityController<SettingsActivity> c = Robolectric.buildActivity(SettingsActivity.class,
                new Intent(app, SettingsActivity.class)).setup();
        settle();
        View root = c.get().getWindow().getDecorView();
        SmokeTest.layout(root);
        assertNotNull(SmokeTest.findText(root, "4 canciones · 1 MB en este teléfono"));
        assertNotNull(SmokeTest.findText(root, "Descargar solo con Wi-Fi"));
        assertNotNull(SmokeTest.findText(root, "Solo canciones completas"));
        SmokeTest.clickText(root, "Borrar todas las descargas");
        android.app.AlertDialog dlg = (android.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
        dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
        settle();
        assertEquals(0, d.tracks().size());
        SmokeTest.layout(root);
        assertNotNull(SmokeTest.findText(root, "No hay canciones ni episodios descargados"));
        c.pause().stop().destroy();
    }

    @Test
    public void fileNamesAreReadableAndUnique() {
        Track a = Track.full("au:1", "Canción Ñandú (Remix)", "X", "Y", 0, null, null, "https://x/stream?app_name=S");
        Track b = Track.full("ia:x/02 - Mar.ogg", "Mar", "X", "Y", 0, null, null, "https://archive.org/download/x/02%20-%20Mar.ogg");
        assertTrue(Downloads.fileName(a), Downloads.fileName(a).endsWith("-cancion-nandu-remix.mp3"));
        assertTrue(Downloads.fileName(b).endsWith("-mar.ogg"));
        assertNotEquals(Downloads.fileName(a), Downloads.fileName(Track.full("au:2", "Canción Ñandú (Remix)", "X", "Y", 0, null, null, "u")));
        assertEquals("1 MB", SettingsActivity.size(1024 * 1024));
        assertEquals("1.5 GB", SettingsActivity.size(1536L * 1024 * 1024));
    }
}

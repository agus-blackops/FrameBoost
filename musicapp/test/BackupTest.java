package com.agus.sonora;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 2.0: backup and restore, M3U playlists and OPML podcasts. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class BackupTest {

    Fixtures net;
    Application app;

    @Before
    public void reset() throws Exception {
        forgetAll();
        Effects.resetForTest();
        PlaybackService.running = false;
        Locale.setDefault(Locale.US);
        net = new Fixtures();
        Net.fetcher = net;
        Net.resolver = net;
        app = RuntimeEnvironment.getApplication();
        shadowOf(app).grantPermissions(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS);
        PlayerEngine.audioFactory = SimAudio.factory(new SimAudio.Durations() {
            @Override
            public long of(String uri) {
                return 60000;
            }
        }, new ArrayList<String>());
    }

    static void forgetAll() throws Exception {
        for (Class<?> k : new Class<?>[]{Library.class, PlayerEngine.class, Downloads.class}) {
            java.lang.reflect.Field f = k.getDeclaredField("sInstance");
            f.setAccessible(true);
            f.set(null, null);
        }
    }

    /** As if this phone's music scan found these files. */
    @SuppressWarnings("unchecked")
    static void setLocal(Library lib, List<Track> tracks) throws Exception {
        java.lang.reflect.Field f = Library.class.getDeclaredField("local");
        f.setAccessible(true);
        f.set(lib, new ArrayList<>(tracks));
        java.lang.reflect.Field k = Library.class.getDeclaredField("byKey");
        k.setAccessible(true);
        Map<String, Track> byKey = (Map<String, Track>) k.get(lib);
        for (Track t : tracks) byKey.put(t.key, t);
    }

    /** A brand-new phone: no saved data at all. */
    void newPhone() throws Exception {
        forgetAll();
        for (String name : new String[]{"library", "player", "downloads", "appearance", "podcastAlerts"}) {
            app.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit();
        }
    }

    static void settle() throws Exception {
        SmokeTest.settle();
    }

    Library.Section loaded(String id) throws Exception {
        Library lib = Library.get(app);
        Library.Section s = lib.section(id);
        lib.loadSection(s);
        settle();
        return s;
    }

    @Test
    public void aBackupBringsEverythingToAnotherPhone() throws Exception {
        Library lib = Library.get(app);
        Track mine = new Track("content://media/external/audio/media/7", "De Música Ligera", "Soda Stereo", "Canción Animal", 0, 210000, false, 1);
        setLocal(lib, java.util.Collections.singletonList(mine));
        Track preview = loaded("top").tracks.get(1);
        Track full = loaded("full-trending").tracks.get(0);
        lib.toggleLike(preview);
        lib.toggleLike(full);
        lib.toggleLike(mine);
        lib.createPlaylist("Para correr");
        lib.addToPlaylist("Para correr", full);
        lib.addToPlaylist("Para correr", mine);
        Library.Item pod = new Library.Item(Library.Item.PODCAST, 201, "Noticias al día", "Voces", null, "https://feeds.example.com/201.xml");
        lib.loadItemTracks(pod);
        settle();
        lib.toggleFollow(pod);
        List<Track> eps = lib.itemTracks(pod);
        lib.setPlayed(eps.get(0), true);
        lib.saveProgress(eps.get(1), 600000, 3723000);
        PlayerEngine.get(app).setCrossfade(6000);
        lib.setFullOnly(true);
        Theme.setMode(app, Theme.LIGHT);

        String backup = Backup.export(app);
        JSONObject o = new JSONObject(backup);
        assertEquals("Sonora", o.getString("app"));
        assertEquals(1, o.getInt("format"));

        // a new phone where the same song file has another id
        newPhone();
        Library fresh = Library.get(app);
        assertTrue(fresh.likedTracks().isEmpty());
        Track sameSong = new Track("content://media/external/audio/media/99", "De Música Ligera", "Soda Stereo", "Canción Animal", 0, 210000, false, 1);
        setLocal(fresh, java.util.Collections.singletonList(sameSong));
        Library.ImportResult r = Backup.restore(app, backup);
        assertEquals(3, r.liked);
        assertEquals(1, r.playlists);
        assertEquals(1, r.podcasts);
        assertEquals(0, r.missing);
        assertEquals("Se agregó: 3 canciones en Me gusta, 1 playlist, 1 podcast seguido.", r.summary());

        List<Track> liked = fresh.likedTracks();
        assertEquals(3, liked.size());
        assertTrue(liked.contains(sameSong));
        assertEquals("the preview keeps its info", preview.title, liked.get(liked.indexOf(preview)).title);
        assertEquals(2, fresh.playlist("Para correr").size());
        assertTrue("your file, found by title and artist", fresh.playlist("Para correr").contains(sameSong));
        assertTrue(fresh.isFollowing(pod));
        assertTrue(fresh.isPlayed(eps.get(0)));
        assertEquals(600000, fresh.progress(eps.get(1)));
        assertEquals(6000, PlayerEngine.get(app).crossfadeMs());
        assertTrue(fresh.fullOnly());
        assertEquals(Theme.LIGHT, Theme.mode(app));

        // restoring twice adds nothing
        Library.ImportResult again = Backup.restore(app, backup);
        assertEquals("No había nada nuevo para agregar.", again.summary());
        assertEquals(3, fresh.likedTracks().size());
    }

    @Test
    public void restoringMergesAndReportsMissingFiles() throws Exception {
        Library lib = Library.get(app);
        Track mine = new Track("content://1", "Canción perdida", "Yo", "Demo", 0, 1000, false, 1);
        setLocal(lib, java.util.Collections.singletonList(mine));
        lib.toggleLike(mine);
        lib.createPlaylist("Mix");
        lib.addToPlaylist("Mix", mine);
        String backup = Backup.export(app);
        newPhone();
        Library fresh = Library.get(app);
        Track other = loaded("full-trending").tracks.get(3);
        fresh.createPlaylist("Mix");
        fresh.addToPlaylist("Mix", other);
        Library.ImportResult r = Backup.restore(app, backup);
        assertEquals(1, r.missing);
        assertTrue(r.summary().contains("1 canción de tu teléfono no se encontraron en este"));
        assertEquals("existing playlist kept, nothing removed", 1, fresh.playlist("Mix").size());
        assertEquals(0, r.playlists);

        try {
            Backup.restore(app, "{\"hola\":1}");
            fail();
        } catch (Exception e) {
            assertEquals("El archivo no es una copia de seguridad de Sonora", e.getMessage());
        }
        try {
            Backup.restore(app, "{\"app\":\"Sonora\",\"format\":9,\"library\":{}}");
            fail();
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("más nueva"));
        }
    }

    @Test
    public void m3uPlaylistsAreMatchedToYourMusic() throws Exception {
        Library lib = Library.get(app);
        List<Track> mine = new ArrayList<>();
        mine.add(new Track("content://1", "De Música Ligera", "Soda Stereo", "Canción Animal", 0, 1000, false, 1));
        mine.add(new Track("content://2", "Persiana Americana", "Soda Stereo", "Signos", 0, 1000, false, 1));
        mine.add(new Track("content://3", "Tren al Sur", "Los Prisioneros", "Corazones", 0, 1000, false, 1));
        setLocal(lib, mine);
        String m3u = "\uFEFF#EXTM3U\r\n"
                + "#EXTINF:210,Soda Stereo - De Música Ligera\r\n"
                + "C:\\Musica\\Soda\\01 - cualquier nombre.mp3\r\n"
                + "/sdcard/Music/Soda Stereo - Persiana Americana.mp3\r\n"
                + "#EXTINF:-1,Radio libre\r\n"
                + "https://stream.example.com/libre.mp3\r\n"
                + "#EXTINF:200,Alguien - No está\r\n"
                + "/sdcard/Music/no-esta.mp3\r\n"
                + "# comentario\r\n"
                + "Los_Prisioneros_-_Tren_al_Sur.flac\r\n";
        List<Library.M3uEntry> entries = Backup.parseM3u(m3u);
        assertEquals(5, entries.size());
        assertEquals("Soda Stereo", entries.get(0).artist);
        assertEquals(210000, entries.get(0).durationMs);
        assertEquals(0, entries.get(2).durationMs);
        Library.ImportResult r = lib.importM3u("Rock nacional", entries);
        assertEquals(1, r.playlists);
        assertEquals(4, r.playlistSongs);
        assertEquals(1, r.missing);
        List<Track> pl = lib.playlist("Rock nacional");
        assertEquals("De Música Ligera", pl.get(0).title);
        assertEquals("by file name", "Persiana Americana", pl.get(1).title);
        assertEquals("links are added as they are", "https://stream.example.com/libre.mp3", pl.get(2).streamUrl);
        assertEquals("Radio libre", pl.get(2).title);
        assertEquals("Tren al Sur", pl.get(3).title);
        // a second import with the same name doesn't overwrite
        lib.importM3u("Rock nacional", entries);
        assertNotNull(lib.playlist("Rock nacional (2)"));
        assertEquals("Mi lista", Backup.playlistName("content/Mi lista.m3u8"));
        assertArrayEquals(new String[]{"Soda Stereo", "Persiana Americana"}, Library.guessFromFileName("/x/Soda Stereo - Persiana Americana.mp3"));
    }

    @Test
    public void opmlInAndOut() throws Exception {
        Library lib = Library.get(app);
        String opml = "<?xml version=\"1.0\"?><opml version=\"1.0\"><head><title>x</title></head><body>"
                + "<outline text=\"Grupo\"><outline type=\"rss\" text=\"Ciencia &amp; Café\" xmlUrl=\"https://feeds.example.com/301.xml\"/>"
                + "<outline title=\"Historias\" xmlUrl=\"https://feeds.example.com/302.xml\"/></outline>"
                + "<outline text=\"Sin feed\"/></body></opml>";
        List<Library.Item> items = Backup.parseOpml(opml);
        assertEquals(2, items.size());
        assertEquals("Ciencia & Café", items.get(0).title);
        assertEquals("Historias", items.get(1).title);
        assertEquals("https://feeds.example.com/302.xml", items.get(1).feed);
        assertEquals(2, lib.followAll(items).podcasts);
        assertEquals("already followed", 0, lib.followAll(Backup.parseOpml(opml)).podcasts);
        // the episodes of an imported podcast load from its feed
        lib.loadItemTracks(items.get(0));
        settle();
        assertFalse(lib.itemTracks(items.get(0)).isEmpty());

        String out = Backup.exportOpml(lib.followedPodcasts());
        List<Library.Item> back = Backup.parseOpml(out);
        assertEquals(2, back.size());
        assertTrue(out.contains("text=\"Ciencia &amp; Café\""));
    }

    @Test
    public void ajustesSavesAndReadsFiles() throws Exception {
        Library lib = Library.get(app);
        lib.toggleLike(loaded("full-trending").tracks.get(0));
        ActivityController<SettingsActivity> c = Robolectric.buildActivity(SettingsActivity.class,
                new Intent(app, SettingsActivity.class)).setup();
        SettingsActivity a = c.get();
        View root = a.getWindow().getDecorView();
        SmokeTest.layout(root);
        SmokeTest.clickText(root, "Exportar copia de seguridad");
        Intent asked = shadowOf(a).getNextStartedActivityForResult().intent;
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, asked.getAction());
        assertTrue(asked.getStringExtra(Intent.EXTRA_TITLE).startsWith("sonora-copia-"));

        File file = new File(app.getCacheDir(), "copia.json");
        a.handleFile(SettingsActivity.REQ_EXPORT, Uri.fromFile(file));
        String saved = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        assertTrue(saved.contains("\"app\": \"Sonora\""));

        newPhone();
        a.handleFile(SettingsActivity.REQ_RESTORE, Uri.fromFile(file));
        assertEquals(1, Library.get(app).likedTracks().size());
        android.app.AlertDialog dlg = (android.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
        assertNotNull(dlg);

        File m3u = new File(app.getCacheDir(), "Mis links.m3u");
        try (FileOutputStream fo = new FileOutputStream(m3u)) {
            fo.write("#EXTM3U\nhttps://x.example.com/a.mp3\n".getBytes(StandardCharsets.UTF_8));
        }
        a.handleFile(SettingsActivity.REQ_M3U, Uri.fromFile(m3u));
        assertEquals("named after the file", 1, Library.get(app).playlist("Mis links").size());

        File bad = new File(app.getCacheDir(), "mal.json");
        try (FileOutputStream fo = new FileOutputStream(bad)) {
            fo.write("no es json".getBytes(StandardCharsets.UTF_8));
        }
        a.handleFile(SettingsActivity.REQ_RESTORE, Uri.fromFile(bad)); // reported, no crash
        c.pause().stop().destroy();
    }
}

package com.agus.sonora;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 1.9: complete songs without a subscription (Audius, Internet Archive), the "only complete
 * songs" mode, the full version of a preview, gapless playback and the discovery mix.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class FullMusicTest {

    Fixtures net;
    Application app;
    final List<String> opened = new ArrayList<>();
    /** Song length the simulated player reports, per link. */
    final Map<String, Long> lengths = new HashMap<>();

    @Before
    public void reset() throws Exception {
        for (Class<?> k : new Class<?>[]{Library.class, PlayerEngine.class}) {
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
        shadowOf(app).grantPermissions(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS);
        PlayerEngine.audioFactory = SimAudio.factory(new SimAudio.Durations() {
            @Override
            public long of(String uri) {
                for (Map.Entry<String, Long> e : lengths.entrySet()) if (uri.contains(e.getKey())) return e.getValue();
                return 30000;
            }
        }, opened);
    }

    static void settle() throws Exception {
        SmokeTest.settle();
    }

    static void idleFor(int ms) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));
    }

    Library.Section loaded(String id) throws Exception {
        Library lib = Library.get(app);
        Library.Section s = lib.section(id);
        lib.loadSection(s);
        settle();
        assertEquals("shelf " + id, Library.READY, s.state);
        return s;
    }

    // ------------------------------------------------------------------ Audius

    @Test
    public void audiusShelvesHaveCompleteSongsAndSkipLockedOnes() throws Exception {
        Library.Section s = loaded("full-trending");
        assertEquals("12 open songs; the one for followers only and the unstreamable one are left out", 12, s.tracks.size());
        Track t = s.tracks.get(0);
        assertEquals(Track.FULL, t.kind);
        assertTrue(t.isComplete());
        assertEquals("Audius", t.sourceName());
        assertEquals("au:t0", t.key);
        assertEquals("Tema completo 1", t.title);
        assertEquals("Productora 1", t.artist);
        assertEquals(150000, t.durationMs);
        assertEquals("https://api.audius.co/v1/tracks/t0/stream?app_name=Sonora", t.playUrl());
        assertEquals("https://audius.example/t0/1000.jpg", t.artUrl);
        for (Track x : s.tracks) assertFalse(x.title.contains("seguidores") || x.title.contains("Borrador"));

        Library.Section lofi = loaded("full-lofi");
        assertEquals("genre shelves ask for their genre", "Lo-Fi", lofi.tracks.get(0).album);
        assertTrue(net.urls.toString().contains("genre=Lo-Fi"));
    }

    @Test
    public void completeSongsPlayDirectlyAndFollowEachOtherWithoutAGap() throws Exception {
        Library.Section s = loaded("full-trending");
        lengths.put("/tracks/t0/", 20000L);
        lengths.put("/tracks/t1/", 25000L);
        PlayerEngine p = PlayerEngine.get(app);
        int urlsBefore = net.urls.size();
        p.playList(s.tracks, 0, "Canciones completas");
        settle();
        assertTrue(p.isPlaying());
        assertEquals("no link lookup before playing a complete song", urlsBefore, net.urls.size());
        assertEquals("https://api.audius.co/v1/tracks/t0/stream?app_name=Sonora", opened.get(opened.size() - 1));
        assertEquals(20000, p.duration());

        // 20 s song: the next one is preloaded inside the last 12 s and takes over without a gap
        for (int i = 0; i < 30 && p.queueIndex() == 0; i++) idleFor(1000);
        assertEquals(1, p.queueIndex());
        assertEquals("Tema completo 2", p.current().title);
        assertEquals("handed over by the player, not reloaded", 1, p.gaplessTransitions);
        assertTrue(p.isPlaying());
        assertEquals(25000, p.duration());
        assertTrue("it counts as played", Library.get(app).recentlyPlayed(5).contains(s.tracks.get(1)));

        // changing the queue forgets a preloaded song that no longer follows
        for (int i = 0; i < 16; i++) idleFor(1000); // inside the last 12 s of song 2: song 3 is preloaded
        p.jumpTo(5);
        settle();
        assertEquals("Tema completo 6", p.current().title);
        for (int i = 0; i < 40 && p.queueIndex() == 5; i++) idleFor(1000);
        assertEquals("after song 6 comes song 7, not the old preloaded one", "Tema completo 7", p.current().title);
    }

    @Test
    public void repeatOneAndStopAfterThisSongDoNotPreload() throws Exception {
        Library.Section s = loaded("full-trending");
        lengths.put("/tracks/t0/", 15000L);
        PlayerEngine p = PlayerEngine.get(app);
        p.playList(s.tracks, 0, "x");
        settle();
        p.cycleRepeat();
        p.cycleRepeat();
        assertEquals(PlayerEngine.REPEAT_ONE, p.repeatMode());
        for (int i = 0; i < 20; i++) idleFor(1000);
        assertEquals("repeat one plays the same song again", 0, p.queueIndex());
        assertTrue(p.isPlaying());
        assertEquals(0, p.gaplessTransitions);
        p.cycleRepeat();
        p.sleepAtEndOfTrack();
        for (int i = 0; i < 20; i++) idleFor(1000);
        assertFalse("stopped after the song", p.isPlaying());
        assertEquals(1, p.queueIndex());
        assertEquals(0, p.gaplessTransitions);
    }

    // ------------------------------------------------------------------ Internet Archive

    @Test
    public void archiveAlbumsListOneFormatInTrackOrder() throws Exception {
        Library lib = Library.get(app);
        Library.Section s = loaded("archive");
        assertEquals(4, s.items.size());
        Library.Item album = s.items.get(0);
        assertEquals(Library.Item.ARCHIVE, album.kind);
        assertEquals("Noches de Verano", album.title);
        assertEquals("creator given as a list", "Colectivo Sur", album.subtitle);
        assertEquals("https://archive.org/services/img/jamendo-001", album.art);
        assertEquals("jamendo-001", album.feed);
        assertEquals("Bruma", s.items.get(1).subtitle);

        lib.loadItemTracks(album);
        settle();
        List<Track> tracks = lib.itemTracks(album);
        assertEquals("only the VBR MP3 copies, one per song", 3, tracks.size());
        assertEquals("01 Intro #1", tracks.get(0).title);
        assertEquals("Mar Abierto", tracks.get(1).title);
        assertEquals("Despedida", tracks.get(2).title);
        assertEquals(65000, tracks.get(0).durationMs);
        assertEquals(241200, tracks.get(1).durationMs);
        assertEquals("Colectivo Sur", tracks.get(1).artist);
        assertEquals("Colectivo Sur y amigos", tracks.get(2).artist);
        assertEquals("Noches de Verano", tracks.get(1).album);
        assertEquals("Internet Archive", tracks.get(1).sourceName());
        assertEquals(Track.FULL, tracks.get(0).kind);
        assertEquals("https://archive.org/download/jamendo-001/01%20Intro%20%231.mp3", tracks.get(0).playUrl());
        assertEquals("ia:jamendo-001/02 - Mar Abierto.mp3", tracks.get(1).key);

        Library.Item empty = s.items.get(3);
        lib.loadItemTracks(empty);
        settle();
        assertEquals("an item without audio shows as failed", Library.FAILED, lib.itemState(empty));
    }

    @Test
    public void archiveHelpers() {
        assertEquals(245.5, Library.parseLength("245.5"), 0.001);
        assertEquals(245, Library.parseLength("4:05"), 0.001);
        assertEquals(3723, Library.parseLength("1:02:03"), 0.001);
        assertEquals(0, Library.parseLength("?"), 0.001);
        assertEquals("a%20b/c%23d%C3%B1", Net.encPath("a b/c#dñ"));
        assertTrue(Library.archiveId("x") > 0);
        assertEquals(Library.archiveId("abc"), Library.archiveId("abc"));
    }

    // ------------------------------------------------------------------ only complete songs

    @Test
    public void onlyCompleteSongsHidesPreviewsEverywhere() throws Exception {
        Library lib = Library.get(app);
        Set<String> all = new HashSet<>();
        for (Library.Section s : lib.visibleSections()) all.add(s.id);
        assertTrue(all.contains("top") && all.contains("releases"));

        lib.setFullOnly(true);
        Set<String> visible = new HashSet<>();
        for (Library.Section s : lib.visibleSections()) visible.add(s.id);
        for (String gone : new String[]{"top", "releases", "artists", "playlists", "reggaeton", "fandom-artists", "tls", "genres", "lofi"}) {
            assertFalse(gone + " is previews", visible.contains(gone));
        }
        for (String kept : new String[]{"full-trending", "archive", "radio", "podcasts", "radio-news", "full-lofi", Library.MIX}) {
            assertTrue(kept + " stays", visible.contains(kept));
        }

        // search: no Deezer request, only complete songs and radios
        final List<Track> got = new ArrayList<>();
        int before = net.urls.size();
        lib.searchOnline("lluvia", new Library.Results() {
            @Override
            public void onResults(List<Track> tracks, boolean failed) {
                got.addAll(tracks);
            }
        });
        settle();
        assertFalse(got.isEmpty());
        for (Track t : got) assertTrue(t.title + " is complete", t.isComplete());
        for (String u : net.urls.subList(before, net.urls.size())) assertFalse(u, u.contains("api.deezer.com"));

        // and the setting is remembered
        PlayerEngine.get(app).stop();
        ContentTest.forget(Library.class);
        assertTrue(Library.get(app).fullOnly());
    }

    @Test
    public void searchMixesPreviewsCompleteSongsAndRadios() throws Exception {
        final List<Track> got = new ArrayList<>();
        Library.get(app).searchOnline("lluvia", new Library.Results() {
            @Override
            public void onResults(List<Track> tracks, boolean failed) {
                got.addAll(tracks);
            }
        });
        settle();
        boolean preview = false, full = false, radio = false;
        for (Track t : got) {
            preview |= t.kind == Track.PREVIEW;
            full |= t.kind == Track.FULL;
            radio |= t.isLive();
        }
        assertTrue(preview && full && radio);
    }

    @Test
    public void homeHasTheFreeCardAndItsSwitchHidesPreviewShelves() throws Exception {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
        settle();
        View root = c.get().getWindow().getDecorView();
        SmokeTest.layout(root);
        assertNotNull(SmokeTest.findText(root, "Gratis y sin suscripción"));
        assertNotNull(SmokeTest.findText(root, "Canciones completas"));
        assertNotNull(SmokeTest.findText(root, "Álbumes libres"));
        assertNotNull(SmokeTest.findText(root, "Mezcla de descubrimiento"));
        assertFalse(ContentTest.texts(root, "Nuevos lanzamientos").isEmpty());

        List<View> all = new ArrayList<>();
        SmokeTest.collect(root, all);
        android.widget.Switch only = null;
        for (View v : all) if (v instanceof android.widget.Switch) only = (android.widget.Switch) v;
        assertNotNull(only);
        assertEquals("Solo canciones completas", only.getText().toString());
        only.performClick();
        settle();
        SmokeTest.layout(root);
        assertTrue(Library.get(app).fullOnly());
        assertTrue("preview shelves are gone", ContentTest.texts(root, "Nuevos lanzamientos").isEmpty());
        assertNotNull(SmokeTest.findText(root, "Canciones completas"));
        c.pause().stop().destroy();
    }

    // ------------------------------------------------------------------ full version of a preview

    @Test
    public void theFullVersionMustBeTheSameSongByTheSameArtist() throws Exception {
        Library lib = Library.get(app);
        Library.Section top = loaded("top");
        Track queen = null, flowers = null;
        for (Track t : top.tracks) {
            if (t.title.equals("Bohemian Rhapsody") && queen == null) queen = t;
            if (t.title.equals("Flowers") && flowers == null) flowers = t;
        }
        final Track[] found = new Track[2];
        final boolean[] answered = new boolean[2];
        lib.findFullVersion(queen, new Library.FullVersion() {
            @Override
            public void onFound(Track full) {
                found[0] = full;
                answered[0] = true;
            }
        });
        lib.findFullVersion(flowers, new Library.FullVersion() {
            @Override
            public void onFound(Track full) {
                found[1] = full;
                answered[1] = true;
            }
        });
        settle();
        assertTrue(answered[0] && answered[1]);
        assertNotNull("same title once \"- Remastered 2011\" is dropped, same artist", found[0]);
        assertEquals("au:qbr2", found[0].key);
        assertNull("a cover or a fan account is not the song", found[1]);

        // asked again: answered from memory, no new request
        int calls = net.audiusCalls.get();
        lib.findFullVersion(flowers, new Library.FullVersion() {
            @Override
            public void onFound(Track full) {
                answered[1] = full == null;
            }
        });
        assertEquals(calls, net.audiusCalls.get());
        assertEquals("bohemian rhapsody", Library.baseTitle("Bohemian Rhapsody (Live at Wembley) - Remastered"));
    }

    @Test
    public void nowPlayingOffersTheCompleteSongAndSwitchesToIt() throws Exception {
        Library.Section top = loaded("top");
        int queen = -1;
        for (int i = 0; i < top.tracks.size() && queen < 0; i++) if (top.tracks.get(i).title.equals("Bohemian Rhapsody")) queen = i;
        PlayerEngine p = PlayerEngine.get(app);
        p.playList(top.tracks, queen, "Top 50");
        settle();
        ActivityController<NowPlayingActivity> c = Robolectric.buildActivity(NowPlayingActivity.class,
                new Intent(app, NowPlayingActivity.class)).setup();
        settle();
        View root = c.get().getWindow().getDecorView();
        SmokeTest.layout(root);
        assertNotNull(SmokeTest.findText(root, "VISTA PREVIA · 30 s"));
        TextView offer = null;
        List<View> all = new ArrayList<>();
        SmokeTest.collect(root, all);
        for (View v : all) {
            if (v instanceof TextView && v.isShown() && ((TextView) v).getText().toString().contains("Escuchar completa")) offer = (TextView) v;
        }
        assertNotNull("offer shown", offer);
        assertTrue(offer.getText().toString().contains("Audius"));
        offer.performClick();
        settle();
        assertEquals("au:qbr2", p.current().key);
        assertEquals("same place in the queue", queen, p.queueIndex());
        assertTrue(p.isPlaying());
        SmokeTest.layout(root);
        assertNotNull(SmokeTest.findText(root, "CANCIÓN COMPLETA · AUDIUS"));
        c.pause().stop().destroy();
    }

    // ------------------------------------------------------------------ discovery mix

    static Set<String> artists(List<Track> l) {
        Set<String> out = new HashSet<>();
        for (Track t : l) out.add(t.artist);
        return out;
    }

    @Test
    public void withoutHistoryTheMixIsAStarterOfWhatsTrending() throws Exception {
        Library lib = Library.get(app);
        Library.Section mix = loaded(Library.MIX);
        assertFalse(lib.mixIsPersonal());
        boolean preview = false, full = false;
        for (Track t : mix.tracks) {
            preview |= t.kind == Track.PREVIEW;
            full |= t.kind == Track.FULL;
        }
        assertTrue("chart previews and complete songs, alternating", preview && full);
        assertTrue(mix.tracks.size() <= Library.MIX_SIZE);
    }

    @Test
    public void theMixBringsNewArtistsCloseToTheOnesYouLike() throws Exception {
        Library lib = Library.get(app);
        Library.Section top = loaded("top");
        Library.Section free = loaded("full-trending");
        Track hips = null, monaco = null;
        for (Track t : top.tracks) {
            if (t.title.equals("Hips Don't Lie") && hips == null) hips = t;
            if (t.title.equals("Monaco") && monaco == null) monaco = t;
        }
        lib.toggleLike(hips);
        lib.toggleLike(free.tracks.get(0)); // an Audius song: its genre guides the complete songs
        lib.recordPlayed(monaco);
        lib.recordPlayed(free.tracks.get(1));

        Library.Section mix = lib.section(Library.MIX);
        mix.state = Library.IDLE;
        lib.loadSection(mix);
        settle();
        assertEquals(Library.READY, mix.state);
        assertTrue(lib.mixIsPersonal());
        List<Track> songs = mix.tracks;
        assertTrue(songs.size() >= 10 && songs.size() <= Library.MIX_SIZE);

        Set<String> known = new HashSet<>();
        for (Track t : lib.likedTracks()) known.add(t.key);
        for (Track t : lib.recentlyPlayed(50)) known.add(t.key);
        Map<String, Integer> perArtist = new HashMap<>();
        boolean related = false, full = false;
        for (Track t : songs) {
            assertFalse("nothing you already know: " + t.title, known.contains(t.key));
            assertFalse("not your own artists: " + t.artist, t.artist.equals("Shakira") || t.artist.equals("Bad Bunny"));
            Integer n = perArtist.get(t.artist);
            perArtist.put(t.artist, n == null ? 1 : n + 1);
            related |= t.artist.startsWith("Nuevo Artista");
            full |= t.kind == Track.FULL;
        }
        for (Map.Entry<String, Integer> e : perArtist.entrySet()) assertTrue("at most 2 by " + e.getKey(), e.getValue() <= 2);
        assertTrue("related artists from the catalog", related);
        assertTrue("complete songs from Audius", full);
        assertTrue("the Audius genre you play was used", net.urls.toString().contains("trending?limit=30&genre=Electronic"));
        assertTrue("Shakira's related artists were asked by her id", net.urls.toString().contains("/artist/51/related"));

        // the same day gives the same mix; another day, another order
        List<Track> again = Library.discoveryMix(lib.likedTracks(), lib.recentlyPlayed(50), false, Library.today());
        List<String> a = new ArrayList<>(), b = new ArrayList<>();
        for (Track t : songs) a.add(t.key);
        for (Track t : again) b.add(t.key);
        assertEquals(a, b);
        List<Track> tomorrow = Library.discoveryMix(lib.likedTracks(), lib.recentlyPlayed(50), false, Library.today() + 1);
        List<String> c2 = new ArrayList<>();
        for (Track t : tomorrow) c2.add(t.key);
        assertNotEquals(a, c2);

        // only complete songs: the mix keeps only those
        for (Track t : Library.discoveryMix(lib.likedTracks(), lib.recentlyPlayed(50), true, Library.today())) {
            assertEquals(Track.FULL, t.kind);
        }

        // and it plays like any shelf
        PlayerEngine p = PlayerEngine.get(app);
        p.playList(songs, 0, mix.title);
        settle();
        assertTrue(p.isPlaying());
        assertEquals("Mezcla de descubrimiento", p.queueName());
    }

    @Test
    public void offlineMixFailsAndRetries() throws Exception {
        Library lib = Library.get(app);
        net.offline = true;
        Library.Section mix = lib.section(Library.MIX);
        lib.loadSection(mix);
        settle();
        assertEquals(Library.FAILED, mix.state);
        net.offline = false;
        lib.loadSection(mix);
        settle();
        assertEquals(Library.READY, mix.state);
    }

    // ------------------------------------------------------------------ radio: song on air

    @Test
    public void radioShowsTheSongOnAir() throws Exception {
        Library.Section radio = loaded("radio");
        PlayerEngine p = PlayerEngine.get(app);
        assertNull(p.liveTitle());
        p.playList(radio.tracks, 0, "Radios");
        settle();
        assertEquals("from the station's metadata", "Canción al aire", p.liveTitle());
        Library.Section s = loaded("full-trending");
        p.playList(s.tracks, 0, "x");
        settle();
        assertNull("only radios have it", p.liveTitle());
    }
}

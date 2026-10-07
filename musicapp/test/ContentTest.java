package com.agus.sonora;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
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
import org.robolectric.shadows.ShadowMediaPlayer;
import org.robolectric.shadows.util.DataSource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 1.6 content: albums, artists, playlists, genres, more radios and podcasts. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ContentTest {

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
        Locale.setDefault(Locale.US);
        net = new Fixtures();
        Net.fetcher = net;
        Net.resolver = net;
        app = RuntimeEnvironment.getApplication();
        shadowOf(app).grantPermissions(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS);
        ShadowMediaPlayer.setMediaInfoProvider(new ShadowMediaPlayer.MediaInfoProvider() {
            @Override
            public ShadowMediaPlayer.MediaInfo get(DataSource ds) {
                return new ShadowMediaPlayer.MediaInfo(2530000, 50);
            }
        });
    }

    static void forget(Class<?> k) throws Exception {
        java.lang.reflect.Field f = k.getDeclaredField("sInstance");
        f.setAccessible(true);
        f.set(null, null);
    }

    static void settle() throws Exception {
        SmokeTest.settle();
    }

    ActivityController<MainActivity> openMain() throws Exception {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
        settle();
        SmokeTest.layout(c.get().getWindow().getDecorView());
        return c;
    }

    static List<TextView> texts(View root, String exact) {
        List<View> all = new ArrayList<>();
        SmokeTest.collect(root, all);
        List<TextView> out = new ArrayList<>();
        for (View v : all) {
            if (v instanceof TextView && v.isShown() && exact.equals(((TextView) v).getText().toString())) out.add((TextView) v);
        }
        return out;
    }

    // ------------------------------------------------------------------ parsing

    @Test
    public void parsingHelpers() throws Exception {
        assertEquals(2530000, Library.parseDuration("42:10"));
        assertEquals(3723000, Library.parseDuration("3723"));
        assertEquals(3723000, Library.parseDuration("1:02:03"));
        assertEquals(0, Library.parseDuration(""));
        assertEquals(0, Library.parseDuration("abc"));
        assertEquals(0, Library.parseDuration(null));
        long a = Library.parsePubDate("Fri, 03 Oct 2025 08:00:00 +0000");
        long b = Library.parsePubDate("Fri, 3 Oct 2025 08:00:00 GMT");
        assertTrue(a > 0);
        assertEquals(a, b);
        assertTrue(Library.parsePubDate("2025-10-03") > 0);
        assertEquals(0, Library.parsePubDate("ayer"));
        assertEquals(0, Library.parsePubDate(null));
    }

    @Test
    public void rssKeepsOnlyAudioEpisodes() throws Exception {
        Library.Item pod = new Library.Item(Library.Item.PODCAST, 7, "Mi podcast", "Yo", "https://art.example/p.jpg", null);
        List<Track> eps = Library.parseEpisodes(Fixtures.rss("Mi podcast"), pod);
        assertEquals("video, text-only and enclosure-less items are skipped", 3, eps.size());
        assertEquals("Episodio 3: El final", eps.get(0).title);
        assertEquals("Mi podcast", eps.get(0).artist);
        assertEquals(2530000, eps.get(0).durationMs);
        assertTrue(eps.get(0).isEpisode());
        assertTrue(eps.get(0).remote);
        assertFalse(eps.get(0).isLive());
        assertEquals("ep:4:7:g-3", eps.get(0).key);
        assertEquals("http://traffic.example.com/ep3.mp3", eps.get(0).streamUrl);
        assertTrue(eps.get(0).dateAdded > 0);
        assertEquals("episode art falls back to the podcast's", "https://art.example/p.jpg", eps.get(0).artUrl);
        assertEquals("Episodio 2", eps.get(1).title);
        assertEquals(3723000, eps.get(1).durationMs);
        assertEquals("episode image wins", "https://img.example.com/ep2.jpg", eps.get(1).artUrl);
        assertTrue("no guid: the link identifies it", eps.get(1).key.endsWith("https://cdn.example.com/ep2.mp3"));
        assertEquals(3723000, eps.get(2).durationMs);
        // when the podcast has no art, the channel's image is used
        Library.Item bare = new Library.Item(Library.Item.PODCAST, 8, "Sin arte", "Yo", null, null);
        assertEquals("https://img.example.com/channel.jpg", Library.parseEpisodes(Fixtures.rss("x"), bare).get(0).artUrl);
        // survives being stored and read back
        Track back = Track.fromJson(eps.get(0).toJson());
        assertTrue(back.isEpisode());
        assertEquals(eps.get(0).key, back.key);
        assertEquals(eps.get(0).dateAdded, back.dateAdded == 0 ? eps.get(0).dateAdded : back.dateAdded);
        assertEquals(eps.get(0).streamUrl, back.streamUrl);
    }

    @Test
    public void itemParsing() throws Exception {
        List<Library.Item> albums = Library.parseItems(Library.Item.ALBUM, Fixtures.items("album", 3));
        assertEquals(3, albums.size());
        assertEquals("Álbum 1", albums.get(0).title);
        assertEquals("Bad Bunny", albums.get(0).subtitle);
        assertEquals("https://cdn.dz.example/alb0/500x500.jpg", albums.get(0).art);
        List<Library.Item> lists = Library.parseItems(Library.Item.PLAYLIST, Fixtures.items("playlist", 2));
        assertEquals("20 canciones", lists.get(0).subtitle);
        List<Library.Item> artists = Library.parseItems(Library.Item.ARTIST, Fixtures.items("artist", 2));
        assertEquals("Artista", artists.get(0).subtitle);
        List<Library.Item> pods = Library.parseItems(Library.Item.PODCAST, Fixtures.podcastsTop());
        assertEquals("duplicates and id-less entries dropped", 3, pods.size());
        assertEquals("Radio Ambulante", pods.get(0).title);
        assertEquals("NPR", pods.get(0).subtitle);
        assertEquals("art upgraded to the large size", "https://is1-ssl.mzstatic.com/image/p101/600x600bb.jpg", pods.get(0).art);
        assertNull("top chart carries no feed", pods.get(0).feed);
        List<Library.Item> found = Library.parseItems(Library.Item.PODCAST, Fixtures.podcastSearch("noticias"));
        assertEquals(201, found.get(0).id);
        assertEquals("https://feeds.example.com/201.xml", found.get(0).feed);
        // Item <-> JSON
        Library.Item back = Library.Item.fromJson(found.get(0).toJson());
        assertEquals(found.get(0), back);
        assertEquals(found.get(0).feed, back.feed);
        assertEquals(found.get(0).title, back.title);
    }

    // ------------------------------------------------------------------ Home / browse

    @Test
    public void homeShowsFeaturedShelvesAndLoadsTheRestLazily() throws Exception {
        ActivityController<MainActivity> c = openMain();
        View root = c.get().getWindow().getDecorView();
        Library lib = Library.get(app);
        for (String t : new String[]{"Top 50 mundial", "Radios en vivo", "Nuevos lanzamientos", "Artistas populares",
                "Playlists populares", "Reggaetón", "Pop latino", "Rock en español"}) {
            assertNotNull(t, SmokeTest.findText(root, t));
        }
        assertNull("not featured: not on Home", SmokeTest.findText(root, "Cumbia"));
        for (Library.Section s : lib.sections()) {
            assertEquals(s.title, s.featured ? Library.READY : Library.IDLE, s.state);
        }
        assertEquals(6, lib.section("releases").items.size());
        assertEquals(6, lib.section("artists").items.size());
        assertEquals(5, lib.section("playlists").items.size());
        for (String url : net.urls) {
            assertFalse("nothing for the lazy shelves yet: " + url, url.contains("podcasts/top") || url.contains("/genre"));
        }
        assertNotNull(SmokeTest.findPrefix(root, "Más géneros, radios y podcasts"));
        assertTrue("many shelves to browse", lib.sections().size() >= 30);
        c.pause().stop().destroy();
    }

    @Test
    public void albumPageListsItsTracksWithAlbumInfo() throws Exception {
        ActivityController<MainActivity> c = openMain();
        MainActivity a = c.get();
        View root = a.getWindow().getDecorView();
        SmokeTest.clickText(root, "Álbum 2");
        settle();
        SmokeTest.layout(root);
        ListView lv = SmokeTest.findList(root);
        assertEquals("header + 4 tracks + footer", 6, lv.getCount());
        Library lib = Library.get(app);
        Library.Item album = lib.section("releases").items.get(1);
        List<Track> tracks = lib.itemTracks(album);
        assertEquals(4, tracks.size());
        assertEquals("Álbum 2", tracks.get(0).album);
        assertEquals(album.id, tracks.get(0).albumId);
        assertEquals(album.art, tracks.get(0).artUrl);
        assertEquals("Pista 1", tracks.get(0).title);
        View header = lv.getAdapter().getView(0, null, lv);
        List<View> hv = new ArrayList<>();
        SmokeTest.collect(header, hv);
        boolean sub = false;
        for (View v : hv) if (v instanceof TextView && ((TextView) v).getText().toString().startsWith("Álbum · " + album.subtitle + " · 4 canciones")) sub = true;
        assertTrue("album subtitle", sub);
        // play from the page
        lv.performItemClick(lv.getAdapter().getView(2, null, lv), 2, 2);
        settle();
        PlayerEngine p = PlayerEngine.get(app);
        assertEquals("Pista 2", p.current().title);
        assertEquals("Álbum 2", p.queueName());
        assertTrue(p.isPlaying());
        a.onBackPressed();
        c.pause().stop().destroy();
    }

    @Test
    public void artistAndPlaylistPagesLoadTheirSongs() throws Exception {
        ActivityController<MainActivity> c = openMain();
        MainActivity a = c.get();
        View root = a.getWindow().getDecorView();
        Library lib = Library.get(app);
        Library.Item artist = lib.section("artists").items.get(0);
        SmokeTest.clickText(root, "Lista 1");
        settle();
        SmokeTest.layout(root);
        Library.Item list = lib.section("playlists").items.get(0);
        assertEquals(20, lib.itemTracks(list).size());
        assertEquals(22, SmokeTest.findList(root).getCount());
        a.onBackPressed();
        settle();
        SmokeTest.layout(root);
        lib.loadItemTracks(artist);
        settle();
        assertEquals(12, lib.itemTracks(artist).size());
        c.pause().stop().destroy();
    }

    @Test
    public void genresOpenFromTheBrowseGrid() throws Exception {
        ActivityController<MainActivity> c = openMain();
        MainActivity a = c.get();
        View root = a.getWindow().getDecorView();
        SmokeTest.clickText(root, "Buscar");
        assertNotNull(SmokeTest.findText(root, "Géneros"));
        SmokeTest.clickText(root, "Géneros");
        settle();
        SmokeTest.layout(root);
        assertNotNull(SmokeTest.findText(root, "Pop"));
        assertNotNull(SmokeTest.findText(root, "Rap/Hip Hop"));
        assertNull("the 'all' pseudo-genre is skipped", SmokeTest.findText(root, "Todo"));
        SmokeTest.clickText(root, "Rock");
        settle();
        SmokeTest.layout(root);
        Library lib = Library.get(app);
        Library.Item rock = lib.section("genres").items.get(2);
        assertEquals("Rock", rock.title);
        assertEquals(50, lib.itemTracks(rock).size());
        assertTrue(net.urls.contains("https://api.deezer.com/chart/152/tracks?limit=50"));
        c.pause().stop().destroy();
    }

    @Test
    public void moreRadiosLoadWhenOpened() throws Exception {
        Library lib = Library.get(app);
        Library.Section news = lib.section("radio-news");
        assertEquals(Library.IDLE, news.state);
        lib.loadSection(news);
        settle();
        assertEquals(Library.READY, news.state);
        assertEquals(5, news.tracks.size());
        assertTrue(news.tracks.get(0).isLive());
        boolean tagged = false;
        for (String u : net.urls) if (u.contains("tag=news") && u.contains("language=spanish")) tagged = true;
        assertTrue(tagged);
        for (String id : new String[]{"radio-sports", "radio-rock", "radio-pop", "radio-latin", "radio-classical", "radio-jazz", "radio-electronic"}) {
            assertNotNull(id, lib.section(id));
            assertTrue(id, lib.section(id).radio);
        }
        // genre shelves are plain searches
        lib.loadSection(lib.section("cumbia"));
        settle();
        assertEquals(Library.READY, lib.section("cumbia").state);
        assertEquals(15, lib.section("cumbia").tracks.size());
    }

    @Test
    public void offlineShelvesRecover() throws Exception {
        net.offline = true;
        ActivityController<MainActivity> c = openMain();
        View root = c.get().getWindow().getDecorView();
        Library lib = Library.get(app);
        assertEquals(Library.FAILED, lib.section("releases").state);
        assertNotNull(SmokeTest.findText(root, "Nuevos lanzamientos"));
        net.offline = false;
        List<TextView> retry = texts(root, "Reintentar");
        assertTrue(retry.size() >= 3);
        for (TextView t : retry) t.performClick();
        settle();
        assertEquals(Library.READY, lib.section("releases").state);
        assertEquals(Library.READY, lib.section("artists").state);

        // an album page that fails to load offers to retry
        Library.Item album = lib.section("releases").items.get(0);
        net.offline = true;
        SmokeTest.layout(root);
        SmokeTest.clickText(root, "Álbum 1");
        settle();
        SmokeTest.layout(root);
        assertEquals(Library.FAILED, lib.itemState(album));
        TextView msg = SmokeTest.findPrefix(root, "No se pudo cargar. Revisa tu conexión.");
        assertNotNull(msg);
        net.offline = false;
        msg.performClick();
        settle();
        assertEquals(Library.READY, lib.itemState(album));
        assertEquals(4, lib.itemTracks(album).size());
        c.pause().stop().destroy();
    }

    // ------------------------------------------------------------------ podcasts

    @Test
    public void podcastsTabShowsChartsAndTopics() throws Exception {
        ActivityController<MainActivity> c = openMain();
        View root = c.get().getWindow().getDecorView();
        SmokeTest.clickText(root, "Podcasts");
        settle();
        SmokeTest.layout(root);
        assertNotNull(SmokeTest.findText(root, "Temas"));
        assertNotNull(SmokeTest.findText(root, "Noticias"));
        assertNotNull(SmokeTest.findText(root, "Podcasts populares"));
        assertNotNull(SmokeTest.findText(root, "Radio Ambulante"));
        assertNotNull(SmokeTest.findText(root, "Entiende Tu Mente"));
        assertNull("no followed shows yet", SmokeTest.findText(root, "Tus podcasts"));
        assertEquals(3, Library.get(app).section("podcasts").items.size());
        c.pause().stop().destroy();
    }

    @Test
    public void podcastsChartFallsBackToTheUsWhenTheCountryIsUnsupported() throws Exception {
        Locale.setDefault(new Locale("es", "AR"));
        Library lib = Library.get(app);
        lib.loadSection(lib.section("podcasts"));
        settle();
        assertEquals(Library.READY, lib.section("podcasts").state);
        assertTrue(net.urls.contains("https://rss.applemarketingtools.com/api/v2/ar/podcasts/top/30/podcasts.json"));
        assertTrue(net.urls.contains("https://rss.applemarketingtools.com/api/v2/us/podcasts/top/30/podcasts.json"));
    }

    @Test
    public void searchAndPlayAnEpisodeThroughRedirects() throws Exception {
        ActivityController<MainActivity> c = openMain();
        MainActivity a = c.get();
        View root = a.getWindow().getDecorView();
        SmokeTest.clickText(root, "Podcasts");
        settle();
        SmokeTest.layout(root);
        SmokeTest.clickText(root, "Noticias"); // topic chip fills the search box
        settle();
        SmokeTest.layout(root);
        assertNotNull(SmokeTest.findText(root, "Noticias al día"));
        assertNotNull(SmokeTest.findText(root, "Otro programa"));
        SmokeTest.clickText(root, "Noticias al día");
        settle();
        SmokeTest.layout(root);
        Library lib = Library.get(app);
        Library.Item pod = lib.followedPodcasts().isEmpty() ? null : lib.followedPodcasts().get(0);
        assertNull(pod);
        ListView lv = SmokeTest.findList(root);
        assertEquals("header + 3 episodes + footer", 5, lv.getCount());
        assertNotNull(SmokeTest.findText(root, "Seguir"));

        // episode rows show date and duration
        View row = lv.getAdapter().getView(1, null, lv);
        List<View> rv = new ArrayList<>();
        SmokeTest.collect(row, rv);
        boolean info = false;
        for (View v : rv) {
            if (v instanceof TextView && ((TextView) v).getText().toString().matches(".*\\d.* · 42 min")) info = true;
        }
        assertTrue("date · duration", info);

        // play it: the redirecting link is resolved first
        lv.performItemClick(row, 1, 1);
        settle();
        PlayerEngine p = PlayerEngine.get(app);
        Track ep = p.current();
        assertEquals("Episodio 3: El final", ep.title);
        assertTrue(ep.isEpisode());
        assertEquals("Noticias al día", p.queueName());
        assertTrue(p.isPlaying());
        assertEquals("https://cdn.example.com/ep3.mp3", ep.resolvedUrl);
        assertEquals("http://traffic.example.com/ep3.mp3", ep.playUrl().replace("https://cdn.example.com/", "http://traffic.example.com/"));
        assertTrue(net.resolved.contains("http://traffic.example.com/ep3.mp3"));
        assertEquals(2530000, p.duration());

        // full-screen player shows the podcast controls
        ActivityController<NowPlayingActivity> n = Robolectric.buildActivity(NowPlayingActivity.class).setup();
        View nroot = n.get().getWindow().getDecorView();
        SmokeTest.layout(nroot);
        assertNotNull(SmokeTest.findPrefix(nroot, "PODCAST"));
        TextView back = SmokeTest.findText(nroot, "−15");
        TextView fwd = SmokeTest.findText(nroot, "+30");
        assertNotNull(back);
        assertNotNull(fwd);
        p.seekTo(100000);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300));
        int at = p.position();
        fwd.performClick();
        idle();
        assertEquals(at + 30000, p.position(), 1500);
        back.performClick();
        idle();
        assertEquals(at + 15000, p.position(), 1500);
        SmokeTest.clickText(nroot, "Letra");
        settle();
        SmokeTest.layout(nroot);
        assertNotNull(SmokeTest.findText(nroot, "Los podcasts no tienen letra"));
        assertEquals("no lyric lookups for podcasts", 0, net.lyricLookups.get());
        n.pause().stop().destroy();
        c.pause().stop().destroy();
    }

    static void idle() {
        SmokeTest.idle();
    }

    @Test
    public void followingPodcastsAndOpeningWithoutAFeedUsesLookup() throws Exception {
        ActivityController<MainActivity> c = openMain();
        MainActivity a = c.get();
        View root = a.getWindow().getDecorView();
        Library lib = Library.get(app);
        SmokeTest.clickText(root, "Podcasts");
        settle();
        SmokeTest.layout(root);
        int fetchesBefore = net.feedFetches.get();
        SmokeTest.clickText(root, "Entiende Tu Mente"); // the top chart has no feed url: looked up first
        settle();
        SmokeTest.layout(root);
        Library.Item pod = lib.section("podcasts").items.get(1);
        assertEquals("https://feeds.example.com/102.xml", pod.feed);
        assertEquals(fetchesBefore + 1, net.feedFetches.get());
        assertEquals(3, lib.itemTracks(pod).size());
        assertEquals("Entiende Tu Mente", lib.itemTracks(pod).get(0).artist);

        assertFalse(lib.isFollowing(pod));
        SmokeTest.clickText(root, "Seguir");
        assertTrue(lib.isFollowing(pod));
        assertNotNull(SmokeTest.findText(root, "Siguiendo"));
        a.onBackPressed();
        settle();
        SmokeTest.layout(root);
        assertNotNull("followed shows appear on the tab", SmokeTest.findText(root, "Tus podcasts"));

        // Home and the Library tab list it too
        SmokeTest.clickText(root, "Inicio");
        assertNotNull(SmokeTest.findText(root, "Tus podcasts"));
        SmokeTest.clickText(root, "Tu biblioteca");
        SmokeTest.clickText(root, "Podcasts");
        SmokeTest.layout(root);
        assertNotNull(SmokeTest.findText(root, "Entiende Tu Mente"));
        assertNotNull(SmokeTest.findPrefix(root, "Podcast · Molo Cebrián"));

        // survives a restart, offline
        net.offline = true;
        forget(Library.class);
        Library lib2 = Library.get(app);
        assertEquals(1, lib2.followedPodcasts().size());
        assertEquals("Entiende Tu Mente", lib2.followedPodcasts().get(0).title);
        assertEquals("feed is remembered", "https://feeds.example.com/102.xml", lib2.followedPodcasts().get(0).feed);

        // unfollow
        lib2.toggleFollow(lib2.followedPodcasts().get(0));
        assertTrue(lib2.followedPodcasts().isEmpty());
        c.pause().stop().destroy();
    }

    @Test
    public void episodesRememberWhereYouStoppedAndResume() throws Exception {
        Library lib = Library.get(app);
        Library.Item pod = new Library.Item(Library.Item.PODCAST, 201, "Noticias al día", "Voces",
                "https://is1-ssl.mzstatic.com/image/p201/600x600bb.jpg", "https://feeds.example.com/201.xml");
        lib.loadItemTracks(pod);
        settle();
        List<Track> eps = lib.itemTracks(pod);
        assertEquals(3, eps.size());
        Track ep = eps.get(0);
        assertEquals(0, lib.progress(ep));
        assertTrue(lib.inProgress(10).isEmpty());

        PlayerEngine p = PlayerEngine.get(app);
        p.playList(eps, 0, pod.title);
        settle();
        p.seekTo(600000);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300));
        p.pause();
        assertEquals(600000, lib.progress(ep), 2000);
        assertEquals(1, lib.inProgress(10).size());
        String info = Ui.episodeInfo(app, ep);
        assertTrue("remaining time: " + info, info.contains("faltan 32 min"));

        // moving on to another episode keeps the first one's place
        p.next();
        settle();
        assertEquals(eps.get(1), p.current());
        assertEquals(600000, lib.progress(ep), 2000);
        lib.saveProgress(eps.get(1), 1000, 3723000); // barely started: not remembered
        assertEquals(0, lib.progress(eps.get(1)));
        assertEquals(1, lib.inProgress(10).size());

        // the progress is saved while playing too (every 15 s)
        p.jumpTo(2);
        settle();
        p.seekTo(90000);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(16));
        assertEquals(90000, lib.progress(eps.get(2)), 20000);
        assertEquals("most recent first", eps.get(2), lib.inProgress(10).get(0));

        // restart the app offline: the shelf, the progress and the episode data are all back
        p.pause();
        net.offline = true;
        forget(PlayerEngine.class);
        forget(Library.class);
        Library lib2 = Library.get(app);
        assertEquals("three episodes were listened to", 3, lib2.inProgress(10).size());
        assertEquals(eps.get(2).key, lib2.inProgress(10).get(0).key);
        assertEquals(eps.get(1).key, lib2.inProgress(10).get(1).key);
        assertEquals(eps.get(0).key, lib2.inProgress(10).get(2).key);
        assertEquals(600000, lib2.progress(lib2.inProgress(10).get(2)), 2000);
        net.offline = false;

        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
        settle();
        View root = c.get().getWindow().getDecorView();
        SmokeTest.layout(root);
        assertNotNull("Home shelf", SmokeTest.findText(root, "Seguir escuchando"));
        // resuming plays from where it was
        PlayerEngine q = PlayerEngine.get(app);
        q.playList(lib2.inProgress(10), 2, "Seguir escuchando");
        settle();
        assertEquals("Episodio 3: El final", q.current().title);
        assertTrue("resumed at the saved place: " + q.position(), q.position() >= 598000);

        // finishing it forgets the place
        q.seekTo(2530000 - 3000);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(8));
        settle();
        assertEquals(0, lib2.progress(eps.get(0)));
        c.pause().stop().destroy();
    }

    @Test
    public void searchPodcastsErrorsAndEmptyResults() throws Exception {
        ActivityController<MainActivity> c = openMain();
        View root = c.get().getWindow().getDecorView();
        SmokeTest.clickText(root, "Podcasts");
        settle();
        List<View> all = new ArrayList<>();
        SmokeTest.collect(root, all);
        EditText input = null;
        for (View v : all) if (v instanceof EditText) input = (EditText) v;
        input.setText("zzzz");
        settle();
        SmokeTest.layout(root);
        assertNotNull(SmokeTest.findPrefix(root, "No se encontraron podcasts para"));
        net.offline = true;
        input.setText("sin red");
        settle();
        SmokeTest.layout(root);
        assertNotNull(SmokeTest.findText(root, "No hay conexión a internet."));
        net.offline = false;
        input.setText("");
        SmokeTest.layout(root);
        assertNotNull(SmokeTest.findText(root, "Temas"));
        c.pause().stop().destroy();
    }

    @Test
    public void episodeThatCannotBeResolvedFailsGracefully() throws Exception {
        Library lib = Library.get(app);
        Library.Item pod = new Library.Item(Library.Item.PODCAST, 201, "P", "A", null, "https://feeds.example.com/201.xml");
        lib.loadItemTracks(pod);
        settle();
        List<Track> eps = lib.itemTracks(pod);
        PlayerEngine p = PlayerEngine.get(app);
        net.offline = true; // the redirect lookup fails
        p.playList(eps, 0, "P");
        settle();
        assertFalse(p.isPlaying());
        assertTrue(org.robolectric.shadows.ShadowToast.getTextOfLatestToast().contains("No se pudo reproducir"));
        net.offline = false;
        p.playList(eps, 0, "P");
        settle();
        assertTrue(p.isPlaying());
    }

    // ------------------------------------------------------------------ fandom artists

    @Test
    public void fandomArtistsAreLookedUpByExactName() throws Exception {
        List<Library.Item> found = Library.artistsByName(Library.FANDOM_ARTISTS);
        assertEquals("only the artists the catalog has", 3, found.size());
        assertEquals("The Living Tombstone", found.get(0).title);
        assertEquals("order of the list is kept", "Black Gryph0n", found.get(1).title);
        assertEquals("CG5", found.get(2).title);
        for (Library.Item it : found) {
            assertEquals(Library.Item.ARTIST, it.kind);
            assertNotEquals("the look-alike (\"Tribute Band\") is never chosen", 8001, it.id);
            assertNotNull(it.art);
        }
        assertEquals("one lookup per name", Library.FANDOM_ARTISTS.length, net.artistLookups.size());
        assertTrue(net.artistLookups.contains("Baasik")); // asked for, just not found
    }

    @Test
    public void lookupMatchesIgnoringCaseAndAccents() throws Exception {
        Library.Item it = Library.findArtist("the living tombstone");
        assertNotNull(it);
        assertEquals("The Living Tombstone", it.title);
        assertNull(Library.findArtist("Nadie Conocido"));
    }

    @Test
    public void fandomShelfOnHomeOpensTheArtistPage() throws Exception {
        ActivityController<MainActivity> c = openMain();
        MainActivity a = c.get();
        View root = a.getWindow().getDecorView();
        Library lib = Library.get(app);
        assertNotNull(SmokeTest.findText(root, "Fandoms y gaming"));
        Library.Section fandom = lib.section("fandom-artists");
        assertEquals(Library.READY, fandom.state);
        assertEquals(3, fandom.items.size());
        assertNotNull(SmokeTest.findPrefix(root, "The Living Tombstone, CG5"));
        // the artist's own track shelf searches by the exact artist name
        Library.Section tls = lib.section("tls");
        assertEquals(Library.READY, tls.state);
        assertEquals(15, tls.tracks.size());
        boolean exact = false;
        for (String u : net.urls) {
            if (u.contains("/search?q=artist%3A%22The+Living+Tombstone%22")) exact = true;
        }
        assertTrue("searched with artist:\"...\"", exact);

        // tapping the artist opens a page with their top songs
        List<TextView> cards = texts(root, "The Living Tombstone");
        assertTrue(cards.size() >= 2); // shelf title and artist card
        View card = cards.get(0); // the artist card comes before the later shelf of the same name
        while (!card.hasOnClickListeners()) card = (View) card.getParent();
        card.performClick();
        settle();
        SmokeTest.layout(root);
        Library.Item artist = fandom.items.get(0);
        assertEquals(12, lib.itemTracks(artist).size());
        assertTrue(net.urls.contains("https://api.deezer.com/artist/" + artist.id + "/top?limit=50"));
        a.onBackPressed();
        c.pause().stop().destroy();
    }

    @Test
    public void fandomShelfFailsOnlyWhenEveryLookupFails() throws Exception {
        net.offline = true;
        Library lib = Library.get(app);
        lib.loadSection(lib.section("fandom-artists"));
        settle();
        assertEquals(Library.FAILED, lib.section("fandom-artists").state);
        net.offline = false;
        lib.loadSection(lib.section("fandom-artists"));
        settle();
        assertEquals(Library.READY, lib.section("fandom-artists").state);
    }
}

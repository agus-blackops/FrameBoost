package com.agus.sonora;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.media.browse.MediaBrowser.MediaItem;
import android.service.media.MediaBrowserService;
import android.view.View;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAppWidgetManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 2.0: the home-screen widget and Android Auto (browse, play, voice search). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class AutoWidgetTest {

    Fixtures net;
    Application app;

    @Before
    public void reset() throws Exception {
        BackupTest.forgetAll();
        Effects.resetForTest();
        SonoraWidget.resetForTest();
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

    static void settle() throws Exception {
        SmokeTest.settle();
    }

    // ------------------------------------------------------------------ widget

    @Test
    public void theWidgetFollowsThePlayerAndItsButtonsWork() throws Exception {
        ShadowAppWidgetManager wm = shadowOf(AppWidgetManager.getInstance(app));
        int id = wm.createWidget(SonoraWidget.class, R.layout.widget);
        View w = wm.getViewFor(id);
        assertEquals("Sonora", ((TextView) w.findViewById(R.id.widget_title)).getText().toString());
        assertEquals("Toca para elegir música", ((TextView) w.findViewById(R.id.widget_artist)).getText().toString());

        Library lib = Library.get(app);
        Library.Section s = lib.section("full-trending");
        lib.loadSection(s);
        settle();
        PlayerEngine p = PlayerEngine.get(app);
        p.playList(s.tracks, 0, "x");
        settle();
        w = wm.getViewFor(id);
        assertEquals("Tema completo 1", ((TextView) w.findViewById(R.id.widget_title)).getText().toString());
        assertEquals("Productora 1", ((TextView) w.findViewById(R.id.widget_artist)).getText().toString());
        assertEquals("Pausa", w.findViewById(R.id.widget_play).getContentDescription().toString());

        // its buttons go through the same receiver as the notification
        w.findViewById(R.id.widget_next).performClick();
        settle();
        assertEquals("Tema completo 2", p.current().title);
        w = wm.getViewFor(id);
        w.findViewById(R.id.widget_play).performClick();
        settle();
        assertFalse(p.isPlaying());
        w = wm.getViewFor(id);
        assertEquals("Reproducir", w.findViewById(R.id.widget_play).getContentDescription().toString());
        assertEquals("Tema completo 2", ((TextView) w.findViewById(R.id.widget_title)).getText().toString());
    }

    @Test
    public void playOnTheWidgetWithTheAppClosedResumesTheLastSession() throws Exception {
        Library lib = Library.get(app);
        Library.Section s = lib.section("full-trending");
        lib.loadSection(s);
        settle();
        PlayerEngine p = PlayerEngine.get(app);
        p.playList(s.tracks, 3, "x");
        settle();
        p.pause();
        p.saveSession();
        // the app process is gone
        ContentTest.forget(PlayerEngine.class);
        new MediaActionReceiver().onReceive(app, new Intent(app, MediaActionReceiver.class).setAction(MediaActionReceiver.PLAY_PAUSE));
        settle();
        PlayerEngine q = PlayerEngine.get(app);
        assertEquals("Tema completo 4", q.current().title);
        assertTrue(q.isPlaying());
    }

    // ------------------------------------------------------------------ Android Auto

    @Test
    public void onlyCarAndVoiceAppsMayBrowse() throws Exception {
        PlaybackService svc = Robolectric.setupService(PlaybackService.class);
        assertFalse("browsing alone doesn't start the foreground notification", PlaybackService.running);
        MediaBrowserService.BrowserRoot root = svc.onGetRoot("com.google.android.projection.gearhead", 1000, null);
        assertNotNull(root);
        assertEquals(AutoBrowser.ROOT, root.getRootId());
        assertTrue(root.getExtras().getBoolean("android.media.browse.SEARCH_SUPPORTED"));
        assertNotNull(svc.onGetRoot(app.getPackageName(), 1000, null));
        assertNull("any other app gets nothing", svc.onGetRoot("com.example.spy", 1000, null));
        assertNotNull(svc.getSessionToken());
    }

    @Test
    public void theCarBrowsesYourMusicAndPlaysWhatYouTap() throws Exception {
        Library lib = Library.get(app);
        Library.Section s = lib.section("full-trending");
        lib.loadSection(s);
        settle();
        lib.toggleLike(s.tracks.get(2));
        lib.toggleLike(s.tracks.get(5));
        lib.createPlaylist("Ruta");
        lib.addToPlaylist("Ruta", s.tracks.get(7));
        AutoBrowser b = new AutoBrowser(app);
        List<MediaItem> root = b.children(AutoBrowser.ROOT, null);
        List<String> titles = new ArrayList<>();
        for (MediaItem m : root) {
            titles.add(m.getDescription().getTitle().toString());
            assertTrue(m.isBrowsable());
        }
        assertEquals("[Te gusta, Mezcla de descubrimiento, Canciones completas, Radios en vivo, Tus playlists]", titles.toString());

        List<MediaItem> liked = b.children(AutoBrowser.LIKED, null);
        assertEquals(2, liked.size());
        assertTrue(liked.get(0).isPlayable());
        assertEquals("Tema completo 6", liked.get(0).getDescription().getTitle().toString());
        assertEquals("liked|au:t5", liked.get(0).getMediaId());

        List<MediaItem> pls = b.children(AutoBrowser.PLAYLISTS, null);
        assertEquals("pl:Ruta", pls.get(0).getMediaId());
        assertEquals(1, b.children("pl:Ruta", null).size());

        // a shelf that isn't loaded yet: empty for now, and the car is told when it's ready
        final boolean[] told = {false};
        assertNull(b.children(AutoBrowser.RADIO, new AutoBrowser.Loaded() {
            @Override
            public void done() {
                told[0] = true;
            }
        }));
        settle();
        assertTrue(told[0]);
        List<MediaItem> radios = b.children(AutoBrowser.RADIO, null);
        assertFalse(radios.isEmpty());
        assertEquals("Radio en vivo", radios.get(0).getDescription().getSubtitle().toString());

        // tapping a song in the car plays that list from that song
        PlaybackService svc = Robolectric.setupService(PlaybackService.class);
        svc.callback.onPlayFromMediaId(liked.get(1).getMediaId(), null);
        settle();
        PlayerEngine p = PlayerEngine.get(app);
        assertEquals("Tema completo 3", p.current().title);
        assertEquals("Canciones que te gustan", p.queueName());
        assertEquals(1, p.queueIndex());
        assertTrue(p.isPlaying());
        Intent started = shadowOf(app).getNextStartedService();
        assertNotNull("playing starts the foreground service", started);
        assertEquals(PlaybackService.class.getName(), started.getComponent().getClassName());
        assertFalse("a stale id does nothing", b.play("liked|au:nope"));
    }

    @Test
    public void podcastsAndDownloadsInTheCar() throws Exception {
        Library lib = Library.get(app);
        Library.Item pod = new Library.Item(Library.Item.PODCAST, 201, "Noticias al día", "Voces", null, "https://feeds.example.com/201.xml");
        lib.toggleFollow(pod);
        AutoBrowser b = new AutoBrowser(app);
        List<MediaItem> pods = b.children(AutoBrowser.PODCASTS, null);
        assertEquals("pod:4:201", pods.get(0).getMediaId());
        assertNull("episodes load first", b.children("pod:4:201", null));
        settle();
        List<MediaItem> eps = b.children("pod:4:201", null);
        assertEquals("Episodio 3: El final", eps.get(0).getDescription().getTitle().toString());
        assertTrue(b.play(eps.get(0).getMediaId()));
        settle();
        assertEquals("Noticias al día", PlayerEngine.get(app).queueName());
    }

    @Test
    public void voiceSearchPlaysYourMusicFirstThenOnline() throws Exception {
        Library lib = Library.get(app);
        BackupTest.setLocal(lib, java.util.Collections.singletonList(
                new Track("content://5", "Persiana Americana", "Soda Stereo", "Signos", 0, 200000, false, 1)));
        PlaybackService svc = Robolectric.setupService(PlaybackService.class);
        PlayerEngine p = PlayerEngine.get(app);
        svc.callback.onPlayFromSearch("persiana americana", null);
        settle();
        assertEquals("Persiana Americana", p.current().title);
        svc.callback.onPlayFromSearch("lluvia", null);
        settle();
        assertTrue(p.current().remote);
        assertEquals("Búsqueda: lluvia", p.queueName());

        // "play music on Sonora" with no likes: the discovery mix
        svc.callback.onPlayFromSearch("", null);
        settle();
        assertEquals("Mezcla de descubrimiento", p.queueName());
        assertTrue(p.isPlaying());
    }
}

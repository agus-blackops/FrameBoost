package com.agus.sonora;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.Intent;
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 2.0 podcasts: played marks, "Nuevo", new-episode alerts and skipping silence. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class PodcastsTest {

    Fixtures net;
    Application app;
    Library.Item pod;

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
        shadowOf(app).grantPermissions(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS);
        SimAudio.skipSilence = false;
        PlayerEngine.audioFactory = SimAudio.factory(new SimAudio.Durations() {
            @Override
            public long of(String uri) {
                return 60000;
            }
        }, new ArrayList<String>());
        pod = new Library.Item(Library.Item.PODCAST, 201, "Noticias al día", "Voces", null, "https://feeds.example.com/201.xml");
    }

    static void settle() throws Exception {
        SmokeTest.settle();
    }

    List<Track> load() throws Exception {
        Library lib = Library.get(app);
        lib.loadItemTracks(pod);
        settle();
        return lib.itemTracks(pod);
    }

    @Test
    public void episodesAreMarkedPlayedWhenFinishedOrByHand() throws Exception {
        Library lib = Library.get(app);
        List<Track> eps = load();
        Track ep = eps.get(0);
        assertFalse(lib.isPlayed(ep));
        lib.saveProgress(ep, 30000, 2530000);
        assertFalse(lib.isPlayed(ep));
        lib.saveProgress(ep, 2525000, 2530000); // the last 15 s count as finished
        assertTrue(lib.isPlayed(ep));
        assertEquals(0, lib.progress(ep));
        assertEquals("3 Oct · Escuchado", Ui.episodeInfo(app, ep));

        lib.setPlayed(ep, false);
        assertFalse(lib.isPlayed(ep));
        lib.saveProgress(eps.get(1), 600000, 3723000);
        lib.setPlayed(eps.get(1), true);
        assertEquals("marking it played forgets where it stopped", 0, lib.progress(eps.get(1)));

        // remembered after a restart
        ContentTest.forget(Library.class);
        assertTrue(Library.get(app).isPlayed(eps.get(1)));
    }

    @Test
    public void followingTakesNoteAndLaterEpisodesShowAsNew() throws Exception {
        Library lib = Library.get(app);
        List<Track> eps = load();
        lib.toggleFollow(pod);
        for (Track t : eps) assertFalse("what's out when you follow is not new", lib.isNew(t));

        // later: a new episode comes out; the page shows it as new during this visit
        net.newEpisode = true;
        ContentTest.forget(Library.class);
        lib = Library.get(app);
        lib.loadItemTracks(pod);
        settle();
        List<Track> now = lib.itemTracks(pod);
        assertEquals("Episodio 4: La vuelta", now.get(0).title);
        assertTrue(lib.isNew(now.get(0)));
        assertFalse(lib.isNew(now.get(1)));
        assertEquals(1, lib.newEpisodes(pod));
        assertTrue(Ui.episodeInfo(app, now.get(0)).startsWith("Nuevo · 10 Oct"));
        lib.setPlayed(now.get(0), true);
        assertFalse("listened: no longer new", lib.isNew(now.get(0)));

        // next visit: nothing new
        ContentTest.forget(Library.class);
        lib = Library.get(app);
        lib.loadItemTracks(pod);
        settle();
        assertEquals(0, lib.newEpisodes(pod));
        assertEquals("ep:4:201:g-3", Library.podcastKeyOf(now.get(1)) == null ? null : now.get(1).key);
        assertEquals("4:201", Library.podcastKeyOf(now.get(1)));
    }

    @Test
    public void alertsCheckFollowedPodcastsAndOpenThePodcast() throws Exception {
        Library lib = Library.get(app);
        load();
        JobScheduler js = (JobScheduler) app.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        assertNull("nothing followed: no check scheduled", js.getPendingJob(PodcastCheckJob.JOB_ID));
        lib.toggleFollow(pod);
        JobInfo job = js.getPendingJob(PodcastCheckJob.JOB_ID);
        assertNotNull(job);
        assertTrue(job.isPeriodic());
        assertEquals(PodcastCheckJob.PERIOD_MS, job.getIntervalMillis());
        assertEquals(JobInfo.NETWORK_TYPE_ANY, job.getNetworkType());

        NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        assertTrue("first look only takes note", PodcastCheckJob.check(app).isEmpty());
        assertTrue(PodcastCheckJob.check(app).isEmpty());
        assertEquals(0, shadowOf(nm).getAllNotifications().size());

        net.newEpisode = true;
        List<PodcastCheckJob.Found> found = PodcastCheckJob.check(app);
        assertEquals(1, found.size());
        assertEquals("Episodio 4: La vuelta", found.get(0).episodes.get(0).title);
        List<Notification> shown = shadowOf(nm).getAllNotifications();
        assertEquals(1, shown.size());
        Notification n = shown.get(0);
        assertEquals("Nuevo episodio · Noticias al día", n.extras.getString(Notification.EXTRA_TITLE));
        assertEquals("Episodio 4: La vuelta", n.extras.getString(Notification.EXTRA_TEXT));
        assertTrue("told once", PodcastCheckJob.check(app).isEmpty());

        // tapping it opens the podcast's page
        Intent open = shadowOf(n.contentIntent).getSavedIntent();
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class, open).setup();
        settle();
        View root = c.get().getWindow().getDecorView();
        SmokeTest.layout(root);
        assertNotNull(SmokeTest.findPrefix(root, "Podcast · Voces"));
        assertNotNull(SmokeTest.findText(root, "Episodio 4: La vuelta"));
        c.pause().stop().destroy();

        // turned off: unscheduled, and no alerts
        PodcastCheckJob.setEnabled(app, false);
        assertNull(js.getPendingJob(PodcastCheckJob.JOB_ID));
        assertTrue(PodcastCheckJob.check(app).isEmpty());
        lib.toggleFollow(pod);
        PodcastCheckJob.setEnabled(app, true);
        assertNull("nothing followed anymore", js.getPendingJob(PodcastCheckJob.JOB_ID));
    }

    @Test
    public void theJobRunsInTheBackgroundAndFinishes() throws Exception {
        Library.get(app);
        load();
        Library.get(app).toggleFollow(pod);
        PodcastCheckJob.check(app); // take note
        net.newEpisode = true;
        PodcastCheckJob job = Robolectric.setupService(PodcastCheckJob.class);
        assertTrue("runs on a background thread", job.onStartJob(null));
        settle();
        NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        assertEquals(1, shadowOf(nm).getAllNotifications().size());
    }

    @Test
    public void skipSilenceOnlyForEpisodes() throws Exception {
        List<Track> eps = load();
        PlayerEngine p = PlayerEngine.get(app);
        p.setSkipSilence(true);
        p.playList(eps, 0, "Noticias al día");
        settle();
        assertTrue(SimAudio.skipSilence);
        Library lib = Library.get(app);
        Library.Section s = lib.section("full-trending");
        lib.loadSection(s);
        settle();
        p.playList(s.tracks, 0, "x");
        settle();
        assertFalse("never on songs", SimAudio.skipSilence);
        p.playList(eps, 0, "Noticias al día");
        settle();
        p.setSkipSilence(false);
        assertFalse(SimAudio.skipSilence);
    }

    @Test
    public void menuAndAjustes() throws Exception {
        ActivityController<SettingsActivity> c = Robolectric.buildActivity(SettingsActivity.class,
                new Intent(app, SettingsActivity.class)).setup();
        View root = c.get().getWindow().getDecorView();
        SmokeTest.layout(root);
        assertNotNull(SmokeTest.findText(root, "Saltar silencios"));
        assertNotNull(SmokeTest.findText(root, "Avisos de episodios nuevos"));
        SmokeTest.clickText(root, "Saltar silencios");
        assertTrue(PlayerEngine.get(app).skipSilence());
        SmokeTest.clickText(root, "Avisos de episodios nuevos");
        assertFalse(PodcastCheckJob.enabled(app));
        c.pause().stop().destroy();
    }
}

package com.agus.sonora;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 2.0.1: downloads keep going outside the app, progress is cheap, the widget doesn't poll. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class Fixes201Test {

    Fixtures net;
    Application app;

    @Before
    public void reset() throws Exception {
        BackupTest.forgetAll();
        Effects.resetForTest();
        SonoraWidget.resetForTest();
        PlaybackService.running = false;
        DownloadService.running = false;
        Downloads.metered = new Downloads.Metered() {
            @Override
            public boolean now(Context c) {
                return false;
            }
        };
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
                return 60000;
            }
        }, new ArrayList<String>());
    }

    List<Track> audius() throws Exception {
        Library lib = Library.get(app);
        Library.Section s = lib.section("full-trending");
        lib.loadSection(s);
        SmokeTest.settle();
        return s.tracks;
    }

    @Test
    public void downloadingStartsAForegroundServiceWithProgressThatLeavesWhenDone() throws Exception {
        List<Track> songs = audius();
        Downloads d = Downloads.get(app);
        net.holdDownloads = new java.util.concurrent.CountDownLatch(1);
        d.downloadAll(songs.subList(0, 3));
        Intent started = shadowOf(app).getNextStartedService();
        assertNotNull("downloads ask for the foreground service", started);
        assertEquals(DownloadService.class.getName(), started.getComponent().getClassName());

        ServiceController<DownloadService> sc = Robolectric.buildService(DownloadService.class, started).create().startCommand(0, 1);
        DownloadService svc = sc.get();
        for (int i = 0; i < 40 && d.percent(songs.get(0)) < 50; i++) SmokeTest.settle();
        NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        Notification n = shadowOf(nm).getNotification(DownloadService.NOTIFICATION_ID);
        assertNotNull(n);
        assertEquals("Descargando · Tema completo 1", n.extras.getString(Notification.EXTRA_TITLE));
        assertEquals("50 % · quedan 3", n.extras.getString(Notification.EXTRA_TEXT));
        assertEquals(50, n.extras.getInt(Notification.EXTRA_PROGRESS));
        assertEquals("Cancelar", n.actions[0].title.toString());
        assertTrue(DownloadService.running);

        net.holdDownloads.countDown();
        net.holdDownloads = null;
        SmokeTest.settle();
        assertEquals(3, d.tracks().size());
        assertFalse("gone once the queue is empty", DownloadService.running);
        assertTrue(shadowOf(svc).isStoppedBySelf());
        sc.destroy();
    }

    @Test
    public void cancelOnTheNotificationStopsWhatIsLeft() throws Exception {
        List<Track> songs = audius();
        Downloads d = Downloads.get(app);
        net.holdDownloads = new java.util.concurrent.CountDownLatch(1);
        d.downloadAll(songs.subList(0, 4));
        ServiceController<DownloadService> sc = Robolectric.buildService(DownloadService.class).create().startCommand(0, 1);
        sc.withIntent(new Intent(app, DownloadService.class).setAction(DownloadService.CANCEL)).startCommand(0, 2);
        net.holdDownloads.countDown();
        net.holdDownloads = null;
        SmokeTest.settle();
        assertEquals(0, d.tracks().size());
        assertEquals(0, d.pending().size());
        File[] left = d.dir().listFiles();
        assertEquals(0, left == null ? 0 : left.length);
        assertFalse(DownloadService.running);
    }

    @Test
    public void progressIsToldInStepsAndNeverRebuildsTheScreen() throws Exception {
        final int[] changed = {0}, progress = {0};
        Downloads d = Downloads.get(app);
        d.addListener(new Downloads.Listener() {
            @Override
            public void onDownloadsChanged() {
                changed[0]++;
            }

            @Override
            public void onDownloadProgress(Track t, int percent) {
                progress[0]++;
            }
        });
        Track t = audius().get(0);
        d.download(t);
        SmokeTest.settle();
        assertEquals(Downloads.DONE, d.state(t));
        assertEquals("50, 75 and 100 (steps of at least 5)", 3, progress[0]);
        assertTrue("state changes only: queued, running, done (" + changed[0] + ")", changed[0] <= 4);

        // Home keeps its views while a download moves
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
        SmokeTest.settle();
        MainActivity a = c.get();
        View before = firstContentChild(a);
        a.onDownloadProgress(t, 40);
        assertSame("not rebuilt", before, firstContentChild(a));
        c.pause().stop().destroy();
    }

    static View firstContentChild(MainActivity a) throws Exception {
        java.lang.reflect.Field f = MainActivity.class.getDeclaredField("content");
        f.setAccessible(true);
        ViewGroup content = (ViewGroup) f.get(a);
        return content.getChildAt(0);
    }

    @Test
    public void theWidgetOnlyAsksTheSystemWhenItMayExist() throws Exception {
        java.lang.reflect.Field f = SonoraWidget.class.getDeclaredField("present");
        f.setAccessible(true);
        assertNull(f.get(null));
        Library lib = Library.get(app);
        PlayerEngine p = PlayerEngine.get(app);
        p.playList(audius(), 0, "x");
        SmokeTest.settle();
        assertEquals("asked once: no widget", Boolean.FALSE, f.get(null));

        int id = shadowOf(AppWidgetManager.getInstance(app)).createWidget(SonoraWidget.class, R.layout.widget);
        assertEquals("adding one is noticed", Boolean.TRUE, f.get(null));
        p.next();
        SmokeTest.settle();
        View w = shadowOf(AppWidgetManager.getInstance(app)).getViewFor(id);
        assertEquals("Tema completo 2", ((android.widget.TextView) w.findViewById(R.id.widget_title)).getText().toString());
        new SonoraWidget().onDisabled(app);
        assertEquals(Boolean.FALSE, f.get(null));
    }

    @Test
    public void version() throws Exception {
        assertEquals("2.0.1", app.getPackageManager().getPackageInfo(app.getPackageName(), 0).versionName);
    }
}

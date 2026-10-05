package com.agus.sonora;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowMediaPlayer;
import org.robolectric.shadows.util.DataSource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SmokeTest {

    @org.junit.Before
    public void reset() throws Exception {
        for (Class<?> k : new Class<?>[]{Library.class, PlayerEngine.class}) {
            java.lang.reflect.Field f = k.getDeclaredField("sInstance");
            f.setAccessible(true);
            f.set(null, null);
        }
        PlaybackService.running = false;
    }

    static void idle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300));
    }

    static void collect(View v, List<View> out) {
        out.add(v);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), out);
        }
    }

    static TextView findText(View root, String text) {
        List<View> all = new ArrayList<>();
        collect(root, all);
        for (View v : all) {
            if (v instanceof TextView && text.equals(((TextView) v).getText().toString()) && v.isShown()) return (TextView) v;
        }
        return null;
    }

    static TextView findPrefix(View root, String text) {
        List<View> all = new ArrayList<>();
        collect(root, all);
        for (View v : all) {
            if (v instanceof TextView && ((TextView) v).getText().toString().startsWith(text) && v.isShown()) return (TextView) v;
        }
        return null;
    }

    static void clickText(View root, String text) {
        TextView t = findText(root, text);
        assertNotNull("text not found: " + text, t);
        View v = t;
        while (true) {
            if (v.hasOnClickListeners()) {
                v.performClick();
                break;
            }
            Object parent = v.getParent();
            if (parent instanceof android.widget.AdapterView) {
                android.widget.AdapterView<?> av = (android.widget.AdapterView<?>) parent;
                int pos = av.getPositionForView(v);
                av.performItemClick(v, pos, av.getItemIdAtPosition(pos));
                break;
            }
            assertTrue("no clickable ancestor for " + text, parent instanceof View);
            v = (View) parent;
        }
        idle();
    }

    static ListView findList(View root) {
        List<View> all = new ArrayList<>();
        collect(root, all);
        for (View v : all) if (v instanceof ListView && v.isShown()) return (ListView) v;
        return null;
    }

    static void layout(View root) {
        root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2200, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1080, 2200);
    }

    @Test
    public void fullFlow() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        shadowOf(app).grantPermissions(Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS);
        ShadowMediaPlayer.setMediaInfoProvider(new ShadowMediaPlayer.MediaInfoProvider() {
            @Override
            public ShadowMediaPlayer.MediaInfo get(DataSource ds) {
                return new ShadowMediaPlayer.MediaInfo(180000, 50);
            }
        });

        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity a = c.get();
        idle();
        View root = a.getWindow().getDecorView();
        layout(root);
        assertNotNull(findText(root, "Descubrir en línea"));
        PlayerEngine p = PlayerEngine.get(app);
        Library lib = Library.get(app);
        assertEquals(16, lib.onlineTracks().size());

        // play from the home row
        clickText(root, lib.onlineTracks().get(2).title);
        assertEquals(lib.onlineTracks().get(2), p.current());
        idle();
        assertTrue("should be playing", p.isPlaying());
        assertEquals(180000, p.duration());
        System.out.println("playing: " + p.current().title + " pos=" + p.position());

        // service + notification
        Intent started = shadowOf(app).getNextStartedService();
        assertNotNull("service should be started", started);
        ServiceController<PlaybackService> sc = Robolectric.buildService(PlaybackService.class, started).create().startCommand(0, 1);
        idle();
        NotificationManager nm = (NotificationManager) app.getSystemService(Application.NOTIFICATION_SERVICE);
        List<Notification> notes = shadowOf(nm).getAllNotifications();
        assertFalse("notification posted", notes.isEmpty());
        System.out.println("notification title: " + notes.get(0).extras.getString(Notification.EXTRA_TITLE));

        // notification buttons
        new MediaActionReceiver().onReceive(app, new Intent(MediaActionReceiver.PLAY_PAUSE));
        idle();
        assertFalse(p.isPlaying());
        new MediaActionReceiver().onReceive(app, new Intent(MediaActionReceiver.PLAY_PAUSE));
        idle();
        assertTrue(p.isPlaying());
        new MediaActionReceiver().onReceive(app, new Intent(MediaActionReceiver.NEXT));
        idle();
        assertEquals(lib.onlineTracks().get(3), p.current());
        assertTrue(p.isPlaying());

        // completion advances
        layout(root);
        p.seekTo(179990);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
        idle();
        System.out.println("after completion: " + p.current().title);
        assertEquals(lib.onlineTracks().get(4), p.current());

        // mini player like
        lib.toggleLike(p.current());
        assertEquals(1, lib.likedTracks().size());

        // search tab
        clickText(root, "Buscar");
        layout(root);
        List<View> all = new ArrayList<>();
        collect(root, all);
        EditText input = null;
        for (View v : all) if (v instanceof EditText) input = (EditText) v;
        assertNotNull(input);
        input.setText("neon");
        idle();
        layout(root);
        ListView results = findList(root);
        assertNotNull(results);
        assertEquals(1, results.getAdapter().getCount());
        results.performItemClick(results.getAdapter().getView(0, null, results), 0, 0);
        idle();
        assertTrue(p.current().title.startsWith("Neón"));
        input.setText("zzzz");
        idle();
        layout(root);
        assertNotNull(findText(root, "No se encontró nada para \"zzzz\""));
        input.setText("");
        idle();
        layout(root);
        clickText(root, "Canciones que te gustan");
        layout(root);
        assertNotNull(findPrefix(root, "Playlist · 1 canción"));
        a.onBackPressed();
        idle();

        // library tab: chips, create playlist, add song, open, delete
        clickText(root, "Tu biblioteca");
        layout(root);
        clickText(root, "Artistas");
        layout(root);
        assertNotNull(findText(root, "SoundHelix"));
        clickText(root, "Álbumes");
        layout(root);
        clickText(root, "Descubrimientos Vol. 2");
        layout(root);
        assertNotNull(findPrefix(root, "Álbum · 8 canciones"));
        a.onBackPressed();
        idle();
        layout(root);
        clickText(root, "Playlists");
        layout(root);

        assertTrue(lib.createPlaylist("Para correr"));
        assertTrue(lib.addToPlaylist("Para correr", lib.onlineTracks().get(0)));
        assertTrue(lib.addToPlaylist("Para correr", lib.onlineTracks().get(5)));
        idle();
        layout(root);
        clickText(root, "Para correr");
        layout(root);
        assertNotNull(findPrefix(root, "Playlist · 2 canciones"));
        ListView detail = findList(root);
        // click the big green play button: find by content - it's the only 56dp oval; use playList directly via item click
        detail.performItemClick(null, detail.getHeaderViewsCount() + 1, 0);
        idle();
        assertEquals(lib.onlineTracks().get(5), p.current());
        assertEquals("Para correr", p.queueName());

        // add-to-playlist dialog
        Dialogs.addToPlaylist(a, lib.onlineTracks().get(7));
        idle();
        AlertDialog dlg = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull(dlg);
        shadowOf(dlg).clickOnItem(1);
        idle();
        assertEquals(3, lib.playlist("Para correr").size());

        // new playlist dialog
        Dialogs.newPlaylist(a, null);
        idle();
        AlertDialog np = ShadowAlertDialog.getLatestAlertDialog();
        np.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
        System.out.println("playlists: " + lib.playlistNames());
        assertEquals(2, lib.playlistNames().size());

        // persistence survives a fresh Library (reads prefs)
        java.lang.reflect.Field f = Library.class.getDeclaredField("sInstance");
        f.setAccessible(true);
        f.set(null, null);
        Library lib2 = Library.get(app);
        assertEquals(3, lib2.playlist("Para correr").size());
        assertEquals(1, lib2.likedTracks().size());
        f.set(null, lib);

        // now playing screen
        ActivityController<NowPlayingActivity> nc = Robolectric.buildActivity(NowPlayingActivity.class).setup();
        NowPlayingActivity np2 = nc.get();
        View nroot = np2.getWindow().getDecorView();
        layout(nroot);
        idle();
        assertNotNull(findText(nroot, p.current().title));
        p.toggleShuffle();
        p.cycleRepeat();
        p.cycleRepeat();
        assertEquals(PlayerEngine.REPEAT_ONE, p.repeatMode());
        Track before = p.current();
        p.seekTo(179990);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
        assertEquals("repeat one keeps the track", before, p.current());
        p.cycleRepeat();
        p.next();
        idle();
        p.previous();
        idle();
        layout(nroot);
        nc.pause().stop().destroy();

        // error handling: a failing source skips to next
        ShadowMediaPlayer.setMediaInfoProvider(new ShadowMediaPlayer.MediaInfoProvider() {
            @Override
            public ShadowMediaPlayer.MediaInfo get(DataSource ds) {
                return new ShadowMediaPlayer.MediaInfo(180000, 50);
            }
        });

        // close from notification stops everything and service shuts down
        new MediaActionReceiver().onReceive(app, new Intent(MediaActionReceiver.CLOSE));
        idle();
        assertNull(p.current());
        layout(root);
        sc.destroy();

        // rotate / recreate main activity
        c.recreate();
        idle();
        c.pause().stop().destroy();
        System.out.println("SMOKE TEST OK");
    }

    @Test
    public void localLibraryAndNoPermission() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        // No permission: app must still start and show the permission card.
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
        idle();
        View root = c.get().getWindow().getDecorView();
        layout(root);
        assertNotNull(findText(root, "Escucha tu propia música"));
        clickText(root, "Tu biblioteca");
        layout(root);
        clickText(root, "Tu música");
        layout(root);
        c.pause().stop().destroy();
        System.out.println("NO-PERMISSION TEST OK");
    }
}

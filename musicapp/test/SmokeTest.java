package com.agus.sonora;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
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
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowMediaPlayer;
import org.robolectric.shadows.ShadowToast;
import org.robolectric.shadows.util.DataSource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SmokeTest {

    Fixtures net;
    final List<String> opened = new ArrayList<>();

    @Before
    public void reset() throws Exception {
        for (Class<?> k : new Class<?>[]{Library.class, PlayerEngine.class}) {
            java.lang.reflect.Field f = k.getDeclaredField("sInstance");
            f.setAccessible(true);
            f.set(null, null);
        }
        PlaybackService.running = false;
        net = new Fixtures();
        Net.fetcher = net;
        ShadowMediaPlayer.setMediaInfoProvider(new ShadowMediaPlayer.MediaInfoProvider() {
            @Override
            public ShadowMediaPlayer.MediaInfo get(DataSource ds) {
                opened.add(ds.toString());
                return new ShadowMediaPlayer.MediaInfo(30000, 50);
            }
        });
    }

    static void idle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300));
    }

    /** Lets background network threads finish and their callbacks run. */
    static void settle() throws InterruptedException {
        for (int i = 0; i < 40; i++) {
            Thread.sleep(25);
            idle();
        }
    }

    static void collect(View v, List<View> out) {
        out.add(v);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), out);
        }
    }

    static TextView findPrefix(View root, String text) {
        List<View> all = new ArrayList<>();
        collect(root, all);
        for (View v : all) {
            if (v instanceof TextView && ((TextView) v).getText().toString().startsWith(text) && v.isShown()) return (TextView) v;
        }
        return null;
    }

    static TextView findText(View root, String text) {
        List<View> all = new ArrayList<>();
        collect(root, all);
        for (View v : all) {
            if (v instanceof TextView && text.equals(((TextView) v).getText().toString()) && v.isShown()) return (TextView) v;
        }
        return null;
    }

    static void clickText(View root, String text) {
        layout(root);
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
        layout(root);
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
    public void realMusicFlow() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        shadowOf(app).grantPermissions(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS);

        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity a = c.get();
        settle();
        View root = a.getWindow().getDecorView();
        layout(root);
        Library lib = Library.get(app);
        PlayerEngine p = PlayerEngine.get(app);

        // shelves loaded from the (fake) internet
        Library.Section top = lib.section("top");
        assertEquals(Library.READY, top.state);
        assertEquals("track without preview is skipped", 50, top.tracks.size());
        assertEquals("Monaco", top.tracks.get(0).title);
        assertEquals("Bad Bunny", top.tracks.get(0).artist);
        assertEquals(181000, top.tracks.get(1).durationMs);
        Library.Section radio = lib.section("radio");
        assertEquals("radio mirror fallback works", Library.READY, radio.state);
        assertEquals("duplicates and broken stations removed", 5, radio.tracks.size());
        for (Library.Section s : lib.sections()) assertEquals(s.title, Library.READY, s.state);
        assertNotNull(findText(root, "Top 50 mundial"));
        assertNotNull(findText(root, "Radios en vivo"));
        assertNotNull(findText(root, "Reggaetón"));

        // tap a real song on the home shelf -> fresh preview link fetched, then plays
        for (Track t : top.tracks) t.streamFetchedAt -= 5 * 60 * 1000L; // links listed a while ago
        int lookups = net.trackLookups.get();
        clickText(root, "Hips Don't Lie");
        settle();
        assertEquals("Hips Don't Lie", p.current().title);
        assertTrue("plays", p.isPlaying());
        assertEquals("Top 50 mundial", p.queueName());
        assertEquals(50, p.upcoming().size() + 1); // index 1 of 50
        assertTrue("fresh link used", p.current().streamUrl.contains("hdnea=fresh"));
        assertEquals(1, net.trackLookups.get() - lookups);
        System.out.println("lookups for first play: " + (net.trackLookups.get() - lookups));

        // notification + service
        Intent started = shadowOf(app).getNextStartedService();
        assertNotNull(started);
        ServiceController<PlaybackService> sc = Robolectric.buildService(PlaybackService.class, started).create().startCommand(0, 1);
        settle();
        NotificationManager nm = (NotificationManager) app.getSystemService(Application.NOTIFICATION_SERVICE);
        Notification n = shadowOf(nm).getAllNotifications().get(0);
        assertEquals("Hips Don't Lie", n.extras.getString(Notification.EXTRA_TITLE));

        // next / completion
        new MediaActionReceiver().onReceive(app, new Intent(MediaActionReceiver.NEXT));
        settle();
        assertEquals("Despacito", p.current().title);
        assertTrue(p.isPlaying());
        p.seekTo(29990);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
        settle();
        assertEquals("auto-advance after the 30 s preview", "La Bachata", p.current().title);

        // radio: live, full songs, seeking ignored
        p.playList(radio.tracks, 1, "Radios en vivo");
        settle();
        assertTrue(p.current().isLive());
        assertEquals("Los 40 Argentina", p.current().title);
        assertTrue(p.isPlaying());
        assertTrue(p.current().streamUrl.contains("los40.mp3"));
        p.seekTo(10000);
        ActivityController<NowPlayingActivity> npc = Robolectric.buildActivity(NowPlayingActivity.class).setup();
        View nroot = npc.get().getWindow().getDecorView();
        layout(nroot);
        idle();
        assertNotNull(findText(nroot, "EN VIVO"));
        assertNotNull(findText(nroot, "● EN VIVO"));
        npc.pause().stop().destroy();

        // like a Deezer song + save to playlist; survives a restart (metadata persisted)
        Track monaco = top.tracks.get(0);
        lib.toggleLike(monaco);
        lib.toggleLike(radio.tracks.get(2));
        assertTrue(lib.createPlaylist("Previa"));
        lib.addToPlaylist("Previa", top.tracks.get(4));
        java.lang.reflect.Field f = Library.class.getDeclaredField("sInstance");
        f.setAccessible(true);
        f.set(null, null);
        net.offline = true; // restart without internet: saved songs still listed
        Library lib2 = Library.get(app);
        assertEquals(2, lib2.likedTracks().size());
        Track restored = lib2.likedTracks().get(1);
        assertEquals("Monaco", restored.title);
        assertEquals("Bad Bunny", restored.artist);
        assertNotNull(restored.artUrl);
        assertEquals(1000, restored.remoteId);
        assertEquals("Bohemian Rhapsody", lib2.playlist("Previa").get(0).title);
        assertTrue(lib2.likedTracks().get(0).isLive());
        // collection groups saved online songs too
        assertNotNull(lib2.group(false).get("Bad Bunny"));
        net.offline = false;
        // play restored song: its stored link is considered stale -> refetched
        int before = net.trackLookups.get();
        PlayerEngine.get(app).playList(lib2.likedTracks(), 1, "Me gusta");
        settle();
        assertEquals(before + 1, net.trackLookups.get());
        assertTrue(PlayerEngine.get(app).isPlaying());
        f.set(null, lib);

        // search: local + Deezer + radios
        clickText(root, "Buscar");
        List<View> all = new ArrayList<>();
        collect(root, all);
        EditText input = null;
        for (View v : all) if (v instanceof EditText) input = (EditText) v;
        input.setText("soda");
        idle();
        layout(root);
        assertNotNull(findPrefix(root, "Buscando"));
        settle();
        layout(root);
        ListView results = findList(root);
        assertTrue("online results shown: " + results.getAdapter().getCount(), results.getAdapter().getCount() >= 15);
        results.performItemClick(results.getAdapter().getView(0, null, results), 0, 0);
        settle();
        assertTrue(p.queueName().startsWith("Búsqueda"));
        input.setText("error");
        settle();
        layout(root);
        assertTrue("radio results still shown when Deezer errors", findList(root).getAdapter().getCount() > 0);
        net.offline = true;
        input.setText("sin red");
        settle();
        layout(root);
        assertNotNull(findPrefix(root, "No hay conexión"));
        net.offline = false;
        input.setText("");
        idle();

        // artist page pulls the artist's top songs
        clickText(root, "Inicio");
        settle();
        lib.loadArtistTop("Queen", 54);
        settle();
        assertNotNull(lib.artistTop("Queen"));
        assertEquals(12, lib.artistTop("Queen").size());

        // "Ver todo" opens the full chart
        a.onBackPressed();
        idle();
        layout(root);
        List<View> vt = new ArrayList<>();
        collect(root, vt);
        List<TextView> verTodos = new ArrayList<>();
        for (View v : vt) if (v instanceof TextView && "Ver todo".equals(((TextView) v).getText().toString())) verTodos.add((TextView) v);
        assertTrue(verTodos.size() >= 2);
        verTodos.get(1).performClick(); // 0 is "Escuchado recientemente", 1 is the Top 50
        settle();
        layout(root);
        ListView lv = findList(root);
        assertEquals(52, lv.getCount()); // header + 50 songs + footer
        View header = lv.getAdapter().getView(0, null, lv);
        List<View> hv = new ArrayList<>();
        collect(header, hv);
        boolean found = false;
        for (View v : hv) if (v instanceof TextView && ((TextView) v).getText().toString().startsWith("Playlist · 50 canciones")) found = true;
        assertTrue("detail header subtitle", found);
        a.onBackPressed();
        settle();

        new MediaActionReceiver().onReceive(app, new Intent(MediaActionReceiver.CLOSE));
        idle();
        assertNull(p.current());
        sc.destroy();
        c.pause().stop().destroy();
        System.out.println("REAL MUSIC FLOW OK; requests=" + net.urls.size());
    }

    @Test
    public void offlineThenRetry() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        net.offline = true;
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
        settle();
        View root = c.get().getWindow().getDecorView();
        layout(root);
        Library lib = Library.get(app);
        assertEquals(Library.FAILED, lib.section("top").state);
        assertNotNull(findText(root, "Sin conexión"));
        assertNotNull(findText(root, "Escucha tu propia música")); // no permission card
        net.offline = false;
        clickText(root, "Reintentar");
        settle();
        assertEquals(Library.READY, lib.section("top").state);

        // preview link fetch failing -> toast and skip, no crash
        PlayerEngine p = PlayerEngine.get(app);
        net.offline = true;
        Track t0 = lib.section("top").tracks.get(0);
        Track t1 = lib.section("top").tracks.get(1);
        t0.streamFetchedAt = 0;
        t1.streamFetchedAt = 0;
        List<Track> two = new ArrayList<>();
        two.add(t0);
        two.add(t1);
        p.playList(two, 0, "x");
        settle();
        assertTrue(ShadowToast.getTextOfLatestToast().contains("No se pudo reproducir"));
        assertFalse(p.isPlaying());
        net.offline = false;
        p.playList(two, 0, "x");
        settle();
        assertTrue(p.isPlaying());
        c.pause().stop().destroy();
        System.out.println("OFFLINE TEST OK");
    }
}

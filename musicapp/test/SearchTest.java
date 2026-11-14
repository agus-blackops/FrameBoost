package com.agus.sonora;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 2.0: one search for songs, radios, artists, albums, podcasts and free albums. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SearchTest {

    Application app;
    Fixtures net;

    @Before
    public void reset() throws Exception {
        BackupTest.forgetAll();
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

    View search(ActivityController<MainActivity> c, String q) throws Exception {
        MainActivity a = c.get();
        java.lang.reflect.Method sel = MainActivity.class.getDeclaredMethod("selectTab", int.class);
        sel.setAccessible(true);
        sel.invoke(a, 1);
        SmokeTest.settle();
        View root = a.getWindow().getDecorView();
        List<View> all = new ArrayList<>();
        SmokeTest.collect(root, all);
        for (View v : all) if (v instanceof EditText) ((EditText) v).setText(q);
        SmokeTest.settle();
        SmokeTest.layout(root);
        return root;
    }

    @Test
    public void everythingInOneSearch() throws Exception {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
        SmokeTest.settle();
        View root = search(c, "lluvia");
        for (String t : new String[]{"Artistas", "Álbumes", "Podcasts", "Álbumes libres", "Canciones y radios"}) {
            assertNotNull(t, SmokeTest.findText(root, t));
        }
        assertNotNull("an artist card", SmokeTest.findText(root, "Otro Artista"));
        assertNotNull("a free album", SmokeTest.findText(root, "Noches de Verano"));

        // tapping a song still plays the right one below the rows
        ListView list = SmokeTest.findList(root);
        int first = list.getHeaderViewsCount();
        assertEquals(1, first);
        View row = list.getAdapter().getView(first, null, list);
        list.performItemClick(row, first, first);
        SmokeTest.settle();
        PlayerEngine p = PlayerEngine.get(app);
        assertNotNull(p.current());
        assertEquals("Búsqueda: lluvia", p.queueName());
        assertEquals(0, p.queueIndex());

        // an album card opens its page
        SmokeTest.clickText(root, "Noches de Verano");
        SmokeTest.settle();
        SmokeTest.layout(root);
        assertNotNull(SmokeTest.findPrefix(root, "Álbum libre · Colectivo Sur"));
        c.pause().stop().destroy();
    }

    @Test
    public void onlyCompleteSongsLeavesOutCatalogArtistsAndAlbums() throws Exception {
        Library.get(app).setFullOnly(true);
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
        SmokeTest.settle();
        int before = net.urls.size();
        View root = search(c, "lluvia");
        assertNull(SmokeTest.findText(root, "Artistas"));
        assertNull(SmokeTest.findText(root, "Álbumes"));
        assertNotNull(SmokeTest.findText(root, "Podcasts"));
        assertNotNull(SmokeTest.findText(root, "Álbumes libres"));
        for (String u : net.urls.subList(before, net.urls.size())) assertFalse(u, u.contains("api.deezer.com/search"));
        c.pause().stop().destroy();
    }

    @Test
    public void nothingFoundStillSaysSo() throws Exception {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
        SmokeTest.settle();
        View root = search(c, "zzzz");
        assertNotNull(SmokeTest.findText(root, "No se encontró nada para \"zzzz\""));
        assertNull(SmokeTest.findText(root, "Artistas"));
        c.pause().stop().destroy();
    }
}

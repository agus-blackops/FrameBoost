package com.agus.sonora;

import static org.junit.Assert.*;

import android.app.Application;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.view.View;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Locale;

/** 2.0: dark, light or system theme, and the system accent color. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ThemeTest {

    Application app;

    @Before
    public void reset() throws Exception {
        BackupTest.forgetAll();
        Effects.resetForTest();
        PlaybackService.running = false;
        Locale.setDefault(Locale.US);
        Fixtures net = new Fixtures();
        Net.fetcher = net;
        Net.resolver = net;
        app = RuntimeEnvironment.getApplication();
        PlayerEngine.audioFactory = SimAudio.factory(new SimAudio.Durations() {
            @Override
            public long of(String uri) {
                return 60000;
            }
        }, new ArrayList<String>());
    }

    static int windowColor(android.app.Activity a) {
        return ((ColorDrawable) a.getWindow().getDecorView().getBackground()).getColor();
    }

    @Test
    public void darkByDefaultLikeAlways() throws Exception {
        assertEquals(Theme.DARK, Theme.mode(app));
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
        assertTrue(Ui.dark);
        assertEquals(0xFF121212, Ui.BG);
        assertEquals(0xFFFFFFFF, Ui.TEXT);
        assertEquals(Ui.BRAND, Ui.GREEN);
        assertEquals("black on Sonora green", 0xFF000000, Ui.ON_ACCENT);
        assertEquals(0xFF121212, windowColor(c.get()));
        c.pause().stop().destroy();
    }

    @Test
    public void lightThemeFromAjustesRebuildsOpenScreens() throws Exception {
        ActivityController<MainActivity> main = Robolectric.buildActivity(MainActivity.class).setup();
        SmokeTest.settle();
        main.pause();
        ActivityController<SettingsActivity> s = Robolectric.buildActivity(SettingsActivity.class,
                new Intent(app, SettingsActivity.class)).setup();
        View root = s.get().getWindow().getDecorView();
        SmokeTest.layout(root);
        assertNotNull(SmokeTest.findText(root, "Apariencia"));
        SmokeTest.clickText(root, "Tema");
        android.app.AlertDialog dlg = (android.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
        dlg.getListView().performItemClick(null, 1, 1); // Claro
        SmokeTest.settle(); // Ajustes rebuilds itself
        assertEquals(Theme.LIGHT, Theme.mode(app));
        assertFalse(Ui.dark);
        assertEquals(0xFFFFFFFF, Ui.BG);
        assertEquals(0xFF121212, Ui.TEXT);
        assertEquals("green dark enough to read on white", Ui.BRAND_ON_LIGHT, Ui.GREEN);
        assertEquals("computed from the accent: black reads well on this green", 0xFF000000, Ui.ON_ACCENT);
        int lightStatus = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        s.pause().stop().destroy();

        // back to the main screen: it notices and rebuilds itself in the new theme
        main.resume();
        SmokeTest.settle();
        MainActivity m = main.get();
        assertEquals(0xFFFFFFFF, windowColor(m));
        assertTrue((m.getWindow().getDecorView().getSystemUiVisibility() & lightStatus) != 0);
        View r2 = m.getWindow().getDecorView();
        SmokeTest.layout(r2);
        android.widget.TextView greet = null;
        for (String g : new String[]{"Buenos días", "Buenas tardes", "Buenas noches"}) {
            if (SmokeTest.findText(r2, g) != null) greet = SmokeTest.findText(r2, g);
        }
        assertNotNull(greet);
        assertEquals("dark text on white", 0xFF121212, greet.getCurrentTextColor());
        main.pause().stop().destroy();
    }

    @Test
    public void systemModeFollowsTheNightSetting() {
        Theme.setMode(app, Theme.SYSTEM);
        RuntimeEnvironment.setQualifiers("+night");
        assertTrue(Theme.isDark(RuntimeEnvironment.getApplication()));
        RuntimeEnvironment.setQualifiers("+notnight");
        assertFalse(Theme.isDark(RuntimeEnvironment.getApplication()));
    }

    @Test
    public void systemAccentOnAndroid12AndUp() throws Exception {
        assertTrue(Theme.systemAccentAvailable());
        Theme.setSystemAccent(app, true);
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
        assertEquals(app.getColor(android.R.color.system_accent1_200), Ui.GREEN);
        assertEquals("readable text on the accent", Ui.luminance(Ui.GREEN) > 0.4 ? 0xFF000000 : 0xFFFFFFFF, Ui.ON_ACCENT);
        c.pause().stop().destroy();
    }

    @Test
    public void coverTintsKeepTextReadable() {
        Ui.dark = true;
        int dark = Ui.wash(0xFFE52D27);
        assertTrue(Ui.luminance(dark) < 0.2);
        Ui.dark = false;
        int light = Ui.wash(0xFFE52D27);
        assertTrue(Ui.luminance(light) > 0.7);
        Ui.dark = true;
        assertEquals(0x80FFFFFF, Ui.alpha(0xFFFFFFFF, 0x80));
    }
}

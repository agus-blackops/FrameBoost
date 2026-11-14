package com.agus.sonora;

import android.content.Context;
import android.content.SharedPreferences;

/** Appearance settings: light, dark or like the system, and the accent color. */
final class Theme {

    static final int SYSTEM = 0;
    static final int DARK = 1;
    static final int LIGHT = 2;

    private Theme() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("appearance", Context.MODE_PRIVATE);
    }

    /** Dark unless chosen otherwise: Sonora's look since 1.0. */
    static int mode(Context c) {
        return prefs(c).getInt("mode", DARK);
    }

    static void setMode(Context c, int mode) {
        if (mode == mode(c)) return;
        prefs(c).edit().putInt("mode", mode).apply();
        Ui.version++;
    }

    static boolean isDark(Context c) {
        int m = mode(c);
        if (m == SYSTEM) {
            int night = c.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            return night != android.content.res.Configuration.UI_MODE_NIGHT_NO;
        }
        return m != LIGHT;
    }

    /** Material You colors exist from Android 12. */
    static boolean systemAccentAvailable() {
        return android.os.Build.VERSION.SDK_INT >= 31;
    }

    static int accent(Context c, boolean dark) {
        if (systemAccent(c) && systemAccentAvailable()) {
            return c.getColor(dark ? android.R.color.system_accent1_200 : android.R.color.system_accent1_600);
        }
        return dark ? Ui.BRAND : Ui.BRAND_ON_LIGHT;
    }

    static boolean systemAccent(Context c) {
        return prefs(c).getBoolean("systemAccent", false);
    }

    static void setSystemAccent(Context c, boolean on) {
        if (on == systemAccent(c)) return;
        prefs(c).edit().putBoolean("systemAccent", on).apply();
        Ui.version++;
    }
}

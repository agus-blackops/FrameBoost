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

    static int mode(Context c) {
        return prefs(c).getInt("mode", SYSTEM);
    }

    static void setMode(Context c, int mode) {
        prefs(c).edit().putInt("mode", mode).apply();
    }

    static boolean systemAccent(Context c) {
        return prefs(c).getBoolean("systemAccent", false);
    }

    static void setSystemAccent(Context c, boolean on) {
        prefs(c).edit().putBoolean("systemAccent", on).apply();
    }
}

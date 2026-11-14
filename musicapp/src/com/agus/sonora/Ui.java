package com.agus.sonora;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

/** Small view factory so screens can be built in code with a consistent dark look. */
final class Ui {

    // The palette of the current theme, set by apply() when a screen opens (dark by default).
    static int BG = 0xFF121212;
    static int SURFACE = 0xFF181818;
    static int CARD = 0xFF2A2A2A;
    /** The accent: Sonora green, or the system's color (Android 12+) if chosen in Ajustes. */
    static int GREEN = 0xFF1DB954;
    static int TEXT = 0xFFFFFFFF;
    static int SUB = 0xFFB3B3B3;
    static int MUTED = 0xFF7A7A7A;
    /** Text and icons drawn on the accent color. */
    static int ON_ACCENT = 0xFF000000;
    static int NAV = 0xF0000000;
    /** Unfilled part of progress bars, faint circles. */
    static int TRACK = 0x4DFFFFFF;
    static int RIPPLE = 0x33FFFFFF;
    static int SEARCH_BG = 0xFFFFFFFF;
    static boolean dark = true;

    static final int BRAND = 0xFF1DB954;
    /** Brand green dark enough to read on white. */
    static final int BRAND_ON_LIGHT = 0xFF13863C;

    /** Bumped whenever the theme changes, so open screens can rebuild themselves. */
    static int version;

    /**
     * Picks the palette for the current theme and dresses the window (status and navigation
     * bars). Call first thing in onCreate, before super.onCreate. Returns the version applied.
     */
    static int apply(android.app.Activity a) {
        dark = Theme.isDark(a);
        a.setTheme(dark ? R.style.AppTheme : R.style.AppThemeLight);
        if (dark) {
            BG = 0xFF121212;
            SURFACE = 0xFF181818;
            CARD = 0xFF2A2A2A;
            TEXT = 0xFFFFFFFF;
            SUB = 0xFFB3B3B3;
            MUTED = 0xFF7A7A7A;
            NAV = 0xF0000000;
            TRACK = 0x4DFFFFFF;
            RIPPLE = 0x33FFFFFF;
            SEARCH_BG = 0xFFFFFFFF;
        } else {
            BG = 0xFFFFFFFF;
            SURFACE = 0xFFF6F6F6;
            CARD = 0xFFECECEC;
            TEXT = 0xFF121212;
            SUB = 0xFF5C5C5C;
            MUTED = 0xFF8A8A8A;
            NAV = 0xF5FFFFFF;
            TRACK = 0x26000000;
            RIPPLE = 0x22000000;
            SEARCH_BG = 0xFFECECEC;
        }
        GREEN = Theme.accent(a, dark);
        ON_ACCENT = luminance(GREEN) > 0.4 ? 0xFF000000 : 0xFFFFFFFF;
        android.view.Window w = a.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(BG));
        w.setStatusBarColor(dark ? 0xFF000000 : BG);
        w.setNavigationBarColor(dark ? 0xFF000000 : BG);
        View decor = w.getDecorView();
        int flags = decor.getSystemUiVisibility();
        int light = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        decor.setSystemUiVisibility(dark ? flags & ~light : flags | light);
        return version;
    }

    /** True when the theme changed since a screen applied {@code applied}: it should recreate(). */
    static boolean stale(int applied) {
        return applied != version;
    }

    static double luminance(int c) {
        double r = ((c >> 16) & 0xFF) / 255.0, g = ((c >> 8) & 0xFF) / 255.0, b = (c & 0xFF) / 255.0;
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    /** {@code color} with alpha {@code a} (0..255). */
    static int alpha(int color, int a) {
        return (color & 0x00FFFFFF) | (a << 24);
    }

    /**
     * A background tinted by a cover's color that keeps text readable: darkened in the dark
     * theme, a pale wash in the light one.
     */
    static int wash(int color) {
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        if (dark) return 0xFF000000 | ((r * 45 / 100) << 16) | ((g * 45 / 100) << 8) | (b * 45 / 100);
        return 0xFF000000 | ((255 - (255 - r) * 30 / 100) << 16) | ((255 - (255 - g) * 30 / 100) << 8) | (255 - (255 - b) * 30 / 100);
    }

    static final int MATCH = LinearLayout.LayoutParams.MATCH_PARENT;
    static final int WRAP = LinearLayout.LayoutParams.WRAP_CONTENT;

    private Ui() {
    }

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }

    static TextView text(Context c, CharSequence s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    static ImageView icon(Context c, int res, int sizeDp, int tint) {
        ImageView v = new ImageView(c);
        v.setImageResource(res);
        v.setColorFilter(tint);
        v.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int pad = dp(c, Math.max(0, (sizeDp - 24) / 2f));
        v.setPadding(pad, pad, pad, pad);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return v;
    }

    /** A tappable icon with a round ripple. */
    static ImageView iconButton(Context c, int res, int sizeDp, int tint, View.OnClickListener l) {
        ImageView v = icon(c, res, sizeDp, tint);
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(RIPPLE), null, null));
        v.setOnClickListener(l);
        v.setClickable(true);
        return v;
    }

    static GradientDrawable rounded(int color, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }

    /** Green when on, grey when off. */
    static void tintSwitch(android.widget.Switch sw) {
        int[][] states = {{android.R.attr.state_checked}, {}};
        sw.setThumbTintList(new ColorStateList(states, new int[]{GREEN, dark ? 0xFFB3B3B3 : 0xFFF2F2F2}));
        sw.setTrackTintList(new ColorStateList(states, new int[]{alpha(GREEN, 0x88), dark ? 0x55FFFFFF : 0x40000000}));
    }

    static GradientDrawable oval(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    /** Ripple on top of an optional background (bounded to it). */
    static Drawable ripple(Drawable content) {
        Drawable mask = content != null ? content : new ColorDrawable(0xFFFFFFFF);
        return new RippleDrawable(ColorStateList.valueOf(RIPPLE), content, mask);
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    static LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, WRAP, w);
    }

    static String time(long ms) {
        if (ms <= 0) return "0:00";
        long s = ms / 1000;
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    /** "3 oct · 42 min", or "3 oct · faltan 12 min" when the episode was started. */
    static String episodeInfo(Context c, Track t) {
        Library lib = Library.get(c);
        StringBuilder b = new StringBuilder();
        if (lib.isNew(t)) b.append("Nuevo · ");
        if (t.dateAdded > 0) {
            b.append(new java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault())
                    .format(new java.util.Date(t.dateAdded)));
        }
        if (lib.isPlayed(t)) {
            if (b.length() > 0 && b.charAt(b.length() - 1) != ' ') b.append(" · ");
            return b.append("Escuchado").toString();
        }
        long total = t.durationMs;
        long done = Library.get(c).progress(t);
        long dur = total > 0 ? total : Library.get(c).progressDuration(t);
        if (dur > 0) {
            if (b.length() > 0 && b.charAt(b.length() - 1) != ' ') b.append(" · ");
            long minutes = Math.max(1, (done > 0 ? dur - done : dur) / 60000);
            String span = minutes >= 60 ? (minutes / 60) + " h " + (minutes % 60) + " min" : minutes + " min";
            b.append(done > 0 ? "faltan " + span : span);
        }
        String out = b.toString();
        return out.endsWith(" · ") ? out.substring(0, out.length() - 3) : out;
    }

    static String songs(int n) {
        return n == 1 ? "1 canción" : n + " canciones";
    }

    static String totalLength(List<Track> tracks) {
        long ms = 0;
        for (Track t : tracks) ms += t.durationMs;
        long min = ms / 60000;
        if (min <= 0) return "";
        if (min < 60) return min + " min";
        return (min / 60) + " h " + (min % 60) + " min";
    }
}

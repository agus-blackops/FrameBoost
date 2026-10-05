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

    static final int BG = 0xFF121212;
    static final int SURFACE = 0xFF181818;
    static final int CARD = 0xFF2A2A2A;
    static final int GREEN = 0xFF1DB954;
    static final int TEXT = 0xFFFFFFFF;
    static final int SUB = 0xFFB3B3B3;
    static final int MUTED = 0xFF7A7A7A;

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
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), null, null));
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

    static GradientDrawable oval(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    /** Ripple on top of an optional background (bounded to it). */
    static Drawable ripple(Drawable content) {
        Drawable mask = content != null ? content : new ColorDrawable(0xFFFFFFFF);
        return new RippleDrawable(ColorStateList.valueOf(0x22FFFFFF), content, mask);
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

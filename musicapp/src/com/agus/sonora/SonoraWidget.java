package com.agus.sonora;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.widget.RemoteViews;

/**
 * Home-screen widget: what's playing, with previous / play-pause / next. It follows the player
 * (PlayerEngine calls {@link #refresh} on every change) and only talks to the launcher when
 * something it shows changed.
 */
public final class SonoraWidget extends AppWidgetProvider {

    private static String lastSignature;
    private static String artKey;
    private static Bitmap art;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        lastSignature = null;
        refresh(c);
    }

    static int[] ids(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        if (m == null) return new int[0];
        return m.getAppWidgetIds(new ComponentName(c, SonoraWidget.class));
    }

    /** Redraws the widgets if there are any and something visible changed. Main thread. */
    static void refresh(Context context) {
        final Context c = context.getApplicationContext();
        int[] ids = ids(c);
        if (ids.length == 0) return;
        PlayerEngine p = PlayerEngine.get(c);
        final Track t = p.current();
        String sub = t == null ? "" : (p.liveTitle() != null ? p.liveTitle() : t.artist);
        String sig = t == null ? "-" : t.key + "|" + t.title + "|" + sub + "|" + p.isActive() + "|" + (t.key.equals(artKey) && art != null);
        if (sig.equals(lastSignature)) return;
        lastSignature = sig;
        AppWidgetManager.getInstance(c).updateAppWidget(ids, views(c, t, sub, p.isActive()));
        if (t != null && !t.key.equals(artKey)) {
            artKey = t.key;
            art = null;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final Bitmap b = Covers.loadSync(c, t, 192);
                    MAIN.post(new Runnable() {
                        @Override
                        public void run() {
                            if (t.key.equals(artKey)) {
                                art = b;
                                refresh(c);
                            }
                        }
                    });
                }
            }).start();
        }
    }

    static RemoteViews views(Context c, Track t, String sub, boolean active) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);
        if (t == null) {
            v.setTextViewText(R.id.widget_title, "Sonora");
            v.setTextViewText(R.id.widget_artist, "Toca para elegir música");
            v.setImageViewResource(R.id.widget_cover, R.mipmap.ic_launcher);
        } else {
            v.setTextViewText(R.id.widget_title, t.title);
            v.setTextViewText(R.id.widget_artist, sub);
            Bitmap b = t.key.equals(artKey) && art != null ? art : Covers.generated(t, 128);
            v.setImageViewBitmap(R.id.widget_cover, b);
        }
        v.setImageViewResource(R.id.widget_play, active ? R.drawable.ic_pause : R.drawable.ic_play);
        v.setContentDescription(R.id.widget_play, active ? "Pausa" : "Reproducir");
        v.setOnClickPendingIntent(R.id.widget_prev, MediaActionReceiver.intent(c, MediaActionReceiver.PREV, 11));
        v.setOnClickPendingIntent(R.id.widget_play, MediaActionReceiver.intent(c, MediaActionReceiver.PLAY_PAUSE, 12));
        v.setOnClickPendingIntent(R.id.widget_next, MediaActionReceiver.intent(c, MediaActionReceiver.NEXT, 13));
        Intent open = new Intent(c, t == null ? MainActivity.class : NowPlayingActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(c, 14, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        v.setOnClickPendingIntent(R.id.widget_cover, pi);
        v.setOnClickPendingIntent(R.id.widget_texts, pi);
        return v;
    }

    /** Tests. */
    static void resetForTest() {
        lastSignature = null;
        artKey = null;
        art = null;
    }
}

package com.agus.sonora;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Album art: the picture embedded in a local file when there is one, otherwise a generated
 * gradient cover (stable per album) with a music note, in the spirit of auto-made playlists.
 */
public final class Covers {

    private static final int[][] PALETTE = {
            {0xFF8E2DE2, 0xFF4A00E0}, {0xFFE52D27, 0xFFB31217}, {0xFF1DB954, 0xFF0B6E2F},
            {0xFFF7971E, 0xFFFFD200}, {0xFF00C6FF, 0xFF0072FF}, {0xFFEC008C, 0xFFFC6767},
            {0xFF11998E, 0xFF38EF7D}, {0xFFFC4A1A, 0xFFF7B733}, {0xFF834D9B, 0xFFD04ED6},
            {0xFF283C86, 0xFF45A247}, {0xFF3A1C71, 0xFFFFAF7B}, {0xFF0F2027, 0xFF2C5364},
    };

    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(24 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };
    private static final ExecutorService IO = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Covers() {
    }

    /** Main accent colour for a track (used for the now-playing background). */
    public static int accent(Track t) {
        return PALETTE[Math.abs(seed(t).hashCode()) % PALETTE.length][0];
    }

    private static String seed(Track t) {
        return t.remote ? t.album + t.title : t.album + t.artist;
    }

    /** Sets {@code view} to the track's cover, loading embedded art off the main thread. */
    public static void load(final Context ctx, final Track t, final ImageView view, final int sizePx) {
        final String key = t.key + "@" + sizePx;
        view.setTag(key);
        Bitmap hit = CACHE.get(key);
        if (hit != null) {
            view.setImageBitmap(hit);
            return;
        }
        if (t.remote) {
            view.setImageBitmap(loadSync(ctx, t, sizePx));
            return;
        }
        String phKey = "gen:" + seed(t) + "@" + sizePx;
        Bitmap placeholder = CACHE.get(phKey);
        if (placeholder == null) CACHE.put(phKey, placeholder = generated(t, sizePx));
        view.setImageBitmap(placeholder);
        final Context app = ctx.getApplicationContext();
        IO.execute(new Runnable() {
            @Override
            public void run() {
                final Bitmap b = loadSync(app, t, sizePx);
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        if (key.equals(view.getTag())) view.setImageBitmap(b);
                    }
                });
            }
        });
    }

    /** Blocking variant for the notification / lock screen. */
    public static Bitmap loadSync(Context ctx, Track t, int sizePx) {
        String key = t.key + "@" + sizePx;
        Bitmap hit = CACHE.get(key);
        if (hit != null) return hit;
        Bitmap b = t.remote ? null : embedded(ctx, t, sizePx);
        if (b == null) b = generated(t, sizePx);
        CACHE.put(key, b);
        return b;
    }

    private static Bitmap embedded(Context ctx, Track t, int sizePx) {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(ctx, Uri.parse(t.key));
            byte[] art = r.getEmbeddedPicture();
            if (art == null) return null;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(art, 0, art.length, o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= sizePx && o.outHeight / (sample * 2) >= sizePx) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            Bitmap raw = BitmapFactory.decodeByteArray(art, 0, art.length, o);
            if (raw == null) return null;
            int side = Math.min(raw.getWidth(), raw.getHeight());
            Bitmap square = Bitmap.createBitmap(raw, (raw.getWidth() - side) / 2,
                    (raw.getHeight() - side) / 2, side, side);
            return side > sizePx ? Bitmap.createScaledBitmap(square, sizePx, sizePx, true) : square;
        } catch (Throwable e) {
            return null;
        } finally {
            try {
                r.release();
            } catch (Throwable ignored) {
            }
        }
    }

    public static Bitmap generated(Track t, int size) {
        return generated(seed(t), size, true);
    }

    /** A gradient tile; {@code note} draws a music note in the middle. */
    public static Bitmap generated(String seed, int size, boolean note) {
        size = Math.max(size, 16);
        int[] c = PALETTE[Math.abs(seed.hashCode()) % PALETTE.length];
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, size, size, c[0], c[1], Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, size, size, p);
        if (note) {
            p.setShader(null);
            p.setColor(Color.argb(200, 255, 255, 255));
            float s = size / 24f * 0.5f;
            float ox = size * 0.25f;
            float oy = size * 0.25f;
            Path path = new Path();
            // Material "music_note" glyph scaled into the middle half of the tile.
            path.moveTo(ox + 12 * s, oy + 3 * s);
            path.lineTo(ox + 12 * s, oy + 13.55f * s);
            path.cubicTo(ox + 11.41f * s, oy + 13.21f * s, ox + 10.73f * s, oy + 13 * s, ox + 10 * s, oy + 13 * s);
            path.cubicTo(ox + 7.79f * s, oy + 13 * s, ox + 6 * s, oy + 14.79f * s, ox + 6 * s, oy + 17 * s);
            path.cubicTo(ox + 6 * s, oy + 19.21f * s, ox + 7.79f * s, oy + 21 * s, ox + 10 * s, oy + 21 * s);
            path.cubicTo(ox + 12.21f * s, oy + 21 * s, ox + 14 * s, oy + 19.21f * s, ox + 14 * s, oy + 17 * s);
            path.lineTo(ox + 14 * s, oy + 7 * s);
            path.lineTo(ox + 18 * s, oy + 7 * s);
            path.lineTo(ox + 18 * s, oy + 3 * s);
            path.close();
            canvas.drawPath(path, p);
        }
        return b;
    }
}

package com.agus.sonora;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Song lyrics from LRCLIB (free, no account): time-synced when available, plain otherwise. */
final class Lyrics {

    static final class Line {
        final long ms;
        final String text;

        Line(long ms, String text) {
            this.ms = ms;
            this.text = text;
        }
    }

    static final class Result {
        final List<Line> synced;
        final String plain;
        final boolean instrumental;

        Result(List<Line> synced, String plain, boolean instrumental) {
            this.synced = synced;
            this.plain = plain;
            this.instrumental = instrumental;
        }

        boolean found() {
            return instrumental || !synced.isEmpty() || !plain.isEmpty();
        }
    }

    interface Callback {
        void onLyrics(Track track, Result result);
    }

    static final String API = "https://lrclib.net/api";
    static final Result NONE = new Result(new ArrayList<Line>(), "", false);

    private static final LruCache<String, Result> CACHE = new LruCache<>(40);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Pattern STAMP = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]");

    private Lyrics() {
    }

    static void clearCache() {
        CACHE.evictAll();
    }

    /** Looks the lyrics up in the background; {@code cb} runs on the main thread. */
    static void load(final Context ctx, final Track t, final Callback cb) {
        if (t.isLive()) {
            cb.onLyrics(t, NONE);
            return;
        }
        Result hit = CACHE.get(t.key);
        if (hit != null) {
            cb.onLyrics(t, hit);
            return;
        }
        final File dir = new File(ctx.getCacheDir(), "lyrics");
        Net.POOL.execute(new Runnable() {
            @Override
            public void run() {
                Result r = null;
                boolean definitive = false;
                File f = new File(dir, Integer.toHexString(t.key.hashCode()) + ".json");
                try {
                    if (f.exists()) {
                        r = parse(read(f));
                        definitive = true;
                    }
                } catch (Exception ignored) {
                }
                if (r == null) {
                    try {
                        String body = fetch(t);
                        r = body == null ? NONE : parse(body); // null body: LRCLIB has no lyrics for it
                        if (body != null && r.found()) {
                            dir.mkdirs();
                            FileOutputStream out = new FileOutputStream(f);
                            out.write(body.getBytes("UTF-8"));
                            out.close();
                        }
                        definitive = true; // a clean "not found" is an answer too
                    } catch (Exception e) {
                        r = null; // network trouble: don't cache, let the user retry
                    }
                }
                final Result result = r == null ? NONE : r;
                if (definitive) CACHE.put(t.key, result);
                final boolean failed = r == null;
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        cb.onLyrics(t, failed ? null : result);
                    }
                });
            }
        });
    }

    private static String fetch(Track t) throws Exception {
        String base = API + "/get?artist_name=" + Net.enc(t.artist) + "&track_name=" + Net.enc(t.title);
        if (!t.album.isEmpty() && !t.album.startsWith("Álbum desconocido")) base += "&album_name=" + Net.enc(t.album);
        if (t.durationMs > 0) base += "&duration=" + Math.round(t.durationMs / 1000.0);
        try {
            return Net.getString(base);
        } catch (java.io.IOException e) {
            if (e.getMessage() == null || !e.getMessage().contains("404")) throw e;
        }
        // Exact match missing: search and take the best candidate that has lyrics.
        String body = Net.getString(API + "/search?track_name=" + Net.enc(t.title)
                + "&artist_name=" + Net.enc(t.artist));
        JSONArray a = new JSONArray(body);
        JSONObject best = null;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            boolean synced = has(o, "syncedLyrics");
            boolean any = synced || has(o, "plainLyrics") || o.optBoolean("instrumental");
            if (!any) continue;
            if (best == null || (synced && !has(best, "syncedLyrics"))) best = o;
            if (synced) break;
        }
        return best == null ? null : best.toString();
    }

    /** True when the key holds a non-empty string (JSON null counts as missing). */
    private static boolean has(JSONObject o, String key) {
        return o.has(key) && !o.isNull(key) && !o.optString(key, "").isEmpty();
    }

    private static String read(File f) throws Exception {
        FileInputStream in = new FileInputStream(f);
        try {
            byte[] data = new byte[(int) f.length()];
            int off = 0;
            while (off < data.length) {
                int n = in.read(data, off, data.length - off);
                if (n < 0) break;
                off += n;
            }
            return new String(data, 0, off, "UTF-8");
        } finally {
            in.close();
        }
    }

    static Result parse(String json) throws Exception {
        JSONObject o = new JSONObject(json);
        boolean instrumental = o.optBoolean("instrumental", false);
        List<Line> synced = has(o, "syncedLyrics") ? parseLrc(o.optString("syncedLyrics")) : new ArrayList<Line>();
        String plain = has(o, "plainLyrics") ? o.optString("plainLyrics").trim() : "";
        if (plain.isEmpty() && !synced.isEmpty()) {
            StringBuilder b = new StringBuilder();
            for (Line l : synced) b.append(l.text).append('\n');
            plain = b.toString().trim();
        }
        return new Result(synced, plain, instrumental);
    }

    /** Parses "[mm:ss.xx] text" lines (several stamps per line allowed) sorted by time. */
    static List<Line> parseLrc(String lrc) {
        List<Line> out = new ArrayList<>();
        for (String raw : lrc.split("\r?\n")) {
            Matcher m = STAMP.matcher(raw);
            List<Long> stamps = new ArrayList<>();
            int end = 0;
            while (m.find()) {
                long ms = Long.parseLong(m.group(1)) * 60000L + Long.parseLong(m.group(2)) * 1000L;
                String frac = m.group(3);
                if (frac != null) {
                    long f = Long.parseLong(frac);
                    ms += frac.length() == 1 ? f * 100 : frac.length() == 2 ? f * 10 : f;
                }
                stamps.add(ms);
                end = m.end();
            }
            if (stamps.isEmpty()) continue; // metadata tags like [ar:...]
            String text = raw.substring(end).trim();
            for (long ms : stamps) out.add(new Line(ms, text));
        }
        Collections.sort(out, new Comparator<Line>() {
            @Override
            public int compare(Line a, Line b) {
                return Long.compare(a.ms, b.ms);
            }
        });
        return out;
    }

    /** Index of the line being sung at {@code posMs}, or -1 before the first line. */
    static int currentLine(List<Line> lines, long posMs) {
        int lo = 0, hi = lines.size() - 1, ans = -1;
        long p = posMs + 200; // highlight slightly early so it feels in time
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (lines.get(mid).ms <= p) {
                ans = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return ans;
    }
}

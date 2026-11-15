package com.agus.sonora;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Songs and episodes kept on the phone to play without internet. Only what is complete and free
 * can be downloaded: songs from Audius and the Internet Archive, and podcast episodes (never the
 * 30-second previews). Files live in the app's own folder, so no storage permission is needed and
 * they go away with the app. One download at a time; the queue survives restarts. All public
 * methods are for the main thread.
 */
final class Downloads {

    interface Listener {
        /** Something was queued, finished, failed or removed. */
        void onDownloadsChanged();

        /** Only the percentage of the running download moved (cheap to show). */
        void onDownloadProgress(Track t, int percent);
    }

    /** Progress is told in steps of this many points, not on every byte. */
    static final int PROGRESS_STEP = 5;

    /** Whether the current connection is metered (mobile data); pluggable for tests. */
    interface Metered {
        boolean now(Context c);
    }

    static final Metered SYSTEM = new Metered() {
        @Override
        public boolean now(Context c) {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            return cm != null && cm.isActiveNetworkMetered();
        }
    };

    static volatile Metered metered = SYSTEM;

    static final int NONE = 0;
    static final int QUEUED = 1;
    static final int RUNNING = 2;
    static final int DONE = 3;
    static final int FAILED = 4;
    /** Queued, waiting for Wi-Fi ("only on Wi-Fi" is on and the phone is on mobile data). */
    static final int WAITING_WIFI = 5;

    static final class Entry {
        final Track track;
        String file;
        long bytes;
        long addedAt;
        int state;
        int percent;
        /** Last percentage the listeners were told. */
        int toldPercent;
        /** Set on the main thread when removed; read by the download thread. */
        volatile boolean cancelled;

        Entry(Track track) {
            this.track = track;
        }

        JSONObject toJson() throws Exception {
            return new JSONObject().put("track", track.toJson()).put("file", file == null ? "" : file)
                    .put("bytes", bytes).put("at", addedAt).put("state", state == DONE ? DONE : QUEUED);
        }
    }

    private static Downloads sInstance;

    static synchronized Downloads get(Context c) {
        if (sInstance == null) sInstance = new Downloads(c.getApplicationContext());
        return sInstance;
    }

    private final Context app;
    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    /** Newest first. */
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Entry running;
    private boolean watchingNetwork;

    private Downloads(Context app) {
        this.app = app;
        this.prefs = app.getSharedPreferences("downloads", Context.MODE_PRIVATE);
        restore();
    }

    File dir() {
        File base = app.getExternalFilesDir("downloads");
        if (base == null) base = new File(app.getFilesDir(), "downloads");
        if (!base.isDirectory()) base.mkdirs();
        return base;
    }

    // ---------------------------------------------------------------- queries

    /** Complete songs from free sources and podcast episodes; not previews, radios or local files. */
    static boolean canDownload(Track t) {
        return t != null && (t.kind == Track.FULL || t.kind == Track.EPISODE);
    }

    int state(Track t) {
        Entry e = t == null ? null : entries.get(t.key);
        return e == null ? NONE : e.state;
    }

    int percent(Track t) {
        Entry e = t == null ? null : entries.get(t.key);
        return e == null ? 0 : e.percent;
    }

    /** The file of a finished download, or null. */
    File file(Track t) {
        Entry e = t == null ? null : entries.get(t.key);
        if (e == null || e.state != DONE || e.file == null) return null;
        File f = new File(dir(), e.file);
        return f.isFile() ? f : null;
    }

    /** Finished downloads, newest first. */
    List<Track> tracks() {
        List<Track> out = new ArrayList<>();
        for (Entry e : entries.values()) if (e.state == DONE) out.add(e.track);
        return out;
    }

    /** Downloads still to finish (queued, running, waiting for Wi-Fi or failed). */
    List<Track> pending() {
        List<Track> out = new ArrayList<>();
        for (Entry e : entries.values()) if (e.state != DONE) out.add(e.track);
        return out;
    }

    /** The download in progress, or null. */
    Track running() {
        return running == null ? null : running.track;
    }

    /** Queued or running (not waiting for Wi-Fi, not failed). */
    int active() {
        int n = 0;
        for (Entry e : entries.values()) if (e.state == QUEUED || e.state == RUNNING) n++;
        return n;
    }

    /** Stops everything not finished yet (the notification's "Cancelar"). */
    void cancelPending() {
        for (Entry e : new ArrayList<>(entries.values())) if (e.state != DONE) remove(e.track);
    }

    long bytesUsed() {
        long n = 0;
        for (Entry e : entries.values()) if (e.state == DONE) n += e.bytes;
        return n;
    }

    boolean wifiOnly() {
        return prefs.getBoolean("wifiOnly", true);
    }

    void setWifiOnly(boolean on) {
        prefs.edit().putBoolean("wifiOnly", on).apply();
        resume();
        notifyChanged();
    }

    // ---------------------------------------------------------------- commands

    void download(Track t) {
        if (!canDownload(t)) return;
        Entry e = entries.get(t.key);
        if (e != null && e.state != FAILED) return;
        if (e == null) {
            e = new Entry(t);
            e.addedAt = System.currentTimeMillis();
            // newest first
            LinkedHashMap<String, Entry> copy = new LinkedHashMap<>(entries);
            entries.clear();
            entries.put(t.key, e);
            entries.putAll(copy);
        }
        e.state = QUEUED;
        e.percent = 0;
        save();
        notifyChanged();
        resume();
    }

    /** Queues every downloadable track of a list; returns how many were added. */
    int downloadAll(List<Track> tracks) {
        int n = 0;
        for (Track t : tracks) {
            if (canDownload(t) && (state(t) == NONE || state(t) == FAILED)) {
                download(t);
                n++;
            }
        }
        return n;
    }

    /** Deletes the file (or cancels the download). */
    void remove(Track t) {
        Entry e = entries.remove(t.key);
        if (e == null) return;
        e.cancelled = true;
        if (e.file != null) {
            new File(dir(), e.file).delete();
            new File(dir(), e.file + ".part").delete();
        }
        save();
        notifyChanged();
    }

    void removeAll() {
        for (Entry e : new ArrayList<>(entries.values())) remove(e.track);
    }

    /** Starts the next queued download if nothing is running. */
    void resume() {
        if (running != null) return;
        Entry next = null;
        boolean wifiWait = wifiOnly() && metered.now(app);
        // oldest queued first
        List<Entry> all = new ArrayList<>(entries.values());
        for (int i = all.size() - 1; i >= 0; i--) {
            Entry e = all.get(i);
            if (e.state == QUEUED || e.state == WAITING_WIFI) {
                if (wifiWait) {
                    e.state = WAITING_WIFI;
                } else if (next == null) {
                    next = e;
                }
            }
        }
        if (wifiWait) {
            watchNetwork();
            notifyChanged();
            return;
        }
        if (next == null) return;
        start(next);
    }

    private void start(final Entry e) {
        running = e;
        e.state = RUNNING;
        e.percent = 0;
        e.toldPercent = 0;
        DownloadService.ensureStarted(app); // keeps going when you leave the app
        if (e.file == null) e.file = fileName(e.track);
        final File out = new File(dir(), e.file);
        final String url = e.track.kind == Track.EPISODE && e.track.resolvedUrl != null
                ? e.track.resolvedUrl : e.track.streamUrl;
        notifyChanged();
        worker.execute(new Runnable() {
            @Override
            public void run() {
                long size = -1;
                try {
                    size = Net.saver.save(url, out, new Net.Progress() {
                        @Override
                        public boolean onProgress(final int pct) {
                            main.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (running == e && pct != e.percent) {
                                        int told = e.toldPercent;
                                        e.percent = pct;
                                        if (pct - told >= PROGRESS_STEP || pct == 100) {
                                            e.toldPercent = pct;
                                            for (Listener l : listeners) l.onDownloadProgress(e.track, pct);
                                        }
                                    }
                                }
                            });
                            return !e.cancelled; // removed meanwhile: stop
                        }
                    });
                } catch (Exception ignored) {
                }
                final long result = size;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        running = null;
                        if (entries.get(e.track.key) != e) {
                            out.delete(); // cancelled while downloading
                        } else if (result >= 0 && out.isFile()) {
                            e.state = DONE;
                            e.percent = 100;
                            e.bytes = result;
                        } else {
                            e.state = FAILED;
                        }
                        save();
                        notifyChanged();
                        resume();
                    }
                });
            }
        });
    }

    /** "<hash>-<title>.<ext>": readable, unique per track key. */
    static String fileName(Track t) {
        String url = t.streamUrl == null ? "" : t.streamUrl;
        String path = url.contains("?") ? url.substring(0, url.indexOf('?')) : url;
        String ext = path.matches(".*\\.(mp3|m4a|aac|ogg|opus|flac|wav)$") ? path.substring(path.lastIndexOf('.')) : ".mp3";
        String slug = Library.fold(t.title).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (slug.length() > 40) slug = slug.substring(0, 40);
        return Integer.toHexString(t.key.hashCode()) + "-" + (slug.isEmpty() ? "audio" : slug) + ext;
    }

    private void watchNetwork() {
        if (watchingNetwork) return;
        ConnectivityManager cm = (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return;
        watchingNetwork = true;
        try {
            cm.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
                @Override
                public void onCapabilitiesChanged(Network network, android.net.NetworkCapabilities caps) {
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            resume();
                        }
                    });
                }
            });
        } catch (Exception ignored) {
            watchingNetwork = false;
        }
    }

    // ---------------------------------------------------------------- persistence

    private void restore() {
        try {
            JSONArray a = new JSONArray(prefs.getString("entries", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Entry e = new Entry(Track.fromJson(o.getJSONObject("track")));
                e.file = o.optString("file", "");
                if (e.file.isEmpty()) e.file = null;
                e.bytes = o.optLong("bytes");
                e.addedAt = o.optLong("at");
                e.state = o.optInt("state", QUEUED);
                // a finished file the system or the user deleted is no longer a download
                if (e.state == DONE && (e.file == null || !new File(dir(), e.file).isFile())) continue;
                if (e.state == DONE) e.percent = 100;
                entries.put(e.track.key, e);
            }
        } catch (Exception ignored) {
        }
    }

    private void save() {
        try {
            JSONArray a = new JSONArray();
            for (Entry e : entries.values()) a.put(e.toJson());
            prefs.edit().putString("entries", a.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    // ---------------------------------------------------------------- listeners

    void addListener(Listener l) {
        listeners.add(l);
    }

    void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void notifyChanged() {
        for (Listener l : listeners) l.onDownloadsChanged();
    }

    /** Tests: forget the singleton. */
    static void resetForTest() {
        sInstance = null;
        metered = SYSTEM;
    }

    Map<String, Entry> entriesForTest() {
        return entries;
    }
}

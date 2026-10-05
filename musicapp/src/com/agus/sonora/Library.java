package com.agus.sonora;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Everything the user can play: songs on the device, real songs and radio stations from the
 * internet (Deezer previews, radio-browser.info), liked songs and user playlists. Likes and playlists are persisted in SharedPreferences as JSON.
 */
public final class Library {

    public interface Listener {
        void onLibraryChanged();
    }

    private static Library sInstance;

    public static synchronized Library get(Context context) {
        if (sInstance == null) sInstance = new Library(context.getApplicationContext());
        return sInstance;
    }

    private final Context app;
    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    private List<Track> local = new ArrayList<>();
    private final LinkedHashMap<String, Section> sections = new LinkedHashMap<>();
    private final Map<String, List<Track>> artistTop = new HashMap<>();
    private final Map<String, Track> byKey = new HashMap<>();
    private final List<String> liked = new ArrayList<>();
    private final LinkedHashMap<String, List<String>> playlists = new LinkedHashMap<>();
    private boolean loading;
    private boolean loadedOnce;

    private Library(Context app) {
        this.app = app;
        this.prefs = app.getSharedPreferences("library", Context.MODE_PRIVATE);
        buildOnlineCatalog();
        restore();
    }

    // ---------------------------------------------------------------- catalog

    // ---------------------------------------------------------------- online catalog

    /** A shelf of online content shown on the home screen (a chart, a genre, radios…). */
    public static final class Section {
        public final String id;
        public final String title;
        public final String subtitle;
        final String path;
        final boolean radio;
        public List<Track> tracks = new ArrayList<>();
        public int state = IDLE;

        Section(String id, String title, String subtitle, String path, boolean radio) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.path = path;
            this.radio = radio;
        }
    }

    public static final int IDLE = 0;
    public static final int LOADING = 1;
    public static final int READY = 2;
    public static final int FAILED = 3;

    static final String DEEZER = "https://api.deezer.com";
    static final String[] RADIO_SERVERS = {
            "https://de1.api.radio-browser.info", "https://de2.api.radio-browser.info",
            "https://fi1.api.radio-browser.info", "https://nl1.api.radio-browser.info",
            "https://at1.api.radio-browser.info", "https://all.api.radio-browser.info",
    };
    static final String RADIO_QUERY = "&hidebroken=true&order=clickcount&reverse=true&limit=40";

    private void buildOnlineCatalog() {
        String country = Locale.getDefault().getCountry();
        String radioPath = country.length() == 2
                ? "/json/stations/search?countrycode=" + country + RADIO_QUERY
                : "/json/stations/search?language=spanish" + RADIO_QUERY;
        addSection(new Section("top", "Top 50 mundial", "Lo más escuchado ahora en Deezer",
                "/chart/0/tracks?limit=50", false));
        addSection(new Section("radio", "Radios en vivo", "Emisoras reales, canciones completas",
                radioPath, true));
        addSection(new Section("reggaeton", "Reggaetón", "Los éxitos del género",
                "/search?q=reggaeton&order=RANKING&limit=40", false));
        addSection(new Section("pop", "Pop latino", "Para cantar a todo pulmón",
                "/search?q=" + Net.enc("pop latino") + "&order=RANKING&limit=40", false));
        addSection(new Section("rock", "Rock en español", "Clásicos y nuevos",
                "/search?q=" + Net.enc("rock en español") + "&order=RANKING&limit=40", false));
        addSection(new Section("cumbia", "Cumbia", "Para mover el esqueleto",
                "/search?q=cumbia&order=RANKING&limit=40", false));
        addSection(new Section("trap", "Trap", "Lo que suena en la calle",
                "/search?q=trap&order=RANKING&limit=40", false));
    }

    private void addSection(Section s) {
        sections.put(s.id, s);
    }

    public List<Section> sections() {
        return new ArrayList<>(sections.values());
    }

    public Section section(String id) {
        return sections.get(id);
    }

    /** Loads every section that isn't loaded yet (or failed). */
    public void loadOnline() {
        for (Section s : sections.values()) {
            if (s.state != READY && s.state != LOADING) loadSection(s);
        }
    }

    public void loadSection(final Section s) {
        s.state = LOADING;
        notifyChanged();
        Net.POOL.execute(new Runnable() {
            @Override
            public void run() {
                List<Track> found = null;
                try {
                    if (s.radio) {
                        found = parseRadios(radioGet(s.path));
                        if (found.size() < 5 && !s.path.contains("language=")) {
                            // Few stations for this country: fill with popular Spanish-language ones.
                            for (Track t : parseRadios(radioGet("/json/stations/search?language=spanish" + RADIO_QUERY))) {
                                if (!found.contains(t)) found.add(t);
                            }
                        }
                    } else {
                        found = parseDeezer(Net.getString(DEEZER + s.path));
                    }
                } catch (Exception ignored) {
                }
                final List<Track> result = found;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (result == null || result.isEmpty()) {
                            s.state = FAILED;
                        } else {
                            register(result);
                            s.tracks = result;
                            s.state = READY;
                        }
                        notifyChanged();
                    }
                });
            }
        });
    }

    public interface Results {
        void onResults(List<Track> tracks, boolean failed);
    }

    /** Searches real songs on Deezer and stations on radio-browser; results on the main thread. */
    public void searchOnline(final String query, final Results cb) {
        Net.POOL.execute(new Runnable() {
            @Override
            public void run() {
                final List<Track> out = new ArrayList<>();
                boolean ok = false;
                try {
                    out.addAll(parseDeezer(Net.getString(DEEZER + "/search?limit=40&q=" + Net.enc(query))));
                    ok = true;
                } catch (Exception ignored) {
                }
                try {
                    List<Track> radios = parseRadios(radioGet("/json/stations/search?name="
                            + Net.enc(query) + "&hidebroken=true&order=clickcount&reverse=true&limit=8"));
                    out.addAll(radios);
                    ok = true;
                } catch (Exception ignored) {
                }
                final boolean failed = !ok;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        register(out);
                        cb.onResults(out, failed);
                    }
                });
            }
        });
    }

    /** Most popular songs of an artist on Deezer (or null while/if unavailable). */
    public List<Track> artistTop(String artist) {
        return artistTop.get(artist);
    }

    public void loadArtistTop(final String artist, final long artistId) {
        if (artistId <= 0 || artistTop.containsKey(artist)) return;
        artistTop.put(artist, null);
        Net.getAsync(DEEZER + "/artist/" + artistId + "/top?limit=50", new Net.Callback() {
            @Override
            public void onResult(String body, Exception error) {
                List<Track> l = null;
                try {
                    if (body != null) l = parseDeezer(body);
                } catch (Exception ignored) {
                }
                if (l == null || l.isEmpty()) {
                    artistTop.remove(artist);
                    return;
                }
                register(l);
                artistTop.put(artist, l);
                notifyChanged();
            }
        });
    }

    /**
     * Makes sure an online track has a playable link. Deezer preview links expire after a
     * while, so they are fetched again right before playing. Called off the main thread.
     */
    static void ensureStream(Track t) throws Exception {
        if (t.kind != Track.PREVIEW) return;
        boolean fresh = t.streamUrl != null
                && System.currentTimeMillis() - t.streamFetchedAt < 60 * 1000L;
        if (fresh || t.remoteId <= 0) return;
        JSONObject o = new JSONObject(Net.getString(DEEZER + "/track/" + t.remoteId));
        String preview = o.optString("preview", "");
        if (preview.isEmpty()) throw new Exception("Sin vista previa disponible");
        t.streamUrl = preview;
        t.streamFetchedAt = System.currentTimeMillis();
    }

    static List<Track> parseDeezer(String body) throws Exception {
        JSONObject root = new JSONObject(body);
        if (root.has("error")) throw new Exception(root.optJSONObject("error").optString("message"));
        JSONArray data = root.optJSONArray("data");
        List<Track> out = new ArrayList<>();
        if (data == null) return out;
        for (int i = 0; i < data.length(); i++) {
            JSONObject o = data.optJSONObject(i);
            if (o == null || o.optString("preview", "").isEmpty()) continue;
            if (!"track".equals(o.optString("type", "track"))) continue;
            Track t = Track.fromDeezer(o);
            if (!out.contains(t)) out.add(t);
        }
        return out;
    }

    static List<Track> parseRadios(String body) throws Exception {
        JSONArray a = new JSONArray(body);
        List<Track> out = new ArrayList<>();
        java.util.Set<String> names = new java.util.HashSet<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            String url = o.optString("url_resolved", "");
            if (url.isEmpty()) url = o.optString("url", "");
            String name = o.optString("name", "").trim();
            if (url.isEmpty() || name.isEmpty() || !names.add(fold(name))) continue;
            out.add(Track.fromRadio(o));
        }
        return out;
    }

    /** radio-browser has several mirrors; use the first that answers. */
    static String radioGet(String path) throws Exception {
        Exception last = null;
        for (String server : RADIO_SERVERS) {
            try {
                return Net.getString(server + path);
            } catch (Exception e) {
                last = e;
            }
        }
        throw last;
    }

    /** Makes online tracks findable by key (for likes/playlists), keeping already-known objects. */
    private void register(List<Track> tracks) {
        for (int i = 0; i < tracks.size(); i++) {
            Track t = tracks.get(i);
            Track known = byKey.get(t.key);
            if (known != null && known.remote) {
                if (t.streamUrl != null) {
                    known.streamUrl = t.streamUrl;
                    known.streamFetchedAt = t.streamFetchedAt;
                }
                if (known.durationMs <= 0) known.durationMs = t.durationMs;
                tracks.set(i, known);
            } else if (known == null) {
                byKey.put(t.key, t);
            }
        }
    }

    public static boolean hasAudioPermission(Context c) {
        if (Build.VERSION.SDK_INT < 23) return true;
        String p = Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        return c.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED;
    }

    /** Re-scans the device's music in the background, then notifies listeners. */
    public void reloadLocal() {
        if (loading || !hasAudioPermission(app)) {
            notifyChanged();
            return;
        }
        loading = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<Track> found = queryDevice();
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        for (Track t : local) byKey.remove(t.key);
                        local = found;
                        for (Track t : local) byKey.put(t.key, t);
                        loading = false;
                        loadedOnce = true;
                        notifyChanged();
                    }
                });
            }
        }, "library-scan").start();
    }

    private List<Track> queryDevice() {
        List<Track> out = new ArrayList<>();
        Uri base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] cols = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATE_ADDED,
        };
        Cursor c = null;
        try {
            c = app.getContentResolver().query(base, cols,
                    MediaStore.Audio.Media.IS_MUSIC + " != 0", null,
                    MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC");
            if (c == null) return out;
            while (c.moveToNext()) {
                long id = c.getLong(0);
                long dur = c.getLong(5);
                if (dur > 0 && dur < 5000) continue; // skip notification blips
                Uri uri = ContentUris.withAppendedId(base, id);
                out.add(new Track(uri.toString(), c.getString(1), c.getString(2),
                        c.getString(3), c.getLong(4), dur, false, c.getLong(6)));
            }
        } catch (Exception ignored) {
            // A broken MediaStore should never crash the app; show what we have.
        } finally {
            if (c != null) c.close();
        }
        return out;
    }

    public boolean isLoading() {
        return loading;
    }

    public boolean loadedOnce() {
        return loadedOnce;
    }

    public List<Track> localTracks() {
        return Collections.unmodifiableList(local);
    }

    /** The user's collection: device songs plus online songs they liked or saved to a playlist. */
    public List<Track> allTracks() {
        List<Track> all = new ArrayList<>(local);
        for (Track t : savedRemote()) if (!all.contains(t)) all.add(t);
        return all;
    }

    private List<Track> savedRemote() {
        java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<>(liked);
        for (List<String> l : playlists.values()) keys.addAll(l);
        List<Track> out = new ArrayList<>();
        for (String k : keys) {
            Track t = byKey.get(k);
            if (t != null && t.remote) out.add(t);
        }
        return out;
    }

    public List<Track> recentlyAdded(int max) {
        List<Track> l = new ArrayList<>(local);
        Collections.sort(l, new Comparator<Track>() {
            @Override
            public int compare(Track a, Track b) {
                return Long.compare(b.dateAdded, a.dateAdded);
            }
        });
        return l.size() > max ? new ArrayList<>(l.subList(0, max)) : l;
    }

    public Track find(String key) {
        return byKey.get(key);
    }

    // ---------------------------------------------------------------- search & grouping

    static String fold(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }

    public List<Track> search(String query) {
        String q = fold(query.trim());
        List<Track> out = new ArrayList<>();
        if (q.isEmpty()) return out;
        for (Track t : allTracks()) {
            if (fold(t.title).contains(q) || fold(t.artist).contains(q) || fold(t.album).contains(q)) {
                out.add(t);
            }
        }
        return out;
    }

    /** Groups all tracks by artist (or album when byAlbum), keys sorted alphabetically. */
    public LinkedHashMap<String, List<Track>> group(boolean byAlbum) {
        Map<String, List<Track>> m = new HashMap<>();
        for (Track t : allTracks()) {
            String k = byAlbum ? t.album : t.artist;
            List<Track> l = m.get(k);
            if (l == null) m.put(k, l = new ArrayList<>());
            l.add(t);
        }
        List<String> keys = new ArrayList<>(m.keySet());
        Collections.sort(keys, String.CASE_INSENSITIVE_ORDER);
        LinkedHashMap<String, List<Track>> out = new LinkedHashMap<>();
        for (String k : keys) out.put(k, m.get(k));
        return out;
    }

    // ---------------------------------------------------------------- likes

    public boolean isLiked(Track t) {
        return t != null && liked.contains(t.key);
    }

    public void toggleLike(Track t) {
        if (t == null) return;
        if (!liked.remove(t.key)) liked.add(0, t.key);
        save();
        notifyChanged();
    }

    public List<Track> likedTracks() {
        return resolve(liked);
    }

    // ---------------------------------------------------------------- playlists

    public List<String> playlistNames() {
        return new ArrayList<>(playlists.keySet());
    }

    public List<Track> playlist(String name) {
        List<String> keys = playlists.get(name);
        return keys == null ? new ArrayList<Track>() : resolve(keys);
    }

    /** @return false if a playlist with that name already exists. */
    public boolean createPlaylist(String name) {
        if (name == null) return false;
        name = name.trim();
        if (name.isEmpty() || name.equals("__order") || playlists.containsKey(name)) return false;
        playlists.put(name, new ArrayList<String>());
        save();
        notifyChanged();
        return true;
    }

    public void deletePlaylist(String name) {
        playlists.remove(name);
        save();
        notifyChanged();
    }

    /** @return false if the track was already in it. */
    public boolean addToPlaylist(String name, Track t) {
        List<String> keys = playlists.get(name);
        if (keys == null || keys.contains(t.key)) return false;
        keys.add(t.key);
        save();
        notifyChanged();
        return true;
    }

    public void removeFromPlaylist(String name, Track t) {
        List<String> keys = playlists.get(name);
        if (keys != null && keys.remove(t.key)) {
            save();
            notifyChanged();
        }
    }

    private List<Track> resolve(List<String> keys) {
        List<Track> out = new ArrayList<>();
        for (String k : keys) {
            Track t = byKey.get(k);
            if (t != null) out.add(t);
        }
        return out;
    }

    // ---------------------------------------------------------------- persistence

    private void restore() {
        try {
            JSONArray l = new JSONArray(prefs.getString("liked", "[]"));
            for (int i = 0; i < l.length(); i++) liked.add(l.getString(i));
            JSONObject p = new JSONObject(prefs.getString("playlists", "{}"));
            JSONArray order = p.optJSONArray("__order");
            List<String> names = new ArrayList<>();
            if (order != null) {
                for (int i = 0; i < order.length(); i++) names.add(order.getString(i));
            } else {
                Iterator<String> it = p.keys();
                while (it.hasNext()) names.add(it.next());
            }
            for (String name : names) {
                JSONArray a = p.optJSONArray(name);
                if (a == null || "__order".equals(name)) continue;
                List<String> keys = new ArrayList<>();
                for (int i = 0; i < a.length(); i++) keys.add(a.getString(i));
                playlists.put(name, keys);
            }
            JSONObject remote = new JSONObject(prefs.getString("remote", "{}"));
            Iterator<String> it = remote.keys();
            while (it.hasNext()) {
                JSONObject o = remote.optJSONObject(it.next());
                if (o == null) continue;
                Track t = Track.fromJson(o);
                byKey.put(t.key, t);
            }
        } catch (Exception ignored) {
            // Corrupt prefs: start empty rather than crash.
        }
    }

    private void save() {
        try {
            JSONObject p = new JSONObject();
            JSONArray order = new JSONArray();
            for (Map.Entry<String, List<String>> e : playlists.entrySet()) {
                p.put(e.getKey(), new JSONArray(e.getValue()));
                order.put(e.getKey());
            }
            p.put("__order", order);
            JSONObject remote = new JSONObject();
            for (Track t : savedRemote()) remote.put(t.key, t.toJson());
            prefs.edit()
                    .putString("remote", remote.toString())
                    .putString("liked", new JSONArray(liked).toString())
                    .putString("playlists", p.toString())
                    .apply();
        } catch (Exception ignored) {
        }
    }

    // ---------------------------------------------------------------- listeners

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void notifyChanged() {
        for (Listener l : listeners) l.onLibraryChanged();
    }
}

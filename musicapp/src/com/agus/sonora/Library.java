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

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.StringReader;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
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
    private final List<String> history = new ArrayList<>();
    private final List<String> searches = new ArrayList<>();
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

    /** A container of tracks: an album, an artist, a playlist, a genre chart or a podcast. */
    public static final class Item {
        public static final int ALBUM = 0;
        public static final int ARTIST = 1;
        public static final int PLAYLIST = 2;
        public static final int GENRE = 3;
        public static final int PODCAST = 4;
        /** An Internet Archive item (usually a free album); {@link #feed} holds its identifier. */
        public static final int ARCHIVE = 5;

        public final int kind;
        public final long id;
        public final String title;
        public final String subtitle;
        public final String art;
        /**
         * Podcasts: the RSS feed; known after a lookup when the listing doesn't carry it.
         * Internet Archive items: the item identifier.
         */
        public volatile String feed;

        public Item(int kind, long id, String title, String subtitle, String art, String feed) {
            this.kind = kind;
            this.id = id;
            this.title = title == null || title.isEmpty() ? "Sin título" : title;
            this.subtitle = subtitle == null ? "" : subtitle;
            this.art = art == null || art.isEmpty() || "null".equals(art) ? null : art;
            this.feed = feed == null || feed.isEmpty() || "null".equals(feed) ? null : feed;
        }

        public String key() {
            return kind + ":" + id;
        }

        JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("kind", kind).put("id", id).put("title", title).put("sub", subtitle)
                        .put("art", art == null ? "" : art).put("feed", feed == null ? "" : feed);
            } catch (Exception ignored) {
            }
            return o;
        }

        static Item fromJson(JSONObject o) {
            return new Item(o.optInt("kind"), o.optLong("id"), o.optString("title"), o.optString("sub"),
                    o.optString("art"), o.optString("feed"));
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Item && ((Item) o).key().equals(key());
        }

        @Override
        public int hashCode() {
            return key().hashCode();
        }
    }

    /** A shelf of online content: a chart, a genre, radios, or a list of albums / artists / podcasts. */
    public static final class Section {
        public final String id;
        public final String title;
        public final String subtitle;
        final String path;
        final boolean radio;
        /** -1 for a list of tracks, otherwise the kind of {@link Item}s the section holds. */
        public final int itemKind;
        /** Artist shelves built by name: each name is looked up in the catalog (exact matches only). */
        final String[] names;
        /** Featured shelves appear on Home and load at startup; the rest load when opened. */
        public boolean featured;
        public List<Track> tracks = new ArrayList<>();
        public List<Item> items = new ArrayList<>();
        public int state = IDLE;
        /** Where the shelf comes from: {@link #SRC_DEEZER} (previews), Audius or the Internet Archive. */
        public int source = SRC_DEEZER;

        Section(String id, String title, String subtitle, String path, boolean radio) {
            this(id, title, subtitle, path, radio, -1);
        }

        Section(String id, String title, String subtitle, String[] artistNames) {
            this(id, title, subtitle, null, false, Item.ARTIST, artistNames);
        }

        Section(String id, String title, String subtitle, String path, boolean radio, int itemKind) {
            this(id, title, subtitle, path, radio, itemKind, null);
        }

        Section(String id, String title, String subtitle, String path, boolean radio, int itemKind,
                String[] artistNames) {
            this.names = artistNames;
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.path = path;
            this.radio = radio;
            this.itemKind = itemKind;
        }

        Section featured() {
            featured = true;
            return this;
        }

        Section from(int src) {
            source = src;
            return this;
        }

        /** Complete songs, radios and podcasts; false for shelves of 30-second Deezer previews. */
        public boolean complete() {
            return source != SRC_DEEZER || radio || itemKind == Item.PODCAST;
        }

        public boolean hasItems() {
            return itemKind >= 0;
        }
    }

    public static final int IDLE = 0;
    public static final int LOADING = 1;
    public static final int READY = 2;
    public static final int FAILED = 3;

    static final String DEEZER = "https://api.deezer.com";
    /** Audius: artists upload their whole songs; free to stream, no account needed. */
    static final String AUDIUS = "https://api.audius.co/v1";
    static final String AUDIUS_APP = "app_name=Sonora";
    static final String ARCHIVE = "https://archive.org";

    public static final int SRC_DEEZER = 0;
    public static final int SRC_AUDIUS = 1;
    public static final int SRC_ARCHIVE = 2;
    /** The discovery mix, built from your likes and history. */
    public static final int SRC_MIX = 3;

    private static String audius(String path) {
        return path + (path.contains("?") ? "&" : "?") + AUDIUS_APP;
    }

    /** Most downloaded audio items of an Internet Archive collection. */
    private static String archive(String collection) {
        return "/advancedsearch.php?q=" + Net.enc("collection:(" + collection + ") AND mediatype:audio")
                + "&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=creator&sort%5B%5D=downloads+desc&rows=30&output=json";
    }
    static final String[] RADIO_SERVERS = {
            "https://de1.api.radio-browser.info", "https://de2.api.radio-browser.info",
            "https://fi1.api.radio-browser.info", "https://nl1.api.radio-browser.info",
            "https://at1.api.radio-browser.info", "https://all.api.radio-browser.info",
    };
    static final String RADIO_QUERY = "&hidebroken=true&order=clickcount&reverse=true&limit=40";

    static final String APPLE_TOP = "https://rss.applemarketingtools.com/api/v2/{cc}/podcasts/top/30/podcasts.json";
    static final String APPLE_SEARCH = "https://itunes.apple.com/search?media=podcast&entity=podcast&limit=25";
    static final String APPLE_LOOKUP = "https://itunes.apple.com/lookup?id=";

    /** Internet-culture, fandom and game-inspired artists; the ones the catalog doesn't carry are skipped. */
    static final String[] FANDOM_ARTISTS = {
            "The Living Tombstone", "Black Gryph0n", "Baasik", "CG5", "NateWantsToBattle", "DAGames",
            "TryHardNinja", "Rockit Gaming", "Miracle Of Sound", "Random Encounters", "Dan Bull",
            "Griffinilla", "Jonathan Young", "Jack Stauber"
    };

    private static String q(String text) {
        return "/search?q=" + Net.enc(text) + "&order=RANKING&limit=40";
    }

    private void buildOnlineCatalog() {
        String country = Locale.getDefault().getCountry();
        String radioPath = country.length() == 2
                ? "/json/stations/search?countrycode=" + country + RADIO_QUERY
                : "/json/stations/search?language=spanish" + RADIO_QUERY;
        addSection(new Section(MIX, "Mezcla de descubrimiento",
                "Artistas nuevos para ti, según lo que te gusta · cambia cada día", null, false).from(SRC_MIX).featured());
        // complete songs, free and without an account
        addSection(new Section("full-trending", "Canciones completas", "Tendencias en Audius, gratis y enteras",
                audius("/tracks/trending?limit=40"), false).from(SRC_AUDIUS).featured());
        addSection(new Section("top", "Top 50 mundial", "Lo más escuchado ahora en Deezer",
                "/chart/0/tracks?limit=50", false).featured());
        addSection(new Section("radio", "Radios en vivo", "Emisoras reales, canciones completas",
                radioPath, true).featured());
        addSection(new Section("archive", "Álbumes libres", "Del Internet Archive: discos completos con licencia libre",
                archive("netlabels"), false, Item.ARCHIVE).from(SRC_ARCHIVE).featured());
        addSection(new Section("releases", "Nuevos lanzamientos", "Álbumes recién salidos",
                "/editorial/0/releases?limit=25", false, Item.ALBUM).featured());
        addSection(new Section("artists", "Artistas populares", "Los más escuchados",
                "/chart/0/artists?limit=25", false, Item.ARTIST).featured());
        addSection(new Section("playlists", "Playlists populares", "Listas que están sonando",
                "/chart/0/playlists?limit=25", false, Item.PLAYLIST).featured());
        addSection(new Section("reggaeton", "Reggaetón", "Los éxitos del género", q("reggaeton"), false).featured());
        addSection(new Section("pop", "Pop latino", "Para cantar a todo pulmón", q("pop latino"), false).featured());
        addSection(new Section("rock", "Rock en español", "Clásicos y nuevos", q("rock en español"), false).featured());
        addSection(new Section("fandom-artists", "Fandoms y gaming",
                "The Living Tombstone, CG5, Black Gryph0n y más", FANDOM_ARTISTS).featured());
        addSection(new Section("tls", "The Living Tombstone", "Sus temas más escuchados",
                "/search?q=" + Net.enc("artist:\"The Living Tombstone\"") + "&order=RANKING&limit=40", false).featured());
        addSection(new Section("genres", "Géneros", "Explora el top de cada estilo",
                "/genre", false, Item.GENRE));
        addSection(new Section("podcasts", "Podcasts populares", "Episodios completos",
                APPLE_TOP, false, Item.PODCAST));
        addSection(new Section("cumbia", "Cumbia", "Para mover el esqueleto", q("cumbia"), false));
        addSection(new Section("trap", "Trap", "Lo que suena en la calle", q("trap"), false));
        addSection(new Section("hiphop", "Hip hop", "Rimas y ritmo", q("hip hop"), false));
        addSection(new Section("electronic", "Electrónica", "Para bailar sin parar", q("electronic dance"), false));
        addSection(new Section("jazz", "Jazz", "Suave y elegante", q("jazz"), false));
        addSection(new Section("classical", "Clásica", "Los grandes compositores", q("classical piano"), false));
        addSection(new Section("indie", "Indie", "Sonidos alternativos", q("indie"), false));
        addSection(new Section("salsa", "Salsa", "Ritmo caribeño", q("salsa"), false));
        addSection(new Section("bachata", "Bachata", "Para bailar de a dos", q("bachata"), false));
        addSection(new Section("ballads", "Baladas", "Para suspirar", q("baladas románticas"), false));
        addSection(new Section("workout", "Para entrenar", "Energía para el gimnasio", q("workout"), false));
        addSection(new Section("chill", "Para relajarse", "Calma y buena onda", q("chill"), false));
        addSection(new Section("party", "Fiesta", "Que no pare la música", q("party hits"), false));
        addSection(new Section("lofi", "Lo-fi", "Para estudiar o trabajar", q("lofi"), false));
        String lang = "&language=spanish";
        addSection(new Section("radio-news", "Radios de noticias", "Actualidad en vivo",
                "/json/stations/search?tag=news" + lang + RADIO_QUERY, true));
        addSection(new Section("radio-sports", "Radios de deportes", "Partidos y análisis",
                "/json/stations/search?tag=sports" + lang + RADIO_QUERY, true));
        addSection(new Section("radio-rock", "Radios de rock", "Rock las 24 horas",
                "/json/stations/search?tag=rock" + lang + RADIO_QUERY, true));
        addSection(new Section("radio-pop", "Radios de pop", "Éxitos del momento",
                "/json/stations/search?tag=pop" + lang + RADIO_QUERY, true));
        addSection(new Section("radio-latin", "Radios latinas", "Salsa, cumbia y más",
                "/json/stations/search?tag=latin" + lang + RADIO_QUERY, true));
        addSection(new Section("radio-classical", "Radios de clásica", "Música para concentrarse",
                "/json/stations/search?tag=classical" + RADIO_QUERY, true));
        addSection(new Section("radio-jazz", "Radios de jazz", "Jazz en vivo",
                "/json/stations/search?tag=jazz" + RADIO_QUERY, true));
        addSection(new Section("radio-electronic", "Radios electrónicas", "Beats sin pausa",
                "/json/stations/search?tag=electronic" + RADIO_QUERY, true));
        String[][] fullGenres = {
                {"full-electronic", "Electrónica completa", "Electronic"}, {"full-hiphop", "Hip hop completo", "Hip-Hop/Rap"},
                {"full-lofi", "Lo-fi completo", "Lo-Fi"}, {"full-latin", "Latina completa", "Latin"},
                {"full-pop", "Pop completo", "Pop"}, {"full-rock", "Rock completo", "Rock"},
                {"full-ambient", "Ambient completo", "Ambient"},
        };
        for (String[] g : fullGenres) {
            addSection(new Section(g[0], g[1], "Canciones enteras en Audius",
                    audius("/tracks/trending?limit=40&genre=" + Net.enc(g[2])), false).from(SRC_AUDIUS));
        }
        addSection(new Section("archive-live", "Conciertos en vivo", "Grabaciones de conciertos del Internet Archive",
                archive("etree"), false, Item.ARCHIVE).from(SRC_ARCHIVE));
    }

    // ---------------------------------------------------------------- only complete songs

    /** "Solo canciones completas": hides the 30-second previews everywhere. */
    public boolean fullOnly() {
        return prefs.getBoolean("fullOnly", false);
    }

    public void setFullOnly(boolean on) {
        prefs.edit().putBoolean("fullOnly", on).apply();
        notifyChanged();
    }

    /** The shelves to show, respecting {@link #fullOnly()}. */
    public List<Section> visibleSections() {
        List<Section> out = new ArrayList<>();
        boolean only = fullOnly();
        for (Section s : sections.values()) if (!only || s.complete()) out.add(s);
        return out;
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

    /** Loads the featured shelves that aren't loaded yet (or failed); the others load when opened. */
    public void loadOnline() {
        Section mix = sections.get(MIX);
        if (mix.state == READY && mixDay != today()) mix.state = IDLE; // a new mix every day
        for (Section s : sections.values()) {
            if (s.featured && s.state != READY && s.state != LOADING) loadSection(s);
        }
    }

    public void loadSection(final Section s) {
        if (s.state == LOADING) return;
        s.state = LOADING;
        notifyChanged();
        if (s.hasItems()) {
            loadItemsSection(s);
            return;
        }
        if (s.source == SRC_MIX) {
            loadMix(s);
            return;
        }
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
                    } else if (s.source == SRC_AUDIUS) {
                        found = parseAudius(Net.getString(AUDIUS + s.path));
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

    private void loadItemsSection(final Section s) {
        Net.POOL.execute(new Runnable() {
            @Override
            public void run() {
                List<Item> found = null;
                try {
                    if (s.names != null) {
                        found = artistsByName(s.names);
                    } else if (s.source == SRC_ARCHIVE) {
                        found = parseArchiveItems(Net.getString(ARCHIVE + s.path));
                    } else {
                        String body = s.itemKind == Item.PODCAST ? topPodcastsBody() : Net.getString(DEEZER + s.path);
                        found = parseItems(s.itemKind, body);
                    }
                } catch (Exception ignored) {
                }
                final List<Item> result = found;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (result == null || result.isEmpty()) {
                            s.state = FAILED;
                        } else {
                            s.items = result;
                            s.state = READY;
                        }
                        notifyChanged();
                    }
                });
            }
        });
    }

    /**
     * Looks each artist up in the catalog and keeps only exact name matches (a search for a small
     * artist can return unrelated ones). Fails only when every lookup failed, e.g. no connection.
     */
    static List<Item> artistsByName(String[] names) throws Exception {
        List<Item> out = new ArrayList<>();
        Exception last = null;
        int failures = 0;
        for (String name : names) {
            try {
                Item it = findArtist(name);
                if (it != null && !out.contains(it)) out.add(it);
            } catch (Exception e) {
                last = e;
                failures++;
            }
        }
        if (failures == names.length && last != null) throw last;
        return out;
    }

    static Item findArtist(String name) throws Exception {
        JSONObject root = new JSONObject(Net.getString(DEEZER + "/search/artist?limit=5&q=" + Net.enc(name)));
        JSONArray data = root.optJSONArray("data");
        if (data == null) return null;
        String want = fold(name);
        for (int i = 0; i < data.length(); i++) {
            JSONObject o = data.optJSONObject(i);
            if (o == null || !fold(o.optString("name")).equals(want) || o.optLong("id") <= 0) continue;
            return new Item(Item.ARTIST, o.optLong("id"), o.optString("name"), "Artista",
                    o.optString("picture_big", o.optString("picture_medium")), null);
        }
        return null;
    }

    static String country() {
        String cc = Locale.getDefault().getCountry().toLowerCase(Locale.ROOT);
        return cc.length() == 2 ? cc : "us";
    }

    /** Apple's top podcasts for the device's country, falling back to the US chart. */
    private static String topPodcastsBody() throws Exception {
        String cc = country();
        try {
            return Net.getString(APPLE_TOP.replace("{cc}", cc));
        } catch (Exception e) {
            if (cc.equals("us")) throw e;
            return Net.getString(APPLE_TOP.replace("{cc}", "us"));
        }
    }

    static List<Item> parseItems(int kind, String body) throws Exception {
        JSONObject root = new JSONObject(body);
        if (root.has("error")) throw new Exception(root.optJSONObject("error").optString("message"));
        JSONArray data = root.optJSONArray("data");
        if (data == null) {
            JSONObject feed = root.optJSONObject("feed");
            data = feed != null ? feed.optJSONArray("results") : root.optJSONArray("results");
        }
        List<Item> out = new ArrayList<>();
        if (data == null) return out;
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < data.length(); i++) {
            JSONObject o = data.optJSONObject(i);
            if (o == null) continue;
            Item it;
            switch (kind) {
                case Item.ALBUM: {
                    JSONObject ar = o.optJSONObject("artist");
                    it = new Item(kind, o.optLong("id"), o.optString("title"),
                            ar == null ? "" : ar.optString("name"), o.optString("cover_big", o.optString("cover_medium")), null);
                    break;
                }
                case Item.ARTIST:
                    it = new Item(kind, o.optLong("id"), o.optString("name"), "Artista",
                            o.optString("picture_big", o.optString("picture_medium")), null);
                    break;
                case Item.PLAYLIST: {
                    int n = o.optInt("nb_tracks");
                    it = new Item(kind, o.optLong("id"), o.optString("title"),
                            n > 0 ? n + " canciones" : "Playlist",
                            o.optString("picture_big", o.optString("picture_medium")), null);
                    break;
                }
                case Item.GENRE:
                    if (o.optLong("id") <= 0) continue; // "All"
                    it = new Item(kind, o.optLong("id"), o.optString("name"), "Género",
                            o.optString("picture_big", o.optString("picture_medium")), null);
                    break;
                default: { // podcast: Apple's top chart or search result
                    long id = o.has("collectionId") ? o.optLong("collectionId") : o.optLong("id");
                    String art = o.optString("artworkUrl600", "");
                    if (art.isEmpty()) art = o.optString("artworkUrl100", "").replace("100x100", "600x600");
                    it = new Item(kind, id, o.has("collectionName") ? o.optString("collectionName") : o.optString("name"),
                            o.optString("artistName"), art, o.optString("feedUrl", ""));
                }
            }
            if (it.id <= 0 || !seen.add(it.key())) continue;
            out.add(it);
        }
        return out;
    }

    // ---------------------------------------------------------------- item contents

    private final Map<String, List<Track>> itemTracks = new HashMap<>();
    private final Map<String, Integer> itemStates = new HashMap<>();

    public List<Track> itemTracks(Item it) {
        List<Track> l = itemTracks.get(it.key());
        return l == null ? new ArrayList<Track>() : l;
    }

    public int itemState(Item it) {
        Integer s = itemStates.get(it.key());
        return s == null ? IDLE : s;
    }

    /** Fetches the songs / episodes of an album, playlist, artist, genre or podcast. */
    public void loadItemTracks(final Item it) {
        int now = itemState(it);
        if (now == LOADING || now == READY) return;
        itemStates.put(it.key(), LOADING);
        notifyChanged();
        Net.POOL.execute(new Runnable() {
            @Override
            public void run() {
                List<Track> found = null;
                try {
                    switch (it.kind) {
                        case Item.ALBUM:
                            found = parseAlbumTracks(Net.getString(DEEZER + "/album/" + it.id + "/tracks?limit=100"), it);
                            break;
                        case Item.PLAYLIST:
                            found = parseDeezer(Net.getString(DEEZER + "/playlist/" + it.id + "/tracks?limit=100"));
                            break;
                        case Item.GENRE:
                            found = parseDeezer(Net.getString(DEEZER + "/chart/" + it.id + "/tracks?limit=50"));
                            break;
                        case Item.ARTIST:
                            found = parseDeezer(Net.getString(DEEZER + "/artist/" + it.id + "/top?limit=50"));
                            break;
                        case Item.ARCHIVE:
                            found = parseArchiveTracks(Net.getString(ARCHIVE + "/metadata/" + Net.enc(it.feed)), it);
                            break;
                        default:
                            if (it.feed == null) {
                                JSONArray r = new JSONObject(Net.getString(APPLE_LOOKUP + it.id)).optJSONArray("results");
                                JSONObject first = r == null ? null : r.optJSONObject(0);
                                String f = first == null ? "" : first.optString("feedUrl", "");
                                if (f.isEmpty()) throw new Exception("Sin feed");
                                it.feed = f;
                            }
                            found = parseEpisodes(Net.getString(it.feed), it);
                    }
                } catch (Exception ignored) {
                }
                final List<Track> result = found;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (result == null || result.isEmpty()) {
                            itemStates.put(it.key(), FAILED);
                        } else {
                            register(result);
                            itemTracks.put(it.key(), result);
                            itemStates.put(it.key(), READY);
                        }
                        notifyChanged();
                    }
                });
            }
        });
    }

    /** Forgets a failed load so it can be retried. */
    public void retryItem(Item it) {
        if (itemState(it) == FAILED) {
            itemStates.remove(it.key());
            loadItemTracks(it);
        }
    }

    static List<Track> parseAlbumTracks(String body, Item album) throws Exception {
        JSONObject root = new JSONObject(body);
        if (root.has("error")) throw new Exception(root.optJSONObject("error").optString("message"));
        JSONArray data = root.optJSONArray("data");
        List<Track> out = new ArrayList<>();
        if (data == null) return out;
        for (int i = 0; i < data.length(); i++) {
            JSONObject o = data.optJSONObject(i);
            if (o == null || o.optString("preview", "").isEmpty()) continue;
            Track t = Track.fromDeezerAlbum(o, album.title, album.id, album.art, album.art);
            if (!out.contains(t)) out.add(t);
        }
        return out;
    }

    // ---------------------------------------------------------------- podcasts

    private static final String[] PUB_FORMATS = {
            "EEE, dd MMM yyyy HH:mm:ss Z", "EEE, d MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm:ss z",
            "EEE, d MMM yyyy HH:mm:ss z", "dd MMM yyyy HH:mm:ss Z", "yyyy-MM-dd'T'HH:mm:ssZ", "yyyy-MM-dd"
    };

    static long parsePubDate(String s) {
        if (s == null) return 0;
        s = s.trim();
        for (String f : PUB_FORMATS) {
            try {
                return new SimpleDateFormat(f, Locale.US).parse(s).getTime();
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    /** "3723", "62:03" or "1:02:03" to milliseconds. */
    static long parseDuration(String s) {
        if (s == null || s.trim().isEmpty()) return 0;
        try {
            String[] parts = s.trim().split(":");
            long total = 0;
            for (String p : parts) total = total * 60 + (long) Double.parseDouble(p);
            return total * 1000L;
        } catch (Exception e) {
            return 0;
        }
    }

    /** Reads an RSS feed's audio episodes (newest first, at most 100). */
    static List<Track> parseEpisodes(String xml, Item podcast) throws Exception {
        XmlPullParser p = Xml.newPullParser();
        p.setInput(new StringReader(xml.trim()));
        List<Track> out = new ArrayList<>();
        String channelArt = podcast.art;
        boolean inItem = false;
        String title = null, url = null, type = null, guid = null, pub = null, dur = null, art = null;
        int event = p.getEventType();
        while (event != XmlPullParser.END_DOCUMENT && out.size() < 100) {
            if (event == XmlPullParser.START_TAG) {
                String n = p.getName();
                if (n.indexOf(':') >= 0) n = n.substring(n.indexOf(':') + 1); // parsers may or may not split off "itunes:"
                if ("item".equals(n)) {
                    inItem = true;
                    title = url = type = guid = pub = dur = art = null;
                } else if ("image".equals(n) && p.getAttributeValue(null, "href") != null) {
                    String href = p.getAttributeValue(null, "href");
                    if (inItem) art = href;
                    else if (channelArt == null) channelArt = href;
                } else if (inItem && "enclosure".equals(n)) {
                    url = p.getAttributeValue(null, "url");
                    type = p.getAttributeValue(null, "type");
                } else if (inItem && "title".equals(n)) {
                    title = p.nextText();
                } else if (inItem && "guid".equals(n)) {
                    guid = p.nextText();
                } else if (inItem && "pubDate".equals(n)) {
                    pub = p.nextText();
                } else if (inItem && "duration".equals(n)) {
                    dur = p.nextText();
                }
            } else if (event == XmlPullParser.END_TAG && "item".equals(p.getName())) {
                inItem = false;
                boolean audio = type == null || type.isEmpty() || type.toLowerCase(Locale.ROOT).startsWith("audio");
                if (url != null && !url.trim().isEmpty() && audio) {
                    String id = guid != null && !guid.trim().isEmpty() ? guid.trim() : url.trim();
                    Track t = Track.episode("ep:" + podcast.key() + ":" + id, title == null ? "Episodio" : title.trim(),
                            podcast.title, parseDuration(dur), parsePubDate(pub),
                            art != null && !art.isEmpty() ? art : channelArt, url.trim());
                    if (!out.contains(t)) out.add(t);
                }
            }
            event = p.next();
        }
        return out;
    }

    public interface ItemResults {
        void onItems(List<Item> items, boolean failed);
    }

    /** Searches Apple's podcast directory; results on the main thread. */
    public void searchPodcasts(final String query, final ItemResults cb) {
        Net.POOL.execute(new Runnable() {
            @Override
            public void run() {
                List<Item> found = new ArrayList<>();
                boolean failed = false;
                try {
                    found = parseItems(Item.PODCAST, Net.getString(APPLE_SEARCH + "&country=" + country()
                            + "&term=" + Net.enc(query)));
                } catch (Exception e) {
                    failed = true;
                }
                final List<Item> result = found;
                final boolean fail = failed;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        cb.onItems(result, fail);
                    }
                });
            }
        });
    }

    private final LinkedHashMap<String, Item> followed = new LinkedHashMap<>();
    /** Episode key -> {position, duration} in ms, oldest first. */
    private final LinkedHashMap<String, long[]> progress = new LinkedHashMap<>();

    public boolean isFollowing(Item it) {
        return followed.containsKey(it.key());
    }

    public void toggleFollow(Item it) {
        if (followed.remove(it.key()) == null) followed.put(it.key(), it);
        save();
        notifyChanged();
    }

    public List<Item> followedPodcasts() {
        List<Item> out = new ArrayList<>(followed.values());
        Collections.reverse(out); // most recently followed first
        return out;
    }

    /** Remembers where the user stopped an episode; finished or barely started ones are forgotten. */
    public void saveProgress(Track t, long pos, long dur) {
        if (t == null || !t.isEpisode()) return;
        boolean finished = dur > 0 && pos >= dur - 15000;
        if (finished) {
            progress.remove(t.key);
        } else if (pos >= 5000) {
            progress.remove(t.key);
            progress.put(t.key, new long[]{pos, dur});
        } else {
            return;
        }
        if (!byKey.containsKey(t.key)) byKey.put(t.key, t);
        save();
    }

    public long progress(Track t) {
        long[] p = t == null ? null : progress.get(t.key);
        return p == null ? 0 : p[0];
    }

    public long progressDuration(Track t) {
        long[] p = t == null ? null : progress.get(t.key);
        return p == null ? 0 : p[1];
    }

    /** Episodes started but not finished, most recent first. */
    public List<Track> inProgress(int max) {
        List<String> keys = new ArrayList<>(progress.keySet());
        Collections.reverse(keys);
        List<Track> all = resolve(keys);
        return all.size() > max ? new ArrayList<>(all.subList(0, max)) : all;
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
                if (!fullOnly()) {
                    try {
                        out.addAll(parseDeezer(Net.getString(DEEZER + "/search?limit=40&q=" + Net.enc(query))));
                        ok = true;
                    } catch (Exception ignored) {
                    }
                }
                try { // complete songs
                    out.addAll(parseAudius(Net.getString(AUDIUS + audius("/tracks/search?limit=20&query=" + Net.enc(query)))));
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
        if (t.kind == Track.EPISODE) {
            if (t.resolvedUrl != null && System.currentTimeMillis() - t.streamFetchedAt < 5 * 60 * 1000L) return;
            t.resolvedUrl = Net.resolve(t.streamUrl);
            t.streamFetchedAt = System.currentTimeMillis();
            return;
        }
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

    static List<Track> parseAudius(String body) throws Exception {
        JSONObject root = new JSONObject(body);
        JSONArray data = root.optJSONArray("data");
        if (data == null) throw new Exception("Respuesta de Audius sin datos");
        List<Track> out = new ArrayList<>();
        for (int i = 0; i < data.length(); i++) {
            JSONObject o = data.optJSONObject(i);
            Track t = o == null ? null : Track.fromAudius(o, AUDIUS, AUDIUS_APP);
            if (t != null && !out.contains(t)) out.add(t);
        }
        return out;
    }

    static List<Item> parseArchiveItems(String body) throws Exception {
        JSONObject resp = new JSONObject(body).optJSONObject("response");
        JSONArray docs = resp == null ? null : resp.optJSONArray("docs");
        if (docs == null) throw new Exception("Respuesta del Internet Archive sin datos");
        List<Item> out = new ArrayList<>();
        for (int i = 0; i < docs.length(); i++) {
            JSONObject o = docs.optJSONObject(i);
            if (o == null) continue;
            String id = o.optString("identifier", "");
            if (id.isEmpty()) continue;
            Item it = new Item(Item.ARCHIVE, archiveId(id), firstString(o, "title"), firstString(o, "creator"),
                    ARCHIVE + "/services/img/" + id, id);
            if (!out.contains(it)) out.add(it);
        }
        return out;
    }

    /** A stable positive number for an Internet Archive identifier (items are keyed by kind + id). */
    static long archiveId(String identifier) {
        return (identifier.hashCode() & 0x7fffffffL) + 1;
    }

    /** Archive fields can be a string or a list of strings. */
    private static String firstString(JSONObject o, String name) {
        JSONArray a = o.optJSONArray(name);
        if (a != null) return a.length() > 0 ? a.optString(0) : "";
        return o.optString(name, "");
    }

    /** Audio formats the Archive derives, best first for streaming on a phone. */
    static final String[] ARCHIVE_FORMATS = {"VBR MP3", "128Kbps MP3", "MP3", "64Kbps MP3", "Ogg Vorbis"};

    /** The songs of an Internet Archive item, in one format (each song exists in several). */
    static List<Track> parseArchiveTracks(String body, Item item) throws Exception {
        JSONObject root = new JSONObject(body);
        JSONArray files = root.optJSONArray("files");
        if (files == null) throw new Exception("Item del Internet Archive sin archivos");
        JSONObject meta = root.optJSONObject("metadata");
        String album = meta != null && !firstString(meta, "title").isEmpty() ? firstString(meta, "title") : item.title;
        String creator = meta != null && !firstString(meta, "creator").isEmpty() ? firstString(meta, "creator") : item.subtitle;
        String format = null;
        for (String f : ARCHIVE_FORMATS) {
            for (int i = 0; i < files.length() && format == null; i++) {
                JSONObject o = files.optJSONObject(i);
                if (o != null && f.equals(o.optString("format"))) format = f;
            }
            if (format != null) break;
        }
        final List<Object[]> picked = new ArrayList<>();
        if (format != null) {
            for (int i = 0; i < files.length(); i++) {
                JSONObject o = files.optJSONObject(i);
                if (o == null || !format.equals(o.optString("format"))) continue;
                String name = o.optString("name");
                String title = o.optString("title", "");
                if (title.isEmpty()) {
                    title = name.replaceAll("\\.[A-Za-z0-9]+$", "").replace('_', ' ');
                    int slash = title.lastIndexOf('/');
                    if (slash >= 0) title = title.substring(slash + 1);
                }
                String artist = o.optString("creator", o.optString("artist", ""));
                int number = leadingInt(o.optString("track", ""));
                Track t = Track.full("ia:" + item.feed + "/" + name, title, artist.isEmpty() ? creator : artist, album,
                        (long) (parseLength(o.optString("length", "")) * 1000), item.art, item.art,
                        ARCHIVE + "/download/" + Net.encPath(item.feed) + "/" + Net.encPath(name));
                picked.add(new Object[]{number, name, t});
            }
        }
        Collections.sort(picked, new Comparator<Object[]>() {
            @Override
            public int compare(Object[] a, Object[] b) {
                int na = (Integer) a[0], nb = (Integer) b[0];
                if (na != nb) return na == 0 ? 1 : nb == 0 ? -1 : Integer.compare(na, nb);
                return ((String) a[1]).compareToIgnoreCase((String) b[1]);
            }
        });
        List<Track> out = new ArrayList<>();
        for (Object[] p : picked) out.add((Track) p[2]);
        return out;
    }

    private static int leadingInt(String s) {
        int n = 0, i = 0;
        while (i < s.length() && Character.isDigit(s.charAt(i))) n = n * 10 + (s.charAt(i++) - '0');
        return n;
    }

    /** "245.62" seconds or "4:05" / "1:02:03". */
    static double parseLength(String s) {
        try {
            if (!s.contains(":")) return s.isEmpty() ? 0 : Double.parseDouble(s);
            double total = 0;
            for (String part : s.split(":")) total = total * 60 + Double.parseDouble(part);
            return total;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ---------------------------------------------------------------- discovery mix

    public static final String MIX = "discovery";
    static final int MIX_SIZE = 30;
    private long mixDay = -1;
    /** True when the last mix was built from your taste (false: a starter mix of what's trending). */
    private boolean mixPersonal;

    static long today() {
        return (System.currentTimeMillis() + java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis())) / 86400000L;
    }

    public boolean mixIsPersonal() {
        return mixPersonal;
    }

    private void loadMix(final Section s) {
        // snapshot on the main thread; the network work happens in the background
        final List<Track> likes = likedTracks();
        final List<Track> played = recentlyPlayed(HISTORY_MAX);
        final boolean only = fullOnly();
        final long day = today();
        Net.POOL.execute(new Runnable() {
            @Override
            public void run() {
                List<Track> found = null;
                boolean personal = false;
                try {
                    personal = !mixSeeds(likes, played).isEmpty();
                    found = discoveryMix(likes, played, only, day);
                } catch (Exception ignored) {
                }
                final List<Track> result = found;
                final boolean fromTaste = personal;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (result == null || result.isEmpty()) {
                            s.state = FAILED;
                        } else {
                            register(result);
                            s.tracks = result;
                            s.state = READY;
                            mixDay = day;
                            mixPersonal = fromTaste;
                        }
                        notifyChanged();
                    }
                });
            }
        });
    }

    /** An artist you listen to: weight 3 per like, 1 per play. */
    static final class Seed {
        final String name;
        long deezerId;
        int weight;

        Seed(String name) {
            this.name = name;
        }
    }

    /** Your artists, most listened first (local files, previews and complete songs alike). */
    static List<Seed> mixSeeds(List<Track> likes, List<Track> played) {
        LinkedHashMap<String, Seed> seeds = new LinkedHashMap<>();
        for (int pass = 0; pass < 2; pass++) {
            for (Track t : pass == 0 ? likes : played) {
                if (t.isLive() || t.isEpisode() || "Artista desconocido".equals(t.artist)) continue;
                String k = fold(t.artist).trim();
                Seed sd = seeds.get(k);
                if (sd == null) seeds.put(k, sd = new Seed(t.artist));
                sd.weight += pass == 0 ? 3 : 1;
                if (sd.deezerId <= 0 && t.key.startsWith("dz:") && t.artistId > 0) sd.deezerId = t.artistId;
            }
        }
        List<Seed> out = new ArrayList<>(seeds.values());
        Collections.sort(out, new Comparator<Seed>() {
            @Override
            public int compare(Seed a, Seed b) {
                return b.weight - a.weight;
            }
        });
        return out;
    }

    /**
     * Songs by artists you don't know yet, close to the ones you do: Deezer's related artists and
     * their top songs, plus complete songs from Audius in the genres you play there. Nothing you
     * already liked or heard. The order is shuffled with the day as seed: same mix all day, a new
     * one tomorrow. Without likes or history it is a starter mix of what's trending.
     * Background thread.
     */
    static List<Track> discoveryMix(List<Track> likes, List<Track> played, boolean fullOnly, long day) throws Exception {
        java.util.Set<String> known = new java.util.HashSet<>();
        for (Track t : likes) known.add(t.key);
        for (Track t : played) known.add(t.key);
        List<Seed> seeds = mixSeeds(likes, played);
        java.util.Set<String> seedNames = new java.util.HashSet<>();
        for (Seed sd : seeds) seedNames.add(fold(sd.name).trim());

        List<Track> catalog = new ArrayList<>(); // previews of related artists
        List<Track> free = new ArrayList<>();    // complete songs
        int calls = 0, failures = 0;
        Exception last = null;

        if (!fullOnly) {
            if (seeds.isEmpty()) {
                calls++;
                try {
                    catalog.addAll(parseDeezer(Net.getString(DEEZER + "/chart/0/tracks?limit=30")));
                } catch (Exception e) {
                    failures++;
                    last = e;
                }
            }
            java.util.Set<Long> usedRelated = new java.util.HashSet<>();
            for (int i = 0; i < Math.min(4, seeds.size()); i++) {
                Seed sd = seeds.get(i);
                calls++;
                try {
                    long id = sd.deezerId;
                    if (id <= 0) {
                        Item a = findArtist(sd.name);
                        if (a == null) continue;
                        id = a.id;
                    }
                    List<Item> related = parseItems(Item.ARTIST, Net.getString(DEEZER + "/artist/" + id + "/related?limit=8"));
                    int taken = 0;
                    for (Item r : related) {
                        if (taken >= 3) break;
                        if (seedNames.contains(fold(r.title).trim()) || !usedRelated.add(r.id)) continue;
                        taken++;
                        int perArtist = 0;
                        for (Track t : parseDeezer(Net.getString(DEEZER + "/artist/" + r.id + "/top?limit=5"))) {
                            if (perArtist >= 2) break;
                            if (known.contains(t.key) || catalog.contains(t)) continue;
                            catalog.add(t);
                            perArtist++;
                        }
                    }
                } catch (Exception e) {
                    failures++;
                    last = e;
                }
            }
        }

        // complete songs: the Audius genres you play, or what's trending there
        List<String> genres = new ArrayList<>();
        for (List<Track> l : java.util.Arrays.asList(likes, played)) {
            for (Track t : l) {
                if (t.key.startsWith("au:") && !genres.contains(t.album) && !"Audius".equals(t.album)) genres.add(t.album);
            }
        }
        if (genres.isEmpty()) genres.add(null);
        for (int i = 0; i < Math.min(2, genres.size()); i++) {
            String g = genres.get(i);
            calls++;
            try {
                java.util.Map<String, Integer> perArtist = new HashMap<>();
                for (Track t : parseAudius(Net.getString(AUDIUS + audius("/tracks/trending?limit=30"
                        + (g == null ? "" : "&genre=" + Net.enc(g)))))) {
                    String a = fold(t.artist).trim();
                    Integer n = perArtist.get(a);
                    if (known.contains(t.key) || seedNames.contains(a) || free.contains(t) || (n != null && n >= 2)) continue;
                    perArtist.put(a, n == null ? 1 : n + 1);
                    free.add(t);
                }
            } catch (Exception e) {
                failures++;
                last = e;
            }
        }
        if (calls > 0 && failures == calls && last != null) throw last;

        java.util.Random r = new java.util.Random(day * 31 + seeds.size());
        Collections.shuffle(catalog, r);
        Collections.shuffle(free, r);
        // alternate: a new artist from the catalog, a complete free song, …
        List<Track> mix = new ArrayList<>();
        for (int i = 0; mix.size() < MIX_SIZE && (i < catalog.size() || i < free.size()); i++) {
            if (i < catalog.size()) mix.add(catalog.get(i));
            if (i < free.size() && mix.size() < MIX_SIZE) mix.add(free.get(i));
        }
        return mix;
    }

    // ---------------------------------------------------------------- full version of a preview

    public interface FullVersion {
        /** Called on the main thread with the complete song, or null if there is none. */
        void onFound(Track full);
    }

    private final Map<String, Track> fullVersions = new HashMap<>();
    private final java.util.Set<String> noFullVersion = new java.util.HashSet<>();

    /** Song title without the "(feat. …)", "[Remastered]" or " - Radio Edit" decorations. */
    static String baseTitle(String title) {
        String t = title.replaceAll("\\s*[\\(\\[][^\\)\\]]*[\\)\\]]", "");
        int dash = t.indexOf(" - ");
        if (dash > 0) t = t.substring(0, dash);
        return fold(t).trim();
    }

    /**
     * Looks for the same song, complete and free, on Audius: same title and same artist name, so
     * a cover or a different song with that name is never offered.
     */
    public void findFullVersion(final Track preview, final FullVersion cb) {
        if (preview.isComplete()) return;
        Track known = fullVersions.get(preview.key);
        if (known != null || noFullVersion.contains(preview.key)) {
            cb.onFound(known);
            return;
        }
        Net.POOL.execute(new Runnable() {
            @Override
            public void run() {
                Track match = null;
                boolean answered = false;
                try {
                    List<Track> found = parseAudius(Net.getString(AUDIUS + audius("/tracks/search?limit=10&query="
                            + Net.enc(preview.artist + " " + preview.title))));
                    answered = true;
                    String title = baseTitle(preview.title), artist = fold(preview.artist).trim();
                    for (Track t : found) {
                        if (baseTitle(t.title).equals(title) && fold(t.artist).trim().equals(artist)) {
                            match = t;
                            break;
                        }
                    }
                } catch (Exception ignored) {
                }
                final Track result = match;
                final boolean definitive = answered;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (result != null) {
                            register(java.util.Collections.singletonList(result));
                            fullVersions.put(preview.key, result);
                        } else if (definitive) {
                            noFullVersion.add(preview.key);
                        }
                        cb.onFound(result);
                    }
                });
            }
        });
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

    /** Online tracks whose metadata must survive a restart: liked, in playlists, or recently played. */
    private List<Track> persistedRemote() {
        java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<>(liked);
        for (List<String> l : playlists.values()) keys.addAll(l);
        keys.addAll(history);
        keys.addAll(progress.keySet());
        List<Track> out = new ArrayList<>();
        for (String k : keys) {
            Track t = byKey.get(k);
            if (t != null && t.remote) out.add(t);
        }
        return out;
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

    // ---------------------------------------------------------------- history

    static final int HISTORY_MAX = 50;

    /** Called when a track starts playing; newest first, no duplicates. */
    public void recordPlayed(Track t) {
        if (t == null) return;
        if (!byKey.containsKey(t.key)) byKey.put(t.key, t);
        history.remove(t.key);
        history.add(0, t.key);
        while (history.size() > HISTORY_MAX) history.remove(history.size() - 1);
        save();
    }

    public List<Track> recentlyPlayed(int max) {
        List<Track> all = resolve(history);
        return all.size() > max ? new ArrayList<>(all.subList(0, max)) : all;
    }

    public void clearHistory() {
        history.clear();
        save();
        notifyChanged();
    }

    // ---------------------------------------------------------------- search history

    public List<String> searchHistory() {
        return new ArrayList<>(searches);
    }

    public void addSearch(String q) {
        if (q == null) return;
        q = q.trim();
        if (q.length() < 2) return;
        for (int i = searches.size() - 1; i >= 0; i--) if (searches.get(i).equalsIgnoreCase(q)) searches.remove(i);
        searches.add(0, q);
        while (searches.size() > 10) searches.remove(searches.size() - 1);
        save();
    }

    public void clearSearchHistory() {
        searches.clear();
        save();
        notifyChanged();
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

    /** @return false if the new name is empty or already taken. */
    public boolean renamePlaylist(String from, String to) {
        if (to == null) return false;
        to = to.trim();
        if (to.isEmpty() || to.equals("__order") || !playlists.containsKey(from)) return false;
        if (to.equals(from)) return true;
        if (playlists.containsKey(to)) return false;
        LinkedHashMap<String, List<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : playlists.entrySet()) {
            copy.put(e.getKey().equals(from) ? to : e.getKey(), e.getValue());
        }
        playlists.clear();
        playlists.putAll(copy);
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
            JSONArray h = new JSONArray(prefs.getString("history", "[]"));
            for (int i = 0; i < h.length(); i++) history.add(h.getString(i));
            JSONArray fo = new JSONArray(prefs.getString("followed", "[]"));
            for (int i = 0; i < fo.length(); i++) {
                Item it = Item.fromJson(fo.getJSONObject(i));
                followed.put(it.key(), it);
            }
            JSONObject pr = new JSONObject(prefs.getString("progress", "{}"));
            Iterator<String> pk = pr.keys();
            while (pk.hasNext()) {
                String k = pk.next();
                JSONArray v = pr.getJSONArray(k);
                progress.put(k, new long[]{v.getLong(0), v.getLong(1)});
            }
            JSONArray sq = new JSONArray(prefs.getString("searches", "[]"));
            for (int i = 0; i < sq.length(); i++) searches.add(sq.getString(i));
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

    private String followedJson() {
        JSONArray a = new JSONArray();
        for (Item it : followed.values()) a.put(it.toJson());
        return a.toString();
    }

    private String progressJson() {
        JSONObject o = new JSONObject();
        try {
            for (Map.Entry<String, long[]> e : progress.entrySet()) {
                o.put(e.getKey(), new JSONArray().put(e.getValue()[0]).put(e.getValue()[1]));
            }
        } catch (Exception ignored) {
        }
        return o.toString();
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
            for (Track t : persistedRemote()) remote.put(t.key, t.toJson());
            prefs.edit()
                    .putString("remote", remote.toString())
                    .putString("history", new JSONArray(history).toString())
                    .putString("searches", new JSONArray(searches).toString())
                    .putString("followed", followedJson())
                    .putString("progress", progressJson())
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

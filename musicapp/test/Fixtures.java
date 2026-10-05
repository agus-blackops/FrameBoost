package com.agus.sonora;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Canned Deezer / radio-browser responses shaped like the real APIs. */
public final class Fixtures implements Net.Fetcher, Net.Resolver {

    static final String[][] SONGS = {
            {"Monaco", "Bad Bunny", "Nadie Sabe Lo Que Va a Pasar Mañana"},
            {"Hips Don't Lie", "Shakira", "Oral Fixation Vol. 2"},
            {"Despacito", "Luis Fonsi", "Vida"},
            {"La Bachata", "Manuel Turizo", "2000"},
            {"Bohemian Rhapsody", "Queen", "A Night at the Opera"},
            {"Flowers", "Miley Cyrus", "Endless Summer Vacation"},
            {"De Música Ligera", "Soda Stereo", "Canción Animal"},
            {"Tusa", "KAROL G", "KG0516"},
            {"Blinding Lights", "The Weeknd", "After Hours"},
            {"Ella Baila Sola", "Eslabon Armado", "Desvelado"},
    };

    public final AtomicInteger trackLookups = new AtomicInteger();
    public final List<String> urls = Collections.synchronizedList(new ArrayList<String>());
    public final AtomicInteger feedLookups = new AtomicInteger();
    public final AtomicInteger feedFetches = new AtomicInteger();
    public final List<String> resolved = Collections.synchronizedList(new ArrayList<String>());
    public final AtomicInteger lyricLookups = new AtomicInteger();
    public volatile boolean offline;
    public volatile boolean radioMirrorDown = true;
    public volatile ImageMaker images;

    public interface ImageMaker {
        byte[] make(String url);
    }

    static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    static String deezerList(int n, int offset) {
        StringBuilder b = new StringBuilder("{\"data\":[");
        for (int i = 0; i < n; i++) {
            String[] s = SONGS[(i + offset) % SONGS.length];
            long id = 1000 + i + offset * 100L;
            if (i > 0) b.append(',');
            b.append("{\"id\":").append(id)
                    .append(",\"readable\":true,\"title\":\"").append(esc(s[0])).append(i >= SONGS.length ? " (Live " + i + ")" : "")
                    .append("\",\"title_short\":\"").append(esc(s[0])).append(i >= SONGS.length ? " (Live " + i + ")" : "")
                    .append("\",\"duration\":").append(180 + i)
                    .append(",\"rank\":900000,\"preview\":\"https://cdnt-preview.dzcdn.net/api/1/1/a/b/c/0/").append(id)
                    .append(".mp3?hdnea=exp=1~acl=old\",\"artist\":{\"id\":").append(50 + (i + offset) % SONGS.length)
                    .append(",\"name\":\"").append(esc(s[1])).append("\",\"picture_medium\":\"https://e-cdns-images.dzcdn.net/images/artist/x/250x250-000000-80-0-0.jpg\"}")
                    .append(",\"album\":{\"id\":").append(7000 + (i + offset) % SONGS.length)
                    .append(",\"title\":\"").append(esc(s[2]))
                    .append("\",\"cover_medium\":\"https://e-cdns-images.dzcdn.net/images/cover/").append((i + offset) % SONGS.length).append("/250x250-000000-80-0-0.jpg\"")
                    .append(",\"cover_big\":\"https://e-cdns-images.dzcdn.net/images/cover/").append((i + offset) % SONGS.length).append("/500x500-000000-80-0-0.jpg\"")
                    .append(",\"type\":\"album\"},\"type\":\"track\"}");
        }
        // a track without preview must be skipped
        b.append(",{\"id\":9,\"title\":\"No preview\",\"preview\":\"\",\"artist\":{\"name\":\"X\"},\"album\":{\"title\":\"Y\"},\"type\":\"track\"}");
        return b.append("],\"total\":").append(n).append("}").toString();
    }

    static String radios() {
        String[][] r = {
                {"Radio Mitre", "news,talk", "https://playerservices.streamtheworld.com/api/livestream-redirect/AM790_56.aac"},
                {"Los 40 Argentina", "pop,hits", "https://stream.example.com/los40.mp3"},
                {"Aspen 102.3", "classic hits,80s", "http://stream.example.com/aspen.mp3"},
                {"Rock & Pop 95.9", "rock", "https://stream.example.com/rockandpop.mp3"},
                {"La 100", "pop,latin", "https://stream.example.com/la100.m3u8"},
                {"Radio Mitre", "duplicate", "https://dup.example.com"},
                {"Broken", "x", ""},
        };
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < r.length; i++) {
            if (i > 0) b.append(',');
            b.append("{\"changeuuid\":\"c").append(i).append("\",\"stationuuid\":\"uuid-").append(i)
                    .append("\",\"name\":\"").append(esc(r[i][0])).append("\",\"url\":\"").append(r[i][2])
                    .append("\",\"url_resolved\":\"").append(r[i][2])
                    .append("\",\"homepage\":\"\",\"favicon\":\"https://logo.example.com/").append(i)
                    .append(".png\",\"tags\":\"").append(r[i][1])
                    .append("\",\"country\":\"Argentina\",\"countrycode\":\"AR\",\"state\":\"Buenos Aires\",\"language\":\"spanish\",\"codec\":\"MP3\",\"bitrate\":128,\"lastcheckok\":1,\"clickcount\":").append(500 - i).append("}");
        }
        return b.append("]").toString();
    }

    @Override
    public String resolve(String url) throws IOException {
        if (offline) throw new IOException("offline");
        resolved.add(url);
        if (url.contains("traffic.example.com")) return "https://cdn.example.com/" + url.substring(url.lastIndexOf('/') + 1);
        return url;
    }

    static String podcastsTop() {
        String[][] p = {{"101", "Radio Ambulante", "NPR"}, {"102", "Entiende Tu Mente", "Molo Cebrián"},
                {"103", "Tanto Monta", "Ivoox"}, {"101", "Duplicado", "X"}, {"0", "Sin id", "X"}};
        StringBuilder b = new StringBuilder("{\"feed\":{\"title\":\"Top\",\"results\":[");
        for (int i = 0; i < p.length; i++) {
            if (i > 0) b.append(',');
            b.append("{\"id\":\"").append(p[i][0]).append("\",\"name\":\"").append(esc(p[i][1]))
                    .append("\",\"artistName\":\"").append(esc(p[i][2]))
                    .append("\",\"artworkUrl100\":\"https://is1-ssl.mzstatic.com/image/p").append(p[i][0]).append("/100x100bb.jpg\",\"url\":\"https://podcasts.apple.com/x\"}");
        }
        return b.append("]}}").toString();
    }

    static String podcastSearch(String term) {
        return "{\"resultCount\":2,\"results\":[{\"collectionId\":201,\"collectionName\":\"" + esc(term) + " al día\","
                + "\"artistName\":\"Voces\",\"artworkUrl600\":\"https://is1-ssl.mzstatic.com/image/p201/600x600bb.jpg\","
                + "\"feedUrl\":\"https://feeds.example.com/201.xml\"},"
                + "{\"collectionId\":202,\"collectionName\":\"Otro programa\",\"artistName\":\"Radio X\","
                + "\"artworkUrl600\":\"https://is1-ssl.mzstatic.com/image/p202/600x600bb.jpg\","
                + "\"feedUrl\":\"https://feeds.example.com/202.xml\"}]}";
    }

    static String rss(String podcast) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<rss version=\"2.0\" xmlns:itunes=\"http://www.itunes.com/dtds/podcast-1.0.dtd\">"
                + "<channel><title>" + podcast + "</title><itunes:image href=\"https://img.example.com/channel.jpg\"/>"
                + "<item><title>Episodio 3: El final</title><guid>g-3</guid><pubDate>Fri, 03 Oct 2025 08:00:00 +0000</pubDate>"
                + "<itunes:duration>42:10</itunes:duration><enclosure url=\"http://traffic.example.com/ep3.mp3\" length=\"1\" type=\"audio/mpeg\"/></item>"
                + "<item><title>Episodio 2 (video)</title><guid>g-2v</guid><enclosure url=\"http://traffic.example.com/ep2.mp4\" type=\"video/mp4\"/></item>"
                + "<item><title>Episodio 2</title><pubDate>Fri, 26 Sep 2025 08:00:00 GMT</pubDate><itunes:duration>3723</itunes:duration>"
                + "<itunes:image href=\"https://img.example.com/ep2.jpg\"/>"
                + "<enclosure url=\"https://cdn.example.com/ep2.mp3\" type=\"audio/mpeg\"/></item>"
                + "<item><title>Solo texto</title><guid>g-text</guid></item>"
                + "<item><title>Episodio 1</title><guid>g-1</guid><itunes:duration>1:02:03</itunes:duration>"
                + "<enclosure url=\"https://cdn.example.com/ep1.mp3\" type=\"audio/mpeg\"/></item>"
                + "</channel></rss>";
    }

    static String items(String kind, int n) {
        StringBuilder b = new StringBuilder("{\"data\":[");
        for (int i = 0; i < n; i++) {
            if (i > 0) b.append(',');
            long id = (kind.equals("album") ? 300 : kind.equals("playlist") ? 400 : 500) + i;
            if (kind.equals("album")) {
                b.append("{\"id\":").append(id).append(",\"title\":\"Álbum ").append(i + 1)
                        .append("\",\"cover_big\":\"https://cdn.dz.example/alb").append(i).append("/500x500.jpg\",\"cover_medium\":\"https://cdn.dz.example/alb").append(i)
                        .append("/250x250.jpg\",\"artist\":{\"id\":").append(50 + i).append(",\"name\":\"").append(esc(SONGS[i % SONGS.length][1])).append("\"},\"type\":\"album\"}");
            } else if (kind.equals("playlist")) {
                b.append("{\"id\":").append(id).append(",\"title\":\"Lista ").append(i + 1)
                        .append("\",\"nb_tracks\":").append(20 + i).append(",\"picture_big\":\"https://cdn.dz.example/pl").append(i)
                        .append("/500x500.jpg\",\"user\":{\"name\":\"Deezer\"},\"type\":\"playlist\"}");
            } else {
                b.append("{\"id\":").append(50 + i).append(",\"name\":\"").append(esc(SONGS[i % SONGS.length][1]))
                        .append("\",\"picture_big\":\"https://cdn.dz.example/ar").append(i).append("/500x500.jpg\",\"type\":\"artist\"}");
            }
        }
        return b.append("]}").toString();
    }

    static String albumTracks() {
        StringBuilder b = new StringBuilder("{\"data\":[");
        for (int i = 0; i < 4; i++) {
            if (i > 0) b.append(',');
            b.append("{\"id\":").append(9000 + i).append(",\"title\":\"Pista ").append(i + 1)
                    .append("\",\"title_short\":\"Pista ").append(i + 1).append("\",\"duration\":").append(200 + i)
                    .append(",\"preview\":\"https://cdnt-preview.dzcdn.net/").append(9000 + i)
                    .append(".mp3?hdnea=a\",\"artist\":{\"id\":50,\"name\":\"Bad Bunny\"},\"type\":\"track\"}");
        }
        return b.append("]}").toString();
    }

    @Override
    public byte[] get(String url) throws IOException {
        urls.add(url);
        if (offline) throw new IOException("offline");
        try {
            Thread.sleep(5);
        } catch (InterruptedException ignored) {
        }
        if (url.contains("radio-browser")) {
            if (radioMirrorDown && url.startsWith("https://de1.")) throw new IOException("mirror down");
            return radios().getBytes("UTF-8");
        }
        if (url.startsWith("https://api.deezer.com/track/")) {
            trackLookups.incrementAndGet();
            String id = url.substring(url.lastIndexOf('/') + 1);
            return ("{\"id\":" + id + ",\"preview\":\"https://cdnt-preview.dzcdn.net/" + id + ".mp3?hdnea=fresh\"}").getBytes("UTF-8");
        }
        if (url.contains("api.deezer.com/search") && url.contains("zzzz")) {
            return "{\"data\":[],\"total\":0}".getBytes("UTF-8");
        }
        if (url.contains("api.deezer.com/search") && url.contains("q=error")) {
            return "{\"error\":{\"type\":\"Exception\",\"message\":\"Quota limit exceeded\",\"code\":4}}".getBytes("UTF-8");
        }
        if (url.startsWith("https://rss.applemarketingtools.com/")) {
            feedLookups.incrementAndGet();
            if (url.contains("/ar/") && !url.contains("/us/")) throw new IOException("HTTP 404");
            return podcastsTop().getBytes("UTF-8");
        }
        if (url.startsWith("https://itunes.apple.com/search")) {
            String term = java.net.URLDecoder.decode(url.substring(url.indexOf("term=") + 5), "UTF-8");
            if (term.contains("zzzz")) return "{\"resultCount\":0,\"results\":[]}".getBytes("UTF-8");
            return podcastSearch(term).getBytes("UTF-8");
        }
        if (url.startsWith("https://itunes.apple.com/lookup")) {
            String id = url.substring(url.indexOf("id=") + 3);
            return ("{\"resultCount\":1,\"results\":[{\"collectionId\":" + id + ",\"feedUrl\":\"https://feeds.example.com/" + id + ".xml\"}]}").getBytes("UTF-8");
        }
        if (url.startsWith("https://feeds.example.com/")) {
            feedFetches.incrementAndGet();
            if (url.contains("/999.xml")) throw new IOException("HTTP 500");
            return rss("Podcast " + url.substring(url.lastIndexOf('/') + 1)).getBytes("UTF-8");
        }
        if (url.contains("api.deezer.com/editorial/0/releases")) return items("album", 6).getBytes("UTF-8");
        if (url.contains("api.deezer.com/chart/0/artists")) return items("artist", 6).getBytes("UTF-8");
        if (url.contains("api.deezer.com/chart/0/playlists")) return items("playlist", 5).getBytes("UTF-8");
        if (url.endsWith("api.deezer.com/genre")) {
            return ("{\"data\":[{\"id\":0,\"name\":\"Todo\"},{\"id\":132,\"name\":\"Pop\",\"picture_big\":\"https://cdn.dz.example/g132/500x500.jpg\"},"
                    + "{\"id\":116,\"name\":\"Rap/Hip Hop\"},{\"id\":152,\"name\":\"Rock\"}]}").getBytes("UTF-8");
        }
        if (url.contains("api.deezer.com/album/")) return albumTracks().getBytes("UTF-8");
        if (url.contains("api.deezer.com/playlist/")) return deezerList(20, 4).getBytes("UTF-8");
        if (url.startsWith("https://lrclib.net/api/")) {
            lyricLookups.incrementAndGet();
            String dec = java.net.URLDecoder.decode(url, "UTF-8");
            boolean search = dec.contains("/api/search");
            if (dec.contains("track_name=Monaco")) {
                return "{\"instrumental\":true,\"plainLyrics\":null,\"syncedLyrics\":null}".getBytes("UTF-8");
            }
            if (dec.contains("track_name=Flowers")) {
                if (search) return "[]".getBytes("UTF-8");
                throw new IOException("HTTP 404");
            }
            if (dec.contains("track_name=Hips Don't Lie")) {
                if (!search) throw new IOException("HTTP 404");
                return "[{\"id\":1,\"trackName\":\"Hips Don't Lie\",\"instrumental\":false,\"plainLyrics\":\"Ladies up in here tonight\\nNo, no, no\",\"syncedLyrics\":null}]".getBytes("UTF-8");
            }
            if (search) return "[]".getBytes("UTF-8");
            return ("{\"id\":7,\"instrumental\":false,\"plainLyrics\":\"uno\\ndos\\ntres\\ncuatro\\ncinco\","
                    + "\"syncedLyrics\":\"[ar:Someone]\\n[00:00.50] Primera línea\\n[00:04.00] Segunda línea\\n[00:08.25] Tercera línea\\n[00:12.00] \\n[00:16.5] Última línea\"}").getBytes("UTF-8");
        }
        if (url.contains("api.deezer.com/chart")) return deezerList(50, 0).getBytes("UTF-8");
        if (url.contains("api.deezer.com/artist/")) return deezerList(12, 3).getBytes("UTF-8");
        if (url.contains("api.deezer.com/search")) {
            int off = Math.abs(url.hashCode()) % SONGS.length;
            return deezerList(15, off).getBytes("UTF-8");
        }
        if (images != null) return images.make(url);
        return new byte[]{1, 2, 3};
    }

    static byte[] bytes(ByteArrayOutputStream o) {
        return o.toByteArray();
    }
}

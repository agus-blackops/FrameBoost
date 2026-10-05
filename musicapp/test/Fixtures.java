package com.agus.sonora;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Canned Deezer / radio-browser responses shaped like the real APIs. */
public final class Fixtures implements Net.Fetcher {

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

package com.agus.sonora;

import android.content.Context;
import android.util.Xml;

import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Your data in files you keep: a full backup (JSON), playlists from other apps (M3U) and podcast
 * subscriptions in the standard format every podcast app reads (OPML).
 */
final class Backup {

    static final String APP = "Sonora";
    static final int FORMAT = 1;

    private Backup() {
    }

    static String fileName() {
        return "sonora-copia-" + new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date()) + ".json";
    }

    /** Likes, playlists, podcasts, progress, history and settings. */
    static String export(Context c) throws Exception {
        PlayerEngine p = PlayerEngine.get(c);
        Library lib = Library.get(c);
        JSONObject settings = new JSONObject()
                .put("fullOnly", lib.fullOnly())
                .put("crossfade", p.crossfadeMs())
                .put("skipSilence", p.skipSilence())
                .put("speed", (double) p.speed())
                .put("wifiOnly", Downloads.get(c).wifiOnly())
                .put("episodeAlerts", PodcastCheckJob.enabled(c))
                .put("theme", Theme.mode(c))
                .put("systemAccent", Theme.systemAccent(c));
        return new JSONObject()
                .put("app", APP)
                .put("format", FORMAT)
                .put("exportedAt", System.currentTimeMillis())
                .put("library", lib.exportData())
                .put("settings", settings)
                .toString(2);
    }

    /** Adds a backup to what's here and applies its settings. Throws if it isn't a Sonora backup. */
    static Library.ImportResult restore(Context c, String text) throws Exception {
        JSONObject o;
        try {
            o = new JSONObject(text);
        } catch (Exception e) {
            throw new Exception("El archivo no es una copia de seguridad de Sonora");
        }
        if (!APP.equals(o.optString("app")) || o.optJSONObject("library") == null) {
            throw new Exception("El archivo no es una copia de seguridad de Sonora");
        }
        if (o.optInt("format", 1) > FORMAT) {
            throw new Exception("La copia es de una versión más nueva de Sonora; actualiza la app");
        }
        Library.ImportResult r = Library.get(c).importData(o.getJSONObject("library"));
        JSONObject s = o.optJSONObject("settings");
        if (s != null) {
            PlayerEngine p = PlayerEngine.get(c);
            if (s.has("fullOnly")) Library.get(c).setFullOnly(s.getBoolean("fullOnly"));
            if (s.has("crossfade")) p.setCrossfade(s.getInt("crossfade"));
            if (s.has("skipSilence")) p.setSkipSilence(s.getBoolean("skipSilence"));
            if (s.has("speed")) p.setSpeed((float) s.getDouble("speed"));
            if (s.has("wifiOnly")) Downloads.get(c).setWifiOnly(s.getBoolean("wifiOnly"));
            if (s.has("episodeAlerts")) PodcastCheckJob.setEnabled(c, s.getBoolean("episodeAlerts"));
            if (s.has("theme")) Theme.setMode(c, s.getInt("theme"));
            if (s.has("systemAccent")) Theme.setSystemAccent(c, s.getBoolean("systemAccent"));
        }
        return r;
    }

    // ---------------------------------------------------------------- M3U

    /** Reads an M3U / M3U8 playlist: "#EXTINF:seconds,Artist - Title" followed by a path or a link. */
    static List<Library.M3uEntry> parseM3u(String text) {
        List<Library.M3uEntry> out = new ArrayList<>();
        String title = null, artist = null;
        long dur = 0;
        for (String raw : text.replace("﻿", "").split("\r?\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#EXTINF:")) {
                String info = line.substring(8);
                int comma = info.indexOf(',');
                String secs = comma >= 0 ? info.substring(0, comma) : info;
                String name = comma >= 0 ? info.substring(comma + 1).trim() : "";
                try {
                    String n = secs.trim().split(" ")[0];
                    dur = Math.max(0, Long.parseLong(n)) * 1000L;
                } catch (NumberFormatException e) {
                    dur = 0;
                }
                int dash = name.indexOf(" - ");
                artist = dash > 0 ? name.substring(0, dash).trim() : null;
                title = dash > 0 ? name.substring(dash + 3).trim() : (name.isEmpty() ? null : name);
                continue;
            }
            if (line.startsWith("#")) continue;
            out.add(new Library.M3uEntry(title, artist, line, dur));
            title = artist = null;
            dur = 0;
        }
        return out;
    }

    /** "Mi lista.m3u8" -> "Mi lista". */
    static String playlistName(String fileName) {
        if (fileName == null) return null;
        String n = fileName;
        int slash = n.lastIndexOf('/');
        if (slash >= 0) n = n.substring(slash + 1);
        return n.replaceAll("(?i)\\.m3u8?$", "").trim();
    }

    // ---------------------------------------------------------------- OPML

    /** Podcasts of an OPML file (the outlines with an RSS feed). */
    static List<Library.Item> parseOpml(String xml) throws Exception {
        XmlPullParser p = Xml.newPullParser();
        p.setInput(new StringReader(xml.replace("﻿", "").trim()));
        List<Library.Item> out = new ArrayList<>();
        for (int e = p.getEventType(); e != XmlPullParser.END_DOCUMENT; e = p.next()) {
            if (e != XmlPullParser.START_TAG || !"outline".equalsIgnoreCase(p.getName())) continue;
            String feed = p.getAttributeValue(null, "xmlUrl");
            if (feed == null || feed.trim().isEmpty()) continue;
            String title = p.getAttributeValue(null, "text");
            if (title == null || title.isEmpty()) title = p.getAttributeValue(null, "title");
            out.add(new Library.Item(Library.Item.PODCAST, Library.archiveId(feed.trim()), title, "", null, feed.trim()));
        }
        return out;
    }

    static String exportOpml(List<Library.Item> podcasts) {
        StringBuilder b = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<opml version=\"2.0\">\n"
                + "  <head><title>Podcasts de Sonora</title></head>\n  <body>\n");
        for (Library.Item it : podcasts) {
            if (it.feed == null) continue;
            b.append("    <outline type=\"rss\" text=\"").append(xml(it.title)).append("\" title=\"").append(xml(it.title))
                    .append("\" xmlUrl=\"").append(xml(it.feed)).append("\"/>\n");
        }
        return b.append("  </body>\n</opml>\n").toString();
    }

    private static String xml(String s) {
        return s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }
}

package com.agus.sonora;

import org.json.JSONObject;

/**
 * One playable item: a file on the device, a 30-second preview of a real song from the
 * Deezer catalog, or a live radio station.
 */
public final class Track {

    public static final int LOCAL = 0;
    public static final int PREVIEW = 1;
    public static final int RADIO = 2;
    public static final int EPISODE = 3;

    /** Stable identity used for likes and playlists: a content:// uri, "dz:<id>" or "radio:<uuid>". */
    public final String key;
    public final String title;
    public final String artist;
    public final String album;
    public final long albumId;
    public final int kind;
    public final boolean remote;
    public final long dateAdded;
    /** Remote cover art (large / small), or null. */
    public final String artUrl;
    public final String artSmall;
    /** Deezer track id / artist id for online tracks. */
    public final long remoteId;
    public final long artistId;
    /** May be 0 until the player learns it. */
    public long durationMs;
    /** What the player opens. Deezer preview links expire, so they are refreshed before playing. */
    public volatile String streamUrl;
    public volatile long streamFetchedAt;
    /** Episodes: the audio link after following redirects (MediaPlayer can't cross http/https redirects). */
    public volatile String resolvedUrl;

    public Track(String key, String title, String artist, String album, long albumId,
                 long durationMs, boolean remote, long dateAdded) {
        this(key, remote ? PREVIEW : LOCAL, title, artist, album, albumId, durationMs, dateAdded,
                null, null, 0, 0, remote ? null : key);
    }

    private Track(String key, int kind, String title, String artist, String album, long albumId,
                  long durationMs, long dateAdded, String artUrl, String artSmall, long remoteId,
                  long artistId, String streamUrl) {
        this.key = key;
        this.kind = kind;
        this.remote = kind != LOCAL;
        this.title = title == null || title.isEmpty() ? "Sin título" : title;
        this.artist = artist == null || artist.isEmpty() || "<unknown>".equals(artist)
                ? "Artista desconocido" : artist;
        this.album = album == null || album.isEmpty() ? (kind == RADIO ? "Radio" : "Álbum desconocido") : album;
        this.albumId = albumId;
        this.durationMs = durationMs;
        this.dateAdded = dateAdded;
        this.artUrl = emptyToNull(artUrl);
        this.artSmall = emptyToNull(artSmall) != null ? artSmall : this.artUrl;
        this.remoteId = remoteId;
        this.artistId = artistId;
        this.streamUrl = emptyToNull(streamUrl);
        this.streamFetchedAt = this.streamUrl != null ? System.currentTimeMillis() : 0;
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() || "null".equals(s) ? null : s;
    }

    public boolean isLive() {
        return kind == RADIO;
    }

    public boolean isEpisode() {
        return kind == EPISODE;
    }

    /** The link handed to the media player. */
    public String playUrl() {
        if (kind == EPISODE && resolvedUrl != null) return resolvedUrl;
        return streamUrl;
    }

    /** A podcast episode; {@code publishedMs} goes into dateAdded and is shown as the release date. */
    static Track episode(String key, String title, String podcast, long durationMs, long publishedMs,
                         String art, String url) {
        return new Track(key, EPISODE, title, podcast, podcast, 0, durationMs, publishedMs,
                art, art, 0, 0, url);
    }

    /** A track from an album listing, where Deezer omits the album object. */
    static Track fromDeezerAlbum(JSONObject o, String albumTitle, long albumId, String cover, String smallCover) {
        JSONObject artist = o.optJSONObject("artist");
        long id = o.optLong("id");
        String title = o.optString("title_short", "");
        if (title.isEmpty()) title = o.optString("title");
        return new Track("dz:" + id, PREVIEW, title, artist == null ? null : artist.optString("name"),
                albumTitle, albumId, o.optLong("duration") * 1000L, 0, cover, smallCover, id,
                artist == null ? 0 : artist.optLong("id"), o.optString("preview"));
    }

    /** Builds a track from a Deezer API track object. */
    static Track fromDeezer(JSONObject o) {
        JSONObject artist = o.optJSONObject("artist");
        JSONObject album = o.optJSONObject("album");
        long id = o.optLong("id");
        String title = o.optString("title_short", "");
        if (title.isEmpty()) title = o.optString("title");
        return new Track("dz:" + id, PREVIEW, title,
                artist == null ? null : artist.optString("name"),
                album == null ? null : album.optString("title"),
                album == null ? 0 : album.optLong("id"),
                o.optLong("duration") * 1000L, 0,
                album == null ? null : album.optString("cover_big"),
                album == null ? null : album.optString("cover_medium"),
                id, artist == null ? 0 : artist.optLong("id"),
                o.optString("preview"));
    }

    /** Builds a station from a radio-browser.info station object. */
    static Track fromRadio(JSONObject o) {
        String tags = o.optString("tags", "").replace(",", " · ");
        if (tags.length() > 60) tags = tags.substring(0, 60);
        String place = o.optString("state", "");
        if (place.isEmpty()) place = o.optString("country", "");
        String fav = o.optString("favicon");
        return new Track("radio:" + o.optString("stationuuid"), RADIO, o.optString("name").trim(),
                tags.isEmpty() ? place : tags, place, 0, 0, 0, fav, fav, 0, 0,
                o.optString("url_resolved", o.optString("url")));
    }

    /** Serialised form for remembering liked / playlisted online tracks between launches. */
    JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("key", key).put("kind", kind).put("title", title).put("artist", artist)
                    .put("album", album).put("albumId", albumId).put("dur", durationMs)
                    .put("art", artUrl == null ? "" : artUrl)
                    .put("artS", artSmall == null ? "" : artSmall)
                    .put("rid", remoteId).put("aid", artistId)
                    .put("url", streamUrl == null ? "" : streamUrl);
        } catch (Exception ignored) {
        }
        return o;
    }

    static Track fromJson(JSONObject o) {
        Track t = new Track(o.optString("key"), o.optInt("kind", PREVIEW), o.optString("title"),
                o.optString("artist"), o.optString("album"), o.optLong("albumId"), o.optLong("dur"),
                0, o.optString("art"), o.optString("artS"), o.optLong("rid"), o.optLong("aid"),
                o.optString("url"));
        t.streamFetchedAt = 0; // stored preview links are probably expired
        return t;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Track && ((Track) o).key.equals(key);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }
}

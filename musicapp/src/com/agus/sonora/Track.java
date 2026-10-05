package com.agus.sonora;

/** One playable song: either a file on the device (content:// uri) or an online stream (https url). */
public final class Track {
    /** Stable identity; the uri the player opens. Used for likes and playlists. */
    public final String key;
    public final String title;
    public final String artist;
    public final String album;
    public final long albumId;
    public final boolean remote;
    public final long dateAdded;
    /** May be 0 until the player learns it (online streams). */
    public long durationMs;

    public Track(String key, String title, String artist, String album, long albumId,
                 long durationMs, boolean remote, long dateAdded) {
        this.key = key;
        this.title = title == null || title.isEmpty() ? "Sin título" : title;
        this.artist = artist == null || artist.isEmpty() || "<unknown>".equals(artist)
                ? "Artista desconocido" : artist;
        this.album = album == null || album.isEmpty() ? "Álbum desconocido" : album;
        this.albumId = albumId;
        this.durationMs = durationMs;
        this.remote = remote;
        this.dateAdded = dateAdded;
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

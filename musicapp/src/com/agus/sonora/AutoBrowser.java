package com.agus.sonora;

import android.content.Context;
import android.media.MediaDescription;
import android.media.browse.MediaBrowser.MediaItem;
import android.net.Uri;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;

/**
 * What Android Auto (and other car or voice clients) can browse: your likes, the discovery mix,
 * downloads, complete songs, radios, your playlists and the podcasts you follow. Playable items
 * are "list|trackKey", so a tap plays that list from that song.
 */
final class AutoBrowser {

    static final String ROOT = "root";
    static final String LIKED = "liked";
    static final String MIX = "mix";
    static final String DOWNLOADS = "downloads";
    static final String FULL = "full";
    static final String RADIO = "radio";
    static final String PLAYLISTS = "playlists";
    static final String PODCASTS = "podcasts";
    static final String PLAYLIST = "pl:";
    static final String PODCAST = "pod:";

    /** Content-style hints Android Auto reads: lists for songs, a grid for categories. */
    static Bundle rootHints() {
        Bundle b = new Bundle();
        b.putBoolean("android.media.browse.CONTENT_STYLE_SUPPORTED", true);
        b.putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 2); // grid
        b.putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", 1); // list
        b.putBoolean("android.media.browse.SEARCH_SUPPORTED", true);
        return b;
    }

    /** Car and voice apps allowed to browse (plus Sonora itself). */
    static boolean allowed(String pkg, String ownPkg) {
        if (pkg == null) return false;
        if (pkg.equals(ownPkg)) return true;
        switch (pkg) {
            case "com.google.android.projection.gearhead": // Android Auto
            case "com.google.android.carassistant":
            case "com.android.car.media": // cars with Android built in
            case "com.google.android.googlequicksearchbox": // Google Assistant
            case "com.google.android.wearable.app":
            case "com.android.systemui":
                return true;
            default:
                return false;
        }
    }

    interface Loaded {
        void done();
    }

    private final Context c;

    AutoBrowser(Context c) {
        this.c = c.getApplicationContext();
    }

    private static Uri icon(Context c, int res) {
        return Uri.parse("android.resource://" + c.getPackageName() + "/" + res);
    }

    private MediaItem folder(String id, String title, String sub, int iconRes) {
        return new MediaItem(new MediaDescription.Builder().setMediaId(id).setTitle(title).setSubtitle(sub)
                .setIconUri(icon(c, iconRes)).build(), MediaItem.FLAG_BROWSABLE);
    }

    private static MediaItem song(String list, Track t) {
        String sub = t.isLive() ? "Radio en vivo" : t.isEpisode() ? t.album : t.artist;
        MediaDescription.Builder d = new MediaDescription.Builder().setMediaId(list + "|" + t.key)
                .setTitle(t.title).setSubtitle(sub);
        if (t.artSmall != null && t.artSmall.startsWith("https://")) d.setIconUri(Uri.parse(t.artSmall));
        return new MediaItem(d.build(), MediaItem.FLAG_PLAYABLE);
    }

    /**
     * The children of {@code parent}, or null while they load: then {@code whenLoaded} runs once
     * they're ready (or failed) and this can be asked again.
     */
    List<MediaItem> children(String parent, final Loaded whenLoaded) {
        Library lib = Library.get(c);
        List<MediaItem> out = new ArrayList<>();
        if (ROOT.equals(parent)) {
            out.add(folder(LIKED, "Te gusta", Ui.songs(lib.likedTracks().size()), R.drawable.ic_heart));
            out.add(folder(MIX, "Mezcla de descubrimiento", "Cambia cada día", R.drawable.ic_note));
            if (!Downloads.get(c).tracks().isEmpty()) {
                out.add(folder(DOWNLOADS, "Descargas", "Sin conexión", R.drawable.ic_download));
            }
            out.add(folder(FULL, "Canciones completas", "Gratis, en Audius", R.drawable.ic_note));
            out.add(folder(RADIO, "Radios en vivo", "Emisoras de tu país", R.drawable.ic_podcast));
            if (!lib.playlistNames().isEmpty()) {
                out.add(folder(PLAYLISTS, "Tus playlists", null, R.drawable.ic_library));
            }
            if (!lib.followedPodcasts().isEmpty()) {
                out.add(folder(PODCASTS, "Tus podcasts", null, R.drawable.ic_podcast));
            }
            return out;
        }
        if (PLAYLISTS.equals(parent)) {
            for (String n : lib.playlistNames()) {
                out.add(folder(PLAYLIST + n, n, Ui.songs(lib.playlist(n).size()), R.drawable.ic_library));
            }
            return out;
        }
        if (PODCASTS.equals(parent)) {
            for (Library.Item it : lib.followedPodcasts()) {
                out.add(folder(PODCAST + it.key(), it.title, it.subtitle, R.drawable.ic_podcast));
            }
            return out;
        }
        List<Track> tracks = tracks(parent, whenLoaded);
        if (tracks == null) return null;
        for (Track t : tracks) out.add(song(parent, t));
        return out;
    }

    /** The playable list behind {@code parent}, or null while it loads (see {@link #children}). */
    List<Track> tracks(String parent, final Loaded whenLoaded) {
        final Library lib = Library.get(c);
        if (LIKED.equals(parent)) return lib.likedTracks();
        if (DOWNLOADS.equals(parent)) return Downloads.get(c).tracks();
        if (parent.startsWith(PLAYLIST)) return lib.playlist(parent.substring(PLAYLIST.length()));
        String section = MIX.equals(parent) ? Library.MIX : FULL.equals(parent) ? "full-trending"
                : RADIO.equals(parent) ? "radio" : null;
        if (section != null) {
            final Library.Section s = lib.section(section);
            if (s.state == Library.READY) return s.tracks;
            if (s.state == Library.FAILED) return new ArrayList<>(); // no connection: empty, not stuck
            whenReady(new Check() {
                @Override
                public boolean ok() {
                    return s.state == Library.READY || s.state == Library.FAILED;
                }
            }, whenLoaded);
            lib.loadSection(s);
            return null;
        }
        if (parent.startsWith(PODCAST)) {
            String key = parent.substring(PODCAST.length());
            for (final Library.Item it : lib.followedPodcasts()) {
                if (!it.key().equals(key)) continue;
                int st = lib.itemState(it);
                if (st == Library.READY) return lib.itemTracks(it);
                if (st == Library.FAILED) return new ArrayList<>();
                whenReady(new Check() {
                    @Override
                    public boolean ok() {
                        int s2 = lib.itemState(it);
                        return s2 == Library.READY || s2 == Library.FAILED;
                    }
                }, whenLoaded);
                lib.loadItemTracks(it);
                return null;
            }
        }
        return new ArrayList<>();
    }

    private interface Check {
        boolean ok();
    }

    /** Calls {@code then} once, the first time the library changes and {@code check} holds. */
    private void whenReady(final Check check, final Loaded then) {
        final Library lib = Library.get(c);
        lib.addListener(new Library.Listener() {
            @Override
            public void onLibraryChanged() {
                if (!check.ok()) return;
                lib.removeListener(this);
                if (then != null) then.done();
            }
        });
    }

    /** Plays "list|trackKey": the list, from that song. Returns false if it's gone. */
    boolean play(String mediaId) {
        int bar = mediaId == null ? -1 : mediaId.indexOf('|');
        if (bar < 0) return false;
        String list = mediaId.substring(0, bar), key = mediaId.substring(bar + 1);
        List<Track> tracks = tracks(list, null);
        if (tracks == null) return false;
        for (int i = 0; i < tracks.size(); i++) {
            if (tracks.get(i).key.equals(key)) {
                PlayerEngine.get(c).playList(new ArrayList<>(tracks), i, title(list));
                return true;
            }
        }
        return false;
    }

    String title(String list) {
        if (LIKED.equals(list)) return "Canciones que te gustan";
        if (MIX.equals(list)) return "Mezcla de descubrimiento";
        if (DOWNLOADS.equals(list)) return "Descargas";
        if (FULL.equals(list)) return "Canciones completas";
        if (RADIO.equals(list)) return "Radios en vivo";
        if (list.startsWith(PLAYLIST)) return list.substring(PLAYLIST.length());
        if (list.startsWith(PODCAST)) {
            String key = list.substring(PODCAST.length());
            for (Library.Item it : Library.get(c).followedPodcasts()) if (it.key().equals(key)) return it.title;
        }
        return "Sonora";
    }

    /**
     * "Ok Google, pon … en Sonora": your own songs first, then the online results. With no words
     * ("pon música en Sonora") it plays your likes, or the discovery mix if you have none.
     */
    void playFromSearch(final String query) {
        final Library lib = Library.get(c);
        final PlayerEngine p = PlayerEngine.get(c);
        if (query == null || query.trim().isEmpty()) {
            List<Track> liked = lib.likedTracks();
            if (!liked.isEmpty()) {
                p.shufflePlay(liked, "Canciones que te gustan");
            } else {
                final Library.Section mix = lib.section(Library.MIX);
                if (mix.state == Library.READY) {
                    p.playList(mix.tracks, 0, mix.title);
                } else {
                    whenReady(new Check() {
                        @Override
                        public boolean ok() {
                            return mix.state == Library.READY || mix.state == Library.FAILED;
                        }
                    }, new Loaded() {
                        @Override
                        public void done() {
                            if (mix.state == Library.READY) p.playList(mix.tracks, 0, mix.title);
                        }
                    });
                    lib.loadSection(mix);
                }
            }
            return;
        }
        final List<Track> mine = lib.search(query);
        if (!mine.isEmpty()) {
            p.playList(mine, 0, "Búsqueda: " + query);
            return;
        }
        lib.searchOnline(query, new Library.Results() {
            @Override
            public void onResults(List<Track> tracks, boolean failed) {
                if (!tracks.isEmpty()) p.playList(tracks, 0, "Búsqueda: " + query);
            }
        });
    }
}

package com.agus.sonora;

/**
 * The math of a crossfade: when it starts and how loud each song is meanwhile. Equal-power
 * curves (cosine out, sine in) so the overall loudness doesn't dip in the middle.
 */
final class Crossfade {

    private Crossfade() {
    }

    /** Fade lengths offered in Ajustes, in seconds (0 = songs follow without a gap). */
    static final int[] CHOICES_S = {0, 2, 4, 6, 8, 10, 12};

    /**
     * How long the fade into the next song can be: the setting, but never more than a third of
     * either song (a 30-second preview gets at most 10 s).
     */
    static long length(long settingMs, long currentDurMs, long nextDurMs) {
        if (settingMs <= 0 || currentDurMs <= 0) return 0;
        long max = currentDurMs / 3;
        if (nextDurMs > 0) max = Math.min(max, nextDurMs / 3);
        return Math.max(0, Math.min(settingMs, max));
    }

    /** True once the current song is inside its last {@code fadeMs}. */
    static boolean shouldStart(long positionMs, long durationMs, long fadeMs) {
        return fadeMs > 0 && durationMs > 0 && durationMs - positionMs <= fadeMs;
    }

    /** Volume of the song that is ending, {@code t} ms into a fade of {@code fadeMs}. */
    static float out(long t, long fadeMs) {
        double x = progress(t, fadeMs);
        return (float) Math.cos(x * Math.PI / 2);
    }

    /** Volume of the song that is starting. */
    static float in(long t, long fadeMs) {
        double x = progress(t, fadeMs);
        return (float) Math.sin(x * Math.PI / 2);
    }

    private static double progress(long t, long fadeMs) {
        if (fadeMs <= 0) return 1;
        return Math.max(0, Math.min(1, (double) t / fadeMs));
    }

    /**
     * Songs of the same recording keep their transitions untouched (live albums, DJ sets, albums
     * where songs run into each other): the same Internet Archive item, the same Deezer album, or
     * the same album among your own files.
     */
    static boolean sameRecording(Track a, Track b) {
        if (a == null || b == null) return false;
        if (a.key.startsWith("ia:") && b.key.startsWith("ia:")) return archiveItem(a).equals(archiveItem(b));
        if (a.key.startsWith("dz:") && b.key.startsWith("dz:")) return a.albumId > 0 && a.albumId == b.albumId;
        if (!a.remote && !b.remote) {
            return a.album.equals(b.album) && !"Álbum desconocido".equals(a.album);
        }
        return false;
    }

    private static String archiveItem(Track t) {
        String rest = t.key.substring(3);
        int slash = rest.indexOf('/');
        return slash < 0 ? rest : rest.substring(0, slash);
    }
}

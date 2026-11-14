package com.agus.sonora;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The single music player of the app: owns the play queue, shuffle and repeat, the sleep timer and
 * the saved session, and drives an {@link Audio} (Media3's ExoPlayer in the app). Near the end of
 * each song it preloads the next one so that songs follow each other without a gap. UI and the
 * playback service observe it through {@link Listener}. All methods must be called on the main
 * thread.
 */
public final class PlayerEngine implements Audio.Events {

    public interface Listener {
        void onPlayerChanged();
    }

    public static final int REPEAT_OFF = 0;
    public static final int REPEAT_ALL = 1;
    public static final int REPEAT_ONE = 2;

    /** How long before the end of a song the next one is fetched and preloaded. */
    static final int PRELOAD_BEFORE_END_MS = 12000;

    /** Which audio player to use; tests replace it with a simulator. */
    static Audio.Factory audioFactory = ExoAudio.FACTORY;

    private static PlayerEngine sInstance;

    public static synchronized PlayerEngine get(Context context) {
        if (sInstance == null) sInstance = new PlayerEngine(context.getApplicationContext());
        return sInstance;
    }

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Random random = new Random();

    /** Created on first use, then kept for the life of the app (one audio session for the effects). */
    private Audio audio;
    /** The current track is loaded in {@link #audio}. */
    private boolean opened;
    /** ... and ready to play (duration known). */
    private boolean ready;
    /** Fetching the current track's link before it can be opened. */
    private boolean preparing;
    private boolean playWhenReady;
    private int consecutiveErrors;
    /** Bumped on every load so late async results for an old track are ignored. */
    private int loadToken;

    private final List<Track> queue = new ArrayList<>();
    /** Playback order as indices into {@link #queue}; identity unless shuffling. */
    private final List<Integer> order = new ArrayList<>();
    private int pos = -1;
    private boolean shuffle;
    private int repeat = REPEAT_OFF;
    private String queueName = "";

    private final SharedPreferences prefs;
    private float speed = 1f;
    /** Where to start the current track once it is opened (restored sessions, seeks before load). */
    private int pendingSeek;
    private boolean saveScheduled;
    /** The track the audio was opened for (to remember podcast progress). */
    private Track openTrack;
    private boolean progressTicking;
    /** The track preloaded (or being fetched) to follow the current one without a gap. */
    private Track plannedNext;
    private boolean ticking;
    /** How many times the next song took over without a gap (for tests and diagnostics). */
    int gaplessTransitions;

    public static final int SLEEP_OFF = 0;
    public static final int SLEEP_TIMED = 1;
    public static final int SLEEP_END_OF_TRACK = 2;
    private long sleepEndsAt;
    private boolean sleepAtTrackEnd;
    private Runnable sleepRunnable;

    private PlayerEngine(Context app) {
        this.app = app;
        this.prefs = app.getSharedPreferences("player", Context.MODE_PRIVATE);
        this.speed = prefs.getFloat("speed", 1f);
        // Media3 itself pauses for calls and when headphones or bluetooth disconnect.
    }

    private Audio audio() {
        if (audio == null) {
            audio = audioFactory.create(app, this);
            try {
                Effects.get(app).attach(audio.audioSessionId());
            } catch (Throwable ignored) {
            }
        }
        return audio;
    }

    // ---------------------------------------------------------------- queries

    public Track current() {
        if (pos < 0 || pos >= order.size()) return null;
        return queue.get(order.get(pos));
    }

    public boolean isPlaying() {
        return opened && audio.isPlaying();
    }

    /** True while the user wants sound: playing, or buffering towards playing. */
    public boolean isActive() {
        return isPlaying() || (isBuffering() && playWhenReady);
    }

    public boolean isBuffering() {
        return preparing || (opened && (!ready || audio.isBuffering()));
    }

    public int position() {
        if (!opened || !ready) return pendingSeek;
        return (int) audio.position();
    }

    public int duration() {
        if (opened && ready) {
            long d = audio.duration();
            if (d > 0) return (int) d;
        }
        Track t = current();
        return t == null ? 0 : (int) t.durationMs;
    }

    /** The song a radio is playing right now, when the station tells (null otherwise). */
    public String liveTitle() {
        Track t = current();
        return opened && t != null && t.isLive() ? audio.liveTitle() : null;
    }

    public boolean isShuffle() {
        return shuffle;
    }

    public int repeatMode() {
        return repeat;
    }

    public String queueName() {
        return queueName;
    }

    /** Upcoming tracks in play order, starting with the current one. */
    public List<Track> upcoming() {
        List<Track> out = new ArrayList<>();
        for (int i = Math.max(pos, 0); i < order.size(); i++) out.add(queue.get(order.get(i)));
        return out;
    }

    // ---------------------------------------------------------------- commands

    /** Replaces the queue with {@code tracks} and starts at {@code index}. */
    public void playList(List<Track> tracks, int index, String name) {
        if (tracks == null || tracks.isEmpty()) return;
        queue.clear();
        queue.addAll(tracks);
        queueName = name == null ? "" : name;
        index = Math.max(0, Math.min(index, tracks.size() - 1));
        buildOrder(index);
        consecutiveErrors = 0;
        load(true);
    }

    /** Plays {@code tracks} in random order. */
    public void shufflePlay(List<Track> tracks, String name) {
        if (tracks == null || tracks.isEmpty()) return;
        shuffle = true;
        playList(tracks, random.nextInt(tracks.size()), name);
    }

    /** Inserts a track right after the current one (or starts playing it if idle). */
    public void playNext(Track t) {
        if (current() == null) {
            List<Track> one = new ArrayList<>();
            one.add(t);
            playList(one, 0, t.title);
            return;
        }
        queue.add(t);
        order.add(pos + 1, queue.size() - 1);
        notifyChanged();
    }

    // ---------------------------------------------------------------- queue editing

    /** The whole queue in play order. */
    public List<Track> queueTracks() {
        List<Track> out = new ArrayList<>();
        for (int i : order) out.add(queue.get(i));
        return out;
    }

    /** Index of the current track within {@link #queueTracks()}. */
    public int queueIndex() {
        return pos;
    }

    /** Plays the track at {@code index} of {@link #queueTracks()}. */
    public void jumpTo(int index) {
        if (index < 0 || index >= order.size()) return;
        pos = index;
        consecutiveErrors = 0;
        load(true);
    }

    /** Removes the track at {@code index} of {@link #queueTracks()} from the queue. */
    public void removeAt(int index) {
        if (index < 0 || index >= order.size()) return;
        boolean wasActive = isActive();
        int q = order.get(index);
        queue.remove(q);
        order.remove(index);
        for (int j = 0; j < order.size(); j++) {
            if (order.get(j) > q) order.set(j, order.get(j) - 1);
        }
        if (order.isEmpty()) {
            stop();
            return;
        }
        if (index < pos) {
            pos--;
            notifyChanged();
        } else if (index == pos) {
            if (pos >= order.size()) pos = order.size() - 1;
            load(wasActive);
        } else {
            notifyChanged();
        }
    }

    /**
     * Swaps {@code was} (the current track) for {@code with} in the queue and plays it, e.g. the
     * complete version of a 30-second preview.
     */
    public void replaceCurrent(Track was, Track with) {
        Track t = current();
        if (t == null || !t.equals(was) || with == null) return;
        queue.set(order.get(pos), with);
        consecutiveErrors = 0;
        load(true);
    }

    /** Drops everything after the current track. */
    public void clearUpcoming() {
        if (pos < 0) return;
        while (order.size() > pos + 1) removeAt(order.size() - 1);
    }

    // ---------------------------------------------------------------- speed

    public float speed() {
        return speed;
    }

    public void setSpeed(float s) {
        speed = Math.max(0.5f, Math.min(2f, s));
        prefs.edit().putFloat("speed", speed).apply();
        if (audio != null) audio.setSpeed(speed); // radios always play at 1x
        notifyChanged();
    }

    /** Sound starts (or resumes) for the current track. */
    private void started() {
        Track t = current();
        if (t != null) Library.get(app).recordPlayed(t);
        if (t != null && t.isEpisode()) startProgressTicker();
        startTicker();
    }

    /** Saves the position of a playing podcast episode every 15 seconds. */
    private void startProgressTicker() {
        if (progressTicking) return;
        progressTicking = true;
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isPlaying() && openTrack != null && openTrack.isEpisode()) {
                    rememberEpisode();
                    main.postDelayed(this, 15000);
                } else {
                    progressTicking = false;
                }
            }
        }, 15000);
    }

    private void rememberEpisode() {
        if (openTrack == null || !openTrack.isEpisode() || !opened || !ready) return;
        try {
            Library.get(app).saveProgress(openTrack, audio.position(), audio.duration());
        } catch (Exception ignored) {
        }
    }

    // ---------------------------------------------------------------- sleep timer

    public int sleepMode() {
        if (sleepAtTrackEnd) return SLEEP_END_OF_TRACK;
        return sleepEndsAt > 0 ? SLEEP_TIMED : SLEEP_OFF;
    }

    public long sleepRemainingMs() {
        return sleepEndsAt > 0 ? Math.max(0, sleepEndsAt - System.currentTimeMillis()) : 0;
    }

    public void setSleepTimer(int minutes) {
        cancelSleep();
        if (minutes <= 0) return;
        sleepEndsAt = System.currentTimeMillis() + minutes * 60000L;
        sleepRunnable = new Runnable() {
            @Override
            public void run() {
                sleepEndsAt = 0;
                sleepRunnable = null;
                fadeOutAndPause();
                notifyChanged();
            }
        };
        main.postDelayed(sleepRunnable, minutes * 60000L);
        notifyChanged();
    }

    public void sleepAtEndOfTrack() {
        cancelSleep();
        sleepAtTrackEnd = true;
        notifyChanged();
    }

    public void cancelSleep() {
        if (sleepRunnable != null) main.removeCallbacks(sleepRunnable);
        sleepRunnable = null;
        sleepEndsAt = 0;
        boolean was = sleepAtTrackEnd;
        sleepAtTrackEnd = false;
        if (was) notifyChanged();
    }

    private void fadeOutAndPause() {
        if (!isPlaying()) return;
        final int steps = 10;
        final int token = loadToken;
        main.post(new Runnable() {
            int i = 0;

            @Override
            public void run() {
                if (token != loadToken || !opened) return;
                i++;
                audio.setVolume(Math.max(0f, 1f - (float) i / steps));
                if (i < steps) {
                    main.postDelayed(this, 300);
                } else {
                    pause();
                    audio.setVolume(1f);
                }
            }
        });
    }

    // ---------------------------------------------------------------- session persistence

    /** Remembers what was playing so the app can pick up where the user left off. */
    public void saveSession() {
        saveScheduled = false;
        rememberEpisode();
        if (queue.isEmpty() || pos < 0 || pos >= order.size()) {
            prefs.edit().remove("session").apply();
            return;
        }
        try {
            int start = Math.max(0, pos - 50);
            int end = Math.min(order.size(), start + 300);
            JSONArray tracks = new JSONArray();
            for (int i = start; i < end; i++) tracks.put(queue.get(order.get(i)).toJson());
            JSONObject o = new JSONObject();
            o.put("tracks", tracks).put("pos", pos - start).put("ms", position())
                    .put("shuffle", shuffle).put("repeat", repeat).put("name", queueName);
            prefs.edit().putString("session", o.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    /** Brings back the last queue, paused, without touching the network. */
    public void restoreSession() {
        if (current() != null) return;
        try {
            String raw = prefs.getString("session", null);
            if (raw == null) return;
            JSONObject o = new JSONObject(raw);
            JSONArray a = o.getJSONArray("tracks");
            List<Track> tracks = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) tracks.add(Track.fromJson(a.getJSONObject(i)));
            if (tracks.isEmpty()) return;
            queue.clear();
            queue.addAll(tracks);
            order.clear();
            for (int i = 0; i < tracks.size(); i++) order.add(i);
            pos = Math.max(0, Math.min(o.optInt("pos"), tracks.size() - 1));
            shuffle = o.optBoolean("shuffle");
            repeat = o.optInt("repeat");
            queueName = o.optString("name");
            Track t = current();
            pendingSeek = t != null && t.isLive() ? 0 : o.optInt("ms");
            playWhenReady = false;
            notifyChanged();
        } catch (Exception ignored) {
        }
    }

    private void scheduleSave() {
        if (saveScheduled) return;
        saveScheduled = true;
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (saveScheduled) saveSession();
            }
        }, 3000);
    }

    public void togglePlay() {
        if (isActive()) pause();
        else play();
    }

    public void play() {
        if (current() == null) return;
        if (!opened) {
            if (preparing) { // still fetching the link; start as soon as it arrives
                playWhenReady = true;
                notifyChanged();
                return;
            }
            load(true, pendingSeek);
            return;
        }
        playWhenReady = true;
        audio.setPlayWhenReady(true);
        if (ready) started();
        PlaybackService.ensureStarted(app);
        notifyChanged();
    }

    public void pause() {
        playWhenReady = false;
        if (opened) audio.setPlayWhenReady(false);
        rememberEpisode();
        notifyChanged();
    }

    public void next() {
        consecutiveErrors = 0;
        advance(true);
    }

    public void previous() {
        if (current() == null) return;
        if ((position() > 3000 && !current().isLive()) || pos == 0) {
            seekTo(0);
            if (!isActive()) play();
            return;
        }
        pos--;
        load(true);
    }

    public void seekTo(int ms) {
        Track t = current();
        if (t != null && t.isLive()) return;
        if (opened) {
            audio.seekTo(Math.max(0, ms)); // Media3 also takes seeks while the song is still loading
            if (!ready) pendingSeek = Math.max(0, ms);
            notifyChanged();
        } else if (!preparing && t != null) {
            pendingSeek = Math.max(0, ms); // restored session: applied when playback starts
            notifyChanged();
        }
    }

    /** Jumps forward or back inside the current track (podcasts: -15 s / +30 s). */
    public void skipBy(int deltaMs) {
        if (current() == null) return;
        int target = position() + deltaMs;
        int dur = duration();
        if (dur > 0) target = Math.min(target, dur - 1000);
        seekTo(Math.max(0, target));
    }

    public void toggleShuffle() {
        shuffle = !shuffle;
        int currentIndex = pos >= 0 && pos < order.size() ? order.get(pos) : 0;
        buildOrder(currentIndex);
        notifyChanged();
    }

    public void cycleRepeat() {
        repeat = (repeat + 1) % 3;
        notifyChanged();
    }

    /** Stops everything and clears the queue (used by the notification's close button). */
    public void stop() {
        loadToken++;
        playWhenReady = false;
        releasePlayer();
        queue.clear();
        order.clear();
        pos = -1;
        pendingSeek = 0;
        cancelSleep();
        prefs.edit().remove("session").apply();
        notifyChanged();
    }

    // ---------------------------------------------------------------- internals

    /** Rebuilds the play order so that queue[startIndex] plays at position 0 (shuffle) or in place. */
    private void buildOrder(int startIndex) {
        order.clear();
        if (shuffle) {
            for (int i = 0; i < queue.size(); i++) if (i != startIndex) order.add(i);
            Collections.shuffle(order, random);
            if (!queue.isEmpty()) order.add(0, startIndex);
            pos = queue.isEmpty() ? -1 : 0;
        } else {
            for (int i = 0; i < queue.size(); i++) order.add(i);
            pos = queue.isEmpty() ? -1 : startIndex;
        }
    }

    private void advance(boolean userAction) {
        if (order.isEmpty()) return;
        if (pos + 1 < order.size()) {
            pos++;
            load(true);
        } else if (repeat == REPEAT_ALL || (userAction && repeat == REPEAT_OFF && order.size() > 1)) {
            if (shuffle) buildOrder(order.get(random.nextInt(order.size())));
            else pos = 0;
            load(repeat == REPEAT_ALL || userAction);
        } else {
            // End of the queue: rewind to the start, paused, like most players.
            pos = 0;
            load(false);
        }
    }

    private void load(boolean autoplay) {
        load(autoplay, 0);
    }

    private void load(boolean autoplay, int seekMs) {
        releasePlayer();
        pendingSeek = seekMs;
        final int token = ++loadToken;
        final Track t = current();
        if (t == null) {
            notifyChanged();
            return;
        }
        if (seekMs == 0 && t.isEpisode()) pendingSeek = (int) Library.get(app).progress(t); // resume where it was left
        playWhenReady = autoplay;
        if (autoplay) PlaybackService.ensureStarted(app);
        if (needsLink(t)) {
            // Online song: get a fresh preview link first (they expire); episodes: follow redirects.
            preparing = true;
            notifyChanged();
            Net.POOL.execute(new Runnable() {
                @Override
                public void run() {
                    final boolean ok = fetchLink(t);
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            if (token != loadToken) return; // the user moved on meanwhile
                            if (ok) open(t);
                            else fail();
                        }
                    });
                }
            });
            return;
        }
        open(t);
    }

    private static boolean needsLink(Track t) {
        return t.kind == Track.PREVIEW || t.kind == Track.EPISODE;
    }

    /** Background thread: makes sure {@code t} has a playable link. */
    private static boolean fetchLink(Track t) {
        try {
            Library.ensureStream(t);
            return t.streamUrl != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static String uriOf(Track t) {
        return t.remote ? t.playUrl() : t.key;
    }

    /** Loads {@code t} into the audio player. */
    private void open(Track t) {
        preparing = false;
        openTrack = t;
        opened = true;
        ready = false;
        plannedNext = null;
        Audio a = audio();
        a.open(uriOf(t), t.isLive() ? 0 : pendingSeek, t.isLive());
        a.setSpeed(speed);
        a.setVolume(1f);
        a.setPlayWhenReady(playWhenReady);
        notifyChanged();
    }

    private void releasePlayer() {
        rememberEpisode();
        openTrack = null;
        preparing = false;
        plannedNext = null;
        if (opened) {
            opened = false;
            ready = false;
            audio.stop();
        }
    }

    // ---------------------------------------------------------------- gapless: the next song

    /** The track that will play after the current one finishes on its own, if it can be preloaded. */
    private Track nextToPreload() {
        Track t = current();
        if (t == null || t.isLive() || repeat == REPEAT_ONE || sleepAtTrackEnd) return null;
        int n;
        if (pos + 1 < order.size()) n = pos + 1;
        else if (repeat == REPEAT_ALL && !shuffle && order.size() > 1) n = 0;
        else return null;
        Track next = queue.get(order.get(n));
        // episodes resume from where they were left and radios never end: those load normally
        if (next.isEpisode() || next.isLive()) return null;
        return next;
    }

    /** Runs every second while sound plays: preloads the next song shortly before the end. */
    private void startTicker() {
        if (ticking) return;
        ticking = true;
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isActive()) {
                    ticking = false;
                    return;
                }
                maybePreloadNext();
                main.postDelayed(this, 1000);
            }
        }, 1000);
    }

    private void maybePreloadNext() {
        if (!opened || !ready || plannedNext != null) return;
        long dur = audio.duration();
        if (dur <= 0 || dur - audio.position() > PRELOAD_BEFORE_END_MS) return;
        final Track next = nextToPreload();
        if (next == null) return;
        plannedNext = next;
        final int token = loadToken;
        if (!needsLink(next)) {
            audio.setNext(uriOf(next), false);
            return;
        }
        Net.POOL.execute(new Runnable() {
            @Override
            public void run() {
                final boolean ok = fetchLink(next);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (token != loadToken || plannedNext != next || !opened) return;
                        if (ok) audio.setNext(uriOf(next), false);
                        else plannedNext = null; // it will be tried (and reported) when its turn comes
                    }
                });
            }
        });
    }

    /** After any change to the queue or the modes: forget a preloaded song that no longer follows. */
    private void checkPlannedNext() {
        if (plannedNext != null && plannedNext != nextToPreload()) {
            plannedNext = null;
            if (opened) audio.setNext(null, false);
        }
    }

    // ---------------------------------------------------------------- Audio.Events

    @Override
    public void onReady() {
        if (!opened) return;
        ready = true;
        consecutiveErrors = 0;
        Track t = current();
        long d = audio.duration();
        if (t != null && d > 0) t.durationMs = d;
        pendingSeek = 0;
        if (playWhenReady) started();
        maybePreloadNext(); // short songs are already inside the preload window
        notifyChanged();
    }

    @Override
    public void onAdvanced() {
        // the preloaded song is already playing: just catch up with it
        gaplessTransitions++;
        rememberEpisode();
        if (pos + 1 < order.size()) pos++;
        else pos = 0;
        plannedNext = null;
        Track t = current();
        openTrack = t;
        ready = true;
        consecutiveErrors = 0;
        long d = audio.duration();
        if (t != null && d > 0) t.durationMs = d;
        if (t != null) Library.get(app).recordPlayed(t);
        maybePreloadNext();
        notifyChanged();
    }

    @Override
    public void onEnded() {
        if (!opened) return;
        if (sleepAtTrackEnd) {
            // "Stop after this song": line up the next one, paused.
            sleepAtTrackEnd = false;
            playWhenReady = false;
            if (repeat != REPEAT_ONE && order.size() > 1) pos = (pos + 1) % order.size();
            load(false);
            return;
        }
        if (repeat == REPEAT_ONE) {
            audio.seekTo(0);
            audio.setPlayWhenReady(true);
            notifyChanged();
            return;
        }
        advance(false);
    }

    @Override
    public void onError(String reason) {
        if (opened) fail();
    }

    @Override
    public void onStateChanged() {
        // another app may have taken the audio focus for good (a call, a video): follow the player
        if (opened && ready && !audio.playWhenReady()) playWhenReady = false;
        if (isActive()) startTicker();
        notifyChanged();
    }

    /** The current track can't be played: tell the user and move on to the next one. */
    private void fail() {
        Track t = current();
        consecutiveErrors++;
        String msg = t == null ? "Error de reproducción"
                : "No se pudo reproducir \"" + t.title + "\""
                + (t.remote ? " (revisa tu conexión)" : "");
        Toast.makeText(app, msg, Toast.LENGTH_SHORT).show();
        if (consecutiveErrors < Math.min(order.size(), 5) && pos + 1 < order.size()) {
            boolean keepPlaying = playWhenReady;
            pos++;
            load(keepPlaying);
        } else {
            releasePlayer();
            playWhenReady = false;
            notifyChanged();
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
        checkPlannedNext();
        if (pos >= 0) scheduleSave();
        for (Listener l : listeners) l.onPlayerChanged();
    }
}

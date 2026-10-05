package com.agus.sonora;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The single music player of the app: owns the MediaPlayer, the play queue, shuffle and
 * repeat, and audio focus. UI and the playback service observe it through {@link Listener}.
 * All methods must be called on the main thread.
 */
public final class PlayerEngine implements MediaPlayer.OnPreparedListener,
        MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener,
        AudioManager.OnAudioFocusChangeListener {

    public interface Listener {
        void onPlayerChanged();
    }

    public static final int REPEAT_OFF = 0;
    public static final int REPEAT_ALL = 1;
    public static final int REPEAT_ONE = 2;

    private static PlayerEngine sInstance;

    public static synchronized PlayerEngine get(Context context) {
        if (sInstance == null) sInstance = new PlayerEngine(context.getApplicationContext());
        return sInstance;
    }

    private final Context app;
    private final AudioManager audio;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Random random = new Random();

    private MediaPlayer mp;
    private boolean prepared;
    private boolean preparing;
    private boolean playWhenReady;
    private boolean resumeOnFocusGain;
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

    private PlayerEngine(Context app) {
        this.app = app;
        this.audio = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
        // Pause when headphones are unplugged / bluetooth disconnects.
        app.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (isPlaying()) pause();
            }
        }, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
    }

    // ---------------------------------------------------------------- queries

    public Track current() {
        if (pos < 0 || pos >= order.size()) return null;
        return queue.get(order.get(pos));
    }

    public boolean isPlaying() {
        return mp != null && prepared && mp.isPlaying();
    }

    /** True while the user wants sound: playing, or buffering towards playing. */
    public boolean isActive() {
        return isPlaying() || (preparing && playWhenReady);
    }

    public boolean isBuffering() {
        return preparing;
    }

    public int position() {
        if (mp == null || !prepared) return 0;
        try {
            return mp.getCurrentPosition();
        } catch (IllegalStateException e) {
            return 0;
        }
    }

    public int duration() {
        if (mp != null && prepared) {
            try {
                int d = mp.getDuration();
                if (d > 0) return d;
            } catch (IllegalStateException ignored) {
            }
        }
        Track t = current();
        return t == null ? 0 : (int) t.durationMs;
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

    public void togglePlay() {
        if (isActive()) pause();
        else play();
    }

    public void play() {
        if (current() == null) return;
        if (mp == null) {
            if (preparing) { // still fetching the link; start as soon as it arrives
                playWhenReady = true;
                notifyChanged();
                return;
            }
            load(true);
            return;
        }
        playWhenReady = true;
        if (prepared && requestFocus()) {
            mp.start();
        }
        PlaybackService.ensureStarted(app);
        notifyChanged();
    }

    public void pause() {
        playWhenReady = false;
        resumeOnFocusGain = false;
        if (mp != null && prepared && mp.isPlaying()) mp.pause();
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
        if (mp != null && prepared) {
            mp.seekTo(Math.max(0, ms));
            notifyChanged();
        }
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
        abandonFocus();
        queue.clear();
        order.clear();
        pos = -1;
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
        releasePlayer();
        final int token = ++loadToken;
        final Track t = current();
        if (t == null) {
            notifyChanged();
            return;
        }
        playWhenReady = autoplay;
        if (autoplay) PlaybackService.ensureStarted(app);
        if (t.kind == Track.PREVIEW) {
            // Online song: get a fresh preview link first (they expire), then open it.
            preparing = true;
            notifyChanged();
            Net.POOL.execute(new Runnable() {
                @Override
                public void run() {
                    Exception err = null;
                    try {
                        Library.ensureStream(t);
                    } catch (Exception e) {
                        err = e;
                    }
                    final boolean ok = err == null && t.streamUrl != null;
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

    /** Creates the MediaPlayer for {@code t} and starts preparing it. */
    private void open(Track t) {
        mp = new MediaPlayer();
        mp.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build());
        mp.setWakeMode(app, PowerManager.PARTIAL_WAKE_LOCK);
        mp.setOnPreparedListener(this);
        mp.setOnCompletionListener(this);
        mp.setOnErrorListener(this);
        try {
            mp.setDataSource(app, Uri.parse(t.remote ? t.streamUrl : t.key));
            preparing = true;
            mp.prepareAsync();
        } catch (Exception e) {
            preparing = false;
            final MediaPlayer failed = mp;
            main.post(new Runnable() {
                @Override
                public void run() {
                    if (mp == failed) fail();
                }
            });
        }
        notifyChanged();
    }

    private void releasePlayer() {
        prepared = false;
        preparing = false;
        if (mp != null) {
            try {
                mp.reset();
                mp.release();
            } catch (Exception ignored) {
            }
            mp = null;
        }
    }

    @Override
    public void onPrepared(MediaPlayer player) {
        if (player != mp) return;
        prepared = true;
        preparing = false;
        consecutiveErrors = 0;
        Track t = current();
        if (t != null && player.getDuration() > 0) t.durationMs = player.getDuration();
        if (playWhenReady && requestFocus()) player.start();
        notifyChanged();
    }

    @Override
    public void onCompletion(MediaPlayer player) {
        if (player != mp) return;
        if (repeat == REPEAT_ONE) {
            player.seekTo(0);
            player.start();
            notifyChanged();
            return;
        }
        advance(false);
    }

    @Override
    public boolean onError(MediaPlayer player, int what, int extra) {
        if (player == mp) fail();
        return true;
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

    // ---------------------------------------------------------------- audio focus

    @SuppressWarnings("deprecation")
    private boolean requestFocus() {
        return audio.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
                == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    @SuppressWarnings("deprecation")
    private void abandonFocus() {
        audio.abandonAudioFocus(this);
    }

    @Override
    public void onAudioFocusChange(int change) {
        switch (change) {
            case AudioManager.AUDIOFOCUS_LOSS:
                pause();
                abandonFocus();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                boolean was = isPlaying();
                pause();
                resumeOnFocusGain = was;
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                if (mp != null) mp.setVolume(0.2f, 0.2f);
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                if (mp != null) mp.setVolume(1f, 1f);
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false;
                    play();
                }
                break;
            default:
                break;
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
        for (Listener l : listeners) l.onPlayerChanged();
    }
}

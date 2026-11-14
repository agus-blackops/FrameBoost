package com.agus.sonora;

import android.content.Context;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Metadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.extractor.metadata.icy.IcyInfo;

/**
 * {@link Audio} on top of Media3's ExoPlayer: streaming with buffering, HLS radios, redirects
 * between http and https, gapless playback, crossfades, audio focus (pauses for calls, lowers the
 * volume for navigation prompts) and pausing when headphones are unplugged.
 *
 * <p>Two players share one audio session (so the equalizer applies to both). The current one
 * holds the current item and, for a gapless change, the next one at index 1. For a crossfade the
 * next song waits, loaded, on the other player; it starts in the last seconds of the current song
 * while the volumes cross, and then the players swap roles.
 */
final class ExoAudio implements Audio, AudioManager.OnAudioFocusChangeListener {

    static final Factory FACTORY = new Factory() {
        @Override
        public Audio create(Context app, Events events) {
            return new ExoAudio(app, events);
        }
    };

    private final Events events;
    private final AudioManager audioManager;
    private final AudioFocusRequest focusRequest;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExoPlayer[] players = new ExoPlayer[2];
    private int cur;

    private boolean readyReported;
    private String liveTitle;
    private float speed = 1f;
    private boolean live;
    private boolean nextLive;
    /** Length of the crossfade into the waiting next song (0: gapless or nothing queued). */
    private long nextFadeMs;
    /** The waiting next song is on the other player (crossfade), not in the current playlist. */
    private boolean nextOnOther;

    /** A crossfade in progress: the player that is fading out, and when it began. */
    private int fadingOut = -1;
    private long fadeStartedAt;
    private long fadeMs;

    /** What the user (or the app) set, and what audio focus allows. */
    private float masterVolume = 1f;
    private float duck = 1f;
    private boolean wantPlay;
    private boolean hasFocus;
    private boolean resumeOnGain;

    ExoAudio(Context app, Events events) {
        this.events = events;
        this.audioManager = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
        this.focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(new android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                .setWillPauseWhenDucked(false)
                .setOnAudioFocusChangeListener(this, main)
                .build();
        for (int i = 0; i < 2; i++) {
            DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                    .setUserAgent("Sonora/2.0 (Android)")
                    .setAllowCrossProtocolRedirects(true)
                    .setConnectTimeoutMs(15000)
                    .setReadTimeoutMs(20000);
            players[i] = new ExoPlayer.Builder(app)
                    .setMediaSourceFactory(new DefaultMediaSourceFactory(new DefaultDataSource.Factory(app, http)))
                    // focus is handled here, once for both players
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(C.USAGE_MEDIA)
                            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                            .build(), false)
                    .setHandleAudioBecomingNoisy(true)
                    .setWakeMode(C.WAKE_MODE_NETWORK)
                    .build();
            players[i].addListener(new Listener(i));
        }
        players[1].setAudioSessionId(players[0].getAudioSessionId());
    }

    private ExoPlayer p() {
        return players[cur];
    }

    private ExoPlayer other() {
        return players[1 - cur];
    }

    private static MediaItem item(String uri) {
        return MediaItem.fromUri(Uri.parse(uri));
    }

    // ---------------------------------------------------------------- Audio

    @Override
    public void open(String uri, long startMs, boolean isLive) {
        stopFade();
        clearOther();
        live = isLive;
        readyReported = false;
        liveTitle = null;
        nextFadeMs = 0;
        p().setMediaItem(item(uri), isLive ? C.TIME_UNSET : Math.max(0, startMs));
        applySpeed();
        applyVolumes();
        p().prepare();
    }

    @Override
    public void setNext(String uri, boolean isLive, long fadeMillis) {
        while (p().getMediaItemCount() > 1) p().removeMediaItem(p().getMediaItemCount() - 1);
        clearOther();
        nextFadeMs = 0;
        if (uri == null || p().getMediaItemCount() != 1) return;
        nextLive = isLive;
        if (fadeMillis > 0) {
            nextFadeMs = fadeMillis;
            nextOnOther = true;
            ExoPlayer o = other();
            o.setMediaItem(item(uri));
            o.setPlaybackParameters(new PlaybackParameters(isLive ? 1f : speed));
            o.setVolume(0f);
            o.setPlayWhenReady(false);
            o.prepare();
            startTicker();
        } else {
            p().addMediaItem(item(uri));
        }
    }

    private void clearOther() {
        if (nextOnOther) {
            other().stop();
            other().clearMediaItems();
            nextOnOther = false;
        }
    }

    @Override
    public void setPlayWhenReady(boolean play) {
        wantPlay = play;
        if (play) {
            if (!requestFocus()) {
                wantPlay = false;
                return;
            }
            if (p().getPlaybackState() == Player.STATE_IDLE && p().getMediaItemCount() > 0) p().prepare();
        } else {
            resumeOnGain = false;
        }
        p().setPlayWhenReady(play);
        if (fadingOut >= 0) players[fadingOut].setPlayWhenReady(play);
        if (play) startTicker();
    }

    @Override
    public boolean playWhenReady() {
        return p().getPlayWhenReady();
    }

    @Override
    public boolean isPlaying() {
        return p().isPlaying();
    }

    @Override
    public boolean isBuffering() {
        return p().getPlaybackState() == Player.STATE_BUFFERING;
    }

    @Override
    public long position() {
        return Math.max(0, p().getCurrentPosition());
    }

    @Override
    public long duration() {
        long d = p().getDuration();
        return d == C.TIME_UNSET || live ? 0 : d;
    }

    @Override
    public void seekTo(long ms) {
        stopFade(); // a seek ends any crossfade: the old song stops right away
        p().seekTo(0, Math.max(0, ms));
    }

    @Override
    public void setSpeed(float s) {
        speed = s;
        applySpeed();
    }

    private void applySpeed() {
        p().setPlaybackParameters(new PlaybackParameters(live ? 1f : speed));
        if (nextOnOther) other().setPlaybackParameters(new PlaybackParameters(nextLive ? 1f : speed));
    }

    @Override
    public void setVolume(float volume) {
        masterVolume = volume;
        applyVolumes();
    }

    @Override
    public void setSkipSilence(boolean skip) {
        for (ExoPlayer x : players) x.setSkipSilenceEnabled(skip);
    }

    @Override
    public void stop() {
        stopFade();
        clearOther();
        p().stop();
        p().clearMediaItems();
        readyReported = false;
        liveTitle = null;
        wantPlay = false;
        abandonFocus();
    }

    @Override
    public int audioSessionId() {
        return players[0].getAudioSessionId();
    }

    @Override
    public String liveTitle() {
        return liveTitle;
    }

    // ---------------------------------------------------------------- crossfade

    private boolean ticking;

    /** Every 50 ms while sound plays: starts and drives crossfades. */
    private void startTicker() {
        if (ticking) return;
        ticking = true;
        main.post(new Runnable() {
            @Override
            public void run() {
                tick();
                if (fadingOut >= 0 || (nextOnOther && p().getPlayWhenReady())) {
                    main.postDelayed(this, 50);
                } else {
                    ticking = false;
                }
            }
        });
    }

    private void tick() {
        if (fadingOut >= 0) {
            long t = SystemClock.elapsedRealtime() - fadeStartedAt;
            if (t >= fadeMs) stopFade();
            else applyVolumes();
            return;
        }
        if (nextOnOther && p().isPlaying()
                && Crossfade.shouldStart(position(), duration(), nextFadeMs)
                && other().getPlaybackState() == Player.STATE_READY) {
            beginFade();
        }
    }

    /** The next song starts on the other player; the players swap roles right away. */
    private void beginFade() {
        fadingOut = cur;
        fadeMs = Math.max(1, Math.min(nextFadeMs, duration() - position()));
        fadeStartedAt = SystemClock.elapsedRealtime();
        cur = 1 - cur;
        nextOnOther = false;
        nextFadeMs = 0;
        live = nextLive;
        liveTitle = null;
        applyVolumes();
        p().setPlayWhenReady(true);
        startTicker();
        events.onAdvanced();
    }

    private void stopFade() {
        if (fadingOut < 0) return;
        ExoPlayer old = players[fadingOut];
        fadingOut = -1;
        old.stop();
        old.clearMediaItems();
        applyVolumes();
    }

    private void applyVolumes() {
        float base = masterVolume * duck;
        if (fadingOut >= 0) {
            long t = SystemClock.elapsedRealtime() - fadeStartedAt;
            players[fadingOut].setVolume(base * Crossfade.out(t, fadeMs));
            p().setVolume(base * Crossfade.in(t, fadeMs));
        } else {
            p().setVolume(base);
        }
    }

    // ---------------------------------------------------------------- audio focus

    private boolean requestFocus() {
        if (hasFocus) return true;
        int r = audioManager.requestAudioFocus(focusRequest);
        hasFocus = r == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        return hasFocus;
    }

    private void abandonFocus() {
        if (!hasFocus) return;
        audioManager.abandonAudioFocusRequest(focusRequest);
        hasFocus = false;
    }

    @Override
    public void onAudioFocusChange(int change) {
        switch (change) {
            case AudioManager.AUDIOFOCUS_LOSS: // another app plays for good: pause and let go
                hasFocus = false;
                resumeOnGain = false;
                pauseForFocus();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT: // a call: pause, resume afterwards
                resumeOnGain = wantPlay;
                pauseForFocus();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK: // a navigation prompt: lower
                duck = 0.2f;
                applyVolumes();
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                hasFocus = true;
                duck = 1f;
                applyVolumes();
                if (resumeOnGain) {
                    resumeOnGain = false;
                    setPlayWhenReady(true);
                }
                break;
            default:
                break;
        }
        events.onStateChanged();
    }

    private void pauseForFocus() {
        wantPlay = false;
        p().setPlayWhenReady(false);
        if (fadingOut >= 0) players[fadingOut].setPlayWhenReady(false);
    }

    // ---------------------------------------------------------------- player events

    /** The queued item became the current one: drop the finished item so the current stays at 0. */
    private void moveToNext() {
        while (p().getCurrentMediaItemIndex() > 0) p().removeMediaItem(0);
        live = nextLive;
        liveTitle = null;
        applySpeed();
        events.onAdvanced();
    }

    private final class Listener implements Player.Listener {
        final int index;

        Listener(int index) {
            this.index = index;
        }

        @Override
        public void onPlaybackStateChanged(int state) {
            if (index == fadingOut) {
                if (state == Player.STATE_ENDED) stopFade();
                return;
            }
            if (index != cur) return; // the next song loading on the other player
            if (state == Player.STATE_READY && !readyReported) {
                readyReported = true;
                events.onReady();
            } else if (state == Player.STATE_ENDED) {
                if (nextOnOther) {
                    // ended before the crossfade could start (a very short song, a slow network):
                    // carry on with the next song without a fade
                    fadeMs = 1;
                    beginFade();
                    stopFade();
                } else {
                    events.onEnded();
                }
            } else {
                events.onStateChanged();
            }
        }

        @Override
        public void onMediaItemTransition(MediaItem mediaItem, int reason) {
            if (index == cur && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && p().getCurrentMediaItemIndex() > 0) {
                moveToNext();
            }
        }

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            if (index == cur) events.onStateChanged();
        }

        @Override
        public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
            if (index != cur) return;
            if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY) {
                wantPlay = false;
                if (fadingOut >= 0) players[fadingOut].setPlayWhenReady(false);
            }
            events.onStateChanged();
        }

        @Override
        public void onPlayerError(PlaybackException error) {
            if (index == fadingOut) {
                stopFade();
                return;
            }
            if (index != cur) {
                // the waiting next song can't be loaded: drop it; it will be tried (and reported)
                // when its turn comes
                nextOnOther = false;
                nextFadeMs = 0;
                players[index].stop();
                players[index].clearMediaItems();
                return;
            }
            // an error in the preloaded item surfaces once playback has moved into it
            if (p().getCurrentMediaItemIndex() > 0) moveToNext();
            events.onError(error.getErrorCodeName());
        }

        @Override
        public void onMetadata(Metadata metadata) {
            if (index != cur) return;
            for (int i = 0; i < metadata.length(); i++) {
                Metadata.Entry e = metadata.get(i);
                if (e instanceof IcyInfo && ((IcyInfo) e).title != null) {
                    String t = ((IcyInfo) e).title.trim();
                    liveTitle = t.isEmpty() ? null : t;
                    events.onStateChanged();
                }
            }
        }
    }
}

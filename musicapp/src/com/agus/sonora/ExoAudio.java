package com.agus.sonora;

import android.content.Context;
import android.net.Uri;

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
 * between http and https, the next song preloaded for gapless playback, audio focus (pauses for
 * calls, lowers the volume for navigation prompts) and pausing when headphones are unplugged.
 *
 * <p>The player always holds the current item at index 0 and, at most, the next one at index 1.
 */
final class ExoAudio implements Audio, Player.Listener {

    static final Factory FACTORY = new Factory() {
        @Override
        public Audio create(Context app, Events events) {
            return new ExoAudio(app, events);
        }
    };

    private final ExoPlayer exo;
    private final Events events;
    private boolean readyReported;
    private String liveTitle;
    private float speed = 1f;
    private boolean live;
    private boolean nextLive;

    ExoAudio(Context app, Events events) {
        this.events = events;
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent("Sonora/1.9 (Android)")
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(20000);
        exo = new ExoPlayer.Builder(app)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(new DefaultDataSource.Factory(app, http)))
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(), true)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .build();
        exo.addListener(this);
    }

    private static MediaItem item(String uri) {
        return MediaItem.fromUri(Uri.parse(uri));
    }

    @Override
    public void open(String uri, long startMs, boolean live) {
        this.live = live;
        readyReported = false;
        liveTitle = null;
        exo.setMediaItem(item(uri), live ? C.TIME_UNSET : Math.max(0, startMs));
        applySpeed();
        exo.prepare();
    }

    @Override
    public void setNext(String uri, boolean live) {
        while (exo.getMediaItemCount() > 1) exo.removeMediaItem(exo.getMediaItemCount() - 1);
        if (uri != null && exo.getMediaItemCount() == 1) {
            nextLive = live;
            exo.addMediaItem(item(uri));
        }
    }

    @Override
    public void setPlayWhenReady(boolean play) {
        if (play && exo.getPlaybackState() == Player.STATE_IDLE && exo.getMediaItemCount() > 0) exo.prepare();
        exo.setPlayWhenReady(play);
    }

    @Override
    public boolean playWhenReady() {
        return exo.getPlayWhenReady();
    }

    @Override
    public boolean isPlaying() {
        return exo.isPlaying();
    }

    @Override
    public boolean isBuffering() {
        return exo.getPlaybackState() == Player.STATE_BUFFERING;
    }

    @Override
    public long position() {
        return Math.max(0, exo.getCurrentPosition());
    }

    @Override
    public long duration() {
        long d = exo.getDuration();
        return d == C.TIME_UNSET || live ? 0 : d;
    }

    @Override
    public void seekTo(long ms) {
        if (exo.getPlaybackState() == Player.STATE_ENDED) exo.setPlayWhenReady(exo.getPlayWhenReady());
        exo.seekTo(0, Math.max(0, ms));
    }

    @Override
    public void setSpeed(float s) {
        speed = s;
        applySpeed();
    }

    private void applySpeed() {
        exo.setPlaybackParameters(new PlaybackParameters(live ? 1f : speed));
    }

    @Override
    public void setVolume(float volume) {
        exo.setVolume(volume);
    }

    @Override
    public void stop() {
        exo.stop();
        exo.clearMediaItems();
        readyReported = false;
        liveTitle = null;
    }

    @Override
    public int audioSessionId() {
        return exo.getAudioSessionId();
    }

    @Override
    public String liveTitle() {
        return liveTitle;
    }

    /** The queued item became the current one: drop the finished item so the current stays at 0. */
    private void moveToNext() {
        while (exo.getCurrentMediaItemIndex() > 0) exo.removeMediaItem(0);
        live = nextLive;
        liveTitle = null;
        applySpeed();
        events.onAdvanced();
    }

    // ---------------------------------------------------------------- Player.Listener

    @Override
    public void onPlaybackStateChanged(int state) {
        if (state == Player.STATE_READY && !readyReported) {
            readyReported = true;
            events.onReady();
        } else if (state == Player.STATE_ENDED) {
            events.onEnded();
        } else {
            events.onStateChanged();
        }
    }

    @Override
    public void onMediaItemTransition(MediaItem mediaItem, int reason) {
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && exo.getCurrentMediaItemIndex() > 0) {
            moveToNext();
        }
    }

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
        events.onStateChanged();
    }

    @Override
    public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
        events.onStateChanged();
    }

    @Override
    public void onPlayerError(PlaybackException error) {
        // an error in the preloaded item surfaces once playback has moved into it
        if (exo.getCurrentMediaItemIndex() > 0) moveToNext();
        events.onError(error.getErrorCodeName());
    }

    @Override
    public void onMetadata(Metadata metadata) {
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

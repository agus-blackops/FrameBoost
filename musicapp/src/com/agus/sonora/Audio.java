package com.agus.sonora;

import android.content.Context;

/**
 * What {@link PlayerEngine} needs from an audio player. The app uses {@link ExoAudio} (Media3);
 * tests plug in a simulator through {@link PlayerEngine#audioFactory}.
 *
 * <p>An Audio holds the current item and, optionally, the next one already loaded so that the
 * change between songs has no gap. All methods are called on the main thread and every event is
 * delivered on the main thread.
 */
public interface Audio {

    interface Events {
        /** The current item finished loading and knows its duration (if it has one). */
        void onReady();

        /** The current item played to the end and no next item was queued. */
        void onEnded();

        /** Playback moved on its own into the queued next item, without a gap. */
        void onAdvanced();

        /** The current item can't be played. */
        void onError(String reason);

        /** Anything else worth redrawing: buffering, paused by another app, a radio's song title. */
        void onStateChanged();
    }

    interface Factory {
        Audio create(Context app, Events events);
    }

    /** Replaces everything with {@code uri}, starting at {@code startMs}; paused until {@link #setPlayWhenReady}. */
    void open(String uri, long startMs, boolean live);

    /** Loads {@code uri} to play right after the current item; null removes any queued item. */
    void setNext(String uri, boolean live);

    void setPlayWhenReady(boolean play);

    /** False if the user paused, or if another app took the audio focus for good. */
    boolean playWhenReady();

    boolean isPlaying();

    boolean isBuffering();

    long position();

    /** Duration of the current item in ms, or 0 when unknown (radios). */
    long duration();

    void seekTo(long ms);

    void setSpeed(float speed);

    void setVolume(float volume);

    /** Drops the current and next items. */
    void stop();

    /** Audio session for the equalizer and the other effects; 0 if there is none. */
    int audioSessionId();

    /** Song currently on air, from a radio's ICY metadata; null when unknown. */
    String liveTitle();
}

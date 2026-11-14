package com.agus.sonora;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.List;

/**
 * A deterministic stand-in for {@link ExoAudio} in Robolectric tests: an item "loads" 50 ms after
 * it is opened, time moves with the main looper's clock (idleFor), songs end on schedule, and a
 * queued next item takes over without a gap, like Media3 does.
 */
final class SimAudio implements Audio {

    /** Duration in ms for a link; negative means the link can't be played. */
    interface Durations {
        long of(String uri);
    }

    static Factory factory(final Durations durations, final List<String> opened) {
        return new Factory() {
            @Override
            public Audio create(Context app, Events events) {
                return new SimAudio(events, durations, opened);
            }
        };
    }

    static final int PREPARE_MS = 50;

    private final Events events;
    private final Durations durations;
    private final List<String> openedLog;
    private final Handler main = new Handler(Looper.getMainLooper());

    private String uri;
    private long dur;
    private boolean live;
    private boolean ready;
    private boolean ended;
    private boolean play;
    private float speed = 1f;
    /** Position at {@link #since} (uptime) when playing, or the frozen position when not. */
    private long base;
    private long since;
    private String next;
    private boolean nextLive;
    private int openToken;
    private int endToken;

    SimAudio(Events events, Durations durations, List<String> openedLog) {
        this.events = events;
        this.durations = durations;
        this.openedLog = openedLog;
    }

    private boolean running() {
        return ready && play && !ended;
    }

    private float rate() {
        return live ? 1f : speed;
    }

    @Override
    public void open(String u, long startMs, boolean isLive) {
        final int t = ++openToken;
        endToken++;
        uri = u;
        live = isLive;
        ready = false;
        ended = false;
        next = null;
        base = Math.max(0, startMs);
        openedLog.add(u);
        final long d = durations.of(u);
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (t != openToken) return;
                if (d < 0) {
                    events.onError("ERROR_CODE_IO_BAD_HTTP_STATUS");
                    return;
                }
                dur = live ? 0 : d;
                ready = true;
                since = SystemClock.uptimeMillis();
                schedule();
                events.onReady();
            }
        }, PREPARE_MS);
    }

    /** Arranges the end of the current item at the right (simulated) moment. */
    private void schedule() {
        final int t = ++endToken;
        if (!running() || live || dur <= 0) return;
        long left = (long) ((dur - position()) / rate());
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (t != endToken || !running()) return;
                if (next != null) {
                    String n = next;
                    next = null;
                    openedLog.add(n);
                    long d = durations.of(n);
                    uri = n;
                    live = nextLive;
                    dur = Math.max(0, d);
                    base = 0;
                    since = SystemClock.uptimeMillis();
                    events.onAdvanced();
                    if (d < 0) events.onError("ERROR_CODE_IO_BAD_HTTP_STATUS");
                    else schedule();
                } else {
                    base = dur;
                    ended = true;
                    events.onEnded();
                }
            }
        }, Math.max(0, left));
    }

    private void freeze() {
        base = position();
        since = SystemClock.uptimeMillis();
    }

    @Override
    public void setNext(String u, boolean isLive) {
        next = u;
        nextLive = isLive;
    }

    @Override
    public void setPlayWhenReady(boolean p) {
        freeze();
        play = p;
        schedule();
    }

    @Override
    public boolean playWhenReady() {
        return play;
    }

    @Override
    public boolean isPlaying() {
        return running();
    }

    @Override
    public boolean isBuffering() {
        return uri != null && !ready;
    }

    @Override
    public long position() {
        if (!running()) return base;
        long p = base + (long) ((SystemClock.uptimeMillis() - since) * rate());
        return dur > 0 ? Math.min(p, dur) : p;
    }

    @Override
    public long duration() {
        return ready ? dur : 0;
    }

    @Override
    public void seekTo(long ms) {
        base = dur > 0 ? Math.min(ms, dur) : ms;
        since = SystemClock.uptimeMillis();
        ended = false;
        schedule();
    }

    @Override
    public void setSpeed(float s) {
        freeze();
        speed = s;
        schedule();
    }

    @Override
    public void setVolume(float volume) {
    }

    @Override
    public void stop() {
        openToken++;
        endToken++;
        uri = null;
        next = null;
        ready = false;
        play = false;
    }

    @Override
    public int audioSessionId() {
        return 0;
    }

    @Override
    public String liveTitle() {
        return live ? "Canción al aire" : null;
    }
}

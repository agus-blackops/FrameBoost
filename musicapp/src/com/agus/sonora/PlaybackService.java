package com.agus.sonora;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/**
 * Keeps the app alive while music plays, and exposes playback to the system: the media
 * notification, the lock screen, headset buttons and Bluetooth controls (via MediaSession).
 */
public final class PlaybackService extends Service implements PlayerEngine.Listener {

    private static final String CHANNEL = "playback";
    private static final int NOTIFICATION_ID = 7;

    static volatile boolean running;

    static void ensureStarted(Context c) {
        if (running) return;
        Intent i = new Intent(c, PlaybackService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
        } catch (Exception ignored) {
            // Background-start restrictions: playback still works while the app is visible.
        }
    }

    private PlayerEngine engine;
    private MediaSession session;
    private NotificationManager nm;
    private final Handler main = new Handler(Looper.getMainLooper());
    private String artKey;
    private Bitmap art;

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        engine = PlayerEngine.get(this);
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Reproducción",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        session = new MediaSession(this, "Sonora");
        session.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                engine.play();
            }

            @Override
            public void onPause() {
                engine.pause();
            }

            @Override
            public void onSkipToNext() {
                engine.next();
            }

            @Override
            public void onSkipToPrevious() {
                engine.previous();
            }

            @Override
            public void onSeekTo(long pos) {
                engine.seekTo((int) pos);
            }

            @Override
            public void onStop() {
                engine.stop();
            }
        });
        setSessionFlags();
        session.setActive(true);
        engine.addListener(this);
        startForeground(NOTIFICATION_ID, build());
    }

    @SuppressWarnings("deprecation")
    private void setSessionFlags() {
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        running = true;
        startForeground(NOTIFICATION_ID, build());
        if (engine.current() == null) shutdown();
        return START_NOT_STICKY;
    }

    @Override
    public void onPlayerChanged() {
        if (engine.current() == null) {
            shutdown();
            return;
        }
        updateSession();
        nm.notify(NOTIFICATION_ID, build());
    }

    @SuppressWarnings("deprecation")
    private void shutdown() {
        running = false;
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // Swiped away from recents while paused: nothing to keep alive for.
        if (!engine.isActive()) engine.stop();
    }

    @Override
    public void onDestroy() {
        running = false;
        engine.removeListener(this);
        session.setActive(false);
        session.release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Bitmap artFor(final Track t) {
        if (t.key.equals(artKey) && art != null) return art;
        artKey = t.key;
        art = Covers.generated(t, 256);
        if (!t.remote) {
            final Context app = getApplicationContext();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final Bitmap b = Covers.loadSync(app, t, 512);
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            if (running && t.key.equals(artKey)) {
                                art = b;
                                onPlayerChanged();
                            }
                        }
                    });
                }
            }).start();
        }
        return art;
    }

    private void updateSession() {
        Track t = engine.current();
        if (t == null) return;
        session.setMetadata(new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, t.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, t.artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, t.album)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, engine.duration())
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, artFor(t))
                .build());
        int state = engine.isBuffering() && engine.isActive() ? PlaybackState.STATE_BUFFERING
                : engine.isPlaying() ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED;
        session.setPlaybackState(new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                        | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT
                        | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SEEK_TO
                        | PlaybackState.ACTION_STOP)
                .setState(state, engine.position(), engine.isPlaying() ? 1f : 0f)
                .build());
    }

    @SuppressWarnings("deprecation")
    private Notification build() {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        Intent open = new Intent(this, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        b.setSmallIcon(R.drawable.ic_note)
                .setContentIntent(PendingIntent.getActivity(this, 0, open, flags))
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setShowWhen(false)
                .setOnlyAlertOnce(true)
                .setOngoing(true);
        Track t = engine.current();
        if (t == null) {
            b.setContentTitle(getString(R.string.app_name)).setContentText("Preparando…");
            return b.build();
        }
        boolean active = engine.isActive();
        b.setContentTitle(t.title)
                .setContentText(t.artist)
                .setSubText(engine.queueName())
                .setLargeIcon(artFor(t))
                .addAction(R.drawable.ic_prev, "Anterior",
                        MediaActionReceiver.intent(this, MediaActionReceiver.PREV, 1))
                .addAction(active ? R.drawable.ic_pause : R.drawable.ic_play,
                        active ? "Pausa" : "Reproducir",
                        MediaActionReceiver.intent(this, MediaActionReceiver.PLAY_PAUSE, 2))
                .addAction(R.drawable.ic_next, "Siguiente",
                        MediaActionReceiver.intent(this, MediaActionReceiver.NEXT, 3))
                .addAction(R.drawable.ic_close, "Cerrar",
                        MediaActionReceiver.intent(this, MediaActionReceiver.CLOSE, 4))
                .setStyle(new Notification.MediaStyle()
                        .setMediaSession(session.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2));
        return b.build();
    }
}

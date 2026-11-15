package com.agus.sonora;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

/**
 * Keeps downloads going when you leave the app: Android lets a process run in the background
 * only while it shows what it's doing. Shows "Descargando …" with progress and a Cancelar button,
 * and goes away by itself when the queue is empty.
 */
public final class DownloadService extends Service implements Downloads.Listener {

    static final String CHANNEL = "downloads";
    static final int NOTIFICATION_ID = 8;
    static final String CANCEL = "com.agus.sonora.CANCEL_DOWNLOADS";

    static volatile boolean running;

    static void ensureStarted(Context c) {
        if (running) return;
        try {
            c.startForegroundService(new Intent(c, DownloadService.class));
        } catch (Exception ignored) {
            // not allowed right now (background start limits): downloads still run while the app is open
        }
    }

    private Downloads downloads;
    private NotificationManager nm;

    @Override
    public void onCreate() {
        super.onCreate();
        downloads = Downloads.get(this);
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Descargas", NotificationManager.IMPORTANCE_LOW));
        downloads.addListener(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        running = true;
        if (intent != null && CANCEL.equals(intent.getAction())) downloads.cancelPending();
        startForeground(NOTIFICATION_ID, build(downloads.percent(downloads.running())));
        stopIfDone();
        return START_NOT_STICKY;
    }

    @Override
    public void onDownloadsChanged() {
        if (stopIfDone()) return;
        nm.notify(NOTIFICATION_ID, build(downloads.percent(downloads.running())));
    }

    @Override
    public void onDownloadProgress(Track t, int percent) {
        if (running) nm.notify(NOTIFICATION_ID, build(percent));
    }

    /** Nothing left to download (or only waiting for Wi-Fi): leave. */
    private boolean stopIfDone() {
        if (downloads.active() > 0) return false;
        running = false;
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
        return true;
    }

    Notification build(int percent) {
        Track t = downloads.running();
        int left = downloads.active();
        Notification.Builder b = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_download)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setContentTitle(t == null ? "Preparando descargas…" : "Descargando · " + t.title)
                .setContentText(left <= 1 ? percent + " %" : percent + " % · quedan " + left)
                .setProgress(100, percent, t == null)
                .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT))
                .addAction(R.drawable.ic_close, "Cancelar", PendingIntent.getService(this, 1,
                        new Intent(this, DownloadService.class).setAction(CANCEL),
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        return b.build();
    }

    @Override
    public void onDestroy() {
        running = false;
        downloads.removeListener(this);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

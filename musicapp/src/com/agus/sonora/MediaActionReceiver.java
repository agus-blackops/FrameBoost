package com.agus.sonora;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Handles the buttons on the playback notification. */
public final class MediaActionReceiver extends BroadcastReceiver {

    static final String PLAY_PAUSE = "com.agus.sonora.PLAY_PAUSE";
    static final String NEXT = "com.agus.sonora.NEXT";
    static final String PREV = "com.agus.sonora.PREV";
    static final String CLOSE = "com.agus.sonora.CLOSE";

    static PendingIntent intent(Context c, String action, int requestCode) {
        Intent i = new Intent(c, MediaActionReceiver.class).setAction(action);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(c, requestCode, i, flags);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        PlayerEngine p = PlayerEngine.get(context);
        String a = intent.getAction();
        if (PLAY_PAUSE.equals(a)) p.togglePlay();
        else if (NEXT.equals(a)) p.next();
        else if (PREV.equals(a)) p.previous();
        else if (CLOSE.equals(a)) p.stop();
    }
}

package com.agus.sonora;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Every few hours, with a connection, Android runs this to look for new episodes of the podcasts
 * you follow and tells you with a notification that opens the podcast. The first look at a
 * podcast only takes note of what's there, so following one never floods you with its back
 * catalog.
 */
public final class PodcastCheckJob extends JobService {

    static final int JOB_ID = 4201;
    static final String CHANNEL = "episodes";
    static final long PERIOD_MS = 6 * 60 * 60 * 1000L;

    /** Turns the periodic check on or off to match the setting and whether you follow anything. */
    static void schedule(Context c) {
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        Library lib = Library.get(c);
        if (!enabled(c) || lib.followedPodcasts().isEmpty()) {
            js.cancel(JOB_ID);
            return;
        }
        if (js.getPendingJob(JOB_ID) != null) return;
        js.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(c, PodcastCheckJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(PERIOD_MS)
                .setPersisted(true)
                .build());
    }

    static boolean enabled(Context c) {
        return c.getSharedPreferences("podcastAlerts", MODE_PRIVATE).getBoolean("enabled", true);
    }

    static void setEnabled(Context c, boolean on) {
        c.getSharedPreferences("podcastAlerts", MODE_PRIVATE).edit().putBoolean("enabled", on).apply();
        schedule(c);
    }

    @Override
    public boolean onStartJob(final JobParameters params) {
        final Context app = getApplicationContext();
        Net.POOL.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    check(app);
                } finally {
                    jobFinished(params, false);
                }
            }
        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true; // try again later
    }

    /** One podcast with the episodes that came out since the last look. */
    static final class Found {
        final Library.Item podcast;
        final List<Track> episodes;

        Found(Library.Item podcast, List<Track> episodes) {
            this.podcast = podcast;
            this.episodes = episodes;
        }
    }

    /**
     * Background thread: reads every followed podcast's feed and notifies its new episodes.
     * Returns what was notified. Reads the followed list from storage, so it also works when the
     * app isn't open.
     */
    static List<Found> check(Context c) {
        SharedPreferences alerts = c.getSharedPreferences("podcastAlerts", MODE_PRIVATE);
        List<Found> out = new ArrayList<>();
        if (!alerts.getBoolean("enabled", true)) return out;
        List<Library.Item> podcasts = new ArrayList<>();
        try {
            JSONArray fo = new JSONArray(c.getSharedPreferences("library", MODE_PRIVATE).getString("followed", "[]"));
            for (int i = 0; i < fo.length(); i++) podcasts.add(Library.Item.fromJson(fo.getJSONObject(i)));
        } catch (Exception ignored) {
        }
        SharedPreferences.Editor edit = alerts.edit();
        for (Library.Item p : podcasts) {
            try {
                String feed = p.feed;
                if (feed == null) {
                    JSONArray r = new JSONObject(Net.getString(Library.APPLE_LOOKUP + p.id)).optJSONArray("results");
                    JSONObject first = r == null ? null : r.optJSONObject(0);
                    feed = first == null ? "" : first.optString("feedUrl", "");
                    if (feed.isEmpty()) continue;
                }
                List<Track> eps = Library.parseEpisodes(Net.getString(feed), p);
                long newest = Library.newestEpisode(eps);
                String k = "newest:" + p.key();
                if (!alerts.contains(k)) { // first look: just take note
                    edit.putLong(k, newest);
                    continue;
                }
                long before = alerts.getLong(k, 0);
                List<Track> fresh = new ArrayList<>();
                for (Track t : eps) if (t.dateAdded > before) fresh.add(t);
                if (fresh.isEmpty()) continue;
                edit.putLong(k, newest);
                out.add(new Found(p, fresh));
                notify(c, p, fresh);
            } catch (Exception ignored) {
                // one feed failing doesn't stop the others
            }
        }
        edit.apply();
        return out;
    }

    static void notify(Context c, Library.Item podcast, List<Track> fresh) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Episodios nuevos",
                NotificationManager.IMPORTANCE_DEFAULT));
        Intent open = new Intent(c, MainActivity.class)
                .putExtra(MainActivity.EXTRA_PODCAST, podcast.toJson().toString())
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, podcast.key().hashCode(), open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String title = fresh.size() == 1 ? "Nuevo episodio · " + podcast.title
                : fresh.size() + " episodios nuevos · " + podcast.title;
        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_podcast)
                .setContentTitle(title)
                .setContentText(fresh.get(0).title)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        try {
            nm.notify(podcast.key().hashCode(), n);
        } catch (SecurityException ignored) {
            // notifications not allowed
        }
    }
}

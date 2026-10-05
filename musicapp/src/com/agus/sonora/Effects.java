package com.agus.sonora;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.Virtualizer;

import org.json.JSONArray;

/**
 * Equalizer, bass boost and surround settings. The settings are plain data (persisted, usable
 * without hardware); {@link #attach} applies them to a MediaPlayer's audio session when the
 * device supports the effects.
 */
public final class Effects {

    /** What the device's equalizer offers. */
    public static final class Info {
        public final int bands;
        public final float minDb;
        public final float maxDb;
        public final int[] centerHz;

        public Info(int bands, float minDb, float maxDb, int[] centerHz) {
            this.bands = bands;
            this.minDb = minDb;
            this.maxDb = maxDb;
            this.centerHz = centerHz;
        }
    }

    /** Presets are curves in dB at 5 points from low to high frequencies, spread over any band count. */
    public static final String[] PRESET_NAMES = {
            "Plano", "Más graves", "Más agudos", "Rock", "Pop", "Jazz", "Clásica", "Electrónica", "Hip hop", "Voz"
    };
    static final float[][] PRESET_CURVES = {
            {0, 0, 0, 0, 0},
            {6, 4, 0, 0, 0},
            {0, 0, 0, 4, 6},
            {4, 2, -2, 3, 5},
            {-1, 2, 4, 2, -1},
            {4, 2, -2, 2, 4},
            {4, 3, -2, 3, 4},
            {5, 3, 0, 3, 5},
            {6, 4, 0, 1, 3},
            {-3, -1, 4, 3, -1},
    };

    private static Effects sInstance;
    private static Info sInfo;
    private static boolean sInfoQueried;

    public static synchronized Effects get(Context c) {
        if (sInstance == null) sInstance = new Effects(c.getApplicationContext());
        return sInstance;
    }

    private final Context app;
    private final SharedPreferences prefs;
    private boolean enabled;
    /** Index into PRESET_NAMES, or -1 when the user adjusted bands by hand. */
    private int preset;
    private float[] custom = new float[0];
    private int bass;
    private int surround;

    private Equalizer eq;
    private BassBoost bassBoost;
    private Virtualizer virtualizer;

    private Effects(Context app) {
        this.app = app;
        prefs = app.getSharedPreferences("effects", Context.MODE_PRIVATE);
        enabled = prefs.getBoolean("enabled", false);
        preset = prefs.getInt("preset", 0);
        bass = prefs.getInt("bass", 0);
        surround = prefs.getInt("surround", 0);
        try {
            JSONArray a = new JSONArray(prefs.getString("custom", "[]"));
            custom = new float[a.length()];
            for (int i = 0; i < custom.length; i++) custom[i] = (float) a.getDouble(i);
        } catch (Exception ignored) {
        }
    }

    // ---------------------------------------------------------------- device capabilities

    /** Asks the device what its equalizer can do (cached); null when unsupported. */
    public static synchronized Info info() {
        if (sInfoQueried) return sInfo;
        sInfoQueried = true;
        MediaPlayer probe = null;
        Equalizer e = null;
        try {
            probe = new MediaPlayer();
            e = new Equalizer(0, probe.getAudioSessionId());
            short bands = e.getNumberOfBands();
            short[] range = e.getBandLevelRange();
            int[] centers = new int[bands];
            for (short b = 0; b < bands; b++) centers[b] = e.getCenterFreq(b) / 1000;
            if (bands > 0) sInfo = new Info(bands, range[0] / 100f, range[1] / 100f, centers);
        } catch (Throwable t) {
            sInfo = null;
        } finally {
            try {
                if (e != null) e.release();
            } catch (Throwable ignored) {
            }
            try {
                if (probe != null) probe.release();
            } catch (Throwable ignored) {
            }
        }
        return sInfo;
    }

    /** For tests: new instance (re-reads prefs) but keep the pretended device info. */
    static synchronized void resetInstanceForTest() {
        sInstance = null;
    }

    /** For tests: forget the singleton and cached device info. */
    static synchronized void resetForTest() {
        sInstance = null;
        sInfo = null;
        sInfoQueried = false;
    }

    /** For tests: pretend the device offers this equalizer. */
    static synchronized void overrideInfo(Info info) {
        sInfo = info;
        sInfoQueried = true;
    }

    // ---------------------------------------------------------------- settings

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean on) {
        enabled = on;
        save();
        apply();
    }

    public int preset() {
        return preset;
    }

    public void setPreset(int index) {
        if (index < 0 || index >= PRESET_NAMES.length) return;
        preset = index;
        custom = new float[0];
        save();
        apply();
    }

    /** Level of every band in dB for the current preset / custom curve. */
    public float[] levelsDb() {
        Info info = info();
        int n = info == null ? 5 : info.bands;
        float[] out = new float[n];
        if (preset >= 0) {
            for (int i = 0; i < n; i++) out[i] = clamp(sample(PRESET_CURVES[preset], n, i));
        } else {
            for (int i = 0; i < n; i++) out[i] = i < custom.length ? clamp(custom[i]) : 0;
        }
        return out;
    }

    public void setBand(int band, float db) {
        float[] now = levelsDb();
        if (band < 0 || band >= now.length) return;
        now[band] = clamp(db);
        custom = now;
        preset = -1;
        save();
        apply();
    }

    public int bass() {
        return bass;
    }

    public void setBass(int strength) {
        bass = Math.max(0, Math.min(1000, strength));
        save();
        apply();
    }

    public int surround() {
        return surround;
    }

    public void setSurround(int strength) {
        surround = Math.max(0, Math.min(1000, strength));
        save();
        apply();
    }

    /** Linear interpolation of a 5-point curve over {@code n} bands. */
    static float sample(float[] curve, int n, int i) {
        if (n <= 1) return curve[curve.length / 2];
        float t = (float) i / (n - 1) * (curve.length - 1);
        int lo = (int) Math.floor(t);
        int hi = Math.min(curve.length - 1, lo + 1);
        return curve[lo] + (curve[hi] - curve[lo]) * (t - lo);
    }

    private float clamp(float db) {
        Info info = info();
        float min = info == null ? -12 : info.minDb;
        float max = info == null ? 12 : info.maxDb;
        return Math.max(min, Math.min(max, db));
    }

    private void save() {
        JSONArray a = new JSONArray();
        for (float f : custom) {
            try {
                a.put((double) f);
            } catch (Exception ignored) {
            }
        }
        prefs.edit().putBoolean("enabled", enabled).putInt("preset", preset)
                .putInt("bass", bass).putInt("surround", surround)
                .putString("custom", a.toString()).apply();
    }

    // ---------------------------------------------------------------- audio session

    /** Creates the effects for a new player's audio session. Never throws. */
    synchronized void attach(int sessionId) {
        detach();
        try {
            eq = new Equalizer(0, sessionId);
        } catch (Throwable t) {
            eq = null;
        }
        try {
            bassBoost = new BassBoost(0, sessionId);
        } catch (Throwable t) {
            bassBoost = null;
        }
        try {
            virtualizer = new Virtualizer(0, sessionId);
        } catch (Throwable t) {
            virtualizer = null;
        }
        apply();
    }

    synchronized void detach() {
        try {
            if (eq != null) eq.release();
        } catch (Throwable ignored) {
        }
        try {
            if (bassBoost != null) bassBoost.release();
        } catch (Throwable ignored) {
        }
        try {
            if (virtualizer != null) virtualizer.release();
        } catch (Throwable ignored) {
        }
        eq = null;
        bassBoost = null;
        virtualizer = null;
    }

    /** Pushes the current settings to the live effects (if any). */
    synchronized void apply() {
        try {
            if (eq != null) {
                eq.setEnabled(enabled);
                if (enabled) {
                    float[] db = levelsDb();
                    short bands = eq.getNumberOfBands();
                    for (short b = 0; b < bands && b < db.length; b++) {
                        eq.setBandLevel(b, (short) Math.round(db[b] * 100));
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            if (bassBoost != null) {
                bassBoost.setEnabled(enabled && bass > 0);
                if (bassBoost.getStrengthSupported()) bassBoost.setStrength((short) bass);
            }
        } catch (Throwable ignored) {
        }
        try {
            if (virtualizer != null) {
                virtualizer.setEnabled(enabled && surround > 0);
                if (virtualizer.getStrengthSupported()) virtualizer.setStrength((short) surround);
            }
        } catch (Throwable ignored) {
        }
    }
}

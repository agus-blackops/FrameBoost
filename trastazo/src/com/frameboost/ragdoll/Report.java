package com.frameboost.ragdoll;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Beta bug reports without any server: every uncaught error (and every error the game recovers from) is appended
 * to a text file the tester can send; the next launch tells the player a report exists.
 */
final class Report {
    private Report() { }

    static final String VERSION = "4.0";
    private static File dir;
    private static String platform = "?";
    /** Set at launch when the previous run ended with an error (shown on the title screen). */
    static String lastCrash;
    private static int logged;

    static File file() { return dir == null ? null : new File(dir, "trastazo-informe.txt"); }

    /** Call once at start-up. platform describes the device ("Android 14 Pixel 7", "Windows 11 Java 17"). */
    static void install(File folder, String platformInfo) {
        dir = folder;
        platform = platformInfo;
        lastCrash = null;
        try {
            File f = file();
            File crash = new File(dir, "trastazo-cierre.flag");
            if (crash.exists()) { lastCrash = f != null && f.exists() ? f.getPath() : "(sin informe)"; crash.delete(); }
        } catch (Throwable ignored) { }
        final Thread.UncaughtExceptionHandler old = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override public void uncaughtException(Thread t, Throwable e) {
                try {
                    write("CIERRE INESPERADO en el hilo " + t.getName(), e);
                    new File(dir, "trastazo-cierre.flag").createNewFile();
                } catch (Throwable ignored) { }
                if (old != null) old.uncaughtException(t, e);
            }
        });
    }

    /** An error the game survived (limited so a broken frame loop cannot fill the disk). */
    static void recovered(String what, Throwable e) {
        if (logged++ < 20) write("Error recuperado: " + what, e);
    }

    private static synchronized void write(String title, Throwable e) {
        File f = file();
        if (f == null) return;
        try {
            if (f.length() > 200 * 1024) f.delete();
            PrintWriter o = new PrintWriter(new FileWriter(f, true));
            o.println("=== " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + " · Trastazo " + VERSION + " · " + platform);
            o.println(title);
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            o.println(sw);
            o.close();
        } catch (Throwable ignored) { }
    }
}

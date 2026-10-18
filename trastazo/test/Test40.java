package com.frameboost.ragdoll;

import java.io.File;
import java.nio.file.Files;

/** 4.0 beta robustness: bug reports, error recovery, safe saves. */
public class Test40 {
    static int fails;
    static void check(String n, boolean ok) { System.out.println((ok ? "PASS " : "FAIL ") + n); if (!ok) fails++; }

    public static void main(String[] a) throws Exception {
        File dir = Files.createTempDirectory("trz40").toFile();
        // 1. an uncaught error leaves a report and the next launch knows about it
        Report.install(dir, "Test");
        check("a clean start has no crash note", Report.lastCrash == null);
        Thread.UncaughtExceptionHandler h = Thread.getDefaultUncaughtExceptionHandler();
        h.uncaughtException(Thread.currentThread(), new IllegalStateException("prueba de cierre"));
        File rep = Report.file();
        String txt = new String(Files.readAllBytes(rep.toPath()), "UTF-8");
        check("the report has version, platform and stack", txt.contains("Trastazo " + Report.VERSION) && txt.contains("Test") && txt.contains("prueba de cierre") && txt.contains("Test40.main"));
        Report.install(dir, "Test");
        check("the next launch is told about the crash", Report.lastCrash != null);
        Report.install(dir, "Test");
        check("and only once", Report.lastCrash == null);
        // 2. errors the game survives are logged and capped
        for (int i = 0; i < 40; i++) Report.recovered("prueba " + i, new RuntimeException("x" + i));
        String txt2 = new String(Files.readAllBytes(rep.toPath()), "UTF-8");
        int entries = txt2.split("Error recuperado").length - 1;
        check("recovered errors are logged but capped", entries >= 1 && entries <= 20);
        // 3. a bug inside the simulation does not kill the game
        Game g = Harness.newGame();
        g.world.spawn(Items.HUMAN, 1400, -120, 1, 1f);
        g.world.ents.add(new Ent() { @Override float cx() { return 0; } @Override float cy() { return 0; } });   // neither Person nor Prop: the world cannot update it
        boolean threw = false;
        try { for (int i = 0; i < 25; i++) g.update(1f / 60f); } catch (Throwable t) { threw = true; }
        check("an error in the world does not propagate", !threw);
        check("after repeated errors the scene is cleared and play continues", g.world.ents.isEmpty());
        g.world.spawn(Items.CRATE, 1400, -120, 1, 1f);
        try { for (int i = 0; i < 60; i++) g.update(1f / 60f); } catch (Throwable t) { threw = true; }
        check("the game keeps working afterwards", !threw && g.world.ents.size() == 1);
        // 4. saves are written safely
        g.screens.screen = Screens.NONE;
        File sf = new File(dir, "save.trz");
        g.saveFile = sf;
        g.saveScene();
        check("the save exists and no temporary file is left", sf.exists() && !new File(dir, "save.trz.tmp").exists());
        g.world.spawn(Items.BARREL, 1600, -100, 1, 1f);
        g.saveScene();
        Game g2 = Harness.newGame();
        check("a second save replaces the first and loads", SceneIO.load(g2, sf) && g2.world.ents.size() == 2);
        // 5. loose guns are never drawn upside down
        World w = new World(null);
        Prop gun = (Prop) w.spawn(Items.GUN_RIFLE, 1500, -100, 1, 1f);
        gun.main().setAngle(3.1f);
        Renderer r = new Renderer();
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(400, 300, java.awt.image.BufferedImage.TYPE_INT_RGB);
        try { r.draw(new CanvasDraw(new android.graphics.Canvas(img)), w, 1500, -100, 1f, 400, 300); check("a gun pointing left draws", true); } catch (Throwable t) { check("a gun pointing left draws", false); }
        // 6. the top buttons never hide behind the open catalog, on any screen
        int[][] sizes = {{800, 480}, {960, 540}, {1024, 768}, {1280, 720}, {1920, 1080}, {2400, 1080}, {2560, 1600}};
        float[] dens = {1.5f, 1.7f, 2f, 2f, 2.7f, 2.75f, 3f};
        boolean fit = true;
        for (int i = 0; i < sizes.length; i++) {
            Game q = new Game(null);
            q.resize(sizes[i][0], sizes[i][1], dens[i]);
            q.ui.stripOpen = true;
            q.ui.placeButtons();
            float left = 1e9f;
            for (int b = 0; b < Ui.BTN_COUNT; b++) { left = Math.min(left, q.ui.btnLeft(b)); }
            if (left < q.ui.leftW() - 0.5f) { fit = false; System.out.println("   overlap at " + sizes[i][0] + "x" + sizes[i][1] + ": " + left + " < " + q.ui.leftW()); }
        }
        check("buttons clear the catalog panel at every size", fit);
        System.out.println(fails == 0 ? "T40 ALL PASS" : "T40 FAILURES " + fails);
    }
}

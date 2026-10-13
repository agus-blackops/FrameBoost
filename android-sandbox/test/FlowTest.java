package com.frameboost.ragdoll;

import java.io.File;

public class FlowTest {
    static String out;
    static void check(String n, boolean ok) { System.out.println((ok ? "PASS " : "FAIL ") + n); }
    static void tap(Game g, float x, float y) { g.touch(Game.DOWN, 0, x, y); g.update(1f/60); g.touch(Game.UP, 0, x, y); g.update(1f/60); }
    static float sx(Game g, float wx) { return (wx - g.camX) * g.zoom + g.sw / 2f; }
    static float sy(Game g, float wy) { return (wy - g.camY) * g.zoom + g.sh / 2f; }
    static void shoot(Game g, Body b) { tap(g, sx(g, b.cx), sy(g, b.cy)); }

    static int play(Game g, int ch, float maxT) {
        float t = 0;
        while (g.chResult == 0 && t < maxT) { Harness.run(g, 0.1f); t += 0.1f; }
        return g.chResult;
    }

    public static void main(String[] a) throws Exception {
        out = a[0];
        Game g = new Game(null, new File("/tmp/rpg-mods"));
        g.resize(Harness.W, Harness.H, Harness.DENS);
        g.saveFile = new File("/tmp/rpg-autosave.trz"); g.saveFile.delete();
        check("starts with the splash cards", g.screens.screen == Screens.SPLASH);
        Harness.run(g, 0.9f); Harness.shot(g, out + "/splash_opengl.png");
        Harness.run(g, 1.9f); Harness.shot(g, out + "/splash_opus.png");
        Harness.run(g, 1.6f);
        Harness.shot(g, out + "/title.png");
        check("starts on title", g.screens.screen == Screens.TITLE);
        // title buttons: find "Sandbox libre" by trying the menu action directly via tap at its layout
        g.menuAction(Screens.A_SANDBOX);
        check("tutorial shown on first sandbox", g.screens.screen == Screens.TUTORIAL);
        Harness.run(g, 0.5f);
        Harness.shot(g, out + "/tutorial.png");
        for (int i = 0; i < 4; i++) tap(g, g.sw / 2f, g.ui.topH + 60 * g.density);
        check("tutorial finished", g.screens.screen == Screens.NONE && g.settings.tutorialDone);

        // save / load: dismember a human, pin a crate
        g.spawn(Tools.HUMAN, 900, -100); g.spawn(Tools.CRATE, 1500, -100); g.spawn(Tools.ZOMBIE, 2000, -100);
        Harness.run(g, 2f);
        Body h = null; for (Body b : g.world.bodies) if (b.isHuman() && b.kind == 0) h = b;
        g.world.breakStick(h.jShF); g.world.breakStick(h.jKneeB);
        Harness.run(g, 0.5f);
        int bodies = g.world.bodies.size();
        float hx = h.head.x, hy = h.head.y;
        g.saveScene();
        check("autosave written", g.saveFile.exists());
        g.menuAction(Screens.A_MENU);
        check("back to title, continue available", g.screens.screen == Screens.TITLE && g.hasSave());
        g.menuAction(Screens.A_CONTINUE);
        Body h2 = null; for (Body b : g.world.bodies) if (b.isHuman() && b.kind == 0) h2 = b;
        check("load restores bodies (" + bodies + " -> " + g.world.bodies.size() + ")", g.world.bodies.size() == bodies);
        check("load restores positions + broken joints", h2 != null && Math.abs(h2.head.x - hx) < 1 && Math.abs(h2.head.y - hy) < 1 && h2.jShF.dead && h2.jKneeB.dead && !h2.jElF.dead);
        Harness.run(g, 1f);
        Harness.shot(g, out + "/loaded.png");

        // pause + settings
        g.menuAction(Screens.A_SETTINGS);
        Harness.run(g, 0.1f);
        Harness.shot(g, out + "/settings.png");
        g.menuAction(Screens.A_BACK);
        g.screens.screen = Screens.PAUSE; Harness.run(g, 0.1f); Harness.shot(g, out + "/pause.png");
        g.menuAction(Screens.A_MENU);
        g.menuAction(Screens.A_CHALLENGES); Harness.run(g, 0.1f); Harness.shot(g, out + "/challenges.png");

        // --- challenges, played through the real touch input
        // 0 Derribo: throw bowling balls at the tower
        g.menuAction(Screens.A_CHAL + 0); g.ui.stripOpen = false;
        Harness.run(g, 1.5f);
        check("derribo not won at start", g.chResult == 0);
        g.tool = Tools.BOWLING;
        for (int k = 0; k < 6 && g.chResult == 0; k++) { tap(g, sx(g, 1540 + (k % 2) * 30), sy(g, -420)); Harness.run(g, 1.5f); }
        // nudge the rest with the hand
        int r0 = play(g, 0, 8f);
        System.out.println("  derribo result=" + r0 + " t=" + g.chTime + " " + g.chProgress);
        Harness.run(g, 1.5f); Harness.shot(g, out + "/result.png");

        // 2 Fruteria with the pistol
        g.menuAction(Screens.A_CHAL + 2); g.ui.stripOpen = false; Harness.run(g, 1f);
        g.tool = Tools.PISTOL;
        for (int k = 0; k < 14 && g.chResult == 0; k++) {
            Body m = null; for (Body b : g.world.bodies) if (b.type == Body.MELON) { m = b; break; }
            if (m == null) break;
            shoot(g, m); Harness.run(g, 0.2f);
        }
        System.out.println("  fruteria result=" + play(g, 2, 3f) + " uses left=" + g.chUses + " " + g.chProgress);
        check("fruteria winnable", g.chResult == 1);

        // 4 Punteria
        g.menuAction(Screens.A_CHAL + 4); g.ui.stripOpen = false; Harness.run(g, 1f);
        for (int k = 0; k < 9 && g.chResult == 0; k++) {
            Body m = null; for (Body b : g.world.bodies) if (b.type == Body.BALLOON) { m = b; break; }
            if (m == null) break;
            shoot(g, m); Harness.run(g, 0.2f);
        }
        System.out.println("  punteria result=" + play(g, 4, 3f) + " " + g.chProgress);
        check("punteria winnable", g.chResult == 1);
        // wasting all shots loses
        g.menuAction(Screens.A_RETRY); g.ui.stripOpen = false; Harness.run(g, 1f);
        for (int k = 0; k < 10; k++) tap(g, 300, 900);
        System.out.println("  punteria (missing) result=" + play(g, 4, 5f));
        check("running out of shots fails", g.chResult == -1);

        // 3 chain reaction: one detonation at the first barrel
        g.menuAction(Screens.A_CHAL + 3); g.ui.stripOpen = false; Harness.run(g, 3f);
        tap(g, sx(g, 650), sy(g, -40));
        System.out.println("  cadena result=" + play(g, 3, 9f) + " " + g.chProgress);
        Harness.shot(g, out + "/chain.png");

        // 1 zombies: headshots
        g.menuAction(Screens.A_CHAL + 1); g.ui.stripOpen = false; Harness.run(g, 2f);
        g.tool = Tools.PISTOL;
        for (int k = 0; k < 30 && g.chResult == 0; k++) {
            Body z = null; for (Body b : g.world.bodies) if (b.isHuman() && b.kind == Body.K_ZOMBIE && !b.dead) { z = b; break; }
            if (z == null) break;
            tap(g, sx(g, z.head.x), sy(g, z.head.y)); Harness.run(g, 0.3f);
        }
        System.out.println("  zombis result=" + play(g, 1, 3f) + " " + g.chProgress);
        check("zombie challenge winnable", g.chResult == 1);

        // 5 survival without doing anything should fail (zombies arrive)
        g.menuAction(Screens.A_CHAL + 5); g.ui.stripOpen = false;
        System.out.println("  supervivencia (idle) result=" + play(g, 5, 40f) + " t=" + g.chTime + " " + g.chProgress);
        Harness.shot(g, out + "/survival.png");
        System.out.println("stars=" + g.settings.stars);
    }
}

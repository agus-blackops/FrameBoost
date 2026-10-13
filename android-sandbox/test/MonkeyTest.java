package com.frameboost.ragdoll;

import android.graphics.Canvas;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;

/** Random play: thousands of actions; any exception or NaN is a bug. */
public class MonkeyTest {
    public static void main(String[] a) throws Exception {
        int seeds = a.length > 0 ? Integer.parseInt(a[0]) : 6;
        BufferedImage img = new BufferedImage(Harness.W, Harness.H, BufferedImage.TYPE_INT_RGB);
        Canvas c = new Canvas(img);
        int fails = 0;
        for (int seed = 1; seed <= seeds; seed++) {
            Random r = new Random(seed);
            Game g = new Game(null, new File("/tmp/rpg-mods"));
            g.resize(Harness.W, Harness.H, Harness.DENS);
            g.saveFile = new File("/tmp/rpg-monkey.trz");
            String last = "";
            try {
                for (int step = 0; step < 1500; step++) {
                    int act = r.nextInt(100);
                    float x = r.nextFloat() * g.sw, y = r.nextFloat() * g.sh;
                    if (act < 30) {                 // tap / drag with a random pointer
                        int id = r.nextInt(3);
                        last = "touch " + id;
                        g.touch(Game.DOWN, id, x, y);
                        int moves = r.nextInt(6);
                        for (int k = 0; k < moves; k++) { g.touch(Game.MOVE, id, x + r.nextFloat() * 300 - 150, y + r.nextFloat() * 300 - 150); g.update(1f / 60); }
                        g.touch(r.nextInt(10) == 0 ? Game.CANCEL : Game.UP, id, x, y);
                    } else if (act < 45) {          // pick a random tool / item
                        Tools.Item[] cat = Tools.CATS[r.nextInt(6)];
                        if (cat.length > 0) { g.tool = cat[r.nextInt(cat.length)].id; last = "tool " + g.tool; }
                        if (g.screens.screen != Screens.NONE && r.nextBoolean()) g.menuAction(Screens.A_SANDBOX);
                    } else if (act < 55) {          // spawn something directly in the world
                        int[] ids = Tools.KEY_IDS;
                        int id = ids[r.nextInt(ids.length)];
                        last = "spawn " + id;
                        g.spawn(id, 100 + r.nextFloat() * 2400, -900 + r.nextFloat() * 880);
                    } else if (act < 60) {          // menu flow
                        int[] menus = {Screens.A_CONTINUE, Screens.A_SANDBOX, Screens.A_CHALLENGES, Screens.A_SETTINGS, Screens.A_RESUME,
                                Screens.A_MENU, Screens.A_RESTART, Screens.A_BACK, Screens.A_MUSIC_DN, Screens.A_GORE, Screens.A_QUALITY,
                                Screens.A_CHAL + r.nextInt(6), Screens.A_NEXT, Screens.A_RETRY};
                        int m = menus[r.nextInt(menus.length)];
                        last = "menu " + m;
                        if (m == Screens.A_RESTART || m == Screens.A_RETRY || m == Screens.A_NEXT) { if (g.challenge < 0) continue; }
                        g.menuAction(m);
                    } else if (act < 63) {          // toggle a mod
                        Mod m = g.mods.mods.get(r.nextInt(g.mods.mods.size()));
                        last = "mod " + m.name;
                        g.mods.toggle(m, g);
                    } else if (act < 65) {
                        last = "save/load";
                        g.saveScene();
                        if (r.nextBoolean()) SceneIO.load(g, g.saveFile);
                    } else if (act < 67) {
                        last = "explode";
                        g.world.explode(r.nextFloat() * 2600, -r.nextFloat() * 800, 100 + r.nextFloat() * 600, 6000, 100);
                    } else {
                        int frames = 1 + r.nextInt(20);
                        last = "run " + frames;
                        for (int k = 0; k < frames; k++) g.update(1f / 60);
                    }
                    if (step % 25 == 0) { last = "draw"; g.draw(c); }
                    for (int i = 0; i < g.world.ps.size(); i++) {
                        Particle p = g.world.ps.get(i);
                        if (Float.isNaN(p.x) || Float.isNaN(p.y) || Float.isInfinite(p.x)) throw new IllegalStateException("NaN particle in " + p.body.type);
                    }
                }
                System.out.printf("seed %d ok: bodies=%d screen=%d challenge=%d%n", seed, g.world.bodies.size(), g.screens.screen, g.challenge);
            } catch (Throwable t) {
                fails++;
                System.out.println("seed " + seed + " FAIL after '" + last + "': " + t);
                StackTraceElement[] st = t.getStackTrace();
                for (int i = 0; i < Math.min(8, st.length); i++) System.out.println("    at " + st[i]);
            }
        }
        System.out.println(fails == 0 ? "MONKEY PASS" : "MONKEY FAILURES: " + fails);
    }
}

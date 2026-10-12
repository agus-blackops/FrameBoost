package com.frameboost.ragdoll;

import android.graphics.Canvas;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Desktop test harness: runs the real game code against a Java2D canvas and writes PNG frames. */
public class Harness {
    static final int W = 2400, H = 1080;
    static final float DENS = 2.75f;

    static Game newGame() {
        Game g = new Game(null);
        g.resize(W, H, DENS);
        g.world.clear();
        g.ui.stripOpen = false;
        return g;
    }

    static void run(Game g, float seconds) {
        int n = (int) (seconds * 60);
        for (int i = 0; i < n; i++) g.update(1f / 60f);
    }

    static void shot(Game g, String name) throws Exception {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Canvas c = new Canvas(img);
        g.draw(c);
        ImageIO.write(img, "png", new File(name));
        System.out.println("wrote " + name);
    }

    static float maxStretch(World w) {
        float m = 0;
        for (Stick s : w.ss) {
            if (s.dead) continue;
            float dx = s.b.x - s.a.x, dy = s.b.y - s.a.y;
            float e = (float) Math.abs(Math.sqrt(dx * dx + dy * dy) - s.rest);
            if (e > m) m = e;
        }
        return m;
    }

    static int deadJoints(World w) {
        int n = 0;
        for (Body b : w.bodies) for (Stick s : b.ss) if (s.dead) n++;
        return n;
    }

    public static void main(String[] a) throws Exception {
        String sc = a.length > 0 ? a[0] : "all";
        String out = a.length > 1 ? a[1] : ".";
        new File(out).mkdirs();

        if (sc.equals("human") || sc.equals("all")) {
            Game g = newGame();
            Body h = Spawner.human(g.world, 1300, -150, 1f, 1);
            g.world.add(h);
            for (int i = 0; i < 6; i++) {
                run(g, 0.5f);
                System.out.printf("t=%.1f pelvis=(%.0f,%.0f) head=(%.0f,%.0f) maxStretch=%.2f deadJoints=%d%n", (i + 1) * 0.5, h.pelvis.x, h.pelvis.y, h.head.x, h.head.y, maxStretch(g.world), deadJoints(g.world));
                if (i == 0) shot(g, out + "/human_fall.png");
            }
            shot(g, out + "/human_rest.png");
        }
        if (sc.equals("crates") || sc.equals("all")) {
            Game g = newGame();
            for (int i = 0; i < 4; i++) g.world.add(Spawner.crate(1300, -100 - i * 90, 60));
            run(g, 4f);
            for (Body b : g.world.bodies) System.out.printf("crate cy=%.1f cx=%.1f%n", b.cy, b.cx);
            shot(g, out + "/crates.png");
        }
        if (sc.equals("scene") || sc.equals("all")) {
            Game g = newGame();
            g.spawn(Tools.HUMAN, 700, -100); g.spawn(Tools.HUMAN, 900, -100); g.spawn(Tools.GIANT, 1100, -200);
            g.spawn(Tools.KID, 1300, -100); g.spawn(Tools.CROWD, 1700, -100);
            g.spawn(Tools.CRATE, 1500, -300); g.spawn(Tools.BARREL, 1850, -200); g.spawn(Tools.BALL, 500, -200);
            g.spawn(Tools.BALLOON, 600, -200); g.spawn(Tools.TNT, 2100, -100); g.spawn(Tools.PLANK, 1000, -400);
            g.spawn(Tools.STEEL, 1300, -500); g.spawn(Tools.KNIFE, 400, -50); g.spawn(Tools.SWORD, 300, -50);
            g.spawn(Tools.DYNAMITE, 800, -300); g.spawn(Tools.GRENADE, 2300, -100);
            g.ui.stripOpen = true; g.ui.cat = 2;
            long t0 = System.nanoTime();
            run(g, 3f);
            long t1 = System.nanoTime();
            System.out.printf("scene: %d bodies %d particles %d sticks; %.2f ms/frame avg; maxStretch=%.2f%n",
                    g.world.bodies.size(), g.world.ps.size(), g.world.ss.size(), (t1 - t0) / 1e6 / 180, maxStretch(g.world));
            shot(g, out + "/scene.png");
        }
        if (sc.equals("explode") || sc.equals("all")) {
            Game g = newGame();
            g.spawn(Tools.CROWD, 1300, -100);
            g.spawn(Tools.CRATE, 1100, -100);
            g.spawn(Tools.CRATE, 1500, -100);
            run(g, 2f);
            g.world.explode(1300, -40, 380f, 6000f, 100f);
            run(g, 0.15f);
            shot(g, out + "/explode_a.png");
            run(g, 0.5f);
            shot(g, out + "/explode_b.png");
            run(g, 2f);
            shot(g, out + "/explode_c.png");
            int dead = 0; for (Body b : g.world.bodies) if (b.isHuman() && b.dead) dead++;
            System.out.println("dead humans: " + dead + " deadJoints=" + deadJoints(g.world));
        }
        if (sc.equals("shoot") || sc.equals("all")) {
            Game g = newGame();
            g.spawn(Tools.HUMAN, 1300, -100);
            run(g, 2f);
            Body h = g.world.bodies.get(0);
            g.world.bullet(h.head.x - 300, h.head.y, 1, 0, 22, 900, 1);
            run(g, 0.2f);
            System.out.println("after headshot dead=" + h.dead + " blood=" + h.blood);
            shot(g, out + "/shoot.png");
        }
        if (sc.equals("stress") || sc.equals("all")) {
            Game g = newGame();
            for (int i = 0; i < 30; i++) g.spawn(Tools.HUMAN, 200 + (i % 15) * 150, -100 - (i / 15) * 250);
            for (int i = 0; i < 40; i++) g.spawn(Tools.CRATE, 250 + (i % 20) * 110, -700 - (i / 20) * 100);
            long t0 = System.nanoTime();
            run(g, 5f);
            long t1 = System.nanoTime();
            System.out.printf("stress: %d bodies %d particles %d sticks; %.2f ms/frame avg; maxStretch=%.2f%n",
                    g.world.bodies.size(), g.world.ps.size(), g.world.ss.size(), (t1 - t0) / 1e6 / 300, maxStretch(g.world));
            shot(g, out + "/stress.png");
        }
    }
}

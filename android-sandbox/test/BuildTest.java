package com.frameboost.ragdoll;

import java.io.File;

public class BuildTest {
    static void check(String n, boolean ok) { System.out.println((ok ? "PASS " : "FAIL ") + n); }
    static void tap(Game g, float wx, float wy) {
        float x = (wx - g.camX) * g.zoom + g.sw / 2f, y = (wy - g.camY) * g.zoom + g.sh / 2f;
        g.touch(Game.DOWN, 0, x, y); g.update(1f / 60); g.touch(Game.UP, 0, x, y); g.update(1f / 60);
    }

    public static void main(String[] a) throws Exception {
        String out = a[0];
        Game g = Harness.newGame();
        g.settings = new Settings();
        g.saveFile = new File("/tmp/rpg-build.trz");
        // --- car: plank + two motor wheels on axles
        Body plank = Spawner.plank(700, -120, 260, false); g.world.add(plank);
        Body w1 = Spawner.motorWheel(600, -60), w2 = Spawner.motorWheel(800, -60);
        g.world.add(w1); g.world.add(w2);
        Harness.run(g, 0.05f);
        g.tool = Tools.HINGE;
        tap(g, 600, -60); tap(g, 600, -115);     // wheel centre -> plank
        tap(g, 800, -60); tap(g, 800, -115);
        int axles = 0; for (Stick s : plank.ss) if (s.weld) axles++; for (Stick s : w1.ss) if (s.weld) axles++; for (Stick s : w2.ss) if (s.weld) axles++;
        check("two axles created (" + axles + " sticks)", axles == 4);
        float x0 = plank.cx;
        Harness.run(g, 3f);
        System.out.printf("  car moved %.0f units, plank y=%.0f%n", plank.cx - x0, plank.cy);
        check("motor car drives forward", plank.cx - x0 > 250);
        Harness.shot(g, out + "/build_car.png");

        // --- rope from an anchor in the air holding a crate, then cut it
        g.world.clear(); g.resetCamera(); Harness.run(g, 0.1f);
        g.tool = Tools.ROPE;
        Body crate = Spawner.crate(1300, -150, 60); g.world.add(crate); Harness.run(g, 0.05f);
        crate.ps.get(1).setPinned(false);
        tap(g, 1300, -330);                       // empty space -> anchor
        tap(g, 1300, -180);                       // crate
        crate.ps.get(0).py = crate.ps.get(0).y;
        Harness.run(g, 2f);
        System.out.printf("  hanging crate y=%.0f%n", crate.cy);
        check("rope holds the crate in the air", crate.cy < -110);
        Harness.shot(g, out + "/build_rope.png");

        // save / load keeps rope + axles
        g.saveScene();
        int bodies = g.world.bodies.size();
        g.world.clear();
        check("scene with links reloads", SceneIO.load(g, g.saveFile) && g.world.bodies.size() == bodies);
        Harness.run(g, 1.5f);
        Body crate2 = null; for (Body b : g.world.bodies) if (b.type == Body.CRATE) crate2 = b;
        check("crate still hanging after reload (y=" + (int) crate2.cy + ")", crate2.cy < -110);

        g.tool = Tools.CUT;
        System.out.println("  before cut: screen=" + g.screens.screen + " paused=" + g.paused + " challenge=" + g.challenge + " cam=" + g.camX + "," + g.camY + " zoom=" + g.zoom);
        float sx = (1250 - g.camX) * g.zoom + g.sw / 2f, sy = (-260 - g.camY) * g.zoom + g.sh / 2f;
        g.touch(Game.DOWN, 0, sx, sy);
        for (int i = 1; i <= 10; i++) { g.touch(Game.MOVE, 0, sx + i * 12 * g.zoom, sy); g.update(1f / 60); }
        g.touch(Game.UP, 0, sx + 120, sy);
        Harness.run(g, 1.5f);
        check("cutting the rope drops the crate (y=" + (int) crate2.cy + ")", crate2.cy > -60);

        // --- thruster welded to a crate, then activated
        g.world.clear(); g.resetCamera(); Harness.run(g, 0.1f);
        Body box = Spawner.crate(1300, -40, 60); g.world.add(box);
        Body th = Spawner.thruster(1300, -100); g.world.add(th);
        Harness.run(g, 0.05f);
        // rotate thruster to point up: nozzle low, nose high
        th.ps.get(0).x = th.ps.get(0).px = 1300; th.ps.get(0).y = th.ps.get(0).py = -76;
        th.ps.get(1).x = th.ps.get(1).px = 1300; th.ps.get(1).y = th.ps.get(1).py = -108;
        Particle top = null; for (Particle q : box.ps) if (top == null || q.y < top.y - 1 || (Math.abs(q.y - top.y) < 1 && Math.abs(q.x - 1300) < Math.abs(top.x - 1300))) top = q;
        g.world.link(th.ps.get(0), top, true);
        g.tool = Tools.ACTIVATE;
        tap(g, 1300, -92);
        check("thruster switched on", th.thrust);
        Harness.run(g, 1.2f);
        System.out.printf("  rocket box y=%.0f%n", box.cy);
        check("thruster lifts the welded crate", box.cy < -250);
        Harness.shot(g, out + "/build_rocket.png");

        // achievements
        g.update(0.3f); g.update(0.3f);
        System.out.println("  achievements: " + g.settings.ach);
        check("Ingeniero + Despegue unlocked", g.achievements.has(g.settings, 6) && g.achievements.has(g.settings, 7));

        // --- engineering challenge: a motor wheel pushing the crate
        Game c = Harness.newGame();
        c.settings = new Settings();
        c.menuAction(Screens.A_CHAL + 6);
        Harness.run(c, 0.5f);
        c.tool = Tools.MOTOR;
        tap(c, 200, -60);
        c.tool = Tools.ACTIVATE; tap(c, 200, -60);
        float t = 0;
        while (c.chResult == 0 && t < 40) { Harness.run(c, 0.2f); t += 0.2f; }
        System.out.println("  ingenieria result=" + c.chResult + " t=" + c.chTime + " " + c.chProgress);
        check("engineering challenge solvable", c.chResult == 1);
    }
}

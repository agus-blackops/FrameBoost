package com.frameboost.ragdoll;

import java.util.ArrayList;

/** New destruction: bullet holes, rebar in broken concrete, chips, metal that tears, craters of dust and the demolition crane. */
public class Test26 {
    static int fails;
    static void check(String n, boolean ok) { System.out.println((ok ? "PASS " : "FAIL ") + n); if (!ok) fails++; }
    static void run(World w, float secs) { int n = (int) (secs * 60); for (int i = 0; i < n; i++) w.update(1f / 60f); }

    public static void main(String[] a) throws Exception {
        String out = a.length > 0 ? a[0] : "/tmp";
        Game.edition = "Unity 2.8";

        // 1. bullet holes
        World w = new World(null);
        Prop crate = (Prop) w.spawn(Items.BIGCRATE, 1500, -60, 1, 1f);
        run(w, 1f);
        w.punch(crate, crate.cx() - 20f, crate.cy() - 10f, 1f, 0f);
        w.punch(crate, crate.cx() + 15f, crate.cy() + 12f, 1f, 0f);
        int holes = 0; for (int i = 0; i < crate.ncr; i++) if (crate.crS[i] >= 2f) holes++;
        check("bullets leave holes", holes == 2);
        WorldTest.shot(w, out + "/u_holes.png", 1500, -80, 3f);

        // 2. concrete breaks into chunks with rebar; steel tears in few pieces
        w = new World(null);
        w.buildAt(Items.BUILDING, 1500, 0);
        run(w, 1f);
        w.explode(1324, -100, 150, 3000, 60, false);
        run(w, 2f);
        int rebar = 0; for (Ent e : w.ents) if (e instanceof Prop && ((Prop) e).rebar) rebar++;
        System.out.printf("   concrete chunks with rebar: %d%n", rebar);
        check("broken concrete shows rebar", rebar > 3);
        WorldTest.shot(w, out + "/u_rebar.png", 1330, -120, 2.2f);
        World ws = new World(null);
        Prop beam = Prop.block(1500, -100, 70f, 8f, Build.STEEL, 1f, false); beam.register(ws.sim); ws.add(beam);
        int e0 = ws.ents.size();
        beam.impX = 1500; beam.impY = -100;
        ws.fracture(beam, 1500, -100);
        int pieces = 0; for (Ent e : ws.ents) if (((Prop) e).frag) pieces++;
        check("a steel beam tears into few pieces", pieces >= 1 && pieces <= 3);

        // 3. chips fly off damaged pieces that do not break yet
        w = new World(null);
        Prop wall = Prop.block(1500, -60, 14f, 60f, Build.BRICK, 1f, false); wall.register(w.sim); w.add(wall);
        wall.impX = 1500; wall.impY = -60;
        int before = w.fx.active();
        w.wear(wall, 20f, false);
        check("a hit that does not break it chips it", !wall.removed && w.fx.active() > before);

        // 4. big blasts raise a cloud of dust at the ground
        w = new World(null);
        int sm = w.fx.active();
        w.explode(1500, -20, 220, 3000, 100, false);
        check("a blast on the ground raises dust", w.fx.active() > sm + 10);

        // 5. the demolition crane: the ball swings and knocks a tower down
        w = new World(null);
        ArrayList<Ent> cr = w.buildAt(Items.CRANE, 1200, 0);
        ArrayList<Ent> tw = w.buildAt(Items.TOWER, 1200 + 235 + 150, 0);
        int towerBricks = 0; for (Ent e : tw) if (!((Prop) e).removed) towerBricks++;
        Prop ball = null; for (Ent e : cr) if (e instanceof Prop && ((Prop) e).type == Items.BOWLING) ball = (Prop) e;
        check("the crane has its ball", ball != null && ball.hw == 34f);
        WorldTest.shot(w, out + "/u_crane.png", 1450, -180, 0.8f);
        float maxX = 0f;
        for (int i = 0; i < 60 * 12; i++) { w.update(1f / 60f); maxX = Math.max(maxX, ball.cx()); }
        int left = 0; for (Ent e : tw) if (!((Prop) e).removed && !((Prop) e).frag) left++;
        System.out.printf("   crane: ball reached x=%.0f, tower bricks %d -> %d, destroyed %d%n", maxX, towerBricks, left, w.stats.destroyed);
        check("the ball swings across", maxX > 1440f);
        check("the cable holds", !ball.removed && w.sim.joints.size() > 5);
        check("the ball wrecks the tower", w.stats.destroyed >= 5 || left < towerBricks);
        WorldTest.shot(w, out + "/u_crane_after.png", 1600, -150, 0.8f);

        // 6. palette
        Game g = Harness.newGame();
        g.ui.stripOpen = true; g.ui.cat = Items.CAT_BUILD;
        Harness.run(g, 0.5f);
        Harness.shot(g, out + "/u_dest3_palette.png");
        check("the crane is in Construir", Items.isBuild(Items.CRANE));
        System.out.println(fails == 0 ? "T26 ALL PASS" : "T26 FAILURES " + fails);
    }
}

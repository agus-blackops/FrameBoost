package com.frameboost.ragdoll;

import java.util.ArrayList;

public class BuildTest {
    static int fails;
    static void check(String n, boolean ok) { System.out.println((ok ? "PASS " : "FAIL ") + n); if (!ok) fails++; }
    static int broken(World w) { int n = 0; for (Joint j : w.sim.joints) if (j.tag == Build.TAG) n++; return n; }
    static int welds(World w) { return w.sim.joints.size() - 0; }
    static int tagged(World w) { int n = 0; for (Joint j : w.sim.joints) if (j.tag == Build.TAG) n++; return n; }
    static void run(World w, float s) { int n = (int) (s * 60); for (int i = 0; i < n; i++) w.update(1f / 60f); }

    static ArrayList<Ent> place(World w, int item, float x) { return w.buildAt(item, x, 0); }

    public static void main(String[] a) throws Exception {
        String out = a[0];
        // 1. every construction stands on its own for 8 seconds
        int[] items = {Items.HOUSE, Items.BRIDGE, Items.TOWER, Items.WALL};
        for (int it : items) {
            World w = new World(null);
            ArrayList<Ent> pieces = place(w, it, 1200);
            int before = tagged(w);
            run(w, 8f);
            int after = tagged(w);
            System.out.printf("   %s: %d pieces, welds %d -> %d, destroyed=%d%n", Items.find(it).name, pieces.size(), before, after, w.stats.destroyed);
            check(Items.find(it).name + " stands under its own weight", after == before && w.stats.destroyed == 0);
        }
        // car drives on its own
        World cw = new World(null);
        ArrayList<Ent> car = place(cw, Items.CAR, 600);
        float x0 = ((Prop) car.get(0)).main().x;
        run(cw, 5f);
        float moved = ((Prop) car.get(0)).main().x - x0;
        System.out.println("   car moved " + (int) moved);
        check("car drives on its own", moved > 250f);

        // 2. a blast breaks a house apart
        World w = new World(null);
        place(w, Items.HOUSE, 1200);
        run(w, 2f);
        int before = tagged(w);
        w.explode(1200, -60, 200, 4000, 40, false);
        run(w, 4f);
        System.out.printf("   house after blast: welds %d -> %d, destroyed=%d%n", before, tagged(w), w.stats.destroyed);
        check("blast breaks the house", w.stats.destroyed > 0);

        // 3. a wrecking ball (fast crate) knocks a tower down
        World t = new World(null);
        place(t, Items.TOWER, 1200);
        run(t, 2f);
        t.explode(1200, -30, 260, 9000, 60, false);
        run(t, 4f);
        System.out.printf("   tower after blast: destroyed=%d%n", t.stats.destroyed);
        check("a blast breaks a brick tower", t.stats.destroyed > 0);

        // 4. undo removes every piece and its welds
        World u = new World(null);
        ArrayList<Ent> h = place(u, Items.HOUSE, 1200);
        for (Ent e : h) u.remove(e);
        check("removing a construction removes its welds", tagged(u) == 0 && u.ents.isEmpty());

        // 5. save / load keeps the construction
        World s = new World(null);
        place(s, Items.BRIDGE, 1200);
        run(s, 1f);
        int joints = tagged(s);
        java.io.File f = new java.io.File(out, "build_save.txt");
        Game g = Harness.newGame();
        g.world.buildAt(Items.BRIDGE, 1200, 0);
        SceneIO.save(g, f);
        Game l = Harness.newGame();
        boolean ok = SceneIO.load(l, f);
        int joints2 = tagged(l.world);
        System.out.printf("   saved %d welds, loaded %d welds%n", joints, joints2);
        check("saved construction loads back", ok && joints2 == joints && joints > 0);
        System.out.println(fails == 0 ? "BUILD ALL PASS" : "BUILD FAILURES " + fails);
    }
}

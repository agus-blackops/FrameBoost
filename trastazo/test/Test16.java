package com.frameboost.ragdoll;

/** Five new constructions: pyramid, castle, greenhouse, dominoes, scaffold. */
public class Test16 {
    static int fails;
    static void check(String n, boolean ok) { System.out.println((ok ? "PASS " : "FAIL ") + n); if (!ok) fails++; }
    static void run(World w, float s) { int n = (int) (s * 60); for (int i = 0; i < n; i++) w.update(1f / 60f); }
    static int tagged(World w) { int n = 0; for (Joint j : w.sim.joints) if (j.tag == Build.TAG) n++; return n; }

    public static void main(String[] a) throws Exception {
        String out = a.length > 0 ? a[0] : "/tmp";
        WorldTest.rend.theme = Renderer.SANDBOX;
        int[] items = {Items.PYRAMID, Items.CASTLE, Items.GREENHOUSE, Items.SCAFFOLD};
        String[] names = {"pyramid", "castle", "greenhouse", "scaffold"};
        for (int k = 0; k < items.length; k++) {
            World bw = new World(null);
            java.util.ArrayList<Ent> pieces = bw.buildAt(items[k], 1200, 0);
            int before = tagged(bw);
            run(bw, 8f);
            int after = tagged(bw);
            System.out.printf("   %s: %d pieces, welds %d -> %d, destroyed=%d%n", names[k], pieces.size(), before, after, bw.stats.destroyed);
            check(names[k] + " is built and stands alone for 8 s", pieces.size() >= 6 && after == before && bw.stats.destroyed == 0);
            WorldTest.shot(bw, out + "/b_" + names[k] + ".png", 1200, -110, 1.5f);
            // a blast brings it down
            bw.explode(1200, bw.floorY() - 60f, 330f, 2400f, 80f, false);
            run(bw, 4f);
            check(names[k] + " collapses under a blast", tagged(bw) < before && bw.stats.destroyed > 0);
        }
        // dominoes: the first one falls on the next and the chain goes all the way
        World w = new World(null);
        java.util.ArrayList<Ent> row = w.buildAt(Items.DOMINOES, 1500, 0);
        run(w, 10f);
        Prop last = (Prop) row.get(row.size() - 1);
        System.out.printf("   dominoes: %d, last tilted %.2f rad%n", row.size(), last.main().a);
        check("dominoes: 14 pieces", row.size() == 14);
        check("the chain reaches the last domino", Math.abs(last.main().a) > 1.0f);
        // the greenhouse has glass, and glass breaks into sharp shards
        w = new World(null);
        w.buildAt(Items.GREENHOUSE, 1500, 0);
        run(w, 2f);
        int glass = 0; for (Ent e : w.ents) if (e instanceof Prop && ((Prop) e).color == Build.GLASS) glass++;
        w.explode(1500, w.floorY() - 70f, 300f, 2200f, 70f, false);
        run(w, 2f);
        int sharp = 0; for (Ent e : w.ents) if (e instanceof Prop && ((Prop) e).color == Build.GLASS && ((Prop) e).sharp) sharp++;
        System.out.printf("   greenhouse: %d panes -> %d sharp shards%n", glass, sharp);
        check("the greenhouse has glass panes that shatter into shards", glass == 4 && sharp > 10);
        // all of them fit in the palette and in a saved scene
        check("seven new entries in Construir", Items.CATS[Items.CAT_BUILD].length == 12);
        Game g = Harness.newGame();
        g.world.buildAt(Items.CASTLE, 1300, 0);
        g.world.buildAt(Items.PYRAMID, 1800, 0);
        Harness.run(g, 2f);
        java.io.File f = new java.io.File("/tmp/trz16.trz");
        SceneIO.save(g, f);
        Game g2 = Harness.newGame();
        check("a saved castle and pyramid load back", SceneIO.load(g2, f) && g2.world.ents.size() == g.world.ents.size() && tagged(g2.world) == tagged(g.world));
        g = Harness.newGame();
        g.ui.stripOpen = true; g.ui.cat = Items.CAT_BUILD;
        g.world.buildAt(Items.CASTLE, 1500, 0);
        g.world.buildAt(Items.DOMINOES, 1950, 0);
        Harness.run(g, 3f);
        Harness.shot(g, out + "/b_palette.png");
        System.out.println(fails == 0 ? "T16 ALL PASS" : "T16 FAILURES " + fails);
    }
}

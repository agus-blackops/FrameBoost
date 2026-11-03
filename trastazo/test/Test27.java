package com.frameboost.ragdoll;

import java.util.ArrayList;

/** Better destruction: fire brings wood down, chunks trail dust, damaged walls get bitten, more rubble kept. */
public class Test27 {
    static int fails;
    static void check(String n, boolean ok) { System.out.println((ok ? "PASS " : "FAIL ") + n); if (!ok) fails++; }
    static void run(World w, float secs) { int n = (int) (secs * 60); for (int i = 0; i < n; i++) w.update(1f / 60f); }

    public static void main(String[] a) throws Exception {
        String out = a.length > 0 ? a[0] : "/tmp";
        Game.edition = "Unity 2.9";

        // 1. fire burns wood through: a crate falls apart, a house collapses
        World w = new World(null);
        Prop crate = (Prop) w.spawn(Items.CRATE, 1500, -40, 1, 1f);
        run(w, 1f);
        w.ignite(crate);
        run(w, 10f);
        check("a burning crate burns through and breaks", crate.removed && w.stats.broken > 0);
        w = new World(null);
        ArrayList<Ent> house = w.buildAt(Items.HOUSE, 1500, 0);
        run(w, 2f);
        int joints = 0; for (Joint j : w.sim.joints) if (j.tag == Build.TAG) joints++;
        for (Ent e : house) w.ignite(e);
        run(w, 3f);
        WorldTest.shot(w, out + "/u_burn_house.png", 1500, -90, 1.4f);
        run(w, 22f);
        int joints2 = 0; for (Joint j : w.sim.joints) if (j.tag == Build.TAG) joints2++;
        int left = 0; for (Ent e : house) if (!((Prop) e).removed) left++;
        System.out.printf("   house: welds %d -> %d, pieces %d -> %d, broken %d%n", joints, joints2, house.size(), left, w.stats.broken);
        check("fire brings the wooden house down", left < house.size() && w.stats.broken >= 2);
        WorldTest.shot(w, out + "/u_burn_after.png", 1500, -90, 1.4f);

        // 2. a building piece that is stressed shows bites; chunks of a building trail dust while they fall
        w = new World(null);
        w.buildAt(Items.BUILDING, 1500, 0);
        run(w, 1f);
        Prop wall = null;
        for (Ent e : w.ents) { Prop q = (Prop) e; if (q.isStruct && !q.anchor && q.hw < 10f && q.cy() > -100f && q.cx() < 1400f) { wall = q; break; } }
        wall.wear = World.toughness(wall) * 0.8f;
        WorldTest.shot(w, out + "/u_bites.png", wall.cx(), wall.cy(), 3.5f);
        check("the wall is damaged but still stands", !wall.removed && wall.wear > 0f);
        w.explode(1500, -330, 200, 3500, 90, false);
        int base = w.fx.active();
        int peak = 0;
        for (int i = 0; i < 120; i++) { w.update(1f / 60f); peak = Math.max(peak, w.fx.active()); }
        check("falling chunks trail dust", peak > base + 20);
        int trailing = 0; for (Ent e : w.ents) if (((Prop) e).trail) trailing++;
        check("chunks of a building are marked to trail dust", trailing > 0);

        // 3. more rubble is kept than before
        check("more rubble is kept on the floor", World.MAX_CHUNKS >= 150);

        // 4. chipped chunks draw (jagged outlines)
        w = new World(null);
        Prop slab = Prop.block(1500, -60, 60f, 12f, Build.CONCRETE, 1f, false); slab.register(w.sim); w.add(slab);
        slab.impX = 1500; slab.impY = -60;
        w.fracture(slab, 1500, -60);
        run(w, 1f);
        WorldTest.shot(w, out + "/u_jagged.png", 1500, -50, 3f);
        check("a broken slab leaves chunks", w.stats.broken > 0);
        System.out.println(fails == 0 ? "T27 ALL PASS" : "T27 FAILURES " + fails);
    }
}

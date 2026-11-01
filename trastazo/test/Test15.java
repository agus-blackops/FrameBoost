package com.frameboost.ragdoll;

/** Destruction Engine 1.5: glass, chips, scorched ground, structural collapse. */
public class Test15 {
    static int fails;
    static void check(String n, boolean ok) { System.out.println((ok ? "PASS " : "FAIL ") + n); if (!ok) fails++; }
    static void run(World w, float secs) { int n = (int) (secs * 60); for (int i = 0; i < n; i++) w.update(1f / 60f); }

    public static void main(String[] a) throws Exception {
        String out = a.length > 0 ? a[0] : "/tmp";
        // 1. glass: spawns, breaks at a gentle hit into many sharp shards
        World w = new World(null);
        Prop g = (Prop) w.spawn(Items.GLASS, 1500, -60, 1, 1f);
        run(w, 1f);
        check("glass is breakable and spawnable", World.breakable(g) && Items.isSpawn(Items.GLASS) && Items.find(Items.GLASS).name.equals("Cristal"));
        w.crack(g, g.cx() - 4f, g.cy() - 20f, 0.8f);
        WorldTest.shot(w, out + "/e_glass_crack.png", 1500, -80, 2.4f);
        w.fracture(g, g.cx() - 4f, g.cy() - 20f);
        int shards = 0, sharp = 0;
        for (Ent e : w.ents) if (e instanceof Prop && ((Prop) e).color == Build.GLASS) { shards++; if (((Prop) e).sharp) sharp++; }
        System.out.printf("   glass: %d shards, %d sharp%n", shards, sharp);
        check("glass shatters into many sharp shards", shards >= 8 && sharp == shards && w.stats.glass == 1);
        run(w, 0.25f);
        WorldTest.shot(w, out + "/e_glass_burst.png", 1500, -80, 2.4f);
        // 2. shards cut: a pane thrown against a person leaves cuts
        w = new World(null);
        Person p = (Person) w.spawn(Items.HUMAN, 1560, -120, -1, 1f);
        Prop pane = (Prop) w.spawn(Items.GLASS, 1420, -90, 1, 1f);
        run(w, 1f);
        pane.main().vx = 1400f;
        float hp0 = 0f; for (float h : p.hp) hp0 += h;
        for (int i = 0; i < 60 * 3; i++) w.update(1f / 60f);
        float hp1 = 0f; for (float h : p.hp) hp1 += h;
        System.out.printf("   pane thrown: person lost %.0f hp, wounds %d, panes left %d%n", hp0 - hp1, p.nw, countGlass(w));
        check("a pane thrown at a person breaks and hurts", hp0 - hp1 > 5f || p.nw > 0);
        // 3. a bullet cracks, the second shatters
        w = new World(null);
        Prop g2 = (Prop) w.spawn(Items.GLASS, 1500, -60, 1, 1f);
        run(w, 0.8f);
        w.shoot(null, 1300, g2.cy(), 1, 0, 16f, 520f, 1);
        run(w, 0.3f);
        boolean crackedFirst = g2.ncr > 0 || g2.removed;
        for (int k = 0; k < 3 && !g2.removed; k++) { w.shoot(null, 1300, g2.cy(), 1, 0, 16f, 520f, 1); run(w, 0.3f); }
        check("bullets crack glass and then shatter it", crackedFirst && g2.removed);
        // 4. chips fly off a hit crate
        w = new World(null);
        Prop crate = (Prop) w.spawn(Items.BIGCRATE, 1500, -60, 1, 1f);
        run(w, 1f);
        int before = 0; for (int i = 0; i < Fx.MAXP; i++) if (w.fx.life[i] > 0f) before++;
        w.crack(crate, crate.cx(), crate.cy() - 30f, 0.7f);
        int after = 0; for (int i = 0; i < Fx.MAXP; i++) if (w.fx.life[i] > 0f) after++;
        check("a hard hit chips splinters off", after > before);
        // 5. explosions burn the ground
        w = new World(null);
        int s0 = w.fx.sn;
        w.explode(1500, w.floorY() - 40f, 250f, 1500f, 50f, false);
        check("blasts leave scorch marks on the floor", w.fx.sn >= s0 + 2);
        // 6. structural collapse: losing joints weakens and cracks the neighbours
        w = new World(null);
        w.buildAt(Items.HOUSE, 1500, 0);
        run(w, 3f);
        int pieces0 = w.ents.size();
        w.explode(1500, w.floorY() - 90f, 330f, 2600f, 80f, false);
        run(w, 4f);
        int cracked = 0;
        for (Ent e : w.ents) if (e instanceof Prop && ((Prop) e).ncr > 0) cracked++;
        System.out.printf("   house: %d -> %d pieces, collapses %d, cracked %d%n", pieces0, w.ents.size(), w.stats.collapses, cracked);
        check("the house collapses into more, cracked pieces", w.stats.collapses > 0 && w.ents.size() > pieces0 && cracked > 0);
        WorldTest.shot(w, out + "/e_collapse.png", 1500, -80, 1.2f);
        // 7. saving keeps glass shards sharp
        Game gm = Harness.newGame();
        gm.world.spawn(Items.GLASS, 1500, -60, 1, 1f);
        Harness.run(gm, 0.8f);
        for (Ent e : new java.util.ArrayList<Ent>(gm.world.ents)) if (e instanceof Prop && ((Prop) e).type == Items.GLASS) gm.world.fracture((Prop) e, e.cx(), e.cy());
        Harness.run(gm, 0.3f);
        java.io.File f = new java.io.File("/tmp/trz15.trz");
        SceneIO.save(gm, f);
        Game g3 = Harness.newGame();
        boolean ok = SceneIO.load(g3, f);
        int sh = 0, sp = 0;
        for (Ent e : g3.world.ents) if (e instanceof Prop && ((Prop) e).color == Build.GLASS) { sh++; if (((Prop) e).sharp) sp++; }
        check("glass shards survive save and load, still sharp", ok && sh >= 6 && sp == sh);
        // 8. the palette shows glass, the scene draws
        Game gp = Harness.newGame();
        gp.ui.stripOpen = true; gp.ui.cat = Items.CAT_OBJECTS;
        gp.world.spawn(Items.GLASS, 1500, -60, 1, 1f);
        gp.world.spawn(Items.GLASS, 1700, -60, 1, 1f);
        Harness.run(gp, 1f);
        Harness.shot(gp, out + "/e_palette.png");
        System.out.println(fails == 0 ? "T15 ALL PASS" : "T15 FAILURES " + fails);
    }

    static int countGlass(World w) { int n = 0; for (Ent e : w.ents) if (e instanceof Prop && ((Prop) e).color == Build.GLASS) n++; return n; }
}

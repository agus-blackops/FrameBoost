package com.frameboost.ragdoll;

/** Rust bar and the Rust Man (infection, claws, chain infection), plus the V3 human model. */
public class Test19 {
    static int fails;
    static void check(String n, boolean ok) { System.out.println((ok ? "PASS " : "FAIL ") + n); if (!ok) fails++; }
    static void run(World w, float s) { int n = (int) (s * 60); for (int i = 0; i < n; i++) w.update(1f / 60f); }
    static int count(World w, int kind) { int n = 0; for (Ent e : w.ents) if (e instanceof Person && ((Person) e).kind == kind && !((Person) e).dead) n++; return n; }

    public static void main(String[] a) throws Exception {
        String out = a.length > 0 ? a[0] : "/tmp";
        WorldTest.rend.theme = Renderer.SANDBOX;
        // 1. touching the bar infects, and the infection ends in a Rust Man
        World w = new World(null);
        Prop bar = (Prop) w.spawn(Items.RUSTBAR, 1500, -40, 1, 1f);
        Person p = (Person) w.spawn(Items.HUMAN, 1400, -120, 1, 1f);
        run(w, 1f);
        check("the rust bar is a machine in the palette", bar.fixedMachine() && Items.find(Items.RUSTBAR).name.equals("Barra de óxido"));
        float t0 = 0f;
        for (int i = 0; i < 60 * 8; i++) { p.walk = p.rustT < 0f && p.kind == Person.HUMAN ? 1 : 0; w.update(1f / 60f); if (p.rustT >= 0f && t0 == 0f) { t0 = p.rustT; } }
        System.out.printf("   infected at rustT=%.1f, kind now %d, hp chest %.0f%n", t0, p.kind, p.hp[Person.CHEST]);
        check("touching the bar starts the infection", t0 > 6f);
        check("after a few seconds the person is a Rust Man", p.kind == Person.RUSTMAN && p.item == Items.RUSTMAN && w.stats.rusted == 1);
        check("the Rust Man is reborn at full health", p.hp[Person.CHEST] >= Person.maxHp(p, Person.CHEST) * 0.9f && !p.dead && p.hostile());
        WorldTest.shot(w, out + "/r_bar.png", 1480, -120, 2.4f);
        // 2. an infected person shows rust spreading before the change
        World w2 = new World(null);
        Person q = (Person) w2.spawn(Items.HUMAN, 1500, -120, 1, 1f);
        run(w2, 1f);
        w2.infectRust(q);
        run(w2, 4f);
        System.out.printf("   mid infection rustT=%.1f%n", q.rustT);
        check("halfway through, they are still a person with rust on them", q.rustT > 0f && q.kind == Person.HUMAN);
        WorldTest.shot(w2, out + "/r_infected.png", 1500, -110, 3.2f);
        // 3. the bar does nothing when off, and skeletons / the dead are immune
        World w3 = new World(null);
        Prop b3 = (Prop) w3.spawn(Items.RUSTBAR, 1500, -40, 1, 1f);
        b3.on = false;
        Person off = (Person) w3.spawn(Items.HUMAN, 1500, -120, 1, 1f);
        Person sk = (Person) w3.spawn(Items.SKELETON, 1470, -120, 1, 1f);
        run(w3, 2f);
        check("an off bar infects nobody", off.rustT < 0f && sk.rustT < 0f);
        b3.on = true;
        run(w3, 2f);
        check("skeletons are immune", sk.rustT < 0f && sk.kind == Person.SKELETON);
        w3.kill(off);
        check("the dead are not infected", !w3.infectRust(off));
        // 4. the Rust Man claws and infects: chain
        World w4 = new World(null);
        Person rm = (Person) w4.spawn(Items.RUSTMAN, 1300, -120, 1, 1f);
        for (int i = 0; i < 7; i++) w4.spawn(Items.HUMAN, 1650 + i * 100, -120, -1, 1f);
        float maxFar = 0f;
        int hurtWounds = 0;
        for (int i = 0; i < 60 * 40; i++) {
            w4.update(1f / 60f);
            if (i % 600 == 599) System.out.printf("   t=%ds: rust men %d, rusting %d, dead %d, severed %d%n", (i + 1) / 60, count(w4, Person.RUSTMAN), rusting(w4), dead(w4), w4.stats.severed);
        }
        int rustMen = count(w4, Person.RUSTMAN);
        check("the Rust Man tears people up (wounds, dead or limbs lost)", dead(w4) > 0 || w4.stats.severed > 0 || w4.stats.rusted > 0);
        check("his claws infect others (and the infected turn into Rust Men)", w4.stats.rusted >= 1 && (rustMen >= 2 || rusting(w4) >= 1));
        WorldTest.shot(w4, out + "/r_horde.png", 1650, -120, 1.3f);
        // 5. he bleeds rust, saving keeps the infection and the change
        World w5 = new World(null);
        Person r5 = (Person) w5.spawn(Items.RUSTMAN, 1500, -120, 1, 1f);
        Person i5 = (Person) w5.spawn(Items.HUMAN, 1800, -120, -1, 1f);
        run(w5, 1f);
        w5.bloodStyle(r5);
        check("a Rust Man bleeds rust orange", w5.fx.bloodA == 0xFFb5481a);
        w5.infectRust(i5);
        run(w5, 2f);
        Game g = Harness.newGame();
        g.world.spawn(Items.RUSTMAN, 1500, -120, 1, 1f);
        Person gi = (Person) g.world.spawn(Items.HUMAN, 1700, -120, -1, 1f);
        Harness.run(g, 1f);
        g.world.infectRust(gi);
        Harness.run(g, 2f);
        java.io.File f = new java.io.File("/tmp/trz19.trz");
        SceneIO.save(g, f);
        Game g2 = Harness.newGame();
        boolean ok = SceneIO.load(g2, f);
        int rms = 0; float rt = -1f;
        for (Ent e : g2.world.ents) if (e instanceof Person) { Person pp = (Person) e; if (pp.kind == Person.RUSTMAN) rms++; if (pp.rustT >= 0f) rt = pp.rustT; }
        check("a saved Rust Man and an infection in progress load back", ok && rms == 1 && rt > 0f && rt < 7f);
        // 6. the renewed human model draws (all outfits, bodies, damaged) and the people category lists the Rust Man
        World w6 = new World(null);
        for (int i = 0; i < 6; i++) w6.spawn(Items.HUMAN, 700 + i * 130, -140, i % 2 == 0 ? 1 : -1, 1f);
        w6.spawn(Items.RUSTMAN, 1500, -140, -1, 1f);
        w6.spawn(Items.ZOMBIE, 1650, -140, -1, 1f);
        run(w6, 2f);
        WorldTest.shot(w6, out + "/r_models.png", 1200, -110, 1.4f);
        check("the people palette has the Rust Man", Items.CATS[Items.CAT_PEOPLE].length == 12);
        Game gp = Harness.newGame();
        gp.ui.stripOpen = true; gp.ui.cat = Items.CAT_MACHINES;
        gp.world.spawn(Items.RUSTBAR, 1450, -40, 1, 1f);
        gp.world.spawn(Items.RUSTMAN, 1600, -120, -1, 1f);
        Harness.run(gp, 1f);
        Harness.shot(gp, out + "/r_palette.png");
        System.out.println(fails == 0 ? "T19 ALL PASS" : "T19 FAILURES " + fails);
    }

    static int rusting(World w) { int n = 0; for (Ent e : w.ents) if (e instanceof Person && ((Person) e).rustT >= 0f) n++; return n; }
    static int dead(World w) { int n = 0; for (Ent e : w.ents) if (e instanceof Person && ((Person) e).dead) n++; return n; }
}

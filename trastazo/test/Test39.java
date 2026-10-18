package com.frameboost.ragdoll;

/** 3.9: better gibs, the fridge with a frozen person inside, smarter AI. */
public class Test39 {
    static int fails;
    static void check(String n, boolean ok) { System.out.println((ok ? "PASS " : "FAIL ") + n); if (!ok) fails++; }
    static void run(World w, float s) { int n = (int) (s * 60); for (int i = 0; i < n; i++) w.update(1f / 60f); }

    public static void main(String[] a) throws Exception {
        String out = a.length > 0 ? a[0] : "/tmp";
        WorldTest.rend.theme = Renderer.SANDBOX;
        // 1. gibs: meat with a raw inside, bone shards, organs, and blood trails
        World w = new World(null);
        w.spawn(Items.HUMAN, 1480, -120, 1, 1f);
        w.spawn(Items.HUMAN, 1500, -120, 1, 1f);
        w.spawn(Items.HUMAN, 1520, -120, -1, 1f);
        run(w, 1.5f);
        w.explode(1500, -60, 360, 1900, 160, false);
        int flesh = 0, bones = 0, organs = 0, raw = 0;
        for (Ent e : w.ents) if (e instanceof Prop && ((Prop) e).gib) {
            Prop q = (Prop) e;
            if (q.bone) bones++; else if (q.round) organs++; else flesh++;
            if (q.color2 != 0 && !q.bone) raw++;
        }
        System.out.printf("   gibs: flesh %d, bones %d, organs %d%n", flesh, bones, organs);
        check("gibs include meat, bones and organs", flesh >= 4 && bones >= 3 && organs >= 1 && raw >= flesh);
        int drops = 0;
        for (int i = 0; i < 60; i++) { w.update(1f / 60f); drops += w.fx.activeBlood(); }
        check("flying chunks leave blood in the air", drops > 40);
        WorldTest.shot(w, out + "/n_gibs.png", 1500, -120, 1.5f);
        // 2. the fridge: shut with a frozen person hidden inside
        w = new World(null);
        Prop fr = (Prop) w.spawn(Items.FRIDGE, 1500, 0, 1, 1f);
        Person in = fr.meal;
        check("a fridge holds a frozen person", in != null && in.cell == fr && in.frozenT > 1e6f);
        boolean allFrozen = true, hidden = true;
        for (RB b : in.rb) { if (!b.frozen) allFrozen = false; for (Shape s : b.shapes) if (!s.sensor) hidden = false; }
        check("the inmate is motionless and not touchable", allFrozen && hidden);
        run(w, 2f);
        check("the closed fridge stays put with its inmate", fr.main().frozen && Math.abs(in.rb[Person.CHEST].x - 1500) < 3f && fr.meal == in);
        WorldTest.shot(w, out + "/n_fridge_closed.png", 1500, -100, 1.9f);
        w.toggleFridge(fr);
        run(w, 0.25f);
        WorldTest.shot(w, out + "/n_fridge_opening.png", 1500, -100, 1.9f);
        run(w, 1.3f);
        System.out.printf("   inmate chest x %.0f, frozenT %.1f%n", in.rb[Person.CHEST].x, in.frozenT);
        check("opening it releases the frozen person", fr.meal == null && in.cell == null && !in.rb[Person.PELVIS].frozen);
        check("the person is still frozen stiff", in.frozenT > 0f && in.frozenT <= 40f && in.jt[Person.KNEE_F].limit);
        run(w, 1f);
        WorldTest.shot(w, out + "/n_fridge_open.png", 1550, -100, 1.5f);
        float cx = in.rb[Person.CHEST].x;
        check("it tipped out of the fridge", Math.abs(cx - 1500) > 50f);
        for (RB b : in.rb) b.vy = 1500f;
        for (RB b : in.rb) b.y -= 400f;
        run(w, 1.2f);
        check("the frozen person shatters when hit hard", w.stats.shattered > 0 || in.gibbed[Person.CHEST]);
        // 3. save and load keep the fridge and its inmate
        Game g = Harness.newGame();
        Prop fr2 = (Prop) g.world.spawn(Items.FRIDGE, 1500, 0, 1, 1f);
        Prop open = (Prop) g.world.spawn(Items.FRIDGE, 900, 0, -1, 1f);
        Harness.run(g, 0.3f);
        java.io.File sf = new java.io.File(out, "n_scene.trz");
        g.saveFile = sf; g.saveScene();
        Game g2 = Harness.newGame();
        check("scene loads", SceneIO.load(g2, sf));
        int fridges = 0, linked = 0, frozen = 0;
        Prop first = null;
        for (Ent e : g2.world.ents) {
            if (e instanceof Prop && ((Prop) e).type == Items.FRIDGE) { fridges++; if (((Prop) e).meal != null) { linked++; first = (Prop) e; } }
            if (e instanceof Person && ((Person) e).frozenT > 0f) frozen++;
        }
        check("fridges and inmates survive save and load", fridges == 2 && linked == 2 && frozen == 2);
        g2.world.toggleFridge(first);
        Harness.run(g2, 2.5f);
        check("a loaded fridge still opens", first.meal == null);
        // 4. AI: jump over low things, back off from hazards, panic after gore
        w = new World(null);
        Person z = (Person) w.spawn(Items.ZOMBIE, 1000, -120, 1, 1f);
        Person prey = (Person) w.spawn(Items.HUMAN, 1700, -120, 1, 1f);
        prey.ko = 99f; prey.tone = 0f;
        w.spawn(Items.CRATE, 1250, -40, 1, 1f);
        run(w, 14f);
        System.out.printf("   zombie x %.0f (crate at 1250)%n", z.rb[Person.PELVIS].x);
        check("a hunter jumps over a crate", z.rb[Person.PELVIS].x > 1330f);
        w = new World(null);
        Person civ = (Person) w.spawn(Items.HUMAN, 1350, -120, 1, 1f);
        Prop saw = (Prop) w.spawn(Items.SAW, 1500, 0, 1, 1f);
        run(w, 1f);
        civ.walk = 1;
        Mind.avoid(w, civ);
        check("people turn back at a running saw", civ.walk == -1);
        saw.on = false;
        civ.walk = 1;
        Mind.avoid(w, civ);
        check("they walk past one that is off", civ.walk == 1);
        w = new World(null);
        Person near = (Person) w.spawn(Items.HUMAN, 1900, -120, 1, 1f), far = (Person) w.spawn(Items.HUMAN, 100, -120, 1, 1f);
        Person victim = (Person) w.spawn(Items.HUMAN, 1500, -120, 1, 1f);
        run(w, 1.5f);
        w.gib(victim, Person.CHEST, 0f, -1f);
        check("gore makes people nearby panic", near.panicT > 0f && far.panicT <= 0f);
        run(w, 1.5f);
        System.out.println("   x " + near.rb[Person.PELVIS].x);
        check("they run away from it", near.rb[Person.PELVIS].x > 1960f);
        w = new World(null);
        Prop fr3 = (Prop) w.spawn(Items.FRIDGE, 1500, 0, 1, 1f);
        Person c1 = null;
        for (int i = 0; i < 4 && (c1 == null || c1.group % 2 != 0); i++) c1 = (Person) w.spawn(Items.HUMAN, 1300 + i * 20, -120, 1, 1f);
        if (c1.group % 2 != 0) c1 = (Person) w.spawn(Items.HUMAN, 1320, -120, 1, 1f);
        run(w, 20f);
        System.out.println("   curious person group " + c1.group + ", fridge open " + fr3.on);
        check("a curious person opens the fridge", c1.group % 2 != 0 || fr3.on);
        System.out.println(fails == 0 ? "T39 ALL PASS" : "T39 FAILURES " + fails);
    }
}

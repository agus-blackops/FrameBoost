package com.frameboost.ragdoll;

/** Trastazo Unity 1.3: SAW traps, blood V3, gore V4, destruction engine, outfits V2 and the new menu. */
public class Test13 {
    static int fails;
    static void check(String n, boolean ok) { System.out.println((ok ? "PASS " : "FAIL ") + n); if (!ok) fails++; }
    static void run(World w, float secs) { int n = (int) (secs * 60); for (int i = 0; i < n; i++) w.update(1f / 60f); }

    static int trapJoints(Prop q) { int n = 0; for (Joint j : q.joints) if (!j.removed && j.tag == Traps.TRAP_TAG) n++; return n; }

    public static void main(String[] a) throws Exception {
        String out = a.length > 0 ? a[0] : "/tmp";
        Game.edition = "Unity 1.6";
        // 1. the bear trap snaps on a foot that steps on it; Activate opens it again
        World w = new World(null);
        Prop bear = (Prop) w.spawn(Items.BEARTRAP, 1500, -40, 1, 1f);
        Person v = (Person) w.spawn(Items.HUMAN, 1500, -260, 1, 1f);
        run(w, 2.5f);
        check("the bear trap catches a foot", w.stats.trapped > 0 && trapJoints(bear) == 1 && !bear.on);
        check("the caught leg is hurt and bleeds", v.soak[Person.FOOT_F] + v.soak[Person.FOOT_B] + v.soak[Person.SHIN_F] + v.soak[Person.SHIN_B] > 0f
                || v.bleed[Person.FOOT_F] + v.bleed[Person.FOOT_B] + v.bleed[Person.SHIN_F] + v.bleed[Person.SHIN_B] > 0f);
        String msg = Traps.activate(w, bear);
        run(w, 0.2f);
        check("Activate re-arms the trap and lets go", bear.on && trapJoints(bear) == 0 && msg.length() > 0);
        WorldTest.shot(w, out + "/u_beartrap.png", 1500, -120, 2.2f);

        // 2. the pendulum swings and cuts whoever stands under it
        w = new World(null);
        Prop pend = (Prop) w.spawn(Items.PENDULUM, 1500, -60, 1, 1f);
        Person p2 = (Person) w.spawn(Items.HUMAN, 1540, -120, -1, 1f);
        float hp0 = 0f; for (float h : p2.hp) hp0 += h;
        float maxA = 0f;
        for (int i = 0; i < 60 * 4; i++) { w.update(1f / 60f); maxA = Math.max(maxA, Math.abs(pend.bodies.get(1).a)); }
        float hp1 = 0f; for (float h : p2.hp) hp1 += h;
        System.out.printf("   pendulum: swing %.2f rad, damage %.0f, severed %d%n", maxA, hp0 - hp1, w.stats.severed);
        check("the pendulum swings", maxA > 0.9f);
        check("the pendulum blade cuts", hp0 - hp1 > 40f);
        check("the blade gets bloody", pend.wear > 0f);
        WorldTest.shot(w, out + "/u_pendulum.png", 1500, -220, 1.4f);
        Traps.activate(w, pend);
        run(w, 3f);
        check("Activate stops the pendulum", !pend.on && Math.abs(pend.bodies.get(1).a) < 0.3f);

        // 3. the reverse bear trap: placed on a head, counts down; freed in time or the jaws rip open
        w = new World(null);
        Person a1 = (Person) w.spawn(Items.HUMAN, 1300, -120, 1, 1f);
        Person a2 = (Person) w.spawn(Items.HUMAN, 1800, -120, -1, 1f);
        run(w, 1f);
        Prop t1 = (Prop) w.spawn(Items.REVTRAP, a1.rb[Person.HEAD].x, a1.rb[Person.HEAD].y, 1, 1f);
        Prop t2 = (Prop) w.spawn(Items.REVTRAP, a2.rb[Person.HEAD].x, a2.rb[Person.HEAD].y, 1, 1f);
        check("placed on a head it locks on", t1.victim == a1 && t2.victim == a2 && trapJoints(t1) == 1);
        run(w, 4f);
        check("it counts down", t1.work < Traps.REV_TIME - 3.5f && t1.work > 0f);
        WorldTest.shot(w, out + "/u_revtrap.png", a1.rb[Person.HEAD].x, a1.rb[Person.HEAD].y + 30f, 2.6f);
        String m2 = Traps.activate(w, t2);
        check("Activate frees the victim in time", t2.victim == null && w.stats.trapsEscaped == 1 && !a2.gibbed[Person.HEAD] && m2.startsWith("¡"));
        run(w, 9f);
        check("time's up: the jaws rip the head apart", a1.gibbed[Person.HEAD] && a1.dead && t1.dead && w.stats.trapsSprung == 1);
        check("the freed one keeps their head", !a2.gibbed[Person.HEAD]);
        check("blood on the wall behind", w.fx.dn > 0);
        WorldTest.shot(w, out + "/u_revtrap_after.png", 1550, -150, 1.2f);

        // 4. Billy rides his tricycle and talks
        w = new World(null);
        Prop billy = (Prop) w.spawn(Items.BILLY, 1500, -60, 1, 1f);
        float x0 = billy.cx();
        boolean talked = false;
        for (int i = 0; i < 60 * 14; i++) { w.update(1f / 60f); if (billy.talkT > 0f) talked = true; }
        System.out.printf("   billy: moved %.0f, angle %.2f%n", Math.abs(billy.cx() - x0), billy.main().a);
        check("Billy rides", Math.abs(billy.cx() - x0) > 150f && Math.abs(Fauna.wrap(billy.main().a)) < 0.5f);
        check("Billy talks", talked);

        // 5. destruction engine: a hard hit cracks a crate, a harder one breaks it into uneven pieces around the hit
        w = new World(null);
        Prop crate = (Prop) w.spawn(Items.BIGCRATE, 1500, -60, 1, 1f);
        run(w, 1f);
        w.crack(crate, crate.cx() - 40f, crate.cy() - 30f, 0.6f);
        check("hits leave cracks where they land", crate.ncr == 1 && crate.crU[0] < 0f && crate.crV[0] < 0f);
        int before = w.ents.size();
        crate.impX = crate.cx() - 50f; crate.impY = crate.cy() - 40f;
        w.fracture(crate, crate.cx(), crate.cy());
        int pieces = w.ents.size() - before + 1;
        float minA = 1e9f, maxArea = 0f;
        for (Ent e : w.ents) { Prop f = (Prop) e; if (!f.frag) continue; float ar = f.hw * f.hh; minA = Math.min(minA, ar); maxArea = Math.max(maxArea, ar); }
        System.out.printf("   fracture: %d pieces, smallest %.0f, largest %.0f%n", pieces - 1, minA, maxArea);
        check("it breaks into several pieces", pieces - 1 >= 3);
        check("the pieces have different sizes", maxArea > minA * 2f);
        run(w, 1.5f);
        WorldTest.shot(w, out + "/u_destroy.png", 1500, -100, 1.6f);

        // 6. blood V3: pools grow, splats age, clothes soak
        w = new World(null);
        Person g1 = (Person) w.spawn(Items.HUMAN, 1500, -120, 1, 1f);
        run(w, 1f);
        int sn0 = w.fx.sn;
        for (int i = 0; i < 40; i++) w.fx.stain(1500f + (i % 3), 4f, 0xD9c0121b);
        check("drops falling on the same spot make one growing pool", w.fx.sn - sn0 <= 2);
        w.hurt(g1, Person.CHEST, 30f, g1.rb[Person.CHEST].x, g1.rb[Person.CHEST].y, 1f, 0f);
        check("blood soaks the clothes", g1.soak[Person.CHEST] > 0.2f);
        int ag = Fx.aged(0xFFc0121b, 40f);
        check("old blood darkens", ag != 0xFFc0121b && (ag >>> 24) == 0xFF);
        // gore V4: a wrecked limb, a cracked skull, an open belly
        g1.hp[Person.THIGH_F] = -80f; g1.hp[Person.HEAD] = 3f; g1.hp[Person.PELVIS] = -70f;
        w.sever(g1, Person.EL_F, g1.rb[Person.FARM_F].x, g1.rb[Person.FARM_F].y);
        run(w, 0.5f);
        WorldTest.shot(w, out + "/u_gore.png", 1500, -110, 3.2f);
        check("gore scene draws", true);

        // 7. outfits V2: stable, every style appears
        boolean[] tops = new boolean[7], bots = new boolean[4], hairs = new boolean[6];
        java.util.Random r = new java.util.Random(3);
        Sim s = new Sim();
        for (int i = 0; i < 300; i++) {
            Person q = Person.create(s, 0, 0, 1f, 1, Person.HUMAN, r);
            tops[Renderer.topStyle(q)] = true; bots[Renderer.bottomStyle(q)] = true; hairs[Renderer.hairStyle(q)] = true;
            if (Renderer.outfit(q) != Renderer.outfit(q)) fails++;
        }
        boolean all = true;
        for (boolean b : tops) all &= b;
        for (boolean b : bots) all &= b;
        for (boolean b : hairs) all &= b;
        check("every top, trousers and hair style shows up", all);
        w = new World(null);
        WorldTest.rend.theme = Renderer.SANDBOX;
        for (int i = 0; i < 8; i++) w.spawn(Items.HUMAN, 700 + i * 160, -140, i % 2 == 0 ? 1 : -1, 1f);
        run(w, 2f);
        WorldTest.shot(w, out + "/u_outfits.png", 1260, -120, 1.5f);

        // 8. the new title screen and the trap palette
        Game g = Harness.newGame();
        g.screens.screen = Screens.TITLE; g.screens.time = 4f;
        Harness.run(g, 0.5f);
        Harness.shot(g, out + "/u_title.png");
        check("the title screen draws", true);
        g = Harness.newGame();
        g.ui.stripOpen = true; g.ui.cat = Items.CAT_TRAPS;
        g.world.spawn(Items.PENDULUM, 1500, -60, 1, 1f);
        g.world.spawn(Items.BEARTRAP, 1250, -40, 1, 1f);
        g.world.spawn(Items.BILLY, 1750, -60, -1, 1f);
        g.world.spawn(Items.HUMAN, 1250, -200, 1, 1f);
        Harness.run(g, 1.5f);
        for (Ent e : g.world.ents) if (e instanceof Prop && ((Prop) e).type == Items.BILLY) Traps.say(g.world, (Prop) e);
        Harness.run(g, 0.4f);
        Harness.shot(g, out + "/u_traps.png");
        check("the traps palette has four traps", Items.CATS[Items.CAT_TRAPS].length == 4);
        System.out.println(fails == 0 ? "T13 ALL PASS" : "T13 FAILURES " + fails);
    }
}

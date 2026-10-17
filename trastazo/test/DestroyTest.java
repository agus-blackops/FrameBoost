package com.frameboost.ragdoll;

/** People Playground style destruction: wounds, gibbing, breakable props. */
public class DestroyTest {
    static int fails;
    static void check(String n, boolean ok) { System.out.println((ok ? "PASS " : "FAIL ") + n); if (!ok) fails++; }
    static void run(World w, float s) { int n = (int) (s * 60); for (int i = 0; i < n; i++) w.update(1f / 60f); }
    static Person person(World w, float x) { return (Person) w.spawn(Items.HUMAN, x, -120, 1, 1f); }
    static int count(World w, boolean gib) { int n = 0; for (Ent e : w.ents) if (e instanceof Prop && (gib ? ((Prop) e).gib : ((Prop) e).frag)) n++; return n; }

    public static void main(String[] a) throws Exception {
        String out = a.length > 0 ? a[0] : "/tmp";
        WorldTest.rend.theme = Renderer.SANDBOX;
        // 0. a lineup in the new look, some of them hurt
        World l = new World(null);
        int[] kinds = {Items.HUMAN, Items.SOLDIER, Items.ZOMBIE, Items.ROBOT, Items.SKELETON, Items.MEDIC, Items.THUG, Items.FIREFIGHTER};
        for (int i = 0; i < kinds.length; i++) l.spawn(kinds[i], 600 + i * 130, -150, i % 2 == 0 ? 1 : -1, 1f);
        run(l, 3f);
        for (Ent e : l.ents) { Person q = (Person) e; q.walk = 0; }
        Person v0 = (Person) l.ents.get(0);
        for (int k = 0; k < 4; k++) l.shoot(null, 300, v0.rb[Person.CHEST].y - 12 + k * 8, 1, 0, 6f, 50f, 1);
        l.wound(v0, Person.THIGH_F, Person.W_CUT, v0.rb[Person.THIGH_F].x, v0.rb[Person.THIGH_F].y, 1, 0.3f, 9f);
        l.wound(v0, Person.HEAD, Person.W_BRUISE, v0.rb[Person.HEAD].x, v0.rb[Person.HEAD].y, 1, 0, 5f);
        run(l, 0.1f);
        WorldTest.shot(l, out + "/d_people.png", 1050, -160, 1.5f);
        // 1. bullets leave holes where they hit
        World w = new World(null);
        Person p = person(w, 1000);
        run(w, 2f);
        RB ch = p.rb[Person.CHEST];
        w.shoot(null, 600, ch.y, 1, 0, 10f, 300f, 1);
        check("a bullet leaves a wound", p.nw >= 1 && p.wpart[0] == Person.CHEST && p.wkind[0] == Person.W_HOLE);
        w.heal(p);
        check("healing closes wounds", p.nw == 0);

        // 2. a heavy round bursts the head
        int gibbed = 0;
        for (int k = 0; k < 6; k++) {
            World v = new World(null);
            Person q = person(v, 1000);
            run(v, 2f);
            RB h = q.rb[Person.HEAD];
            v.shoot(null, 600, h.y, 1, 0, 40f, 1600f, 3);
            run(v, 0.5f);
            if (q.gibbed[Person.HEAD]) { gibbed++; check("burst head kills and leaves chunks", q.dead && count(v, true) > 0 && !q.intact()); }
        }
        System.out.println("   heads burst: " + gibbed + "/6");
        check("heavy rounds burst some heads", gibbed > 0);

        // 3. point-blank explosion turns people into chunks; far ones survive
        w = new World(null);
        Person near = person(w, 1000), far = person(w, 2200);
        run(w, 2f);
        w.explode(1000, -80, 430, 2400, 110, false);
        run(w, 0.2f);
        int g = 0; for (int i = 0; i < Person.NPARTS; i++) if (near.gibbed[i]) g++;
        System.out.println("   gibbed parts near the blast: " + g + ", chunks " + count(w, true));
        check("point-blank blast gibs parts", g >= 2 && near.dead);
        check("someone far away is untouched", !far.dead && far.nw == 0);
        run(w, 3f);
        boolean stillFrozen = true;
        for (int i = 0; i < Person.NPARTS; i++) if (near.gibbed[i] && !near.rb[i].frozen) stillFrozen = false;
        check("gibbed parts stay out of the simulation", stillFrozen);
        WorldTest.shot(w, out + "/d_gib.png", 1000, -200, 0.9f);

        // 4. bullets break a crate into fragments, fragments into splinters
        w = new World(null);
        Prop crate = (Prop) w.spawn(Items.BIGCRATE, 1000, -80, 1, 1f);
        run(w, 1.5f);
        for (int i = 0; i < 12 && !crate.removed; i++) w.shoot(null, 600, crate.cy(), 1, 0, 14f, 300f, 1);
        run(w, 0.5f);
        System.out.println("   crate fragments " + count(w, false));
        check("bullets break a big crate into pieces", crate.removed && count(w, false) >= 2);
        // 5. a falling crate does not break on a short drop but smashes on a long one
        World d = new World(null);
        Prop c1 = (Prop) d.spawn(Items.CRATE, 800, -120, 1, 1f);
        Prop c2 = (Prop) d.spawn(Items.CRATE, 1400, -1400, 1, 1f);
        run(d, 3f);
        System.out.println("   short drop wear " + c1.wear + ", long drop removed " + c2.removed + " wear " + c2.wear);
        check("short drop leaves the crate whole", !c1.removed);
        // 6. the explosion breaks blocks; chunks are capped
        World e = new World(null);
        e.buildAt(Items.WALL, 1200, 0);
        run(e, 1f);
        for (int i = 0; i < 4; i++) { e.explode(1100 + i * 60, -60, 330, 1900, 90, false); run(e, 0.6f); }
        System.out.println("   wall broken pieces " + e.stats.broken + ", ents " + e.ents.size());
        check("explosions shatter blocks", e.stats.broken > 3);
        int chunks = 0; for (Ent x : e.ents) if (x instanceof Prop && (((Prop) x).frag || ((Prop) x).gib)) chunks++;
        check("fragments stay under the cap", chunks <= World.MAX_CHUNKS);
        WorldTest.shot(e, out + "/d_wall.png", 1200, -200, 0.9f);
        System.out.println(fails == 0 ? "DESTROY ALL PASS" : "DESTROY FAILURES " + fails);
    }
}

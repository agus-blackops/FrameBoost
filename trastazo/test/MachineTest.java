package com.frameboost.ragdoll;

/** People Playground style machines: saw, spikes, fan, crusher, Tesla coil, liquid nitrogen, blunt weapons. */
public class MachineTest {
    static int fails;
    static void check(String n, boolean ok) { System.out.println((ok ? "PASS " : "FAIL ") + n); if (!ok) fails++; }
    static void run(World w, float s) { int n = (int) (s * 60); for (int i = 0; i < n; i++) w.update(1f / 60f); }
    static float hpLost(Person p) { float l = 0; for (int i = 0; i < Person.NPARTS; i++) l += Person.maxHp(p, i) - p.hp[i]; return l; }
    static int gibbed(Person p) { int n = 0; for (int i = 0; i < Person.NPARTS; i++) if (p.gibbed[i]) n++; return n; }

    public static void main(String[] a) throws Exception {
        String out = a.length > 0 ? a[0] : "/tmp";
        WorldTest.rend.theme = Renderer.SANDBOX;
        // 1. the saw cuts whoever falls on it
        World w = new World(null);
        Prop saw = (Prop) w.spawn(Items.SAW, 1500, 0, 1, 1f);
        check("machines are fixed in place", saw.main().frozen && saw.bodies.get(1).frozen);
        Person p = (Person) w.spawn(Items.HUMAN, 1500, -520, 1, 1f);
        run(w, 2.5f);
        System.out.printf("   saw: hp lost %.0f, wounds %d, severed %d%n", hpLost(p), p.nw, w.stats.severed);
        check("the saw cuts a person", hpLost(p) > 40f && p.nw > 0);
        float a0 = saw.bodies.get(1).a;
        run(w, 0.5f);
        check("the blade spins", Math.abs(saw.bodies.get(1).a - a0) > 3f);
        // 2. spikes impale what falls on them
        w = new World(null);
        Prop sp = (Prop) w.spawn(Items.SPIKES, 1500, 0, 1, 1f);
        Person q = (Person) w.spawn(Items.HUMAN, 1500, -600, 1, 1f);
        q.rb[Person.PELVIS].vy = 400f;
        run(w, 2f);
        int stuck = 0; for (Joint j : sp.joints) if (!j.removed) stuck++;
        System.out.println("   impaled parts " + stuck);
        check("falling on spikes impales", stuck > 0 && w.stats.impaled > 0);
        run(w, 2f);
        int still = 0; for (Joint j : sp.joints) if (!j.removed) still++;
        check("impaled parts stay stuck", still > 0);
        for (Joint j : sp.joints) if (!j.removed) { j.b.vy = -9000f; }
        run(w, 0.3f);
        int left = 0; for (Joint j : sp.joints) if (!j.removed) left++;
        check("a hard pull frees them", left < still);
        WorldTest.shot(w, out + "/m_spikes.png", 1500, -100, 1.6f);
        // 3. the fan blows things away
        w = new World(null);
        w.spawn(Items.FAN, 1200, 0, 1, 1f);
        Prop behind = (Prop) w.spawn(Items.CRATE, 1000, -40, 1, 1f);
        Prop bal = (Prop) w.spawn(Items.BALL, 1400, -40, 1, 1f);
        Person blown = (Person) w.spawn(Items.HUMAN, 1650, -120, -1, 1f);
        float b0 = behind.cx(), ball0 = bal.cx(), h0 = blown.rb[Person.PELVIS].x;
        run(w, 2f);
        System.out.printf("   fan: ball %+.0f, person %+.0f, behind %+.0f%n", bal.cx() - ball0, blown.rb[Person.PELVIS].x - h0, behind.cx() - b0);
        check("the fan pushes what is in front", bal.cx() - ball0 > 150f && blown.rb[Person.PELVIS].x - h0 > 40f);
        check("nothing behind the fan moves", Math.abs(behind.cx() - b0) < 5f);
        // 4. the crusher flattens whoever is under it
        w = new World(null);
        w.spawn(Items.CRUSHER, 1500, 0, 1, 1f);
        Person v = (Person) w.spawn(Items.HUMAN, 1500, -120, 1, 1f);
        v.ko = 99f; v.tone = 0f;
        run(w, 6f);
        System.out.println("   crusher: gibbed parts " + gibbed(v) + ", dead " + v.dead + ", removed " + v.removed);
        check("the crusher crushes", v.removed || v.dead || gibbed(v) > 0);
        WorldTest.shot(w, out + "/m_crusher.png", 1500, -160, 1.4f);
        // 5. the Tesla coil shocks people nearby
        w = new World(null);
        w.spawn(Items.TESLA, 1500, 0, 1, 1f);
        Person t = (Person) w.spawn(Items.HUMAN, 1720, -120, -1, 1f);
        run(w, 3f);
        System.out.println("   shocks " + w.stats.shocks + ", burns " + t.nw);
        check("the Tesla coil shocks", w.stats.shocks > 0 && t.nw > 0);
        boolean arcs = false; for (float[] ar : w.arcs) if (ar[4] > 0f) arcs = true;
        WorldTest.shot(w, out + "/m_tesla.png", 1600, -120, 1.6f);
        // 6. liquid nitrogen: frozen stiff, thaws, or shatters on a hard hit
        w = new World(null);
        Person f = (Person) w.spawn(Items.HUMAN, 1500, -120, 1, 1f);
        run(w, 1f);
        w.inject(f, Items.SYR_FREEZE);
        check("liquid nitrogen freezes", f.frozenT > 0f && f.jt[Person.KNEE_F].limit && f.tone == 0f);
        run(w, 1f);
        WorldTest.shot(w, out + "/m_frozen.png", 1500, -120, 1.8f);
        f.frozenT = 0.1f;
        run(w, 0.5f);
        check("it thaws and moves again", f.frozenT <= 0f);
        w.inject(f, Items.SYR_FREEZE);
        for (RB b : f.rb) { b.vy = 1400f; }
        for (RB b : f.rb) b.y -= 600f;
        run(w, 1.2f);
        System.out.println("   shattered " + w.stats.shattered + ", removed " + f.removed);
        check("a frozen body shatters on a hard landing", w.stats.shattered > 0);
        WorldTest.shot(w, out + "/m_shatter.png", 1500, -120, 1.4f);
        // 7. blunt weapons, and everything saves and loads
        w = new World(null);
        w.spawn(Items.BAT, 1300, -100, 1, 1f);
        w.spawn(Items.HAMMER, 1400, -100, 1, 1f);
        w.spawn(Items.SAW, 1600, 0, 1, 1f);
        w.spawn(Items.CRUSHER, 2000, 0, 1, 1f);
        w.spawn(Items.TESLA, 2350, 0, 1, 1f);
        w.spawn(Items.FAN, 900, 0, 1, 1f);
        w.spawn(Items.SPIKES, 2650, 0, 1, 1f);
        run(w, 1f);
        check("bat and hammer spawn", w.ents.size() == 7);
        WorldTest.shot(w, out + "/m_lineup.png", 1750, -150, 0.95f);
        Game g = Harness.newGame();
        for (Ent e : w.ents.toArray(new Ent[0])) { }
        g.world.spawn(Items.CRUSHER, 1500, 0, 1, 1f);
        g.world.spawn(Items.SAW, 1100, 0, 1, 1f);
        java.io.File sf = new java.io.File(out, "m_scene.trz");
        g.saveFile = sf;
        g.saveScene();
        Game g2 = Harness.newGame();
        g2.saveFile = sf;
        boolean ok = SceneIO.load(g2, sf);
        int machines = 0; Prop cr = null;
        for (Ent e : g2.world.ents) if (e instanceof Prop && ((Prop) e).fixedMachine()) { machines++; if (((Prop) e).type == Items.CRUSHER) cr = (Prop) e; }
        float py0 = cr.bodies.get(3).y;
        for (int i = 0; i < 120; i++) g2.update(1f / 60f);
        check("machines survive save and load and keep working", ok && machines == 2 && Math.abs(cr.bodies.get(3).y - py0) > 20f);
        System.out.println(fails == 0 ? "MACHINE ALL PASS" : "MACHINE FAILURES " + fails);
    }
}

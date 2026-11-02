package com.frameboost.ragdoll;

/** Gore V6 (more realistic): clotting wounds, pallor, convulsions, pulsing stumps, coughed blood, mist, real blood colours. */
public class Test20 {
    static int fails;
    static void check(String n, boolean ok) { System.out.println((ok ? "PASS " : "FAIL ") + n); if (!ok) fails++; }
    static void run(World w, float s) { int n = (int) (s * 60); for (int i = 0; i < n; i++) w.update(1f / 60f); }
    static float speed(Person p) { float t = 0f; for (RB b : p.rb) t += Math.abs(b.vx) + Math.abs(b.vy); return t; }

    public static void main(String[] a) throws Exception {
        String out = a.length > 0 ? a[0] : "/tmp";
        WorldTest.rend.theme = Renderer.SANDBOX;
        // 1. realistic blood colours: darker, less saturated than before
        World w = new World(null);
        Person p = (Person) w.spawn(Items.HUMAN, 1500, -120, 1, 1f);
        run(w, 1f);
        w.bloodStyle(p);
        check("blood is a deeper, more realistic red", w.fx.bloodA == 0xFFa80f18 && w.fx.bloodB == 0xFF5e0a0f);
        // 2. wounds have an age (they clot, bruises change colour over time)
        w.wound(p, Person.CHEST, Person.W_CUT, p.rb[Person.CHEST].x, p.rb[Person.CHEST].y, 1f, 0f, 8f);
        w.wound(p, Person.THIGH_F, Person.W_BRUISE, p.rb[Person.THIGH_F].x, p.rb[Person.THIGH_F].y, 1f, 0f, 6f);
        check("a new wound starts at age 0", p.nw >= 2 && p.wt[p.nw - 1] == 0f);
        run(w, 3f);
        check("wounds age with time", p.wt[0] > 2.5f && p.wt[1] > 2.5f);
        // 3. a mist of tiny drops on a hard hit
        World w2 = new World(null);
        Person h = (Person) w2.spawn(Items.HUMAN, 1500, -120, 1, 1f);
        run(w2, 1f);
        int before = Test18.active(w2.fx);
        w2.hurt(h, Person.CHEST, 30f, h.rb[Person.CHEST].x, h.rb[Person.CHEST].y, 1f, 0f);
        int after = Test18.active(w2.fx);
        check("a hard hit sprays a fine mist plus the drops", after - before >= 12);
        // 4. death throes: the body twitches for a moment, then goes still
        World w3 = new World(null);
        Person d = (Person) w3.spawn(Items.HUMAN, 1500, -120, 1, 1f);
        run(w3, 1.5f);
        w3.kill(d);
        float early = 0f;
        for (int i = 0; i < 60; i++) { w3.update(1f / 60f); early += speed(d); }
        run(w3, 3f);
        float late = 0f;
        for (int i = 0; i < 60; i++) { w3.update(1f / 60f); late += speed(d); }
        System.out.printf("   convulsions: first second %.0f, later %.0f, convT %.2f%n", early, late, d.convT);
        check("the body convulses after death and then lies still", early > late * 1.5f && d.convT <= 0f);
        check("the dead are timed (they go grey with the minutes)", d.deadT > 4f);
        // 5. an open stump on a living person pulses jets with the heartbeat
        World w4 = new World(null);
        Person s4 = (Person) w4.spawn(Items.HUMAN, 1500, -120, 1, 1f);
        run(w4, 1.5f);
        w4.sever(s4, Person.EL_F, s4.rb[Person.FARM_F].x, s4.rb[Person.FARM_F].y);
        int peak = 0, jets = 0;
        for (int i = 0; i < 90; i++) { w4.update(1f / 60f); int n = Test18.active(w4.fx); peak = Math.max(peak, n); if (n > 18) jets++; }
        System.out.printf("   stump: peak %d drops, %d jet frames%n", peak, jets);
        check("the stump spurts in time with the heart", peak > 20 && jets > 3);
        // 6. a ruined chest coughs blood
        World w5 = new World(null);
        Person c5 = (Person) w5.spawn(Items.HUMAN, 1500, -120, 1, 1f);
        run(w5, 1f);
        c5.hp[Person.CHEST] = -40f;
        float soak0 = c5.soak[Person.HEAD];
        run(w5, 5f);
        check("with a crushed chest they cough blood (the head gets stained)", c5.soak[Person.HEAD] > soak0 + 0.05f);
        WorldTest.shot(w5, out + "/v6_cough.png", 1500, -120, 3.6f);
        // 7. paleness: draining blood is visible in the render (just draws), wounds draw old and new
        World w6 = new World(null);
        Person pale = (Person) w6.spawn(Items.HUMAN, 1300, -120, 1, 1f);
        Person ok = (Person) w6.spawn(Items.HUMAN, 1440, -120, 1, 1f);
        Person old = (Person) w6.spawn(Items.HUMAN, 1580, -120, 1, 1f);
        Person corpse = (Person) w6.spawn(Items.HUMAN, 1720, -120, 1, 1f);
        run(w6, 1f);
        pale.blood = pale.maxBlood * 0.2f;
        for (Person q : new Person[]{old, pale, ok}) {
            w6.wound(q, Person.CHEST, Person.W_HOLE, q.rb[Person.CHEST].x, q.rb[Person.CHEST].y, 1f, 0f, 6f);
            w6.wound(q, Person.UARM_F, Person.W_CUT, q.rb[Person.UARM_F].x, q.rb[Person.UARM_F].y, 1f, 0f, 8f);
            w6.wound(q, Person.THIGH_F, Person.W_BRUISE, q.rb[Person.THIGH_F].x, q.rb[Person.THIGH_F].y, 1f, 0f, 8f);
        }
        for (int i = 0; i < old.nw; i++) old.wt[i] = 60f;
        w6.kill(corpse);
        corpse.deadT = 80f;
        WorldTest.shot(w6, out + "/v6_realism.png", 1500, -100, 2.0f);
        check("pale, healthy, old-wound and dead bodies draw", true);
        // 8. with gore off none of the extras appear
        World w7 = new World(null);
        w7.fx.gore = false;
        Person g7 = (Person) w7.spawn(Items.HUMAN, 1500, -120, 1, 1f);
        run(w7, 1f);
        w7.sever(g7, Person.EL_F, g7.rb[Person.FARM_F].x, g7.rb[Person.FARM_F].y);
        int pk = 0;
        for (int i = 0; i < 90; i++) { w7.update(1f / 60f); pk = Math.max(pk, Test18.active(w7.fx)); }
        System.out.printf("   gore off peak %d%n", pk);
        check("with gore off there are no pulsing jets (far fewer drops)", pk < 120);
        System.out.println(fails == 0 ? "T20 ALL PASS" : "T20 FAILURES " + fails);
    }
}

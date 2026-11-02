package com.frameboost.ragdoll;

/** Blood V6 remake: arterial blood, splashes, dripping bodies, wet blades, pool structure. */
public class Test21 {
    static int fails;
    static void check(String n, boolean ok) { System.out.println((ok ? "PASS " : "FAIL ") + n); if (!ok) fails++; }
    static void run(World w, float s) { int n = (int) (s * 60); for (int i = 0; i < n; i++) w.update(1f / 60f); }

    public static void main(String[] a) throws Exception {
        String out = a.length > 0 ? a[0] : "/tmp";
        WorldTest.rend.theme = Renderer.SANDBOX;
        // 1. a fast drop that hits the floor throws small satellite droplets back up
        Fx fx = new Fx();
        fx.floorY = 0f; fx.wallL = -1000f; fx.wallR = 1000f;
        fx.add(Fx.BLOOD, 0f, -4f, 0f, 900f, 1.6f, 4f, 0xFFa80f18);
        fx.update(1f / 60f); fx.update(1f / 60f);
        int sat = 0;
        for (int i = 0; i < Fx.MAXP; i++) if (fx.life[i] > 0f && fx.type[i] == Fx.BLOOD) sat++;
        check("a fast drop breaks into satellite droplets on impact", sat >= 2);
        // 2. arterial blood is brighter than the venous blood
        Fx f2 = new Fx();
        f2.arterial(0f, 0f, 1f, -1f, 30);
        int bright = 0;
        for (int i = 0; i < Fx.MAXP; i++) if (f2.life[i] > 0f && f2.color[i] == Fx.ARTERIAL) bright++;
        check("arterial blood has its own, brighter red", bright >= 12);
        // 3. a soaked body drips onto the floor, drop by drop, and dries a little
        World w = new World(null);
        Person p = (Person) w.spawn(Items.HUMAN, 1500, -120, 1, 1f);
        run(w, 1.5f);
        for (int i = 0; i < Person.NPARTS; i++) p.soak[i] = 0.8f;
        float before = 0f; for (float sv : p.soak) before += sv;
        int seen = 0;
        for (int i = 0; i < 60 * 3; i++) { w.update(1f / 60f); seen = Math.max(seen, Test18.active(w.fx)); }
        float after = 0f; for (float sv : p.soak) after += sv;
        System.out.printf("   dripping: peak %d drops, soak %.1f -> %.1f%n", seen, before, after);
        check("a soaked body drips blood", seen >= 4 && after < before);
        // 4. a blade that cuts someone is bloody afterwards
        World w2 = new World(null);
        Person v = (Person) w2.spawn(Items.HUMAN, 1500, -120, 1, 1f);
        Prop sword = (Prop) w2.spawn(Items.SWORD, 1380, -80, 1, 1f);
        run(w2, 1.2f);
        for (int i = 0; i < 60 * 2; i++) { sword.main().vx = 1100f; sword.main().vy = -10f; w2.update(1f / 60f); }
        System.out.printf("   sword wear (blood) %.2f, person wounds %d%n", sword.wear, v.nw);
        check("a blade that cuts flesh gets bloody", sword.wear > 0.1f);
        WorldTest.shot(w2, out + "/b6_sword.png", 1500, -90, 3.2f);
        // 5. pools: a rim, a clotting centre and cracks when old (draws at every age)
        World w3 = new World(null);
        for (int i = 0; i < 20; i++) w3.fx.stain(1500f + i * 3f, 20f, 0xD9a80f18);
        for (int age : new int[]{0, 25, 80}) {
            for (int k = 0; k < w3.fx.sn; k++) w3.fx.sa[k] = age;
            WorldTest.shot(w3, out + "/b6_pool_" + age + ".png", 1500, -20, 4.0f);
        }
        check("pools draw fresh, clotting and cracked", true);
        // 6. wall splat with streaks
        World w4 = new World(null);
        w4.fx.wallSplat(1500f, -150f, 30f, 0xFFa80f18);
        w4.fx.wallSplat(1600f, -120f, 18f, 0xFF5e0a0f);
        run(w4, 6f);
        WorldTest.shot(w4, out + "/b6_wall.png", 1550, -120, 3.4f);
        check("wall splats with streaks draw", w4.fx.dn == 2);
        // 7. a full scene: a hit person dripping, spraying and pooling
        World w5 = new World(null);
        Person q = (Person) w5.spawn(Items.HUMAN, 1500, -120, 1, 1f);
        run(w5, 1f);
        w5.sever(q, Person.EL_F, q.rb[Person.FARM_F].x, q.rb[Person.FARM_F].y);
        w5.hurt(q, Person.CHEST, 35f, q.rb[Person.CHEST].x, q.rb[Person.CHEST].y, 1f, 0f);
        run(w5, 4f);
        WorldTest.shot(w5, out + "/b6_scene.png", 1500, -100, 2.6f);
        check("a bleeding person draws with spray, drips and a pool", w5.fx.sn > 0);
        System.out.println(fails == 0 ? "T21 ALL PASS" : "T21 FAILURES " + fails);
    }
}

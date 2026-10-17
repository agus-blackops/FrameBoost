package com.frameboost.ragdoll;

/** Animals: walking, eating the dead, pooping, dying, fleeing. */
public class AnimalTest {
    static int fails;
    static void check(String n, boolean ok) { System.out.println((ok ? "PASS " : "FAIL ") + n); if (!ok) fails++; }
    static void run(World w, float s) { int n = (int) (s * 60); for (int i = 0; i < n; i++) w.update(1f / 60f); }
    static int count(World w, int item) { int n = 0; for (Ent e : w.ents) if (e.item == item) n++; return n; }

    public static void main(String[] a) throws Exception {
        String out = a.length > 0 ? a[0] : "/tmp";
        WorldTest.rend.theme = Renderer.SANDBOX;
        // 1. every animal stands and walks around
        for (int it : new int[]{Items.DOG, Items.PIG, Items.RAT}) {
            World w = new World(null);
            Prop an = (Prop) w.spawn(it, 1500, -100, 1, 1f);
            run(w, 1f);
            float x0 = an.cx(), travel = 0f, maxTilt = 0f, last = x0;
            for (int i = 0; i < 60 * 20; i++) { w.update(1f / 60f); travel += Math.abs(an.cx() - last); last = an.cx(); if (i > 60) maxTilt = Math.max(maxTilt, Math.abs(Fauna.wrap(an.main().a))); }
            System.out.printf("   %s: travelled %.0f, max tilt %.2f, y %.1f%n", Items.find(it).name, travel, maxTilt, an.cy());
            check(Items.find(it).name + " walks around on its feet", travel > 100f && maxTilt < 0.6f && !an.dead);
        }
        // 2. a dog and a pig eat a corpse and then poop
        World w = new World(null);
        Person body = (Person) w.spawn(Items.HUMAN, 1500, -120, 1, 1f);
        run(w, 1f);
        w.kill(body);
        Prop dog = (Prop) w.spawn(Items.DOG, 1100, -100, 1, 1f);
        Prop pig = (Prop) w.spawn(Items.PIG, 1950, -100, -1, 1f);
        run(w, 6f);
        WorldTest.shot(w, out + "/a_eat.png", 1500, -120, 1.6f);
        float t = 6f;
        while (t < 60f && w.stats.poops == 0) { run(w, 1f); t += 1f; }
        System.out.println("   eaten parts " + w.stats.eaten + ", poops " + w.stats.poops + " after " + (int) t + " s, poop props " + count(w, Items.POOP));
        check("animals eat parts of the dead", w.stats.eaten >= 1);
        check("after eating they poop", w.stats.poops >= 1 && count(w, Items.POOP) >= 1);
        WorldTest.shot(w, out + "/a_poop.png", 1500, -120, 1.6f);
        while (t < 150f && !body.removed) { run(w, 2f); t += 2f; }
        System.out.println("   corpse removed: " + body.removed + " after " + (int) t + " s, eaten " + w.stats.eaten);
        check("a corpse is eaten completely in time", body.removed);
        // 3. animals do not eat the living
        World l = new World(null);
        Person alive = (Person) l.spawn(Items.HUMAN, 1500, -120, 1, 1f);
        l.spawn(Items.DOG, 1350, -100, 1, 1f);
        run(l, 15f);
        check("the living are left alone", l.stats.eaten == 0 && !alive.dead);
        // 4. a rat runs away from a zombie
        World z = new World(null);
        Prop rat = (Prop) z.spawn(Items.RAT, 1500, -60, 1, 1f);
        Person zom = (Person) z.spawn(Items.ZOMBIE, 1650, -120, -1, 1f);
        run(z, 1f);
        float d0 = Math.abs(rat.cx() - zom.cx());
        run(z, 2f);
        float d1 = Math.abs(rat.cx() - zom.cx());
        System.out.printf("   rat-zombie distance %.0f -> %.0f (%s)%n", d0, d1, rat.mood);
        check("a rat runs from a zombie", d1 > d0 || d1 > 260f);
        // 5. bullets kill, close blasts burst, poop squashes
        World k = new World(null);
        Prop d2 = (Prop) k.spawn(Items.DOG, 1500, -100, 1, 1f);
        run(k, 1f);
        for (int i = 0; i < 4 && !d2.dead; i++) k.shoot(null, 1100, d2.cy(), 1, 0, 24f, 900f, 1);
        check("bullets kill an animal", d2.dead);
        Prop d3 = (Prop) k.spawn(Items.PIG, 2200, -100, 1, 1f);
        Prop poo = (Prop) k.spawn(Items.POOP, 2600, -40, 1, 1f);
        run(k, 1f);
        k.explode(2200, -40, 430, 2400, 110, false);
        k.shoot(null, 2900, poo.cy(), -1, 0, 10f, 300f, 1);
        run(k, 0.2f);
        check("a close blast bursts an animal", d3.removed);
        check("poop squashes", poo.removed);
        System.out.println(fails == 0 ? "ANIMAL ALL PASS" : "ANIMAL FAILURES " + fails);
    }
}

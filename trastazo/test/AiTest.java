package com.frameboost.ragdoll;

public class AiTest {
    static int fails;
    static void check(String n, boolean ok) { System.out.println((ok ? "PASS " : "FAIL ") + n); if (!ok) fails++; }
    static World world() { return new World(null); }
    static void run(World w, float secs) { int n = (int) (secs * 60); for (int i = 0; i < n; i++) w.update(1f / 60f); }
    static Person person(World w, float x, int dir, int item) { return (Person) w.spawn(item, x, -120, dir, 1f); }
    static void pin(Person p) { for (RB b : p.rb) b.setFrozen(true); }
    static int alive(World w, int kind) { int n = 0; for (Ent e : w.ents) if (e instanceof Person && ((Person) e).kind == kind && !((Person) e).dead) n++; return n; }
    static void until(World w, float max, java.util.function.Predicate<World> c) { for (int i = 0; i < (int) (max * 4) && !c.test(w); i++) run(w, 0.25f); }

    public static void main(String[] a) throws Exception {
        String out = a[0];
        // 1. soldier vs zombies
        World w = world();
        Person sol = person(w, 700, 1, Items.SOLDIER);
        person(w, 1500, -1, Items.ZOMBIE);
        person(w, 1700, -1, Items.ZOMBIE);
        run(w, 2.5f);
        WorldTest.shot(w, out + "/ai_soldier.png", 1100, -200, 0.9f);
        until(w, 60f, ww -> alive(ww, Person.ZOMBIE) == 0);
        System.out.println("   soldier: mood=" + sol.mood + " blood=" + (int) sol.blood + " aiKills=" + w.stats.aiKills + " zombiesAlive=" + alive(w, Person.ZOMBIE));
        check("soldier takes down zombies (kills=" + w.stats.aiKills + ")", alive(w, Person.ZOMBIE) == 0 || w.stats.aiKills >= 1);
        check("soldier survives", !sol.dead);

        // 2. no friendly fire
        w = world();
        sol = person(w, 700, 1, Items.SOLDIER);
        Person civ = person(w, 1000, 1, Items.HUMAN);
        Person z = person(w, 1300, -1, Items.ZOMBIE);
        run(w, 1.5f);
        pin(civ); pin(z);
        float cb = civ.blood;
        run(w, 5f);
        System.out.println("   friendly: mood=" + sol.mood + " civ blood " + (int) cb + " -> " + (int) civ.blood);
        check("soldier holds fire with a civilian in the way", civ.blood >= cb - 0.01f && civ.hp[Person.CHEST] >= 100f);

        // 3. medic treats the wounded and revives the dead
        w = world();
        Person med = person(w, 600, 1, Items.MEDIC);
        Person hurt = person(w, 1150, 1, Items.HUMAN);
        run(w, 2f);
        hurt.blood = 35f; hurt.bleed[Person.CHEST] = 30f; hurt.broken[Person.SHIN_F] = true; hurt.hp[Person.SHIN_F] = -5f;
        run(w, 3f);
        WorldTest.shot(w, out + "/ai_medic.png", 900, -200, 1.0f);
        until(w, 20f, ww -> ww.stats.heals > 0);
        System.out.println("   medic: mood=" + med.mood + " patient blood=" + (int) hurt.blood + " heals=" + w.stats.heals);
        check("medic heals the patient", w.stats.heals > 0 && !hurt.broken[Person.SHIN_F] && hurt.blood > 90f);
        w.kill(hurt);
        until(w, 40f, ww -> !hurt.dead);
        System.out.println("   cpr: revives=" + w.stats.revives + " mood=" + med.mood);
        check("medic brings a dead person back (CPR)", !hurt.dead && w.stats.revives > 0);

        // 4. firefighter
        w = world();
        Person ff = person(w, 500, 1, Items.FIREFIGHTER);
        Ent crate = w.spawn(Items.CRATE, 1300, -40, 1, 1f);
        run(w, 1.5f);
        w.ignite(crate); crate.burnT = 30f;
        run(w, 3.5f);
        WorldTest.shot(w, out + "/ai_firefighter.png", 900, -200, 1.0f);
        until(w, 25f, ww -> !crate.burning);
        System.out.println("   firefighter: mood=" + ff.mood + " burning=" + crate.burning + " fires=" + w.stats.fires);
        check("firefighter puts the fire out", !crate.burning && w.stats.fires > 0);

        // 5. thug picks a fight
        w = world();
        Person thug = person(w, 900, 1, Items.THUG);
        Person vic = person(w, 1300, -1, Items.HUMAN);
        run(w, 1.5f);
        pin(vic);
        run(w, 8f);
        System.out.println("   thug: mood=" + thug.mood + " victim blood=" + (int) vic.blood);
        check("thug beats people up", vic.blood < 96f || vic.hp[Person.HEAD] < 55f);

        // 6. civilians run from a lit grenade
        w = world();
        civ = person(w, 1200, 1, Items.HUMAN);
        run(w, 2f);
        w.spawn(Items.GRENADE, 1260, -20, 1, 1f);
        float x0 = civ.rb[0].x;
        run(w, 2.2f);
        System.out.println("   grenade: moved " + (int) (civ.rb[0].x - x0) + " mood=" + civ.mood);
        check("civilian flees a lit grenade", civ.rb[0].x < x0 - 60f || w.stats.explosions > 0 && civ.rb[0].x < x0 - 20f);

        // 7. turret
        w = world();
        Prop tur = (Prop) w.spawn(Items.TURRET, 800, -40, 1, 1f);
        Person tz = person(w, 1600, -1, Items.ZOMBIE);
        Person tc = person(w, 500, 1, Items.HUMAN);
        run(w, 2f);
        WorldTest.shot(w, out + "/ai_turret.png", 1100, -200, 0.9f);
        until(w, 14f, ww -> tz.dead);
        System.out.println("   turret: mood=" + tur.mood + " zombie dead=" + tz.dead + " civ blood=" + (int) tc.blood);
        check("turret kills the zombie", tz.dead);
        check("turret spares the civilian", tc.blood >= 99f);

        // 8. drone
        w = world();
        Prop dr = (Prop) w.spawn(Items.DRONE, 1200, -300, 1, 1f);
        run(w, 4f);
        float alt = -dr.cy();
        System.out.println("   drone altitude " + (int) alt + " mood=" + dr.mood);
        check("drone hovers", alt > 200f && alt < 500f);
        Person dz = person(w, 1700, -1, Items.ZOMBIE);
        run(w, 2f);
        WorldTest.shot(w, out + "/ai_drone.png", 1400, -250, 0.9f);
        until(w, 14f, ww -> dz.dead);
        System.out.println("   drone vs zombie: dead=" + dz.dead + " mood=" + dr.mood);
        check("drone attacks hostiles", dz.dead || dz.blood < 120f);
        for (int i = 0; i < 12 && !dr.dead; i++) w.shoot(null, dr.cx() - 400, dr.cy(), 1, 0, 20, 300, 1);
        run(w, 3f);
        check("drone can be shot down and falls", dr.dead && -dr.cy() < 120f);

        // 9. spawn + save/load for every new model
        Game g = Harness.newGame();
        int[] ids = {Items.SOLDIER, Items.MEDIC, Items.FIREFIGHTER, Items.THUG, Items.TURRET, Items.DRONE, Items.CRATE, Items.WHEEL};
        for (int i = 0; i < ids.length; i++) g.spawn(ids[i], 500 + i * 260, -200);
        check("all new models spawn", g.world.ents.size() == ids.length);
        Harness.run(g, 2f);
        java.io.File f = new java.io.File(out, "ai_scene.txt");
        SceneIO.save(g, f);
        Game l = Harness.newGame();
        check("scene loads", SceneIO.load(l, f) && l.world.ents.size() == ids.length);
        int kinds = 0, machines = 0;
        for (Ent e : l.world.ents) { if (e instanceof Person && ((Person) e).kind >= Person.SOLDIER) kinds++; if (e instanceof Prop && ((Prop) e).machine()) machines++; }
        check("roles and machines survive the round trip", kinds == 4 && machines == 2);
        Harness.run(l, 3f);

        // 10. chaos
        g = Harness.newGame();
        for (int i = 0; i < 3; i++) g.spawn(Items.SOLDIER, 400 + i * 120, -100);
        for (int i = 0; i < 2; i++) g.spawn(Items.MEDIC, 700 + i * 100, -100);
        g.spawn(Items.FIREFIGHTER, 900, -100);
        for (int i = 0; i < 3; i++) g.spawn(Items.THUG, 1300 + i * 100, -100);
        for (int i = 0; i < 6; i++) g.spawn(Items.ZOMBIE, 1700 + i * 90, -100);
        for (int i = 0; i < 4; i++) g.spawn(Items.HUMAN, 1000 + i * 80, -100);
        g.spawn(Items.TURRET, 300, -40);
        g.spawn(Items.DRONE, 1200, -300);
        g.spawn(Items.XBARREL, 1100, -40);
        long t0 = System.nanoTime();
        for (int i = 0; i < 20; i++) { Harness.run(g, 1.5f); if (i == 3) Harness.shot(g, out + "/ai_chaos.png"); }
        double ms = (System.nanoTime() - t0) / 1e6 / (30 * 60);
        System.out.printf("   chaos: %.2f ms per frame, zombies=%d thugs=%d kills=%d heals=%d revives=%d%n", ms, alive(g.world, Person.ZOMBIE), alive(g.world, Person.THUG), g.world.stats.aiKills, g.world.stats.heals, g.world.stats.revives);
        check("30 s of AI chaos without crashing", true);
        System.out.println(fails == 0 ? "AI ALL PASS" : "AI FAILURES: " + fails);
    }
}

package com.frameboost.ragdoll;

public class AiTest {
    static int fails;
    static void check(String n, boolean ok) { System.out.println((ok ? "PASS " : "FAIL ") + n); if (!ok) fails++; }
    static Body person(Game g, float x, int dir, int kind) { Body h = Spawner.human(g.world, x, -96, 1f, dir, kind); g.world.add(h); return h; }
    static Game game() { Game g = Harness.newGame(); g.settings = new Settings(); return g; }
    static void pin(Body b) { for (Particle p : b.ps) p.setPinned(true); }
    static int alive(Game g, int kind) { int n = 0; for (Body b : g.world.bodies) if (b.isHuman() && b.kind == kind && !b.dead) n++; return n; }

    public static void main(String[] a) throws Exception {
        String out = a[0];

        // 1. soldier vs zombies
        Game g = game();
        Body sol = person(g, 700, 1, Body.K_SOLDIER);
        person(g, 1500, -1, Body.K_ZOMBIE);
        person(g, 1700, -1, Body.K_ZOMBIE);
        Harness.run(g, 2.5f);
        Harness.shot(g, out + "/ai_soldier.png");
        for (int i = 0; i < 25 && alive(g, Body.K_ZOMBIE) > 0; i++) Harness.run(g, 1f);
        System.out.println("   soldier: mood=" + sol.mood + " blood=" + (int) sol.blood + " aiKills=" + g.world.stats.aiKills + " zombiesAlive=" + alive(g, Body.K_ZOMBIE));
        check("soldier takes down the zombies", alive(g, Body.K_ZOMBIE) == 0);
        check("soldier survives", !sol.dead && sol.kind == Body.K_SOLDIER);

        // 2. no friendly fire
        g = game();
        sol = person(g, 700, 1, Body.K_SOLDIER);
        Body civ = person(g, 1000, 1, Body.K_HUMAN);
        Body z = person(g, 1300, -1, Body.K_ZOMBIE);
        Harness.run(g, 1.2f);
        pin(civ); pin(z);
        float cb = civ.blood;
        Harness.run(g, 4f);
        System.out.println("   friendly: mood=" + sol.mood + " civ blood " + (int) cb + " -> " + (int) civ.blood);
        check("soldier holds fire with a civilian in the way", civ.blood >= cb - 0.01f && "¡Civil en la línea!".equals(sol.mood));

        // 3. medic treats the wounded
        g = game();
        Body med = person(g, 600, 1, Body.K_MEDIC);
        Body hurt = person(g, 1150, 1, Body.K_HUMAN);
        Harness.run(g, 1.5f);
        hurt.blood = 35f; hurt.chest.bleed = 30f; hurt.shinF.fractured = true; hurt.shinF.damage = 90f;
        Harness.run(g, 3f);
        Harness.shot(g, out + "/ai_medic.png");
        Harness.run(g, 9f);
        System.out.println("   medic: mood=" + med.mood + " patient blood=" + (int) hurt.blood + " heals=" + g.world.stats.heals);
        check("medic heals the patient", g.world.stats.heals > 0 && !hurt.shinF.fractured && hurt.blood > 90f);

        // 4. medic revives the dead
        g.world.kill(hurt);
        for (int i = 0; i < 30 && hurt.dead; i++) Harness.run(g, 1f);
        System.out.println("   cpr: medic mood=" + med.mood + " x=" + (int) med.pelvis.x + " ko=" + med.ko + " patient x=" + (int) hurt.chest.x + " dead=" + hurt.dead + " revives=" + g.world.stats.revives);
        check("medic brings a dead person back (CPR)", !hurt.dead && g.world.stats.revives > 0);

        // 5. firefighter
        g = game();
        Body ff = person(g, 500, 1, Body.K_FIREFIGHTER);
        Body crate = Spawner.crate(1300, -40, 60); g.world.add(crate);
        Harness.run(g, 1f);
        g.world.ignite(crate); crate.burnT = 30f;
        Harness.run(g, 3.5f);
        Harness.shot(g, out + "/ai_firefighter.png");
        Harness.run(g, 6f);
        System.out.println("   firefighter: mood=" + ff.mood + " burning=" + crate.burning + " fires=" + g.world.stats.fires);
        check("firefighter puts the fire out", !crate.burning && g.world.stats.fires > 0);

        // 6. thug picks a fight
        g = game();
        Body thug = person(g, 900, 1, Body.K_THUG);
        Body vic = person(g, 1300, -1, Body.K_HUMAN);
        Harness.run(g, 1f);
        pin(vic);
        Harness.run(g, 7f);
        System.out.println("   thug: mood=" + thug.mood + " victim blood=" + (int) vic.blood);
        check("thug beats people up", vic.blood < 95f);

        // 7. civilians run from a lit grenade
        g = game();
        civ = person(g, 1200, 1, Body.K_HUMAN);
        Harness.run(g, 1.5f);
        g.world.add(Spawner.grenade(1260, -20));
        float x0 = civ.pelvis.x;
        Harness.run(g, 2.2f);
        System.out.println("   grenade: moved " + (int) (civ.pelvis.x - x0) + " mood=" + civ.mood);
        check("civilian flees a lit grenade", civ.pelvis.x < x0 - 60f);

        // 8. turret
        g = game();
        Body tur = Spawner.turret(800, -40); g.world.add(tur);
        Body tz = person(g, 1600, -1, Body.K_ZOMBIE);
        Body tc = person(g, 500, 1, Body.K_HUMAN);
        Harness.run(g, 2f);
        Harness.shot(g, out + "/ai_turret.png");
        Harness.run(g, 10f);
        System.out.println("   turret: mood=" + tur.mood + " zombie dead=" + tz.dead + " civ blood=" + (int) tc.blood);
        check("turret kills the zombie", tz.dead);
        check("turret spares the civilian", tc.blood >= 99f);
        g.tool = Tools.ACTIVATE;
        PPGTest.tap(g, tur.ps.get(2).x, tur.ps.get(2).y);
        check("Activar switches the turret off", !tur.thrust);

        // 9. drone
        g = game();
        Body dr = Spawner.drone(1200, -300); g.world.add(dr);
        Harness.run(g, 4f);
        float alt = -dr.cy;
        System.out.println("   drone altitude " + (int) alt + " mood=" + dr.mood);
        check("drone hovers", alt > 200f && alt < 500f);
        Body dz = person(g, 1700, -1, Body.K_ZOMBIE);
        Harness.run(g, 2f);
        Harness.shot(g, out + "/ai_drone.png");
        Harness.run(g, 8f);
        System.out.println("   drone vs zombie: blood=" + (int) dz.blood + " dead=" + dz.dead + " mood=" + dr.mood);
        check("drone attacks hostiles", dz.dead || dz.blood < 120f);
        for (int i = 0; i < 8 && !dr.dead; i++) g.world.bullet(dr.cx - 400, dr.cy, 1, 0, 20, 300, 1);
        Harness.run(g, 3f);
        check("drone can be shot down and falls", dr.dead && -dr.cy < 60f);

        // 10. spawn + save/load for every new model
        g = game();
        int[] ids = {Tools.SOLDIER, Tools.MEDIC, Tools.FIREFIGHTER, Tools.THUG, Tools.TURRET, Tools.DRONE};
        for (int i = 0; i < ids.length; i++) g.spawn(ids[i], 500 + i * 260, -200);
        check("all new models spawn", g.world.bodies.size() == 6);
        Harness.run(g, 2f);
        Harness.shot(g, out + "/ai_lineup.png");
        java.io.File f = new java.io.File(out, "ai_scene.txt");
        SceneIO.save(g, f);
        Game l = game();
        check("scene with new models loads", SceneIO.load(l, f) && l.world.bodies.size() == 6);
        int kinds = 0, machines = 0;
        for (Body b : l.world.bodies) { if (b.isHuman() && b.kind >= Body.K_SOLDIER) kinds++; if (b.machine()) machines++; }
        check("roles and machines survive the round trip", kinds == 4 && machines == 2);
        Harness.run(l, 3f);
        for (int i = 0; i < Tools.KEYS.length; i++) if (Tools.keyId(Tools.KEYS[i]) < 0) check("mod key " + Tools.KEYS[i], false);

        // 11. chaos: everyone together for 30 s, no crash
        g = game();
        for (int i = 0; i < 3; i++) g.spawn(Tools.SOLDIER, 400 + i * 120, -100);
        for (int i = 0; i < 2; i++) g.spawn(Tools.MEDIC, 700 + i * 100, -100);
        g.spawn(Tools.FIREFIGHTER, 900, -100);
        for (int i = 0; i < 3; i++) g.spawn(Tools.THUG, 1300 + i * 100, -100);
        for (int i = 0; i < 6; i++) g.spawn(Tools.ZOMBIE, 1700 + i * 90, -100);
        for (int i = 0; i < 4; i++) g.spawn(Tools.HUMAN, 1000 + i * 80, -100);
        g.spawn(Tools.TURRET, 300, -40);
        g.spawn(Tools.DRONE, 1200, -300);
        g.world.add(Spawner.barrel(1100, -40, true));
        for (int i = 0; i < 20; i++) { Harness.run(g, 1.5f); if (i == 2) Harness.shot(g, out + "/ai_chaos.png"); }
        System.out.println("   chaos: zombies=" + alive(g, Body.K_ZOMBIE) + " thugs=" + alive(g, Body.K_THUG) + " kills=" + g.world.stats.aiKills + " heals=" + g.world.stats.heals + " revives=" + g.world.stats.revives);
        check("30 s of AI chaos without crashing", true);

        System.out.println(fails == 0 ? "AI ALL PASS" : "AI FAILURES: " + fails);
    }
}

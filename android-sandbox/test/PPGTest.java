package com.frameboost.ragdoll;

public class PPGTest {
    static void check(String n, boolean ok) { System.out.println((ok ? "PASS " : "FAIL ") + n); }
    static float sx(Game g, float wx) { return (wx - g.camX) * g.zoom + g.sw / 2f; }
    static float sy(Game g, float wy) { return (wy - g.camY) * g.zoom + g.sh / 2f; }
    static void tap(Game g, float wx, float wy) { g.touch(Game.DOWN, 0, sx(g, wx), sy(g, wy)); g.update(1f / 60); g.touch(Game.UP, 0, sx(g, wx), sy(g, wy)); g.update(1f / 60); }
    static Body human(Game g, float x) { Body h = Spawner.human(g.world, x, -96, 1f, 1); g.world.add(h); return h; }

    public static void main(String[] a) throws Exception {
        String out = a[0];
        Game g = Harness.newGame(); g.settings = new Settings();
        // fractures: shoot the front shin several times
        Body h = human(g, 1100);
        Harness.run(g, 2f);
        for (int i = 0; i < 3 && !h.shinF.fractured; i++) { g.world.hit(h, 24, (h.shinF.a.x + h.shinF.b.x) / 2, (h.shinF.a.y + h.shinF.b.y) / 2, false); Harness.run(g, 0.3f); }
        check("leg fractures under fire (damage=" + (int) h.shinF.damage + ")", h.shinF.fractured);
        Harness.run(g, 5f);
        check("broken leg: can't stand", !Brain.upright(h) || h.pelvis.y > -40);
        g.tool = Tools.INSPECT; tap(g, h.chest.x, h.chest.y);
        check("inspect selects the person", g.inspected == h);
        Harness.shot(g, out + "/ppg_inspect.png");
        g.tool = Tools.SYR_HEAL; tap(g, h.chest.x, h.chest.y);
        check("healing syringe mends the bone", !h.shinF.fractured && h.shinF.damage == 0 && h.blood == h.maxBlood);
        Harness.run(g, 5f);
        System.out.printf("   after heal: ko=%.1f tone=%.2f dead=%s legsBroken=%s blood=%.0f pelvisY=%.0f%n", h.ko, h.tone, h.dead, h.legsBroken(), h.blood, h.pelvis.y);
        check("healed person stands again", Brain.upright(h) && h.pelvis.y < -55);

        // death + resurrection
        g.world.bullet(h.head.x - 300, h.head.y, 1, 0, 22, 900, 1);
        Harness.run(g, 1f);
        check("headshot kills", h.dead);
        g.tool = Tools.SYR_LIFE; tap(g, h.chest.x, h.chest.y);
        check("life syringe revives", !h.dead);
        Harness.run(g, 6f);
        check("revived person gets up", Brain.upright(h));

        // acid -> skeleton
        Body v = human(g, 1600);
        Harness.run(g, 1.5f);
        g.tool = Tools.SYR_ACID; tap(g, v.chest.x, v.chest.y);
        Harness.run(g, 11f);
        check("acid dissolves to a skeleton (kind=" + v.kind + ", dead=" + v.dead + ")", v.dead && v.kind == Body.K_SKELETON);
        Harness.shot(g, out + "/ppg_acid.png");

        // zombie + sedative
        Body z = human(g, 700);
        Harness.run(g, 1.5f);
        g.tool = Tools.SYR_ZOMBIE; tap(g, z.chest.x, z.chest.y);
        Harness.run(g, 3.5f);
        check("virus turns into a zombie", z.kind == Body.K_ZOMBIE);
        g.tool = Tools.SYR_SEDATIVE; tap(g, z.chest.x, z.chest.y);
        System.out.println("   sedative toast: " + g.ui.toast + " ko=" + z.ko + " dead=" + z.dead);
        check("sedative knocks out", z.ko > 20);

        // guns: pistol pointing at a person, machine gun bursts
        Game q = Harness.newGame(); q.settings = new Settings();
        Body t = human(q, 1400);
        Body pistol = Spawner.gun(1150, -110, 0); q.world.add(pistol);
        pistol.ps.get(0).setPinned(true); pistol.ps.get(1).setPinned(true);
        Harness.run(q, 1.5f);
        float b0 = t.blood;
        q.tool = Tools.ACTIVATE; tap(q, 1150, -110);
        Harness.run(q, 0.3f);
        check("pistol object shoots along its barrel (blood " + (int) b0 + " -> " + (int) t.blood + ")", t.blood < b0);
        Body mg = Spawner.gun(1150, -60, 2); q.world.add(mg);
        mg.ps.get(0).setPinned(true); mg.ps.get(1).setPinned(true);
        Harness.run(q, 0.1f);
        tap(q, 1150, -60);
        check("machine gun starts firing", mg.firing);
        Harness.run(q, 1f);
        tap(q, 1150, -60);
        check("machine gun stops", !mg.firing);
        Harness.shot(q, out + "/ppg_guns.png");

        // C4 sticks and detonates remotely
        Game r = Harness.newGame(); r.settings = new Settings();
        Body crate = Spawner.crate(1300, -40, 60); r.world.add(crate);
        Body c4 = Spawner.c4(1300, -100); r.world.add(c4);
        Body c4b = Spawner.c4(900, -20); r.world.add(c4b);
        Harness.run(r, 1.5f);
        check("C4 sticks to the crate", c4.stuck);
        r.tool = Tools.ACTIVATE; tap(r, c4b.cx, c4b.cy);
        Harness.run(r, 1f);
        check("remote detonation blows every C4", c4.removed && c4b.removed);

        // long press context menu -> duplicate
        Game m = Harness.newGame(); m.settings = new Settings();
        Body box = Spawner.crate(1300, -40, 60); m.world.add(box);
        Harness.run(m, 1f);
        m.tool = Tools.HAND;
        m.touch(Game.DOWN, 0, sx(m, box.cx), sy(m, box.cy));
        Harness.run(m, 0.8f);
        Harness.shot(m, out + "/ppg_context.png");
        m.touch(Game.UP, 0, sx(m, box.cx), sy(m, box.cy));
        int crates = 0;
        // tap each context button until one duplicates (the layout is radial)
        for (float ang = -2.5208f; ang <= -0.6f && crates < 2; ang += 0.95f) {
            if (crates == 0) { }
            float bx = sx(m, box.cx) + (float) Math.cos(ang) * 78 * m.density, by = sy(m, box.cy) + (float) Math.sin(ang) * 78 * m.density;
            if (Math.abs(ang + 2.5208f) < 0.3f) { m.touch(Game.DOWN, 0, bx, by); m.touch(Game.UP, 0, bx, by); }
        }
        Harness.run(m, 0.5f);
        for (Body b : m.world.bodies) if (b.type == Body.CRATE) crates++;
        check("context menu duplicates (" + crates + " crates)", crates == 2);
    }
}

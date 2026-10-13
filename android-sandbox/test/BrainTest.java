package com.frameboost.ragdoll;

public class BrainTest {
    static void check(String n, boolean ok) { System.out.println((ok ? "PASS " : "FAIL ") + n); }
    static String st(Body h) { return String.format("pelvis=(%.0f,%.0f) chest=(%.0f,%.0f) up=%s tone=%.2f ko=%.1f dead=%s kind=%d",
            h.pelvis.x, h.pelvis.y, h.chest.x, h.chest.y, Brain.upright(h), h.tone, h.ko, h.dead, h.kind); }

    public static void main(String[] a) throws Exception {
        String out = a[0];
        Game g = Harness.newGame();
        Body h = Spawner.human(g.world, 1300, -90, 1f, 1);
        g.world.add(h);
        for (int i = 0; i < 8; i++) { Harness.run(g, 0.5f); System.out.println(" t=" + (i + 1) * 0.5 + " " + st(h)); }
        check("human stands upright", Brain.upright(h) && h.pelvis.y < -60);
        check("no drift while standing", Math.abs(h.pelvis.x - 1300) < 40);
        Harness.shot(g, out + "/brain_stand.png");
        // shove it: push torso hard
        h.chest.addVel(900, -200);
        Harness.run(g, 0.6f);
        System.out.println(" after shove " + st(h));
        Harness.run(g, 3f);
        System.out.println(" 3s later " + st(h));
        // shoot the leg -> KO
        g.world.bullet(h.pelvis.x - 300, h.pelvis.y + 30, 1, 0, 22, 900, 1);
        Harness.run(g, 0.8f);
        System.out.println(" shot " + st(h));
        check("knocked out by a shot", h.ko > 0 || !Brain.upright(h));
        Harness.run(g, 6f);
        System.out.println(" recovered? " + st(h));
        check("gets back up", Brain.upright(h) && h.pelvis.y < -55 && !h.dead);
        Harness.shot(g, out + "/brain_recover.png");

        // zombie hunt
        Game z = Harness.newGame();
        Body vic = Spawner.human(z.world, 800, -90, 1f, 1);
        Body zb = Spawner.human(z.world, 1700, -90, 1f, -1, Body.K_ZOMBIE);
        z.world.add(vic); z.world.add(zb);
        float vx0 = 800;
        for (int i = 0; i < 16; i++) {
            Harness.run(z, 1f);
            System.out.println(" z t=" + (i + 1) + " zombie " + st(zb) + " | victim x=" + (int) vic.pelvis.x + " blood=" + (int) vic.blood + " kind=" + vic.kind);
            if (i == 3) Harness.shot(z, out + "/brain_chase.png");
        }
        check("zombie moved toward the victim", zb.pelvis.x < 1500);
        check("victim fled (moved left)", vic.pelvis.x < vx0 - 100 || vic.blood < 100);
        // trapped victim: pin far to the wall then let zombie bite
        Game t = Harness.newGame();
        Body v2 = Spawner.human(t.world, 300, -90, 1f, 1);
        Body z2 = Spawner.human(t.world, 460, -90, 1f, -1, Body.K_ZOMBIE);
        t.world.add(v2); t.world.add(z2);
        Harness.run(t, 0.5f);
        v2.pelvis.setPinned(true);   // cornered civilians now try to dodge past; keep this one trapped
        for (int i = 0; i < 40; i++) { Harness.run(t, 1f); if (v2.kind == Body.K_ZOMBIE) break; }
        System.out.println(" trapped victim: " + st(v2) + " blood=" + v2.blood);
        check("bitten victim turns into a zombie", v2.kind == Body.K_ZOMBIE);
        Harness.shot(t, out + "/brain_infect.png");
    }
}

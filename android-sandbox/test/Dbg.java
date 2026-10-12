package com.frameboost.ragdoll;
public class Dbg {
    public static void main(String[] a) {
        for (int mode = 0; mode < 2; mode++) {
            Game g = Harness.newGame();
            Body h = Spawner.human(g.world, 1300, -150, 1f, 1);
            if (mode == 1) h.ls.clear();
            g.world.add(h);
            System.out.println(mode == 0 ? "with limits" : "NO limits");
            for (int i = 0; i < 8; i++) {
                Harness.run(g, 0.25f);
                System.out.printf(" t=%.2f pelvis=(%.0f,%.0f) vx=%.0f%n", (i+1)*0.25, h.pelvis.x, h.pelvis.y, h.pelvis.vx());
            }
        }
    }
}

package com.frameboost.ragdoll;
public class CrashTest {
    public static void main(String[] a) {
        // A: a fuse explosion that removes several bodies listed before it
        try {
            Game g = Harness.newGame();
            for (int i = 0; i < 6; i++) g.world.add(Spawner.ball(1200 + i * 30, -200, 26f, 0xFFff0000, true));
            Body gr = Spawner.grenade(1290, -150); gr.fuse = 0.05f; g.world.add(gr);
            Harness.run(g, 1f);
            System.out.println("A ok, bodies=" + g.world.bodies.size());
        } catch (Throwable t) { System.out.println("A CRASH " + t); }
        // B: many spawns in one frame near the particle limit
        try {
            Game g = Harness.newGame();
            for (int i = 0; i < 40; i++) g.spawn(Tools.CROWD, 300 + (i % 20) * 100, -200 - (i / 20) * 300);
            Harness.run(g, 0.5f);
            System.out.println("B ok, particles=" + g.world.ps.size() + " bodies=" + g.world.bodies.size());
        } catch (Throwable t) { System.out.println("B CRASH " + t); }
    }
}

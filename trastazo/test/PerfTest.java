package com.frameboost.ragdoll;

public class PerfTest {
    public static void main(String[] a) {
        int[] counts = {20, 60, 120, 200};
        for (int n : counts) {
            World w = new World(null);
            java.util.Random r = new java.util.Random(7);
            for (int i = 0; i < n; i++) {
                int item = new int[]{Items.HUMAN, Items.ZOMBIE, Items.CRATE, Items.BALL, Items.BARREL, Items.PLANK}[i % 6];
                w.spawn(item, 200 + r.nextInt(2600), -400 - r.nextInt(900), 1, 1f);
            }
            for (int i = 0; i < 60; i++) w.update(1f / 60f);
            long t0 = System.nanoTime();
            int frames = 180;
            for (int i = 0; i < frames; i++) w.update(1f / 60f);
            double ms = (System.nanoTime() - t0) / 1e6 / frames;
            System.out.printf("%4d objects: %.2f ms per frame (%d RB, %d joints)%n", n, ms, w.sim.bodies.size(), w.sim.joints.size());
        }
    }
}

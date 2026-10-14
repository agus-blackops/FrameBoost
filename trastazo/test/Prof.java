package com.frameboost.ragdoll;
public class Prof {
    public static void main(String[] a) {
        World w = new World(null);
        java.util.Random r = new java.util.Random(7);
        for (int i = 0; i < 200; i++) {
            int item = new int[]{Items.HUMAN, Items.ZOMBIE, Items.CRATE, Items.BALL, Items.BARREL, Items.PLANK}[i % 6];
            w.spawn(item, 200 + r.nextInt(2600), -400 - r.nextInt(900), 1, 1f);
        }
        for (int i = 0; i < 60; i++) w.update(1f / 60f);
        Sim s = w.sim;
        // time the sim step only (no world post-processing), split by sub-phase via reflection-free copies
        long tSim = 0, tPost = 0;
        for (int i = 0; i < 300; i++) {
            long t0 = System.nanoTime();
            s.update(1f / 60f);
            tSim += System.nanoTime() - t0;
            long t1 = System.nanoTime();
            w.update(0f);
            tPost += System.nanoTime() - t1;
        }
        System.out.printf("sim %.2f ms/frame, world-post %.2f ms/frame, contacts=%d%n", tSim / 1e6 / 300, tPost / 1e6 / 300, s.nc);
    }
}

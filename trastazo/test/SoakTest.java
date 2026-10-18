package com.frameboost.ragdoll;

import java.util.Random;

/** Beta soak test: chaos on every item for a long time; looks for exceptions, NaN, runaway bodies and slow steps. */
public class SoakTest {
    static int fails;
    static void bad(String m) { System.out.println("FAIL " + m); fails++; }

    static boolean finite(RB b) { return Float.isFinite(b.x) && Float.isFinite(b.y) && Float.isFinite(b.a) && Float.isFinite(b.vx) && Float.isFinite(b.vy) && Float.isFinite(b.w); }

    public static void main(String[] args) throws Exception {
        int seeds = args.length > 1 ? Integer.parseInt(args[1]) : 30;
        int secs = args.length > 2 ? Integer.parseInt(args[2]) : 40;
        int[] spawnable = new int[200]; int ns = 0;
        for (int id = 0; id < 200; id++) if (Items.isSpawn(id) && !Items.isBuild(id) || Items.isBuild(id)) spawnable[ns++] = id;
        int[] syr = {Items.SYR_HEAL, Items.SYR_ADRENALINE, Items.SYR_LIFE, Items.SYR_ZOMBIE, Items.SYR_ACID, Items.SYR_SEDATIVE, Items.SYR_FREEZE};
        double worstMs = 0; int worstSeed = -1; int maxBodies = 0;
        for (int seed = 1; seed <= seeds; seed++) {
            Random r = new Random(seed * 7919L);
            World w = new World(null);
            String last = "start";
            try {
                for (int step = 0; step < secs * 60; step++) {
                    if (step % 20 == 0 && r.nextFloat() < 0.8f) {
                        int id = spawnable[r.nextInt(ns)];
                        float x = 300 + r.nextFloat() * 2600, y = -r.nextFloat() * 700;
                        last = "spawn " + id;
                        if (Items.isBuild(id)) w.buildAt(id, x, 0); else w.spawn(id, x, y, r.nextBoolean() ? 1 : -1, 1f);
                    }
                    if (step % 45 == 0 && !w.ents.isEmpty()) {
                        Ent e = w.ents.get(r.nextInt(w.ents.size()));
                        int act = r.nextInt(10);
                        last = "action " + act + " on " + e.item;
                        if (act == 0 && e instanceof Person) w.inject((Person) e, syr[r.nextInt(syr.length)]);
                        else if (act == 1) w.explode(e.cx(), e.cy(), 120 + r.nextFloat() * 300, 1200 + r.nextFloat() * 1500, 40 + r.nextFloat() * 90, r.nextBoolean());
                        else if (act == 2) w.shoot(null, e.cx() - 400, e.cy(), 1, 0, 10 + r.nextFloat() * 30, 300 + r.nextFloat() * 1200, 1 + r.nextInt(3));
                        else if (act == 3) w.ignite(e);
                        else if (act == 4 && e instanceof Person) w.hurt((Person) e, r.nextInt(Person.NPARTS), 20 + r.nextFloat() * 120, e.cx(), e.cy(), 0, -1);
                        else if (act == 5 && e instanceof Person) w.gib((Person) e, r.nextInt(Person.NPARTS), 0, -1);
                        else if (act == 6 && e instanceof Prop && ((Prop) e).type == Items.FRIDGE) w.toggleFridge((Prop) e);
                        else if (act == 7 && e instanceof Prop && ((Prop) e).fixedMachine()) ((Prop) e).on = !((Prop) e).on;
                        else if (act == 8) w.remove(e);
                        else if (act == 9) for (RB b : e.bodies) b.applyImpulse(r.nextFloat() * 4e6f - 2e6f, -r.nextFloat() * 4e6f, b.x, b.y);
                    }
                    long t0 = System.nanoTime();
                    w.update(1f / 60f);
                    double ms = (System.nanoTime() - t0) / 1e6;
                    if (ms > worstMs && step > 120) { worstMs = ms; worstSeed = seed; }
                    if (step % 60 == 59) {
                        maxBodies = Math.max(maxBodies, w.sim.bodies.size());
                        for (RB b : w.sim.bodies) {
                            if (!finite(b)) { bad("seed " + seed + " t=" + step / 60 + " non-finite body after " + last); throw new RuntimeException("nan"); }
                            if (Math.abs(b.x) > 12000 || b.y > 3000 || b.y < -9000) { if (b.invM > 0f) { /* falls out of the room: World removes it */ } }
                        }
                        if (w.sim.bodies.size() > 1100) { bad("seed " + seed + " too many bodies " + w.sim.bodies.size()); break; }
                    }
                }
            } catch (Throwable t) {
                if (!"nan".equals(t.getMessage())) { bad("seed " + seed + " exception after " + last + ": " + t); t.printStackTrace(System.out); }
            }
        }
        System.out.printf("   worst step %.1f ms (seed %d), most bodies %d%n", worstMs, worstSeed, maxBodies);
        System.out.println(fails == 0 ? "SOAK ALL PASS" : "SOAK FAILURES " + fails);
    }
}

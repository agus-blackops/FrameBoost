package com.frameboost.ragdoll;
public class Dbg2 {
    static Prop blk(World w, float x, float y, float hw, float hh, float dens, boolean st) {
        Prop p = Prop.block(x, y, hw, hh, Build.BRICK, dens, false);
        if (st) p.main().makeStatic();
        p.register(w.sim); w.add(p); return p;
    }
    static void wd(World w, Prop a, Prop b, float x, float y, boolean pin) {
        Joint j = pin ? Joint.pin(a.main(), b.main(), x, y) : Joint.weld(a.main(), b.main(), x, y);
        j.collide = false; j.breakForce = 3e9f; j.tag = Build.TAG; w.sim.add(j);
    }
    public static void main(String[] a) {
        int n = Integer.getInteger("n", 5), cols = Integer.getInteger("cols", 1);
        float sd = Float.parseFloat(System.getProperty("sd", "0.3")), wd = Float.parseFloat(System.getProperty("wd", "0.55"));
        World w = new World(null);
        w.sim.iters = Integer.getInteger("it", 6);
        float f = -2, H = 120, hwS = cols == 1 ? 20 : 186;
        Prop[] slab = new Prop[n + 1];
        slab[0] = blk(w, 1500, f - 6, hwS, 6, 1.2f, true);
        for (int i = 1; i <= n; i++) slab[i] = blk(w, 1500, f - 6 - i * H, hwS, 6, sd, false);
        for (int i = 1; i <= n; i++) {
            float top = f - 12 - (i - 1) * H;
            for (int k = 0; k < cols; k++) {
                float wx = cols == 1 ? 1500 : (k == 0 ? 1500 - 176 : (k == 1 ? 1500 + 176 : 1500));
                Prop p = blk(w, wx, top - 54, 8, 54, wd, false);
                wd(w, slab[i - 1], p, wx, top, false);
                if (!Boolean.getBoolean("notop") || k != 2) wd(w, p, slab[i], wx, top - 108, Boolean.getBoolean("pintop"));
            }
        }
        float mr = 0;
        for (int i = 0; i < 600; i++) { if (i<40 && i%4==0) System.out.println("i="+i+" top y "+slab[n].main().y+" slab1 y "+slab[1].main().y); w.update(1f / 60f); for (Joint j : w.sim.joints) mr = Math.max(mr, j.reaction); if (i % 100 == 99) { System.out.println("t=" + i + " maxreaction " + mr + " top y " + slab[n].main().y + " joints " + w.sim.joints.size() + " destroyed " + w.stats.destroyed); mr = 0; } }
    }
}

package com.frameboost.ragdoll;
public class Dbg {
    public static void main(String[] a) {
        World w = new World(null);
        w.sim.iters = Integer.getInteger("it", 6); w.buildAt(Items.BUILDING, 1500, 0);
        int last = -1;
        for (int i = 0; i < 600; i++) {
            w.update(1f/60f);
            int n = 0; for (Joint j : w.sim.joints) if (j.tag == Build.TAG) n++;
            if (n != last) { System.out.println("t=" + i + " welds " + n + " destroyed " + w.stats.destroyed + " broken " + w.stats.broken); last = n; }
            if (i>=20&&i<=30){ float mx=0; RB mb=null; for (RB b : w.sim.bodies) if(Math.abs(b.vy)>mx){mx=Math.abs(b.vy);mb=b;} System.out.println("  i="+i+" maxvy "+mx+" at y="+mb.y+" x="+mb.x+" static="+mb.isStatic); }
            if (i%5==0&&i<60){ float mr=0; Joint mj=null; for (Joint j : w.sim.joints) if (j.tag==Build.TAG && j.reaction>mr){mr=j.reaction;mj=j;} if(mj!=null) System.out.println("  i="+i+" maxreaction "+mr+" bf "+mj.breakForce+" at y="+mj.anchorBY()); }
            if (i == 5 || i == 30) { float mx = 0; for (RB b : w.sim.bodies) mx = Math.max(mx, Math.abs(b.vy)); System.out.println("  maxvy " + mx); }
        }
    }
}

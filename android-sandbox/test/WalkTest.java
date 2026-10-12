package com.frameboost.ragdoll;
public class WalkTest { public static void main(String[] a) throws Exception {
  for (int k = 0; k < 3; k++) {
    Game g = Harness.newGame();
    Body h = Spawner.human(g.world, 600, -90, 1f, 1, k == 2 ? Body.K_ZOMBIE : Body.K_HUMAN);
    g.world.add(h); Harness.run(g, 2f);
    float x0 = h.pelvis.x; int falls = 0;
    for (int i = 0; i < 60; i++) { h.thinkT = 1f; h.walk = 1; h.panic = k == 1 ? 1 : 0; Harness.run(g, 0.1f); if (!Brain.upright(h)) falls++; }
    System.out.printf("%s: speed=%.0f u/s  notUprightSamples=%d  y=%.0f%n", k==0?"walk":k==1?"panic":"zombie", (h.pelvis.x - x0) / 6f, falls, h.pelvis.y);
    if (k == 1) Harness.shot(g, a[0] + "/walk.png");
  }
}}

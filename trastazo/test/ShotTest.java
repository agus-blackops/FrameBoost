package com.frameboost.ragdoll;

public class ShotTest {
    public static void main(String[] a) throws Exception {
        String out = a[0];
        Game g = Harness.newGame();
        g.clearWorldForLoad();
        g.world.buildAt(Items.HOUSE, 1000, 0);
        g.world.buildAt(Items.TOWER, 1500, 0);
        g.world.buildAt(Items.BRIDGE, 1950, 0);
        g.world.buildAt(Items.CAR, 2400, 0);
        Harness.run(g, 2f);
        g.ui.stripOpen = true; g.ui.cat = Items.CAT_BUILD;
        Harness.shot(g, out + "/b_sandbox.png");
        g.world.explode(1000, -60, 200, 4000, 40, false);
        Harness.run(g, 3f);
        Harness.shot(g, out + "/b_destroyed.png");
    }
}

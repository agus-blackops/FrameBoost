package com.frameboost.ragdoll;

public class ShotTest {
    public static void main(String[] a) throws Exception {
        String out = a[0];
        Game g = Harness.newGame();
        int[] items = {Items.HUMAN, Items.GIANT, Items.KID, Items.ROBOT, Items.SKELETON, Items.SOLDIER, Items.MEDIC, Items.FIREFIGHTER, Items.THUG};
        for (int i = 0; i < items.length; i++) g.spawn(items[i], 500 + i * 190, -140);
        // keep them from killing each other: freeze the aggressive roles' targets by making all passive
        Harness.run(g, 0.6f);
        for (Ent e : g.world.ents) if (e instanceof Person) ((Person) e).thinkT = 99f;
        Harness.run(g, 2f);
        g.camX = 1300; g.zoom = g.sw / 1900f;
        Harness.shot(g, out + "/g_lineup.png");
        g.clearWorldForLoad();
        for (int i = 0; i < 6; i++) g.spawn(Items.HUMAN, 900 + i * 90, -140);
        g.spawn(Items.TNT, 1150, -60);
        Harness.run(g, 2f);
        for (Ent e : g.world.ents) if (e instanceof Prop && ((Prop) e).type == Items.TNT) ((Prop) e).fuse = 0.05f;
        Harness.run(g, 0.25f);
        Harness.shot(g, out + "/g_boom.png");
        Harness.run(g, 1.5f);
        Harness.shot(g, out + "/g_after.png");
    }
}

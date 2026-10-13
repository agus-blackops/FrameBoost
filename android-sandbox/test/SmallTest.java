package com.frameboost.ragdoll;

import android.graphics.Canvas;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Renders key screens on a small phone (640x360 dp) and a big one to check layout. */
public class SmallTest {
    static void shot(Game g, int w, int h, String f) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        g.draw(new Canvas(img));
        ImageIO.write(img, "png", new File(f));
    }
    public static void main(String[] a) throws Exception {
        int[][] sizes = {{1280, 720}, {2400, 1080}};
        float[] dens = {2f, 2.75f};
        for (int k = 0; k < 2; k++) {
            int w = sizes[k][0], h = sizes[k][1];
            Game g = new Game(null, new File("/tmp/rpg-mods"));
            g.resize(w, h, dens[k]);
            Harness.run(g, 1f);
            shot(g, w, h, a[0] + "/s" + k + "_title.png");
            g.menuAction(Screens.A_SANDBOX);
            g.spawn(Tools.HUMAN, 1100, -100); g.spawn(Tools.ZOMBIE, 1500, -100); g.spawn(Tools.CRATE, 1300, -300);
            Harness.run(g, 2f);
            shot(g, w, h, a[0] + "/s" + k + "_sandbox.png");
            g.menuAction(Screens.A_CHAL + 2);
            Harness.run(g, 1f);
            shot(g, w, h, a[0] + "/s" + k + "_challenge.png");
            g.menuAction(Screens.A_MENU); g.menuAction(Screens.A_SETTINGS);
            Harness.run(g, 0.2f);
            shot(g, w, h, a[0] + "/s" + k + "_settings.png");
        }
        System.out.println("ok");
    }
}

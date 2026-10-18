package com.frameboost.ragdoll;

import android.graphics.Canvas;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Renders the main screens at several phone/tablet resolutions to check the layout. */
public class LayoutShot {
    static void shot(Game g, String file, int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Canvas c = new Canvas(img);
        g.draw(c);
        ImageIO.write(img, "png", new File(file));
    }
    public static void main(String[] a) throws Exception {
        int[][] sizes = {{800, 480}, {1280, 720}, {1024, 768}, {2400, 1080}};
        float[] dens = {1.5f, 2f, 2f, 2.75f};
        for (int i = 0; i < sizes.length; i++) {
            int w = sizes[i][0], h = sizes[i][1];
            Game g = new Game(null);
            g.resize(w, h, dens[i]);
            g.settings.tutorialDone = true;
            Harness.run(g, 0.1f);
            g.screens.screen = Screens.TITLE; g.screens.time = 5f;
            for (int k = 0; k < 30; k++) g.update(1f / 60);
            shot(g, a[0] + "/l" + w + "_title.png", w, h);
            g.screens.screen = Screens.SETTINGS;
            shot(g, a[0] + "/l" + w + "_settings.png", w, h);
            g.screens.screen = Screens.NONE;
            g.clearWorldForLoad();
            g.world.spawn(Items.HUMAN, 1500, -120, 1, 1f);
            g.ui.stripOpen = true; g.ui.cat = Items.CAT_MACHINES;
            Harness.run(g, 1f);
            shot(g, a[0] + "/l" + w + "_game.png", w, h);
        }
    }
}

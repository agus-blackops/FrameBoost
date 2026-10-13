package com.frameboost.ragdoll;

import android.graphics.Canvas;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Desktop test harness: runs the real game code against a Java2D canvas and writes PNG frames. */
public class Harness {
    static final int W = 2400, H = 1080;
    static final float DENS = 2.75f;

    static Game newGame() {
        Game g = new Game(null);
        g.resize(W, H, DENS);
        g.clearWorldForLoad();
        g.ui.stripOpen = false;
        g.screens.screen = Screens.NONE;
        g.settings.tutorialDone = true;
        return g;
    }

    static void run(Game g, float seconds) {
        int n = (int) (seconds * 60);
        for (int i = 0; i < n; i++) g.update(1f / 60f);
    }

    static void shot(Game g, String name) throws Exception {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Canvas c = new Canvas(img);
        g.draw(c);
        ImageIO.write(img, "png", new File(name));
        System.out.println("wrote " + name);
    }

    static float sx(Game g, float wx) { return (wx - g.camX) * g.zoom + g.sw / 2f; }
    static float sy(Game g, float wy) { return (wy - g.camY) * g.zoom + g.sh / 2f; }
    static void tap(Game g, float wx, float wy) { g.touch(Game.DOWN, 0, sx(g, wx), sy(g, wy)); g.update(1f / 60); g.touch(Game.UP, 0, sx(g, wx), sy(g, wy)); g.update(1f / 60); }
    static void tapScreen(Game g, float x, float y) { g.touch(Game.DOWN, 0, x, y); g.update(1f / 60); g.touch(Game.UP, 0, x, y); g.update(1f / 60); }
}

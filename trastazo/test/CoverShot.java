package com.frameboost.ragdoll;

/** Renders the cover (title screen) after a few seconds of its live scene. */
public class CoverShot {
    public static void main(String[] a) throws Exception {
        Game.edition = null;
        float[] secs = {3.5f, 8f};
        for (float t : secs) {
            Game g = new Game(null);
            g.resize(1280, 720, 2f);
            g.settings.tutorialDone = true;
            g.screens.screen = Screens.TITLE; g.screens.time = 5f;
            for (int i = 0; i < (int) (t * 60); i++) g.update(1f / 60);
            LayoutShot.shot(g, a[0] + "/cover_" + (int) (t * 10) + ".png", 1280, 720);
        }
    }
}

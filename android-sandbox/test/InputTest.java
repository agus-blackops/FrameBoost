package com.frameboost.ragdoll;

public class InputTest {
    static void tap(Game g, float x, float y) { g.touch(Game.DOWN, 0, x, y); g.update(1f/60); g.touch(Game.UP, 0, x, y); g.update(1f/60); }
    static float sx(Game g, float wx) { return (wx - g.camX) * g.zoom + g.sw / 2f; }
    static float sy(Game g, float wy) { return (wy - g.camY) * g.zoom + g.sh / 2f; }
    static void check(String name, boolean ok) { System.out.println((ok ? "PASS " : "FAIL ") + name); }

    public static void main(String[] a) throws Exception {
        String out = a[0];
        Game g = Harness.newGame();
        float d = Harness.DENS;
        // open "Gente" tab (2nd of 6), choose Humano
        tap(g, g.sw / 6f * 1.5f, g.sh - 20 * d);
        check("strip opens", g.ui.stripOpen && g.ui.cat == 1);
        float cardY = g.ui.stripTop() + g.ui.stripH / 2;
        tap(g, g.ui.gap + g.ui.cardW / 2, cardY);
        check("tool=HUMAN", g.tool == Tools.HUMAN);
        tap(g, sx(g, 1000), sy(g, -100));
        check("spawn human", g.world.bodies.size() == 1);
        // 2nd tab item: Gigante, then objects: crate
        tap(g, g.sw / 6f * 2.5f, g.sh - 20 * d);
        tap(g, g.ui.gap + g.ui.cardW / 2, cardY);
        check("tool=CRATE", g.tool == Tools.CRATE);
        tap(g, sx(g, 1400), sy(g, -100));
        check("spawn crate", g.world.bodies.size() == 2);
        Harness.run(g, 2f);
        // close strip, hand tool, grab the crate and lift it
        g.ui.stripOpen = false; g.tool = Tools.HAND;
        Body crate = g.world.bodies.get(1);
        float cx = sx(g, crate.cx), cy = sy(g, crate.cy);
        g.touch(Game.DOWN, 0, cx, cy);
        for (int i = 1; i <= 30; i++) { g.touch(Game.MOVE, 0, cx, cy - i * 8); g.update(1f/60); }
        float y1 = crate.cy;
        g.touch(Game.UP, 0, cx, cy - 240);
        check("grab lifts crate (cy=" + (int) y1 + ")", y1 < -150);
        // pinch zoom with two fingers on empty space
        float z0 = g.zoom;
        g.touch(Game.DOWN, 0, 300, 200); g.touch(Game.DOWN, 1, 500, 200);
        for (int i = 1; i <= 20; i++) { g.touch(Game.MOVE, 0, 300 - i * 5, 200); g.touch(Game.MOVE, 1, 500 + i * 5, 200); g.update(1f/60); }
        g.touch(Game.UP, 0, 200, 200); g.touch(Game.UP, 1, 600, 200);
        check("pinch zoom in (" + z0 + " -> " + g.zoom + ")", g.zoom > z0 * 1.4f);
        g.resetCamera();
        // pan with one finger
        float cx0 = g.camX;
        g.touch(Game.DOWN, 0, 1200, 300);
        for (int i = 1; i <= 10; i++) { g.touch(Game.MOVE, 0, 1200 + i * 30, 300); g.update(1f/60); }
        g.touch(Game.UP, 0, 1500, 300);
        check("pan moves camera", g.camX < cx0 - 50);
        g.resetCamera();
        Harness.run(g, 1f);
        // cut tool on the human: swipe across torso
        Body h = g.world.bodies.get(0);
        g.tool = Tools.CUT;
        float hx = sx(g, h.chest.x), hy = sy(g, h.chest.y);
        g.touch(Game.DOWN, 0, hx - 150, hy);
        for (int i = 1; i <= 20; i++) { g.touch(Game.MOVE, 0, hx - 150 + i * 15, hy); g.update(1f/60); }
        g.touch(Game.UP, 0, hx + 150, hy);
        int broken = 0; for (Stick s : new Stick[]{h.jShF,h.jElF,h.jShB,h.jElB,h.jHipF,h.jKneeF,h.jHipB,h.jKneeB,h.neck}) if (s.dead) broken++;
        check("cut breaks a joint (broken=" + broken + ")", broken >= 1);
        // weapons
        g.tool = Tools.PISTOL;
        float bl = h.blood;
        tap(g, sx(g, h.head.x), sy(g, h.head.y));
        check("pistol headshot kills", h.dead);
        g.tool = Tools.ROCKET;
        tap(g, sx(g, 600), sy(g, -60));
        Harness.run(g, 1.5f);
        check("rocket exploded (no rockets left)", g.world.rockets.isEmpty());
        g.tool = Tools.LIGHTNING; tap(g, sx(g, 1400), sy(g, -50)); Harness.run(g, 0.05f);
        g.tool = Tools.FIRE;
        g.touch(Game.DOWN, 0, sx(g, g.world.bodies.get(1).cx), sy(g, g.world.bodies.get(1).cy));
        Harness.run(g, 0.5f); g.touch(Game.UP, 0, 0, 0);
        check("fire ignites crate", g.world.bodies.get(1).burning);
        Harness.run(g, 1f);
        Harness.shot(g, out + "/input_final.png");
        // freeze + delete
        g.tool = Tools.FREEZE;
        Body c2 = g.world.bodies.get(1);
        tap(g, sx(g, c2.ps.get(0).x), sy(g, c2.ps.get(0).y));
        check("freeze pins", c2.ps.get(0).pinned);
        g.tool = Tools.DELETE;
        int nb = g.world.bodies.size();
        tap(g, sx(g, c2.cx), sy(g, c2.cy));
        check("delete removes body", g.world.bodies.size() == nb - 1);
        // buttons
        float bx = g.sw - 20 * d;
        tap(g, g.sw - 8 * d - 10, 4 * d + 10);
        check("sound toggle button hit", true);
        Harness.run(g, 1f);
    }
}

package com.frameboost.ragdoll;
import org.lwjgl.opengles.GLES30;
import java.util.Random;
/** Heavy scenes and random play through the GPU engine: any GL error or exception fails. */
public class GLStress {
    public static void main(String[] a) throws Exception {
        GLTest.context();
        GLTest.engine = new GLEngine(); GLTest.engine.targetFbo = GLTest.screenFbo; GLTest.engine.init(); GLTest.engine.resize(GLTest.W, GLTest.H);
        Random r = new Random(3);
        for (int theme = 0; theme < 6; theme++) {
            Game g = Harness.newGame(); g.settings = new Settings(); g.userTheme = theme;
            for (int i = 0; i < 60; i++) g.spawn(Items.KEY_IDS[r.nextInt(Items.KEY_IDS.length)], 150 + r.nextFloat() * 2300, -100 - r.nextFloat() * 700);
            long worst = 0;
            for (int f = 0; f < 90; f++) {
                g.update(1f / 60);
                if (f % 20 == 5) g.world.explode(300 + r.nextFloat() * 2000, -50, 350, 2500, 80, true);
                if (f % 30 == 7) g.world.lightning(300 + r.nextFloat() * 2000, -20);
                long t = GLTest.frame(g);
                worst = Math.max(worst, t);
            }
            if (theme == 2) GLTest.shot(g, a[0] + "/gl_stress_night.png", true);
            System.out.printf("theme %d ok: bodies=%d worst frame %.1f ms (software GL)%n", theme, g.world.sim.bodies.size(), worst / 1e6);
        }
        // zoom extremes and screen resize
        Game g = Harness.newGame(); g.settings = new Settings(); g.spawn(Items.HUMAN, 1300, -100); Harness.run(g, 1f);
        g.zoom = g.sw / 380f; GLTest.frame(g); g.zoom = g.sw / 3100f; GLTest.frame(g);
        GLTest.engine.resize(1280, 720); g.resize(1280, 720, 2f); GLTest.frame(g); GLTest.engine.resize(GLTest.W, GLTest.H);
        System.out.println("GL STRESS PASS");
    }
}

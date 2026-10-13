package com.frameboost.ragdoll;

import android.graphics.Canvas;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengles.GLES;
import org.lwjgl.opengles.GLES30;
import org.lwjgl.system.MemoryStack;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import javax.imageio.ImageIO;

import static org.lwjgl.egl.EGL10.*;
import static org.lwjgl.egl.EGL14.*;

/** Runs the real GPU engine headless (Mesa llvmpipe) and saves frames: world on GL + Canvas UI on top. */
public class GLTest {
    static int W = 2400, H = 1080;
    static int screenFbo;

    static void context() {
        long dpy = eglGetDisplay(EGL_DEFAULT_DISPLAY);
        try (MemoryStack s = MemoryStack.stackPush()) {
            if (!eglInitialize(dpy, s.mallocInt(1), s.mallocInt(1))) throw new RuntimeException("egl init");
            eglBindAPI(EGL_OPENGL_ES_API);
            PointerBuffer cfg = s.mallocPointer(1); IntBuffer n = s.mallocInt(1);
            eglChooseConfig(dpy, s.ints(EGL_SURFACE_TYPE, 0, EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT, EGL_NONE), cfg, n);
            long ctx = eglCreateContext(dpy, cfg.get(0), EGL_NO_CONTEXT, s.ints(EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE));
            if (!eglMakeCurrent(dpy, EGL_NO_SURFACE, EGL_NO_SURFACE, ctx)) throw new RuntimeException("makeCurrent");
        }
        GLES.createCapabilities();
        // an off-screen "window"
        screenFbo = GLES30.glGenFramebuffers();
        int t = GLES30.glGenTextures();
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, t);
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, W, H, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, screenFbo);
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, t, 0);
    }

    static GLEngine engine;

    static long frame(Game g) {
        long t0 = System.nanoTime();
        g.setupEngine(engine);
        engine.begin();
        g.drawWorld(engine);
        engine.end();
        GLES30.glFinish();
        return System.nanoTime() - t0;
    }

    static void shot(Game g, String f, boolean overlay) throws Exception {
        frame(g);
        ByteBuffer px = org.lwjgl.BufferUtils.createByteBuffer(W * H * 4);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, screenFbo);
        GLES30.glReadPixels(0, 0, W, H, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, px);
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                int i = ((H - 1 - y) * W + x) * 4;
                img.setRGB(x, y, ((px.get(i) & 255) << 16) | ((px.get(i + 1) & 255) << 8) | (px.get(i + 2) & 255));
            }
        if (overlay) g.drawOverlay(new Canvas(img));
        ImageIO.write(img, "png", new File(f));
        System.out.println("wrote " + f);
    }

    public static void main(String[] a) throws Exception {
        String out = a[0];
        context();
        engine = new GLEngine();
        engine.targetFbo = screenFbo;
        engine.init();
        engine.resize(W, H);
        System.out.println("engine ready");

        Game g = Harness.newGame();
        g.settings = new Settings();
        g.spawn(Items.HUMAN, 900, -100); g.spawn(Items.ZOMBIE, 1150, -100); g.spawn(Items.ROBOT, 1400, -100);
        g.spawn(Items.CRATE, 1650, -100); g.spawn(Items.BALL, 1800, -100); g.spawn(Items.TNT, 1950, -60);
        g.spawn(Items.MOTOR, 600, -60); g.spawn(Items.THRUSTER, 750, -40);
        Harness.run(g, 2.5f);
        shot(g, out + "/gl_room.png", true);
        // perf
        long tot = 0; for (int i = 0; i < 20; i++) tot += frame(g);
        System.out.printf("gl frame (llvmpipe, software): %.1f ms%n", tot / 20 / 1e6);

        // fire + explosion at night
        g.userTheme = 2;
        for (Ent e : new java.util.ArrayList<>(g.world.ents)) if (e.item == Items.CRATE) g.world.ignite(e);
        Harness.run(g, 1.0f);
        g.world.explode(1300, -40, 300, 2000, 20, true);
        Harness.run(g, 0.12f);
        shot(g, out + "/gl_night_boom.png", true);
        Harness.run(g, 1.2f);
        shot(g, out + "/gl_night_fire.png", false);

        // neon theme with lightning
        g.userTheme = 4;
        g.world.lightning(1000, -30);
        Harness.run(g, 0.03f);
        shot(g, out + "/gl_neon.png", false);
        System.out.println("done");
    }
}

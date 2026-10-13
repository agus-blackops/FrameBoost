package com.frameboost.ragdoll;

import android.content.Context;
import android.graphics.Canvas;
import android.opengl.GLSurfaceView;
import android.view.MotionEvent;
import android.view.View;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * GPU path. The GL thread runs the simulation and renders the world through GLEngine; the interface lives in
 * a transparent Canvas view on top (OverlayView), which also receives the touches. Both sides lock the Game.
 */
final class GLGameView extends GLSurfaceView implements GLSurfaceView.Renderer {
    interface Listener { void onEngineFailed(String why); }

    final Game game;
    private final GLEngine engine = new GLEngine();
    private final Listener listener;
    private volatile boolean failed;
    private long last;

    GLGameView(Context ctx, Game game, Listener listener) {
        super(ctx);
        this.game = game;
        this.listener = listener;
        setEGLContextClientVersion(2);
        setPreserveEGLContextOnPause(true);
        setRenderer(this);
        setRenderMode(RENDERMODE_CONTINUOUSLY);
    }

    private void fail(Throwable t) {
        if (failed) return;
        failed = true;
        final String why = t.getMessage() != null ? t.getMessage() : t.toString();
        post(new Runnable() { @Override public void run() { listener.onEngineFailed(why); } });
    }

    @Override public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        try { engine.init(); } catch (Throwable t) { fail(t); }
    }

    @Override public void onSurfaceChanged(GL10 unused, int w, int h) {
        if (failed) return;
        try { engine.resize(w, h); } catch (Throwable t) { fail(t); }
    }

    @Override public void onDrawFrame(GL10 unused) {
        if (failed) return;
        long now = System.nanoTime();
        float dt = last == 0 ? 1f / 60f : (now - last) / 1e9f;
        last = now;
        try {
            synchronized (game) {
                if (game.sw == 0) return;
                game.update(dt);
                game.setupEngine(engine);
                engine.begin();
                game.drawWorld(engine);
            }
            engine.end();
        } catch (Throwable t) {
            fail(t);
        }
    }

    /** Transparent interface layer: menus, buttons, texts, aiming helpers. */
    static final class OverlayView extends View {
        private final Game game;

        OverlayView(Context ctx, Game game) {
            super(ctx);
            this.game = game;
            setKeepScreenOn(true);
            setFocusable(true);
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            synchronized (game) { game.resize(w, h, getResources().getDisplayMetrics().density); }
        }

        @Override protected void onDraw(Canvas canvas) {
            synchronized (game) { game.drawOverlay(canvas); }
            postInvalidateOnAnimation();
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            synchronized (game) { GameView.dispatch(game, e); }
            return true;
        }
    }
}

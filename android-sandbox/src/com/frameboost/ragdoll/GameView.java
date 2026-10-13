package com.frameboost.ragdoll;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;

/** Canvas path (fallback engine): runs the loop from onDraw and forwards touches. */
final class GameView extends View {
    final Game game;
    private long last;

    GameView(Context ctx, Game game) {
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
        long now = System.nanoTime();
        float dt = last == 0 ? 1f / 60f : (now - last) / 1e9f;
        last = now;
        synchronized (game) {
            game.update(dt);
            game.draw(canvas);
        }
        postInvalidateOnAnimation();
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        synchronized (game) { dispatch(game, e); }
        return true;
    }

    /** Multi-touch MotionEvent -> Game.touch calls (shared by both engines). */
    static void dispatch(Game game, MotionEvent e) {
        final int act = e.getActionMasked();
        switch (act) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int i = e.getActionIndex();
                game.touch(Game.DOWN, e.getPointerId(i), e.getX(i), e.getY(i));
                break;
            }
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < e.getPointerCount(); i++) game.touch(Game.MOVE, e.getPointerId(i), e.getX(i), e.getY(i));
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                int i = e.getActionIndex();
                game.touch(Game.UP, e.getPointerId(i), e.getX(i), e.getY(i));
                break;
            }
            case MotionEvent.ACTION_CANCEL:
                for (int i = 0; i < e.getPointerCount(); i++) game.touch(Game.CANCEL, e.getPointerId(i), e.getX(i), e.getY(i));
                break;
            default:
                break;
        }
    }
}

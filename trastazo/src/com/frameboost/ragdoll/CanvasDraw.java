package com.frameboost.ragdoll;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/** Draw2D on top of a plain Android Canvas (fallback engine; also used by the desktop test harness). */
final class CanvasDraw implements Draw2D {
    Canvas c;
    private final Path path = new Path();

    CanvasDraw(Canvas c) { this.c = c; }

    @Override public void save() { c.save(); }
    @Override public void restore() { c.restore(); }
    @Override public void translate(float x, float y) { c.translate(x, y); }
    @Override public void scale(float sx, float sy) { c.scale(sx, sy); }
    @Override public void rotate(float d) { c.rotate(d); }
    @Override public void drawLine(float x0, float y0, float x1, float y1, Paint p) { c.drawLine(x0, y0, x1, y1, p); }
    @Override public void drawCircle(float x, float y, float r, Paint p) { c.drawCircle(x, y, r, p); }
    @Override public void drawRect(float l, float t, float r, float b, Paint p) { c.drawRect(l, t, r, b, p); }
    @Override public void drawRect(RectF r, Paint p) { c.drawRect(r, p); }
    @Override public void drawOval(RectF r, Paint p) { c.drawOval(r, p); }
    @Override public void drawRoundRect(RectF r, float rx, float ry, Paint p) { c.drawRoundRect(r, rx, ry, p); }
    @Override public void drawArc(RectF r, float s, float w, boolean center, Paint p) { c.drawArc(r, s, w, center, p); }
    @Override public void drawPoly(float[] xy, int n, Paint p) {
        path.reset();
        for (int i = 0; i < n; i++) { if (i == 0) path.moveTo(xy[0], xy[1]); else path.lineTo(xy[i * 2], xy[i * 2 + 1]); }
        path.close();
        c.drawPath(path, p);
    }
    @Override public void emissive(boolean on) { }
    @Override public void light(float x, float y, float radius, int color, float intensity) { }
    @Override public boolean hasLighting() { return false; }
    @Override public void ripple(float x, float y, float radius, float strength) { }
}

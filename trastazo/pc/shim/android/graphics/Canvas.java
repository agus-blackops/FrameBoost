package android.graphics;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.HashMap;

/** Desktop version of android.graphics.Canvas: draws into a BufferedImage with Java2D. */
public class Canvas {
    public final BufferedImage img;
    private final Graphics2D g;
    private final ArrayDeque<AffineTransform> tstack = new ArrayDeque<>();
    private final ArrayDeque<Shape> cstack = new ArrayDeque<>();
    private final HashMap<Long, BasicStroke> strokes = new HashMap<>();
    private final Line2D.Float line = new Line2D.Float();
    private final Ellipse2D.Float ell = new Ellipse2D.Float();
    private final Rectangle2D.Float rect = new Rectangle2D.Float();
    private final RoundRectangle2D.Float rrect = new RoundRectangle2D.Float();
    private final Arc2D.Float arc = new Arc2D.Float();
    private int lastColor = 1;
    private Color lastAwt;

    public Canvas(BufferedImage img) {
        this.img = img;
        g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    public void dispose() { g.dispose(); }

    /** Low quality mode (PC "Calidad: Ahorro"): no anti-aliasing, roughly twice as fast in software. */
    public void setAntiAlias(boolean on) {
        aa = on;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, on ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, on ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);
    }

    private Color col(int c) {
        if (c != lastColor || lastAwt == null) { lastColor = c; lastAwt = new Color((c >> 16) & 255, (c >> 8) & 255, c & 255, (c >>> 24) & 255); }
        return lastAwt;
    }

    private void stroke(Paint p) {
        int cap = p.cap == Paint.Cap.ROUND ? BasicStroke.CAP_ROUND : (p.cap == Paint.Cap.SQUARE ? BasicStroke.CAP_SQUARE : BasicStroke.CAP_BUTT);
        float w = Math.max(0.01f, p.strokeWidth);
        long key = ((long) Float.floatToIntBits(w) << 2) | cap;
        BasicStroke s = strokes.get(key);
        if (s == null) { if (strokes.size() > 512) strokes.clear(); s = new BasicStroke(w, cap, BasicStroke.JOIN_ROUND); strokes.put(key, s); }
        g.setStroke(s);
        g.setColor(col(p.color));
    }

    private void paintShape(Shape s, Paint p) {
        if (p.style == Paint.Style.STROKE) { stroke(p); g.draw(s); } else { g.setColor(col(p.color)); g.fill(s); }
    }

    public void drawColor(int c) { Shape cl = g.getClip(); g.setClip(null); AffineTransform t = g.getTransform(); g.setTransform(new AffineTransform()); g.setColor(col(c)); g.fillRect(0, 0, img.getWidth(), img.getHeight()); g.setTransform(t); g.setClip(cl); }
    public int save() { tstack.push(g.getTransform()); cstack.push(g.getClip() == null ? new Rectangle(-1000000, -1000000, 2000000, 2000000) : g.getClip()); return tstack.size(); }
    public void restore() { g.setTransform(tstack.pop()); g.setClip(cstack.pop()); }
    public void translate(float x, float y) { g.translate(x, y); }
    public void scale(float x, float y) { g.scale(x, y); }
    public void rotate(float deg) { g.rotate(Math.toRadians(deg)); }
    public void clipRect(float l, float t, float r, float b) { g.clip(new Rectangle2D.Float(l, t, r - l, b - t)); }
    private boolean aa = true;

    /** Axis-aligned rectangles and lines need no anti-aliasing: skipping it makes big background fills much cheaper. */
    private boolean axisAligned() { AffineTransform t = g.getTransform(); return t.getShearX() == 0 && t.getShearY() == 0; }
    private void noAA() { if (aa) g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF); }
    private void reAA() { if (aa) g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); }

    public void drawRect(float l, float t, float r, float b, Paint p) {
        rect.setRect(l, t, r - l, b - t);
        if (p.style != Paint.Style.STROKE && axisAligned()) { noAA(); paintShape(rect, p); reAA(); }
        else paintShape(rect, p);
    }
    public void drawRect(RectF r, Paint p) { drawRect(r.left, r.top, r.right, r.bottom, p); }
    public void drawLine(float x0, float y0, float x1, float y1, Paint p) {
        stroke(p); line.setLine(x0, y0, x1, y1);
        if (p.cap != Paint.Cap.ROUND && (x0 == x1 || y0 == y1) && axisAligned()) { noAA(); g.draw(line); reAA(); }
        else g.draw(line);
    }
    public void drawCircle(float x, float y, float r, Paint p) { ell.setFrame(x - r, y - r, r * 2, r * 2); paintShape(ell, p); }
    public void drawOval(RectF r, Paint p) { ell.setFrame(r.left, r.top, r.right - r.left, r.bottom - r.top); paintShape(ell, p); }
    public void drawRoundRect(RectF r, float rx, float ry, Paint p) { rrect.setRoundRect(r.left, r.top, r.right - r.left, r.bottom - r.top, rx * 2, ry * 2); paintShape(rrect, p); }
    public void drawArc(RectF r, float start, float sweep, boolean center, Paint p) {
        arc.setArc(r.left, r.top, r.right - r.left, r.bottom - r.top, -start, -sweep, center ? Arc2D.PIE : Arc2D.OPEN);
        paintShape(arc, p);
    }
    public void drawPath(Path path, Paint p) { paintShape(path.p, p); }
    public void drawText(String s, float x, float y, Paint p) {
        g.setFont(Paint.font(p.textSize, p.bold));
        float w = p.measureText(s);
        if (p.align == Paint.Align.CENTER) x -= w / 2; else if (p.align == Paint.Align.RIGHT) x -= w;
        g.setColor(col(p.color));
        g.drawString(s, x, y);
    }
}

package android.graphics;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;

/** Desktop stand-in for android.graphics.Canvas using Java2D (test harness only). */
public class Canvas {
    public final BufferedImage img;
    private final Graphics2D g;
    private final ArrayDeque<AffineTransform> tstack = new ArrayDeque<>();
    private final ArrayDeque<Shape> cstack = new ArrayDeque<>();

    public Canvas(BufferedImage img) {
        this.img = img;
        g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    private static Color col(int c) { return new Color((c >> 16) & 255, (c >> 8) & 255, c & 255, (c >>> 24) & 255); }

    private void stroke(Paint p) {
        int cap = p.cap == Paint.Cap.ROUND ? BasicStroke.CAP_ROUND : (p.cap == Paint.Cap.SQUARE ? BasicStroke.CAP_SQUARE : BasicStroke.CAP_BUTT);
        g.setStroke(new BasicStroke(Math.max(0.01f, p.strokeWidth), cap, BasicStroke.JOIN_ROUND));
        g.setColor(col(p.color));
    }

    private void paintShape(Shape s, Paint p) {
        g.setColor(col(p.color));
        if (p.style == Paint.Style.STROKE) { stroke(p); g.draw(s); } else g.fill(s);
    }

    public void drawColor(int c) { Shape cl = g.getClip(); g.setClip(null); AffineTransform t = g.getTransform(); g.setTransform(new AffineTransform()); g.setColor(col(c)); g.fillRect(0, 0, img.getWidth(), img.getHeight()); g.setTransform(t); g.setClip(cl); }
    public int save() { tstack.push(g.getTransform()); cstack.push(g.getClip() == null ? new Rectangle(-1000000, -1000000, 2000000, 2000000) : g.getClip()); return tstack.size(); }
    public void restore() { g.setTransform(tstack.pop()); g.setClip(cstack.pop()); }
    public void translate(float x, float y) { g.translate(x, y); }
    public void scale(float x, float y) { g.scale(x, y); }
    public void rotate(float deg) { g.rotate(Math.toRadians(deg)); }
    public void clipRect(float l, float t, float r, float b) { g.clip(new Rectangle2D.Float(l, t, r - l, b - t)); }
    public void drawRect(float l, float t, float r, float b, Paint p) { paintShape(new Rectangle2D.Float(l, t, r - l, b - t), p); }
    public void drawRect(RectF r, Paint p) { drawRect(r.left, r.top, r.right, r.bottom, p); }
    public void drawLine(float x0, float y0, float x1, float y1, Paint p) { stroke(p); g.draw(new Line2D.Float(x0, y0, x1, y1)); }
    public void drawCircle(float x, float y, float r, Paint p) { paintShape(new Ellipse2D.Float(x - r, y - r, r * 2, r * 2), p); }
    public void drawOval(RectF r, Paint p) { paintShape(new Ellipse2D.Float(r.left, r.top, r.right - r.left, r.bottom - r.top), p); }
    public void drawRoundRect(RectF r, float rx, float ry, Paint p) { paintShape(new RoundRectangle2D.Float(r.left, r.top, r.right - r.left, r.bottom - r.top, rx * 2, ry * 2), p); }
    public void drawArc(RectF r, float start, float sweep, boolean center, Paint p) {
        paintShape(new Arc2D.Float(r.left, r.top, r.right - r.left, r.bottom - r.top, -start, -sweep, center ? Arc2D.PIE : Arc2D.OPEN), p);
    }
    public void drawPath(Path path, Paint p) { paintShape(path.p, p); }
    public void drawText(String s, float x, float y, Paint p) {
        g.setFont(new Font("SansSerif", p.bold ? Font.BOLD : Font.PLAIN, Math.max(1, Math.round(p.textSize))));
        float w = p.measureText(s);
        if (p.align == Paint.Align.CENTER) x -= w / 2; else if (p.align == Paint.Align.RIGHT) x -= w;
        g.setColor(col(p.color));
        g.drawString(s, x, y);
    }
}

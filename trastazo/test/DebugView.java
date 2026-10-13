package com.frameboost.ragdoll;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Plain Java2D drawing of a Sim (for physics tuning without the game renderer). */
public class DebugView {
    public static void draw(Graphics2D g, Sim s, float camX, float camY, float zoom, int w, int h) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0xdfe6ee)); g.fillRect(0, 0, w, h);
        g.setColor(new Color(0x59616e));
        int fy = (int) ((s.floorY - camY) * zoom + h / 2f);
        g.fillRect(0, fy, w, h - fy);
        for (RB b : s.bodies) for (Shape sh : b.shapes) {
            g.setColor(new Color(sh.color | 0xFF000000));
            float cx = (sh.cx - camX) * zoom + w / 2f, cy = (sh.cy - camY) * zoom + h / 2f;
            switch (sh.type) {
                case Shape.CIRCLE: g.fill(new Ellipse2D.Float(cx - sh.r * zoom, cy - sh.r * zoom, sh.r * 2 * zoom, sh.r * 2 * zoom)); break;
                case Shape.CAPSULE: {
                    g.setStroke(new BasicStroke(sh.r * 2 * zoom, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.draw(new Line2D.Float(cx - sh.ax * sh.hl * zoom, cy - sh.ay * sh.hl * zoom, cx + sh.ax * sh.hl * zoom, cy + sh.ay * sh.hl * zoom));
                    break;
                }
                default: {
                    Path2D p = new Path2D.Float();
                    float ux = sh.ax * sh.hl, uy = sh.ay * sh.hl, vx = -sh.ay * sh.hh, vy = sh.ax * sh.hh;
                    float[][] c = {{-ux - vx, -uy - vy}, {ux - vx, uy - vy}, {ux + vx, uy + vy}, {-ux + vx, -uy + vy}};
                    for (int i = 0; i < 4; i++) { float x = cx + c[i][0] * zoom, y = cy + c[i][1] * zoom; if (i == 0) p.moveTo(x, y); else p.lineTo(x, y); }
                    p.closePath(); g.fill(p);
                }
            }
        }
        g.setColor(Color.RED);
        for (Joint j : s.joints) {
            float x = (j.anchorBX() - camX) * zoom + w / 2f, y = (j.anchorBY() - camY) * zoom + h / 2f;
            g.fill(new Ellipse2D.Float(x - 2, y - 2, 4, 4));
        }
    }

    /** Writes a strip of snapshots side by side. */
    public static void strip(java.util.List<BufferedImage> frames, String file) throws Exception {
        int w = 0, h = 0;
        for (BufferedImage f : frames) { w += f.getWidth(); h = Math.max(h, f.getHeight()); }
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        int x = 0;
        for (BufferedImage f : frames) { g.drawImage(f, x, 0, null); x += f.getWidth(); }
        ImageIO.write(out, "png", new File(file));
    }

    public static BufferedImage snap(Sim s, float camX, float camY, float zoom, int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        draw(g, s, camX, camY, zoom, w, h);
        g.setColor(Color.BLACK);
        g.drawRect(0, 0, w - 1, h - 1);
        return img;
    }
}

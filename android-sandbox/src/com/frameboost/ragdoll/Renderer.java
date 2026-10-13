package com.frameboost.ragdoll;

import android.graphics.Paint;
import android.graphics.RectF;

/** Draws the world (everything in world coordinates). */
final class Renderer {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private final float[] poly = new float[16];

    int theme = 0;
    boolean detail = true;
    boolean gore = true;
    Body inspect;            // person under the Inspect tool (damage overlay)
    private static final int[] WALL = {0xFF2f3846, 0xFFdde5ea, 0xFF0f1630, 0xFF6b3b32, 0xFF0a0612, 0xFFe8c98a};
    private static final int[] GRIDC = {0xFF3a4455, 0xFFc3ced6, 0xFF1a2348, 0xFF4f2a24, 0xFF7a1fa8, 0xFFd1ac6a};
    private static final int[] FLOORC = {0xFF4c525d, 0xFFb8c2c9, 0xFF1c2230, 0xFF5a3e28, 0xFF14091f, 0xFFc79a55};
    private static final int[] FLOORTOP = {0xFF666d7a, 0xFFf2f6f8, 0xFF3a4258, 0xFF7a5636, 0xFF00e5ff, 0xFFe6b867};
    private static final int[] FLOORLINE = {0xFF3b404a, 0xFFa3aeb6, 0xFF151a26, 0xFF45301f, 0xFFff2bd6, 0xFFb08440};

    Renderer() {
        p.setStrokeCap(Paint.Cap.ROUND);
    }

    // --------------------------------------------------------------------------- colour utils ---

    static int mix(int a, int b, float t) {
        if (t <= 0) return a;
        if (t >= 1) return b;
        int aa = (a >>> 24), ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = (b >>> 24), br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    static int alpha(int c, float a) {
        int al = (int) ((c >>> 24) * Math.max(0f, Math.min(1f, a)));
        return (al << 24) | (c & 0xFFFFFF);
    }

    static int dark(int c, float t) { return mix(c, 0xFF000000, t); }

    // ------------------------------------------------------------------------------------ main ---

    void draw(Draw2D c, World w, Fx fx, float camX, float camY, float zoom, int sw, int sh) {
        float shx = 0, shy = 0;
        if (fx.shake > 0) {
            float a = fx.shake * fx.shake * 26f;
            shx = (fx.rnd.nextFloat() - 0.5f) * a;
            shy = (fx.rnd.nextFloat() - 0.5f) * a;
        }
        c.save();
        c.translate(sw / 2f + shx, sh / 2f + shy);
        c.scale(zoom, zoom);
        c.translate(-camX, -camY);

        float vx0 = camX - sw / 2f / zoom - 20, vx1 = camX + sw / 2f / zoom + 20;
        float vy0 = camY - sh / 2f / zoom - 20, vy1 = camY + sh / 2f / zoom + 20;
        drawBackground(c, w, vx0, vy0, vx1, vy1);
        drawStains(c, w, fx);
        drawShadows(c, w);

        for (int i = 0; i < w.bodies.size(); i++) {
            Body b = w.bodies.get(i);
            if (b.isHuman()) continue;
            drawProp(c, b);
        }
        for (int i = 0; i < w.bodies.size(); i++) {
            Body b = w.bodies.get(i);
            if (b.isHuman()) drawHuman(c, b);
        }
        drawLinks(c, w);
        drawPins(c, w);
        drawFx(c, w, fx);
        c.restore();
    }

    // ------------------------------------------------------------------------------ background ---

    private void drawBackground(Draw2D c, World w, float vx0, float vy0, float vx1, float vy1) {
        p.setStyle(Paint.Style.FILL);
        int t = Math.max(0, Math.min(WALL.length - 1, theme));
        p.setColor(0xFF10131a);
        rf.set(vx0 - 50, vy0 - 50, vx1 + 50, vy1 + 50);
        c.drawRect(rf, p);
        float x0 = Math.max(w.wallL, vx0 - 1), x1 = Math.min(w.wallR, vx1 + 1);
        float top = Math.max(w.ceilY, vy0 - 1);
        p.setColor(WALL[t]);
        c.drawRect(x0, top, x1, w.floorY, p);

        if (t == 2 && detail) {                    // night: sky gradient bands, stars, moon
            p.setColor(0x2210204a);
            c.drawRect(x0, w.floorY - 520, x1, w.floorY, p);
            p.setColor(0xFFdfe6ff);
            for (int i = 0; i < 160; i++) {
                float sx = (i * 7919 % 2600), sy = w.floorY - 80 - ((i * 104729) % 1500);
                if (sx < x0 || sx > x1 || sy < top || sy > vy1) continue;
                c.drawCircle(sx, sy, 1.5f + (i % 3), p);
            }
            p.setColor(0xFFf5f0d0);
            c.drawCircle(2000, w.floorY - 640, 70, p);
            p.setColor(0xFF0f1630);
            c.drawCircle(2030, w.floorY - 655, 62, p);
        } else if (t == 5 && detail) {             // desert: sun + dunes
            p.setColor(0xFFfff0b0);
            c.drawCircle(1850, w.floorY - 520, 120, p);
            p.setColor(0xFFf3b25a);
            c.drawCircle(1850, w.floorY - 520, 85, p);
            p.setColor(0xFFd8aa62);
            for (int i = 0; i < 4; i++) c.drawCircle(300 + i * 750, w.floorY + 80, 420 + (i % 2) * 120, p);
        }
        // grid / brick pattern
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.BUTT);
        if (t == 3 && detail) {                    // bricks
            p.setColor(GRIDC[t]);
            p.setStrokeWidth(3f);
            int row = 0;
            for (float gy = (float) Math.floor(w.floorY / 44f) * 44f; gy >= top; gy -= 44f, row++) {
                c.drawLine(x0, gy, x1, gy, p);
                float off = (row & 1) == 0 ? 0 : 60;
                for (float gx = (float) Math.floor(x0 / 120f) * 120f + off; gx <= x1; gx += 120f) {
                    if (gx >= x0) c.drawLine(gx, gy, gx, gy - 44f, p);
                }
            }
        } else {
            p.setColor(GRIDC[t]);
            p.setStrokeWidth(t == 4 ? 3f : 2f);
            float step = 100f;
            float gx = (float) Math.ceil(x0 / step) * step;
            for (; gx <= x1; gx += step) c.drawLine(gx, top, gx, w.floorY, p);
            float gy = (float) Math.floor(w.floorY / step) * step;
            for (; gy >= top; gy -= step) c.drawLine(x0, gy, x1, gy, p);
            if (t == 4) {                           // neon glow lines
                p.setColor(0x5500e5ff);
                p.setStrokeWidth(8f);
                for (gy = (float) Math.floor(w.floorY / 300f) * 300f; gy >= top; gy -= 300f) c.drawLine(x0, gy, x1, gy, p);
            }
        }
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x33000000);
        if (vx0 < w.wallL + 60) c.drawRect(w.wallL, top, w.wallL + 60, w.floorY, p);
        if (vx1 > w.wallR - 60) c.drawRect(w.wallR - 60, top, w.wallR, w.floorY, p);
        // floor
        if (vy1 > w.floorY) {
            p.setColor(FLOORC[t]);
            c.drawRect(x0, w.floorY, x1, vy1 + 100, p);
            if (t == 4) { p.setColor(0x4400e5ff); c.drawRect(x0, w.floorY, x1, w.floorY + 26, p); }
            p.setColor(FLOORTOP[t]);
            c.emissive(t == 4);
            c.drawRect(x0, w.floorY, x1, w.floorY + (t == 4 ? 5 : 9), p);
            c.emissive(false);
            p.setStyle(Paint.Style.STROKE);
            p.setColor(FLOORLINE[t]);
            p.setStrokeWidth(3f);
            if (detail && (t == 1 || t == 5 || t == 0)) {      // tiles / planks
                for (float fy = w.floorY + 60; fy < vy1 + 100; fy += 60) c.drawLine(x0, fy, x1, fy, p);
                if (t == 1) for (float fx = (float) Math.ceil(x0 / 140f) * 140f; fx <= x1; fx += 140f) c.drawLine(fx, w.floorY, fx, vy1 + 100, p);
            } else if (detail && t == 3) {                       // wooden boards
                for (float fy = w.floorY + 50; fy < vy1 + 100; fy += 50) c.drawLine(x0, fy, x1, fy, p);
                int i = 0;
                for (float fx = (float) Math.ceil(x0 / 190f) * 190f; fx <= x1; fx += 190f, i++)
                    c.drawLine(fx + (i % 3) * 40, w.floorY, fx + (i % 3) * 40, w.floorY + 50, p);
            } else if (t == 4) {
                for (float fx = (float) Math.ceil(x0 / 120f) * 120f; fx <= x1; fx += 120f) c.drawLine(fx, w.floorY, fx - (fx - 1300f) * 0.25f, vy1 + 100, p);
            }
            p.setStyle(Paint.Style.FILL);
        }
        p.setColor(0xFF0e1117);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(8f);
        if (vx0 < w.wallL) c.drawLine(w.wallL, top, w.wallL, vy1 + 100, p);
        if (vx1 > w.wallR) c.drawLine(w.wallR, top, w.wallR, vy1 + 100, p);
        p.setStyle(Paint.Style.FILL);
    }

    /** Soft contact shadows on the floor: they ground everything visually. */
    private void drawShadows(Draw2D c, World w) {
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < w.bodies.size(); i++) {
            Body b = w.bodies.get(i);
            if (b.ps.isEmpty()) continue;
            float bottom = b.cy + b.rad * 0.5f;
            float h = w.floorY - bottom;
            if (h > 420f || h < -60f) continue;
            float k = 1f - Math.max(0f, h) / 420f;
            float wd = Math.min(b.rad, 120f) * (0.75f + 0.35f * k);
            p.setColor(alpha(0xFF000000, 0.32f * k));
            rf.set(b.cx - wd, w.floorY - 3f, b.cx + wd, w.floorY + 7f);
            c.drawOval(rf, p);
        }
    }

    private void drawStains(Draw2D c, World w, Fx fx) {
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < fx.stCount; i++) {
            float wd = fx.stw[i];
            p.setColor(fx.stc[i]);
            rf.set(fx.stx[i] - wd, w.floorY - 1.5f, fx.stx[i] + wd, w.floorY + wd * 0.38f + 2f);
            c.drawOval(rf, p);
        }
    }

    // ------------------------------------------------------------------------------------ props ---

    private int bodyColor(Body b, int col) {
        if (b.charT > 0) col = mix(col, 0xFF1b1b1b, b.charT);
        if (b.flashT > 0) col = mix(col, 0xFFffffff, b.flashT / 0.14f * 0.65f);
        if (b.fuse >= 0f && b.fuse < 99f) {
            float rate = b.fuse < 1f ? 14f : 6f;
            if (((int) (b.fuse * rate) & 1) == 0) col = mix(col, 0xFFffffff, 0.55f);
        }
        return col;
    }

    private void drawProp(Draw2D c, Body b) {
        if (b.type == Body.BALL || b.type == Body.BALLOON || b.type == Body.WHEEL || b.type == Body.BOWLING || b.type == Body.MELON) { drawBall(c, b); return; }
        // outline pass, then fill pass (so touching sticks merge into one silhouette)
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        int n = b.ss.size();
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < n; i++) {
                Stick s = b.ss.get(i);
                if (s.dead || s.kind != Stick.FILL || s.joint || s.weld) continue;
                int col = bodyColor(b, s.color != 0 ? s.color : b.color);
                if (pass == 0) { p.setColor(dark(col, 0.55f)); p.setStrokeWidth(s.r * 2 + 3.4f); }
                else { p.setColor(col); p.setStrokeWidth(s.r * 2); }
                c.drawLine(s.a.x, s.a.y, s.b.x, s.b.y, p);
            }
        }
        // details
        int detailCol = dark(bodyColor(b, b.color), 0.4f);
        p.setColor(detailCol);
        p.setStrokeWidth(2.2f);
        for (int i = 0; i < n; i++) {
            Stick s = b.ss.get(i);
            if (s.dead || !s.band) continue;
            c.drawLine(s.a.x, s.a.y, s.b.x, s.b.y, p);
        }
        if (detail) textures(c, b);
        overlay(c, b);
    }

    /** Material textures: wood grain, steel rivets. */
    private void textures(Draw2D c, Body b) {
        int n = b.ss.size();
        boolean wood = b.type == Body.CRATE || b.type == Body.PLANK || b.type == Body.TNT;
        if (wood) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.BUTT);
            p.setStrokeWidth(1.3f);
            p.setColor(alpha(dark(bodyColor(b, b.color), 0.5f), 0.55f));
            for (int i = 0; i < n; i++) {
                Stick s = b.ss.get(i);
                if (s.dead || s.kind != Stick.FILL || s.joint || s.band) continue;
                float dx = s.b.x - s.a.x, dy = s.b.y - s.a.y;
                float l = (float) Math.sqrt(dx * dx + dy * dy);
                if (l < 1f) continue;
                float nx = -dy / l * s.r * 0.5f, ny = dx / l * s.r * 0.5f;
                c.drawLine(s.a.x + nx, s.a.y + ny, s.b.x + nx, s.b.y + ny, p);
                c.drawLine(s.a.x - nx, s.a.y - ny, s.b.x - nx, s.b.y - ny, p);
            }
            p.setStrokeCap(Paint.Cap.ROUND);
        } else if (b.type == Body.HEAVY || b.type == Body.PLATFORM) {
            p.setStyle(Paint.Style.FILL);
            for (int i = 0; i < b.ps.size(); i += (b.type == Body.PLATFORM ? 2 : 1)) {
                Particle q = b.ps.get(i);
                p.setColor(dark(bodyColor(b, b.color), 0.45f));
                c.drawCircle(q.x, q.y, 2.9f, p);
                p.setColor(0x66ffffff);
                c.drawCircle(q.x - 0.8f, q.y - 0.8f, 1.2f, p);
            }
        }
    }

    private void overlay(Draw2D c, Body b) {
        if (b.ps.isEmpty()) return;
        switch (b.type) {
            case Body.GUN: {
                if (b.ps.size() < 2) break;
                Particle a = b.ps.get(0), z = b.ps.get(1);
                float dx = z.x - a.x, dy = z.y - a.y, l = (float) Math.sqrt(dx * dx + dy * dy) + 1e-3f;
                float ux = dx / l, uy = dy / l, nx = -uy, ny = ux;
                // grip below the back of the barrel (towards +n), magazine for the machine gun
                float s = b.gunKind == 2 ? 1.4f : 1f;
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setColor(0xFF2b2b30);
                p.setStrokeWidth(7f * s);
                float gx = a.x + ux * 5f, gy = a.y + uy * 5f;
                c.drawLine(gx, gy, gx + nx * 13f * s - ux * 3f, gy + ny * 13f * s - uy * 3f, p);
                if (b.gunKind == 2) {
                    p.setColor(0xFF6a5a2a);
                    p.setStrokeWidth(9f);
                    float mx = a.x + ux * l * 0.5f, my = a.y + uy * l * 0.5f;
                    c.drawLine(mx, my, mx + nx * 16f, my + ny * 16f, p);
                }
                if (b.gunKind == 1) {       // scope
                    p.setColor(0xFF22252a);
                    p.setStrokeWidth(5f);
                    float sx = a.x + ux * l * 0.35f - nx * 8f, sy = a.y + uy * l * 0.35f - ny * 8f;
                    c.drawLine(sx, sy, sx + ux * 16f, sy + uy * 16f, p);
                }
                p.setColor(0xFF15171a);       // muzzle
                p.setStrokeWidth(b.ps.get(1).r * 1.4f);
                c.drawLine(z.x - ux * 3f, z.y - uy * 3f, z.x + ux * 2f, z.y + uy * 2f, p);
                if (b.firing) { p.setStyle(Paint.Style.FILL); p.setColor(0xFFff5a3a); c.drawCircle(a.x, a.y, 3f, p); }
                p.setStyle(Paint.Style.FILL);
                break;
            }
            case Body.C4: {
                if (b.ps.size() < 2) break;
                Particle a = b.ps.get(0), z = b.ps.get(1);
                float mx = (a.x + z.x) / 2, my = (a.y + z.y) / 2;
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2f);
                p.setColor(0xFF3a3d20);
                c.drawLine(a.x, a.y, z.x, z.y, p);
                boolean on = ((int) (System.currentTimeMillis() / 400) & 1) == 0;
                p.setStyle(Paint.Style.FILL);
                p.setColor(on ? 0xFFff3b30 : 0xFF5a1a16);
                c.emissive(on);
                c.drawCircle(mx, my - 4f, 2.6f, p);
                c.emissive(false);
                if (on) c.light(mx, my - 4f, 60f, 0xFFff3b30, 0.6f);
                break;
            }
            case Body.ANCHOR: {
                Particle a = b.ps.get(0);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFF2b2f36);
                c.drawCircle(a.x, a.y, 8f, p);
                p.setColor(0xFF9aa3ad);
                c.drawCircle(a.x, a.y, 6f, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.6f);
                p.setColor(0xFF4a525c);
                c.drawLine(a.x - 3.5f, a.y, a.x + 3.5f, a.y, p);
                c.drawLine(a.x, a.y - 3.5f, a.x, a.y + 3.5f, p);
                break;
            }
            case Body.THRUSTER: {
                if (b.ps.size() < 2) break;
                Particle a = b.ps.get(0), z = b.ps.get(1);
                float dx = z.x - a.x, dy = z.y - a.y, l = (float) Math.sqrt(dx * dx + dy * dy) + 1e-3f;
                float nx = -dy / l, ny = dx / l;
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFF3a3f48);                       // nozzle
                poly[0] = a.x + nx * 5; poly[1] = a.y + ny * 5;
                poly[2] = a.x - nx * 5; poly[3] = a.y - ny * 5;
                poly[4] = a.x - dx / l * 10 - nx * 10; poly[5] = a.y - dy / l * 10 - ny * 10;
                poly[6] = a.x - dx / l * 10 + nx * 10; poly[7] = a.y - dy / l * 10 + ny * 10;
                c.drawPoly(poly, 4, p);
                p.setColor(0xFFd33a2c);                       // red nose
                c.drawCircle(z.x, z.y, 7.5f, p);
                p.setColor(b.thrust ? 0xFF6dff8a : 0xFF555b66); // status light
                c.drawCircle((a.x + z.x) / 2, (a.y + z.y) / 2, 2.8f, p);
                break;
            }
            case Body.TNT: {
                label(c, b, "TNT", 0xFFfff2cf, 15f);
                break;
            }
            case Body.XBARREL: {
                label(c, b, "!", 0xFFffd23f, 22f);
                break;
            }
            case Body.GRENADE: {
                Particle a = b.ps.get(0);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(3f);
                p.setColor(0xFFb9bcc2);
                c.drawLine(a.x, a.y - 8, a.x + 5, a.y - 14, p);
                break;
            }
            case Body.DYNAMITE: {
                Particle a = b.ps.get(0), z = b.ps.get(1);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2.5f);
                p.setColor(0xFFeadfc4);
                float mx = (a.x + z.x) / 2, my = (a.y + z.y) / 2;
                c.drawLine(z.x, z.y, z.x + (z.x - mx) * 0.2f + 6, z.y - 9, p);
                break;
            }
            default: break;
        }
    }

    private void label(Draw2D c, Body b, String text, int col, float size) {
        Particle a = b.ps.get(0), z = b.ps.get(Math.min(2, b.ps.size() - 1));
        float ang = (float) Math.toDegrees(Math.atan2(z.y - a.y, z.x - a.x)) - 90f;
        float mx = 0, my = 0;
        for (int i = 0; i < b.ps.size(); i++) { mx += b.ps.get(i).x; my += b.ps.get(i).y; }
        mx /= b.ps.size(); my /= b.ps.size();
        c.save();
        c.translate(mx, my);
        c.rotate(ang);
        p.setColor(bodyColor(b, col));
        strokeText(c, text, size);
        c.restore();
    }

    /** Tiny vector font (only the glyphs the world needs), so labels look the same on every engine. */
    private void strokeText(Draw2D c, String text, float size) {
        float gw = size * 0.62f, gap = size * 0.22f;
        float total = text.length() * gw + (text.length() - 1) * gap;
        float x = -total / 2, y = -size / 2;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(size * 0.2f);
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            float[] g;
            switch (ch) {
                case 'T': g = new float[]{0, 0, 1, 0, 0.5f, 0, 0.5f, 1}; break;
                case 'N': g = new float[]{0, 1, 0, 0, 0, 0, 1, 1, 1, 1, 1, 0}; break;
                default: g = new float[]{0.5f, 0, 0.5f, 0.58f}; break;   // '!'
            }
            for (int k = 0; k + 3 < g.length; k += 4)
                c.drawLine(x + g[k] * gw, y + g[k + 1] * size, x + g[k + 2] * gw, y + g[k + 3] * size, p);
            if (ch == '!') {
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(x + 0.5f * gw, y + 0.92f * size, size * 0.11f, p);
                p.setStyle(Paint.Style.STROKE);
            }
            x += gw + gap;
        }
        p.setStyle(Paint.Style.FILL);
    }

    private void drawBall(Draw2D c, Body b) {
        if (b.ps.size() < 3) return;
        Particle ctr = b.ps.get(0);
        float rad = 0;
        int n = b.ps.size() - 1;
        float lowY = -1e9f, lowX = 0;
        for (int i = 1; i <= n; i++) {
            Particle q = b.ps.get(i);
            float dx = q.x - ctr.x, dy = q.y - ctr.y;
            rad += (float) Math.sqrt(dx * dx + dy * dy);
            if (q.y > lowY) { lowY = q.y; lowX = q.x; }
        }
        rad = rad / n + b.ps.get(1).r;
        int col = bodyColor(b, b.color);
        p.setStyle(Paint.Style.FILL);
        if (b.type == Body.BALLOON) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.8f);
            p.setColor(0xFFdddddd);
            c.drawLine(lowX, lowY, lowX + 4, lowY + 22, p);
            c.drawLine(lowX + 4, lowY + 22, lowX - 3, lowY + 44, p);
            p.setStyle(Paint.Style.FILL);
        }
        p.setColor(dark(col, 0.5f));
        c.drawCircle(ctr.x, ctr.y, rad + 1.6f, p);
        p.setColor(col);
        c.drawCircle(ctr.x, ctr.y, rad, p);
        // rotation mark so rolling is visible
        Particle m = b.ps.get(1);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(rad * 0.18f);
        p.setColor(dark(col, 0.28f));
        c.drawLine(ctr.x, ctr.y, ctr.x + (m.x - ctr.x) * 0.82f, ctr.y + (m.y - ctr.y) * 0.82f, p);
        p.setStyle(Paint.Style.FILL);
        if (b.type == Body.WHEEL && b.motor != 0) {
            p.setColor(0xFFf2b134);                           // motor hub
            c.drawCircle(ctr.x, ctr.y, rad * 0.5f, p);
            p.setColor(0xFF1b2029);
            c.drawCircle(ctr.x, ctr.y, rad * 0.2f, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3f);
            p.setColor(0xFF1b2029);
            rf.set(ctr.x - rad * 0.36f, ctr.y - rad * 0.36f, ctr.x + rad * 0.36f, ctr.y + rad * 0.36f);
            float spin = (float) Math.toDegrees(Math.atan2(m.y - ctr.y, m.x - ctr.x));
            c.drawArc(rf, spin, b.motor > 0 ? 220f : -220f, false, p);
            p.setStyle(Paint.Style.FILL);
        } else if (detail && b.type == Body.WHEEL) {
            p.setColor(0xFF9aa3ad);
            c.drawCircle(ctr.x, ctr.y, rad * 0.5f, p);
            p.setColor(0xFF4a525c);
            c.drawCircle(ctr.x, ctr.y, rad * 0.22f, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(rad * 0.09f);
            p.setColor(0xFF45494f);
            for (int i = 1; i <= n; i += 2) {
                Particle q = b.ps.get(i);
                c.drawLine(ctr.x + (q.x - ctr.x) * 0.3f, ctr.y + (q.y - ctr.y) * 0.3f, ctr.x + (q.x - ctr.x) * 0.5f, ctr.y + (q.y - ctr.y) * 0.5f, p);
            }
            p.setStyle(Paint.Style.FILL);
        } else if (detail && b.type == Body.BOWLING) {
            float vx = m.x - ctr.x, vy = m.y - ctr.y;
            p.setColor(0xFF05070f);
            for (int i = 0; i < 3; i++) {
                float a = i == 0 ? 0f : (i == 1 ? 0.5f : -0.5f);
                float cs = (float) Math.cos(a), sn = (float) Math.sin(a);
                c.drawCircle(ctr.x + (vx * cs - vy * sn) * 0.5f, ctr.y + (vx * sn + vy * cs) * 0.5f, rad * 0.14f, p);
            }
        } else if (detail && b.type == Body.MELON) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(rad * 0.16f);
            p.setColor(0xFF2a6e29);
            for (int i = 1; i <= n; i += 3) {
                Particle q = b.ps.get(i);
                c.drawLine(ctr.x + (q.x - ctr.x) * 0.15f, ctr.y + (q.y - ctr.y) * 0.15f, ctr.x + (q.x - ctr.x) * 0.9f, ctr.y + (q.y - ctr.y) * 0.9f, p);
            }
            p.setStyle(Paint.Style.FILL);
        }
        p.setColor(0x55ffffff);
        c.drawCircle(ctr.x - rad * 0.35f, ctr.y - rad * 0.38f, rad * 0.22f, p);
    }

    // ---------------------------------------------------------------------------------- humans ---

    private void seg(Draw2D c, Body b, Particle a, Particle z, float r, int col) {
        col = bodyColor(b, col);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(dark(col, 0.5f));
        p.setStrokeWidth(r * 2 + 3f);
        c.drawLine(a.x, a.y, z.x, z.y, p);
        p.setColor(col);
        p.setStrokeWidth(r * 2);
        c.drawLine(a.x, a.y, z.x, z.y, p);
    }

    private void part(Draw2D c, Body b, Stick s, int col) {
        seg(c, b, s.a, s.b, s.r, col);
    }

    private void foot(Draw2D c, Body b, Stick shin) {
        float sx = shin.b.x - shin.a.x, sy = shin.b.y - shin.a.y;
        float l = (float) Math.sqrt(sx * sx + sy * sy);
        if (l < 1e-3f) return;
        sx /= l; sy /= l;
        float fx = b.dir * sy, fy = -b.dir * sx;
        float s = b.scale;
        int col = bodyColor(b, 0xFF2b2b30);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(col);
        p.setStrokeWidth(8.5f * s);
        c.drawLine(shin.b.x, shin.b.y + 1, shin.b.x + fx * 9 * s, shin.b.y + fy * 9 * s + 1, p);
    }

    private void hand(Draw2D c, Body b, Stick fore) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(bodyColor(b, b.skin));
        c.drawCircle(fore.b.x, fore.b.y, fore.r * 1.15f, p);
    }

    private void drawHuman(Draw2D c, Body b) {
        int skin = b.skin;
        if (b.dead) skin = mix(skin, 0xFF9aa0a8, 0.25f);
        // back limbs
        part(c, b, b.thighB, b.pants); part(c, b, b.shinB, b.pants); foot(c, b, b.shinB);
        part(c, b, b.uarmB, b.shirt); part(c, b, b.farmB, skin); hand(c, b, b.farmB);
        // torso
        seg(c, b, b.pelvis, b.chest, b.torso.r, b.shirt);
        torsoDetail(c, b);
        if (!b.neck.dead) seg(c, b, b.head, b.chest, b.neck.r, skin);
        // front legs
        part(c, b, b.thighF, b.pants); part(c, b, b.shinF, b.pants); foot(c, b, b.shinF);
        // head
        drawHead(c, b, skin);
        // front arm
        part(c, b, b.uarmF, b.shirt); part(c, b, b.farmF, skin); hand(c, b, b.farmF);
        if (b == inspect) inspectOverlay(c, b);
        // stumps
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF8e1111);
        if (b.jointList == null) b.jointList = new Stick[]{b.jShF, b.jElF, b.jShB, b.jElB, b.jHipF, b.jKneeF, b.jHipB, b.jKneeB, b.neck};
        Stick[] js = b.jointList;
        p.setColor(gore ? 0xFF8e1111 : 0xFF6b6f78);
        for (int i = 0; i < js.length; i++) {
            Stick s = js[i];
            if (s == null || !s.dead) continue;
            float r = (s == b.neck ? 4.8f : 4.3f) * b.scale;
            c.drawCircle(s.a.x, s.a.y, r, p);
            c.drawCircle(s.b.x, s.b.y, r, p);
        }
    }

    private void torsoDetail(Draw2D c, Body b) {
        Particle a = b.pelvis, z = b.chest;
        float dx = z.x - a.x, dy = z.y - a.y;
        float l = (float) Math.sqrt(dx * dx + dy * dy);
        if (l < 1f) return;
        float ux = dx / l, uy = dy / l, nx = -uy, ny = ux, r = b.torso.r;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.BUTT);
        if (b.kind == Body.K_SKELETON) {                 // ribs + spine
            p.setColor(0xFF8d8774);
            p.setStrokeWidth(2.2f * b.scale);
            for (int i = 0; i < 4; i++) {
                float t = 0.3f + i * 0.17f;
                float cx = a.x + dx * t, cy = a.y + dy * t;
                c.drawLine(cx - nx * r * 0.85f, cy - ny * r * 0.85f, cx + nx * r * 0.85f, cy + ny * r * 0.85f, p);
            }
            c.drawLine(a.x, a.y, z.x, z.y, p);
        } else if (b.kind == Body.K_ROBOT) {             // chest plate + panel lines
            p.setColor(0xFF33414f);
            p.setStrokeWidth(2f * b.scale);
            float cx = a.x + dx * 0.6f, cy = a.y + dy * 0.6f;
            c.drawLine(cx - nx * r * 0.8f, cy - ny * r * 0.8f, cx + nx * r * 0.8f, cy + ny * r * 0.8f, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(b.dead ? 0xFF555f6a : 0xFF35e0ff);
            c.drawCircle(a.x + dx * 0.5f, a.y + dy * 0.5f, 3.2f * b.scale, p);
        } else if (detail) {
            int pat = (b.shirt >>> 8) & 3;
            if (b.kind == Body.K_ZOMBIE) {               // torn shirt
                p.setColor(alpha(0xFF111111, 0.55f));
                p.setStrokeWidth(2f * b.scale);
                for (int i = 0; i < 3; i++) {
                    float t = 0.25f + i * 0.25f;
                    float cx = a.x + dx * t, cy = a.y + dy * t;
                    c.drawLine(cx - nx * r * 0.6f, cy - ny * r * 0.6f, cx + nx * r * 0.2f + ux * 6, cy + ny * r * 0.2f + uy * 6, p);
                }
            } else if (pat == 1) {                       // stripes
                p.setColor(alpha(dark(bodyColor(b, b.shirt), 0.45f), 0.8f));
                p.setStrokeWidth(4f * b.scale);
                for (int i = 0; i < 5; i++) {
                    float t = 0.12f + i * 0.19f;
                    float cx = a.x + dx * t, cy = a.y + dy * t;
                    c.drawLine(cx - nx * r * 0.95f, cy - ny * r * 0.95f, cx + nx * r * 0.95f, cy + ny * r * 0.95f, p);
                }
            } else if (pat == 2) {                       // chest pocket
                p.setColor(alpha(dark(bodyColor(b, b.shirt), 0.5f), 0.9f));
                p.setStrokeWidth(2f * b.scale);
                float cx = a.x + dx * 0.72f + nx * b.dir * r * 0.4f, cy = a.y + dy * 0.72f + ny * b.dir * r * 0.4f;
                c.drawLine(cx - 4 * b.scale, cy, cx + 4 * b.scale, cy, p);
            }
            // belt
            p.setColor(alpha(0xFF14161c, 0.8f));
            p.setStrokeWidth(3f * b.scale);
            float cx = a.x + dx * 0.08f, cy = a.y + dy * 0.08f;
            c.drawLine(cx - nx * r, cy - ny * r, cx + nx * r, cy + ny * r, p);
        }
        p.setStrokeCap(Paint.Cap.ROUND);
    }

    /** Inspect view: every part tinted by its health (green -> red), broken bones in violet. */
    private void inspectOverlay(Draw2D c, Body b) {
        Stick[] parts = b.parts();
        if (parts == null) return;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        for (Stick s : parts) {
            float h = 1f - s.damage / 100f;
            int col = s.fractured ? 0xFFc061ff : (h > 0.5f ? mix(0xFFf2d13a, 0xFF4cdc6b, (h - 0.5f) * 2f) : mix(0xFFe0463c, 0xFFf2d13a, h * 2f));
            p.setColor(alpha(col, 0.6f));
            p.setStrokeWidth(s.r * 2f + 2f);
            c.drawLine(s.a.x, s.a.y, s.b.x, s.b.y, p);
        }
        float hh = 1f - b.headDmg / 100f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(alpha(hh > 0.5f ? 0xFF4cdc6b : 0xFFe0463c, 0.45f));
        c.drawCircle(b.head.x, b.head.y, b.head.r, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2.5f);
        p.setColor(0xCCffffff);
        c.drawCircle(b.cx, b.cy, b.rad + 10f, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void drawHead(Draw2D c, Body b, int skin) {
        Particle h = b.head;
        float ux = h.x - b.chest.x, uy = h.y - b.chest.y;
        if (b.neck.dead) { ux = h.x - h.px; uy = h.y - h.py - 1; if (ux * ux + uy * uy < 0.01f) uy = -1; }
        float ang = (float) Math.toDegrees(Math.atan2(ux, -uy));
        float r = h.r;
        int col = bodyColor(b, skin);
        c.save();
        c.translate(h.x, h.y);
        c.rotate(ang);
        p.setStyle(Paint.Style.FILL);
        p.setColor(dark(col, 0.5f));
        c.drawCircle(0, 0, r + 1.6f, p);
        p.setColor(col);
        c.drawCircle(0, 0, r, p);
        float ex = b.dir * r * 0.5f, ey = -r * 0.12f;
        if (b.kind == Body.K_ROBOT) {
            p.setColor(bodyColor(b, 0xFF7f8c99));
            rf.set(-r * 0.95f, -r * 0.95f, r * 0.95f, r * 0.95f);
            c.drawRoundRect(rf, r * 0.25f, r * 0.25f, p);
            p.setColor(0xFF1b222b);
            rf.set(-r * 0.2f + b.dir * r * 0.15f, -r * 0.42f, r * 0.85f * b.dir + b.dir * r * 0.1f, r * 0.1f);
            if (b.dir < 0) rf.set(r * 0.85f * b.dir + b.dir * r * 0.1f, -r * 0.42f, -r * 0.2f + b.dir * r * 0.15f, r * 0.1f);
            c.drawRoundRect(rf, r * 0.1f, r * 0.1f, p);
            p.setColor(b.dead ? 0xFF3b4652 : 0xFF35e0ff);
            c.drawCircle(ex, -r * 0.16f, r * 0.14f, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2f);
            p.setColor(0xFF9aa5b1);
            c.drawLine(0, -r * 0.95f, 0, -r * 1.35f, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(b.dead ? 0xFF555f6a : 0xFFff4d4d);
            c.drawCircle(0, -r * 1.4f, r * 0.12f, p);
        } else if (b.kind == Body.K_SKELETON) {
            p.setColor(0xFF2a2820);
            c.drawCircle(b.dir * r * 0.42f, -r * 0.1f, r * 0.24f, p);
            c.drawCircle(-b.dir * r * 0.1f, -r * 0.1f, r * 0.2f, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.8f);
            for (int i = -1; i <= 1; i++) c.drawLine(b.dir * (r * 0.1f + i * r * 0.18f), r * 0.45f, b.dir * (r * 0.1f + i * r * 0.18f), r * 0.78f, p);
            p.setStyle(Paint.Style.FILL);
        } else {
            int hair = bodyColor(b, b.hair);
            p.setColor(hair);
            rf.set(-r, -r, r, r);
            if (b.kind != Body.K_ZOMBIE || b.hair != 0) c.drawArc(rf, 180f, 180f, true, p);
            c.drawCircle(-b.dir * r * 0.42f, -r * 0.1f, r * 0.62f, p);
            p.setColor(col);
            c.drawCircle(b.dir * r * 0.28f, r * 0.02f, r * 0.62f, p);
            boolean z = b.kind == Body.K_ZOMBIE;
            p.setColor(z ? 0xFFd62020 : 0xFF1b1b1b);
            if (b.dead && !z) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.8f);
                float k = r * 0.2f;
                c.drawLine(ex - k, ey - k, ex + k, ey + k, p);
                c.drawLine(ex - k, ey + k, ex + k, ey - k, p);
            } else {
                c.drawCircle(ex, ey, r * (z ? 0.2f : 0.15f), p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.6f);
                p.setColor(z ? 0xFF4a1010 : 0xFF1b1b1b);
                if (z) { c.drawLine(b.dir * r * 0.25f, r * 0.45f, b.dir * r * 0.75f, r * 0.55f, p); }
                else c.drawLine(b.dir * r * 0.3f, r * 0.42f, b.dir * r * 0.72f, r * 0.42f, p);
            }
        }
        c.restore();
    }

    /** Welds and axles: a steel strap with a rivet at each end. */
    private void drawLinks(Draw2D c, World w) {
        for (int i = 0; i < w.bodies.size(); i++) {
            Body b = w.bodies.get(i);
            for (int k = 0; k < b.ss.size(); k++) {
                Stick s = b.ss.get(k);
                if (s.dead || !s.weld || s.kind == Stick.HIDDEN) continue;
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setColor(0xFF2b2f36);
                p.setStrokeWidth(7f);
                c.drawLine(s.a.x, s.a.y, s.b.x, s.b.y, p);
                p.setColor(0xFFb9c0ca);
                p.setStrokeWidth(4f);
                c.drawLine(s.a.x, s.a.y, s.b.x, s.b.y, p);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFFe9ecf1);
                c.drawCircle(s.a.x, s.a.y, 3.6f, p);
                c.drawCircle(s.b.x, s.b.y, 3.6f, p);
            }
        }
    }

    private void drawPins(Draw2D c, World w) {
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < w.ps.size(); i++) {
            Particle q = w.ps.get(i);
            if (!q.pinned) continue;
            p.setColor(0xFF1d1d1d);
            c.drawCircle(q.x, q.y, 7.5f, p);
            p.setColor(0xFFe9ecf1);
            c.drawCircle(q.x, q.y, 5.2f, p);
            p.setColor(0xFFb4bac4);
            c.drawCircle(q.x - 1.4f, q.y - 1.4f, 2f, p);
        }
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3f);
        p.setColor(0xCCffffff);
        for (int i = 0; i < w.grabs.size(); i++) {
            World.Grab g = w.grabs.get(i);
            c.drawCircle(g.p.x, g.p.y, g.p.r + 7f, p);
        }
    }

    // ----------------------------------------------------------------------------------- effects ---

    private void drawFx(Draw2D c, World w, Fx fx) {
        p.setStyle(Paint.Style.FILL);
        for (int pass = 0; pass < 2; pass++) {
            c.emissive(pass == 1);
            for (int i = 0; i < Fx.MAXP; i++) {
                if (fx.life[i] <= 0) continue;
                int t = fx.type[i];
                boolean front = t == Fx.FLAME || t == Fx.SPARK || t == Fx.GLOW;
                if (front != (pass == 1)) continue;
                if (pass == 1 && (i & 15) == 0 && t == Fx.FLAME) c.light(fx.x[i], fx.y[i], 150f, 0xFFff8a2a, 0.22f * fx.life[i] / fx.maxLife[i]);
                float k = fx.life[i] / fx.maxLife[i];
                switch (t) {
                    case Fx.BLOOD:
                        p.setColor(fx.color[i]);
                        c.drawCircle(fx.x[i], fx.y[i], fx.size[i], p);
                        break;
                    case Fx.SMOKE:
                        p.setColor(alpha(fx.color[i] == 0 ? 0xFF444444 : fx.color[i], k * 0.5f));
                        c.drawCircle(fx.x[i], fx.y[i], fx.size[i], p);
                        break;
                    case Fx.DEBRIS:
                        p.setColor(alpha(fx.color[i], Math.min(1f, k * 2f)));
                        rf.set(fx.x[i] - fx.size[i], fx.y[i] - fx.size[i], fx.x[i] + fx.size[i], fx.y[i] + fx.size[i]);
                        c.drawRect(rf, p);
                        break;
                    case Fx.FLAME: {
                        int col = k > 0.6f ? mix(0xFFffe27a, 0xFFfff6c8, (k - 0.6f) * 2.5f)
                                : (k > 0.3f ? mix(0xFFff7a1a, 0xFFffe27a, (k - 0.3f) * 3.3f) : mix(0xFF8a1e08, 0xFFff7a1a, k * 3.3f));
                        p.setColor(alpha(col, Math.min(1f, k * 2.2f)));
                        c.drawCircle(fx.x[i], fx.y[i], fx.size[i] * (0.35f + 0.65f * k), p);
                        break;
                    }
                    case Fx.SPARK:
                        p.setStyle(Paint.Style.STROKE);
                        p.setStrokeWidth(fx.size[i]);
                        p.setColor(alpha(fx.color[i], Math.min(1f, k * 2f)));
                        c.drawLine(fx.x[i], fx.y[i], fx.x[i] - fx.vx[i] * 0.025f, fx.y[i] - fx.vy[i] * 0.025f, p);
                        p.setStyle(Paint.Style.FILL);
                        break;
                    case Fx.GLOW:
                        c.light(fx.x[i], fx.y[i], Math.min(420f, fx.size[i] * 3.5f), fx.color[i], k * 0.75f);
                        // with real lighting the light itself is the glow: keep the disc subtle
                        p.setColor(alpha(fx.color[i], k * (c.hasLighting() ? 0.3f : 0.85f)));
                        c.drawCircle(fx.x[i], fx.y[i], fx.size[i] * (1.7f - k * 0.7f), p);
                        break;
                    default: break;
                }
            }
        }
        c.emissive(true);
        // shock rings
        p.setStyle(Paint.Style.STROKE);
        for (int i = 0; i < Fx.MAXR; i++) {
            if (fx.rt[i] <= 0) continue;
            c.ripple(fx.rx[i], fx.ry[i], fx.rr[i], fx.rt[i]);
            p.setColor(alpha(0xFFffffff, fx.rt[i] * 0.55f));
            p.setStrokeWidth(4f + 18f * fx.rt[i]);
            c.drawCircle(fx.rx[i], fx.ry[i], fx.rr[i], p);
        }
        // bullet tracers
        p.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 8; i++) {
            if (w.trT[i] <= 0) continue;
            float k = w.trT[i] / 0.07f;
            p.setColor(alpha(0xFFfff1b0, k));
            p.setStrokeWidth(3f);
            c.drawLine(w.trX0[i], w.trY0[i], w.trX1[i], w.trY1[i], p);
            c.light(w.trX0[i], w.trY0[i], 160f, 0xFFffd27a, k);
        }
        // rockets
        for (int i = 0; i < w.rockets.size(); i++) {
            World.Projectile r = w.rockets.get(i);
            float l = (float) Math.sqrt(r.vx * r.vx + r.vy * r.vy);
            float dx = r.vx / l, dy = r.vy / l;
            p.setColor(0xFF2a2a2a);
            p.setStrokeWidth(10f);
            c.drawLine(r.x - dx * 24, r.y - dy * 24, r.x, r.y, p);
            p.setColor(0xFFcfd3da);
            p.setStrokeWidth(7f);
            c.drawLine(r.x - dx * 24, r.y - dy * 24, r.x - dx * 4, r.y - dy * 4, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFd33a2c);
            c.drawCircle(r.x + dx * 2, r.y + dy * 2, 6f, p);
            c.light(r.x - dx * 30, r.y - dy * 30, 220f, 0xFFff8a2a, 0.9f);
            p.setStyle(Paint.Style.STROKE);
        }
        // lightning
        if (fx.bt > 0 && fx.bn > 1) {
            float k = Math.min(1f, fx.bt / 0.22f);
            for (int pass = 0; pass < 2; pass++) {
                p.setColor(pass == 0 ? alpha(0xFF7fb8ff, k * 0.45f) : alpha(0xFFffffff, k));
                p.setStrokeWidth(pass == 0 ? 16f : 5f);
                for (int i = 0; i + 1 < fx.bn; i++) c.drawLine(fx.bx[i], fx.by[i], fx.bx[i + 1], fx.by[i + 1], p);
            }
            for (int i = 0; i < fx.bn; i += 3) c.light(fx.bx[i], fx.by[i], 420f, 0xFF9cc8ff, k * 1.2f);
        }
        c.emissive(false);
        p.setStyle(Paint.Style.FILL);
        drawLights(c, w);
    }

    /** Light sources that belong to bodies: fire, thrusters, lit fuses, motor hubs. */
    private void drawLights(Draw2D c, World w) {
        if (!c.hasLighting()) return;
        for (int i = 0; i < w.bodies.size(); i++) {
            Body b = w.bodies.get(i);
            if (b.ps.isEmpty()) continue;
            if (b.burning) c.light(b.cx, b.cy, Math.max(160f, b.rad * 3.2f), 0xFFff7a24, 0.75f + 0.15f * (float) Math.sin(b.burnT * 23f));
            if (b.type == Body.THRUSTER && b.thrust && b.ps.size() == 2) c.light(b.ps.get(0).x, b.ps.get(0).y, 260f, 0xFFffa040, 0.9f);
            if (b.fuse >= 0f && b.type != Body.TNT && b.type != Body.XBARREL) c.light(b.ps.get(0).x, b.ps.get(0).y - 6, 90f, 0xFFffc83d, 0.6f);
        }
        if (theme == 4) {          // neon floor strip lights the room
            for (float x = 100; x < w.wallR; x += 300) c.light(x, w.floorY, 360f, (((int) x / 300) & 1) == 0 ? 0xFF00e5ff : 0xFFff2bd6, 0.45f);
        } else if (theme == 2) {   // moonlight
            c.light(2000, w.floorY - 640, 900f, 0xFF8fa8ff, 0.35f);
        }
    }
}

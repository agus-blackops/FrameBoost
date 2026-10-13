package com.frameboost.ragdoll;

import android.graphics.Paint;
import android.graphics.RectF;

/** Draws the world through Draw2D (GPU engine or Canvas): backgrounds, people, props, effects. */
final class Renderer {
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private final float[] poly = new float[16];
    int theme;
    boolean detail = true;
    Person inspect;
    float time;

    Renderer() {
        p.setStrokeCap(Paint.Cap.ROUND);
    }

    // ----------------------------------------------------------------------------- colour utils --

    static int mix(int a, int b, float t) {
        int ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255, br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
        return 0xFF000000 | (int) (ar + (br - ar) * t) << 16 | (int) (ag + (bg - ag) * t) << 8 | (int) (ab + (bb - ab) * t);
    }
    static int alpha(int c, float a) { return ((int) (255 * Math.max(0f, Math.min(1f, a))) << 24) | (c & 0xFFFFFF); }
    static int dark(int c, float t) { return mix(c, 0xFF000000, t); }
    static int light(int c, float t) { return mix(c, 0xFFffffff, t); }

    // ------------------------------------------------------------------------------- themes ------

    // wall top, wall bottom, floor top, floor body, accent
    static final int[][] THEME = {
        {0xFF2c3550, 0xFF3d4a6b, 0xFF8b7a66, 0xFF65594b, 0xFF4d5b82},
        {0xFFd5dde5, 0xFFe8eef3, 0xFFaab4bf, 0xFF8892a0, 0xFFb9c4cf},
        {0xFF0b1020, 0xFF1a2342, 0xFF2b3552, 0xFF1d2640, 0xFF8fa8ff},
        {0xFF74483a, 0xFF8a5a47, 0xFF55504b, 0xFF3f3b37, 0xFF5e3a2d},
        {0xFF140a2b, 0xFF24134a, 0xFF2a1c4a, 0xFF1c1336, 0xFFff2bd6},
        {0xFFf0c277, 0xFFf8dca5, 0xFFd8a65d, 0xFFbf8d47, 0xFFe9ba70},
    };

    private void bands(Draw2D c, float x0, float x1, float y0, float y1, int top, int bot) {
        int n = 14;
        float h = (y1 - y0) / n;
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < n; i++) {
            p.setColor(mix(top, bot, i / (float) (n - 1)));
            c.drawRect(x0, y0 + i * h, x1, y0 + (i + 1) * h + 1f, p);
        }
    }

    void background(Draw2D c, World w, float x0, float x1, float y0, float y1) {
        int[] th = THEME[Math.max(0, Math.min(THEME.length - 1, theme))];
        float fl = w.sim.floorY;
        p.setStyle(Paint.Style.FILL);
        p.setColor(dark(th[0], 0.35f));
        c.drawRect(x0 - 2000, y0 - 2000, x1 + 2000, y1 + 2000, p);
        float wl = w.sim.wallL, wr = w.sim.wallR, ct = w.sim.ceilY;
        bands(c, wl, wr, ct, fl, th[0], th[1]);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f);
        switch (theme) {
            case 0: {
                p.setColor(alpha(th[4], 0.35f));
                for (float x = wl; x <= wr; x += 120f) c.drawLine(x, ct, x, fl, p);
                for (float y = fl; y >= ct; y -= 120f) c.drawLine(wl, y, wr, y, p);
                // window
                float wx = 1500f, wy = fl - 520f;
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFF9bc4e6);
                c.drawRect(wx, wy, wx + 300f, wy + 220f, p);
                p.setColor(alpha(0xFFffffff, 0.35f));
                c.drawRect(wx + 20f, wy + 20f, wx + 120f, wy + 100f, p);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(8f); p.setColor(0xFFe9e0cf);
                c.drawRect(wx, wy, wx + 300f, wy + 220f, p);
                c.drawLine(wx + 150f, wy, wx + 150f, wy + 220f, p);
                c.drawLine(wx, wy + 110f, wx + 300f, wy + 110f, p);
                break;
            }
            case 1: {
                p.setColor(alpha(0xFF8da0b3, 0.7f));
                for (float x = wl; x <= wr; x += 100f) c.drawLine(x, ct, x, fl, p);
                for (float y = fl; y >= ct; y -= 100f) c.drawLine(wl, y, wr, y, p);
                p.setStyle(Paint.Style.FILL);
                for (float x = wl; x < wr; x += 80f) { p.setColor(((int) (x / 80f) & 1) == 0 ? 0xFFf2c200 : 0xFF222222); c.drawRect(x, fl - 110f, x + 40f, fl - 96f, p); }
                break;
            }
            case 2: {
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFFf2f1d8);
                c.emissive(true);
                c.drawCircle(2600f, fl - 780f, 60f, p);
                c.emissive(false);
                p.setColor(0xFF0e1530);
                for (int i = 0; i < 14; i++) { float bx = wl + i * 250f, bh = 160f + ((i * 73) % 5) * 70f; c.drawRect(bx, fl - bh, bx + 150f, fl, p); }
                p.setColor(0xFFffe08a);
                for (int i = 0; i < 14; i++) for (int j = 0; j < 3; j++) if (((i * 7 + j * 3) & 3) != 0) c.drawRect(wl + i * 250f + 30f + j * 40f, fl - 80f - j * 50f, wl + i * 250f + 52f + j * 40f, fl - 60f - j * 50f, p);
                for (int i = 0; i < 60; i++) { float sx = wl + (i * 97 % 3200), sy = ct + 60f + (i * 53 % 900); c.drawCircle(sx, sy, 2f, p); }
                break;
            }
            case 3: {
                p.setColor(alpha(th[4], 0.8f));
                float bh = 40f, bw = 100f;
                int row = 0;
                for (float y = fl; y > ct; y -= bh, row++) {
                    c.drawLine(wl, y, wr, y, p);
                    for (float x = wl + (row % 2) * bw * 0.5f; x < wr; x += bw) c.drawLine(x, y, x, y - bh, p);
                }
                break;
            }
            case 4: {
                p.setColor(alpha(0xFFff2bd6, 0.5f));
                c.emissive(true);
                for (float x = wl; x <= wr; x += 160f) c.drawLine(x, ct, x, fl, p);
                p.setColor(alpha(0xFF00e5ff, 0.5f));
                for (float y = fl; y >= ct; y -= 160f) c.drawLine(wl, y, wr, y, p);
                c.emissive(false);
                break;
            }
            default: {
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFFe8b56a);
                for (int i = 0; i < 6; i++) { float bx = wl + i * 600f; rf.set(bx - 300f, fl - 220f, bx + 500f, fl + 400f); c.drawOval(rf, p); }
                p.setColor(0xFFffefc4);
                c.drawCircle(2700f, fl - 700f, 70f, p);
            }
        }
        // floor
        p.setStyle(Paint.Style.FILL);
        p.setColor(th[3]);
        c.drawRect(wl - 400f, fl, wr + 400f, fl + 2400f, p);
        p.setColor(th[2]);
        c.drawRect(wl - 400f, fl, wr + 400f, fl + 14f, p);
        p.setColor(dark(th[3], 0.25f));
        c.drawRect(wl - 400f, fl + 14f, wr + 400f, fl + 20f, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2f); p.setColor(alpha(0xFF000000, 0.18f));
        for (float x = wl - 400f; x < wr + 400f; x += 160f) c.drawLine(x, fl + 20f, x - 40f, fl + 700f, p);
        for (float y = fl + 100f; y < fl + 700f; y += 100f) c.drawLine(wl - 400f, y, wr + 400f, y, p);
        // walls' edge
        p.setStyle(Paint.Style.FILL);
        p.setColor(dark(th[0], 0.55f));
        c.drawRect(wl - 600f, ct - 800f, wl, fl + 2400f, p);
        c.drawRect(wr, ct - 800f, wr + 600f, fl + 2400f, p);
        c.drawRect(wl - 600f, ct - 800f, wr + 600f, ct, p);
    }

    // ---------------------------------------------------------------------------------- world ----

    void draw(Draw2D c, World w, float camX, float camY, float zoom, int sw, int sh) {
        time += 1f / 60f;
        float vw = sw / zoom, vh = sh / zoom;
        float x0 = camX - vw / 2f, x1 = camX + vw / 2f, y0 = camY - vh / 2f, y1 = camY + vh / 2f;
        c.save();
        c.translate(sw / 2f, sh / 2f);
        c.scale(zoom, zoom);
        c.translate(-camX, -camY);
        background(c, w, x0, x1, y0, y1);
        w.fx.drawStains(c, p);
        // ground shadows
        p.setStyle(Paint.Style.FILL);
        p.setColor(alpha(0xFF000000, 0.22f));
        for (int i = 0; i < w.ents.size(); i++) {
            Ent e = w.ents.get(i);
            float h = w.sim.floorY - e.cy();
            if (h > 700f || h < -40f) continue;
            float rad = e.radius();
            float k = Math.max(0.3f, 1f - h / 700f);
            rf.set(e.cx() - rad * 0.8f * k, w.sim.floorY - 3f, e.cx() + rad * 0.8f * k, w.sim.floorY + 6f);
            c.drawOval(rf, p);
        }
        // props, then people on top
        for (int i = 0; i < w.ents.size(); i++) { Ent e = w.ents.get(i); if (e instanceof Prop) drawProp(c, (Prop) e, w); }
        drawLinks(c, w);
        for (int i = 0; i < w.ents.size(); i++) { Ent e = w.ents.get(i); if (e instanceof Person) drawPerson(c, (Person) e, w); }
        drawPins(c, w);
        w.fx.draw(c, p);
        // tracers and rockets
        p.setStyle(Paint.Style.STROKE);
        c.emissive(true);
        for (int i = 0; i < 8; i++) {
            if (w.trT[i] <= 0f) continue;
            p.setColor(alpha(0xFFfff0b0, w.trT[i] / 0.07f));
            p.setStrokeWidth(2.4f);
            c.drawLine(w.trX0[i], w.trY0[i], w.trX1[i], w.trY1[i], p);
        }
        p.setStyle(Paint.Style.FILL);
        for (World.Rocket r : w.rockets) {
            float sp = (float) Math.sqrt(r.vx * r.vx + r.vy * r.vy), dx = r.vx / sp, dy = r.vy / sp;
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(7f); p.setColor(0xFFd0d4da);
            c.drawLine(r.x - dx * 24f, r.y - dy * 24f, r.x - dx * 4f, r.y - dy * 4f, p);
            p.setStyle(Paint.Style.FILL); p.setColor(0xFFd33a2c);
            c.drawCircle(r.x + dx * 2f, r.y + dy * 2f, 6f, p);
            c.light(r.x - dx * 30f, r.y - dy * 30f, 220f, 0xFFff8a2a, 0.9f);
        }
        c.emissive(false);
        drawLights(c, w);
        c.restore();
    }

    private void drawLights(Draw2D c, World w) {
        if (!c.hasLighting()) return;
        for (int i = 0; i < w.ents.size(); i++) {
            Ent e = w.ents.get(i);
            if (e.bodies.isEmpty()) continue;
            float cx = e.cx(), cy = e.cy();
            if (e.burning) c.light(cx, cy, Math.max(160f, e.radius() * 3.2f), 0xFFff7a24, 0.75f + 0.15f * (float) Math.sin(e.burnT * 23f));
            if (e instanceof Prop) {
                Prop q = (Prop) e;
                if (q.type == Items.THRUSTER && q.thrust) c.light(cx, cy + 20f, 260f, 0xFFffa040, 0.9f);
                if (q.fuse >= 0f && q.type != Items.TNT && q.type != Items.XBARREL) c.light(cx, cy - 6f, 90f, 0xFFffc83d, 0.6f);
                if (q.machine() && q.on && !q.dead) c.light(cx, cy, 110f, q.target != null ? 0xFFff3b30 : 0xFF35e0ff, 0.45f);
            }
        }
        if (theme == 4) for (float x = 100; x < w.sim.wallR; x += 300) c.light(x, w.sim.floorY, 360f, (((int) x / 300) & 1) == 0 ? 0xFF00e5ff : 0xFFff2bd6, 0.45f);
        else if (theme == 2) c.light(2600f, w.sim.floorY - 780f, 900f, 0xFF8fa8ff, 0.35f);
    }

    // ---------------------------------------------------------------------------------- shapes ----

    private int tint(Ent e, int col, float flash) {
        if (e.charT > 0f) col = mix(col, 0xFF1a1714, e.charT * 0.75f);
        if (flash > 0f) col = mix(col, 0xFFffffff, Math.min(0.6f, flash * 5f));
        return col;
    }

    private void capsuleShape(Draw2D c, Shape s, int col) {
        float x0 = s.cx - s.ax * s.hl, y0 = s.cy - s.ay * s.hl, x1 = s.cx + s.ax * s.hl, y1 = s.cy + s.ay * s.hl;
        p.setStyle(Paint.Style.STROKE);
        p.setColor(dark(col, 0.55f));
        p.setStrokeWidth(s.r * 2f + 3.2f);
        c.drawLine(x0, y0, x1, y1, p);
        p.setColor(col);
        p.setStrokeWidth(s.r * 2f);
        c.drawLine(x0, y0, x1, y1, p);
    }

    private void boxShape(Draw2D c, Shape s, int col, boolean outline) {
        float ux = s.ax * s.hl, uy = s.ay * s.hl, vx = -s.ay * s.hh, vy = s.ax * s.hh;
        p.setStyle(Paint.Style.FILL);
        if (outline) {
            float k = 1.6f;
            float lx = s.ax * k, ly = s.ay * k, mx = -s.ay * k, my = s.ax * k;
            poly[0] = s.cx - ux - vx - lx - mx; poly[1] = s.cy - uy - vy - ly - my;
            poly[2] = s.cx + ux - vx + lx - mx; poly[3] = s.cy + uy - vy + ly - my;
            poly[4] = s.cx + ux + vx + lx + mx; poly[5] = s.cy + uy + vy + ly + my;
            poly[6] = s.cx - ux + vx - lx + mx; poly[7] = s.cy - uy + vy - ly + my;
            p.setColor(dark(col, 0.55f));
            c.drawPoly(poly, 4, p);
        }
        poly[0] = s.cx - ux - vx; poly[1] = s.cy - uy - vy;
        poly[2] = s.cx + ux - vx; poly[3] = s.cy + uy - vy;
        poly[4] = s.cx + ux + vx; poly[5] = s.cy + uy + vy;
        poly[6] = s.cx - ux + vx; poly[7] = s.cy - uy + vy;
        p.setColor(col);
        c.drawPoly(poly, 4, p);
    }

    private void circleShape(Draw2D c, Shape s, int col) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(dark(col, 0.55f));
        c.drawCircle(s.cx, s.cy, s.r + 1.6f, p);
        p.setColor(col);
        c.drawCircle(s.cx, s.cy, s.r, p);
    }

    /** Draws something in the local frame of a shape / body (x along the body axis). */
    private void local(Draw2D c, float x, float y, float angle) {
        c.save();
        c.translate(x, y);
        c.rotate((float) Math.toDegrees(angle));
    }

    // ----------------------------------------------------------------------------------- props ----

    private void drawProp(Draw2D c, Prop q, World w) {
        RB m = q.main();
        int col = tint(q, q.color, q.flash);
        switch (q.type) {
            case Items.CRATE: case Items.BIGCRATE: case Items.PLANK: case Items.PLATFORM: case Items.STEEL: {
                Shape s = m.shapes.get(0);
                boxShape(c, s, col, true);
                local(c, m.x, m.y, m.a);
                float hw = s.hl, hh = s.hh;
                p.setStyle(Paint.Style.STROKE);
                if (q.type == Items.CRATE || q.type == Items.BIGCRATE) {
                    p.setStrokeWidth(3f); p.setColor(dark(col, 0.45f));
                    c.drawLine(-hw + 4, -hh + 4, hw - 4, hh - 4, p);
                    c.drawLine(hw - 4, -hh + 4, -hw + 4, hh - 4, p);
                    p.setStrokeWidth(4f);
                    rf.set(-hw + 3, -hh + 3, hw - 3, hh - 3);
                    c.drawRect(rf, p);
                    if (detail) { p.setStyle(Paint.Style.FILL); p.setColor(dark(col, 0.6f)); for (int sx = -1; sx <= 1; sx += 2) for (int sy = -1; sy <= 1; sy += 2) c.drawCircle(sx * (hw - 7), sy * (hh - 7), 2f, p); }
                } else if (q.type == Items.PLANK) {
                    p.setStrokeWidth(1.4f); p.setColor(dark(col, 0.4f));
                    for (float x = -hw + 20; x < hw; x += 40) c.drawLine(x, -hh + 2, x + 10, hh - 2, p);
                } else {
                    p.setStyle(Paint.Style.FILL); p.setColor(dark(col, 0.4f));
                    for (float x = -hw + 12; x < hw; x += (q.type == Items.PLATFORM ? 30f : 22f)) c.drawCircle(x, 0, 2.6f, p);
                    p.setColor(alpha(0xFFffffff, 0.25f)); rf.set(-hw + 2, -hh + 2, hw - 2, -hh + 6); c.drawRect(rf, p);
                }
                c.restore();
                break;
            }
            case Items.BARREL: case Items.XBARREL: case Items.TNT: {
                Shape s = m.shapes.get(0);
                boxShape(c, s, col, true);
                local(c, m.x, m.y, m.a);
                float hw = s.hl, hh = s.hh;
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3f); p.setColor(dark(col, 0.45f));
                if (q.type == Items.TNT) {
                    for (int i = -1; i <= 1; i++) c.drawLine(i * hw * 0.55f, -hh + 2, i * hw * 0.55f, hh - 2, p);
                    p.setStyle(Paint.Style.FILL); p.setColor(0xFFfff2cf); label(c, "TNT", hh * 0.9f);
                } else {
                    c.drawLine(-hw + 1, -hh * 0.55f, hw - 1, -hh * 0.55f, p);
                    c.drawLine(-hw + 1, hh * 0.55f, hw - 1, hh * 0.55f, p);
                    if (q.type == Items.XBARREL) { p.setStyle(Paint.Style.FILL); p.setColor(0xFFffd23f); label(c, "!", hh * 0.9f); }
                }
                c.restore();
                break;
            }
            case Items.BALL: case Items.BALLOON: case Items.BOWLING: case Items.MELON: {
                Shape s = m.shapes.get(0);
                circleShape(c, s, col);
                p.setStyle(Paint.Style.FILL);
                p.setColor(alpha(0xFFffffff, 0.35f));
                c.drawCircle(s.cx - s.r * 0.35f, s.cy - s.r * 0.38f, s.r * 0.22f, p);
                local(c, m.x, m.y, m.a);
                if (q.type == Items.BOWLING) {
                    p.setColor(0xFF05070f);
                    c.drawCircle(-4, -6, 2.4f, p); c.drawCircle(4, -7, 2.4f, p); c.drawCircle(0, -1, 2.4f, p);
                } else if (q.type == Items.MELON) {
                    p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3f); p.setColor(0xFF2a6e29);
                    for (int i = -1; i <= 1; i++) c.drawLine(i * 8f, -s.r + 3, i * 11f, s.r - 3, p);
                } else if (q.type == Items.BALL) {
                    p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.2f); p.setColor(dark(col, 0.4f));
                    c.drawLine(-s.r + 2, 0, s.r - 2, 0, p);
                }
                c.restore();
                if (q.type == Items.BALLOON) {
                    p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.4f); p.setColor(0xCC333333);
                    c.drawLine(s.cx, s.cy + s.r, s.cx + (float) Math.sin(time * 2f + q.id) * 6f, s.cy + s.r + 38f, p);
                }
                break;
            }
            case Items.WHEEL: case Items.MOTOR: {
                Shape s = m.shapes.get(0);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFF1f2126); c.drawCircle(s.cx, s.cy, s.r, p);
                local(c, m.x, m.y, m.a);
                p.setColor(0xFF363a42);
                for (int i = 0; i < 10; i++) { float a = i * 0.6283f; c.drawCircle((float) Math.cos(a) * (s.r - 3.5f), (float) Math.sin(a) * (s.r - 3.5f), 2.6f, p); }
                p.setColor(q.motorized ? 0xFFf2b134 : 0xFF9aa3ad); c.drawCircle(0, 0, s.r * 0.55f, p);
                p.setColor(0xFF4a525c); c.drawCircle(0, 0, s.r * 0.2f, p);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2f); p.setColor(dark(q.motorized ? 0xFFf2b134 : 0xFF9aa3ad, 0.5f));
                for (int i = 0; i < 5; i++) { float a = i * 1.2566f; c.drawLine(0, 0, (float) Math.cos(a) * s.r * 0.52f, (float) Math.sin(a) * s.r * 0.52f, p); }
                c.restore();
                break;
            }
            case Items.GRENADE: {
                circleShape(c, m.shapes.get(0), col);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.6f); p.setColor(0xFFb9bcc2);
                Shape s = m.shapes.get(0);
                c.drawLine(s.cx, s.cy - 6f, s.cx + 5f, s.cy - 12f, p);
                break;
            }
            case Items.DYNAMITE: {
                Shape s = m.shapes.get(0);
                local(c, m.x, m.y, m.a);
                p.setStyle(Paint.Style.FILL);
                for (int i = -1; i <= 1; i++) { p.setColor(dark(col, 0.5f)); rf.set(-18f, i * 7f - 4.4f, 18f, i * 7f + 4.4f); c.drawRoundRect(rf, 3, 3, p); p.setColor(col); rf.set(-17f, i * 7f - 3.2f, 17f, i * 7f + 3.2f); c.drawRoundRect(rf, 3, 3, p); }
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2f); p.setColor(0xFFe9e0c9);
                c.drawLine(16f, -2f, 24f, -12f, p);
                c.restore();
                break;
            }
            case Items.C4: {
                boxShape(c, m.shapes.get(0), col, true);
                boolean on = ((int) (time * 2.5f) & 1) == 0;
                p.setStyle(Paint.Style.FILL);
                p.setColor(on ? 0xFFff3b30 : 0xFF5a1a16);
                c.emissive(on);
                c.drawCircle(m.x, m.y - 2f, 2.8f, p);
                c.emissive(false);
                if (on) c.light(m.x, m.y - 4f, 60f, 0xFFff3b30, 0.6f);
                break;
            }
            case Items.KNIFE: case Items.SWORD: {
                for (Shape s : m.shapes) capsuleShape(c, s, s.color);
                break;
            }
            case Items.THRUSTER: {
                Shape s = m.shapes.get(0);
                boxShape(c, s, col, true);
                local(c, m.x, m.y, m.a);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFFd33a2c); c.drawCircle(0, -s.hh, s.hl, p);
                p.setColor(0xFF3a3f48); rf.set(-s.hl * 0.8f, s.hh - 2, s.hl * 0.8f, s.hh + 8); c.drawRect(rf, p);
                p.setColor(q.thrust ? 0xFF6dff8a : 0xFF555b66); c.drawCircle(0, 0, 2.8f, p);
                if (q.thrust) { c.emissive(true); p.setColor(0xFFffe27a); poly[0] = -5; poly[1] = s.hh + 8; poly[2] = 5; poly[3] = s.hh + 8; poly[4] = 0; poly[5] = s.hh + 24 + (float) Math.sin(time * 50f) * 5f; c.drawPoly(poly, 3, p); c.emissive(false); }
                c.restore();
                break;
            }
            case Items.GUN_PISTOL: case Items.GUN_RIFLE: case Items.GUN_MG: {
                for (Shape s : m.shapes) { if (s.type == Shape.CAPSULE) capsuleShape(c, s, s.color); else boxShape(c, s, s.color, true); }
                if (q.firing) { p.setStyle(Paint.Style.FILL); p.setColor(0xFFff5a3a); c.drawCircle(m.x, m.y, 3f, p); }
                break;
            }
            case Items.TURRET: drawTurret(c, q); break;
            case Items.DRONE: drawDrone(c, q); break;
            case Items.ROPE_ITEM: {
                for (RB b : q.bodies) for (Shape s : b.shapes) capsuleShape(c, s, 0xFFc9a066);
                break;
            }
            default: for (RB b : q.bodies) for (Shape s : b.shapes) { if (s.type == Shape.CIRCLE) circleShape(c, s, col); else if (s.type == Shape.BOX) boxShape(c, s, col, true); else capsuleShape(c, s, col); }
        }
        if (m.frozen) pin(c, m.x, m.y);
    }

    private void label(Draw2D c, String t, float size) {
        // tiny stroke font for "TNT" and "!"
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.6f);
        float k = size * 0.3f;
        if (t.equals("!")) { c.drawLine(0, -size * 0.6f, 0, size * 0.15f, p); p.setStyle(Paint.Style.FILL); c.drawCircle(0, size * 0.5f, 2.4f, p); return; }
        float[] xs = {-k * 2.4f, 0f, k * 2.4f};
        for (int i = 0; i < 3; i++) {
            float x = xs[i];
            if (i == 1) { c.drawLine(x - k, -k, x - k, k, p); c.drawLine(x - k, -k, x + k, k, p); c.drawLine(x + k, -k, x + k, k, p); }
            else { c.drawLine(x - k, -k, x + k, -k, p); c.drawLine(x, -k, x, k, p); }
        }
    }

    private void drawTurret(Draw2D c, Prop q) {
        RB base = q.bodies.get(0), gun = q.barrel;
        int col = tint(q, q.color, q.flash);
        for (Shape s : base.shapes) { if (s.type == Shape.CAPSULE) capsuleShape(c, s, s.color); else boxShape(c, s, s.color, true); }
        if (gun != null) {
            Shape bar = gun.shapes.get(0), head = gun.shapes.get(1);
            if (q.target != null && !q.dead && q.on) {
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.6f); p.setColor(0x88ff2a2a);
                c.emissive(true);
                float mx = gun.x + (float) Math.cos(gun.a) * 28f, my = gun.y + (float) Math.sin(gun.a) * 28f;
                c.drawLine(mx, my, mx + (float) Math.cos(gun.a) * 900f, my + (float) Math.sin(gun.a) * 900f, p);
                c.emissive(false);
            }
            capsuleShape(c, bar, bar.color);
            boxShape(c, head, col, true);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFF1b222b);
            c.drawCircle(head.cx, head.cy, 5.5f, p);
            int eye = q.dead ? 0xFF3b4652 : (!q.on ? 0xFF555b66 : (q.target != null ? 0xFFff3b30 : 0xFF35e0ff));
            p.setColor(eye);
            c.emissive(!q.dead && q.on);
            c.drawCircle(head.cx, head.cy, 3f, p);
            c.emissive(false);
        }
    }

    private void drawDrone(Draw2D c, Prop q) {
        RB m = q.main();
        Shape s = m.shapes.get(0);
        capsuleShape(c, s, tint(q, q.color, q.flash));
        boolean on = q.on && !q.dead;
        float spin = on ? (float) Math.abs(Math.sin(time * 50f)) : 1f;
        float ux = s.ax, uy = s.ay, nx = uy, ny = -ux;
        if (ny > 0) { nx = -nx; ny = -ny; }
        for (int k = -1; k <= 1; k += 2) {
            float rx = s.cx + ux * s.hl * k, ry = s.cy + uy * s.hl * k;
            float hx = rx + nx * 9f, hy = ry + ny * 9f;
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3f); p.setColor(0xFF2a2e35);
            c.drawLine(rx, ry, hx, hy, p);
            p.setStrokeWidth(3.2f); p.setColor(on ? 0x88c8d0da : 0xFF8a929c);
            float half = 6f + 14f * spin;
            c.drawLine(hx - ux * half, hy - uy * half, hx + ux * half, hy + uy * half, p);
        }
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF111418); c.drawCircle(s.cx - nx * 5f, s.cy - ny * 5f, 4.6f, p);
        p.setColor(q.dead ? 0xFF3b4652 : (!q.on ? 0xFF555b66 : (q.target != null ? 0xFFff3b30 : 0xFF6dff8a)));
        c.emissive(on);
        c.drawCircle(s.cx - nx * 5f, s.cy - ny * 5f, 2.6f, p);
        c.emissive(false);
    }

    private void pin(Draw2D c, float x, float y) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFe0463c);
        c.drawCircle(x, y, 5f, p);
        p.setColor(0xFFffffff);
        c.drawCircle(x - 1.5f, y - 1.5f, 1.6f, p);
    }

    private void drawPins(Draw2D c, World w) { }

    private void drawLinks(Draw2D c, World w) {
        for (int i = 0; i < w.sim.joints.size(); i++) {
            Joint j = w.sim.joints.get(i);
            if (!(j.user == null) || j.kind == Joint.MOUSE) continue;
            float x = j.anchorBX(), y = j.anchorBY();
            if (j.a != null && j.a.isStatic && j.b != null) {
                p.setStyle(Paint.Style.FILL); p.setColor(0xFF2b2f36); c.drawCircle(x, y, 7f, p); p.setColor(0xFF9aa3ad); c.drawCircle(x, y, 5f, p);
                continue;
            }
            p.setStyle(Paint.Style.FILL);
            if (j.kind == Joint.WELD) { p.setColor(0xFF2b2f36); c.drawCircle(x, y, 6f, p); p.setColor(0xFFb9c0ca); c.drawCircle(x, y, 4f, p); }
            else { p.setColor(0xFF2b2f36); c.drawCircle(x, y, 7f, p); p.setColor(0xFFe9ecf1); c.drawCircle(x, y, 3.4f, p); p.setColor(0xFF4a525c); c.drawCircle(x, y, 1.4f, p); }
        }
    }

    // ---------------------------------------------------------------------------------- people -----

    private int bodyColor(Person b, int col) {
        if (b.dead) col = mix(col, 0xFF9aa0a8, 0.22f);
        return tint(b, col, b.flash);
    }

    private void part(Draw2D c, Person b, int idx, int col) {
        if (b.severed[Person.PART_JOINT[idx] < 0 ? 0 : Person.PART_JOINT[idx]] && false) return;
        Shape s = b.rb[idx].shapes.get(0);
        if (s.type == Shape.CAPSULE) capsuleShape(c, s, bodyColor(b, col));
        else if (s.type == Shape.CIRCLE) circleShape(c, s, bodyColor(b, col));
    }

    private void foot(Draw2D c, Person b, int idx) {
        Shape s = b.rb[idx].shapes.get(0);
        capsuleShape(c, s, bodyColor(b, 0xFF2b2b30));
    }

    private void hand(Draw2D c, Person b, int idx) {
        RB r = b.rb[idx];
        float hl = r.shapes.get(0).hl, rad = r.shapes.get(0).r;
        float hx = r.localToWorldX(0, hl), hy = r.localToWorldY(0, hl);
        p.setStyle(Paint.Style.FILL);
        p.setColor(bodyColor(b, b.skin));
        c.drawCircle(hx, hy, rad * 1.15f, p);
    }

    private void drawPerson(Draw2D c, Person b, World w) {
        int skin = b.skin;
        // back limbs
        part(c, b, Person.THIGH_B, b.pants); part(c, b, Person.SHIN_B, b.pants); foot(c, b, Person.FOOT_B);
        part(c, b, Person.UARM_B, b.shirt); part(c, b, Person.FARM_B, skin); hand(c, b, Person.FARM_B);
        // torso
        part(c, b, Person.PELVIS, b.pants);
        part(c, b, Person.CHEST, b.shirt);
        torsoDetail(c, b);
        // head
        drawHead(c, b);
        // front leg
        part(c, b, Person.THIGH_F, b.pants); part(c, b, Person.SHIN_F, b.pants); foot(c, b, Person.FOOT_F);
        gear(c, b);
        part(c, b, Person.UARM_F, b.shirt); part(c, b, Person.FARM_F, skin); hand(c, b, Person.FARM_F);
        if (b == inspect) inspectOverlay(c, b);
        // stumps
        p.setStyle(Paint.Style.FILL);
        for (int j = 0; j < Person.NJOINTS; j++) {
            if (!b.severed[j]) continue;
            Joint jn = b.jt[j];
            p.setColor(b.organic() || b.kind == Person.ZOMBIE ? (b.kind == Person.ZOMBIE ? 0xFF3b6e1a : 0xFF8e1111) : 0xFF6b6f78);
            float r = (j == Person.NECK ? 4.8f : 4.3f) * b.scale;
            c.drawCircle(jn.anchorAX(), jn.anchorAY(), r, p);
            c.drawCircle(jn.anchorBX(), jn.anchorBY(), r, p);
        }
        if (b.rb[Person.PELVIS].frozen) pin(c, b.rb[Person.PELVIS].x, b.rb[Person.PELVIS].y);
        if (b.adrenaline > 0f) { p.setStyle(Paint.Style.FILL); c.emissive(true); p.setColor(alpha(0xFFffd23f, 0.25f)); c.drawCircle(b.cx(), b.cy() - 40f, b.radius() * 0.5f, p); c.emissive(false); }
    }

    private void torsoDetail(Draw2D c, Person b) {
        RB ch = b.rb[Person.CHEST];
        float s = b.scale;
        local(c, ch.x, ch.y, ch.a);
        float hl = 22f * s, hw = 11.5f * s;
        p.setStrokeCap(Paint.Cap.BUTT);
        switch (b.kind) {
            case Person.SKELETON:
                p.setStyle(Paint.Style.STROKE); p.setColor(0xFF8d8774); p.setStrokeWidth(2.2f * s);
                for (int i = 0; i < 4; i++) { float y = -hl * 0.5f + i * hl * 0.3f; c.drawLine(-hw * 0.85f, y, hw * 0.85f, y, p); }
                c.drawLine(0, -hl, 0, hl, p);
                break;
            case Person.ROBOT:
                p.setStyle(Paint.Style.STROKE); p.setColor(0xFF33414f); p.setStrokeWidth(2f * s);
                c.drawLine(-hw * 0.8f, hl * 0.2f, hw * 0.8f, hl * 0.2f, p);
                p.setStyle(Paint.Style.FILL); p.setColor(b.dead ? 0xFF555f6a : 0xFF35e0ff);
                c.emissive(!b.dead);
                c.drawCircle(b.dir * 3f * s, -hl * 0.25f, 3.2f * s, p);
                c.emissive(false);
                break;
            case Person.ZOMBIE:
                p.setStyle(Paint.Style.STROKE); p.setColor(alpha(0xFF111111, 0.55f)); p.setStrokeWidth(2f * s);
                for (int i = 0; i < 3; i++) { float y = -hl * 0.6f + i * hl * 0.5f; c.drawLine(-hw * 0.6f, y, hw * 0.2f, y + 6f, p); }
                break;
            case Person.SOLDIER:
                p.setStyle(Paint.Style.FILL); p.setColor(alpha(0xFF2f3a22, 0.7f));
                for (int i = 0; i < 3; i++) c.drawCircle((i & 1) == 0 ? -hw * 0.4f : hw * 0.4f, -hl * 0.6f + i * hl * 0.55f, hw * 0.38f, p);
                p.setStyle(Paint.Style.STROKE); p.setColor(0xFF3a2f1c); p.setStrokeWidth(3f * s);
                c.drawLine(-hw * 0.7f, -hl * 0.9f, hw * 0.7f, hl * 0.9f, p);
                break;
            case Person.MEDIC: {
                float k = hw * 0.55f;
                p.setStyle(Paint.Style.STROKE); p.setColor(b.dead ? 0xFF8a5a5a : 0xFFe0332c); p.setStrokeWidth(3.4f * s);
                c.drawLine(-k, -hl * 0.25f, k, -hl * 0.25f, p);
                c.drawLine(0, -hl * 0.25f - k, 0, -hl * 0.25f + k, p);
                break;
            }
            case Person.FIREFIGHTER:
                p.setStyle(Paint.Style.STROKE); p.setColor(0xFFe9f04a); p.setStrokeWidth(3.2f * s);
                for (int i = 0; i < 2; i++) { float y = -hl * 0.1f + i * hl * 0.55f; c.drawLine(-hw, y, hw, y, p); }
                break;
            case Person.THUG:
                p.setStyle(Paint.Style.STROKE); p.setColor(0xFF4a4d55); p.setStrokeWidth(1.6f * s);
                c.drawLine(0, -hl * 0.9f, 0, hl * 0.9f, p);
                p.setColor(0xFFd8b23a); p.setStrokeWidth(1.8f * s);
                c.drawLine(-hw * 0.5f, -hl * 0.8f, 0, -hl * 0.45f, p); c.drawLine(hw * 0.5f, -hl * 0.8f, 0, -hl * 0.45f, p);
                break;
            default:
                if (detail) {
                    int pat = (b.shirt >>> 8) & 3;
                    p.setStyle(Paint.Style.STROKE);
                    if (pat == 1) { p.setColor(alpha(dark(bodyColor(b, b.shirt), 0.45f), 0.8f)); p.setStrokeWidth(4f * s); for (int i = 0; i < 5; i++) { float y = -hl * 0.85f + i * hl * 0.4f; c.drawLine(-hw * 0.95f, y, hw * 0.95f, y, p); } }
                    else if (pat == 2) { p.setColor(alpha(dark(bodyColor(b, b.shirt), 0.5f), 0.9f)); p.setStrokeWidth(2f * s); c.drawLine(b.dir * hw * 0.2f, -hl * 0.4f, b.dir * hw * 0.8f, -hl * 0.4f, p); }
                }
        }
        c.restore();
        // belt on the pelvis
        if (b.kind == Person.HUMAN || b.kind == Person.SOLDIER || b.kind == Person.MEDIC || b.kind == Person.THUG) {
            RB pel = b.rb[Person.PELVIS];
            local(c, pel.x, pel.y, pel.a);
            p.setStyle(Paint.Style.STROKE); p.setColor(alpha(0xFF14161c, 0.8f)); p.setStrokeWidth(3f * s);
            c.drawLine(-11f * s, -9f * s, 11f * s, -9f * s, p);
            c.restore();
        }
        p.setStrokeCap(Paint.Cap.ROUND);
    }

    private void drawHead(Draw2D c, Person b) {
        RB h = b.rb[Person.HEAD];
        float r = h.shapes.get(0).r;
        int col = bodyColor(b, b.skin);
        local(c, h.x, h.y, h.a);
        int d = b.dir;
        p.setStyle(Paint.Style.FILL);
        p.setColor(dark(col, 0.55f));
        c.drawCircle(0, 0, r + 1.6f, p);
        p.setColor(col);
        c.drawCircle(0, 0, r, p);
        float ex = d * r * 0.5f, ey = -r * 0.12f;
        if (b.kind == Person.ROBOT) {
            p.setColor(bodyColor(b, 0xFF7f8c99));
            rf.set(-r * 0.95f, -r * 0.95f, r * 0.95f, r * 0.95f);
            c.drawRoundRect(rf, r * 0.25f, r * 0.25f, p);
            p.setColor(0xFF1b222b);
            if (d > 0) rf.set(-r * 0.05f, -r * 0.42f, r * 0.95f, r * 0.1f); else rf.set(-r * 0.95f, -r * 0.42f, r * 0.05f, r * 0.1f);
            c.drawRoundRect(rf, r * 0.1f, r * 0.1f, p);
            p.setColor(b.dead ? 0xFF3b4652 : 0xFF35e0ff);
            c.emissive(!b.dead);
            c.drawCircle(ex, -r * 0.16f, r * 0.14f, p);
            c.emissive(false);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2f); p.setColor(0xFF9aa5b1);
            c.drawLine(0, -r * 0.95f, 0, -r * 1.35f, p);
            p.setStyle(Paint.Style.FILL); p.setColor(b.dead ? 0xFF555f6a : 0xFFff4d4d);
            c.drawCircle(0, -r * 1.4f, r * 0.12f, p);
        } else if (b.kind == Person.SKELETON) {
            p.setColor(0xFF2a2820);
            c.drawCircle(d * r * 0.42f, -r * 0.1f, r * 0.24f, p);
            c.drawCircle(-d * r * 0.1f, -r * 0.1f, r * 0.2f, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.8f);
            for (int i = -1; i <= 1; i++) c.drawLine(d * (r * 0.1f + i * r * 0.18f), r * 0.45f, d * (r * 0.1f + i * r * 0.18f), r * 0.78f, p);
            p.setStyle(Paint.Style.FILL);
        } else {
            boolean z = b.kind == Person.ZOMBIE;
            if (!z) {
                p.setColor(bodyColor(b, b.hair));
                rf.set(-r, -r, r, r);
                c.drawArc(rf, 180f, 180f, true, p);
                c.drawCircle(-d * r * 0.42f, -r * 0.1f, r * 0.62f, p);
                p.setColor(col);
                c.drawCircle(d * r * 0.28f, r * 0.02f, r * 0.62f, p);
            }
            p.setColor(z ? 0xFFd62020 : 0xFF1b1b1b);
            if (b.dead && !z) {
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.8f);
                float k = r * 0.2f;
                c.drawLine(ex - k, ey - k, ex + k, ey + k, p); c.drawLine(ex - k, ey + k, ex + k, ey - k, p);
            } else {
                c.drawCircle(ex, ey, r * (z ? 0.2f : 0.15f), p);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.6f); p.setColor(z ? 0xFF4a1010 : 0xFF1b1b1b);
                if (z) c.drawLine(d * r * 0.25f, r * 0.45f, d * r * 0.75f, r * 0.55f, p);
                else c.drawLine(d * r * 0.3f, r * 0.42f, d * r * 0.72f, r * 0.42f, p);
            }
            p.setStyle(Paint.Style.FILL);
            hat(c, b, r);
        }
        c.restore();
    }

    private void hat(Draw2D c, Person b, float r) {
        p.setStyle(Paint.Style.FILL);
        int d = b.dir;
        switch (b.kind) {
            case Person.SOLDIER:
                p.setColor(bodyColor(b, 0xFF3f4a2c));
                rf.set(-r * 1.12f, -r * 1.2f, r * 1.12f, r * 0.9f); c.drawArc(rf, 180f, 180f, true, p);
                rf.set(-r * 1.25f, -r * 0.22f, r * 1.25f, r * 0.02f); c.drawRoundRect(rf, r * 0.1f, r * 0.1f, p);
                break;
            case Person.MEDIC:
                p.setColor(bodyColor(b, 0xFFf4f6f8));
                rf.set(-r * 0.98f, -r * 1.05f, r * 0.98f, r * 0.55f); c.drawArc(rf, 180f, 180f, true, p);
                p.setColor(0xFFe0332c);
                rf.set(-r * 0.08f, -r * 0.92f, r * 0.08f, -r * 0.4f); c.drawRect(rf, p);
                rf.set(-r * 0.26f, -r * 0.74f, r * 0.26f, -r * 0.58f); c.drawRect(rf, p);
                break;
            case Person.FIREFIGHTER:
                p.setColor(bodyColor(b, 0xFFf2b134));
                rf.set(-r * 1.1f, -r * 1.25f, r * 1.1f, r * 0.85f); c.drawArc(rf, 180f, 180f, true, p);
                rf.set(-d * r * 0.6f - r * 0.75f, -r * 0.24f, -d * r * 0.6f + r * 0.75f, r * 0.04f); c.drawRoundRect(rf, r * 0.12f, r * 0.12f, p);
                p.setColor(0xFFd5d9de); c.drawCircle(d * r * 0.55f, -r * 0.55f, r * 0.2f, p);
                break;
            case Person.THUG:
                p.setColor(bodyColor(b, 0xFF1c1e22));
                rf.set(-r * 1.02f, -r * 1.12f, r * 1.02f, r * 0.6f); c.drawArc(rf, 180f, 180f, true, p);
                if (!b.dead) {
                    p.setColor(0xFF0d0e10);
                    float xa = d * r * 0.15f, xb = d * r * 0.95f;
                    rf.set(Math.min(xa, xb), -r * 0.3f, Math.max(xa, xb), r * 0.05f); c.drawRoundRect(rf, r * 0.1f, r * 0.1f, p);
                }
                break;
            default: break;
        }
    }

    private void gear(Draw2D c, Person b) {
        if (b.kind != Person.SOLDIER && b.kind != Person.FIREFIGHTER) return;
        if (b.severed[Person.SH_F] || b.severed[Person.EL_F]) return;
        float s = b.scale;
        RB f = b.rb[Person.FARM_F];
        float hl = f.shapes.get(0).hl;
        float hx = f.localToWorldX(0, hl), hy = f.localToWorldY(0, hl);
        float ux = -(float) Math.sin(f.a), uy = (float) Math.cos(f.a);        // elbow -> wrist
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        if (b.kind == Person.SOLDIER) {
            float bx = hx - ux * 24f * s, by = hy - uy * 24f * s, ex = hx + ux * 30f * s, ey = hy + uy * 30f * s;
            p.setColor(bodyColor(b, 0xFF1d2024)); p.setStrokeWidth(6.5f * s);
            c.drawLine(bx, by, hx + ux * 8f * s, hy + uy * 8f * s, p);
            p.setStrokeWidth(3.6f * s);
            c.drawLine(bx, by, ex, ey, p);
            p.setColor(bodyColor(b, 0xFF5b4024)); p.setStrokeWidth(6f * s);
            c.drawLine(bx - ux * 10f * s, by - uy * 10f * s, bx, by, p);
            p.setColor(bodyColor(b, 0xFF2b2b30)); p.setStrokeWidth(4f * s);
            float nx = -uy * b.dir, ny = ux * b.dir;
            if (ny < 0) { nx = -nx; ny = -ny; }
            c.drawLine(hx - ux * 6f * s, hy - uy * 6f * s, hx - ux * 8f * s + nx * 10f * s, hy - uy * 8f * s + ny * 10f * s, p);
        } else {
            RB ch = b.rb[Person.CHEST];
            float tx = ch.localToWorldX(-b.dir * 13f * s, 6f * s), ty = ch.localToWorldY(-b.dir * 13f * s, 6f * s);
            p.setColor(bodyColor(b, 0xFFc93a2c)); p.setStrokeWidth(10f * s);
            c.drawLine(tx, ty - 10f * s, tx, ty + 14f * s, p);
            p.setColor(0xFF1d1f24); p.setStrokeWidth(2.6f * s);
            float mx = (tx + hx) * 0.5f, my = Math.max(ty, hy) + 14f * s;
            c.drawLine(tx, ty + 14f * s, mx, my, p);
            c.drawLine(mx, my, hx, hy, p);
            p.setColor(0xFFb9c0ca); p.setStrokeWidth(4f * s);
            c.drawLine(hx, hy, hx + ux * 12f * s, hy + uy * 12f * s, p);
        }
        p.setStyle(Paint.Style.FILL);
    }

    private void inspectOverlay(Draw2D c, Person b) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < Person.NPARTS; i++) {
            if (i == Person.HEAD) continue;
            Shape s = b.rb[i].shapes.get(0);
            float h = Math.max(0f, Math.min(1f, b.hp[i] / Person.maxHp(b, i)));
            int col = b.broken[i] ? 0xFFc061ff : (h > 0.5f ? mix(0xFFf2d13a, 0xFF4cdc6b, (h - 0.5f) * 2f) : mix(0xFFe0463c, 0xFFf2d13a, h * 2f));
            p.setColor(alpha(col, 0.6f));
            p.setStrokeWidth(s.r * 2f + 2f);
            c.drawLine(s.cx - s.ax * s.hl, s.cy - s.ay * s.hl, s.cx + s.ax * s.hl, s.cy + s.ay * s.hl, p);
        }
        RB hd = b.rb[Person.HEAD];
        float hh = Math.max(0f, b.hp[Person.HEAD] / Person.maxHp(b, Person.HEAD));
        p.setStyle(Paint.Style.FILL);
        p.setColor(alpha(hh > 0.5f ? 0xFF4cdc6b : 0xFFe0463c, 0.45f));
        c.drawCircle(hd.x, hd.y, hd.shapes.get(0).r, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2.5f);
        p.setColor(0xCCffffff);
        c.drawCircle(b.cx(), b.cy() - 40f * b.scale, b.radius() * 0.8f, p);
        p.setStyle(Paint.Style.FILL);
    }
}

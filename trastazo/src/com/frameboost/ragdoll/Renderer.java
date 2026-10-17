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
    private boolean gore = true;

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
        {0xFF2a2e35, 0xFF22252b, 0xFF5a5f68, 0xFF34373d, 0xFF3d424b},
    };
    static final int SANDBOX = 6;

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
            case SANDBOX: {
                // the classic sandbox look: a flat dark room with a fine and a bold grid
                p.setStrokeWidth(1.2f);
                p.setColor(alpha(0xFF59606c, 0.35f));
                for (float x = wl; x <= wr; x += 50f) c.drawLine(x, ct, x, fl, p);
                for (float y = fl; y >= ct; y -= 50f) c.drawLine(wl, y, wr, y, p);
                p.setStrokeWidth(2.4f);
                p.setColor(alpha(0xFF6c7480, 0.45f));
                for (float x = wl; x <= wr; x += 250f) c.drawLine(x, ct, x, fl, p);
                for (float y = fl; y >= ct; y -= 250f) c.drawLine(wl, y, wr, y, p);
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
        if (theme == SANDBOX) {
            p.setColor(alpha(0xFF000000, 0.25f));
            for (float x = wl - 400f; x < wr + 400f; x += 250f) c.drawLine(x, fl + 20f, x, fl + 700f, p);
            for (float y = fl + 125f; y < fl + 700f; y += 125f) c.drawLine(wl - 400f, y, wr + 400f, y, p);
        } else {
            for (float x = wl - 400f; x < wr + 400f; x += 160f) c.drawLine(x, fl + 20f, x - 40f, fl + 700f, p);
            for (float y = fl + 100f; y < fl + 700f; y += 100f) c.drawLine(wl - 400f, y, wr + 400f, y, p);
        }
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
        gore = w.fx.gore;
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
            case Items.CRATE: case Items.BIGCRATE: {
                Shape s = m.shapes.get(0);
                float hw = s.hl, hh = s.hh;
                local(c, m.x, m.y, m.a);
                orect(c, -hw, -hh, hw, hh, 2f, col);
                // planks with a dark frame and a diagonal brace
                p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.BUTT);
                int seam = dark(col, 0.35f);
                p.setColor(seam); p.setStrokeWidth(1.4f);
                int planks = q.type == Items.BIGCRATE ? 5 : 3;
                for (int i = 1; i < planks; i++) { float y = -hh + i * 2f * hh / planks; c.drawLine(-hw + 3f, y, hw - 3f, y, p); }
                float fw = hw * 0.16f;
                p.setStyle(Paint.Style.FILL); p.setColor(dark(col, 0.18f));
                rf.set(-hw, -hh, hw, -hh + fw); c.drawRect(rf, p); rf.set(-hw, hh - fw, hw, hh); c.drawRect(rf, p);
                rf.set(-hw, -hh, -hw + fw, hh); c.drawRect(rf, p); rf.set(hw - fw, -hh, hw, hh); c.drawRect(rf, p);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(fw * 0.9f); p.setColor(dark(col, 0.1f));
                c.drawLine(-hw + fw, hh - fw, hw - fw, -hh + fw, p);
                p.setStrokeWidth(1.2f); p.setColor(seam);
                c.drawLine(-hw + fw, -hh + fw, hw - fw, -hh + fw, p); c.drawLine(-hw + fw, hh - fw, hw - fw, hh - fw, p);
                if (detail) {
                    p.setStyle(Paint.Style.FILL); p.setColor(0xFF6f7177);
                    for (int sx = -1; sx <= 1; sx += 2) for (int sy = -1; sy <= 1; sy += 2) c.drawCircle(sx * (hw - fw * 0.5f), sy * (hh - fw * 0.5f), 1.6f, p);
                    p.setColor(alpha(0xFFffffff, 0.14f)); rf.set(-hw + 2f, -hh + 2f, hw - 2f, -hh + fw * 0.6f); c.drawRect(rf, p);
                }
                p.setStrokeCap(Paint.Cap.ROUND);
                c.restore();
                break;
            }
            case Items.PLANK: {
                Shape s = m.shapes.get(0);
                float hw = s.hl, hh = s.hh;
                local(c, m.x, m.y, m.a);
                orect(c, -hw, -hh, hw, hh, 2f, col);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.1f); p.setColor(alpha(dark(col, 0.4f), 0.8f));
                for (float x = -hw + 14f; x < hw - 6f; x += 46f) { c.drawLine(x, -hh + 3f, x + 22f, -hh + 4.5f, p); c.drawLine(x + 10f, hh - 3f, x + 34f, hh - 4f, p); }
                p.setStyle(Paint.Style.FILL); p.setColor(dark(col, 0.45f));
                c.drawCircle(-hw * 0.35f, 0f, 2f, p); c.drawCircle(hw * 0.55f, hh * 0.2f, 1.6f, p);
                p.setColor(0xFF6f7177); c.drawCircle(-hw + 6f, 0f, 1.5f, p); c.drawCircle(hw - 6f, 0f, 1.5f, p);
                c.restore();
                break;
            }
            case Items.PLATFORM: {
                Shape s = m.shapes.get(0);
                float hw = s.hl, hh = s.hh;
                local(c, m.x, m.y, m.a);
                orect(c, -hw, -hh, hw, hh, 1.5f, col);
                p.setStyle(Paint.Style.FILL);
                // hazard stripes at both ends
                for (int e = -1; e <= 1; e += 2) for (int i = 0; i < 4; i++) {
                    float x0 = e < 0 ? -hw + i * 10f : hw - (i + 1) * 10f;
                    p.setColor((i & 1) == 0 ? 0xFFf2c200 : 0xFF222222);
                    poly[0] = x0; poly[1] = hh; poly[2] = x0 + 5f; poly[3] = -hh; poly[4] = x0 + 10f; poly[5] = -hh; poly[6] = x0 + 5f; poly[7] = hh;
                    c.drawPoly(poly, 4, p);
                }
                p.setColor(alpha(0xFFffffff, 0.2f)); rf.set(-hw + 40f, -hh + 1.5f, hw - 40f, -hh + 4f); c.drawRect(rf, p);
                p.setColor(dark(col, 0.4f)); for (float x = -hw + 52f; x < hw - 45f; x += 34f) c.drawCircle(x, 2f, 2f, p);
                c.restore();
                break;
            }
            case Items.STEEL: {
                Shape s = m.shapes.get(0);
                float hw = s.hl, hh = s.hh;
                local(c, m.x, m.y, m.a);
                // an I-beam section: web and two flanges
                orect(c, -hw, -hh, hw, hh, 1.5f, dark(col, 0.12f));
                p.setStyle(Paint.Style.FILL);
                p.setColor(col); rf.set(-hw, -hh, hw, -hh + hh * 0.38f); c.drawRect(rf, p); rf.set(-hw, hh - hh * 0.38f, hw, hh); c.drawRect(rf, p);
                p.setColor(light(col, 0.25f)); rf.set(-hw + 1f, -hh + 1f, hw - 1f, -hh + 3.5f); c.drawRect(rf, p);
                p.setColor(dark(col, 0.3f)); rf.set(-hw + 1f, hh - hh * 0.38f, hw - 1f, hh - hh * 0.38f + 2f); c.drawRect(rf, p);
                p.setColor(dark(col, 0.45f));
                for (float x = -hw + 8f; x < hw; x += 16f) { c.drawCircle(x, -hh + hh * 0.19f, 1.7f, p); c.drawCircle(x, hh - hh * 0.19f, 1.7f, p); }
                c.restore();
                break;
            }
            case Items.BARREL: case Items.XBARREL: {
                Shape s = m.shapes.get(0);
                float hw = s.hl, hh = s.hh;
                local(c, m.x, m.y, m.a);
                orect(c, -hw, -hh, hw, hh, 5f, col);
                p.setStyle(Paint.Style.FILL);
                // cylinder shading
                p.setColor(dark(col, 0.25f)); rf.set(hw * 0.45f, -hh + 2f, hw - 1f, hh - 2f); c.drawRoundRect(rf, 4f, 4f, p);
                p.setColor(alpha(0xFFffffff, 0.22f)); rf.set(-hw * 0.6f, -hh + 3f, -hw * 0.3f, hh - 3f); c.drawRoundRect(rf, 3f, 3f, p);
                // ribs and lid
                p.setColor(dark(col, 0.45f));
                for (int i = -1; i <= 1; i += 2) { rf.set(-hw, i * hh * 0.45f - 2f, hw, i * hh * 0.45f + 2f); c.drawRect(rf, p); }
                rf.set(-hw * 0.85f, -hh - 2.5f, hw * 0.85f, -hh + 1f); c.drawRoundRect(rf, 2f, 2f, p);
                if (q.type == Items.XBARREL) {
                    // hazard diamond with a flame
                    p.setColor(0xFFffd23f);
                    poly[0] = 0; poly[1] = -9f; poly[2] = 9f; poly[3] = 0; poly[4] = 0; poly[5] = 9f; poly[6] = -9f; poly[7] = 0;
                    c.drawPoly(poly, 4, p);
                    p.setColor(0xFF1b1b1b);
                    poly[0] = -3.5f; poly[1] = 4f; poly[2] = 3.5f; poly[3] = 4f; poly[4] = 0.5f; poly[5] = -5f; c.drawPoly(poly, 3, p);
                } else {
                    p.setColor(alpha(0xFFffffff, 0.75f)); rf.set(-hw * 0.5f, -3.5f, hw * 0.5f, 3.5f); c.drawRect(rf, p);
                }
                c.restore();
                break;
            }
            case Items.TNT: {
                Shape s = m.shapes.get(0);
                float hw = s.hl, hh = s.hh;
                local(c, m.x, m.y, m.a);
                // three sticks taped together with a timer
                float sw = hw * 2f / 3f;
                for (int i = 0; i < 3; i++) {
                    float x0 = -hw + i * sw;
                    orect(c, x0 + 0.5f, -hh, x0 + sw - 0.5f, hh, sw * 0.45f, i == 1 ? light(col, 0.06f) : col);
                    p.setStyle(Paint.Style.FILL); p.setColor(alpha(0xFFffffff, 0.2f)); rf.set(x0 + sw * 0.2f, -hh + 3f, x0 + sw * 0.4f, hh - 3f); c.drawRoundRect(rf, 2f, 2f, p);
                }
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFF3a3a3a); rf.set(-hw - 1f, -hh * 0.3f, hw + 1f, hh * 0.15f); c.drawRect(rf, p);
                p.setColor(0xFF15171b); rf.set(-hw * 0.45f, -hh * 0.25f, hw * 0.45f, hh * 0.1f); c.drawRoundRect(rf, 2f, 2f, p);
                boolean blink = q.fuse >= 0f ? ((int) (time * 8f) & 1) == 0 : true;
                p.setColor(blink ? 0xFFff3b30 : 0xFF5a1a16);
                c.emissive(blink && q.fuse >= 0f);
                label(c, "TNT", hh * 0.42f);
                c.emissive(false);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.6f); p.setColor(0xFFe9e0c9);
                c.drawLine(0f, -hh, 3f, -hh - 7f, p); c.drawLine(3f, -hh - 7f, -1f, -hh - 11f, p);
                c.restore();
                break;
            }
            case Items.BALL: case Items.BALLOON: case Items.BOWLING: case Items.MELON: {
                Shape s = m.shapes.get(0);
                circleShape(c, s, col);
                p.setStyle(Paint.Style.FILL);
                // shade below, shine above
                p.setColor(alpha(0xFF000000, 0.18f)); c.drawCircle(s.cx + s.r * 0.15f, s.cy + s.r * 0.18f, s.r * 0.82f, p);
                p.setColor(col); c.drawCircle(s.cx - s.r * 0.08f, s.cy - s.r * 0.1f, s.r * 0.8f, p);
                p.setColor(alpha(0xFFffffff, 0.4f));
                c.drawCircle(s.cx - s.r * 0.38f, s.cy - s.r * 0.4f, s.r * 0.22f, p);
                local(c, m.x, m.y, m.a);
                if (q.type == Items.BOWLING) {
                    p.setColor(0xFF05070f);
                    c.drawCircle(-4, -6, 2.4f, p); c.drawCircle(4, -7, 2.4f, p); c.drawCircle(0, -1, 2.4f, p);
                } else if (q.type == Items.MELON) {
                    p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3f); p.setColor(0xFF2a6e29);
                    for (int i = -1; i <= 1; i++) c.drawLine(i * 8f, -s.r + 3, i * 11f, s.r - 3, p);
                    p.setStrokeWidth(2f); p.setColor(0xFF6b4a23); c.drawLine(0, -s.r, 2f, -s.r - 5f, p);
                } else if (q.type == Items.BALL) {
                    p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.2f); p.setColor(0xFFf4f4f4);
                    rf.set(-s.r + 2, -s.r * 0.35f, s.r - 2, s.r * 0.35f); c.drawArc(rf, 0f, 180f, false, p);
                    c.drawLine(0, -s.r + 2, 0, s.r - 2, p);
                }
                c.restore();
                if (q.type == Items.BALLOON) {
                    p.setStyle(Paint.Style.FILL); p.setColor(dark(col, 0.3f));
                    poly[0] = s.cx - 3f; poly[1] = s.cy + s.r + 4f; poly[2] = s.cx + 3f; poly[3] = s.cy + s.r + 4f; poly[4] = s.cx; poly[5] = s.cy + s.r - 1f; c.drawPoly(poly, 3, p);
                    p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.4f); p.setColor(0xCC333333);
                    c.drawLine(s.cx, s.cy + s.r + 4f, s.cx + (float) Math.sin(time * 2f + q.id) * 6f, s.cy + s.r + 40f, p);
                }
                break;
            }
            case Items.WHEEL: case Items.MOTOR: {
                Shape s = m.shapes.get(0);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFF111215); c.drawCircle(s.cx, s.cy, s.r + 1.4f, p);
                p.setColor(0xFF24262b); c.drawCircle(s.cx, s.cy, s.r, p);
                local(c, m.x, m.y, m.a);
                p.setColor(0xFF34373e);
                for (int i = 0; i < 12; i++) { float a = i * 0.5236f; c.drawCircle((float) Math.cos(a) * (s.r - 2.5f), (float) Math.sin(a) * (s.r - 2.5f), 2.4f, p); }
                int hub = q.motorized ? 0xFFf2b134 : 0xFFb3bac3;
                p.setColor(dark(hub, 0.5f)); c.drawCircle(0, 0, s.r * 0.6f, p);
                p.setColor(hub); c.drawCircle(0, 0, s.r * 0.52f, p);
                p.setColor(dark(hub, 0.35f));
                for (int i = 0; i < 5; i++) { float a = i * 1.2566f; c.drawCircle((float) Math.cos(a) * s.r * 0.33f, (float) Math.sin(a) * s.r * 0.33f, s.r * 0.08f, p); }
                p.setColor(light(hub, 0.3f)); c.drawCircle(0, 0, s.r * 0.16f, p);
                if (q.motorized) { p.setColor(0xFF2b2b30); rf.set(-s.r * 0.14f, -s.r * 0.14f, s.r * 0.14f, s.r * 0.14f); c.drawRect(rf, p); }
                c.restore();
                p.setColor(alpha(0xFFffffff, 0.1f)); c.drawCircle(s.cx - s.r * 0.3f, s.cy - s.r * 0.3f, s.r * 0.5f, p);
                break;
            }
            case Items.GRENADE: {
                Shape s = m.shapes.get(0);
                local(c, m.x, m.y, m.a);
                float r = s.r;
                p.setStyle(Paint.Style.FILL);
                p.setColor(dark(col, 0.6f)); rf.set(-r - 1.3f, -r * 1.15f - 1.3f, r + 1.3f, r * 1.15f + 1.3f); c.drawOval(rf, p);
                p.setColor(col); rf.set(-r, -r * 1.15f, r, r * 1.15f); c.drawOval(rf, p);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1f); p.setColor(dark(col, 0.4f));
                c.drawLine(-r, 0, r, 0, p); c.drawLine(0, -r * 1.1f, 0, r * 1.1f, p);
                c.drawLine(-r * 0.8f, -r * 0.55f, r * 0.8f, -r * 0.55f, p); c.drawLine(-r * 0.8f, r * 0.55f, r * 0.8f, r * 0.55f, p);
                p.setStyle(Paint.Style.FILL); p.setColor(0xFF8c9096); rf.set(-r * 0.45f, -r * 1.55f, r * 0.45f, -r * 1.05f); c.drawRect(rf, p);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.6f); p.setColor(0xFFb9bcc2);
                c.drawLine(r * 0.3f, -r * 1.4f, r * 1.2f, -r * 0.3f, p);
                if (q.fuse < 0f) { p.setStrokeWidth(1.2f); p.setColor(0xFFd8b23a); c.drawCircle(-r * 0.8f, -r * 1.5f, r * 0.35f, p); }
                c.restore();
                break;
            }
            case Items.DYNAMITE: {
                local(c, m.x, m.y, m.a);
                p.setStyle(Paint.Style.FILL);
                for (int i = -1; i <= 1; i++) { p.setColor(dark(col, 0.55f)); rf.set(-18f, i * 7f - 4.4f, 18f, i * 7f + 4.4f); c.drawRoundRect(rf, 3, 3, p); p.setColor(col); rf.set(-17f, i * 7f - 3.2f, 17f, i * 7f + 3.2f); c.drawRoundRect(rf, 3, 3, p); p.setColor(alpha(0xFFffffff, 0.22f)); rf.set(-15f, i * 7f - 2.6f, 15f, i * 7f - 1.2f); c.drawRect(rf, p); }
                p.setColor(0xFF3a3a3a); rf.set(-6f, -11f, 0f, 11f); c.drawRect(rf, p);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2f); p.setColor(0xFFe9e0c9);
                c.drawLine(16f, -2f, 22f, -8f, p); c.drawLine(22f, -8f, 24f, -14f, p);
                c.restore();
                break;
            }
            case Items.C4: {
                Shape s = m.shapes.get(0);
                float hw = s.hl, hh = s.hh;
                local(c, m.x, m.y, m.a);
                orect(c, -hw, -hh, hw, hh, 2f, col);
                p.setStyle(Paint.Style.FILL);
                p.setColor(dark(col, 0.2f)); rf.set(-hw + 2f, -hh + 2f, -hw * 0.1f, hh - 2f); c.drawRect(rf, p);
                p.setColor(0xFF15171b); rf.set(hw * 0.05f, -hh + 2f, hw - 2f, hh * 0.25f); c.drawRect(rf, p);
                boolean on = ((int) (time * 2.5f) & 1) == 0;
                p.setColor(on ? 0xFFff3b30 : 0xFF5a1a16);
                c.emissive(on);
                c.drawCircle(hw * 0.5f, -hh * 0.35f, 1.8f, p);
                c.emissive(false);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.2f);
                p.setColor(0xFFd33a2c); c.drawLine(-hw * 0.6f, -hh, -hw * 0.9f, -hh - 5f, p);
                p.setColor(0xFF3a7bd5); c.drawLine(-hw * 0.3f, -hh, -hw * 0.1f, -hh - 5f, p);
                c.restore();
                if (on) c.light(m.x, m.y - 4f, 60f, 0xFFff3b30, 0.6f);
                break;
            }
            case Items.KNIFE: case Items.SWORD: {
                boolean sw = q.type == Items.SWORD;
                local(c, m.x, m.y, m.a);
                float bl0 = sw ? -32f : -10f, bl1 = sw ? 64f : 24f, bw = sw ? 3.6f : 3.4f;
                // blade: a tapered polygon with an edge shine
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFF6b7380);
                poly[0] = bl0; poly[1] = -bw - 1.2f; poly[2] = bl1 - bw * 2.5f; poly[3] = -bw - 1.2f; poly[4] = bl1 + 1.5f; poly[5] = 0f; poly[6] = bl1 - bw * 2.5f; poly[7] = bw + 1.2f; poly[8] = bl0; poly[9] = bw + 1.2f;
                c.drawPoly(poly, 5, p);
                p.setColor(0xFFdfe4ea);
                poly[0] = bl0; poly[1] = -bw; poly[2] = bl1 - bw * 2.5f; poly[3] = -bw; poly[4] = bl1; poly[5] = 0f; poly[6] = bl1 - bw * 2.5f; poly[7] = bw; poly[8] = bl0; poly[9] = bw;
                c.drawPoly(poly, 5, p);
                p.setColor(0xFFf7f9fc); rf.set(bl0 + 2f, -bw, bl1 - bw * 3f, -bw * 0.35f); c.drawRect(rf, p);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1f); p.setColor(0xFFa9b0ba); c.drawLine(bl0 + 2f, 0.5f, bl1 - bw * 3f, 0.5f, p);
                // guard, handle and pommel
                float gx = bl0 - 1.5f, hl0 = sw ? -52f : -22f;
                orect(c, gx - 2.5f, sw ? -9f : -6f, gx + 1f, sw ? 9f : 6f, 1.2f, sw ? 0xFFc9a23a : 0xFF3a3d44);
                orect(c, hl0, -3.4f, gx - 2.5f, 3.4f, 2.5f, 0xFF5b3a1e);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1f); p.setColor(0xFF3a2410);
                for (float x = hl0 + 3f; x < gx - 3f; x += 3.5f) c.drawLine(x, -3f, x + 1.5f, 3f, p);
                if (sw) { p.setStyle(Paint.Style.FILL); p.setColor(0xFF8a6d1f); c.drawCircle(hl0 - 2f, 0, 3.6f, p); p.setColor(0xFFc9a23a); c.drawCircle(hl0 - 2f, 0, 2.6f, p); }
                c.restore();
                break;
            }
            case Items.THRUSTER: {
                Shape s = m.shapes.get(0);
                float hw = s.hl, hh = s.hh;
                local(c, m.x, m.y, m.a);
                orect(c, -hw, -hh, hw, hh, hw * 0.7f, col);
                p.setStyle(Paint.Style.FILL);
                p.setColor(alpha(0xFFffffff, 0.3f)); rf.set(-hw * 0.6f, -hh + 3f, -hw * 0.2f, hh - 3f); c.drawRoundRect(rf, 2f, 2f, p);
                p.setColor(0xFFd33a2c); rf.set(-hw, -hh * 0.55f, hw, -hh * 0.3f); c.drawRect(rf, p);
                // nose cone and nozzle
                p.setColor(0xFFd33a2c); poly[0] = -hw; poly[1] = -hh + 1f; poly[2] = hw; poly[3] = -hh + 1f; poly[4] = 0; poly[5] = -hh - hw * 1.3f; c.drawPoly(poly, 3, p);
                p.setColor(0xFF3a3f48); poly[0] = -hw * 0.6f; poly[1] = hh; poly[2] = hw * 0.6f; poly[3] = hh; poly[4] = hw * 1.05f; poly[5] = hh + 8f; poly[6] = -hw * 1.05f; poly[7] = hh + 8f; c.drawPoly(poly, 4, p);
                p.setColor(q.thrust ? 0xFF6dff8a : 0xFF555b66); c.drawCircle(0, hh * 0.25f, 2.4f, p);
                if (q.thrust) { c.emissive(true); p.setColor(0xFFffe27a); poly[0] = -5; poly[1] = hh + 8; poly[2] = 5; poly[3] = hh + 8; poly[4] = 0; poly[5] = hh + 24 + (float) Math.sin(time * 50f) * 5f; c.drawPoly(poly, 3, p); c.emissive(false); }
                c.restore();
                break;
            }
            case Items.GUN_PISTOL: case Items.GUN_RIFLE: case Items.GUN_MG: {
                local(c, m.x, m.y, m.a);
                drawGun(c, (int) q.param, q.hw, q.firing, q.dead ? 0 : 1);
                c.restore();
                break;
            }
            case Items.BLOCK: drawBlock(c, q, col); break;
            case Items.DOG: case Items.PIG: case Items.RAT: drawAnimal(c, q, col); break;
            case Items.POOP: drawPoop(c, q, col); break;
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

    private void limbLine(Draw2D c, float x0, float y0, float x1, float y1, float wdt, int col) {
        p.setStyle(Paint.Style.STROKE);
        p.setColor(dark(col, 0.6f)); p.setStrokeWidth(wdt + 2.2f); c.drawLine(x0, y0, x1, y1, p);
        p.setColor(col); p.setStrokeWidth(wdt); c.drawLine(x0, y0, x1, y1, p);
    }

    /** Dogs, pigs and rats, drawn in the body frame and mirrored to face their head. */
    private void drawAnimal(Draw2D c, Prop q, int col) {
        RB m = q.main();
        float[] d = q.dims();
        float hw = d[0], hh = d[1], hr = d[2], hx = Math.abs(d[3]), hy = d[4], leg = d[5], fr = d[6];
        int k = q.type - Items.DOG;
        local(c, m.x, m.y, m.a);
        c.scale(q.face, 1f);
        float ph = q.legPhase, sw = q.dead ? 0f : (float) Math.sin(ph) * leg * 0.5f, sw2 = q.dead ? 0f : (float) Math.sin(ph + Math.PI) * leg * 0.5f;
        float lift = q.dead ? 0f : Math.max(0f, (float) -Math.cos(ph)) * leg * 0.25f, lift2 = q.dead ? 0f : Math.max(0f, (float) Math.cos(ph)) * leg * 0.25f;
        float lw = k == Fauna.PIG ? fr * 1.9f : (k == Fauna.RAT ? fr * 1.1f : fr * 1.5f);
        float fy = hh + leg - fr;
        int back = dark(col, 0.22f);
        // far legs
        limbLine(c, hw * 0.62f, hh * 0.5f, hw * 0.72f + sw2, fy - lift2, lw, back);
        limbLine(c, -hw * 0.62f, hh * 0.5f, -hw * 0.72f + sw, fy - lift, lw, back);
        // tail
        float wag = q.dead ? 0f : (float) Math.sin(time * (q.mood.equals("Comiendo") ? 14f : 6f)) * 5f;
        p.setStyle(Paint.Style.STROKE);
        if (k == Fauna.DOG) { limbLine(c, -hw, -hh * 0.4f, -hw - 10f, -hh - 9f + wag, 3.2f, col); }
        else if (k == Fauna.PIG) {
            p.setColor(dark(col, 0.25f)); p.setStrokeWidth(2.4f);
            rf.set(-hw - 9f, -hh * 0.6f - 4f, -hw - 1f, -hh * 0.6f + 4f); c.drawArc(rf, 0f, 300f, false, p);
        } else {
            p.setColor(0xFFd9a3a8); p.setStrokeWidth(1.6f);
            c.drawLine(-hw, 0f, -hw - 9f, hh * 0.8f + wag * 0.3f, p); c.drawLine(-hw - 9f, hh * 0.8f + wag * 0.3f, -hw - 20f, hh * 0.4f, p);
        }
        // body
        p.setStyle(Paint.Style.FILL);
        float rr = hh * 0.85f;
        p.setColor(dark(col, 0.6f)); rf.set(-hw - 1.4f, -hh - 1.4f, hw + 1.4f, hh + 1.4f); c.drawRoundRect(rf, rr, rr, p);
        p.setColor(col); rf.set(-hw, -hh, hw, hh); c.drawRoundRect(rf, rr, rr, p);
        if (detail) {
            p.setColor(light(col, 0.18f)); rf.set(-hw * 0.8f, hh * 0.25f, hw * 0.8f, hh); c.drawRoundRect(rf, rr * 0.6f, rr * 0.6f, p);
            p.setColor(alpha(0xFFffffff, 0.16f)); rf.set(-hw * 0.7f, -hh * 0.85f, hw * 0.6f, -hh * 0.45f); c.drawRoundRect(rf, rr * 0.4f, rr * 0.4f, p);
            if (k == Fauna.DOG) { p.setColor(dark(col, 0.3f)); c.drawCircle(-hw * 0.3f, -hh * 0.2f, hh * 0.55f, p); }
        }
        // near legs
        limbLine(c, hw * 0.72f, hh * 0.5f, hw * 0.72f + sw, fy - lift, lw, col);
        limbLine(c, -hw * 0.72f, hh * 0.5f, -hw * 0.72f + sw2, fy - lift2, lw, col);
        // head
        p.setStyle(Paint.Style.FILL);
        float bob = q.eatT > 0f ? (float) Math.abs(Math.sin(time * 16f)) * 2.5f : 0f;
        float cy = hy + bob;
        if (k == Fauna.PIG) {
            p.setColor(dark(col, 0.25f)); poly[0] = hx - hr * 0.5f; poly[1] = cy - hr * 0.6f; poly[2] = hx - hr * 0.1f; poly[3] = cy - hr * 1.35f; poly[4] = hx + hr * 0.25f; poly[5] = cy - hr * 0.7f; c.drawPoly(poly, 3, p);
        } else if (k == Fauna.RAT) {
            p.setColor(0xFFd9a3a8); c.drawCircle(hx - hr * 0.3f, cy - hr * 0.95f, hr * 0.62f, p);
        }
        p.setColor(dark(col, 0.6f)); c.drawCircle(hx, cy, hr + 1.4f, p);
        p.setColor(col); c.drawCircle(hx, cy, hr, p);
        if (k == Fauna.DOG) {
            p.setColor(dark(col, 0.6f)); rf.set(hx + hr * 0.3f, cy - hr * 0.1f - 1.4f, hx + hr * 1.55f + 1.4f, cy + hr * 0.6f + 1.4f); c.drawRoundRect(rf, hr * 0.3f, hr * 0.3f, p);
            p.setColor(light(col, 0.12f)); rf.set(hx + hr * 0.3f, cy - hr * 0.1f, hx + hr * 1.55f, cy + hr * 0.6f); c.drawRoundRect(rf, hr * 0.3f, hr * 0.3f, p);
            p.setColor(0xFF1b1b1b); c.drawCircle(hx + hr * 1.5f, cy + hr * 0.05f, hr * 0.22f, p);
            p.setColor(dark(col, 0.35f)); rf.set(hx - hr * 0.75f, cy - hr * 0.9f, hx - hr * 0.1f, cy + hr * 0.5f); c.drawRoundRect(rf, hr * 0.3f, hr * 0.3f, p);
        } else if (k == Fauna.PIG) {
            p.setColor(dark(col, 0.12f)); rf.set(hx + hr * 0.55f, cy - hr * 0.3f, hx + hr * 1.3f, cy + hr * 0.5f); c.drawRoundRect(rf, hr * 0.25f, hr * 0.25f, p);
            p.setColor(dark(col, 0.45f)); c.drawCircle(hx + hr * 1.05f, cy, hr * 0.1f, p); c.drawCircle(hx + hr * 1.05f, cy + hr * 0.25f, hr * 0.1f, p);
        } else {
            p.setColor(col); poly[0] = hx + hr * 0.6f; poly[1] = cy - hr * 0.55f; poly[2] = hx + hr * 2.0f; poly[3] = cy + hr * 0.15f; poly[4] = hx + hr * 0.6f; poly[5] = cy + hr * 0.75f; c.drawPoly(poly, 3, p);
            p.setColor(0xFFd9a3a8); c.drawCircle(hx + hr * 1.95f, cy + hr * 0.12f, hr * 0.22f, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(0.8f); p.setColor(0x99222222);
            c.drawLine(hx + hr * 1.5f, cy, hx + hr * 2.6f, cy - hr * 0.3f, p); c.drawLine(hx + hr * 1.5f, cy + hr * 0.2f, hx + hr * 2.6f, cy + hr * 0.4f, p);
            p.setStyle(Paint.Style.FILL);
        }
        // eye and mouth
        float ex = hx + hr * 0.35f, ey = cy - hr * 0.3f;
        if (q.dead) {
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.4f); p.setColor(0xFF1b1b1b);
            float e = hr * 0.18f; c.drawLine(ex - e, ey - e, ex + e, ey + e, p); c.drawLine(ex - e, ey + e, ex + e, ey - e, p);
        } else {
            p.setColor(0xFF1b1b1b); c.drawCircle(ex, ey, Math.max(1.2f, hr * 0.15f), p);
            if (q.eatT > 0f && ((int) (time * 8f) & 1) == 0) {
                p.setColor(0xFF7d1a1a); c.drawCircle(hx + hr * (k == Fauna.RAT ? 1.4f : 1.1f), cy + hr * 0.6f, hr * 0.25f, p);
            }
        }
        c.restore();
    }

    /** A soft-serve swirl with stink. */
    private void drawPoop(Draw2D c, Prop q, int col) {
        RB m = q.main();
        float hw = q.hw, hh = q.hh;
        local(c, m.x, m.y, m.a);
        p.setStyle(Paint.Style.FILL);
        int out = dark(col, 0.55f);
        float[][] tiers = {{-hw, hh * 0.05f, hw, hh}, {-hw * 0.72f, -hh * 0.55f, hw * 0.72f, hh * 0.35f}, {-hw * 0.42f, -hh * 1.05f, hw * 0.42f, -hh * 0.3f}};
        for (float[] t : tiers) { p.setColor(out); rf.set(t[0] - 1.2f, t[1] - 1.2f, t[2] + 1.2f, t[3] + 1.2f); c.drawOval(rf, p); }
        for (int i = 0; i < tiers.length; i++) {
            float[] t = tiers[i];
            p.setColor(i == 1 ? light(col, 0.06f) : col); rf.set(t[0], t[1], t[2], t[3]); c.drawOval(rf, p);
        }
        poly[0] = -hw * 0.2f; poly[1] = -hh * 0.95f; poly[2] = hw * 0.25f; poly[3] = -hh * 0.9f; poly[4] = hw * 0.05f; poly[5] = -hh * 1.45f;
        p.setColor(col); c.drawPoly(poly, 3, p);
        p.setColor(alpha(0xFFffffff, 0.22f)); c.drawCircle(-hw * 0.4f, -hh * 0.1f, hh * 0.22f, p);
        c.restore();
        if (q.stinkT > 0f && detail) {
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.4f);
            p.setColor(alpha(0xFF8fa23e, Math.min(0.6f, q.stinkT * 0.05f)));
            for (int i = -1; i <= 1; i++) {
                float bx = m.x + i * hw * 0.6f, t = time * 3f + i;
                float o = (float) Math.sin(t) * 3f;
                c.drawLine(bx, m.y - hh * 1.6f, bx + o, m.y - hh * 2.4f, p);
                c.drawLine(bx + o, m.y - hh * 2.4f, bx - o, m.y - hh * 3.2f, p);
            }
        }
    }

    /** Building blocks by material, fragments with cracks, and flesh/metal chunks. */
    private void drawBlock(Draw2D c, Prop q, int col) {
        RB m = q.main();
        Shape s = m.shapes.get(0);
        float hw = s.hl, hh = s.hh;
        if (q.gib) {
            local(c, m.x, m.y, m.a);
            p.setStyle(Paint.Style.FILL);
            p.setColor(dark(col, 0.5f));
            rf.set(-hw - 1.4f, -hh - 1.4f, hw + 1.4f, hh + 1.4f); c.drawRoundRect(rf, hh, hh, p);
            p.setColor(col);
            rf.set(-hw, -hh, hw, hh); c.drawRoundRect(rf, hh * 0.8f, hh * 0.8f, p);
            p.setColor(alpha(0xFFffffff, 0.22f));
            c.drawCircle(-hw * 0.35f, -hh * 0.35f, Math.min(hw, hh) * 0.35f, p);
            c.restore();
            return;
        }
        boxShape(c, s, col, true);
        local(c, m.x, m.y, m.a);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.BUTT);
        if (q.color == Build.BRICK) {
            p.setStrokeWidth(1.6f); p.setColor(alpha(0xFFe8d8c8, 0.45f));
            float rh = Math.min(10f, hh);
            int row = 0;
            for (float y = -hh + rh; y < hh - 0.5f; y += rh, row++) c.drawLine(-hw + 1, y, hw - 1, y, p);
            row = 0;
            for (float y = -hh; y < hh - 0.5f; y += rh, row++) for (float x = -hw + (row % 2 == 0 ? 10f : 20f); x < hw - 2f; x += 20f) c.drawLine(x, y + 1, x, Math.min(hh, y + rh) - 1, p);
        } else if (q.color == Build.STEEL) {
            p.setStyle(Paint.Style.FILL); p.setColor(dark(col, 0.4f));
            if (hw > 8f && hh > 8f) for (int sx = -1; sx <= 1; sx += 2) for (int sy = -1; sy <= 1; sy += 2) c.drawCircle(sx * (hw - 5f), sy * (hh - 5f), 1.8f, p);
            p.setColor(alpha(0xFFffffff, 0.22f)); rf.set(-hw + 1.5f, -hh + 1.5f, hw - 1.5f, -hh + 4f); c.drawRect(rf, p);
        } else {
            p.setStrokeWidth(1.3f); p.setColor(alpha(dark(col, 0.45f), 0.8f));
            if (hw >= hh) for (float y = -hh + 5f; y < hh - 2f; y += 6f) c.drawLine(-hw + 3f, y, hw - 3f, y + 1f, p);
            else for (float x = -hw + 5f; x < hw - 2f; x += 6f) c.drawLine(x, -hh + 3f, x + 1f, hh - 3f, p);
        }
        if (q.frag || q.wear > 0f) {
            // cracks grow with the damage taken
            float k = q.frag ? 0.5f : Math.min(1f, q.wear / World.toughness(q));
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.6f); p.setColor(alpha(0xFF14100c, 0.75f));
            if (k > 0.15f) { c.drawLine(-hw * 0.9f, -hh * 0.2f, -hw * 0.2f, hh * 0.15f, p); c.drawLine(-hw * 0.2f, hh * 0.15f, hw * 0.1f, -hh * 0.5f, p); }
            if (k > 0.45f) { c.drawLine(hw * 0.1f, -hh * 0.5f, hw * 0.8f, hh * 0.3f, p); c.drawLine(-hw * 0.2f, hh * 0.15f, -hw * 0.05f, hh * 0.9f, p); }
        }
        p.setStrokeCap(Paint.Cap.ROUND);
        c.restore();
    }

    /** Rounded rectangle with the dark outline used everywhere (current transform). */
    private void orect(Draw2D c, float x0, float y0, float x1, float y1, float rad, int col) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(dark(col, 0.6f)); rf.set(x0 - 1.3f, y0 - 1.3f, x1 + 1.3f, y1 + 1.3f); c.drawRoundRect(rf, rad + 1f, rad + 1f, p);
        p.setColor(col); rf.set(x0, y0, x1, y1); c.drawRoundRect(rf, rad, rad, p);
    }

    /**
     * Guns in their own frame (barrel along +x, grip below): 0 pistol, 1 rifle, 2 machine gun. hl is the half
     * length of the physical barrel. Shared by the loose guns and the ones soldiers carry.
     */
    void drawGun(Draw2D c, int k, float hl, boolean firing, int alive) {
        int metal = 0xFF4a4f57, dark = 0xFF2b2d33, light = 0xFF7a818c;
        p.setStyle(Paint.Style.FILL);
        if (k == 0) {
            orect(c, -hl * 0.95f, 1.5f, hl * 0.55f, 5f, 1f, dark);                    // frame
            poly[0] = -hl * 0.9f; poly[1] = 3f; poly[2] = -hl * 0.25f; poly[3] = 3f; poly[4] = -hl * 0.4f; poly[5] = 15f; poly[6] = -hl * 1.0f; poly[7] = 14f;
            p.setColor(0xFF15171b); c.drawPoly(poly, 4, p);
            poly[0] = -hl * 0.82f; poly[1] = 4f; poly[2] = -hl * 0.33f; poly[3] = 4f; poly[4] = -hl * 0.45f; poly[5] = 13.5f; poly[6] = -hl * 0.92f; poly[7] = 12.8f;
            p.setColor(0xFF3b2a1c); c.drawPoly(poly, 4, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.4f); p.setColor(0xFF15171b);
            rf.set(-hl * 0.3f, 3f, hl * 0.15f, 10f); c.drawArc(rf, 0f, 180f, false, p);
            orect(c, -hl, -5f, hl, 1.8f, 1.2f, metal);                                 // slide
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(0.9f); p.setColor(dark);
            for (int i = 0; i < 4; i++) c.drawLine(-hl * 0.85f + i * 2.2f, -4f, -hl * 0.85f + i * 2.2f, 0.8f, p);
            p.setStyle(Paint.Style.FILL); p.setColor(light); rf.set(-hl * 0.6f, -4.6f, hl * 0.9f, -3.4f); c.drawRect(rf, p);
            p.setColor(0xFF15171b); c.drawCircle(hl, -1.6f, 1.3f, p);
        } else if (k == 1) {
            poly[0] = -hl * 1.3f; poly[1] = -3.5f; poly[2] = -hl * 0.5f; poly[3] = -3.5f; poly[4] = -hl * 0.5f; poly[5] = 3.5f; poly[6] = -hl * 1.3f; poly[7] = 9f;
            p.setColor(0xFF15171b); c.drawPoly(poly, 4, p);
            poly[0] = -hl * 1.27f; poly[1] = -2.4f; poly[2] = -hl * 0.52f; poly[3] = -2.4f; poly[4] = -hl * 0.52f; poly[5] = 2.6f; poly[6] = -hl * 1.27f; poly[7] = 7.8f;
            p.setColor(0xFF5b3a1e); c.drawPoly(poly, 4, p);                           // wooden stock
            orect(c, hl * 0.25f, -2.2f, hl + 7f, 0.6f, 0.8f, dark);                    // barrel
            orect(c, -hl * 0.05f, -4f, hl * 0.6f, 3f, 1.5f, 0xFF5b3a1e);               // handguard
            orect(c, -hl * 0.6f, -5f, hl * 0.05f, 3.5f, 1.2f, metal);                  // receiver
            orect(c, -hl * 0.12f, 3f, hl * 0.12f, 13f, 2f, dark);                      // magazine
            orect(c, -hl * 0.5f, 3f, -hl * 0.32f, 12f, 1.5f, 0xFF15171b);              // grip
            orect(c, -hl * 0.45f, -10.5f, hl * 0.15f, -7f, 1.6f, 0xFF15171b);          // scope
            p.setColor(0xFF15171b); rf.set(-hl * 0.3f, -7.5f, -hl * 0.22f, -5f); c.drawRect(rf, p); rf.set(hl * 0.0f, -7.5f, hl * 0.08f, -5f); c.drawRect(rf, p);
            p.setColor(alive > 0 ? 0xFF6fd6ff : 0xFF3b4652); c.drawCircle(hl * 0.15f, -8.7f, 1.4f, p);
            p.setColor(light); rf.set(-hl * 0.55f, -4.4f, hl * 0.0f, -3.4f); c.drawRect(rf, p);
        } else {
            float spin = firing ? (float) Math.sin(time * 60f) * 1.2f : 0f;
            orect(c, -hl * 0.35f, 6f, hl * 0.12f, 15f, 1.2f, 0xFF4d5a2f);              // ammo box
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.5f); p.setColor(0xFFc9a23a);
            for (int i = 0; i < 4; i++) c.drawLine(-hl * 0.3f + i * 3f, 6f, -hl * 0.2f + i * 3f, 2f, p);
            for (int i = -1; i <= 1; i++) orect(c, hl * 0.15f, i * 3.2f - 1.2f + spin * (i == 0 ? 0f : 1f), hl + 5f, i * 3.2f + 1.2f + spin * (i == 0 ? 0f : 1f), 0.8f, i == 0 ? metal : dark);
            orect(c, hl * 0.85f, -5.5f, hl * 0.98f, 5.5f, 1f, 0xFF15171b);             // barrel collar
            orect(c, -hl * 0.8f, -7f, hl * 0.2f, 6f, 2.5f, metal);                     // body
            orect(c, -hl * 1.0f, -3f, -hl * 0.78f, 9f, 1.5f, 0xFF15171b);              // rear grip
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.2f); p.setColor(0xFF15171b);
            c.drawLine(-hl * 0.55f, -7f, -hl * 0.45f, -12f, p); c.drawLine(-hl * 0.45f, -12f, -hl * 0.05f, -12f, p); c.drawLine(-hl * 0.05f, -12f, hl * 0.02f, -7f, p);
            p.setStyle(Paint.Style.FILL); p.setColor(light); rf.set(-hl * 0.7f, -6f, hl * 0.1f, -4.6f); c.drawRect(rf, p);
        }
        if (firing) {
            float mx = (k == 0 ? hl + 1f : hl + 7f);
            c.emissive(true);
            p.setStyle(Paint.Style.FILL); p.setColor(0xFFffd27a);
            float fl = 6f + (float) Math.abs(Math.sin(time * 70f)) * 6f;
            poly[0] = mx; poly[1] = -3f; poly[2] = mx + fl; poly[3] = -1f; poly[4] = mx; poly[5] = 1f; c.drawPoly(poly, 3, p);
            c.emissive(false);
        }
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

    /** Colour of a hurt part: flesh reddens and darkens, metal blackens. */
    private int hurtColor(Person b, int idx, int col) {
        float dmg = 1f - b.hp[idx] / Person.maxHp(b, idx);
        if (dmg > 0.05f) {
            float k = Math.min(1f, dmg) * 0.42f;
            int to = b.kind == Person.ROBOT ? 0xFF22252a : (b.kind == Person.ZOMBIE ? 0xFF3e5a22 : (b.kind == Person.SKELETON ? 0xFF8a8268 : 0xFF6e1f22));
            col = mix(col, to, k);
        }
        return bodyColor(b, col);
    }

    /** A limb in the sandbox style: dark outline, flat fill, a lit edge on top and a shaded one below. */
    private void limb(Draw2D c, Shape s, int col) {
        float x0 = s.cx - s.ax * s.hl, y0 = s.cy - s.ay * s.hl, x1 = s.cx + s.ax * s.hl, y1 = s.cy + s.ay * s.hl;
        p.setStyle(Paint.Style.STROKE);
        p.setColor(dark(col, 0.62f));
        p.setStrokeWidth(s.r * 2f + 2.6f);
        c.drawLine(x0, y0, x1, y1, p);
        p.setColor(col);
        p.setStrokeWidth(s.r * 2f);
        c.drawLine(x0, y0, x1, y1, p);
        if (!detail) return;
        float nx = -s.ay, ny = s.ax;
        if (ny > 0f) { nx = -nx; ny = -ny; }          // n points up
        float o = s.r * 0.42f;
        p.setStrokeWidth(s.r * 0.5f);
        p.setColor(light(col, 0.16f));
        c.drawLine(x0 + nx * o, y0 + ny * o, x1 + nx * o, y1 + ny * o, p);
        p.setColor(dark(col, 0.16f));
        c.drawLine(x0 - nx * o, y0 - ny * o, x1 - nx * o, y1 - ny * o, p);
    }

    private void part(Draw2D c, Person b, int idx, int col) {
        if (b.gibbed[idx]) return;
        Shape s = b.rb[idx].shapes.get(0);
        if (s.type == Shape.CAPSULE) limb(c, s, hurtColor(b, idx, col));
        else if (s.type == Shape.CIRCLE) circleShape(c, s, hurtColor(b, idx, col));
        wounds(c, b, idx);
    }

    private void foot(Draw2D c, Person b, int idx) {
        if (b.gibbed[idx]) return;
        Shape s = b.rb[idx].shapes.get(0);
        limb(c, s, hurtColor(b, idx, b.kind == Person.SKELETON ? 0xFFe9e5d3 : (b.kind == Person.ROBOT ? 0xFF5d6670 : 0xFF2b2b30)));
        wounds(c, b, idx);
    }

    /** Bullet holes, cuts, burns and bruises in the part's own frame. */
    private void wounds(Draw2D c, Person b, int idx) {
        if (b.nw == 0) return;
        Shape s = b.rb[idx].shapes.get(0);
        boolean open = false;
        boolean metal = b.kind == Person.ROBOT, bone = b.kind == Person.SKELETON, z = b.kind == Person.ZOMBIE;
        int deep = metal ? 0xFF15171b : (bone ? 0xFF4a4536 : (z ? 0xFF2c4a14 : (gore ? 0xFF5c0a0e : 0xFF2e2e33)));
        int red = metal ? 0xFF3a3f47 : (bone ? 0xFF8d8774 : (z ? 0xFF4f8a2a : (gore ? 0xFFa3141c : 0xFF5d5d66)));
        for (int i = 0; i < b.nw; i++) {
            if (b.wpart[i] != idx || b.ws[i] <= 0f) continue;
            if (!open) { local(c, s.cx, s.cy, (float) Math.atan2(s.ay, s.ax)); open = true; }
            float u = b.wu[i], v = b.wv[i], r = b.ws[i];
            p.setStyle(Paint.Style.FILL);
            switch (b.wkind[i]) {
                case Person.W_HOLE:
                    p.setColor(red); c.drawCircle(u, v, r * 1.25f, p);
                    p.setColor(deep); c.drawCircle(u, v, r * 0.75f, p);
                    if (metal) { p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1f); p.setColor(0xFFb5bcc6); c.drawCircle(u, v, r * 1.3f, p); }
                    break;
                case Person.W_CUT: {
                    float dx = (float) Math.cos(b.wa[i]) * r, dy = (float) Math.sin(b.wa[i]) * r;
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(Math.max(2f, r * 0.42f)); p.setColor(red);
                    c.drawLine(u - dx, v - dy, u + dx, v + dy, p);
                    p.setStrokeWidth(Math.max(1f, r * 0.16f)); p.setColor(deep);
                    c.drawLine(u - dx * 0.8f, v - dy * 0.8f, u + dx * 0.8f, v + dy * 0.8f, p);
                    break;
                }
                case Person.W_BURN:
                    p.setColor(alpha(0xFF16120f, 0.55f)); c.drawCircle(u, v, r, p);
                    p.setColor(alpha(0xFF2a1e17, 0.5f)); c.drawCircle(u + r * 0.3f, v - r * 0.2f, r * 0.55f, p);
                    break;
                default:
                    p.setColor(alpha(metal ? 0xFF1b1d22 : 0xFF5a2a5e, 0.32f)); c.drawCircle(u, v, r, p);
            }
        }
        if (open) c.restore();
        p.setStyle(Paint.Style.FILL);
    }

    private void hand(Draw2D c, Person b, int idx) {
        if (b.gibbed[idx]) return;
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
        wounds(c, b, Person.HEAD);
        // front leg
        part(c, b, Person.THIGH_F, b.pants); part(c, b, Person.SHIN_F, b.pants); foot(c, b, Person.FOOT_F);
        gear(c, b);
        part(c, b, Person.UARM_F, b.shirt); part(c, b, Person.FARM_F, skin); hand(c, b, Person.FARM_F);
        if (b == inspect) inspectOverlay(c, b);
        // stumps (not on the side that was blown to pieces)
        p.setStyle(Paint.Style.FILL);
        for (int j = 0; j < Person.NJOINTS; j++) {
            if (!b.severed[j]) continue;
            Joint jn = b.jt[j];
            int stump = b.organic() || b.kind == Person.ZOMBIE ? (b.kind == Person.ZOMBIE ? 0xFF3b6e1a : 0xFF8e1111) : 0xFF6b6f78;
            float r = (j == Person.NECK ? 4.8f : 4.3f) * b.scale;
            for (int side = 0; side < 2; side++) {
                RB body = side == 0 ? jn.a : jn.b;
                if (body == null || gibbedBody(b, body)) continue;
                float x = side == 0 ? jn.anchorAX() : jn.anchorBX(), y = side == 0 ? jn.anchorAY() : jn.anchorBY();
                p.setColor(dark(stump, 0.35f)); c.drawCircle(x, y, r + 1.2f, p);
                p.setColor(stump); c.drawCircle(x, y, r, p);
                if (b.organic()) { p.setColor(0xFFe9e2cf); c.drawCircle(x, y, r * 0.35f, p); }
            }
        }
        if (b.rb[Person.PELVIS].frozen) pin(c, b.rb[Person.PELVIS].x, b.rb[Person.PELVIS].y);
        if (b.adrenaline > 0f) { p.setStyle(Paint.Style.FILL); c.emissive(true); p.setColor(alpha(0xFFffd23f, 0.25f)); c.drawCircle(b.cx(), b.cy() - 40f, b.radius() * 0.5f, p); c.emissive(false); }
    }

    private static boolean gibbedBody(Person b, RB body) {
        for (int i = 0; i < Person.NPARTS; i++) if (b.rb[i] == body) return b.gibbed[i];
        return false;
    }

    private void torsoDetail(Draw2D c, Person b) {
        if (b.gibbed[Person.CHEST]) return;
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
        if (b.gibbed[Person.PELVIS]) { p.setStrokeCap(Paint.Cap.ROUND); return; }
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
        if (b.gibbed[Person.HEAD]) return;
        RB h = b.rb[Person.HEAD];
        float r = h.shapes.get(0).r;
        int col = hurtColor(b, Person.HEAD, b.skin);
        local(c, h.x, h.y, h.a);
        int d = b.dir;
        p.setStyle(Paint.Style.FILL);
        boolean boxy = b.kind != Person.SKELETON && b.kind != Person.ROBOT;
        p.setColor(dark(col, 0.62f));
        if (boxy) { rf.set(-r - 1.4f, -r - 1.4f, r + 1.4f, r + 1.4f); c.drawRoundRect(rf, r * 0.5f, r * 0.5f, p); }
        else c.drawCircle(0, 0, r + 1.6f, p);
        p.setColor(col);
        if (boxy) {
            rf.set(-r, -r, r, r); c.drawRoundRect(rf, r * 0.42f, r * 0.42f, p);
            if (detail) { p.setColor(dark(col, 0.14f)); rf.set(-r, r * 0.35f, r, r); c.drawRoundRect(rf, r * 0.42f, r * 0.42f, p); }
        } else c.drawCircle(0, 0, r, p);
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
                // short boxy hair: a cap on top and a block at the back
                p.setColor(bodyColor(b, b.hair));
                rf.set(-r * 1.04f, -r * 1.06f, r * 1.04f, -r * 0.38f); c.drawRoundRect(rf, r * 0.45f, r * 0.3f, p);
                if (d > 0) rf.set(-r * 1.04f, -r * 0.7f, -r * 0.35f, r * 0.2f); else rf.set(r * 0.35f, -r * 0.7f, r * 1.04f, r * 0.2f);
                c.drawRoundRect(rf, r * 0.3f, r * 0.3f, p);
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
        if (b.severed[Person.SH_F] || b.severed[Person.EL_F] || b.gibbed[Person.FARM_F] || b.gibbed[Person.CHEST]) return;
        float s = b.scale;
        RB f = b.rb[Person.FARM_F];
        float hl = f.shapes.get(0).hl;
        float hx = f.localToWorldX(0, hl), hy = f.localToWorldY(0, hl);
        float ux = -(float) Math.sin(f.a), uy = (float) Math.cos(f.a);        // elbow -> wrist
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        if (b.kind == Person.SOLDIER) {
            // the same rifle model as the loose one, gripped at the hand
            c.save();
            c.translate(hx, hy);
            c.rotate((float) Math.toDegrees(Math.atan2(uy, ux)));
            if (ux < 0) c.scale(1f, -1f);
            c.scale(s * 0.85f, s * 0.85f);
            c.translate(11f, -8f);
            drawGun(c, 1, 25f, false, b.dead ? 0 : 1);
            c.restore();
            p.setStyle(Paint.Style.STROKE);
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
            if (i == Person.HEAD || b.gibbed[i]) continue;
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

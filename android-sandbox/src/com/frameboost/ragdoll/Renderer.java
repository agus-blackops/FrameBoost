package com.frameboost.ragdoll;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

/** Draws the world (everything in world coordinates). */
final class Renderer {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();

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

    void draw(Canvas c, World w, Fx fx, float camX, float camY, float zoom, int sw, int sh) {
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

        for (int i = 0; i < w.bodies.size(); i++) {
            Body b = w.bodies.get(i);
            if (b.isHuman()) continue;
            drawProp(c, b);
        }
        for (int i = 0; i < w.bodies.size(); i++) {
            Body b = w.bodies.get(i);
            if (b.isHuman()) drawHuman(c, b);
        }
        drawPins(c, w);
        drawFx(c, w, fx);
        c.restore();
    }

    // ------------------------------------------------------------------------------ background ---

    private void drawBackground(Canvas c, World w, float vx0, float vy0, float vx1, float vy1) {
        p.setStyle(Paint.Style.FILL);
        // outside the room
        p.setColor(0xFF161a22);
        rf.set(vx0 - 50, vy0 - 50, vx1 + 50, vy1 + 50);
        c.drawRect(rf, p);
        // room wall
        float x0 = Math.max(w.wallL, vx0 - 1), x1 = Math.min(w.wallR, vx1 + 1);
        float top = Math.max(w.ceilY, vy0 - 1);
        p.setColor(0xFF2f3846);
        c.drawRect(x0, top, x1, w.floorY, p);
        // grid
        p.setColor(0xFF3a4455);
        p.setStrokeWidth(2f);
        float step = 100f;
        float gx = (float) Math.ceil(x0 / step) * step;
        for (; gx <= x1; gx += step) c.drawLine(gx, top, gx, w.floorY, p);
        float gy = (float) Math.floor(w.floorY / step) * step;
        for (; gy >= top; gy -= step) c.drawLine(x0, gy, x1, gy, p);
        // darker bands on walls give some depth
        p.setColor(0x33000000);
        if (vx0 < w.wallL + 60) c.drawRect(w.wallL, top, w.wallL + 60, w.floorY, p);
        if (vx1 > w.wallR - 60) c.drawRect(w.wallR - 60, top, w.wallR, w.floorY, p);
        // floor
        if (vy1 > w.floorY) {
            p.setColor(0xFF4c525d);
            c.drawRect(x0, w.floorY, x1, vy1 + 100, p);
            p.setColor(0xFF666d7a);
            c.drawRect(x0, w.floorY, x1, w.floorY + 9, p);
            p.setColor(0xFF3b404a);
            p.setStrokeWidth(3f);
            float fy = w.floorY + 60;
            for (; fy < vy1 + 100; fy += 60) c.drawLine(x0, fy, x1, fy, p);
        }
        // wall edges
        p.setColor(0xFF0e1117);
        p.setStrokeWidth(8f);
        if (vx0 < w.wallL) c.drawLine(w.wallL, top, w.wallL, vy1 + 100, p);
        if (vx1 > w.wallR) c.drawLine(w.wallR, top, w.wallR, vy1 + 100, p);
    }

    private void drawStains(Canvas c, World w, Fx fx) {
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
        if (b.fuse >= 0f && b.fuse < 99f) {
            float rate = b.fuse < 1f ? 14f : 6f;
            if (((int) (b.fuse * rate) & 1) == 0) col = mix(col, 0xFFffffff, 0.55f);
        }
        return col;
    }

    private void drawProp(Canvas c, Body b) {
        if (b.type == Body.BALL || b.type == Body.BALLOON) { drawBall(c, b); return; }
        // outline pass, then fill pass (so touching sticks merge into one silhouette)
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        int n = b.ss.size();
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < n; i++) {
                Stick s = b.ss.get(i);
                if (s.dead || s.kind != Stick.FILL || s.joint) continue;
                int col = bodyColor(b, s.color != 0 ? s.color : b.color);
                if (pass == 0) { p.setColor(dark(col, 0.55f)); p.setStrokeWidth(s.r * 2 + 3.4f); }
                else { p.setColor(col); p.setStrokeWidth(s.r * 2); }
                c.drawLine(s.a.x, s.a.y, s.b.x, s.b.y, p);
            }
        }
        // details
        int detail = dark(bodyColor(b, b.color), 0.4f);
        p.setColor(detail);
        p.setStrokeWidth(2.2f);
        for (int i = 0; i < n; i++) {
            Stick s = b.ss.get(i);
            if (s.dead || !s.band) continue;
            c.drawLine(s.a.x, s.a.y, s.b.x, s.b.y, p);
        }
        overlay(c, b);
    }

    private void overlay(Canvas c, Body b) {
        if (b.ps.isEmpty()) return;
        switch (b.type) {
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

    private void label(Canvas c, Body b, String text, int col, float size) {
        Particle a = b.ps.get(0), z = b.ps.get(Math.min(2, b.ps.size() - 1));
        float ang = (float) Math.toDegrees(Math.atan2(z.y - a.y, z.x - a.x)) - 90f;
        float mx = 0, my = 0;
        for (int i = 0; i < b.ps.size(); i++) { mx += b.ps.get(i).x; my += b.ps.get(i).y; }
        mx /= b.ps.size(); my /= b.ps.size();
        c.save();
        c.translate(mx, my);
        c.rotate(ang);
        p.setStyle(Paint.Style.FILL);
        p.setColor(bodyColor(b, col));
        p.setTextSize(size);
        p.setTextAlign(Paint.Align.CENTER);
        p.setFakeBoldText(true);
        c.drawText(text, 0, size * 0.36f, p);
        p.setFakeBoldText(false);
        c.restore();
    }

    private void drawBall(Canvas c, Body b) {
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
        p.setColor(0x55ffffff);
        c.drawCircle(ctr.x - rad * 0.35f, ctr.y - rad * 0.38f, rad * 0.22f, p);
    }

    // ---------------------------------------------------------------------------------- humans ---

    private void seg(Canvas c, Body b, Particle a, Particle z, float r, int col) {
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

    private void part(Canvas c, Body b, Stick s, int col) {
        seg(c, b, s.a, s.b, s.r, col);
    }

    private void foot(Canvas c, Body b, Stick shin) {
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

    private void hand(Canvas c, Body b, Stick fore) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(bodyColor(b, b.skin));
        c.drawCircle(fore.b.x, fore.b.y, fore.r * 1.15f, p);
    }

    private void drawHuman(Canvas c, Body b) {
        int skin = b.skin;
        if (b.dead) skin = mix(skin, 0xFF9aa0a8, 0.25f);
        // back limbs
        part(c, b, b.thighB, b.pants); part(c, b, b.shinB, b.pants); foot(c, b, b.shinB);
        part(c, b, b.uarmB, b.shirt); part(c, b, b.farmB, skin); hand(c, b, b.farmB);
        // torso
        seg(c, b, b.pelvis, b.chest, b.torso.r, b.shirt);
        if (!b.neck.dead) seg(c, b, b.head, b.chest, b.neck.r, skin);
        // front legs
        part(c, b, b.thighF, b.pants); part(c, b, b.shinF, b.pants); foot(c, b, b.shinF);
        // head
        drawHead(c, b, skin);
        // front arm
        part(c, b, b.uarmF, b.shirt); part(c, b, b.farmF, skin); hand(c, b, b.farmF);
        // stumps
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF8e1111);
        Stick[] js = {b.jShF, b.jElF, b.jShB, b.jElB, b.jHipF, b.jKneeF, b.jHipB, b.jKneeB, b.neck};
        for (int i = 0; i < js.length; i++) {
            Stick s = js[i];
            if (s == null || !s.dead) continue;
            float r = (s == b.neck ? 4.8f : 4.3f) * b.scale;
            c.drawCircle(s.a.x, s.a.y, r, p);
            c.drawCircle(s.b.x, s.b.y, r, p);
        }
    }

    private void drawHead(Canvas c, Body b, int skin) {
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
        // hair: cap over the top and the back of the head
        int hair = bodyColor(b, b.hair);
        p.setColor(hair);
        rf.set(-r, -r, r, r);
        c.drawArc(rf, 180f, 180f, true, p);
        c.drawCircle(-b.dir * r * 0.42f, -r * 0.1f, r * 0.62f, p);
        p.setColor(col);
        c.drawCircle(b.dir * r * 0.28f, r * 0.02f, r * 0.62f, p);   // forehead/face cleans the hair front
        // eye
        float ex = b.dir * r * 0.5f, ey = -r * 0.12f;
        p.setColor(0xFF1b1b1b);
        if (b.dead) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.8f);
            float k = r * 0.2f;
            c.drawLine(ex - k, ey - k, ex + k, ey + k, p);
            c.drawLine(ex - k, ey + k, ex + k, ey - k, p);
        } else {
            c.drawCircle(ex, ey, r * 0.15f, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.6f);
            c.drawLine(b.dir * r * 0.3f, r * 0.42f, b.dir * r * 0.72f, r * 0.42f, p);
        }
        c.restore();
    }

    private void drawPins(Canvas c, World w) {
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

    private void drawFx(Canvas c, World w, Fx fx) {
        p.setStyle(Paint.Style.FILL);
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < Fx.MAXP; i++) {
                if (fx.life[i] <= 0) continue;
                int t = fx.type[i];
                boolean front = t == Fx.FLAME || t == Fx.SPARK || t == Fx.GLOW;
                if (front != (pass == 1)) continue;
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
                        p.setColor(alpha(fx.color[i], k * 0.85f));
                        c.drawCircle(fx.x[i], fx.y[i], fx.size[i] * (1.7f - k * 0.7f), p);
                        break;
                    default: break;
                }
            }
        }
        // shock rings
        p.setStyle(Paint.Style.STROKE);
        for (int i = 0; i < Fx.MAXR; i++) {
            if (fx.rt[i] <= 0) continue;
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
        }
        p.setStyle(Paint.Style.FILL);
    }
}

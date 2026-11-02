package com.frameboost.ragdoll;

import android.graphics.Paint;
import android.graphics.RectF;

import java.util.Random;

/** Particles, floor stains, shock rings, lightning and screen flash / shake. */
final class Fx {
    static final int BLOOD = 0, FLAME = 1, SMOKE = 2, SPARK = 3, DEBRIS = 4, GLOW = 5, WATER = 6;
    static final int MAXP = 1600, MAXS = 500, MAXR = 10;

    final Random rnd = new Random();
    final float[] x = new float[MAXP], y = new float[MAXP], vx = new float[MAXP], vy = new float[MAXP];
    final float[] life = new float[MAXP], maxLife = new float[MAXP], size = new float[MAXP], rot = new float[MAXP];
    final int[] type = new int[MAXP], color = new int[MAXP];
    int next;

    final float[] sx = new float[MAXS], sw = new float[MAXS], sa = new float[MAXS];
    final int[] sc = new int[MAXS];
    int sn, sNext;

    // blood V3: splats on the back wall with drips that run down, aging from bright red to dark brown
    static final int MAXD = 160;
    final float[] dx = new float[MAXD], dy = new float[MAXD], dr = new float[MAXD], dage = new float[MAXD];
    final int[] dc = new int[MAXD], dseed = new int[MAXD];
    int dn, dNext;

    final float[] rx = new float[MAXR], ry = new float[MAXR], rr = new float[MAXR], rmax = new float[MAXR], rt = new float[MAXR];
    int rNext;

    final float[] bx = new float[24], by = new float[24];
    int bn;
    float bt;

    float flash, shake;
    float floorY, wallL, wallR;
    int bloodA = 0xFFa80f18, bloodB = 0xFF5e0a0f;
    boolean gore = true;

    /** Live blood particles (used by tests). */
    int activeBlood() { int n = 0; for (int i = 0; i < MAXP; i++) if (life[i] > 0f && type[i] == BLOOD) n++; return n; }

    float rf() { return rnd.nextFloat(); }
    float rr(float m) { return (rnd.nextFloat() - 0.5f) * 2f * m; }

    void clear() {
        for (int i = 0; i < MAXP; i++) life[i] = 0f;
        sn = 0; sNext = 0; bt = 0f; flash = 0f; shake = 0f;
        dn = 0; dNext = 0;
        for (int i = 0; i < MAXR; i++) rt[i] = 0f;
    }

    void add(int t, float px, float py, float pvx, float pvy, float lf, float sz, int col) {
        int i = next;
        next = (next + 1) % MAXP;
        type[i] = t; x[i] = px; y[i] = py; vx[i] = pvx; vy[i] = pvy;
        life[i] = lf; maxLife[i] = lf; size[i] = sz; color[i] = col; rot[i] = rf() * 6.28f;
    }

    void blood(float px, float py, float dx, float dy, int n) {
        if (!gore) n = Math.min(n, 2);
        for (int i = 0; i < n; i++) {
            float sp = 120f + rf() * 420f;
            add(BLOOD, px, py, dx * sp * 0.55f + rr(160), dy * sp * 0.55f - rf() * 200f, 1.6f, 2f + rf() * 3f, rnd.nextBoolean() ? bloodA : bloodB);
        }
    }

    static final int ARTERIAL = 0xFFd4141f;

    /** Blood straight from an artery: brighter and faster than the dark blood that oozes from veins. */
    void arterial(float px, float py, float dx, float dy, int n) {
        if (!gore) n = Math.min(n, 2);
        for (int i = 0; i < n; i++) {
            float sp = 220f + rf() * 520f;
            add(BLOOD, px, py, dx * sp * 0.6f + rr(110), dy * sp * 0.6f - rf() * 160f, 1.7f, 1.8f + rf() * 2.6f, rf() < 0.7f ? ARTERIAL : bloodA);
        }
    }

    void flame(float px, float py, float sz) {
        add(FLAME, px + rr(sz * 0.5f), py + rr(sz * 0.4f), rr(30), -50f - rf() * 90f, 0.35f + rf() * 0.35f, sz, 0xFFff9a1f);
    }

    void smoke(float px, float py, float sz, int shade) {
        add(SMOKE, px, py, rr(40), -20f - rf() * 50f, 0.9f + rf() * 0.9f, sz, shade);
    }

    /** A heavy cloud of dust that rolls out and hangs in the air: n puffs spread over a radius r. */
    void dust(float px, float py, float r, int n) {
        for (int i = 0; i < n; i++) {
            float a = rf() * 6.283f, d = rf() * r;
            add(SMOKE, px + (float) Math.cos(a) * d, py + (float) Math.sin(a) * d * 0.6f, (float) Math.cos(a) * (30f + rf() * 90f), -10f - rf() * 40f, 2.2f + rf() * 2.4f, r * 0.35f + 10f + rf() * 14f, rf() < 0.5f ? 0xFFb8ab9c : 0xFFa39a8e);
        }
    }

    void sparks(float px, float py, int n, int col) {
        for (int i = 0; i < n; i++) {
            float a = rf() * 6.28f, sp = 150f + rf() * 380f;
            add(SPARK, px, py, (float) Math.cos(a) * sp, (float) Math.sin(a) * sp - 80f, 0.25f + rf() * 0.3f, 1.8f + rf() * 1.4f, col);
        }
    }

    void debris(float px, float py, int n, int col, float power) {
        for (int i = 0; i < n; i++) {
            float a = rf() * 6.28f, sp = power * (0.4f + rf() * 0.8f);
            add(DEBRIS, px, py, (float) Math.cos(a) * sp, (float) Math.sin(a) * sp - 120f, 0.8f + rf() * 0.7f, 3f + rf() * 4f, col);
        }
    }

    void water(float px, float py, float pvx, float pvy) {
        add(WATER, px, py, pvx, pvy, 0.7f, 2.4f, 0xFF8fd3ff);
    }

    void ring(float px, float py, float rmaxv) {
        int i = rNext;
        rNext = (rNext + 1) % MAXR;
        rx[i] = px; ry[i] = py; rmax[i] = rmaxv; rr[i] = 0f; rt[i] = 1f;
    }

    /** A drop lands on the floor: it joins a pool it falls into (pools spread) or starts a new stain. */
    void stain(float px, float w, int col) {
        if (!gore) w *= 0.3f;                       // gore off: only small spots
        for (int k = 0; k < sn; k++) {
            if (Math.abs(sx[k] - px) < sw[k] * 0.45f && sw[k] < 150f && (sc[k] & 0xFFFFFF) == (col & 0xFFFFFF)) {
                sw[k] += w * 0.22f;
                sa[k] = Math.min(sa[k], 2f);              // fresh blood on top
                return;
            }
        }
        int i = sNext;
        sNext = (sNext + 1) % MAXS;
        sx[i] = px; sw[i] = w; sc[i] = col; sa[i] = 0f;
        if (sn < MAXS) sn++;
    }

    /** Width of a fresh pool of blood under x (0 = none): feet that step in it get soaked. */
    float poolAt(float px) {
        float best = 0f;
        for (int k = 0; k < sn; k++) if (Math.abs(sx[k] - px) < sw[k] * 0.5f && sa[k] < 45f && sw[k] > best) best = sw[k];
        return best;
    }

    /** A splat on the back wall (gore that sprays behind, traps, blasts). */
    void wallSplat(float px, float py, float r, int col) {
        if (!gore) return;
        if (py > floorY - 4f) py = floorY - 4f;
        int i = dNext;
        dNext = (dNext + 1) % MAXD;
        dx[i] = px; dy[i] = py; dr[i] = r; dc[i] = col; dage[i] = 0f; dseed[i] = rnd.nextInt(1 << 20);
        if (dn < MAXD) dn++;
    }

    void explosion(float px, float py, float R) {
        flash = Math.min(1f, flash + R / 520f);
        shake = Math.min(1f, shake + R / 700f);
        ring(px, py, R * 1.1f);
        add(GLOW, px, py, 0, 0, 0.5f, R * 0.9f, 0xFFffd07a);
        int n = (int) (R / 7f);
        for (int i = 0; i < n; i++) {
            float a = rf() * 6.28f, sp = R * (0.5f + rf() * 1.8f);
            add(FLAME, px + rr(R * 0.15f), py + rr(R * 0.15f), (float) Math.cos(a) * sp * 0.5f, (float) Math.sin(a) * sp * 0.5f - 40f, 0.3f + rf() * 0.4f, R * 0.07f + rf() * R * 0.06f, 0xFFff8a1f);
        }
        for (int i = 0; i < n / 2; i++) smoke(px + rr(R * 0.3f), py + rr(R * 0.2f), R * 0.12f, 0xFF3a3a3a);
        sparks(px, py, 18, 0xFFffd25a);
        debris(px, py, 10, 0xFF3a3a3a, R * 1.6f);
    }

    void lightning(float x0, float y0, float x1, float y1) {
        bn = 0;
        int seg = 12;
        for (int i = 0; i <= seg; i++) {
            float t = i / (float) seg;
            float jx = i == 0 || i == seg ? 0f : rr(26f), jy = i == 0 || i == seg ? 0f : rr(26f);
            bx[bn] = x0 + (x1 - x0) * t + jx; by[bn] = y0 + (y1 - y0) * t + jy; bn++;
        }
        bt = 0.22f;
        flash = Math.min(1f, flash + 0.5f);
        sparks(x1, y1, 14, 0xFF9cc8ff);
    }

    void update(float dt) {
        for (int i = 0; i < sn; i++) sa[i] += dt;
        for (int i = 0; i < dn; i++) dage[i] += dt;
        if (flash > 0) flash = Math.max(0, flash - dt * 2.6f);
        if (shake > 0) shake = Math.max(0, shake - dt * 2.4f);
        if (bt > 0) bt -= dt;
        for (int i = 0; i < MAXR; i++) {
            if (rt[i] <= 0) continue;
            rt[i] -= dt * 2.4f;
            rr[i] = rmax[i] * (1f - rt[i] * rt[i]);
        }
        for (int i = 0; i < MAXP; i++) {
            if (life[i] <= 0) continue;
            life[i] -= dt;
            switch (type[i]) {
                case BLOOD: case WATER:
                    vy[i] += 1700f * dt;
                    x[i] += vx[i] * dt; y[i] += vy[i] * dt;
                    if (y[i] >= floorY) {
                        if (type[i] == BLOOD) {
                            stain(x[i], 2f + size[i] * 1.7f, Renderer.alpha(color[i], 0.85f));
                            // a fast drop breaks into small satellite droplets that jump back up
                            if (gore && vy[i] > 520f && size[i] > 2.5f) {
                                int ns = 2 + (int) Math.min(2f, vy[i] / 700f);
                                for (int k = 0; k < ns; k++) add(BLOOD, x[i], floorY - 1f, rr(150f) + vx[i] * 0.12f, -vy[i] * (0.12f + rf() * 0.12f), 0.45f, 1.1f + rf() * 0.9f, color[i]);
                            }
                        }
                        life[i] = 0;
                    } else if (x[i] < wallL || x[i] > wallR) {
                        if (type[i] == BLOOD && rnd.nextFloat() < 0.5f) wallSplat(x[i] < wallL ? wallL + 6f : wallR - 6f, y[i], 3f + size[i], color[i]);
                        life[i] = 0;
                    }
                    break;
                case FLAME:
                    vy[i] -= 120f * dt;
                    vx[i] *= (1f - 2.2f * dt); vy[i] *= (1f - 1.2f * dt);
                    x[i] += vx[i] * dt; y[i] += vy[i] * dt;
                    break;
                case SMOKE:
                    vx[i] *= (1f - 1.5f * dt); vy[i] *= (1f - 0.8f * dt);
                    vy[i] -= 30f * dt;
                    x[i] += vx[i] * dt; y[i] += vy[i] * dt;
                    size[i] += dt * 14f;
                    break;
                case GLOW:
                    break;
                default: // SPARK, DEBRIS
                    vy[i] += 1500f * dt;
                    x[i] += vx[i] * dt; y[i] += vy[i] * dt;
                    rot[i] += dt * 9f;
                    if (y[i] >= floorY) { y[i] = floorY; vy[i] *= -0.35f; vx[i] *= 0.6f; }
                    break;
            }
        }
    }

    // ------------------------------------------------------------------------------- drawing ---

    /** Blood darkens and dries over half a minute. */
    static int aged(int col, float age) {
        float k = Math.min(1f, age / 30f) * 0.55f;
        int a = col >>> 24;
        return (Renderer.mix(col, 0xFF2a0d0a, k) & 0xFFFFFF) | (a << 24);
    }

    private final RectF sr = new RectF();

    void drawStains(Draw2D c, Paint p) {
        p.setStyle(Paint.Style.FILL);
        // splats on the wall: a blot, satellite drops, and drips that run down for a few seconds
        for (int i = 0; i < dn; i++) {
            int col = aged(dc[i], dage[i]);
            float r = dr[i], cx = dx[i], cy = dy[i];
            int seed = dseed[i];
            p.setColor(Renderer.alpha(col, 0.8f));
            c.drawCircle(cx, cy, r * 0.55f, p);
            for (int k = 0; k < 7; k++) {
                int h = seed * (k + 3) * 2654435 + k * 40503;
                float an = ((h >>> 3) & 1023) / 1023f * 6.283f, dd = r * (0.35f + ((h >>> 13) & 255) / 255f * 0.9f);
                float rr = r * (0.12f + ((h >>> 21) & 63) / 63f * 0.22f);
                c.drawCircle(cx + (float) Math.cos(an) * dd, cy + (float) Math.sin(an) * dd * 0.8f, rr, p);
            }
            // blood V6: a splat is not round - thin streaks fly out of it, with tiny droplets at their tips
            p.setStyle(Paint.Style.STROKE);
            p.setColor(Renderer.alpha(col, 0.7f));
            for (int k = 0; k < 6; k++) {
                int h = seed * (k + 21) * 40503 + k * 977;
                float an = ((h >>> 5) & 1023) / 1023f * 6.283f, l0 = r * 0.5f, l1 = r * (1.0f + ((h >>> 15) & 255) / 255f * 1.3f);
                p.setStrokeWidth(Math.max(0.8f, r * 0.07f));
                c.drawLine(cx + (float) Math.cos(an) * l0, cy + (float) Math.sin(an) * l0, cx + (float) Math.cos(an) * l1, cy + (float) Math.sin(an) * l1, p);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(cx + (float) Math.cos(an) * (l1 + r * 0.18f), cy + (float) Math.sin(an) * (l1 + r * 0.18f), Math.max(0.8f, r * 0.07f), p);
                p.setStyle(Paint.Style.STROKE);
            }
            p.setStyle(Paint.Style.FILL);
            float run = Math.min(1f, dage[i] / 5f);
            p.setStyle(Paint.Style.STROKE);
            for (int k = 0; k < 3; k++) {
                int h = seed * (k + 11) * 69069 + 12345;
                float ox = (((h >>> 5) & 255) / 255f - 0.5f) * r * 1.1f;
                float len = r * (0.8f + ((h >>> 15) & 255) / 255f * 2.6f) * run;
                if (len < 1f) continue;
                float wd = Math.max(1.2f, r * 0.13f);
                p.setStrokeWidth(wd);
                c.drawLine(cx + ox, cy, cx + ox, Math.min(floorY - 2f, cy + len), p);
                c.drawCircle(cx + ox, Math.min(floorY - 2f, cy + len), wd * 0.7f, p);
            }
            p.setStyle(Paint.Style.FILL);
        }
        // pools on the floor: a flat ellipse with a glossy streak, darker as they dry
        for (int i = 0; i < sn; i++) {
            int col = aged(sc[i], sa[i]);
            float w = sw[i];
            if (w > 80f && (sc[i] & 0xFFFFFF) < 0x201810) {          // scorch mark from a blast: a wide dark smear
                p.setColor(Renderer.alpha(0xFF0a0806, 0.55f));
                sr.set(sx[i] - w * 0.5f, floorY - 3f, sx[i] + w * 0.5f, floorY + 9f);
                c.drawOval(sr, p);
                p.setColor(Renderer.alpha(0xFF0a0806, 0.35f));
                sr.set(sx[i] - w * 0.3f, floorY - 5f, sx[i] + w * 0.3f, floorY + 6f);
                c.drawOval(sr, p);
                continue;
            }
            p.setColor(Renderer.alpha(col, 0.9f));
            if (w > 14f) {
                // a pool seen from the side: a flat ellipse that gets thicker as it grows
                float hh = Math.min(7f, 1.5f + w * 0.035f);
                sr.set(sx[i] - w * 0.5f, floorY + 1.4f - hh * 0.5f, sx[i] + w * 0.5f, floorY + 1.4f + hh * 0.5f);
                c.drawOval(sr, p);
                // the rim dries first (darker line), the middle clots into a dark jelly, an old pool cracks
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1f);
                p.setColor(Renderer.alpha(Renderer.dark(col, 0.5f), 0.55f));
                c.drawOval(sr, p);
                if (sa[i] > 18f && w > 24f) {
                    float clot = Math.min(1f, (sa[i] - 18f) / 40f);
                    p.setStyle(Paint.Style.FILL); p.setColor(Renderer.alpha(0xFF2a0407, 0.5f * clot));
                    sr.set(sx[i] - w * 0.32f, floorY + 1.4f - hh * 0.3f, sx[i] + w * 0.32f, floorY + 1.4f + hh * 0.3f);
                    c.drawOval(sr, p);
                }
                if (sa[i] > 70f && w > 30f) {
                    p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(0.8f); p.setColor(Renderer.alpha(0xFF6a4a3a, 0.5f));
                    for (int ck = -1; ck <= 1; ck++) c.drawLine(sx[i] + ck * w * 0.22f, floorY + 0.4f, sx[i] + ck * w * 0.22f + w * 0.05f, floorY + 2.6f, p);
                }
                p.setStyle(Paint.Style.FILL);
            } else c.drawRect(sx[i] - w * 0.5f, floorY - 1f, sx[i] + w * 0.5f, floorY + 2.2f, p);
            if (w > 14f) {
                p.setColor(Renderer.alpha(0xFFffffff, 0.16f * Math.max(0f, 1f - sa[i] / 25f)));
                c.drawRect(sx[i] - w * 0.3f, floorY - 0.6f, sx[i] + w * 0.15f, floorY + 0.2f, p);
            }
        }
    }

    void draw(Draw2D c, Paint p) {
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < MAXP; i++) {
            if (life[i] <= 0) continue;
            float k = life[i] / maxLife[i];
            switch (type[i]) {
                case BLOOD: {
                    // a drop with a short streak behind it, stretched by its speed
                    p.setColor(color[i]);
                    float sp = (float) Math.sqrt(vx[i] * vx[i] + vy[i] * vy[i]);
                    if (sp > 160f) {
                        float k2 = Math.min(0.03f, 6f / sp);
                        p.setStyle(Paint.Style.STROKE);
                        p.setStrokeWidth(size[i] * 0.75f);
                        c.drawLine(x[i] - vx[i] * k2, y[i] - vy[i] * k2, x[i], y[i], p);
                        p.setStyle(Paint.Style.FILL);
                    }
                    c.drawCircle(x[i], y[i], size[i] * 0.55f, p);
                    if (size[i] > 3f && k > 0.45f) { p.setColor(Renderer.alpha(0xFFffffff, 0.4f)); c.drawCircle(x[i] - size[i] * 0.18f, y[i] - size[i] * 0.2f, size[i] * 0.14f, p); }
                    break;
                }
                case WATER: p.setColor(Renderer.alpha(color[i], 0.8f)); c.drawCircle(x[i], y[i], size[i], p); break;
                case FLAME: {
                    c.emissive(true);
                    p.setColor(Renderer.mix(0xFFff3a10, 0xFFffd27a, k));
                    c.drawCircle(x[i], y[i], size[i] * (0.4f + 0.6f * k), p);
                    c.emissive(false);
                    if (!c.hasLighting()) { p.setColor(Renderer.alpha(0xFFffd27a, 0.5f * k)); c.drawCircle(x[i], y[i], size[i] * 0.6f * k, p); }
                    break;
                }
                case SMOKE: p.setColor(Renderer.alpha(color[i], 0.4f * k)); c.drawCircle(x[i], y[i], size[i], p); break;
                case SPARK: c.emissive(true); p.setColor(color[i]); c.drawCircle(x[i], y[i], size[i] * (0.4f + k), p); c.emissive(false); break;
                case DEBRIS: {
                    p.setColor(color[i]);
                    float s = size[i];
                    c.save(); c.translate(x[i], y[i]); c.rotate(rot[i] * 57.3f);
                    c.drawRect(-s, -s * 0.6f, s, s * 0.6f, p);
                    c.restore();
                    break;
                }
                default: {
                    c.emissive(true);
                    p.setColor(Renderer.alpha(color[i], 0.55f * k));
                    c.drawCircle(x[i], y[i], size[i] * (1.15f - k * 0.5f), p);
                    c.emissive(false);
                    c.light(x[i], y[i], size[i] * 2.6f, color[i], 1.2f * k);
                }
            }
        }
        p.setStyle(Paint.Style.STROKE);
        for (int i = 0; i < MAXR; i++) {
            if (rt[i] <= 0) continue;
            p.setColor(Renderer.alpha(0xFFffffff, rt[i] * 0.7f));
            p.setStrokeWidth(3f + 10f * rt[i]);
            c.drawCircle(rx[i], ry[i], rr[i], p);
            c.ripple(rx[i], ry[i], rr[i], rt[i]);
        }
        if (bt > 0 && bn > 1) {
            float k = Math.min(1f, bt / 0.22f);
            c.emissive(true);
            for (int pass = 0; pass < 2; pass++) {
                p.setColor(pass == 0 ? Renderer.alpha(0xFF7fb8ff, k * 0.45f) : Renderer.alpha(0xFFffffff, k));
                p.setStrokeWidth(pass == 0 ? 16f : 5f);
                for (int i = 0; i + 1 < bn; i++) c.drawLine(bx[i], by[i], bx[i + 1], by[i + 1], p);
            }
            c.emissive(false);
            for (int i = 0; i < bn; i += 3) c.light(bx[i], by[i], 420f, 0xFF9cc8ff, k * 1.2f);
        }
        p.setStyle(Paint.Style.FILL);
    }
}

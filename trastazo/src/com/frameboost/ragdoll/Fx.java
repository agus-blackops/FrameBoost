package com.frameboost.ragdoll;

import android.graphics.Paint;

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

    final float[] sx = new float[MAXS], sw = new float[MAXS];
    final int[] sc = new int[MAXS];
    int sn, sNext;

    final float[] rx = new float[MAXR], ry = new float[MAXR], rr = new float[MAXR], rmax = new float[MAXR], rt = new float[MAXR];
    int rNext;

    final float[] bx = new float[24], by = new float[24];
    int bn;
    float bt;

    float flash, shake;
    float floorY, wallL, wallR;
    int bloodA = 0xFFc0121b, bloodB = 0xFF7d0a10;
    boolean gore = true;

    float rf() { return rnd.nextFloat(); }
    float rr(float m) { return (rnd.nextFloat() - 0.5f) * 2f * m; }

    void clear() {
        for (int i = 0; i < MAXP; i++) life[i] = 0f;
        sn = 0; sNext = 0; bt = 0f; flash = 0f; shake = 0f;
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

    void flame(float px, float py, float sz) {
        add(FLAME, px + rr(sz * 0.5f), py + rr(sz * 0.4f), rr(30), -50f - rf() * 90f, 0.35f + rf() * 0.35f, sz, 0xFFff9a1f);
    }

    void smoke(float px, float py, float sz, int shade) {
        add(SMOKE, px, py, rr(40), -20f - rf() * 50f, 0.9f + rf() * 0.9f, sz, shade);
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

    void stain(float px, float w, int col) {
        int i = sNext;
        sNext = (sNext + 1) % MAXS;
        sx[i] = px; sw[i] = w; sc[i] = col;
        if (sn < MAXS) sn++;
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
                        if (type[i] == BLOOD) stain(x[i], 2f + size[i] * 1.7f, Renderer.alpha(color[i], 0.85f));
                        life[i] = 0;
                    } else if (x[i] < wallL || x[i] > wallR) life[i] = 0;
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

    void drawStains(Draw2D c, Paint p) {
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < sn; i++) {
            p.setColor(sc[i]);
            c.drawRect(sx[i] - sw[i] * 0.5f, floorY - 1f, sx[i] + sw[i] * 0.5f, floorY + 2.2f, p);
        }
    }

    void draw(Draw2D c, Paint p) {
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < MAXP; i++) {
            if (life[i] <= 0) continue;
            float k = life[i] / maxLife[i];
            switch (type[i]) {
                case BLOOD: p.setColor(color[i]); c.drawCircle(x[i], y[i], size[i] * 0.55f, p); break;
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

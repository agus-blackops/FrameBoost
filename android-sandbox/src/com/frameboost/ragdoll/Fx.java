package com.frameboost.ragdoll;

import java.util.Random;

/** Purely visual particles, floor stains, shock rings and screen shake. */
final class Fx {
    static final int BLOOD = 0, FLAME = 1, SMOKE = 2, SPARK = 3, DEBRIS = 4, GLOW = 5;
    static final int MAXP = 1800;
    static final int MAXS = 650;
    static final int MAXR = 12;

    final float[] x = new float[MAXP], y = new float[MAXP], vx = new float[MAXP], vy = new float[MAXP];
    final float[] life = new float[MAXP], maxLife = new float[MAXP], size = new float[MAXP];
    final int[] type = new int[MAXP], color = new int[MAXP];
    int next;

    // floor stains (blood pools, scorch marks)
    final float[] stx = new float[MAXS], stw = new float[MAXS];
    final int[] stc = new int[MAXS];
    int stNext, stCount;

    // expanding shock rings
    final float[] rx = new float[MAXR], ry = new float[MAXR], rr = new float[MAXR], rmax = new float[MAXR], rt = new float[MAXR];
    int rNext;

    // lightning
    final float[] bx = new float[40], by = new float[40];
    int bn;
    float bt;

    float flash;          // white screen flash 0..1
    float shake;          // camera shake 0..1
    final Random rnd = new Random();
    World world;

    void clear() {
        for (int i = 0; i < MAXP; i++) life[i] = 0;
        stCount = 0; stNext = 0;
        for (int i = 0; i < MAXR; i++) rt[i] = 0;
        flash = 0; shake = 0; bt = 0;
    }

    void add(int t, float px, float py, float pvx, float pvy, float lf, float sz, int col) {
        int i = next;
        next = (next + 1) % MAXP;
        type[i] = t; x[i] = px; y[i] = py; vx[i] = pvx; vy[i] = pvy;
        life[i] = lf; maxLife[i] = lf; size[i] = sz; color[i] = col;
    }

    float rf() { return rnd.nextFloat(); }
    float rr(float a) { return (rnd.nextFloat() * 2f - 1f) * a; }

    // ---- emitters -------------------------------------------------------------------------

    void blood(float px, float py, float dx, float dy, int n) {
        for (int i = 0; i < n; i++) {
            float sp = 120f + rf() * 520f;
            float ax = dx + rr(0.9f), ay = dy + rr(0.9f);
            float l = (float) Math.sqrt(ax * ax + ay * ay) + 1e-4f;
            int c = rnd.nextInt(3) == 0 ? 0xFF7d0c0c : 0xFFb71414;
            add(BLOOD, px + rr(3), py + rr(3), ax / l * sp, ay / l * sp - 80f, 1.6f + rf(), 1.6f + rf() * 2.4f, c);
        }
    }

    void bloodBurst(float px, float py, int n) {
        for (int i = 0; i < n; i++) {
            float a = rf() * 6.2832f;
            float sp = 80f + rf() * 420f;
            int c = rnd.nextInt(3) == 0 ? 0xFF7d0c0c : 0xFFb71414;
            add(BLOOD, px, py, (float) Math.cos(a) * sp, (float) Math.sin(a) * sp - 120f, 1.4f + rf(), 1.8f + rf() * 2.6f, c);
        }
    }

    void flame(float px, float py, float sz) {
        add(FLAME, px + rr(5), py + rr(5), rr(25), -60f - rf() * 90f, 0.45f + rf() * 0.4f, sz * (0.7f + rf() * 0.6f), 0);
    }

    void smoke(float px, float py, float sz, int shade) {
        add(SMOKE, px + rr(6), py + rr(6), rr(30), -40f - rf() * 60f, 1.2f + rf() * 1.3f, sz, shade);
    }

    void sparks(float px, float py, int n, int col) {
        for (int i = 0; i < n; i++) {
            float a = rf() * 6.2832f;
            float sp = 150f + rf() * 600f;
            add(SPARK, px, py, (float) Math.cos(a) * sp, (float) Math.sin(a) * sp, 0.25f + rf() * 0.35f, 2f, col);
        }
    }

    void debris(float px, float py, int n, int col, float power) {
        for (int i = 0; i < n; i++) {
            float a = rf() * 6.2832f;
            float sp = power * (0.3f + rf());
            add(DEBRIS, px + rr(6), py + rr(6), (float) Math.cos(a) * sp, (float) Math.sin(a) * sp - power * 0.3f,
                    1.2f + rf() * 1.2f, 2.5f + rf() * 3.5f, col);
        }
    }

    void ring(float px, float py, float rmaxv) {
        int i = rNext;
        rNext = (rNext + 1) % MAXR;
        rx[i] = px; ry[i] = py; rr[i] = 0; rmax[i] = rmaxv; rt[i] = 1f;
    }

    void stain(float px, float w, int col) {
        int i = stNext;
        stNext = (stNext + 1) % MAXS;
        if (stCount < MAXS) stCount++;
        stx[i] = px; stw[i] = w; stc[i] = col;
    }

    void explosion(float px, float py, float R) {
        int flames = (int) Math.min(140, 22 + R * 0.16f);
        for (int i = 0; i < flames; i++) {
            float a = rf() * 6.2832f;
            float sp = (0.15f + rf()) * R * 2.2f;
            add(FLAME, px, py, (float) Math.cos(a) * sp, (float) Math.sin(a) * sp, 0.35f + rf() * 0.6f, 12f + rf() * R * 0.07f, 0);
        }
        for (int i = 0; i < flames / 2; i++) {
            float a = rf() * 6.2832f;
            float sp = rf() * R * 0.9f;
            add(SMOKE, px, py, (float) Math.cos(a) * sp, (float) Math.sin(a) * sp - 40, 1.4f + rf() * 1.8f, 14f + rf() * R * 0.08f,
                    rnd.nextInt(2) == 0 ? 0xFF3a3a3a : 0xFF555555);
        }
        sparks(px, py, (int) Math.min(60, 10 + R * 0.08f), 0xFFffd25a);
        debris(px, py, (int) Math.min(40, 6 + R * 0.05f), 0xFF4a4038, R * 2.6f);
        add(GLOW, px, py, 0, 0, 0.2f, R * 0.3f, 0xFFfff2b0);
        ring(px, py, R * 1.15f);
        flash = Math.max(flash, Math.min(0.9f, R / 700f));
        shake = Math.max(shake, Math.min(1f, R / 650f + 0.1f));
        if (py > world.floorY - R) stain(px, R * 0.38f, 0x99111111);
    }

    void lightning(float fromX, float fromY, float toX, float toY) {
        bn = 0;
        int segs = 14;
        for (int i = 0; i <= segs && bn < 40; i++) {
            float t = i / (float) segs;
            float jx = (i == 0 || i == segs) ? 0 : rr(34);
            bx[bn] = fromX + (toX - fromX) * t + jx;
            by[bn] = fromY + (toY - fromY) * t;
            bn++;
        }
        bt = 0.22f;
        flash = Math.max(flash, 0.5f);
        shake = Math.max(shake, 0.35f);
    }

    // ---- simulation -----------------------------------------------------------------------

    void update(float dt) {
        if (flash > 0) flash = Math.max(0, flash - dt * 2.6f);
        if (shake > 0) shake = Math.max(0, shake - dt * 2.2f);
        if (bt > 0) bt -= dt;
        for (int i = 0; i < MAXR; i++) {
            if (rt[i] <= 0) continue;
            rt[i] -= dt * 2.4f;
            rr[i] = rmax[i] * (1f - rt[i] * rt[i]);
        }
        final float floor = world.floorY, wl = world.wallL, wr = world.wallR;
        for (int i = 0; i < MAXP; i++) {
            if (life[i] <= 0) continue;
            life[i] -= dt;
            switch (type[i]) {
                case BLOOD:
                    vy[i] += 1700f * dt;
                    x[i] += vx[i] * dt; y[i] += vy[i] * dt;
                    if (y[i] >= floor) {
                        stain(x[i], 2f + size[i] * 1.7f, color[i] == 0xFF7d0c0c ? 0xDD6d0a0a : 0xDDa31212);
                        life[i] = 0;
                    } else if (x[i] < wl || x[i] > wr) {
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
                case SPARK:
                    vy[i] += 1200f * dt;
                    x[i] += vx[i] * dt; y[i] += vy[i] * dt;
                    if (y[i] > floor) { y[i] = floor; vy[i] *= -0.4f; vx[i] *= 0.7f; }
                    break;
                case DEBRIS:
                    vy[i] += 1800f * dt;
                    x[i] += vx[i] * dt; y[i] += vy[i] * dt;
                    if (y[i] > floor - size[i]) { y[i] = floor - size[i]; vy[i] *= -0.35f; vx[i] *= 0.6f; }
                    if (x[i] < wl) { x[i] = wl; vx[i] *= -0.4f; }
                    if (x[i] > wr) { x[i] = wr; vx[i] *= -0.4f; }
                    break;
                default:
                    break;
            }
        }
    }
}

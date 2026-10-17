package com.frameboost.ragdoll;

import java.util.ArrayList;

/**
 * 2D rigid-body simulation: sweep-and-prune broadphase, circle / capsule / box narrowphase with
 * persistent warm-started contacts, sequential-impulse solver for contacts and joints.
 * World space: y points down, the floor is the plane y = floorY.
 */
final class Sim {
    interface Listener {
        /** A contact absorbed a velocity change of dv along its normal (b == null for the room itself). */
        void impact(Shape a, Shape b, float dv, float px, float py, float nx, float ny);
        void jointBroken(Joint j);
    }

    static final float DT = 1f / 120f;
    static final float MARGIN = 1.5f, SLOP = 0.5f, BETA = 0.2f;

    float gravity = 1800f, gravMul = 1f;
    float floorY = 0f, ceilY = -2600f, wallL = -400f, wallR = 3400f;
    float floorFriction = 0.9f, wallFriction = 0.3f, floorRestitution = 0.08f;
    int iters = 6;
    Listener listener;
    interface Hook { void before(float dt); }
    Hook hook;
    int stepNo;
    float time, acc;
    int stepsLast;

    final RB ground = new RB().makeStatic();
    final ArrayList<RB> bodies = new ArrayList<>();
    final ArrayList<Joint> joints = new ArrayList<>();

    private Shape[] sh = new Shape[256];
    private int ns;
    Contact[] cs = new Contact[1024];
    int nc;

    static final class Contact {
        Shape sa, sb;
        RB a, b;
        float nx, ny, px, py, pen;
        int feat;
        float rax, ray, rbx, rby, nMass, tMass, kN, vTarget, jn, jt, fric, appr;
        long key;
    }

    // ------------------------------------------------------------------------------ bodies ----

    RB add(RB b) {
        b.removed = false;
        bodies.add(b);
        for (int i = 0; i < b.shapes.size(); i++) b.shapes.get(i).update();
        return b;
    }

    void remove(RB b) {
        if (b.removed) return;
        b.removed = true;
        bodies.remove(b);
        for (int i = b.joints.size() - 1; i >= 0; i--) remove(b.joints.get(i));
    }

    Joint add(Joint j) {
        joints.add(j);
        j.removed = false;
        if (j.a != null) j.a.joints.add(j);
        if (j.b != null && j.b != j.a) j.b.joints.add(j);
        j.resetImpulses();
        return j;
    }

    void remove(Joint j) {
        if (j.removed) return;
        j.removed = true;
        joints.remove(j);
        if (j.a != null) j.a.joints.remove(j);
        if (j.b != null) j.b.joints.remove(j);
    }

    void clear() {
        bodies.clear();
        joints.clear();
        nc = 0;
        ns = 0;
        warmPrev.clear();
        warmCur.clear();
        acc = 0f;
    }

    // ------------------------------------------------------------------------------- stepping ---

    void update(float dt) {
        acc += dt;
        int n = 0;
        while (acc >= DT && n < 6) { step(DT); acc -= DT; n++; }
        if (n >= 6) acc = 0f;
        stepsLast = n;
    }

    void step(float dt) {
        time += dt;
        stepNo++;
        if (hook != null) hook.before(dt);
        for (int i = 0; i < bodies.size(); i++) bodies.get(i).update();
        broadphase();
        // integrate velocities
        float g = gravity * gravMul;
        for (int i = 0; i < bodies.size(); i++) {
            RB b = bodies.get(i);
            if (b.invM == 0f) { b.fx = b.fy = b.tq = 0f; continue; }
            b.vx += b.fx * b.invM * dt;
            b.vy += (g * b.gravScale + b.fy * b.invM) * dt;
            b.w += b.tq * b.invI * dt;
            b.fx = b.fy = b.tq = 0f;
            float ld = 1f / (1f + dt * b.linDamp), ad = 1f / (1f + dt * b.angDamp);
            b.vx *= ld; b.vy *= ld; b.w *= ad;
            float sp = b.vx * b.vx + b.vy * b.vy;
            if (sp > 4200f * 4200f) { float k = 4200f / (float) Math.sqrt(sp); b.vx *= k; b.vy *= k; }
            if (b.w > 70f) b.w = 70f; else if (b.w < -70f) b.w = -70f;
        }
        // prepare
        for (int i = 0; i < joints.size(); i++) joints.get(i).prestep(dt);
        for (int i = 0; i < nc; i++) preContact(cs[i], dt);
        for (int it = 0; it < iters; it++) {
            for (int i = 0; i < joints.size(); i++) joints.get(i).solve(dt);
            for (int i = 0; i < nc; i++) solveContact(cs[i]);
        }
        // store impulses for the next step, report impacts
        warmCur.clear();
        for (int i = 0; i < nc; i++) {
            Contact c = cs[i];
            warmCur.put(c.key, c.jn, c.jt);
            if (listener != null && c.jn > 0f && c.appr > 80f) listener.impact(c.sa, c.sb, c.appr, c.px, c.py, c.nx, c.ny);
        }
        Warm t = warmPrev; warmPrev = warmCur; warmCur = t;
        // positions
        for (int i = 0; i < bodies.size(); i++) {
            RB b = bodies.get(i);
            if (b.invM == 0f) continue;
            b.x += b.vx * dt; b.y += b.vy * dt;
            b.setAngle(b.a + b.w * dt);
        }
        for (int i = joints.size() - 1; i >= 0; i--) {
            Joint j = joints.get(i);
            j.post(dt);
            if (j.broken) { remove(j); if (listener != null) listener.jointBroken(j); }
        }
    }

    // -------------------------------------------------------------------------------- warm ----

    static final class Warm {
        static final int CAP = 16384, MASK = CAP - 1;
        final long[] key = new long[CAP];
        final float[] jn = new float[CAP], jt = new float[CAP];
        final int[] stamp = new int[CAP];
        int gen = 1;
        void clear() { gen++; }
        private static int h(long k) { k ^= k >>> 29; k *= 0x9E3779B97F4A7C15L; return (int) (k >>> 40); }
        int find(long k) {
            int i = h(k) & MASK;
            while (stamp[i] == gen) { if (key[i] == k) return i; i = (i + 1) & MASK; }
            return -1;
        }
        void put(long k, float n, float t) {
            int i = h(k) & MASK, guard = 0;
            while (stamp[i] == gen && key[i] != k) { i = (i + 1) & MASK; if (++guard > CAP) return; }
            stamp[i] = gen; key[i] = k; jn[i] = n; jt[i] = t;
        }
    }

    private Warm warmPrev = new Warm(), warmCur = new Warm();

    // ----------------------------------------------------------------------------- broadphase ---

    private void broadphase() {
        ns = 0;
        for (int i = 0; i < bodies.size(); i++) {
            RB b = bodies.get(i);
            for (int k = 0; k < b.shapes.size(); k++) {
                Shape s = b.shapes.get(k);
                if (s.sensor) continue;
                if (ns == sh.length) { Shape[] n = new Shape[ns * 2]; System.arraycopy(sh, 0, n, 0, ns); sh = n; }
                sh[ns++] = s;
            }
        }
        // insertion sort by minX (nearly sorted frame to frame)
        for (int i = 1; i < ns; i++) {
            Shape k = sh[i];
            int j = i - 1;
            while (j >= 0 && sh[j].minX > k.minX) { sh[j + 1] = sh[j]; j--; }
            sh[j + 1] = k;
        }
        nc = 0;
        for (int i = 0; i < ns; i++) {
            Shape a = sh[i];
            planes(a);
            for (int j = i + 1; j < ns; j++) {
                Shape b = sh[j];
                if (b.minX > a.maxX) break;
                if (b.minY > a.maxY || b.maxY < a.minY) continue;
                if (!shouldCollide(a, b)) continue;
                if (a.id < b.id) collide(a, b); else collide(b, a);
            }
        }
    }

    private boolean shouldCollide(Shape a, Shape b) {
        RB ra = a.body, rb = b.body;
        if (ra == rb) return false;
        if (ra.invM == 0f && rb.invM == 0f) return false;
        if (a.group != 0 && a.group == b.group) return false;
        ArrayList<Joint> js = ra.joints.size() <= rb.joints.size() ? ra.joints : rb.joints;
        for (int i = 0; i < js.size(); i++) {
            Joint j = js.get(i);
            if (!j.collide && ((j.a == ra && j.b == rb) || (j.a == rb && j.b == ra))) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------------------ contacts ----

    private Contact newContact() {
        if (nc == cs.length) { Contact[] n = new Contact[nc * 2]; System.arraycopy(cs, 0, n, 0, nc); cs = n; }
        Contact c = cs[nc];
        if (c == null) c = cs[nc] = new Contact();
        nc++;
        return c;
    }

    private void emit(Shape sa, Shape sb, float px, float py, float nx, float ny, float pen, int feat) {
        Contact c = newContact();
        c.sa = sa; c.sb = sb; c.a = sa.body; c.b = sb == null ? ground : sb.body;
        c.px = px; c.py = py; c.nx = nx; c.ny = ny; c.pen = pen; c.feat = feat;
        if (pen > -0.6f) {
            if (ny > 0.55f) c.a.supportStep = stepNo; else if (ny < -0.55f) c.b.supportStep = stepNo;
        }
        c.key = ((long) sa.id << 40) | ((long) (sb == null ? 0 : sb.id) << 8) | (feat & 255);
        if (sb == null) {
            c.fric = (float) Math.sqrt(sa.friction * (feat >= 8 && feat < 16 ? floorFriction : wallFriction));
            if (feat < 8) c.fric = (float) Math.sqrt(sa.friction * floorFriction);
        } else {
            c.fric = (float) Math.sqrt(sa.friction * sb.friction);
        }
    }

    // sample points of a shape (world space); returns count, fills sx/sy, radius in sr
    private final float[] sx = new float[8], sy = new float[8];
    private float sr;

    private int samples(Shape s) {
        switch (s.type) {
            case Shape.CIRCLE: sx[0] = s.cx; sy[0] = s.cy; sr = s.r; return 1;
            case Shape.CAPSULE:
                sx[0] = s.cx - s.ax * s.hl; sy[0] = s.cy - s.ay * s.hl;
                sx[1] = s.cx + s.ax * s.hl; sy[1] = s.cy + s.ay * s.hl;
                sr = s.r;
                if (s.hl > 16f) { sx[2] = s.cx; sy[2] = s.cy; return 3; }
                return 2;
            default: {
                float ux = s.ax * s.hl, uy = s.ay * s.hl, vx = -s.ay * s.hh, vy = s.ax * s.hh;
                sx[0] = s.cx - ux - vx; sy[0] = s.cy - uy - vy;
                sx[1] = s.cx + ux - vx; sy[1] = s.cy + uy - vy;
                sx[2] = s.cx + ux + vx; sy[2] = s.cy + uy + vy;
                sx[3] = s.cx - ux + vx; sy[3] = s.cy - uy + vy;
                sr = 0f;
                return 4;
            }
        }
    }

    private void planes(Shape s) {
        if (s.body.invM == 0f) return;
        // quick reject using the AABB
        if (s.maxY < floorY - MARGIN && s.minY > ceilY + MARGIN && s.minX > wallL + MARGIN && s.maxX < wallR - MARGIN) return;
        int n = samples(s);
        float r = sr;
        for (int i = 0; i < n; i++) {
            float px = sx[i], py = sy[i];
            float sep = floorY - (py + r);
            if (sep < MARGIN) emit(s, null, px, floorY, 0f, 1f, -sep, 8 + i);
            sep = (py - r) - ceilY;
            if (sep < MARGIN) emit(s, null, px, ceilY, 0f, -1f, -sep, 16 + i);
            sep = (px - r) - wallL;
            if (sep < MARGIN) emit(s, null, wallL, py, -1f, 0f, -sep, 24 + i);
            sep = wallR - (px + r);
            if (sep < MARGIN) emit(s, null, wallR, py, 1f, 0f, -sep, 32 + i);
        }
    }

    // signed distance of a point to a shape core (negative inside boxes), outward normal
    private float dd, dnx, dny;

    private void sdf(Shape s, float px, float py) {
        switch (s.type) {
            case Shape.CIRCLE: {
                float dx = px - s.cx, dy = py - s.cy, d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d < 1e-5f) { dd = 0f; dnx = 0f; dny = -1f; } else { dd = d; dnx = dx / d; dny = dy / d; }
                return;
            }
            case Shape.CAPSULE: {
                float dx = px - s.cx, dy = py - s.cy;
                float u = dx * s.ax + dy * s.ay;
                if (u > s.hl) u = s.hl; else if (u < -s.hl) u = -s.hl;
                float qx = px - (s.cx + s.ax * u), qy = py - (s.cy + s.ay * u);
                float d = (float) Math.sqrt(qx * qx + qy * qy);
                if (d < 1e-5f) { dd = 0f; dnx = -s.ay; dny = s.ax; } else { dd = d; dnx = qx / d; dny = qy / d; }
                return;
            }
            default: {
                float dx = px - s.cx, dy = py - s.cy;
                float lx = dx * s.ax + dy * s.ay, ly = -dx * s.ay + dy * s.ax;
                float ex = Math.abs(lx) - s.hl, ey = Math.abs(ly) - s.hh;
                float nx, ny;
                if (ex > 0f || ey > 0f) {
                    float ox = Math.max(ex, 0f), oy = Math.max(ey, 0f);
                    float d = (float) Math.sqrt(ox * ox + oy * oy);
                    dd = d;
                    nx = (lx < 0 ? -ox : ox) / d; ny = (ly < 0 ? -oy : oy) / d;
                } else if (ex > ey) { dd = ex; nx = lx < 0 ? -1f : 1f; ny = 0f; }
                else { dd = ey; nx = 0f; ny = ly < 0 ? -1f : 1f; }
                dnx = nx * s.ax - ny * s.ay; dny = nx * s.ay + ny * s.ax;
            }
        }
    }

    private static float rad(Shape s) { return s.type == Shape.BOX ? 0f : s.r; }

    // candidate list for the generic pair test
    private final float[] cpx = new float[24], cpy = new float[24], cnx = new float[24], cny = new float[24], cpen = new float[24];
    private final int[] cf = new int[24];
    private int cn;

    private void cand(float px, float py, float nx, float ny, float pen, int f) {
        if (cn < cpx.length) { cpx[cn] = px; cpy[cn] = py; cnx[cn] = nx; cny[cn] = ny; cpen[cn] = pen; cf[cn] = f; cn++; }
    }

    private void collide(Shape A, Shape B) {
        if (A.type == Shape.BOX && B.type == Shape.BOX) { boxBox(A, B); return; }
        cn = 0;
        int n = samples(A);
        float ra = sr, rb = rad(B);
        for (int i = 0; i < n; i++) {
            sdf(B, sx[i], sy[i]);
            float d = dd - rb - ra;
            if (d < MARGIN) cand(sx[i] - dnx * (ra + d * 0.5f), sy[i] - dny * (ra + d * 0.5f), -dnx, -dny, -d, i);
        }
        n = samples(B);
        rb = sr; ra = rad(A);
        for (int i = 0; i < n; i++) {
            sdf(A, sx[i], sy[i]);
            float d = dd - ra - rb;
            if (d < MARGIN) cand(sx[i] - dnx * (rb + d * 0.5f), sy[i] - dny * (rb + d * 0.5f), dnx, dny, -d, 8 + i);
        }
        if (A.type != Shape.CIRCLE && B.type != Shape.CIRCLE) edges(A, B);
        if (cn == 0) return;
        // keep the two deepest, well separated contacts
        int b0 = 0;
        for (int i = 1; i < cn; i++) if (cpen[i] > cpen[b0]) b0 = i;
        emit(A, B, cpx[b0], cpy[b0], cnx[b0], cny[b0], cpen[b0], cf[b0]);
        int b1 = -1;
        for (int i = 0; i < cn; i++) {
            if (i == b0) continue;
            float dx = cpx[i] - cpx[b0], dy = cpy[i] - cpy[b0];
            if (dx * dx + dy * dy < 16f) continue;
            if (b1 < 0 || cpen[i] > cpen[b1]) b1 = i;
        }
        if (b1 >= 0) emit(A, B, cpx[b1], cpy[b1], cnx[b1], cny[b1], cpen[b1], cf[b1]);
    }

    private final float[] ex0 = new float[8], ey0 = new float[8], ex1 = new float[8], ey1 = new float[8];

    private int segs(Shape s, float[] x0, float[] y0, float[] x1, float[] y1) {
        if (s.type == Shape.CAPSULE) {
            x0[0] = s.cx - s.ax * s.hl; y0[0] = s.cy - s.ay * s.hl; x1[0] = s.cx + s.ax * s.hl; y1[0] = s.cy + s.ay * s.hl;
            return 1;
        }
        float ux = s.ax * s.hl, uy = s.ay * s.hl, vx = -s.ay * s.hh, vy = s.ax * s.hh;
        float[] qx = {s.cx - ux - vx, s.cx + ux - vx, s.cx + ux + vx, s.cx - ux + vx};
        float[] qy = {s.cy - uy - vy, s.cy + uy - vy, s.cy + uy + vy, s.cy - uy + vy};
        for (int i = 0; i < 4; i++) { x0[i] = qx[i]; y0[i] = qy[i]; x1[i] = qx[(i + 1) & 3]; y1[i] = qy[(i + 1) & 3]; }
        return 4;
    }

    private final float[] bx0 = new float[8], by0 = new float[8], bx1 = new float[8], by1 = new float[8];

    /** Edge-edge crossings (an X of two capsules or a capsule across a plank) that no vertex sample sees. */
    private void edges(Shape A, Shape B) {
        int na = segs(A, ex0, ey0, ex1, ey1), nb = segs(B, bx0, by0, bx1, by1);
        float ra = rad(A), rb = rad(B);
        for (int i = 0; i < na; i++)
            for (int j = 0; j < nb; j++) {
                float d1x = ex1[i] - ex0[i], d1y = ey1[i] - ey0[i], d2x = bx1[j] - bx0[j], d2y = by1[j] - by0[j];
                float rx = ex0[i] - bx0[j], ry = ey0[i] - by0[j];
                float a = d1x * d1x + d1y * d1y, e = d2x * d2x + d2y * d2y, f = d2x * rx + d2y * ry;
                if (a < 1e-6f || e < 1e-6f) continue;
                float c = d1x * rx + d1y * ry, b = d1x * d2x + d1y * d2y;
                float den = a * e - b * b;
                if (den < 1e-6f * a * e) continue;           // parallel: the vertex samples cover it
                float s = (b * f - c * e) / den;
                if (s <= 0.01f || s >= 0.99f) continue;
                float t = (b * s + f) / e;
                if (t <= 0.01f || t >= 0.99f) continue;
                float pax = ex0[i] + d1x * s, pay = ey0[i] + d1y * s, pbx = bx0[j] + d2x * t, pby = by0[j] + d2y * t;
                float dx = pbx - pax, dy = pby - pay, d = (float) Math.sqrt(dx * dx + dy * dy);
                float sep = d - ra - rb;
                if (sep >= MARGIN) continue;
                float nx, ny;
                if (d > 1e-4f) { nx = dx / d; ny = dy / d; }
                else {                                        // segments cross: push along the thinner axis
                    nx = -d1y; ny = d1x;
                    float l = (float) Math.sqrt(nx * nx + ny * ny);
                    nx /= l; ny /= l;
                    if (nx * (B.cx - A.cx) + ny * (B.cy - A.cy) < 0f) { nx = -nx; ny = -ny; }
                    sep = -(ra + rb) - 0f;
                }
                cand((pax + pbx) * 0.5f, (pay + pby) * 0.5f, nx, ny, -sep, 16 + i * 4 + j);
            }
    }

    // --------------------------------------------------------------------------- box vs box -----

    private final float[] c0x = new float[2], c0y = new float[2], c1x = new float[2], c1y = new float[2];

    private int clip(float[] ox, float[] oy, float[] ix, float[] iy, float nx, float ny, float off) {
        int n = 0;
        float d0 = nx * ix[0] + ny * iy[0] - off, d1 = nx * ix[1] + ny * iy[1] - off;
        if (d0 <= 0f) { ox[n] = ix[0]; oy[n] = iy[0]; n++; }
        if (d1 <= 0f) { ox[n] = ix[1]; oy[n] = iy[1]; n++; }
        if (d0 * d1 < 0f && n < 2) {
            float k = d0 / (d0 - d1);
            ox[n] = ix[0] + k * (ix[1] - ix[0]); oy[n] = iy[0] + k * (iy[1] - iy[0]); n++;
        }
        return n;
    }

    private final float[] iex = new float[2], iey = new float[2];

    private void incident(Shape s, float nx, float ny) {
        // normal in the box frame, flipped
        float lx = -(nx * s.ax + ny * s.ay), ly = -(-nx * s.ay + ny * s.ax);
        float hx = s.hl, hy = s.hh;
        float p0x, p0y, p1x, p1y;
        if (Math.abs(lx) > Math.abs(ly)) {
            if (lx > 0) { p0x = hx; p0y = -hy; p1x = hx; p1y = hy; }
            else { p0x = -hx; p0y = hy; p1x = -hx; p1y = -hy; }
        } else {
            if (ly > 0) { p0x = hx; p0y = hy; p1x = -hx; p1y = hy; }
            else { p0x = -hx; p0y = -hy; p1x = hx; p1y = -hy; }
        }
        iex[0] = s.cx + s.ax * p0x - s.ay * p0y; iey[0] = s.cy + s.ay * p0x + s.ax * p0y;
        iex[1] = s.cx + s.ax * p1x - s.ay * p1y; iey[1] = s.cy + s.ay * p1x + s.ax * p1y;
    }

    private void boxBox(Shape A, Shape B) {
        float hax = A.hl, hay = A.hh, hbx = B.hl, hby = B.hh;
        float dpx = B.cx - A.cx, dpy = B.cy - A.cy;
        // A frame: x axis (ax, ay), y axis (-ay, ax)
        float dAx = dpx * A.ax + dpy * A.ay, dAy = -dpx * A.ay + dpy * A.ax;
        float dBx = dpx * B.ax + dpy * B.ay, dBy = -dpx * B.ay + dpy * B.ax;
        // C = A^T B
        float c00 = A.ax * B.ax + A.ay * B.ay, c01 = -A.ax * B.ay + A.ay * B.ax;
        float c10 = -A.ay * B.ax + A.ax * B.ay, c11 = A.ay * B.ay + A.ax * B.ax;
        float a00 = Math.abs(c00), a01 = Math.abs(c01), a10 = Math.abs(c10), a11 = Math.abs(c11);
        float faX = Math.abs(dAx) - hax - (a00 * hbx + a01 * hby);
        if (faX > 0f) return;
        float faY = Math.abs(dAy) - hay - (a10 * hbx + a11 * hby);
        if (faY > 0f) return;
        // C^T abs
        float fbX = Math.abs(dBx) - (a00 * hax + a10 * hay) - hbx;
        if (fbX > 0f) return;
        float fbY = Math.abs(dBy) - (a01 * hax + a11 * hay) - hby;
        if (fbY > 0f) return;
        int axis = 0;
        float sep = faX;
        float nx = dAx > 0 ? A.ax : -A.ax, ny = dAx > 0 ? A.ay : -A.ay;
        final float rel = 0.95f, abs = 0.01f;
        if (faY > rel * sep + abs * hay) { axis = 1; sep = faY; nx = dAy > 0 ? -A.ay : A.ay; ny = dAy > 0 ? A.ax : -A.ax; }
        if (fbX > rel * sep + abs * hbx) { axis = 2; sep = fbX; nx = dBx > 0 ? B.ax : -B.ax; ny = dBx > 0 ? B.ay : -B.ay; }
        if (fbY > rel * sep + abs * hby) { axis = 3; sep = fbY; nx = dBy > 0 ? -B.ay : B.ay; ny = dBy > 0 ? B.ax : -B.ax; }
        float fnx, fny, front, snx, sny, negSide, posSide;
        switch (axis) {
            case 0:
                fnx = nx; fny = ny; front = A.cx * fnx + A.cy * fny + hax;
                snx = -A.ay; sny = A.ax;
                { float side = A.cx * snx + A.cy * sny; negSide = -side + hay; posSide = side + hay; }
                incident(B, fnx, fny);
                break;
            case 1:
                fnx = nx; fny = ny; front = A.cx * fnx + A.cy * fny + hay;
                snx = A.ax; sny = A.ay;
                { float side = A.cx * snx + A.cy * sny; negSide = -side + hax; posSide = side + hax; }
                incident(B, fnx, fny);
                break;
            case 2:
                fnx = -nx; fny = -ny; front = B.cx * fnx + B.cy * fny + hbx;
                snx = -B.ay; sny = B.ax;
                { float side = B.cx * snx + B.cy * sny; negSide = -side + hby; posSide = side + hby; }
                incident(A, fnx, fny);
                break;
            default:
                fnx = -nx; fny = -ny; front = B.cx * fnx + B.cy * fny + hby;
                snx = B.ax; sny = B.ay;
                { float side = B.cx * snx + B.cy * sny; negSide = -side + hbx; posSide = side + hbx; }
                incident(A, fnx, fny);
                break;
        }
        if (clip(c0x, c0y, iex, iey, -snx, -sny, negSide) < 2) return;
        if (clip(c1x, c1y, c0x, c0y, snx, sny, posSide) < 2) return;
        for (int i = 0; i < 2; i++) {
            float separation = fnx * c1x[i] + fny * c1y[i] - front;
            if (separation <= MARGIN) {
                emit(A, B, c1x[i] - 0.5f * separation * fnx, c1y[i] - 0.5f * separation * fny, nx, ny, -separation, axis * 2 + i);
            }
        }
    }

    // ---------------------------------------------------------------------------- solver --------

    private static final float REST_THRESHOLD = 260f;

    private void preContact(Contact c, float dt) {
        RB a = c.a, b = c.b;
        c.rax = c.px - a.x; c.ray = c.py - a.y; c.rbx = c.px - b.x; c.rby = c.py - b.y;
        float nx = c.nx, ny = c.ny, tx = -ny, ty = nx;
        float rnA = c.rax * ny - c.ray * nx, rnB = c.rbx * ny - c.rby * nx;
        float k = a.invM + b.invM + a.invI * rnA * rnA + b.invI * rnB * rnB;
        c.kN = k;
        c.nMass = k > 0f ? 1f / k : 0f;
        float rtA = c.rax * ty - c.ray * tx, rtB = c.rbx * ty - c.rby * tx;
        float kt = a.invM + b.invM + a.invI * rtA * rtA + b.invI * rtB * rtB;
        c.tMass = kt > 0f ? 1f / kt : 0f;
        float vx = (b.vx - b.w * c.rby) - (a.vx - a.w * c.ray), vy = (b.vy + b.w * c.rbx) - (a.vy + a.w * c.rax);
        float vn = vx * nx + vy * ny;
        float vl = (b.vx - a.vx) * nx + (b.vy - a.vy) * ny;   // linear approach speed (spin of light limbs would exaggerate it)
        c.appr = vl < 0f ? -vl : 0f;
        float sep = -c.pen;
        float vt = sep > 0f ? -sep / dt : Math.min(380f, Math.max(0f, BETA * (c.pen - SLOP) / dt));
        float rest = c.sb == null ? Math.max(c.sa.restitution, floorRestitution) : Math.max(c.sa.restitution, c.sb.restitution);
        if (vn < -REST_THRESHOLD && rest > 0f && sep < 1f) vt = Math.max(vt, -rest * vn);
        c.vTarget = vt;
        int w = warmPrev.find(c.key);
        if (w >= 0) {
            c.jn = warmPrev.jn[w] * 0.92f; c.jt = warmPrev.jt[w] * 0.92f;
            float px = c.jn * nx + c.jt * tx, py = c.jn * ny + c.jt * ty;
            a.vx -= a.invM * px; a.vy -= a.invM * py; a.w -= a.invI * (c.rax * py - c.ray * px);
            b.vx += b.invM * px; b.vy += b.invM * py; b.w += b.invI * (c.rbx * py - c.rby * px);
        } else { c.jn = 0f; c.jt = 0f; }
    }

    private void solveContact(Contact c) {
        RB a = c.a, b = c.b;
        float nx = c.nx, ny = c.ny, tx = -ny, ty = nx;
        // friction
        float vx = (b.vx - b.w * c.rby) - (a.vx - a.w * c.ray), vy = (b.vy + b.w * c.rbx) - (a.vy + a.w * c.rax);
        float vt = vx * tx + vy * ty;
        float lam = -c.tMass * vt;
        float max = c.fric * c.jn;
        float nj = Math.max(-max, Math.min(max, c.jt + lam));
        lam = nj - c.jt; c.jt = nj;
        float px = lam * tx, py = lam * ty;
        a.vx -= a.invM * px; a.vy -= a.invM * py; a.w -= a.invI * (c.rax * py - c.ray * px);
        b.vx += b.invM * px; b.vy += b.invM * py; b.w += b.invI * (c.rbx * py - c.rby * px);
        // normal
        vx = (b.vx - b.w * c.rby) - (a.vx - a.w * c.ray); vy = (b.vy + b.w * c.rbx) - (a.vy + a.w * c.rax);
        float vn = vx * nx + vy * ny;
        lam = c.nMass * (c.vTarget - vn);
        nj = Math.max(0f, c.jn + lam);
        lam = nj - c.jn; c.jn = nj;
        px = lam * nx; py = lam * ny;
        a.vx -= a.invM * px; a.vy -= a.invM * py; a.w -= a.invI * (c.rax * py - c.ray * px);
        b.vx += b.invM * px; b.vy += b.invM * py; b.w += b.invI * (c.rbx * py - c.rby * px);
    }

    // ----------------------------------------------------------------------------- queries ------

    static final class Hit {
        Shape shape;
        float t, nx, ny;
    }

    private final ArrayList<Hit> hitPool = new ArrayList<>();
    private int hitUsed;
    final ArrayList<Hit> hits = new ArrayList<>();

    private Hit hit() {
        if (hitUsed == hitPool.size()) hitPool.add(new Hit());
        return hitPool.get(hitUsed++);
    }

    private float rt, rnx, rny;

    private boolean rayCircle(float ox, float oy, float dx, float dy, float cx, float cy, float r, float maxT) {
        float fx = ox - cx, fy = oy - cy;
        float b = fx * dx + fy * dy, c = fx * fx + fy * fy - r * r;
        if (c > 0f && b > 0f) return false;
        float disc = b * b - c;
        if (disc < 0f) return false;
        float t = -b - (float) Math.sqrt(disc);
        if (t < 0f) t = 0f;
        if (t > maxT) return false;
        rt = t;
        float px = ox + dx * t - cx, py = oy + dy * t - cy, l = (float) Math.sqrt(px * px + py * py);
        if (l > 1e-5f) { rnx = px / l; rny = py / l; } else { rnx = -dx; rny = -dy; }
        return true;
    }

    private boolean rayBox(float ox, float oy, float dx, float dy, float cx, float cy, float ax, float ay, float hx, float hy, float maxT) {
        float px = ox - cx, py = oy - cy;
        float lx = px * ax + py * ay, ly = -px * ay + py * ax;
        float ldx = dx * ax + dy * ay, ldy = -dx * ay + dy * ax;
        float tmin = 0f, tmax = maxT;
        float nxl = 0f, nyl = 0f;
        for (int k = 0; k < 2; k++) {
            float o = k == 0 ? lx : ly, d = k == 0 ? ldx : ldy, h = k == 0 ? hx : hy;
            if (Math.abs(d) < 1e-7f) { if (o < -h || o > h) return false; continue; }
            float t1 = (-h - o) / d, t2 = (h - o) / d;
            float sgn = -1f;
            if (t1 > t2) { float t = t1; t1 = t2; t2 = t; sgn = 1f; }
            if (t1 > tmin) { tmin = t1; nxl = k == 0 ? sgn : 0f; nyl = k == 1 ? sgn : 0f; }
            if (t2 < tmax) tmax = t2;
            if (tmin > tmax) return false;
        }
        rt = tmin;
        rnx = nxl * ax - nyl * ay; rny = nxl * ay + nyl * ax;
        if (nxl == 0f && nyl == 0f) { rnx = -dx; rny = -dy; }
        return true;
    }

    private boolean rayShape(Shape s, float ox, float oy, float dx, float dy, float maxT) {
        switch (s.type) {
            case Shape.CIRCLE: return rayCircle(ox, oy, dx, dy, s.cx, s.cy, s.r, maxT);
            case Shape.BOX: return rayBox(ox, oy, dx, dy, s.cx, s.cy, s.ax, s.ay, s.hl, s.hh, maxT);
            default: {
                boolean got = false;
                float bt = maxT, bnx = 0f, bny = 0f;
                if (rayBox(ox, oy, dx, dy, s.cx, s.cy, s.ax, s.ay, s.hl, s.r, bt)) { got = true; bt = rt; bnx = rnx; bny = rny; }
                if (rayCircle(ox, oy, dx, dy, s.cx - s.ax * s.hl, s.cy - s.ay * s.hl, s.r, bt)) { got = true; bt = rt; bnx = rnx; bny = rny; }
                if (rayCircle(ox, oy, dx, dy, s.cx + s.ax * s.hl, s.cy + s.ay * s.hl, s.r, bt)) { got = true; bt = rt; bnx = rnx; bny = rny; }
                if (got) { rt = bt; rnx = bnx; rny = bny; }
                return got;
            }
        }
    }

    /** All shapes the ray crosses (nearest first); (dx, dy) must be a unit vector. */
    ArrayList<Hit> ray(float ox, float oy, float dx, float dy, float maxT) {
        hits.clear();
        hitUsed = 0;
        float ex = ox + dx * maxT, ey = oy + dy * maxT;
        float x0 = Math.min(ox, ex), x1 = Math.max(ox, ex), y0 = Math.min(oy, ey), y1 = Math.max(oy, ey);
        for (int i = 0; i < bodies.size(); i++) {
            RB b = bodies.get(i);
            for (int k = 0; k < b.shapes.size(); k++) {
                Shape s = b.shapes.get(k);
                if (s.sensor || s.maxX < x0 || s.minX > x1 || s.maxY < y0 || s.minY > y1) continue;
                if (!rayShape(s, ox, oy, dx, dy, maxT)) continue;
                Hit h = hit();
                h.shape = s; h.t = rt; h.nx = rnx; h.ny = rny;
                int j = hits.size();
                hits.add(h);
                while (j > 0 && hits.get(j - 1).t > h.t) { hits.set(j, hits.get(j - 1)); j--; }
                hits.set(j, h);
            }
        }
        return hits;
    }

    /** Nearest wall / floor / ceiling along the ray. */
    float rayRoom(float ox, float oy, float dx, float dy, float maxT) {
        float t = maxT;
        if (dy > 1e-5f) t = Math.min(t, (floorY - oy) / dy);
        if (dy < -1e-5f) t = Math.min(t, (ceilY - oy) / dy);
        if (dx > 1e-5f) t = Math.min(t, (wallR - ox) / dx);
        if (dx < -1e-5f) t = Math.min(t, (wallL - ox) / dx);
        return Math.max(0f, t);
    }

    /** The shape under a point (tolerance in world units): nearest surface wins; small shapes beat big ones. */
    Shape pick(float px, float py, float tol, boolean includeStatic) {
        Shape best = null;
        float bs = Float.MAX_VALUE;
        for (int i = 0; i < bodies.size(); i++) {
            RB b = bodies.get(i);
            if (!includeStatic && b.isStatic) continue;
            for (int k = 0; k < b.shapes.size(); k++) {
                Shape s = b.shapes.get(k);
                if (s.sensor) continue;
                if (px < s.minX - tol || px > s.maxX + tol || py < s.minY - tol || py > s.maxY + tol) continue;
                sdf(s, px, py);
                float d = dd - rad(s);
                if (d > tol) continue;
                float score = d + s.reach() * 0.02f;
                if (score < bs) { bs = score; best = s; }
            }
        }
        return best;
    }

    /** Applies a radial impulse (explosions). Falloff is linear; returns nothing, bodies keep their mass. */
    void blast(float x, float y, float radius, float speed) {
        for (int i = 0; i < bodies.size(); i++) {
            RB b = bodies.get(i);
            if (b.invM == 0f) continue;
            float dx = b.x - x, dy = b.y - y;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            float reach = radius + (b.shapes.isEmpty() ? 0f : b.shapes.get(0).reach());
            if (d > reach) continue;
            float f = 1f - d / reach;
            if (d < 1e-3f) { dx = 0f; dy = -1f; d = 1f; }
            float k = speed * f * f;
            b.vx += dx / d * k;
            b.vy += (dy / d - 0.25f) * k;
            b.w += (dx > 0 ? 1f : -1f) * k * 0.004f * (b.invM > 0 ? 1f : 0f) * (dy > 0 ? -1f : 1f);
        }
    }
}

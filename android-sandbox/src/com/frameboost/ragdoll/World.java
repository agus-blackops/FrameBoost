package com.frameboost.ragdoll;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

/** Physics + gameplay rules: Verlet solver, collisions, damage, bleeding, fire, explosions, bullets. */
final class World {
    static final float H = 1f / 240f;
    static final int ITER = 3;
    static final float CELL = 64f;
    static final int GRID = 4096;
    static final int MAX_PARTICLES = 3600;
    static final int MAX_ENTRIES = 60000;
    static final int MAX_BODIES = 160;
    static final float FRICTION = 0.45f;

    float floorY = 0f, wallL = 0f, wallR = 2600f, ceilY = -1900f;
    float gravity = 1900f;
    float gravMul = 1f;
    float wind = 0f;          // horizontal acceleration (mods)
    float modBounce = 0f;     // minimum bounce (mods)

    final ArrayList<Body> bodies = new ArrayList<>();
    final ArrayList<Particle> ps = new ArrayList<>();
    final ArrayList<Stick> ss = new ArrayList<>();
    final ArrayList<AngleLimit> ls = new ArrayList<>();
    final ArrayList<Grab> grabs = new ArrayList<>();
    final ArrayList<Projectile> rockets = new ArrayList<>();
    final Fx fx;
    final Sfx sfx;
    final Random rnd = new Random();
    boolean dirty = true;

    private float acc;
    private float spreadT, bleedAcc;
    private int frame, stampSeq;

    // broadphase
    private final int[] pHead = new int[GRID], pNext = new int[MAX_PARTICLES + 8];
    private final int[] sHead = new int[GRID], eNext = new int[MAX_ENTRIES], eStick = new int[MAX_ENTRIES];
    private int ec;

    static final class Grab {
        Particle p; float tx, ty;
    }

    static final class Projectile {
        float x, y, vx, vy, life;
    }

    World(Fx fx, Sfx sfx) {
        this.fx = fx;
        this.sfx = sfx;
        fx.world = this;
    }

    // ================================================================= body management =======

    boolean canAdd(Body b) {
        return bodies.size() < MAX_BODIES && ps.size() + b.ps.size() < MAX_PARTICLES - 60;
    }

    boolean add(Body b) {
        if (!canAdd(b)) return false;
        bodies.add(b);
        dirty = true;
        return true;
    }

    void removeBody(Body b) {
        if (b.removed) return;
        b.removed = true;
        bodies.remove(b);
        for (int i = 0; i < b.ps.size(); i++) b.ps.get(i).dead = true;
        for (int i = grabs.size() - 1; i >= 0; i--) if (grabs.get(i).p.dead) grabs.remove(i);
        dirty = true;
    }

    void clear() {
        for (int i = bodies.size() - 1; i >= 0; i--) removeBody(bodies.get(i));
        rockets.clear();
        grabs.clear();
        fx.clear();
    }

    private void rebuild() {
        ps.clear(); ss.clear(); ls.clear();
        for (int i = 0; i < bodies.size(); i++) {
            Body b = bodies.get(i);
            b.compact();
            ps.addAll(b.ps);
            ss.addAll(b.ss);
            ls.addAll(b.ls);
        }
        for (int i = 0; i < ps.size(); i++) ps.get(i).idx = i;
        for (int i = 0; i < ss.size(); i++) ss.get(i).idx = i;
        for (int i = grabs.size() - 1; i >= 0; i--) if (grabs.get(i).p.dead) grabs.remove(i);
        dirty = false;
    }

    // ===================================================================== stepping =========

    /** Advances the simulation by simDt seconds of game time. */
    void update(float simDt) {
        if (dirty) rebuild();
        acc += simDt;
        int steps = 0;
        while (acc >= H && steps < 10) {
            step();
            acc -= H;
            steps++;
        }
        if (steps >= 10) acc = 0;
        post(simDt);
    }

    private void step() {
        final int n = ps.size();
        if (n == 0) return;
        frame++;
        final float g = gravity * gravMul * H * H;
        for (int i = 0; i < n; i++) {
            Particle p = ps.get(i);
            if (p.invM == 0f) continue;
            float d = p.body.drag;
            float vx = (p.x - p.px) * d, vy = (p.y - p.py) * d;
            p.px = p.x; p.py = p.y;
            p.x += vx + wind * H * H;
            p.y += vy + g * p.body.gravScale;
        }
        buildGrids();
        for (int it = 0; it < ITER; it++) {
            solveSticks();
            solveLimits();
            applyGrabs(it == 0);
            if (it == 0 || it == ITER - 1) collide(it == 0);
            bounds(it == 0);
        }
    }

    private void solveSticks() {
        final int n = ss.size();
        for (int i = 0; i < n; i++) {
            Stick s = ss.get(i);
            if (s.dead) continue;
            Particle a = s.a, b = s.b;
            float dx = b.x - a.x, dy = b.y - a.y;
            float d2 = dx * dx + dy * dy;
            if (d2 < 1e-12f) continue;
            float w = a.invM + b.invM;
            if (w == 0f) continue;
            float d = (float) Math.sqrt(d2);
            float k = (d - s.rest) / d / w;
            float ka = k * a.invM, kb = k * b.invM;
            a.x += dx * ka; a.y += dy * ka;
            b.x -= dx * kb; b.y -= dy * kb;
        }
    }

    private void solveLimits() {
        final int n = ls.size();
        for (int i = 0; i < n; i++) ls.get(i).solve();
    }

    private void applyGrabs(boolean first) {
        for (int i = 0; i < grabs.size(); i++) {
            Grab g = grabs.get(i);
            Particle p = g.p;
            if (p.dead) continue;
            float dx = g.tx - p.x, dy = g.ty - p.y;
            float k = 0.3f;
            p.x += dx * k; p.y += dy * k;
            if (first) {
                // damp so the held part doesn't oscillate
                p.px = p.x - (p.x - p.px) * 0.8f;
                p.py = p.y - (p.y - p.py) * 0.8f;
            }
        }
    }

    // ----------------------------------------------------------------------- collisions -----

    private static int hash(int cx, int cy) {
        return ((cx * 73856093) ^ (cy * 19349663)) & (GRID - 1);
    }

    private void buildGrids() {
        Arrays.fill(pHead, -1);
        Arrays.fill(sHead, -1);
        ec = 0;
        final int n = ps.size();
        for (int i = 0; i < n; i++) {
            Particle p = ps.get(i);
            int key = hash((int) Math.floor(p.x / CELL), (int) Math.floor(p.y / CELL));
            pNext[i] = pHead[key];
            pHead[key] = i;
        }
        final int m = ss.size();
        for (int i = 0; i < m; i++) {
            Stick s = ss.get(i);
            if (s.dead || !s.collide) continue;
            float mg = s.r + 34f;
            float x0 = Math.min(s.a.x, s.b.x) - mg, x1 = Math.max(s.a.x, s.b.x) + mg;
            float y0 = Math.min(s.a.y, s.b.y) - mg, y1 = Math.max(s.a.y, s.b.y) + mg;
            int cx0 = (int) Math.floor(x0 / CELL), cx1 = (int) Math.floor(x1 / CELL);
            int cy0 = (int) Math.floor(y0 / CELL), cy1 = (int) Math.floor(y1 / CELL);
            for (int cx = cx0; cx <= cx1; cx++)
                for (int cy = cy0; cy <= cy1; cy++) {
                    if (ec >= MAX_ENTRIES) return;
                    int key = hash(cx, cy);
                    eStick[ec] = i;
                    eNext[ec] = sHead[key];
                    sHead[key] = ec++;
                }
        }
    }

    private void collide(boolean first) {
        final int n = ps.size();
        for (int i = 0; i < n; i++) {
            Particle p = ps.get(i);
            int cx = (int) Math.floor(p.x / CELL), cy = (int) Math.floor(p.y / CELL);
            for (int ox = -1; ox <= 1; ox++)
                for (int oy = -1; oy <= 1; oy++) {
                    int j = pHead[hash(cx + ox, cy + oy)];
                    while (j >= 0) {
                        if (j > i) {
                            Particle q = ps.get(j);
                            if (q.body != p.body) collidePP(p, q, first);
                        }
                        j = pNext[j];
                    }
                }
            int e = sHead[hash(cx, cy)];
            stampSeq++;
            while (e >= 0) {
                Stick s = ss.get(eStick[e]);
                if (s.body != p.body && !s.dead && s.stamp != stampSeq) {
                    s.stamp = stampSeq;
                    collidePS(p, s, first);
                }
                e = eNext[e];
            }
        }
    }

    private void collidePP(Particle p, Particle q, boolean first) {
        float dx = p.x - q.x, dy = p.y - q.y;
        float min = p.r + q.r;
        float d2 = dx * dx + dy * dy;
        if (d2 >= min * min) return;
        float tot = p.invM + q.invM;
        if (tot == 0f) return;
        float d = (float) Math.sqrt(d2);
        float nx, ny;
        if (d < 1e-4f) { nx = 0; ny = -1; d = 0; } else { nx = dx / d; ny = dy / d; }
        float pen = min - d;
        float rvx = (p.x - p.px) - (q.x - q.px), rvy = (p.y - p.py) - (q.y - q.py);
        float closing = -(rvx * nx + rvy * ny) / H;
        float sp = p.invM / tot, sq = q.invM / tot;
        p.x += nx * pen * sp; p.y += ny * pen * sp;
        q.x -= nx * pen * sq; q.y -= ny * pen * sq;
        float tvx = rvx - nx * (rvx * nx + rvy * ny), tvy = rvy - ny * (rvx * nx + rvy * ny);
        p.px += tvx * FRICTION * sp; p.py += tvy * FRICTION * sp;
        q.px -= tvx * FRICTION * sq; q.py -= tvy * FRICTION * sq;
        if (first && closing > 450f) contact(p.body, q.body, closing, p, q, (p.x + q.x) * 0.5f, (p.y + q.y) * 0.5f);
    }

    private void collidePS(Particle p, Stick s, boolean first) {
        if (!s.collide) return;
        Particle a = s.a, b = s.b;
        float abx = b.x - a.x, aby = b.y - a.y;
        float len2 = abx * abx + aby * aby;
        float t = len2 > 1e-6f ? ((p.x - a.x) * abx + (p.y - a.y) * aby) / len2 : 0f;
        if (t <= 0.02f || t >= 0.98f) return;      // ends are handled by particle-particle
        float qx = a.x + abx * t, qy = a.y + aby * t;
        float dx = p.x - qx, dy = p.y - qy;
        float min = p.r + s.r;
        float d2 = dx * dx + dy * dy;
        if (d2 >= min * min) return;
        float d = (float) Math.sqrt(d2);
        float nx, ny;
        if (d < 1e-4f) {
            float l = (float) Math.sqrt(len2);
            nx = -aby / l; ny = abx / l; d = 0;
        } else { nx = dx / d; ny = dy / d; }
        float pen = min - d;
        float wa = (1 - t) * (1 - t) * a.invM, wb = t * t * b.invM, wp = p.invM;
        float tot = wa + wb + wp;
        if (tot == 0f) return;
        float vqx = (1 - t) * (a.x - a.px) + t * (b.x - b.px), vqy = (1 - t) * (a.y - a.py) + t * (b.y - b.py);
        float rvx = (p.x - p.px) - vqx, rvy = (p.y - p.py) - vqy;
        float rn = rvx * nx + rvy * ny;
        float closing = -rn / H;
        float cp = pen * wp / tot, ca = pen / tot;
        p.x += nx * cp; p.y += ny * cp;
        a.x -= nx * ca * (1 - t) * a.invM; a.y -= ny * ca * (1 - t) * a.invM;
        b.x -= nx * ca * t * b.invM; b.y -= ny * ca * t * b.invM;
        float tvx = rvx - nx * rn, tvy = rvy - ny * rn;
        float f = FRICTION / tot;
        p.px += tvx * f * wp; p.py += tvy * f * wp;
        a.px -= tvx * f * (1 - t) * a.invM; a.py -= tvy * f * (1 - t) * a.invM;
        b.px -= tvx * f * t * b.invM; b.py -= tvy * f * t * b.invM;
        if (first && closing > 450f) contact(p.body, s.body, closing, p, a, qx, qy);
    }

    private void bounds(boolean first) {
        final int n = ps.size();
        for (int i = 0; i < n; i++) {
            Particle p = ps.get(i);
            if (p.invM == 0f) continue;
            float r = p.r;
            if (p.y + r > floorY) {
                float vy = p.y - p.py;
                p.y = floorY - r;
                if (first) {
                    float vyUnits = vy / H;
                    if (vyUnits > 1400f) hit(p.body, (vyUnits - 1400f) * 0.012f, p.x, p.y, vyUnits > 2200f);
                    if (vy > 0) p.py = p.y + vy * Math.max(Math.max(0.04f, p.body.bounce), modBounce);
                    float vx = p.x - p.px;
                    float dv = FRICTION * gravity * Math.abs(gravMul) * H * H * 1.3f;
                    if (Math.abs(vx) <= dv) p.px = p.x; else p.px += vx > 0 ? dv : -dv;
                }
            }
            if (p.x - r < wallL) { float vx = p.x - p.px; p.x = wallL + r; if (first && vx < 0) p.px = p.x + vx * 0.2f; }
            else if (p.x + r > wallR) { float vx = p.x - p.px; p.x = wallR - r; if (first && vx > 0) p.px = p.x + vx * 0.2f; }
            if (p.y - r < ceilY) { p.y = ceilY + r; if (first) p.py = p.y; }
        }
    }

    // --------------------------------------------------------------------- contact events ---

    private void contact(Body a, Body b, float speed, Particle pa, Particle pb, float x, float y) {
        float ma = Math.min(3f, Math.max(0.4f, b.mass / 22f));
        float mb = Math.min(3f, Math.max(0.4f, a.mass / 22f));
        if (a.isHuman() && speed > 700f) hit(a, (speed - 700f) * 0.012f * ma, x, y, false);
        if (b.isHuman() && speed > 700f) hit(b, (speed - 700f) * 0.012f * mb, x, y, false);
        if (speed > 1700f) {
            if (a.explosive && a.fuse < 0) a.fuse = 0.05f;
            if (b.explosive && b.fuse < 0) b.fuse = 0.05f;
        }
        if (a.type == Body.MELON && speed > 850f) { smash(a); return; }
        if (b.type == Body.MELON && speed > 850f) { smash(b); return; }
        if (speed > 1000f && sfx != null) sfx.thud(Math.min(1f, speed / 2600f));
        if (a.sharp && speed > 420f) slash(a, b, x, y);
        if (b.sharp && speed > 420f) slash(b, a, x, y);
    }

    private void slash(Body blade, Body victim, float x, float y) {
        if (victim.type == Body.PLATFORM || victim.sharp) return;
        if (victim.type == Body.BALLOON) { pop(victim); return; }
        if (!victim.isHuman()) return;
        if (rnd.nextFloat() < (blade.type == Body.SWORD ? 0.5f : 0.3f)) {
            if (cutNear(victim, x, y, 46f)) return;
        }
        Particle nearest = nearestParticle(victim, x, y);
        if (nearest != null && nearest.bleed < 4f) nearest.bleed = 5f;
        hit(victim, 3f, x, y, false);
        fx.blood(x, y, rnd.nextFloat() - 0.5f, -0.6f, 4);
    }

    /** Picks droplet colours for a creature (red blood, green goo, oil, bone dust). */
    void setBlood(Body b) {
        switch (b.kind) {
            case Body.K_ZOMBIE: fx.bloodA = 0xFF5aa02a; fx.bloodB = 0xFF3b6e1a; break;
            case Body.K_ROBOT: fx.bloodA = 0xFF2b2b2f; fx.bloodB = 0xFF4a4a52; break;
            case Body.K_SKELETON: fx.bloodA = 0xFFe9e5d3; fx.bloodB = 0xFFc9c4ad; break;
            default: fx.bloodA = 0xFFb71414; fx.bloodB = 0xFF7d0c0c; break;
        }
    }

    /** Applies damage to a human; may kill. */
    void hit(Body b, float dmg, float x, float y, boolean gore) {
        if (!b.isHuman() || b.dead || dmg <= 0.2f) return;
        setBlood(b);
        if (b.kind == Body.K_ROBOT && dmg > 3f) fx.sparks(x, y, 5, 0xFFffd25a);
        if (b.hurtCd > 0.05f && dmg < 20f) dmg *= 0.3f;
        b.hurtCd = 0.12f;
        b.blood -= dmg;
        if (dmg > 6f || gore) fx.bloodBurst(x, y, Math.min(14, 2 + (int) (dmg * 0.5f)));
        if (b.blood <= 0) kill(b);
    }

    void kill(Body b) {
        if (b.dead) return;
        b.dead = true;
        b.blood = 0;
    }

    Particle nearestParticle(Body b, float x, float y) {
        Particle best = null;
        float bd = Float.MAX_VALUE;
        for (int i = 0; i < b.ps.size(); i++) {
            Particle p = b.ps.get(i);
            float dx = p.x - x, dy = p.y - y, d = dx * dx + dy * dy;
            if (d < bd) { bd = d; best = p; }
        }
        return best;
    }

    // ============================================================================ post-step ====

    private void post(float dt) {
        if (dt <= 0f) return;
        // tearing / dismemberment
        for (int i = 0; i < ss.size(); i++) {
            Stick s = ss.get(i);
            if (s.dead || s.tear <= 0f) continue;
            float dx = s.b.x - s.a.x, dy = s.b.y - s.a.y;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d - s.rest > s.tear) breakStick(s);
        }
        final int nb = bodies.size();
        for (int i = nb - 1; i >= 0; i--) {
            Body b = bodies.get(i);
            b.updateBounds();
            if (b.hurtCd > 0) b.hurtCd -= dt;
            if (b.isHuman()) updateHuman(b, dt);
            if (b.burning) updateBurning(b, dt);
            if (b.fuse >= 0f) {
                b.fuse -= dt;
                Particle fp = b.ps.size() > 0 ? b.ps.get(0) : null;
                if (fp != null && rnd.nextFloat() < 0.6f && b.type != Body.TNT && b.type != Body.XBARREL)
                    fx.add(Fx.SPARK, fp.x, fp.y - 6, fx.rr(120), -120f - fx.rf() * 160f, 0.3f, 2f, 0xFFffc83d);
                if (b.fuse <= 0f) {
                    float bx = b.cx, by = b.cy, R = b.blastR, pw = b.blastPow, dm = b.blastDmg;
                    boolean fire = b.type == Body.XBARREL;
                    removeBody(b);
                    explode(bx, by, R, pw, dm);
                    if (fire) for (int k = 0; k < 6; k++) fx.flame(bx + fx.rr(60), by + fx.rr(30), 18f);
                    continue;
                }
            }
        }
        spreadT += dt;
        if (spreadT > 0.25f) { spreadT = 0; spreadFire(); }
        // rockets
        for (int i = rockets.size() - 1; i >= 0; i--) {
            Projectile r = rockets.get(i);
            if (!stepRocket(r, dt)) rockets.remove(i);
        }
        // the very high up / loose balloon cleanup is handled by the ceiling
        if (dirty) rebuild();
    }

    private void updateHuman(Body b, float dt) {
        int wounds = 0;
        setBlood(b);
        for (int i = 0; i < b.ps.size(); i++) {
            Particle p = b.ps.get(i);
            if (p.bleed <= 0f) continue;
            p.bleed -= dt;
            wounds++;
            if (rnd.nextFloat() < 22f * dt) {
                float sp = 0.4f + rnd.nextFloat();
                fx.add(Fx.BLOOD, p.x, p.y, p.vx() * 0.2f + fx.rr(160) * sp, p.vy() * 0.2f - 60f + fx.rr(120), 1.6f, 1.8f + fx.rf() * 2f,
                        rnd.nextInt(3) == 0 ? fx.bloodB : fx.bloodA);
            }
        }
        if (wounds > 0 && !b.dead) {
            b.blood -= wounds * 1.1f * dt;
            if (b.blood <= 0) kill(b);
        }
    }

    // ------------------------------------------------------------------------------ fire -----

    void ignite(Body b) {
        if (b.burning || !(b.flammable || b.explosive)) return;
        b.burning = true;
        b.burnT = b.isHuman() ? 16f : (b.explosive ? 1f : 9f);
        if (b.explosive && b.fuse < 0f) b.fuse = 0.4f + rnd.nextFloat() * 0.5f;
    }

    private void updateBurning(Body b, float dt) {
        b.burnT -= dt;
        b.charT = Math.min(0.85f, b.charT + dt * 0.07f);
        int n = b.ps.size();
        if (n == 0) { b.burning = false; return; }
        float rate = b.isHuman() ? 70f : 45f;
        int cnt = (int) (rate * dt + rnd.nextFloat());
        for (int k = 0; k < cnt; k++) {
            Particle p = b.ps.get(rnd.nextInt(n));
            fx.flame(p.x, p.y, 7f + p.r * 0.5f);
        }
        if (rnd.nextFloat() < 14f * dt) {
            Particle p = b.ps.get(rnd.nextInt(n));
            fx.smoke(p.x, p.y - 8, 6f, 0xFF3c3c3c);
        }
        if (b.isHuman() && !b.dead) {
            b.blood -= 4.5f * dt;
            if (b.blood <= 0) kill(b);
        }
        if (b.burnT <= 0f) b.burning = false;
    }

    private void spreadFire() {
        final int n = bodies.size();
        for (int i = 0; i < n; i++) {
            Body a = bodies.get(i);
            if (!a.burning) continue;
            for (int j = 0; j < n; j++) {
                if (i == j) continue;
                Body b = bodies.get(j);
                if (b.burning || !(b.flammable || b.explosive)) continue;
                float dx = a.cx - b.cx, dy = a.cy - b.cy, rr = a.rad + b.rad + 14f;
                if (dx * dx + dy * dy > rr * rr) continue;
                // closest particle pair
                boolean near = false;
                for (int u = 0; u < a.ps.size() && !near; u++) {
                    Particle p = a.ps.get(u);
                    for (int v = 0; v < b.ps.size(); v++) {
                        Particle q = b.ps.get(v);
                        float ex = p.x - q.x, ey = p.y - q.y, rr2 = p.r + q.r + 16f;
                        if (ex * ex + ey * ey < rr2 * rr2) { near = true; break; }
                    }
                }
                if (near && rnd.nextFloat() < 0.28f) ignite(b);
            }
        }
    }

    // ------------------------------------------------------------------- breaking things ----

    void breakStick(Stick s) {
        if (s.dead) return;
        s.dead = true;
        dirty = true;
        Body b = s.body;
        if (b.isHuman()) {
            setBlood(b);
            if (b.kind == Body.K_ROBOT) fx.sparks((s.a.x + s.b.x) / 2, (s.a.y + s.b.y) / 2, 10, 0xFFffd25a);
            if (s.joint || s == b.neck) {
                s.capA = s.capB = true;
                s.a.bleed = Math.max(s.a.bleed, 9f + rnd.nextFloat() * 5f);
                s.b.bleed = Math.max(s.b.bleed, 9f + rnd.nextFloat() * 5f);
                fx.bloodBurst((s.a.x + s.b.x) / 2, (s.a.y + s.b.y) / 2, 16);
                if (sfx != null) sfx.splat();
                if (s == b.neck) kill(b);
                else if (!b.dead) hit(b, 18f, s.a.x, s.a.y, false);
            }
        } else {
            fx.debris((s.a.x + s.b.x) / 2, (s.a.y + s.b.y) / 2, 3, b.color, 260f);
        }
    }

    /** Breaks the joint (humans) or structural stick (props) closest to a point. */
    boolean cutNear(Body b, float x, float y, float maxDist) {
        Stick best = null;
        float bd = maxDist * maxDist;
        for (int i = 0; i < b.ss.size(); i++) {
            Stick s = b.ss.get(i);
            if (s.dead) continue;
            if (b.isHuman()) {
                if (!(s.joint || s == b.neck)) continue;
            } else if (s.kind == Stick.HIDDEN) continue;
            float mx = (s.a.x + s.b.x) * 0.5f, my = (s.a.y + s.b.y) * 0.5f;
            float dx = mx - x, dy = my - y, d = dx * dx + dy * dy;
            if (d < bd) { bd = d; best = s; }
        }
        if (best == null) return false;
        breakStick(best);
        return true;
    }

    void smash(Body b) {
        if (b.removed) return;
        int a0 = fx.bloodA, b0 = fx.bloodB;
        fx.bloodA = 0xFFe0384a; fx.bloodB = 0xFFb82234;
        fx.bloodBurst(b.cx, b.cy, 26);
        fx.bloodA = 0xFF3f9a3c; fx.bloodB = 0xFF2c6e2a;
        fx.bloodBurst(b.cx, b.cy, 10);
        fx.bloodA = a0; fx.bloodB = b0;
        fx.debris(b.cx, b.cy, 8, 0xFF3f9a3c, 300f);
        if (sfx != null) sfx.splat();
        removeBody(b);
    }

    void pop(Body b) {
        fx.sparks(b.cx, b.cy, 8, b.color);
        fx.debris(b.cx, b.cy, 6, b.color, 220f);
        if (sfx != null) sfx.pop();
        removeBody(b);
    }

    // ================================================================================ explosions ===

    void explode(float x, float y, float R, float pow, float dmg) {
        fx.explosion(x, y, R);
        if (sfx != null) sfx.boom(Math.min(1f, R / 600f));
        final float R2 = R * R;
        for (int i = 0; i < ps.size(); i++) {
            Particle p = ps.get(i);
            if (p.invM == 0f) continue;
            float dx = p.x - x, dy = p.y - y;
            float d2 = dx * dx + dy * dy;
            if (d2 > R2) continue;
            float d = (float) Math.sqrt(d2) + 1e-3f;
            float f = 1f - d / R;
            float kick = pow * f * (0.45f + p.invM * 1.2f);
            p.addVel(dx / d * kick, (dy / d - 0.25f) * kick);
        }
        for (int i = bodies.size() - 1; i >= 0; i--) {
            Body b = bodies.get(i);
            float dx = b.cx - x, dy = b.cy - y;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d - b.rad > R) continue;
            float f = Math.max(0f, 1f - Math.max(0f, d - b.rad * 0.6f) / R);
            if (f <= 0.02f) continue;
            if (b.type == Body.BALLOON) { if (f > 0.1f) { pop(b); } continue; }
            if (b.explosive && b.fuse < 0f && f > 0.15f) b.fuse = 0.06f + rnd.nextFloat() * 0.25f;
            if (b.flammable && f > 0.35f) ignite(b);
            if (b.isHuman()) {
                hit(b, dmg * f * f, b.cx, b.cy, f > 0.5f);
                if (f > 0.4f) {
                    for (int k = 0; k < b.ss.size(); k++) {
                        Stick s = b.ss.get(k);
                        if (s.dead || !(s.joint || s == b.neck)) continue;
                        if (rnd.nextFloat() < (f - 0.3f) * 1.25f) breakStick(s);
                    }
                }
                if (f > 0.7f && !b.dead) kill(b);
            } else if (b.type != Body.PLATFORM && f > 0.55f) {
                for (int k = 0; k < b.ss.size(); k++) {
                    Stick s = b.ss.get(k);
                    if (s.dead || s.kind == Stick.HIDDEN && !s.collide) continue;
                    if (rnd.nextFloat() < (f - 0.45f) * 1.1f) breakStick(s);
                }
            }
        }
    }

    /** Non-damaging radial push. */
    void shockwave(float x, float y, float R, float pow) {
        fx.ring(x, y, R);
        fx.add(Fx.GLOW, x, y, 0, 0, 0.18f, R * 0.25f, 0xFFbfe3ff);
        fx.shake = Math.max(fx.shake, 0.2f);
        for (int i = 0; i < ps.size(); i++) {
            Particle p = ps.get(i);
            if (p.invM == 0f) continue;
            float dx = p.x - x, dy = p.y - y;
            float d = (float) Math.sqrt(dx * dx + dy * dy) + 1e-3f;
            if (d > R) continue;
            float f = 1f - d / R;
            p.addVel(dx / d * pow * f, (dy / d - 0.3f) * pow * f);
        }
    }

    // ================================================================================ weapons ======

    /** Result of a ray cast. */
    static final class Hit {
        float t;                // distance along the ray
        Particle p;             // particle closest to the impact (null for the ground/walls)
        Body body;
        boolean head;
        float x, y;
    }
    private final ArrayList<Hit> hits = new ArrayList<>();
    private final float[] segOut = new float[2];

    private static float segSegDist(float ax, float ay, float bx, float by, float cx, float cy, float dx, float dy, float[] out) {
        float d1x = bx - ax, d1y = by - ay, d2x = dx - cx, d2y = dy - cy, rx = ax - cx, ry = ay - cy;
        float a = d1x * d1x + d1y * d1y, e = d2x * d2x + d2y * d2y, f = d2x * rx + d2y * ry;
        float s, t;
        if (a <= 1e-9f && e <= 1e-9f) { s = 0; t = 0; }
        else if (a <= 1e-9f) { s = 0; t = clamp(f / e); }
        else {
            float c = d1x * rx + d1y * ry;
            if (e <= 1e-9f) { t = 0; s = clamp(-c / a); }
            else {
                float b = d1x * d2x + d1y * d2y, den = a * e - b * b;
                s = den > 1e-9f ? clamp((b * f - c * e) / den) : 0f;
                t = (b * s + f) / e;
                if (t < 0) { t = 0; s = clamp(-c / a); }
                else if (t > 1) { t = 1; s = clamp((b - c) / a); }
            }
        }
        out[0] = s; out[1] = t;
        float px = ax + d1x * s - (cx + d2x * t), py = ay + d1y * s - (cy + d2y * t);
        return (float) Math.sqrt(px * px + py * py);
    }

    private static float clamp(float v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }

    /** Casts a ray and collects every body hit, nearest first. */
    ArrayList<Hit> ray(float x, float y, float dx, float dy, float len) {
        hits.clear();
        float ex = x + dx * len, ey = y + dy * len;
        for (int i = 0; i < ss.size(); i++) {
            Stick s = ss.get(i);
            if (s.dead || !s.collide) continue;
            float d = segSegDist(x, y, ex, ey, s.a.x, s.a.y, s.b.x, s.b.y, segOut);
            if (d < s.r) addHit(segOut[0] * len, s.body, segOut[1] < 0.5f ? s.a : s.b, false, x + dx * segOut[0] * len, y + dy * segOut[0] * len);
        }
        for (int i = 0; i < bodies.size(); i++) {
            Body b = bodies.get(i);
            if (!b.isHuman() || b.head == null) continue;
            Particle h = b.head;
            float fx0 = x - h.x, fy0 = y - h.y;
            float bq = fx0 * dx + fy0 * dy, c = fx0 * fx0 + fy0 * fy0 - h.r * h.r;
            float disc = bq * bq - c;
            if (disc < 0) continue;
            float t = -bq - (float) Math.sqrt(disc);
            if (t < 0 || t > len) continue;
            addHit(t, b, h, true, x + dx * t, y + dy * t);
        }
        // sort by t (tiny lists)
        for (int i = 1; i < hits.size(); i++) {
            Hit k = hits.get(i);
            int j = i - 1;
            while (j >= 0 && hits.get(j).t > k.t) { hits.set(j + 1, hits.get(j)); j--; }
            hits.set(j + 1, k);
        }
        return hits;
    }

    private void addHit(float t, Body b, Particle p, boolean head, float x, float y) {
        // merge hits on the same body: keep the nearest (heads win ties)
        for (int i = 0; i < hits.size(); i++) {
            Hit h = hits.get(i);
            if (h.body == b) {
                if (head && !h.head && t <= h.t + 18f) { h.t = t; h.p = p; h.head = true; h.x = x; h.y = y; }
                else if (t < h.t && !h.head) { h.t = t; h.p = p; h.x = x; h.y = y; }
                return;
            }
        }
        Hit h = new Hit();
        h.t = t; h.body = b; h.p = p; h.head = head; h.x = x; h.y = y;
        hits.add(h);
    }

    /** Fires a bullet. pierce = how many bodies it can go through. Returns the impact distance. */
    float bullet(float x, float y, float dx, float dy, float dmg, float impulse, int pierce) {
        float l = (float) Math.sqrt(dx * dx + dy * dy);
        dx /= l; dy /= l;
        float maxLen = 5200f;
        // ground / walls
        float tEnd = maxLen;
        boolean hitGround = false;
        if (dy > 1e-4f) { float t = (floorY - y) / dy; if (t > 0 && t < tEnd) { tEnd = t; hitGround = true; } }
        if (dx > 1e-4f) { float t = (wallR - x) / dx; if (t > 0 && t < tEnd) { tEnd = t; hitGround = true; } }
        if (dx < -1e-4f) { float t = (wallL - x) / dx; if (t > 0 && t < tEnd) { tEnd = t; hitGround = true; } }
        if (dy < -1e-4f) { float t = (ceilY - y) / dy; if (t > 0 && t < tEnd) { tEnd = t; hitGround = true; } }
        ArrayList<Hit> hs = ray(x, y, dx, dy, tEnd);
        int pierced = 0;
        float stopT = tEnd;
        for (int i = 0; i < hs.size(); i++) {
            Hit h = hs.get(i);
            applyBullet(h, dx, dy, dmg, impulse);
            if (++pierced >= pierce) { stopT = h.t; hitGround = false; break; }
        }
        float ix = x + dx * stopT, iy = y + dy * stopT;
        fx.add(Fx.GLOW, x, y, 0, 0, 0.05f, 14f, 0xFFfff0b0);
        tracer(x, y, ix, iy);
        if (hitGround) fx.sparks(ix, iy, 6, 0xFFffe08a);
        return stopT;
    }

    // short-lived tracer lines are drawn from these slots
    final float[] trX0 = new float[8], trY0 = new float[8], trX1 = new float[8], trY1 = new float[8], trT = new float[8];
    private int trNext;

    private void tracer(float x0, float y0, float x1, float y1) {
        int i = trNext;
        trNext = (trNext + 1) & 7;
        trX0[i] = x0; trY0[i] = y0; trX1[i] = x1; trY1[i] = y1; trT[i] = 0.07f;
    }

    void updateTracers(float dt) {
        for (int i = 0; i < 8; i++) if (trT[i] > 0) trT[i] -= dt;
    }

    private void applyBullet(Hit h, float dx, float dy, float dmg, float impulse) {
        Body b = h.body;
        if (b.type == Body.BALLOON) { pop(b); return; }
        if (b.explosive && b.fuse < 0f && b.type != Body.GRENADE && b.type != Body.DYNAMITE) b.fuse = 0.05f;
        if (h.p != null) h.p.addVel(dx * impulse, dy * impulse);
        if (b.isHuman()) {
            Particle p = h.p;
            setBlood(b);
            fx.blood(h.x, h.y, dx, dy, 7);
            if (p != null) p.bleed = Math.max(p.bleed, 6f);
            if (h.head && !b.dead) {
                hit(b, 60f, h.x, h.y, true);
                kill(b);
                fx.blood(h.x, h.y, dx, dy, 12);
            } else {
                hit(b, dmg, h.x, h.y, false);
            }
            if (sfx != null) sfx.splat();
        } else {
            fx.sparks(h.x, h.y, 5, b.type == Body.CRATE || b.type == Body.PLANK ? 0xFFe8c48a : 0xFFffe08a);
            if (b.type == Body.CRATE || b.type == Body.PLANK) fx.debris(h.x, h.y, 2, b.color, 180f);
        }
    }

    void shotgun(float x, float y, float dx, float dy) {
        float base = (float) Math.atan2(dy, dx);
        for (int i = 0; i < 9; i++) {
            float a = base + (rnd.nextFloat() - 0.5f) * 0.28f;
            bullet(x, y, (float) Math.cos(a), (float) Math.sin(a), 9f, 420f, 1);
        }
    }

    void launchRocket(float x, float y, float dx, float dy) {
        if (rockets.size() > 8) return;
        float l = (float) Math.sqrt(dx * dx + dy * dy);
        Projectile r = new Projectile();
        r.x = x; r.y = y; r.vx = dx / l * 1700f; r.vy = dy / l * 1700f; r.life = 4f;
        rockets.add(r);
        if (sfx != null) sfx.launch();
    }

    private boolean stepRocket(Projectile r, float dt) {
        int sub = 4;
        float h = dt / sub;
        for (int k = 0; k < sub; k++) {
            r.x += r.vx * h; r.y += r.vy * h;
            fx.smoke(r.x - r.vx * 0.01f, r.y - r.vy * 0.01f, 5f, 0xFF777777);
            if (rnd.nextFloat() < 0.8f) fx.flame(r.x - r.vx * 0.008f, r.y - r.vy * 0.008f, 5f);
            boolean boom = r.y > floorY - 4 || r.x < wallL || r.x > wallR || r.y < ceilY;
            if (!boom) {
                for (int i = 0; i < ss.size() && !boom; i++) {
                    Stick s = ss.get(i);
                    if (s.dead || !s.collide) continue;
                    float abx = s.b.x - s.a.x, aby = s.b.y - s.a.y, l2 = abx * abx + aby * aby;
                    float t = l2 > 1e-6f ? clamp(((r.x - s.a.x) * abx + (r.y - s.a.y) * aby) / l2) : 0f;
                    float qx = s.a.x + abx * t - r.x, qy = s.a.y + aby * t - r.y;
                    if (qx * qx + qy * qy < (s.r + 6) * (s.r + 6)) boom = true;
                }
                for (int i = 0; i < bodies.size() && !boom; i++) {
                    Body b = bodies.get(i);
                    if (b.isHuman() && b.head != null) {
                        float qx = b.head.x - r.x, qy = b.head.y - r.y;
                        if (qx * qx + qy * qy < (b.head.r + 6) * (b.head.r + 6)) boom = true;
                    }
                }
            }
            if (boom) {
                explode(r.x, Math.min(r.y, floorY - 4), 330f, 5600f, 95f);
                return false;
            }
        }
        r.life -= dt;
        if (r.life <= 0) { explode(r.x, r.y, 260f, 4200f, 60f); return false; }
        return true;
    }

    void lightning(float x) {
        float topY = Math.max(ceilY, floorY - 1500f);
        ArrayList<Hit> hs = ray(x, topY, 0, 1, floorY - topY);
        float ty = floorY, tx = x;
        if (!hs.isEmpty()) {
            Hit h = hs.get(0);
            ty = h.y; tx = h.x;
            Body b = h.body;
            if (b.type == Body.BALLOON) { pop(b); }
            else {
                if (h.p != null) h.p.addVel(rnd.nextFloat() * 400f - 200f, -600f);
                if (b.isHuman()) {
                    hit(b, 55f, tx, ty, true);
                    if (!b.dead && rnd.nextFloat() < 0.5f) kill(b);
                }
                ignite(b);
                if (b.explosive && b.fuse < 0f) b.fuse = 0.05f;
            }
        } else {
            fx.stain(tx, 22f, 0xCC111111);
        }
        fx.lightning(x, topY, tx, ty);
        fx.sparks(tx, ty, 14, 0xFFcfe6ff);
        if (sfx != null) sfx.zap();
    }

    // ================================================================================ picking ========

    /** Closest particle to (x,y) whose collision shape is within maxDist; null if none. */
    Particle pickParticle(float x, float y, float maxDist) {
        Particle best = null;
        float bd = maxDist;
        for (int i = 0; i < ss.size(); i++) {
            Stick s = ss.get(i);
            if (s.dead || s.kind == Stick.HIDDEN && !s.collide) continue;
            if (s.joint) continue;
            float abx = s.b.x - s.a.x, aby = s.b.y - s.a.y, l2 = abx * abx + aby * aby;
            float t = l2 > 1e-6f ? clamp(((x - s.a.x) * abx + (y - s.a.y) * aby) / l2) : 0f;
            float qx = s.a.x + abx * t - x, qy = s.a.y + aby * t - y;
            float d = (float) Math.sqrt(qx * qx + qy * qy) - s.r;
            if (d < bd) { bd = d; best = t < 0.5f ? s.a : s.b; }
        }
        for (int i = 0; i < bodies.size(); i++) {
            Body b = bodies.get(i);
            if (b.isHuman() && b.head != null) {
                float qx = b.head.x - x, qy = b.head.y - y;
                float d = (float) Math.sqrt(qx * qx + qy * qy) - b.head.r;
                if (d < bd) { bd = d; best = b.head; }
            }
        }
        return best;
    }

    Body pickBody(float x, float y, float maxDist) {
        Particle p = pickParticle(x, y, maxDist);
        return p == null ? null : p.body;
    }
}

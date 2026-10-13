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
    Body rayIgnore;           // the shooter: its own bullets go through it
    float rumble;             // accumulated 'oomph' this frame (haptics, music intensity)
    final Stats stats = new Stats();

    /** Counters used by achievements. */
    static final class Stats {
        int explosions, joints, headshots, infections, melons, thrusts, links, recentBooms;
        int revives, heals, fires, machinesDown, aiKills;
        float recentT;
    }

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
    private Body[] snap = new Body[64];
    private float spreadT, bleedAcc;
    int frame;
    private int stampSeq;

    // broadphase
    private final int[] pHead = new int[GRID];
    private int[] pNext = new int[MAX_PARTICLES + 8];
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
        int n = 0;   // count live bodies, not the (possibly stale) flattened list
        for (int i = 0; i < bodies.size(); i++) n += bodies.get(i).ps.size();
        return bodies.size() < MAX_BODIES && n + b.ps.size() < MAX_PARTICLES - 60;
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

    void rebuildNow() { rebuild(); }

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
        Brain.muscles(this);
        machines();
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
        if (pNext.length < n) pNext = new int[n + 256];
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
        if (ny < -0.4f) p.groundFrame = frame; else if (ny > 0.4f) q.groundFrame = frame;
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
        if (ny < -0.4f) p.groundFrame = frame;
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
                p.groundFrame = frame;
                if (first) {
                    float vyUnits = vy / H;
                    if (vyUnits > 1400f) hit(p.body, (vyUnits - 1400f) * 0.012f, p.x, p.y, vyUnits > 2200f);
                    if (vy > 0) p.py = p.y + vy * Math.max(Math.max(0.04f, p.body.bounce), modBounce);
                    float vx = p.x - p.px;
                    float dv = FRICTION * gravity * Math.abs(gravMul) * H * H * 1.3f;
                    if (p.body.type == Body.WHEEL) p.px = p.x;          // tyres grip: roll, don't skid
                    else if (Math.abs(vx) <= dv) p.px = p.x; else p.px += vx > 0 ? dv : -dv;
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
        // people bumping into people (or limbs into their own neighbours) needs a much harder hit to hurt
        float th = a.isHuman() && b.isHuman() ? 1500f : 700f;
        if (a.isHuman() && speed > th) hit(a, Math.min(30f, (speed - th) * 0.012f * ma), x, y, false);
        if (b.isHuman() && speed > th) hit(b, Math.min(30f, (speed - th) * 0.012f * mb), x, y, false);
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
        b.flashT = 0.14f;
        b.blood -= dmg;
        damagePart(b, dmg, x, y);
        if (b.adrenaline > 0f && dmg < 30f) dmg *= 0.25f;     // adrenaline: hard to knock out
        if (dmg > 9f) { b.ko = Math.max(b.ko, Math.min(6f, 1.2f + dmg * 0.07f) * (b.kind == Body.K_ROBOT ? 0.5f : 1f)); b.tone = 0f; }
        if (dmg > 6f || gore) fx.bloodBurst(x, y, Math.min(14, 2 + (int) (dmg * 0.5f)));
        if (b.blood <= 0) kill(b);
    }

    /** The part nearest to the impact takes the damage; enough of it breaks the bone. */
    void damagePart(Body b, float dmg, float x, float y) {
        if (b.head != null) {
            float hx = b.head.x - x, hy = b.head.y - y;
            if (hx * hx + hy * hy < (b.head.r + 6f) * (b.head.r + 6f)) {
                b.headDmg = Math.min(100f, b.headDmg + dmg * 1.4f);
                if (b.headDmg >= 100f) kill(b);
                return;
            }
        }
        Stick[] parts = b.parts();
        if (parts == null) return;
        Stick best = null;
        float bd = Float.MAX_VALUE;
        for (Stick s : parts) {
            float abx = s.b.x - s.a.x, aby = s.b.y - s.a.y, l2 = abx * abx + aby * aby;
            float t = l2 > 1e-6f ? clamp(((x - s.a.x) * abx + (y - s.a.y) * aby) / l2) : 0f;
            float qx = s.a.x + abx * t - x, qy = s.a.y + aby * t - y, d = qx * qx + qy * qy;
            if (d < bd) { bd = d; best = s; }
        }
        if (best == null) return;
        best.damage = Math.min(100f, best.damage + dmg * 1.6f);
        if (best.damage >= 70f && !best.fractured && best != b.torso) fracture(b, best);
    }

    void fracture(Body b, Stick part) {
        part.fractured = true;
        if (part.limit != null) part.limit.disabled = true;
        float mx = (part.a.x + part.b.x) / 2, my = (part.a.y + part.b.y) / 2;
        if (b.kind != Body.K_ROBOT) fx.debris(mx, my, 3, 0xFFf0ead6, 160f);
        else fx.sparks(mx, my, 6, 0xFFffd25a);
        if (sfx != null) sfx.crunch();
        if (b.legsBroken()) { b.ko = Math.max(b.ko, 0.5f); b.tone = 0f; }
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
        // iterate over a snapshot: explosions below may remove any number of bodies
        final int nb = bodies.size();
        if (snap.length < nb) snap = new Body[nb * 2];
        bodies.toArray(snap);
        for (int i = nb - 1; i >= 0; i--) {
            Body b = snap[i];
            snap[i] = null;
            if (b.removed) continue;
            b.updateBounds();
            if (b.hurtCd > 0) b.hurtCd -= dt;
            if (b.flashT > 0) b.flashT -= dt;
            if (b.isHuman()) { updateHuman(b, dt); updateAcid(b, dt); if (b.adrenaline > 0f) b.adrenaline -= dt; Ai.tick(this, b, dt); Brain.think(this, b, dt); }
            else if (b.machine()) Ai.machineThink(this, b, dt);
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
        if (stats.recentT > 0f) { stats.recentT -= dt; if (stats.recentT <= 0f) stats.recentBooms = 0; }
        for (int i = 0; i < bodies.size(); i++) {      // thruster flames
            Body b = bodies.get(i);
            if (b.type != Body.THRUSTER || !b.thrust || b.ps.size() != 2) continue;
            Particle a = b.ps.get(0), z = b.ps.get(1);
            float dx = a.x - z.x, dy = a.y - z.y, l = (float) Math.sqrt(dx * dx + dy * dy) + 1e-3f;
            for (int k = 0; k < 3; k++)
                fx.add(Fx.FLAME, a.x + dx / l * 8, a.y + dy / l * 8, dx / l * 500 + fx.rr(60), dy / l * 500 + fx.rr(60), 0.25f, 9f, 0);
            if (rnd.nextFloat() < 0.4f) fx.smoke(a.x + dx / l * 20, a.y + dy / l * 20, 7f, 0xFF888888);
        }
        guns(dt);
        stickyC4();
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
        if (b.burning || !(b.flammable || b.explosive) || b.type == Body.C4) return;
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
                rumble += 0.18f;
                stats.joints++;
                if (sfx != null) sfx.splat();
                if (s == b.neck) kill(b);
                else if (!b.dead) hit(b, 18f, s.a.x, s.a.y, false);
            }
        } else {
            fx.debris((s.a.x + s.b.x) / 2, (s.a.y + s.b.y) / 2, 3, b.color, 260f);
            if (sfx != null && s.kind != Stick.HIDDEN) sfx.crunch();
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
        stats.melons++;
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

    // ----------------------------------------------------------------------- machines -----

    /** Motor wheels spin toward a target speed; thrusters push along their axis. Once per step. */
    private void machines() {
        for (int i = 0; i < bodies.size(); i++) {
            Body b = bodies.get(i);
            if (b.type == Body.DRONE) { Ai.fly(this, b); continue; }
            if (b.type == Body.TURRET) { Ai.servo(this, b); continue; }
            if (b.type == Body.WHEEL && b.motor != 0 && b.ps.size() > 3) {
                Particle c = b.ps.get(0);
                float w = 0f, rr = 0f;
                int n = b.ps.size();
                for (int k = 1; k < n; k++) {
                    Particle p = b.ps.get(k);
                    float rx = p.x - c.x, ry = p.y - c.y;
                    float vx = (p.x - p.px) - (c.x - c.px), vy = (p.y - p.py) - (c.y - c.py);
                    w += (rx * vy - ry * vx);
                    rr += rx * rx + ry * ry;
                }
                if (rr < 1f) continue;
                float omega = w / rr;                        // radians per step
                float target = b.motor * 9.5f * H;           // ~9.5 rad/s
                float dw = Math.max(-0.0016f, Math.min(0.0016f, (target - omega) * 0.25f));
                for (int k = 1; k < n; k++) {
                    Particle p = b.ps.get(k);
                    float rx = p.x - c.x, ry = p.y - c.y;
                    p.px += ry * dw;           // tangential velocity change = dw x r
                    p.py -= rx * dw;
                }
            } else if (b.type == Body.THRUSTER && b.thrust && b.ps.size() == 2) {
                Particle a = b.ps.get(0), z = b.ps.get(1);
                float dx = z.x - a.x, dy = z.y - a.y;
                float l = (float) Math.sqrt(dx * dx + dy * dy);
                if (l < 1f) continue;
                float ux = dx / l, uy = dy / l;
                float vAlong = (a.vx() + z.vx()) * 0.5f * ux + (a.vy() + z.vy()) * 0.5f * uy;
                if (vAlong > 1600f) continue;                 // top speed, so it stays controllable
                float acc = 55000f * H * H;                    // ~130k force units: can lift a person
                a.px -= ux * acc; a.py -= uy * acc;
                z.px -= ux * acc; z.py -= uy * acc;
            }
        }
    }

    /**
     * Joins two bodies. Axle (rigid = false): the pivot particle is pinned to the other body with a
     * triangle of two sticks, so that body holds the pivot fixed and the pivot's own body spins around it.
     * Weld (rigid = true): three sticks in a triangle, nothing can rotate.
     */
    Stick link(Particle a, Particle b, boolean rigid) {
        if (a == b || a.dead || b.dead || a.body == b.body) return null;
        // the centre of a wheel/ball is always the pivot
        if (!rigid && isCentre(b) && !isCentre(a)) { Particle t = a; a = b; b = t; }
        Particle b2 = triangle(b.body, a, b);
        Stick s = mk(a, b, Stick.FILL);
        if (b2 != null) mk(a, b2, Stick.HIDDEN);
        if (rigid) {
            // two points on each side, all four cross-linked: a braced quadrilateral can't bend
            Particle a2 = triangle(a.body, b, a);
            if (a2 != null) {
                mk(a2, b, Stick.HIDDEN);
                if (b2 != null) mk(a2, b2, Stick.HIDDEN);
            }
        }
        stats.links++;
        dirty = true;
        if (a.body.motorized && a.body.motor == 0) a.body.motor = 1;
        if (b.body.motorized && b.body.motor == 0) b.body.motor = 1;
        return s;
    }

    private Stick mk(Particle a, Particle b, int kind) {
        Stick s = new Stick(a.body, a, b, -1, 2.2f);
        s.kind = kind;
        s.color = 0xFFb9c0ca;
        s.tear = 160f;
        s.weld = true;
        return s;
    }

    /** Third corner for a stiff triangle: a particle of `body` near `b`, as far as possible from the line a-b. */
    private static Particle triangle(Body body, Particle a, Particle b) {
        Particle best = null;
        float bestArea = 0f;
        float ux = b.x - a.x, uy = b.y - a.y;
        for (int i = 0; i < body.ps.size(); i++) {
            Particle p = body.ps.get(i);
            if (p == b) continue;
            float dx = p.x - b.x, dy = p.y - b.y;
            if (dx * dx + dy * dy > 90f * 90f) continue;
            float area = Math.abs(ux * (p.y - a.y) - uy * (p.x - a.x));
            if (area > bestArea) { bestArea = area; best = p; }
        }
        return best;
    }

    static boolean isRound(Body b) {
        return b.type == Body.WHEEL || b.type == Body.BALL || b.type == Body.BOWLING || b.type == Body.MELON || b.type == Body.BALLOON;
    }

    private static boolean isCentre(Particle p) {
        Body b = p.body;
        return (b.type == Body.WHEEL || b.type == Body.BALL || b.type == Body.BOWLING || b.type == Body.MELON) && !b.ps.isEmpty() && b.ps.get(0) == p;
    }

    private static Particle nearestOther(Body body, Particle not, float x, float y) {
        Particle best = null;
        float bd = Float.MAX_VALUE;
        for (int i = 0; i < body.ps.size(); i++) {
            Particle p = body.ps.get(i);
            if (p == not) continue;
            float dx = p.x - x, dy = p.y - y, d = dx * dx + dy * dy;
            if (d < bd) { bd = d; best = p; }
        }
        return best;
    }

    // ------------------------------------------------------------------- guns & C4 -----------

    private void guns(float dt) {
        for (int i = 0; i < bodies.size(); i++) {
            Body b = bodies.get(i);
            if (b.type != Body.GUN || !b.firing) continue;
            b.fireCd -= dt;
            if (b.fireCd <= 0f) { b.fireCd = 0.075f; fireGun(b); }
        }
    }

    /** One shot along the barrel, with recoil and muzzle flash. */
    void fireGun(Body g) {
        if (g.ps.size() < 2) return;
        Particle a = g.ps.get(0), z = g.ps.get(1);
        float dx = z.x - a.x, dy = z.y - a.y, l = (float) Math.sqrt(dx * dx + dy * dy);
        if (l < 1f) return;
        dx /= l; dy /= l;
        float ox = z.x + dx * 9f, oy = z.y + dy * 9f;
        switch (g.gunKind) {
            case 0: bullet(ox, oy, dx, dy, 24f, 950f, 1); break;
            case 1: bullet(ox, oy, dx, dy, 40f, 1600f, 3); break;
            default: {
                float ang = (float) Math.atan2(dy, dx) + (rnd.nextFloat() - 0.5f) * 0.07f;
                bullet(ox, oy, (float) Math.cos(ang), (float) Math.sin(ang), 14f, 600f, 1);
            }
        }
        float rec = g.gunKind == 1 ? 520f : (g.gunKind == 2 ? 150f : 300f);
        a.addVel(-dx * rec, -dy * rec - 40f);
        z.addVel(-dx * rec * 0.7f, -dy * rec * 0.7f - 90f);
        fx.add(Fx.GLOW, ox, oy, 0, 0, 0.06f, 16f, 0xFFfff0b0);
        fx.sparks(ox, oy, 3, 0xFFffd25a);
        if (sfx != null) sfx.shot();
        rumble += 0.06f;
    }

    /** C4 sticks to the first thing it touches. */
    private void stickyC4() {
        for (int i = 0; i < bodies.size(); i++) {
            Body c = bodies.get(i);
            if (c.type != Body.C4 || c.stuck || c.ps.size() < 2) continue;
            outer:
            for (int k = 0; k < c.ps.size(); k++) {
                Particle p = c.ps.get(k);
                for (int j = 0; j < bodies.size(); j++) {
                    Body o = bodies.get(j);
                    if (o == c || o.type == Body.C4) continue;
                    float dx = o.cx - p.x, dy = o.cy - p.y;
                    if (dx * dx + dy * dy > (o.rad + 20f) * (o.rad + 20f)) continue;
                    for (int m = 0; m < o.ps.size(); m++) {
                        Particle q = o.ps.get(m);
                        float ex = q.x - p.x, ey = q.y - p.y, rr = q.r + p.r + 4f;
                        if (ex * ex + ey * ey < rr * rr) {
                            link(p, q, true);
                            c.stuck = true;
                            break outer;
                        }
                    }
                }
            }
        }
    }

    /** Detonates every C4 in the room (the remote detonator). */
    int detonateC4() {
        int n = 0;
        for (int i = 0; i < bodies.size(); i++) {
            Body b = bodies.get(i);
            if (b.type == Body.C4 && b.fuse < 0f) { b.fuse = 0.05f + n * 0.08f; n++; }
        }
        return n;
    }

    // ------------------------------------------------------------------------- syringes ----

    /** Applies a syringe. Returns a short message for the player. */
    String inject(Body b, int kind) {
        if (!b.isHuman()) return "Las jeringas solo funcionan con personas";
        if (b.kind == Body.K_ROBOT || b.kind == Body.K_SKELETON) return "No hace efecto: no tiene sangre";
        Particle at = b.chest;
        int col;
        String msg;
        switch (kind) {
            case Tools.SYR_HEAL:
                heal(b);
                col = 0xFF6dff8a; msg = "Curado";
                break;
            case Tools.SYR_ADRENALINE:
                if (b.dead) return "Está muerto: usa Resurrección";
                b.adrenaline = 25f; b.ko = 0f; b.tone = Math.max(b.tone, 0.6f);
                b.blood = Math.min(b.maxBlood, b.blood + 20f);
                col = 0xFFffd23f; msg = "¡Adrenalina!";
                break;
            case Tools.SYR_LIFE:
                if (!b.dead) return "Todavía está vivo";
                if (b.neck.dead) return "Sin cabeza no hay resurrección";
                heal(b);
                b.dead = false; b.ko = 2.5f; b.tone = 0f;
                col = 0xFF7fd1ff; msg = "¡Ha vuelto a la vida!";
                break;
            case Tools.SYR_ZOMBIE:
                if (b.kind == Body.K_ZOMBIE) return "Ya es un zombi";
                if (b.neck.dead) return "Sin cabeza no hay zombi";
                b.infected = 2.5f; b.bitten = true;
                col = 0xFF5aa02a; msg = "Infectado... en unos segundos";
                break;
            case Tools.SYR_ACID:
                b.acid = 9f;
                col = 0xFFb6ff3a; msg = "Ácido en las venas";
                break;
            default:
                if (b.dead) return "Está muerto";
                b.ko = 25f; b.tone = 0f; b.adrenaline = 0f;
                col = 0xFF9a8cff; msg = "Zzz...";
        }
        for (int i = 0; i < 10; i++)
            fx.add(Fx.SPARK, at.x, at.y, fx.rr(180), -80f - fx.rf() * 160f, 0.5f, 2.2f, col);
        fx.add(Fx.GLOW, at.x, at.y, 0, 0, 0.3f, 26f, col);
        if (sfx != null) sfx.pop();
        return msg;
    }

    void heal(Body b) {
        b.blood = b.maxBlood;
        b.headDmg = 0f;
        b.burning = false; b.burnT = 0f; b.acid = 0f; b.ko = 0f;
        b.infected = -1f; b.bitten = false;
        for (Particle p : b.ps) p.bleed = 0f;
        Stick[] parts = b.parts();
        if (parts != null) for (Stick s : parts) { s.damage = 0f; s.fractured = false; if (s.limit != null) s.limit.disabled = false; }
    }

    /** Acid eats a person: damage, green fumes, limbs falling off, and finally only bones are left. */
    void updateAcid(Body b, float dt) {
        if (b.acid <= 0f) return;
        b.acid -= dt;
        b.blood -= 9f * dt;
        b.charT = Math.min(0.55f, b.charT + dt * 0.05f);
        if (rnd.nextFloat() < dt * 10f) {
            Particle p = b.ps.get(rnd.nextInt(b.ps.size()));
            fx.smoke(p.x, p.y, 7f, 0xFF8fd14a);
        }
        if (rnd.nextFloat() < dt * 0.6f) {
            Stick[] js = {b.jElF, b.jElB, b.jKneeF, b.jKneeB, b.jShF, b.jShB};
            Stick s = js[rnd.nextInt(js.length)];
            if (!s.dead) breakStick(s);
        }
        if (b.blood <= 0f && !b.dead) kill(b);
        if (b.acid <= 0f && b.dead) skeletonize(b);
    }

    private void skeletonize(Body b) {
        b.kind = Body.K_SKELETON;
        b.skin = b.shirt = b.hair = 0xFFe9e5d3; b.pants = 0xFFe3dfcc;
        b.charT = 0f; b.burning = false;
        SceneIO.recolor(b);
        for (Particle p : b.ps) p.bleed = 0f;
        Stick[] bones = {b.uarmF, b.farmF, b.uarmB, b.farmB, b.thighF, b.shinF, b.thighB, b.shinB};
        for (Stick s : bones) { s.r *= 0.55f; s.a.r *= 0.55f; s.b.r *= 0.55f; }
        fx.smoke(b.cx, b.cy, 16f, 0xFF8fd14a);
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
        rumble += Math.min(1.2f, R / 450f);
        stats.explosions++;
        stats.recentBooms++;
        stats.recentT = 2f;
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
        final int nb = bodies.size();
        Body[] list = bodies.toArray(new Body[nb]);
        for (int i = nb - 1; i >= 0; i--) {
            Body b = list[i];
            if (b.removed) continue;
            float dx = b.cx - x, dy = b.cy - y;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d - b.rad > R) continue;
            float f = Math.max(0f, 1f - Math.max(0f, d - b.rad * 0.6f) / R);
            if (f <= 0.02f) continue;
            if (b.type == Body.BALLOON) { if (f > 0.1f) { pop(b); } continue; }
            if (b.explosive && b.fuse < 0f && f > 0.15f) b.fuse = 0.06f + rnd.nextFloat() * 0.25f;
            if (b.flammable && f > 0.35f) ignite(b);
            if (b.machine()) Ai.damage(this, b, dmg * f * 1.5f);
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
            if (s.dead || !s.collide || s.body == rayIgnore) continue;
            float d = segSegDist(x, y, ex, ey, s.a.x, s.a.y, s.b.x, s.b.y, segOut);
            if (d < s.r) addHit(segOut[0] * len, s.body, segOut[1] < 0.5f ? s.a : s.b, false, x + dx * segOut[0] * len, y + dy * segOut[0] * len);
        }
        for (int i = 0; i < bodies.size(); i++) {
            Body b = bodies.get(i);
            if (!b.isHuman() || b.head == null || b == rayIgnore) continue;
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
        if (b.type == Body.MELON) { smash(b); return; }
        if (b.machine()) Ai.damage(this, b, dmg);
        if (b.explosive && b.fuse < 0f && b.type != Body.GRENADE && b.type != Body.DYNAMITE) b.fuse = 0.05f;
        if (h.p != null) h.p.addVel(dx * impulse, dy * impulse);
        if (b.isHuman()) {
            Particle p = h.p;
            setBlood(b);
            fx.blood(h.x, h.y, dx, dy, 7);
            if (p != null) p.bleed = Math.max(p.bleed, 6f);
            if (h.head && !b.dead) {
                rumble += 0.25f;
                stats.headshots++;
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
        rumble += 0.5f;
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
            if (s.joint || s.weld) continue;
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
            } else if (isRound(b) && !b.ps.isEmpty()) {
                // the inside of a ball or wheel counts too (its ring is the only collider)
                Particle c = b.ps.get(0);
                float qx = c.x - x, qy = c.y - y;
                float d = (float) Math.sqrt(qx * qx + qy * qy) - b.param - 4f;
                if (d < bd) { bd = d; best = c; }
            }
        }
        return best;
    }

    Body pickBody(float x, float y, float maxDist) {
        Particle p = pickParticle(x, y, maxDist);
        return p == null ? null : p.body;
    }
}

package com.frameboost.ragdoll;

/**
 * Sandbox machines and hazards: circular saw, spikes that impale, fan, hydraulic crusher, Tesla coil
 * (electric shocks), and liquid nitrogen (frozen people turn rigid and shatter).
 * Machines are fixed in place: their bodies are frozen and the moving parts are driven kinematically
 * (position and velocity set by hand, so the contact solver pushes and drags what they touch).
 */
final class Machines {
    private Machines() { }

    static final int IMPALE_TAG = 78;
    static final float SAW_SPIN = 32f;            // rad/s
    static final float FAN_RANGE = 720f, TESLA_RANGE = 380f;

    static void update(World w, Prop q, float dt) {
        for (RB b : q.bodies) if (!b.frozen) { b.vx = b.vy = b.w = 0f; b.setFrozen(true); }
        switch (q.type) {
            case Items.SAW: saw(w, q, dt); break;
            case Items.FAN: fan(w, q, dt); break;
            case Items.CRUSHER: crusher(w, q, dt); break;
            case Items.TESLA: tesla(w, q, dt); break;
            default: break;
        }
    }

    // ------------------------------------------------------------------------------- saw ---

    /** Distance from a point to the core segment of a shape, minus its radius (negative = inside). */
    static float dist(Shape s, float px, float py) {
        if (s.type == Shape.CIRCLE) return (float) Math.hypot(px - s.cx, py - s.cy) - s.r;
        float hl = s.type == Shape.CAPSULE ? s.hl : 0f;
        float t = (px - s.cx) * s.ax + (py - s.cy) * s.ay;
        t = Math.max(-hl, Math.min(hl, t));
        float qx = s.cx + s.ax * t, qy = s.cy + s.ay * t;
        float r = s.type == Shape.CAPSULE ? s.r : Math.min(s.hl, s.hh);
        return (float) Math.hypot(px - qx, py - qy) - r;
    }

    private static void saw(World w, Prop q, float dt) {
        RB blade = q.bodies.get(1);
        if (!q.on) { blade.w = 0f; return; }
        blade.w = SAW_SPIN;
        blade.setAngle(blade.a + SAW_SPIN * dt);
        float r = q.param, bx = blade.x, by = blade.y;
        if (w.rnd.nextFloat() < dt * 2f) w.fx.add(Fx.SPARK, bx, by - r, w.fx.rr(40), -60f, 0.2f, 1.6f, 0xFFffe08a);
        for (int i = 0; i < w.ents.size(); i++) {
            Ent e = w.ents.get(i);
            if (e == q || e.removed) continue;
            if (Math.abs(e.cx() - bx) > r + e.radius() + 40f) continue;
            if (e instanceof Person) {
                Person p = (Person) e;
                for (int k = 0; k < Person.NPARTS; k++) {
                    if (p.gibbed[k] || p.hitCd[k] > 0f) continue;
                    Shape s = p.rb[k].shapes.get(0);
                    float d = dist(s, bx, by) - r;
                    if (d > 2f) continue;
                    // contact point on the blade rim towards the part
                    float dx = s.cx - bx, dy = s.cy - by, l = (float) Math.hypot(dx, dy) + 1e-3f;
                    float cx = bx + dx / l * r, cy = by + dy / l * r;
                    p.hitCd[k] = 0.1f;
                    w.wound(p, k, Person.W_CUT, cx, cy, -dy / l, dx / l, 7f);
                    w.hurt(p, k, 16f, cx, cy, -dy / l, dx / l);
                    // the blade flings the part along its rim
                    RB b = p.rb[k];
                    b.applyImpulse((-dy / l * 260f + dx / l * 120f) * b.m, (dx / l * 260f + dy / l * 120f) * b.m, cx, cy);
                    w.fx.sparks(cx, cy, 2, 0xFFffd25a);
                    int j = Person.PART_JOINT[k];
                    if (j >= 0 && j != Person.WAIST && !p.severed[j] && w.rnd.nextFloat() < 0.3f) w.sever(p, j, cx, cy);
                    if (w.sfx != null) w.sfx.crunch();
                }
            } else if (e instanceof Prop) {
                Prop o = (Prop) e;
                if (o.animal()) {
                    if (o.flash <= 0f && dist(o.main().shapes.get(0), bx, by) - r < 3f) { w.hurtAnimal(o, 18f, bx, by - r, 0f, -1f); o.flash = 0.1f; }
                } else if (World.breakable(o) && dist(o.main().shapes.get(0), bx, by) - r < 3f) {
                    w.wear(o, 80f * dt, true);
                    if (w.rnd.nextFloat() < dt * 20f) w.fx.debris(bx, by - r, 1, o.color, 200f);
                }
            }
        }
    }

    // ------------------------------------------------------------------------------- fan ---

    private static void fan(World w, Prop q, float dt) {
        q.legPhase += (q.on ? 40f : 0f) * dt;            // blade spin, for drawing
        if (!q.on) return;
        RB m = q.main();
        int dir = q.homeDirV >= 0 ? 1 : -1;
        float ox = m.x + dir * 16f, oy = m.y - 8f;
        for (int i = 0; i < w.sim.bodies.size(); i++) {
            RB b = w.sim.bodies.get(i);
            if (b.invM == 0f || b.user == q) continue;
            float dx = (b.x - ox) * dir, dy = b.y - oy;
            if (dx < 0f || dx > FAN_RANGE) continue;
            float spread = 60f + dx * 0.25f;
            if (Math.abs(dy) > spread) continue;
            float f = (1f - dx / FAN_RANGE) * (1f - Math.abs(dy) / spread * 0.6f);
            // a strong wind: light things fly, heavy things barely move
            float dv = Math.min(2.5e6f * f * b.invM, 2400f) * dt;
            b.vx += dir * dv;
            b.vy -= dv * 0.08f;
        }
        if (w.rnd.nextFloat() < dt * 10f)
            w.fx.add(Fx.SMOKE, ox + dir * 10f, oy + w.fx.rr(18f), dir * (500f + w.fx.rf() * 300f), w.fx.rr(40f), 0.7f, 2.5f, 0x55ffffff);
    }

    // --------------------------------------------------------------------------- crusher ---

    private static void crusher(World w, Prop q, float dt) {
        RB plate = q.bodies.get(3);
        // the stroke comes from the frame itself (so it survives saving and loading)
        float top = q.bodies.get(0).y + 32f, bottom = q.bodies.get(1).y + 150f - 18f;
        Shape ps = plate.shapes.get(0);
        float y0 = plate.y;
        if (!q.on) {
            plate.vy = 0f;
            if (plate.y > top) { plate.vy = -120f; plate.y = Math.max(top, plate.y - 120f * dt); }
            plate.update();
            return;
        }
        q.thinkT -= dt;
        int state = (int) q.work;
        if (state == 0) {                                   // waiting at the top
            plate.vy = 0f;
            if (q.thinkT <= 0f) { q.work = 1; }
        } else if (state == 1) {                            // slam down
            float speed = 950f;
            float ny = Math.min(bottom, plate.y + speed * dt);
            // something under the plate stops it: it gets crushed
            if (crush(w, q, plate, ps, ny)) ny = plate.y + speed * dt * 0.15f;
            plate.vy = (ny - plate.y) / dt;
            plate.y = ny;
            if (plate.y >= bottom - 0.5f) {
                q.work = 2; q.thinkT = 0.35f;
                w.rumble += 0.25f;
                if (w.sfx != null) w.sfx.thud(1f);
                w.fx.smoke(plate.x - ps.hl, bottom + ps.hh, 6f, 0xFF9a9a9a);
                w.fx.smoke(plate.x + ps.hl, bottom + ps.hh, 6f, 0xFF9a9a9a);
            }
        } else if (state == 2) {                            // short pause at the bottom
            plate.vy = 0f;
            if (q.thinkT <= 0f) q.work = 3;
        } else {                                            // rise slowly
            plate.vy = -160f;
            plate.y = Math.max(top, plate.y - 160f * dt);
            if (plate.y <= top + 0.5f) { q.work = 0; q.thinkT = 1.0f; }
        }
        if (plate.y == y0) plate.vy = 0f;
        plate.update();
    }

    /** Parts caught between the falling plate and whatever is below are crushed. Returns true if something resisted. */
    private static boolean crush(World w, Prop q, RB plate, Shape ps, float ny) {
        float bottomEdge = ny + ps.hh, left = plate.x - ps.hl, right = plate.x + ps.hl;
        boolean resisted = false;
        for (int i = 0; i < w.ents.size(); i++) {
            Ent e = w.ents.get(i);
            if (e == q || e.removed) continue;
            if (e.cx() + e.radius() < left || e.cx() - e.radius() > right) continue;
            if (e instanceof Person) {
                Person p = (Person) e;
                for (int k = 0; k < Person.NPARTS; k++) {
                    if (p.gibbed[k]) continue;
                    RB b = p.rb[k];
                    Shape s = b.shapes.get(0);
                    if (s.cx < left || s.cx > right) continue;
                    float topOfPart = s.minY;
                    if (topOfPart > bottomEdge || s.maxY < ny - ps.hh) continue;
                    // only when it is pinned against something solid below
                    boolean pinned = s.maxY >= w.sim.floorY - 6f || w.sim.stepNo - b.supportStep < 4;
                    if (!pinned) continue;
                    resisted = true;
                    float squeeze = bottomEdge - topOfPart;
                    if (squeeze > (s.maxY - s.minY) * 0.45f) w.gib(p, k, 0f, -1f);
                    else if (p.hitCd[k] <= 0f) { p.hitCd[k] = 0.1f; w.hurt(p, k, 25f, s.cx, s.minY, 0f, 1f); w.wound(p, k, Person.W_BRUISE, s.cx, s.minY, 0f, 1f, 8f); }
                }
            } else if (e instanceof Prop) {
                Prop o = (Prop) e;
                if (o.fixedMachine() || o.bodies.isEmpty()) continue;
                Shape s = o.main().shapes.get(0);
                if (s.minY > bottomEdge || s.maxY < ny - ps.hh) continue;
                boolean pinned = s.maxY >= w.sim.floorY - 6f || w.sim.stepNo - o.main().supportStep < 4;
                if (!pinned) continue;
                resisted = true;
                if (o.animal()) w.gibAnimal(o, 0f, -1f);
                else if (o.type == Items.POOP) w.squish(o);
                else if (o.type == Items.MELON) w.smash(o);
                else if (o.type == Items.BALLOON) w.pop(o);
                else if (o.explosive && o.fuse < 0f) o.fuse = 0.05f;
                else if (World.breakable(o)) w.wear(o, 400f, true);
                else resisted = o.main().m > 300f;       // steel and heavy things hold the plate up
            }
        }
        return resisted;
    }

    // ----------------------------------------------------------------------------- tesla ---

    private static void tesla(World w, Prop q, float dt) {
        if (!q.on) return;
        RB m = q.main();
        float tx = m.x, ty = m.y - 60f;
        if (w.rnd.nextFloat() < dt * 6f) w.addArc(tx, ty, tx + w.fx.rr(40f), ty + w.fx.rr(40f) - 20f, 0.06f);
        q.fireCd -= dt;
        if (q.fireCd > 0f) return;
        q.fireCd = 0.35f + w.rnd.nextFloat() * 0.35f;
        // the nearest thing in reach gets the bolt
        Ent best = null;
        float bd = TESLA_RANGE, hx = 0f, hy = 0f;
        for (int i = 0; i < w.ents.size(); i++) {
            Ent e = w.ents.get(i);
            if (e == q || e.removed || e.bodies.isEmpty()) continue;
            for (RB b : e.bodies) {
                if (b.shapes.isEmpty() || b.shapes.get(0).sensor) continue;
                float d = (float) Math.hypot(b.x - tx, b.y - ty);
                if (d < bd) { bd = d; best = e; hx = b.x; hy = b.y; }
            }
        }
        if (best == null) return;
        w.addArc(tx, ty, hx, hy, 0.12f);
        w.fx.add(Fx.GLOW, hx, hy, 0, 0, 0.12f, 30f, 0xFFbfe8ff);
        if (w.sfx != null) w.sfx.zap();
        if (best instanceof Person) shock(w, (Person) best, hx, hy);
        else {
            Prop o = (Prop) best;
            if (o.explosive && o.fuse < 0f) o.fuse = 0.05f;
            if (o.machine()) w.damageMachine(o, 12f);
            if (o.animal()) w.hurtAnimal(o, 10f, hx, hy, 0f, -1f);
            if (o.flammable && w.rnd.nextFloat() < 0.15f) w.ignite(o);
            w.fx.sparks(hx, hy, 6, 0xFFbfe8ff);
            for (RB b : o.bodies) if (b.invM > 0f) { b.vx += w.fx.rr(120f); b.vy -= 80f; }
        }
    }

    /** An electric shock: the whole body twitches, the skin burns where the bolt lands. */
    static void shock(World w, Person p, float x, float y) {
        w.stats.shocks++;
        int hit = 0;
        float bd = Float.MAX_VALUE;
        for (int k = 0; k < Person.NPARTS; k++) {
            if (p.gibbed[k]) continue;
            float d = (float) Math.hypot(p.rb[k].x - x, p.rb[k].y - y);
            if (d < bd) { bd = d; hit = k; }
        }
        if (p.frozenT > 0f) { shatter(w, p); return; }
        w.wound(p, hit, Person.W_BURN, x, y, 0f, -1f, 6f);
        w.hurt(p, hit, p.kind == Person.ROBOT ? 14f : 7f, x, y, 0f, -1f);
        p.charT = Math.min(0.6f, p.charT + 0.04f);
        if (!p.dead) { p.ko = Math.max(p.ko, 1.2f); p.tone = 0f; }
        for (int k = 0; k < Person.NPARTS; k++) {
            if (p.gibbed[k]) continue;
            RB b = p.rb[k];
            b.applyImpulse(w.fx.rr(1f) * b.m * 260f, (w.fx.rr(1f) * 200f - 80f) * b.m, b.x, b.y);
        }
        w.fx.sparks(x, y, 8, 0xFFbfe8ff);
        if (p.kind == Person.ROBOT) w.fx.sparks(p.rb[Person.CHEST].x, p.rb[Person.CHEST].y, 10, 0xFFffd25a);
    }

    // ----------------------------------------------------------------------------- spikes ---

    /** Something hit the spikes hard enough: the part stays stuck on them. */
    static void impale(World w, Person p, int part, Prop spikes, float x, float y) {
        if (p.removed || part < 0 || p.gibbed[part]) return;
        int stuck = 0;
        for (Joint j : spikes.joints) if (!j.removed) stuck++;
        if (stuck >= 8) return;
        for (Joint j : spikes.joints) if (!j.removed && j.b == p.rb[part]) return;
        RB b = p.rb[part];
        Joint j = Joint.weld(spikes.main(), b, x, y);
        j.tag = IMPALE_TAG;
        j.breakForce = b.m * w.sim.gravity * 55f;      // its own weight holds, a hard pull frees it
        w.sim.add(j);
        spikes.joints.add(j);
        w.stats.impaled++;
        w.wound(p, part, Person.W_HOLE, x, y, 0f, 1f, 5f);
        w.hurt(p, part, 22f, x, y, 0f, 1f);
        if (p.organic() || p.kind == Person.ZOMBIE) { w.bloodStyle(p); w.fx.blood(x, y, 0f, -0.5f, 12); p.bleed[part] = Math.max(p.bleed[part], 12f); }
        if (w.sfx != null) w.sfx.splat();
    }

    // ------------------------------------------------------------------------------ freeze ---

    /** Liquid nitrogen: every joint locks where it is and the body turns to ice for a while. */
    static void freeze(World w, Person p, float secs) {
        if (p.frozenT <= 0f) {
            for (int j = 0; j < Person.NJOINTS; j++) {
                Joint jn = p.jt[j];
                p.fzLimit[j] = jn.limit; p.fzLo[j] = jn.lo; p.fzHi[j] = jn.hi;
                if (p.severed[j]) continue;
                float rel = jn.b.a - jn.a.a - jn.ref;
                while (rel > Math.PI) rel -= 2 * Math.PI;
                while (rel < -Math.PI) rel += 2 * Math.PI;
                jn.limit = true; jn.lo = rel - 0.01f; jn.hi = rel + 0.01f;
                jn.muscle = false;
            }
        }
        p.frozenT = secs;
        p.ko = Math.max(p.ko, secs);
        p.tone = 0f;
        p.burning = false;
        for (int k = 0; k < Person.NPARTS; k++) p.bleed[k] = 0f;
        RB c = p.rb[Person.CHEST];
        for (int i = 0; i < 14; i++) w.fx.add(Fx.SMOKE, c.x + w.fx.rr(30f), c.y + w.fx.rr(50f), w.fx.rr(40f), -30f, 1f, 6f, 0xAAdff4ff);
    }

    /** Called once when the ice melts (or fire melts it): joints get their own limits back. */
    static void thaw(World w, Person p) {
        p.frozenT = 0f;
        for (int j = 0; j < Person.NJOINTS; j++) {
            Joint jn = p.jt[j];
            jn.limit = p.fzLimit[j]; jn.lo = p.fzLo[j]; jn.hi = p.fzHi[j];
        }
        p.ko = Math.min(p.ko, 1.5f);
        RB c = p.rb[Person.CHEST];
        for (int i = 0; i < 8; i++) w.fx.add(Fx.WATER, c.x + w.fx.rr(20f), c.y + w.fx.rr(40f), w.fx.rr(60f), 20f, 0.8f, 3f, 0xFF9fd3ff);
    }

    /** A frozen body hit hard breaks into ice chunks. */
    static void shatter(World w, Person p) {
        if (p.removed || p.gibbed[Person.CHEST]) return;     // already in pieces
        w.stats.shattered++;
        RB c = p.rb[Person.CHEST];
        for (int i = 0; i < 20; i++) w.fx.add(Fx.DEBRIS, c.x + w.fx.rr(30f), c.y + w.fx.rr(50f), w.fx.rr(380f), -w.fx.rf() * 380f, 1.2f, 3f, 0xFFcfeeff);
        if (w.sfx != null) w.sfx.crunch();
        for (int k = 0; k < Person.NPARTS; k++) if (!p.gibbed[k]) w.gib(p, k, w.fx.rr(1f), -0.5f);
    }
}

package com.frameboost.ragdoll;

/**
 * SAW traps: the bear trap that snaps shut on a foot, the swinging pendulum blade, the reverse bear trap
 * locked on a head with a countdown, and Billy, the puppet on the tricycle.
 */
final class Traps {
    private Traps() { }

    static final int TRAP_TAG = 79;
    static final float REV_TIME = 12f;
    static final String[] BILLY_LINES = {
        "Hola. Quiero jugar a un juego.",
        "Vive o muere. Tú eliges.",
        "Que empiece el juego.",
        "La sangre fluirá... o no.",
        "¿Cuánta sangre derramarías por sobrevivir?",
    };

    static void update(World w, Prop q, float dt) {
        switch (q.type) {
            case Items.BEARTRAP: bearTrap(w, q, dt); break;
            case Items.PENDULUM: pendulum(w, q, dt); break;
            case Items.REVTRAP: reverseTrap(w, q, dt); break;
            case Items.BILLY: billy(w, q, dt); break;
            case Items.LANDMINE: landmine(w, q, dt); break;
            case Items.GUILLOTINE: guillotine(w, q, dt); break;
            case Items.FLAMEJET: flamejet(w, q, dt); break;
            case Items.LASER: laser(w, q, dt); break;
            default: break;
        }
    }

    /** Activate tool / context menu. Returns the message for the toast. */
    static String activate(World w, Prop q) {
        switch (q.type) {
            case Items.BEARTRAP:
                release(w, q);
                q.on = true; q.work = 0f; q.wear = 0f; q.thinkT = 1.2f;      // a moment to get the foot out
                if (w.sfx != null) w.sfx.snap();
                return "Cepo armado";
            case Items.PENDULUM:
                q.on = !q.on;
                return q.on ? "Péndulo en marcha" : "Péndulo parado";
            case Items.REVTRAP:
                if (q.victim != null && !q.dead) {
                    Person v = q.victim;
                    release(w, q);
                    q.thinkT = 3f;
                    w.stats.trapsEscaped++;
                    if (w.sfx != null) w.sfx.snap();
                    return v.dead ? "Trampa quitada (demasiado tarde)" : "¡Liberado a tiempo!";
                }
                if (q.dead) { q.dead = false; q.work = REV_TIME; return "Trampa invertida rearmada"; }
                return "Tócala sobre una cabeza (o déjala caer encima)";
            case Items.BILLY:
                q.on = !q.on;
                say(w, q);
                return q.on ? "Billy pedalea" : "Billy se detiene";
            case Items.LANDMINE:
                if (q.ammo == 0) { q.ammo = 1; q.work = 0.05f; }
                return "¡Mina detonada!";
            case Items.GUILLOTINE:
                if (q.ammo != 0) return "La guillotina aún no está lista";
                q.ammo = 1; q.thinkT = 0f;
                if (w.sfx != null) w.sfx.click();
                return "¡Guillotina soltada!";
            case Items.FLAMEJET:
                q.on = !q.on; q.work = 0f;
                return q.on ? "Lanzallamas encendido" : "Lanzallamas apagado";
            case Items.LASER:
                q.on = !q.on;
                if (w.sfx != null) w.sfx.click();
                return q.on ? "Láser encendido" : "Láser apagado";
            default: return "";
        }
    }

    /** Lets go of whatever the trap holds. */
    static void release(World w, Prop q) {
        for (int i = q.joints.size() - 1; i >= 0; i--) {
            Joint j = q.joints.get(i);
            if (j.tag != TRAP_TAG) continue;
            if (!j.removed) w.sim.remove(j);
            q.joints.remove(i);
        }
        if (q.type == Items.REVTRAP) {
            for (Shape s : q.main().shapes) s.group = 0;
            q.victim = null;
            RB b = q.main();
            b.vx += w.fx.rr(80f); b.vy -= 120f;
        }
    }

    // ------------------------------------------------------------------------- bear trap ---

    private static void bearTrap(World w, Prop q, float dt) {
        RB m = q.main();
        if (!m.frozen) { m.vx = m.vy = m.w = 0f; m.setFrozen(true); }
        if (q.work > 0f) q.work = Math.max(0f, q.work - dt * 9f);        // jaws closing (1 -> 0 drawn as shut)
        // a held limb bleeds onto the steel
        for (int i = 0; i < q.joints.size(); i++) {
            Joint j = q.joints.get(i);
            if (j.removed && j.tag == TRAP_TAG) { q.joints.remove(i); i--; }
        }
        if (!q.on) return;
        if (q.thinkT > 0f) { q.thinkT -= dt; return; }
        Shape s = m.shapes.get(0);
        float left = m.x - s.hl, right = m.x + s.hl, top = m.y - s.hh - 16f;
        for (int i = 0; i < w.ents.size(); i++) {
            Ent e = w.ents.get(i);
            if (e.removed || Math.abs(e.cx() - m.x) > 200f) continue;
            if (e instanceof Person) {
                Person p = (Person) e;
                int[] parts = {Person.FOOT_F, Person.FOOT_B, Person.SHIN_F, Person.SHIN_B};
                for (int k : parts) {
                    if (p.gibbed[k]) continue;
                    Shape ps = p.rb[k].shapes.get(0);
                    if (ps.maxY < top || ps.minY > m.y + s.hh || ps.maxX < left + 6f || ps.minX > right - 6f) continue;
                    snap(w, q, p, k);
                    return;
                }
            } else if (e instanceof Prop) {
                Prop o = (Prop) e;
                if (o == q || o.bodies.isEmpty() || Items.isTrap(o.type) || o.fixedMachine()) continue;
                Shape ps = o.main().shapes.get(0);
                if (ps.maxY < top || ps.minY > m.y || ps.maxX < left + 6f || ps.minX > right - 6f) continue;
                if (o.lastSp < 30f && o.age < 0.3f) continue;
                q.on = false; q.work = 1f;
                if (o.animal()) w.hurtAnimal(o, 45f, m.x, top, 0f, 1f);
                else if (World.breakable(o)) w.wear(o, 60f, true);
                if (w.sfx != null) w.sfx.snap();
                w.fx.sparks(m.x, top, 8, 0xFFffd25a);
                return;
            }
        }
    }

    private static void snap(World w, Prop q, Person p, int part) {
        RB m = q.main();
        RB b = p.rb[part];
        q.on = false; q.work = 1f; q.wear = 1f;
        float x = Math.max(m.x - 20f, Math.min(m.x + 20f, b.x)), y = m.y - 8f;
        Joint j = Joint.weld(m, b, x, y);
        j.tag = TRAP_TAG;
        j.breakForce = Math.max(b.m * 200f, p.mass) * w.sim.gravity * 6f;   // only a very hard pull tears the leg free
        w.sim.add(j);
        q.joints.add(j);
        w.stats.trapped++;
        w.wound(p, part, Person.W_CUT, x - 6f, y, 1f, 0f, 7f);
        w.wound(p, part, Person.W_HOLE, x + 5f, y - 3f, 0f, 1f, 4f);
        w.hurt(p, part, 34f, x, y, 0f, -1f);
        if (p.organic()) { p.broken[part] = true; p.bleed[part] = Math.max(p.bleed[part], 14f); }
        if (!p.dead) { p.ko = Math.max(p.ko, 2.5f); p.tone = 0f; p.panicT = 6f; }
        w.bloodStyle(p);
        w.fx.blood(x, y, 0f, -1f, 16);
        if (w.fx.gore) w.fx.wallSplat(x, y - 30f, 18f, w.fx.bloodA);
        w.fx.sparks(x, y - 6f, 6, 0xFFffd25a);
        if (w.sfx != null) { w.sfx.snap(); w.sfx.crunch(); }
        w.alarm(x, y, 500f, 4f);
    }

    // -------------------------------------------------------------------------- pendulum ---

    static float pendulumAngle(Prop q) { return q.work; }

    private static void pendulum(World w, Prop q, float dt) {
        RB mount = q.bodies.get(0), arm = q.bodies.get(1);
        for (RB b : q.bodies) if (!b.frozen) { b.vx = b.vy = b.w = 0f; b.setFrozen(true); }
        arm.x = mount.x; arm.y = mount.y;
        if (q.on) q.legPhase += dt * 2.6f;
        float amp = 1.15f;
        float ang = q.on ? amp * (float) Math.sin(q.legPhase) : q.work * Math.max(0f, 1f - dt * 1.5f);
        float omega = (ang - q.work) / Math.max(1e-4f, dt);
        q.work = ang;
        arm.setAngle(ang);
        arm.w = omega;
        arm.update();
        float speed = Math.abs(omega) * q.param;
        Shape blade = arm.shapes.get(1);
        if (speed < 120f) return;
        float ux = blade.ax, uy = blade.ay;                 // along the blade
        for (int i = 0; i < w.ents.size(); i++) {
            Ent e = w.ents.get(i);
            if (e == q || e.removed) continue;
            if (Math.abs(e.cx() - blade.cx) > 120f + e.radius()) continue;
            if (e instanceof Person) {
                Person p = (Person) e;
                for (int k = 0; k < Person.NPARTS; k++) {
                    if (p.gibbed[k] || p.hitCd[k] > 0f) continue;
                    Shape s = p.rb[k].shapes.get(0);
                    float dx = s.cx - blade.cx, dy = s.cy - blade.cy;
                    float u = dx * ux + dy * uy, v = -dx * uy + dy * ux;
                    float rad = s.type == Shape.CAPSULE ? s.r + s.hl * 0.5f : s.r;
                    if (Math.abs(u) > blade.hl + rad * 0.5f || Math.abs(v) > blade.hh + rad + 4f) continue;
                    float cx = blade.cx + ux * u, cy = blade.cy + uy * u;
                    float tx = ux * Math.signum(omega), ty = uy * Math.signum(omega);
                    p.hitCd[k] = 0.22f;
                    if (p.fleshy()) q.wear = Math.min(1f, q.wear + 0.2f);
                    w.wound(p, k, Person.W_CUT, cx, cy, tx, ty, 9f);
                    w.hurt(p, k, 20f + speed * 0.02f, cx, cy, tx, ty);
                    RB b = p.rb[k];
                    b.applyImpulse(tx * b.m * Math.min(700f, speed * 0.6f), ty * b.m * Math.min(700f, speed * 0.6f) - b.m * 120f, cx, cy);
                    int j = Person.PART_JOINT[k];
                    if (j >= 0 && j != Person.WAIST && !p.severed[j] && w.rnd.nextFloat() < 0.45f) w.sever(p, j, cx, cy);
                    else if (k == Person.CHEST && w.rnd.nextFloat() < 0.15f) w.gib(p, k, tx, ty);
                    if (w.fx.gore && p.fleshy()) w.fx.wallSplat(cx + tx * 40f, cy - 20f, 14f + w.fx.rf() * 14f, w.fx.bloodA);
                    if (w.sfx != null) w.sfx.crunch();
                    w.stats.hits++;
                }
            } else if (e instanceof Prop) {
                Prop o = (Prop) e;
                if (o.bodies.isEmpty() || o.fixedMachine() || Items.isTrap(o.type)) continue;
                Shape s = o.main().shapes.get(0);
                float dx = s.cx - blade.cx, dy = s.cy - blade.cy;
                float u = dx * ux + dy * uy, v = -dx * uy + dy * ux;
                if (Math.abs(u) > blade.hl + 10f || Math.abs(v) > blade.hh + Math.max(o.hw, o.hh)) continue;
                if (o.animal()) { if (o.flash <= 0f) { w.hurtAnimal(o, 30f, blade.cx, blade.cy, ux, uy); o.flash = 0.2f; } }
                else if (World.breakable(o)) { o.impX = blade.cx + ux * u; o.impY = blade.cy + uy * u; w.wear(o, 140f, true); }
                else if (o.type == Items.MELON) w.smash(o);
                else if (o.type == Items.BALLOON) w.pop(o);
            }
        }
        if (w.rnd.nextFloat() < dt * 3f && speed > 500f && w.sfx != null) w.sfx.launch();
    }

    // ---------------------------------------------------------------- reverse bear trap ---

    private static void reverseTrap(World w, Prop q, float dt) {
        if (q.thinkT > 0f) q.thinkT -= dt;
        RB m = q.main();
        if (q.victim == null) {
            if (q.dead || q.thinkT > 0f) return;
            // the first head it touches gets it
            for (int i = 0; i < w.ents.size(); i++) {
                Ent e = w.ents.get(i);
                if (!(e instanceof Person) || e.removed) continue;
                Person cand = (Person) e;
                if (cand.gibbed[Person.HEAD] || cand.kind == Person.ROBOT) continue;
                RB h = cand.rb[Person.HEAD];
                float r = h.shapes.get(0).r;
                if (Math.hypot(h.x - m.x, h.y - m.y) < r + 20f) { lockOn(w, q, cand); return; }
            }
            return;
        }
        Person p = q.victim;
        if (p.removed || p.gibbed[Person.HEAD]) { release(w, q); q.dead = true; return; }
        q.work -= dt;
        if (!p.dead) { p.panicT = Math.max(p.panicT, 1f); p.armsUp = true; }
        int sec = (int) Math.ceil(q.work);
        if (sec != q.ammo) { q.ammo = sec; if (w.sfx != null && sec > 0) w.sfx.click(); }
        if (q.work <= 0f) spring(w, q, p);
    }

    /** Puts the trap on a head (placing it on someone, or falling on them). */
    static void lockOn(World w, Prop q, Person p) {
        RB m = q.main(), h = p.rb[Person.HEAD];
        m.x = h.x; m.y = h.y; m.setAngle(h.a); m.vx = h.vx; m.vy = h.vy; m.w = h.w;
        m.update();
        for (Shape s : m.shapes) s.group = p.group;
        Joint j = Joint.weld(h, m, h.x, h.y);
        j.tag = TRAP_TAG;
        w.sim.add(j);
        q.joints.add(j);
        q.victim = p;
        q.work = REV_TIME;
        q.ammo = (int) REV_TIME;
        q.dead = false;
        if (w.sfx != null) w.sfx.snap();
        if (!p.dead) { p.panicT = 8f; p.mood = "¡Quítamelo!"; }
    }

    /** Time's up: the jaws rip open. */
    private static void spring(World w, Prop q, Person p) {
        RB h = p.rb[Person.HEAD];
        float hx = h.x, hy = h.y;
        release(w, q);
        q.dead = true;
        w.stats.trapsSprung++;
        if (w.sfx != null) w.sfx.snap();
        w.bloodStyle(p);
        if (w.fx.gore) {
            w.fx.wallSplat(hx, hy - 10f, 34f, w.fx.bloodA);
            w.fx.wallSplat(hx + w.fx.rr(50f), hy + w.fx.rr(30f), 18f, w.fx.bloodB);
        }
        w.gib(p, Person.HEAD, 0f, -1f);
        RB m = q.main();
        m.vy -= 260f; m.w += w.fx.rr(12f);
    }

    // ----------------------------------------------------------------------------- billy ---

    static void say(World w, Prop q) {
        q.talkLine = (q.talkLine + 1 + w.rnd.nextInt(BILLY_LINES.length - 1)) % BILLY_LINES.length;
        q.talkT = 3.2f;
        if (w.sfx != null) w.sfx.laugh();
    }

    private static void billy(World w, Prop q, float dt) {
        RB b = q.main();
        if (q.talkT > 0f) q.talkT -= dt;
        if (q.dead) return;
        q.thinkT -= dt;
        if (q.thinkT <= 0f) { q.thinkT = 7f + w.rnd.nextFloat() * 6f; say(w, q); }
        boolean grounded = w.sim.stepNo - b.supportStep < 6;
        float ang = Fauna.wrap(b.a);
        if (!b.frozen) b.w += (-ang * 26f - b.w * 6f) * dt * (grounded ? 1f : 0.4f);
        if (!q.on || !grounded || Math.abs(ang) > 0.6f || b.frozen) return;
        if ((b.x < w.sim.wallL + 70f && q.face < 0) || (b.x > w.sim.wallR - 70f && q.face > 0)) q.face = -q.face;
        float want = q.face * 70f;
        b.vx += (want - b.vx) * Math.min(1f, dt * 12f);
        q.legPhase += b.vx * dt * 0.08f;                    // pedals and wheels turn as it rolls
    }

    // ------------------------------------------------------------------------------ landmine ---

    private static void landmine(World w, Prop q, float dt) {
        RB m = q.main();
        if (!m.frozen) { m.vx = m.vy = m.w = 0f; m.setFrozen(true); }
        if (q.ammo == 1) {                                   // triggered: the click, then the blast
            q.work -= dt;
            if (q.work <= 0f) {
                float x = m.x, y = m.y - 6f;
                w.remove(q);
                w.explode(x, y, 190f, 1100f, 130f, true);
            }
            return;
        }
        if (q.thinkT > 0f) { q.thinkT -= dt; return; }       // a moment to arm itself when placed
        if (!q.on) return;
        Shape s = m.shapes.get(0);
        float left = m.x - s.hl - 4f, right = m.x + s.hl + 4f, top = m.y - s.hh - 6f;
        for (int i = 0; i < w.ents.size(); i++) {
            Ent e = w.ents.get(i);
            if (e.removed || Math.abs(e.cx() - m.x) > 240f) continue;
            boolean hit = false;
            if (e instanceof Person) {
                Person p = (Person) e;
                for (int k = 0; k < Person.NPARTS && !hit; k++) {
                    if (p.gibbed[k]) continue;
                    Shape ps = p.rb[k].shapes.get(0);
                    hit = ps.maxY > top && ps.minY < m.y + s.hh && ps.maxX > left && ps.minX < right;
                }
            } else if (e instanceof Prop) {
                Prop o = (Prop) e;
                if (o == q || o.bodies.isEmpty() || Items.isTrap(o.type) || o.fixedMachine() || o.main().frozen) continue;
                Shape ps = o.main().shapes.get(0);
                hit = o.main().m > 1.2f && ps.maxY > top && ps.minY < m.y && ps.maxX > left && ps.minX < right && o.lastSp > 20f;
            }
            if (hit) {
                q.ammo = 1; q.work = 0.3f;
                if (w.sfx != null) w.sfx.click();
                w.fx.sparks(m.x, top, 4, 0xFFffd25a);
                return;
            }
        }
    }

    // ---------------------------------------------------------------------------- guillotine ---

    /** Blade centre, in the frame's local y (0 = up at the top of the frame, 1 = down at the bottom). */
    static float bladeLocalY(Prop q) { return -95f + q.work * 205f; }

    private static void guillotine(World w, Prop q, float dt) {
        RB m = q.main();
        if (!m.frozen) { m.vx = m.vy = m.w = 0f; m.setFrozen(true); }
        if (q.ammo == 0) {                                   // armed: waits for someone under the blade
            if (q.thinkT > 0f) { q.thinkT -= dt; return; }
            if (!q.on) return;
            for (int i = 0; i < w.ents.size(); i++) {
                Ent e = w.ents.get(i);
                if (!(e instanceof Person) || e.removed || Math.abs(e.cx() - m.x) > 120f) continue;
                Person p = (Person) e;
                if (p.gibbed[Person.HEAD] || p.dead) continue;
                RB h = p.rb[Person.HEAD];
                if (Math.abs(h.x - m.x) < 26f && h.y > m.y - 120f && h.y < m.y + 140f) { q.ammo = 1; if (w.sfx != null) w.sfx.click(); break; }
            }
            return;
        }
        if (q.ammo == 1) {                                   // falling
            float before = q.work;
            q.work = Math.min(1f, q.work + dt * 7f);
            chop(w, q, m.y + bladeLocalY(q), m.y + (-95f + before * 205f));
            if (q.work >= 1f) {
                q.ammo = 3; q.thinkT = 1.6f;
                if (w.sfx != null) { w.sfx.snap(); w.sfx.crunch(); }
                w.fx.sparks(m.x, m.y + 124f, 10, 0xFFffd25a);
            }
        } else if (q.ammo == 3) {                            // lying at the bottom
            q.thinkT -= dt;
            if (q.thinkT <= 0f) q.ammo = 2;
        } else {                                             // hoisted back up
            q.work = Math.max(0f, q.work - dt * 0.45f);
            if (q.work <= 0f) { q.ammo = 0; q.thinkT = 0.8f; }
        }
    }

    /** Whatever lies between the blade's previous and current heights is cut. */
    private static void chop(World w, Prop q, float by, float prevY) {
        RB m = q.main();
        float lo = Math.min(by, prevY) - 14f, hi = Math.max(by, prevY) + 14f;
        for (int i = 0; i < w.ents.size(); i++) {
            Ent e = w.ents.get(i);
            if (e == q || e.removed || Math.abs(e.cx() - m.x) > 160f) continue;
            if (e instanceof Person) {
                Person p = (Person) e;
                for (int k = 0; k < Person.NPARTS; k++) {
                    if (p.gibbed[k] || p.hitCd[k] > 0f) continue;
                    Shape s = p.rb[k].shapes.get(0);
                    float rad = s.type == Shape.CAPSULE ? s.r + s.hl * 0.5f : s.r;
                    if (Math.abs(s.cx - m.x) > 40f + rad * 0.3f || s.cy + rad < lo || s.cy - rad > hi) continue;
                    p.hitCd[k] = 0.4f;
                    if (p.fleshy()) q.wear = Math.min(1f, q.wear + 0.25f);
                    w.wound(p, k, Person.W_CUT, s.cx, by, 1f, 0f, 11f);
                    w.hurt(p, k, 70f, s.cx, by, 0f, 1f);
                    p.rb[k].applyImpulse(0f, p.rb[k].m * 200f, s.cx, s.cy);
                    int j = Person.PART_JOINT[k];
                    if (j >= 0 && j != Person.WAIST && !p.severed[j] && (k == Person.HEAD || w.rnd.nextFloat() < 0.7f)) w.sever(p, j, s.cx, by);
                    else if (k == Person.CHEST && w.rnd.nextFloat() < 0.25f) w.gib(p, k, 0f, 1f);
                    if (w.fx.gore && p.fleshy()) w.fx.wallSplat(s.cx + w.fx.rr(60f), by - 20f, 16f + w.fx.rf() * 16f, w.fx.bloodA);
                    if (w.sfx != null) w.sfx.crunch();
                    w.stats.hits++;
                }
            } else if (e instanceof Prop) {
                Prop o = (Prop) e;
                if (o.bodies.isEmpty() || o.fixedMachine() || Items.isTrap(o.type)) continue;
                Shape s = o.main().shapes.get(0);
                if (Math.abs(s.cx - m.x) > 40f + o.hw || s.cy + o.hh < lo || s.cy - o.hh > hi) continue;
                if (o.animal()) { if (o.flash <= 0f) { w.hurtAnimal(o, 90f, s.cx, by, 0f, 1f); o.flash = 0.3f; } }
                else if (World.breakable(o)) { o.impX = s.cx; o.impY = by; w.wear(o, 220f, true); }
                else if (o.type == Items.MELON) w.smash(o);
                else if (o.type == Items.BALLOON) w.pop(o);
            }
        }
    }

    // ----------------------------------------------------------------------------- flamejet ---

    private static void flamejet(World w, Prop q, float dt) {
        RB m = q.main();
        if (!m.frozen) { m.vx = m.vy = m.w = 0f; m.setFrozen(true); }
        if (!q.on) return;
        float before = q.work;
        q.work += dt;
        if (q.work > 3.8f) q.work = 0f;
        float nx = m.x, ny = m.y - q.hh - 4f;
        if (q.work < 2.2f) {                                 // pilot light
            if (w.rnd.nextFloat() < dt * 24f) w.fx.add(Fx.FLAME, nx + w.fx.rr(2f), ny, w.fx.rr(8f), -50f, 0.25f, 5f, 0xFFffa040);
            return;
        }
        if (before < 2.2f && w.sfx != null) w.sfx.launch();
        int cnt = (int) (150f * dt + w.rnd.nextFloat());
        for (int k = 0; k < cnt; k++)
            w.fx.add(Fx.FLAME, nx + w.fx.rr(6f), ny, w.fx.rr(32f), -(300f + w.rnd.nextFloat() * 160f), 0.4f + w.rnd.nextFloat() * 0.2f, 9f + w.rnd.nextFloat() * 6f, 0xFFffa040);
        if (w.rnd.nextFloat() < dt * 10f) w.fx.smoke(nx + w.fx.rr(12f), ny - 150f, 8f, 0xFF3c3c3c);
        float top = ny - 165f;
        for (int i = 0; i < w.ents.size(); i++) {
            Ent e = w.ents.get(i);
            if (e == q || e.removed || Math.abs(e.cx() - nx) > 200f) continue;
            if (e instanceof Person) {
                Person p = (Person) e;
                for (int k = 0; k < Person.NPARTS; k++) {
                    if (p.gibbed[k]) continue;
                    RB b = p.rb[k];
                    if (Math.abs(b.x - nx) > 30f || b.y < top || b.y > ny + 10f) continue;
                    w.hurt(p, k, 55f * dt, b.x, b.y, 0f, -1f);
                    if (w.rnd.nextFloat() < dt * 3f) w.wound(p, k, Person.W_BURN, b.x, b.y, 0f, -1f, 6f);
                    b.vy -= 380f * dt;                         // the blast lifts them a little
                    w.ignite(p);
                }
            } else if (e instanceof Prop) {
                Prop o = (Prop) e;
                if (o.bodies.isEmpty() || Items.isTrap(o.type) || o.fixedMachine()) continue;
                if (Math.abs(o.cx() - nx) > 30f + o.hw || o.cy() < top || o.cy() > ny + 20f) continue;
                if (o.animal()) { w.hurtAnimal(o, 40f * dt, o.cx(), o.cy(), 0f, -1f); w.ignite(o); }
                else { w.ignite(o); if (o.explosive && o.fuse < 0f) o.fuse = 0.3f; }
            }
        }
    }

    // --------------------------------------------------------------------------------- laser ---

    private static void laser(World w, Prop q, float dt) {
        RB m = q.main();
        if (!m.frozen) { m.vx = m.vy = m.w = 0f; m.setFrozen(true); }
        if (q.legPhase > 0f) q.legPhase = Math.max(0f, q.legPhase - dt);
        if (!q.on) return;
        float by = m.y - 12f, x0 = m.x - 120f, x1 = m.x + 120f;
        for (int i = 0; i < w.ents.size(); i++) {
            Ent e = w.ents.get(i);
            if (e == q || e.removed || Math.abs(e.cx() - m.x) > 190f) continue;
            if (e instanceof Person) {
                Person p = (Person) e;
                for (int k = 0; k < Person.NPARTS; k++) {
                    if (p.gibbed[k] || p.hitCd[k] > 0f) continue;
                    Shape s = p.rb[k].shapes.get(0);
                    if (s.maxY < by || s.minY > by || s.maxX < x0 || s.minX > x1) continue;
                    p.hitCd[k] = 0.5f;
                    q.legPhase = 0.3f;
                    float cx = Math.max(x0, Math.min(x1, s.cx));
                    w.wound(p, k, Person.W_BURN, cx, by, 1f, 0f, 6f);
                    w.wound(p, k, Person.W_CUT, cx, by, 1f, 0f, 8f);
                    w.hurt(p, k, 45f, cx, by, 0f, -1f);
                    int j = Person.PART_JOINT[k];
                    if (j >= 0 && j != Person.WAIST && !p.severed[j] && w.rnd.nextFloat() < 0.6f) w.sever(p, j, cx, by);
                    w.fx.sparks(cx, by, 8, 0xFFff5a40);
                    if (w.sfx != null) w.sfx.zap();
                    w.alarm(cx, by, 400f, 3f);
                    w.stats.hits++;
                }
            } else if (e instanceof Prop) {
                Prop o = (Prop) e;
                if (o.bodies.isEmpty() || Items.isTrap(o.type) || o.fixedMachine()) continue;
                Shape s = o.main().shapes.get(0);
                if (s.maxY < by || s.minY > by || s.maxX < x0 || s.minX > x1) continue;
                if (o.animal()) { if (o.flash <= 0f) { w.hurtAnimal(o, 35f, o.cx(), by, 0f, -1f); o.flash = 0.3f; q.legPhase = 0.3f; } }
                else if (o.type == Items.BALLOON) w.pop(o);
                else if (o.type == Items.MELON) w.smash(o);
            }
        }
    }
}

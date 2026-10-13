package com.frameboost.ragdoll;

/**
 * Programmed behaviour for the characters and machines that "do things" in the room:
 * civilians read danger (zombies, thugs, lit explosives, fire) and run from it; soldiers hunt
 * hostiles and never shoot through a friendly; medics treat the wounded and do CPR on the dead;
 * firefighters put fires out; thugs pick fights; turrets and drones guard the area on their own.
 */
final class Ai {
    private Ai() { }

    static final float THINK = 0.32f;   // average time between two decisions (Brain.think)

    // --------------------------------------------------------------------------- perception ---

    static float ax(Body b) { return b.isHuman() ? b.chest.x : b.cx; }
    static float ay(Body b) { return b.isHuman() ? b.chest.y : b.cy; }

    /** Nearest hostile person (zombie or thug) around (x, y). */
    static Body nearestHostile(World w, float x, float y, float range) {
        Body best = null;
        float bd = range;
        for (int i = 0; i < w.bodies.size(); i++) {
            Body o = w.bodies.get(i);
            if (!o.hostile() || o.ps.isEmpty()) continue;
            float dx = o.chest.x - x, dy = o.chest.y - y;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d < bd) { bd = d; best = o; }
        }
        return best;
    }

    /** Someone a thug wants to beat up: alive, standing on our side of the fight. */
    private static Body nearestVictim(World w, Body self, float range) {
        Body best = null;
        float bd = range;
        for (int i = 0; i < w.bodies.size(); i++) {
            Body o = w.bodies.get(i);
            if (o == self || !o.isHuman() || o.dead || o.hostile() || o.kind == Body.K_SKELETON) continue;
            float d = Math.abs(o.pelvis.x - self.pelvis.x) + Math.abs(o.pelvis.y - self.pelvis.y) * 0.6f;
            if (d < bd) { bd = d; best = o; }
        }
        return best;
    }

    /**
     * Worst danger near a person. Returns the direction to run (-1 / +1) or 0 when it is safe.
     * hostiles: also count zombies/thugs (soldiers and thugs ignore them).
     */
    static int danger(World w, Body b, boolean hostiles, boolean fire) {
        float x = b.pelvis.x, y = b.pelvis.y;
        float best = 0f, fromX = 0f;
        for (int i = 0; i < w.bodies.size(); i++) {
            Body o = w.bodies.get(i);
            if (o == b || o.ps.isEmpty()) continue;
            float score = 0f;
            if (o.hostile()) {
                if (!hostiles || (b.kind == Body.K_THUG && o.kind == Body.K_THUG)) continue;
                float d = Math.abs(o.pelvis.x - x) + Math.abs(o.pelvis.y - y) * 0.5f;
                if (d < 520f) score = 1.5f - d / 520f;
            } else if (o.explosive && o.fuse > 0f && o.fuse < 4.5f) {          // lit fuse: get away!
                float dx = o.cx - x, dy = o.cy - y, d = (float) Math.sqrt(dx * dx + dy * dy), r = o.blastR * 1.1f;
                if (d < r) score = 1.8f - d / r;
            } else if (fire && o.burning) {
                float dx = o.cx - x, dy = o.cy - y, d = (float) Math.sqrt(dx * dx + dy * dy) - o.rad;
                if (d < 200f) score = 0.8f - Math.max(0f, d) / 400f;
            }
            if (score > best) { best = score; fromX = o.cx; }
        }
        if (best <= 0f) return 0;
        int run = fromX > x ? -1 : 1;
        if (run < 0 && x < w.wallL + 110) run = 1;    // cornered: try the other way
        if (run > 0 && x > w.wallR - 110) run = -1;
        return run;
    }

    /** Turns to face a point (only when standing straight, like Brain.turn). */
    private static void face(Body b, float x) {
        int want = x > b.pelvis.x ? 1 : -1;
        if (want != b.dir && Brain.upright(b)) Brain.turn(b);
    }

    private static void aimAt(Body b, float x, float y) {
        float dx = x - b.chest.x, dy = y - b.chest.y, l = (float) Math.sqrt(dx * dx + dy * dy);
        if (l < 1f) return;
        b.aimX = dx / l; b.aimY = dy / l;
        b.aimT = 0.6f;
    }

    private static boolean armsWork(Body b) {
        return !b.jShF.dead && !b.jElF.dead && !b.farmF.fractured && !b.uarmF.fractured;
    }

    // -------------------------------------------------------------------------- every frame ---

    /** Timers and continuous actions (spraying water). Called every frame for every person. */
    static void tick(World w, Body b, float dt) {
        if (b.aimT > 0f) b.aimT -= dt;
        if (b.punchT > 0f) b.punchT -= dt;
        if (b.shotCd > 0f) b.shotCd -= dt;
        if (b.reloadT > 0f) { b.reloadT -= dt; if (b.reloadT <= 0f) b.ammo = 12; }
        if (b.kind == Body.K_FIREFIGHTER && b.target != null && b.aimT > 0f && Brain.able(b) && armsWork(b)) spray(w, b, dt);
    }

    // ----------------------------------------------------------------------------- decisions ---

    /** One decision for a person (every ~0.3 s). Returns false when Brain should use its own logic. */
    static boolean decide(World w, Body b) {
        switch (b.kind) {
            case Body.K_HUMAN: civilian(w, b); return true;
            case Body.K_SOLDIER: soldier(w, b); return true;
            case Body.K_MEDIC: medic(w, b); return true;
            case Body.K_FIREFIGHTER: firefighter(w, b); return true;
            case Body.K_THUG: thug(w, b); return true;
            default: return false;
        }
    }

    private static boolean flee(World w, Body b, boolean hostiles, boolean fire) {
        int run = danger(w, b, hostiles, fire);
        if (run == 0) return false;
        b.walk = run;
        b.panic = 1f;
        b.target = null;
        b.mood = "Huyendo";
        return true;
    }

    private static void civilian(World w, Body b) {
        b.panic = 0f;
        if (flee(w, b, true, true)) return;
        if (b.burning) { b.walk = w.rnd.nextBoolean() ? 1 : -1; b.panic = 1f; b.mood = "¡Se quema!"; return; }
        b.mood = "Tranquilo";
    }

    private static void soldier(World w, Body b) {
        b.panic = 0f;
        if (flee(w, b, false, false)) return;   // only lit explosives scare a soldier
        if (!armsWork(b)) {                      // can't hold the rifle: retreat from everything
            if (!flee(w, b, true, true)) b.mood = "Herido";
            return;
        }
        Body t = nearestHostile(w, b.chest.x, b.chest.y, 1400f);
        b.target = t;
        if (t == null) { b.mood = "Patrullando"; return; }
        float dx = t.chest.x - b.chest.x, adx = Math.abs(dx);
        face(b, t.chest.x);
        if (adx < 85f * b.scale && Math.abs(t.chest.y - b.chest.y) < 110f) {
            if (b.punchT <= 0f && b.shotCd <= 0f) strike(w, b, t, 11f, 640f);   // rifle butt
            b.mood = "Culatazo";
            return;
        }
        if (adx > 950f) { b.walk = dx > 0 ? 1 : -1; b.mood = "Avanzando"; }
        // aim at the chest (heads are smaller), a little lead on moving targets
        float tx = t.chest.x + t.chest.vx() * 0.05f, ty = t.chest.y + t.chest.vy() * 0.05f - 6f;
        aimAt(b, tx, ty);
        if (b.walk != 0) return;
        if (b.reloadT > 0f) { b.mood = "Recargando"; return; }
        if (b.shotCd > 0f) { b.mood = "Apuntando"; return; }
        if (!Brain.upright(b) || b.dir != (dx > 0 ? 1 : -1)) return;
        Particle hand = b.farmF.b;
        float mx = hand.x + b.aimX * 14f * b.scale, my = hand.y + b.aimY * 14f * b.scale;
        float dist = (float) Math.sqrt((tx - mx) * (tx - mx) + (ty - my) * (ty - my));
        if (!clearShot(w, b, mx, my, b.aimX, b.aimY, dist + 30f)) { b.mood = "¡Civil en la línea!"; return; }
        float spread = (w.rnd.nextFloat() - 0.5f) * 0.05f;
        float ca = (float) Math.cos(spread), sa = (float) Math.sin(spread);
        shoot(w, b, mx, my, b.aimX * ca - b.aimY * sa, b.aimX * sa + b.aimY * ca, 24f, 780f);
        b.ammo--;
        b.shotCd = 0.24f + w.rnd.nextFloat() * 0.12f;
        if (b.ammo <= 0) b.reloadT = 1.8f;
        b.farmF.b.addVel(-b.aimX * 160f, -b.aimY * 160f - 40f);   // recoil
        b.mood = "Disparando";
    }

    private static boolean needsCare(Body o) {
        if (!o.organic() || o.infected > 0f || o.bitten) return false;
        if (o.dead) return !o.neck.dead && o.acid <= 0f;
        if (o.blood < o.maxBlood * 0.8f || o.ko > 0.5f || o.legsBroken() || o.headDmg > 20f) return true;
        for (int i = 0; i < o.ps.size(); i++) if (o.ps.get(i).bleed > 0f) return true;
        Stick[] parts = o.parts();
        for (Stick s : parts) if (s.fractured || s.damage > 40f) return true;
        return false;
    }

    private static void medic(World w, Body b) {
        b.panic = 0f;
        Body z = nearestHostile(w, b.chest.x, b.chest.y, 320f);
        if (z != null || danger(w, b, false, true) != 0) { flee(w, b, true, true); b.work = 0f; return; }
        Body t = b.target;
        if (t == null || t.removed || !needsCare(t)) {
            t = null;
            float bd = 1800f;
            for (int i = 0; i < w.bodies.size(); i++) {
                Body o = w.bodies.get(i);
                if (o == b || !o.isHuman() || !needsCare(o)) continue;
                float d = Math.abs(o.pelvis.x - b.pelvis.x) + Math.abs(o.pelvis.y - b.pelvis.y) * 0.5f;
                if (o.dead) d *= 1.3f;              // the living first
                if (d < bd) { bd = d; t = o; }
            }
            if (t != b.target) b.work = 0f;
            b.target = t;
        }
        if (t == null) { b.mood = "Esperando pacientes"; b.work = 0f; return; }
        // walk to the nearest part of the patient (a body lying on the floor can block the way to its chest)
        Particle spot = t.chest;
        Particle[] cand = {t.chest, t.pelvis, t.head};
        for (Particle q : cand) if (Math.abs(q.x - b.pelvis.x) < Math.abs(spot.x - b.pelvis.x)) spot = q;
        float dx = spot.x - b.pelvis.x;
        if (Math.abs(dx) > 84f * b.scale || Math.abs(spot.y - b.pelvis.y) > 150f) {
            b.walk = dx > 0 ? 1 : -1;
            b.mood = t.dead ? "Corre a reanimar" : "Va a curar";
            b.panic = t.dead ? 1f : 0f;
            return;
        }
        face(b, spot.x);
        aimAt(b, spot.x, spot.y);
        b.work += THINK;
        int col = 0xFF6dff8a;
        if (t.dead) {
            b.mood = "Reanimando (RCP)";
            t.chest.addVel(0, 160f);                 // chest compressions
            if (b.work > 4f) {
                w.heal(t);
                t.blood = t.maxBlood * 0.5f;
                t.dead = false; t.ko = 2.5f; t.tone = 0f;
                w.stats.revives++;
                w.fx.add(Fx.GLOW, t.chest.x, t.chest.y, 0, 0, 0.5f, 60f, 0xFF7fd1ff);
                if (w.sfx != null) w.sfx.pop();
                b.work = 0f; b.target = null;
                return;
            }
            col = 0xFF7fd1ff;
        } else {
            b.mood = "Curando";
            t.blood = Math.min(t.maxBlood, t.blood + 9f);
            for (int i = 0; i < t.ps.size(); i++) t.ps.get(i).bleed = Math.max(0f, t.ps.get(i).bleed - 2f);
            if (b.work > 2.5f) {                     // splints and bandages
                w.heal(t);
                w.stats.heals++;
                b.work = 0f; b.target = null;
                if (w.sfx != null) w.sfx.pop();
            }
        }
        w.fx.add(Fx.SPARK, t.chest.x + w.fx.rr(14), t.chest.y + w.fx.rr(14), w.fx.rr(40), -60f - w.fx.rf() * 50f, 0.5f, 2.2f, col);
    }

    private static void firefighter(World w, Body b) {
        b.panic = 0f;
        if (flee(w, b, true, false)) return;
        Body t = null;
        float bd = 1800f;
        for (int i = 0; i < w.bodies.size(); i++) {
            Body o = w.bodies.get(i);
            if (!o.burning || o == b || o.ps.isEmpty()) continue;
            float d = Math.abs(o.cx - b.pelvis.x) + Math.abs(o.cy - b.pelvis.y) * 0.5f;
            if (d < bd) { bd = d; t = o; }
        }
        b.target = t;
        if (t == null) { b.mood = "De guardia"; return; }
        float dx = t.cx - b.chest.x;
        if (Math.abs(dx) > 210f + t.rad) { b.walk = dx > 0 ? 1 : -1; b.panic = 1f; b.mood = "¡Al fuego!"; return; }
        face(b, t.cx);
        aimAt(b, t.cx, t.cy);
        b.mood = "Apagando el fuego";
    }

    /** Water from the hose: drops along the aim; anything burning near the stream cools down. */
    private static void spray(World w, Body b, float dt) {
        Particle h = b.farmF.b;
        float s = b.scale;
        int n = (int) (60f * dt + w.rnd.nextFloat());
        for (int k = 0; k < n; k++) {
            float sp = 620f + w.fx.rf() * 160f;
            w.fx.add(Fx.SPARK, h.x, h.y, b.aimX * sp + w.fx.rr(50), b.aimY * sp - 90f + w.fx.rr(50), 0.45f, 2.6f * s, 0xFF8fd3ff);
        }
        float reach = 300f * s;
        for (int i = 0; i < w.bodies.size(); i++) {
            Body o = w.bodies.get(i);
            if (!o.burning) continue;
            // distance from the body to the stream (a segment from the nozzle)
            float rx = o.cx - h.x, ry = o.cy - h.y;
            float t = Math.max(0f, Math.min(reach, rx * b.aimX + ry * b.aimY));
            float ex = rx - b.aimX * t, ey = ry - b.aimY * t;
            if (ex * ex + ey * ey > (o.rad + 30f) * (o.rad + 30f)) continue;
            o.burnT -= dt * 12f;
            o.fuse = o.explosive && o.fuse > 0f && o.type != Body.GRENADE && o.type != Body.DYNAMITE ? -1f : o.fuse;
            if (w.rnd.nextFloat() < dt * 12f) w.fx.smoke(o.cx + w.fx.rr(o.rad), o.cy - 10f, 8f, 0xFFd8dde3);
            if (o.burnT <= 0.5f) { o.burning = false; o.burnT = 0f; w.stats.fires++; }
        }
    }

    private static void thug(World w, Body b) {
        b.panic = 0f;
        if (b.blood < b.maxBlood * 0.35f) { if (!flee(w, b, true, true)) { b.mood = "Malherido"; } else b.mood = "Huyendo"; return; }
        if (flee(w, b, false, true)) return;
        Body z = nearestHostile(w, b.chest.x, b.chest.y, 260f);
        if (z != null && z.kind == Body.K_ZOMBIE) { b.walk = z.pelvis.x > b.pelvis.x ? -1 : 1; b.panic = 1f; b.mood = "Huyendo"; return; }
        Body t = nearestVictim(w, b, 1300f);
        b.target = t;
        if (t == null) { b.mood = "Aburrido"; return; }
        float dx = t.chest.x - b.chest.x;
        if (Math.abs(dx) > 58f * b.scale || Math.abs(t.chest.y - b.chest.y) > 110f) {
            b.walk = dx > 0 ? 1 : -1;
            b.panic = Math.abs(dx) < 320f ? 1f : 0f;   // charges the last few metres
            b.mood = "Buscando pelea";
            return;
        }
        face(b, t.chest.x);
        b.mood = "Pegando";
        if (b.punchT <= 0f && b.shotCd <= 0f && armsWork(b)) strike(w, b, t, 9f + w.rnd.nextFloat() * 5f, 560f);
    }

    /** A punch (or rifle butt): the front arm snaps out and the victim takes a knock. */
    private static void strike(World w, Body b, Body t, float dmg, float power) {
        b.punchT = 0.28f;
        b.shotCd = 0.65f + w.rnd.nextFloat() * 0.3f;
        aimAt(b, t.head.x, t.head.y + 10f);
        Particle hit = w.rnd.nextFloat() < 0.55f ? t.head : t.chest;
        hit.addVel(b.dir * power, -power * 0.3f);
        t.chest.addVel(b.dir * power * 0.4f, -60f);
        w.hit(t, dmg, hit.x, hit.y, false);
        if (t.kind == Body.K_ROBOT) w.fx.sparks(hit.x, hit.y, 4, 0xFFffd25a);
        w.fx.add(Fx.GLOW, hit.x, hit.y, 0, 0, 0.08f, 18f, 0xFFffffff);
        if (w.sfx != null) w.sfx.thud(0.6f);
        w.rumble += 0.04f;
    }

    // ----------------------------------------------------------------------------- shooting ---

    /** True when nothing friendly (a non-hostile person) is in the line of fire before the target. */
    static boolean clearShot(World w, Body self, float x, float y, float dx, float dy, float len) {
        java.util.ArrayList<World.Hit> hs = w.ray(x, y, dx, dy, len);
        for (int i = 0; i < hs.size(); i++) {
            Body o = hs.get(i).body;
            if (o == self) continue;
            if (o.isHuman()) return o.hostile() || o.dead;
            if (o.machine() || o.type == Body.PLATFORM) return false;   // don't shoot our own hardware / walls
        }
        return true;
    }

    static void shoot(World w, Body self, float x, float y, float dx, float dy, float dmg, float impulse) {
        w.rayIgnore = self;
        java.util.ArrayList<World.Hit> hs = w.ray(x, y, dx, dy, 3000f);
        Body first = hs.isEmpty() ? null : hs.get(0).body;
        boolean wasAlive = first != null && first.isHuman() && !first.dead;
        w.bullet(x, y, dx, dy, dmg, impulse, 1);
        w.rayIgnore = null;
        if (wasAlive && first.dead) w.stats.aiKills++;
        w.fx.add(Fx.GLOW, x, y, 0, 0, 0.06f, 15f, 0xFFfff0b0);
        if (w.sfx != null) w.sfx.shot();
        w.rumble += 0.02f;
    }

    // ----------------------------------------------------------------------------- machines ---

    /** Damage to a turret or a drone; at zero it breaks down (sparks, smoke, falls). */
    static void damage(World w, Body m, float dmg) {
        if (m.dead) return;
        m.blood -= dmg;
        m.flashT = 0.12f;
        w.fx.sparks(m.cx, m.cy, 4, 0xFFffd25a);
        if (m.blood <= 0f) {
            m.dead = true;
            m.thrust = false;
            m.mood = "Averiado";
            w.fx.sparks(m.cx, m.cy, 18, 0xFFffd25a);
            w.fx.smoke(m.cx, m.cy, 16f, 0xFF3c3c3c);
            w.fx.add(Fx.GLOW, m.cx, m.cy, 0, 0, 0.25f, 70f, 0xFFffb060);
            if (w.sfx != null) w.sfx.boom(0.15f);
            w.stats.machinesDown++;
        }
    }

    /** Machine brains, once per frame: pick targets, shoot, decide where to fly. */
    static void machineThink(World w, Body m, float dt) {
        if (m.shotCd > 0f) m.shotCd -= dt;
        if (m.reloadT > 0f) { m.reloadT -= dt; if (m.reloadT <= 0f) m.ammo = 12; }
        if (m.dead) {
            if (w.rnd.nextFloat() < dt * 6f) w.fx.smoke(m.cx, m.cy - 6f, 7f, 0xFF3c3c3c);
            return;
        }
        if (!m.thrust) { m.target = null; m.mood = "Apagado"; return; }
        m.thinkT -= dt;
        if (m.type == Body.TURRET) turret(w, m, dt);
        else drone(w, m, dt);
    }

    private static void turret(World w, Body m, float dt) {
        if (m.ps.size() < 4) { m.mood = "Roto"; return; }
        Particle c = m.ps.get(2), z = m.ps.get(3);
        if (m.thinkT <= 0f) {
            m.thinkT = 0.2f;
            Body t = nearestHostile(w, c.x, c.y, 1500f);
            m.target = t != null && clearShot(w, m, c.x, c.y, t.chest.x - c.x, t.chest.y - c.y,
                    (float) Math.hypot(t.chest.x - c.x, t.chest.y - c.y)) ? t : null;
        }
        Body t = m.target;
        if (t != null && (t.removed || !t.hostile())) t = m.target = null;
        if (t == null) {                                    // slow sweep, looking around
            m.work += dt;
            float a = (float) Math.sin(m.work * 0.8f) * 0.7f;
            m.aimX = m.dir * (float) Math.cos(a); m.aimY = -(float) Math.abs(Math.sin(a)) * 0.6f;
            float l = (float) Math.sqrt(m.aimX * m.aimX + m.aimY * m.aimY);
            m.aimX /= l; m.aimY /= l;
            m.mood = "Vigilando";
            return;
        }
        float dx = t.chest.x - c.x, dy = t.chest.y - c.y, l = (float) Math.sqrt(dx * dx + dy * dy);
        if (l < 1f) return;
        m.aimX = dx / l; m.aimY = dy / l;
        m.dir = dx > 0 ? 1 : -1;
        m.mood = "Objetivo fijado";
        // fire when the barrel actually points at the target
        float bx = z.x - c.x, by = z.y - c.y, bl = (float) Math.sqrt(bx * bx + by * by);
        if (bl < 1f) return;
        bx /= bl; by /= bl;
        if (bx * m.aimX + by * m.aimY < 0.985f || m.shotCd > 0f || m.reloadT > 0f) return;
        shoot(w, m, z.x + bx * 8f, z.y + by * 8f, bx, by, 16f, 520f);
        z.addVel(-bx * 220f, -by * 220f);
        m.shotCd = 0.13f;
        if (--m.ammo <= 0) { m.reloadT = 0.9f; m.mood = "Recargando"; }
    }

    private static void drone(World w, Body m, float dt) {
        if (m.ps.size() < 2) { m.mood = "Roto"; m.dead = true; return; }
        Particle a = m.ps.get(0), z = m.ps.get(1);
        float cx = (a.x + z.x) * 0.5f, cy = (a.y + z.y) * 0.5f;
        if (m.thinkT <= 0f) {
            m.thinkT = 0.25f;
            m.target = nearestHostile(w, cx, cy, 1700f);
        }
        Body t = m.target;
        if (t != null && (t.removed || !t.hostile())) t = m.target = null;
        if (t == null) {
            m.work += dt;
            m.goalX = m.param + (float) Math.sin(m.work * 0.45f) * 220f;
            m.goalY = w.floorY - 330f + (float) Math.sin(m.work * 1.3f) * 25f;
            m.mood = "Patrullando";
        } else {
            float side = cx < t.chest.x ? -1f : 1f;
            m.goalX = t.chest.x + side * 240f;
            m.goalY = t.head.y - 190f;
            m.mood = "Atacando";
            float dx = t.chest.x - cx, dy = t.chest.y - cy, l = (float) Math.sqrt(dx * dx + dy * dy);
            if (l > 1f && l < 900f && m.shotCd <= 0f && m.reloadT <= 0f) {
                m.aimX = dx / l; m.aimY = dy / l;
                if (clearShot(w, m, cx, cy + 10f, m.aimX, m.aimY, l + 20f)) {
                    shoot(w, m, cx + m.aimX * 14f, cy + 10f + m.aimY * 14f, m.aimX, m.aimY, 12f, 380f);
                    m.shotCd = 0.32f;
                    if (--m.ammo <= 0) m.reloadT = 1.4f;
                }
            }
        }
        m.goalX = Math.max(w.wallL + 60f, Math.min(w.wallR - 60f, m.goalX));
        m.goalY = Math.max(w.ceilY + 70f, Math.min(w.floorY - 90f, m.goalY));
    }

    /** Drone flight controller, once per physics step: altitude, position and level (PD loops). */
    static void fly(World w, Body m) {
        if (m.ps.size() < 2 || m.dead || !m.thrust) return;
        Particle a = m.ps.get(0), z = m.ps.get(1);
        float cx = (a.x + z.x) * 0.5f, cy = (a.y + z.y) * 0.5f;
        float vx = (a.vx() + z.vx()) * 0.5f, vy = (a.vy() + z.vy()) * 0.5f;
        float axc = 9f * (m.goalX - cx) - 4.5f * vx;
        float ayc = 14f * (m.goalY - cy) - 6f * vy - w.gravity * w.gravMul * m.gravScale;
        axc = Math.max(-1400f, Math.min(1400f, axc));
        ayc = Math.max(-5200f, Math.min(5200f, ayc));
        // keep it level, tilted a little into the motion
        float tilt = Math.max(-14f, Math.min(14f, -axc * 0.012f));
        float rel = (z.y - a.y) - tilt;
        float rv = z.vy() - a.vy();
        float lev = -60f * rel - 10f * rv;
        a.addVel(axc * World.H, (ayc - lev * 0.5f) * World.H);
        z.addVel(axc * World.H, (ayc + lev * 0.5f) * World.H);
    }

    /** Turret servo, once per physics step: swings the barrel toward the aim direction. */
    static void servo(World w, Body m) {
        if (m.ps.size() < 4 || m.dead || !m.thrust) return;
        Particle c = m.ps.get(2), z = m.ps.get(3);
        // the tripod grips the floor while it stands upright, so recoil and the servo can't tip it over
        Particle fl = m.ps.get(0), fr = m.ps.get(1);
        boolean upright = c.y < Math.min(fl.y, fr.y) - 15f;
        if (upright) {
            if (w.frame - fl.groundFrame < 3 && fl.invM > 0f) fl.px = fl.x;
            if (w.frame - fr.groundFrame < 3 && fr.invM > 0f) fr.px = fr.x;
        }
        float len = 44f;
        float tx = c.x + m.aimX * len, ty = c.y + m.aimY * len;
        float vx = z.vx() - c.vx(), vy = z.vy() - c.vy();
        float ax = 260f * (tx - z.x) - 26f * vx, ay = 260f * (ty - z.y) - 26f * vy - w.gravity * w.gravMul;
        float l = (float) Math.sqrt(ax * ax + ay * ay);
        if (l > 30000f) { ax *= 30000f / l; ay *= 30000f / l; }
        z.addVel(ax * World.H, ay * World.H);
    }
}

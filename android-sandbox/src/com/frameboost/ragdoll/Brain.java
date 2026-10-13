package com.frameboost.ragdoll;

/**
 * Active ragdolls: conscious people keep their balance, stand back up after being knocked out,
 * walk, run away from zombies; zombies hunt people and their bites are contagious.
 * Muscles are soft position targets applied only while the feet have support.
 */
final class Brain {
    private Brain() { }

    static boolean supported(World w, Body b) {
        int f = w.frame;
        return f - b.shinF.b.groundFrame < 10 || f - b.shinB.b.groundFrame < 10;
    }

    static boolean upright(Body b) {
        float dy = b.pelvis.y - b.chest.y, dx = b.chest.x - b.pelvis.x;
        return dy > Math.abs(dx) * 2.2f;
    }

    /** Can this body use its muscles at all (alive, whole, conscious)? */
    static boolean able(Body b) {
        return b.isHuman() && !b.dead && b.ko <= 0f && !b.neck.dead
                && !b.jHipF.dead && !b.jHipB.dead && !b.jKneeF.dead && !b.jKneeB.dead;
    }

    /** Called once per frame: perception, decisions, timers. */
    static void think(World w, Body b, float dt) {
        if (b.ko > 0f) b.ko -= dt;
        if (b.biteCd > 0f) b.biteCd -= dt;
        if (b.dead && b.bitten && b.infected < 0f && b.kind == Body.K_HUMAN) b.infected = 3f;
        if (b.infected > 0f) {
            b.infected -= dt;
            if (b.infected <= 0f) reanimate(w, b);
        }
        b.held = false;
        for (int i = 0; i < w.grabs.size(); i++) if (w.grabs.get(i).p.body == b) b.held = true;
        float target = able(b) && !b.held ? Math.min(1f, b.blood / 45f) : 0f;
        if (b.burning) target *= 0.35f;
        if (target > b.tone) b.tone = Math.min(target, b.tone + dt * 0.7f);
        else b.tone = target;
        // balance: once standing, leaning too far means falling over instead of magically holding on
        if (target > 0f && b.tone > 0.9f) {
            float dy = b.pelvis.y - b.chest.y, dx = Math.abs(b.chest.x - b.pelvis.x);
            if (dy > dx * 2.2f) b.stood = true;
            else if (b.stood && (dy < 0 || dx > dy * 1.45f)) { b.ko = Math.max(b.ko, 1.1f); b.tone = 0f; b.stood = false; }
        }
        if (b.ko > 0f) b.stood = false;

        // struggling while held or burning
        if ((b.held || b.burning) && !b.dead && w.rnd.nextFloat() < dt * 6f) {
            Particle[] limbs = {b.farmF.b, b.farmB.b, b.shinF.b, b.shinB.b};
            Particle p = limbs[w.rnd.nextInt(4)];
            p.addVel((w.rnd.nextFloat() - 0.5f) * 500f, (w.rnd.nextFloat() - 0.5f) * 500f);
        }

        b.thinkT -= dt;
        if (b.thinkT > 0f) return;
        b.thinkT = 0.25f + w.rnd.nextFloat() * 0.15f;
        b.walk = 0;
        if (!able(b) || b.tone < 0.6f) return;
        if (b.kind == Body.K_ZOMBIE) {
            Body prey = nearest(w, b, false, 1400f);
            if (prey != null) {
                float dx = prey.pelvis.x - b.pelvis.x;
                if (Math.abs(dx) > 34f * b.scale) b.walk = dx > 0 ? 1 : -1;
                if (Math.abs(dx) < 70f * b.scale && Math.abs(prey.pelvis.y - b.pelvis.y) < 120f && b.biteCd <= 0f) bite(w, b, prey);
            }
        } else if (b.kind == Body.K_HUMAN) {
            Body z = nearest(w, b, true, 520f);
            if (z != null) b.walk = z.pelvis.x > b.pelvis.x ? -1 : 1;
            else if (b.burning) b.walk = w.rnd.nextBoolean() ? 1 : -1;
            b.panic = z != null || b.burning ? 1f : 0f;
        }
        // stop at the walls
        if (b.walk < 0 && b.pelvis.x < w.wallL + 80) b.walk = 0;
        if (b.walk > 0 && b.pelvis.x > w.wallR - 80) b.walk = 0;
        // face the way we walk (only when standing straight, so joint limits stay happy)
        if (b.walk != 0 && b.walk != b.dir && upright(b)) {
            turn(b);
        }
    }

    /** Turns around: mirrors the pose about the hips (velocities kept), then flips the joint limits. */
    static void turn(Body b) {
        float cx = b.pelvis.x;
        for (int i = 0; i < b.ps.size(); i++) {
            Particle p = b.ps.get(i);
            float vx = p.x - p.px;
            p.x = 2f * cx - p.x;
            p.px = p.x - vx;
        }
        b.dir = -b.dir;
        for (int i = 0; i < b.ls.size(); i++) b.ls.get(i).dir = b.dir;
    }

    private static Body nearest(World w, Body self, boolean zombies, float range) {
        Body best = null;
        float bd = range;
        for (int i = 0; i < w.bodies.size(); i++) {
            Body o = w.bodies.get(i);
            if (o == self || !o.isHuman() || o.dead) continue;
            if (zombies != (o.kind == Body.K_ZOMBIE)) continue;
            if (!zombies && (o.kind == Body.K_SKELETON || o.infected > 0f)) continue;
            float d = Math.abs(o.pelvis.x - self.pelvis.x) + Math.abs(o.pelvis.y - self.pelvis.y) * 0.5f;
            if (d < bd) { bd = d; best = o; }
        }
        return best;
    }

    private static void bite(World w, Body z, Body v) {
        z.biteCd = 0.9f;
        if (v.kind == Body.K_HUMAN) v.bitten = true;
        Particle target = v.chest;
        target.bleed = Math.max(target.bleed, 4f);
        w.hit(v, 7f, target.x, target.y, true);
        target.addVel(z.dir * 260f, -120f);
        z.head.addVel(z.dir * 200f, 60f);
        if (v.kind == Body.K_HUMAN && v.dead && v.infected < 0f) v.infected = 3f;
        else if (v.kind == Body.K_HUMAN && v.blood < 35f && v.infected < 0f) { v.infected = 4f; }
    }

    /** A bitten person rises again as a zombie. */
    private static void reanimate(World w, Body b) {
        if (b.neck.dead || b.removed) return;
        b.kind = Body.K_ZOMBIE;
        b.skin = 0xFF7fa65a;
        b.shirt = Renderer.mix(b.shirt, 0xFF4a473a, 0.6f);
        b.dead = false;
        b.blood = 120f;
        b.ko = 1.5f;
        b.tone = 0f;
        for (int i = 0; i < b.ss.size(); i++) {
            Stick s = b.ss.get(i);
            if (s == b.torso || s == b.uarmF || s == b.uarmB) s.color = b.shirt;
            if (s == b.farmF || s == b.farmB || s == b.neck) s.color = b.skin;
        }
        w.fx.smoke(b.head.x, b.head.y, 12, 0xFF5aa02a);
        if (w.sfx != null) w.sfx.splat();
    }

    // ------------------------------------------------------------------------- muscles ----

    /** Muscles: damped springs on velocity (PD control) + gravity compensation. Once per physics step. */
    static void muscles(World w) {
        for (int i = 0; i < w.bodies.size(); i++) {
            Body b = w.bodies.get(i);
            if (!b.isHuman() || b.tone <= 0.02f || b.ps.isEmpty()) continue;
            if (!supported(w, b)) continue;
            pose(w, b, b.tone * (b.kind == Body.K_ROBOT ? 1.15f : 1f));
        }
    }

    private static float refVx, refVy;

    /** a = kp*(target - x) - kd*(v - vRef) - g*ff, clamped, applied as a velocity change. */
    private static void spring(World w, Particle p, float tx, float ty, float kp, float kd, float ff) {
        if (p.invM == 0f) return;
        float vx = p.vx() - refVx, vy = p.vy() - refVy;
        float ax = kp * (tx - p.x) - kd * vx;
        float ay = kp * (ty - p.y) - kd * vy - w.gravity * w.gravMul * ff;
        float am = 9000f;
        float l = (float) Math.sqrt(ax * ax + ay * ay);
        if (l > am) { ax *= am / l; ay *= am / l; }
        p.addVel(ax * World.H, ay * World.H);
    }

    /** A foot on the ground grips it (static friction): no sliding while it carries the body. */
    private static void plant(World w, Particle f) {
        if (w.frame - f.groundFrame < 3 && f.invM > 0f) f.px = f.x;
    }

    private static void pose(World w, Body b, float str) {
        float s = b.scale;
        Particle fA = b.shinF.b, fB = b.shinB.b;
        boolean aDown = w.frame - fA.groundFrame < 10, bDown = w.frame - fB.groundFrame < 10;
        float baseY = aDown && bDown ? Math.max(fA.y, fB.y) : (aDown ? fA.y : fB.y);
        float fx = (fA.x + fB.x) * 0.5f;
        refVx = 0f;
        refVy = 0f;
        float kp = 150f * str, kd = 22f * str, ff = str;
        float lean = b.walk * (b.panic > 0 ? 24f : 14f) * s;
        float px = fx + lean, py = baseY - 78f * s;
        if (b.kind == Body.K_ZOMBIE) { lean += b.dir * 5f * s; py += 4f * s; }
        spring(w, b.pelvis, px, py, kp, kd, ff);
        spring(w, b.chest, px + lean * 0.6f, py - 52f * s, kp * 1.2f, kd, ff);
        spring(w, b.head, px + lean * 0.8f + b.dir * 2f * s, py - 52f * s - b.neck.rest, kp * 0.8f, kd, ff);
        // thighs: carry their own weight so the knees don't buckle
        spring(w, b.thighF.b, (b.thighF.b.x + fx) * 0.5f, py + 40f * s, kp * 0.5f, kd, ff);
        spring(w, b.thighB.b, (b.thighB.b.x + fx) * 0.5f, py + 40f * s, kp * 0.5f, kd, ff);
        // arms: zombies reach forward, panicking people flail up, others hang
        if (b.kind == Body.K_ZOMBIE) {
            spring(w, b.farmF.b, b.chest.x + b.dir * 55f * s, b.chest.y + 4f * s, kp * 0.3f, kd * 0.5f, ff);
            spring(w, b.farmB.b, b.chest.x + b.dir * 52f * s, b.chest.y + 10f * s, kp * 0.3f, kd * 0.5f, ff);
        } else if (b.panic > 0f) {
            spring(w, b.farmF.b, b.chest.x + b.dir * 10f * s, b.chest.y - 50f * s, kp * 0.25f, kd * 0.4f, ff);
            spring(w, b.farmB.b, b.chest.x - b.dir * 10f * s, b.chest.y - 48f * s, kp * 0.25f, kd * 0.4f, ff);
        }
        if (b.walk != 0) {
            float dur = b.panic > 0 ? 0.22f : (b.kind == Body.K_ZOMBIE ? 0.42f : 0.32f);
            b.stepT -= World.H;
            if (b.stepT <= 0f) { b.stepT = dur; b.stepFoot ^= 1; }
            float ph = 1f - b.stepT / dur;
            Particle sf = b.stepFoot == 0 ? fA : fB;
            float lift = (float) Math.sin(ph * Math.PI) * 18f * s;
            spring(w, sf, b.pelvis.x + b.walk * (b.panic > 0 ? 62f : 42f) * s, baseY - lift, kp * 1.3f, kd, ff * 0.5f);
            plant(w, sf == fA ? fB : fA);
        } else {
            plant(w, fA);
            plant(w, fB);
            // stance: feet a little apart under the hips
            spring(w, fA, fx + b.dir * 9f * s, fA.y, kp * 0.3f, kd * 0.5f, 0f);
            spring(w, fB, fx - b.dir * 9f * s, fB.y, kp * 0.3f, kd * 0.5f, 0f);
        }
    }
}

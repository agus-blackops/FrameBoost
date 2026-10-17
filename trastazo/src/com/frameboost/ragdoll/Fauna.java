package com.frameboost.ragdoll;

/**
 * Animals (dog, pig, rat): one rigid body with a head and two feet, kept upright like a cat and walked by a
 * velocity servo while the feet touch something. They wander, run from (or bark at) zombies and thugs, eat
 * the dead piece by piece, and some seconds after a meal they leave a poop behind.
 */
final class Fauna {
    private Fauna() { }

    static final int DOG = 0, PIG = 1, RAT = 2;
    static final float[] WALK = {150f, 95f, 190f}, RUN = {330f, 190f, 320f};
    static final float[] PITCH = {1f, 0.6f, 1.9f};
    static final float[] BITE = {0.65f, 0.5f, 0.45f};
    static final float[] CHEW = {34f, 40f, 18f};

    static float wrap(float a) {
        while (a > Math.PI) a -= 2 * Math.PI;
        while (a < -Math.PI) a += 2 * Math.PI;
        return a;
    }

    static float headX(Prop a) { Shape h = a.main().shapes.get(1); return h.cx; }
    static float headY(Prop a) { Shape h = a.main().shapes.get(1); return h.cy; }

    static void think(World w, Prop a, float dt) {
        RB b = a.main();
        int k = a.type - Items.DOG;
        float[] d = a.dims();
        if (a.barkT > 0f) a.barkT -= dt;
        if (a.eatT > 0f) a.eatT -= dt;
        if (a.dead) { a.mood = "Muerto"; a.meal = null; return; }
        boolean grounded = w.sim.stepNo - b.supportStep < 6;
        float ang = wrap(b.a);
        // land on its feet and stay level
        if (!b.frozen) b.w += (-ang * 30f - b.w * 6f) * dt * (grounded ? 1f : 0.5f);
        a.legPhase += Math.abs(b.vx) * dt * (k == RAT ? 0.3f : 0.1f);
        a.hunger = Math.min(1f, a.hunger + dt * 0.02f);
        if (a.digestT >= 0f) {
            a.digestT -= dt;
            if (a.digestT <= 0f) { poop(w, a); return; }
        }
        float want = 0f;
        Person threat = threat(w, a);
        if (threat != null) {
            int away = threat.cx() < b.x ? 1 : -1;
            if (k == DOG) {
                a.turn(-away);
                a.mood = "Ladrando";
                if (a.barkT <= 0f) { a.barkT = 0.45f + w.rnd.nextFloat() * 0.3f; if (w.sfx != null) w.sfx.call(PITCH[k]); b.vy -= 90f; }
                want = Math.abs(threat.cx() - b.x) > 160f ? -away * WALK[k] * 0.6f : 0f;
            } else {
                a.turn(away);
                a.mood = "Huyendo";
                want = away * RUN[k];
            }
            a.meal = null;
        } else if (a.stomach < 4 && a.hunger > 0.25f && findMeal(w, a)) {
            Person p = a.meal;
            float hx = headX(a), hy = headY(a);
            // always go for the piece nearest to the mouth (the first choice may be blocked by the rest of the body)
            float best = Float.MAX_VALUE;
            for (int i = 0; i < Person.NPARTS; i++) {
                if (p.gibbed[i] || p.rb[i].frozen) continue;
                float ex = p.rb[i].x - hx, ey = (p.rb[i].y - hy) * 0.3f, dd = ex * ex + ey * ey;
                if (dd < best) { best = dd; a.mealPart = i; }
            }
            RB part = p.rb[a.mealPart];
            float reach = d[2] + part.shapes.get(0).reach() * 0.6f + 16f;
            float dx = part.x - hx;
            // the head dips to the ground to eat, so height matters little
            // pushing against the body without getting closer counts as being there (a limb can be in the way)
            if (Math.abs(b.vx) < 25f && Math.abs(dx) < reach + 60f) a.trim += dt; else a.trim = 0f;
            if ((Math.abs(dx) < reach || a.trim > 1f) && Math.abs(part.y - hy) < 85f) {
                a.mood = "Comiendo";
                a.eatT = 0.3f;
                a.biteT -= dt;
                if (a.biteT <= 0f) { a.biteT = BITE[k]; bite(w, a, p, a.mealPart, hx, hy); }
            } else {
                int dir = part.x > b.x ? 1 : -1;
                if (Math.abs(dx) > d[2] + 10f) a.turn(dir);
                a.mood = "Va a comer";
                want = Math.signum(dx) * (Math.abs(dx) < 90f ? WALK[k] * 0.5f : RUN[k] * 0.8f);
            }
        } else {
            a.meal = null;
            a.thinkT -= dt;
            if (a.thinkT <= 0f) {
                a.thinkT = 1.5f + w.rnd.nextFloat() * 3f;
                if (w.rnd.nextFloat() < 0.35f) a.work = 0f;
                else {
                    int dir = w.rnd.nextBoolean() ? 1 : -1;
                    a.turn(dir);
                    a.work = dir * WALK[k] * (0.6f + w.rnd.nextFloat() * 0.4f);
                }
                if (k == DOG && w.rnd.nextFloat() < 0.1f && w.sfx != null) w.sfx.call(PITCH[k]);
            }
            want = a.work;
            a.mood = want == 0f ? (a.digestT >= 0f ? "Haciendo la digestión" : "Tranquilo") : "Paseando";
        }
        // walls turn it round
        if ((b.x < w.sim.wallL + 60f && want < 0f) || (b.x > w.sim.wallR - 60f && want > 0f)) { want = 0f; a.work = -a.work; a.turn(-a.face); }
        if (grounded && Math.abs(ang) < 0.7f && !b.frozen) b.vx += (want - b.vx) * Math.min(1f, dt * 10f);
        // knocked over on the ground: a little hop to get back up
        if (grounded && Math.abs(ang) > 1.2f && a.thinkT > 0f && w.rnd.nextFloat() < dt * 2f) { b.vy -= 260f; b.w += -Math.signum(ang) * 4f; }
    }

    private static Person threat(World w, Prop a) {
        Person best = null;
        float bd = 420f;
        for (int i = 0; i < w.ents.size(); i++) {
            Ent e = w.ents.get(i);
            if (!(e instanceof Person)) continue;
            Person p = (Person) e;
            if (!p.hostile()) continue;
            float dd = Math.abs(p.cx() - a.cx());
            if (dd < bd && Math.abs(p.cy() - a.cy()) < 260f) { bd = dd; best = p; }
        }
        return best;
    }

    static boolean edible(Person p) {
        return p.dead && !p.removed && (p.organic() || p.kind == Person.ZOMBIE);
    }

    /** Picks (or keeps) a corpse and the piece of it nearest to the mouth. */
    private static boolean findMeal(World w, Prop a) {
        Person p = a.meal;
        if (p == null || !edible(p) || a.mealPart < 0 || p.gibbed[a.mealPart] || p.rb[a.mealPart].frozen) {
            p = null;
            float bd = 1800f;
            for (int i = 0; i < w.ents.size(); i++) {
                Ent e = w.ents.get(i);
                if (!(e instanceof Person) || !edible((Person) e)) continue;
                float dd = Math.abs(e.cx() - a.cx());
                if (dd < bd && Math.abs(e.cy() - a.cy()) < 500f) { bd = dd; p = (Person) e; }
            }
            if (p == null) { a.meal = null; return false; }
            a.meal = p;
            float hx = headX(a), hy = headY(a), best = Float.MAX_VALUE;
            a.mealPart = -1;
            for (int i = 0; i < Person.NPARTS; i++) {
                if (p.gibbed[i] || p.rb[i].frozen) continue;
                float dx = p.rb[i].x - hx, dy = p.rb[i].y - hy, dd = dx * dx + dy * dy;
                if (dd < best) { best = dd; a.mealPart = i; }
            }
            if (a.mealPart < 0) { a.meal = null; return false; }
        }
        return true;
    }

    /** One bite: the piece tears a little; after enough bites it is gone and goes to the stomach. */
    private static void bite(World w, Prop a, Person p, int part, float hx, float hy) {
        int k = a.type - Items.DOG;
        RB r = p.rb[part];
        w.wound(p, part, Person.W_CUT, hx, hy, a.face, 0.4f, 5f);
        p.hp[part] -= CHEW[k];
        // the animal tugs at it
        r.applyImpulse(-a.face * r.m * 60f, -r.m * 40f, hx, hy);
        a.main().vy -= 40f;
        w.bloodStyle(p);
        if (w.fx.gore) w.fx.blood(hx, hy, -a.face * 0.4f, -0.6f, 4);
        if (w.sfx != null) w.sfx.chomp();
        if (p.hp[part] < -Person.maxHp(p, part)) {
            float px = r.x, py = r.y;
            w.gib(p, part, 0f, -1f, false);
            w.stats.eaten++;
            a.stomach++;
            a.hunger = Math.max(0f, a.hunger - 0.3f);
            if (a.digestT < 0f) a.digestT = 6f + w.rnd.nextFloat() * 4f;
            // a bone is left behind now and then
            if (w.rnd.nextFloat() < 0.4f && w.canAdd() && p.kind != Person.ZOMBIE) {
                Prop bone = Prop.block(px, py - 6f, 9f, 2.6f, 0xFFeee8d6, 0.8f, false);
                bone.gib = true;
                bone.register(w.sim);
                bone.main().setAngle(w.rnd.nextFloat() * 3f);
                w.add(bone);
                w.keepChunk(bone);
            }
            a.meal = null;
            boolean left = false;
            for (int i = 0; i < Person.NPARTS; i++) if (!p.gibbed[i]) { left = true; break; }
            if (!left) w.remove(p);
        }
    }

    /** Some time after eating, out it comes. */
    static void poop(World w, Prop a) {
        RB b = a.main();
        float[] d = a.dims();
        int n = a.type == Items.PIG ? 2 : 1;
        for (int i = 0; i < n && w.canAdd(); i++) {
            float x = b.localToWorldX(-a.face * (d[0] + 6f + i * 10f), d[1] * 0.2f), y = b.localToWorldY(-a.face * (d[0] + 6f), d[1] * 0.2f);
            Prop q = Prop.make(w.sim, Items.POOP, x, y, w.rnd);
            if (a.type == Items.RAT) { q.hw = 4f; q.hh = 3f; q.main().shapes.get(0).hl = 4f; q.main().shapes.get(0).hh = 3f; q.main().computeMass(); }
            q.register(w.sim);
            q.main().vx = b.vx - a.face * 60f;
            q.main().vy = -30f;
            w.add(q);
            w.keepChunk(q);
        }
        a.stomach = Math.max(0, a.stomach - 2);
        a.digestT = a.stomach > 0 ? 5f : -1f;
        a.mood = "Haciendo caca";
        w.stats.poops++;
        if (w.sfx != null) w.sfx.fart();
        float tx = b.localToWorldX(-a.face * d[0], 0), ty = b.localToWorldY(-a.face * d[0], 0);
        for (int i = 0; i < 6; i++) w.fx.add(Fx.SMOKE, tx, ty, -a.face * 40f + w.fx.rr(30f), -20f - w.fx.rf() * 30f, 1.4f, 7f + w.fx.rf() * 5f, 0xFF9aa84e);
    }

    /** A poop stinks for a while. */
    static void stink(World w, Prop q, float dt) {
        if (q.stinkT <= 0f) return;
        q.stinkT -= dt;
        if (w.rnd.nextFloat() < dt * 2.5f)
            w.fx.add(Fx.SMOKE, q.cx() + w.fx.rr(6f), q.cy() - q.hh - 2f, w.fx.rr(8f), -28f - w.fx.rf() * 20f, 1.6f, 4f + w.fx.rf() * 3f, 0xFF9aa84e);
    }
}

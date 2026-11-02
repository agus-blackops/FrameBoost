package com.frameboost.ragdoll;

import java.util.ArrayList;

/**
 * Levolution: big scripted events that change the whole map (the idea comes from Battlefield): a controlled
 * demolition that brings a building down with a wave of dust, an earthquake, an air raid and a tornado.
 */
final class Levolution {
    private Levolution() { }

    static final int COLLAPSE = 0, QUAKE = 1, STRIKE = 2, TORNADO = 3;
    static final float QUAKE_TIME = 11.5f, TORNADO_TIME = 16f, STRIKE_TIME = 6.5f;

    static final class Event {
        int type, n;
        float t, x, a, b, c, s;
        ArrayList<Prop> list = new ArrayList<Prop>();
    }

    static int typeOf(int item) { return item - Items.LEVO_COLLAPSE; }

    static boolean active(World w, int type) {
        for (int i = 0; i < w.events.size(); i++) if (w.events.get(i).type == type) return true;
        return false;
    }

    /** Starts an event at world x. Returns the message for the toast ("" means it did not start). */
    static String start(World w, int item, float x) {
        int type = typeOf(item);
        Event e = new Event();
        e.type = type; e.x = x;
        switch (type) {
            case COLLAPSE: {
                Prop anchor = null;
                float best = 1e9f;
                for (int i = 0; i < w.ents.size(); i++) {
                    Ent o = w.ents.get(i);
                    if (!(o instanceof Prop)) continue;
                    Prop q = (Prop) o;
                    if (!q.isStruct || !q.anchor || q.removed) continue;
                    float d = Math.abs(q.cx() - x);
                    if (d < best) { best = d; anchor = q; }
                }
                if (anchor == null || best > 700f) return "No hay ningún edificio cerca: coloca un Edificio o un Rascacielos";
                for (int i = 0; i < w.events.size(); i++) if (w.events.get(i).type == COLLAPSE && Math.abs(w.events.get(i).a - anchor.cx()) < 50f) return "Ya está en marcha";
                e.a = anchor.cx(); e.b = anchor.hw;
                float floor = w.floorY();
                for (int i = 0; i < w.ents.size(); i++) {      // the load-bearing pieces of the ground floor, left to right
                    Ent o = w.ents.get(i);
                    if (!(o instanceof Prop)) continue;
                    Prop q = (Prop) o;
                    if (!q.isStruct || q.anchor || q.removed || q.bodies.isEmpty() || !q.main().frozen) continue;
                    if (Math.abs(q.cx() - e.a) > e.b + 12f || q.cy() < floor - 150f) continue;
                    e.list.add(q);
                }
                if (e.list.isEmpty()) return "Ese edificio ya está sin planta baja";
                for (int i = 1; i < e.list.size(); i++) {
                    Prop k = e.list.get(i);
                    int j = i - 1;
                    while (j >= 0 && e.list.get(j).cx() > k.cx()) { e.list.set(j + 1, e.list.get(j)); j--; }
                    e.list.set(j + 1, k);
                }
                w.banner = "LEVOLUTION · DERRUMBE"; w.bannerT = 3.5f;
                if (w.sfx != null) w.sfx.click();
                break;
            }
            case QUAKE:
                if (active(w, QUAKE)) return "Ya está temblando";
                for (int f = 0; f < 3; f++) {                // fissures open in the floor
                    float fx0 = w.sim.wallL + 150f + w.rnd.nextFloat() * (w.sim.wallR - w.sim.wallL - 300f);
                    for (int j = 0; j < 14; j++) w.fx.stain(fx0 + j * 13f + w.fx.rr(4f), 5f + w.rnd.nextFloat() * 5f, 0xF00d0a08);
                }
                w.banner = "LEVOLUTION · TERREMOTO"; w.bannerT = 3.5f;
                break;
            case STRIKE:
                e.a = x;
                w.banner = "LEVOLUTION · BOMBARDEO"; w.bannerT = 3.5f;
                w.alarm(x, w.floorY(), 2200f, 8f);
                break;
            default:
                e.a = x; e.b = w.rnd.nextBoolean() ? 1f : -1f; e.s = 0.85f + w.rnd.nextFloat() * 0.45f;
                w.banner = "LEVOLUTION · TORNADO"; w.bannerT = 3.5f;
                break;
        }
        w.events.add(e);
        return w.banner;
    }

    static void update(World w, float dt) {
        if (w.bannerT > 0f) w.bannerT -= dt;
        for (int i = w.events.size() - 1; i >= 0; i--) {
            Event e = w.events.get(i);
            e.t += dt;
            boolean done;
            switch (e.type) {
                case COLLAPSE: done = collapse(w, e, dt); break;
                case QUAKE: done = quake(w, e, dt); break;
                case STRIKE: done = strike(w, e, dt); break;
                default: done = tornado(w, e, dt); break;
            }
            if (done) w.events.remove(i);
        }
    }

    // ------------------------------------------------------------------------------ collapse ---

    private static final float WARN = 2.0f;

    private static boolean collapse(World w, Event e, float dt) {
        float floor = w.floorY();
        if (e.t < WARN) {                                    // the structure groans: dust sifts down, cracks open, the charges are armed
            if (w.rnd.nextFloat() < dt * 7f && !e.list.isEmpty()) {
                Prop q = e.list.get(w.rnd.nextInt(e.list.size()));
                if (!q.removed) {
                    w.fx.dust(q.cx() + w.fx.rr(q.hw), q.cy() + w.fx.rr(q.hh), 12f, 1);
                    w.crack(q, q.cx(), q.cy(), 0.15f);
                }
            }
            if (w.rnd.nextFloat() < dt * 5f && w.sfx != null) { if (w.rnd.nextBoolean()) w.sfx.click(); else w.sfx.crunch(); }
            w.fx.shake = Math.max(w.fx.shake, 0.12f * (e.t / WARN));
            return false;
        }
        // two charges every 0.2 s, the blasts a little out of step
        int due = (int) ((e.t - WARN) / 0.2f) * 2 + 2;
        while (e.n < due && e.n < e.list.size()) {
            Prop q = e.list.get(e.n++);
            if (q.removed) continue;
            float px = q.cx(), py = q.cy();
            w.fx.explosion(px, py, 48f + w.rnd.nextFloat() * 24f);
            w.fx.dust(px, py, 46f, 5);
            w.fx.debris(px, py, 10, q.color, 340f);
            if (w.sfx != null) w.sfx.boom(0.35f);
            w.rumble += 0.25f;
            q.impX = px; q.impY = py;
            w.fracture(q, px, py);
        }
        if (e.n < e.list.size()) return false;
        float since = e.t - WARN - (e.list.size() / 2 + 1) * 0.2f;
        if (e.c == 0f && since > 1.2f) {                     // the building is coming down: the dust wave rolls out and a plume climbs
            e.c = 1f;
            float reach = Math.max(760f, e.b * 4.5f);
            for (int k = 0; k < 60; k++) {
                float dir = k % 2 == 0 ? 1f : -1f;
                float off = e.b * 0.6f + 30f + (k / 2) * (reach / 30f);
                float px = e.a + dir * off;
                w.fx.add(Fx.SMOKE, px, floor - 12f - w.rnd.nextFloat() * 40f, dir * (200f + w.rnd.nextFloat() * 320f), -8f - w.rnd.nextFloat() * 30f, 3.5f + w.rnd.nextFloat() * 2.5f, 24f + w.rnd.nextFloat() * 34f, w.rnd.nextFloat() < 0.5f ? 0xFFb8ab9c : 0xFFa39a8e);
            }
            for (int k = 0; k < 44; k++)                     // the plume rising from the pile
                w.fx.add(Fx.SMOKE, e.a + w.fx.rr(e.b), floor - 20f, w.fx.rr(90f), -140f - w.rnd.nextFloat() * 300f, 3f + w.rnd.nextFloat() * 3f, 30f + w.rnd.nextFloat() * 36f, w.rnd.nextFloat() < 0.5f ? 0xFFb8ab9c : 0xFF8d857b);
            w.fx.debris(e.a, floor - 80f, 30, Build.CONCRETE, 520f);
            for (int i = 0; i < w.sim.bodies.size(); i++) {  // the blast of air pushes everything along the ground, harder the nearer
                RB b = w.sim.bodies.get(i);
                if (b.invM == 0f) continue;
                float dx = b.x - e.a;
                if (Math.abs(dx) > reach + 200f || b.y < floor - 420f) continue;
                float f = 1f - Math.abs(dx) / (reach + 200f);
                float push = 1300f * f;
                b.vx += (dx >= 0f ? 1f : -1f) * push;
                b.vy -= push * 0.25f;
                b.w += (dx >= 0f ? 1f : -1f) * f * 3f;
            }
            w.fx.shake = Math.min(1f, w.fx.shake + 0.8f);
            w.rumble += 1f;
            w.stats.collapses++;
            w.alarm(e.a, floor, 1600f, 6f);
            if (w.sfx != null) w.sfx.boom(1f);
        }
        if (e.c > 0f) {                                      // while it falls: shaking ground and a haze that lingers
            w.fx.shake = Math.max(w.fx.shake, 0.35f * Math.max(0f, 1f - (since - 1.2f) / 3f));
            if (w.rnd.nextFloat() < dt * 12f) w.fx.add(Fx.SMOKE, e.a + w.fx.rr(e.b * 1.6f), floor - 16f, w.fx.rr(40f), -20f - w.rnd.nextFloat() * 40f, 5f + w.rnd.nextFloat() * 3f, 36f + w.rnd.nextFloat() * 30f, 0xFFa39a8e);
            if (w.rnd.nextFloat() < dt * 3f && w.sfx != null) w.sfx.crunch();
        }
        return since > 6f;
    }

    // ------------------------------------------------------------------------------ earthquake ---

    private static boolean quake(World w, Event e, float dt) {
        float t = e.t;
        float k = Math.min(1f, t / 1.5f) * Math.min(1f, (8f - t) / 1.5f);   // the main shock
        if (k < 0f) k = 0f;
        if (t > 9f && t < 11.2f) k = Math.max(k, 0.45f * (float) Math.sin(Math.PI * (t - 9f) / 2.2f));   // the aftershock
        float floor = w.floorY();
        w.fx.shake = Math.max(w.fx.shake, 0.5f * k);
        w.rumble += 0.02f;
        float swing = (float) Math.cos(t * 11f) * 1000f * k;                 // the ground slides to and fro
        for (int i = 0; i < w.sim.bodies.size(); i++) {
            RB b = w.sim.bodies.get(i);
            if (b.invM == 0f || b.y < floor - 420f) continue;
            b.vx += swing * dt;
            if (b.vy > -90f && w.rnd.nextFloat() < dt * 2.2f * k) {
                b.vy -= 300f + w.rnd.nextFloat() * 380f * k;
                b.w += (w.rnd.nextFloat() - 0.5f) * 3f * k;
            }
        }
        e.b += dt;
        if (e.b >= 0.14f) {                                  // the structures crack and give way
            e.b = 0f;
            int tries = 0, hit = 0;
            for (int n = w.ents.size(); n > 0 && hit < 3 && tries < 40; tries++) {
                Ent o = w.ents.get(w.rnd.nextInt(n));
                if (o instanceof Person) {                   // people lose their footing
                    Person p = (Person) o;
                    if (!p.dead && k > 0.5f && w.rnd.nextFloat() < 0.25f * k) { p.ko = Math.max(p.ko, 1.3f); p.tone = 0f; }
                    continue;
                }
                if (!(o instanceof Prop)) continue;
                Prop q = (Prop) o;
                if (!q.isStruct || q.removed || q.anchor || q.bodies.isEmpty() || !World.breakable(q)) continue;
                w.crack(q, q.cx() + w.fx.rr(q.hw * 0.6f), q.cy() + w.fx.rr(q.hh * 0.6f), 0.25f * k);
                w.wear(q, World.toughness(q) * 0.2f * k, true);
                if (w.rnd.nextFloat() < 0.5f) w.fx.dust(q.cx(), q.cy(), 14f, 1);
                hit++;
            }
            float fx0 = w.sim.wallL + w.rnd.nextFloat() * (w.sim.wallR - w.sim.wallL);
            if (k > 0.2f) w.fx.dust(fx0, floor - 6f, 22f, 1);
        }
        if (w.rnd.nextFloat() < dt * 1.6f * k && w.sfx != null) w.sfx.crunch();
        return t >= QUAKE_TIME;
    }

    // ------------------------------------------------------------------------------- air raid ---

    private static boolean strike(World w, Event e, float dt) {
        int due = Math.min(24, (int) (e.t / 0.26f));
        while (e.n < due) {
            e.n++;
            float px = e.a + (w.rnd.nextFloat() - 0.5f) * 1200f;
            px = Math.max(w.sim.wallL + 60f, Math.min(w.sim.wallR - 60f, px));
            float lean = (w.rnd.nextFloat() - 0.5f) * 0.3f;
            float size = e.n % 8 == 5 ? 400f : 160f + w.rnd.nextFloat() * 130f;      // a few heavy bombs among the rest
            w.launchBomb(px - lean * 1600f, w.sim.ceilY + 80f, lean, 1f, size);
        }
        if (w.rnd.nextFloat() < dt * 3f && w.sfx != null) w.sfx.launch();
        return e.t >= STRIKE_TIME;
    }

    /** Where a bomb lands: a crater, flung earth, flames and a column of black smoke that stays for a while. */
    static void impact(World w, float x, float y, float R) {
        float floor = w.floorY();
        if (y > floor - R * 0.9f) {
            w.fx.stain(x, R * 1.2f, 0xF0100c09);
            w.fx.stain(x + w.fx.rr(R * 0.3f), R * 0.7f, 0xF0231a12);
        }
        w.fx.dust(x, Math.min(y, floor - 8f), R * 0.5f, 5);
        w.fx.debris(x, y, 16, 0xFF5a4a3a, R * 2.2f);
        for (int k = 0; k < 14; k++)
            w.fx.add(Fx.SMOKE, x + w.fx.rr(R * 0.25f), y - 10f, w.fx.rr(40f), -110f - w.rnd.nextFloat() * 220f, 3f + w.rnd.nextFloat() * 3.5f, R * 0.08f + 10f + w.rnd.nextFloat() * 14f, 0xFF3a3a3a);
        for (int k = 0; k < 6; k++)
            w.fx.add(Fx.FLAME, x + w.fx.rr(R * 0.3f), y - 4f, w.fx.rr(50f), -60f - w.rnd.nextFloat() * 90f, 0.9f + w.rnd.nextFloat() * 0.6f, 10f + w.rnd.nextFloat() * 8f, 0xFFff8a1f);
    }

    // --------------------------------------------------------------------------------- tornado ---

    private static boolean tornado(World w, Event e, float dt) {
        float floor = w.floorY();
        float fade = Math.min(1f, Math.min(e.t / 2f, (TORNADO_TIME - e.t) / 2.5f));
        if (fade < 0f) fade = 0f;
        float str = e.s * fade;
        e.a += e.b * 55f * dt;
        if (e.a < w.sim.wallL + 200f) { e.a = w.sim.wallL + 200f; e.b = 1f; }
        if (e.a > w.sim.wallR - 200f) { e.a = w.sim.wallR - 200f; e.b = -1f; }
        float cx = e.a;
        float time = w.time;
        // the funnel: a thin rope at the ground that widens upwards and sways, with a dark cloud on top
        for (int k = 0; k < 15; k++) {
            float u = (float) Math.pow(w.rnd.nextFloat(), 0.8f);
            float h = 560f * u * (0.4f + 0.6f * fade);
            float r = (7f + 98f * (float) Math.pow(u, 1.7f)) * e.s;
            float sway = (float) Math.sin(time * 1.6f + h * 0.012f) * h * 0.07f;
            float th = w.rnd.nextFloat() * 6.283f;
            int col = u < 0.25f ? (w.rnd.nextBoolean() ? 0xFF8d7a63 : 0xFF77665a) : (w.rnd.nextBoolean() ? 0xFF5a5f66 : 0xFF444850);
            w.fx.add(Fx.SMOKE, cx + sway + (float) Math.sin(th) * r, floor - h, (float) Math.cos(th) * r * 3f, -35f, 0.55f + w.rnd.nextFloat() * 0.45f, 7f + u * 18f, col);
        }
        for (int k = 0; k < 3; k++)                          // the wall cloud
            w.fx.add(Fx.SMOKE, cx + w.fx.rr(190f * e.s), floor - (590f + w.rnd.nextFloat() * 70f) * (0.4f + 0.6f * fade), w.fx.rr(60f), w.fx.rr(10f), 1.6f, 36f + w.rnd.nextFloat() * 24f, 0xFF3a3d42);
        for (int k = 0; k < 2; k++) {                        // earth kicked up in a ring round the base
            float dir = w.rnd.nextBoolean() ? 1f : -1f;
            w.fx.add(Fx.SMOKE, cx + dir * w.rnd.nextFloat() * 90f, floor - 8f, dir * (180f + w.rnd.nextFloat() * 220f), -20f, 1.1f, 16f + w.rnd.nextFloat() * 12f, 0xFF8d7a63);
        }
        if (w.rnd.nextFloat() < dt * 26f) {                  // debris thrown out of the funnel
            float h = w.rnd.nextFloat() * 300f, th = w.rnd.nextFloat() * 6.283f;
            w.fx.add(Fx.DEBRIS, cx + (float) Math.sin(th) * 30f, floor - h, (float) Math.cos(th) * 700f, -300f - w.rnd.nextFloat() * 400f, 1.3f, 3f + w.rnd.nextFloat() * 3f, w.rnd.nextBoolean() ? 0xFF8a7a68 : 0xFF5b4a3a);
        }
        float reach = 470f * e.s;
        for (int i = 0; i < w.sim.bodies.size(); i++) {
            RB b = w.sim.bodies.get(i);
            if (b.invM == 0f) continue;
            float dx = b.x - cx;
            float ad = Math.abs(dx);
            if (ad > reach || b.y < floor - 1300f) continue;
            float f = (1f - ad / reach) * str;
            float h = floor - b.y;
            float tang = (float) Math.sin(time * 3.4f + h * 0.02f + b.y * 0.003f);
            float core = ad < reach * 0.45f ? 1f : 0.3f;
            b.vx += (-dx * 3.6f * f + tang * 1700f * f) * dt;
            b.vy -= 3500f * f * core * dt;
            b.vx *= 1f - Math.min(0.9f, 1.2f * f * dt);
            b.w += tang * 6f * f * dt * 20f;
        }
        e.n++;
        if (e.n % 12 == 0) {                                 // it tears the buildings apart
            for (int i = 0; i < w.ents.size(); i++) {
                Ent o = w.ents.get(i);
                if (!(o instanceof Prop)) continue;
                Prop q = (Prop) o;
                if (!q.isStruct || q.removed || q.anchor || q.bodies.isEmpty() || Math.abs(q.cx() - cx) > 240f * e.s || !World.breakable(q)) continue;
                w.crack(q, q.cx(), q.cy(), 0.2f * fade);
                w.wear(q, World.toughness(q) * 0.28f * str, true);
            }
        }
        if (e.n % 150 == 70 && fade > 0.5f) w.lightning(cx + w.fx.rr(260f), floor - 300f);
        if (w.rnd.nextFloat() < dt * 3f && w.sfx != null) w.sfx.launch();
        w.fx.shake = Math.max(w.fx.shake, 0.12f * str);
        return e.t >= TORNADO_TIME;
    }
}

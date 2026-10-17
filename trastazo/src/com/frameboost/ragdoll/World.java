package com.frameboost.ragdoll;

import java.util.ArrayList;
import java.util.Random;

/** The game world: entities on top of the physics, damage, blood, fire, explosions, projectiles. */
final class World implements Sim.Listener, Sim.Hook {
    static final int MAX_BODIES = 900;

    static final class Stats {
        int explosions, headshots, kills, severed, infections, melons, thrusts, links, revives, heals, fires, machinesDown, aiKills, recentBooms, hits, destroyed, gibs, broken;
        float recentT;
    }

    static final class Rocket { float x, y, vx, vy, life; Ent owner; }

    final Sim sim = new Sim();
    final Fx fx = new Fx();
    Sfx sfx;
    final Random rnd = new Random();
    final ArrayList<Ent> ents = new ArrayList<>();
    final ArrayList<Rocket> rockets = new ArrayList<>();
    final Stats stats = new Stats();
    float rumble, time;
    final float[] trX0 = new float[8], trY0 = new float[8], trX1 = new float[8], trY1 = new float[8], trT = new float[8];
    private int trNext;
    Ent[] snap = new Ent[64];
    private final float[] hitCd = new float[0];

    World(Sfx sfx) {
        this.sfx = sfx;
        sim.listener = this;
        sim.hook = this;
        sim.floorY = 0f; sim.ceilY = -1900f; sim.wallL = 0f; sim.wallR = 3200f;
        fx.floorY = 0f; fx.wallL = 0f; fx.wallR = 3200f;
    }

    float floorY() { return sim.floorY; }

    // ------------------------------------------------------------------------------ entities ---

    boolean canAdd() { return sim.bodies.size() < MAX_BODIES; }

    Ent add(Ent e) {
        if (e == null) return null;
        ents.add(e);
        return e;
    }

    void remove(Ent e) {
        if (e == null || e.removed) return;
        e.removed = true;
        for (RB b : e.bodies) sim.remove(b);
        for (Joint j : e.joints) sim.remove(j);
        // links made by the player that hang from this entity's bodies
        ents.remove(e);
        for (int i = sim.joints.size() - 1; i >= 0; i--) {
            Joint j = sim.joints.get(i);
            if ((j.a != null && j.a.removed) || (j.b != null && j.b.removed)) sim.remove(j);
        }
        for (int i = ents.size() - 1; i >= 0; i--) {
            Ent o = ents.get(i);
            if (o instanceof Prop && ((Prop) o).type == Items.ROPE_ITEM && ((Prop) o).bodies.isEmpty()) { ents.remove(i); }
        }
    }

    void clear() {
        for (int i = ents.size() - 1; i >= 0; i--) remove(ents.get(i));
        ents.clear();
        sim.clear();
        rockets.clear();
        fx.clear();
        for (int i = 0; i < 8; i++) trT[i] = 0f;
    }

    Person newPerson(float x, float y, float scale, int dir, int kind) {
        if (!canAdd()) return null;
        float stand = sim.floorY - 92f * scale - 1f;
        float py = stand - y < 160f ? stand : y;   // close to the floor: start on foot; higher up: fall
        Person p = Person.create(sim, x, py, scale, dir, kind, rnd);
        p.item = itemOfKind(kind, scale);
        p.tone = 1f;
        if (kind == Person.ZOMBIE || kind == Person.ROBOT || kind == Person.SKELETON) fx.bloodA = fx.bloodA;
        ents.add(p);
        return p;
    }

    static int itemOfKind(int kind, float scale) {
        switch (kind) {
            case Person.ROBOT: return Items.ROBOT;
            case Person.ZOMBIE: return Items.ZOMBIE;
            case Person.SKELETON: return Items.SKELETON;
            case Person.SOLDIER: return Items.SOLDIER;
            case Person.MEDIC: return Items.MEDIC;
            case Person.FIREFIGHTER: return Items.FIREFIGHTER;
            case Person.THUG: return Items.THUG;
            default: return scale > 1.4f ? Items.GIANT : (scale < 0.8f ? Items.KID : Items.HUMAN);
        }
    }

    /** Places an item. Returns the first entity created (or null when nothing could be added). */
    Ent spawn(int item, float x, float y, int dir, float headScale) {
        Person.headScale = headScale;
        if (Items.isBuild(item)) return null;   // constructions are placed with buildAt()
        float fl = sim.floorY;
        x = Math.max(sim.wallL + 70, Math.min(sim.wallR - 70, x));
        y = Math.max(sim.ceilY + 120, y);
        Ent e = null;
        switch (item) {
            case Items.HUMAN: e = newPerson(x, y, 1f, dir, Person.HUMAN); break;
            case Items.GIANT: e = newPerson(x, y, 1.8f, dir, Person.HUMAN); break;
            case Items.KID: e = newPerson(x, y, 0.62f, dir, Person.HUMAN); break;
            case Items.ROBOT: e = newPerson(x, y, 1f, dir, Person.ROBOT); break;
            case Items.ZOMBIE: e = newPerson(x, y, 1f, dir, Person.ZOMBIE); break;
            case Items.SKELETON: e = newPerson(x, y, 1f, dir, Person.SKELETON); break;
            case Items.SOLDIER: e = newPerson(x, y, 1f, dir, Person.SOLDIER); break;
            case Items.MEDIC: e = newPerson(x, y, 1f, dir, Person.MEDIC); break;
            case Items.FIREFIGHTER: e = newPerson(x, y, 1f, dir, Person.FIREFIGHTER); break;
            case Items.THUG: e = newPerson(x, y, 1.08f, dir, Person.THUG); break;
            case Items.CROWD:
                for (int i = 0; i < 5; i++) {
                    float hx = Math.max(sim.wallL + 70, Math.min(sim.wallR - 70, x + (i - 2) * 75f));
                    Ent q = newPerson(hx, y - (i % 2) * 30f, 1f, rnd.nextBoolean() ? 1 : -1, Person.HUMAN);
                    if (e == null) e = q;
                }
                break;
            case Items.TURRET: if (canAdd()) { e = add(Prop.turret(sim, x, Math.min(y, fl - 34f), dir)); } break;
            case Items.DRONE: if (canAdd()) { e = add(Prop.drone(sim, x, Math.min(y, fl - 200f))); } break;
            default: {
                if (!canAdd()) return null;
                float ry = y;
                switch (item) {
                    case Items.CRATE: ry = Math.min(y, fl - 32f); break;
                    case Items.BIGCRATE: ry = Math.min(y, fl - 57f); break;
                    case Items.PLANK: ry = Math.min(y, fl - 10f); break;
                    case Items.BARREL: case Items.XBARREL: ry = Math.min(y, fl - 29f); break;
                    case Items.TNT: ry = Math.min(y, fl - 22f); break;
                    case Items.BALL: case Items.BALLOON: ry = Math.min(y, fl - 30f); break;
                    case Items.WHEEL: case Items.MOTOR: ry = Math.min(y, fl - 28f); break;
                    case Items.BOWLING: ry = Math.min(y, fl - 18f); break;
                    case Items.MELON: ry = Math.min(y, fl - 24f); break;
                    case Items.STEEL: ry = Math.min(y, fl - 27f); break;
                    case Items.PLATFORM: ry = y; break;
                    case Items.THRUSTER: ry = Math.min(y, fl - 20f); break;
                    default: ry = Math.min(y, fl - 14f); break;
                }
                Prop p = Prop.make(sim, item, x, ry, rnd);
                if (p == null) return null;
                if (item == Items.MOTOR) p.motorized = true;
                if (p.fuse >= 0f) p.fuse = item == Items.GRENADE ? 3f : 4f;
                if (item == Items.KNIFE || item == Items.SWORD) { /* sharp bodies are registered by the factory */ }
                p.register(sim);
                e = add(p);
            }
        }
        if (e != null) fx.add(Fx.GLOW, e.cx(), e.cy(), 0, 0, 0.18f, Math.max(16f, e.radius() * 0.6f), 0xFFffffff);
        return e;
    }

    // ------------------------------------------------------------------------------ stepping ---

    Ent[] snapshot() {
        int n = ents.size();
        if (snap.length < n) snap = new Ent[n * 2];
        ents.toArray(snap);
        for (int i = n; i < snap.length; i++) snap[i] = null;
        return snap;
    }

    @Override public void before(float dt) {
        int n = ents.size();
        for (int i = 0; i < n; i++) {
            Ent e = ents.get(i);
            if (e instanceof Person) ((Person) e).control(sim, dt);
            else Mind.machineStep(this, (Prop) e, dt);
        }
    }

    void update(float dt) {
        if (dt > 0.1f) dt = 0.1f;
        time += dt;
        rumble = 0f;
        sim.update(dt);
        post(dt);
        fx.update(dt);
        for (int i = 0; i < 8; i++) if (trT[i] > 0) trT[i] -= dt;
        if (stats.recentT > 0) { stats.recentT -= dt; if (stats.recentT <= 0) stats.recentBooms = 0; }
        updateRockets(dt);
    }

    private void post(float dt) {
        Ent[] list = snapshot();
        int n = ents.size();
        for (int i = n - 1; i >= 0; i--) {
            Ent e = list[i];
            list[i] = null;
            if (e == null || e.removed) continue;
            // out of the room for good
            RB m = e.bodies.isEmpty() ? null : e.bodies.get(0);
            if (m != null && (m.y > sim.floorY + 600f || Math.abs(m.x) > 9000f)) { remove(e); continue; }
            if (e instanceof Person) updatePerson((Person) e, dt);
            else updateProp((Prop) e, dt);
            if (e.burning && !e.removed) updateBurning(e, dt);
        }
        spreadT -= dt;
        if (spreadT <= 0f) { spreadT = 0.12f; spreadFire(); }
        // breakage found during the physics step is applied here, outside the solver
        for (int i = 0; i < breakQ.size(); i++) { Prop q = breakQ.get(i); if (!q.removed) fracture(q, q.cx(), q.cy()); }
        breakQ.clear();
        for (int i = 0; i < gibQ.size(); i++) { Person p = gibQ.get(i); int part = gibQP.get(i); if (!p.removed) gib(p, part, 0f, -1f); }
        gibQ.clear(); gibQP.clear();
    }

    private final ArrayList<Prop> breakQ = new ArrayList<>();
    private final ArrayList<Person> gibQ = new ArrayList<>();
    private final ArrayList<Integer> gibQP = new ArrayList<>();
    /** gib chunks and fragments, oldest first: the oldest go away past the cap */
    private final ArrayList<Prop> chunks = new ArrayList<>();
    static final int MAX_CHUNKS = 90;

    // ------------------------------------------------------------------------------- persons ---

    private void updatePerson(Person p, float dt) {
        if (p.flash > 0f) p.flash -= dt;
        for (int k = 0; k < Person.NPARTS; k++) if (p.gibbed[k] && !p.rb[k].frozen) p.rb[k].setFrozen(true);
        if (p.punchT > 0f) { p.punchT -= dt; if (p.punchT <= 0f) p.punch = false; }
        if (p.shotCd > 0f) p.shotCd -= dt;
        if (p.reloadT > 0f) { p.reloadT -= dt; if (p.reloadT <= 0f) p.ammo = 12; }
        if (p.ko > 0f) p.ko -= dt;
        if (p.biteCd > 0f) p.biteCd -= dt;
        for (int i = 0; i < Person.NPARTS; i++) if (p.hitCd[i] > 0f) p.hitCd[i] -= dt;
        p.held = false;
        for (int i = 0; i < grabCount; i++) if (grabbed[i] != null && grabbed[i].user == p) p.held = true;
        // bleeding
        float lost = 0f;
        for (int i = 0; i < Person.NPARTS; i++) {
            if (p.bleed[i] <= 0f) continue;
            p.bleed[i] -= dt;
            RB b = p.rb[i];
            lost += 2.2f;
            if (p.organic() || p.kind == Person.ZOMBIE) {
                if (rnd.nextFloat() < dt * 22f) fx.blood(b.x, b.y, 0f, 0.4f, 1);
            } else if (rnd.nextFloat() < dt * 6f) fx.sparks(b.x, b.y, 2, 0xFFffd25a);
        }
        if (!p.dead && p.organic() || p.kind == Person.ZOMBIE) { if (!p.dead) p.blood -= lost * dt * 0.5f; }
        if (!p.dead && p.blood <= 0f) kill(p);
        // infection / acid / adrenaline
        if (p.dead && p.bitten && p.infected < 0f && p.organic()) p.infected = 3f;
        if (p.infected > 0f) { p.infected -= dt; if (p.infected <= 0f) reanimate(p); }
        if (p.adrenaline > 0f) p.adrenaline -= dt;
        if (p.acid > 0f) updateAcid(p, dt);
        // tone: conscious people tense up, hurt or sleeping ones go limp
        boolean conscious = !p.dead && p.ko <= 0f && !p.severed[Person.NECK];
        float target = conscious && !p.held ? Math.min(1f, p.blood / 45f) : 0f;
        if (p.burning) target *= 0.5f;
        if (p.adrenaline > 0f) target = Math.max(target, conscious ? 1f : 0f);
        if (target > p.tone) p.tone = Math.min(target, p.tone + dt * 0.8f);
        else p.tone = target;
        p.strength = p.baseStrength * (p.adrenaline > 0f ? 1.35f : 1f);
        // losing balance: leaning far over, or the pelvis support is saturated for too long
        if (conscious && p.tone > 0.9f && p.canMove()) {
            RB pel = p.rb[Person.PELVIS], ch = p.rb[Person.CHEST];
            float tilt = Math.abs(ch.a);
            if (tilt > 1.0f || Math.abs(pel.a) > 1.1f) { p.ko = Math.max(p.ko, 1.0f); p.tone = 0f; }
            if (p.balanceStress > 0.97f) { p.balanceStress = 0f; p.ko = Math.max(p.ko, 0.9f); p.tone = 0f; }
        }
        // struggling while held or burning
        if ((p.held || p.burning) && !p.dead && rnd.nextFloat() < dt * 6f) {
            RB limb = p.rb[Person.FARM_F + rnd.nextInt(4)];
            limb.applyImpulse((rnd.nextFloat() - 0.5f) * 2.5e5f, (rnd.nextFloat() - 0.5f) * 2.5e5f, limb.x, limb.y);
        }
        p.thinkT -= dt;
        if (p.thinkT <= 0f) {
            p.thinkT = 0.25f + rnd.nextFloat() * 0.15f;
            p.walk = 0; p.run = false; p.aim = false; p.reach = false; p.armsUp = false; p.armsForward = false; p.kneel = false;
            p.walkIdleGuard = false;
            if (p.able && p.tone > 0.6f) Mind.think(this, p);
            else { p.target = null; p.mood = p.dead ? "" : (p.ko > 0f ? "Inconsciente" : (p.legsBroken() ? "No puede andar" : "Aturdido")); }
            // keep inside the room
            if (p.walk < 0 && p.rb[Person.PELVIS].x < sim.wallL + 90f) p.walk = 0;
            if (p.walk > 0 && p.rb[Person.PELVIS].x > sim.wallR - 90f) p.walk = 0;
            if (p.walk != 0 && p.walk != p.dir && p.upright() && p.able) p.turn();
            else if (p.walk == 0 && p.target != null && p.able && p.upright()) {
                float tx = p.target instanceof Person ? ((Person) p.target).rb[Person.CHEST].x : p.target.cx();
                int want = tx > p.rb[Person.PELVIS].x ? 1 : -1;
                if (want != p.dir) p.turn();
            }
        }
        Mind.tick(this, p, dt);
    }

    void kill(Person p) {
        if (p.dead) return;
        p.dead = true;
        p.blood = 0f;
        p.tone = 0f;
        stats.kills++;
    }

    /** A bitten person rises again as a zombie. */
    void reanimate(Person p) {
        if (!p.intact() || p.removed) return;
        p.kind = Person.ZOMBIE;
        p.item = Items.ZOMBIE;
        p.target = null;
        stats.infections++;
        p.skin = 0xFF7fa65a;
        p.shirt = Renderer.mix(p.shirt, 0xFF4a473a, 0.6f);
        p.dead = false; p.blood = 120f; p.ko = 1.5f; p.tone = 0f; p.baseStrength = 0.8f; p.speed = 0.55f;
        for (int i = 0; i < Person.NPARTS; i++) { p.broken[i] = false; if (p.hp[i] < 40f) p.hp[i] = 40f; }
        recolor(p);
        fx.smoke(p.rb[Person.HEAD].x, p.rb[Person.HEAD].y, 12, 0xFF5aa02a);
        if (sfx != null) sfx.splat();
    }

    void recolor(Person p) {
        int[] cols = {p.pants, p.shirt, p.skin, p.shirt, p.skin, p.shirt, p.skin, p.pants, p.pants, 0xFF2b2b30, p.pants, p.pants, 0xFF2b2b30};
        for (int i = 0; i < Person.NPARTS; i++) if (!p.rb[i].shapes.isEmpty()) p.rb[i].shapes.get(0).color = cols[i];
    }

    // damage ------------------------------------------------------------------------------------

    void bloodStyle(Person p) {
        switch (p.kind) {
            case Person.ZOMBIE: fx.bloodA = 0xFF5aa02a; fx.bloodB = 0xFF3b6e1a; break;
            case Person.ROBOT: fx.bloodA = 0xFF2b2b2f; fx.bloodB = 0xFF4a4a52; break;
            case Person.SKELETON: fx.bloodA = 0xFFe9e5d3; fx.bloodB = 0xFFc9c4ad; break;
            default: fx.bloodA = 0xFFc0121b; fx.bloodB = 0xFF7d0a10; break;
        }
    }

    /** Damage to one part of a person. Heavy hits knock people out, break bones, open wounds. */
    void hurt(Person p, int part, float dmg, float x, float y, float dx, float dy) {
        if (dmg <= 0f || part < 0) return;
        bloodStyle(p);
        p.flash = 0.12f;
        p.hp[part] -= dmg;
        if (p.kind == Person.ROBOT) fx.sparks(x, y, 4, 0xFFffd25a);
        else if (p.kind == Person.SKELETON) fx.debris(x, y, 2, 0xFFf0ead6, 160f);
        else if (dmg > 3f) fx.blood(x, y, dx, dy, (int) Math.min(10, 2 + dmg * 0.25f));
        if (!p.dead && p.organic()) p.blood -= dmg * 0.22f;
        if (!p.dead && p.kind == Person.ZOMBIE) p.blood -= dmg * 0.18f;
        if (dmg > 9f && !p.dead) {
            float kt = Math.min(5f, 1.1f + dmg * 0.06f) * (p.kind == Person.ROBOT ? 0.5f : 1f);
            p.ko = Math.max(p.ko, kt);
            p.tone = 0f;
        }
        if (p.hp[part] <= 0f && !p.broken[part]) {
            p.broken[part] = true;
            if (sfx != null) sfx.crunch();
            if (p.organic()) p.bleed[part] = Math.max(p.bleed[part], 6f);
            if (part == Person.HEAD) { if (!p.dead) { kill(p); } }
            else if (part == Person.CHEST && !p.dead && p.hp[Person.CHEST] < -40f) kill(p);
            else if (!p.dead) { p.ko = Math.max(p.ko, 0.6f); p.tone = 0f; }
        }
        if (p.blood <= 0f && !p.dead) kill(p);
        // a part far beyond destroyed bursts into pieces
        if (p.hp[part] < -Person.maxHp(p, part) * 1.7f && dmg > 20f && !p.gibbed[part]) { gib(p, part, dx, dy); return; }
        // deep damage tears the joint (dismemberment) for the extremities
        if (p.hp[part] < -60f) {
            int j = Person.PART_JOINT[part];
            if (j >= 0 && j != Person.WAIST && !p.severed[j]) sever(p, j, x, y);
        }
        rumble += Math.min(0.5f, dmg * 0.01f);
    }

    static final int[][] DETACH = {
        {Person.HEAD},
        {Person.CHEST, Person.HEAD, Person.UARM_F, Person.FARM_F, Person.UARM_B, Person.FARM_B},
        {Person.UARM_F, Person.FARM_F}, {Person.FARM_F}, {Person.UARM_B, Person.FARM_B}, {Person.FARM_B},
        {Person.THIGH_F, Person.SHIN_F, Person.FOOT_F}, {Person.SHIN_F, Person.FOOT_F}, {Person.FOOT_F},
        {Person.THIGH_B, Person.SHIN_B, Person.FOOT_B}, {Person.SHIN_B, Person.FOOT_B}, {Person.FOOT_B},
    };

    /** Cuts a joint: the limb flies off. */
    void sever(Person p, int j, float x, float y) {
        if (p.severed[j]) return;
        Joint jn = p.jt[j];
        if (!jn.removed) sim.remove(jn);
        p.severed[j] = true;
        stats.severed++;
        jn.muscle = false;
        int g = ++Person.groupSeq;
        for (int part : DETACH[j]) for (Shape s : p.rb[part].shapes) s.group = g;
        bloodStyle(p);
        if (p.organic() || p.kind == Person.ZOMBIE) {
            fx.blood(x, y, 0f, -0.3f, 18);
            for (int part : stumpParts(j)) p.bleed[part] = Math.max(p.bleed[part], 9f);
            if (!p.dead) p.blood -= 6f;
        } else fx.sparks(x, y, 14, 0xFFffd25a);
        if (sfx != null) sfx.crunch();
        if (!p.dead) { p.ko = Math.max(p.ko, j == Person.NECK ? 99f : 1.2f); p.tone = 0f; }
        if (j == Person.NECK || j == Person.WAIST) kill(p);
        rumble += 0.3f;
    }

    private static int[] stumpParts(int j) {
        switch (j) {
            case Person.NECK: return new int[]{Person.CHEST};
            case Person.SH_F: case Person.EL_F: return new int[]{j == Person.SH_F ? Person.CHEST : Person.UARM_F};
            case Person.SH_B: case Person.EL_B: return new int[]{j == Person.SH_B ? Person.CHEST : Person.UARM_B};
            case Person.HIP_F: case Person.HIP_B: return new int[]{Person.PELVIS};
            case Person.KNEE_F: return new int[]{Person.THIGH_F};
            case Person.KNEE_B: return new int[]{Person.THIGH_B};
            default: return new int[]{Person.PELVIS};
        }
    }

    @Override public void jointBroken(Joint j) {
        if (j.tag == Build.TAG) {                       // a construction let go: pieces fall and debris flies
            float x = j.anchorBX(), y = j.anchorBY();
            fx.debris(x, y, 7, Build.WOOD, 200f);
            fx.smoke(x, y, 10, 0xFF9a9a9a);
            if (sfx != null) sfx.crunch();
            stats.destroyed++;
            rumble += 0.12f;
            return;
        }
        if (j.user instanceof Person) {
            Person p = (Person) j.user;
            int idx = j.tag;
            if (idx >= 0 && idx < Person.NJOINTS && !p.severed[idx]) {
                j.removed = true;
                // already removed by the Sim: replay the bookkeeping
                p.severed[idx] = true;
                stats.severed++;
                int g = ++Person.groupSeq;
                for (int part : DETACH[idx]) for (Shape s : p.rb[part].shapes) s.group = g;
                bloodStyle(p);
                float x = j.anchorBX(), y = j.anchorBY();
                if (p.organic() || p.kind == Person.ZOMBIE) {
                    fx.blood(x, y, 0f, -0.3f, 18);
                    for (int part : stumpParts(idx)) p.bleed[part] = Math.max(p.bleed[part], 9f);
                    if (!p.dead) p.blood -= 6f;
                } else fx.sparks(x, y, 14, 0xFFffd25a);
                if (sfx != null) sfx.crunch();
                if (!p.dead) { p.ko = Math.max(p.ko, idx == Person.NECK ? 99f : 1.2f); p.tone = 0f; }
                if (idx == Person.NECK || idx == Person.WAIST) kill(p);
                rumble += 0.3f;
            }
        } else if (j.user instanceof Prop && ((Prop) j.user).type == Items.ROPE_ITEM) {
            fx.sparks(j.anchorBX(), j.anchorBY(), 6, 0xFFe8c48a);
        }
    }

    // impacts ------------------------------------------------------------------------------------

    /** per-part damage cooldown shared by both sides of a contact */
    @Override public void impact(Shape a, Shape b, float dv, float px, float py, float nx, float ny) {
        impactOn(a, b, dv, px, py, -nx, -ny);
        if (b != null) impactOn(b, a, dv, px, py, nx, ny);
        else if (dv > 300f) { if (sfx != null) sfx.thud(Math.min(1f, dv / 1400f)); rumble += Math.min(0.3f, dv * 0.0002f); }
        if (dv > 600f && b != null && sfx != null) sfx.thud(Math.min(1f, dv / 1600f));
    }

    private void impactOn(Shape s, Shape other, float dv, float px, float py, float dx, float dy) {
        Object u = s.body.user;
        if (u instanceof Person) {
            Person p = (Person) u;
            if (p.removed) return;
            int part = Person.partOf(p, s);
            if (part < 0 || p.hitCd[part] > 0f) return;
            float thr = part == Person.HEAD ? 520f : (part == Person.FOOT_F || part == Person.FOOT_B ? 1250f : (part == Person.SHIN_F || part == Person.SHIN_B ? 1000f : (part >= Person.THIGH_F ? 880f : 640f)));
            if (p.kind == Person.ROBOT) thr *= 1.6f;
            boolean sharp = other != null && other.body.user instanceof Prop && ((Prop) other.body.user).sharp;
            float heavy = 1f;
            if (other != null) {
                float om = other.body.invM > 0f ? other.body.m : 20000f;
                heavy = Math.max(0.35f, Math.min(2.2f, (float) Math.sqrt(om / Math.max(1f, s.body.m)) * 0.9f));
                if (other.body.user instanceof Person) heavy = Math.min(heavy, 0.9f);
            }
            if (sharp && dv > 140f) {
                p.hitCd[part] = 0.18f;
                wound(p, part, Person.W_CUT, px, py, dx, dy, 5f + Math.min(9f, dv * 0.01f));
                hurt(p, part, 8f + dv * 0.05f, px, py, dx, dy);
                if (dv > 330f && rnd.nextFloat() < 0.55f) {
                    int j = Person.PART_JOINT[part];
                    if (j >= 0 && j != Person.WAIST && !p.severed[j]) sever(p, j, px, py);
                }
                stats.hits++;
            } else if (dv > thr) {
                p.hitCd[part] = 0.2f;
                if (dv > thr * 4.5f && heavy >= 0.9f && p.kind != Person.ROBOT) { gibQ.add(p); gibQP.add(part); return; }
                float d = (dv - thr) * 0.05f * heavy;
                if (d > 6f) wound(p, part, Person.W_BRUISE, px, py, dx, dy, 3f + Math.min(7f, d * 0.25f));
                hurt(p, part, d, px, py, dx, dy);
            }
            // a hurt person drops what they were doing when thrown hard
            if (dv > 1000f && !p.dead && p.ko <= 0f && (part == Person.PELVIS || part == Person.CHEST || part == Person.HEAD)) { p.ko = 0.9f; p.tone = 0f; }
        } else if (u instanceof Prop) {
            Prop q = (Prop) u;
            if (q.removed) return;
            boolean sharp = other != null && (other.body.user instanceof Prop && ((Prop) other.body.user).sharp);
            if (q.type == Items.BALLOON) { if (dv > 90f || sharp) pop(q); return; }
            if (q.type == Items.MELON) { if (dv > 480f || (sharp && dv > 100f)) smash(q); return; }
            if (q.explosive && q.fuse < 0f && q.type != Items.C4 && dv > 1500f && q.age > 0.8f) q.fuse = 0.05f;
            if (q.machine() && dv > 600f) damageMachine(q, (dv - 600f) * 0.08f);
            if (breakable(q)) {
                float th = sharp ? 200f : 650f;
                // the contact speed is noisy for heavy welded stacks: cap it by how fast things were really moving
                float osp = other == null ? 0f : (other.body.user instanceof Prop ? ((Prop) other.body.user).lastSp : (float) Math.hypot(other.body.vx, other.body.vy));
                dv = Math.min(dv, q.lastSp + osp);
                if (dv > th) {
                    float heavy = 1f;
                    if (other != null) {
                        float om = other.body.invM > 0f ? other.body.m : 20000f;
                        heavy = Math.max(0.3f, Math.min(2.5f, (float) Math.sqrt(om / Math.max(1f, s.body.m))));
                    }
                    wear(q, (dv - th) * 0.04f * heavy * (sharp ? 2f : 1f), true);
                }
            }
        }
    }

    // --------------------------------------------------------------------------- destruction ---

    /** Records a wound on the skin of a part (bullet hole, cut, burn, bruise) in its own frame. */
    void wound(Person p, int part, int kind, float x, float y, float dx, float dy, float size) {
        if (part < 0 || p.gibbed[part] || p.rb[part].shapes.isEmpty()) return;
        Shape s = p.rb[part].shapes.get(0);
        float ex = x - s.cx, ey = y - s.cy;
        float u = ex * s.ax + ey * s.ay, v = -ex * s.ay + ey * s.ax;
        if (s.type == Shape.CAPSULE) {
            u = Math.max(-s.hl - s.r * 0.4f, Math.min(s.hl + s.r * 0.4f, u));
            v = Math.max(-s.r * 0.62f, Math.min(s.r * 0.62f, v));
        } else if (s.type == Shape.CIRCLE) {
            float d = (float) Math.sqrt(u * u + v * v), m = s.r * 0.62f;
            if (d > m) { u *= m / d; v *= m / d; }
        } else {
            u = Math.max(-s.hl * 0.85f, Math.min(s.hl * 0.85f, u));
            v = Math.max(-s.hh * 0.85f, Math.min(s.hh * 0.85f, v));
        }
        int i = p.wNext;
        p.wNext = (p.wNext + 1) % Person.MAXW;
        if (p.nw < Person.MAXW) p.nw++;
        p.wpart[i] = (byte) part; p.wkind[i] = (byte) kind;
        p.wu[i] = u; p.wv[i] = v; p.ws[i] = size * p.scale;
        // cuts follow the blow direction, in the shape frame
        p.wa[i] = (float) Math.atan2(-dx * s.ay + dy * s.ax, dx * s.ax + dy * s.ay) + (rnd.nextFloat() - 0.5f) * 0.6f;
    }

    /** Blows a part of a person into meat (or metal, or bone) chunks. */
    void gib(Person p, int part, float dx, float dy) {
        if (p.removed || part < 0 || p.gibbed[part]) return;
        RB b = p.rb[part];
        float bx = b.x, by = b.y, vx = b.vx, vy = b.vy;
        for (int j = 0; j < Person.NJOINTS; j++) {
            Joint jn = p.jt[j];
            if (!p.severed[j] && (jn.a == b || jn.b == b)) sever(p, j, bx, by);
        }
        p.gibbed[part] = true;
        p.bleed[part] = 0f;
        for (Shape s : b.shapes) s.sensor = true;
        b.vx = b.vy = 0f; b.w = 0f;
        b.setFrozen(true);
        for (int k = 0; k < p.nw; k++) if (p.wpart[k] == part) p.ws[k] = 0f;
        stats.gibs++;
        bloodStyle(p);
        boolean metal = p.kind == Person.ROBOT, bone = p.kind == Person.SKELETON;
        Shape s0 = b.shapes.get(0);
        int n = part == Person.CHEST || part == Person.PELVIS ? 6 : (part == Person.HEAD ? 5 : 3);
        float size = s0.type == Shape.CIRCLE ? s0.r : (s0.type == Shape.CAPSULE ? s0.r + s0.hl * 0.35f : (s0.hl + s0.hh) * 0.4f);
        int outer = s0.color;
        for (int k = 0; k < n && canAdd(); k++) {
            float hw = Math.max(3.5f, size * (0.35f + rnd.nextFloat() * 0.3f)), hh = Math.max(3f, hw * (0.6f + rnd.nextFloat() * 0.5f));
            int col = metal ? (k % 2 == 0 ? 0xFF6b737d : dark(outer, 0.2f)) : bone ? 0xFFe9e5d3
                    : !fx.gore ? (k % 2 == 0 ? outer : dark(outer, 0.25f))
                    : (k % 3 == 0 ? outer : (p.kind == Person.ZOMBIE ? 0xFF4f7d2a : (k % 3 == 1 ? 0xFFa3202a : 0xFFc94a52)));
            Prop c = Prop.block(bx + fx.rr(size), by + fx.rr(size), hw, hh, col, metal ? 2f : 1f, false);
            c.gib = true;
            c.register(sim);
            RB r = c.main();
            r.setAngle(rnd.nextFloat() * 6.283f);
            float sp = 260f + rnd.nextFloat() * 420f, ang = rnd.nextFloat() * 6.283f;
            r.vx = vx * 0.5f + dx * 200f + (float) Math.cos(ang) * sp;
            r.vy = vy * 0.5f + dy * 200f + (float) Math.sin(ang) * sp - 150f;
            r.w = fx.rr(30f);
            add(c);
            keepChunk(c);
        }
        if (metal) { fx.sparks(bx, by, 24, 0xFFffd25a); fx.smoke(bx, by, 10f, 0xFF3c3c3c); }
        else if (bone) fx.debris(bx, by, 12, 0xFFf0ead6, 320f);
        else { fx.blood(bx, by, 0f, -0.4f, 30); fx.blood(bx, by, dx, dy, 18); fx.debris(bx, by, 6, 0xFF8e1111, 300f); }
        if (sfx != null) { sfx.splat(); sfx.crunch(); }
        if (!p.dead && (part == Person.HEAD || part == Person.CHEST || part == Person.PELVIS)) kill(p);
        else if (!p.dead) { p.ko = Math.max(p.ko, 2f); p.tone = 0f; p.blood -= 15f; if (p.blood <= 0f) kill(p); }
        rumble += 0.35f;
    }

    private static int dark(int c, float t) { return Renderer.dark(c, t); }

    private void keepChunk(Prop c) {
        chunks.add(c);
        while (chunks.size() > MAX_CHUNKS) {
            Prop o = chunks.remove(0);
            if (!o.removed) { fx.debris(o.cx(), o.cy(), 2, o.color, 80f); remove(o); }
        }
    }

    /** Things that splinter and break apart (not machines, bombs or static pillars). */
    static boolean breakable(Prop q) {
        if (q.bodies.isEmpty() || q.main().isStatic) return false;
        return q.type == Items.CRATE || q.type == Items.BIGCRATE || q.type == Items.PLANK || q.type == Items.BLOCK;
    }

    /** How much damage a breakable thing takes before it breaks. */
    static float toughness(Prop q) {
        float base;
        if (q.gib) base = 30f;
        else if (q.type == Items.CRATE) base = 70f;
        else if (q.type == Items.BIGCRATE) base = 120f;
        else if (q.type == Items.PLANK) base = 80f;
        else if (q.color == Build.STEEL) base = 420f;
        else if (q.color == Build.BRICK) base = 150f;
        else base = 85f;
        float size = Math.max(q.hw, q.hh);
        return base * Math.max(0.3f, Math.min(1f, size / 30f));
    }

    /** Adds damage to a breakable thing; it breaks when it has taken enough. */
    void wear(Prop q, float dmg, boolean deferred) {
        if (q.removed || dmg <= 0f || !breakable(q)) return;
        q.wear += dmg;
        if (q.wear < toughness(q)) return;
        if (deferred) { if (!breakQ.contains(q)) breakQ.add(q); }
        else fracture(q, q.cx(), q.cy());
    }

    /** Splits a block/crate/plank into smaller fragments; small ones just turn to splinters. */
    void fracture(Prop q, float ix, float iy) {
        if (q.removed) return;
        RB b = q.main();
        float hw = q.hw, hh = q.hh, bx = b.x, by = b.y, a = b.a, vx = b.vx, vy = b.vy, w = b.w;
        boolean burning = q.burning;
        int col = q.color;
        boolean brick = col == Build.BRICK, steel = col == Build.STEEL;
        remove(q);
        stats.broken++;
        if (!q.gib) stats.destroyed++;
        fx.debris(bx, by, 10, col, 260f);
        fx.smoke(bx, by, brick ? 14f : 8f, brick ? 0xFFb09a8a : 0xFF9a9a9a);
        if (steel) fx.sparks(bx, by, 10, 0xFFffd25a);
        if (sfx != null) sfx.crunch();
        rumble += 0.1f;
        if (Math.max(hw, hh) < 9f || q.gib) return;
        int nx = hw >= 2f * hh ? Math.min(4, Math.round(hw / hh)) : (hw >= 10f ? 2 : 1);
        int ny = hh >= 2f * hw ? Math.min(4, Math.round(hh / hw)) : (hh >= 10f ? 2 : 1);
        if (nx * ny < 2) return;
        float pw = hw / nx, ph = hh / ny, ca = (float) Math.cos(a), sa = (float) Math.sin(a);
        for (int i = 0; i < nx; i++) for (int j = 0; j < ny; j++) {
            if (!canAdd()) return;
            float lx = -hw + (i + 0.5f) * 2f * pw, ly = -hh + (j + 0.5f) * 2f * ph;
            float wx = bx + ca * lx - sa * ly, wy = by + sa * lx + ca * ly;
            float jw = pw * (0.82f + rnd.nextFloat() * 0.14f), jh = ph * (0.82f + rnd.nextFloat() * 0.14f);
            Prop f = Prop.block(wx, wy, jw, jh, col, q.type == Items.BLOCK ? q.dens : 0.7f, q.flammable);
            f.frag = true;
            f.register(sim);
            RB r = f.main();
            r.setAngle(a + (rnd.nextFloat() - 0.5f) * 0.3f);
            float ox = wx - ix, oy = wy - iy, ol = (float) Math.sqrt(ox * ox + oy * oy) + 1f;
            float kick = 90f + rnd.nextFloat() * 160f;
            r.vx = vx - w * (wy - by) + ox / ol * kick;
            r.vy = vy + w * (wx - bx) + oy / ol * kick;
            r.w = w + fx.rr(6f);
            add(f);
            keepChunk(f);
            if (burning) ignite(f);
        }
    }

    void pop(Prop q) {
        fx.sparks(q.cx(), q.cy(), 8, q.color);
        fx.debris(q.cx(), q.cy(), 6, q.color, 220f);
        if (sfx != null) sfx.pop();
        remove(q);
    }

    void smash(Prop q) {
        stats.melons++;
        fx.debris(q.cx(), q.cy(), 8, 0xFF3f9a3c, 300f);
        for (int i = 0; i < 12; i++) fx.add(Fx.BLOOD, q.cx(), q.cy(), fx.rr(300), -fx.rf() * 300f, 1.3f, 3f + fx.rf() * 3f, 0xFFd8344a);
        if (sfx != null) sfx.splat();
        remove(q);
    }

    // props ---------------------------------------------------------------------------------------

    private void updateProp(Prop q, float dt) {
        if (q.flash > 0f) q.flash -= dt;
        if (!q.bodies.isEmpty()) { RB r = q.main(); q.lastSp = (float) Math.hypot(r.vx, r.vy) + Math.abs(r.w) * Math.max(q.hw, q.hh); }
        q.age += dt;
        RB m = q.main();
        if (q.fuse >= 0f) {
            q.fuse -= dt;
            if (rnd.nextFloat() < 0.6f && q.type != Items.TNT && q.type != Items.XBARREL)
                fx.add(Fx.SPARK, m.x, m.y - 8f, fx.rr(120), -120f - fx.rf() * 160f, 0.3f, 2f, 0xFFffc83d);
            if (q.fuse <= 0f) {
                float bx = m.x, by = m.y;
                boolean fire = q.type == Items.XBARREL;
                float r = q.blastR, pw = q.blastPow, dm = q.blastDmg;
                remove(q);
                explode(bx, by, r, pw, dm, true);
                if (fire) for (int k = 0; k < 6; k++) fx.flame(bx + fx.rr(60), by + fx.rr(30), 18f);
                return;
            }
        }
        if (q.type == Items.THRUSTER && q.thrust) {
            if (rnd.nextFloat() < dt * 60f) {
                float ux = (float) Math.sin(m.a), uy = (float) -Math.cos(m.a);
                fx.add(Fx.FLAME, m.x - ux * 20f, m.y - uy * 20f, -ux * 160f + fx.rr(40), -uy * 160f + fx.rr(40), 0.3f, 9f, 0xFFffa040);
            }
        }
        if (q.type == Items.GUN_MG && q.firing) {
            q.fireCd -= dt;
            if (q.fireCd <= 0f) { q.fireCd = 0.075f; fireGun(q); }
        }
        if (q.machine()) Mind.machineThink(this, q, dt);
    }

    /** Fires a gun object along its barrel with recoil. */
    void fireGun(Prop g) {
        RB b = g.main();
        float hl = g.hw;
        float dx = (float) Math.cos(b.a), dy = (float) Math.sin(b.a);
        float ox = b.x + dx * (hl + 8f), oy = b.y + dy * (hl + 8f);
        int k = (int) g.param;
        switch (k) {
            case 0: shoot(g, ox, oy, dx, dy, 24f, 950f, 1); break;
            case 1: shoot(g, ox, oy, dx, dy, 40f, 1600f, 3); break;
            default: {
                float ang = (float) Math.atan2(dy, dx) + (rnd.nextFloat() - 0.5f) * 0.07f;
                shoot(g, ox, oy, (float) Math.cos(ang), (float) Math.sin(ang), 14f, 600f, 1);
            }
        }
        float rec = (k == 1 ? 9e5f : (k == 2 ? 3e5f : 5e5f));
        b.applyImpulse(-dx * rec, -dy * rec, b.x, b.y);
        b.w += -(dx > 0 ? 1f : -1f) * 0.8f * (k == 1 ? 3f : 1f);
        fx.add(Fx.GLOW, ox, oy, 0, 0, 0.06f, 16f, 0xFFfff0b0);
        fx.sparks(ox, oy, 3, 0xFFffd25a);
        if (sfx != null) sfx.shot();
        rumble += 0.06f;
    }

    void damageMachine(Prop m, float dmg) {
        if (m.dead || dmg <= 0f) return;
        m.hp -= dmg;
        m.flash = 0.12f;
        fx.sparks(m.cx(), m.cy(), 4, 0xFFffd25a);
        if (m.hp <= 0f) {
            m.dead = true; m.on = false; m.thrust = false;
            m.mood = "Averiado";
            fx.sparks(m.cx(), m.cy(), 18, 0xFFffd25a);
            fx.smoke(m.cx(), m.cy(), 16f, 0xFF3c3c3c);
            fx.add(Fx.GLOW, m.cx(), m.cy(), 0, 0, 0.25f, 70f, 0xFFffb060);
            if (sfx != null) sfx.boom(0.15f);
            stats.machinesDown++;
            if (m.type == Items.DRONE) for (RB b : m.bodies) b.gravScale = 1f;
            if (m.aimJoint != null) m.aimJoint.muscle = false;
        }
    }

    /** The shock wave snaps construction welds near the blast, the nearer the likelier. */
    private void shockWelds(float x, float y, float R) {
        for (int i = sim.joints.size() - 1; i >= 0; i--) {
            Joint j = sim.joints.get(i);
            if (j.tag != Build.TAG || j.removed) continue;
            float dx = j.anchorBX() - x, dy = j.anchorBY() - y;
            float f = 1f - (float) Math.sqrt(dx * dx + dy * dy) / R;
            if (f <= 0.1f || rnd.nextFloat() >= (f - 0.05f) * 1.6f) continue;
            sim.remove(j);
            jointBroken(j);
        }
    }

    // ------------------------------------------------------------------------------- explosions --

    /** Blast: damages and throws everything, sets fire, detonates neighbours. power is a speed in units/s. */
    void explode(float x, float y, float R, float power, float dmg, boolean chain) {
        fx.explosion(x, y, R);
        rumble += Math.min(1.2f, R / 450f);
        stats.explosions++;
        stats.recentBooms++;
        stats.recentT = 2f;
        if (sfx != null) sfx.boom(Math.min(1f, R / 600f));
        sim.blast(x, y, R, power);
        shockWelds(x, y, R);
        Ent[] list = snapshot();
        int n = ents.size();
        for (int i = n - 1; i >= 0; i--) {
            Ent e = list[i];
            list[i] = null;
            if (e == null || e.removed) continue;
            float dx = e.cx() - x, dy = e.cy() - y;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d - e.radius() > R) continue;
            float f = Math.max(0f, 1f - Math.max(0f, d - e.radius() * 0.6f) / R);
            if (f <= 0.02f) continue;
            if (e instanceof Prop) {
                Prop q = (Prop) e;
                if (q.type == Items.BALLOON) { if (f > 0.1f) pop(q); continue; }
                if (q.type == Items.MELON) { if (f > 0.15f) smash(q); continue; }
                if (q.explosive && q.fuse < 0f && f > 0.15f) q.fuse = 0.06f + rnd.nextFloat() * 0.25f;
                if (q.flammable && f > 0.35f) ignite(q);
                if (q.machine()) damageMachine(q, dmg * f * f * 2f);
                if (f > 0.1f) wear(q, dmg * f * f * 2.6f, false);
            } else {
                Person p = (Person) e;
                if (p.flammable && f > 0.35f) ignite(p);
                for (int part = 0; part < Person.NPARTS; part++) {
                    RB b = p.rb[part];
                    float px = b.x - x, py = b.y - y, pd = (float) Math.sqrt(px * px + py * py);
                    float pf = Math.max(0f, 1f - pd / R);
                    if (pf <= 0.02f) continue;
                    if (p.gibbed[part]) continue;
                    float hd = dmg * pf * pf * 0.9f;
                    if (hd > 8f) wound(p, part, Person.W_BURN, b.x - px / (pd + 1f) * 8f, b.y - py / (pd + 1f) * 8f, px, py, 4f + Math.min(8f, hd * 0.12f));
                    // point blank: the part bursts
                    if (hd > 55f && rnd.nextFloat() < (pf - 0.7f) * 2.2f) { gib(p, part, px / (pd + 1f), py / (pd + 1f)); continue; }
                    hurt(p, part, hd, b.x, b.y, px / (pd + 1f), py / (pd + 1f));
                }
                if (f > 0.4f) {
                    for (int j = 0; j < Person.NJOINTS; j++) {
                        if (p.severed[j] || j == Person.WAIST) continue;
                        if (rnd.nextFloat() < (f - 0.3f) * 1.25f) {
                            RB b = p.rb[Person.PART_JOINT_CHILD[j]];
                            sever(p, j, b.x, b.y);
                        }
                    }
                }
                if (f > 0.7f && !p.dead) kill(p);
            }
        }
    }

    // ------------------------------------------------------------------------------------ fire ---

    void ignite(Ent e) {
        if (e.burning || !e.flammable) return;
        if (e instanceof Prop && ((Prop) e).type == Items.C4) return;
        e.burning = true;
        e.burnT = e instanceof Person ? 16f : (e instanceof Prop && ((Prop) e).explosive ? 1f : 9f);
        if (e instanceof Prop && ((Prop) e).explosive && ((Prop) e).fuse < 0f) ((Prop) e).fuse = 0.4f + rnd.nextFloat() * 0.5f;
    }

    private void updateBurning(Ent e, float dt) {
        e.burnT -= dt;
        e.charT = Math.min(0.85f, e.charT + dt * 0.07f);
        int nb = e.bodies.size();
        if (nb == 0) { e.burning = false; return; }
        float rate = e instanceof Person ? 70f : 45f;
        int cnt = (int) (rate * dt + rnd.nextFloat());
        for (int k = 0; k < cnt; k++) {
            RB b = e.bodies.get(rnd.nextInt(nb));
            fx.flame(b.x, b.y, 7f + (b.shapes.isEmpty() ? 6f : b.shapes.get(0).reach() * 0.3f));
        }
        if (rnd.nextFloat() < 14f * dt) { RB b = e.bodies.get(rnd.nextInt(nb)); fx.smoke(b.x, b.y - 8f, 6f, 0xFF3c3c3c); }
        if (e instanceof Person) {
            Person p = (Person) e;
            if (!p.dead) { p.blood -= 4.5f * dt; if (p.blood <= 0f) kill(p); }
        }
        if (e.burnT <= 0f) e.burning = false;
    }

    private float spreadT;

    private void spreadFire() {
        int n = ents.size();
        for (int i = 0; i < n; i++) {
            Ent a = ents.get(i);
            if (!a.burning) continue;
            for (int j = 0; j < n; j++) {
                if (i == j) continue;
                Ent b = ents.get(j);
                if (b.burning || !b.flammable) continue;
                float dx = a.cx() - b.cx(), dy = a.cy() - b.cy(), rr = a.radius() + b.radius() + 14f;
                if (dx * dx + dy * dy > rr * rr) continue;
                boolean near = false;
                for (int u = 0; u < a.bodies.size() && !near; u++) {
                    RB p = a.bodies.get(u);
                    for (int v = 0; v < b.bodies.size(); v++) {
                        RB q = b.bodies.get(v);
                        float ex = p.x - q.x, ey = p.y - q.y;
                        float r = (p.shapes.isEmpty() ? 10f : p.shapes.get(0).reach()) + (q.shapes.isEmpty() ? 10f : q.shapes.get(0).reach()) + 16f;
                        if (ex * ex + ey * ey < r * r) { near = true; break; }
                    }
                }
                if (near && rnd.nextFloat() < 0.28f) ignite(b);
            }
        }
    }

    // ------------------------------------------------------------------------------- projectiles --

    private void tracer(float x0, float y0, float x1, float y1) {
        int i = trNext;
        trNext = (trNext + 1) & 7;
        trX0[i] = x0; trY0[i] = y0; trX1[i] = x1; trY1[i] = y1; trT[i] = 0.07f;
    }

    /** Fires a bullet. Returns the distance travelled. The shooter's own parts are ignored. */
    float shoot(Ent shooter, float x, float y, float dx, float dy, float dmg, float impulse, int pierce) {
        float l = (float) Math.sqrt(dx * dx + dy * dy);
        dx /= l; dy /= l;
        float tEnd = sim.rayRoom(x, y, dx, dy, 5200f);
        boolean hitRoom = true;
        ArrayList<Sim.Hit> hs = sim.ray(x, y, dx, dy, tEnd);
        int pierced = 0;
        float stopT = tEnd;
        for (int i = 0; i < hs.size(); i++) {
            Sim.Hit h = hs.get(i);
            Object u = h.shape.body.user;
            if (u == shooter && shooter != null) continue;
            float hx = x + dx * h.t, hy = y + dy * h.t;
            applyBullet(h.shape, u, hx, hy, dx, dy, dmg, impulse, shooter);
            if (++pierced >= pierce) { stopT = h.t; hitRoom = false; break; }
        }
        float ix = x + dx * stopT, iy = y + dy * stopT;
        fx.add(Fx.GLOW, x, y, 0, 0, 0.05f, 14f, 0xFFfff0b0);
        tracer(x, y, ix, iy);
        if (hitRoom) fx.sparks(ix, iy, 6, 0xFFffe08a);
        return stopT;
    }

    private void applyBullet(Shape s, Object u, float hx, float hy, float dx, float dy, float dmg, float impulse, Ent shooter) {
        RB b = s.body;
        float J = impulse * 350f;
        if (u instanceof Prop) {
            Prop q = (Prop) u;
            if (q.type == Items.BALLOON) { pop(q); return; }
            if (q.type == Items.MELON) { smash(q); return; }
            if (q.explosive && q.fuse < 0f && q.type != Items.GRENADE && q.type != Items.DYNAMITE) q.fuse = 0.05f;
            if (q.machine()) damageMachine(q, dmg * 1.1f);
            b.applyImpulse(dx * J, dy * J, hx, hy);
            fx.sparks(hx, hy, 5, q.type == Items.CRATE || q.type == Items.PLANK ? 0xFFe8c48a : 0xFFffe08a);
            if (q.type == Items.CRATE || q.type == Items.PLANK) fx.debris(hx, hy, 2, q.color, 180f);
            if (breakable(q)) { fx.debris(hx, hy, 2, q.color, 160f); wear(q, dmg * 1.3f, false); }
        } else if (u instanceof Person) {
            Person p = (Person) u;
            int part = Person.partOf(p, s);
            b.applyImpulse(dx * J, dy * J, hx, hy);
            bloodStyle(p);
            fx.blood(hx, hy, dx, dy, 7);
            if (part < 0) return;
            wound(p, part, Person.W_HOLE, hx, hy, dx, dy, 2.2f + dmg * 0.03f);
            if (p.organic()) p.bleed[part] = Math.max(p.bleed[part], 6f);
            // heavy rounds burst heads
            if (part == Person.HEAD && dmg >= 40f && rnd.nextFloat() < 0.6f) { boolean alive = !p.dead; gib(p, part, dx, dy); if (alive) { stats.headshots++; if (shooter != null && !(shooter instanceof Person && ((Person) shooter).kind == Person.THUG)) stats.aiKills++; } return; }
            boolean wasAlive = !p.dead;
            if (part == Person.HEAD && wasAlive) {
                stats.headshots++;
                hurt(p, part, 140f, hx, hy, dx, dy);
                kill(p);
                fx.blood(hx, hy, dx, dy, 12);
            } else {
                hurt(p, part, dmg * 1.2f, hx, hy, dx, dy);
            }
            if (sfx != null) sfx.splat();
            if (wasAlive && p.dead && shooter != null && !(shooter instanceof Person && ((Person) shooter).kind == Person.THUG)) stats.aiKills++;
        }
    }

    void shotgun(float x, float y, float dx, float dy) {
        float base = (float) Math.atan2(dy, dx);
        for (int i = 0; i < 9; i++) {
            float a = base + (rnd.nextFloat() - 0.5f) * 0.28f;
            shoot(null, x, y, (float) Math.cos(a), (float) Math.sin(a), 9f, 420f, 1);
        }
    }

    void launchRocket(float x, float y, float dx, float dy, Ent owner) {
        if (rockets.size() > 8) return;
        Rocket r = new Rocket();
        float l = (float) Math.sqrt(dx * dx + dy * dy);
        r.x = x; r.y = y; r.vx = dx / l * 1250f; r.vy = dy / l * 1250f; r.life = 4f; r.owner = owner;
        rockets.add(r);
        if (sfx != null) sfx.launch();
    }

    private void updateRockets(float dt) {
        for (int i = rockets.size() - 1; i >= 0; i--) {
            Rocket r = rockets.get(i);
            r.life -= dt;
            float sp = (float) Math.sqrt(r.vx * r.vx + r.vy * r.vy);
            float dx = r.vx / sp, dy = r.vy / sp, step = sp * dt;
            boolean boom = r.life <= 0f;
            float hx = r.x + dx * step, hy = r.y + dy * step;
            ArrayList<Sim.Hit> hs = sim.ray(r.x, r.y, dx, dy, step);
            for (int k = 0; k < hs.size(); k++) {
                if (hs.get(k).shape.body.user == r.owner && r.owner != null) continue;
                hx = r.x + dx * hs.get(k).t; hy = r.y + dy * hs.get(k).t; boom = true; break;
            }
            float room = sim.rayRoom(r.x, r.y, dx, dy, step + 1f);
            if (room < step) { hx = r.x + dx * room; hy = r.y + dy * room; boom = true; }
            fx.add(Fx.SMOKE, r.x, r.y, fx.rr(30), fx.rr(30), 0.7f, 7f, 0xFF9a9a9a);
            fx.add(Fx.FLAME, r.x, r.y, fx.rr(40), fx.rr(40), 0.2f, 7f, 0xFFffa040);
            r.x = hx; r.y = hy;
            if (boom) { rockets.remove(i); explode(hx, hy, 340f, 2300f, 95f, true); }
        }
    }

    // ------------------------------------------------------------------------------ other fx ----

    void lightning(float x, float y) {
        float top = sim.ceilY;
        fx.lightning(x + fx.rr(60), top, x, y);
        if (sfx != null) sfx.zap();
        rumble += 0.6f;
        sim.blast(x, y, 120f, 700f);
        Shape s = sim.pick(x, y, 36f, false);
        if (s != null && s.body.user instanceof Person) {
            Person p = (Person) s.body.user;
            for (int i = 0; i < Person.NPARTS; i++) hurt(p, i, 22f, p.rb[i].x, p.rb[i].y, 0f, -1f);
            ignite(p);
            if (!p.dead && p.blood < 30f) kill(p);
        } else if (s != null && s.body.user instanceof Ent) ignite((Ent) s.body.user);
    }

    void push(float x, float y) {
        fx.ring(x, y, 280f);
        sim.blast(x, y, 280f, 1400f);
        if (sfx != null) sfx.pop();
    }

    // ------------------------------------------------------------------------------- syringes ----

    String inject(Person p, int kind) {
        if (p.kind == Person.ROBOT || p.kind == Person.SKELETON) return "No hace efecto: no tiene sangre";
        RB at = p.rb[Person.CHEST];
        int col;
        String msg;
        switch (kind) {
            case Items.SYR_HEAL: heal(p); col = 0xFF6dff8a; msg = "Curado"; break;
            case Items.SYR_ADRENALINE:
                if (p.dead) return "Está muerto: usa Resurrección";
                p.adrenaline = 25f; p.ko = 0f; p.blood = Math.min(p.maxBlood, p.blood + 20f);
                col = 0xFFffd23f; msg = "¡Adrenalina!";
                break;
            case Items.SYR_LIFE:
                if (!p.dead) return "Todavía está vivo";
                if (!p.intact()) return "Está hecho pedazos: no hay resurrección";
                heal(p); p.dead = false; p.ko = 2.5f; p.tone = 0f; p.blood = p.maxBlood * 0.6f;
                stats.revives++;
                col = 0xFF7fd1ff; msg = "¡Ha vuelto a la vida!";
                break;
            case Items.SYR_ZOMBIE:
                if (p.kind == Person.ZOMBIE) return "Ya es un zombi";
                if (!p.intact()) return "Está hecho pedazos: no hay zombi";
                p.infected = 2.5f; p.bitten = true;
                col = 0xFF5aa02a; msg = "Infectado... en unos segundos";
                break;
            case Items.SYR_ACID: p.acid = 9f; col = 0xFFb6ff3a; msg = "Ácido en las venas"; break;
            default:
                if (p.dead) return "Está muerto";
                p.ko = 25f; p.tone = 0f; p.adrenaline = 0f;
                col = 0xFF9a8cff; msg = "Zzz...";
        }
        for (int i = 0; i < 10; i++) fx.add(Fx.SPARK, at.x, at.y, fx.rr(180), -80f - fx.rf() * 160f, 0.5f, 2.2f, col);
        fx.add(Fx.GLOW, at.x, at.y, 0, 0, 0.3f, 26f, col);
        if (sfx != null) sfx.pop();
        return msg;
    }

    void heal(Person p) {
        p.blood = p.maxBlood;
        p.burning = false; p.burnT = 0f; p.acid = 0f; p.ko = 0f; p.infected = -1f; p.bitten = false;
        for (int i = 0; i < Person.NPARTS; i++) {
            p.bleed[i] = 0f;
            if (p.broken[i]) p.broken[i] = false;
            p.hp[i] = Person.maxHp(p, i);
        }
        p.nw = 0; p.wNext = 0;          // wounds close
        stats.heals++;
    }

    private void updateAcid(Person p, float dt) {
        p.acid -= dt;
        p.blood -= 9f * dt;
        p.charT = Math.min(0.55f, p.charT + dt * 0.05f);
        if (rnd.nextFloat() < dt * 10f) { RB b = p.rb[rnd.nextInt(Person.NPARTS)]; fx.smoke(b.x, b.y, 7f, 0xFF8fd14a); }
        if (rnd.nextFloat() < dt * 0.6f) {
            int[] js = {Person.EL_F, Person.EL_B, Person.KNEE_F, Person.KNEE_B, Person.SH_F, Person.SH_B};
            int j = js[rnd.nextInt(js.length)];
            if (!p.severed[j]) sever(p, j, p.rb[Person.PART_JOINT_CHILD[j]].x, p.rb[Person.PART_JOINT_CHILD[j]].y);
        }
        if (p.blood <= 0f && !p.dead) kill(p);
        if (p.acid <= 0f && p.dead) skeletonize(p);
    }

    void skeletonize(Person p) {
        p.kind = Person.SKELETON;
        p.item = Items.SKELETON;
        p.skin = p.shirt = p.hair = 0xFFe9e5d3; p.pants = 0xFFe3dfcc;
        p.charT = 0f; p.burning = false;
        recolor(p);
        for (int i = 0; i < Person.NPARTS; i++) p.bleed[i] = 0f;
        for (int i = Person.UARM_F; i < Person.NPARTS; i++) {
            if (i == Person.FOOT_F || i == Person.FOOT_B) continue;
            for (Shape s : p.rb[i].shapes) s.r *= 0.6f;
        }
        fx.smoke(p.cx(), p.cy(), 16f, 0xFF8fd14a);
    }

    // ------------------------------------------------------------------------------ grabbing -----

    RB[] grabbed = new RB[8];
    int grabCount;

    // ---------------------------------------------------------------------------- construction ----

    int detonateC4() {
        int n = 0;
        for (int i = 0; i < ents.size(); i++) {
            Ent e = ents.get(i);
            if (e instanceof Prop && ((Prop) e).type == Items.C4 && ((Prop) e).fuse < 0f) { ((Prop) e).fuse = 0.05f + n * 0.08f; n++; }
        }
        return n;
    }

    /** A rope: a chain of capsules between two anchor points, pinned to the bodies at both ends. */
    Ent rope(RB a, float ax, float ay, RB b, float bx, float by) {
        float dx = bx - ax, dy = by - ay, len = (float) Math.sqrt(dx * dx + dy * dy);
        int n = Math.max(2, Math.min(70, (int) (len / 16f)));
        if (sim.bodies.size() + n > MAX_BODIES) return null;
        Prop r = ropeShell(n, ax, ay, bx, by);
        RB prev = a;
        float px = ax, py = ay;
        for (int i = 0; i < n; i++) {
            RB seg = r.bodies.get(i);
            Joint j = Joint.pin(prev, seg, px, py);
            j.breakForce = 9e7f;
            r.joint(j);
            sim.add(j);
            px = ax + dx * (i + 1) / n; py = ay + dy * (i + 1) / n;
            prev = seg;
        }
        Joint j = Joint.pin(prev, b, bx, by);
        j.breakForce = 9e7f;
        r.joint(j);
        sim.add(j);
        stats.links++;
        return r;
    }

    /** The bodies of a rope without any joint (also used when loading a scene). */
    Prop ropeShell(int n, float ax, float ay, float bx, float by) {
        float dx = bx - ax, dy = by - ay, len = (float) Math.sqrt(dx * dx + dy * dy);
        float ang = (float) Math.atan2(dy, dx), seg = len / n;
        Prop r = Prop.of(Items.ROPE_ITEM);
        r.param = n;
        int g = ++Person.groupSeq;
        for (int i = 0; i < n; i++) {
            float t = (i + 0.5f) / n;
            RB s = new RB(ax + dx * t, ay + dy * t, ang);
            s.add(Shape.capsule(Math.max(2f, seg / 2f - 1.5f), 3.2f).mat(0.6f, 0.5f, 0.02f).grp(g));
            s.linDamp = 0.1f; s.angDamp = 0.3f;
            r.body(s);
            sim.add(s);
        }
        ents.add(r);
        return r;
    }

    /** Welds or pins two bodies together (the pivot is the first anchor, or a wheel's own centre). */
    Joint link(RB a, float ax, float ay, RB b, float bx, float by, boolean weld) {
        float px = ax, py = ay;
        if (isRound(b) && !isRound(a)) { px = bx; py = by; }
        Joint j = weld ? Joint.weld(a, b, px, py) : Joint.pin(a, b, px, py);
        j.collide = false;
        if (weld) j.breakForce = 2.5e8f;
        sim.add(j);
        stats.links++;
        if (!weld) {
            Prop wa = a.user instanceof Prop ? (Prop) a.user : null, wb = b.user instanceof Prop ? (Prop) b.user : null;
            Prop wheel = wb != null && wb.motorized ? wb : (wa != null && wa.motorized ? wa : null);
            if (wheel != null) { if (wheel.motor == 0) wheel.motor = 1; setMotor(wheel); }
        }
        return j;
    }

    private static boolean isRound(RB b) {
        return b.shapes.size() == 1 && b.shapes.get(0).type == Shape.CIRCLE;
    }

    /** Applies a wheel's motor state to every pin it has. */
    void setMotor(Prop wheel) {
        RB w = wheel.main();
        for (int i = 0; i < w.joints.size(); i++) {
            Joint j = w.joints.get(i);
            if (j.kind != Joint.REVOLUTE || j.user != null) continue;
            j.motor = wheel.motor != 0;
            j.motorSpeed = wheel.motor * 9.5f * (j.b == w ? 1f : -1f);
            j.motorMax = 6e7f;
        }
    }

    /** Places a ready-made construction. Returns its pieces (for undo). */
    java.util.ArrayList<Ent> buildAt(int item, float x, float y) {
        float fl = sim.floorY;
        x = Math.max(sim.wallL + 150, Math.min(sim.wallR - 150, x));
        java.util.ArrayList<Ent> list = Build.build(this, item, x, fl);
        if (!list.isEmpty()) fx.add(Fx.GLOW, x, fl - 60, 0, 0, 0.25f, 120f, 0xFFffffff);
        return list;
    }

    /** Loading: a person of the given item, without the spawn effects or position clamping. */
    Person newPersonRaw(int item, float scale, int dir, int kind) {
        if (!canAdd()) return null;
        Person.headScale = 1f;
        Person p = Person.create(sim, 400f, -100f, scale, dir, kind, rnd);
        p.item = item;
        ents.add(p);
        return p;
    }

    /** Loading: a prop of the given item at the origin of the room. */
    Ent spawnRaw(int item) {
        Ent e;
        if (item == Items.TURRET) e = add(Prop.turret(sim, 400f, -100f, 1));
        else if (item == Items.DRONE) e = add(Prop.drone(sim, 400f, -200f));
        else {
            Prop q = Prop.make(sim, item, 400f, -100f, rnd);
            if (q == null) return null;
            q.register(sim);
            e = add(q);
        }
        return e;
    }

    /** Loading: cut a joint without effects. */
    void silentSever(Person p, int j) {
        if (p.severed[j]) return;
        if (!p.jt[j].removed) sim.remove(p.jt[j]);
        p.severed[j] = true;
        int g = ++Person.groupSeq;
        for (int part : DETACH[j]) for (Shape s : p.rb[part].shapes) s.group = g;
    }
}

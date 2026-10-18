package com.frameboost.ragdoll;

import java.util.Random;

/** Everything that is not a person: crates, balls, bombs, guns, wheels, turrets... Each is one or two rigid bodies. */
final class Prop extends Ent {
    int type;                       // an Items.* id
    int color = 0xFFB7864B;
    boolean explosive, sharp, motorized, stuck, firing, on = true, dead;
    float fuse = -1f;
    float blastR, blastPow, blastDmg;
    int motor;                      // wheels: 0 off, 1 forward, -1 backward
    boolean thrust;                 // thruster
    float fireCd, shotCd, reloadT, thinkT, work, aimA;
    int ammo = 12;
    float hp = 100f, flash, age, trim;
    float dens = 1f;                // block density (constructions)
    // gib chunks: what they are made of (for drawing and for the blood they leave)
    boolean bone, bloody, round;
    int color2;                     // colour of the raw inside of a flesh chunk / of its blood
    float splatT;
    float lastSp;                   // speed at the end of the previous frame
    float wear;                     // damage taken by breakable things (crates, planks, blocks)
    boolean gib, frag;              // flesh chunk / fragment of something that broke
    // animals
    int face = 1;                   // which way the head points
    float hunger, digestT = -1f, biteT, legPhase, barkT, stinkT, eatT;
    int stomach;                    // body parts eaten and not yet pooped
    Person meal;                    // the corpse being eaten
    int mealPart = -1;
    float goalX, goalY, homeX;      // drone flight
    Ent target;
    String mood = "";
    float hw, hh;                   // main box half sizes (drawing)
    RB barrel;                      // turret barrel
    Joint aimJoint;

    int homeDir() { return homeDirV; }
    int homeDirV = 1;
    boolean machine() { return type == Items.TURRET || type == Items.DRONE; }
    boolean animal() { return Items.isAnimal(type); }
    boolean fixedMachine() { return Items.isMachine(type); }

    // animal sizes: body half width/height, head radius, head x/y, leg length, foot radius, density, colour, hp
    static final float[][] ANIMAL = {
        {26f, 10f, 10f, 31f, -12f, 16f, 4f, 0.9f},
        {30f, 15f, 13f, 33f, -3f, 11f, 5f, 1.0f},
        {11f, 5f, 5.5f, 13f, -2f, 5f, 2.5f, 0.8f},
    };
    static final int[] ANIMAL_COL = {0xFFa8743f, 0xFFeea3ad, 0xFF8d8a86};
    static final float[] ANIMAL_HP = {70f, 110f, 20f};
    float[] dims() { return ANIMAL[type - Items.DOG]; }
    float legLen() { return dims()[5]; }

    /** Turns an animal round: the head (a physical shape) moves to the other end. */
    void turn(int f) {
        if (f == face || !animal()) return;
        face = f;
        Shape head = main().shapes.get(1);
        head.ox = Math.abs(head.ox) * f;
    }

    @Override float cx() { return bodies.get(0).x; }
    @Override float cy() { return bodies.get(0).y; }
    RB main() { return bodies.get(0); }

    /** A plain rigid block (building pieces): half sizes, colour and density decide mass and look. */
    static Prop block(float x, float y, float hw, float hh, int color, float dens, boolean flammable) {
        Prop p = of(Items.BLOCK);
        p.color = color; p.hw = hw; p.hh = hh; p.dens = dens; p.flammable = flammable;
        box(p, x, y, hw, hh, dens, 0.7f, 0.05f);
        return p;
    }

    /** A round lump (organs, brain): a gib shaped like a ball. */
    static Prop gibBall(float x, float y, float r, int color, float dens) {
        Prop p = of(Items.BLOCK);
        p.color = color; p.hw = p.hh = r; p.dens = dens; p.flammable = false; p.gib = true; p.round = true;
        circle(p, x, y, r, dens, 0.8f, 0.08f);
        return p;
    }

    static Prop of(int item) { Prop p = new Prop(); p.item = item; p.type = item; p.flammable = false; return p; }

    private static int[] BALLC = {0xFFe0463c, 0xFF3aa0e0, 0xFF4cbf6b, 0xFFf2b134, 0xFFb06ad8, 0xFFf27ab0};

    static Prop make(Sim sim, int item, float x, float y, Random rnd) {
        Prop p = of(item);
        RB b;
        switch (item) {
            case Items.CRATE: case Items.BIGCRATE: {
                float size = item == Items.CRATE ? 60f : 110f;
                p.param = size; p.hw = p.hh = size / 2; p.color = 0xFFB7864B; p.flammable = true;
                b = box(p, x, y, size / 2, size / 2, item == Items.CRATE ? 0.8f : 0.55f, 0.75f, 0.08f);
                break;
            }
            case Items.PLANK: {
                p.param = 260f; p.hw = 130f; p.hh = 8f; p.color = 0xFFc9a066; p.flammable = true;
                b = box(p, x, y, 130f, 8f, 0.7f, 0.75f, 0.05f);
                break;
            }
            case Items.BARREL: case Items.XBARREL: {
                boolean red = item == Items.XBARREL;
                p.hw = 19f; p.hh = 27f; p.color = red ? 0xFFc9302c : 0xFF4f7fb0;
                if (red) { p.explosive = true; p.blastR = 330f; p.blastPow = 1900f; p.blastDmg = 90f; p.flammable = true; }
                b = box(p, x, y, 19f, 27f, 0.8f, 0.6f, 0.1f);
                break;
            }
            case Items.TNT: {
                p.hw = p.hh = 20f; p.color = 0xFFd2402f; p.explosive = true; p.flammable = true;
                p.blastR = 430f; p.blastPow = 2300f; p.blastDmg = 110f;
                b = box(p, x, y, 20f, 20f, 0.8f, 0.7f, 0.05f);
                break;
            }
            case Items.STEEL: {
                p.hw = 40f; p.hh = 25f; p.color = 0xFF8d96a3;
                b = box(p, x, y, 40f, 25f, 3.0f, 0.5f, 0.05f);
                break;
            }
            case Items.PLATFORM: {
                p.param = 320f; p.hw = 160f; p.hh = 10f; p.color = 0xFF6d7683;
                b = box(p, x, y, 160f, 10f, 1f, 0.7f, 0.05f);
                b.makeStatic();
                break;
            }
            case Items.BALL: {
                p.param = 24f; p.color = BALLC[rnd.nextInt(BALLC.length)];
                b = circle(p, x, y, 24f, 0.4f, 0.7f, 0.62f);
                break;
            }
            case Items.BALLOON: {
                p.param = 26f; p.color = BALLC[rnd.nextInt(BALLC.length)];
                b = circle(p, x, y, 26f, 0.03f, 0.3f, 0.3f);
                b.gravScale = -0.1f; b.linDamp = 0.9f; b.angDamp = 0.6f;
                break;
            }
            case Items.WHEEL: case Items.MOTOR: {
                p.param = 26f; p.motorized = item == Items.MOTOR; p.color = p.motorized ? 0xFFf2b134 : 0xFF9aa3ad;
                b = circle(p, x, y, 26f, 0.5f, 1.3f, 0.08f);
                break;
            }
            case Items.BOWLING: {
                p.param = 16f; p.color = 0xFF1d2a5a;
                b = circle(p, x, y, 16f, 6.5f, 0.5f, 0.1f);
                break;
            }
            case Items.MELON: {
                p.param = 22f; p.color = 0xFF3f9a3c; p.flammable = true;
                b = circle(p, x, y, 22f, 0.35f, 0.6f, 0.2f);
                break;
            }
            case Items.GRENADE: {
                p.explosive = true; p.fuse = 3f; p.color = 0xFF4d6030;
                p.blastR = 280f; p.blastPow = 1700f; p.blastDmg = 70f;
                b = circle(p, x, y, 8f, 2.2f, 0.6f, 0.3f);
                break;
            }
            case Items.DYNAMITE: {
                p.explosive = true; p.fuse = 4f; p.color = 0xFFcc2e2e; p.hw = 17f; p.hh = 6f;
                p.blastR = 430f; p.blastPow = 2400f; p.blastDmg = 110f;
                b = box(p, x, y, 17f, 6f, 1.4f, 0.7f, 0.1f);
                break;
            }
            case Items.C4: {
                p.explosive = true; p.color = 0xFF7a7f3a; p.hw = 12f; p.hh = 7f;
                p.blastR = 380f; p.blastPow = 2200f; p.blastDmg = 120f;
                b = box(p, x, y, 12f, 7f, 1.2f, 0.8f, 0.05f);
                break;
            }
            case Items.KNIFE: case Items.SWORD: {
                boolean sw = item == Items.SWORD;
                p.sharp = true; p.color = 0xFFdfe4ea;
                b = new RB(x, y, 0);
                b.add(Shape.capsule(sw ? 48f : 17f, sw ? 3.4f : 2.8f).at(sw ? 14f : 6f, 0, 0).mat(1.6f, 0.4f, 0.05f).col(0xFFdfe4ea));
                b.add(Shape.capsule(sw ? 10f : 6f, 3.6f).at(sw ? -42f : -16f, 0, 0).mat(1.2f, 0.6f, 0.05f).col(0xFF5b3a1e));
                p.hw = sw ? 58f : 22f;
                b.user = p; p.bodies.add(b); sim.add(b);
                break;
            }
            case Items.THRUSTER: {
                p.color = 0xFFd0d4da; p.hw = 7f; p.hh = 18f;
                b = box(p, x, y, 7f, 18f, 1.4f, 0.5f, 0.05f);
                break;
            }
            case Items.GUN_PISTOL: case Items.GUN_RIFLE: case Items.GUN_MG: {
                int k = item == Items.GUN_PISTOL ? 0 : (item == Items.GUN_RIFLE ? 1 : 2);
                p.param = k; p.color = k == 1 ? 0xFF4a4f57 : (k == 2 ? 0xFF3b3f46 : 0xFF5a5f68);
                float hl = k == 0 ? 13f : (k == 1 ? 25f : 23f);
                b = new RB(x, y, 0);
                b.add(Shape.capsule(hl, k == 2 ? 6f : 4.5f).mat(2.2f, 0.5f, 0.05f).col(p.color));
                b.add(Shape.box(4f, 9f).at(-hl * 0.55f, 9f, 0).mat(1.5f, 0.5f, 0.05f).col(0xFF2b2b30));
                p.hw = hl;
                b.user = p; p.bodies.add(b); sim.add(b);
                break;
            }
            case Items.DOG: case Items.PIG: case Items.RAT: {
                float[] d = ANIMAL[item - Items.DOG];
                p.color = ANIMAL_COL[item - Items.DOG]; p.hp = ANIMAL_HP[item - Items.DOG];
                p.hw = d[0]; p.hh = d[1]; p.flammable = true;
                p.face = rnd.nextBoolean() ? 1 : -1;
                p.hunger = 0.4f + rnd.nextFloat() * 0.4f;
                b = new RB(x, y, 0);
                Shape torso = Shape.box(d[0], d[1]).mat(d[7], 0.5f, 0.05f).col(p.color); torso.user = p;
                Shape head = Shape.circle(d[2]).at(d[3] * p.face, d[4], 0).mat(d[7] * 0.8f, 0.5f, 0.05f).col(p.color); head.user = p;
                Shape ff = Shape.circle(d[6]).at(d[0] * 0.72f, d[1] + d[5] - d[6], 0).mat(0.5f, 0.06f, 0f).col(p.color); ff.user = p;
                Shape fb = Shape.circle(d[6]).at(-d[0] * 0.72f, d[1] + d[5] - d[6], 0).mat(0.5f, 0.06f, 0f).col(p.color); fb.user = p;
                b.add(torso); b.add(head); b.add(ff); b.add(fb);
                b.angDamp = 0.5f;
                b.user = p; p.bodies.add(b);
                break;
            }
            case Items.FRIDGE: {
                p.color = 0xFFe8edf2; p.hw = 46f; p.hh = 100f; p.on = false;
                b = box(p, x, y, 46f, 100f, 0.9f, 0.7f, 0.05f);
                break;
            }
            case Items.SAW: {
                // a stand and a toothed blade; both fixed in place (the blade spins kinematically)
                p.param = 34f; p.sharp = true; p.color = 0xFF5d6875; p.hw = 34f; p.hh = 50f; p.on = true;
                RB stand = new RB(x, y - 28f, 0);
                Shape st = Shape.box(16f, 28f).mat(3f, 0.8f, 0.05f).col(0xFF3a4048); st.user = p; stand.add(st);
                stand.user = p; p.bodies.add(stand);
                b = new RB(x, y - 62f, 0);
                Shape bl = Shape.circle(34f).mat(3f, 0.9f, 0.05f).col(0xFFc9ced6); bl.user = p; b.add(bl);
                b.user = p; p.bodies.add(b);
                break;
            }
            case Items.SPIKES: {
                p.sharp = true; p.color = 0xFF6f7782; p.hw = 70f; p.hh = 9f;
                b = box(p, x, y - 9f, 70f, 9f, 3f, 0.9f, 0f);
                break;
            }
            case Items.FAN: {
                p.color = 0xFF4f6d8a; p.hw = 26f; p.hh = 34f; p.on = true;
                b = new RB(x, y - 34f, 0);
                Shape h = Shape.box(12f, 26f).at(0, -8f, 0).mat(2f, 0.6f, 0.05f).col(p.color); h.user = p;
                Shape ft = Shape.box(26f, 6f).at(0, 28f, 0).mat(2f, 0.8f, 0.05f).col(0xFF2f353d); ft.user = p;
                b.add(h); b.add(ft);
                b.user = p; p.bodies.add(b);
                break;
            }
            case Items.CRUSHER: {
                p.color = 0xFF5d6875; p.hw = 120f; p.hh = 156f; p.on = true;
                RB beam = new RB(x, y - 312f, 0);
                Shape bs = Shape.box(120f, 12f).mat(3f, 0.8f, 0.05f).col(0xFF3a4048); bs.user = p; beam.add(bs);
                beam.user = p; p.bodies.add(beam);
                for (int k = -1; k <= 1; k += 2) {
                    RB col = new RB(x + k * 110f, y - 150f, 0);
                    Shape cs = Shape.box(10f, 150f).mat(3f, 0.8f, 0.05f).col(0xFF3a4048); cs.user = p; col.add(cs);
                    col.user = p; p.bodies.add(col);
                }
                b = new RB(x, y - 280f, 0);
                Shape pl = Shape.box(92f, 16f).mat(4f, 0.8f, 0.02f).col(0xFFd9a420); pl.user = p; b.add(pl);
                b.user = p; p.bodies.add(b);
                p.goalY = y - 280f; p.homeX = y - 18f;          // top and bottom of the stroke
                break;
            }
            case Items.TESLA: {
                p.color = 0xFF7a5a3a; p.hw = 28f; p.hh = 78f; p.on = true;
                b = new RB(x, y - 78f, 0);
                Shape base = Shape.box(28f, 8f).at(0, 70f, 0).mat(3f, 0.8f, 0.05f).col(0xFF2f353d); base.user = p;
                Shape colm = Shape.box(9f, 54f).at(0, 8f, 0).mat(3f, 0.6f, 0.05f).col(0xFFb87333); colm.user = p;
                Shape ball = Shape.circle(17f).at(0, -60f, 0).mat(3f, 0.6f, 0.05f).col(0xFFc9ced6); ball.user = p;
                b.add(base); b.add(colm); b.add(ball);
                b.user = p; p.bodies.add(b);
                break;
            }
            case Items.BAT: {
                p.color = 0xFFb07a45; p.hw = 42f; p.flammable = true;
                b = new RB(x, y, 0);
                Shape sh = Shape.capsule(36f, 5.5f).mat(1.1f, 0.7f, 0.1f).col(p.color); sh.user = p; b.add(sh);
                b.user = p; p.bodies.add(b);
                break;
            }
            case Items.HAMMER: {
                p.color = 0xFF8d96a3; p.hw = 30f;
                b = new RB(x, y, 0);
                Shape hd = Shape.capsule(22f, 3f).at(-6f, 0, 0).mat(0.9f, 0.7f, 0.05f).col(0xFF6b4a2f); hd.user = p;
                Shape hh = Shape.box(7f, 13f).at(20f, 0, 0).mat(7f, 0.6f, 0.05f).col(p.color); hh.user = p;
                b.add(hd); b.add(hh);
                b.user = p; p.bodies.add(b);
                break;
            }
            case Items.POOP: {
                p.hw = 10f; p.hh = 7f; p.color = 0xFF6b4423; p.stinkT = 40f;
                b = box(p, x, y, 10f, 7f, 1.1f, 0.95f, 0f);
                break;
            }
            default: return null;
        }
        return p;
    }

    private static RB box(Prop p, float x, float y, float hw, float hh, float dens, float fr, float rest) {
        RB b = new RB(x, y, 0);
        Shape s = Shape.box(hw, hh).mat(dens, fr, rest).col(p.color);
        s.user = p;
        b.add(s);
        b.user = p;
        p.bodies.add(b);
        return b;
    }

    private static RB circle(Prop p, float x, float y, float r, float dens, float fr, float rest) {
        RB b = new RB(x, y, 0);
        Shape s = Shape.circle(r).mat(dens, fr, rest).col(p.color);
        s.user = p;
        b.add(s);
        b.user = p;
        p.bodies.add(b);
        return b;
    }

    /** Adds the bodies/joints of a prop to the simulation (kinds that were built without it). */
    void register(Sim sim) {
        for (RB b : bodies) if (!sim.bodies.contains(b)) sim.add(b);
        for (Joint j : joints) if (!sim.joints.contains(j)) sim.add(j);
    }

    static Prop turret(Sim sim, float x, float y, int dir) {
        Prop p = of(Items.TURRET);
        p.color = 0xFF5d6875; p.on = true; p.hp = p.maxHp(); p.homeX = x; p.homeDirV = dir;
        RB base = new RB(x, y + 12f, 0);
        Shape bs = Shape.box(26f, 10f).mat(2.2f, 0.9f, 0.05f).col(0xFF2f353d); bs.user = p;
        Shape legL = Shape.capsule(14f, 3.5f).at(-20f, 14f, 0.9f).mat(1f, 0.9f, 0.05f).col(0xFF2f353d); legL.user = p;
        Shape legR = Shape.capsule(14f, 3.5f).at(20f, 14f, -0.9f).mat(1f, 0.9f, 0.05f).col(0xFF2f353d); legR.user = p;
        base.add(bs); base.add(legL); base.add(legR);
        base.user = p; p.bodies.add(base); sim.add(base);
        RB gun = new RB(x + 22f * dir, y - 8f, dir > 0 ? 0f : (float) Math.PI);
        Shape g = Shape.capsule(22f, 4.5f).at(0, 0, 0).mat(1.4f, 0.4f, 0.05f).col(0xFF2a2e35); g.user = p;
        Shape head = Shape.box(14f, 11f).at(-14f, 0, 0).mat(1.4f, 0.4f, 0.05f).col(0xFF5d6875); head.user = p;
        gun.add(g); gun.add(head);
        gun.user = p; p.bodies.add(gun); sim.add(gun);
        gun.shapes.get(0).group = gun.shapes.get(1).group = base.shapes.get(0).group = base.shapes.get(1).group = base.shapes.get(2).group = ++Person.groupSeq;
        p.barrel = gun;
        Joint j = Joint.pin(base, gun, x - 14f * dir * 0 + 0f, y - 8f);
        j.muscle(12f, 1.0f, 3e8f);
        j.target = 0f;
        p.aimA = dir > 0 ? 0f : (float) Math.PI;
        p.aimJoint = p.joint(j);
        sim.add(j);
        p.hw = 26f; p.hh = 10f;
        return p;
    }

    static Prop anchor(Sim sim, float x, float y) {
        Prop p = of(Items.ANCHOR_ITEM);
        p.color = 0xFF9aa3ad;
        RB b = circle(p, x, y, 6f, 1f, 0.5f, 0.1f);
        b.makeStatic();
        sim.add(b);
        return p;
    }

    float maxHp() { return type == Items.DRONE ? 70f : 160f; }

    static Prop drone(Sim sim, float x, float y) {
        Prop p = of(Items.DRONE);
        p.color = 0xFF39414c; p.hp = p.maxHp(); p.homeX = x; p.goalX = x; p.goalY = y;
        RB b = new RB(x, y, 0);
        Shape s = Shape.capsule(20f, 7f).mat(0.9f, 0.5f, 0.1f).col(p.color); s.user = p;
        b.add(s);
        b.linDamp = 0.25f; b.angDamp = 0.6f;
        b.user = p; p.bodies.add(b); sim.add(b);
        p.hw = 28f; p.hh = 7f;
        return p;
    }
}

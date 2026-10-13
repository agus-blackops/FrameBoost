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
    float goalX, goalY, homeX;      // drone flight
    Ent target;
    String mood = "";
    float hw, hh;                   // main box half sizes (drawing)
    RB barrel;                      // turret barrel
    Joint aimJoint;

    int homeDir() { return homeDirV; }
    int homeDirV = 1;
    boolean machine() { return type == Items.TURRET || type == Items.DRONE; }

    @Override float cx() { return bodies.get(0).x; }
    @Override float cy() { return bodies.get(0).y; }
    RB main() { return bodies.get(0); }

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

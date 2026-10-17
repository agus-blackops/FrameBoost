package com.frameboost.ragdoll;

/**
 * An active ragdoll: 13 rigid parts joined by 12 pins with limits and PD "muscles".
 * Standing and walking use a puppeteer: a soft spring guides the pelvis above planted feet (the reaction
 * pushes on whatever touches the ground) while two-link IK sets the leg and arm joint targets.
 */
final class Person extends Ent {
    static final int HUMAN = 0, ROBOT = 1, ZOMBIE = 2, SKELETON = 3, SOLDIER = 4, MEDIC = 5, FIREFIGHTER = 6, THUG = 7;

    static final int PELVIS = 0, CHEST = 1, HEAD = 2, UARM_F = 3, FARM_F = 4, UARM_B = 5, FARM_B = 6,
            THIGH_F = 7, SHIN_F = 8, FOOT_F = 9, THIGH_B = 10, SHIN_B = 11, FOOT_B = 12, NPARTS = 13;
    static final int NECK = 0, WAIST = 1, SH_F = 2, EL_F = 3, SH_B = 4, EL_B = 5, HIP_F = 6, KNEE_F = 7, ANK_F = 8,
            HIP_B = 9, KNEE_B = 10, ANK_B = 11, NJOINTS = 12;
    static final String[] PART_NAMES = {"Pelvis", "Torso", "Cabeza", "Brazo (frente)", "Antebrazo (frente)", "Brazo (atrás)",
            "Antebrazo (atrás)", "Muslo (frente)", "Pierna (frente)", "Pie (frente)", "Muslo (atrás)", "Pierna (atrás)", "Pie (atrás)"};
    /** Joint that carries each part (the one that goes limp when the part is broken). */
    static final int[] PART_JOINT = {-1, WAIST, NECK, SH_F, EL_F, SH_B, EL_B, HIP_F, KNEE_F, ANK_F, HIP_B, KNEE_B, ANK_B};
    static int groupSeq = 100;
    static float headScale = 1f;
    /** The body part on the child side of each joint. */
    static final int[] PART_JOINT_CHILD = {HEAD, CHEST, UARM_F, FARM_F, UARM_B, FARM_B, THIGH_F, SHIN_F, FOOT_F, THIGH_B, SHIN_B, FOOT_B};
    static float maxHp(Person p, int part) { return (part == HEAD ? 60f : 100f) * p.hpMul; }
    float hpMul = 1f;

    final RB[] rb = new RB[NPARTS];
    final Joint[] jt = new Joint[NJOINTS];
    final float[] hp = new float[NPARTS];
    final boolean[] broken = new boolean[NPARTS];     // fractured: its joint is limp
    final boolean[] severed = new boolean[NJOINTS];
    final float[] bleed = new float[NPARTS];
    final boolean[] gibbed = new boolean[NPARTS];     // blown to pieces: invisible, frozen sensor

    // wounds drawn on the skin (People Playground style), in the local frame of each part's shape
    static final int MAXW = 48, W_HOLE = 0, W_CUT = 1, W_BURN = 2, W_BRUISE = 3;
    final float[] wu = new float[MAXW], wv = new float[MAXW], wa = new float[MAXW], ws = new float[MAXW];
    final byte[] wpart = new byte[MAXW], wkind = new byte[MAXW];
    int nw, wNext;

    int kind, dir = 1, group;
    float scale = 1f;
    int skin, shirt, pants, hair;
    float mass;
    float blood = 100f, maxBlood = 100f;
    boolean dead;
    float ko, tone;
    float strength = 1f, speed = 1f;
    float flash;

    // inputs written by the AI (Mind) or the player
    int walk;                       // -1, 0, +1
    boolean run;
    boolean aim;                    // arm points along (aimX, aimY)
    float aimX = 1f, aimY = 0f;
    boolean armsUp, armsForward;    // panic / zombie reach
    float reachX, reachY;           // hands target (world) when reach is on
    boolean reach;
    boolean kneel;                  // medics working on a patient
    boolean punch;
    float punchT;
    boolean held;                   // being dragged by the player
    Ent target;
    String mood = "";
    final float[] hitCd = new float[NPARTS];
    float baseStrength = 1f, adrenaline, acid, infected = -1f, biteCd, thinkT, shotCd, reloadT;
    int ammo = 12;
    boolean bitten;
    float work;

    // controller state
    int stepFoot;                   // 0 = front swings, 1 = back swings
    float stepP = 1f;
    final float[] plantX = new float[2], plantY = new float[2];
    boolean planted;
    float relFromF, relFromB;
    float balanceStress;
    float standT;
    int groundSteps;
    boolean able;
    private boolean severed(int part) { int j = PART_JOINT[part]; return j >= 0 && severed[j]; }

    Person(int kind) { this.kind = kind; }

    @Override float cx() { return rb[PELVIS].x; }
    @Override float cy() { return rb[PELVIS].y; }

    /** Whole enough to be brought back (head on, torso and pelvis in one piece). */
    boolean intact() { return !severed[NECK] && !severed[WAIST] && !gibbed[HEAD] && !gibbed[CHEST] && !gibbed[PELVIS]; }
    boolean organic() { return kind != ROBOT && kind != SKELETON && kind != ZOMBIE; }
    boolean hostile() { return !dead && (kind == ZOMBIE || kind == THUG); }
    float h(float v) { return v * scale; }

    // ------------------------------------------------------------------------------- build ----

    static Person create(Sim sim, float x, float y, float s, int dir, int kind, java.util.Random rnd) {
        Person p = new Person(kind);
        p.item = Items.HUMAN;
        p.scale = s; p.dir = dir; p.group = ++groupSeq;
        p.flammable = true;
        p.skin = Look.SKIN[rnd.nextInt(Look.SKIN.length)];
        p.shirt = Look.SHIRT[rnd.nextInt(Look.SHIRT.length)];
        p.pants = Look.PANTS[rnd.nextInt(Look.PANTS.length)];
        p.hair = Look.HAIR[rnd.nextInt(Look.HAIR.length)];
        float dens = 0.9f, hpMul = 1f;
        switch (kind) {
            case ROBOT: p.skin = 0xFFa9b4bf; p.shirt = 0xFF68788a; p.pants = 0xFF4b5666; p.hair = 0xFFd8dee6; p.flammable = false; p.blood = 260f; p.strength = 1.3f; dens = 1.4f; hpMul = 2.2f; break;
            case ZOMBIE: p.skin = 0xFF7fa65a; p.shirt = 0xFF5d5a4a; p.pants = 0xFF3b3f4c; p.hair = 0xFF2d3322; p.blood = 170f; p.strength = 0.8f; p.speed = 0.55f; hpMul = 1.3f; break;
            case SKELETON: p.skin = p.shirt = p.hair = 0xFFe9e5d3; p.pants = 0xFFe3dfcc; p.flammable = false; p.blood = 90f; dens = 0.55f; hpMul = 0.8f; break;
            case SOLDIER: p.shirt = 0xFF55663a; p.pants = 0xFF4a5232; p.blood = 130f; p.strength = 1.1f; hpMul = 1.3f; break;
            case MEDIC: p.shirt = 0xFFf4f6f8; p.pants = 0xFF3f8f9a; break;
            case FIREFIGHTER: p.shirt = 0xFFb8402c; p.pants = 0xFF2d2f36; p.flammable = false; p.blood = 120f; hpMul = 1.2f; break;
            case THUG: p.shirt = 0xFF26282e; p.pants = 0xFF3a4a6a; p.hair = 0xFF111111; p.blood = 125f; p.strength = 1.1f; hpMul = 1.2f; break;
            default: break;
        }
        p.maxBlood = p.blood;
        p.baseStrength = p.strength;
        float d = dir;
        // part table: centre (relative to pelvis), half length, radius, vertical?
        float[][] g = {
            {0, 0, 6, 11, 1},        // pelvis
            {0, -38, 11, 11.5f, 1},  // chest
            {0, -78 - 13 * (headScale - 1f), 0, 13 * headScale, 0},      // head (circle)
            {0, -38, 11, 5, 1}, {0, -10.5f, 11, 4.5f, 1},      // arm F
            {0, -38, 11, 5, 1}, {0, -10.5f, 11, 4.5f, 1},      // arm B
            {0, 24, 15, 8, 1}, {0, 62, 15, 6.5f, 1}, {7 * d, 86, 7, 5, 0},   // leg F
            {0, 24, 15, 8, 1}, {0, 62, 15, 6.5f, 1}, {7 * d, 86, 7, 5, 0},   // leg B
        };
        int[] cols = {p.pants, p.shirt, p.skin, p.shirt, p.skin, p.shirt, p.skin, p.pants, p.pants, 0xFF2b2b30, p.pants, p.pants, 0xFF2b2b30};
        for (int i = 0; i < NPARTS; i++) {
            RB b = new RB(x + g[i][0] * s, y + g[i][1] * s, 0);
            Shape sh;
            if (i == HEAD) sh = Shape.circle(g[i][3] * s);
            else sh = Shape.capsule(g[i][2] * s, g[i][3] * s).at(0, 0, g[i][4] > 0 ? (float) Math.PI / 2 : 0f);
            sh.mat(dens, i >= THIGH_F && (i == FOOT_F || i == FOOT_B) ? 1.6f : 0.7f, 0.02f).grp(p.group).col(cols[i]);
            sh.user = p;
            sh.friction = (i == FOOT_F || i == FOOT_B) ? 1.5f : 0.7f;
            b.linDamp = 0.05f; b.angDamp = 0.12f;
            b.add(sh);
            p.rb[i] = p.body(b);
            p.hp[i] = (i == HEAD ? 60f : 100f) * hpMul;
            p.hpMul = hpMul;
            p.mass += b.m;
            sim.add(b);
        }
        // joints (anchor positions relative to pelvis centre)
        j(p, sim, NECK, CHEST, HEAD, 0, -62, 4e7f * headScale, L(d, -0.55f, 0.55f), x, y, s);
        j(p, sim, WAIST, PELVIS, CHEST, 0, -16, 1.4e8f, L(d, -0.5f, 0.8f), x, y, s);
        j(p, sim, SH_F, CHEST, UARM_F, 0, -52, 6e7f, L(d, -3.1f, 1.2f), x, y, s);
        j(p, sim, EL_F, UARM_F, FARM_F, 0, -24, 3e7f, L(d, -2.5f, 0.02f), x, y, s);
        j(p, sim, SH_B, CHEST, UARM_B, 0, -52, 6e7f, L(d, -3.1f, 1.2f), x, y, s);
        j(p, sim, EL_B, UARM_B, FARM_B, 0, -24, 3e7f, L(d, -2.5f, 0.02f), x, y, s);
        j(p, sim, HIP_F, PELVIS, THIGH_F, 0, 5, 3e8f, L(d, -1.9f, 0.7f), x, y, s);
        j(p, sim, KNEE_F, THIGH_F, SHIN_F, 0, 43, 2.4e8f, L(d, -0.02f, 2.6f), x, y, s);
        j(p, sim, ANK_F, SHIN_F, FOOT_F, 0, 81, 8e7f, L(d, -0.8f, 0.7f), x, y, s);
        j(p, sim, HIP_B, PELVIS, THIGH_B, 0, 5, 3e8f, L(d, -1.9f, 0.7f), x, y, s);
        j(p, sim, KNEE_B, THIGH_B, SHIN_B, 0, 43, 2.4e8f, L(d, -0.02f, 2.6f), x, y, s);
        j(p, sim, ANK_B, SHIN_B, FOOT_B, 0, 81, 8e7f, L(d, -0.8f, 0.7f), x, y, s);
        p.tone = 0f;
        return p;
    }

    private static float[] L(float d, float lo, float hi) { return d > 0 ? new float[]{lo, hi} : new float[]{-hi, -lo}; }

    private static final float[] PEAK = new float[NJOINTS];

    private static void j(Person p, Sim sim, int idx, int pa, int pb, float ax, float ay, float torque, float[] lim, float x, float y, float s) {
        Joint jn = Joint.pin(p.rb[pa], p.rb[pb], x + ax * s, y + ay * s);
        jn.limits(lim[0], lim[1]);
        jn.muscle(8f, 0.9f, torque * s * s * s);
        jn.baseTorque = torque * s * s * s;
        jn.muscle = false;
        jn.tag = idx;
        jn.collide = false;
        // limbs tear off under extreme loads
        float beyond = 0f;
        switch (idx) {
            case NECK: beyond = p.rb[HEAD].m; jn.breakForce = beyond * 300000f; break;
            case WAIST: jn.breakForce = 0f; break;
            case SH_F: case SH_B: beyond = p.rb[UARM_F].m + p.rb[FARM_F].m; jn.breakForce = beyond * 240000f; break;
            case EL_F: case EL_B: beyond = p.rb[FARM_F].m; jn.breakForce = beyond * 240000f; break;
            case HIP_F: case HIP_B: beyond = p.rb[THIGH_F].m + p.rb[SHIN_F].m + p.rb[FOOT_F].m; jn.breakForce = beyond * 220000f; break;
            case KNEE_F: case KNEE_B: beyond = p.rb[SHIN_F].m + p.rb[FOOT_F].m; jn.breakForce = beyond * 230000f; break;
            default: jn.breakForce = 0f; break;
        }
        p.jt[idx] = p.joint(jn);
        sim.add(jn);
    }

    // ----------------------------------------------------------------------------- queries ----

    boolean legsBroken() { return broken[THIGH_F] || broken[SHIN_F] || broken[THIGH_B] || broken[SHIN_B]
            || severed[HIP_F] || severed[KNEE_F] || severed[HIP_B] || severed[KNEE_B]; }

    boolean canMove() { return !dead && ko <= 0f && !severed[NECK] && !legsBroken(); }

    boolean armWorks() { return !severed[SH_F] && !severed[EL_F] && !broken[UARM_F] && !broken[FARM_F]; }

    float pelvisX() { return rb[PELVIS].x; }

    boolean upright() {
        RB c = rb[CHEST], p = rb[PELVIS];
        float dy = p.y - c.y, dx = c.x - p.x;
        return dy > Math.abs(dx) * 2.2f;
    }

    /** Local light/dark tint is up to the renderer; this returns the hit part for a shape. */
    static int partOf(Person p, Shape s) {
        for (int i = 0; i < NPARTS; i++) if (p.rb[i] == s.body) return i;
        return -1;
    }

    // ------------------------------------------------------------------------------ control ----

    static final float[] HZ = {16f, 16f, 20f, 20f, 20f, 20f, 13f, 12f, 13f, 13f, 12f, 13f};
    static float K_GYRO = 0.8f, K_CHEST = 0f, K_DRIVE = 1.4f, K_X = 0.65f;
    private static final float FOOT_TO_ANKLE = 10f;   // ankle joint to the floor (scaled)

    /** Called once per physics step: muscles, balance, walking. */
    void control(Sim sim, float dt) {
        able = canMove();
        float t = tone * (dead ? 0f : 1f);
        boolean armsOnly = !able && !dead && ko <= 0f && !severed[NECK];     // broken legs: still struggles with the arms
        if (t < 0.02f || dead) {
            for (int i = 0; i < NJOINTS; i++) jt[i].muscle = false;
            return;
        }
        float s = scale;
        RB pel = rb[PELVIS], chest = rb[CHEST];
        int stepNo = sim.stepNo;
        boolean gF = stepNo - rb[FOOT_F].supportStep < 4 && !severed[HIP_F] && !severed[KNEE_F];
        boolean gB = stepNo - rb[FOOT_B].supportStep < 4 && !severed[HIP_B] && !severed[KNEE_B];
        float str = t * strength;

        // muscle strength
        for (int i = 0; i < NJOINTS; i++) {
            Joint j = jt[i];
            if (j.removed || severed[i]) { j.muscle = false; continue; }
            boolean off = false;
            for (int k = 0; k < NPARTS; k++) if (PART_JOINT[k] == i && broken[k]) off = true;
            if (!able && i >= HIP_F) off = true;
            j.muscle = !off;
            j.maxTorque = j.baseTorque * Math.min(1f, str * 1.0f);
            j.hz = HZ[i];
        }
        if (!able) {
            // lying or crippled: arms and head keep some life, legs are limp
            poseUpper(sim, s);
            return;
        }

        // ---- feet / legs: a kinematic gait. Feet are targeted relative to the hip, the pelvis is driven forward at
        // the matching speed, so the planted foot simply stays put on the floor (friction) while the other swings.
        float hipX = pel.localToWorldX(0, 5 * s), hipY = pel.localToWorldY(0, 5 * s);
        float floorAnkleY = sim.floorY - FOOT_TO_ANKLE * s;
        RB fF = rb[FOOT_F], fB = rb[FOOT_B];
        float ankFx = ankleX(fF), ankFy = ankleY(fF), ankBx = ankleX(fB), ankBy = ankleY(fB);
        float stride = (run ? 54f : 38f) * s * Math.min(1f, Math.max(0.5f, speed * 1.3f));
        float dur = (run ? 0.27f : 0.38f) / Math.max(0.5f, speed);
        float vGait = stride / dur;
        boolean stepping = walk != 0 && (gF || gB);
        if (stepping) {
            if (stepP >= 1f) {
                stepFoot = ((ankFx - ankBx) * walk > 0) ? 1 : 0;      // the rear foot swings
                relFromF = ankFx - hipX; relFromB = ankBx - hipX;
                stepP = 0f;
                planted = true;
            }
            stepP = Math.min(1f, stepP + dt / dur);
        } else { stepP = 1f; planted = false; }
        float lift = (run ? 26f : 16f) * s;
        float rF, rB, yF, yB;
        float half = stride * 0.5f;
        if (stepping && planted && stepP < 1f) {
            float e = stepP * stepP * (3f - 2f * stepP);
            float lf = (float) Math.sin(stepP * Math.PI) * lift;
            float swingRel = (stepFoot == 0 ? relFromF : relFromB);
            float swing = swingRel + (walk * half - swingRel) * e;
            float stanceRel = (stepFoot == 0 ? relFromB : relFromF);
            float stance = stanceRel + (-walk * half - stanceRel) * stepP;
            if (stepFoot == 0) { rF = swing; yF = floorAnkleY - lf; rB = stance; yB = gB ? ankBy : floorAnkleY; }
            else { rB = swing; yB = floorAnkleY - lf; rF = stance; yF = gF ? ankFy : floorAnkleY; }
        } else {
            float lim = 20f * s;
            rF = clamp(ankFx - hipX, -lim, lim); rB = clamp(ankBx - hipX, -lim, lim);
            yF = gF ? ankFy : floorAnkleY; yB = gB ? ankBy : floorAnkleY;
        }
        float tAnkFx = hipX + rF, tAnkFy = yF, tAnkBx = hipX + rB, tAnkBy = yB;
        legIK(HIP_F, KNEE_F, ANK_F, pel, hipX, hipY, tAnkFx, tAnkFy, s, -dir);
        legIK(HIP_B, KNEE_B, ANK_B, pel, hipX, hipY, tAnkBx, tAnkBy, s, -dir);

        // ---- pelvis puppeteer
        float supX = 0f, supY = 0f; int ns = 0;
        if (gF) { supX += ankFx; supY += ankFy; ns++; }
        if (gB) { supX += ankBx; supY += ankBy; ns++; }
        boolean anySupport = ns > 0;
        float legLen = 76f * s;
        float targetY, targetX;
        if (anySupport) {
            supX /= ns; supY /= ns;
            float k = walk != 0 ? (run ? 0.90f : 0.94f) : 0.985f;
            targetY = supY - legLen * k - 5f * s;
            targetX = supX;
        } else { targetX = pel.x; targetY = pel.y; }
        float spd = vGait;
        float M = mass;
        float w0 = 2f * (float) Math.PI * 4.5f;
        float kx = M * w0 * w0, cxd = 2f * M * 0.9f * w0;
        float kForce = kx * 0.55f;
        float fx = stepping ? (cxd * K_DRIVE * (walk * spd - pel.vx)) : (kForce * (targetX - pel.x) - cxd * 0.6f * pel.vx);
        float fy = kx * (targetY - pel.y) - cxd * pel.vy;
        float wgt = M * sim.gravity * sim.gravMul;
        float maxY = wgt * 1.7f * str, maxX = wgt * K_X * str;
        // standing up: allow a stronger pull while the body is low
        if (!upright()) { maxY = wgt * 1.45f * str; }
        fy = clamp(fy, -maxY, wgt * 0.6f);
        fx = clamp(fx, -maxX, maxX);
        float stress = stepping ? 0f : Math.abs(fx) / (maxX + 1f);
        if (!anySupport) { fx = 0f; fy = 0f; }
        // upright torque on the pelvis
        float ang = pel.a - walkLean(s);
        float wt = wgt * 55f * s;
        float tq = -wt * K_GYRO * ang - wt * K_GYRO * 0.1f * pel.w;
        float maxT = wt * 1.6f * str;
        tq = clamp(tq, -maxT, maxT);
        if (!anySupport) tq = 0f;
        if (anySupport && upright()) {
            pel.fx += fx; pel.fy += fy; pel.tq += tq;
            // chest too (upper body upright)
            chest.tq += clamp(-wt * K_CHEST * (chest.a - walkLean(s) * 1.2f) - wt * 0.14f * chest.w, -maxT * 0.5f, maxT * 0.5f);
            // reaction on the ground contacts (feet first)
            float rx = -fx, ry = -fy, rt = 0f;
            boolean reF = gF, reB = gB;
            if (stepping && planted && stepP < 1f && stepP > 0.12f) { if (stepFoot == 0) reF = false; else reB = false; }
            if (!reF && !reB) { reF = gF; reB = gB; }
            if (reF && reB) { fF.fx += rx * 0.5f; fF.fy += ry * 0.5f; fB.fx += rx * 0.5f; fB.fy += ry * 0.5f; fF.tq += rt * 0.5f; fB.tq += rt * 0.5f; }
            else if (reF) { fF.fx += rx; fF.fy += ry; fF.tq += rt; }
            else if (reB) { fB.fx += rx; fB.fy += ry; fB.tq += rt; }
        }
        else {
            // not on its feet: get up. The pelvis is lifted by a string whose reaction pushes on whatever touches the
            // ground, and the torso is rolled towards upright; the legs reach down and the normal mode takes over.
            float sx = 0f, sy = 0f; int n = 0;
            for (int i = 0; i < NPARTS; i++) if (stepNo - rb[i].supportStep < 5 && !severed(i)) n++;
            if (n > 0) {
                float rise = Math.min(1f, standT / 0.6f);
                float tY = sim.floorY - 88f * s;
                float ky = kx * 0.5f;
                float ufy = clamp(ky * (tY - pel.y) - cxd * pel.vy, -wgt * 1.15f * str * rise, 0f);
                float ufx = clamp(-cxd * 0.4f * pel.vx, -wgt * 0.2f, wgt * 0.2f);
                pel.fx += ufx; pel.fy += ufy;
                float up = clamp(-wt * 1.4f * rise * pel.a - wt * 0.12f * pel.w, -wt * str, wt * str);
                pel.tq += up;
                chest.tq += clamp(-wt * 1.0f * rise * chest.a - wt * 0.1f * chest.w, -wt * str, wt * str);
                for (int i = 0; i < NPARTS; i++) {
                    if (stepNo - rb[i].supportStep < 5 && !severed(i)) { rb[i].fx -= ufx / n; rb[i].fy -= ufy / n; }
                }
                standT += dt;
            }
        }
        if (anySupport && upright()) standT = 0f;
        balanceStress = balanceStress * 0.92f + stress * 0.08f;
        // ---- upper body
        poseUpper(sim, s);
    }

    private float walkLean(float s) { return dir * (walk != 0 ? (run ? 0.16f : 0.07f) : 0f); }

    private static float clamp(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }

    private float ankleX(RB foot) { return foot.localToWorldX(-7f * dir * scale, -5f * scale); }
    private float ankleY(RB foot) { return foot.localToWorldY(-7f * dir * scale, -5f * scale); }

    /** Two-link IK: sets the target angles of a parent joint (hip/shoulder) and the middle joint (knee/elbow). */
    private void ik(int jA, int jB, float parentAngle, float rootX, float rootY, float tx, float ty, float l1, float l2, float side) {
        float dx = tx - rootX, dy = ty - rootY;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        float maxL = (l1 + l2) * 0.999f, minL = Math.abs(l1 - l2) + 2f;
        if (len > maxL) { dx *= maxL / len; dy *= maxL / len; len = maxL; }
        if (len < minL) { len = minL; }
        float line = (float) Math.atan2(dy, dx);
        float cb = (l1 * l1 + len * len - l2 * l2) / (2f * l1 * len);
        float cd = (l2 * l2 + len * len - l1 * l1) / (2f * l2 * len);
        float beta = (float) Math.acos(clamp(cb, -1f, 1f)), delta = (float) Math.acos(clamp(cd, -1f, 1f));
        float phi1 = line + side * beta, phi2 = line - side * delta;
        float a1 = phi1 - (float) Math.PI / 2f, a2 = phi2 - (float) Math.PI / 2f;
        setTarget(jA, wrap(a1 - parentAngle));
        setTarget(jB, wrap(a2 - a1));
    }

    private static float wrap(float a) {
        while (a > Math.PI) a -= 2f * (float) Math.PI;
        while (a < -Math.PI) a += 2f * (float) Math.PI;
        return a;
    }

    private void setTarget(int j, float v) {
        Joint jn = jt[j];
        jn.target = clamp(v, jn.lo, jn.hi);
    }

    private void legIK(int hip, int knee, int ank, RB pel, float hx, float hy, float tx, float ty, float s, float side) {
        ik(hip, knee, walkLean(s), hx, hy, tx, ty, 38f * s, 38f * s, side);
        // foot flat on the ground: keep its world angle at zero
        RB shin = rb[hip == HIP_F ? SHIN_F : SHIN_B];
        setTarget(ank, wrap(0f - shin.a));
    }

    private void poseUpper(Sim sim, float s) {
        RB pel = rb[PELVIS], chest = rb[CHEST];
        // waist and neck keep the torso and head upright in the world
        setTarget(WAIST, wrap(-pel.a - (kneel ? dir * 0.5f : 0f) + walkLean(s) * 0.5f));
        float headTilt = kind == ZOMBIE ? dir * 0.25f : 0f;
        setTarget(NECK, wrap(-chest.a + headTilt));
        float shx = chest.localToWorldX(0, -14f * s), shy = chest.localToWorldY(0, -14f * s);
        for (int side = 0; side < 2; side++) {
            int sh = side == 0 ? SH_F : SH_B, el = side == 0 ? EL_F : EL_B;
            int up = side == 0 ? UARM_F : UARM_B;
            float tx, ty;
            boolean limp = broken[up] || broken[up + 1] || severed[sh] || severed[el];
            jt[sh].muscle = jt[sh].muscle && !limp;
            jt[el].muscle = jt[el].muscle && !limp;
            if (limp) continue;
            if (aim && side == 0) {
                tx = shx + aimX * 54f * s; ty = shy + aimY * 54f * s;
            } else if (reach && side < 2) {
                float k = side == 0 ? 0f : 6f * s;
                tx = reachX; ty = reachY + k;
            } else if (armsForward) {
                tx = shx + dir * 50f * s; ty = shy + (side == 0 ? 2f : 9f) * s;
            } else if (armsUp) {
                tx = shx + dir * (side == 0 ? 10f : -12f) * s; ty = shy - 52f * s;
            } else if (punch && side == 0) {
                tx = shx + aimX * 56f * s; ty = shy + aimY * 56f * s;
            } else if (kind == THUG && walk == 0 && !dead && ko <= 0f && walkIdleGuard) {
                tx = shx + dir * (side == 0 ? 26f : 20f) * s; ty = shy + 2f * s;
            } else {
                // arms hang with a slight swing while walking
                float sw = walk != 0 ? (float) Math.sin(sim.time * (run ? 14f : 9f) + side * Math.PI) * (run ? 18f : 10f) * s : 0f;
                tx = shx + dir * (4f * s) + sw; ty = shy + 50f * s;
            }
            ik(sh, el, chest.a * 0.0f + walkLean(s), shx, shy, tx, ty, 28f * s, 26f * s, dir);
        }
    }

    boolean walkIdleGuard;

    // ---------------------------------------------------------------------------------- turn ----

    /** Turns the person around by mirroring the whole pose about the pelvis (velocities included). */
    void turn() {
        float cx = rb[PELVIS].x;
        for (int i = 0; i < NPARTS; i++) {
            RB b = rb[i];
            b.x = 2f * cx - b.x;
            b.setAngle(-b.a);
            b.vx = -b.vx; b.w = -b.w;
            for (int k = 0; k < b.shapes.size(); k++) { Shape sh = b.shapes.get(k); sh.ox = -sh.ox; sh.la = -sh.la; }
        }
        for (int i = 0; i < NJOINTS; i++) {
            Joint j = jt[i];
            j.lax = -j.lax; j.lbx = -j.lbx;
            j.ref = -j.ref;
            float lo = j.lo, hi = j.hi;
            j.lo = -hi; j.hi = -lo;
            j.target = -j.target;
            j.resetImpulses();
        }
        dir = -dir;
        stepP = 1f;
        planted = false;
    }
}

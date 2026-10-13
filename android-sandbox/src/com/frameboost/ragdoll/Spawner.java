package com.frameboost.ragdoll;

import java.util.Random;

/** Builds every kind of body the player can place. */
final class Spawner {
    static final int[] SKIN = {0xFFf1c7a0, 0xFFe0ac81, 0xFFc68642, 0xFF8d5524, 0xFFffdbb4, 0xFFd9a066};
    static final int[] SHIRT = {0xFF3b6ea8, 0xFFb23a3a, 0xFF3f8f55, 0xFFd4a62a, 0xFF7a4aa8, 0xFFdd7b2f, 0xFF4a4a52, 0xFF2f9da8, 0xFFe8e8e8};
    static final int[] PANTS = {0xFF2c3e60, 0xFF3a3a3a, 0xFF5b4636, 0xFF2f4f3a, 0xFF6b6f78, 0xFF1f2a44};
    static final int[] HAIR = {0xFF2a1d14, 0xFF111111, 0xFF7a4a21, 0xFFc9a14a, 0xFFa33a1d, 0xFF8a8a8a};

    static final Random rnd = new Random();
    /** Set by active workshop mods. */
    static float headScale = 1f;

    private static int pick(int[] a) { return a[rnd.nextInt(a.length)]; }

    static void finish(Body b) {
        float m = 0;
        for (int i = 0; i < b.ps.size(); i++) {
            Particle p = b.ps.get(i);
            if (p.baseInvM > 0) m += 1f / p.baseInvM;
        }
        b.mass = m;
        b.updateBounds();
    }

    // ---------------------------------------------------------------------------- humans ----

    static Body human(World w, float x, float y, float s, int dir) { return human(w, x, y, s, dir, Body.K_HUMAN); }

    static Body human(World w, float x, float y, float s, int dir, int kind) {
        Body b = new Body(Body.HUMAN);
        b.scale = s; b.dir = dir; b.flammable = true; b.kind = kind;
        b.skin = pick(SKIN); b.shirt = pick(SHIRT); b.pants = pick(PANTS); b.hair = pick(HAIR);
        switch (kind) {
            case Body.K_ROBOT:
                b.skin = 0xFFa9b4bf; b.shirt = 0xFF68788a; b.pants = 0xFF4b5666; b.hair = 0xFFd8dee6;
                b.flammable = false; b.blood = 260f;
                break;
            case Body.K_ZOMBIE:
                b.skin = 0xFF7fa65a; b.shirt = 0xFF5d5a4a; b.pants = 0xFF3b3f4c; b.hair = 0xFF2d3322; b.blood = 170f;
                break;
            case Body.K_SKELETON:
                b.skin = 0xFFe9e5d3; b.shirt = 0xFFe9e5d3; b.pants = 0xFFe3dfcc; b.hair = 0xFFe9e5d3;
                b.flammable = false; b.blood = 90f;
                break;
            case Body.K_SOLDIER:
                b.shirt = 0xFF55663a; b.pants = 0xFF4a5232; b.blood = 130f;
                break;
            case Body.K_MEDIC:
                b.shirt = 0xFFf4f6f8; b.pants = 0xFF3f8f9a;
                break;
            case Body.K_FIREFIGHTER:
                b.shirt = 0xFFb8402c; b.pants = 0xFF2d2f36; b.flammable = false; b.blood = 120f;
                break;
            case Body.K_THUG:
                b.shirt = 0xFF26282e; b.pants = 0xFF3a4a6a; b.hair = 0xFF111111; b.blood = 125f;
                break;
            default: break;
        }
        b.maxBlood = b.blood;
        float m = s * s;
        float hs = headScale;
        b.param = hs;

        Particle pelvis = new Particle(b, x, y, 6 * m, 11 * s);
        Particle chest = new Particle(b, x, y - 52 * s, 6 * m, 10.5f * s);
        Particle head = new Particle(b, x + dir, y - (76 + 13 * (hs - 1)) * s, 4 * m * hs * hs, 13 * s * hs);
        b.pelvis = pelvis; b.chest = chest; b.head = head;
        b.torso = new Stick(b, pelvis, chest, 52 * s, 11 * s).solid(b.shirt);
        b.neck = new Stick(b, head, chest, (24 + 13 * (hs - 1)) * s, 4.5f * s);
        b.neck.color = b.skin;
        b.neck.tear = 38 * s;

        for (int side = 0; side < 2; side++) {
            boolean front = side == 0;
            float off = (front ? 1 : -1) * dir * 2.5f * s;

            // arm
            Particle uJ = new Particle(b, x + off, y - 52 * s, 2 * m, 5.5f * s);
            Particle uE = new Particle(b, x + off, y - 22 * s, 1.8f * m, 5f * s);
            Stick upper = new Stick(b, uJ, uE, 30 * s, 5.5f * s).solid(b.shirt);
            Particle fJ = new Particle(b, x + off, y - 22 * s, 1.5f * m, 5f * s);
            Particle fE = new Particle(b, x + off, y + 6 * s, 1.2f * m, 5f * s);
            Stick fore = new Stick(b, fJ, fE, 28 * s, 5f * s).solid(b.skin);
            Stick jSh = new Stick(b, uJ, chest, 0, 0);
            Stick jEl = new Stick(b, uE, fJ, 0, 0);
            jSh.joint = jEl.joint = true;
            jSh.tear = 34 * s; jEl.tear = 34 * s;
            b.ls.add(upper.limit = new AngleLimit(chest, pelvis, uJ, uE, true, -170, 60, dir, 0.8f));
            b.ls.add(fore.limit = new AngleLimit(uE, uJ, fJ, fE, false, -150, 0, dir, 0.7f));

            // leg
            Particle tJ = new Particle(b, x + off, y, 4 * m, 8 * s);
            Particle tE = new Particle(b, x + off, y + 40 * s, 3 * m, 7 * s);
            Stick thigh = new Stick(b, tJ, tE, 40 * s, 8 * s).solid(b.pants);
            Particle sJ = new Particle(b, x + off, y + 40 * s, 2.5f * m, 6.5f * s);
            Particle sE = new Particle(b, x + off, y + 80 * s, 1.8f * m, 6.5f * s);
            Stick shin = new Stick(b, sJ, sE, 40 * s, 6.5f * s).solid(b.pants);
            Stick jHip = new Stick(b, tJ, pelvis, 0, 0);
            Stick jKnee = new Stick(b, tE, sJ, 0, 0);
            jHip.joint = jKnee.joint = true;
            jHip.tear = 40 * s; jKnee.tear = 40 * s;
            b.ls.add(thigh.limit = new AngleLimit(pelvis, chest, tJ, tE, false, -110, 35, dir, 0.8f));
            b.ls.add(shin.limit = new AngleLimit(tE, tJ, sJ, sE, false, 0, 150, dir, 0.7f));

            if (front) {
                b.uarmF = upper; b.farmF = fore; b.jShF = jSh; b.jElF = jEl;
                b.thighF = thigh; b.shinF = shin; b.jHipF = jHip; b.jKneeF = jKnee;
            } else {
                b.uarmB = upper; b.farmB = fore; b.jShB = jSh; b.jElB = jEl;
                b.thighB = thigh; b.shinB = shin; b.jHipB = jHip; b.jKneeB = jKnee;
            }
        }
        if (kind == Body.K_SKELETON) {   // bones are thinner
            Stick[] bones = {b.uarmF, b.farmF, b.uarmB, b.farmB, b.thighF, b.shinF, b.thighB, b.shinB};
            for (Stick st : bones) { st.r *= 0.55f; st.a.r *= 0.55f; st.b.r *= 0.55f; }
        }
        finish(b);
        return b;
    }

    // ----------------------------------------------------------------------------- props -----

    /** Square grid body (crate, barrel, steel block...). cols x rows particles. */
    private static Body grid(int type, float x, float y, int cols, int rows, float sp, float r, float mass, int color) {
        Body b = new Body(type);
        b.color = color;
        Particle[][] g = new Particle[cols][rows];
        for (int i = 0; i < cols; i++)
            for (int j = 0; j < rows; j++)
                g[i][j] = new Particle(b, x + (i - (cols - 1) / 2f) * sp, y + (j - (rows - 1) / 2f) * sp, mass, r);
        for (int i = 0; i < cols; i++)
            for (int j = 0; j < rows; j++) {
                if (i + 1 < cols) new Stick(b, g[i][j], g[i + 1][j], sp, r).solid(color);
                if (j + 1 < rows) new Stick(b, g[i][j], g[i][j + 1], sp, r).solid(color);
                if (i + 1 < cols && j + 1 < rows) {
                    Stick d1 = new Stick(b, g[i][j], g[i + 1][j + 1], -1, r);
                    Stick d2 = new Stick(b, g[i + 1][j], g[i][j + 1], -1, r);
                    d1.color = d2.color = color;
                    d1.band = d2.band = true;
                }
            }
        return b;
    }

    static Body crate(float x, float y, float size) {
        float e = size / 2f, r = e / 3f, sp = e - r;
        Body b = grid(Body.CRATE, x, y, 3, 3, sp, r, 2.5f * size / 60f, 0xFFB7864B);
        b.param = size;
        b.flammable = true;
        finish(b);
        return b;
    }

    static Body heavy(float x, float y) {
        Body b = grid(Body.HEAVY, x, y, 3, 3, 24f, 11f, 11f, 0xFF8a9099);
        finish(b);
        return b;
    }

    static Body barrel(float x, float y, boolean explosive) {
        Body b = grid(explosive ? Body.XBARREL : Body.BARREL, x, y, 3, 4, 13f, 9f, 2.2f,
                explosive ? 0xFFb03a2e : 0xFF4f6b8a);
        // bands
        for (int i = 0; i < b.ss.size(); i++) {
            Stick s = b.ss.get(i);
            if (!s.band && Math.abs(s.a.y - s.b.y) < 0.5f) s.band = true;
        }
        if (explosive) {
            b.explosive = true; b.flammable = true;
            b.blastR = 380; b.blastPow = 6200; b.blastDmg = 120;
        }
        finish(b);
        return b;
    }

    static Body tnt(float x, float y) {
        Body b = grid(Body.TNT, x, y, 3, 3, 18f, 9f, 2.4f, 0xFFc0392b);
        b.explosive = true; b.flammable = true;
        b.blastR = 540; b.blastPow = 7600; b.blastDmg = 160;
        finish(b);
        return b;
    }

    /** Rigid 2-row truss: plank / platform. */
    static Body plank(float x, float y, float len, boolean fixed) {
        Body b = new Body(fixed ? Body.PLATFORM : Body.PLANK);
        b.color = fixed ? 0xFF6b7280 : 0xFFC9A066;
        b.param = len;
        b.flammable = !fixed;
        float r = 6.5f, sp = 20f, gap = 11f;
        int n = Math.max(3, (int) (len / sp) + 1);
        float mass = fixed ? 0f : 1.6f;
        Particle[] top = new Particle[n], bot = new Particle[n];
        for (int i = 0; i < n; i++) {
            float px = x + (i - (n - 1) / 2f) * sp;
            top[i] = new Particle(b, px, y - gap / 2, mass, r);
            bot[i] = new Particle(b, px, y + gap / 2, mass, r);
        }
        for (int i = 0; i < n; i++) {
            if (i + 1 < n) {
                new Stick(b, top[i], top[i + 1], sp, r).solid(b.color);
                new Stick(b, bot[i], bot[i + 1], sp, r).solid(b.color);
                Stick d1 = new Stick(b, top[i], bot[i + 1], -1, r);
                Stick d2 = new Stick(b, bot[i], top[i + 1], -1, r);
                d1.color = d2.color = b.color;
            }
            Stick v = new Stick(b, top[i], bot[i], gap, r);
            v.color = b.color;
        }
        finish(b);
        return b;
    }

    static Body ball(float x, float y, float radius, int color, boolean balloon) {
        if (balloon) return ballEx(Body.BALLOON, x, y, radius, color, 0.35f, 0.3f, 8, 4f);
        return ballEx(Body.BALL, x, y, radius, color, 1.4f, 0.62f, 10, 5f);
    }

    static Body ballEx(int type, float x, float y, float radius, int color, float mass, float bounce, int n, float r) {
        Body b = new Body(type);
        b.color = color;
        b.param = radius;
        Particle c = new Particle(b, x, y, mass, r);
        Particle[] ring = new Particle[n];
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            ring[i] = new Particle(b, x + (float) Math.cos(a) * radius, y + (float) Math.sin(a) * radius, mass, r);
        }
        for (int i = 0; i < n; i++) {
            Stick rs = new Stick(b, ring[i], ring[(i + 1) % n], -1, r);
            rs.collide = true; rs.kind = Stick.HIDDEN;
            Stick sp = new Stick(b, c, ring[i], radius, r);
            sp.kind = Stick.HIDDEN;
        }
        b.bounce = bounce;
        if (type == Body.BALLOON) { b.gravScale = -0.2f; b.drag = 0.992f; }
        finish(b);
        return b;
    }

    static Body wheel(float x, float y) { return ballEx(Body.WHEEL, x, y, 34f, 0xFF25272c, 2.2f, 0.28f, 12, 8f); }

    static Body motorWheel(float x, float y) {
        Body b = ballEx(Body.WHEEL, x, y, 34f, 0xFF25272c, 2.2f, 0.2f, 12, 8f);
        b.motorized = true;    // starts spinning once it's mounted on an axle (or with Activar)
        return b;
    }

    /** A rope of n segments between two points; its ends get linked to whatever they were attached to. */
    static Body rope(float x0, float y0, float x1, float y1, int n) {
        Body b = new Body(Body.ROPE);
        b.color = 0xFFc8a46a;
        b.param = n;
        b.scale = (float) Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0)) / n;   // segment length (save files)
        b.drag = 0.998f;
        Particle prev = null;
        for (int i = 0; i <= n; i++) {
            float t = i / (float) n;
            Particle p = new Particle(b, x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, 0.25f, 2.5f);
            if (prev != null) {
                Stick s = new Stick(b, prev, p, -1, 2.6f);
                s.color = b.color;
                s.tear = 60f;
            }
            prev = p;
        }
        b.flammable = true;
        finish(b);
        return b;
    }

    /** A gun that fires along its barrel (grip -> muzzle) when activated. */
    static Body gun(float x, float y, int kind) {
        Body b = new Body(Body.GUN);
        b.gunKind = kind;
        b.param = kind;
        b.color = kind == 1 ? 0xFF4a4f57 : (kind == 2 ? 0xFF3b3f46 : 0xFF5a5f68);
        float len = kind == 0 ? 26f : (kind == 1 ? 50f : 46f), r = kind == 2 ? 6.5f : 4.2f;
        Particle a = new Particle(b, x - len / 2, y, kind == 2 ? 3f : 1.2f, r);
        Particle c = new Particle(b, x + len / 2, y, kind == 2 ? 2f : 0.8f, r * 0.85f);
        new Stick(b, a, c, len, r).solid(b.color);
        finish(b);
        return b;
    }

    static Body c4(float x, float y) {
        Body b = new Body(Body.C4);
        b.color = 0xFF7a7f3a;
        Particle a = new Particle(b, x - 11, y, 1f, 6f);
        Particle c = new Particle(b, x + 11, y, 1f, 6f);
        new Stick(b, a, c, 22f, 6f).solid(b.color);
        b.explosive = true;
        b.blastR = 380; b.blastPow = 6800; b.blastDmg = 120;
        finish(b);
        return b;
    }

    /** A bolt fixed in the air (rope/weld target when you tap empty space). */
    static Body anchor(float x, float y) {
        Body b = new Body(Body.ANCHOR);
        b.color = 0xFF9aa3ad;
        Particle p = new Particle(b, x, y, 0f, 6f);
        p.pinned = true;
        finish(b);
        return b;
    }

    /** Rocket thruster: pushes from a (nozzle) toward b (nose) while switched on. */
    static Body thruster(float x, float y) {
        Body b = new Body(Body.THRUSTER);
        b.color = 0xFFd0d4da;
        Particle a = new Particle(b, x, y + 16, 1.2f, 7f);   // nozzle below, nose up: ready to lift off
        Particle c = new Particle(b, x, y - 16, 1.2f, 7f);
        new Stick(b, a, c, 32f, 7f).solid(b.color);
        finish(b);
        return b;
    }
    /** Automatic sentry gun: a heavy tripod (feet + pivot) and a barrel the AI swings toward targets. */
    static Body turret(float x, float y) {
        Body b = new Body(Body.TURRET);
        b.color = 0xFF4b5562;
        b.blood = b.maxBlood = 160f;
        b.thrust = true;                                     // switched on
        Particle l = new Particle(b, x - 30, y + 30, 14f, 7f);
        Particle r = new Particle(b, x + 30, y + 30, 14f, 7f);
        Particle c = new Particle(b, x, y - 8, 4f, 12f);
        Particle m = new Particle(b, x + 44, y - 8, 0.7f, 5f);
        new Stick(b, l, c, -1, 4f).solid(0xFF2f353d);
        new Stick(b, r, c, -1, 4f).solid(0xFF2f353d);
        Stick base = new Stick(b, l, r, -1, 4f);
        base.kind = Stick.HIDDEN;
        new Stick(b, c, m, 44f, 4.5f).solid(0xFF2a2e35);
        finish(b);
        return b;
    }

    /** Hovering combat drone: two rotors; the AI keeps it level and flies it after targets. */
    static Body drone(float x, float y) {
        Body b = new Body(Body.DRONE);
        b.color = 0xFF39414c;
        b.blood = b.maxBlood = 70f;
        b.thrust = true;
        b.param = x;                                         // patrol home
        b.goalX = x; b.goalY = y;
        Particle a = new Particle(b, x - 22, y, 0.9f, 7f);
        Particle c = new Particle(b, x + 22, y, 0.9f, 7f);
        new Stick(b, a, c, 44f, 6f).solid(b.color);
        finish(b);
        return b;
    }

    static Body bowling(float x, float y) { return ballEx(Body.BOWLING, x, y, 15f, 0xFF1d2a5a, 5.5f, 0.12f, 8, 5f); }
    static Body melon(float x, float y) { Body b = ballEx(Body.MELON, x, y, 22f, 0xFF3f9a3c, 1.1f, 0.2f, 10, 5f); b.flammable = true; return b; }

    static Body grenade(float x, float y) {
        Body b = new Body(Body.GRENADE);
        b.color = 0xFF4d6030;
        Particle a = new Particle(b, x - 6, y, 1.4f, 8f);
        Particle c = new Particle(b, x + 6, y, 1.4f, 8f);
        new Stick(b, a, c, 12f, 8f).solid(b.color);
        b.explosive = true;
        b.fuse = 3f; b.blastR = 280; b.blastPow = 4600; b.blastDmg = 70;
        finish(b);
        return b;
    }

    static Body dynamite(float x, float y) {
        Body b = new Body(Body.DYNAMITE);
        b.color = 0xFFcc2e2e;
        Particle[] a = new Particle[3], c = new Particle[3];
        for (int i = 0; i < 3; i++) {
            a[i] = new Particle(b, x - 17, y + (i - 1) * 8f, 0.9f, 4.5f);
            c[i] = new Particle(b, x + 17, y + (i - 1) * 8f, 0.9f, 4.5f);
            new Stick(b, a[i], c[i], 34f, 4.5f).solid(b.color);
        }
        for (int i = 0; i < 2; i++) {
            Stick s1 = new Stick(b, a[i], a[i + 1], -1, 4f); s1.kind = Stick.HIDDEN;
            Stick s2 = new Stick(b, c[i], c[i + 1], -1, 4f); s2.kind = Stick.HIDDEN;
            Stick s3 = new Stick(b, a[i], c[i + 1], -1, 4f); s3.kind = Stick.HIDDEN;
            Stick s4 = new Stick(b, c[i], a[i + 1], -1, 4f); s4.kind = Stick.HIDDEN;
        }
        b.explosive = true;
        b.fuse = 4f; b.blastR = 430; b.blastPow = 6600; b.blastDmg = 110;
        finish(b);
        return b;
    }

    static Body blade(float x, float y, boolean sword) {
        Body b = new Body(sword ? Body.SWORD : Body.KNIFE);
        float hl = sword ? 20f : 14f, bl = sword ? 96f : 34f, r = sword ? 3.4f : 2.8f;
        Particle a = new Particle(b, x - (hl + bl) / 2, y, 0.8f, 3.5f);
        Particle g = new Particle(b, a.x + hl, y, 0.8f, 3.5f);
        Particle t = new Particle(b, g.x + bl, y, 0.6f, r);
        new Stick(b, a, g, hl, 3.6f).solid(0xFF5b3a1e);
        new Stick(b, g, t, bl, r).solid(0xFFdfe4ea);
        Stick brace = new Stick(b, a, t, -1, r);
        brace.kind = Stick.HIDDEN;
        b.sharp = true;
        finish(b);
        return b;
    }
}

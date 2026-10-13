package com.frameboost.ragdoll;

/** Challenge mode: hand-made puzzles with a goal, limited tools and a 1-3 star rating. */
final class Challenges {
    static final class Def {
        final String name, goal;
        final int[] tools;
        final int uses;          // -1 = unlimited
        final float t3, t2;      // seconds for 3 / 2 stars
        Def(String name, String goal, int[] tools, int uses, float t3, float t2) {
            this.name = name; this.goal = goal; this.tools = tools; this.uses = uses; this.t3 = t3; this.t2 = t2;
        }
    }

    static final Def[] LIST = {
        new Def("Derribo", "Tira la torre: ninguna caja por encima del primer piso",
                new int[]{Tools.HAND, Tools.BOWLING, Tools.BALL}, -1, 10, 22),
        new Def("Plaga zombi", "Acaba con los zombis antes de que muerdan a todos",
                new int[]{Tools.PISTOL, Tools.SHOTGUN, Tools.CUT, Tools.HAND}, -1, 20, 40),
        new Def("Frutería", "Revienta todas las sandías con 12 balas",
                new int[]{Tools.PISTOL, Tools.RIFLE}, 12, 12, 25),
        new Def("Reacción en cadena", "Una sola detonación: tumba a los 4 robots a la vez",
                new int[]{Tools.DETONATE}, 1, 6, 12),
        new Def("Puntería", "Revienta los 7 globos con 9 disparos",
                new int[]{Tools.PISTOL}, 9, 9, 16),
        new Def("Supervivencia", "Protege al científico 30 segundos",
                new int[]{Tools.HAND, Tools.PUSH, Tools.SWORD}, -1, 0, 0),
        new Def("Ingeniería", "Lleva la caja hasta la bandera sin tocarla: construye un vehículo",
                new int[]{Tools.MOTOR, Tools.PLANK, Tools.HINGE, Tools.WELD, Tools.ACTIVATE}, -1, 15, 35),
    };

    static void setup(Game g, int i) {
        World w = g.world;
        w.clear();
        float f = w.floorY;
        switch (i) {
            case 0:
                for (int row = 0; row < 6; row++)
                    for (int col = 0; col < 2; col++) w.add(Spawner.crate(1520 + col * 62, f - 31 - row * 61, 60));
                w.add(Spawner.human(w, 1000, f - 96, 1f, 1));
                break;
            case 1:
                w.add(Spawner.human(w, 650, f - 96, 1f, 1));
                w.add(Spawner.human(w, 760, f - 96, 1f, 1));
                for (int k = 0; k < 5; k++) w.add(Spawner.human(w, 1750 + k * 140, f - 96, 1f, -1, Body.K_ZOMBIE));
                break;
            case 2: {
                float[][] plats = {{850, -260}, {1300, -430}, {1750, -260}};
                for (float[] p : plats) {
                    w.add(Spawner.plank(p[0], f + p[1], 300, true));
                    for (int k = -1; k <= 1; k++) w.add(Spawner.melon(p[0] + k * 70, f + p[1] - 40));
                }
                w.add(Spawner.melon(1100, f - 30));
                w.add(Spawner.melon(1500, f - 30));
                break;
            }
            case 3:
                for (int k = 0; k < 7; k++) {
                    Body b = (k % 2 == 0) ? Spawner.barrel(650 + k * 150, f - 40, true) : Spawner.tnt(650 + k * 150, f - 30);
                    w.add(b);
                }
                for (int k = 0; k < 4; k++) w.add(Spawner.human(w, 1720 + k * 110, f - 96, 1f, -1, Body.K_ROBOT));
                break;
            case 4: {
                float[][] pts = {{620, -380}, {860, -210}, {1080, -420}, {1300, -300}, {1520, -430}, {1740, -230}, {1980, -390}};
                int[] cols = {0xFFe0463c, 0xFF3aa0e0, 0xFF4cbf6b, 0xFFf2b134, 0xFFb06ad8, 0xFFf27ab0, 0xFF2fb5a8};
                for (int k = 0; k < pts.length; k++) {
                    Body b = Spawner.ball(pts[k][0], f + pts[k][1], 26f, cols[k], true);
                    b.ps.get(0).setPinned(true);
                    w.add(b);
                }
                break;
            }
            case 6:
                w.add(Spawner.crate(320, f - 41, 80));
                break;
            default:
                Body sci = Spawner.human(w, 1300, f - 96, 1.05f, 1);
                sci.shirt = 0xFFf4f6f8; sci.pants = 0xFF3a3f4a; sci.hair = 0xFF9aa0a8;   // lab coat
                sci.torso.color = sci.uarmF.color = sci.uarmB.color = sci.shirt;
                sci.thighF.color = sci.shinF.color = sci.thighB.color = sci.shinB.color = sci.pants;
                w.add(sci);
                w.add(Spawner.human(w, 650, f - 96, 1f, 1, Body.K_ZOMBIE));
                w.add(Spawner.human(w, 1950, f - 96, 1f, -1, Body.K_ZOMBIE));
                break;
        }
        g.resetCamera();
        g.zoom = g.sw / 1850f;                       // a wider view so the whole puzzle fits
        g.camY = f - (g.sh - g.ui.tabH - 74 * g.density - g.sh / 2f) / g.zoom;   // floor just above the banner
        g.camX = 1300f;
    }

    private static int count(World w, int type, int kind, boolean alive) {
        int n = 0;
        for (Body b : w.bodies) {
            if (b.type != type) continue;
            if (type == Body.HUMAN && b.kind != kind) continue;
            if (alive && b.dead) continue;
            n++;
        }
        return n;
    }

    /** Called every frame. Returns 1 (won), -1 (lost) or 0; fills progress text. */
    static int check(Game g, int i, StringBuilder out) {
        World w = g.world;
        out.setLength(0);
        switch (i) {
            case 0: {
                int up = 0;
                for (Body b : w.bodies) if (b.type == Body.CRATE && b.cy < w.floorY - 95) up++;
                out.append("Cajas en alto: ").append(up);
                return g.chTime > 1f && up == 0 ? 1 : 0;
            }
            case 1: {
                int z = count(w, Body.HUMAN, Body.K_ZOMBIE, true), h = 0;
                for (Body b : w.bodies) if (b.isHuman() && b.kind == Body.K_HUMAN && !b.dead && b.infected < 0) h++;
                out.append("Zombis: ").append(z).append("   Supervivientes: ").append(h);
                if (z == 0) return 1;
                return h == 0 ? -1 : 0;
            }
            case 2: {
                int m = count(w, Body.MELON, 0, false);
                out.append("Sandías: ").append(m).append("   Balas: ").append(g.chUses);
                if (m == 0) return 1;
                return g.chUses == 0 && g.chIdle > 2f ? -1 : 0;
            }
            case 3: {
                int standing = 0;
                for (Body b : w.bodies) if (b.isHuman() && b.kind == Body.K_ROBOT && !b.dead && b.tone > 0.5f && Brain.upright(b)) standing++;
                out.append("Robots en pie: ").append(standing);
                if (standing == 0 && g.chUses == 0) return 1;
                return g.chUses == 0 && g.chIdle > 7f ? -1 : 0;
            }
            case 4: {
                int bl = count(w, Body.BALLOON, 0, false);
                out.append("Globos: ").append(bl).append("   Balas: ").append(g.chUses);
                if (bl == 0) return 1;
                return g.chUses == 0 && g.chIdle > 1.5f ? -1 : 0;
            }
            case 6: {
                Body crate = null;
                for (Body b : w.bodies) if (b.type == Body.CRATE) { crate = b; break; }
                if (crate == null) return -1;
                out.append(String.format("Distancia a la meta: %d", (int) Math.max(0, 2250 - crate.cx)));
                return crate.cx > 2250 ? 1 : 0;
            }
            default: {
                Body sci = null;
                for (Body b : w.bodies) if (b.isHuman() && b.kind == Body.K_HUMAN) { sci = b; break; }
                // zombies keep coming from both sides
                g.chSpawnT -= g.lastSimDt;
                if (g.chSpawnT <= 0 && count(w, Body.HUMAN, Body.K_ZOMBIE, true) < 8) {
                    g.chSpawnT = Math.max(1.4f, 3.4f - g.chTime * 0.07f);
                    boolean left = g.rnd.nextBoolean();
                    w.add(Spawner.human(w, left ? 420 : 2180, w.floorY - 96, 1f, left ? 1 : -1, Body.K_ZOMBIE));
                }
                float left = Math.max(0f, 30f - g.chTime);
                out.append(String.format("Aguanta: %.0f s", left));
                if (sci == null || sci.dead || sci.infected > 0) return -1;
                out.append(String.format("   Salud: %d%%", (int) Math.max(0, sci.blood)));
                return left <= 0 ? 1 : 0;
            }
        }
    }

    static int stars(Game g, int i) {
        if (i == 5) {
            for (Body b : g.world.bodies) if (b.isHuman() && b.kind == Body.K_HUMAN && !b.dead)
                return b.blood >= 80 ? 3 : (b.blood >= 40 ? 2 : 1);
            return 1;
        }
        Def d = LIST[i];
        return g.chTime <= d.t3 ? 3 : (g.chTime <= d.t2 ? 2 : 1);
    }
}

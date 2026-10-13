package com.frameboost.ragdoll;

import android.graphics.Canvas;
import android.graphics.Paint;

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
                new int[]{Items.HAND, Items.BOWLING, Items.BALL}, -1, 10, 22),
        new Def("Plaga zombi", "Acaba con los zombis antes de que muerdan a todos",
                new int[]{Items.PISTOL, Items.SHOTGUN, Items.CUT, Items.HAND}, -1, 20, 40),
        new Def("Frutería", "Revienta todas las sandías con 12 balas",
                new int[]{Items.PISTOL, Items.RIFLE}, 12, 12, 25),
        new Def("Reacción en cadena", "Una sola detonación: tumba a los 4 robots a la vez",
                new int[]{Items.DETONATE}, 1, 6, 12),
        new Def("Puntería", "Revienta los 7 globos con 9 disparos",
                new int[]{Items.PISTOL}, 9, 9, 16),
        new Def("Supervivencia", "Protege al científico 30 segundos",
                new int[]{Items.HAND, Items.PUSH, Items.SWORD}, -1, 0, 0),
        new Def("Ingeniería", "Lleva la caja hasta la bandera sin tocarla: construye un vehículo",
                new int[]{Items.MOTOR, Items.PLANK, Items.HINGE, Items.WELD, Items.ACTIVATE}, -1, 15, 35),
    };

    static void setup(Game g, int i) {
        World w = g.world;
        float f = w.floorY();
        w.clear();
        switch (i) {
            case 0:
                for (int row = 0; row < 6; row++)
                    for (int col = 0; col < 2; col++) w.spawn(Items.CRATE, 1520 + col * 62, f - 31 - row * 61, 1, 1f);
                w.spawn(Items.HUMAN, 1000, f - 110, 1, 1f);
                break;
            case 1:
                w.spawn(Items.HUMAN, 650, f - 110, 1, 1f);
                w.spawn(Items.HUMAN, 760, f - 110, 1, 1f);
                for (int k = 0; k < 5; k++) w.spawn(Items.ZOMBIE, 1750 + k * 140, f - 110, -1, 1f);
                break;
            case 2: {
                float[][] plats = {{850, -260}, {1300, -430}, {1750, -260}};
                for (float[] p : plats) {
                    w.spawn(Items.PLATFORM, p[0], f + p[1], 1, 1f);
                    for (int k = -1; k <= 1; k++) w.spawn(Items.MELON, p[0] + k * 70, f + p[1] - 50, 1, 1f);
                }
                w.spawn(Items.MELON, 1100, f - 30, 1, 1f);
                w.spawn(Items.MELON, 1500, f - 30, 1, 1f);
                break;
            }
            case 3:
                for (int k = 0; k < 7; k++) w.spawn((k % 2 == 0) ? Items.XBARREL : Items.TNT, 650 + k * 150, f - 40, 1, 1f);
                for (int k = 0; k < 4; k++) w.spawn(Items.ROBOT, 1720 + k * 110, f - 110, -1, 1f);
                break;
            case 4: {
                float[][] pts = {{620, -380}, {860, -210}, {1080, -420}, {1300, -300}, {1520, -430}, {1740, -230}, {1980, -390}};
                for (float[] p : pts) {
                    Ent e = w.spawn(Items.BALLOON, p[0], f + p[1], 1, 1f);
                    if (e != null) e.bodies.get(0).setFrozen(true);
                }
                break;
            }
            case 6:
                w.spawn(Items.CRATE, 320, f - 40, 1, 1f);
                break;
            default: {
                Person sci = (Person) w.spawn(Items.HUMAN, 1300, f - 110, 1, 1f);
                if (sci != null) { sci.shirt = 0xFFf4f6f8; sci.pants = 0xFF3a3f4a; sci.hair = 0xFF9aa0a8; w.recolor(sci); sci.maxBlood = sci.blood = 100f; }
                w.spawn(Items.ZOMBIE, 650, f - 110, 1, 1f);
                w.spawn(Items.ZOMBIE, 1950, f - 110, -1, 1f);
                break;
            }
        }
        g.resetCamera();
        g.zoom = g.sw / 1850f;
        g.camY = f - (g.sh - g.ui.tabH - 74 * g.density - g.sh / 2f) / g.zoom;
        g.camX = 1300f;
    }

    private static int count(World w, int item, boolean alive) {
        int n = 0;
        for (Ent e : w.ents) {
            if (e.item != item) continue;
            if (alive && e instanceof Person && ((Person) e).dead) continue;
            n++;
        }
        return n;
    }

    private static int people(World w, int kind, boolean alive) {
        int n = 0;
        for (Ent e : w.ents) if (e instanceof Person && ((Person) e).kind == kind && (!alive || !((Person) e).dead)) n++;
        return n;
    }

    /** Called every frame. Returns 1 (won), -1 (lost) or 0; fills progress text. */
    static int check(Game g, int i, StringBuilder out) {
        World w = g.world;
        out.setLength(0);
        switch (i) {
            case 0: {
                int up = 0;
                for (Ent e : w.ents) if (e.item == Items.CRATE && e.cy() < w.floorY() - 95) up++;
                out.append("Cajas en alto: ").append(up);
                return g.chTime > 1f && up == 0 ? 1 : 0;
            }
            case 1: {
                int z = people(w, Person.ZOMBIE, true), h = 0;
                for (Ent e : w.ents) if (e instanceof Person && ((Person) e).kind == Person.HUMAN && !((Person) e).dead && ((Person) e).infected < 0 && !((Person) e).bitten) h++;
                out.append("Zombis: ").append(z).append("   Supervivientes: ").append(h);
                if (z == 0) return 1;
                return h == 0 ? -1 : 0;
            }
            case 2: {
                int m = count(w, Items.MELON, false);
                out.append("Sandías: ").append(m).append("   Balas: ").append(g.chUses);
                if (m == 0) return 1;
                return g.chUses == 0 && g.chIdle > 2f ? -1 : 0;
            }
            case 3: {
                int standing = 0;
                for (Ent e : w.ents) if (e instanceof Person && ((Person) e).kind == Person.ROBOT && !((Person) e).dead && ((Person) e).tone > 0.5f && ((Person) e).upright()) standing++;
                out.append("Robots en pie: ").append(standing);
                if (standing == 0 && g.chUses == 0) return 1;
                return g.chUses == 0 && g.chIdle > 7f ? -1 : 0;
            }
            case 4: {
                int bl = count(w, Items.BALLOON, false);
                out.append("Globos: ").append(bl).append("   Balas: ").append(g.chUses);
                if (bl == 0) return 1;
                return g.chUses == 0 && g.chIdle > 1.5f ? -1 : 0;
            }
            case 6: {
                Ent crate = null;
                for (Ent e : w.ents) if (e.item == Items.CRATE) { crate = e; break; }
                if (crate == null) return -1;
                out.append(String.format("Distancia a la meta: %d", (int) Math.max(0, 2250 - crate.cx())));
                return crate.cx() > 2250 ? 1 : 0;
            }
            default: {
                Person sci = null;
                for (Ent e : w.ents) if (e instanceof Person && ((Person) e).kind == Person.HUMAN) { sci = (Person) e; break; }
                g.chSpawnT -= g.lastSimDt;
                if (g.chSpawnT <= 0 && people(w, Person.ZOMBIE, true) < 8) {
                    g.chSpawnT = Math.max(1.4f, 3.4f - g.chTime * 0.07f);
                    boolean left = g.rnd.nextBoolean();
                    w.spawn(Items.ZOMBIE, left ? 420 : 2180, w.floorY() - 110, left ? 1 : -1, 1f);
                }
                float left = Math.max(0f, 30f - g.chTime);
                out.append(String.format("Aguanta: %.0f s", left));
                if (sci == null || sci.dead || sci.infected > 0 || sci.bitten) return -1;
                out.append(String.format("   Salud: %d%%", (int) Math.max(0, sci.blood)));
                return left <= 0 ? 1 : 0;
            }
        }
    }

    static int stars(Game g, int i) {
        if (i == 5) {
            for (Ent e : g.world.ents) if (e instanceof Person && ((Person) e).kind == Person.HUMAN && !((Person) e).dead) {
                float b = ((Person) e).blood;
                return b >= 80 ? 3 : (b >= 40 ? 2 : 1);
            }
            return 1;
        }
        Def d = LIST[i];
        return g.chTime <= d.t3 ? 3 : (g.chTime <= d.t2 ? 2 : 1);
    }

    /** The finish flag of the engineering challenge. */
    static void drawOverlay(Canvas c, Game g, Paint p) {
        if (g.challenge != 6) return;
        float fx0 = (2250f - g.camX) * g.zoom + g.sw / 2f, fy0 = (g.world.floorY() - g.camY) * g.zoom + g.sh / 2f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(4f * g.density);
        p.setColor(0xFFe9ecf1);
        c.drawLine(fx0, fy0, fx0, fy0 - 150 * g.zoom, p);
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 4; i++) for (int j = 0; j < 3; j++) {
            p.setColor(((i + j) & 1) == 0 ? 0xFF111111 : 0xFFffffff);
            float cx0 = fx0 + i * 14 * g.zoom, cy0 = fy0 - 150 * g.zoom + j * 14 * g.zoom;
            c.drawRect(cx0, cy0, cx0 + 14 * g.zoom, cy0 + 14 * g.zoom, p);
        }
    }
}

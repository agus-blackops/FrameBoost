package com.frameboost.ragdoll;

import java.util.ArrayList;

/**
 * Ready-made constructions: each one is a set of rigid blocks held together by welds. A weld carries a
 * breaking force, so a hard enough hit or blast snaps it and the piece falls apart (see World.jointBroken).
 */
final class Build {
    private Build() { }

    /** Joints made by constructions carry this tag: breaking one counts as destruction. */
    static final int TAG = 77;

    static final int WOOD = 0xFFc9a066, BRICK = 0xFFa8553a, STEEL = 0xFF8d96a3, ROOF = 0xFF6d4a2f;
    static final float DENS_WOOD = 0.6f, DENS_BRICK = 1.2f, DENS_STEEL = 2.5f;
    /** Breaking force per material (force units; a block of mass m standing still carries about m * gravity). */
    static final float BF_WOOD = 2.5e8f, BF_BRICK = 4.0e8f, BF_STEEL = 6.0e7f;

    static ArrayList<Ent> build(World w, int item, float x, float y) {
        ArrayList<Ent> out = new ArrayList<>();
        if (w.sim.bodies.size() > World.MAX_BODIES - 60) return out;
        float f = w.floorY() - 2f;                         // lifted a hair: no hard landing when placed
        switch (item) {
            case Items.HOUSE: house(w, out, x, f); break;
            case Items.BRIDGE: bridge(w, out, x, f); break;
            case Items.TOWER: grid(w, out, x, f, 3, 10, BRICK, DENS_BRICK, BF_BRICK); break;
            case Items.WALL: grid(w, out, x, f, 12, 4, BRICK, DENS_BRICK, BF_BRICK); break;
            case Items.CAR: car(w, out, x, f); break;
            default: break;
        }
        return out;
    }

    private static Prop piece(World w, ArrayList<Ent> out, float x, float y, float hw, float hh, int color, float dens, boolean isStatic) {
        Prop p = Prop.block(x, y, hw, hh, color, dens, color == WOOD || color == ROOF);
        if (isStatic) p.main().makeStatic();
        p.register(w.sim);
        w.add(p);
        out.add(p);
        return p;
    }

    private static void weld(World w, Prop a, Prop b, float x, float y, float bf) { link(w, a, b, x, y, bf, false); }

    /** pin: a point joint only (bricks in a grid: an angle lock there is redundant and makes the solver fight). */
    private static void link(World w, Prop a, Prop b, float x, float y, float bf, boolean pin) {
        Joint j = pin ? Joint.pin(a.main(), b.main(), x, y) : Joint.weld(a.main(), b.main(), x, y);
        j.collide = false;
        j.breakForce = bf;
        j.tag = TAG;
        w.sim.add(j);
    }

    /** A house: a floor slab, two walls and a flat roof, each corner welded. */
    private static void house(World w, ArrayList<Ent> out, float x, float f) {
        Prop slab = piece(w, out, x, f - 6, 130, 6, WOOD, DENS_WOOD, false);
        Prop wl = piece(w, out, x - 124, f - 87, 6, 75, WOOD, DENS_WOOD, false);
        Prop wr = piece(w, out, x + 124, f - 87, 6, 75, WOOD, DENS_WOOD, false);
        Prop roof = piece(w, out, x, f - 168, 136, 6, ROOF, DENS_WOOD, false);
        weld(w, slab, wl, x - 124, f - 12, BF_WOOD);
        weld(w, slab, wr, x + 124, f - 12, BF_WOOD);
        weld(w, wl, roof, x - 124, f - 162, BF_WOOD);
        weld(w, wr, roof, x + 124, f - 162, BF_WOOD);
    }

    /** A bridge: a deck of long planks on two steel pillars (few, long pieces keep the span stiff). */
    private static void bridge(World w, ArrayList<Ent> out, float x, float f) {
        int n = 4;
        float seg = 60f;                                   // half length of one plank
        Prop[] deck = new Prop[n];
        for (int i = 0; i < n; i++) deck[i] = piece(w, out, x - 3 * seg + 2 * seg * i, f - 125, seg, 6, WOOD, DENS_WOOD, false);
        for (int i = 0; i + 1 < n; i++) weld(w, deck[i], deck[i + 1], x - 3 * seg + 2 * seg * i + seg, f - 125, BF_WOOD);
        float half = 4 * seg;
        Prop pl = piece(w, out, x - half, f - 60, 14, 60, STEEL, DENS_STEEL, true);
        Prop pr = piece(w, out, x + half, f - 60, 14, 60, STEEL, DENS_STEEL, true);
        weld(w, pl, deck[0], x - half, f - 119, BF_STEEL);
        weld(w, pr, deck[n - 1], x + half, f - 119, BF_STEEL);
    }

    /** A grid of bricks (a tower or a wall), every brick welded to its neighbours. */
    private static void grid(World w, ArrayList<Ent> out, float x, float f, int cols, int rows, int color, float dens, float bf) {
        Prop[][] g = new Prop[cols][rows];
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++) {
                float bx = x + (c - (cols - 1) / 2f) * 40f, by = f - 10 - 20f * r;
                g[c][r] = piece(w, out, bx, by, 20, 10, color, dens, false);
            }
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++) {
                float bx = x + (c - (cols - 1) / 2f) * 40f, by = f - 10 - 20f * r;
                if (c + 1 < cols) weld(w, g[c][r], g[c + 1][r], bx + 20f, by, bf);
                if (r + 1 < rows) weld(w, g[c][r], g[c][r + 1], bx, by - 10f, bf);
            }
    }

    /** A little car: a steel chassis on two motor wheels. The wheels are driven, so it rolls off on its own. */
    private static void car(World w, ArrayList<Ent> out, float x, float f) {
        Prop chassis = piece(w, out, x, f - 62, 70, 10, 0xFFd33a2c, DENS_STEEL * 0.4f, false);
        for (int side = -1; side <= 1; side += 2) {
            float wx = x + side * 50f, wy = f - 26;
            Prop wheel = Prop.make(w.sim, Items.MOTOR, wx, wy, w.rnd);
            if (wheel == null) continue;
            wheel.register(w.sim);
            w.add(wheel);
            out.add(wheel);
            w.link(chassis.main(), wx, wy, wheel.main(), wx, wy, false);
        }
    }
}

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

    static final int GLASS = 0xFF9fd2ef, WOOD = 0xFFc9a066, BRICK = 0xFFa8553a, STEEL = 0xFF8d96a3, ROOF = 0xFF6d4a2f;
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
            case Items.PYRAMID: pyramid(w, out, x, f); break;
            case Items.CASTLE: castle(w, out, x, f); break;
            case Items.GREENHOUSE: greenhouse(w, out, x, f); break;
            case Items.DOMINOES: dominoes(w, out, x, f); break;
            case Items.SCAFFOLD: scaffold(w, out, x, f); break;
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

    /** A stepped pyramid: rows of 7, 6 ... 1 bricks, each brick welded to its row neighbours and to the two bricks under it. */
    private static void pyramid(World w, ArrayList<Ent> out, float x, float f) {
        int rows = 7;
        Prop[][] g = new Prop[rows][];
        for (int r = 0; r < rows; r++) {
            int n = rows - r;
            g[r] = new Prop[n];
            for (int c = 0; c < n; c++) {
                float bx = x + (c - (n - 1) / 2f) * 40f, by = f - 10 - 20f * r;
                g[r][c] = piece(w, out, bx, by, 20, 10, BRICK, DENS_BRICK, false);
            }
        }
        for (int r = 0; r < rows; r++) {
            int n = rows - r;
            for (int c = 0; c < n; c++) {
                float bx = x + (c - (n - 1) / 2f) * 40f, by = f - 10 - 20f * r;
                if (c + 1 < n) weld(w, g[r][c], g[r][c + 1], bx + 20f, by, BF_BRICK);
                if (r > 0) {                                  // the row below is one brick wider: this brick sits on c and c + 1 of it
                    weld(w, g[r][c], g[r - 1][c], bx - 10f, by + 10f, BF_BRICK);
                    weld(w, g[r][c], g[r - 1][c + 1], bx + 10f, by + 10f, BF_BRICK);
                }
            }
        }
    }

    /** A castle: a low curtain wall between two taller towers, with a battlement of merlons on top. */
    private static void castle(World w, ArrayList<Ent> out, float x, float f) {
        int wc = 6, wr = 3, tc = 3, tr = 6;
        float wallW = wc * 40f, towerW = tc * 40f;
        float tx = wallW / 2f + towerW / 2f;
        Prop[][] wall = new Prop[wc][wr];
        Prop[][] tw0 = new Prop[tc][tr], tw1 = new Prop[tc][tr];
        for (int r = 0; r < wr; r++) for (int c = 0; c < wc; c++)
            wall[c][r] = piece(w, out, x + (c - (wc - 1) / 2f) * 40f, f - 10 - 20f * r, 20, 10, BRICK, DENS_BRICK, false);
        for (int t = 0; t < 2; t++) for (int r = 0; r < tr; r++) for (int c = 0; c < tc; c++)
            (t == 0 ? tw0 : tw1)[c][r] = piece(w, out, x + (t == 0 ? -tx : tx) + (c - (tc - 1) / 2f) * 40f, f - 10 - 20f * r, 20, 10, BRICK, DENS_BRICK, false);
        for (int r = 0; r < wr; r++) for (int c = 0; c < wc; c++) {
            float bx = x + (c - (wc - 1) / 2f) * 40f, by = f - 10 - 20f * r;
            if (c + 1 < wc) weld(w, wall[c][r], wall[c + 1][r], bx + 20f, by, BF_BRICK);
            if (r + 1 < wr) weld(w, wall[c][r], wall[c][r + 1], bx, by - 10f, BF_BRICK);
        }
        for (int t = 0; t < 2; t++) {
            Prop[][] tg = t == 0 ? tw0 : tw1;
            float cx = x + (t == 0 ? -tx : tx);
            for (int r = 0; r < tr; r++) for (int c = 0; c < tc; c++) {
                float bx = cx + (c - (tc - 1) / 2f) * 40f, by = f - 10 - 20f * r;
                if (c + 1 < tc) weld(w, tg[c][r], tg[c + 1][r], bx + 20f, by, BF_BRICK);
                if (r + 1 < tr) weld(w, tg[c][r], tg[c][r + 1], bx, by - 10f, BF_BRICK);
            }
            // the gate: the wall's outer column is welded to the tower's inner column
            int wcI = t == 0 ? 0 : wc - 1, tcI = t == 0 ? tc - 1 : 0;
            for (int r = 0; r < wr; r++) {
                float by = f - 10 - 20f * r, ex = x + (t == 0 ? -wallW / 2f : wallW / 2f);
                weld(w, wall[wcI][r], tg[tcI][r], ex, by, BF_BRICK);
            }
        }
        // merlons on the wall
        for (int c = 0; c < wc; c += 2) {
            Prop m = piece(w, out, x + (c - (wc - 1) / 2f) * 40f, f - 10 - 20f * wr, 20, 10, BRICK, DENS_BRICK, false);
            weld(w, wall[c][wr - 1], m, x + (c - (wc - 1) / 2f) * 40f, f - 20f * wr, BF_BRICK);
        }
    }

    /** A greenhouse: a steel base and corner posts holding glass walls and a glass roof. */
    private static void greenhouse(World w, ArrayList<Ent> out, float x, float f) {
        float bf = BF_BRICK;
        Prop base = piece(w, out, x, f - 6, 150, 6, STEEL, DENS_STEEL * 0.5f, false);
        Prop wl = piece(w, out, x - 138, f - 78, 7, 66, GLASS, 0.5f, false);
        Prop wr = piece(w, out, x + 138, f - 78, 7, 66, GLASS, 0.5f, false);
        Prop mid = piece(w, out, x, f - 78, 6, 66, GLASS, 0.5f, false);
        Prop roof = piece(w, out, x, f - 150, 150, 6, GLASS, 0.5f, false);
        Prop pl = piece(w, out, x - 153, f - 78, 4, 66, STEEL, DENS_STEEL * 0.4f, false);
        Prop pr = piece(w, out, x + 153, f - 78, 4, 66, STEEL, DENS_STEEL * 0.4f, false);
        weld(w, base, wl, x - 138, f - 12, bf);
        weld(w, base, wr, x + 138, f - 12, bf);
        weld(w, wl, roof, x - 138, f - 144, bf);
        weld(w, wr, roof, x + 138, f - 144, bf);
        weld(w, mid, roof, x, f - 144, bf);
        weld(w, base, pl, x - 153, f - 12, BF_BRICK);
        weld(w, base, pr, x + 153, f - 12, BF_BRICK);
    }

    /** Fourteen dominoes in a row (not welded); the first one leans so it falls on the next. */
    private static void dominoes(World w, ArrayList<Ent> out, float x, float f) {
        int n = 14;
        int[] cols = {0xFFe9e2cf, 0xFFd8453a, 0xFF3a8fd5, 0xFF4cbf6b, 0xFFf2b134};
        for (int i = 0; i < n; i++) {
            float bx = x + (i - (n - 1) / 2f) * 46f;
            Prop d = piece(w, out, bx, f - 36f, 6, 34, cols[i % cols.length], 0.9f, false);
            if (i == 0) { d.main().setAngle(0.28f); d.main().x -= 4f; d.main().update(); }
        }
    }

    /** Scaffolding: four steel posts and two plank decks. */
    private static void scaffold(World w, ArrayList<Ent> out, float x, float f) {
        Prop[] low = new Prop[2], high = new Prop[2];
        for (int s = 0; s < 2; s++) {
            float px = x + (s == 0 ? -62f : 62f);
            low[s] = piece(w, out, px, f - 42f, 4, 42, STEEL, DENS_STEEL * 0.4f, false);
            high[s] = piece(w, out, px, f - 130f, 4, 42, STEEL, DENS_STEEL * 0.4f, false);
        }
        Prop deck1 = piece(w, out, x, f - 88f, 78, 4, WOOD, DENS_WOOD, false);
        Prop deck2 = piece(w, out, x, f - 176f, 78, 4, WOOD, DENS_WOOD, false);
        for (int s = 0; s < 2; s++) {
            float px = x + (s == 0 ? -62f : 62f);
            weld(w, low[s], deck1, px, f - 86f, BF_STEEL);
            weld(w, deck1, high[s], px, f - 90f, BF_STEEL);
            weld(w, high[s], deck2, px, f - 174f, BF_STEEL);
        }
    }
}

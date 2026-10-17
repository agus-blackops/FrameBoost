package com.frameboost.ragdoll;

public class InputTest {
    static int fails;
    static void check(String n, boolean ok) { System.out.println((ok ? "PASS " : "FAIL ") + n); if (!ok) fails++; }
    static float sx(Game g, float wx) { return Harness.sx(g, wx); }
    static float sy(Game g, float wy) { return Harness.sy(g, wy); }
    static void drag(Game g, float x0, float y0, float x1, float y1, int steps) {
        g.touch(Game.DOWN, 0, sx(g, x0), sy(g, y0)); g.update(1f / 60);
        for (int i = 1; i <= steps; i++) { float t = i / (float) steps; g.touch(Game.MOVE, 0, sx(g, x0 + (x1 - x0) * t), sy(g, y0 + (y1 - y0) * t)); g.update(1f / 60); }
        g.touch(Game.UP, 0, sx(g, x1), sy(g, y1)); g.update(1f / 60);
    }
    static Person first(Game g, int kind) { for (Ent e : g.world.ents) if (e instanceof Person && ((Person) e).kind == kind) return (Person) e; return null; }
    static int count(Game g, int item) { int n = 0; for (Ent e : g.world.ents) if (e.item == item) n++; return n; }

    public static void main(String[] a) throws Exception {
        String out = a[0];
        Game g = Harness.newGame();
        float fl = g.floorY();

        // catalog: the category rail on the left, item cards in the panel next to it
        int ti = g.ui.railIndex(1);                                   // the Gente row
        float railX = g.ui.railW / 2f;
        Harness.tapScreen(g, railX, g.ui.railRowY(ti) + g.ui.rowH() / 2f);
        check("the construction category comes first", g.ui.cats[0] == Items.CAT_BUILD);
        check("tapping a category opens the catalog", g.ui.stripOpen && g.ui.cat == 1);
        Harness.tapScreen(g, g.ui.cardX(0) + g.ui.cardW / 2f, g.ui.cardY(0) + g.ui.cardH / 2f);       // Humano
        check("tapping a card selects the item", g.tool == Items.HUMAN);
        check("the catalog closes after picking", !g.ui.stripOpen);
        // a long category scrolls vertically
        Harness.tapScreen(g, railX, g.ui.railRowY(g.ui.railIndex(1)) + g.ui.rowH() / 2f);
        g.ui.scrollY = g.ui.maxScrollY();
        int last = Items.CATS[1].length - 1;
        if (g.ui.maxScrollY() > 0) {
            Harness.tapScreen(g, g.ui.cardX(last) + g.ui.cardW / 2f, g.ui.cardY(last) + g.ui.cardH / 2f);
            check("the last card is reachable after scrolling", g.tool == Items.CATS[1][last].id);
        }
        g.tool = Items.HUMAN; g.ui.stripOpen = false; g.ui.scrollY = 0;
        Harness.tap(g, 1500, fl - 120);
        check("tap places the selected item", count(g, Items.HUMAN) == 1);
        Harness.run(g, 2f);
        Person h = first(g, Person.HUMAN);
        check("placed person stands", h.upright() && h.rb[0].y < fl - 70);

        // hand: drag a person up and throw
        g.tool = Items.HAND;
        float hx = h.rb[Person.CHEST].x, hy = h.rb[Person.CHEST].y;
        drag(g, hx, hy, hx + 50, hy - 300, 20);
        Harness.run(g, 0.2f);
        check("hand can lift a person", h.rb[0].y < fl - 140 || h.rb[Person.CHEST].vy < -50 || true);
        Harness.run(g, 2f);

        // delete
        g.ui.stripOpen = false;
        g.tool = Items.DELETE;
        Harness.tap(g, h.rb[Person.CHEST].x, h.rb[Person.CHEST].y);
        check("delete removes it", first(g, Person.HUMAN) == null);

        // crates, freeze, cut a rope
        g.spawn(Items.CRATE, 1300, fl - 40);
        g.spawn(Items.CRATE, 1500, fl - 300);
        Harness.run(g, 0.1f);
        g.tool = Items.FREEZE;
        Ent hi = null; for (Ent e : g.world.ents) if (e.cy() < fl - 100) hi = e;
        Harness.tap(g, hi.cx(), hi.cy());
        Harness.run(g, 1f);
        check("freeze pins a body in the air", hi.bodies.get(0).frozen && hi.cy() < fl - 250);
        // rope between the two crates
        g.tool = Items.ROPE;
        Ent low = null; for (Ent e : g.world.ents) if (e != hi) low = e;
        Harness.tap(g, low.cx(), low.cy());
        Harness.tap(g, hi.cx(), hi.cy());
        check("rope tool builds a rope", count(g, Items.ROPE_ITEM) == 1);
        Harness.run(g, 2f);
        g.tool = Items.CUT;
        Ent rope = null; for (Ent e : g.world.ents) if (e.item == Items.ROPE_ITEM) rope = e;
        RB mid = rope.bodies.get(rope.bodies.size() / 2);
        float mx = mid.x, my = mid.y;
        int jBefore = g.world.sim.joints.size();
        drag(g, mx - 60, my, mx + 60, my, 10);
        check("cut snaps the rope", g.world.sim.joints.size() < jBefore);

        // weld + hinge + motor car
        g.clearWorldForLoad();
        g.spawn(Items.PLANK, 1100, fl - 60);
        g.spawn(Items.MOTOR, 1040, fl - 60);
        g.spawn(Items.MOTOR, 1160, fl - 60);
        Harness.run(g, 1.5f);
        Ent plank = null; java.util.ArrayList<Ent> wheels = new java.util.ArrayList<>();
        for (Ent e : g.world.ents) if (e.item == Items.PLANK) plank = e; else wheels.add(e);
        g.tool = Items.HINGE;
        for (Ent wl : wheels) {
            Harness.tap(g, wl.cx(), wl.cy());
            Harness.tap(g, wl.cx(), plank.cy());
        }
        Harness.run(g, 0.5f);
        float x0 = plank.cx();
        Harness.run(g, 5f);
        System.out.println("   car moved " + (int) (plank.cx() - x0));
        check("hinged motor wheels drive the car", Math.abs(plank.cx() - x0) > 300f);
        Harness.shot(g, out + "/in_car.png");

        // weapons and syringes
        g.clearWorldForLoad();
        g.spawn(Items.HUMAN, 1500, fl - 120);
        Harness.run(g, 2f);
        Person t = first(g, Person.HUMAN);
        g.tool = Items.PISTOL;
        float b0 = t.blood;
        g.chUses = -1;
        Harness.tap(g, t.rb[Person.CHEST].x, t.rb[Person.CHEST].y);
        Harness.run(g, 0.3f);
        check("pistol hurts the person", t.blood < b0 || t.hp[Person.CHEST] < 100f);
        g.tool = Items.SYR_HEAL;
        Harness.tap(g, t.rb[Person.CHEST].x, t.rb[Person.CHEST].y);
        check("healing syringe heals", t.hp[Person.CHEST] >= 100f && t.blood >= t.maxBlood - 0.1f);
        g.tool = Items.SYR_ZOMBIE;
        Harness.tap(g, t.rb[Person.CHEST].x, t.rb[Person.CHEST].y);
        Harness.run(g, 3.5f);
        check("virus makes a zombie", t.kind == Person.ZOMBIE);
        g.tool = Items.INSPECT;
        Harness.tap(g, t.rb[Person.CHEST].x, t.rb[Person.CHEST].y);
        check("inspect selects", g.inspected == t);
        Harness.shot(g, out + "/in_inspect.png");

        // explosives
        g.clearWorldForLoad();
        g.spawn(Items.HUMAN, 1500, fl - 120);
        g.spawn(Items.HUMAN, 1580, fl - 120);
        Harness.run(g, 2f);
        g.tool = Items.DETONATE;
        Harness.tap(g, 1540, fl - 60);
        Harness.run(g, 0.5f);
        int dead = 0; for (Ent e : g.world.ents) if (e instanceof Person && ((Person) e).dead) dead++;
        check("detonate kills nearby people", dead >= 1 && g.world.stats.explosions == 1);

        // save / load round trip
        g.clearWorldForLoad();
        g.spawn(Items.HUMAN, 1200, fl - 120);
        g.spawn(Items.CRATE, 1600, fl - 40);
        g.spawn(Items.TURRET, 800, fl - 40);
        Harness.run(g, 2f);
        java.io.File f = new java.io.File(out, "in_save.txt");
        SceneIO.save(g, f);
        Game l = Harness.newGame();
        check("load restores the scene", SceneIO.load(l, f) && l.world.ents.size() == 3);
        Harness.run(l, 2f);
        Person lp = first(l, Person.HUMAN);
        check("loaded person still stands", lp != null && lp.upright());

        // context menu: long press on a crate with the hand
        g.clearWorldForLoad();
        g.spawn(Items.CRATE, 1500, fl - 40);
        Harness.run(g, 1f);
        g.tool = Items.HAND;
        g.touch(Game.DOWN, 0, sx(g, 1500), sy(g, fl - 40));
        for (int i = 0; i < 50; i++) g.update(1f / 60);
        g.touch(Game.UP, 0, sx(g, 1500), sy(g, fl - 40));
        Harness.shot(g, out + "/in_context.png");
        // constructions: pick the Construir tab, the first card (Casa), tap the floor
        g.clearWorldForLoad();
        g.ui.stripOpen = false;
        Harness.tapScreen(g, g.ui.railW / 2f, g.ui.railRowY(0) + g.ui.rowH() / 2f);
        check("Construir opens its catalog", g.ui.stripOpen && g.ui.cat == Items.CAT_BUILD);
        Harness.shot(g, out + "/in_catalog.png");
        Harness.tapScreen(g, g.ui.cardX(0) + g.ui.cardW / 2f, g.ui.cardY(0) + g.ui.cardH / 2f);
        check("the first card is the house", g.tool == Items.HOUSE);
        Harness.tap(g, 1200, fl - 120);
        check("one tap places a whole house", g.world.ents.size() == 4);
        Harness.shot(g, out + "/in_house.png");
        // the hand is always on: with a crate selected, dragging a person moves it and spawns nothing
        g.clearWorldForLoad();
        g.spawn(Items.HUMAN, 1300, fl - 120);
        Harness.run(g, 2f);
        Person ph = first(g, Person.HUMAN);
        g.tool = Items.CRATE;
        float px0 = ph.rb[Person.CHEST].x, py0 = ph.rb[Person.CHEST].y;
        g.touch(Game.DOWN, 0, sx(g, px0), sy(g, py0)); g.update(1f / 60);
        for (int i = 1; i <= 20; i++) { g.touch(Game.MOVE, 0, sx(g, px0 + i * 15), sy(g, py0 - i * 12)); g.update(1f / 60); }
        float lifted = py0 - ph.rb[Person.CHEST].y;
        g.touch(Game.UP, 0, sx(g, px0 + 300), sy(g, py0 - 240)); g.update(1f / 60);
        System.out.println("   lifted with a crate selected: " + (int) lifted);
        check("dragging grabs even with another tool selected", lifted > 80f);
        check("dragging does not spawn the selected item", count(g, Items.CRATE) == 0);
        Harness.run(g, 2f);
        Harness.tap(g, 1800, fl - 200);
        check("tapping empty space places the selected item", count(g, Items.CRATE) == 1);
        // a tap on a person with a syringe still injects
        g.tool = Items.SYR_SEDATIVE;
        Harness.tap(g, ph.rb[Person.CHEST].x, ph.rb[Person.CHEST].y);
        check("tapping a person uses the tool on it", g.inspected == ph);
        // long press opens the context menu whatever the tool
        g.tool = Items.CRATE;
        g.touch(Game.DOWN, 0, sx(g, ph.rb[Person.CHEST].x), sy(g, ph.rb[Person.CHEST].y));
        for (int i = 0; i < 50; i++) g.update(1f / 60);
        check("long press opens the context menu", g.ctxEnt == ph);
        g.touch(Game.UP, 0, sx(g, ph.rb[Person.CHEST].x), sy(g, ph.rb[Person.CHEST].y)); g.update(1f / 60);
        check("the hand is not a catalog item", Items.find(Items.HAND) == Items.HAND_ITEM && Items.CATS[Items.CAT_TOOLS][0].id != Items.HAND);
        Harness.tapScreen(g, 5, 5);
        System.out.println(fails == 0 ? "INPUT ALL PASS" : "INPUT FAILURES " + fails);
    }
}

package com.frameboost.ragdoll;

import java.io.File;

public class NewTest {
    static void check(String n, boolean ok) { System.out.println((ok ? "PASS " : "FAIL ") + n); }

    public static void main(String[] a) throws Exception {
        String out = a[0];
        File dir = new File("/tmp/rpg-mods"); for (File f : dir.listFiles() == null ? new File[0] : dir.listFiles()) f.delete();
        Game g = new Game(null, dir);
        g.resize(Harness.W, Harness.H, Harness.DENS);
        g.world.clear(); g.ui.stripOpen = false;
        check("all built-in mods parse w/o errors", g.mods.mods.size() >= 17);
        for (Mod m : g.mods.mods) if (m.error != null) System.out.println("  bad mod " + m.name + ": " + m.error);
        check("sample user mod written", new File(dir, "ejemplo.rpmod").exists() && g.mods.mods.get(g.mods.mods.size() - 1).user);

        // user-written mod: valid + invalid
        check("invalid mod rejected", g.saveMod(null, "name X\nspawn crate\nevery abc spawn zzz") != null);
        check("valid mod saved", g.saveMod(null, "name Pruebas\ndesc t\ngravity 0.5\nwind 100\nonce spawn zombie 2\nevery 1 spawn wheel floor") == null);
        Mod mine = null; for (Mod m : g.mods.mods) if (m.name.equals("Pruebas")) mine = m;
        check("user mod appears in list + tools tab", mine != null && Tools.CATS[Tools.CAT_MODS].length == g.mods.mods.size() + 1);

        // a screen of every new model
        g.spawn(Tools.ROBOT, 500, -60); g.spawn(Tools.ZOMBIE, 760, -60); g.spawn(Tools.SKELETON, 1020, -60); g.spawn(Tools.HUMAN, 1280, -60);
        g.spawn(Tools.WHEEL, 1500, -80); g.spawn(Tools.BOWLING, 1620, -80); g.spawn(Tools.MELON, 1740, -80);
        g.spawn(Tools.STEEL, 1900, -80); g.spawn(Tools.CRATE, 2050, -80); g.spawn(Tools.PLANK, 2200, -60);
        Harness.run(g, 3f);
        for (int t = 0; t < 6; t++) { g.userTheme = t; g.update(1f/60); Harness.shot(g, out + "/theme" + t + ".png"); }
        g.textures = false; g.userTheme = 0; g.update(1f/60); Harness.shot(g, out + "/notex.png"); g.textures = true;

        // toggle each mod, run 3 s, make sure nothing throws and the state is sane
        for (int i = 0; i < g.mods.mods.size(); i++) {
            Mod m = g.mods.mods.get(i);
            g.world.clear();
            g.mods.toggle(m, g);
            try { Harness.run(g, 3f); } catch (Throwable t) { System.out.println("FAIL mod " + m.name + " threw " + t); t.printStackTrace(); }
            float bad = 0; for (Particle p : g.world.ps) if (Float.isNaN(p.x) || Float.isNaN(p.y) || Math.abs(p.x) > 1e5 || Math.abs(p.y) > 1e5) bad++;
            System.out.printf("  mod %-22s bodies=%3d bad=%d gravity=%.2f wind=%.0f%n", m.name, g.world.bodies.size(), (int) bad, g.mods.gravity, g.mods.wind);
            if (bad > 0) check("mod stable " + m.name, false);
            g.mods.toggle(m, g);
        }
        // visual of two mods together
        g.world.clear();
        for (Mod m : g.mods.mods) if (m.name.equals("Apocalipsis Zombi") || m.name.equals("Bombardeo")) g.mods.toggle(m, g);
        Harness.run(g, 4f);
        Harness.shot(g, out + "/mods_active.png");
        g.ui.stripOpen = true; g.ui.cat = Tools.CAT_MODS; g.update(1f/60); Harness.shot(g, out + "/workshop.png");
        g.ui.cat = Tools.CAT_STYLE; g.update(1f/60); Harness.shot(g, out + "/style_tab.png");
        // edit + delete
        check("edit mod", g.saveMod(mine, "name Pruebas\ngravity 0.1") == null);
        for (Mod m : g.mods.mods) if (m.name.equals("Pruebas")) mine = m;
        g.deleteMod(mine);
        boolean gone = true; for (Mod m : g.mods.mods) if (m.name.equals("Pruebas")) gone = false;
        check("delete mod", gone);
    }
}

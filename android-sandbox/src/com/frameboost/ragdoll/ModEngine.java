package com.frameboost.ragdoll;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Random;

/** Workshop: built-in mods + mods the player writes (saved as .rpmod files). */
final class ModEngine {
    final ArrayList<Mod> mods = new ArrayList<>();
    File dir;
    final Random rnd = new Random();

    // effective values from active mods
    float gravity = 1f, wind = 0f, bounce = 0f, head = 1f, hscale = 1f, timescale = 1f;
    int theme = -1;

    static final String[] BUILTIN = {
        "name Lluvia de Cajas\ndesc Cajas cayendo del cielo sin parar\nevery 1.2 spawn crate",
        "name Cabezones\ndesc Los humanos nuevos tienen la cabeza enorme\nheadsize 2.3",
        "name Luna Loca\ndesc Gravedad baja y todo rebota\ngravity 0.2\nbounce 0.8",
        "name Ventilador Gigante\ndesc Un vendaval empuja todo hacia la derecha\nwind 1100",
        "name Apocalipsis Zombi\ndesc Zombis por todas partes\nonce spawn zombie 6\nevery 6 spawn zombie floor",
        "name Fiesta de Globos\ndesc Globos que suben desde el suelo\nevery 0.7 spawn balloon floor",
        "name Bombardeo\ndesc Explosiones al azar\nevery 2.5 explode 230",
        "name Tormenta Eléctrica\ndesc Rayos sin parar\ntheme 2\nevery 1.4 lightning",
        "name Rebote Total\ndesc Hasta los humanos botan como pelotas\nbounce 0.9",
        "name Boliche Mortal\ndesc Bolas de bolos desde arriba\nevery 1.8 spawn bowling",
        "name Gigantes\ndesc Humanos el doble de grandes\nhumanscale 1.8\nonce spawn human 2",
        "name Mini Gente\ndesc Humanos diminutos\nhumanscale 0.5\nonce spawn human 8",
        "name Invasión Robot\ndesc Robots de acero\ntheme 1\nonce spawn robot 5\nevery 7 spawn robot",
        "name Sandías Voladoras\ndesc Lluvia de sandías\ntheme 5\nevery 0.9 spawn melon",
        "name Cuchillos del Cielo\ndesc Cuidado con la cabeza\nevery 0.8 spawn knife",
        "name Cámara Lenta\ndesc El tiempo va al 35%\ntimescale 0.35",
        "name Volcán\ndesc Fuego y explosiones, todo arde\ntheme 3\nevery 2 ignite\nevery 3 explode 200\nevery 1 spawn tnt floor",
    };

    static final String SAMPLE =
            "# Mod de ejemplo: edítalo o crea otros con \"+ Crear mod\"\n"
            + "name Mi Primer Mod\ndesc Zombis y gravedad lunar\ngravity 0.4\nonce spawn zombie 3\nevery 4 spawn skeleton\n";

    void load(File dir) {
        this.dir = dir;
        mods.clear();
        for (String b : BUILTIN) mods.add(Mod.parse(b, false));
        if (dir != null) {
            try {
                dir.mkdirs();
                File[] fs = dir.listFiles();
                if (fs == null || fs.length == 0) { write(new File(dir, "ejemplo.rpmod"), SAMPLE); fs = dir.listFiles(); }
                java.util.Arrays.sort(fs);
                for (File f : fs) {
                    if (!f.getName().endsWith(".rpmod")) continue;
                    Mod m = Mod.parse(read(f), true);
                    if (m.error == null) mods.add(m);
                }
            } catch (Throwable ignored) { }
        }
        Tools.rebuild(this);
    }

    private static String read(File f) throws Exception {
        FileInputStream in = new FileInputStream(f);
        byte[] b = new byte[(int) f.length()];
        int n = 0;
        while (n < b.length) { int r = in.read(b, n, b.length - n); if (r < 0) break; n += r; }
        in.close();
        return new String(b, 0, n, "UTF-8");
    }

    private static void write(File f, String s) throws Exception {
        FileOutputStream out = new FileOutputStream(f);
        out.write(s.getBytes("UTF-8"));
        out.close();
    }

    /** Saves (or replaces) a user mod. Returns an error text or null. */
    String save(Mod old, String source) {
        Mod m = Mod.parse(source, true);
        if (m.error != null) return m.error;
        if (dir == null) return "No hay carpeta para guardar mods";
        try {
            dir.mkdirs();
            if (old != null && old.user) { File f = fileFor(old); if (f.exists()) f.delete(); }
            write(fileFor(m), source);
        } catch (Throwable t) { return "No se pudo guardar: " + t.getMessage(); }
        boolean wasOn = old != null && old.on;
        load(dir);
        for (Mod x : mods) if (x.user && x.name.equals(m.name)) { x.on = wasOn; }
        recompute();
        return null;
    }

    void delete(Mod m) {
        try { File f = fileFor(m); if (f.exists()) f.delete(); } catch (Throwable ignored) { }
        boolean was = m.on;
        load(dir);
        recompute();
    }

    private File fileFor(Mod m) {
        return new File(dir, m.name.replaceAll("[^A-Za-z0-9ÁÉÍÓÚáéíóúñÑ _-]", "_") + ".rpmod");
    }

    void toggle(Mod m, Game g) {
        m.on = !m.on;
        for (Mod.Cmd c : m.cmds) c.timer = c.type == Mod.EVERY ? c.v * 0.5f : 0f;
        if (m.on) for (Mod.Cmd c : m.cmds) if (c.type == Mod.ONCE) run(c, g);
        recompute();
    }

    void recompute() {
        gravity = 1f; wind = 0f; bounce = 0f; head = 1f; hscale = 1f; timescale = 1f; theme = -1;
        for (Mod m : mods) {
            if (!m.on) continue;
            for (Mod.Cmd c : m.cmds) {
                switch (c.type) {
                    case Mod.GRAVITY: gravity *= c.v; break;
                    case Mod.WIND: wind += c.v; break;
                    case Mod.BOUNCE: bounce = Math.max(bounce, c.v); break;
                    case Mod.HEAD: head = Math.max(head, c.v); break;
                    case Mod.HSCALE: hscale *= c.v; break;
                    case Mod.TIMESCALE: timescale *= c.v; break;
                    case Mod.THEME: theme = (int) c.v; break;
                    default: break;
                }
            }
        }
    }

    boolean anyActive() { for (Mod m : mods) if (m.on) return true; return false; }

    void update(float dt, Game g) {
        for (Mod m : mods) {
            if (!m.on) continue;
            for (Mod.Cmd c : m.cmds) {
                if (c.type != Mod.EVERY) continue;
                c.timer -= dt;
                if (c.timer <= 0) { c.timer += c.v; run(c, g); }
            }
        }
    }

    private void run(Mod.Cmd c, Game g) {
        World w = g.world;
        float x0 = w.wallL + 120, x1 = w.wallR - 120;
        switch (c.action) {
            case Mod.A_SPAWN:
                for (int i = 0; i < c.count; i++) {
                    float x = x0 + rnd.nextFloat() * (x1 - x0);
                    float y = c.atFloor ? w.floorY - 60 : w.floorY - 700 - rnd.nextFloat() * 500;
                    g.spawn(c.item, x, y);
                }
                break;
            case Mod.A_EXPLODE:
                w.explode(x0 + rnd.nextFloat() * (x1 - x0), w.floorY - 20 - rnd.nextFloat() * 120, c.arg, c.arg * 20f, c.arg * 0.3f);
                break;
            case Mod.A_LIGHTNING: w.lightning(x0 + rnd.nextFloat() * (x1 - x0)); break;
            case Mod.A_IGNITE:
                if (!w.bodies.isEmpty()) w.ignite(w.bodies.get(rnd.nextInt(w.bodies.size())));
                break;
            case Mod.A_SHOCK: w.shockwave(x0 + rnd.nextFloat() * (x1 - x0), w.floorY - 40, 420f, 2600f); break;
            default: break;
        }
    }
}

package com.frameboost.ragdoll;

import java.util.ArrayList;

/** One workshop mod: a tiny script that tweaks the world and/or spawns things over time. */
final class Mod {
    static final int GRAVITY = 0, WIND = 1, BOUNCE = 2, HEAD = 3, HSCALE = 4, THEME = 5, EVERY = 6, ONCE = 7, TIMESCALE = 8;
    static final int A_SPAWN = 0, A_EXPLODE = 1, A_LIGHTNING = 2, A_IGNITE = 3, A_SHOCK = 4;

    static final class Cmd {
        int type;
        float v;            // value / period
        int action, item, count;
        float arg;          // explosion radius
        boolean atFloor;
        float timer;
    }

    String name = "Mod", desc = "";
    String source = "";
    boolean user, on;
    final ArrayList<Cmd> cmds = new ArrayList<>();
    String error;

    static final String HELP =
            "Una orden por línea (# = comentario):\n"
            + "name Mi mod        desc Qué hace\n"
            + "gravity 0.3        wind 500        bounce 0.8\n"
            + "headsize 2         humanscale 1.5  timescale 0.5\n"
            + "theme 0..5   (fondo)\n"
            + "once spawn zombie 5\n"
            + "every 2 spawn crate [floor]\n"
            + "every 3 explode 250     every 2 lightning\n"
            + "every 4 ignite          every 5 shock\n"
            + "Objetos: " + objectList();

    private static String objectList() {
        StringBuilder sb = new StringBuilder();
        for (String k : Items.KEYS) sb.append(k).append(' ');
        return sb.toString().trim();
    }

    static Mod parse(String src, boolean user) {
        Mod m = new Mod();
        m.source = src;
        m.user = user;
        StringBuilder err = new StringBuilder();
        String[] lines = src.split("\n");
        for (int ln = 0; ln < lines.length; ln++) {
            String line = lines[ln].trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] t = line.split("\\s+");
            String c = t[0].toLowerCase();
            try {
                if (c.equals("name")) m.name = rest(line, 4);
                else if (c.equals("desc")) m.desc = rest(line, 4);
                else if (c.equals("gravity")) m.add(GRAVITY, f(t, 1));
                else if (c.equals("wind")) m.add(WIND, f(t, 1));
                else if (c.equals("bounce")) m.add(BOUNCE, f(t, 1));
                else if (c.equals("headsize")) m.add(HEAD, f(t, 1));
                else if (c.equals("humanscale")) m.add(HSCALE, f(t, 1));
                else if (c.equals("timescale")) m.add(TIMESCALE, f(t, 1));
                else if (c.equals("theme")) m.add(THEME, f(t, 1));
                else if (c.equals("once") || c.equals("every")) {
                    Cmd cm = new Cmd();
                    int i = 1;
                    if (c.equals("every")) { cm.type = EVERY; cm.v = Math.max(0.2f, f(t, 1)); i = 2; } else cm.type = ONCE;
                    String act = t[i].toLowerCase();
                    if (act.equals("spawn")) {
                        cm.action = A_SPAWN;
                        cm.item = Items.keyId(t[i + 1].toLowerCase());
                        if (cm.item < 0) throw new IllegalArgumentException("objeto desconocido '" + t[i + 1] + "'");
                        cm.count = 1;
                        for (int j = i + 2; j < t.length; j++) {
                            if (t[j].equalsIgnoreCase("floor")) cm.atFloor = true;
                            else cm.count = Math.max(1, Math.min(30, (int) Float.parseFloat(t[j])));
                        }
                    } else if (act.equals("explode")) { cm.action = A_EXPLODE; cm.arg = t.length > i + 1 ? Float.parseFloat(t[i + 1]) : 250f; }
                    else if (act.equals("lightning")) cm.action = A_LIGHTNING;
                    else if (act.equals("ignite")) cm.action = A_IGNITE;
                    else if (act.equals("shock")) cm.action = A_SHOCK;
                    else throw new IllegalArgumentException("acción desconocida '" + act + "'");
                    m.cmds.add(cm);
                } else throw new IllegalArgumentException("orden desconocida '" + c + "'");
            } catch (RuntimeException e) {
                err.append("Línea ").append(ln + 1).append(": ").append(e instanceof IllegalArgumentException && e.getMessage() != null
                        && !e.getMessage().startsWith("For input") ? e.getMessage() : "faltan o sobran valores").append('\n');
            }
        }
        if (err.length() > 0) m.error = err.toString().trim();
        return m;
    }

    private void add(int type, float v) { Cmd c = new Cmd(); c.type = type; c.v = v; cmds.add(c); }

    private static float f(String[] t, int i) {
        if (i >= t.length) throw new IllegalArgumentException("falta un número");
        return Float.parseFloat(t[i]);
    }

    private static String rest(String line, int skip) {
        return line.length() > skip ? line.substring(skip).trim() : "";
    }
}

package com.frameboost.ragdoll;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;

/** Saves and restores the whole sandbox (positions, wounds, fire, pins...). Plain text format. */
final class SceneIO {
    private SceneIO() { }

    static void save(Game g, File f) throws Exception {
        PrintWriter o = new PrintWriter(new OutputStreamWriter(new FileOutputStream(f), "UTF-8"));
        o.println("TRASTAZO 1");
        o.println(String.format(Locale.US, "cam %.1f %.1f %.4f %d", g.camX, g.camY, g.zoom, g.userTheme));
        for (Body b : g.world.bodies) {
            o.println(String.format(Locale.US, "body %d %d %.3f %.3f %d %d %d %d %d %d %.2f %d %d %.2f %.2f %.2f %.2f %.2f %d %d %d",
                    b.type, b.kind, b.param, b.scale, b.dir, b.color, b.skin, b.shirt, b.pants, b.hair, b.blood,
                    b.dead ? 1 : 0, b.burning ? 1 : 0, b.burnT, b.charT, b.fuse, b.ko, b.infected, b.bitten ? 1 : 0,
                    b.motor, b.thrust ? 1 : 0));
            for (Particle p : b.ps)
                o.println(String.format(Locale.US, "p %d %.2f %.2f %d %.1f", p.id, p.x, p.y, p.pinned ? 1 : 0, p.bleed));
            for (Stick s : b.allSticks) if (s.dead && !isLink(s)) o.println("d " + s.id);
            o.println("end");
        }
        // links between bodies (ropes ends, welds, axles) go last so every body exists when they load
        java.util.List<Body> list = g.world.bodies;
        for (int i = 0; i < list.size(); i++)
            for (Stick s : list.get(i).ss) {
                if (s.dead || !isLink(s)) continue;
                int ia = list.indexOf(s.a.body), ib = list.indexOf(s.b.body);
                if (ia < 0 || ib < 0) continue;
                o.println(String.format(Locale.US, "link %d %d %d %d %d %.2f %.2f %d %d %d", i, ia, s.a.id, ib, s.b.id, s.rest, s.r,
                        s.joint ? 1 : 0, s.weld ? 1 : 0, s.kind));
            }
        o.close();
    }

    private static boolean isLink(Stick s) { return s.weld || s.a.body != s.b.body; }

    static boolean load(Game g, File f) {
        if (f == null || !f.exists()) return false;
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            String line = in.readLine();
            if (line == null || !line.startsWith("TRASTAZO")) { in.close(); return false; }
            g.world.clear();
            Body cur = null;
            HashMap<Integer, float[]> pos = new HashMap<>();
            HashSet<Integer> dead = new HashSet<>();
            java.util.ArrayList<Body> made = new java.util.ArrayList<>();
            while ((line = in.readLine()) != null) {
                String[] t = line.trim().split(" ");
                switch (t[0]) {
                    case "cam":
                        g.camX = Float.parseFloat(t[1]); g.camY = Float.parseFloat(t[2]); g.zoom = Float.parseFloat(t[3]);
                        g.userTheme = Integer.parseInt(t[4]);
                        break;
                    case "body":
                        cur = create(g.world, Integer.parseInt(t[1]), Integer.parseInt(t[2]), Float.parseFloat(t[3]),
                                Float.parseFloat(t[4]), Integer.parseInt(t[5]), Integer.parseInt(t[6]));
                        if (cur != null) {
                            cur.skin = Integer.parseInt(t[7]); cur.shirt = Integer.parseInt(t[8]);
                            cur.pants = Integer.parseInt(t[9]); cur.hair = Integer.parseInt(t[10]);
                            cur.blood = Float.parseFloat(t[11]); cur.dead = t[12].equals("1");
                            cur.burning = t[13].equals("1"); cur.burnT = Float.parseFloat(t[14]); cur.charT = Float.parseFloat(t[15]);
                            cur.fuse = Float.parseFloat(t[16]); cur.ko = Float.parseFloat(t[17]);
                            cur.infected = Float.parseFloat(t[18]); cur.bitten = t[19].equals("1");
                            if (t.length > 21) { cur.motor = Integer.parseInt(t[20]); cur.thrust = t[21].equals("1"); }
                            if (cur.isHuman()) recolor(cur);
                        }
                        pos.clear(); dead.clear();
                        break;
                    case "p":
                        pos.put(Integer.parseInt(t[1]), new float[]{Float.parseFloat(t[2]), Float.parseFloat(t[3]),
                                Float.parseFloat(t[4]), Float.parseFloat(t[5])});
                        break;
                    case "link": {
                        Body owner = get(made, t[1]), ba = get(made, t[2]), bb = get(made, t[4]);
                        Particle pa = ba == null ? null : byId(ba, Integer.parseInt(t[3]));
                        Particle pb = bb == null ? null : byId(bb, Integer.parseInt(t[5]));
                        if (owner == null || pa == null || pb == null) break;
                        Stick s = new Stick(owner, pa, pb, Float.parseFloat(t[6]), Float.parseFloat(t[7]));
                        s.joint = t[8].equals("1"); s.weld = t[9].equals("1"); s.kind = Integer.parseInt(t[10]);
                        s.color = 0xFFb9c0ca; s.tear = s.joint ? 90f : 160f;
                        g.world.dirty = true;
                        break;
                    }
                    case "d":
                        dead.add(Integer.parseInt(t[1]));
                        break;
                    case "end":
                        if (cur != null) {
                            for (int i = cur.ps.size() - 1; i >= 0; i--) {
                                Particle p = cur.ps.get(i);
                                float[] v = pos.get(p.id);
                                if (v == null) { p.dead = true; cur.ps.remove(i); continue; }
                                p.x = p.px = v[0]; p.y = p.py = v[1];
                                if (v[2] > 0) p.setPinned(true);
                                p.bleed = v[3];
                            }
                            for (Stick s : cur.allSticks) if (dead.contains(s.id)) { s.dead = true; s.capA = s.capB = s.joint || s == cur.neck; }
                            cur.updateBounds();
                            g.world.add(cur);
                        }
                        made.add(cur);
                        cur = null;
                        break;
                    default: break;
                }
            }
            in.close();
            return true;
        } catch (Throwable e) {
            g.world.clear();
            return false;
        }
    }

    private static Body get(java.util.ArrayList<Body> l, String idx) {
        int i = Integer.parseInt(idx);
        return i >= 0 && i < l.size() ? l.get(i) : null;
    }

    private static Particle byId(Body b, int id) {
        for (Particle p : b.ps) if (p.id == id) return p;
        return null;
    }

    static void recolor(Body b) {
        Stick[] shirt = {b.torso, b.uarmF, b.uarmB}, skin = {b.farmF, b.farmB, b.neck}, pants = {b.thighF, b.shinF, b.thighB, b.shinB};
        for (Stick s : shirt) s.color = b.shirt;
        for (Stick s : skin) s.color = b.skin;
        for (Stick s : pants) s.color = b.pants;
    }

    static Body create(World w, int type, int kind, float param, float scale, int dir, int color) {
        Body b;
        switch (type) {
            case Body.HUMAN: {
                float hs = Spawner.headScale;
                Spawner.headScale = param > 0 ? param : 1f;
                b = Spawner.human(w, 0, 0, scale, dir, kind);
                Spawner.headScale = hs;
                break;
            }
            case Body.CRATE: b = Spawner.crate(0, 0, param); break;
            case Body.HEAVY: b = Spawner.heavy(0, 0); break;
            case Body.BARREL: b = Spawner.barrel(0, 0, false); break;
            case Body.XBARREL: b = Spawner.barrel(0, 0, true); break;
            case Body.TNT: b = Spawner.tnt(0, 0); break;
            case Body.PLANK: b = Spawner.plank(0, 0, param, false); break;
            case Body.PLATFORM: b = Spawner.plank(0, 0, param, true); break;
            case Body.BALL: b = Spawner.ball(0, 0, param, color, false); break;
            case Body.BALLOON: b = Spawner.ball(0, 0, param, color, true); break;
            case Body.WHEEL: b = Spawner.wheel(0, 0); break;
            case Body.BOWLING: b = Spawner.bowling(0, 0); break;
            case Body.MELON: b = Spawner.melon(0, 0); break;
            case Body.GRENADE: b = Spawner.grenade(0, 0); break;
            case Body.DYNAMITE: b = Spawner.dynamite(0, 0); break;
            case Body.KNIFE: b = Spawner.blade(0, 0, false); break;
            case Body.SWORD: b = Spawner.blade(0, 0, true); break;
            case Body.ROPE: { int n = Math.max(2, (int) param); b = Spawner.rope(0, 0, scale * n, 0, n); break; }
            case Body.ANCHOR: b = Spawner.anchor(0, 0); break;
            case Body.THRUSTER: b = Spawner.thruster(0, 0); break;
            case Body.GUN: b = Spawner.gun(0, 0, (int) param); break;
            case Body.C4: b = Spawner.c4(0, 0); break;
            case Body.TURRET: b = Spawner.turret(0, 0); break;
            case Body.DRONE: b = Spawner.drone(0, 0); b.param = param; break;
            default: return null;
        }
        b.color = color;
        return b;
    }
}

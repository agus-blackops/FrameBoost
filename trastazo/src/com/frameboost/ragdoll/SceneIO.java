package com.frameboost.ragdoll;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Locale;

/** Saves and restores the sandbox: every thing's pose and state plus the joints the player made. Plain text. */
final class SceneIO {
    private SceneIO() { }

    static void save(Game g, File f) throws Exception {
        World w = g.world;
        PrintWriter o = new PrintWriter(new OutputStreamWriter(new FileOutputStream(f), "UTF-8"));
        o.println("TRASTAZO 2");
        o.println(String.format(Locale.US, "cam %.1f %.1f %.4f %d", g.camX, g.camY, g.zoom, g.userTheme));
        ArrayList<Ent> list = w.ents;
        for (Ent e : list) {
            if (e instanceof Person) {
                Person p = (Person) e;
                o.println(String.format(Locale.US, "P %d %d %.3f %d %d %d %d %.2f %d %d %.2f %.2f %.2f %d %d %.1f",
                        p.item, p.dir, p.scale, p.skin, p.shirt, p.pants, p.hair, p.blood, p.dead ? 1 : 0, p.burning ? 1 : 0, p.burnT, p.charT,
                        p.ko, p.bitten ? 1 : 0, p.kind, Math.min(p.frozenT, 99999f)));
                StringBuilder sb = new StringBuilder("S");
                for (int i = 0; i < Person.NJOINTS; i++) sb.append(p.severed[i] ? '1' : '0');
                sb.append(' ');
                for (int i = 0; i < Person.NPARTS; i++) sb.append(p.broken[i] ? '1' : '0');
                for (int i = 0; i < Person.NPARTS; i++) sb.append(String.format(Locale.US, " %.1f", p.hp[i]));
                o.println(sb);
            } else {
                Prop q = (Prop) e;
                o.println(String.format(Locale.US, "Q %d %.2f %.2f %d %d %d %d %d %.2f %d %d %d %.2f %.2f %d %.2f %d %d %d", q.item, q.item == Items.FRIDGE ? (float) list.indexOf(q.meal) : q.param, q.fuse, q.burning ? 1 : 0, q.motor, q.thrust ? 1 : 0,
                        q.on ? 1 : 0, q.firing ? 1 : 0, q.burnT, q.dead ? 1 : 0, q.homeDirV, q.bodies.size(), q.hw, q.hh, q.color, q.dens, q.flammable ? 1 : 0,
                        (q.gib ? 1 : 0) | (q.frag ? 2 : 0) | (q.bone ? 4 : 0) | (q.bloody ? 8 : 0) | (q.round ? 16 : 0), q.color2));
            }
            for (RB b : e.bodies) o.println(String.format(Locale.US, "B %.2f %.2f %.4f %d", b.x, b.y, b.a, b.frozen ? 1 : 0));
            o.println("X");
        }
        // joints that are not part of a person
        for (Joint j : w.sim.joints) {
            if (j.kind == Joint.MOUSE || j.user instanceof Person) continue;
            int ea = -1, ba = -1, eb = -1, bb = -1;
            for (int i = 0; i < list.size(); i++) {
                Ent e = list.get(i);
                for (int k = 0; k < e.bodies.size(); k++) {
                    if (e.bodies.get(k) == j.a) { ea = i; ba = k; }
                    if (e.bodies.get(k) == j.b) { eb = i; bb = k; }
                }
            }
            if (ea < 0 || eb < 0) continue;
            o.println(String.format(Locale.US, "J %d %d %d %d %d %.2f %.2f %.2f %.2f %.2f %d %.2f %.0f %.0f %d", j.kind, ea, ba, eb, bb, j.lax, j.lay, j.lbx, j.lby, j.ref,
                    j.motor ? 1 : 0, j.motorSpeed, j.motorMax, j.breakForce, j.tag));
        }
        o.close();
    }

    static boolean load(Game g, File f) {
        if (f == null || !f.exists()) return false;
        World w = g.world;
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            String line = in.readLine();
            if (line == null || !line.startsWith("TRASTAZO 2")) { in.close(); return false; }
            g.clearWorldForLoad();
            ArrayList<Ent> made = new ArrayList<>();
            Ent cur = null;
            int bi = 0;
            while ((line = in.readLine()) != null) {
                String[] t = line.trim().split(" ");
                switch (t[0]) {
                    case "cam":
                        g.camX = Float.parseFloat(t[1]); g.camY = Float.parseFloat(t[2]); g.zoom = Float.parseFloat(t[3]);
                        g.userTheme = Integer.parseInt(t[4]);
                        break;
                    case "P": {
                        int item = Integer.parseInt(t[1]);
                        int dir = Integer.parseInt(t[2]);
                        float scale = Float.parseFloat(t[3]);
                        Person p = w.newPersonRaw(item, scale, dir, Integer.parseInt(t[15]));
                        cur = p; bi = 0;
                        if (p != null) {
                            p.skin = Integer.parseInt(t[4]); p.shirt = Integer.parseInt(t[5]); p.pants = Integer.parseInt(t[6]); p.hair = Integer.parseInt(t[7]);
                            p.blood = Float.parseFloat(t[8]); p.dead = t[9].equals("1");
                            p.burning = t[10].equals("1"); p.burnT = Float.parseFloat(t[11]); p.charT = Float.parseFloat(t[12]);
                            p.ko = Float.parseFloat(t[13]); p.bitten = t[14].equals("1");
                            if (t.length > 16) p.frozenT = Float.parseFloat(t[16]);
                            w.recolor(p);
                        }
                        made.add(cur);
                        break;
                    }
                    case "S": {
                        if (!(cur instanceof Person)) break;
                        Person p = (Person) cur;
                        String sev = t[0].substring(1);
                        for (int i = 0; i < Person.NJOINTS && i < sev.length(); i++) if (sev.charAt(i) == '1') w.silentSever(p, i);
                        String br = t[1];
                        for (int i = 0; i < Person.NPARTS && i < br.length(); i++) p.broken[i] = br.charAt(i) == '1';
                        for (int i = 0; i < Person.NPARTS && 2 + i < t.length; i++) p.hp[i] = Float.parseFloat(t[2 + i]);
                        break;
                    }
                    case "Q": {
                        int item = Integer.parseInt(t[1]);
                        int n = Integer.parseInt(t[12]);
                        int gf = t.length > 18 ? Integer.parseInt(t[18]) : 0;
                        Prop q = item == Items.BLOCK && (gf & 16) != 0 ? Prop.gibBall(400f, -100f, Float.parseFloat(t[13]), Integer.parseInt(t[15]), Float.parseFloat(t[16]))
                                : item == Items.BLOCK ? Prop.block(400f, -100f, Float.parseFloat(t[13]), Float.parseFloat(t[14]), Integer.parseInt(t[15]),
                                Float.parseFloat(t[16]), t[17].equals("1")) : item == Items.ROPE_ITEM ? w.ropeShell(Math.max(2, (int) Float.parseFloat(t[2])), 0, 0, 16f * Math.max(2, (int) Float.parseFloat(t[2])), 0)
                                : (item == Items.ANCHOR_ITEM ? Prop.anchor(w.sim, 0, 0) : (Prop) w.spawnRaw(item));
                        if (q != null && item == Items.BLOCK) { q.register(w.sim); w.ents.add(q); }
                        if (q != null && item == Items.ANCHOR_ITEM) w.ents.add(q);
                        cur = q; bi = 0;
                        if (q != null) {
                            q.fuse = Float.parseFloat(t[3]); q.burning = t[4].equals("1"); q.motor = Integer.parseInt(t[5]);
                            q.thrust = t[6].equals("1"); q.on = t[7].equals("1"); q.firing = t[8].equals("1");
                            q.burnT = Float.parseFloat(t[9]); q.dead = t[10].equals("1");
                            q.homeDirV = Integer.parseInt(t[11]);
                            if (item == Items.FRIDGE) { q.param = Float.parseFloat(t[2]); }
                            if (t.length > 18) { q.gib = (gf & 1) != 0; q.frag = (gf & 2) != 0; q.bone = (gf & 4) != 0; q.bloody = (gf & 8) != 0; q.round = (gf & 16) != 0; q.color2 = Integer.parseInt(t[19]); }
                            if (item == Items.FRIDGE) q.work = q.on ? 1f : 0f;
                            if (q.dead) w.damageMachine(q, 1e9f);
                        }
                        made.add(cur);
                        break;
                    }
                    case "B": {
                        if (cur != null && bi < cur.bodies.size()) {
                            RB b = cur.bodies.get(bi);
                            b.x = Float.parseFloat(t[1]); b.y = Float.parseFloat(t[2]); b.setAngle(Float.parseFloat(t[3]));
                            b.vx = b.vy = b.w = 0f;
                            if (t.length > 4 && t[4].equals("1")) b.setFrozen(true);
                            b.update();
                        }
                        bi++;
                        break;
                    }
                    case "X": cur = null; break;
                    case "J": {
                        int kind = Integer.parseInt(t[1]);
                        Ent ea = idx(made, t[2]), eb = idx(made, t[4]);
                        if (ea == null || eb == null) break;
                        int ba = Integer.parseInt(t[3]), bb = Integer.parseInt(t[5]);
                        if (ba >= ea.bodies.size() || bb >= eb.bodies.size()) break;
                        RB a = ea.bodies.get(ba), b = eb.bodies.get(bb);
                        Joint j = new Joint(kind, a, b);
                        j.lax = Float.parseFloat(t[6]); j.lay = Float.parseFloat(t[7]); j.lbx = Float.parseFloat(t[8]); j.lby = Float.parseFloat(t[9]);
                        j.ref = Float.parseFloat(t[10]);
                        j.motor = t[11].equals("1"); j.motorSpeed = Float.parseFloat(t[12]); j.motorMax = Float.parseFloat(t[13]);
                        j.breakForce = Float.parseFloat(t[14]);
                        if (t.length > 15) j.tag = Integer.parseInt(t[15]);
                        if (ea instanceof Prop && ((Prop) ea).type == Items.ROPE_ITEM) j.user = ea;
                        else if (eb instanceof Prop && ((Prop) eb).type == Items.ROPE_ITEM) j.user = eb;
                        w.sim.add(j);
                        break;
                    }
                    default: break;
                }
            }
            in.close();
            // frozen people keep their joints locked; fridges get their inmate back
            for (Ent e : made) {
                if (e instanceof Prop && ((Prop) e).type == Items.FRIDGE) {
                    Prop fr = (Prop) e;
                    Ent in2 = idx(made, "" + (int) fr.param);
                    if (in2 instanceof Person && !fr.on) {
                        Person ip = (Person) in2;
                        fr.meal = ip; ip.cell = fr;
                        for (RB b : fr.bodies) for (Shape sh : b.shapes) sh.group = ip.group;
                    }
                }
            }
            for (Ent e : made) {
                if (!(e instanceof Person)) continue;
                Person p = (Person) e;
                if (p.frozenT <= 0f) continue;
                float secs = p.frozenT;
                p.frozenT = 0f;
                Machines.freeze(w, p, secs);
                if (p.cell != null) for (RB b : p.rb) { b.setFrozen(true); for (Shape sh : b.shapes) sh.sensor = true; }
            }
            return true;
        } catch (Throwable e) {
            g.clearWorldForLoad();
            return false;
        }
    }

    private static Ent idx(ArrayList<Ent> l, String s) {
        int i = Integer.parseInt(s);
        return i >= 0 && i < l.size() ? l.get(i) : null;
    }
}

package com.frameboost.ragdoll;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.Random;

/** Game controller: input, tools, camera, update loop and drawing. No Android view code in here. */
final class Game {
    static final int DOWN = 0, MOVE = 1, UP = 2, CANCEL = 3;
    private static final int R_NONE = 0, R_UI = 1, R_TOOL = 2, R_PAN = 3, R_CAM = 4;
    static final float ROOM_W = 2600f;

    final Fx fx = new Fx();
    final Sfx sfx;
    final World world;
    final Renderer renderer = new Renderer();
    final Ui ui = new Ui();
    final Random rnd = new Random();
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();

    int sw, sh;
    float density = 1f;
    float camX, camY, zoom = 1f;
    boolean paused, slow;
    int gravMode;
    int tool = Tools.HAND;
    float fps = 60f;
    private float fpsAcc;
    private int fpsFrames;
    private boolean confirmClear;
    private float confirmT;
    private final ArrayList<Body> undo = new ArrayList<>();

    private static final class Ptr {
        boolean down;
        int role;
        float x, y, sx, sy, lx, ly;
        World.Grab grab;
        int uiAction;
        boolean dragged;
        float scrollStart;
        float fireCd;
    }
    private final Ptr[] ptrs = new Ptr[12];
    // camera gesture baseline
    private float camDist, camZoom0, camMidX, camMidY, camCx0, camCy0;

    Game(Sfx sfx) {
        this.sfx = sfx;
        world = new World(fx, sfx);
        world.wallL = 0; world.wallR = ROOM_W;
        for (int i = 0; i < ptrs.length; i++) ptrs[i] = new Ptr();
        refreshButtons();
        spawnDemo();
    }

    void spawnDemo() {
        world.add(Spawner.human(world, ROOM_W / 2 - 80, -120, 1f, 1));
        world.add(Spawner.crate(ROOM_W / 2 + 160, -80, 60));
        ui.stripOpen = true;
        ui.cat = 1;
    }

    void resize(int w, int h, float density) {
        sw = w; sh = h; this.density = density;
        ui.layout(w, h, density);
        resetCamera();
    }

    void resetCamera() {
        zoom = sw / 1500f;
        camX = ROOM_W / 2;
        float visH = sh / zoom;
        camY = world.floorY - visH * 0.12f;   // floor sits at ~62% of the screen, above the item strip
        clampCam();
    }

    private void clampCam() {
        float zMin = sw / (ROOM_W + 500f), zMax = sw / 380f;
        if (zoom < zMin) zoom = zMin;
        if (zoom > zMax) zoom = zMax;
        float hw = sw / zoom / 2f, hh = sh / zoom / 2f;
        float minX = world.wallL - 150f + Math.min(hw, 0), maxX = world.wallR + 150f;
        if (hw * 2 > ROOM_W + 300f) camX = ROOM_W / 2;
        else camX = Math.max(world.wallL - 150f + hw, Math.min(world.wallR + 150f - hw, camX));
        float topLimit = world.floorY - 1700f, botLimit = world.floorY + 520f;
        if (hh * 2 > botLimit - topLimit) camY = (topLimit + botLimit) / 2;
        else camY = Math.max(topLimit + hh, Math.min(botLimit - hh, camY));
    }

    float toWorldX(float sx) { return (sx - sw / 2f) / zoom + camX; }
    float toWorldY(float sy) { return (sy - sh / 2f) / zoom + camY; }

    // ---------------------------------------------------------------------------- update ----

    void update(float dtReal) {
        if (dtReal > 0.05f) dtReal = 0.05f;
        fpsAcc += dtReal; fpsFrames++;
        if (fpsAcc > 0.5f) { fps = fpsFrames / fpsAcc; fpsAcc = 0; fpsFrames = 0; }
        if (confirmT > 0) { confirmT -= dtReal; if (confirmT <= 0) confirmClear = false; }
        float sim = paused ? 0f : dtReal * (slow ? 0.3f : 1f);
        world.gravMul = gravMode == 0 ? 1f : (gravMode == 1 ? 0.17f : 0.0f);
        // held weapons
        for (Ptr pt : ptrs) {
            if (!pt.down || pt.role != R_TOOL) continue;
            if (tool == Tools.RIFLE && !paused) {
                pt.fireCd -= dtReal;
                if (pt.fireCd <= 0) { pt.fireCd = 0.09f; fire(tool, toWorldX(pt.x), toWorldY(pt.y)); }
            }
            if (tool == Tools.FIRE) igniteAt(toWorldX(pt.x), toWorldY(pt.y));
        }
        world.update(sim);
        fx.update(sim > 0 ? sim : 0);
        if (paused) fx.shake = Math.max(0, fx.shake - dtReal * 2f);
        world.updateTracers(dtReal);
        ui.update(dtReal);
    }

    // ------------------------------------------------------------------------------ draw ----

    void draw(Canvas c) {
        c.drawColor(0xFF0e1117);
        renderer.draw(c, world, fx, camX, camY, zoom, sw, sh);
        if (fx.flash > 0.01f) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(Renderer.alpha(0xFFfff6dd, fx.flash * 0.7f));
            c.drawRect(0, 0, sw, sh, p);
        }
        // aiming helpers
        for (Ptr pt : ptrs) {
            if (!pt.down || pt.role != R_TOOL) continue;
            if (Tools.isSpawn(tool)) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2.5f * density);
                p.setColor(0xCCf2b134);
                c.drawCircle(pt.x, pt.y, 22 * density, p);
                p.setStyle(Paint.Style.FILL);
                ui.drawIcon(c, tool, pt.x, pt.y, 18 * density);
            } else if (Tools.isWeapon(tool) || tool == Tools.LIGHTNING || tool == Tools.DETONATE || tool == Tools.NUKE || tool == Tools.PUSH) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2f * density);
                p.setColor(0xCCffffff);
                c.drawCircle(pt.x, pt.y, 16 * density, p);
                c.drawLine(pt.x - 24 * density, pt.y, pt.x + 24 * density, pt.y, p);
                c.drawLine(pt.x, pt.y - 24 * density, pt.x, pt.y + 24 * density, p);
            } else if (tool == Tools.CUT || tool == Tools.DELETE || tool == Tools.FIRE || tool == Tools.FREEZE) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2f * density);
                p.setColor(tool == Tools.FIRE ? 0xCCff9a1f : 0xCCffffff);
                c.drawCircle(pt.x, pt.y, 14 * density, p);
            }
        }
        Tools.Item it = Tools.find(tool);
        ui.draw(c, tool, it.name, it.hint, fps, world.bodies.size(), confirmClear);
    }

    // ------------------------------------------------------------------------------ input ----

    void touch(int action, int id, float x, float y) {
        if (id < 0 || id >= ptrs.length) return;
        Ptr pt = ptrs[id];
        switch (action) {
            case DOWN: onDown(pt, x, y); break;
            case MOVE: onMove(pt, x, y); break;
            case UP: onUp(pt, x, y, false); break;
            default: onUp(pt, x, y, true); break;
        }
    }

    private int activeOther(Ptr self) {
        int n = 0;
        for (Ptr o : ptrs) if (o != self && o.down && o.role != R_UI && o.role != R_NONE) n++;
        return n;
    }

    private Ptr firstOther(Ptr self) {
        for (Ptr o : ptrs) if (o != self && o.down && o.role != R_UI && o.role != R_NONE) return o;
        return null;
    }

    private void onDown(Ptr pt, float x, float y) {
        pt.down = true; pt.x = pt.sx = pt.lx = x; pt.y = pt.sy = pt.ly = y;
        pt.dragged = false; pt.grab = null; pt.role = R_NONE;
        int a = ui.hit(x, y);
        if (a != Ui.NONE) {
            pt.role = R_UI; pt.uiAction = a; pt.scrollStart = ui.scrollX;
            return;
        }
        Ptr other = firstOther(pt);
        boolean grabbed = false;
        if (tool == Tools.HAND) grabbed = tryGrab(pt, x, y);
        if (other != null && !grabbed && other.grab == null) {
            // second finger on empty space => camera gesture
            cancelTool(other);
            other.role = R_CAM; pt.role = R_CAM;
            beginCam(other, pt);
            return;
        }
        if (tool == Tools.HAND) {
            pt.role = grabbed ? R_TOOL : R_PAN;
            return;
        }
        pt.role = R_TOOL;
        float wx = toWorldX(x), wy = toWorldY(y);
        pt.fireCd = 0.09f;
        switch (tool) {
            case Tools.DELETE: deleteAt(wx, wy); break;
            case Tools.FREEZE: freezeAt(wx, wy); break;
            case Tools.FIRE: igniteAt(wx, wy); break;
            case Tools.CUT: break;
            case Tools.PISTOL: case Tools.SHOTGUN: case Tools.ROCKET: case Tools.RIFLE:
                fire(tool, wx, wy); break;
            case Tools.LIGHTNING: world.lightning(wx); break;
            case Tools.DETONATE: world.explode(wx, wy, 300f, 5000f, 90f); break;
            case Tools.NUKE: world.explode(wx, Math.min(wy, world.floorY - 60), 1500f, 14000f, 400f); break;
            case Tools.PUSH: world.shockwave(wx, wy, 520f, 3200f); break;
            default: break; // spawn items are placed on release
        }
    }

    private void cancelTool(Ptr o) {
        if (o.grab != null) { world.grabs.remove(o.grab); o.grab = null; }
    }

    private void onMove(Ptr pt, float x, float y) {
        if (!pt.down) return;
        pt.lx = pt.x; pt.ly = pt.y;
        pt.x = x; pt.y = y;
        float dx = x - pt.sx, dy = y - pt.sy;
        if (dx * dx + dy * dy > (10 * density) * (10 * density)) pt.dragged = true;
        switch (pt.role) {
            case R_UI:
                if (pt.uiAction >= Ui.ITEM && pt.uiAction < Ui.BTN || pt.uiAction == Ui.BLOCK) {
                    if (pt.dragged) {
                        ui.scrollX = Math.max(0, Math.min(ui.maxScroll(), pt.scrollStart - (x - pt.sx)));
                    }
                }
                break;
            case R_PAN:
                camX -= (x - pt.lx) / zoom;
                camY -= (y - pt.ly) / zoom;
                clampCam();
                break;
            case R_CAM:
                updateCam();
                break;
            case R_TOOL: {
                float wx = toWorldX(x), wy = toWorldY(y);
                if (pt.grab != null) { pt.grab.tx = wx; pt.grab.ty = wy; }
                else if (tool == Tools.DELETE) deleteAt(wx, wy);
                else if (tool == Tools.CUT) cutSegment(toWorldX(pt.lx), toWorldY(pt.ly), wx, wy);
                break;
            }
            default: break;
        }
    }

    private void onUp(Ptr pt, float x, float y, boolean cancelled) {
        if (!pt.down) return;
        pt.down = false;
        int role = pt.role;
        pt.role = R_NONE;
        if (role == R_UI) {
            if (!cancelled && !pt.dragged) {
                int a = ui.hit(x, y);
                if (a == pt.uiAction) uiAction(a);
            }
            return;
        }
        if (role == R_CAM) {
            // the other camera finger becomes a pure pan finger so nothing jumps
            for (Ptr o : ptrs) if (o != pt && o.down && o.role == R_CAM) { o.role = R_PAN; o.lx = o.x; o.ly = o.y; }
            return;
        }
        if (role == R_TOOL) {
            if (pt.grab != null) { world.grabs.remove(pt.grab); pt.grab = null; }
            else if (!cancelled && Tools.isSpawn(tool)) {
                spawn(tool, toWorldX(x), toWorldY(y));
            }
        }
    }

    // -------------------------------------------------------------------------------- camera ----

    private void beginCam(Ptr a, Ptr b) {
        camDist = Math.max(20f, dist(a.x, a.y, b.x, b.y));
        camZoom0 = zoom;
        camMidX = (a.x + b.x) / 2; camMidY = (a.y + b.y) / 2;
        camCx0 = camX; camCy0 = camY;
        // world point under the midpoint stays under the midpoint
        camMidWx = toWorldX(camMidX); camMidWy = toWorldY(camMidY);
    }
    private float camMidWx, camMidWy;

    private void updateCam() {
        Ptr a = null, b = null;
        for (Ptr o : ptrs) if (o.down && o.role == R_CAM) { if (a == null) a = o; else if (b == null) b = o; }
        if (a == null || b == null) return;
        float d = Math.max(20f, dist(a.x, a.y, b.x, b.y));
        zoom = camZoom0 * d / camDist;
        float mx = (a.x + b.x) / 2, my = (a.y + b.y) / 2;
        float zMin = sw / (ROOM_W + 500f), zMax = sw / 380f;
        zoom = Math.max(zMin, Math.min(zMax, zoom));
        camX = camMidWx - (mx - sw / 2f) / zoom;
        camY = camMidWy - (my - sh / 2f) / zoom;
        clampCam();
    }

    private static float dist(float x0, float y0, float x1, float y1) {
        float dx = x1 - x0, dy = y1 - y0;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    // ---------------------------------------------------------------------------------- UI ----

    private void uiAction(int a) {
        if (a >= Ui.TAB && a < Ui.ITEM) {
            int c = a - Ui.TAB;
            if (ui.cat == c && ui.stripOpen) ui.stripOpen = false;
            else { ui.cat = c; ui.stripOpen = true; ui.scrollX = 0; }
        } else if (a >= Ui.ITEM && a < Ui.BTN) {
            Tools.Item[] items = Tools.CATS[ui.cat];
            int i = a - Ui.ITEM;
            if (i >= 0 && i < items.length) {
                tool = items[i].id;
                ui.toast(items[i].name + " · " + items[i].hint);
            }
        } else if (a >= Ui.BTN) {
            switch (a - Ui.BTN) {
                case Ui.BTN_PAUSE: paused = !paused; break;
                case Ui.BTN_SLOW: slow = !slow; break;
                case Ui.BTN_GRAV: gravMode = (gravMode + 1) % 3; break;
                case Ui.BTN_UNDO: undoLast(); break;
                case Ui.BTN_CLEAR:
                    if (confirmClear) { clearAll(); confirmClear = false; }
                    else { confirmClear = true; confirmT = 2.5f; }
                    break;
                case Ui.BTN_SOUND: if (sfx != null) sfx.enabled = !sfx.enabled; break;
                default: break;
            }
            refreshButtons();
        }
    }

    void refreshButtons() {
        ui.btnLabel[Ui.BTN_PAUSE] = paused ? "Seguir" : "Pausa";
        ui.btnOn[Ui.BTN_PAUSE] = paused;
        ui.btnLabel[Ui.BTN_SLOW] = "Lento";
        ui.btnOn[Ui.BTN_SLOW] = slow;
        ui.btnLabel[Ui.BTN_GRAV] = gravMode == 0 ? "Gravedad" : (gravMode == 1 ? "Luna" : "Sin grav.");
        ui.btnOn[Ui.BTN_GRAV] = gravMode != 0;
        ui.btnLabel[Ui.BTN_UNDO] = "Deshacer";
        ui.btnLabel[Ui.BTN_CLEAR] = "Limpiar";
        ui.btnLabel[Ui.BTN_SOUND] = sfx != null && sfx.enabled ? "Sonido" : "Mudo";
        ui.btnOn[Ui.BTN_SOUND] = sfx == null || !sfx.enabled;
    }

    private void undoLast() {
        while (!undo.isEmpty()) {
            Body b = undo.remove(undo.size() - 1);
            if (!b.removed) { fx.smoke(b.cx, b.cy, 14, 0xFFaaaaaa); world.removeBody(b); return; }
        }
        ui.toast("Nada que deshacer");
    }

    private void clearAll() {
        world.clear();
        undo.clear();
        ui.toast("Escenario limpio");
    }

    // ------------------------------------------------------------------------------- tools -----

    private boolean tryGrab(Ptr pt, float sx, float sy) {
        float wx = toWorldX(sx), wy = toWorldY(sy);
        Particle q = world.pickParticle(wx, wy, 46f / zoom);
        if (q == null) return false;
        World.Grab g = new World.Grab();
        g.p = q; g.tx = wx; g.ty = wy;
        world.grabs.add(g);
        pt.grab = g;
        return true;
    }

    private void deleteAt(float wx, float wy) {
        Body b = world.pickBody(wx, wy, 34f / zoom);
        if (b == null) return;
        fx.smoke(b.cx, b.cy, 12, 0xFFbbbbbb);
        fx.sparks(b.cx, b.cy, 6, 0xFFffffff);
        world.removeBody(b);
    }

    private void freezeAt(float wx, float wy) {
        Particle q = world.pickParticle(wx, wy, 40f / zoom);
        if (q == null) return;
        q.setPinned(!q.pinned);
        for (int i = world.grabs.size() - 1; i >= 0; i--) if (world.grabs.get(i).p == q) world.grabs.remove(i);
        ui.toast(q.pinned ? "Pieza clavada" : "Pieza liberada");
    }

    private void igniteAt(float wx, float wy) {
        float rad = 36f / zoom;
        fx.flame(wx, wy, 9f);
        for (Body b : world.bodies) {
            float dx = b.cx - wx, dy = b.cy - wy;
            if (dx * dx + dy * dy > (b.rad + rad) * (b.rad + rad)) continue;
            for (Particle q : b.ps) {
                float ex = q.x - wx, ey = q.y - wy;
                if (ex * ex + ey * ey < (q.r + rad) * (q.r + rad)) { world.ignite(b); break; }
            }
        }
    }

    private void cutSegment(float x0, float y0, float x1, float y1) {
        if (dist(x0, y0, x1, y1) < 1f) return;
        fx.add(Fx.SPARK, x1, y1, fx.rr(60), fx.rr(60), 0.15f, 2f, 0xFFffffff);
        ArrayList<Body> victims = null;
        for (int i = 0; i < world.ss.size(); i++) {
            Stick s = world.ss.get(i);
            if (s.dead || !s.collide) continue;
            if (segDist(x0, y0, x1, y1, s.a.x, s.a.y, s.b.x, s.b.y) <= s.r + 2f) {
                float mx = (s.a.x + s.b.x) / 2, my = (s.a.y + s.b.y) / 2;
                if (victims == null) victims = new ArrayList<>();
                if (!victims.contains(s.body)) {
                    victims.add(s.body);
                    // cut where the swipe crosses
                    float cx = (x0 + x1) / 2, cy = (y0 + y1) / 2;
                    world.cutNear(s.body, Math.abs(cx - mx) < 80 ? cx : mx, Math.abs(cy - my) < 80 ? cy : my, 50f);
                }
            }
        }
    }

    private float[] tmp = new float[2];

    private float segDist(float ax, float ay, float bx, float by, float cx, float cy, float dx, float dy) {
        // 2D segment/segment distance (0 when they cross)
        float d1x = bx - ax, d1y = by - ay, d2x = dx - cx, d2y = dy - cy;
        float den = d1x * d2y - d1y * d2x;
        if (Math.abs(den) > 1e-6f) {
            float t = ((cx - ax) * d2y - (cy - ay) * d2x) / den;
            float u = ((cx - ax) * d1y - (cy - ay) * d1x) / den;
            if (t >= 0 && t <= 1 && u >= 0 && u <= 1) return 0f;
        }
        return Math.min(Math.min(pointSeg(ax, ay, cx, cy, dx, dy), pointSeg(bx, by, cx, cy, dx, dy)),
                Math.min(pointSeg(cx, cy, ax, ay, bx, by), pointSeg(dx, dy, ax, ay, bx, by)));
    }

    private static float pointSeg(float px, float py, float ax, float ay, float bx, float by) {
        float abx = bx - ax, aby = by - ay, l2 = abx * abx + aby * aby;
        float t = l2 > 1e-6f ? ((px - ax) * abx + (py - ay) * aby) / l2 : 0f;
        t = t < 0 ? 0 : (t > 1 ? 1 : t);
        float qx = ax + abx * t - px, qy = ay + aby * t - py;
        return (float) Math.sqrt(qx * qx + qy * qy);
    }

    // ---------------------------------------------------------------------------- weapons ------

    private void fire(int weapon, float tx, float ty) {
        // the shot comes in from the nearer side of the screen and ends at the tapped point
        float side = tx < camX ? 1f : -1f;
        float ox = tx - side * 230f, oy = ty + (weapon == Tools.RIFLE ? fx.rr(6) : 0f);
        ox = Math.max(world.wallL + 6, Math.min(world.wallR - 6, ox));
        float dx = tx - ox, dy = ty - oy;
        if (Math.abs(dx) < 20) dx = side * 20;
        if (sfx != null && weapon != Tools.ROCKET) sfx.shot();
        switch (weapon) {
            case Tools.PISTOL: world.bullet(ox, oy, dx, dy, 22f, 900f, 1); break;
            case Tools.RIFLE: world.bullet(ox, oy, dx, dy, 30f, 1500f, 2); break;
            case Tools.SHOTGUN: world.shotgun(ox, oy, dx, dy); fx.shake = Math.max(fx.shake, 0.15f); break;
            case Tools.ROCKET: world.launchRocket(ox, oy, dx, dy); break;
            default: break;
        }
        fx.sparks(ox, oy, 3, 0xFFffd25a);
    }

    // ----------------------------------------------------------------------------- spawning -----

    private void register(Body b) {
        if (b == null) return;
        if (!world.add(b)) { ui.toast("Límite de objetos alcanzado"); return; }
        undo.add(b);
        if (undo.size() > 80) undo.remove(0);
        fx.add(Fx.GLOW, b.cx, b.cy, 0, 0, 0.18f, Math.max(16f, b.rad * 0.8f), 0xFFffffff);
    }

    void spawn(int id, float x, float y) {
        float fl = world.floorY;
        x = Math.max(world.wallL + 70, Math.min(world.wallR - 70, x));
        y = Math.max(world.ceilY + 120, y);
        int dir = rnd.nextBoolean() ? 1 : -1;
        switch (id) {
            case Tools.HUMAN: register(Spawner.human(world, x, Math.min(y, fl - 96), 1f, dir)); break;
            case Tools.GIANT: register(Spawner.human(world, x, Math.min(y, fl - 180), 1.8f, dir)); break;
            case Tools.KID: register(Spawner.human(world, x, Math.min(y, fl - 60), 0.62f, dir)); break;
            case Tools.CROWD:
                for (int i = 0; i < 5; i++) {
                    float hx = Math.max(world.wallL + 70, Math.min(world.wallR - 70, x + (i - 2) * 75f));
                    register(Spawner.human(world, hx, Math.min(y, fl - 96) - (i % 2) * 30, 1f, rnd.nextBoolean() ? 1 : -1));
                }
                break;
            case Tools.CRATE: register(Spawner.crate(x, Math.min(y, fl - 40), 60)); break;
            case Tools.BIGCRATE: register(Spawner.crate(x, Math.min(y, fl - 70), 110)); break;
            case Tools.PLANK: register(Spawner.plank(x, Math.min(y, fl - 20), 260f, false)); break;
            case Tools.BARREL: register(Spawner.barrel(x, Math.min(y, fl - 40), false)); break;
            case Tools.BALL: register(Spawner.ball(x, Math.min(y, fl - 40), 24f, BALLC[rnd.nextInt(BALLC.length)], false)); break;
            case Tools.BALLOON: register(Spawner.ball(x, Math.min(y, fl - 40), 26f, BALLC[rnd.nextInt(BALLC.length)], true)); break;
            case Tools.STEEL: register(Spawner.heavy(x, Math.min(y, fl - 50))); break;
            case Tools.PLATFORM: register(Spawner.plank(x, y, 320f, true)); break;
            case Tools.GRENADE: register(Spawner.grenade(x, Math.min(y, fl - 20))); break;
            case Tools.DYNAMITE: register(Spawner.dynamite(x, Math.min(y, fl - 20))); break;
            case Tools.TNT: register(Spawner.tnt(x, Math.min(y, fl - 40))); break;
            case Tools.XBARREL: register(Spawner.barrel(x, Math.min(y, fl - 40), true)); break;
            case Tools.KNIFE: register(Spawner.blade(x, Math.min(y, fl - 20), false)); break;
            case Tools.SWORD: register(Spawner.blade(x, Math.min(y, fl - 20), true)); break;
            default: break;
        }
    }

    private static final int[] BALLC = {0xFFe0463c, 0xFF3aa0e0, 0xFF4cbf6b, 0xFFf2b134, 0xFFb06ad8, 0xFFf27ab0};
}

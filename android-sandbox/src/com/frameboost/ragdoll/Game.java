package com.frameboost.ragdoll;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import java.io.File;
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
    final ModEngine mods = new ModEngine();
    int userTheme = 0;
    boolean textures = true;
    private boolean silentSpawn;

    /** Implemented by the Activity so the game can open the native text editor for mods. */
    interface Host {
        void editMod(Mod existing);
        void saveSettings(Settings s);
        void vibrate(int ms);
    }
    Host host;
    Settings settings = new Settings();
    final Screens screens = new Screens();
    MusicPlayer music;
    File saveFile;

    // challenge state
    int challenge = -1, chUses, chResult, chStars;
    float chTime, chIdle, chSpawnT, chEndT, lastSimDt;
    final StringBuilder chProgress = new StringBuilder();
    private float slowmoT, demoT;
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
        float holdT;
    }
    private final Ptr[] ptrs = new Ptr[12];
    // camera gesture baseline
    private float camDist, camZoom0, camMidX, camMidY, camCx0, camCy0;

    Game(Sfx sfx) { this(sfx, null); }

    Game(Sfx sfx, File modsDir) {
        this.sfx = sfx;
        mods.load(modsDir);
        world = new World(fx, sfx);
        world.wallL = 0; world.wallR = ROOM_W;
        for (int i = 0; i < ptrs.length; i++) ptrs[i] = new Ptr();
        refreshButtons();
        titleDemo();
    }

    /** Live scene behind the title menu: people, zombies and the odd explosion. */
    void titleDemo() {
        world.clear();
        float f = world.floorY;
        for (int i = 0; i < 3; i++) world.add(Spawner.human(world, 1350 + i * 140, f - 96, 1f, -1));
        world.add(Spawner.human(world, 2350, f - 96, 1f, -1, Body.K_ZOMBIE));
        world.add(Spawner.human(world, 2480, f - 96, 1f, -1, Body.K_ZOMBIE));
        for (int i = 0; i < 3; i++) world.add(Spawner.crate(1900, f - 31 - i * 61, 60));
        world.add(Spawner.barrel(2060, f - 40, true));
        demoT = 0;
    }

    void spawnDemo() {
        world.clear();
        world.add(Spawner.human(world, ROOM_W / 2 - 80, world.floorY - 96, 1f, 1));
        world.add(Spawner.crate(ROOM_W / 2 + 160, -80, 60));
        ui.stripOpen = true;
        ui.cat = 1;
    }

    // ------------------------------------------------------------------------- game flow ----

    boolean hasSave() { return saveFile != null && saveFile.exists() && challenge < 0; }

    void saveScene() {
        if (saveFile == null || challenge >= 0 || screens.screen == Screens.TITLE) return;
        try { SceneIO.save(this, saveFile); } catch (Throwable ignored) { }
    }

    void saveSettings() { if (host != null) host.saveSettings(settings); applySettings(); }

    void applySettings() {
        fx.gore = settings.gore;
        fx.quality = settings.highQuality ? 1f : 0.5f;
        ui.showFps = settings.showFps;
        if (sfx != null) sfx.volume = settings.sfx;
        if (music != null) music.volume = settings.music;
    }

    void click() {
        if (sfx != null) sfx.click();
        haptic(8);
    }

    void haptic(int ms) { if (settings.haptics && host != null) host.vibrate(ms); }

    private void setPlayMode(int chal) {
        challenge = chal;
        screens.screen = Screens.NONE;
        paused = false; slow = false; gravMode = 0;
        world.grabs.clear();
        undo.clear();
        for (Mod m : mods.mods) if (m.on) mods.toggle(m, this);
        if (chal >= 0) {
            Challenges.setup(this, chal);
            Challenges.Def d = Challenges.LIST[chal];
            Tools.Item[] items = new Tools.Item[d.tools.length];
            for (int i = 0; i < d.tools.length; i++) items[i] = Tools.find(d.tools[i]);
            Tools.CATS[Tools.CAT_CHAL] = items;
            ui.cats = new int[]{Tools.CAT_CHAL};
            ui.cat = Tools.CAT_CHAL; ui.stripOpen = true; ui.scrollX = 0;
            tool = d.tools[0];
            chUses = d.uses; chTime = 0; chIdle = 0; chResult = 0; chSpawnT = 3f; chEndT = 0;
            ui.toast(d.goal);
            if (music != null) music.play(Music.CHALLENGE);
        } else {
            ui.cats = new int[]{0, 1, 2, 3, 4, 5, 6, 7};
            tool = Tools.HAND;
            if (music != null) music.play(Music.LAB);
        }
        ui.btnHidden[Ui.BTN_GRAV] = chal >= 0;
        ui.btnHidden[Ui.BTN_CLEAR] = chal >= 0;
        ui.btnHidden[Ui.BTN_UNDO] = chal >= 0;
        if (sw > 0) ui.layout(sw, sh, density);
        refreshButtons();
    }

    /** Actions coming from the menus. */
    void menuAction(int a) {
        switch (a) {
            case Screens.A_CONTINUE:
                setPlayMode(-1);
                if (!SceneIO.load(this, saveFile)) spawnDemo();
                ui.stripOpen = false;
                clampCam();
                break;
            case Screens.A_SANDBOX:
                setPlayMode(-1);
                spawnDemo();
                resetCamera();
                if (!settings.tutorialDone) { screens.screen = Screens.TUTORIAL; screens.tutStep = 0; }
                break;
            case Screens.A_CHALLENGES: screens.screen = Screens.CHALLENGES; break;
            case Screens.A_SETTINGS: screens.back = screens.screen; screens.screen = Screens.SETTINGS; break;
            case Screens.A_BACK:
                screens.screen = screens.screen == Screens.SETTINGS ? screens.back
                        : (screens.screen == Screens.CHALLENGES ? Screens.TITLE : Screens.NONE);
                break;
            case Screens.A_RESUME: screens.screen = Screens.NONE; paused = false; refreshButtons(); break;
            case Screens.A_MENU:
                saveScene();
                challenge = -1;
                screens.screen = Screens.TITLE;
                titleDemo();
                resetCamera();
                if (music != null) music.play(Music.TITLE);
                break;
            case Screens.A_RESTART: case Screens.A_RETRY: setPlayMode(challenge); break;
            case Screens.A_NEXT: setPlayMode(Math.min(Challenges.LIST.length - 1, challenge + 1)); break;
            case Screens.A_MUSIC_DN: settings.music = Math.max(0f, settings.music - 0.1f); saveSettings(); break;
            case Screens.A_MUSIC_UP: settings.music = Math.min(1f, settings.music + 0.1f); saveSettings(); break;
            case Screens.A_SFX_DN: settings.sfx = Math.max(0f, settings.sfx - 0.1f); saveSettings(); break;
            case Screens.A_SFX_UP: settings.sfx = Math.min(1f, settings.sfx + 0.1f); saveSettings(); break;
            case Screens.A_HAPTICS: settings.haptics = !settings.haptics; saveSettings(); haptic(30); break;
            case Screens.A_GORE: settings.gore = !settings.gore; saveSettings(); break;
            case Screens.A_QUALITY: settings.highQuality = !settings.highQuality; saveSettings(); break;
            case Screens.A_FPS: settings.showFps = !settings.showFps; saveSettings(); break;
            case Screens.A_TEXTURES: textures = !textures; break;
            default:
                if (a >= Screens.A_CHAL) setPlayMode(a - Screens.A_CHAL);
                break;
        }
    }

    /** One tool use in a challenge with limited uses. Returns false when none are left. */
    private boolean useCharge() {
        if (challenge < 0 || chUses < 0) return true;
        if (chUses == 0) { ui.toast("¡Sin usos! Espera al resultado o reinicia"); return false; }
        chUses--;
        chIdle = 0;
        return true;
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
        lastFrameDt = dtReal;
        fpsAcc += dtReal; fpsFrames++;
        if (fpsAcc > 0.5f) { fps = fpsFrames / fpsAcc; fpsAcc = 0; fpsFrames = 0; }
        if (confirmT > 0) { confirmT -= dtReal; if (confirmT <= 0) confirmClear = false; }
        boolean menu = screens.screen != Screens.NONE && screens.screen != Screens.TITLE && screens.screen != Screens.RESULT
                && screens.screen != Screens.TUTORIAL;
        if (slowmoT > 0) slowmoT -= dtReal;
        float sim = paused || menu ? 0f : dtReal * (slow ? 0.3f : 1f) * mods.timescale * (slowmoT > 0 ? 0.35f : 1f);
        lastSimDt = sim;
        if (screens.screen == Screens.TITLE) {
            demoT += dtReal;
            if (demoT > 7f) {     // something dramatic every few seconds
                demoT = 0;
                int n = 0;
                for (Body b : world.bodies) if (b.isHuman()) n++;
                if (n < 4 || world.bodies.size() < 6) titleDemo();
                else world.explode(1700 + rnd.nextFloat() * 500, world.floorY - 30, 260, 4800, 30);
            }
        }
        if (challenge >= 0 && screens.screen == Screens.NONE && chResult == 0 && sim > 0) {
            chTime += sim; chIdle += sim;
            int r = Challenges.check(this, challenge, chProgress);
            if (r != 0) {
                chResult = r;
                chEndT = 1.2f;
                if (r > 0) { chStars = Challenges.stars(this, challenge); settings.setStars(challenge, chStars); saveSettings(); }
            }
        } else if (challenge >= 0 && chResult != 0 && chEndT > 0) {
            chEndT -= dtReal;
            if (chEndT <= 0) { screens.screen = Screens.RESULT; screens.time = 0; haptic(chResult > 0 ? 60 : 30); }
        }
        world.gravMul = (gravMode == 0 ? 1f : (gravMode == 1 ? 0.17f : 0.0f)) * mods.gravity;
        world.wind = mods.wind;
        world.modBounce = mods.bounce;
        Spawner.headScale = mods.head;
        renderer.theme = mods.theme >= 0 ? mods.theme : userTheme;
        renderer.detail = textures;
        ui.active.clear();
        for (int i = 0; i < mods.mods.size(); i++) if (mods.mods.get(i).on) ui.active.add(Tools.MOD_BASE + i);
        ui.active.add(Tools.STYLE_BASE + (mods.theme >= 0 ? mods.theme : userTheme));
        if (textures) ui.active.add(Tools.STYLE_TEX);
        // long-press on one of your own mods opens the editor
        for (Ptr pt : ptrs) {
            if (!pt.down || pt.role != R_UI || pt.dragged) continue;
            pt.holdT += dtReal;
            if (pt.holdT > 0.6f && pt.uiAction >= Ui.ITEM && pt.uiAction < Ui.BTN && ui.cat == Tools.CAT_MODS) {
                int mi = pt.uiAction - Ui.ITEM - 1;
                pt.uiAction = Ui.BLOCK;
                if (mi >= 0 && mi < mods.mods.size() && mods.mods.get(mi).user && host != null) host.editMod(mods.mods.get(mi));
                else if (mi >= 0) ui.toast("Los mods del Taller incluidos no se editan; crea uno nuevo");
            }
        }
        if (!paused) { silentSpawn = true; mods.update(sim, this); silentSpawn = false; }
        // held weapons
        for (Ptr pt : ptrs) {
            if (!pt.down || pt.role != R_TOOL) continue;
            if (tool == Tools.RIFLE && !paused) {
                pt.fireCd -= dtReal;
                if (pt.fireCd <= 0) { pt.fireCd = 0.09f; if (useCharge()) fire(tool, toWorldX(pt.x), toWorldY(pt.y)); }
            }
            if (tool == Tools.FIRE) igniteAt(toWorldX(pt.x), toWorldY(pt.y));
        }
        world.update(sim);
        if (world.rumble > 0f) {
            float rr = world.rumble;
            world.rumble = 0f;
            haptic((int) Math.min(160, 12 + rr * 90));
            if (music != null) music.excite(Math.min(1f, 0.3f + rr));
            if (rr >= 0.95f && slowmoT <= 0 && screens.screen == Screens.NONE) slowmoT = 0.55f;
        }
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
        if (screens.screen != Screens.TITLE && screens.screen != Screens.CHALLENGES) {
            Tools.Item it = Tools.find(tool);
            ui.draw(c, tool, it.name, it.hint, fps, world.bodies.size(), confirmClear);
            if (challenge >= 0) challengeBanner(c);
        }
        screens.draw(c, this, lastFrameDt);
    }

    private float lastFrameDt = 1f / 60f;

    private void challengeBanner(Canvas c) {
        Challenges.Def d = Challenges.LIST[challenge];
        float w = Math.min(sw * 0.5f, 460 * density), x = sw / 2f - w / 2, y = 52 * density;
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xCC141820);
        rf.set(x, y, x + w, y + 54 * density);
        c.drawRoundRect(rf, 12 * density, 12 * density, p);
        p.setTextAlign(Paint.Align.CENTER);
        p.setFakeBoldText(true);
        p.setTextSize(13 * density);
        p.setColor(0xFFf2b134);
        c.drawText(d.name + String.format("   %.1f s", chTime), sw / 2f, y + 21 * density, p);
        p.setFakeBoldText(false);
        p.setTextSize(12 * density);
        p.setColor(0xFFe9edf4);
        c.drawText(chProgress.length() > 0 ? chProgress.toString() : d.goal, sw / 2f, y + 42 * density, p);
    }

    // ------------------------------------------------------------------------------ input ----

    void touch(int action, int id, float x, float y) {
        if (id < 0 || id >= ptrs.length) return;
        Ptr pt = ptrs[id];
        if (screens.screen != Screens.NONE && !pt.down) {
            screens.touch(action, x, y, this);
            return;
        }
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
        pt.dragged = false; pt.grab = null; pt.role = R_NONE; pt.holdT = 0;
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
        boolean instant = Tools.isWeapon(tool) || tool == Tools.LIGHTNING || tool == Tools.DETONATE || tool == Tools.NUKE || tool == Tools.PUSH;
        if (instant && !useCharge()) return;
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
            else if (!cancelled && Tools.isSpawn(tool) && useCharge()) {
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
        click();
        if (a >= Ui.TAB && a < Ui.ITEM) {
            int c = a - Ui.TAB;
            if (ui.cat == c && ui.stripOpen) ui.stripOpen = false;
            else { ui.cat = c; ui.stripOpen = true; ui.scrollX = 0; }
        } else if (a >= Ui.ITEM && a < Ui.BTN) {
            Tools.Item[] items = Tools.CATS[ui.cat];
            int i = a - Ui.ITEM;
            if (i >= 0 && i < items.length) {
                int id = items[i].id;
                if (id == Tools.MOD_CREATE) { if (host != null) host.editMod(null); else ui.toast("Editor no disponible"); }
                else if (id >= Tools.STYLE_TEX && id < Tools.STYLE_TEX + 50) { textures = !textures; ui.toast(textures ? "Texturas activadas" : "Texturas desactivadas"); }
                else if (id >= Tools.STYLE_BASE) { userTheme = id - Tools.STYLE_BASE; ui.toast("Fondo: " + Tools.THEMES[userTheme]); }
                else if (id >= Tools.MOD_BASE) {
                    Mod m = mods.mods.get(id - Tools.MOD_BASE);
                    mods.toggle(m, this);
                    ui.toast((m.on ? "Mod activado: " : "Mod desactivado: ") + m.name);
                } else {
                    tool = id;
                    ui.toast(items[i].name + " · " + items[i].hint);
                }
            }
        } else if (a >= Ui.BTN) {
            switch (a - Ui.BTN) {
                case Ui.BTN_PAUSE: paused = !paused; break;
                case Ui.BTN_MENU: screens.screen = Screens.PAUSE; saveScene(); break;
                case Ui.BTN_SLOW: slow = !slow; break;
                case Ui.BTN_GRAV: gravMode = (gravMode + 1) % 3; break;
                case Ui.BTN_UNDO: undoLast(); break;
                case Ui.BTN_CLEAR:
                    if (confirmClear) { clearAll(); confirmClear = false; }
                    else { confirmClear = true; confirmT = 2.5f; }
                    break;
                case Ui.BTN_SOUND:
                    if (sfx != null) sfx.enabled = !sfx.enabled;
                    if (music != null) music.volume = sfx != null && !sfx.enabled ? 0f : settings.music;
                    break;
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
        ui.btnLabel[Ui.BTN_MENU] = "Menú";
        ui.btnOn[Ui.BTN_SOUND] = sfx == null || !sfx.enabled;
    }

    /** Called by the editor dialog. Returns an error text, or null when saved. */
    String saveMod(Mod existing, String source) {
        String err = mods.save(existing, source);
        if (err == null) ui.toast("Mod guardado");
        return err;
    }

    void deleteMod(Mod m) { mods.delete(m); ui.toast("Mod borrado"); }

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
        if (!world.add(b)) { if (!silentSpawn) ui.toast("Límite de objetos alcanzado"); return; }
        undo.add(b);
        if (undo.size() > 80) undo.remove(0);
        fx.add(Fx.GLOW, b.cx, b.cy, 0, 0, 0.18f, Math.max(16f, b.rad * 0.8f), 0xFFffffff);
    }

    void spawn(int id, float x, float y) {
        float fl = world.floorY;
        x = Math.max(world.wallL + 70, Math.min(world.wallR - 70, x));
        y = Math.max(world.ceilY + 120, y);
        int dir = rnd.nextBoolean() ? 1 : -1;
        float hs = mods.hscale;
        switch (id) {
            case Tools.ROBOT: register(Spawner.human(world, x, Math.min(y, fl - 96 * hs), hs, dir, Body.K_ROBOT)); break;
            case Tools.ZOMBIE: register(Spawner.human(world, x, Math.min(y, fl - 96 * hs), hs, dir, Body.K_ZOMBIE)); break;
            case Tools.SKELETON: register(Spawner.human(world, x, Math.min(y, fl - 96 * hs), hs, dir, Body.K_SKELETON)); break;
            case Tools.WHEEL: register(Spawner.wheel(x, Math.min(y, fl - 50))); break;
            case Tools.BOWLING: register(Spawner.bowling(x, Math.min(y, fl - 30))); break;
            case Tools.MELON: register(Spawner.melon(x, Math.min(y, fl - 40))); break;
            case Tools.HUMAN: register(Spawner.human(world, x, Math.min(y, fl - 96 * hs), hs, dir)); break;
            case Tools.GIANT: register(Spawner.human(world, x, Math.min(y, fl - 180), 1.8f * hs, dir)); break;
            case Tools.KID: register(Spawner.human(world, x, Math.min(y, fl - 60), 0.62f * hs, dir)); break;
            case Tools.CROWD:
                for (int i = 0; i < 5; i++) {
                    float hx = Math.max(world.wallL + 70, Math.min(world.wallR - 70, x + (i - 2) * 75f));
                    register(Spawner.human(world, hx, Math.min(y, fl - 96 * hs) - (i % 2) * 30, hs, rnd.nextBoolean() ? 1 : -1));
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

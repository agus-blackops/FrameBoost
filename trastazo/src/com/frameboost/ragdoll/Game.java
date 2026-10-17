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
    private static final int R_NONE = 0, R_UI = 1, R_TOOL = 2, R_PAN = 3, R_CAM = 4, R_HOLD = 5;
    static final float ROOM_W = 3200f;

    final Sfx sfx;
    final World world;
    final Fx fx;
    final ModEngine mods = new ModEngine();
    int userTheme = Renderer.SANDBOX;
    boolean textures = true;
    private boolean silentSpawn;

    /** Implemented by the Activity so the game can open the native text editor for mods. */
    interface Host {
        void editMod(Mod existing);
        void saveSettings(Settings s);
        void vibrate(int ms);
        void switchEngine(boolean gpu);
        /** PC: toggles full screen (unused on Android). */
        void toggleFullscreen();
    }
    /** True in the PC port: mouse and keyboard, settings show full screen instead of vibration/engine. */
    static boolean desktop;
    boolean fullscreen;
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
    // inspection + long-press context menu
    Person inspected;
    Ent ctxEnt;
    private float ctxX, ctxY;
    private final int[] ctxActs = new int[6];
    private int ctxN;
    private static final int CX_ACTIVATE = 1, CX_INSPECT = 2, CX_DUPLICATE = 3, CX_PIN = 4, CX_DELETE = 5;
    // two-tap tools (rope / weld / hinge)
    private RB linkBody;
    private float linkLx, linkLy;           // anchor in the body's frame
    // camera follow (double tap with the hand)
    Ent follow;
    private float lastTapT = -1f, lastTapX, lastTapY, clock;
    final Achievements achievements = new Achievements();
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
    int tool = Items.HAND;
    float fps = 60f;
    private float fpsAcc;
    private int fpsFrames;
    private boolean confirmClear;
    private float confirmT;
    private final ArrayList<Ent> undo = new ArrayList<>();

    private static final class Ptr {
        boolean down;
        int role;
        float x, y, sx, sy, lx, ly;
        Joint grab;
        int uiAction;
        boolean dragged;
        float scrollStart;
        float fireCd;
        float holdT;
    }
    private final Ptr[] ptrs = new Ptr[12];
    private float camDist, camZoom0, camMidX, camMidY, camCx0, camCy0;

    Game(Sfx sfx) { this(sfx, null); }

    Game(Sfx sfx, File modsDir) {
        this.sfx = sfx;
        mods.load(modsDir);
        world = new World(sfx);
        fx = world.fx;
        world.sim.wallL = 0; world.sim.wallR = ROOM_W;
        fx.wallL = 0; fx.wallR = ROOM_W;
        for (int i = 0; i < ptrs.length; i++) ptrs[i] = new Ptr();
        refreshButtons();
        titleDemo();
        screens.screen = Screens.SPLASH;      // "Made with OpenGL" -> "Made with Opus 5.5" -> title
        screens.time = 0;
    }

    float floorY() { return world.sim.floorY; }

    /** Live scene behind the title menu: people, zombies and the odd explosion. */
    void titleDemo() {
        clearWorld();
        for (int i = 0; i < 3; i++) world.spawn(Items.HUMAN, 1500 + i * 140, -120, -1, 1f);
        world.spawn(Items.ZOMBIE, 2500, -120, -1, 1f);
        world.spawn(Items.ZOMBIE, 2640, -120, -1, 1f);
        for (int i = 0; i < 3; i++) world.spawn(Items.CRATE, 2050, -40 - i * 62, 1, 1f);
        world.spawn(Items.XBARREL, 2210, -40, 1, 1f);
        demoT = 0;
    }

    void spawnDemo() {
        clearWorld();
        world.spawn(Items.HUMAN, ROOM_W / 2 - 80, -120, 1, 1f);
        world.spawn(Items.CRATE, ROOM_W / 2 + 160, -200, 1, 1f);
        ui.stripOpen = true;
        ui.cat = 1;
    }

    /** Used by the loader. */
    void clearWorldForLoad() { clearWorld(); }

    private void clearWorld() {
        for (Ptr pt : ptrs) { pt.grab = null; pt.down = false; pt.role = R_NONE; }
        world.clear();
        world.grabCount = 0;
        undo.clear();
        follow = null; linkBody = null; inspected = null; ctxEnt = null;
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
        textures = settings.textures;
        if (sfx != null) sfx.enabled = !settings.muted;
        ui.showFps = settings.showFps;
        if (sfx != null) sfx.volume = settings.sfx;
        if (music != null) music.volume = settings.muted ? 0f : settings.music;
        refreshButtons();
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
        undo.clear();
        for (Ptr pt : ptrs) { pt.grab = null; pt.down = false; pt.role = R_NONE; }
        world.grabCount = 0;
        follow = null; linkBody = null; inspected = null; ctxEnt = null;
        for (Mod m : mods.mods) if (m.on) mods.toggle(m, this);
        if (chal >= 0) {
            Challenges.setup(this, chal);
            Challenges.Def d = Challenges.LIST[chal];
            // the hand is always there, so it never takes a slot in the dock
            int nt = 0; for (int t : d.tools) if (t != Items.HAND) nt++;
            Items.Item[] items = new Items.Item[Math.max(1, nt)];
            int k = 0; for (int t : d.tools) if (t != Items.HAND) items[k++] = Items.find(t);
            if (nt == 0) items[0] = Items.find(Items.HAND);
            Items.CATS[Items.CAT_CHAL] = items;
            ui.cats = new int[]{Items.CAT_CHAL};
            ui.cat = Items.CAT_CHAL; ui.stripOpen = false; ui.scrollY = 0; ui.dock = true;
            tool = items[0].id;
            chUses = d.uses; chTime = 0; chIdle = 0; chResult = 0; chSpawnT = 3f; chEndT = 0;
            ui.toast(d.goal);
            if (music != null) music.play(Music.CHALLENGE);
        } else {
            ui.cats = Items.PALETTE;
            ui.dock = false; ui.dockBadge = null;
            tool = Items.HAND;
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
                if (!settings.tutorialDone) { screens.screen = Screens.TUTORIAL; screens.tutStep = 0; screens.tutT = 0; tutBodies = world.ents.size(); tutZoom = zoom; }
                break;
            case Screens.A_CHALLENGES: screens.screen = Screens.CHALLENGES; break;
            case Screens.A_ACH: screens.screen = Screens.ACHIEVEMENTS; break;
            case Screens.A_SETTINGS: screens.back = screens.screen; screens.screen = Screens.SETTINGS; break;
            case Screens.A_BACK:
                screens.screen = screens.screen == Screens.SETTINGS ? screens.back
                        : (screens.screen == Screens.CHALLENGES || screens.screen == Screens.ACHIEVEMENTS ? Screens.TITLE : Screens.NONE);
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
            case Screens.A_HAPTICS:
                if (desktop) { if (host != null) host.toggleFullscreen(); break; }
                settings.haptics = !settings.haptics; saveSettings(); haptic(30); break;
            case Screens.A_GORE: settings.gore = !settings.gore; saveSettings(); break;
            case Screens.A_QUALITY: settings.highQuality = !settings.highQuality; saveSettings(); break;
            case Screens.A_FPS: settings.showFps = !settings.showFps; saveSettings(); break;
            case Screens.A_ENGINE:
                if (desktop) { ui.toast("En PC se usa el motor Java2D"); break; }
                settings.gpu = !settings.gpu;
                saveSettings();
                if (host != null) host.switchEngine(settings.gpu);
                ui.toast(settings.gpu ? "Motor gráfico: GPU (OpenGL ES)" : "Motor gráfico: Canvas");
                break;
            case Screens.A_TEXTURES: settings.textures = !settings.textures; saveSettings(); break;
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
        zoom = sw / 1750f;
        camX = ROOM_W / 2;
        float floorScreenY = sh - 64 * density;
        floorScreenY = Math.max(sh * 0.45f, floorScreenY);
        camY = floorY() - (floorScreenY - sh / 2f) / zoom;
        camX = ROOM_W / 2 - ui.railW / 2f / zoom;      // keep the room centred in the space right of the rail
        clampCam();
    }

    void clampCam() {
        float zMin = sw / (ROOM_W + 500f), zMax = sw / 380f;
        if (zoom < zMin) zoom = zMin;
        if (zoom > zMax) zoom = zMax;
        float hw = sw / zoom / 2f, hh = sh / zoom / 2f;
        if (hw * 2 > ROOM_W + 300f) camX = ROOM_W / 2;
        else camX = Math.max(-150f + hw, Math.min(ROOM_W + 150f - hw, camX));
        float topLimit = floorY() - 1900f, botLimit = floorY() + 520f;
        if (hh * 2 > botLimit - topLimit) camY = (topLimit + botLimit) / 2;
        else camY = Math.max(topLimit + hh, Math.min(botLimit - hh, camY));
    }

    float toWorldX(float sx) { return (sx - sw / 2f) / zoom + camX; }
    float toWorldY(float sy) { return (sy - sh / 2f) / zoom + camY; }

    // ---------------------------------------------------------------------------- update ----

    void update(float dtReal) {
        if (dtReal > 0.05f) dtReal = 0.05f;
        lastFrameDt = dtReal;
        screens.tickSplash(this, dtReal);
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
            if (demoT > 7f) {
                demoT = 0;
                int n = 0;
                for (Ent e : world.ents) if (e instanceof Person && !((Person) e).dead) n++;
                if (n < 4 || world.ents.size() < 6) titleDemo();
                else world.explode(1900 + rnd.nextFloat() * 600, floorY() - 30, 260, 1800f, 30f, true);
            }
        }
        ui.dockBadge = challenge >= 0 && chUses >= 0 ? "×" + chUses : null;
        tutorialProgress();
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
        world.sim.gravMul = (gravMode == 0 ? 1f : (gravMode == 1 ? 0.17f : 0.0f)) * mods.gravity;
        world.fx.gore = settings.gore;
        Person.headScale = mods.head;
        renderer.theme = mods.theme >= 0 ? mods.theme : userTheme;
        renderer.detail = textures;
        ui.active.clear();
        for (int i = 0; i < mods.mods.size(); i++) if (mods.mods.get(i).on) ui.active.add(Items.MOD_BASE + i);
        ui.active.add(Items.STYLE_BASE + (mods.theme >= 0 ? mods.theme : userTheme));
        // long-press on an object (whatever the tool) opens its context menu
        for (Ptr pt : ptrs) {
            if (!pt.down || pt.role != R_HOLD || pt.dragged) continue;
            pt.holdT += dtReal;
            if (pt.holdT > 0.55f) {
                Ent e = entAt(pt.sx, pt.sy);
                pt.role = R_NONE;
                if (e != null) openContext(e, pt.x, pt.y);
            }
        }
        if (inspected != null && inspected.removed) inspected = null;
        renderer.inspect = inspected;
        for (Ptr pt : ptrs) {
            if (!pt.down || pt.role != R_UI || pt.dragged) continue;
            pt.holdT += dtReal;
            if (pt.holdT > 0.6f && pt.uiAction >= Ui.ITEM && pt.uiAction < Ui.BTN && ui.cat == Items.CAT_MODS) {
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
            if (tool == Items.RIFLE && !paused) {
                pt.fireCd -= dtReal;
                if (pt.fireCd <= 0) { pt.fireCd = 0.09f; if (useCharge()) fire(tool, toWorldX(pt.x), toWorldY(pt.y)); }
            }
            if (tool == Items.FIRE) igniteAt(toWorldX(pt.x), toWorldY(pt.y));
        }
        // the mouse joints tell the world who is being held
        world.grabCount = 0;
        for (Ptr pt : ptrs) if (pt.down && pt.grab != null && world.grabCount < world.grabbed.length) world.grabbed[world.grabCount++] = pt.grab.b;
        world.update(sim);
        clock += dtReal;
        if (follow != null) {
            if (follow.removed || follow.bodies.isEmpty()) follow = null;
            else {
                float k = Math.min(1f, dtReal * 4f);
                camX += (follow.cx() - camX) * k;
                camY += (follow.cy() - 60f - camY) * k * 0.6f;
                clampCam();
            }
        }
        if (linkBody != null && linkBody.removed) linkBody = null;
        achievements.update(this, dtReal);
        if (world.rumble > 0f) {
            float rr = world.rumble;
            world.rumble = 0f;
            haptic((int) Math.min(160, 12 + rr * 90));
            if (music != null) music.excite(Math.min(1f, 0.3f + rr));
            if (rr >= 0.95f && slowmoT <= 0 && screens.screen == Screens.NONE) slowmoT = 0.55f;
        }
        if (paused) fx.shake = Math.max(0, fx.shake - dtReal * 2f);
        ui.update(dtReal);
    }

    // ------------------------------------------------------------------------------ draw ----

    private CanvasDraw canvasDraw;

    /** Software path (Canvas engine): world + interface on one canvas. */
    void draw(Canvas c) {
        c.drawColor(0xFF0e1117);
        if (canvasDraw == null) canvasDraw = new CanvasDraw(c);
        canvasDraw.c = c;
        drawWorld(canvasDraw);
        if (fx.flash > 0.01f) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(Renderer.alpha(0xFFfff6dd, fx.flash * 0.7f));
            c.drawRect(0, 0, sw, sh, p);
        }
        drawOverlay(c);
    }

    /** The world, through any engine. */
    void drawWorld(Draw2D d) {
        float shake = fx.shake * 14f;
        float ox = shake > 0.01f ? (rnd.nextFloat() - 0.5f) * shake : 0f, oy = shake > 0.01f ? (rnd.nextFloat() - 0.5f) * shake : 0f;
        renderer.draw(d, world, camX + ox, camY + oy, zoom, sw, sh);
    }

    private static final float[][] AMBIENT = {
        {0.93f, 0.93f, 0.97f}, {1.0f, 1.0f, 1.0f}, {0.42f, 0.47f, 0.66f}, {0.84f, 0.78f, 0.72f}, {0.55f, 0.48f, 0.68f}, {1.0f, 0.97f, 0.9f}, {0.96f, 0.97f, 1.0f},
    };

    /** Per-frame settings for the GPU engine (lighting mood, flash, quality). */
    void setupEngine(GLEngine e) {
        int t = Math.max(0, Math.min(AMBIENT.length - 1, renderer.theme));
        e.ambR = AMBIENT[t][0]; e.ambG = AMBIENT[t][1]; e.ambB = AMBIENT[t][2];
        e.flash = Math.min(0.8f, fx.flash * 0.75f);
        e.effects = settings.highQuality;
        e.vignette = t == 2 || t == 4 ? 0.8f : 0.5f;
    }

    /** Everything that is interface, drawn on top of the world (a Canvas overlay on the GPU path). */
    void drawOverlay(Canvas c) {
        Challenges.drawOverlay(c, this, p);
        // pending rope / weld / hinge
        if (linkBody != null && (tool == Items.ROPE || tool == Items.WELD || tool == Items.HINGE)) {
            float lx = (linkBody.localToWorldX(linkLx, linkLy) - camX) * zoom + sw / 2f, ly = (linkBody.localToWorldY(linkLx, linkLy) - camY) * zoom + sh / 2f;
            float pulse = 0.5f + 0.5f * (float) Math.sin(clock * 8f);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3f * density);
            p.setColor(Renderer.alpha(0xFF35d6c0, 0.6f + 0.4f * pulse));
            c.drawCircle(lx, ly, (10 + 4 * pulse) * density, p);
        }
        if (inspected != null && screens.screen == Screens.NONE) drawInspection(c);
        if (ctxEnt != null) drawContext(c);
        achievements.draw(c, this);
        // aiming helpers
        for (Ptr pt : ptrs) {
            if (!pt.down || pt.role != R_TOOL) continue;
            if (Items.isSpawn(tool)) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2.5f * density);
                p.setColor(0xCC35d6c0);
                c.drawCircle(pt.x, pt.y, 22 * density, p);
                p.setStyle(Paint.Style.FILL);
                ui.drawIcon(c, tool, pt.x, pt.y, 18 * density);
            } else if (Items.isShot(tool) || tool == Items.LIGHTNING || tool == Items.DETONATE || tool == Items.NUKE || tool == Items.PUSH) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2f * density);
                p.setColor(0xCCffffff);
                c.drawCircle(pt.x, pt.y, 16 * density, p);
                c.drawLine(pt.x - 24 * density, pt.y, pt.x + 24 * density, pt.y, p);
                c.drawLine(pt.x, pt.y - 24 * density, pt.x, pt.y + 24 * density, p);
            } else if (tool == Items.CUT || tool == Items.DELETE || tool == Items.FIRE || tool == Items.FREEZE) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2f * density);
                p.setColor(tool == Items.FIRE ? 0xCCff9a1f : 0xCCffffff);
                c.drawCircle(pt.x, pt.y, 14 * density, p);
            }
        }
        if (screens.screen != Screens.TITLE && screens.screen != Screens.CHALLENGES && screens.screen != Screens.ACHIEVEMENTS
                && screens.screen != Screens.SPLASH) {
            Items.Item it = Items.find(tool);
            ui.draw(c, tool, it.name, it.hint, fps, world.ents.size(), confirmClear);
            if (challenge >= 0) challengeBanner(c);
        }
        screens.draw(c, this, lastFrameDt);
    }

    private float lastFrameDt = 1f / 60f;

    private void challengeBanner(Canvas c) {
        Challenges.Def d = Challenges.LIST[challenge];
        float w = Math.min(sw * 0.62f, 460 * density), x = sw / 2f - w / 2, y = sh - ui.tabH - 62 * density;
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xCC141820);
        rf.set(x, y, x + w, y + 54 * density);
        c.drawRoundRect(rf, 12 * density, 12 * density, p);
        p.setTextAlign(Paint.Align.CENTER);
        p.setFakeBoldText(true);
        p.setTextSize(13 * density);
        p.setColor(0xFF35d6c0);
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
        if (ctxEnt != null && !pt.down) {
            if (action == UP) contextTap(x, y);
            return;
        }
        if (screens.screen != Screens.NONE && !pt.down && !(action == MOVE && pt.down)) {
            if (screens.touch(action, x, y, this)) return;
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
            pt.role = R_UI; pt.uiAction = a; pt.scrollStart = ui.scrollY;
            return;
        }
        Ptr other = firstOther(pt);
        if (other != null && other.grab == null) {
            cancelTool(other);
            other.role = R_CAM; pt.role = R_CAM;
            beginCam(other, pt);
            return;
        }
        // the hand is always on: a finger that lands on something waits to see if it drags (grab),
        // holds still (context menu) or lifts (the selected tool acts there)
        if (handActive() && entAt(x, y) != null) { pt.role = R_HOLD; return; }
        if (tool == Items.HAND) {
            pt.role = R_PAN;
            boolean dbl = clock - lastTapT < 0.32f && Math.abs(x - lastTapX) < 40 * density && Math.abs(y - lastTapY) < 40 * density;
            lastTapT = clock; lastTapX = x; lastTapY = y;
            if (dbl && follow != null) { follow = null; ui.toast("Cámara libre"); }
            return;
        }
        toolDown(pt, x, y);
    }

    /** Tools that act by dragging over things keep the finger for themselves; with the rest the hand grabs. */
    private boolean handActive() {
        return tool != Items.DELETE && tool != Items.CUT && tool != Items.FIRE && tool != Items.RIFLE;
    }

    private Ent entAt(float sx, float sy) {
        Shape s = world.sim.pick(toWorldX(sx), toWorldY(sy), 46f / zoom, false);
        return s != null && !s.body.isStatic && s.body.user instanceof Ent ? (Ent) s.body.user : null;
    }

    private void toolDown(Ptr pt, float x, float y) {
        pt.role = R_TOOL;
        float wx = toWorldX(x), wy = toWorldY(y);
        pt.fireCd = 0.09f;
        boolean instant = Items.isShot(tool) || tool == Items.LIGHTNING || tool == Items.DETONATE || tool == Items.NUKE || tool == Items.PUSH;
        if (instant && !useCharge()) return;
        switch (tool) {
            case Items.DELETE: deleteAt(wx, wy); break;
            case Items.FREEZE: freezeAt(wx, wy); break;
            case Items.FIRE: igniteAt(wx, wy); break;
            case Items.CUT: break;
            case Items.ROPE: case Items.WELD: case Items.HINGE: linkTap(wx, wy); break;
            case Items.ACTIVATE: activateAt(wx, wy); break;
            case Items.INSPECT: {
                Person h = pickPerson(wx, wy);
                inspected = h;
                if (inspected == null) ui.toast("Toca a una persona para inspeccionarla");
                break;
            }
            case Items.PISTOL: case Items.SHOTGUN: case Items.ROCKET: case Items.RIFLE:
                fire(tool, wx, wy); break;
            case Items.LIGHTNING: world.lightning(wx, wy); break;
            case Items.DETONATE: world.explode(wx, wy, 330f, 1900f, 95f, true); break;
            case Items.NUKE: world.explode(wx, Math.min(wy, floorY() - 60), 1400f, 4200f, 420f, true); break;
            case Items.PUSH: world.push(wx, wy); break;
            default:
                if (Items.isSyringe(tool)) {
                    Person h = pickPerson(wx, wy);
                    if (h == null) ui.toast("Toca a una persona");
                    else { ui.toast(world.inject(h, tool)); haptic(15); inspected = h; }
                }
                break; // spawn items are placed on release
        }
    }

    private Person pickPerson(float wx, float wy) {
        Shape s = world.sim.pick(wx, wy, 40f / zoom, false);
        return s != null && s.body.user instanceof Person ? (Person) s.body.user : null;
    }

    private void cancelTool(Ptr o) {
        if (o.grab != null) { world.sim.remove(o.grab); o.grab = null; }
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
                    if (pt.dragged) ui.scrollY = Math.max(0, Math.min(ui.maxScrollY(), pt.scrollStart - (y - pt.sy)));
                }
                break;
            case R_HOLD:
                if (pt.dragged) {
                    if (tryGrab(pt, pt.sx, pt.sy)) { pt.role = R_TOOL; pt.grab.tx = toWorldX(x); pt.grab.ty = toWorldY(y); }
                    else pt.role = R_NONE;
                }
                break;
            case R_PAN:
                if (pt.dragged) follow = null;
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
                else if (tool == Items.DELETE) deleteAt(wx, wy);
                else if (tool == Items.CUT) cutSegment(toWorldX(pt.lx), toWorldY(pt.ly), wx, wy);
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
            for (Ptr o : ptrs) if (o != pt && o.down && o.role == R_CAM) { o.role = R_PAN; o.lx = o.x; o.ly = o.y; }
            return;
        }
        if (role == R_HOLD) {
            if (cancelled) return;
            if (tool == Items.HAND) {
                // double tap on something: the camera follows it
                Ent e = entAt(x, y);
                boolean dbl = clock - lastTapT < 0.32f && Math.abs(x - lastTapX) < 40 * density && Math.abs(y - lastTapY) < 40 * density;
                lastTapT = clock; lastTapX = x; lastTapY = y;
                if (dbl && e != null && e != follow) { follow = e; ui.toast("Cámara siguiendo · doble toque en el fondo para soltar"); }
                return;
            }
            toolDown(pt, pt.sx, pt.sy);
            pt.role = R_NONE;
            if (Items.isSpawn(tool) && useCharge()) spawn(tool, toWorldX(x), toWorldY(y));
            return;
        }
        if (role == R_TOOL) {
            if (pt.grab != null) { world.sim.remove(pt.grab); pt.grab = null; }
            else if (!cancelled && Items.isSpawn(tool) && useCharge()) {
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

    /** A top bar button, as if tapped (PC keyboard shortcuts). */
    void pressButton(int b) { uiAction(Ui.BTN + b); }

    /** Opens/closes a category of the catalog (PC number keys). */
    void toggleCategory(int cat) { uiAction(Ui.TAB + cat); }

    /** Android back button / PC Escape. Returns false when there is nothing left to go back to. */
    boolean back() {
        if (screens.screen == Screens.NONE) {
            if (ctxEnt != null) { ctxEnt = null; return true; }
            if (ui.stripOpen) ui.stripOpen = false;
            else { screens.screen = Screens.PAUSE; saveScene(); }
            return true;
        }
        if (screens.screen != Screens.TITLE) {
            menuAction(screens.screen == Screens.PAUSE ? Screens.A_RESUME
                    : (screens.screen == Screens.RESULT ? Screens.A_MENU : Screens.A_BACK));
            return true;
        }
        return false;
    }

    /** Mouse wheel / pinch zoom around a screen point. */
    void zoomAt(float sx, float sy, float factor) {
        float wx = toWorldX(sx), wy = toWorldY(sy);
        float zMin = sw / (ROOM_W + 500f), zMax = sw / 380f;
        zoom = Math.max(zMin, Math.min(zMax, zoom * factor));
        camX = wx - (sx - sw / 2f) / zoom;
        camY = wy - (sy - sh / 2f) / zoom;
        follow = null;
        clampCam();
    }

    /** Right-button drag pans the camera (screen pixels). */
    void panBy(float dx, float dy) {
        camX -= dx / zoom; camY -= dy / zoom;
        follow = null;
        clampCam();
    }

    private void uiAction(int a) {
        click();
        if (a >= Ui.TAB && a < Ui.ITEM) {
            int c = a - Ui.TAB;
            if (ui.cat == c && ui.stripOpen) ui.stripOpen = false;
            else { ui.cat = c; ui.stripOpen = true; ui.scrollY = 0; }
        } else if (a >= Ui.ITEM && a < Ui.BTN) {
            Items.Item[] items = Items.CATS[ui.cat];
            int i = a - Ui.ITEM;
            if (i >= 0 && i < items.length) {
                int id = items[i].id;
                linkBody = null;
                if (id != Items.INSPECT && !Items.isSyringe(id) && id < Items.MOD_CREATE) inspected = null;
                if (id == Items.MOD_CREATE) { if (host != null) host.editMod(null); else ui.toast("Editor no disponible"); }
                else if (id >= Items.STYLE_BASE) { userTheme = id - Items.STYLE_BASE; ui.toast("Fondo: " + Items.THEMES[userTheme]); }
                else if (id >= Items.MOD_BASE) {
                    Mod m = mods.mods.get(id - Items.MOD_BASE);
                    mods.toggle(m, this);
                    ui.toast((m.on ? "Mod activado: " : "Mod desactivado: ") + m.name);
                } else if (id == tool && id != Items.HAND && challenge < 0) {
                    tool = Items.HAND;
                    ui.toast("Sin herramienta · la mano siempre está activa");
                } else {
                    tool = id;
                    ui.toast(items[i].name + " · " + items[i].hint);
                    ui.stripOpen = false;          // the catalog gets out of the way once something is picked
                }
            }
        } else if (a >= Ui.BTN) {
            switch (a - Ui.BTN) {
                case Ui.BTN_PAUSE: paused = !paused; ui.toast(paused ? "Pausa" : "Reanudado"); break;
                case Ui.BTN_MENU: screens.screen = Screens.PAUSE; saveScene(); break;
                case Ui.BTN_SLOW: slow = !slow; ui.toast(slow ? "Cámara lenta" : "Velocidad normal"); break;
                case Ui.BTN_GRAV:
                    gravMode = (gravMode + 1) % 3;
                    ui.toast(gravMode == 0 ? "Gravedad normal" : (gravMode == 1 ? "Gravedad lunar" : "Sin gravedad"));
                    break;
                case Ui.BTN_UNDO: undoLast(); break;
                case Ui.BTN_CLEAR:
                    if (confirmClear) { clearAll(); confirmClear = false; }
                    else { confirmClear = true; confirmT = 2.5f; }
                    break;
                case Ui.BTN_SOUND:
                    settings.muted = !settings.muted;
                    saveSettings();
                    ui.toast(settings.muted ? "Sonido desactivado" : "Sonido activado");
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
        ui.btnLabel[Ui.BTN_SOUND] = settings.muted ? "Mudo" : "Sonido";
        ui.btnLabel[Ui.BTN_MENU] = "Menú";
        ui.btnOn[Ui.BTN_SOUND] = settings.muted;
        ui.gravMode = gravMode;
    }


    String saveMod(Mod existing, String source) {
        String err = mods.save(existing, source);
        if (err == null) ui.toast("Mod guardado");
        return err;
    }

    void deleteMod(Mod m) { mods.delete(m); ui.toast("Mod borrado"); }

    private int tutBodies;
    private float tutZoom;

    private void tutorialProgress() {
        if (screens.screen != Screens.TUTORIAL) return;
        screens.tutT += lastFrameDt;
        boolean done;
        switch (screens.tutStep) {
            case 0: done = ui.stripOpen && ui.cat == 1; break;
            case 1: done = world.ents.size() > tutBodies; break;
            case 2: done = world.grabCount > 0 || Math.abs(zoom - tutZoom) > tutZoom * 0.15f; break;
            default: done = screens.tutT > 9f; break;
        }
        if (done) screens.nextTip(this);
    }

    private void undoLast() {
        while (!undo.isEmpty()) {
            Ent e = undo.remove(undo.size() - 1);
            if (!e.removed) { fx.smoke(e.cx(), e.cy(), 14, 0xFFaaaaaa); world.remove(e); return; }
        }
        ui.toast("Nada que deshacer");
    }

    private void clearAll() {
        clearWorld();
        ui.toast("Escenario limpio");
    }

    // ------------------------------------------------------------------------------- tools -----

    private boolean tryGrab(Ptr pt, float sx, float sy) {
        float wx = toWorldX(sx), wy = toWorldY(sy);
        Shape s = world.sim.pick(wx, wy, 46f / zoom, false);
        if (s == null) return false;
        RB b = s.body;
        if (b.isStatic) return false;
        float mass = b.m;
        if (b.user instanceof Ent) { mass = 0f; for (RB o : ((Ent) b.user).bodies) mass += o.m; }
        Joint j = Joint.mouse(world.sim.ground, b, wx, wy, Math.max(b.m, mass) * world.sim.gravity * 14f);
        world.sim.add(j);
        pt.grab = j;
        return true;
    }

    /** Anchor for the link tools: a point on a body (a wheel's centre when tapped near it) or a bolt in the air. */
    private boolean pickLink(float wx, float wy, RB[] bodyOut, float[] localOut) {
        Shape s = world.sim.pick(wx, wy, 30f / zoom, true);
        if (s == null) {
            Prop an = Prop.anchor(world.sim, wx, wy);
            world.add(an);
            undo.add(an);
            bodyOut[0] = an.main();
            localOut[0] = 0f; localOut[1] = 0f;
            return true;
        }
        RB b = s.body;
        float ax = wx, ay = wy;
        if (s.type == Shape.CIRCLE && b.shapes.size() == 1) {
            float dx = s.cx - wx, dy = s.cy - wy;
            if (dx * dx + dy * dy < (s.r * 0.75f) * (s.r * 0.75f)) { ax = s.cx; ay = s.cy; }
        }
        bodyOut[0] = b;
        localOut[0] = b.worldToLocalX(ax, ay); localOut[1] = b.worldToLocalY(ax, ay);
        return true;
    }

    private void linkTap(float wx, float wy) {
        RB[] bo = new RB[1];
        float[] lo = new float[2];
        pickLink(wx, wy, bo, lo);
        RB q = bo[0];
        if (linkBody == null) {
            linkBody = q; linkLx = lo[0]; linkLy = lo[1];
            ui.toast(tool == Items.ROPE ? "Ahora toca el otro extremo de la cuerda" : "Ahora toca la segunda pieza");
            click();
            return;
        }
        RB a = linkBody;
        float ax = a.localToWorldX(linkLx, linkLy), ay = a.localToWorldY(linkLx, linkLy);
        float bx = q.localToWorldX(lo[0], lo[1]), by = q.localToWorldY(lo[0], lo[1]);
        linkBody = null;
        if (a == q) { ui.toast("Elige piezas de dos objetos distintos"); return; }
        if (tool == Items.ROPE) {
            Ent r = world.rope(a, ax, ay, q, bx, by);
            if (r == null) { ui.toast("Límite de objetos alcanzado"); return; }
            undo.add(r);
            ui.toast("Cuerda creada");
        } else {
            world.link(a, ax, ay, q, bx, by, tool == Items.WELD);
            ui.toast(tool == Items.WELD ? "Soldado" : "Eje creado");
        }
        haptic(20);
        if (sfx != null) sfx.thud(0.4f);
    }

    private void deleteAt(float wx, float wy) {
        Shape s = world.sim.pick(wx, wy, 34f / zoom, true);
        if (s == null) return;
        Object u = s.body.user;
        if (u instanceof Ent) {
            Ent e = (Ent) u;
            fx.smoke(e.cx(), e.cy(), 12, 0xFFbbbbbb);
            fx.sparks(e.cx(), e.cy(), 6, 0xFFffffff);
            world.remove(e);
        }
    }

    private void freezeAt(float wx, float wy) {
        Shape s = world.sim.pick(wx, wy, 40f / zoom, false);
        if (s == null) return;
        RB b = s.body;
        b.setFrozen(!b.frozen);
        for (Ptr pt : ptrs) if (pt.grab != null && pt.grab.b == b) { world.sim.remove(pt.grab); pt.grab = null; }
        ui.toast(b.frozen ? "Pieza clavada" : "Pieza liberada");
    }

    private void igniteAt(float wx, float wy) {
        float rad = 36f / zoom;
        fx.flame(wx, wy, 9f);
        for (int i = 0; i < world.ents.size(); i++) {
            Ent e = world.ents.get(i);
            boolean hit = false;
            for (RB b : e.bodies) {
                for (Shape s : b.shapes) {
                    float dx = s.cx - wx, dy = s.cy - wy, r = s.reach() + rad;
                    if (dx * dx + dy * dy < r * r) { hit = true; break; }
                }
                if (hit) break;
            }
            if (hit) world.ignite(e);
        }
    }

    private void cutSegment(float x0, float y0, float x1, float y1) {
        if (dist(x0, y0, x1, y1) < 1f) return;
        fx.add(Fx.SPARK, x1, y1, fx.rr(60), fx.rr(60), 0.15f, 2f, 0xFFffffff);
        ArrayList<Joint> list = new ArrayList<>(world.sim.joints);
        for (Joint j : list) {
            if (j.kind == Joint.MOUSE || j.removed) continue;
            float jx = j.anchorBX(), jy = j.anchorBY();
            float d = pointSeg(jx, jy, x0, y0, x1, y1);
            if (d > 18f) continue;
            if (j.user instanceof Person) {
                Person per = (Person) j.user;
                if (j.tag >= 0 && j.tag < Person.NJOINTS && !per.severed[j.tag]) world.sever(per, j.tag, jx, jy);
            } else {
                world.sim.remove(j);
                fx.sparks(jx, jy, 8, 0xFFe8c48a);
                if (sfx != null) sfx.pop();
            }
        }
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
        float side = tx < camX ? 1f : -1f;
        float ox = tx - side * 230f, oy = ty + (weapon == Items.RIFLE ? fx.rr(6) : 0f);
        ox = Math.max(6f, Math.min(ROOM_W - 6f, ox));
        float dx = tx - ox, dy = ty - oy;
        if (Math.abs(dx) < 20) dx = side * 20;
        if (sfx != null && weapon != Items.ROCKET) sfx.shot();
        switch (weapon) {
            case Items.PISTOL: world.shoot(null, ox, oy, dx, dy, 24f, 950f, 1); break;
            case Items.RIFLE: world.shoot(null, ox, oy, dx, dy, 40f, 1600f, 3); break;
            case Items.SHOTGUN: world.shotgun(ox, oy, dx, dy); break;
            case Items.ROCKET: world.launchRocket(ox, oy, dx, dy, null); break;
            default: break;
        }
        fx.sparks(ox, oy, 3, 0xFFffd25a);
    }

    // ----------------------------------------------------------------------------- spawning -----

    void spawn(int id, float x, float y) {
        if (Items.isBuild(id)) {
            java.util.ArrayList<Ent> pieces = world.buildAt(id, x, y);
            if (pieces.isEmpty()) { if (!silentSpawn) ui.toast("Límite de objetos alcanzado"); return; }
            undo.addAll(pieces);
            return;
        }
        int dir = rnd.nextBoolean() ? 1 : -1;
        Ent e = world.spawn(id, x, y, dir, mods.hscale);
        if (e == null) { if (!silentSpawn) ui.toast("Límite de objetos alcanzado"); return; }
        undo.add(e);
        if (undo.size() > 80) undo.remove(0);
    }

    // -------------------------------------------------------------------------- activation ------

    private void activateAt(float wx, float wy) {
        Shape s = world.sim.pick(wx, wy, 34f / zoom, true);
        if (s == null || !(s.body.user instanceof Ent)) return;
        activate((Ent) s.body.user);
    }

    private void activate(Ent e) {
        if (e instanceof Person) {
            Person p = (Person) e;
            if (p.able && !p.dead) {
                for (RB b : p.rb) b.applyImpulse(0f, -b.m * 760f, b.x, b.y);
                ui.toast("¡Salta!");
            }
            click();
            return;
        }
        Prop q = (Prop) e;
        switch (q.type) {
            case Items.GUN_PISTOL: case Items.GUN_RIFLE:
                world.fireGun(q);
                break;
            case Items.GUN_MG:
                q.firing = !q.firing; q.fireCd = 0f;
                ui.toast(q.firing ? "Ametralladora: fuego" : "Ametralladora: alto");
                break;
            case Items.C4: {
                int n = world.detonateC4();
                ui.toast(n > 1 ? "¡Detonador! " + n + " cargas de C4" : "¡Detonador!");
                break;
            }
            case Items.WHEEL: case Items.MOTOR:
                q.motorized = true;
                q.motor = q.motor == 0 ? 1 : (q.motor == 1 ? -1 : 0);
                world.setMotor(q);
                ui.toast(q.motor == 0 ? "Motor apagado" : (q.motor == 1 ? "Motor: adelante →" : "Motor: atrás ←"));
                break;
            case Items.THRUSTER:
                q.thrust = !q.thrust;
                if (q.thrust) world.stats.thrusts++;
                ui.toast(q.thrust ? "Propulsor encendido" : "Propulsor apagado");
                break;
            case Items.TURRET: case Items.DRONE:
                if (q.dead) { ui.toast("Está averiado"); break; }
                q.on = !q.on;
                if (q.type == Items.DRONE && q.on) { q.homeX = q.cx(); q.goalX = q.cx(); q.goalY = q.cy(); }
                ui.toast((q.type == Items.TURRET ? "Torreta " : "Dron ") + (q.on ? "encendido" : "apagado"));
                break;
            default:
                if (q.explosive && q.fuse < 0f) { q.fuse = q.type == Items.GRENADE ? 3f : (q.type == Items.DYNAMITE ? 4f : 0.05f); ui.toast("¡Activado!"); }
                else if (q.explosive) q.fuse = Math.min(q.fuse, 0.05f);
                else if (q.type == Items.BALLOON) world.pop(q);
                else ui.toast("Esto no se puede activar");
        }
        click();
    }

    // ------------------------------------------------------------------------ context menu ----

    private void openContext(Ent e, float x, float y) {
        ctxEnt = e; ctxX = x; ctxY = y; ctxN = 0;
        boolean activatable = e instanceof Person ? ((Person) e).able : (((Prop) e).type == Items.WHEEL || ((Prop) e).type == Items.MOTOR
                || ((Prop) e).type == Items.THRUSTER || ((Prop) e).type == Items.GUN_PISTOL || ((Prop) e).type == Items.GUN_RIFLE
                || ((Prop) e).type == Items.GUN_MG || ((Prop) e).machine() || ((Prop) e).explosive || ((Prop) e).type == Items.BALLOON);
        if (activatable) ctxActs[ctxN++] = CX_ACTIVATE;
        if (e instanceof Person) ctxActs[ctxN++] = CX_INSPECT;
        ctxActs[ctxN++] = CX_DUPLICATE;
        ctxActs[ctxN++] = CX_PIN;
        ctxActs[ctxN++] = CX_DELETE;
        haptic(25);
        click();
    }

    private float ctxRadius() { return 78 * density; }

    private float[] ctxPos(int i) {
        double a = -Math.PI / 2 + (i - (ctxN - 1) / 2.0) * 0.95;
        float r = ctxRadius();
        float x = ctxX + (float) Math.cos(a) * r, y = ctxY + (float) Math.sin(a) * r;
        x = Math.max(36 * density, Math.min(sw - 36 * density, x));
        y = Math.max(36 * density, Math.min(sh - 36 * density, y));
        return new float[]{x, y};
    }

    private void contextTap(float x, float y) {
        Ent e = ctxEnt;
        ctxEnt = null;
        if (e == null || e.removed) return;
        for (int i = 0; i < ctxN; i++) {
            float[] q = ctxPos(i);
            if (dist(x, y, q[0], q[1]) > 32 * density) continue;
            click();
            switch (ctxActs[i]) {
                case CX_ACTIVATE: activate(e); break;
                case CX_INSPECT: if (e instanceof Person) inspected = (Person) e; break;
                case CX_DUPLICATE: duplicate(e); break;
                case CX_PIN: {
                    boolean any = false;
                    for (RB b : e.bodies) if (b.frozen) { b.setFrozen(false); any = true; }
                    if (!any) {
                        RB best = e.bodies.get(0);
                        float bd = Float.MAX_VALUE, wx = toWorldX(ctxX), wy = toWorldY(ctxY);
                        for (RB b : e.bodies) { float d = dist(b.x, b.y, wx, wy); if (d < bd) { bd = d; best = b; } }
                        best.setFrozen(true);
                    }
                    ui.toast(any ? "Liberado" : "Clavado");
                    break;
                }
                default:
                    fx.smoke(e.cx(), e.cy(), 12, 0xFFbbbbbb);
                    world.remove(e);
            }
            return;
        }
    }

    private static final String[] CX_NAMES = {"", "Activar", "Inspeccionar", "Duplicar", "Clavar", "Borrar"};
    private static final int[] CX_COLORS = {0, 0xFF3fbf6b, 0xFF3aa0e0, 0xFFb06ad8, 0xFF8a9099, 0xFFe0463c};

    private void drawContext(Canvas c) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x55000000);
        c.drawRect(0, 0, sw, sh, p);
        p.setColor(0xCCffffff);
        c.drawCircle(ctxX, ctxY, 6 * density, p);
        for (int i = 0; i < ctxN; i++) {
            float[] q = ctxPos(i);
            int act = ctxActs[i];
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2f * density);
            p.setColor(0x88ffffff);
            c.drawLine(ctxX, ctxY, q[0], q[1], p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xF01b2029);
            c.drawCircle(q[0], q[1], 30 * density, p);
            p.setColor(CX_COLORS[act]);
            c.drawCircle(q[0], q[1], 26 * density, p);
            p.setColor(0xFFffffff);
            p.setTextAlign(Paint.Align.CENTER);
            p.setFakeBoldText(true);
            p.setTextSize(9.5f * density);
            boolean pinned = false;
            if (ctxEnt != null) for (RB b : ctxEnt.bodies) if (b.frozen) pinned = true;
            String label = act == CX_PIN && pinned ? "Soltar" : CX_NAMES[act];
            c.drawText(label, q[0], q[1] + 3.5f * density, p);
            p.setFakeBoldText(false);
        }
    }

    /** Copy of an entity placed next to the original. */
    private void duplicate(Ent e) {
        float off = Math.min(220f, e.radius() * 1.6f + 30f);
        if (e.cx() + off > ROOM_W - 60) off = -off;
        Ent c = world.spawn(e.item, e.cx() + off, Math.min(e.cy(), floorY() - 60f), e instanceof Person ? ((Person) e).dir : 1, 1f);
        if (c == null) { ui.toast("Esto no se puede duplicar"); return; }
        if (e instanceof Person && c instanceof Person) {
            Person a = (Person) e, b = (Person) c;
            b.skin = a.skin; b.shirt = a.shirt; b.pants = a.pants; b.hair = a.hair;
            world.recolor(b);
        }
        undo.add(c);
        ui.toast("Duplicado");
    }

    // -------------------------------------------------------------------------- inspection -----

    private void drawInspection(Canvas c) {
        Person b = inspected;
        float d = density, x = ui.infoX(), y = 74 * d, w = Math.min(250 * d, sw * 0.32f);
        float rowH = Math.min(18 * d, (sh - y - 64 * d) / 18.4f);
        float h = rowH * 18.2f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xE6141820);
        rf.set(x, y, x + w, y + h);
        c.drawRoundRect(rf, 12 * d, 12 * d, p);
        String kind = kindName(b.kind);
        String state = b.dead ? "Muerto" : (b.ko > 0 ? "Inconsciente" : (b.legsBroken() ? "En el suelo (fractura)" : "Consciente"));
        if (b.infected > 0) state += " · infectado";
        if (b.adrenaline > 0) state += " · adrenalina";
        if (b.acid > 0) state += " · ácido";
        if (b.burning) state += " · ardiendo";
        p.setTextAlign(Paint.Align.LEFT);
        p.setFakeBoldText(true);
        p.setTextSize(rowH * 0.78f);
        p.setColor(0xFF35d6c0);
        c.drawText(kind, x + 10 * d, y + rowH * 1.05f, p);
        p.setFakeBoldText(false);
        p.setTextSize(rowH * 0.62f);
        p.setColor(b.dead ? 0xFFe0463c : 0xFFd5dbe6);
        c.drawText(state, x + 10 * d, y + rowH * 1.95f, p);
        if (!b.dead && b.mood != null && !b.mood.isEmpty()) {
            p.setColor(0xFFf2b134);
            c.drawText("IA: " + b.mood, x + 10 * d, y + rowH * 2.75f, p);
        }
        float by = y + rowH * 3.4f;
        bar(c, "Sangre", Math.max(0, b.blood) / b.maxBlood, null, x, by, w, rowH);
        for (int i = 0; i < Person.NPARTS; i++) {
            int jn = Person.PART_JOINT[i];
            boolean cut = jn >= 0 && b.severed[jn];
            float v = Math.max(0f, b.hp[i] / Person.maxHp(b, i));
            String tag = cut ? "Amputado" : (b.broken[i] ? "Fractura" : (b.bleed[i] > 0 ? "Sangra" : null));
            bar(c, Person.PART_NAMES[i], cut ? -1 : v, tag, x, by + rowH * (i + 1), w, rowH);
        }
    }

    static String kindName(int k) {
        switch (k) {
            case Person.ZOMBIE: return "Zombi";
            case Person.ROBOT: return "Robot";
            case Person.SKELETON: return "Esqueleto";
            case Person.SOLDIER: return "Soldado";
            case Person.MEDIC: return "Médico";
            case Person.FIREFIGHTER: return "Bombero";
            case Person.THUG: return "Matón";
            default: return "Civil";
        }
    }

    private void bar(Canvas c, String label, float v, String tag, float x, float y, float w, float rowH) {
        float d = density;
        p.setTextAlign(Paint.Align.LEFT);
        p.setTextSize(rowH * 0.56f);
        p.setColor(0xFFb9c2d0);
        c.drawText(label, x + 10 * d, y + rowH * 0.68f, p);
        float bx = x + w * 0.5f, bw = w * 0.5f - 12 * d, bh = rowH * 0.36f, top = y + rowH * 0.32f;
        p.setColor(0xFF2a3142);
        rf.set(bx, top, bx + bw, top + bh);
        c.drawRoundRect(rf, bh / 2, bh / 2, p);
        if (v >= 0) {
            int col = v > 0.6f ? 0xFF4cbf6b : (v > 0.3f ? 0xFFf2b134 : 0xFFe0463c);
            p.setColor(col);
            rf.set(bx, top, bx + bw * Math.max(0.03f, Math.min(1f, v)), top + bh);
            c.drawRoundRect(rf, bh / 2, bh / 2, p);
        }
        if (tag != null) {
            p.setTextAlign(Paint.Align.RIGHT);
            p.setTextSize(rowH * 0.5f);
            p.setColor(tag.equals("Sangra") ? 0xFFff6b6b : (tag.equals("Fractura") ? 0xFFd88cff : 0xFF9aa0a8));
            c.drawText(tag, bx - 4 * d, y + rowH * 0.68f, p);
        }
    }
}

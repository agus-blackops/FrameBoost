package com.frameboost.ragdoll;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

/** Menus drawn over the live world: title, pause, settings, challenge list, results, tutorial. */
final class Screens {
    static final int NONE = 0, TITLE = 1, PAUSE = 2, SETTINGS = 3, CHALLENGES = 4, RESULT = 5, TUTORIAL = 6, ACHIEVEMENTS = 7, SPLASH = 8;
    static final int A_CONTINUE = 1, A_SANDBOX = 2, A_CHALLENGES = 3, A_SETTINGS = 4, A_RESUME = 5, A_MENU = 6,
            A_RESTART = 7, A_BACK = 8, A_MUSIC_DN = 9, A_MUSIC_UP = 10, A_SFX_DN = 11, A_SFX_UP = 12, A_HAPTICS = 13,
            A_GORE = 14, A_QUALITY = 15, A_FPS = 16, A_TEXTURES = 17, A_NEXT = 18, A_RETRY = 19, A_TUT = 20, A_ACH = 21, A_ENGINE = 22, A_CHAL = 100;

    int screen = TITLE, back = TITLE, tutStep;
    float time, tutT;
    private final RectF tutBox = new RectF();
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float[] bx = new float[64], by = new float[64], bw = new float[64], bh = new float[64];
    private final int[] ba = new int[64];
    private int bn, pressed = -1;
    private float d = 1f;

    static final String[] TUT = {
        "Abre la categoría \"Gente\" en la barra de la izquierda.",
        "Elige \"Humano\" y toca el escenario para colocarlo. Se mantendrá en pie... hasta que algo lo tumbe.",
        "Arrastra a alguien con el dedo: la mano siempre está activa, elijas lo que elijas. Con dos dedos, zoom.",
        "Prueba Máquinas, Animales y Explosivos, abre un refrigerador y mira el Taller de mods. ¡A experimentar!",
    };

    // ------------------------------------------------------------------------------- input ----

    /** Returns true when the touch belongs to a menu. */
    boolean touch(int action, float x, float y, Game g) {
        if (screen == NONE) return false;
        if (screen == SPLASH) {                         // tap skips to the next card
            if (action == Game.UP) {
                float card = splashCard(g);
                if (card < 1f && g.settings.gpu) time = SPLASH_CARD; else { screen = TITLE; time = 0; }
            }
            return true;
        }
        if (screen == TUTORIAL) {
            boolean inside = x >= tutBox.left && x <= tutBox.right && y >= tutBox.top && y <= tutBox.bottom;
            if (inside && action == Game.UP) { g.click(); nextTip(g); }
            return inside;    // everything else goes to the game: the tips ask you to try things
        }
        int hit = -1;
        for (int i = 0; i < bn; i++) if (x >= bx[i] && x <= bx[i] + bw[i] && y >= by[i] && y <= by[i] + bh[i]) hit = i;
        if (action == Game.DOWN) pressed = hit;
        else if (action == Game.UP) {
            if (hit >= 0 && hit == pressed) { g.click(); g.menuAction(ba[hit]); }
            pressed = -1;
        } else if (action == Game.CANCEL) pressed = -1;
        return true;
    }

    void nextTip(Game g) {
        tutStep++;
        tutT = 0;
        if (tutStep >= TUT.length) { screen = NONE; g.settings.tutorialDone = true; g.saveSettings(); }
    }

    // ----------------------------------------------------------------------------- drawing ----

    void draw(Canvas c, Game g, float dt) {
        if (screen != SPLASH) time += dt;   // the splash is timed by the game loop (tickSplash)
        d = g.density;
        bn = 0;
        int sw = g.sw, sh = g.sh;
        switch (screen) {
            case TITLE: title(c, g, sw, sh); break;
            case PAUSE: dim(c, sw, sh, 0xAA0b0e14); panel(c, g, "Pausa", sw, sh,
                    new String[]{"Reanudar", g.challenge >= 0 ? "Reiniciar desafío" : "Ajustes", g.challenge >= 0 ? "Ajustes" : null, "Menú principal"},
                    new int[]{A_RESUME, g.challenge >= 0 ? A_RESTART : A_SETTINGS, A_SETTINGS, A_MENU}); break;
            case SETTINGS: settings(c, g, sw, sh); break;
            case CHALLENGES: challenges(c, g, sw, sh); break;
            case RESULT: result(c, g, sw, sh); break;
            case TUTORIAL: tutorial(c, g, sw, sh); break;
            case ACHIEVEMENTS: achievements(c, g, sw, sh); break;
            case SPLASH: splash(c, g, sw, sh); break;
            default: break;
        }
    }

    // ------------------------------------------------------------------------- start-up cards ----

    static final float SPLASH_CARD = 1.9f;

    void tickSplash(Game g, float dt) {
        if (screen != SPLASH) return;
        time += dt;
        if (splashCard(g) >= 2f) { screen = TITLE; time = 0; }
    }

    /** 0..1 = "Made with OpenGL", 1..2 = "Made with Opus 5.5" (the OpenGL card only on the GPU engine). */
    private float splashCard(Game g) {
        float t = time / SPLASH_CARD;
        return g.settings.gpu ? t : t + 1f;
    }

    private void splash(Canvas c, Game g, int sw, int sh) {
        float card = splashCard(g);
        if (card >= 2f) { screen = TITLE; time = 0; return; }
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF0b0e14);
        c.drawRect(0, 0, sw, sh, p);
        int idx = (int) card;
        float k = card - idx;                                   // 0..1 inside this card
        float a = Math.min(1f, Math.min(k / 0.18f, (1f - k) / 0.18f));
        a = Math.max(0f, a);
        float scale = 0.94f + 0.06f * (float) Math.sin(Math.min(1f, k * 1.6f) * Math.PI / 2);
        float cx = sw / 2f, cy = sh / 2f;
        c.save();
        c.translate(cx, cy);
        c.scale(scale, scale);
        float big = Math.min(64 * d, sw * 0.09f);
        // "MADE WITH", spaced capitals
        p.setTextAlign(Paint.Align.CENTER);
        p.setFakeBoldText(true);
        p.setTextSize(13 * d);
        p.setColor(Renderer.alpha(0xFF8b93a7, a));
        c.drawText("M A D E   W I T H", 0, -big * 0.95f, p);
        if (idx == 0) {
            // glowing concentric rings, like a shader warming up
            p.setStyle(Paint.Style.STROKE);
            for (int i = 0; i < 4; i++) {
                float ph = (time * 0.9f + i * 0.25f) % 1f;
                p.setStrokeWidth(2f * d);
                p.setColor(Renderer.alpha(0xFF5b8cff, a * (1f - ph) * 0.5f));
                c.drawCircle(0, -big * 0.3f, big * (1.2f + ph * 2.4f), p);
            }
            p.setStyle(Paint.Style.FILL);
            p.setTextSize(big * 1.15f);
            float wOpen = p.measureText("Open"), wGL = p.measureText("GL");
            float x0 = -(wOpen + wGL) / 2;
            p.setTextAlign(Paint.Align.LEFT);
            p.setColor(Renderer.alpha(0xFFffffff, a));
            c.drawText("Open", x0, big * 0.12f, p);
            p.setColor(Renderer.alpha(0xFF5b8cff, a));
            c.drawText("GL", x0 + wOpen, big * 0.12f, p);
            p.setTextAlign(Paint.Align.CENTER);
            p.setFakeBoldText(false);
            p.setTextSize(13 * d);
            p.setColor(Renderer.alpha(0xFFb9c2d0, a));
            c.drawText("ES 2.0  ·  shaders  ·  iluminación dinámica  ·  bloom", 0, big * 0.12f + 34 * d, p);
        } else {
            // a four-point sparkle next to the name
            p.setStyle(Paint.Style.FILL);
            p.setTextSize(big * 1.15f);
            float w = p.measureText("Opus 5.5");
            float sx = -w / 2 - big * 0.55f, sy = -big * 0.28f, r = big * 0.36f * (0.85f + 0.15f * (float) Math.sin(time * 6f));
            p.setColor(Renderer.alpha(0xFFe8846a, a));
            float[] pts = new float[16];
            for (int i = 0; i < 8; i++) {
                double ang = -Math.PI / 2 + i * Math.PI / 4;
                float rr = (i & 1) == 0 ? r : r * 0.28f;
                pts[i * 2] = sx + (float) Math.cos(ang) * rr;
                pts[i * 2 + 1] = sy + (float) Math.sin(ang) * rr;
            }
            android.graphics.Path path = new android.graphics.Path();
            path.moveTo(pts[0], pts[1]);
            for (int i = 1; i < 8; i++) path.lineTo(pts[i * 2], pts[i * 2 + 1]);
            path.close();
            c.drawPath(path, p);
            p.setTextAlign(Paint.Align.LEFT);
            p.setColor(Renderer.alpha(0xFFffffff, a));
            c.drawText("Opus 5.5", -w / 2 + big * 0.1f, big * 0.12f, p);
            p.setTextAlign(Paint.Align.CENTER);
            p.setFakeBoldText(false);
            p.setTextSize(13 * d);
            p.setColor(Renderer.alpha(0xFFb9c2d0, a));
            c.drawText("código  ·  física  ·  arte  ·  música", 0, big * 0.12f + 34 * d, p);
        }
        p.setFakeBoldText(false);
        c.restore();
        // thin progress line at the bottom
        p.setColor(Renderer.alpha(0xFF2a3142, 1f));
        c.drawRect(sw * 0.4f, sh - 28 * d, sw * 0.6f, sh - 26 * d, p);
        p.setColor(0xFF2fd0b5);
        float prog = g.settings.gpu ? card / 2f : (card - 1f);
        c.drawRect(sw * 0.4f, sh - 28 * d, sw * 0.4f + sw * 0.2f * Math.min(1f, prog), sh - 26 * d, p);
    }

    private void dim(Canvas c, int sw, int sh, int col) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(col);
        c.drawRect(0, 0, sw, sh, p);
    }

    private void button(Canvas c, float x, float y, float w, float h, String label, int action, boolean primary) {
        button(c, x, y, w, h, label, action, primary, 0);
    }

    static final int IC_PLAY = 1, IC_CONTINUE = 2, IC_STAR = 3, IC_TROPHY = 4, IC_GEAR = 5;

    /** Small vector icons for the menu buttons. */
    private void icon(Canvas c, int ic, float cx, float cy, float s, int col) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(col);
        android.graphics.Path path = new android.graphics.Path();
        switch (ic) {
            case IC_PLAY:
                path.moveTo(cx - s * 0.45f, cy - s * 0.6f); path.lineTo(cx + s * 0.6f, cy); path.lineTo(cx - s * 0.45f, cy + s * 0.6f); path.close();
                c.drawPath(path, p);
                break;
            case IC_CONTINUE:
                path.moveTo(cx - s * 0.6f, cy - s * 0.55f); path.lineTo(cx + s * 0.1f, cy); path.lineTo(cx - s * 0.6f, cy + s * 0.55f); path.close();
                path.moveTo(cx - s * 0.05f, cy - s * 0.55f); path.lineTo(cx + s * 0.65f, cy); path.lineTo(cx - s * 0.05f, cy + s * 0.55f); path.close();
                c.drawPath(path, p);
                break;
            case IC_STAR:
                for (int i = 0; i < 10; i++) {
                    double a = -Math.PI / 2 + i * Math.PI / 5;
                    float rr = (i & 1) == 0 ? s * 0.7f : s * 0.3f;
                    float x = cx + (float) Math.cos(a) * rr, y = cy + (float) Math.sin(a) * rr;
                    if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
                }
                path.close();
                c.drawPath(path, p);
                break;
            case IC_TROPHY:
                r.set(cx - s * 0.45f, cy - s * 0.65f, cx + s * 0.45f, cy + s * 0.15f); c.drawRoundRect(r, s * 0.25f, s * 0.25f, p);
                c.drawRect(cx - s * 0.08f, cy + s * 0.1f, cx + s * 0.08f, cy + s * 0.4f, p);
                c.drawRect(cx - s * 0.35f, cy + s * 0.4f, cx + s * 0.35f, cy + s * 0.6f, p);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(s * 0.13f);
                c.drawCircle(cx - s * 0.5f, cy - s * 0.3f, s * 0.2f, p); c.drawCircle(cx + s * 0.5f, cy - s * 0.3f, s * 0.2f, p);
                break;
            case IC_GEAR:
                c.drawCircle(cx, cy, s * 0.45f, p);
                for (int k = 0; k < 8; k++) {
                    double a = k * Math.PI / 4;
                    c.drawCircle(cx + (float) Math.cos(a) * s * 0.55f, cy + (float) Math.sin(a) * s * 0.55f, s * 0.15f, p);
                }
                break;
            default: break;
        }
        p.setStyle(Paint.Style.FILL);
    }

    private void button(Canvas c, float x, float y, float w, float h, String label, int action, boolean primary, int ic) {
        int i = bn++;
        bx[i] = x; by[i] = y; bw[i] = w; bh[i] = h; ba[i] = action;
        boolean down = pressed == i;
        float o = down ? 2 * d : 0;
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x66000000);
        r.set(x, y + 4 * d, x + w, y + h + 4 * d);
        c.drawRoundRect(r, 14 * d, 14 * d, p);
        p.setColor(primary ? (down ? 0xFFd99a22 : 0xFF2fd0b5) : (down ? 0xFF2c3446 : 0xFF394459));
        r.set(x, y + o, x + w, y + h + o);
        c.drawRoundRect(r, 14 * d, 14 * d, p);
        if (ic != 0) {
            // a red stripe and the icon on the left of the menu buttons
            p.setColor(primary ? 0x33000000 : 0x22ffffff);
            r.set(x, y + o, x + h, y + h + o);
            c.drawRoundRect(r, 14 * d, 14 * d, p);
            icon(c, ic, x + h * 0.5f, y + h * 0.5f + o, h * 0.3f, primary ? 0xFF1b2029 : 0xFFe0463c);
        }
        p.setColor(primary ? 0xFF1b2029 : 0xFFeef2f8);
        p.setTextAlign(Paint.Align.CENTER);
        p.setFakeBoldText(true);
        p.setTextSize(Math.min(17 * d, h * 0.42f));
        c.drawText(label, x + w / 2 + (ic != 0 ? (w > h * 3.5f ? h * 0.4f : h * 0.5f) : 0f), y + h / 2 + p.getTextSize() * 0.36f + o, p);
        p.setFakeBoldText(false);
    }

    private void text(Canvas c, String s, float x, float y, float size, int col, Paint.Align al, boolean bold) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(col);
        p.setTextSize(size);
        p.setTextAlign(al);
        p.setFakeBoldText(bold);
        c.drawText(s, x, y, p);
        p.setFakeBoldText(false);
    }

    private void logo(Canvas c, float cx, float cy, float size) {
        String word = "TRASTAZO";
        p.setTextSize(size);
        p.setFakeBoldText(true);
        p.setTextAlign(Paint.Align.LEFT);
        float total = p.measureText(word) + size * 0.04f * (word.length() - 1);
        float x = cx - total / 2;
        int[] cols = {0xFF2fd0b5, 0xFFe0463c, 0xFF3aa0e0, 0xFF4cbf6b, 0xFF2fd0b5, 0xFFe0463c, 0xFF3aa0e0, 0xFF4cbf6b};
        for (int i = 0; i < word.length(); i++) {
            String ch = word.substring(i, i + 1);
            float w = p.measureText(ch);
            float bob = (float) Math.sin(time * 2.6f + i * 0.7f) * size * 0.05f;
            float rot = (float) Math.sin(time * 1.9f + i * 1.3f) * 5f;
            c.save();
            c.translate(x + w / 2, cy + bob);
            c.rotate(rot);
            p.setColor(0xFF0b0e14);
            c.drawText(ch, -w / 2 + size * 0.05f, size * 0.07f, p);
            p.setColor(cols[i]);
            c.drawText(ch, -w / 2, 0, p);
            c.restore();
            x += w + size * 0.04f;
        }
        p.setFakeBoldText(false);
    }

    /** What is new, on the title screen's card. */
    static final String[] NEWS = {
        "Edificio y rascacielos + destrucción tipo The Finals",
        "Más trampas: mina, guillotina, lanzallamas, láser",
        "Hombre óxido y barra de óxido",
        "Modelo de humanos V3 renovado",
        "Sangre V6 remake: más real",
        "Gore V6: realista: coágulos, palidez, espasmos",
        "Trampas de SAW: cepo, péndulo, Billy...",
        "Destruction Engine 1.5: cristal y colapsos",
        "5 construcciones: castillo, pirámide, dominó...",
        "Texturas nuevas para personas y objetos",
    };

    private void title(Canvas c, Game g, int sw, int sh) {
        p.setStyle(Paint.Style.FILL);
        // darken the left side where the menu sits, the live demo stays visible on the right
        for (int i = 0; i < 14; i++) {
            p.setColor(Renderer.alpha(0xFF07090d, 0.9f * (1f - i / 14f)));
            c.drawRect(i * sw * 0.045f, 0, (i + 1) * sw * 0.045f, sh, p);
        }
        for (int i = 0; i < 8; i++) {                    // a dark red glow from the top
            p.setColor(Renderer.alpha(0xFF5a0a10, 0.18f * (1f - i / 8f)));
            c.drawRect(0, i * sh * 0.03f, sw, (i + 1) * sh * 0.03f, p);
        }
        // a spotlight from above on the scene, like a poster
        {
            float lx = sw * 0.66f;
            for (int i = 0; i < 4; i++) {
                p.setColor(Renderer.alpha(0xFFfff2d0, 0.028f));
                android.graphics.Path cone = new android.graphics.Path();
                float top = 6 * d, wTop = (10 + i * 5) * d, wBot = (sw * 0.16f) + i * sw * 0.035f;
                cone.moveTo(lx - wTop, top); cone.lineTo(lx + wTop, top); cone.lineTo(lx + wBot, sh * 0.8f); cone.lineTo(lx - wBot, sh * 0.8f); cone.close();
                c.drawPath(cone, p);
            }
            p.setColor(0xFF1b1f27); c.drawRect(lx - 24 * d, 0, lx + 24 * d, 10 * d, p);
            p.setColor(Renderer.alpha(0xFFfff2d0, 0.9f)); c.drawRect(lx - 14 * d, 8 * d, lx + 14 * d, 12 * d, p);
        }
        // blood running down from the top edge
        p.setColor(0xFF7d0a10);
        c.drawRect(0, 0, sw, 5 * d, p);
        for (int i = 0; i < 22; i++) {
            float x = sw * (0.015f + i * 0.047f) + (i * 37 % 11) * d;
            float ph = (time * 0.07f + i * 0.37f) % 1f;
            float maxLen = sh * (0.06f + (i * 53 % 10) / 10f * 0.22f);
            float len = 5 * d + ph * maxLen, wdt = (2.5f + i % 3) * d;
            p.setColor(i % 3 == 0 ? 0xFF8e1218 : 0xFF6e0a10);
            c.drawRect(x - wdt / 2, 0, x + wdt / 2, len, p);
            c.drawCircle(x, len, wdt * 0.9f, p);
            c.drawCircle(x, 5 * d, wdt * 1.4f, p);
        }
        float colX = 40 * d, colW = Math.min(sw * 0.4f, 380 * d);
        float logoY = sh * 0.2f, logoSize = Math.min(70 * d, colW / 5f);
        logo(c, colX + colW / 2, logoY, logoSize);
        {   // edition ribbon: "UNITY 1.3" on the Unity build, "BETA 4.0" elsewhere
            String tag = Game.edition != null ? Game.edition.toUpperCase() : "BETA " + Report.VERSION;
            p.setTextSize(12 * d);
            p.setFakeBoldText(true);
            float bw = p.measureText(tag) + 22 * d, bh = 22 * d, bx = colX + colW - bw, by = Math.max(14 * d, logoY - logoSize - 18 * d);
            p.setFakeBoldText(false);
            p.setColor(Game.edition != null ? 0xFFc0121b : 0xFFf2b134);
            r.set(bx, by, bx + bw, by + bh);
            c.drawRoundRect(r, 8 * d, 8 * d, p);
            text(c, tag, bx + bw / 2, by + bh * 0.72f, 12 * d, Game.edition != null ? 0xFFffffff : 0xFF1b2029, Paint.Align.CENTER, true);
        }
        // "PLAYGROUND" in spaced capitals under the logo, between two red bars
        float pgY = logoY + logoSize * 0.52f, pgS = Math.max(12 * d, logoSize * 0.27f);
        p.setTextSize(pgS); p.setFakeBoldText(true);
        String pg = "P L A Y G R O U N D";
        float pgW = p.measureText(pg);
        p.setFakeBoldText(false);
        text(c, pg, colX + colW / 2, pgY, pgS, 0xFF2fd0b5, Paint.Align.CENTER, true);
        p.setColor(0xFFc0121b);
        c.drawRect(colX + colW / 2 - pgW / 2 - 34 * d, pgY - pgS * 0.38f, colX + colW / 2 - pgW / 2 - 10 * d, pgY - pgS * 0.38f + 3 * d, p);
        c.drawRect(colX + colW / 2 + pgW / 2 + 10 * d, pgY - pgS * 0.38f, colX + colW / 2 + pgW / 2 + 34 * d, pgY - pgS * 0.38f + 3 * d, p);
        text(c, "laboratorio de muñecos, trampas y caos", colX + colW / 2, pgY + 26 * d, 14 * d, 0xFFb9c2d0, Paint.Align.CENTER, false);
        if (Report.lastCrash != null) text(c, "La última vez el juego se cerró por un error. Informe: " + Report.lastCrash, colX, sh - 52 * d, 10 * d, 0xFFf2b134, Paint.Align.LEFT, false);
        float y = sh * 0.37f, bh = Math.min(52 * d, sh * 0.105f), gap = Math.min(11 * d, sh * 0.022f);
        if (g.hasSave()) { button(c, colX, y, colW, bh, "Continuar", A_CONTINUE, true, IC_CONTINUE); y += bh + gap; }
        button(c, colX, y, colW, bh, "Jugar: sandbox libre", A_SANDBOX, !g.hasSave(), IC_PLAY); y += bh + gap;
        button(c, colX, y, colW, bh, "Desafíos   " + g.settings.totalStars() + "/" + Challenges.LIST.length * 3, A_CHALLENGES, false, IC_STAR); y += bh + gap;
        float half = (colW - gap) / 2;
        button(c, colX, y, half, bh, "Logros " + g.achievements.count(g.settings) + "/" + Achievements.LIST.length, A_ACH, false, IC_TROPHY);
        button(c, colX + half + gap, y, half, bh, "Ajustes", A_SETTINGS, false, IC_GEAR);
        // what's new: one line at the bottom, so the cover scene stays clear
        {
            String news = "Novedades: Sangre V6 · Gore V6 · Hombre óxido · modelo V3 · trampas de SAW";
            p.setTextSize(11 * d);
            float nw = p.measureText(news), maxN = sw - colX - 24 * d;
            float ns = nw > maxN ? 11 * d * maxN / nw : 11 * d;
            text(c, news, colX, sh - 42 * d, ns, 0xFFe0463c, Paint.Align.LEFT, true);
        }
        String plat = Game.edition != null ? Game.edition : (Game.desktop ? "versión PC" : "Android");
        text(c, "Trastazo Playground · v" + Report.VERSION + " · " + plat, colX, sh - 16 * d, 11 * d, 0x88ffffff, Paint.Align.LEFT, false);
        if (Game.desktop) text(c, "Ratón: arrastra y toca · clic derecho: mover cámara · rueda: zoom · Esc: menú · Espacio: pausa · 1-9: categorías · F11: pantalla completa", colX, sh - 32 * d, 10 * d, 0x88ffffff, Paint.Align.LEFT, false);
    }

    private void panel(Canvas c, Game g, String title, int sw, int sh, String[] labels, int[] acts) {
        int n = 0;
        for (String l : labels) if (l != null) n++;
        float bh = Math.min(52 * d, sh * 0.11f), gap = 12 * d;
        float w = Math.min(380 * d, sw * 0.6f), h = 70 * d + n * (bh + gap);
        float x = (sw - w) / 2, y = (sh - h) / 2;
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xF01b2029);
        r.set(x, y, x + w, y + h);
        c.drawRoundRect(r, 20 * d, 20 * d, p);
        text(c, title, sw / 2f, y + 42 * d, 24 * d, 0xFF2fd0b5, Paint.Align.CENTER, true);
        float by0 = y + 64 * d;
        for (int i = 0; i < labels.length; i++) {
            if (labels[i] == null) continue;
            button(c, x + 24 * d, by0, w - 48 * d, bh, labels[i], acts[i], i == 0);
            by0 += bh + gap;
        }
    }

    private void settings(Canvas c, Game g, int sw, int sh) {
        dim(c, sw, sh, 0xCC0b0e14);
        Settings s = g.settings;
        float w = Math.min(560 * d, sw * 0.86f), x = (sw - w) / 2;
        float rowH = Math.min(46 * d, (sh - 110 * d) / 6f), panelH = 56 * d + rowH * 6 + 14 * d;
        float y = Math.max(8 * d, (sh - panelH) / 2);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xF01b2029);
        r.set(x, y, x + w, y + panelH);
        c.drawRoundRect(r, 20 * d, 20 * d, p);
        text(c, "Ajustes", sw / 2f, y + 38 * d, 22 * d, 0xFF2fd0b5, Paint.Align.CENTER, true);
        float ry = y + 56 * d, lx = x + 24 * d, cw = w - 48 * d;
        ry = slider(c, "Música", s.music, lx, ry, cw, rowH, A_MUSIC_DN, A_MUSIC_UP);
        ry = slider(c, "Efectos", s.sfx, lx, ry, cw, rowH, A_SFX_DN, A_SFX_UP);
        float half = (cw - 12 * d) / 2;
        button(c, lx, ry, half, rowH * 0.86f, Game.desktop ? "Pantalla completa: " + (g.fullscreen ? "Sí" : "No") : "Vibración: " + (s.haptics ? "Sí" : "No"), A_HAPTICS, false);
        button(c, lx + half + 12 * d, ry, half, rowH * 0.86f, "Sangre: " + (s.gore ? "Sí" : "No"), A_GORE, false);
        ry += rowH;
        button(c, lx, ry, half, rowH * 0.86f, "Calidad: " + (s.highQuality ? "Alta" : "Ahorro"), A_QUALITY, false);
        button(c, lx + half + 12 * d, ry, half, rowH * 0.86f, "Texturas: " + (g.textures ? "Sí" : "No"), A_TEXTURES, false);
        ry += rowH;
        button(c, lx, ry, half, rowH * 0.86f, "Mostrar FPS: " + (s.showFps ? "Sí" : "No"), A_FPS, false);
        button(c, lx + half + 12 * d, ry, half, rowH * 0.86f, "Motor: " + (Game.desktop ? "PC (Java2D)" : (s.gpu ? "GPU" : "Canvas")), A_ENGINE, false);
        ry += rowH;
        button(c, lx + cw / 4, ry, cw / 2, rowH * 0.86f, "Volver", A_BACK, true);
    }

    private float slider(Canvas c, String label, float v, float x, float y, float w, float h, int dn, int up) {
        text(c, label, x, y + h * 0.58f, 15 * d, 0xFFe9edf4, Paint.Align.LEFT, true);
        float bw = h * 0.9f, sx = x + w * 0.3f, ex = x + w - bw - 8 * d;
        button(c, sx, y + h * 0.05f, bw, h * 0.8f, "−", dn, false);
        button(c, ex, y + h * 0.05f, bw, h * 0.8f, "+", up, false);
        float tx0 = sx + bw + 12 * d, tx1 = ex - 12 * d, ty = y + h * 0.45f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF394459);
        r.set(tx0, ty - 4 * d, tx1, ty + 4 * d);
        c.drawRoundRect(r, 4 * d, 4 * d, p);
        p.setColor(0xFF2fd0b5);
        r.set(tx0, ty - 4 * d, tx0 + (tx1 - tx0) * v, ty + 4 * d);
        c.drawRoundRect(r, 4 * d, 4 * d, p);
        c.drawCircle(tx0 + (tx1 - tx0) * v, ty, 9 * d, p);
        return y + h;
    }

    private void challenges(Canvas c, Game g, int sw, int sh) {
        dim(c, sw, sh, 0xE60b0e14);
        text(c, "Desafíos", sw / 2f, 46 * d, 26 * d, 0xFF2fd0b5, Paint.Align.CENTER, true);
        int n = Challenges.LIST.length, cols = n > 6 ? 4 : 3, rows = (n + cols - 1) / cols;
        float gap = 12 * d, cw = Math.min(250 * d, (sw - gap * (cols + 1)) / cols), ch = Math.min(118 * d, (sh - 146 * d) / rows - gap);
        float x0 = (sw - (cw * cols + gap * (cols - 1))) / 2, y0 = 66 * d;
        for (int i = 0; i < n; i++) {
            float x = x0 + (i % cols) * (cw + gap), y = y0 + (i / cols) * (ch + gap);
            int st = g.settings.stars(i);
            int k = bn++;
            bx[k] = x; by[k] = y; bw[k] = cw; bh[k] = ch; ba[k] = A_CHAL + i;
            p.setStyle(Paint.Style.FILL);
            p.setColor(pressed == k ? 0xFF2c3446 : 0xFF262d3b);
            r.set(x, y, x + cw, y + ch);
            c.drawRoundRect(r, 16 * d, 16 * d, p);
            text(c, (i + 1) + ". " + Challenges.LIST[i].name, x + 14 * d, y + 28 * d, 16 * d, 0xFFffffff, Paint.Align.LEFT, true);
            wrap(c, Challenges.LIST[i].goal, x + 14 * d, y + 50 * d, cw - 28 * d, 11.5f * d, 0xFFb9c2d0);
            for (int s = 0; s < 3; s++) star(c, x + 24 * d + s * 26 * d, y + ch - 20 * d, 10 * d, s < st);
        }
        button(c, sw / 2f - 90 * d, sh - 62 * d, 180 * d, 46 * d, "Volver", A_BACK, true);
    }

    private void achievements(Canvas c, Game g, int sw, int sh) {
        dim(c, sw, sh, 0xE60b0e14);
        text(c, "Logros  " + g.achievements.count(g.settings) + "/" + Achievements.LIST.length, sw / 2f, 46 * d, 24 * d, 0xFF2fd0b5, Paint.Align.CENTER, true);
        int n = Achievements.LIST.length, cols = n > 15 ? 4 : 3, rows = (n + cols - 1) / cols;
        float gap = 10 * d, cw = Math.min(280 * d, (sw - gap * (cols + 1)) / cols), ch = Math.min(64 * d, (sh - 140 * d) / rows - gap);
        float x0 = (sw - (cw * cols + gap * (cols - 1))) / 2, y0 = 64 * d;
        for (int i = 0; i < n; i++) {
            float x = x0 + (i % cols) * (cw + gap), y = y0 + (i / cols) * (ch + gap);
            boolean on = g.achievements.has(g.settings, i);
            p.setStyle(Paint.Style.FILL);
            p.setColor(on ? 0xFF2d3548 : 0xFF1f2531);
            r.set(x, y, x + cw, y + ch);
            c.drawRoundRect(r, 12 * d, 12 * d, p);
            g.achievements.trophy(c, x + 24 * d, y + ch / 2, Math.min(12 * d, ch * 0.25f), on);
            float ts = Math.min(14 * d, ch * 0.26f);
            text(c, Achievements.LIST[i][0], x + 46 * d, y + ch * 0.45f, ts, on ? 0xFFffffff : 0xFF7d8698, Paint.Align.LEFT, true);
            text(c, Achievements.LIST[i][1], x + 46 * d, y + ch * 0.45f + ts * 1.2f, ts * 0.78f, on ? 0xFFb9c2d0 : 0xFF5d6575, Paint.Align.LEFT, false);
        }
        button(c, sw / 2f - 90 * d, sh - 58 * d, 180 * d, 44 * d, "Volver", A_BACK, true);
    }

    private void wrap(Canvas c, String s, float x, float y, float maxW, float size, int col) {
        p.setTextSize(size);
        String[] words = s.split(" ");
        StringBuilder line = new StringBuilder();
        for (String w : words) {
            String t = line.length() == 0 ? w : line + " " + w;
            if (p.measureText(t) > maxW && line.length() > 0) {
                text(c, line.toString(), x, y, size, col, Paint.Align.LEFT, false);
                y += size * 1.3f;
                line.setLength(0);
                line.append(w);
            } else { line.setLength(0); line.append(t); }
        }
        if (line.length() > 0) text(c, line.toString(), x, y, size, col, Paint.Align.LEFT, false);
    }

    private void star(Canvas c, float cx, float cy, float rad, boolean on) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(on ? 0xFF2fd0b5 : 0xFF3a4255);
        android.graphics.Path path = new android.graphics.Path();
        for (int i = 0; i < 10; i++) {
            double a = -Math.PI / 2 + i * Math.PI / 5;
            float rr = (i & 1) == 0 ? rad : rad * 0.45f;
            float px = cx + (float) Math.cos(a) * rr, py = cy + (float) Math.sin(a) * rr;
            if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
        }
        path.close();
        c.drawPath(path, p);
    }

    private void result(Canvas c, Game g, int sw, int sh) {
        dim(c, sw, sh, 0xB30b0e14);
        boolean won = g.chResult > 0;
        float w = Math.min(420 * d, sw * 0.7f), h = Math.min(300 * d, sh * 0.8f), x = (sw - w) / 2, y = (sh - h) / 2;
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xF01b2029);
        r.set(x, y, x + w, y + h);
        c.drawRoundRect(r, 22 * d, 22 * d, p);
        text(c, won ? "¡Conseguido!" : "Fallaste", sw / 2f, y + 46 * d, 26 * d, won ? 0xFF2fd0b5 : 0xFFe0463c, Paint.Align.CENTER, true);
        text(c, Challenges.LIST[g.challenge].name + String.format("  ·  %.1f s", g.chTime), sw / 2f, y + 74 * d, 14 * d, 0xFFb9c2d0, Paint.Align.CENTER, false);
        if (won) {
            for (int s = 0; s < 3; s++) {
                float pop = Math.min(1f, Math.max(0f, (time - s * 0.25f) * 4f));
                star(c, sw / 2f + (s - 1) * 58 * d, y + 124 * d, 24 * d * (0.6f + 0.4f * pop), s < g.chStars && pop > 0.5f);
            }
        }
        float bwid = (w - 72 * d) / 3, by0 = y + h - 70 * d;
        button(c, x + 24 * d, by0, bwid, 50 * d, "Menú", A_MENU, false);
        button(c, x + 36 * d + bwid, by0, bwid, 50 * d, "Repetir", A_RETRY, !won);
        if (won && g.challenge + 1 < Challenges.LIST.length) button(c, x + 48 * d + bwid * 2, by0, bwid, 50 * d, "Siguiente", A_NEXT, true);
        else button(c, x + 48 * d + bwid * 2, by0, bwid, 50 * d, "Lista", A_CHALLENGES, won);
    }

    private void tutorial(Canvas c, Game g, int sw, int sh) {
        String s = TUT[Math.min(tutStep, TUT.length - 1)];
        float w = Math.min(500 * d, sw * 0.62f), h = 96 * d, x = (sw - w) / 2, y = g.ui.topH + 24 * d;
        tutBox.set(x, y, x + w, y + h);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xF01b2029);
        r.set(x, y, x + w, y + h);
        c.drawRoundRect(r, 18 * d, 18 * d, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2.5f * d);
        p.setColor(0xFF2fd0b5);
        c.drawRoundRect(r, 18 * d, 18 * d, p);
        text(c, "Consejo " + (tutStep + 1) + "/" + TUT.length, x + 20 * d, y + 30 * d, 13 * d, 0xFF2fd0b5, Paint.Align.LEFT, true);
        wrap(c, s, x + 20 * d, y + 56 * d, w - 40 * d, 15 * d, 0xFFe9edf4);
        float blink = 0.5f + 0.5f * (float) Math.sin(time * 4f);
        text(c, tutStep == TUT.length - 1 ? "Toca para cerrar" : "Toca aquí para saltar", x + w - 20 * d, y + h - 14 * d, 11 * d, Renderer.alpha(0xFFffffff, 0.4f + 0.5f * blink), Paint.Align.RIGHT, false);
        if (tutStep == 0) {   // point at the "Gente" tab
            int ri = Math.max(0, g.ui.railIndex(1));
            float tx = g.ui.railW + 6 * d + blink * 10 * d, ty = g.ui.railRowY(ri) + g.ui.rowH() / 2;
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFF2fd0b5);
            android.graphics.Path a = new android.graphics.Path();
            a.moveTo(tx, ty); a.lineTo(tx + 22 * d, ty - 14 * d); a.lineTo(tx + 22 * d, ty + 14 * d); a.close();
            c.drawPath(a, p);
        }
    }
}

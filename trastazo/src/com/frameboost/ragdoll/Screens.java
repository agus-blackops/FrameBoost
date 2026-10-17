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
        "Prueba zombis, explosivos y el Taller de mods. ¡A experimentar!",
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
        p.setColor(primary ? 0xFF1b2029 : 0xFFeef2f8);
        p.setTextAlign(Paint.Align.CENTER);
        p.setFakeBoldText(true);
        p.setTextSize(Math.min(17 * d, h * 0.42f));
        c.drawText(label, x + w / 2, y + h / 2 + p.getTextSize() * 0.36f + o, p);
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

    private void title(Canvas c, Game g, int sw, int sh) {
        // darken the left side where the menu sits, the live demo stays visible on the right
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 12; i++) {
            p.setColor(Renderer.alpha(0xFF0b0e14, 0.82f * (1f - i / 12f)));
            c.drawRect(i * sw * 0.05f, 0, (i + 1) * sw * 0.05f, sh, p);
        }
        float colX = 40 * d, colW = Math.min(sw * 0.42f, 360 * d);
        logo(c, colX + colW / 2, sh * 0.2f, Math.min(64 * d, colW / 5.2f));
        text(c, "laboratorio de muñecos y caos", colX + colW / 2, sh * 0.2f + 30 * d, 14 * d, 0xFFb9c2d0, Paint.Align.CENTER, false);
        float y = sh * 0.33f, bh = Math.min(54 * d, sh * 0.12f), gap = Math.min(12 * d, sh * 0.025f);
        if (g.hasSave()) { button(c, colX, y, colW, bh, "Continuar", A_CONTINUE, true); y += bh + gap; }
        button(c, colX, y, colW, bh, "Sandbox libre", A_SANDBOX, !g.hasSave()); y += bh + gap;
        button(c, colX, y, colW, bh, "Desafíos   ★ " + g.settings.totalStars() + "/" + Challenges.LIST.length * 3, A_CHALLENGES, false); y += bh + gap;
        float half = (colW - gap) / 2;
        button(c, colX, y, half, bh, "Logros " + g.achievements.count(g.settings) + "/" + Achievements.LIST.length, A_ACH, false);
        button(c, colX + half + gap, y, half, bh, "Ajustes", A_SETTINGS, false);
        text(c, "Física, arte y música originales · v3.6 · animales, mano siempre activa y armas nuevas", colX, sh - 16 * d, 11 * d, 0x88ffffff, Paint.Align.LEFT, false);
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
        button(c, lx, ry, half, rowH * 0.86f, "Vibración: " + (s.haptics ? "Sí" : "No"), A_HAPTICS, false);
        button(c, lx + half + 12 * d, ry, half, rowH * 0.86f, "Sangre: " + (s.gore ? "Sí" : "No"), A_GORE, false);
        ry += rowH;
        button(c, lx, ry, half, rowH * 0.86f, "Calidad: " + (s.highQuality ? "Alta" : "Ahorro"), A_QUALITY, false);
        button(c, lx + half + 12 * d, ry, half, rowH * 0.86f, "Texturas: " + (g.textures ? "Sí" : "No"), A_TEXTURES, false);
        ry += rowH;
        button(c, lx, ry, half, rowH * 0.86f, "Mostrar FPS: " + (s.showFps ? "Sí" : "No"), A_FPS, false);
        button(c, lx + half + 12 * d, ry, half, rowH * 0.86f, "Motor: " + (s.gpu ? "GPU" : "Canvas"), A_ENGINE, false);
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

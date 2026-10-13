package com.frameboost.ragdoll;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

/** Screen-space interface: category tabs, item strip and top buttons. Hit testing + drawing. */
final class Ui {
    static final int NONE = 0, BLOCK = 1, TAB = 100, ITEM = 200, BTN = 300;
    static final int BTN_PAUSE = 0, BTN_SLOW = 1, BTN_GRAV = 2, BTN_UNDO = 3, BTN_CLEAR = 4, BTN_SOUND = 5, BTN_MENU = 6, BTN_COUNT = 7;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final RectF r2 = new RectF();

    float d = 1f;
    int sw, sh;
    int cat = 0;
    boolean stripOpen = false;
    float scrollX;
    // layout
    float tabH, stripH, topH, cardW, cardH, gap;
    final String[] btnLabel = new String[BTN_COUNT];
    final boolean[] btnOn = new boolean[BTN_COUNT];
    final boolean[] btnHidden = new boolean[BTN_COUNT];
    int[] cats = {0, 1, 2, 3, 4, 5, 6, 7};
    boolean showFps;
    boolean dock;              // challenge mode: the bottom bar holds the allowed tools directly
    String dockBadge;          // e.g. "x3" uses left
    int gravMode;              // for the gravity icon
    float infoW;
    private final float[] btnX = new float[BTN_COUNT], btnW = new float[BTN_COUNT];

    final java.util.HashSet<Integer> active = new java.util.HashSet<>();
    String toast;
    float toastT;

    void layout(int w, int h, float density) {
        sw = w; sh = h; d = density;
        tabH = 50 * d; stripH = 88 * d; topH = 40 * d;
        cardW = 88 * d; cardH = 76 * d; gap = 8 * d;
        p.setTextSize(12 * d);
        float x = sw - 8 * d;
        int[] order = {BTN_MENU, BTN_PAUSE, BTN_SLOW, BTN_GRAV, BTN_UNDO, BTN_CLEAR, BTN_SOUND};
        for (int oi = order.length - 1; oi >= 0; oi--) {
            int i = order[oi];
            if (btnHidden[i]) continue;
            btnW[i] = topH;       // square icon buttons
            x -= btnW[i];
            btnX[i] = x;
            x -= 6 * d;
        }
        infoW = Math.max(120 * d, Math.min(300 * d, buttonsLeft() - 20 * d));
    }

    void toast(String s) { toast = s; toastT = 2.2f; }

    /** Left edge of the right-hand button group (the info box must stop before it). */
    private float buttonsLeft() {
        float m = sw;
        for (int i = 0; i < BTN_COUNT; i++) if (!btnHidden[i]) m = Math.min(m, btnX[i]);
        return m;
    }

    float stripTop() { return sh - tabH - stripH; }

    float contentW(int n) { return n * (cardW + gap) + gap; }

    float maxScroll() {
        int n = Tools.CATS[cat].length;
        return Math.max(0, contentW(n) - sw);
    }

    /** What is under the screen point? */
    int hit(float x, float y) {
        if (y >= sh - tabH && dock) {
            int n = Math.max(1, Tools.CATS[Tools.CAT_CHAL].length);
            int i = (int) (x / (sw / (float) n));
            return ITEM + Math.max(0, Math.min(n - 1, i));
        }
        if (y >= sh - tabH) {
            int n = cats.length;
            int i = (int) (x / (sw / (float) n));
            return TAB + cats[Math.max(0, Math.min(n - 1, i))];
        }
        if (stripOpen && y >= stripTop()) {
            float cy0 = stripTop() + (stripH - cardH) / 2;
            int n = Tools.CATS[cat].length;
            if (y >= cy0 && y <= cy0 + cardH) {
                float rel = x + scrollX - gap;
                int i = (int) Math.floor(rel / (cardW + gap));
                float inCard = rel - i * (cardW + gap);
                if (i >= 0 && i < n && inCard <= cardW) return ITEM + i;
            }
            return BLOCK;
        }
        if (y <= topH + 6 * d) {
            for (int i = 0; i < BTN_COUNT; i++)
                if (!btnHidden[i] && x >= btnX[i] && x <= btnX[i] + btnW[i] && y >= 4 * d && y <= 4 * d + topH) return BTN + i;
        }
        return NONE;
    }

    void update(float dt) {
        if (toastT > 0) toastT -= dt;
    }

    // ------------------------------------------------------------------------------ drawing -----

    void draw(Canvas c, int selectedTool, String hudName, String hudHint, float fps, int bodies, boolean confirmClear) {
        // top-left info
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x99141820);
        r.set(8 * d, 4 * d, 8 * d + infoW, 4 * d + topH + 14 * d);
        c.drawRoundRect(r, 8 * d, 8 * d, p);
        p.setColor(0xFFf2b134);
        p.setTextSize(14 * d);
        p.setTextAlign(Paint.Align.LEFT);
        p.setFakeBoldText(true);
        drawFitted(c, hudName, 16 * d, 22 * d, infoW - 16 * d);
        p.setFakeBoldText(false);
        p.setColor(0xFFd5dbe6);
        p.setTextSize(10 * d);
        drawFitted(c, hudHint, 16 * d, 38 * d, infoW - 16 * d);
        p.setColor(0x88ffffff);
        p.setTextSize(9 * d);
        if (showFps) c.drawText(String.format("%d FPS · %d objetos", (int) fps, bodies), 16 * d, 52 * d, p);
        else c.drawText(bodies + " objetos", 16 * d, 52 * d, p);

        // top-right buttons
        for (int i = 0; i < BTN_COUNT; i++) {
            if (btnHidden[i]) continue;
            boolean on = btnOn[i] || (i == BTN_CLEAR && confirmClear);
            p.setColor(on ? 0xEEf2b134 : 0xCC1b2029);
            r.set(btnX[i], 4 * d, btnX[i] + btnW[i], 4 * d + topH);
            c.drawRoundRect(r, 8 * d, 8 * d, p);
            btnIcon(c, i, btnX[i] + btnW[i] / 2, 4 * d + topH / 2, topH * 0.27f, on ? 0xFF1b2029 : 0xFFe9edf4, confirmClear);
        }

        // strip
        if (stripOpen) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xE6161a22);
            c.drawRect(0, stripTop(), sw, sh - tabH, p);
            c.save();
            c.clipRect(0, stripTop(), sw, sh - tabH);
            Tools.Item[] items = Tools.CATS[cat];
            float cy0 = stripTop() + (stripH - cardH) / 2;
            for (int i = 0; i < items.length; i++) {
                float x0 = gap + i * (cardW + gap) - scrollX;
                if (x0 + cardW < 0 || x0 > sw) continue;
                boolean sel = items[i].id == selectedTool || active.contains(items[i].id);
                p.setStyle(Paint.Style.FILL);
                p.setColor(sel ? 0xFF3a4560 : 0xFF262d3b);
                r.set(x0, cy0, x0 + cardW, cy0 + cardH);
                c.drawRoundRect(r, 10 * d, 10 * d, p);
                if (sel) {
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(2.5f * d);
                    p.setColor(0xFFf2b134);
                    c.drawRoundRect(r, 10 * d, 10 * d, p);
                    p.setStyle(Paint.Style.FILL);
                }
                if (items[i].id >= Tools.MOD_CREATE) letterIcon(c, items[i], x0 + cardW / 2, cy0 + cardH * 0.4f, cardH * 0.36f);
                else drawIcon(c, items[i].id, x0 + cardW / 2, cy0 + cardH * 0.4f, cardH * 0.36f);
                p.setColor(0xFFe9edf4);
                p.setTextSize(11.5f * d);
                p.setTextAlign(Paint.Align.CENTER);
                drawFittedCentered(c, items[i].name, x0 + cardW / 2, cy0 + cardH - 9 * d, cardW - 8 * d);
            }
            c.restore();
        }

        // tabs (or the challenge tool dock)
        if (dock) { drawDock(c, selectedTool); drawToast(c); return; }
        int n = cats.length;
        float tw = sw / (float) n;
        for (int i = 0; i < n; i++) {
            int ci = cats[i];
            boolean sel = ci == cat && stripOpen;
            p.setStyle(Paint.Style.FILL);
            p.setColor(sel ? 0xFFf2b134 : 0xF0161a22);
            r.set(i * tw, sh - tabH, (i + 1) * tw, sh);
            c.drawRect(r, p);
            p.setColor(0x33ffffff);
            c.drawRect(i * tw, sh - tabH, i * tw + 1.5f, sh, p);
            catIcon(c, ci, i * tw + tw / 2, sh - tabH + 17 * d, 9 * d, sel);
            p.setStyle(Paint.Style.FILL);
            p.setColor(sel ? 0xFF161a22 : 0xFFe9edf4);
            p.setTextSize(11.5f * d);
            p.setTextAlign(Paint.Align.CENTER);
            p.setFakeBoldText(true);
            drawFittedCentered(c, Tools.CAT_NAMES[ci], i * tw + tw / 2, sh - 9 * d, tw - 6 * d);
            p.setFakeBoldText(false);
        }
        drawToast(c);
    }

    private void drawDock(Canvas c, int selectedTool) {
        Tools.Item[] items = Tools.CATS[Tools.CAT_CHAL];
        int n = Math.max(1, items.length);
        float tw = sw / (float) n;
        for (int i = 0; i < items.length; i++) {
            boolean sel = items[i].id == selectedTool;
            p.setStyle(Paint.Style.FILL);
            p.setColor(sel ? 0xFF3a4560 : 0xF0161a22);
            r.set(i * tw, sh - tabH, (i + 1) * tw, sh);
            c.drawRect(r, p);
            if (sel) { p.setColor(0xFFf2b134); c.drawRect(i * tw, sh - tabH, (i + 1) * tw, sh - tabH + 3 * d, p); }
            p.setColor(0x33ffffff);
            c.drawRect(i * tw, sh - tabH, i * tw + 1.5f, sh, p);
            drawIcon(c, items[i].id, i * tw + tw / 2 - 40 * d, sh - tabH / 2, 14 * d);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFe9edf4);
            p.setTextSize(14 * d);
            p.setTextAlign(Paint.Align.LEFT);
            p.setFakeBoldText(true);
            String label = items[i].name + (dockBadge != null && Tools.isWeapon(items[i].id) || dockBadge != null && items[i].id == Tools.DETONATE ? "  " + dockBadge : "");
            drawFitted(c, label, i * tw + tw / 2 - 20 * d, sh - tabH / 2 + 5 * d, tw / 2 + 10 * d);
            p.setFakeBoldText(false);
        }
    }

    private void drawToast(Canvas c) {
        if (toastT > 0 && toast != null) {
            p.setTextSize(14 * d);
            p.setTextAlign(Paint.Align.CENTER);
            while (p.measureText(toast) > sw - 60 * d && p.getTextSize() > 8 * d) p.setTextSize(p.getTextSize() - 0.5f * d);
            float w = p.measureText(toast) + 28 * d;
            float ty = dock ? sh - tabH - 84 * d : sh - tabH - (stripOpen ? stripH : 0) - 48 * d;
            p.setStyle(Paint.Style.FILL);
            p.setColor(alpha(0xE6000000, Math.min(1f, toastT * 2f)));
            r.set(sw / 2f - w / 2, ty - 22 * d, sw / 2f + w / 2, ty + 12 * d);
            c.drawRoundRect(r, 14 * d, 14 * d, p);
            p.setColor(alpha(0xFFffffff, Math.min(1f, toastT * 2f)));
            c.drawText(toast, sw / 2f, ty, p);
        }
    }

    /** Icon for workshop mods and styles: coloured tile with the initial (or a symbol). */
    private void letterIcon(Canvas c, Tools.Item it, float cx, float cy, float s) {
        int id = it.id;
        int[] pal = {0xFFe0463c, 0xFF3aa0e0, 0xFF4cbf6b, 0xFFf2b134, 0xFFb06ad8, 0xFFf27ab0, 0xFF2fb5a8, 0xFFdd7b2f};
        int col = id == Tools.MOD_CREATE ? 0xFF4a5568 : pal[Math.abs(id * 7 + it.name.length()) % pal.length];
        if (id >= Tools.STYLE_BASE && id < Tools.STYLE_TEX) {
            int[] wall = {0xFF2f3846, 0xFFdde5ea, 0xFF0f1630, 0xFF6b3b32, 0xFF0a0612, 0xFFe8c98a};
            int[] flr = {0xFF666d7a, 0xFFf2f6f8, 0xFF3a4258, 0xFF7a5636, 0xFF00e5ff, 0xFFe6b867};
            int k = id - Tools.STYLE_BASE;
            box(c, cx - s * 0.9f, cy - s * 0.7f, cx + s * 0.9f, cy + s * 0.7f, wall[k], s * 0.12f);
            box(c, cx - s * 0.9f, cy + s * 0.25f, cx + s * 0.9f, cy + s * 0.7f, flr[k], s * 0.12f);
            return;
        }
        box(c, cx - s * 0.8f, cy - s * 0.7f, cx + s * 0.8f, cy + s * 0.7f, col, s * 0.2f);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFffffff);
        p.setTextAlign(Paint.Align.CENTER);
        p.setFakeBoldText(true);
        p.setTextSize(s * 1.2f);
        String t = id == Tools.MOD_CREATE ? "+" : (id == Tools.STYLE_TEX ? "▦" : it.name.substring(0, 1).toUpperCase());
        c.drawText(t, cx, cy + s * 0.42f, p);
        p.setFakeBoldText(false);
    }

    private final android.graphics.Path path = new android.graphics.Path();

    /** Vector icons for the top bar. */
    private void btnIcon(Canvas c, int i, float cx, float cy, float s, int col, boolean confirmClear) {
        p.setColor(col);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(s * 0.24f);
        switch (i) {
            case BTN_MENU:
                p.setStyle(Paint.Style.STROKE);
                for (int k = -1; k <= 1; k++) c.drawLine(cx - s, cy + k * s * 0.6f, cx + s, cy + k * s * 0.6f, p);
                break;
            case BTN_PAUSE:
                p.setStyle(Paint.Style.FILL);
                if (btnOn[BTN_PAUSE]) {
                    path.reset(); path.moveTo(cx - s * 0.6f, cy - s); path.lineTo(cx + s, cy); path.lineTo(cx - s * 0.6f, cy + s); path.close();
                    c.drawPath(path, p);
                } else {
                    r2.set(cx - s * 0.8f, cy - s, cx - s * 0.25f, cy + s); c.drawRoundRect(r2, s * 0.12f, s * 0.12f, p);
                    r2.set(cx + s * 0.25f, cy - s, cx + s * 0.8f, cy + s); c.drawRoundRect(r2, s * 0.12f, s * 0.12f, p);
                }
                break;
            case BTN_SLOW:   // stopwatch
                p.setStyle(Paint.Style.STROKE);
                c.drawCircle(cx, cy + s * 0.12f, s * 0.85f, p);
                c.drawLine(cx, cy + s * 0.12f, cx + s * 0.4f, cy - s * 0.3f, p);
                c.drawLine(cx - s * 0.3f, cy - s * 1.05f, cx + s * 0.3f, cy - s * 1.05f, p);
                break;
            case BTN_GRAV:
                p.setStyle(Paint.Style.STROKE);
                if (gravMode == 0) {
                    c.drawLine(cx, cy - s, cx, cy + s * 0.7f, p);
                    c.drawLine(cx, cy + s * 0.7f, cx - s * 0.5f, cy + s * 0.2f, p);
                    c.drawLine(cx, cy + s * 0.7f, cx + s * 0.5f, cy + s * 0.2f, p);
                    c.drawLine(cx - s, cy + s * 1.05f, cx + s, cy + s * 1.05f, p);
                } else if (gravMode == 1) {
                    p.setStyle(Paint.Style.FILL);
                    c.drawCircle(cx, cy, s * 0.95f, p);
                    p.setColor(btnOn[BTN_GRAV] ? 0xFFf2b134 : 0xFF1b2029);
                    c.drawCircle(cx + s * 0.45f, cy - s * 0.3f, s * 0.8f, p);
                } else {
                    c.drawCircle(cx, cy, s * 0.45f, p);
                    r2.set(cx - s * 1.1f, cy - s * 0.45f, cx + s * 1.1f, cy + s * 0.45f);
                    c.drawOval(r2, p);
                }
                break;
            case BTN_UNDO:
                p.setStyle(Paint.Style.STROKE);
                r2.set(cx - s * 0.8f, cy - s * 0.7f, cx + s * 0.9f, cy + s * 0.9f);
                c.drawArc(r2, 180f, 230f, false, p);
                c.drawLine(cx - s * 0.8f, cy + s * 0.1f, cx - s * 1.1f, cy - s * 0.4f, p);
                c.drawLine(cx - s * 0.8f, cy + s * 0.1f, cx - s * 0.25f, cy - s * 0.15f, p);
                break;
            case BTN_CLEAR:
                if (confirmClear) {
                    p.setStyle(Paint.Style.FILL);
                    p.setTextAlign(Paint.Align.CENTER);
                    p.setTextSize(s * 1.7f);
                    p.setFakeBoldText(true);
                    c.drawText("?", cx, cy + s * 0.6f, p);
                    p.setFakeBoldText(false);
                    break;
                }
                p.setStyle(Paint.Style.STROKE);
                c.drawLine(cx - s, cy - s * 0.6f, cx + s, cy - s * 0.6f, p);
                c.drawLine(cx - s * 0.3f, cy - s * 0.95f, cx + s * 0.3f, cy - s * 0.95f, p);
                c.drawLine(cx - s * 0.7f, cy - s * 0.4f, cx - s * 0.55f, cy + s, p);
                c.drawLine(cx + s * 0.7f, cy - s * 0.4f, cx + s * 0.55f, cy + s, p);
                c.drawLine(cx - s * 0.55f, cy + s, cx + s * 0.55f, cy + s, p);
                break;
            case BTN_SOUND:
                p.setStyle(Paint.Style.FILL);
                path.reset(); path.moveTo(cx - s, cy - s * 0.35f); path.lineTo(cx - s * 0.5f, cy - s * 0.35f);
                path.lineTo(cx, cy - s * 0.9f); path.lineTo(cx, cy + s * 0.9f); path.lineTo(cx - s * 0.5f, cy + s * 0.35f);
                path.lineTo(cx - s, cy + s * 0.35f); path.close();
                c.drawPath(path, p);
                p.setStyle(Paint.Style.STROKE);
                if (btnOn[BTN_SOUND]) {
                    c.drawLine(cx + s * 0.35f, cy - s * 0.45f, cx + s * 1.1f, cy + s * 0.45f, p);
                    c.drawLine(cx + s * 0.35f, cy + s * 0.45f, cx + s * 1.1f, cy - s * 0.45f, p);
                } else {
                    r2.set(cx - s * 0.4f, cy - s * 0.7f, cx + s * 0.9f, cy + s * 0.7f);
                    c.drawArc(r2, -45f, 90f, false, p);
                }
                break;
            default: break;
        }
        p.setStyle(Paint.Style.FILL);
    }

    /** Small icon per category tab. */
    private void catIcon(Canvas c, int ci, float cx, float cy, float s, boolean sel) {
        int[] rep = {Tools.HAND, Tools.HUMAN, Tools.CRATE, Tools.DYNAMITE, Tools.PISTOL, Tools.FIRE};
        if (ci < rep.length) { drawIcon(c, rep[ci], cx, cy, s); return; }
        p.setStyle(Paint.Style.FILL);
        if (ci == Tools.CAT_MODS) {            // puzzle-piece-ish gear
            p.setColor(sel ? 0xFF161a22 : 0xFFb06ad8);
            c.drawCircle(cx, cy, s * 0.7f, p);
            for (int k = 0; k < 6; k++) {
                double a = k * Math.PI / 3;
                c.drawCircle(cx + (float) Math.cos(a) * s * 0.85f, cy + (float) Math.sin(a) * s * 0.85f, s * 0.25f, p);
            }
            p.setColor(sel ? 0xFFf2b134 : 0xF0161a22);
            c.drawCircle(cx, cy, s * 0.3f, p);
        } else {                               // palette
            int[] cols = {0xFFe0463c, 0xFF3aa0e0, 0xFF4cbf6b, 0xFFf2b134};
            p.setColor(sel ? 0xFF161a22 : 0xFFe9edf4);
            c.drawCircle(cx, cy, s * 0.95f, p);
            for (int k = 0; k < 4; k++) {
                double a = -Math.PI * 0.9 + k * 0.55;
                p.setColor(cols[k]);
                c.drawCircle(cx + (float) Math.cos(a) * s * 0.55f, cy + (float) Math.sin(a) * s * 0.55f, s * 0.2f, p);
            }
        }
    }

    private static int alpha(int c, float a) {
        int al = (int) ((c >>> 24) * Math.max(0f, Math.min(1f, a)));
        return (al << 24) | (c & 0xFFFFFF);
    }

    private void drawFitted(Canvas c, String s, float x, float y, float maxW) {
        float size = p.getTextSize();
        while (p.measureText(s) > maxW && size > 6 * d) { size -= 0.5f * d; p.setTextSize(size); }
        c.drawText(s, x, y, p);
    }

    private void drawFittedCentered(Canvas c, String s, float x, float y, float maxW) {
        float size = p.getTextSize();
        while (p.measureText(s) > maxW && size > 6 * d) { size -= 0.5f * d; p.setTextSize(size); }
        c.drawText(s, x, y, p);
    }

    // ------------------------------------------------------------------------------------ icons ---

    private void line(Canvas c, float x0, float y0, float x1, float y1, float w, int col) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(w);
        p.setColor(col);
        c.drawLine(x0, y0, x1, y1, p);
    }

    private void disc(Canvas c, float x, float y, float rad, int col) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(col);
        c.drawCircle(x, y, rad, p);
    }

    private void box(Canvas c, float x0, float y0, float x1, float y1, int col, float rad) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(col);
        r2.set(x0, y0, x1, y1);
        c.drawRoundRect(r2, rad, rad, p);
    }

    private void person(Canvas c, float cx, float cy, float s, int shirt) { person(c, cx, cy, s, shirt, 0xFFf1c7a0, 0xFF2c3e60, 1f); }

    private void person(Canvas c, float cx, float cy, float s, int shirt, int skin, int pants, float thick) {
        float w = Math.max(1.6f, s * 0.13f);
        disc(c, cx, cy - s * 0.78f, s * 0.2f, skin);
        line(c, cx, cy - s * 0.55f, cx, cy + s * 0.05f, s * 0.3f * thick, shirt);
        line(c, cx, cy - s * 0.45f, cx - s * 0.3f, cy - s * 0.05f, w * thick, skin);
        line(c, cx, cy - s * 0.45f, cx + s * 0.3f, cy - s * 0.05f, w * thick, skin);
        line(c, cx - s * 0.08f, cy + s * 0.05f, cx - s * 0.2f, cy + s * 0.7f, w * 1.4f * thick, pants);
        line(c, cx + s * 0.08f, cy + s * 0.05f, cx + s * 0.2f, cy + s * 0.7f, w * 1.4f * thick, pants);
    }

    void drawIcon(Canvas c, int id, float cx, float cy, float s) {
        float w = Math.max(2f, s * 0.14f);
        switch (id) {
            case Tools.HAND:
                disc(c, cx, cy + s * 0.15f, s * 0.42f, 0xFFf1c7a0);
                for (int i = -1; i <= 2; i++) line(c, cx + i * s * 0.2f - s * 0.1f, cy - s * 0.1f, cx + i * s * 0.2f - s * 0.1f, cy - s * 0.6f, s * 0.15f, 0xFFf1c7a0);
                break;
            case Tools.DELETE:
                line(c, cx - s * 0.55f, cy - s * 0.55f, cx + s * 0.55f, cy + s * 0.55f, w * 1.6f, 0xFFe0463c);
                line(c, cx + s * 0.55f, cy - s * 0.55f, cx - s * 0.55f, cy + s * 0.55f, w * 1.6f, 0xFFe0463c);
                break;
            case Tools.FREEZE:
                disc(c, cx, cy - s * 0.2f, s * 0.32f, 0xFFe9ecf1);
                line(c, cx, cy, cx, cy + s * 0.65f, w, 0xFFb4bac4);
                break;
            case Tools.CUT:
                line(c, cx - s * 0.6f, cy + s * 0.5f, cx + s * 0.6f, cy - s * 0.5f, w * 1.3f, 0xFFdfe4ea);
                line(c, cx - s * 0.1f, cy - s * 0.6f, cx + s * 0.1f, cy + s * 0.6f, w * 0.7f, 0xFFe0463c);
                break;
            case Tools.HUMAN: person(c, cx, cy + s * 0.1f, s * 0.9f, 0xFF3b6ea8); break;
            case Tools.GIANT: person(c, cx, cy + s * 0.2f, s * 1.15f, 0xFFb23a3a); break;
            case Tools.KID: person(c, cx, cy + s * 0.35f, s * 0.6f, 0xFF3f8f55); break;
            case Tools.CROWD:
                person(c, cx - s * 0.45f, cy + s * 0.3f, s * 0.55f, 0xFFd4a62a);
                person(c, cx + s * 0.45f, cy + s * 0.3f, s * 0.55f, 0xFF7a4aa8);
                person(c, cx, cy + s * 0.2f, s * 0.7f, 0xFF3b6ea8);
                break;
            case Tools.ROBOT:
                person(c, cx, cy + s * 0.1f, s * 0.9f, 0xFF68788a, 0xFFa9b4bf, 0xFF4b5666, 1f);
                line(c, cx, cy - s * 0.88f, cx, cy - s * 1.05f, w * 0.5f, 0xFF9aa5b1);
                disc(c, cx, cy - s * 1.08f, s * 0.07f, 0xFFff4d4d);
                disc(c, cx + s * 0.08f, cy - s * 0.62f, s * 0.05f, 0xFF35e0ff);
                break;
            case Tools.ZOMBIE:
                person(c, cx, cy + s * 0.1f, s * 0.9f, 0xFF5d5a4a, 0xFF7fa65a, 0xFF3b3f4c, 1f);
                line(c, cx, cy - s * 0.3f, cx + s * 0.55f, cy - s * 0.32f, w, 0xFF7fa65a);
                disc(c, cx + s * 0.07f, cy - s * 0.62f, s * 0.05f, 0xFFd62020);
                break;
            case Tools.SKELETON:
                person(c, cx, cy + s * 0.1f, s * 0.9f, 0xFFe9e5d3, 0xFFe9e5d3, 0xFFe3dfcc, 0.55f);
                disc(c, cx + s * 0.06f, cy - s * 0.62f, s * 0.05f, 0xFF2a2820);
                break;
            case Tools.WHEEL:
                disc(c, cx, cy, s * 0.62f, 0xFF25272c);
                disc(c, cx, cy, s * 0.32f, 0xFF9aa3ad);
                disc(c, cx, cy, s * 0.12f, 0xFF4a525c);
                break;
            case Tools.BOWLING:
                disc(c, cx, cy, s * 0.5f, 0xFF1d2a5a);
                disc(c, cx - s * 0.12f, cy - s * 0.2f, s * 0.07f, 0xFF05070f);
                disc(c, cx + s * 0.1f, cy - s * 0.22f, s * 0.07f, 0xFF05070f);
                disc(c, cx, cy - s * 0.04f, s * 0.07f, 0xFF05070f);
                break;
            case Tools.MELON:
                disc(c, cx, cy, s * 0.55f, 0xFF3f9a3c);
                line(c, cx - s * 0.25f, cy - s * 0.4f, cx - s * 0.2f, cy + s * 0.4f, w * 0.6f, 0xFF2a6e29);
                line(c, cx + s * 0.15f, cy - s * 0.45f, cx + s * 0.2f, cy + s * 0.42f, w * 0.6f, 0xFF2a6e29);
                break;
            case Tools.CRATE: case Tools.BIGCRATE: {
                float k = id == Tools.CRATE ? 0.55f : 0.72f;
                box(c, cx - s * k, cy - s * k, cx + s * k, cy + s * k, 0xFFB7864B, s * 0.1f);
                line(c, cx - s * k, cy - s * k, cx + s * k, cy + s * k, w * 0.7f, 0xFF6a4a22);
                line(c, cx + s * k, cy - s * k, cx - s * k, cy + s * k, w * 0.7f, 0xFF6a4a22);
                break;
            }
            case Tools.PLANK:
                box(c, cx - s * 0.9f, cy - s * 0.18f, cx + s * 0.9f, cy + s * 0.18f, 0xFFC9A066, s * 0.1f);
                break;
            case Tools.BARREL:
                box(c, cx - s * 0.4f, cy - s * 0.65f, cx + s * 0.4f, cy + s * 0.65f, 0xFF4f6b8a, s * 0.14f);
                line(c, cx - s * 0.4f, cy - s * 0.25f, cx + s * 0.4f, cy - s * 0.25f, w * 0.7f, 0xFF2c3f55);
                line(c, cx - s * 0.4f, cy + s * 0.25f, cx + s * 0.4f, cy + s * 0.25f, w * 0.7f, 0xFF2c3f55);
                break;
            case Tools.BALL:
                disc(c, cx, cy, s * 0.55f, 0xFFe0463c);
                disc(c, cx - s * 0.2f, cy - s * 0.2f, s * 0.14f, 0x66ffffff);
                break;
            case Tools.BALLOON:
                disc(c, cx, cy - s * 0.15f, s * 0.5f, 0xFF3aa0e0);
                line(c, cx, cy + s * 0.35f, cx + s * 0.1f, cy + s * 0.8f, w * 0.5f, 0xFFdddddd);
                break;
            case Tools.STEEL:
                box(c, cx - s * 0.55f, cy - s * 0.55f, cx + s * 0.55f, cy + s * 0.55f, 0xFF8a9099, s * 0.08f);
                line(c, cx - s * 0.3f, cy - s * 0.3f, cx + s * 0.3f, cy - s * 0.3f, w * 0.6f, 0xFFc9ced6);
                break;
            case Tools.PLATFORM:
                box(c, cx - s * 0.9f, cy - s * 0.12f, cx + s * 0.9f, cy + s * 0.12f, 0xFF6b7280, s * 0.05f);
                disc(c, cx - s * 0.7f, cy, s * 0.08f, 0xFFe9ecf1);
                disc(c, cx + s * 0.7f, cy, s * 0.08f, 0xFFe9ecf1);
                break;
            case Tools.GRENADE:
                box(c, cx - s * 0.32f, cy - s * 0.35f, cx + s * 0.32f, cy + s * 0.5f, 0xFF4d6030, s * 0.25f);
                line(c, cx, cy - s * 0.35f, cx + s * 0.28f, cy - s * 0.65f, w, 0xFFb9bcc2);
                break;
            case Tools.DYNAMITE:
                for (int i = -1; i <= 1; i++) line(c, cx - s * 0.5f, cy + i * s * 0.28f, cx + s * 0.5f, cy + i * s * 0.28f, s * 0.26f, 0xFFcc2e2e);
                line(c, cx + s * 0.5f, cy, cx + s * 0.7f, cy - s * 0.4f, w * 0.5f, 0xFFeadfc4);
                disc(c, cx + s * 0.7f, cy - s * 0.42f, s * 0.1f, 0xFFffc83d);
                break;
            case Tools.TNT:
                box(c, cx - s * 0.55f, cy - s * 0.55f, cx + s * 0.55f, cy + s * 0.55f, 0xFFc0392b, s * 0.1f);
                p.setColor(0xFFfff2cf); p.setTextSize(s * 0.55f); p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true);
                p.setStyle(Paint.Style.FILL);
                c.drawText("TNT", cx, cy + s * 0.2f, p);
                p.setFakeBoldText(false);
                break;
            case Tools.XBARREL:
                box(c, cx - s * 0.4f, cy - s * 0.65f, cx + s * 0.4f, cy + s * 0.65f, 0xFFb03a2e, s * 0.14f);
                line(c, cx - s * 0.4f, cy - s * 0.2f, cx + s * 0.4f, cy - s * 0.2f, w * 0.7f, 0xFFffd23f);
                line(c, cx - s * 0.4f, cy + s * 0.2f, cx + s * 0.4f, cy + s * 0.2f, w * 0.7f, 0xFFffd23f);
                break;
            case Tools.DETONATE: case Tools.NUKE: {
                int col = id == Tools.NUKE ? 0xFFfff2b0 : 0xFFff9a1f;
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    line(c, cx, cy, cx + (float) Math.cos(a) * s * 0.8f, cy + (float) Math.sin(a) * s * 0.8f, w * 1.2f, col);
                }
                disc(c, cx, cy, s * 0.4f, id == Tools.NUKE ? 0xFFffffff : 0xFFffd25a);
                break;
            }
            case Tools.PISTOL:
                box(c, cx - s * 0.6f, cy - s * 0.3f, cx + s * 0.5f, cy, 0xFF8a8f99, s * 0.06f);
                box(c, cx - s * 0.55f, cy - s * 0.05f, cx - s * 0.25f, cy + s * 0.6f, 0xFF4a4d55, s * 0.06f);
                break;
            case Tools.RIFLE:
                box(c, cx - s * 0.9f, cy - s * 0.18f, cx + s * 0.9f, cy + s * 0.05f, 0xFF8a8f99, s * 0.05f);
                box(c, cx - s * 0.9f, cy - s * 0.25f, cx - s * 0.35f, cy + s * 0.2f, 0xFF6a4a2a, s * 0.05f);
                box(c, cx - s * 0.1f, cy, cx + s * 0.1f, cy + s * 0.45f, 0xFF4a4d55, s * 0.04f);
                break;
            case Tools.SHOTGUN:
                box(c, cx - s * 0.9f, cy - s * 0.2f, cx + s * 0.9f, cy - s * 0.02f, 0xFF8a8f99, s * 0.05f);
                box(c, cx - s * 0.9f, cy + s * 0.02f, cx + s * 0.9f, cy + s * 0.2f, 0xFF8a8f99, s * 0.05f);
                box(c, cx - s * 0.9f, cy - s * 0.25f, cx - s * 0.4f, cy + s * 0.3f, 0xFF6a4a2a, s * 0.05f);
                break;
            case Tools.ROCKET:
                box(c, cx - s * 0.8f, cy - s * 0.2f, cx + s * 0.5f, cy + s * 0.2f, 0xFFcfd3da, s * 0.1f);
                disc(c, cx + s * 0.6f, cy, s * 0.22f, 0xFFd33a2c);
                line(c, cx - s * 0.8f, cy, cx - s * 1.0f, cy, w * 1.5f, 0xFFff9a1f);
                break;
            case Tools.KNIFE:
                line(c, cx - s * 0.7f, cy + s * 0.5f, cx - s * 0.3f, cy + s * 0.1f, s * 0.2f, 0xFF5b3a1e);
                line(c, cx - s * 0.3f, cy + s * 0.1f, cx + s * 0.6f, cy - s * 0.6f, s * 0.2f, 0xFFdfe4ea);
                break;
            case Tools.SWORD:
                line(c, cx - s * 0.8f, cy + s * 0.7f, cx - s * 0.6f, cy + s * 0.5f, s * 0.2f, 0xFF5b3a1e);
                line(c, cx - s * 0.75f, cy + s * 0.25f, cx - s * 0.25f, cy + s * 0.75f, s * 0.16f, 0xFFc9a14a);
                line(c, cx - s * 0.5f, cy + s * 0.5f, cx + s * 0.8f, cy - s * 0.8f, s * 0.18f, 0xFFdfe4ea);
                break;
            case Tools.FIRE:
                disc(c, cx, cy + s * 0.15f, s * 0.5f, 0xFFe8521a);
                disc(c, cx, cy + s * 0.25f, s * 0.32f, 0xFFffb02e);
                disc(c, cx, cy + s * 0.32f, s * 0.15f, 0xFFfff2a0);
                line(c, cx, cy - s * 0.2f, cx, cy - s * 0.7f, s * 0.2f, 0xFFe8521a);
                break;
            case Tools.LIGHTNING:
                line(c, cx + s * 0.3f, cy - s * 0.8f, cx - s * 0.2f, cy - s * 0.05f, w * 1.3f, 0xFFffe14a);
                line(c, cx - s * 0.2f, cy - s * 0.05f, cx + s * 0.25f, cy + s * 0.05f, w * 1.3f, 0xFFffe14a);
                line(c, cx + s * 0.25f, cy + s * 0.05f, cx - s * 0.2f, cy + s * 0.8f, w * 1.3f, 0xFFffe14a);
                break;
            case Tools.PUSH:
                p.setStyle(Paint.Style.STROKE);
                p.setColor(0xFFbfe3ff);
                for (int i = 1; i <= 3; i++) {
                    p.setStrokeWidth(w * 0.8f);
                    c.drawCircle(cx, cy, s * 0.25f * i, p);
                }
                break;
            default:
                disc(c, cx, cy, s * 0.4f, 0xFFaaaaaa);
        }
    }
}

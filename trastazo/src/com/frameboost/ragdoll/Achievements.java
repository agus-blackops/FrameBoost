package com.frameboost.ragdoll;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

/** Fifteen goals for the sandbox, with a pop-up when one unlocks. Saved in Settings.ach. */
final class Achievements {
    static final String[][] LIST = {
        {"Primer trastazo", "Provoca tu primera explosión"},
        {"Reacción en cadena", "5 explosiones en 2 segundos"},
        {"Desmembrador", "Arranca 25 extremidades"},
        {"Francotirador", "Un disparo a la cabeza"},
        {"Paciente cero", "Alguien se convierte en zombi"},
        {"Fruta madura", "Revienta 15 sandías"},
        {"Ingeniero", "Une piezas con cuerda, soldadura o eje"},
        {"Despegue", "Enciende un propulsor"},
        {"Al infinito", "Manda a alguien hasta el techo"},
        {"Multitud", "Ten 25 personas a la vez"},
        {"Creador", "Crea tu propio mod en el Taller"},
        {"Maestro", "Consigue 15 estrellas en Desafíos"},
        {"Primeros auxilios", "Un médico reanima a alguien"},
        {"Escuadrón", "Tus soldados, torretas y drones abaten 10 enemigos"},
        {"Brigada", "Un bombero apaga un incendio"},
    };

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private int shown = -1;
    private float showT;
    private float checkT;

    boolean has(Settings s, int i) { return i < s.ach.length() && s.ach.charAt(i) == '1'; }

    int count(Settings s) { int n = 0; for (int i = 0; i < LIST.length; i++) if (has(s, i)) n++; return n; }

    private void unlock(Game g, int i) {
        Settings s = g.settings;
        if (has(s, i)) return;
        StringBuilder b = new StringBuilder(s.ach);
        while (b.length() < LIST.length) b.append('0');
        b.setCharAt(i, '1');
        s.ach = b.toString();
        g.saveSettings();
        shown = i;
        showT = 3.2f;
        g.haptic(40);
        if (g.sfx != null) g.sfx.pop();
    }

    void update(Game g, float dt) {
        if (showT > 0) showT -= dt;
        if (g.challenge >= 0 && g.screens.screen == Screens.NONE) return;   // sandbox goals only... except Maestro
        checkT -= dt;
        if (checkT > 0) return;
        checkT = 0.25f;
        if (g.screens.screen == Screens.TITLE) { if (g.settings.totalStars() >= 15) unlock(g, 11); return; }
        World.Stats st = g.world.stats;
        if (st.explosions > 0) unlock(g, 0);
        if (st.recentBooms >= 5) unlock(g, 1);
        if (st.severed >= 25) unlock(g, 2);
        if (st.headshots > 0) unlock(g, 3);
        if (st.infections > 0) unlock(g, 4);
        if (st.melons >= 15) unlock(g, 5);
        if (st.links > 0) unlock(g, 6);
        if (st.thrusts > 0) unlock(g, 7);
        if (st.revives > 0) unlock(g, 12);
        if (st.aiKills >= 10) unlock(g, 13);
        if (st.fires > 0) unlock(g, 14);
        int people = 0;
        for (int i = 0; i < g.world.ents.size(); i++) {
            Ent e = g.world.ents.get(i);
            if (!(e instanceof Person)) continue;
            people++;
            if (((Person) e).rb[Person.HEAD].y < g.world.sim.ceilY + 80) unlock(g, 8);
        }
        if (people >= 25) unlock(g, 9);
        if (g.mods.hasUserMod()) unlock(g, 10);
        if (g.settings.totalStars() >= 15) unlock(g, 11);
    }

    void draw(Canvas c, Game g) {
        if (showT <= 0 || shown < 0) return;
        float d = g.density;
        float in = Math.min(1f, (3.2f - showT) * 5f), out = Math.min(1f, showT * 3f);
        float k = Math.min(in, out);
        float w = Math.min(380 * d, g.sw * 0.6f), h = 58 * d;
        float x = g.sw / 2f - w / 2, y = -h + (h + 56 * d) * k;
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xF01b2029);
        r.set(x, y, x + w, y + h);
        c.drawRoundRect(r, 14 * d, 14 * d, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2.5f * d);
        p.setColor(0xFF2fd0b5);
        c.drawRoundRect(r, 14 * d, 14 * d, p);
        trophy(c, x + 30 * d, y + h / 2, 14 * d, true);
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.LEFT);
        p.setFakeBoldText(true);
        p.setTextSize(11 * d);
        p.setColor(0xFF2fd0b5);
        c.drawText("¡LOGRO DESBLOQUEADO!", x + 54 * d, y + 22 * d, p);
        p.setTextSize(15 * d);
        p.setColor(0xFFffffff);
        c.drawText(LIST[shown][0], x + 54 * d, y + 43 * d, p);
        p.setFakeBoldText(false);
    }

    void trophy(Canvas c, float cx, float cy, float s, boolean on) {
        int col = on ? 0xFF2fd0b5 : 0xFF3a4255;
        p.setStyle(Paint.Style.FILL);
        p.setColor(col);
        r.set(cx - s * 0.7f, cy - s, cx + s * 0.7f, cy + s * 0.2f);
        c.drawArc(r, 0, 180, true, p);
        c.drawRect(cx - s * 0.7f, cy - s, cx + s * 0.7f, cy - s * 0.4f, p);
        c.drawRect(cx - s * 0.12f, cy + s * 0.15f, cx + s * 0.12f, cy + s * 0.65f, p);
        c.drawRect(cx - s * 0.45f, cy + s * 0.6f, cx + s * 0.45f, cy + s * 0.85f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(s * 0.16f);
        c.drawCircle(cx - s * 0.75f, cy - s * 0.55f, s * 0.3f, p);
        c.drawCircle(cx + s * 0.75f, cy - s * 0.55f, s * 0.3f, p);
        p.setStyle(Paint.Style.FILL);
    }
}

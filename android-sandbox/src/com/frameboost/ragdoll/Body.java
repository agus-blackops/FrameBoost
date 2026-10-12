package com.frameboost.ragdoll;

import java.util.ArrayList;

/** A group of particles/sticks that behaves as one object (a person, a crate, a bomb...). */
final class Body {
    static final int HUMAN = 0, CRATE = 1, PLANK = 2, BARREL = 3, BALL = 4, BALLOON = 5, HEAVY = 6,
            PLATFORM = 7, TNT = 8, XBARREL = 9, GRENADE = 10, DYNAMITE = 11, KNIFE = 12, SWORD = 13;

    final int type;
    final ArrayList<Particle> ps = new ArrayList<>();
    final ArrayList<Stick> ss = new ArrayList<>();
    final ArrayList<AngleLimit> ls = new ArrayList<>();

    boolean removed;
    boolean flammable, explosive, sharp;
    boolean burning;
    float burnT, charT, fireCd;
    float fuse = -1f;
    float blastR, blastPow, blastDmg;
    float gravScale = 1f;
    float bounce = 0.05f;
    float drag = 0.9993f;
    float mass;
    float hurtCd;
    int color = 0xFFB7864B;

    // bounding circle, refreshed every frame
    float cx, cy, rad;

    // human data
    float blood = 100f;
    boolean dead;
    int dir = 1;
    float scale = 1f;
    int skin, shirt, pants, hair;
    Particle head, chest, pelvis;
    Stick neck, torso, uarmF, farmF, uarmB, farmB, thighF, shinF, thighB, shinB;
    Stick jShF, jElF, jShB, jElB, jHipF, jKneeF, jHipB, jKneeB;

    Body(int type) { this.type = type; }

    boolean isHuman() { return type == HUMAN; }

    void updateBounds() {
        int n = ps.size();
        if (n == 0) { rad = 0; return; }
        float sx = 0, sy = 0;
        for (int i = 0; i < n; i++) { Particle p = ps.get(i); sx += p.x; sy += p.y; }
        cx = sx / n; cy = sy / n;
        float m = 0;
        for (int i = 0; i < n; i++) {
            Particle p = ps.get(i);
            float dx = p.x - cx, dy = p.y - cy;
            float d = (float) Math.sqrt(dx * dx + dy * dy) + p.r;
            if (d > m) m = d;
        }
        rad = m;
    }

    /** Drops dead sticks and (for props) particles that lost every stick. */
    void compact() {
        for (int i = ss.size() - 1; i >= 0; i--) if (ss.get(i).dead) ss.remove(i);
        for (int i = ls.size() - 1; i >= 0; i--) {
            AngleLimit l = ls.get(i);
            if (l.cj.dead || l.cf.dead || l.pj.dead || l.pf.dead) ls.remove(i);
        }
        if (type == HUMAN) return;
        for (int i = 0; i < ps.size(); i++) ps.get(i).idx = 0;
        for (int i = 0; i < ss.size(); i++) { ss.get(i).a.idx++; ss.get(i).b.idx++; }
        for (int i = ps.size() - 1; i >= 0; i--) {
            Particle p = ps.get(i);
            if (p.idx == 0) { p.dead = true; ps.remove(i); }
        }
    }
}

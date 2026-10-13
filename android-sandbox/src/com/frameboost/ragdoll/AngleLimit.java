package com.frameboost.ragdoll;

/**
 * Keeps the relative angle between a parent segment (pf -> pj) and a child segment (cj -> cf)
 * inside [lo, hi] (radians, already multiplied by the body's facing direction).
 */
final class AngleLimit {
    final Particle pj, pf, cj, cf;
    final float lo, hi, childShare;
    final boolean invert;
    int dir;
    boolean disabled;       // broken bone

    AngleLimit(Particle pj, Particle pf, Particle cj, Particle cf, boolean invert,
               float loDeg, float hiDeg, int dir, float childShare) {
        this.pj = pj; this.pf = pf; this.cj = cj; this.cf = cf;
        this.invert = invert;
        this.lo = (float) Math.toRadians(loDeg);
        this.hi = (float) Math.toRadians(hiDeg);
        this.dir = dir;
        this.childShare = childShare;
    }

    void solve() {
        if (disabled) return;
        float ux = pj.x - pf.x, uy = pj.y - pf.y;
        if (invert) { ux = -ux; uy = -uy; }
        float vx = cf.x - cj.x, vy = cf.y - cj.y;
        float l1 = (float) Math.sqrt(ux * ux + uy * uy), l2 = (float) Math.sqrt(vx * vx + vy * vy);
        if (l1 < 1e-4f || l2 < 1e-4f) return;
        float theta = (float) Math.atan2(ux * vy - uy * vx, ux * vx + uy * vy);
        float td = theta * dir;
        float target = td < lo ? lo : (td > hi ? hi : td);
        if (target == td) return;
        // Enforce the limit as a distance constraint between the far ends: it conserves momentum,
        // unlike rotating points around the joint, which pushes the whole body around.
        float phi = invert ? Math.abs(target) : (float) Math.PI - Math.abs(target);
        float d2 = l1 * l1 + l2 * l2 - 2f * l1 * l2 * (float) Math.cos(phi);
        float want = (float) Math.sqrt(Math.max(d2, 0f));
        float dx = cf.x - pf.x, dy = cf.y - pf.y;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        float w = pf.invM + cf.invM;
        if (d < 1e-4f || w == 0f) return;
        float k = (d - want) / d / w;
        pf.x += dx * k * pf.invM; pf.y += dy * k * pf.invM;
        cf.x -= dx * k * cf.invM; cf.y -= dy * k * cf.invM;
    }
}

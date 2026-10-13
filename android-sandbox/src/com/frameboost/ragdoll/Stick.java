package com.frameboost.ragdoll;

/** Distance constraint between two particles. Also the collision/draw capsule of a body part. */
final class Stick {
    static final int FILL = 0, DETAIL = 1, HIDDEN = 2;

    final Body body;
    final Particle a, b;
    float rest, r, tear;
    boolean collide, joint, dead, capA, capB, band, weld;
    float damage;           // body parts: 0..100
    boolean fractured;      // broken bone: the joint above it goes limp
    AngleLimit limit;       // the joint limit this part hangs from
    int kind = FILL;
    int color;
    int idx;
    int id;                // creation order inside its body (save files)
    int stamp = -1;

    Stick(Body body, Particle a, Particle b, float rest, float r) {
        this.body = body;
        this.a = a;
        this.b = b;
        if (rest < 0) {
            float dx = b.x - a.x, dy = b.y - a.y;
            rest = (float) Math.sqrt(dx * dx + dy * dy);
        }
        this.rest = rest;
        this.r = r;
        this.id = body.ss.size();
        body.ss.add(this);
        body.allSticks.add(this);
    }

    Stick solid(int color) { this.collide = true; this.color = color; return this; }
    Stick kind(int k, int color) { this.kind = k; this.color = color; return this; }
}

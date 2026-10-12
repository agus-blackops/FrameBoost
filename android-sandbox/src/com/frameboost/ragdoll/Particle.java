package com.frameboost.ragdoll;

/** A point mass with a collision radius (position-based / Verlet dynamics). */
final class Particle {
    final Body body;
    float x, y, px, py;
    float invM, baseInvM, r;
    float bleed;          // seconds of bleeding left
    boolean pinned;       // frozen by the player
    boolean dead;         // removed from the world
    int idx;
    int id;                // creation order inside its body (save files)
    int groundFrame = -1000;  // last physics step it was supported from below

    Particle(Body body, float x, float y, float mass, float r) {
        this.body = body;
        this.x = this.px = x;
        this.y = this.py = y;
        this.baseInvM = mass > 0 ? 1f / mass : 0f;
        this.invM = baseInvM;
        this.r = r;
        this.id = body.ps.size();
        body.ps.add(this);
    }

    float vx() { return (x - px) / World.H; }
    float vy() { return (y - py) / World.H; }

    void addVel(float vx, float vy) {
        if (invM == 0) return;
        px -= vx * World.H;
        py -= vy * World.H;
    }

    void setPinned(boolean p) {
        pinned = p;
        invM = p ? 0f : baseInvM;
        px = x;
        py = y;
    }
}

package com.frameboost.ragdoll;

/** A collision shape glued to a rigid body: circle, capsule (segment + radius) or box. */
final class Shape {
    static final int CIRCLE = 0, CAPSULE = 1, BOX = 2;
    static int nextId;

    final int type;
    final int id = ++nextId;
    RB body;
    float ox, oy, la;          // offset and angle in the body frame
    float hl, hh, r;           // capsule: half length + radius | box: half width, half height | circle: radius
    float density = 1f, friction = 0.6f, restitution = 0.05f;
    int group;                 // shapes sharing a non-zero group never collide (the limbs of one character)
    boolean sensor;            // no collisions (kept for hit-tests only)
    int color = 0xFFcccccc;    // drawing hint
    Object user;               // gameplay data (the character part this shape belongs to)

    // world cache, refreshed every step by RB.update()
    float cx, cy, ax, ay;      // centre and unit x axis of the shape
    float minX, minY, maxX, maxY;

    private Shape(int type) { this.type = type; }

    static Shape circle(float r) { Shape s = new Shape(CIRCLE); s.r = r; return s; }
    static Shape capsule(float halfLen, float r) { Shape s = new Shape(CAPSULE); s.hl = halfLen; s.r = r; return s; }
    static Shape box(float hw, float hh) { Shape s = new Shape(BOX); s.hl = hw; s.hh = hh; return s; }

    Shape at(float x, float y, float angle) { ox = x; oy = y; la = angle; return this; }
    Shape mat(float density, float friction, float restitution) { this.density = density; this.friction = friction; this.restitution = restitution; return this; }
    Shape grp(int g) { group = g; return this; }
    Shape col(int c) { color = c; return this; }

    float area() {
        switch (type) {
            case CIRCLE: return (float) Math.PI * r * r;
            case CAPSULE: return 4f * hl * r + (float) Math.PI * r * r;
            default: return 4f * hl * hh;
        }
    }

    /** Moment of inertia about the shape's own centre for the given mass. */
    float inertia(float m) {
        switch (type) {
            case CIRCLE: return 0.5f * m * r * r;
            case CAPSULE: { float w = 2f * (hl + r), h = 2f * r; return m * (w * w + h * h) / 12f; }
            default: { float w = 2f * hl, h = 2f * hh; return m * (w * w + h * h) / 12f; }
        }
    }

    /** Longest reach from the shape centre (for bounding circles). */
    float reach() {
        switch (type) {
            case CIRCLE: return r;
            case CAPSULE: return hl + r;
            default: return (float) Math.sqrt(hl * hl + hh * hh);
        }
    }

    void update() {
        RB b = body;
        cx = b.x + b.c * ox - b.s * oy;
        cy = b.y + b.s * ox + b.c * oy;
        float a = b.a + la;
        ax = (float) Math.cos(a);
        ay = (float) Math.sin(a);
        float ex, ey;
        switch (type) {
            case CIRCLE: ex = ey = r; break;
            case CAPSULE: ex = Math.abs(ax) * hl + r; ey = Math.abs(ay) * hl + r; break;
            default: ex = Math.abs(ax) * hl + Math.abs(ay) * hh; ey = Math.abs(ay) * hl + Math.abs(ax) * hh; break;
        }
        minX = cx - ex; maxX = cx + ex; minY = cy - ey; maxY = cy + ey;
    }
}

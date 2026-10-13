package com.frameboost.ragdoll;

import java.util.ArrayList;

/** Rigid body: position of the centre of mass, angle, velocities and its shapes. */
final class RB {
    static int nextId;
    final int id = ++nextId;

    float x, y, a, c = 1f, s = 0f;
    float vx, vy, w;
    float m, I, invM, invI;
    float fx, fy, tq;
    float linDamp = 0.03f, angDamp = 0.08f;     // per second
    float gravScale = 1f;
    boolean frozen, removed, isStatic;
    int supportStep = -100;                     // last step this body rested on something
    float sInvM, sInvI;
    final ArrayList<Shape> shapes = new ArrayList<>(2);
    final ArrayList<Joint> joints = new ArrayList<>(2);
    Object user;                                // gameplay owner

    RB() { }

    RB(float x, float y, float angle) { this.x = x; this.y = y; setAngle(angle); }

    RB add(Shape sh) {
        sh.body = this;
        shapes.add(sh);
        computeMass();
        sh.update();
        return this;
    }

    RB makeStatic() {
        isStatic = true;
        invM = invI = 0f;
        vx = vy = w = 0f;
        return this;
    }

    void setAngle(float angle) { a = angle; c = (float) Math.cos(angle); s = (float) Math.sin(angle); }

    void computeMass() {
        if (isStatic) { invM = invI = 0f; return; }
        float mass = 0f, inertia = 0f;
        for (int i = 0; i < shapes.size(); i++) {
            Shape sh = shapes.get(i);
            float sm = sh.area() * sh.density;
            mass += sm;
            inertia += sh.inertia(sm) + sm * (sh.ox * sh.ox + sh.oy * sh.oy);
        }
        m = mass; I = inertia;
        invM = mass > 0f ? 1f / mass : 0f;
        invI = inertia > 0f ? 1f / inertia : 0f;
        if (frozen) { sInvM = invM; sInvI = invI; invM = invI = 0f; }
    }

    void setFrozen(boolean f) {
        if (f == frozen || isStatic) return;
        if (f) { sInvM = invM; sInvI = invI; invM = invI = 0f; vx = vy = w = 0f; }
        else { invM = sInvM; invI = sInvI; }
        frozen = f;
    }

    boolean dynamic() { return invM > 0f; }

    void update() {
        for (int i = 0; i < shapes.size(); i++) shapes.get(i).update();
    }

    void applyImpulse(float jx, float jy, float px, float py) {
        if (invM == 0f) return;
        vx += jx * invM; vy += jy * invM;
        w += invI * ((px - x) * jy - (py - y) * jx);
    }

    void applyForce(float fxx, float fyy, float px, float py) {
        fx += fxx; fy += fyy;
        tq += (px - x) * fyy - (py - y) * fxx;
    }

    /** Velocity of the material point at world (px, py). */
    float velX(float py) { return vx - w * (py - y); }
    float velY(float px) { return vy + w * (px - x); }

    float localToWorldX(float lx, float ly) { return x + c * lx - s * ly; }
    float localToWorldY(float lx, float ly) { return y + s * lx + c * ly; }
    float worldToLocalX(float px, float py) { return c * (px - x) + s * (py - y); }
    float worldToLocalY(float px, float py) { return -s * (px - x) + c * (py - y); }
}

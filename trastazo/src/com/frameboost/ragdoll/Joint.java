package com.frameboost.ragdoll;

/**
 * Constraints between two bodies (sequential impulses):
 * REVOLUTE: a pin with optional angle limits, a PD "muscle" and a velocity motor.
 * WELD: pin + locked angle. MOUSE: soft pull of a body point towards a target (dragging).
 */
final class Joint {
    static final int REVOLUTE = 0, WELD = 1, MOUSE = 2;

    final int kind;
    RB a, b;
    float lax, lay, lbx, lby;         // anchors in each body's frame
    float ref;                        // relative angle at rest (b.a - a.a)
    boolean limit; float lo, hi;      // relative angle limits around ref
    boolean muscle; float target, hz = 6f, damp = 0.8f, maxTorque;
    boolean motor; float motorSpeed, motorMax;
    float baseTorque;                 // full-strength muscle torque (Person scales maxTorque with muscle tone)
    float breakForce;                 // 0 = unbreakable (force units: impulse / dt)
    boolean collide;                  // do the two bodies collide with each other?
    boolean broken, removed;
    Object user;
    int tag;

    // mouse joint
    float tx, ty, maxForce = 1e9f;

    // solver state
    private float rax, ray, rbx, rby, k11, k12, k22, axial, bx, by;
    private float ix, iy, iMuscle, iMotor, iLo, iHi, iWeld;
    private float muMass, muGamma, muBias;
    private float mGamma, mbx, mby;
    float reaction;                   // last step's linear reaction force (for breaking)
    float angleErr;

    Joint(int kind, RB a, RB b) { this.kind = kind; this.a = a; this.b = b; }

    static Joint pin(RB a, RB b, float wx, float wy) {
        Joint j = new Joint(REVOLUTE, a, b);
        j.setAnchorWorld(wx, wy);
        return j;
    }

    static Joint weld(RB a, RB b, float wx, float wy) {
        Joint j = new Joint(WELD, a, b);
        j.setAnchorWorld(wx, wy);
        return j;
    }

    static Joint mouse(RB ground, RB body, float wx, float wy, float maxForce) {
        Joint j = new Joint(MOUSE, ground, body);
        j.lax = j.lay = 0f;
        j.lbx = body.worldToLocalX(wx, wy);
        j.lby = body.worldToLocalY(wx, wy);
        j.tx = wx; j.ty = wy;
        j.maxForce = maxForce;
        j.hz = 5f; j.damp = 0.9f;
        return j;
    }

    void setAnchorWorld(float wx, float wy) {
        lax = a.worldToLocalX(wx, wy); lay = a.worldToLocalY(wx, wy);
        lbx = b.worldToLocalX(wx, wy); lby = b.worldToLocalY(wx, wy);
        ref = b.a - a.a;
    }

    Joint limits(float lo, float hi) { this.limit = true; this.lo = lo; this.hi = hi; return this; }

    Joint muscle(float hz, float damp, float maxTorque) { this.muscle = true; this.hz = hz; this.damp = damp; this.maxTorque = maxTorque; return this; }

    float angle() { return b.a - a.a - ref; }

    float anchorAX() { return a.localToWorldX(lax, lay); }
    float anchorAY() { return a.localToWorldY(lax, lay); }
    float anchorBX() { return b.localToWorldX(lbx, lby); }
    float anchorBY() { return b.localToWorldY(lbx, lby); }

    private static final float BETA = 0.25f;

    void prestep(float dt) {
        rax = a.c * lax - a.s * lay; ray = a.s * lax + a.c * lay;
        rbx = b.c * lbx - b.s * lby; rby = b.s * lbx + b.c * lby;
        float mA = a.invM, mB = b.invM, iA = a.invI, iB = b.invI;
        float cx = (b.x + rbx) - (a.x + rax), cy = (b.y + rby) - (a.y + ray);
        angleErr = b.a - a.a - ref;
        if (kind == MOUSE) {
            float omega = 2f * (float) Math.PI * hz;
            float d = 2f * (1f / Math.max(1e-9f, mB)) * damp * omega;
            float k = (1f / Math.max(1e-9f, mB)) * omega * omega;
            mGamma = dt * (d + dt * k);
            mGamma = mGamma != 0f ? 1f / mGamma : 0f;
            float beta = dt * k * mGamma;
            mbx = (b.x + rbx - tx) * beta; mby = (b.y + rby - ty) * beta;
            k11 = mB + iB * rby * rby + mGamma;
            k12 = -iB * rbx * rby;
            k22 = mB + iB * rbx * rbx + mGamma;
            // warm start
            b.vx += mB * ix; b.vy += mB * iy;
            b.w += iB * (rbx * iy - rby * ix);
            return;
        }
        k11 = mA + mB + iA * ray * ray + iB * rby * rby;
        k12 = -iA * rax * ray - iB * rbx * rby;
        k22 = mA + mB + iA * rax * rax + iB * rbx * rbx;
        axial = iA + iB > 0f ? 1f / (iA + iB) : 0f;
        float inv = 1f / dt;
        bx = BETA * inv * cx; by = BETA * inv * cy;
        float bl = (float) Math.sqrt(bx * bx + by * by);
        if (bl > 900f) { bx *= 900f / bl; by *= 900f / bl; }
        if (muscle && maxTorque > 0f && axial > 0f) {
            float m = axial;
            float omega = 2f * (float) Math.PI * hz;
            float d = 2f * m * damp * omega, k = m * omega * omega;
            muGamma = dt * (d + dt * k);
            muGamma = muGamma != 0f ? 1f / muGamma : 0f;
            muBias = angleErr_forMuscle() * dt * k * muGamma;
            float em = iA + iB + muGamma;
            muMass = em > 0f ? 1f / em : 0f;
        } else { iMuscle = 0f; }
        if (!motor) iMotor = 0f;
        if (!limit) { iLo = iHi = 0f; }
        if (kind != WELD) iWeld = 0f;
        // warm start (angular impulse p: a.w -= iA*p, b.w += iB*p)
        float ang = iMuscle + iMotor + iWeld + iLo - iHi;
        a.vx -= mA * ix; a.vy -= mA * iy;
        a.w -= iA * (rax * iy - ray * ix + ang);
        b.vx += mB * ix; b.vy += mB * iy;
        b.w += iB * (rbx * iy - rby * ix + ang);
    }

    private float angleErr_forMuscle() { return (b.a - a.a - ref) - target; }

    void solve(float dt) {
        float mA = a.invM, mB = b.invM, iA = a.invI, iB = b.invI;
        if (kind == MOUSE) {
            float cdx = b.vx - b.w * rby, cdy = b.vy + b.w * rbx;
            float rx = -(cdx + mbx + mGamma * ix), ry = -(cdy + mby + mGamma * iy);
            float det = k11 * k22 - k12 * k12;
            if (det == 0f) return;
            float px = (k22 * rx - k12 * ry) / det, py = (k11 * ry - k12 * rx) / det;
            float ox = ix, oy = iy;
            ix += px; iy += py;
            float max = maxForce * dt, l = (float) Math.sqrt(ix * ix + iy * iy);
            if (l > max) { ix *= max / l; iy *= max / l; }
            px = ix - ox; py = iy - oy;
            b.vx += mB * px; b.vy += mB * py;
            b.w += iB * (rbx * py - rby * px);
            return;
        }
        // muscle (PD spring on the relative angle)
        if (muscle && muMass > 0f) {
            float cdot = b.w - a.w;
            float imp = -muMass * (cdot + muBias + muGamma * iMuscle);
            float old = iMuscle, max = dt * maxTorque;
            iMuscle = Math.max(-max, Math.min(max, old + imp));
            imp = iMuscle - old;
            a.w -= iA * imp; b.w += iB * imp;
        }
        if (motor && axial > 0f) {
            float cdot = b.w - a.w - motorSpeed;
            float imp = -axial * cdot;
            float old = iMotor, max = dt * motorMax;
            iMotor = Math.max(-max, Math.min(max, old + imp));
            imp = iMotor - old;
            a.w -= iA * imp; b.w += iB * imp;
        }
        if (limit && axial > 0f) {
            float ang = b.a - a.a - ref;
            float cLo = ang - lo;
            float cdot = b.w - a.w;
            float bias = cLo > 0f ? cLo / dt : BETA * cLo / dt;
            float imp = -axial * (cdot + bias);
            float old = iLo;
            iLo = Math.max(0f, old + imp);
            imp = iLo - old;
            a.w -= iA * imp; b.w += iB * imp;
            float cHi = hi - ang;
            cdot = a.w - b.w;
            bias = cHi > 0f ? cHi / dt : BETA * cHi / dt;
            imp = -axial * (cdot + bias);
            old = iHi;
            iHi = Math.max(0f, old + imp);
            imp = iHi - old;
            a.w += iA * imp; b.w -= iB * imp;
        }
        if (kind == WELD && axial > 0f) {
            float cdot = b.w - a.w;
            float imp = -axial * (cdot + BETA / dt * (b.a - a.a - ref));
            iWeld += imp;
            a.w -= iA * imp; b.w += iB * imp;
        }
        // point constraint
        float cdx = (b.vx - b.w * rby) - (a.vx - a.w * ray);
        float cdy = (b.vy + b.w * rbx) - (a.vy + a.w * rax);
        float rx = -(cdx + bx), ry = -(cdy + by);
        float det = k11 * k22 - k12 * k12;
        if (det == 0f) return;
        float px = (k22 * rx - k12 * ry) / det, py = (k11 * ry - k12 * rx) / det;
        ix += px; iy += py;
        a.vx -= mA * px; a.vy -= mA * py;
        a.w -= iA * (rax * py - ray * px);
        b.vx += mB * px; b.vy += mB * py;
        b.w += iB * (rbx * py - rby * px);
    }

    void post(float dt) {
        reaction = (float) Math.sqrt(ix * ix + iy * iy) / dt;
        if (breakForce > 0f && reaction > breakForce) broken = true;
    }

    void resetImpulses() { ix = iy = iMuscle = iMotor = iLo = iHi = iWeld = 0f; }
}

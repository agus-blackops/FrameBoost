package com.frameboost.ragdoll;

import java.util.ArrayList;

/** Anything the player can place: a person, a crate, a bomb, a turret... owns bodies and joints in the Sim. */
abstract class Ent {
    static int nextId;
    final int id = ++nextId;
    final ArrayList<RB> bodies = new ArrayList<>();
    final ArrayList<Joint> joints = new ArrayList<>();
    boolean removed;
    int item;                   // Items.* id that spawned it (save files)
    float param;                // spawn parameter (size, kind...)
    boolean burning, flammable;
    float burnT, charT;

    RB body(RB b) { bodies.add(b); b.user = this; return b; }
    Joint joint(Joint j) { joints.add(j); j.user = this; return j; }

    abstract float cx();
    abstract float cy();

    float radius() {
        float x = cx(), y = cy(), m = 0f;
        for (int i = 0; i < bodies.size(); i++) {
            RB b = bodies.get(i);
            float d = (float) Math.sqrt((b.x - x) * (b.x - x) + (b.y - y) * (b.y - y));
            if (!b.shapes.isEmpty()) d += b.shapes.get(0).reach();
            if (d > m) m = d;
        }
        return m;
    }
}

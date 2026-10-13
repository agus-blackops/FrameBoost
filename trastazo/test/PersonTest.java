package com.frameboost.ragdoll;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class PersonTest {
    static int fails;
    static void check(String n, boolean ok) { System.out.println((ok ? "PASS " : "FAIL ") + n); if (!ok) fails++; }

    public static void main(String[] a) throws Exception {
        String out = a[0];
        Random rnd = new Random(5);
        Sim s = new Sim();
        s.floorY = 0;
        final Person[] ps = new Person[1];
        ps[0] = Person.create(s, 400, -100, 1f, 1, Person.HUMAN, rnd);
        s.hook = new Sim.Hook() { @Override public void before(float dt) { for (Person p : ps) if (p != null) p.control(s, dt); } };
        Person p = ps[0];
        p.tone = 1f;
        List<BufferedImage> strip = new ArrayList<>();
        // drop and stand
        for (int i = 0; i < 60 * 4; i++) {
            s.update(1f / 60f);
            if (i % 40 == 0 && strip.size() < 8) strip.add(DebugView.snap(s, 400, -100, 1.0f, 300, 260));
        }
        DebugView.strip(strip, out + "/p_stand.png");
        RB pel = p.rb[Person.PELVIS];
        System.out.printf("   standing: pelvis y=%.1f (target ~%.0f) x=%.1f angle=%.2f chest a=%.2f%n", pel.y, -91.0, pel.x, pel.a, p.rb[Person.CHEST].a);
        check("stands upright after landing", p.upright() && pel.y < -70 && pel.y > -105);
        // walk
        strip.clear();
        p.walk = 1;
        float x0 = pel.x;
        for (int i = 0; i < 60 * 5; i++) {
            s.update(1f / 60f);
            if (i % 30 == 0 && strip.size() < 10) strip.add(DebugView.snap(s, pel.x, -100, 1.0f, 240, 260));
        }
        DebugView.strip(strip, out + "/p_walk.png");
        System.out.printf("   walked %.0f units in 5 s, upright=%s pelvis y=%.1f%n", pel.x - x0, p.upright(), pel.y);
        check("walks forward", pel.x - x0 > 250 && p.upright());
        // turn and walk back
        p.walk = 0;
        for (int i = 0; i < 40; i++) s.update(1f / 60f);
        p.turn(); p.walk = -1;
        x0 = pel.x;
        for (int i = 0; i < 60 * 4; i++) s.update(1f / 60f);
        System.out.printf("   after turn walked %.0f, upright=%s%n", pel.x - x0, p.upright());
        check("turns around and walks back", pel.x - x0 < -200 && p.upright());
        // run
        p.walk = 1; p.run = true; p.turn();
        x0 = pel.x;
        for (int i = 0; i < 60 * 3; i++) s.update(1f / 60f);
        System.out.printf("   ran %.0f in 3 s upright=%s%n", pel.x - x0, p.upright());
        check("runs", pel.x - x0 > 350 && p.upright());
        // ragdoll: tone off, drop from height
        p.walk = 0; p.run = false;
        p.tone = 0f;
        for (RB b : p.rb) { b.y -= 200; }
        strip.clear();
        for (int i = 0; i < 60 * 3; i++) {
            s.update(1f / 60f);
            if (i % 20 == 0 && strip.size() < 8) strip.add(DebugView.snap(s, pel.x, -100, 1.0f, 300, 260));
        }
        DebugView.strip(strip, out + "/p_fall.png");
        // joint integrity
        float maxGap = 0;
        for (Joint j : p.jt) maxGap = Math.max(maxGap, (float) Math.hypot(j.anchorAX() - j.anchorBX(), j.anchorAY() - j.anchorBY()));
        System.out.printf("   ragdoll max joint gap %.2f%n", maxGap);
        check("joints hold while limp", maxGap < 6f);
        // get up
        p.tone = 1f;
        strip.clear();
        for (int i = 0; i < 60 * 6; i++) {
            s.update(1f / 60f);
            if (i % 40 == 0 && strip.size() < 8) strip.add(DebugView.snap(s, pel.x, -100, 1.0f, 300, 260));
        }
        DebugView.strip(strip, out + "/p_getup.png");
        System.out.printf("   after getting up: upright=%s pelvis y=%.1f%n", p.upright(), pel.y);
        check("gets back on its feet", p.upright() && pel.y < -60);
        System.out.println(fails == 0 ? "PERSON ALL PASS" : "PERSON FAILURES " + fails);
    }
}
